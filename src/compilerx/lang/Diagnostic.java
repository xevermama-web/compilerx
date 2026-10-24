package compilerx.lang;

public final class Diagnostic extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String phase;
    private final int line;
    private final int column;

    public Diagnostic(String phase, int line, int column, String message) {
        super(message);
        this.phase = phase;
        this.line = line;
        this.column = column;
    }

    public Diagnostic(String phase, Token token, String message) {
        this(phase, token.line(), token.column(), message);
    }

    public int line() { return line; }
    public int column() { return column; }
    public String phase() { return phase; }

    public String format() {
        return phase + " error at line " + line + ", column " + column + ": " + getMessage();
    }
}
