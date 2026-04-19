package server;

import java.util.logging.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * UtilitárioLog — Utilitário centralizado de registo de eventos (logging) para o servidor.
 *
 * Uso:
 *   UtilitárioLog.info("Mensagem informativa");
 *   UtilitárioLog.aviso("Aviso importante");
 *   UtilitárioLog.erro("Erro crítico", excecao);
 *
 * Os registos são gravados simultaneamente em:
 *  - Ficheiro: logs/hangman-YYYYMMDD-HHmmss.log
 *  - Consola:  saída padrão de erro (System.err)
 */
public class UtilitarioLog {

    private static Logger logger;
    private static final DateTimeFormatter formatoData =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static {
        try {
            logger = Logger.getLogger("JogoDaForca");

            // Criar ficheiro de registo com marca temporal única
            String nomeFicheiro = "logs/hangman-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) +
                ".log";

            // Criar o diretório logs/ se não existir
            new File("logs").mkdirs();

            // Gestor de ficheiro de registo
            FileHandler gestorFicheiro = new FileHandler(nomeFicheiro, true);
            gestorFicheiro.setFormatter(new SimpleFormatter() {
                @Override
                public synchronized String format(LogRecord record) {
                    return String.format("[%s] [%s] %s\n",
                        LocalDateTime.now().format(formatoData),
                        record.getLevel().getName(),
                        record.getMessage());
                }
            });
            logger.addHandler(gestorFicheiro);

            // Gestor de consola para visualização em tempo real
            ConsoleHandler gestorConsola = new ConsoleHandler();
            gestorConsola.setFormatter(new SimpleFormatter() {
                @Override
                public synchronized String format(LogRecord record) {
                    return String.format("[%s] [%-8s] %s",
                        LocalDateTime.now().format(formatoData),
                        record.getLevel().getName(),
                        record.getMessage());
                }
            });
            logger.addHandler(gestorConsola);

            logger.setLevel(Level.INFO);

        } catch (IOException e) {
            System.err.println("Erro ao inicializar registo de eventos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Regista uma mensagem informativa. */
    public static void info(String mensagem) {
        logger.info(mensagem);
    }

    /** Regista um aviso. */
    public static void warning(String mensagem) {
        logger.warning(mensagem);
    }

    /** Regista um erro grave. */
    public static void severe(String mensagem) {
        logger.severe(mensagem);
    }

    /** Regista um erro com exceção associada. */
    public static void error(String mensagem, Throwable excecao) {
        logger.log(Level.SEVERE, mensagem, excecao);
    }

    /** Regista uma mensagem de depuração (nível FINE). */
    public static void debug(String mensagem) {
        logger.fine(mensagem);
    }

    /** Fecha todos os gestores de registo e liberta recursos. */
    public static void close() {
        for (Handler gestor : logger.getHandlers()) {
            gestor.close();
        }
    }
}
