package server;

import java.util.*;

/**
 * EstadoJogo — Estado partilhado de uma partida do Jogo da Forca.
 *
 * Esta classe é o "coração" do jogo. Guarda:
 *  - A palavra secreta e a máscara atual (letras reveladas).
 *  - O número de tentativas restantes.
 *  - As letras já utilizadas.
 *  - As jogadas submetidas na ronda atual.
 *
 * CONCORRÊNCIA: Todos os métodos que acedem ou modificam estado partilhado
 * são sincronizados (synchronized). Isto garante que duas threads (dois
 * clientes) não corrompem o estado ao escrever ao mesmo tempo.
 *
 * Diagrama de estados do jogo:
 *
 *   A_AGUARDAR_JOGADORES → EM_PROGRESSO → TERMINADO
 */
public class EstadoJogo {

    // ─────────────────────────────────────────────────────────────
    //  Constantes de configuração do jogo
    // ─────────────────────────────────────────────────────────────

    /** Número máximo de jogadores por partida. */
    public static final int MAX_PLAYERS = 4;

    /** Número de tentativas iniciais. */
    public static final int MAX_ATTEMPTS = 6;

    /** Tempo (em ms) que cada jogador tem para submeter a sua jogada por ronda. */
    public static final int ROUND_TIMEOUT_MS = 15_000;

    /** Tempo (em ms) de espera no lobby após entrada do primeiro jogador. */
    public static final int LOBBY_TIMEOUT_MS = 20_000;

    // ─────────────────────────────────────────────────────────────
    //  ASCII Art do Boneco — Estágios da forca
    // ─────────────────────────────────────────────────────────────

    /** Estágios do boneco conforme as tentativas diminuem (MAX_TENTATIVAS = 6). */
    private static final String[] HANGMAN_STAGES = {
        // Estágio 0: Sem boneco (6 tentativas restantes)
        "  ┌─────┐\n  │     │\n           \n           \n           \n           \n ──────────",
        
        // Estágio 1: Cabeça (5 tentativas)
        "  ┌─────┐\n  │     │\n  O        \n           \n           \n           \n ──────────",
        
        // Estágio 2: Corpo (4 tentativas)
        "  ┌─────┐\n  │     │\n  O        \n  │        \n           \n           \n ──────────",
        
        // Estágio 3: Braço esquerdo (3 tentativas)
        "  ┌─────┐\n  │     │\n  O        \n /│        \n           \n           \n ──────────",
        
        // Estágio 4: Braço direito (2 tentativas)
        "  ┌─────┐\n  │     │\n  O        \n /│\\       \n           \n           \n ──────────",
        
        // Estágio 5: Perna esquerda (1 tentativa)
        "  ┌─────┐\n  │     │\n  O        \n /│\\       \n /         \n           \n ──────────",
        
        // Estágio 6: Perna direita (0 tentativas) — DERROTA!
        "  ┌─────┐\n  │     │\n  O        \n /│\\       \n / \\       \n           \n ──────────"
    };

    // ─────────────────────────────────────────────────────────────
    //  Enumeração dos estados possíveis do jogo
    // ─────────────────────────────────────────────────────────────

    public enum Estado {
        A_AGUARDAR_JOGADORES,  // No lobby, à espera de jogadores
        EM_PROGRESSO,           // Jogo a decorrer
        TERMINADO               // Jogo terminado (vitória ou derrota)
    }

    // ─────────────────────────────────────────────────────────────
    //  Estado interno do jogo
    // ─────────────────────────────────────────────────────────────

    /** Entrada de palavra com categoria (Fase 2). */
    private final EntradaPalavra wordEntry;

    /** Palavra secreta (em minúsculas para comparação insensível a maiúsculas/minúsculas). */
    private final String secretWord;

    /** Máscara da palavra (ex: "_ a _ _ o" para "gato"). */
    private char[] mask;

    /** Nível de dificuldade desta partida. */
    private final Dificuldade difficulty;

    /** Tentativas restantes. */
    private int attemptsLeft;

    /** Dicas restantes (máximo 2). */
    private int hintsRemaining;

    /** Conjunto de letras já utilizadas (para mostrar ao jogador). */
    private final Set<Character> usedLetters;

    /** Número total de jogadores nesta partida. */
    private int totalPlayers;

    /** Estado atual do jogo. */
    private Estado status;

    /** Número da ronda atual (começa em 1). */
    private int currentRound;

    /**
     * Mapa de jogadas da ronda atual: playerId → jogada submetida.
     * Uma jogada é "" se o jogador não respondeu a tempo (timeout).
     */
    private final Map<Integer, String> roundGuesses;

    /**
     * IDs dos jogadores vencedores. Só é preenchido quando status = TERMINADO.
     * Pode estar vazio se nenhum jogador ganhou (derrota por tentativas = 0).
     */
    private final List<Integer> winnerIds;

    /**
     * Mapa de pontuação individual: playerId → pontuação acumulada.
     * A pontuação é atualizada após cada ronda em função do desempenho.
     */
    private final Map<Integer, Integer> playerScores;

    // ─────────────────────────────────────────────────────────────
    //  Construtores
    // ─────────────────────────────────────────────────────────────

    /**
     * Cria um novo estado de jogo com dificuldade e palavra (Fase 2+).
     *
     * @param wordEntry A entrada da palavra (com categoria).
     * @param difficulty O nível de dificuldade desta partida.
     */
    public EstadoJogo(EntradaPalavra wordEntry, Dificuldade difficulty) {
        this.wordEntry      = wordEntry;
        this.secretWord     = wordEntry.getWord().toLowerCase();
        this.difficulty     = difficulty;
        this.attemptsLeft   = MAX_ATTEMPTS;
        this.hintsRemaining = 2;
        this.usedLetters    = new LinkedHashSet<>(); // Mantém a ordem de inserção
        this.totalPlayers   = 0;
        this.status         = Estado.A_AGUARDAR_JOGADORES;
        this.currentRound   = 0;
        this.roundGuesses   = new HashMap<>();
        this.winnerIds      = new ArrayList<>();
        this.playerScores   = new HashMap<>();

        // Inicializar a máscara: cada caractere é '_', exceto espaços
        this.mask = new char[secretWord.length()];
        for (int i = 0; i < secretWord.length(); i++) {
            mask[i] = (secretWord.charAt(i) == ' ') ? ' ' : '_';
        }
    }

    /**
     * Cria um novo estado de jogo para a palavra fornecida (compatibilidade com Fase 1).
     * Usa a dificuldade padrão NORMAL.
     *
     * @param word A palavra secreta (será convertida para minúsculas internamente).
     */
    public EstadoJogo(String word) {
        this(new EntradaPalavra(word, CategoriaPalavra.ANIMAL), Dificuldade.NORMAL);
    }

    // ─────────────────────────────────────────────────────────────
    //  Métodos de acesso (getters) — synchronized para leitura segura
    // ─────────────────────────────────────────────────────────────

    public synchronized Estado getStatus()        { return status; }
    public synchronized int    getAttemptsLeft()   { return attemptsLeft; }
    public synchronized int    getTotalPlayers()   { return totalPlayers; }
    public synchronized int    getCurrentRound()   { return currentRound; }
    public synchronized int    getHintsRemaining() { return hintsRemaining; }
    public synchronized List<Integer> getWinnerIds() { return Collections.unmodifiableList(winnerIds); }
    public synchronized String getSecretWord()     { return secretWord; }
    public synchronized Dificuldade getDifficulty() { return difficulty; }
    public synchronized EntradaPalavra getEntradaPalavra()   { return wordEntry; }
    public synchronized CategoriaPalavra getCategory() { return wordEntry.getCategory(); }

    /**
     * Obtém a pontuação de um jogador específico.
     * 
     * @param playerId ID do jogador
     * @return Pontuação do jogador (padrão: 0 se não inicializado)
     */
    public synchronized int getPlayerScore(int playerId) {
        return playerScores.getOrDefault(playerId, 0);
    }

    /**
     * Obtém um mapa imutável de todas as pontuações.
     * 
     * @return Mapa playerId → pontuação
     */
    public synchronized Map<Integer, Integer> getAllScores() {
        return Collections.unmodifiableMap(new HashMap<>(playerScores));
    }

    /**
     * Inicializa a pontuação de um jogador em 0.
     * 
     * @param playerId ID do jogador
     */
    public synchronized void initializePlayerScore(int playerId) {
        playerScores.putIfAbsent(playerId, 0);
    }

    /**
     * Adiciona pontos à pontuação de um jogador.
     * 
     * @param playerId ID do jogador
     * @param points Pontos a adicionar
     */
    public synchronized void addPlayerScore(int playerId, int points) {
        playerScores.put(playerId, getPlayerScore(playerId) + points);
    }

    /**
     * Devolve a máscara atual como String (ex: "_ a _ _ o").
     * Os underscores são separados por espaços para melhor legibilidade.
     */
    public synchronized String getMaskString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mask.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(mask[i]);
        }
        return sb.toString();
    }

    /**
     * Devolve as letras já utilizadas como String separada por vírgulas.
     * Ex: "a,e,r,t"
     */
    public synchronized String getUsedLettersString() {
        if (usedLetters.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (char c : usedLetters) {
            if (sb.length() > 0) sb.append(',');
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Devolve o desenho ASCII do boneco (Hangman stage).
     * A progressão é baseada nas tentativas que foram perdidas.
     * 
     * @return String contendo o desenho ASCII do boneco.
     */
    public synchronized String getHangmanStage() {
        int stageIndex = Math.min(MAX_ATTEMPTS - attemptsLeft, HANGMAN_STAGES.length - 1);
        return HANGMAN_STAGES[stageIndex];
    }

    /**
     * Obtém uma dica baseada na categoria da palavra.
     * Consome uma dica se ainda existirem dicas disponíveis.
     * 
     * A primeira dica revela apenas a categoria.
     * A segunda dica revela a categoria + primeira letra.
     *
     * @return Uma String com a dica, ou null se não há dicas restantes.
     */
    public synchronized String getHint() {
        if (hintsRemaining <= 0) {
            return null;
        }
        
        // Nível de dica: 1 (primeira) ou 2 (segunda)
        int hintLevel = 3 - hintsRemaining; // Se hints=2, level=1; se hints=1, level=2
        hintsRemaining--;
        
        return BancoPalavras.getHint(wordEntry, hintLevel);
    }

    // ─────────────────────────────────────────────────────────────
    //  Métodos de modificação de estado
    // ─────────────────────────────────────────────────────────────

    /** Incrementa o contador de jogadores registados. */
    public synchronized void addPlayer() {
        totalPlayers++;
    }

    /** Muda o estado para EM_PROGRESSO e regista o início da ronda 1. */
    public synchronized void startGame() {
        this.status       = Estado.EM_PROGRESSO;
        this.currentRound = 1;
        this.roundGuesses.clear();
    }

    /**
     * Valida uma jogada antes de ser registada.
     * Lança ExcecaoJogadaInvalida se inválida.
     * 
     * Regras de validação:
     *  - Não pode ser null ou vazia
     *  - Se letra única: deve ser A-Z
     *  - Se palavra inteira: 2+ chars, apenas A-Z e espaços
     *  - A jogada não foi já utilizada
     *  - A jogada não é válida para a palavra (depuração)
     *
     * @param guess Jogada a validar
     * @throws ExcecaoJogadaInvalida Se inválida
     */
    public synchronized void validateGuess(String guess) throws ExcecaoJogadaInvalida {
        if (guess == null) {
            throw new ExcecaoJogadaInvalida("Jogada não pode ser nula");
        }

        guess = guess.trim().toLowerCase();

        if (guess.isEmpty()) {
            throw new ExcecaoJogadaInvalida("Jogada não pode estar vazia");
        }

        // Verificar tipo: letra única ou palavra
        if (guess.length() == 1) {
            // Validar como letra individual
            char letter = guess.charAt(0);
            if (!Character.isLetter(letter)) {
                throw new ExcecaoJogadaInvalida("Deve ser uma letra A-Z, recebido: " + letter);
            }
            if (usedLetters.contains(letter)) {
                throw new ExcecaoJogadaInvalida("Letra já foi utilizada: " + letter);
            }
        } else {
            // Validar como palavra inteira
            if (!guess.matches("[a-z ]+")) {
                throw new ExcecaoJogadaInvalida("Palavra contém caracteres inválidos. Apenas A-Z e espaços permitidos.");
            }
            if (guess.length() > 50) {
                throw new ExcecaoJogadaInvalida("Palavra muito comprida (máx 50 caracteres)");
            }
            // Aviso: Não verificar se a palavra já foi tentada (menos importante)
        }
    }

    /**
     * Regista a jogada de um jogador para a ronda atual.
     * Se o jogador já tiver jogado nesta ronda, a jogada é ignorada
     * (o protocolo não permite jogar duas vezes por ronda).
     * 
     * IMPORTANTE: Chamar validateGuess() ANTES desta função!
     *
     * @param playerId ID do jogador.
     * @param guess    Jogada (letra ou palavra). Pode ser "" em caso de timeout.
     */
    public synchronized boolean submitGuess(int playerId, String guess) {
        // Só registar se o jogo ainda está em progresso e o jogador ainda não jogou
        if (status == Estado.EM_PROGRESSO && !roundGuesses.containsKey(playerId)) {
            roundGuesses.put(playerId, guess.toLowerCase().trim());
            return true;
        }
        return false;
    }

    /**
     * Verifica se todos os jogadores já submeteram a sua jogada para a ronda atual.
     *
     * @param registeredPlayerIds IDs de todos os jogadores registados.
     * @return true se todas as jogadas foram recolhidas.
     */
    public synchronized boolean allGuessesCollected(Set<Integer> registeredPlayerIds) {
        return roundGuesses.keySet().containsAll(registeredPlayerIds);
    }

    /**
     * Processa todas as jogadas da ronda atual e atualiza o estado do jogo.
     *
     * Regras rigorosas (conforme especificação):
     *  - Cada jogada individual que não contribui consome 1 tentativa do pool partilhado:
     *      · Letra errada (não existe na palavra)         → 1 tentativa
     *      · Palavra errada (não corresponde à secreta)   → 1 tentativa
     *      · Timeout / jogada em branco                   → 1 tentativa
     *  - Jogada correta (letra ou palavra) não consome tentativa.
     *  - Se dois jogadores propõem a mesma letra, a segunda revelação não conta
     *    como errada nem como correta (letra já estava revelada).
     *  - Vitória: um ou mais jogadores adivinham a palavra completa na mesma ronda.
     *  - Vitória alternativa: máscara completa por letras → todos os jogadores ativos ganham.
     *  - Derrota: tentativas chegam a 0.
     */
    public synchronized void processRound(Set<Integer> activePlayerIds) {
        winnerIds.clear();
        int attemptsToConsume = 0;

        for (int playerId : activePlayerIds) {
            String guess = roundGuesses.getOrDefault(playerId, "");

            if (guess.isEmpty()) {
                // Timeout ou jogada em branco: consome 1 tentativa (obrigatório por spec)
                attemptsToConsume++;
                UtilitarioLog.info("Jogador " + playerId + " não jogou (timeout) → -1 tentativa");
                continue;
            }

            if (guess.length() == 1) {
                char letter = guess.charAt(0);
                if (secretWord.contains(String.valueOf(letter))) {
                    // Letra correta: revelar (apenas se ainda não revelada)
                    if (!usedLetters.contains(letter)) {
                        usedLetters.add(letter);
                        revealLetter(letter);
                        UtilitarioLog.info("Jogador " + playerId + " acertou letra '" + letter + "'");
                    } else {
                        // Letra já revelada por outro jogador nesta ronda: ignorar sem custo
                        UtilitarioLog.info("Jogador " + playerId + " propôs letra '" + letter + "' já revelada");
                    }
                    // Sem consumir tentativa
                } else {
                    // Letra errada: adicionar às usadas e consumir 1 tentativa
                    usedLetters.add(letter);
                    attemptsToConsume++;
                    UtilitarioLog.info("Jogador " + playerId + " errou letra '" + letter + "' → -1 tentativa");
                }
            } else {
                // Proposta de palavra completa
                if (guess.equals(secretWord)) {
                    winnerIds.add(playerId);
                    UtilitarioLog.info("Jogador " + playerId + " adivinhou a palavra!");
                    // Sem consumir tentativa
                } else {
                    // Palavra errada: consome 1 tentativa
                    attemptsToConsume++;
                    UtilitarioLog.info("Jogador " + playerId + " errou a palavra '" + guess + "' → -1 tentativa");
                }
            }
        }

        // Vitória por adivinha da palavra completa (antes de consumir tentativas):
        // o jogo termina independentemente das tentativas restantes
        if (!winnerIds.isEmpty()) {
            revealWholeWord();
            status = Estado.TERMINADO;
            UtilitarioLog.info("[Fim] Vitória! Vencedores: " + winnerIds);
            return;
        }

        // Consumir todas as tentativas acumuladas nesta ronda
        for (int i = 0; i < attemptsToConsume; i++) {
            consumeAttempt();
        }
        UtilitarioLog.info("[Ronda] " + attemptsToConsume + " tentativa(s) consumida(s). Restam: " + attemptsLeft);

        // Vitória por revelação completa da máscara via letras
        if (isMaskComplete()) {
            winnerIds.addAll(activePlayerIds);
            status = Estado.TERMINADO;
            UtilitarioLog.info("[Fim] Máscara completa! Todos os jogadores ativos ganham.");
            return;
        }

        // Derrota: tentativas esgotadas
        if (attemptsLeft <= 0) {
            attemptsLeft = 0;
            status = Estado.TERMINADO;
            UtilitarioLog.info("[Fim] Tentativas esgotadas. Derrota.");
            return;
        }

        // Continuar para a próxima ronda
        currentRound++;
        roundGuesses.clear();
    }

    private void revealLetter(char letter) {
        for (int i = 0; i < secretWord.length(); i++) {
            if (secretWord.charAt(i) == letter) {
                mask[i] = letter;
            }
        }
    }

    private void revealWholeWord() {
        for (int i = 0; i < secretWord.length(); i++) {
            mask[i] = secretWord.charAt(i);
        }
    }

    private void consumeAttempt() {
        if (attemptsLeft > 0) {
            attemptsLeft--;
        }
    }

    /**
     * Verifica se a máscara está totalmente revelada (sem '_').
     */
    private boolean isMaskComplete() {
        for (char c : mask) {
            if (c == '_') return false;
        }
        return true;
    }

    /**
     * Verifica se o jogo terminou com vitória (alguém ganhou).
     */
    public synchronized boolean isVictory() {
        return status == Estado.TERMINADO && !winnerIds.isEmpty();
    }

    /**
     * Devolve uma cópia das jogadas da ronda atual (para registo e depuração).
     */
    public synchronized Map<Integer, String> getRoundGuesses() {
        return Collections.unmodifiableMap(new HashMap<>(roundGuesses));
    }

    /**
     * Finaliza o jogo forçadamente (ex: todos os jogadores desconectaram).
     * Muda o status para TERMINADO.
     */
    public synchronized void finishGame() {
        this.status = Estado.TERMINADO;
    }
}
