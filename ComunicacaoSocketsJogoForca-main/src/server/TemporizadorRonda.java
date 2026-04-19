package server;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
/**
 * TemporizadorRonda — Temporizador de contagem decrescente por ronda.
 *
 * Funcionalidades:
 *  - Contagem decrescente configurável (em segundos)
 *  - Função de retorno (listener) a cada segundo (para atualizar clientes)
 *  - Deteção automática de expiração
 *  - Seguro para uso em ambiente multi-cliente (uso concorrente seguro)
 *
 * Exemplo de uso:
 *   TemporizadorRonda timer = new TemporizadorRonda(30, EstadoJogo.ROUND_TIMEOUT_MS);
 *   timer.setListenerPorSegundo(() -> enviarATodos("TIMER " + timer.getSegundosRestantes()));
 *   timer.iniciar();
 *   // ... aguardar
 *   boolean expirou = timer.expirou();
 *   timer.parar();
 */
public class TemporizadorRonda {

    /** Duração total do temporizador em segundos. */
    private final int duracaoSegundos;

    /** Tempo total em milissegundos (para sincronização com ROUND_TIMEOUT_MS). */
    private final int totalMillis;

    /** Tempo restante (em segundos). */
    private final AtomicInteger segundosRestantes;

    /** Indicador: o temporizador expirou? */
    private final AtomicInteger expirado = new AtomicInteger(0);

    /** Função de retorno invocada a cada segundo. */
    private volatile Runnable listenerPorSegundo;

    /** Thread que executa a contagem decrescente. */
    private volatile Thread threadTemporizador;

    /** Indicador de controlo: o temporizador ainda está ativo? */
    private volatile boolean ativo = true;

    /**
     * Cria um novo TemporizadorRonda.
     *
     * @param duracaoSegundos Duração em segundos (tipicamente 15).
     * @param totalMillis     Tempo total em milissegundos.
     */
    public TemporizadorRonda(int duracaoSegundos, int totalMillis) {
        this.duracaoSegundos   = duracaoSegundos;
        this.totalMillis       = totalMillis;
        this.segundosRestantes = new AtomicInteger(duracaoSegundos);
    }

    /**
     * Define a função de retorno invocada a cada pulso (segundo).
     * A função é chamada de forma síncrona na thread do temporizador.
     *
     * @param listener O Runnable a executar por segundo.
     */
    public void setOnTickListener(Runnable listener) {
        this.listenerPorSegundo = listener;
    }

    /**
     * Inicia a contagem decrescente.
     * Cria uma nova thread dedicada que decrementa o contador a cada segundo.
     */
    public void start() {
        if (threadTemporizador != null && threadTemporizador.isAlive()) {
            return; // Já está em execução
        }

        ativo = true;
        segundosRestantes.set(duracaoSegundos);
        expirado.set(0);

        threadTemporizador = new Thread(() -> {
            while (ativo && segundosRestantes.get() > 0) {
                try {
                    Thread.sleep(1000); // Aguardar 1 segundo
                    segundosRestantes.decrementAndGet();

                    if (listenerPorSegundo != null) {
                        try {
                            listenerPorSegundo.run();
                        } catch (Exception e) {
                            UtilitarioLog.error("Erro ao invocar função de retorno do temporizador", e);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Temporizador expirou
            if (ativo && segundosRestantes.get() <= 0) {
                expirado.set(1);
                if (listenerPorSegundo != null) {
                    try {
                        listenerPorSegundo.run();
                    } catch (Exception e) {
                        UtilitarioLog.error("Erro ao invocar função de retorno (expiração)", e);
                    }
                }
            }
        });

        threadTemporizador.setDaemon(true);
        threadTemporizador.setName("thread-temporizador");
        threadTemporizador.start();
    }

    /**
     * Para o temporizador imediatamente.
     */
    public void stop() {
        ativo = false;
        if (threadTemporizador != null && threadTemporizador.isAlive()) {
            threadTemporizador.interrupt();
            try {
                threadTemporizador.join(500); // Aguardar até 500ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Obtém o número de segundos restantes (mínimo 0).
     *
     * @return Segundos restantes.
     */
    public int getSecondsRemaining() {
        return Math.max(0, segundosRestantes.get());
    }

    /**
     * Verifica se o temporizador expirou.
     *
     * @return {@code true} se o tempo esgotou.
     */
    public boolean isExpired() {
        return expirado.get() == 1 || segundosRestantes.get() <= 0;
    }

    /**
     * Reinicia o temporizador (repõe o tempo original).
     */
    public void reset() {
        stop();
        segundosRestantes.set(duracaoSegundos);
        expirado.set(0);
    }

    /**
     * Devolve a percentagem do tempo restante (0–100).
     * Útil para barras de progresso visuais.
     *
     * @return Percentagem do tempo restante.
     */
    public int getProgressPercent() {
        return (getSecondsRemaining() * 100) / duracaoSegundos;
    }
}
