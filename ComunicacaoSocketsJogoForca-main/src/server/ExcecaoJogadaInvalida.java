package server;

/**
 * Exceção para jogadas inválidas.
 * Lançada quando o utilizador faz uma jogada inválida (letra errada, palavra vazia, etc).
 */
public class ExcecaoJogadaInvalida extends ExcecaoJogo {
    public ExcecaoJogadaInvalida(String message) {
        super(message);
    }

    public ExcecaoJogadaInvalida(String message, Throwable cause) {
        super(message, cause);
    }
}
