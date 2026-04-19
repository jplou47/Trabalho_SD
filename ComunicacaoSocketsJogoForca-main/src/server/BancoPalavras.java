package server;

import java.util.*;

/**
 * BancoPalavras — Banco de palavras para o Jogo da Forca com categorias e dificuldades.
 *
 * Organiza 100+ palavras por:
 *  - Categoria (Animal, Fruta, Profissão, etc)
 *  - Dificuldade (comprimento da palavra)
 *
 * Suporta:
 *  - Seleção aleatória por dificuldade
 *  - Dicas baseadas na categoria
 *  - Busca por categoria
 */
public class BancoPalavras {

    /** Banco de palavras com categorias. */
    private static final EntradaPalavra[] WORDS = {
        // ────────────────────────────────────────
        //  ANIMAIS
        // ────────────────────────────────────────
        new EntradaPalavra("elefante", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("girafa", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("pinguim", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("crocodilo", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("borboleta", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("camaleao", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("rinoceronte", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("hipopotamo", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("leao", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("tigre", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("golfinho", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("coelho", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("cavalo", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("lobo", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("urso", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("baleia", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("cabra", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("ovelha", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("porco", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("gato", CategoriaPalavra.ANIMAL),
        new EntradaPalavra("cão", CategoriaPalavra.ANIMAL),

        
        // ────────────────────────────────────────
        //  FRUTAS
        // ────────────────────────────────────────
        new EntradaPalavra("morango", CategoriaPalavra.FRUIT),
        new EntradaPalavra("melancia", CategoriaPalavra.FRUIT),
        new EntradaPalavra("ananas", CategoriaPalavra.FRUIT),
        new EntradaPalavra("framboesa", CategoriaPalavra.FRUIT),
        new EntradaPalavra("maracuja", CategoriaPalavra.FRUIT),
        new EntradaPalavra("goiaba", CategoriaPalavra.FRUIT),
        new EntradaPalavra("pitanga", CategoriaPalavra.FRUIT),
        new EntradaPalavra("carambola", CategoriaPalavra.FRUIT),
        new EntradaPalavra("tamarindo", CategoriaPalavra.FRUIT),
        new EntradaPalavra("lichia", CategoriaPalavra.FRUIT),
        
        // ────────────────────────────────────────
        //  PROFISSÕES
        // ────────────────────────────────────────
        new EntradaPalavra("arquiteto", CategoriaPalavra.PROFESSION),
        new EntradaPalavra("bombeiro", CategoriaPalavra.PROFESSION),
        new EntradaPalavra("engenheiro", CategoriaPalavra.PROFESSION),
        new EntradaPalavra("veterinario", CategoriaPalavra.PROFESSION),
        new EntradaPalavra("farmaceutico", CategoriaPalavra.PROFESSION),
        new EntradaPalavra("advogado", CategoriaPalavra.PROFESSION),
        new EntradaPalavra("professor", CategoriaPalavra.PROFESSION),
        new EntradaPalavra("enfermeiro", CategoriaPalavra.PROFESSION),
        new EntradaPalavra("astronauta", CategoriaPalavra.PROFESSION),
        new EntradaPalavra("geologo", CategoriaPalavra.PROFESSION),
        
        // ────────────────────────────────────────
        //  PAÍSES
        // ────────────────────────────────────────
        new EntradaPalavra("portugal", CategoriaPalavra.COUNTRY),
        new EntradaPalavra("espanha", CategoriaPalavra.COUNTRY),
        new EntradaPalavra("franca", CategoriaPalavra.COUNTRY),
        new EntradaPalavra("alemanha", CategoriaPalavra.COUNTRY),
        new EntradaPalavra("italia", CategoriaPalavra.COUNTRY),
        new EntradaPalavra("argentina", CategoriaPalavra.COUNTRY),
        new EntradaPalavra("brasil", CategoriaPalavra.COUNTRY),
        new EntradaPalavra("egipto", CategoriaPalavra.COUNTRY),
        new EntradaPalavra("tailandia", CategoriaPalavra.COUNTRY),
        new EntradaPalavra("australia", CategoriaPalavra.COUNTRY),
        
        // ────────────────────────────────────────
        //  CIDADES
        // ────────────────────────────────────────
        new EntradaPalavra("lisboa", CategoriaPalavra.CITY),
        new EntradaPalavra("porto", CategoriaPalavra.CITY),
        new EntradaPalavra("covilha", CategoriaPalavra.CITY),
        new EntradaPalavra("almada", CategoriaPalavra.CITY),
        new EntradaPalavra("barcelona", CategoriaPalavra.CITY),
        new EntradaPalavra("paris", CategoriaPalavra.CITY),
        new EntradaPalavra("berlim", CategoriaPalavra.CITY),
        new EntradaPalavra("roma", CategoriaPalavra.CITY),
        new EntradaPalavra("viana do castelo", CategoriaPalavra.CITY),
        new EntradaPalavra("guarda", CategoriaPalavra.CITY),
        
        // ────────────────────────────────────────
        //  DESPORTOS
        // ────────────────────────────────────────
        new EntradaPalavra("basquetebol", CategoriaPalavra.SPORT),
        new EntradaPalavra("badminton", CategoriaPalavra.SPORT),
        new EntradaPalavra("karate", CategoriaPalavra.SPORT),
        new EntradaPalavra("ciclismo", CategoriaPalavra.SPORT),
        new EntradaPalavra("ginastica", CategoriaPalavra.SPORT),
        new EntradaPalavra("esgrima", CategoriaPalavra.SPORT),
        new EntradaPalavra("atletismo", CategoriaPalavra.SPORT),
        new EntradaPalavra("remo", CategoriaPalavra.SPORT),
        new EntradaPalavra("mergulho", CategoriaPalavra.SPORT),
        new EntradaPalavra("hipismo", CategoriaPalavra.SPORT),
        
        // ────────────────────────────────────────
        //  INSTRUMENTOS
        // ────────────────────────────────────────
        new EntradaPalavra("violoncelo", CategoriaPalavra.INSTRUMENT),
        new EntradaPalavra("trompete", CategoriaPalavra.INSTRUMENT),
        new EntradaPalavra("clarinete", CategoriaPalavra.INSTRUMENT),
        new EntradaPalavra("trombone", CategoriaPalavra.INSTRUMENT),
        new EntradaPalavra("acordeao", CategoriaPalavra.INSTRUMENT),
        new EntradaPalavra("bandolim", CategoriaPalavra.INSTRUMENT),
        new EntradaPalavra("gaita", CategoriaPalavra.INSTRUMENT),
        new EntradaPalavra("ukulele", CategoriaPalavra.INSTRUMENT),
        new EntradaPalavra("fagote", CategoriaPalavra.INSTRUMENT),
        new EntradaPalavra("clavicordio", CategoriaPalavra.INSTRUMENT),
        
        // ────────────────────────────────────────
        //  COMIDA
        // ────────────────────────────────────────
        new EntradaPalavra("esparguete", CategoriaPalavra.FOOD),
        new EntradaPalavra("lasanha", CategoriaPalavra.FOOD),
        new EntradaPalavra("coentro", CategoriaPalavra.FOOD),
        new EntradaPalavra("alface", CategoriaPalavra.FOOD),
        new EntradaPalavra("repolho", CategoriaPalavra.FOOD),
        new EntradaPalavra("brocolis", CategoriaPalavra.FOOD),
        new EntradaPalavra("espinafre", CategoriaPalavra.FOOD),
        new EntradaPalavra("abobora", CategoriaPalavra.FOOD),
        new EntradaPalavra("beringela", CategoriaPalavra.FOOD),
        new EntradaPalavra("cogumelo", CategoriaPalavra.FOOD),
        
        // ────────────────────────────────────────
        //  TECNOLOGIA
        // ────────────────────────────────────────
        new EntradaPalavra("computador", CategoriaPalavra.TECHNOLOGY),
        new EntradaPalavra("teclado", CategoriaPalavra.TECHNOLOGY),
        new EntradaPalavra("internet", CategoriaPalavra.TECHNOLOGY),
        new EntradaPalavra("programa", CategoriaPalavra.TECHNOLOGY),
        new EntradaPalavra("algoritmo", CategoriaPalavra.TECHNOLOGY),
        new EntradaPalavra("processador", CategoriaPalavra.TECHNOLOGY),
        new EntradaPalavra("servidor", CategoriaPalavra.TECHNOLOGY),
        new EntradaPalavra("protocolo", CategoriaPalavra.TECHNOLOGY),
        new EntradaPalavra("compilador", CategoriaPalavra.TECHNOLOGY),
        new EntradaPalavra("interface", CategoriaPalavra.TECHNOLOGY),
        
        // ────────────────────────────────────────
        //  NATUREZA
        // ────────────────────────────────────────
        new EntradaPalavra("horizonte", CategoriaPalavra.NATURE),
        new EntradaPalavra("avalanche", CategoriaPalavra.NATURE),
        new EntradaPalavra("terremoto", CategoriaPalavra.NATURE),
        new EntradaPalavra("furacao", CategoriaPalavra.NATURE),
        new EntradaPalavra("vulcao", CategoriaPalavra.NATURE),
        new EntradaPalavra("ecossistema", CategoriaPalavra.NATURE),
        new EntradaPalavra("magnetismo", CategoriaPalavra.NATURE),
        new EntradaPalavra("radiacao", CategoriaPalavra.NATURE),
        new EntradaPalavra("fotossintese", CategoriaPalavra.NATURE),
        new EntradaPalavra("evaporacao", CategoriaPalavra.NATURE)
    };

    private static final Random RANDOM = new Random();

    /**
     * Devolve uma palavra aleatória (sem considerar dificuldade).
     * Mantém compatibilidade com código antigo.
     *
     * @return Uma EntradaPalavra aleatória.
     */
    public static EntradaPalavra getRandomWord() {
        return WORDS[RANDOM.nextInt(WORDS.length)];
    }

    /**
     * Devolve uma palavra aleatória baseada na dificuldade.
     * 
     * EASY: Palavras curtas (≤ 5 letras)
     * NORMAL: Palavras médias (6-10 letras)
     * HARD: Palavras longas (> 10 letras)
     *
     * @param difficulty Nível de dificuldade.
     * @return Uma EntradaPalavra que corresponde à dificuldade.
     */
    public static EntradaPalavra getRandomWordByDifficulty(Dificuldade difficulty) {
        List<EntradaPalavra> candidates = new ArrayList<>();
        
        for (EntradaPalavra entry : WORDS) {
            int len = entry.getLength();
            if (difficulty == Dificuldade.EASY && len <= 5) {
                candidates.add(entry);
            } else if (difficulty == Dificuldade.NORMAL && len > 5 && len <= 10) {
                candidates.add(entry);
            } else if (difficulty == Dificuldade.HARD && len > 10) {
                candidates.add(entry);
            }
        }
        
        // Se não há palavras nessa faixa (improvável), retornar uma aleatória
        if (candidates.isEmpty()) {
            return getRandomWord();
        }
        
        return candidates.get(RANDOM.nextInt(candidates.size()));
    }

    /**
     * Devolve uma dica baseada na categoria.
     * 
     * Três tipos de dicas disponíveis (aumentando em detalhe):
     * 1. "É um(a) [categoria]" - ex: "É um Animal"
     * 2. "Começa com [primeira letra]" - ex: "Começa com G"
     * 3. Ambas - ex: "É um Animal, começa com G"
     *
     * @param entry A palavra para a qual gerar dica.
     * @param hintLevel 1 (categoria), 2 (primeira letra), 3+ (ambas).
     * @return Uma string com a dica.
     */
    public static String getHint(EntradaPalavra entry, int hintLevel) {
        String category = entry.getCategory().getDisplayName();
        char firstLetter = entry.getWord().charAt(0);
        
        if (hintLevel == 1) {
            return "É um(a) " + category.toLowerCase();
        } else if (hintLevel == 2) {
            return "Começa com a letra " + firstLetter;
        } else {
            return "É um(a) " + category.toLowerCase() + " que começa com " + firstLetter;
        }
    }

    // Construtor privado: classe utilitária
    private BancoPalavras() {}
}
