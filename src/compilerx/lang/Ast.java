package compilerx.lang;

import java.util.List;

public final class Ast {
    private Ast() {}

    public interface Node { Token token(); }
    public interface Stmt extends Node {}
    public interface Expr extends Node {}

    public record Program(List<Declaration> globals, List<Function> functions, Token token) implements Node {}
    public record Function(Type type, Token name, List<Parameter> parameters, Block body) implements Node {
        public Token token() { return name; }
    }
    public record Parameter(Type type, Token name, boolean constant) {}
    public record Binding(Token name, Expr initializer) {}
    public record Block(List<Stmt> statements, Token token) implements Stmt {}
    public record Declaration(Type type, boolean constant, List<Binding> bindings, Token token) implements Stmt {}
    public record Expression(Expr value, Token token) implements Stmt {}
    public record Empty(Token token) implements Stmt {}
    public record If(Expr condition, Stmt thenBranch, Stmt elseBranch, Token token) implements Stmt {}
    public record While(Expr condition, Stmt body, Token token) implements Stmt {}
    public record DoWhile(Stmt body, Expr condition, Token token) implements Stmt {}
    public record For(Stmt initializer, Expr condition, Expr update, Stmt body, Token token) implements Stmt {}
    public record Return(Expr value, Token token) implements Stmt {}
    public record Break(Token token) implements Stmt {}
    public record Continue(Token token) implements Stmt {}
    public record Output(List<Expr> values, Token token) implements Stmt {}
    public record Input(List<Token> names, Token token) implements Stmt {}
    public record Literal(Object value, Type type, Token token) implements Expr {}
    public record Variable(Token token) implements Expr {}
    public record Binary(Expr left, Token operator, Expr right) implements Expr {
        public Token token() { return operator; }
    }
    public record Unary(Token operator, Expr operand, boolean postfix) implements Expr {
        public Token token() { return operator; }
    }
    public record Assign(Variable target, Token operator, Expr value) implements Expr {
        public Token token() { return operator; }
    }
    public record Conditional(Expr condition, Expr whenTrue, Expr whenFalse, Token token) implements Expr {}
    public record Call(Token name, List<Expr> arguments) implements Expr {
        public Token token() { return name; }
    }
    public record Cast(Type type, Expr value, Token token) implements Expr {}
    public record Address(Token token) implements Expr {}
}
