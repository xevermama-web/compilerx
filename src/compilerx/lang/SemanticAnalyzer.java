package compilerx.lang;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import static compilerx.lang.Ast.*;

public final class SemanticAnalyzer {
    public static final Set<String> BUILTINS = Set.of("pow", "sqrt", "abs", "floor", "ceil", "round", "sin", "cos", "tan", "log", "exp", "min", "max", "printf", "scanf", "puts");
    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();
    private final Map<String, Function> functions = new HashMap<>();
    private final Map<Expr, Type> types = new IdentityHashMap<>();
    private Function currentFunction;
    private int loopDepth;

    public CheckedProgram analyze(Program program) {
        scopes.push(new HashMap<>());
        for (Function function : program.functions()) {
            String name = function.name().text();
            if (BUILTINS.contains(name) || functions.putIfAbsent(name, function) != null) fail(function.name(), "Function '" + name + "' is already defined or reserved.");
        }
        Function main = functions.get("main");
        if (main == null) fail(program.token(), "A program requires int main().");
        if (main.type() != Type.INT || !main.parameters().isEmpty()) fail(main.name(), "Use int main() or int main(void), without command-line parameters.");
        for (Declaration declaration : program.globals()) statement(declaration);
        for (Function function : program.functions()) {
            currentFunction = function;
            loopDepth = 0;
            scopes.push(new HashMap<>());
            for (Parameter parameter : function.parameters()) declare(parameter.name(), parameter.type(), parameter.constant());
            for (Stmt stmt : function.body().statements()) statement(stmt);
            scopes.pop();
        }
        return new CheckedProgram(program, Collections.unmodifiableMap(types));
    }

    private void statement(Stmt stmt) {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        if (stmt instanceof Block block) {
            scopes.push(new HashMap<>());
            for (Stmt child : block.statements()) statement(child);
            scopes.pop();
        } else if (stmt instanceof Declaration declaration) {
            for (Binding binding : declaration.bindings()) {
                declare(binding.name(), declaration.type(), declaration.constant());
                if (binding.initializer() != null) compatible(declaration.type(), expression(binding.initializer()), binding.name());
                else if (declaration.constant()) fail(binding.name(), "A const variable requires an initializer.");
            }
        } else if (stmt instanceof Expression expression) {
            expression(expression.value());
        } else if (stmt instanceof If branch) {
            condition(branch.condition()); scoped(branch.thenBranch());
            if (branch.elseBranch() != null) scoped(branch.elseBranch());
        } else if (stmt instanceof While loop) {
            condition(loop.condition()); loopDepth++; scoped(loop.body()); loopDepth--;
        } else if (stmt instanceof DoWhile loop) {
            loopDepth++; scoped(loop.body()); loopDepth--; condition(loop.condition());
        } else if (stmt instanceof For loop) {
            scopes.push(new HashMap<>());
            statement(loop.initializer());
            if (loop.condition() != null) condition(loop.condition());
            if (loop.update() != null) expression(loop.update());
            loopDepth++; scoped(loop.body()); loopDepth--;
            scopes.pop();
        } else if (stmt instanceof Return result) {
            if (currentFunction == null) fail(result.token(), "return must be inside a function.");
            if (currentFunction.type() == Type.VOID) {
                if (result.value() != null) fail(result.token(), "A void function cannot return a value.");
            } else {
                if (result.value() == null) fail(result.token(), "This function must return a " + currentFunction.type().label() + " value.");
                compatible(currentFunction.type(), expression(result.value()), result.token());
            }
        } else if (stmt instanceof Break || stmt instanceof Continue) {
            if (loopDepth == 0) fail(stmt.token(), stmt.token().text() + " is only valid inside a loop.");
        } else if (stmt instanceof Output output) {
            for (Expr value : output.values()) if (expression(value) == Type.VOID) fail(value.token(), "Cannot print a void expression.");
        } else if (stmt instanceof Input input) {
            for (Token name : input.names()) writable(name);
        }
    }

    private void scoped(Stmt stmt) {
        scopes.push(new HashMap<>());
        statement(stmt);
        scopes.pop();
    }

    private Type expression(Expr expr) {
        Type result;
        if (expr instanceof Literal literal) result = literal.type();
        else if (expr instanceof Variable variable) result = resolve(variable.token()).type;
        else if (expr instanceof Binary binary) result = binaryType(expression(binary.left()), binary.operator(), expression(binary.right()));
        else if (expr instanceof Unary unary) {
            Type type = expression(unary.operand());
            String operator = unary.operator().text();
            if (operator.equals("++") || operator.equals("--")) {
                if (!(unary.operand() instanceof Variable)) fail(unary.token(), "Increment and decrement require a variable.");
                writable(unary.operand().token());
                if (!type.numeric() || type == Type.BOOL) fail(unary.token(), "Increment and decrement require a non-bool numeric variable.");
                result = type;
            } else {
                numeric(type, unary.token());
                if (operator.equals("~") && !type.integral()) fail(unary.token(), "Bitwise complement requires an integer.");
                result = operator.equals("!") ? Type.BOOL : Type.promoted(type, Type.INT);
            }
        } else if (expr instanceof Assign assign) {
            Type target = writable(assign.target().token()).type;
            Type value = expression(assign.value());
            String operator = assign.operator().text();
            if (!operator.equals("=")) value = binaryType(target, new Token(Token.Kind.SYMBOL, operator.substring(0, operator.length() - 1), null, assign.token().line(), assign.token().column()), value);
            compatible(target, value, assign.token());
            result = target;
        } else if (expr instanceof Conditional conditional) {
            condition(conditional.condition());
            Type a = expression(conditional.whenTrue()), b = expression(conditional.whenFalse());
            if (a == b && a != Type.VOID) result = a;
            else if (a.numeric() && b.numeric()) result = Type.promoted(a, b);
            else { fail(conditional.token(), "Conditional branches must have compatible non-void types."); return Type.VOID; }
        } else if (expr instanceof Cast cast) {
            Type value = expression(cast.value());
            if (cast.type() == Type.VOID) fail(cast.token(), "Casting to void is outside this subset.");
            compatible(cast.type(), value, cast.token());
            result = cast.type();
        } else if (expr instanceof Call call) result = call(call);
        else { fail(expr.token(), "An address (&variable) is only allowed as a scanf destination."); return Type.VOID; }
        types.put(expr, result);
        return result;
    }

    private Type call(Call call) {
        String name = call.name().text();
        if (name.equals("printf") || name.equals("scanf")) {
            if (call.arguments().isEmpty()) fail(call.token(), name + " requires a format string.");
            if (expression(call.arguments().get(0)) != Type.STRING) fail(call.token(), name + " requires a string as its first argument.");
            for (int i = 1; i < call.arguments().size(); i++) {
                Expr argument = call.arguments().get(i);
                if (name.equals("scanf")) {
                    if (!(argument instanceof Address)) fail(argument.token(), "scanf destinations must use &variable.");
                    Type type = writable(argument.token()).type;
                    if (!type.numeric() || type == Type.BOOL) fail(argument.token(), "scanf supports numeric and char destinations; use cin for string/bool input.");
                } else if (expression(argument) == Type.VOID) fail(argument.token(), "printf cannot format void.");
            }
            return Type.INT;
        }
        if (name.equals("puts")) {
            arity(call, 1);
            if (expression(call.arguments().get(0)) != Type.STRING) fail(call.token(), "puts requires a string.");
            return Type.INT;
        }
        if (BUILTINS.contains(name)) {
            int count = name.equals("pow") || name.equals("min") || name.equals("max") ? 2 : 1;
            arity(call, count);
            Type result = Type.INT;
            for (Expr argument : call.arguments()) {
                Type type = expression(argument); numeric(type, argument.token());
                result = Type.promoted(result, type);
            }
            if ((name.equals("min") || name.equals("max")) && types.get(call.arguments().get(0)) == types.get(call.arguments().get(1))) return types.get(call.arguments().get(0));
            return name.equals("min") || name.equals("max") || name.equals("abs") ? result : Type.DOUBLE;
        }
        Function function = functions.get(name);
        if (function == null) fail(call.name(), "Unknown function '" + name + "'.");
        if (name.equals("main")) fail(call.name(), "Calling main is not supported.");
        arity(call, function.parameters().size());
        for (int i = 0; i < call.arguments().size(); i++) compatible(function.parameters().get(i).type(), expression(call.arguments().get(i)), call.arguments().get(i).token());
        return function.type();
    }

    private Type binaryType(Type left, Token operator, Type right) {
        String op = operator.text();
        if (op.equals("+") && left == Type.STRING && right == Type.STRING) return Type.STRING;
        if (Set.of("==", "!=", "<", "<=", ">", ">=").contains(op)) {
            if (!(left.numeric() && right.numeric()) && !(left == Type.STRING && right == Type.STRING)) fail(operator, "Comparison requires compatible numeric or string operands.");
            return Type.BOOL;
        }
        numeric(left, operator); numeric(right, operator);
        if (Set.of("%", "&", "|", "^", "<<", ">>").contains(op) && !(left.integral() && right.integral())) fail(operator, "Operator '" + op + "' requires integral operands.");
        if (op.equals("&&") || op.equals("||")) return Type.BOOL;
        if (op.equals("<<") || op.equals(">>")) return Type.promoted(left, Type.INT);
        return Type.promoted(left, right);
    }

    private void arity(Call call, int count) {
        if (call.arguments().size() != count) fail(call.token(), "Function '" + call.name().text() + "' expects " + count + " argument(s), received " + call.arguments().size() + ".");
    }
    private void condition(Expr expr) { numeric(expression(expr), expr.token()); }
    private void numeric(Type type, Token token) { if (!type.numeric()) fail(token, "Expected a numeric or bool value, received " + type.label() + "."); }
    private void compatible(Type target, Type value, Token token) {
        if (!(target.numeric() && value.numeric()) && !(target == Type.STRING && value == Type.STRING)) fail(token, "Cannot convert " + value.label() + " to " + target.label() + ".");
    }
    private void declare(Token name, Type type, boolean constant) {
        if (functions.containsKey(name.text()) || BUILTINS.contains(name.text())) fail(name, "The name '" + name.text() + "' is reserved for a function.");
        if (scopes.peek().putIfAbsent(name.text(), new Symbol(type, constant)) != null) fail(name, "Variable '" + name.text() + "' is already declared in this scope.");
    }
    private Symbol writable(Token name) {
        Symbol symbol = resolve(name);
        if (symbol.constant) fail(name, "Cannot modify const variable '" + name.text() + "'.");
        return symbol;
    }
    private Symbol resolve(Token name) {
        for (Map<String, Symbol> scope : scopes) if (scope.containsKey(name.text())) return scope.get(name.text());
        fail(name, "Undeclared variable '" + name.text() + "'.");
        return null;
    }
    private void fail(Token token, String message) { throw new Diagnostic("Semantic", token, message); }
    private record Symbol(Type type, boolean constant) {}
    public record CheckedProgram(Program program, Map<Expr, Type> expressionTypes) {}
}
