package server;

/**
 * Enum CategoriaPalavra — Categorias de palavras.
 * 
 * Facilita organização e dicas relacionadas.
 */
public enum CategoriaPalavra {
    ANIMAL("Animal"),
    FRUIT("Fruta"),
    PROFESSION("Profissão"),
    COUNTRY("País"),
    CITY("Cidade"),
    SPORT("Desporto"),
    INSTRUMENT("Instrumento"),
    FOOD("Comida"),
    TECHNOLOGY("Tecnologia"),
    NATURE("Natureza");

    private final String displayName;

    CategoriaPalavra(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
