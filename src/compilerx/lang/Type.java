package compilerx.lang;

public enum Type {
    INT, LONG, SHORT, FLOAT, DOUBLE, CHAR, BOOL, STRING, VOID;

    public boolean numeric() { return this != STRING && this != VOID; }
    public boolean integral() { return numeric() && this != FLOAT && this != DOUBLE; }
    public boolean floating() { return this == FLOAT || this == DOUBLE; }

    public static Type promoted(Type a, Type b) {
        if (a == DOUBLE || b == DOUBLE) return DOUBLE;
        if (a == FLOAT || b == FLOAT) return FLOAT;
        if (a == LONG || b == LONG) return LONG;
        return INT;
    }

    public String label() { return name().toLowerCase(java.util.Locale.ROOT); }
}
