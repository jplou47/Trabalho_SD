package client;

import java.net.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.swing.*;

/**
 * ClienteForcaGUI — Cliente visual para o Jogo da Forca (Multijogador TCP/IP)
 *
 * Funcionalidades:
 *  - Tema escuro moderno com acentos neon
 *  - Desenho da forca com Graphics2D (7 etapas)
 *  - Caras animadas: feliz (acerto), triste (erro), vitória, derrota
 *  - Cronómetro animado do lado do cliente (contagem decrescente com cores)
 *  - Fullscreen via F11 ou botão
 *  - Protocolo: WELCOME, START, ROUND, STATE, END, FULL
 */
public class ClienteForcaGUI extends JFrame {

    // ─── Tema escuro ──────────────────────────────────────────────
    private static final Color BG_DARK   = new Color(15, 15, 28);
    private static final Color BG_PANEL  = new Color(24, 24, 42);
    private static final Color BG_CARD   = new Color(33, 33, 55);
    private static final Color CYAN      = new Color(0, 210, 215);
    private static final Color GREEN     = new Color(50, 205, 100);
    private static final Color RED       = new Color(220, 60, 60);
    private static final Color GOLD      = new Color(255, 200, 0);
    private static final Color ORANGE    = new Color(255, 150, 30);
    private static final Color TXT_MAIN  = new Color(220, 220, 240);
    private static final Color TXT_DIM   = new Color(130, 130, 160);

    private static final Font F_TITLE  = new Font("Segoe UI", Font.BOLD, 46);
    private static final Font F_WORD   = new Font("Courier New", Font.BOLD, 34);
    private static final Font F_BTN    = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font F_LABEL  = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font F_LOG    = new Font("Consolas", Font.PLAIN, 11);

    // ─── Estados do ecrã ──────────────────────────────────────────
    private static final int ST_MENU = 0, ST_GAME = 1, ST_OVER = 2;
    private int currentState = ST_MENU;

    // ─── Cara atual ───────────────────────────────────────────────
    private static final int FACE_NONE = 0, FACE_HAPPY = 1, FACE_SAD = 2,
                              FACE_WIN = 3, FACE_LOSE = 4;
    private volatile int currentFace = FACE_NONE;

    // ─── CardLayout ───────────────────────────────────────────────
    private JPanel cardPanel;
    private String playerName = "Jogador";

    // ─── Componentes do painel de jogo ────────────────────────────
    private HangmanPanel hangmanPanel;
    private FacePanel    facePanel;
    private JLabel       wordLabel;
    private JLabel       attemptsLabel;
    private JLabel       lettersLabel;
    private JLabel       timerLabel;
    private JProgressBar timerBar;
    private JLabel       statusLabel;
    private JLabel       playerInfoLabel;
    private JButton[]    letterButtons;
    private JTextField   wordInput;
    private JButton      guessWordButton;
    private JTextArea    logArea;

    // ─── Componentes do painel de fim de jogo ─────────────────────
    private JLabel goTitleLabel;
    private JLabel goResultLabel;
    private JLabel goWordLabel;

    // ─── Rede ─────────────────────────────────────────────────────
    private Socket        socket;
    private PrintWriter   out;
    private BufferedReader in;
    private int playerId = -1;

    // ─── Estado do jogo ───────────────────────────────────────────
    private volatile boolean connected   = false;
    private volatile boolean gameRunning = false;
    private volatile boolean myTurn      = false;
    private volatile String  currentMask = "";
    private volatile int     attemptsLeft = 6;
    private volatile String  usedLetters  = "";
    private volatile boolean[] letterUsed = new boolean[26];
    private volatile int     roundDurationSeconds = 15;

    // ─── Cronómetro do lado do cliente ────────────────────────────
    private javax.swing.Timer countdownTimer;
    private volatile int timerSeconds = 0;

    // ─── Fullscreen ───────────────────────────────────────────────
    private boolean isFullscreen = false;

    // ─── Main ─────────────────────────────────────────────────────

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClienteForcaGUI gui = new ClienteForcaGUI();
            gui.setVisible(true);
            GestorAudio.iniciarMenuMusica();
        });
    }

    // ─── Construtor ───────────────────────────────────────────────

    public ClienteForcaGUI() {
        setTitle("Jogo da Forca");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1120, 760);
        setMinimumSize(new Dimension(900, 620));
        setLocationRelativeTo(null);
        setResizable(true);

        // Tecla F11 para fullscreen
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "fs");
        getRootPane().getActionMap().put("fs",
                new AbstractAction() { public void actionPerformed(ActionEvent e) { toggleFullscreen(); } });

        cardPanel = new JPanel(new CardLayout());
        cardPanel.add(createMenuPanel(),    "MENU");
        cardPanel.add(createGamePanel(),    "GAME");
        cardPanel.add(createGameOverPanel(), "GAMEOVER");
        add(cardPanel);
    }

    // ─── Fullscreen ───────────────────────────────────────────────

    private void toggleFullscreen() {
        if (isFullscreen) {
            setExtendedState(JFrame.NORMAL);
        } else {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
        isFullscreen = !isFullscreen;
    }

    // ═════════════════════════════════════════════════════════════
    //  PAINEL DO MENU
    // ═════════════════════════════════════════════════════════════

    private JPanel createMenuPanel() {
        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, new Color(8,8,22), 0, getHeight(), new Color(25,8,50)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Estrelas decorativas
                g2.setColor(new Color(255,255,255,50));
                java.util.Random rng = new java.util.Random(99);
                for (int i = 0; i < 80; i++) {
                    int r = rng.nextInt(2)+1;
                    g2.fillOval(rng.nextInt(getWidth()), rng.nextInt(getHeight()), r, r);
                }
                g2.dispose();
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = label("⚰  JOGO DA FORCA  ⚰", F_TITLE, GOLD);
        title.setAlignmentX(CENTER_ALIGNMENT);

        JLabel sub = label("Multijogador TCP/IP  ·  2-4 Jogadores", new Font("Segoe UI", Font.PLAIN, 16), TXT_DIM);
        sub.setAlignmentX(CENTER_ALIGNMENT);

        JLabel hint = label("[ F11 = Ecrã Completo ]", new Font("Consolas", Font.PLAIN, 12), new Color(80,80,110));
        hint.setAlignmentX(CENTER_ALIGNMENT);

        JButton play = menuBtn("▶   JOGAR", GREEN);
        play.addActionListener(e -> askPlayerName());

        JButton somMenuBtn = menuBtn("🔊   SOM", new Color(70, 70, 120));
        somMenuBtn.addActionListener(e -> {
            boolean silenciado = GestorAudio.alternarSilencio();
            somMenuBtn.setText(silenciado ? "🔇   SOM" : "🔊   SOM");
        });

        JButton exit = menuBtn("✖   SAIR", RED);
        exit.addActionListener(e -> System.exit(0));

        panel.add(Box.createVerticalGlue());
        panel.add(title);
        panel.add(Box.createVerticalStrut(14));
        panel.add(sub);
        panel.add(Box.createVerticalStrut(8));
        panel.add(hint);
        panel.add(Box.createVerticalStrut(70));
        panel.add(play);
        panel.add(Box.createVerticalStrut(16));
        panel.add(somMenuBtn);
        panel.add(Box.createVerticalStrut(16));
        panel.add(exit);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    // ═════════════════════════════════════════════════════════════
    //  PAINEL DO JOGO
    // ═════════════════════════════════════════════════════════════

    private JPanel createGamePanel() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(BG_DARK);
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));

        // ── Barra superior ─────────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG_PANEL);
        topBar.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));

        playerInfoLabel = label("Aguardando...", F_LABEL, TXT_DIM);
        playerInfoLabel.setHorizontalAlignment(SwingConstants.LEFT);

        JLabel gameLbl = label("⚰  JOGO DA FORCA", new Font("Segoe UI", Font.BOLD, 16), GOLD);
        gameLbl.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        topRight.setOpaque(false);
        JButton menuBtn = smallBtn("🏠 Menu");   menuBtn.addActionListener(e -> backToMenu());
        JButton fsBtn   = smallBtn("⛶ F11");    fsBtn.addActionListener(e -> toggleFullscreen());
        JButton somBtn  = smallBtn("🔊 Som");
        somBtn.addActionListener(e -> {
            boolean silenciado = GestorAudio.alternarSilencio();
            somBtn.setText(silenciado ? "🔇 Som" : "🔊 Som");
        });
        topRight.add(menuBtn);
        topRight.add(fsBtn);
        topRight.add(somBtn);

        topBar.add(playerInfoLabel, BorderLayout.WEST);
        topBar.add(gameLbl,         BorderLayout.CENTER);
        topBar.add(topRight,        BorderLayout.EAST);

        // ── Painel esquerdo: forca + cara ──────────────────────
        JPanel leftPanel = new JPanel(new BorderLayout(0, 6));
        leftPanel.setBackground(BG_DARK);
        leftPanel.setPreferredSize(new Dimension(290, 0));

        hangmanPanel = new HangmanPanel();
        facePanel    = new FacePanel();
        facePanel.setPreferredSize(new Dimension(290, 115));

        leftPanel.add(hangmanPanel, BorderLayout.CENTER);
        leftPanel.add(facePanel,    BorderLayout.SOUTH);

        // ── Painel central: informações de jogo ────────────────
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(BG_DARK);
        infoPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));

        // Palavra
        JPanel wordCard = card();
        wordLabel = label("_ _ _ _ _", F_WORD, CYAN);
        wordCard.add(wordLabel);

        // Vidas
        attemptsLabel = label("❤ ❤ ❤ ❤ ❤ ❤   (6 vidas)", new Font("Segoe UI", Font.BOLD, 16), RED);
        attemptsLabel.setAlignmentX(CENTER_ALIGNMENT);

        // Cronómetro
        JPanel timerCard = new JPanel(new BorderLayout(10, 0));
        timerCard.setBackground(BG_CARD);
        timerCard.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        timerCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        timerLabel = label("⏱  --s", F_LABEL, GREEN);
        timerLabel.setPreferredSize(new Dimension(60, 20));
        timerBar = new JProgressBar(0, 100);
        timerBar.setValue(100);
        timerBar.setForeground(GREEN);
        timerBar.setBackground(new Color(40, 40, 65));
        timerBar.setBorderPainted(false);
        timerCard.add(timerLabel, BorderLayout.WEST);
        timerCard.add(timerBar,   BorderLayout.CENTER);

        // Letras usadas
        lettersLabel = label("Letras: —", new Font("Segoe UI", Font.PLAIN, 12), TXT_DIM);
        lettersLabel.setAlignmentX(CENTER_ALIGNMENT);

        // Status
        statusLabel = label("Aguardando jogadores...", F_LABEL, GOLD);
        statusLabel.setAlignmentX(CENTER_ALIGNMENT);

        infoPanel.add(wordCard);
        infoPanel.add(Box.createVerticalStrut(8));
        infoPanel.add(attemptsLabel);
        infoPanel.add(Box.createVerticalStrut(8));
        infoPanel.add(timerCard);
        infoPanel.add(Box.createVerticalStrut(8));
        infoPanel.add(lettersLabel);
        infoPanel.add(Box.createVerticalStrut(10));
        infoPanel.add(statusLabel);
        infoPanel.add(Box.createVerticalGlue());

        // ── Área principal ─────────────────────────────────────
        JPanel mainArea = new JPanel(new BorderLayout(0, 0));
        mainArea.setBackground(BG_DARK);
        mainArea.add(leftPanel,  BorderLayout.WEST);
        mainArea.add(infoPanel,  BorderLayout.CENTER);

        // ── Teclado de letras ──────────────────────────────────
        JPanel keyboard = new JPanel(new GridLayout(3, 9, 4, 4));
        keyboard.setBackground(BG_DARK);
        keyboard.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
        letterButtons = new JButton[26];
        for (int i = 0; i < 26; i++) {
            char ch = (char)('A' + i);
            final int idx = i;
            JButton btn = new JButton(String.valueOf(ch)) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color bg = isEnabled()
                        ? (getModel().isRollover() ? CYAN.darker() : new Color(45,55,85))
                        : new Color(35,30,45);
                    g2.setColor(bg);
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setFont(F_BTN);
            btn.setForeground(TXT_MAIN);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> { GestorAudio.tocarClique(); guessLetter(ch, idx); });
            letterButtons[i] = btn;
            keyboard.add(btn);
        }

        // ── Input de palavra ───────────────────────────────────
        JPanel wordInputRow = new JPanel(new BorderLayout(6, 0));
        wordInputRow.setBackground(BG_DARK);
        wordInput = new JTextField();
        wordInput.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        wordInput.setBackground(BG_CARD);
        wordInput.setForeground(TXT_MAIN);
        wordInput.setCaretColor(TXT_MAIN);
        wordInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70,70,110), 1),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        wordInput.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_ENTER) guessWord(); }
        });
        guessWordButton = new JButton("✓ Adivinhar Palavra");
        guessWordButton.setFont(F_BTN);
        guessWordButton.setForeground(Color.WHITE);
        guessWordButton.setBackground(GREEN.darker());
        guessWordButton.setBorder(BorderFactory.createEmptyBorder(8,16,8,16));
        guessWordButton.setFocusPainted(false);
        guessWordButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        guessWordButton.addActionListener(e -> { GestorAudio.tocarClique(); guessWord(); });
        wordInputRow.add(wordInput,       BorderLayout.CENTER);
        wordInputRow.add(guessWordButton, BorderLayout.EAST);

        // ── Log ───────────────────────────────────────────────
        logArea = new JTextArea(3, 40);
        logArea.setFont(F_LOG);
        logArea.setForeground(new Color(140, 200, 140));
        logArea.setBackground(new Color(8, 12, 18));
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createLineBorder(new Color(35,35,60), 1));
        logScroll.setPreferredSize(new Dimension(0, 80));

        // ── Rodapé ────────────────────────────────────────────
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setBackground(BG_DARK);
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        bottom.add(keyboard,    BorderLayout.NORTH);
        bottom.add(wordInputRow, BorderLayout.CENTER);
        bottom.add(logScroll,   BorderLayout.SOUTH);

        root.add(topBar,   BorderLayout.NORTH);
        root.add(mainArea, BorderLayout.CENTER);
        root.add(bottom,   BorderLayout.SOUTH);
        return root;
    }

    // ═════════════════════════════════════════════════════════════
    //  PAINEL DE FIM DE JOGO
    // ═════════════════════════════════════════════════════════════

    private JPanel createGameOverPanel() {
        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0,0,new Color(8,8,22),0,getHeight(),new Color(25,8,50)));
                g2.fillRect(0,0,getWidth(),getHeight());
                g2.dispose();
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        goTitleLabel  = label("JOGO TERMINADO", F_TITLE, GOLD);
        goTitleLabel.setAlignmentX(CENTER_ALIGNMENT);
        goResultLabel = label("", new Font("Segoe UI", Font.PLAIN, 20), TXT_MAIN);
        goResultLabel.setAlignmentX(CENTER_ALIGNMENT);
        goWordLabel   = label("", new Font("Courier New", Font.BOLD, 28), CYAN);
        goWordLabel.setAlignmentX(CENTER_ALIGNMENT);

        JButton again = menuBtn("▶   JOGAR DE NOVO", GREEN);
        again.addActionListener(e -> startGame());
        JButton menu  = menuBtn("🏠   MENU",          new Color(70,80,160));
        menu.addActionListener(e -> backToMenu());
        JButton quit  = menuBtn("✖   SAIR",           RED);
        quit.addActionListener(e -> System.exit(0));

        panel.add(Box.createVerticalGlue());
        panel.add(goTitleLabel);
        panel.add(Box.createVerticalStrut(14));
        panel.add(goResultLabel);
        panel.add(Box.createVerticalStrut(16));
        panel.add(goWordLabel);
        panel.add(Box.createVerticalStrut(55));
        panel.add(again);
        panel.add(Box.createVerticalStrut(18));
        panel.add(menu);
        panel.add(Box.createVerticalStrut(18));
        panel.add(quit);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    // ═════════════════════════════════════════════════════════════
    //  NAVEGAÇÃO
    // ═════════════════════════════════════════════════════════════

    private void askPlayerName() {
        JTextField nameField = new JTextField("Jogador", 15);
        nameField.selectAll();
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.add(new JLabel("O teu nome de jogador:"), BorderLayout.NORTH);
        p.add(nameField, BorderLayout.CENTER);
        int r = JOptionPane.showConfirmDialog(this, p, "Entrar no Jogo",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r == JOptionPane.OK_OPTION) {
            String n = nameField.getText().trim();
            if (!n.isEmpty()) playerName = n;
            startGame();
        }
    }

    private void startGame() {
        showGamePanel();
        resetEstadoJogo();
        connectToServer();
    }

    private void backToMenu() {
        stopCountdown();
        GestorAudio.pararMusicaJogo();
        disconnectFromServer();
        showMenuPanel();
    }

    private void showMenuPanel() {
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "MENU");
        setTitle("Jogo da Forca — Menu");
        currentState = ST_MENU;
        GestorAudio.iniciarMenuMusica();
    }

    private void showGamePanel() {
        GestorAudio.pararMenuMusica();
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "GAME");
        setTitle("Jogo da Forca — A Jogar");
        currentState = ST_GAME;
    }

    private void showGameOverPanel(String result, String word) {
        stopCountdown();
        if ("WIN".equals(result)) {
            goTitleLabel.setText("🎉  VITÓRIA!");
            goTitleLabel.setForeground(GREEN);
            goResultLabel.setText("Parabéns, " + playerName + "! Adivinhaste a palavra.");
            currentFace = FACE_WIN;
        } else {
            goTitleLabel.setText("💀  DERROTA!");
            goTitleLabel.setForeground(RED);
            goResultLabel.setText("Não foi desta vez...");
            currentFace = FACE_LOSE;
        }
        goWordLabel.setText("Palavra: " + word.toUpperCase());
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "GAMEOVER");
        setTitle("Jogo da Forca — Fim do Jogo");
        currentState = ST_OVER;
    }

    private void resetEstadoJogo() {
        currentMask  = "";
        attemptsLeft = 6;
        usedLetters  = "";
        gameRunning  = false;
        myTurn       = false;
        currentFace  = FACE_NONE;
        roundDurationSeconds = 15;
        for (int i = 0; i < 26; i++) letterUsed[i] = false;

        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < 26; i++) {
                if (letterButtons != null && letterButtons[i] != null) {
                    letterButtons[i].setEnabled(false);
                    letterButtons[i].repaint();
                }
            }
            if (guessWordButton != null) guessWordButton.setEnabled(false);
            if (wordInput   != null) wordInput.setText("");
            if (logArea     != null) logArea.setText("");
            if (wordLabel   != null) wordLabel.setText("_ _ _ _ _");
            updateAttemptsUI();
            if (timerLabel  != null) timerLabel.setText("⏱  --s");
            if (timerBar    != null) { timerBar.setValue(100); timerBar.setForeground(GREEN); }
            if (statusLabel != null) statusLabel.setText("Aguardando jogadores...");
            if (playerInfoLabel != null) playerInfoLabel.setText("A conectar...");
            if (lettersLabel != null) lettersLabel.setText("Letras: —");
            if (hangmanPanel != null) hangmanPanel.repaint();
            if (facePanel    != null) facePanel.repaint();
        });
    }

    // ═════════════════════════════════════════════════════════════
    //  REDE
    // ═════════════════════════════════════════════════════════════

    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket("localhost", 12345);
                out = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream())), true);
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                connected = true;
                log("✓ Conectado ao servidor");
                Thread t = new Thread(this::readMessages);
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                log("✗ Falha na conexão: " + e.getMessage());
                SwingUtilities.invokeLater(this::backToMenu);
            }
        }).start();
    }

    private void disconnectFromServer() {
        connected = false;
        try { if (socket != null && !socket.isClosed()) socket.close(); }
        catch (IOException ignored) {}
    }

    private void readMessages() {
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                processServerMessage(line.trim());
            }
        } catch (IOException e) {
            if (connected) log("✗ Conexão perdida");
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  PROTOCOLO
    // ═════════════════════════════════════════════════════════════

    private void processServerMessage(String msg) {
        if (msg.isEmpty()) return;
        String[] parts = msg.split("\\s+", 2);
        switch (parts[0].toUpperCase()) {
            case "WELCOME": handleWelcome(parts);  break;
            case "START":   handleStart(msg);       gameRunning = true; GestorAudio.iniciarMusicaJogo(); break;
            case "ROUND":   handleRound(msg);       break;
            case "STATE":   handleState(msg);       break;
            case "END":     handleEnd(msg);         break;
            case "FULL":
                log("✗ Servidor cheio ou jogo já em curso");
                SwingUtilities.invokeLater(this::backToMenu);
                break;
            case "INFO":
                log("ℹ " + (parts.length > 1 ? parts[1] : ""));
                break;
            case "TIMER":
                handleTimer(msg);
                break;
        }
    }

    private void handleWelcome(String[] parts) {
        try {
            String[] tok = parts[1].trim().split("\\s+");
            playerId = Integer.parseInt(tok[0]);
            String total = tok.length >= 2 ? tok[1] : "?";
            if (out != null) out.println("NAME " + playerName);
            SwingUtilities.invokeLater(() -> {
                playerInfoLabel.setText("🎮 " + playerName + "  (ID: " + playerId + ")  |  Online: " + total);
                statusLabel.setText("Aguardando início do jogo...");
                log("✓ Bem-vindo, " + playerName + "! ID: " + playerId);
            });
        } catch (Exception e) { log("✗ Erro WELCOME"); }
    }

    private void handleStart(String line) {
        try {
            String[] parts = line.trim().split("\\s+");
            int attIdx = -1, toIdx = -1;
            for (int i = parts.length - 1; i >= 1; i--) {
                if (isInt(parts[i])) {
                    if (toIdx == -1) toIdx = i;
                    else { attIdx = i; break; }
                }
            }
            if (attIdx == -1 || toIdx == -1) throw new Exception("interpretar START");
            int att = Integer.parseInt(parts[attIdx]);
            int timeout = Integer.parseInt(parts[toIdx]);
            StringBuilder mask = new StringBuilder();
            for (int i = 1; i < attIdx; i++) { if (i > 1) mask.append(" "); mask.append(parts[i]); }
            currentMask = mask.toString();
            attemptsLeft = att;
            roundDurationSeconds = Math.max(1, timeout / 1000);
            SwingUtilities.invokeLater(() -> {
                wordLabel.setText(fmtMask(currentMask));
                updateAttemptsUI();
                statusLabel.setText("Jogo iniciado! Aguardando primeira ronda...");
                log("🎮 Jogo iniciado!");
                hangmanPanel.repaint();
            });
        } catch (Exception e) { log("✗ Erro START: " + e.getMessage()); }
    }

    private void handleRound(String line) {
        try {
            String[] parts = line.trim().split("\\s+");
            int round = Integer.parseInt(parts[1]);
            int newAtt = Integer.parseInt(parts[parts.length - 2]);
            String newLetters = parts[parts.length - 1];
            StringBuilder mask = new StringBuilder();
            for (int i = 2; i < parts.length - 2; i++) { if (i > 2) mask.append(" "); mask.append(parts[i]); }
            currentMask  = mask.toString();
            attemptsLeft = newAtt;
            usedLetters  = newLetters;
            currentFace  = FACE_NONE;
            myTurn = true;
            startCountdown(roundDurationSeconds);
            SwingUtilities.invokeLater(() -> {
                wordLabel.setText(fmtMask(currentMask));
                updateAttemptsUI();
                updateLettersUI();
                statusLabel.setText("Ronda " + round + " — A sua vez! Adivinhe uma letra ou a palavra.");
                setInputEnabled(true);
                hangmanPanel.repaint();
                facePanel.repaint();
            });
        } catch (Exception e) { log("✗ Erro ROUND"); }
    }

    private void handleState(String line) {
        try {
            String[] parts = line.trim().split("\\s+");
            int newAtt = Integer.parseInt(parts[parts.length - 2]);
            String newLetters = parts[parts.length - 1];
            StringBuilder mask = new StringBuilder();
            for (int i = 1; i < parts.length - 2; i++) { if (i > 1) mask.append(" "); mask.append(parts[i]); }
            String newMask = mask.toString();

            boolean maskChanged   = !newMask.equals(currentMask);
            boolean attDecreased  = newAtt < attemptsLeft;

            // Determinar cara com base nos resultados desta ronda
            if (maskChanged && !attDecreased) {
                currentFace = FACE_HAPPY;   // letra acertada
                GestorAudio.tocarAcerto();
            } else if (attDecreased) {
                currentFace = FACE_SAD;     // letra/palavra errada ou timeout
                GestorAudio.tocarErro();
            } else {
                currentFace = FACE_NONE;    // sem mudança
            }

            currentMask  = newMask;
            attemptsLeft = newAtt;
            usedLetters  = newLetters;
            myTurn = false;
            stopCountdown();
            SwingUtilities.invokeLater(() -> {
                wordLabel.setText(fmtMask(currentMask));
                updateAttemptsUI();
                updateLettersUI();
                statusLabel.setText("Aguardando próxima ronda...");
                setInputEnabled(false);
                hangmanPanel.repaint();
                facePanel.repaint();
            });
        } catch (Exception e) { log("✗ Erro STATE"); }
    }

    private void handleEnd(String line) {
        try {
            String[] parts = line.split(" ", 4);
            String result = parts[1].toUpperCase();
            String word;
            if ("WIN".equals(result)) {
                word = parts.length > 3 ? parts[3] : currentMask;
                log("🎉 VITÓRIA! Palavra: " + word);
                GestorAudio.tocarVitoria();
            } else {
                word = line.length() > "END LOSE ".length() ? line.substring("END LOSE ".length()) : currentMask;
                log("💀 DERROTA! Palavra: " + word);
                GestorAudio.tocarDerrota();
            }
            gameRunning = false;
            myTurn = false;
            final String wf = word, rf = result;
            SwingUtilities.invokeLater(() -> showGameOverPanel(rf, wf));
        } catch (Exception e) { log("✗ Erro END"); }
    }

    private void handleTimer(String line) {
        try {
            String[] parts = line.trim().split("\\s+");
            int secsLeft = Integer.parseInt(parts[1]);
            timerSeconds = secsLeft;
            SwingUtilities.invokeLater(this::updateTimerUI);
        } catch (Exception e) { /* ignorar */ }
    }

    // ═════════════════════════════════════════════════════════════
    //  CRONÓMETRO DO CLIENTE
    // ═════════════════════════════════════════════════════════════

    private void startCountdown(int seconds) {
        stopCountdown();
        timerSeconds = seconds;
        updateTimerUI();
        countdownTimer = new javax.swing.Timer(1000, e -> {
            timerSeconds = Math.max(0, timerSeconds - 1);
            updateTimerUI();
            if (timerSeconds <= 0) ((javax.swing.Timer) e.getSource()).stop();
        });
        countdownTimer.start();
    }

    private void stopCountdown() {
        if (countdownTimer != null) { countdownTimer.stop(); countdownTimer = null; }
    }

    private void updateTimerUI() {
        int pct = roundDurationSeconds > 0 ? (timerSeconds * 100) / roundDurationSeconds : 0;
        Color c = timerSeconds <= 3 ? RED : timerSeconds <= 7 ? ORANGE : GREEN;
        timerLabel.setText("⏱  " + timerSeconds + "s");
        timerLabel.setForeground(c);
        timerBar.setValue(pct);
        timerBar.setForeground(c);
    }

    // ═════════════════════════════════════════════════════════════
    //  AÇÕES DO UTILIZADOR
    // ═════════════════════════════════════════════════════════════

    private void guessLetter(char letter, int idx) {
        if (!gameRunning || !myTurn || letterUsed[idx] || !connected) return;
        myTurn = false;
        letterUsed[idx] = true;
        setInputEnabled(false);
        out.println("GUESS " + letter);
        log("📤 Jogada: " + letter);
    }

    private void guessWord() {
        String word = wordInput.getText().trim();
        if (word.isEmpty() || !gameRunning || !myTurn || !connected) return;
        myTurn = false;
        setInputEnabled(false);
        wordInput.setText("");
        out.println("GUESS " + word.toUpperCase());
        log("📤 Jogada: " + word.toUpperCase());
    }

    private void setInputEnabled(boolean on) {
        if (letterButtons != null)
            for (int i = 0; i < 26; i++)
                if (letterButtons[i] != null && !letterUsed[i]) {
                    letterButtons[i].setEnabled(on);
                    letterButtons[i].repaint();
                }
        if (guessWordButton != null) guessWordButton.setEnabled(on);
    }

    private void updateAttemptsUI() {
        if (attemptsLabel == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < attemptsLeft; i++) sb.append("❤ ");
        for (int i = attemptsLeft; i < 6; i++) sb.append("✖ ");
        sb.append("  (").append(attemptsLeft).append(attemptsLeft == 1 ? " vida)" : " vidas)");
        attemptsLabel.setText(sb.toString());
        attemptsLabel.setForeground(attemptsLeft <= 2 ? RED : new Color(220, 80, 80));
    }

    private void updateLettersUI() {
        if (lettersLabel == null) return;
        boolean empty = usedLetters.isEmpty() || "-".equals(usedLetters);
        lettersLabel.setText("Letras: " + (empty ? "—" : usedLetters.toUpperCase().replace(",", "  ")));
        if (!empty) {
            for (String l : usedLetters.split(",")) {
                if (l.isEmpty()) continue;
                try {
                    int idx = l.toUpperCase().charAt(0) - 'A';
                    if (idx >= 0 && idx < 26 && !letterUsed[idx]) {
                        letterUsed[idx] = true;
                        letterButtons[idx].setEnabled(false);
                        letterButtons[idx].repaint();
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  PAINÉIS CUSTOM (FORCA + CARA)
    // ═════════════════════════════════════════════════════════════

    /** Desenha a forca e o corpo do enforcado com Graphics2D. */
    private class HangmanPanel extends JPanel {
        HangmanPanel() { setBackground(BG_PANEL); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight(), pad = 18;

            // Estrutura da forca
            g2.setColor(new Color(130, 110, 75));
            g2.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(pad, h - pad, w - pad, h - pad);          // base
            g2.drawLine(w / 4, h - pad, w / 4, pad + 10);         // poste vertical
            g2.drawLine(w / 4, pad + 10, 3 * w / 4, pad + 10);    // viga horizontal
            g2.setColor(new Color(190, 170, 110));
            g2.setStroke(new BasicStroke(2));
            g2.drawLine(3 * w / 4, pad + 10, 3 * w / 4, h / 4);   // corda

            int errors  = 6 - attemptsLeft;
            int cx      = 3 * w / 4;
            int ropeEnd = h / 4;
            int headR   = w / 10;
            int neckY   = ropeEnd + headR * 2;
            int bodyLen = headR * 3;
            int limbLen = headR * 2;

            g2.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // Cabeça
            if (errors >= 1) {
                g2.setColor(new Color(240, 205, 155));
                g2.fillOval(cx - headR, ropeEnd, headR * 2, headR * 2);
                g2.setColor(new Color(90, 65, 40));
                g2.setStroke(new BasicStroke(2));
                g2.drawOval(cx - headR, ropeEnd, headR * 2, headR * 2);
                g2.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int eyeY = ropeEnd + headR / 2;
                if (errors >= 6) {
                    // Olhos X (morto)
                    g2.setColor(RED);
                    drawX(g2, cx - headR / 2, eyeY, headR / 5);
                    drawX(g2, cx + headR / 2, eyeY, headR / 5);
                    // Boca triste
                    g2.setColor(new Color(90, 65, 40));
                    g2.drawArc(cx - headR / 3, ropeEnd + headR + headR / 5,
                               headR * 2 / 3, headR / 4, 0, 180);
                } else {
                    // Olhos normais
                    g2.setColor(new Color(55, 35, 15));
                    g2.fillOval(cx - headR / 2 - 3, eyeY - 2, 6, 6);
                    g2.fillOval(cx + headR / 2 - 3, eyeY - 2, 6, 6);
                }
            }

            g2.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(205, 185, 160));

            if (errors >= 2) g2.drawLine(cx, neckY, cx, neckY + bodyLen);           // corpo
            if (errors >= 3) g2.drawLine(cx, neckY + headR, cx - limbLen, neckY + headR * 2); // braço esq
            if (errors >= 4) g2.drawLine(cx, neckY + headR, cx + limbLen, neckY + headR * 2); // braço dir
            if (errors >= 5) g2.drawLine(cx, neckY + bodyLen, cx - limbLen, neckY + bodyLen + limbLen); // perna esq
            if (errors >= 6) g2.drawLine(cx, neckY + bodyLen, cx + limbLen, neckY + bodyLen + limbLen); // perna dir

            g2.dispose();
        }

        private void drawX(Graphics2D g2, int x, int y, int r) {
            g2.drawLine(x - r, y - r, x + r, y + r);
            g2.drawLine(x + r, y - r, x - r, y + r);
        }
    }

    /** Desenha uma cara feliz, triste, vitória ou derrota. */
    private class FacePanel extends JPanel {
        FacePanel() { setBackground(BG_DARK); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (currentFace == FACE_NONE) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int r  = Math.min(w, h) / 2 - 8;
            int cx = w / 2, cy = h / 2 - 10;

            Color face, outline;
            switch (currentFace) {
                case FACE_HAPPY: face = new Color(70, 210, 100);  outline = new Color(20,120,40); break;
                case FACE_SAD:   face = new Color(215, 80, 80);   outline = new Color(120,20,20); break;
                case FACE_WIN:   face = new Color(255, 215, 0);   outline = new Color(180,140,0); break;
                default:         face = new Color(110,110,130);   outline = new Color(60,60,80);  break; // LOSE
            }

            // Rosto
            g2.setColor(face);
            g2.fillOval(cx - r, cy - r, r * 2, r * 2);
            g2.setColor(outline);
            g2.setStroke(new BasicStroke(3));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);

            // Olhos
            int eo = r / 3, ey = cy - r / 5, er = r / 7;
            g2.setColor(outline);
            if (currentFace == FACE_LOSE) {
                g2.setStroke(new BasicStroke(2));
                drawXEye(g2, cx - eo, ey, er);
                drawXEye(g2, cx + eo, ey, er);
            } else {
                g2.fillOval(cx - eo - er, ey - er, er * 2, er * 2);
                g2.fillOval(cx + eo - er, ey - er, er * 2, er * 2);
                // Brilho
                g2.setColor(new Color(255, 255, 255, 160));
                g2.fillOval(cx - eo - er + 2, ey - er, er, er);
                g2.fillOval(cx + eo - er + 2, ey - er, er, er);
            }

            // Boca
            g2.setColor(outline);
            g2.setStroke(new BasicStroke(2.5f));
            int mw = r * 2 / 3, mh = r / 3;
            int mx = cx - mw / 2, my = cy + r / 5;
            if (currentFace == FACE_HAPPY || currentFace == FACE_WIN) {
                g2.drawArc(mx, my, mw, mh, 0, -180);  // sorriso
            } else {
                g2.drawArc(mx, my + mh / 2, mw, mh, 0, 180);  // tristeza
            }

            // Etiqueta
            String lbl;
            Color  lc;
            switch (currentFace) {
                case FACE_HAPPY: lbl = "ACERTASTE! ✓"; lc = GREEN; break;
                case FACE_SAD:   lbl = "ERRASTE! ✗";   lc = RED;   break;
                case FACE_WIN:   lbl = "VITÓRIA! 🎉";  lc = GOLD;  break;
                default:         lbl = "DERROTA...";   lc = TXT_DIM; break;
            }
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(lc);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(lbl, cx - fm.stringWidth(lbl) / 2, cy + r + 16);

            g2.dispose();
        }

        private void drawXEye(Graphics2D g2, int x, int y, int r) {
            g2.drawLine(x - r, y - r, x + r, y + r);
            g2.drawLine(x + r, y - r, x - r, y + r);
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  AUXILIARES DE UI
    // ═════════════════════════════════════════════════════════════

    private JPanel card() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER));
        p.setBackground(BG_PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        return p;
    }

    private JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
    }

    private JButton menuBtn(String text, Color accent) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed() ? accent.darker().darker()
                           : getModel().isRollover() ? accent : accent.darker());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(accent.brighter());
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 21));
        btn.setForeground(Color.WHITE);
        btn.setAlignmentX(CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(320, 62));
        btn.setPreferredSize(new Dimension(320, 62));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton smallBtn(String text) {
        JButton btn = new JButton(text);
        btn.setFont(F_LABEL);
        btn.setForeground(TXT_DIM);
        btn.setBackground(BG_CARD);
        btn.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private String fmtMask(String mask) {
        return mask.toUpperCase();
    }

    private boolean isInt(String s) {
        try { Integer.parseInt(s); return true; } catch (NumberFormatException e) { return false; }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            if (logArea == null) return;
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
