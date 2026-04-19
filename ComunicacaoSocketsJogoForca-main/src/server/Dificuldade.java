package server;

/**
 * Enum Dificuldade — Níveis de dificuldade do jogo.
 * 
 * Define o número de tentativas e complexidade das palavras.
 */
public enum Dificuldade {
    EASY(12, "Fácil"),
    NORMAL(10, "Normal"),
    HARD(6, "Difícil");

    private final int attempts;
    private final String displayName;

    Dificuldade(int attempts, String displayName) {
        this.attempts = attempts;
        this.displayName = displayName;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Devolve a dificuldade a partir de uma string.
     * Ex: "HARD" → Dificuldade.HARD
     */
    public static Dificuldade fromString(String str) {
        try {
            return Dificuldade.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;  // Default
        }
    }
}
