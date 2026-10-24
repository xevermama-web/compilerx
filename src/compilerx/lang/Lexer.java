package compilerx.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Lexer {
    private static final Set<String> HEADERS = Set.of("iostream", "string", "cmath", "math.h", "cstdio", "stdio.h", "cstdlib", "stdlib.h", "algorithm", "stdbool.h");
    private static final Pattern INCLUDE = Pattern.compile("#\\s*include\\s*<([^>]+)>\\s*(?://.*)?");
    private static final String[] OPERATORS = {"<<=", ">>=", "++", "--", "+=", "-=", "*=", "/=", "%=", "==", "!=", "<=", ">=", "&&", "||", "<<", ">>", "&=", "|=", "^=", "::"};
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int index;
    private int line = 1;
    private int column = 1;

    public Lexer(String source) {
        this.source = source.replace("\r\n", "\n").replace('\r', '\n');
    }

    public List<Token> scan() {
        if (source.length() > 200_000) throw new Diagnostic("Lexer", 1, 1, "Source exceeds the 200,000-character limit.");
        if (peek() == '\uFEFF') advance();
        while (!end()) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            char c = peek();
            if (Character.isWhitespace(c)) { advance(); continue; }
            int startLine = line, startColumn = column;
            if (c == '/' && next() == '/') {
                while (!end() && peek() != '\n') advance();
            } else if (c == '/' && next() == '*') {
                advance(); advance();
                while (!end() && !(peek() == '*' && next() == '/')) advance();
                if (end()) throw error(startLine, startColumn, "Unterminated block comment.");
                advance(); advance();
            } else if (c == '#') {
                int start = index;
                int lineStart = source.lastIndexOf('\n', Math.max(0, index - 1)) + 1;
                if (!source.substring(lineStart, index).isBlank()) throw error(line, column, "A directive must begin a line.");
                while (!end() && peek() != '\n') advance();
                Matcher match = INCLUDE.matcher(source.substring(start, index));
                if (!match.matches()) throw error(startLine, startColumn, "Only #include <supported-header> directives are supported.");
                if (!HEADERS.contains(match.group(1))) throw error(startLine, startColumn, "Unsupported header <" + match.group(1) + ">.");
            } else if (Character.isLetter(c) || c == '_') {
                int start = index;
                while (Character.isLetterOrDigit(peek()) || peek() == '_') advance();
                tokens.add(new Token(Token.Kind.WORD, source.substring(start, index), null, startLine, startColumn));
            } else if (Character.isDigit(c) || c == '.' && Character.isDigit(next())) {
                number(startLine, startColumn);
            } else if (c == '"' || c == '\'') {
                quoted(startLine, startColumn);
            } else {
                boolean found = false;
                for (String operator : OPERATORS) {
                    if (source.startsWith(operator, index)) {
                        for (int n = 0; n < operator.length(); n++) advance();
                        tokens.add(new Token(Token.Kind.SYMBOL, operator, null, startLine, startColumn));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    if ("+-*/%=<>!&|^~?:;,(){}".indexOf(c) < 0) throw error(line, column, "Unsupported character '" + c + "'.");
                    advance();
                    tokens.add(new Token(Token.Kind.SYMBOL, String.valueOf(c), null, startLine, startColumn));
                }
            }
        }
        tokens.add(new Token(Token.Kind.END, "<end>", null, line, column));
        return List.copyOf(tokens);
    }

    private void number(int startLine, int startColumn) {
        int start = index;
        boolean floating = false;
        if (peek() == '0' && (next() == 'x' || next() == 'X')) {
            advance(); advance();
            int digits = index;
            while (Character.digit(peek(), 16) >= 0) advance();
            if (digits == index) throw error(startLine, startColumn, "Expected hexadecimal digits.");
        } else {
            while (Character.isDigit(peek())) advance();
            if (peek() == '.') {
                floating = true; advance();
                while (Character.isDigit(peek())) advance();
            }
            if (peek() == 'e' || peek() == 'E') {
                floating = true; advance();
                if (peek() == '+' || peek() == '-') advance();
                int digits = index;
                while (Character.isDigit(peek())) advance();
                if (digits == index) throw error(startLine, startColumn, "Expected exponent digits.");
            }
        }
        int numericEnd = index;
        Type type = floating ? Type.DOUBLE : Type.INT;
        if (peek() == 'f' || peek() == 'F') {
            if (!floating) throw error(startLine, startColumn, "Use a decimal point for a float literal, for example 2.0f.");
            type = Type.FLOAT; advance();
        } else if (peek() == 'l' || peek() == 'L') {
            if (floating) throw error(startLine, startColumn, "Long double is outside this subset.");
            char suffix = advance();
            if (peek() == suffix) advance();
            type = Type.LONG;
        }
        if (Character.isLetterOrDigit(peek()) || peek() == '_' || peek() == '.') throw error(startLine, startColumn, "Invalid numeric literal.");
        String numeric = source.substring(start, numericEnd);
        Object value;
        try {
            if (type.floating()) {
                double d = Double.parseDouble(numeric);
                if (!Double.isFinite(d) || type == Type.FLOAT && !Float.isFinite((float) d)) throw new NumberFormatException();
                value = d;
            } else {
                int radix = numeric.startsWith("0x") || numeric.startsWith("0X") ? 16 : numeric.length() > 1 && numeric.charAt(0) == '0' ? 8 : 10;
                long n = Long.parseLong(radix == 16 ? numeric.substring(2) : numeric, radix);
                if (type == Type.INT && n > Integer.MAX_VALUE) type = Type.LONG;
                value = n;
            }
        } catch (NumberFormatException exception) {
            throw error(startLine, startColumn, "Invalid or out-of-range numeric literal.");
        }
        tokens.add(new Token(Token.Kind.NUMBER, source.substring(start, index), new Numeric(value, type), startLine, startColumn));
    }

    private void quoted(int startLine, int startColumn) {
        int start = index;
        char quote = advance();
        StringBuilder value = new StringBuilder();
        while (!end() && peek() != quote) {
            char c = advance();
            if (c == '\n') throw error(startLine, startColumn, "Unterminated literal.");
            if (c == '\\') {
                if (end()) throw error(startLine, startColumn, "Unterminated escape sequence.");
                char escape = advance();
                c = switch (escape) {
                    case 'n' -> '\n'; case 't' -> '\t'; case 'r' -> '\r'; case '0' -> '\0';
                    case 'b' -> '\b'; case 'f' -> '\f'; case 'v' -> '\u000B';
                    case '\\' -> '\\'; case '\'' -> '\''; case '"' -> '"'; case '?' -> '?';
                    default -> throw error(line, column - 1, "Unsupported escape sequence: \\" + escape);
                };
            }
            value.append(c);
        }
        if (end()) throw error(startLine, startColumn, "Unterminated literal.");
        advance();
        if (quote == '\'' && value.length() != 1) throw error(startLine, startColumn, "A character literal must contain exactly one character.");
        tokens.add(new Token(quote == '\'' ? Token.Kind.CHARACTER : Token.Kind.STRING, source.substring(start, index), value.toString(), startLine, startColumn));
    }

    private boolean end() { return index >= source.length(); }
    private char peek() { return end() ? '\0' : source.charAt(index); }
    private char next() { return index + 1 >= source.length() ? '\0' : source.charAt(index + 1); }
    private char advance() {
        char c = source.charAt(index++);
        if (c == '\n') { line++; column = 1; } else column++;
        return c;
    }
    private Diagnostic error(int row, int col, String message) { return new Diagnostic("Lexer", row, col, message); }
    public record Numeric(Object value, Type type) {}
}
