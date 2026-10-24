package compilerx.lang;

import compilerx.runtime.ExecutionIO;
import compilerx.runtime.Interpreter;

public final class Engine {
    public SemanticAnalyzer.CheckedProgram compile(String source) {
        try {
            Ast.Program program = new Parser(new Lexer(source).scan()).parse();
            return new SemanticAnalyzer().analyze(program);
        } catch (StackOverflowError error) {
            throw new Diagnostic("Syntax", 1, 1, "Program nesting is too deep; simplify nested statements or expressions.");
        }
    }

    public int run(String source, ExecutionIO io) {
        SemanticAnalyzer.CheckedProgram program = compile(source);
        try { return new Interpreter(program, io).execute(); }
        catch (StackOverflowError error) { throw new Diagnostic("Runtime", 1, 1, "Expression or call nesting exceeded the available Java stack."); }
    }
}
