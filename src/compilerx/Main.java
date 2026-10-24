package compilerx;

import compilerx.lang.Diagnostic;
import compilerx.lang.Engine;
import compilerx.runtime.ExecutionIO;
import compilerx.ui.MainWindow;
import compilerx.ui.Theme;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

public final class Main {
    private Main() {}

    public static void main(String[] arguments) {
        if (arguments.length > 0) { System.exit(commandLine(arguments)); return; }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("A desktop session is required for the editor. Use --run <file.cpp> for terminal execution.");
            System.exit(2); return;
        }
        SwingUtilities.invokeLater(() -> { Theme.install(); new MainWindow().setVisible(true); });
    }

    private static int commandLine(String[] arguments) {
        if (arguments.length == 1 && (arguments[0].equals("--help") || arguments[0].equals("-h"))) {
            System.out.println("Compiler X — Java-only C/C++ subset interpreter\nUsage:\n  java -cp out compilerx.Main\n  java -cp out compilerx.Main --run program.cpp\n  java -cp out compilerx.Main --check program.cpp");
            return 0;
        }
        if (arguments.length != 2 || !(arguments[0].equals("--run") || arguments[0].equals("--check"))) {
            System.err.println("Expected --run <file.cpp> or --check <file.cpp>. Use --help for usage.");
            return 2;
        }
        try {
            Path path = Path.of(arguments[1]);
            if (Files.size(path) > 800_000) throw new IOException("Source file exceeds the size limit.");
            String source = Files.readString(path, StandardCharsets.UTF_8);
            Engine engine = new Engine();
            if (arguments[0].equals("--check")) { engine.compile(source); System.out.println("Analysis successful."); return 0; }
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            return engine.run(source, new ExecutionIO() {
                public String readLine() {
                    try { return input.readLine(); }
                    catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
                }
                public void write(String text) { System.out.print(text); System.out.flush(); }
            });
        } catch (Diagnostic exception) { System.err.println(exception.format()); return 1; }
        catch (IOException | RuntimeException exception) { System.err.println("Error: " + exception.getMessage()); return 1; }
    }
}
