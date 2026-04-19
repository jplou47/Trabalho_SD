package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ServidorForca — Ponto de entrada do servidor do Jogo da Forca.
 *
 * Inicia um ServerSocket TCP na porta configurada e aceita ligações
 * de jogadores, delegando cada uma a um {@link GestorCliente} gerido
 * por um conjunto de threads (thread pool).
 */
public class ServidorForca {

    /** Porta TCP onde o servidor escuta ligações. */
    private static final int PORT = 12345;

    public static void main(String[] args) {
        GestorJogo gameManager = GestorJogo.getInstance();

        // Conjunto de threads com capacidade dinâmica para lidar com os MAX_PLAYERS
        // jogadores ativos mais ligações excedentes que chegam após o início do jogo
        // e precisam de ser rejeitadas rapidamente (envio de FULL). Um conjunto fixo
        // de apenas MAX_PLAYERS bloquearia essas rejeições enquanto o jogo decorre.
        ExecutorService conjuntoThreads = Executors.newCachedThreadPool();

        try (ServerSocket servidorSocket = new ServerSocket(PORT)) {
            System.out.println("[Servidor] À espera de ligações na porta " + PORT + "...");

            // Aceita ligações indefinidamente; o GestorJogo rejeita com FULL
            // quando o jogo já está em curso.
            while (true) {
                Socket socketCliente = servidorSocket.accept();
                System.out.println("[Servidor] Nova ligação de: "
                        + socketCliente.getInetAddress().getHostAddress());

                // Submete o gestor de cliente ao conjunto de threads em vez de criar
                // threads manualmente. Uma thread do conjunto fica responsável por este
                // cliente até ele desligar.
                GestorCliente gestor = new GestorCliente(socketCliente, gameManager);
                conjuntoThreads.submit(gestor);
            }

        } catch (IOException e) {
            System.err.println("[Servidor] Erro fatal: " + e.getMessage());
        } finally {
            conjuntoThreads.shutdown();
        }
    }
}
