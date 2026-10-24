package compilerx.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static compilerx.lang.Ast.*;

public final class Parser {
    private static final Set<String> TYPES = Set.of("int", "long", "short", "float", "double", "char", "bool", "string", "void");
    private static final Set<String> RESERVED = Set.of("const", "int", "long", "short", "float", "double", "char", "bool", "string", "void", "if", "else", "for", "while", "do", "return", "break", "continue", "true", "false", "using", "namespace", "std", "cout", "cin", "endl", "class", "struct", "switch", "case", "default", "unsigned", "signed", "auto", "new", "delete", "sizeof", "static", "extern", "typedef", "enum", "template", "volatile", "nullptr");
    private static final Set<String> ASSIGNMENTS = Set.of("=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=");
    private static final Map<String, Integer> PRECEDENCE = Map.ofEntries(
        Map.entry("||", 1), Map.entry("&&", 2), Map.entry("|", 3), Map.entry("^", 4), Map.entry("&", 5),
        Map.entry("==", 6), Map.entry("!=", 6), Map.entry("<", 7), Map.entry("<=", 7), Map.entry(">", 7), Map.entry(">=", 7),
        Map.entry("<<", 8), Map.entry(">>", 8), Map.entry("+", 9), Map.entry("-", 9), Map.entry("*", 10), Map.entry("/", 10), Map.entry("%", 10));
    private final List<Token> tokens;
    private int index;

    public Parser(List<Token> tokens) { this.tokens = tokens; }

    public Program parse() {
        List<Declaration> globals = new ArrayList<>();
        List<Function> functions = new ArrayList<>();
        Token start = peek();
        while (peek().kind() != Token.Kind.END) {
            if (match("using")) {
                expect("namespace"); expect("std"); expect(";");
                continue;
            }
            DeclType type = declarationType();
            Token name = identifier();
            if (match("(")) {
                if (type.constant) throw error(name, "A const-qualified function return type is outside this subset.");
                List<Parameter> parameters = new ArrayList<>();
                if (check("void") && at(1).is(")")) advance();
                else if (!check(")")) {
                    do {
                        DeclType parameter = declarationType();
                        if (parameter.type == Type.VOID) throw error(peek(), "A parameter cannot have type void.");
                        parameters.add(new Parameter(parameter.type, identifier(), parameter.constant));
                    } while (match(","));
                }
                expect(")");
                if (check(";")) throw error(peek(), "Function prototypes are not supported; provide the function body.");
                functions.add(new Function(type.type, name, List.copyOf(parameters), block()));
            } else globals.add(declarationTail(type, name, true));
        }
        return new Program(List.copyOf(globals), List.copyOf(functions), start);
    }

    private Block block() {
        Token opening = expect("{");
        List<Stmt> statements = new ArrayList<>();
        while (!check("}") && peek().kind() != Token.Kind.END) statements.add(statement());
        expect("}");
        return new Block(List.copyOf(statements), opening);
    }

    private Stmt statement() {
        Token start = peek();
        if (check("{")) return block();
        if (match(";")) return new Empty(start);
        if (match("if")) {
            expect("("); Expr condition = expression(); expect(")");
            Stmt thenBranch = statement();
            return new If(condition, thenBranch, match("else") ? statement() : null, start);
        }
        if (match("while")) {
            expect("("); Expr condition = expression(); expect(")");
            return new While(condition, statement(), start);
        }
        if (match("do")) {
            Stmt body = statement();
            expect("while"); expect("("); Expr condition = expression(); expect(")"); expect(";");
            return new DoWhile(body, condition, start);
        }
        if (match("for")) {
            expect("(");
            Stmt initializer;
            if (match(";")) initializer = new Empty(start);
            else if (typeStart()) {
                DeclType type = declarationType();
                initializer = declarationTail(type, identifier(), true);
            } else {
                Expr value = expression(); expect(";");
                initializer = new Expression(value, value.token());
            }
            Expr condition = check(";") ? null : expression(); expect(";");
            Expr update = check(")") ? null : expression(); expect(")");
            return new For(initializer, condition, update, statement(), start);
        }
        if (match("return")) {
            Expr value = check(";") ? null : expression(); expect(";");
            return new Return(value, start);
        }
        if (match("break")) { expect(";"); return new Break(start); }
        if (match("continue")) { expect(";"); return new Continue(start); }
        if (qualified("cout")) {
            consumeQualified();
            List<Expr> values = new ArrayList<>();
            do { expect("<<"); values.add(binary(9)); } while (check("<<"));
            expect(";");
            return new Output(List.copyOf(values), start);
        }
        if (qualified("cin")) {
            consumeQualified();
            List<Token> names = new ArrayList<>();
            do { expect(">>"); names.add(identifier()); } while (check(">>"));
            expect(";");
            return new Input(List.copyOf(names), start);
        }
        if (typeStart()) {
            DeclType type = declarationType();
            return declarationTail(type, identifier(), true);
        }
        Expr value = expression(); expect(";");
        return new Expression(value, start);
    }

    private Declaration declarationTail(DeclType type, Token first, boolean semicolon) {
        if (type.type == Type.VOID) throw error(first, "A variable cannot have type void.");
        List<Binding> bindings = new ArrayList<>();
        Token name = first;
        do {
            Expr initializer = match("=") ? expression() : null;
            bindings.add(new Binding(name, initializer));
            if (!match(",")) break;
            name = identifier();
        } while (true);
        if (semicolon) expect(";");
        return new Declaration(type.type, type.constant, List.copyOf(bindings), first);
    }

    private Expr expression() {
        Expr value = conditional();
        if (ASSIGNMENTS.contains(peek().text())) {
            Token operator = advance();
            if (!(value instanceof Variable variable)) throw error(operator, "Assignment requires a variable on the left.");
            return new Assign(variable, operator, expression());
        }
        return value;
    }

    private Expr conditional() {
        Expr value = binary(1);
        if (match("?")) {
            Token question = previous();
            Expr whenTrue = expression(); expect(":");
            return new Conditional(value, whenTrue, expression(), question);
        }
        return value;
    }

    private Expr binary(int minimum) {
        Expr value = unary();
        while (PRECEDENCE.getOrDefault(peek().text(), 0) >= minimum) {
            Token operator = advance();
            Expr right = binary(PRECEDENCE.get(operator.text()) + 1);
            value = new Binary(value, operator, right);
        }
        return value;
    }

    private Expr unary() {
        if (check("+") || check("-") || check("!") || check("~") || check("++") || check("--")) {
            Token operator = advance();
            return new Unary(operator, unary(), false);
        }
        if (match("&")) return new Address(identifier());
        if (check("(") && (TYPES.contains(at(1).text()) || at(1).is("std") && at(2).is("::") && at(3).is("string"))) {
            Token start = advance();
            DeclType type = declarationType(); expect(")");
            return new Cast(type.type, unary(), start);
        }
        Expr value = primary();
        if (check("++") || check("--")) value = new Unary(advance(), value, true);
        return value;
    }

    private Expr primary() {
        Token token = peek();
        if (match("(")) { Expr value = expression(); expect(")"); return value; }
        if (token.kind() == Token.Kind.NUMBER) {
            advance(); Lexer.Numeric numeric = (Lexer.Numeric) token.literal();
            return new Literal(numeric.value(), numeric.type(), token);
        }
        if (token.kind() == Token.Kind.STRING) {
            advance(); StringBuilder text = new StringBuilder((String) token.literal());
            while (peek().kind() == Token.Kind.STRING) text.append(advance().literal());
            return new Literal(text.toString(), Type.STRING, token);
        }
        if (token.kind() == Token.Kind.CHARACTER) {
            advance(); return new Literal((long) ((String) token.literal()).charAt(0), Type.CHAR, token);
        }
        if (match("true") || match("false")) return new Literal(token.is("true") ? 1L : 0L, Type.BOOL, token);
        if (qualified("endl")) { consumeQualified(); return new Literal("\n", Type.STRING, token); }
        if (token.kind() == Token.Kind.WORD) {
            Token name;
            if (check("std") && at(1).is("::")) {
                advance(); advance(); name = identifier();
                if (!SemanticAnalyzer.BUILTINS.contains(name.text())) throw error(name, "Only supported standard-library functions may use the std:: qualifier here.");
            }
            else name = identifier();
            if (match("(")) {
                List<Expr> arguments = new ArrayList<>();
                if (!check(")")) do { arguments.add(expression()); } while (match(","));
                expect(")");
                return new Call(name, List.copyOf(arguments));
            }
            return new Variable(name);
        }
        throw error(token, "Expected an expression, found '" + token.text() + "'.");
    }

    private DeclType declarationType() {
        boolean constant = match("const");
        Token token = advance();
        if (token.is("std")) { expect("::"); token = expect("string"); }
        if (!TYPES.contains(token.text())) throw error(token, "Expected a supported type (int, long, short, float, double, char, bool, string, void).");
        Type type = Type.valueOf(token.text().toUpperCase(java.util.Locale.ROOT));
        if (type == Type.LONG) { match("long"); match("int"); }
        else if (type == Type.SHORT) match("int");
        constant |= match("const");
        return new DeclType(type, constant);
    }

    private boolean typeStart() { return check("const") || TYPES.contains(peek().text()) || qualified("string"); }
    private boolean qualified(String name) { return check(name) || check("std") && at(1).is("::") && at(2).is(name); }
    private Token consumeQualified() { if (check("std")) { advance(); expect("::"); } return advance(); }
    private Token identifier() {
        Token token = peek();
        if (token.kind() != Token.Kind.WORD || RESERVED.contains(token.text())) throw error(token, "Expected an identifier; '" + token.text() + "' is not supported here.");
        return advance();
    }
    private boolean check(String text) { return peek().is(text); }
    private boolean match(String text) { if (!check(text)) return false; advance(); return true; }
    private Token expect(String text) {
        if (!check(text)) throw error(peek(), "Expected '" + text + "', found '" + peek().text() + "'.");
        return advance();
    }
    private Token peek() { return at(0); }
    private Token at(int offset) { return tokens.get(Math.min(index + offset, tokens.size() - 1)); }
    private Token previous() { return tokens.get(index - 1); }
    private Token advance() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        Token token = peek();
        if (token.kind() != Token.Kind.END) index++;
        return token;
    }
    private Diagnostic error(Token token, String text) { return new Diagnostic("Syntax", token, text); }
    private record DeclType(Type type, boolean constant) {}
}
