package server;

/**
 * EntradaPalavra — Uma entrada no banco de palavras.
 * 
 * Combina palavra com categoria e metadados.
 */
public class EntradaPalavra {
    private final String word;
    private final CategoriaPalavra category;
    
    public EntradaPalavra(String word, CategoriaPalavra category) {
        this.word = word.toUpperCase();
        this.category = category;
    }
    
    public String getWord() {
        return word;
    }
    
    public CategoriaPalavra getCategory() {
        return category;
    }
    
    public int getLength() {
        return word.replaceAll("\\s+", "").length();  // Contar sem espaços
    }
    
    @Override
    public String toString() {
        return word + " (" + category.getDisplayName() + ")";
    }
}
