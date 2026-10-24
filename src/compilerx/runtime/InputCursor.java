package compilerx.runtime;

import compilerx.lang.Token;
import compilerx.lang.Type;
import java.util.concurrent.CancellationException;

final class InputCursor {
    private final ExecutionIO io;
    private String buffer = "";
    private int index;
    private boolean eof;

    InputCursor(ExecutionIO io) { this.io = io; }

    int peek() {
        while (index == buffer.length() && !eof) {
            try {
                String line = io.readLine();
                if (line == null) { eof = true; return -1; }
                buffer = line + "\n"; index = 0;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CancellationException();
            }
        }
        return eof ? -1 : buffer.charAt(index);
    }

    int take() { int value = peek(); if (value != -1) index++; return value; }
    void whitespace() { while (peek() != -1 && Character.isWhitespace(peek())) take(); }

    Value read(Type type, Token token, boolean skipWhitespace) {
        if (skipWhitespace) whitespace();
        if (peek() == -1) throw Value.error(token, "Input ended before a value was supplied for '" + token.text() + "'.");
        if (type == Type.CHAR) return new Value(Type.CHAR, (long) take());
        StringBuilder text = new StringBuilder();
        while (peek() != -1 && !Character.isWhitespace(peek())) {
            text.append((char) take());
            if (text.length() > 50_000) throw Value.error(token, "Input token is too long.");
        }
        String input = text.toString();
        if (type == Type.STRING) return new Value(Type.STRING, input);
        try {
            if (type == Type.BOOL) {
                if (!input.equals("0") && !input.equals("1")) throw new NumberFormatException();
                return Value.bool(input.equals("1"));
            }
            if (type.integral()) {
                if (!input.matches("[+-]?[0-9]+")) throw new NumberFormatException();
                long n = Long.parseLong(input);
                if (type == Type.INT && (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE) || type == Type.SHORT && (n < Short.MIN_VALUE || n > Short.MAX_VALUE)) throw new NumberFormatException();
                return new Value(Type.LONG, n).convert(type, token);
            }
            if (!input.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) throw new NumberFormatException();
            double n = Double.parseDouble(input);
            if (!Double.isFinite(n)) throw new NumberFormatException();
            return new Value(Type.DOUBLE, n).convert(type, token);
        } catch (NumberFormatException exception) {
            throw Value.error(token, "Invalid " + type.label() + " input '" + input + "'.");
        }
    }
}
