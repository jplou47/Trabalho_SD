package server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * GestorJogo centraliza o ciclo de vida de uma única partida.
 *
 * Regras implementadas:
 * - Lobby aceita até 4 jogadores.
 * - O jogo inicia com 2+ jogadores ou quando expira o timeout do lobby.
 * - Após o início, novas ligações são rejeitadas com FULL.
 * - Rondas síncronas: o estado só é atualizado após recolher todas as jogadas
 *   (ou timeouts convertidos em jogadas vazias).
 */
public class GestorJogo {

    private static final GestorJogo INSTANCE = new GestorJogo();

    public static GestorJogo getInstance() {
        return INSTANCE;
    }

    private final List<GestorCliente> handlers = new ArrayList<>();

    /** Mapa playerId → nome do jogador (enviado pelo cliente após WELCOME). */
    private final Map<Integer, String> playerNames = new HashMap<>();

    private int nextPlayerId = 1;
    private volatile EstadoJogo gameState;
    private volatile CountDownLatch roundLatch;

    private volatile boolean gameStarted = false;
    private volatile boolean gameFinished = false;
    /**
     * Controla se ainda é possível entrar no jogo.
     * Permanece true desde o início até ao momento anterior ao envio coletivo da
     * primeira ronda. Após isso, novas ligações são rejeitadas com FULL.
     */
    private volatile boolean joiningOpen = true;

    private GestorJogo() {
        Thread lobbyThread = new Thread(this::runLobby, "lobby-thread");
        lobbyThread.setDaemon(true);
        lobbyThread.start();
    }

    public synchronized int registerPlayer(GestorCliente handler) {
        if (!joiningOpen || gameFinished || handlers.size() >= EstadoJogo.MAX_PLAYERS) {
            return -1;
        }

        int id = nextPlayerId++;
        handlers.add(handler);

        // Se o jogo já começou (entre o START e o primeiro ROUND), registar
        // o jogador também no estado do jogo para que participe desde a ronda 1.
        if (gameStarted && gameState != null) {
            gameState.addPlayer();
            gameState.initializePlayerScore(id);
            UtilitarioLog.info("Jogador " + id + " entrou em jogo antes da 1ª ronda. Total: " + handlers.size());
        } else {
            UtilitarioLog.info("Jogador " + id + " entrou no lobby. Total: " + handlers.size());
        }

        notifyAll();
        return id;
    }

    /** Indica se o jogo já arrancou (gameState existe e está em progresso). */
    public boolean isGameStarted() {
        return gameStarted;
    }

    public void removePlayer(GestorCliente handler, int playerId) {
        CountDownLatch latch = null;
        synchronized (this) {
            boolean removed = handlers.remove(handler);
            if (!removed) {
                return;
            }
            UtilitarioLog.info("Jogador " + playerId + " desconectado.");
            if (gameStarted && !gameFinished && gameState != null) {
                latch = roundLatch;
            }
            notifyAll();
        }

        // Submeter jogada vazia fora do lock do GestorJogo para evitar deadlock
        if (latch != null) {
            boolean accepted = gameState.submitGuess(playerId, "");
            if (accepted && latch.getCount() > 0) {
                latch.countDown();
            }
        }
    }

    public EstadoJogo getCurrentEstadoJogo() {
        return gameState;
    }

    public void submitGuess(int playerId, String guess) {
        EstadoJogo current = gameState;
        if (current == null || !gameStarted || gameFinished) {
            return;
        }

        boolean accepted = current.submitGuess(playerId, guess);

        if (accepted) {
            CountDownLatch latch = roundLatch;
            if (latch != null && latch.getCount() > 0) {
                latch.countDown();
            }
        }
    }

    public synchronized int getActiveCount() {
        return handlers.size();
    }

    /** Regista ou atualiza o nome de um jogador. */
    public synchronized void registerPlayerName(int playerId, String name) {
        playerNames.put(playerId, name);
        UtilitarioLog.info("Nome registado: Jogador " + playerId + " = " + name);
    }

    /** Devolve o nome de um jogador, ou 'Jogador<id>' como fallback. */
    private synchronized String getPlayerName(int playerId) {
        return playerNames.getOrDefault(playerId, "Jogador" + playerId);
    }

    private void runLobby() {
        try {
            while (true) {
                synchronized (this) {
                    // Reiniciar estado para o próximo jogo
                    gameStarted = false;
                    joiningOpen = true;
                    handlers.clear();
                    playerNames.clear();
                    nextPlayerId = 1;
                    gameState = null;
                    roundLatch = null;

                    while (!gameStarted) {
                        while (handlers.isEmpty()) {
                            UtilitarioLog.info("[Lobby] À espera do primeiro jogador...");
                            wait();
                        }

                        long deadline = System.currentTimeMillis() + EstadoJogo.LOBBY_TIMEOUT_MS;
                        broadcast("INFO À espera de jogadores... (timeout "
                                + (EstadoJogo.LOBBY_TIMEOUT_MS / 1000) + "s)");

                        // Aguarda até que:
                        //  (a) o máximo de jogadores (4) esteja ligado → inicia imediatamente, OU
                        //  (b) o timeout expire → inicia se houver pelo menos 2 jogadores.
                        // Desta forma, jogadores 3 e 4 podem entrar dentro da janela de 20s.
                        while (handlers.size() < EstadoJogo.MAX_PLAYERS) {
                            long remaining = deadline - System.currentTimeMillis();
                            if (remaining <= 0) {
                                break; // Timeout expirado
                            }
                            wait(remaining);
                        }

                        // Não há jogadores suficientes para iniciar o jogo
                        if (handlers.size() < 2) {
                            broadcast("INFO Jogadores insuficientes para iniciar. A aguardar mais jogadores...");
                            continue;
                        }

                        gameStarted = true;
                        EntradaPalavra word = BancoPalavras.getRandomWord();
                        gameState = new EstadoJogo(word, Dificuldade.NORMAL);
                        for (int i = 0; i < handlers.size(); i++) {
                            gameState.addPlayer();
                        }
                        // Inicializar pontuação para cada jogador
                        for (GestorCliente h : handlers) {
                            gameState.initializePlayerScore(h.getPlayerId());
                        }
                    }
                }

                runGame();
                UtilitarioLog.info("[Lobby] Jogo concluído. A aguardar novo jogo...");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            synchronized (this) {
                gameFinished = true;
                notifyAll();
            }
        }
    }

    private void runGame() {
        gameState.startGame();

        broadcast("START " + gameState.getMaskString() + " "
                + gameState.getAttemptsLeft() + " "
                + EstadoJogo.ROUND_TIMEOUT_MS);

        UtilitarioLog.info("[Jogo] Palavra escolhida: " + gameState.getSecretWord());

        boolean firstRound = true;
        while (gameState.getStatus() == EstadoJogo.Estado.EM_PROGRESSO) {
            List<GestorCliente> instantaneo;
            if (firstRound) {
                // Fechar entradas de novos jogadores atomicamente com o instantâneo,
                // garantindo que ninguém entra depois do trinco de sincronização estar definido.
                synchronized (this) {
                    joiningOpen = false;
                    instantaneo = new ArrayList<>(handlers);
                }
                UtilitarioLog.info("[Jogo] Entradas fechadas. Jogadores na 1ª ronda: " + instantaneo.size());
                firstRound = false;
            } else {
                instantaneo = getActiveHandlersSnapshot();
            }
            if (instantaneo.isEmpty()) {
                break;
            }

            Set<Integer> activeIds = new HashSet<>();
            for (GestorCliente h : instantaneo) {
                activeIds.add(h.getPlayerId());
            }

            int round = gameState.getCurrentRound();
            broadcast("ROUND " + round + " " + gameState.getMaskString() + " "
                    + gameState.getAttemptsLeft() + " " + gameState.getUsedLettersString());

            roundLatch = new CountDownLatch(activeIds.size());
            try {
                boolean complete = roundLatch.await(EstadoJogo.ROUND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!complete) {
                    applyTimeoutGuesses(activeIds);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            gameState.processRound(activeIds);

            // Envio coletivo do resumo das jogadas desta ronda a todos os clientes
            broadcastRoundSummary(activeIds);

            broadcast("STATE " + gameState.getMaskString() + " "
                    + gameState.getAttemptsLeft() + " " + gameState.getUsedLettersString());
        }

        // Se o jogo ainda está em progresso (todos desconectaram antes do fim), terminar
        if (gameState.getStatus() == EstadoJogo.Estado.EM_PROGRESSO) {
            UtilitarioLog.info("[Jogo] Todos os jogadores desconectaram. Encerrando.");
            gameState.finishGame();
        }

        sendEndMessage();
        closeAllConnections();
    }

    /**
     * Envia o resumo das jogadas da ronda a todos os clientes.
     * Formato: "INFO -- Ronda X: <Nome> (#id): <jogada> | ..."
     */
    private void broadcastRoundSummary(Set<Integer> activeIds) {
        Map<Integer, String> guesses = gameState.getRoundGuesses();
        List<Integer> sorted = new ArrayList<>(activeIds);
        Collections.sort(sorted);

        StringBuilder sb = new StringBuilder();
        sb.append("INFO --- Jogadas da ronda ---");
        broadcast(sb.toString());

        for (int id : sorted) {
            String name = getPlayerName(id);
            String guess = guesses.getOrDefault(id, "");
            String descricao;
            if (guess.isEmpty()) {
                descricao = "(sem jogada - timeout)";
            } else if (guess.length() == 1) {
                descricao = "letra '" + guess.toUpperCase() + "'";
            } else {
                descricao = "palavra '" + guess.toUpperCase() + "'";
            }
            broadcast("INFO " + name + " (#" + id + "): " + descricao);
        }
    }

    private void applyTimeoutGuesses(Set<Integer> activeIds) {        Map<Integer, String> guesses = gameState.getRoundGuesses();
        for (int id : activeIds) {
            if (!guesses.containsKey(id)) {
                gameState.submitGuess(id, "");
            }
        }
    }

    private void sendEndMessage() {
        if (gameState.isVictory()) {
            List<Integer> winners = new ArrayList<>(gameState.getWinnerIds());
            Collections.sort(winners);
            StringBuilder ids = new StringBuilder();
            for (int i = 0; i < winners.size(); i++) {
                if (i > 0) ids.append(',');
                ids.append(winners.get(i));
            }
            broadcast("END WIN " + ids + " " + gameState.getSecretWord());
        } else {
            broadcast("END LOSE " + gameState.getSecretWord());
        }
    }

    private synchronized void closeAllConnections() {
        for (GestorCliente h : handlers) {
            h.close();
        }
        handlers.clear();
    }

    private synchronized List<GestorCliente> getActiveHandlersSnapshot() {
        return new ArrayList<>(handlers);
    }

    public synchronized void broadcast(String message) {
        for (GestorCliente h : handlers) {
            h.sendMessage(message);
        }
    }
}
