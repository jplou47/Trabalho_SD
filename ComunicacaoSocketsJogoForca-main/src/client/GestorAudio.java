package client;

import javax.sound.sampled.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GestorAudio — Sintetizador de sons e música para o Jogo da Forca.
 *
 * Gera todos os sons por síntese de onda sinusoidal pura (javax.sound.sampled),
 * sem ficheiros externos. Totalmente original e livre de royalties.
 *
 * Sons/músicas disponíveis:
 *  - Melodia do menu (loop pentatónico enquanto aguarda)
 *  - Música lofi durante o jogo (acordes jazz Am7→Fmaj7→Dm7→E7 a 80 BPM)
 *  - Acerto (letra correta)
 *  - Erro (letra errada)
 *  - Vitória (fanfarra)
 *  - Derrota (tom descendente)
 *  - Clique de botão
 */
public class GestorAudio {

    private static final int TAXA_AMOSTRAGEM = 44100;

    // Estado das músicas de fundo
    private static volatile boolean musicaMenuAtiva  = false;
    private static volatile boolean musicaJogoAtiva  = false;
    private static volatile boolean silenciado        = false;
    private static Thread threadMenuMusica            = null;
    private static Thread threadJogoMusica            = null;

    private static final ExecutorService executor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "thread-audio");
                t.setDaemon(true);
                return t;
            });

    // ─────────────────────────────────────────────────────────────
    //  API pública — músicas de fundo
    // ─────────────────────────────────────────────────────────────

    /** Inicia a melodia do menu em loop. Não bloqueia. */
    public static void iniciarMenuMusica() {
        pararMusicaJogo();
        if (musicaMenuAtiva || silenciado) return;
        musicaMenuAtiva = true;
        threadMenuMusica = new Thread(GestorAudio::loopMenuMusica, "thread-musica-menu");
        threadMenuMusica.setDaemon(true);
        threadMenuMusica.start();
    }

    /** Para a melodia do menu. */
    public static void pararMenuMusica() {
        musicaMenuAtiva = false;
        if (threadMenuMusica != null) {
            threadMenuMusica.interrupt();
            threadMenuMusica = null;
        }
    }

    /**
     * Inicia a música lofi durante o jogo em loop.
     * Progressão jazz: Am7 → Fmaj7 → Dm7 → E7 a ~80 BPM.
     * Não bloqueia.
     */
    public static void iniciarMusicaJogo() {
        pararMenuMusica();
        if (musicaJogoAtiva || silenciado) return;
        musicaJogoAtiva = true;
        threadJogoMusica = new Thread(GestorAudio::loopMusicaLofi, "thread-musica-jogo");
        threadJogoMusica.setDaemon(true);
        threadJogoMusica.start();
    }

    /** Para a música lofi do jogo. */
    public static void pararMusicaJogo() {
        musicaJogoAtiva = false;
        if (threadJogoMusica != null) {
            threadJogoMusica.interrupt();
            threadJogoMusica = null;
        }
    }

    /** Para toda a música de fundo. */
    public static void pararTodaMusica() {
        pararMenuMusica();
        pararMusicaJogo();
    }

    /** Alterna entre som ligado e silenciado. Devolve o novo estado (true = silenciado). */
    public static boolean alternarSilencio() {
        silenciado = !silenciado;
        if (silenciado) {
            pararTodaMusica();
        }
        return silenciado;
    }

    public static boolean estaSilenciado() { return silenciado; }

    // ─────────────────────────────────────────────────────────────
    //  API pública — efeitos sonoros
    // ─────────────────────────────────────────────────────────────

    /** Toca o som de acerto (letra correta). */
    public static void tocarAcerto() {
        if (silenciado) return;
        executor.submit(() -> tocarSequencia(new double[]{523.25, 659.25, 783.99}, new int[]{80, 80, 140}));
    }

    /** Toca o som de erro (letra errada). */
    public static void tocarErro() {
        if (silenciado) return;
        executor.submit(() -> tocarSequencia(new double[]{220, 180, 150}, new int[]{100, 100, 180}));
    }

    /** Toca a fanfarra de vitória. */
    public static void tocarVitoria() {
        if (silenciado) return;
        pararTodaMusica();
        executor.submit(() -> {
            double[] notas = {523.25, 659.25, 783.99, 1046.50, 783.99, 1046.50};
            int[]    durs  = {120, 120, 120, 300, 120, 400};
            tocarSequencia(notas, durs);
        });
    }

    /** Toca o som de derrota (descida trágica). */
    public static void tocarDerrota() {
        if (silenciado) return;
        pararTodaMusica();
        executor.submit(() -> {
            double[] notas = {392, 349.23, 329.63, 261.63};
            int[]    durs  = {200, 200, 200, 500};
            tocarSequencia(notas, durs);
        });
    }

    /** Toca o clique de botão. */
    public static void tocarClique() {
        if (silenciado) return;
        executor.submit(() -> tocarTom(880, 30, 0.2));
    }

    // ─────────────────────────────────────────────────────────────
    //  Melodia do menu (loop pentatónico)
    // ─────────────────────────────────────────────────────────────

    /**
     * Melodia do menu — escala pentatónica de Lá menor, estilo aventura/mistério.
     * Composição original, royalty-free.
     */
    private static void loopMenuMusica() {
        double[] notas = {
            220.00, 261.63, 293.66, 329.63, 392.00, 440.00,
            392.00, 329.63, 293.66, 261.63, 220.00, 0,
            261.63, 329.63, 392.00, 440.00, 523.25, 440.00,
            392.00, 329.63, 261.63, 220.00, 0, 0,
        };
        int[] duracoes = {
            200, 150, 150, 200, 150, 300,
            150, 200, 150, 150, 300, 200,
            150, 200, 150, 300, 200, 150,
            150, 200, 150, 400, 300, 200,
        };
        try {
            while (musicaMenuAtiva && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < notas.length && musicaMenuAtiva; i++) {
                    if (notas[i] == 0) Thread.sleep(duracoes[i]);
                    else tocarTomComFade(notas[i], duracoes[i], 0.35);
                }
                Thread.sleep(600);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Música lofi do jogo (loop jazz)
    // ─────────────────────────────────────────────────────────────

    /**
     * Música lofi para o decorrer do jogo.
     *
     * Progressão jazz a ~80 BPM (tempo de colcheia ≈ 375ms):
     *   Am7 (A C E G) → Fmaj7 (F A C E) → Dm7 (D F A C) → E7 (E G# B D)
     *
     * Cada acorde é arpegiado suavemente (notas sobrepostas em fundo)
     * com uma linha de baixo marcada. Inspiração: lofi hip-hop/jazz.
     * Composição original, royalty-free.
     */
    private static void loopMusicaLofi() {
        // Acorde Am7: A2 C3 E3 G3 A3 (fundamental + 3ª menor + 5ª + 7ª menor + oitava)
        double[][] acordes = {
            {110.00, 130.81, 164.81, 196.00, 220.00},  // Am7
            { 87.31, 110.00, 130.81, 164.81, 196.00},  // Fmaj7
            { 73.42,  87.31, 110.00, 130.81, 164.81},  // Dm7
            { 82.41, 103.83, 123.47, 146.83, 164.81},  // E7
        };

        // Duração de cada nota do arpejo (ms) — tempo relaxado lofi
        int notaDur  = 320;   // cada nota do arpejo
        int acordeDur = 2400; // duração total do acorde (= 1 compasso a 80BPM)

        // Melodia simples por cima (A pentatónica menor, oitava mais alta)
        double[][] melodiaPorAcorde = {
            {440.00, 392.00, 440.00, 0},         // sobre Am7
            {349.23, 392.00, 349.23, 0},         // sobre Fmaj7
            {293.66, 349.23, 329.63, 0},         // sobre Dm7
            {329.63, 349.23, 392.00, 329.63},    // sobre E7
        };

        try {
            while (musicaJogoAtiva && !Thread.currentThread().isInterrupted()) {
                for (int a = 0; a < acordes.length && musicaJogoAtiva; a++) {
                    double[] notas   = acordes[a];
                    double[] melodia = melodiaPorAcorde[a];

                    // ── Arpejo do acorde (notas do baixo ao agudo) ────────
                    for (int n = 0; n < notas.length && musicaJogoAtiva; n++) {
                        tocarTomComFade(notas[n], notaDur, 0.28);
                    }

                    // ── Melodia por cima (suave) ──────────────────────────
                    int restante = acordeDur - notaDur * notas.length;
                    int durMel   = restante / melodia.length;
                    for (double freq : melodia) {
                        if (!musicaJogoAtiva) break;
                        if (freq == 0) Thread.sleep(durMel);
                        else tocarTomComFade(freq, durMel, 0.18);
                    }
                }
                // Pequena pausa entre repetições
                if (musicaJogoAtiva) Thread.sleep(400);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Motor de síntese de áudio
    // ─────────────────────────────────────────────────────────────

    private static void tocarSequencia(double[] frequencias, int[] duracoes) {
        try {
            for (int i = 0; i < frequencias.length; i++) {
                tocarTomComFade(frequencias[i], duracoes[i], 0.5);
            }
        } catch (Exception e) { /* ignorar */ }
    }

    private static void tocarTom(double frequencia, int duracaoMs, double volume) {
        tocarTomComFade(frequencia, duracaoMs, volume);
    }

    /**
     * Sintetiza e toca um tom sinusoidal com envelope de fade-in/fade-out.
     * Evita cliques de áudio nas transições.
     *
     * @param frequencia Frequência em Hz.
     * @param duracaoMs  Duração em milissegundos.
     * @param volume     Volume máximo (0.0 a 1.0).
     */
    private static void tocarTomComFade(double frequencia, int duracaoMs, double volume) {
        try {
            AudioFormat formato = new AudioFormat(TAXA_AMOSTRAGEM, 16, 1, true, false);
            DataLine.Info info  = new DataLine.Info(SourceDataLine.class, formato);
            if (!AudioSystem.isLineSupported(info)) return;

            SourceDataLine linha = (SourceDataLine) AudioSystem.getLine(info);
            linha.open(formato);
            linha.start();

            int totalAmostras = (int) (TAXA_AMOSTRAGEM * duracaoMs / 1000.0);
            int fadeSamples   = Math.min(totalAmostras / 8, 600);
            byte[] buffer     = new byte[totalAmostras * 2];

            for (int i = 0; i < totalAmostras; i++) {
                double envelope = 1.0;
                if (i < fadeSamples) envelope = (double) i / fadeSamples;
                else if (i > totalAmostras - fadeSamples)
                    envelope = (double) (totalAmostras - i) / fadeSamples;

                double angulo = 2.0 * Math.PI * frequencia * i / TAXA_AMOSTRAGEM;
                // Onda sinusoidal suavizada com harmónico para timbre mais quente
                double amostra = (Math.sin(angulo) * 0.8 + Math.sin(2 * angulo) * 0.15)
                                 * envelope * volume;

                short valor = (short) (amostra * Short.MAX_VALUE * 0.9);
                buffer[2 * i]     = (byte) (valor & 0xFF);
                buffer[2 * i + 1] = (byte) ((valor >> 8) & 0xFF);
            }

            linha.write(buffer, 0, buffer.length);
            linha.drain();
            linha.close();

        } catch (LineUnavailableException e) { /* áudio não disponível */ }
    }
}
