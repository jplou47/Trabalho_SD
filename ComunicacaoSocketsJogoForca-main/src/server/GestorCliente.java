package server;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * GestorCliente — Gere a ligação TCP de um único cliente.
 *
 * Corre numa thread dedicada (uma por cliente).
 * Responsabilidades:
 *  1. Tentar registar o jogador no GestorJogo.
 *     - Se o jogo estiver cheio/iniciado, enviar FULL e fechar.
 *  2. Enviar WELCOME ao cliente com o seu ID.
 *  3. Ler mensagens do cliente em loop (protocolo: uma mensagem por linha).
 *  4. Processar mensagens GUESS e delegar ao GestorJogo.
 *  5. Lidar com desconexões e timeouts de socket.
 *
 * Protocolo (mensagens que este handler processa):
 *   Cliente → Servidor:  GUESS <texto>
 *   Servidor → Cliente:  WELCOME, START, ROUND, STATE, END, FULL, INFO
 *
 * Cada mensagem enviada ao cliente termina com '\n' (newline),
 * para que o cliente possa usar readLine().
 */
public class GestorCliente implements Runnable {

    /** Socket TCP desta ligação. */
    private final Socket socket;

    /** Referência ao GestorJogo central. */
    private final GestorJogo gameManager;

    /** ID atribuído a este jogador (-1 até ser registado). */
    private int playerId = -1;

    /** Nome do jogador (definido via mensagem NAME após WELCOME). */
    private volatile String playerName = null;

    /** Stream para enviar mensagens ao cliente. */
    private PrintWriter out;

    /** Stream para ler mensagens do cliente. */
    private BufferedReader in;

    /** Indicador de controlo: o gestor de cliente ainda está ativo? */
    private volatile boolean ativo = true;

    /**
     * @param socket      Socket TCP do cliente.
     * @param gameManager O GestorJogo (instância única) para delegar operações de jogo.
     */
    public GestorCliente(Socket socket, GestorJogo gameManager) {
        this.socket      = socket;
        this.gameManager = gameManager;
    }

    // ─────────────────────────────────────────────────────────────
    //  Ciclo de vida da thread
    // ─────────────────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            // Configurar streams de entrada/saída
            // PrintWriter com emissão automática (autoFlush=true): envia imediatamente após println
            out = new PrintWriter(
                    new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),
                    true);
            in  = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            // ── Tentativa de registo no lobby ────────────────────────────────
            playerId = gameManager.registerPlayer(this);

            if (playerId == -1) {
                // Servidor cheio ou jogo já iniciado: rejeitar ligação
                sendMessage("INFO Jogo em curso ou lobby cheio. Tente novamente na próxima partida.");
                sendMessage("FULL");
                UtilitarioLog.warning("Ligação rejeitada (jogo em curso ou lobby cheio).");
                return; // O finally fecha o socket
            }

            // Enviar boas-vindas com ID e número de jogadores atuais
            // Nota: o total pode ainda crescer (mais jogadores podem entrar)
            sendMessage("WELCOME " + playerId + " " + gameManager.getActiveCount());
            UtilitarioLog.info("Jogador " + playerId + " bem-vindo.");

            // Se o jogo já arrancou (jogador entrou depois do lobby mas antes da 1ª ronda),
            // enviar o estado atual para que o cliente saiba a máscara e as tentativas.
            if (gameManager.isGameStarted()) {
                EstadoJogo gs = gameManager.getCurrentEstadoJogo();
                if (gs != null) {
                    sendMessage("START " + gs.getMaskString() + " "
                            + gs.getAttemptsLeft() + " "
                            + EstadoJogo.ROUND_TIMEOUT_MS);
                    UtilitarioLog.info("Jogador " + playerId + " recebeu estado atual do jogo.");
                }
            }

            // ── Ciclo de leitura de mensagens ─────────────────────────────────
            // Tempo limite de leitura para evitar bloqueio indefinido da thread.
            // O controlo do tempo limite da ronda continua no GestorJogo.
            socket.setSoTimeout(EstadoJogo.ROUND_TIMEOUT_MS);

            while (ativo) {
                try {
                    String line = readLine();
                    if (line == null) {
                        break;
                    }
                    processMessage(line.trim());
                } catch (SocketTimeoutException e) {
                    // Sem mensagem neste intervalo: continuar a aguardar.
                    // A ronda é resolvida no GestorJogo com CountDownLatch + timeout.
                }
            }

        } catch (IOException e) {
            if (ativo) {
                UtilitarioLog.warning("Jogador " + playerId + " desconectou: " + e.getMessage());
            }
        } catch (Exception e) {
            UtilitarioLog.error("Erro inesperado no gestor do jogador " + playerId, e);

        } finally {
            // Notificar o GestorJogo da desconexão (para não bloquear o trinco de sincronização)
            if (playerId != -1) {
                gameManager.removePlayer(this, playerId);
            }
            close();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Processamento de mensagens recebidas
    // ─────────────────────────────────────────────────────────────

    /**
     * Processa uma linha recebida do cliente.
     * Protocolo esperado: "GUESS <texto>"
     *
     * Com tratamento robusto de exceções e validação.
     *
     * @param message A linha recebida (já com trim aplicado).
     */
    private void processMessage(String message) {
        try {
            if (message == null || message.isEmpty()) {
                UtilitarioLog.debug("Mensagem vazia recebida de jogador " + playerId);
                return;
            }

            UtilitarioLog.debug("Jogador " + playerId + " enviou: " + message);

            if (message.toUpperCase().startsWith("GUESS ")) {
                // Extrair o texto após "GUESS " (índice 6)
                String guess = message.substring(6).trim();
                
                // Validar a jogada
                EstadoJogo currentState = gameManager.getCurrentEstadoJogo();
                if (currentState != null) {
                    try {
                        currentState.validateGuess(guess);
                    } catch (ExcecaoJogadaInvalida e) {
                        UtilitarioLog.warning("Jogada inválida de jogador " + playerId + ": " + e.getMessage());
                        sendMessage("INFO Jogada inválida: " + e.getMessage());
                        return;
                    }
                }
                
                // Delegar ao GestorJogo (que atualiza o EstadoJogo e o trinco de sincronização)
                gameManager.submitGuess(playerId, guess);
                UtilitarioLog.debug("Jogada registada para jogador " + playerId);

            } else if (message.toUpperCase().startsWith("NAME ")) {
                // Registar o nome do jogador (enviado pelo cliente após WELCOME)
                String name = message.substring(5).trim();
                if (!name.isEmpty() && name.length() <= 30) {
                    // Sanitizar: remover caracteres de controlo
                    name = name.replaceAll("[\\p{Cntrl}]", "").trim();
                    if (!name.isEmpty()) {
                        playerName = name;
                        gameManager.registerPlayerName(playerId, playerName);
                        UtilitarioLog.info("Jogador " + playerId + " definiu nome: " + playerName);
                    }
                }

            } else {
                // Mensagem desconhecida: ignorar (o protocolo é simples)
                UtilitarioLog.debug("Mensagem desconhecida de jogador " + playerId + ": " + message);
            }
        } catch (Exception e) {
            UtilitarioLog.error("Erro ao processar mensagem", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  I/O com o cliente
    // ─────────────────────────────────────────────────────────────

    /**
     * Lê uma linha do cliente.
     * Retorna null se a ligação for fechada.
     */
    private String readLine() throws IOException {
        return in.readLine();
    }

    /**
     * Envia uma mensagem ao cliente.
     * Seguro para uso concorrente: PrintWriter.println() é atómico para linhas únicas.
     *
     * @param message A mensagem a enviar (sem '\n' — println adiciona).
     */
    public synchronized void sendMessage(String message) {
        if (out != null && !socket.isClosed()) {
            out.println(message);
        }
    }

    /**
     * Fecha o socket e liberta recursos.
     * Pode ser chamado pelo próprio gestor ou pelo GestorJogo (fim do jogo).
     */
    public void close() {
        ativo = false;
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignorar erros ao fechar
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Getters
    // ─────────────────────────────────────────────────────────────

    public int getPlayerId() { return playerId; }

    public String getPlayerName() {
        return (playerName != null && !playerName.isEmpty()) ? playerName : "Jogador" + playerId;
    }
}
