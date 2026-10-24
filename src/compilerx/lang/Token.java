package compilerx.lang;

public record Token(Kind kind, String text, Object literal, int line, int column) {
    public enum Kind { WORD, NUMBER, STRING, CHARACTER, SYMBOL, END }
    public boolean is(String value) { return text.equals(value); }
}
