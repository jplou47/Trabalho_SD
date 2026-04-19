package server;

/**
 * Exceção base para erros do jogo.
 * Todas as exceções do jogo da forca devem estender esta classe.
 */
public class ExcecaoJogo extends Exception {
    public ExcecaoJogo(String message) {
        super(message);
    }

    public ExcecaoJogo(String message, Throwable cause) {
        super(message, cause);
    }
}
