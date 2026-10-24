package compilerx.runtime;

import compilerx.lang.Token;
import compilerx.lang.Type;
import java.util.HashMap;
import java.util.Map;

final class Environment {
    private final Environment parent;
    private final Map<String, Cell> values = new HashMap<>();

    Environment(Environment parent) { this.parent = parent; }
    Cell define(Token name, Type type, boolean constant) {
        Cell cell = new Cell(type, constant);
        values.put(name.text(), cell);
        return cell;
    }
    Cell resolve(Token name) {
        Cell cell = values.get(name.text());
        if (cell != null) return cell;
        if (parent != null) return parent.resolve(name);
        throw Value.error(name, "Variable '" + name.text() + "' is not available yet.");
    }

    static final class Cell {
        final Type type;
        final boolean constant;
        private Value value;
        Cell(Type type, boolean constant) { this.type = type; this.constant = constant; }
        Value get(Token token) {
            if (value == null) throw Value.error(token, "Variable '" + token.text() + "' is used before initialization.");
            return value;
        }
        void initialize(Value initial, Token token) { value = initial.convert(type, token); }
        Value set(Value next, Token token) {
            if (constant) throw Value.error(token, "Cannot modify a const variable.");
            value = next.convert(type, token);
            return value;
        }
    }
}
