package client;

import java.net.*;
import java.io.*;
import java.util.Scanner;

/**
 * ClienteForca — Cliente para o Jogo da Forca (Hangman).
 *
 * Estabelece ligação TCP com o ServidorForca e participa no jogo seguindo o protocolo:
 *   Servidor → Cliente: WELCOME, START, ROUND, STATE, END, FULL, INFO
 *   Cliente → Servidor: GUESS <letra ou palavra>
 *
 * O cliente lê mensagens do servidor numa thread dedicada (para não bloquear a UI)
 * e permite que o jogador submeta jogadas via teclado.
 */
public class ClienteForca {

    /** Endereço do servidor (localhost). */
    private static final String SERVER_HOST = "localhost";

    /** Porta do servidor. */
    private static final int SERVER_PORT = 12345;

    // ─────────────────────────────────────────────────────────────
    //  ASCII Art — Forca (6 erros possíveis)
    // ─────────────────────────────────────────────────────────────

    private static final String[] HANGMAN = {
        // 6 tentativas restantes — sem boneco
        "  ╔═════╗\n" +
        "  ║     ║\n" +
        "        ║\n" +
        "        ║\n" +
        "        ║\n" +
        "        ║\n" +
        "  ════════",

        // 5 tentativas — cabeça
        "  ╔═════╗\n" +
        "  ║     ║\n" +
        "  O     ║\n" +
        "        ║\n" +
        "        ║\n" +
        "        ║\n" +
        "  ════════",

        // 4 tentativas — cabeça + corpo
        "  ╔═════╗\n" +
        "  ║     ║\n" +
        "  O     ║\n" +
        "  │     ║\n" +
        "        ║\n" +
        "        ║\n" +
        "  ════════",

        // 3 tentativas — + braço esquerdo
        "  ╔═════╗\n" +
        "  ║     ║\n" +
        "  O     ║\n" +
        " /│     ║\n" +
        "        ║\n" +
        "        ║\n" +
        "  ════════",

        // 2 tentativas — + braço direito
        "  ╔═════╗\n" +
        "  ║     ║\n" +
        "  O     ║\n" +
        " /│\\    ║\n" +
        "        ║\n" +
        "        ║\n" +
        "  ════════",

        // 1 tentativa — + perna esquerda
        "  ╔═════╗\n" +
        "  ║     ║\n" +
        "  O     ║\n" +
        " /│\\    ║\n" +
        " /      ║\n" +
        "        ║\n" +
        "  ════════",

        // 0 tentativas — DERROTA
        "  ╔═════╗\n" +
        "  ║     ║\n" +
        "  O     ║\n" +
        " /│\\    ║\n" +
        " / \\    ║\n" +
        "        ║\n" +
        "  ════════"
    };

    /** Socket TCP da ligação. */
    private Socket socket;

    /** Stream para enviar mensagens ao servidor. */
    private PrintWriter out;

    /** Stream para ler mensagens do servidor. */
    private BufferedReader in;

    /** Scanner para ler input do utilizador. */
    private Scanner scanner;

    /** ID do jogador (atribuído pelo servidor). */
    private int playerId = -1;

    /** Indicador de controlo: o cliente ainda está ativo? */
    private volatile boolean running = true;

    /** A máscara atual da palavra (ex: "_ a _ _"). */
    private volatile String currentMask = "";

    /** Tentativas restantes. */
    private volatile int attemptsLeft = 6;

    /** Tentativas restantes antes da última jogada (para detetar acerto/erro). */
    private volatile int previousAttemptsLeft = 6;

    /** Máscara anterior (para detetar se houve acerto). */
    private volatile String previousMask = "";

    /** Letras utilizadas. */
    private volatile String usedLetters = "";

    /** Se já foi feita pelo menos uma jogada. */
    private volatile boolean firstGuessReceived = false;

    /** Nome escolhido antes de a thread de leitura arrancar (evita condição de corrida). */
    private volatile String pendingName = null;

    // ─────────────────────────────────────────────────────────────
    //  Método principal
    // ─────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        ClienteForca client = new ClienteForca();
        client.run();
    }

    // ─────────────────────────────────────────────────────────────
    //  Ciclo de vida do cliente
    // ─────────────────────────────────────────────────────────────

    /**
     * Executa o cliente: estabelece a ligação e inicia o ciclo de jogo.
     *
     * O nome é pedido ANTES de a thread de leitura arrancar para evitar
     * uma condição de corrida entre as duas threads no mesmo Scanner.
     */
    public void run() {
        try {
            connectToServer();

            // Pedir o nome aqui, na thread principal, antes de iniciar a
            // thread de leitura — assim apenas uma thread lê do scanner de cada vez.
            System.out.print("  Digite o seu nome: ");
            System.out.flush();
            String name = scanner.nextLine().trim();
            if (name.isEmpty()) name = "Jogador";
            this.pendingName = name;

            Thread threadLeitura = new Thread(this::readMessages);
            threadLeitura.setDaemon(true);
            threadLeitura.setName("thread-leitura-servidor");
            threadLeitura.start();

            inputLoop();

        } catch (IOException e) {
            System.err.println("[Cliente] Erro fatal: " + e.getMessage());
        } finally {
            close();
        }
    }

    /**
     * Estabelece a ligação TCP com o servidor.
     */
    private void connectToServer() throws IOException {
        System.out.println("[Cliente] A conectar em " + SERVER_HOST + ":" + SERVER_PORT);
        socket = new Socket(SERVER_HOST, SERVER_PORT);
        out = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),
                true);
        in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        scanner = new Scanner(System.in);
        System.out.println("[Cliente] Ligado ao servidor!");
    }

    /**
     * Lê mensagens do servidor e as processa.
     * Corre numa thread separada para não bloquear o input do utilizador.
     */
    private void readMessages() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                processServerMessage(line.trim());
            }
        } catch (IOException e) {
            if (running) {
                System.out.println("[Cliente] Conexão com servidor perdida: " + e.getMessage());
            }
        } finally {
            running = false;
        }
    }

    /**
     * Processa uma mensagem recebida do servidor.
     * Protocolo esperado:
     *   - WELCOME <playerId> <playerCount>
     *   - START <mask> <attemptsLeft> <roundTimeoutMs>
     *   - ROUND <roundNumber> <mask> <attemptsLeft> <usedLetters>
     *   - STATE <mask> <attemptsLeft> <usedLetters>
     *   - END <WIN|LOSE>
     *   - FULL
     *   - INFO <message>
     */
    private void processServerMessage(String message) {
        if (message.isEmpty()) return;

        String[] parts = message.split(" ");
        if (parts.length == 0) return;
        
        String command = parts[0].toUpperCase();

        switch (command) {
            case "WELCOME":
                handleWelcome(parts);
                break;

            case "START":
                handleStart(parts);
                break;

            case "ROUND":
                handleRound(parts);
                break;

            case "STATE":
                handleState(parts);
                break;

            case "END":
                handleEnd(message);
                break;

            case "FULL":
                handleFull();
                break;

            case "INFO":
                handleInfo(message);
                break;

            default:
                System.out.println("[Cliente] Comando desconhecido: " + command);
        }

        if (running) {
            System.out.print("> "); // Voltar a mostrar o prompt
            System.out.flush();
        }
    }

    /**
     * WELCOME: mensagem de boas-vindas com ID do jogador.
     * Formato: WELCOME <playerId> <playerCount>
     */
    private void handleWelcome(String[] parts) {
        try {
            if (parts.length >= 3) {
                playerId = Integer.parseInt(parts[1]);
                int playerCount = Integer.parseInt(parts[2]);
                // Usar o nome já pedido na thread principal (pendingName)
                String name = (pendingName != null && !pendingName.isEmpty())
                        ? pendingName : "Jogador" + playerId;
                out.println("NAME " + name);
                System.out.println("  ✅  Bem-vindo, " + name + "! (ID: " + playerId + " | Jogadores: " + playerCount + ")");
            }
        } catch (NumberFormatException e) {
            System.out.println("[Cliente] Erro ao interpretar mensagem WELCOME: " + e.getMessage());
        }
    }

    /**
     * START: o jogo começou.
     * Formato: START <mask> <attemptsLeft> <roundTimeoutMs>
     * Onde <mask> pode ser: _ _ _ _ _ (com espaços)
     */
    private void handleStart(String[] parts) {
        try {
            if (parts.length >= 4) {
                int attemptsIdx = parts.length - 2;
                int timeoutIdx = parts.length - 1;

                this.attemptsLeft = Integer.parseInt(parts[attemptsIdx]);
                this.previousAttemptsLeft = this.attemptsLeft;
                int roundTimeoutMs = Integer.parseInt(parts[timeoutIdx]);

                StringBuilder maskBuilder = new StringBuilder();
                for (int i = 1; i < attemptsIdx; i++) {
                    if (i > 1) maskBuilder.append(" ");
                    maskBuilder.append(parts[i]);
                }
                this.currentMask = maskBuilder.toString();
                this.previousMask = this.currentMask;
                this.firstGuessReceived = false;

                printSeparator();
                System.out.println("  🎮  JOGO INICIADO!");
                System.out.println("  ⏱   Tempo por ronda: " + roundTimeoutMs / 1000 + "s");
                printSeparator();
                displayEstadoJogo();
            }
        } catch (NumberFormatException e) {
            System.out.println("[Cliente] Erro ao interpretar mensagem START: " + e.getMessage());
        }
    }

    /**
     * ROUND: início de uma nova ronda.
     * Formato: ROUND <roundNumber> <mask> <attemptsLeft> <usedLetters>
     * Onde <mask> pode ser: _ _ _ _ _ (com espaços)
     */
    private void handleRound(String[] parts) {
        try {
            if (parts.length >= 5) {
                int roundNumber = Integer.parseInt(parts[1]);
                int attemptsIdx = parts.length - 2;
                int lettersIdx = parts.length - 1;

                this.previousAttemptsLeft = this.attemptsLeft;
                this.previousMask = this.currentMask;
                this.attemptsLeft = Integer.parseInt(parts[attemptsIdx]);
                this.usedLetters = parts[lettersIdx];

                StringBuilder maskBuilder = new StringBuilder();
                for (int i = 2; i < attemptsIdx; i++) {
                    if (i > 2) maskBuilder.append(" ");
                    maskBuilder.append(parts[i]);
                }
                this.currentMask = maskBuilder.toString();
                this.firstGuessReceived = false;

                printSeparator();
                System.out.println("  🎯  RONDA " + roundNumber);
                printSeparator();
                displayEstadoJogo();
            }
        } catch (NumberFormatException e) {
            System.out.println("[Cliente] Erro ao interpretar mensagem ROUND: " + e.getMessage());
        }
    }

    /**
     * STATE: atualização do estado após processamento de jogadas.
     * Formato: STATE <mask> <attemptsLeft> <usedLetters>
     * Onde <mask> pode ser: _ _ _ _ _ (com espaços)
     */
    private void handleState(String[] parts) {
        try {
            if (parts.length >= 4) {
                int attemptsIdx = parts.length - 2;
                int lettersIdx = parts.length - 1;

                String newMask = "";
                StringBuilder maskBuilder = new StringBuilder();
                for (int i = 1; i < attemptsIdx; i++) {
                    if (i > 1) maskBuilder.append(" ");
                    maskBuilder.append(parts[i]);
                }
                newMask = maskBuilder.toString();

                int newAttempts = Integer.parseInt(parts[attemptsIdx]);

                // Detetar acerto ou erro
                boolean maskChanged = !newMask.equals(this.currentMask);
                boolean attemptsDecreased = newAttempts < this.attemptsLeft;

                this.previousAttemptsLeft = this.attemptsLeft;
                this.previousMask = this.currentMask;
                this.attemptsLeft = newAttempts;
                this.usedLetters = parts[lettersIdx];
                this.currentMask = newMask;

                // Mostrar cara feliz se a máscara mudou (acerto), cara triste se perdeu tentativa
                if (maskChanged && !attemptsDecreased) {
                    printHappyFace();
                } else if (attemptsDecreased) {
                    printSadFace();
                }

                displayEstadoJogo();
            }
        } catch (NumberFormatException e) {
            System.out.println("[Cliente] Erro ao interpretar mensagem STATE: " + e.getMessage());
        }
    }

    /**
     * END: o jogo terminou.
     * Formato: END <WIN|LOSE>
     */
    private void handleEnd(String message) {
        String[] parts = message.split(" ", 4);
        if (parts.length < 3) {
            running = false;
            return;
        }

        String result = parts[1].toUpperCase();
        printSeparator();
        if (result.equals("WIN")) {
            String winners = parts[2];
            String word = parts.length >= 4 ? parts[3] : currentMask;
            printVictoryFace();
            System.out.println("  🏆  VITÓRIA! Palavra: " + word.toUpperCase());
            System.out.println("      Vencedores: " + winners);
        } else if (result.equals("LOSE")) {
            String word = message.substring("END LOSE ".length());
            printDefeatedHangman();
            System.out.println("  💀  DERROTA! A palavra era: " + word.toUpperCase());
        }
        printSeparator();

        running = false;
    }

    /**
     * FULL: o servidor está cheio ou o jogo já começou.
     */
    private void handleFull() {
        System.out.println("  ⛔  Servidor cheio ou jogo já iniciado. Conexão rejeitada.");
        running = false;
    }

    /**
     * INFO: mensagem informativa do servidor.
     * Formato: INFO <message>
     */
    private void handleInfo(String message) {
        if (message.length() > 5) {
            String info = message.substring(5);
            System.out.println("  ℹ  " + info);
        }
    }

    /**
     * Exibe o estado atual do jogo com visual melhorado:
     * forca ASCII, palavra mascarada e letras usadas.
     */
    private void displayEstadoJogo() {
        System.out.println();
        printHangman(attemptsLeft);
        System.out.println();

        // Palavra com espaçamento visual
        System.out.print("  Palavra: ");
        String[] letters = currentMask.split(" ");
        for (String l : letters) {
            System.out.print(" " + l.toUpperCase() + " ");
        }
        System.out.println();

        // Barra de tentativas
        System.out.print("  Vidas:   ");
        for (int i = 0; i < attemptsLeft; i++) System.out.print("❤ ");
        for (int i = attemptsLeft; i < 6; i++) System.out.print("✖ ");
        System.out.println(" (" + attemptsLeft + " restante(s))");

        // Letras usadas
        boolean noLetters = usedLetters.isEmpty() || usedLetters.equals("-");
        System.out.println("  Letras:  " + (noLetters ? "nenhuma" : usedLetters.toUpperCase().replace(",", "  ")));
        System.out.println();
    }

    /** Desenha a forca ASCII correspondente às tentativas restantes. */
    private void printHangman(int remaining) {
        int stage = Math.max(0, Math.min(6, 6 - remaining));
        String[] lines = HANGMAN[stage].split("\n");
        for (String line : lines) {
            System.out.println("  " + line);
        }
    }

    /** Cara feliz — letra/palavra correta! */
    private void printHappyFace() {
        System.out.println();
        System.out.println("  ╔══════════════════╗");
        System.out.println("  ║   ( ＾ω＾)  ✓   ║");
        System.out.println("  ║   ACERTASTE!     ║");
        System.out.println("  ╚══════════════════╝");
        System.out.println();
    }

    /** Cara triste — letra/palavra errada! */
    private void printSadFace() {
        System.out.println();
        System.out.println("  ╔══════════════════╗");
        System.out.println("  ║   (╥_╥)   ✗     ║");
        System.out.println("  ║   ERRASTE!       ║");
        System.out.println("  ╚══════════════════╝");
        System.out.println();
    }

    /** Cara de vitória — jogo ganho! */
    private void printVictoryFace() {
        System.out.println("  ╔═══════════════════════╗");
        System.out.println("  ║  \\(★ω★)/  🎉🎉🎉    ║");
        System.out.println("  ║   PARABÉNS!!!         ║");
        System.out.println("  ╚═══════════════════════╝");
        System.out.println();
    }

    /** Forca completa — jogo perdido! */
    private void printDefeatedHangman() {
        System.out.println("  ╔═════╗");
        System.out.println("  ║     ║");
        System.out.println("  X     ║   ← DERROTA");
        System.out.println(" /|\\    ║");
        System.out.println(" / \\    ║");
        System.out.println("        ║");
        System.out.println("  ════════");
        System.out.println();
    }

    /** Linha separadora visual. */
    private void printSeparator() {
        System.out.println("  ══════════════════════════════");
    }

    /**
     * Loop de input: permite ao jogador submeter adivinhas.
     */
    private void inputLoop() {
        System.out.println();
        System.out.println("  ╔═══════════════════════════════╗");
        System.out.println("  ║   🎮  JOGO DA FORCA  🎮       ║");
        System.out.println("  ║  Escreve uma letra ou palavra  ║");
        System.out.println("  ║  'sair' para desconectar       ║");
        System.out.println("  ╚═══════════════════════════════╝");
        System.out.println();

        while (running) {
            System.out.print("> ");
            System.out.flush();

            try {
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue;
                }

                if (input.equalsIgnoreCase("sair")) {
                    System.out.println("[Cliente] Desconectando...");
                    running = false;
                    break;
                }

                // Enviar a adivinha ao servidor
                sendGuess(input);

            } catch (Exception e) {
                System.out.println("[Cliente] Erro ao ler input: " + e.getMessage());
                running = false;
                break;
            }
        }
    }

    /**
     * Envia uma adivinha (letra ou palavra) ao servidor.
     *
     * @param guess A adivinha (uma letra ou uma palavra).
     */
    private void sendGuess(String guess) {
        if (out != null && !socket.isClosed()) {
            out.println("GUESS " + guess);
            System.out.println("[Enviado] GUESS " + guess);
        }
    }

    /**
     * Fecha a ligação e liberta recursos.
     */
    private void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (scanner != null) {
                scanner.close();
            }
        } catch (IOException e) {
            System.err.println("[Cliente] Erro ao fechar socket: " + e.getMessage());
        }
        System.out.println("[Cliente] Desconectado.");
    }
}
