package compilerx.runtime;

import compilerx.lang.SemanticAnalyzer;
import compilerx.lang.Token;
import compilerx.lang.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import static compilerx.lang.Ast.*;

public final class Interpreter {
    public static final long STEP_LIMIT = 1_000_000;
    public static final int OUTPUT_LIMIT = 200_000;
    private final SemanticAnalyzer.CheckedProgram checked;
    private final ExecutionIO io;
    private final InputCursor input;
    private final Map<String, Function> functions = new HashMap<>();
    private final Environment globals = new Environment(null);
    private Environment environment = globals;
    private long steps;
    private int outputSize;
    private int depth;

    public Interpreter(SemanticAnalyzer.CheckedProgram checked, ExecutionIO io) {
        this.checked = checked;
        this.io = io;
        input = new InputCursor(io);
        for (Function function : checked.program().functions()) functions.put(function.name().text(), function);
    }

    public int execute() {
        for (Declaration declaration : checked.program().globals()) declare(declaration, true);
        return (int) invoke(functions.get("main"), List.of(), checked.program().token()).integer();
    }

    private void tick(Token token) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
        if (++steps > STEP_LIMIT) throw Value.error(token, "Execution stopped after 1,000,000 steps. Check for an infinite loop.");
    }

    private void execute(Stmt stmt) {
        tick(stmt.token());
        if (stmt instanceof Block block) {
            Environment old = environment;
            environment = new Environment(old);
            try { for (Stmt child : block.statements()) execute(child); }
            finally { environment = old; }
        } else if (stmt instanceof Declaration declaration) declare(declaration, false);
        else if (stmt instanceof Expression expression) evaluate(expression.value());
        else if (stmt instanceof If branch) {
            if (evaluate(branch.condition()).truth()) scoped(branch.thenBranch());
            else if (branch.elseBranch() != null) scoped(branch.elseBranch());
        } else if (stmt instanceof While loop) {
            while (evaluate(loop.condition()).truth()) {
                try { scoped(loop.body()); } catch (ContinueSignal ignored) {} catch (BreakSignal ignored) { break; }
            }
        } else if (stmt instanceof DoWhile loop) {
            do {
                try { scoped(loop.body()); } catch (ContinueSignal ignored) {} catch (BreakSignal ignored) { break; }
            } while (evaluate(loop.condition()).truth());
        } else if (stmt instanceof For loop) {
            Environment old = environment;
            environment = new Environment(old);
            try {
                execute(loop.initializer());
                while (loop.condition() == null || evaluate(loop.condition()).truth()) {
                    tick(loop.token());
                    try { scoped(loop.body()); } catch (ContinueSignal ignored) {} catch (BreakSignal ignored) { break; }
                    if (loop.update() != null) evaluate(loop.update());
                }
            } finally { environment = old; }
        } else if (stmt instanceof Return result) throw new ReturnSignal(result.value() == null ? Value.VOID : evaluate(result.value()));
        else if (stmt instanceof Break) throw new BreakSignal();
        else if (stmt instanceof Continue) throw new ContinueSignal();
        else if (stmt instanceof Output output) {
            for (Expr value : output.values()) write(evaluate(value).display(), value.token());
        } else if (stmt instanceof Input read) {
            for (Token name : read.names()) {
                Environment.Cell cell = environment.resolve(name);
                cell.set(input.read(cell.type, name, true), name);
            }
        }
    }

    private void scoped(Stmt stmt) {
        Environment old = environment;
        environment = new Environment(old);
        try { execute(stmt); } finally { environment = old; }
    }

    private void declare(Declaration declaration, boolean global) {
        for (Binding binding : declaration.bindings()) {
            Environment.Cell cell = environment.define(binding.name(), declaration.type(), declaration.constant());
            if (global || declaration.type() == Type.STRING) cell.initialize(declaration.type() == Type.STRING ? new Value(Type.STRING, "") : Value.integer(0), binding.name());
            if (binding.initializer() != null) cell.initialize(evaluate(binding.initializer()), binding.name());
        }
    }

    private Value evaluate(Expr expr) {
        tick(expr.token());
        if (expr instanceof Literal literal) return new Value(literal.type(), literal.value()).convert(literal.type(), literal.token());
        if (expr instanceof Variable variable) return environment.resolve(variable.token()).get(variable.token());
        if (expr instanceof Binary binary) {
            Value left = evaluate(binary.left());
            if (binary.operator().is("&&") && !left.truth()) return Value.bool(false);
            if (binary.operator().is("||") && left.truth()) return Value.bool(true);
            return binary(left, binary.operator(), evaluate(binary.right()));
        }
        if (expr instanceof Unary unary) {
            String operator = unary.operator().text();
            Value value = evaluate(unary.operand());
            if (operator.equals("++") || operator.equals("--")) {
                Token name = unary.operand().token();
                Value next = binary(value, operator(unary.token(), operator.equals("++") ? "+" : "-"), Value.integer(1));
                Value stored = environment.resolve(name).set(next, name);
                return unary.postfix() ? value : stored;
            }
            if (operator.equals("!")) return Value.bool(!value.truth());
            Type type = checked.expressionTypes().get(expr);
            if (operator.equals("+")) return value.convert(type, unary.token());
            if (operator.equals("~")) return new Value(Type.LONG, ~value.integer()).convert(type, unary.token());
            if (type.floating()) return new Value(Type.DOUBLE, -value.number()).convert(type, unary.token());
            return new Value(Type.LONG, -value.integer()).convert(type, unary.token());
        }
        if (expr instanceof Assign assign) {
            Environment.Cell cell = environment.resolve(assign.target().token());
            String op = assign.operator().text();
            Value value = evaluate(assign.value());
            if (!op.equals("=")) value = binary(cell.get(assign.target().token()), operator(assign.token(), op.substring(0, op.length() - 1)), value);
            return cell.set(value, assign.target().token());
        }
        if (expr instanceof Conditional conditional) return evaluate(evaluate(conditional.condition()).truth() ? conditional.whenTrue() : conditional.whenFalse()).convert(checked.expressionTypes().get(expr), expr.token());
        if (expr instanceof Cast cast) return evaluate(cast.value()).convert(cast.type(), cast.token());
        if (expr instanceof Call call) return call(call);
        throw Value.error(expr.token(), "An address cannot be evaluated as a value.");
    }

    private Value binary(Value left, Token token, Value right) {
        String op = token.text();
        if (op.equals("&&")) return Value.bool(left.truth() && right.truth());
        if (op.equals("||")) return Value.bool(left.truth() || right.truth());
        if (left.type() == Type.STRING && right.type() == Type.STRING) {
            String a = (String) left.raw(), b = (String) right.raw();
            if (op.equals("+")) {
                if (a.length() + b.length() > OUTPUT_LIMIT) throw Value.error(token, "String exceeds the 200,000-character limit.");
                return new Value(Type.STRING, a + b);
            }
            return comparison(a.compareTo(b), op);
        }
        Type type = Type.promoted(left.type(), right.type());
        if (Set.of("==", "!=", "<", "<=", ">", ">=").contains(op)) {
            Value a = left.convert(type, token), b = right.convert(type, token);
            int order = type.integral() ? Long.compare(a.integer(), b.integer()) : a.number() == b.number() ? 0 : Double.compare(a.number(), b.number());
            return comparison(order, op);
        }
        if (op.equals("<<") || op.equals(">>")) {
            type = Type.promoted(left.type(), Type.INT);
            long distance = right.integer();
            if (distance < 0 || distance >= (type == Type.LONG ? 64 : 32)) throw Value.error(token, "Shift count must be between 0 and " + (type == Type.LONG ? 63 : 31) + ".");
            long a = left.convert(type, token).integer();
            return new Value(Type.LONG, op.equals("<<") ? a << distance : a >> distance).convert(type, token);
        }
        if ((op.equals("/") || op.equals("%")) && right.number() == 0.0) throw Value.error(token, "Division or modulus by zero.");
        if (type.floating()) {
            double a = left.convert(type, token).number(), b = right.convert(type, token).number();
            double result = switch (op) {
                case "+" -> a + b; case "-" -> a - b; case "*" -> a * b; case "/" -> a / b;
                default -> throw Value.error(token, "Invalid floating-point operator.");
            };
            return new Value(Type.DOUBLE, result).convert(type, token);
        }
        long a = left.integer(), b = right.integer();
        long result = switch (op) {
            case "+" -> a + b; case "-" -> a - b; case "*" -> a * b; case "/" -> a / b; case "%" -> a % b;
            case "&" -> a & b; case "|" -> a | b; case "^" -> a ^ b;
            default -> throw Value.error(token, "Unsupported operator '" + op + "'.");
        };
        return new Value(Type.LONG, result).convert(type, token);
    }

    private Value comparison(int order, String op) {
        return Value.bool(switch (op) {
            case "==" -> order == 0; case "!=" -> order != 0; case "<" -> order < 0;
            case "<=" -> order <= 0; case ">" -> order > 0; case ">=" -> order >= 0;
            default -> false;
        });
    }

    private Value call(Call call) {
        String name = call.name().text();
        if (name.equals("scanf")) return scan(call);
        List<Value> arguments = new ArrayList<>();
        for (Expr argument : call.arguments()) arguments.add(evaluate(argument));
        if (name.equals("printf")) {
            List<Formats.Part> parts = Formats.parse((String) arguments.get(0).raw(), false, call.token());
            long count = parts.stream().filter(part -> part.literal() == null).count();
            if (count != arguments.size() - 1) throw Value.error(call.token(), "printf format requires " + count + " value(s).");
            StringBuilder result = new StringBuilder();
            int index = 1;
            for (Formats.Part part : parts) {
                result.append(part.literal() == null ? Formats.print(part, arguments.get(index++), call.token()) : part.literal());
                if (result.length() > OUTPUT_LIMIT) throw Value.error(call.token(), "Formatted output is too long.");
            }
            write(result.toString(), call.token());
            return Value.integer(result.length());
        }
        if (name.equals("puts")) {
            write((String) arguments.get(0).raw() + "\n", call.token());
            return Value.integer(0);
        }
        if (!SemanticAnalyzer.BUILTINS.contains(name)) return invoke(functions.get(name), arguments, call.token());
        Value a = arguments.get(0);
        Type resultType = checked.expressionTypes().get(call);
        if (name.equals("min") || name.equals("max")) {
            Value b = arguments.get(1);
            boolean less = binary(a, operator(call.token(), "<"), b).truth();
            return (name.equals("min") ? less ? a : b : less ? b : a).convert(resultType, call.token());
        }
        if (name.equals("abs") && a.type().integral()) {
            if (a.integer() == Long.MIN_VALUE || resultType == Type.INT && a.integer() == Integer.MIN_VALUE) throw Value.error(call.token(), "abs result is outside the signed range.");
            return new Value(Type.LONG, Math.abs(a.integer())).convert(resultType, call.token());
        }
        double x = a.number();
        double result = switch (name) {
            case "pow" -> Math.pow(x, arguments.get(1).number()); case "sqrt" -> Math.sqrt(x); case "abs" -> Math.abs(x);
            case "floor" -> Math.floor(x); case "ceil" -> Math.ceil(x); case "round" -> Math.copySign(Math.floor(Math.abs(x) + 0.5), x);
            case "sin" -> Math.sin(x); case "cos" -> Math.cos(x); case "tan" -> Math.tan(x); case "log" -> Math.log(x); case "exp" -> Math.exp(x);
            default -> throw Value.error(call.token(), "Unknown mathematical function.");
        };
        return new Value(Type.DOUBLE, result).convert(resultType, call.token());
    }

    private Value scan(Call call) {
        String format = (String) evaluate(call.arguments().get(0)).raw();
        List<Formats.Part> parts = Formats.parse(format, true, call.token());
        long count = parts.stream().filter(part -> part.literal() == null).count();
        if (count != call.arguments().size() - 1) throw Value.error(call.token(), "scanf format requires " + count + " destination(s).");
        int argument = 1;
        for (Formats.Part part : parts) {
            if (part.literal() == null) {
                Token name = call.arguments().get(argument++).token();
                if (environment.resolve(name).type != Formats.inputType(part)) throw Value.error(name, "scanf format does not match destination type; expected " + Formats.inputType(part).label() + ".");
            } else if (!part.literal().isBlank()) throw Value.error(call.token(), "This scanf subset accepts whitespace-separated conversions only, without literal separators.");
        }
        argument = 1;
        for (Formats.Part part : parts) {
            if (part.literal() != null) { if (!part.literal().isEmpty()) input.whitespace(); continue; }
            Token name = call.arguments().get(argument++).token();
            Environment.Cell cell = environment.resolve(name);
            cell.set(input.read(cell.type, name, part.kind() != 'c'), name);
        }
        return Value.integer(argument - 1);
    }

    private Value invoke(Function function, List<Value> arguments, Token token) {
        tick(token);
        if (++depth > 128) throw Value.error(token, "Function call depth exceeded 128.");
        Environment previous = environment;
        environment = new Environment(globals);
        try {
            for (int i = 0; i < arguments.size(); i++) {
                Parameter parameter = function.parameters().get(i);
                environment.define(parameter.name(), parameter.type(), parameter.constant()).initialize(arguments.get(i), parameter.name());
            }
            try { for (Stmt stmt : function.body().statements()) execute(stmt); }
            catch (ReturnSignal result) { return result.value.convert(function.type(), token); }
            if (function.type() == Type.VOID) return Value.VOID;
            if (function.name().is("main")) return Value.integer(0);
            throw Value.error(function.name(), "Function '" + function.name().text() + "' ended without returning a value.");
        } finally { environment = previous; depth--; }
    }

    private void write(String text, Token token) {
        if (outputSize + text.length() > OUTPUT_LIMIT) throw Value.error(token, "Output exceeded the 200,000-character limit.");
        outputSize += text.length();
        io.write(text);
    }
    private Token operator(Token position, String text) { return new Token(Token.Kind.SYMBOL, text, null, position.line(), position.column()); }
    private static final class ReturnSignal extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final Value value;
        ReturnSignal(Value value) { super(null, null, false, false); this.value = value; }
    }
    private static final class BreakSignal extends RuntimeException {
        private static final long serialVersionUID = 1L;
        BreakSignal() { super(null, null, false, false); }
    }
    private static final class ContinueSignal extends RuntimeException {
        private static final long serialVersionUID = 1L;
        ContinueSignal() { super(null, null, false, false); }
    }
}
