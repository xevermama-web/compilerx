# Compiler X

A lightweight, offline C/C++ educational subset interpreter with a Java Swing desktop editor. The interface follows the supplied reference: a charcoal workspace, explorer sidebar, syntax-colored editor, line-number gutter, resizable output console, compact toolbar, and status bar.

The complete language pipeline is implemented in Java: **lexer → parser → abstract syntax tree → semantic analyzer → interpreter**. User-written programs are parsed and executed dynamically. This project does not invoke GCC, MinGW, Clang, shell commands, other executables, online APIs, or third-party compiler libraries. It does not produce native C/C++ machine code or claim full ISO C/C++ compatibility.

## Requirements

- A full **JDK 17 or newer**, with both `java` and `javac` available in your terminal. A JRE alone is insufficient to compile the Java sources.
- A graphical desktop session for the editor. Terminal execution is also available without a display.
- VS Code is optional. No JavaFX, Maven, Gradle, additional libraries, environment services, or internet connection is needed to build or run this project after the JDK is installed.

## Compile and run

1. Extract the ZIP.
2. Open a terminal in the extracted **CompilerX** directory, the directory containing `README.md` and `src`.
3. Verify your JDK:

```text
java -version
javac -version
```

4. Compile from source:

```text
javac --release 17 -encoding UTF-8 -d out -sourcepath src src/compilerx/Main.java
```

5. Start the desktop editor:

```text
java -cp out compilerx.Main
```

These commands work in Windows PowerShell, Windows Command Prompt, and macOS/Linux terminals. The compiler automatically discovers the other project sources through `-sourcepath src`. The `out` directory is created by your local compilation; it is intentionally absent from the ZIP. Re-run the compile command after changing Java source files.

If your JDK is installed but the commands are unavailable, add that JDK's `bin` directory to your system's `PATH`, then reopen the terminal. If `java` and `javac` report different major versions, select the same JDK for both.

### Use with VS Code

1. Choose **File → Open Folder** and select the extracted `CompilerX` folder.
2. Choose **Terminal → New Terminal**.
3. Run the compile and launch commands above in that terminal.
4. The Swing application opens in its own window. Write C/C++ source in that window and press **Run**.

VS Code Java extensions are optional; the terminal workflow needs no extensions or project configuration files. An optional Java extension can also recognize `src/compilerx/Main.java` as the Java entry point. The reliable baseline is the terminal workflow above. Do not run an individual Java source file in isolation or use a C/C++ extension's build command for this application.

### Terminal mode

Save a supported C/C++ program as `program.cpp` or `program.c`, then use:

```text
java -cp out compilerx.Main --run program.cpp
java -cp out compilerx.Main --check program.cpp
java -cp out compilerx.Main --help
```

`--run` reads program input from standard input, writes program output to standard output, and returns the program's `main` return code. Diagnostics go to standard error. `--check` performs lexical, syntax, and semantic analysis without execution; runtime-only problems such as division by zero or reading an uninitialized variable are reported when the program runs. Analysis/execution errors return status 1; invalid command usage returns status 2. The operating system may truncate large exit codes.

Use quoted paths when a filename contains spaces:

```text
java -cp out compilerx.Main --run "my programs/calculator.cpp"
```

## Editor workflow

- **New / Open / Save:** create, load, or save one UTF-8 C/C++ source document. Unsaved changes are confirmed before replacing a document or closing. Save As is available with `Ctrl+Shift+S`.
- **Run:** analyzes a snapshot of the source and executes it on a worker thread. The editor becomes read-only during execution; scrolling, selection, console controls, and Stop remain available.
- **Stop:** interrupts computation or a blocked input request. The UI thread is never used for executing the program.
- **Clear:** clears the output console. It does not erase source code or stop a running program.
- **Input:** when `cin` or `scanf` requests data, type into the `stdin >` field and press Enter or Send. Multiple whitespace-separated values can share a line. You can also queue input before the program requests it. EOF closes the current run's input stream; a missing required value becomes a line-numbered runtime error.
- **Examples:** load editable source for Hello World, all eight arithmetic operations, input, conditions, loops, functions, or C console I/O. These examples are embedded in a Java source file and run through the same engine as every other program.
- **Editor:** syntax highlighting, horizontal scrolling for long lines, line numbers, error-line highlighting, undo/redo, four-space Tab indentation, Shift+Tab outdent, and brace-aware Enter indentation.
- **Layout:** drag the explorer or console divider to resize panes. The explorer can be hidden. The minimum window size is 800 × 570.

| Action | Shortcut |
| --- | --- |
| Run | `Ctrl+Enter` or `F5` |
| Stop | `Shift+F5` |
| New / Open / Save | `Ctrl+N` / `Ctrl+O` / `Ctrl+S` |
| Save As | `Ctrl+Shift+S` |
| Find, then next match | `Ctrl+F`, then Enter |
| Toggle explorer | `Ctrl+B` |
| Clear output | `Ctrl+L` |
| Undo / Redo | `Ctrl+Z` / `Ctrl+Y` or `Ctrl+Shift+Z` |

The listed Control shortcuts are used on all platforms, including macOS.

## Supported language

### Program structure and headers

- `int main()` and `int main(void)`; falling off the end of `main` returns 0.
- `using namespace std;` at the top level.
- Recognized headers: `iostream`, `string`, `cmath`, `math.h`, `cstdio`, `stdio.h`, `cstdlib`, `stdlib.h`, `algorithm`, and `stdbool.h`.
- An include uses `#include <header>` on its own line, optionally followed by a `//` comment. Headers are recognized declarations of intent; no external header files are read, and no real preprocessor runs. Built-ins are available independently of header choice or the `using` statement.
- Unqualified `cout`, `cin`, and `endl`, or their `std::` forms. Supported built-in functions also accept `std::`. `std::string` is accepted as a type.
- `//` and `/* ... */` comments, character literals, strings, adjacent string literals, and common escapes: `\n`, `\t`, `\r`, `\0`, `\b`, `\f`, `\v`, `\\`, `\'`, `\"`, and `\?`.
- Decimal, leading-zero octal, and hexadecimal integer literals; decimal/scientific floating literals; `L`/`LL` integer suffixes and `f`/`F` floating suffixes. A float suffix requires a decimal point or exponent, such as `2.0f`.

### Values and declarations

| Type | Engine representation |
| --- | --- |
| `short`, `short int` | Signed 16-bit integer |
| `int` | Signed 32-bit integer |
| `long`, `long int`, `long long`, `long long int` | Signed 64-bit integer |
| `float` | 32-bit floating-point value |
| `double` | 64-bit floating-point value |
| `char` | One unsigned 16-bit Java character |
| `bool` | `true` / `false`, displayed and read as `1` / `0` |
| `string`, `std::string` | Java-backed text value |
| `void` | Function return type only |

Local and global declarations, multiple declarations such as `int a = 2, b = 3;`, assignment, `const`, nested lexical scopes, numeric conversions, and scalar C-style casts such as `(double)a / b` are supported. Strings support assignment, string-to-string concatenation with `+`/`+=`, and lexicographic comparisons. String variables default to empty. Global numeric variables default to zero; reading an uninitialized local numeric variable is a runtime error.

### Operators

| Category | Supported operations |
| --- | --- |
| Eight requested arithmetic operations | Addition `+`, subtraction `-`, multiplication `*`, division `/`, modulus `%`, increment `++`, decrement `--`, exponentiation `pow(a, b)` |
| Unary | `+`, `-`, `!`, `~`, prefix and postfix `++` / `--` |
| Assignment | `=`, `+=`, `-=`, `*=`, `/=`, `%=`, `&=`, `|=`, `^=`, `<<=`, `>>=` |
| Comparison | `==`, `!=`, `<`, `<=`, `>`, `>=` |
| Logical | Short-circuit `&&`, `||`, and `!` |
| Bitwise | `&`, `|`, `^`, `~`, `<<`, `>>` |
| Other | Parentheses and short-circuit conditional `condition ? value1 : value2` |

**`^` is bitwise XOR, never exponentiation.** Use `pow(2, 5)` for 32. There is no C/C++ `**` exponentiation operator.

C/C++ operator precedence is applied: multiplicative operators before additive operators, then shifts, comparisons, equality, bitwise operators, and logical operators. Assignment and conditional expressions associate to the right. Integral division truncates toward zero; floating-point division requires a floating operand or cast. `%` and bitwise operators require integral operands. `bool` cannot be incremented or decremented.

In `cout` expressions, parenthesize comparisons, assignments, shifts, logical expressions, and ternary expressions when using them as a single output item; for example `cout << (a > b) << endl;` or `cout << (1 << 3);`.

### Control flow and functions

- `if`, `else if`, and `else`, including nested branches.
- `while`, `do ... while`, and `for`, including empty `for` clauses.
- `break`, `continue`, and `return` with normal loop/function behavior.
- User-defined functions with typed parameters, pass-by-value arguments, scalar/string return values, `void` returns, and recursion.
- Functions may be defined before or after their call sites as an educational convenience. No separate prototypes are required or accepted. Overloading is not supported.
- Function names and built-in function names cannot be reused as variable names. `main` cannot be called from program code.
- A non-void function other than `main` must return a value on every executed path; falling through is diagnosed at runtime.

### Input, output, and math

- `cout << expression << ... << endl;` prints actual evaluated values. Floating-point output uses approximately six significant digits with a dot decimal separator. No `iomanip` manipulators, stream objects, or stream state flags are implemented.
- `cin >> variable >> ...;` supports declared numeric, character, bool, and string variables. String input reads one whitespace-delimited token; character input skips whitespace and takes one character. Bool input accepts only 0 or 1.
- Mathematical functions: `pow`, `sqrt`, `abs`, `floor`, `ceil`, `round`, `sin`, `cos`, `tan`, `log`, `exp`, `min`, and `max`. Trigonometric inputs are radians. `round` rounds halfway away from zero. Domain errors and non-finite results produce runtime errors.
- `printf` supports `%d`, `%i`, `%ld`, `%lld`, `%f`, `%lf`, `%F`, `%e`, `%E`, `%g`, `%G`, `%c`, `%s`, and `%%`, with compatible fixed widths/flags and precision combinations. Precision is useful for floating values and strings; integer precision is not supported. Width and precision are limited to 1,000. There is no `*` width/precision, unsigned/hex/octal formatting, `%n`, or wide-character formatting. Unsupported combinations are diagnosed.
- `printf` validates value categories and uses Java's locale-independent formatting for the supported conversions. Its rounding details and `%g` trailing-zero behavior can differ from a native C library; `%s`/`%c` operate on Java text, and the returned count is a Java character count. Numeric float conversions accept numeric operands, including integers, as a subset convenience.
- `scanf` supports whitespace-separated `%d` → `int`, `%hd` → `short`, `%ld`/`%lld` → `long`, `%f` → `float`, `%lf` → `double`, and `%c` → `char`, with `&variable` destinations. Types must match. `%c` does not skip whitespace unless the format contains a preceding space. Literal separators, field widths, `%s`, pointers, and scanf assignment suppression are not supported.
- `puts(string)` writes a string followed by a newline.
- Numeric input is parsed as a whole whitespace-delimited decimal token. Invalid input or premature EOF raises an error instead of implementing native C stream fail bits or scanf partial-conversion return values.

## Try the engine

The examples below can be pasted into the editor. They are documentation, not separate required project files.

### Hello World

```cpp
#include <iostream>
using namespace std;

int main() {
    cout << "Hello, World!" << endl;
    return 0;
}
```

Expected output:

```text
Hello, World!
```

### All eight arithmetic operations

```cpp
#include <iostream>
#include <cmath>
using namespace std;

int main() {
    int a = 12, b = 5;
    cout << a + b << " " << a - b << " " << a * b << " ";
    cout << a / b << " " << a % b << " ";
    cout << ++a << " " << --b << " " << pow(2, 5) << endl;
    cout << 2 + 3 * 4 << " " << (2 + 3) * 4 << endl;
    return 0;
}
```

Expected output:

```text
17 7 60 2 2 13 4 32
14 20
```

### C-style input

```c
#include <stdio.h>

int main(void) {
    int count;
    double price;
    scanf("%d %lf", &count, &price);
    printf("Total: %.2f\n", count * price);
    return 0;
}
```

Enter `3 5.00`. Expected output: `Total: 15.00`.

## Error handling and execution limits

Diagnostics identify the phase, one-based line, and one-based column. The editor highlights the relevant line. For example:

```text
Runtime error at line 3, column 14: Division or modulus by zero.
```

The first error stops analysis or execution. Errors include invalid tokens, unsupported constructs, missing punctuation, undeclared/duplicate variables, invalid types, const writes, incorrect function calls, invalid input, uninitialized reads, division by zero, invalid shifts, and mathematical domain/overflow failures.

Limits keep the application responsive:

- Source: 200,000 characters. The GUI expands tabs to four spaces and normalizes line endings.
- Execution: 1,000,000 statement/expression steps, including loop checks.
- Recursion: 128 active function calls.
- Output and individual concatenated strings: 200,000 characters per run/value.
- Input token and GUI input line: 50,000 characters; queued GUI input is bounded.
- Excessive parser/expression nesting is rejected with a diagnostic rather than exposing a Java stack trace.

These limits are constants in `Lexer`, `Interpreter`, `InputCursor`, and the UI. Stop remains usable while awaiting input or running code.

## Deliberate limitations

This is a teaching-oriented C/C++ subset, not a replacement for a production compiler. Unsupported features include pointers (except the restricted scanf destination syntax), references, arrays, structs/classes, templates, inheritance, namespaces other than the recognized `std::` names, user-defined headers, macros, conditional compilation, overloaded functions/operators, constructors, destructors, exceptions, `switch`, `goto`, the comma operator, `auto`, `unsigned`/`signed` specifiers, `sizeof`, dynamic allocation, STL containers, file/network/process APIs, and native linking. Source files run individually; there is no multi-file project linker.

The numeric model is deliberately deterministic: `long` is always 64-bit, `char` is a Java 16-bit character, and integer overflow/narrowing wraps using Java signed arithmetic. Native C/C++ signed overflow and some conversions have different or undefined behavior. Floating-to-integral conversions outside the destination range are rejected. Expressions and function arguments are evaluated left-to-right, including cases where native C/C++ has unspecified or undefined evaluation order. Avoid writing programs whose meaning relies on those differences. String literals are treated as string values, not C character arrays.

The app translates source into an AST and interprets it; it does not emit object files, executables, assembly, or JVM bytecode for the C/C++ program. Only the Java application itself is compiled by the JDK.

## Project structure

| Location | Responsibility |
| --- | --- |
| `src/compilerx/Main.java` | Desktop and command-line entry points |
| `src/compilerx/lang/Lexer.java`, `Token.java` | Tokenization, literal decoding, source positions, recognized directives |
| `src/compilerx/lang/Parser.java`, `Ast.java` | Recursive-descent parser, precedence, typed AST node definitions |
| `src/compilerx/lang/SemanticAnalyzer.java`, `Type.java` | Symbols, lexical scopes, type checks, function checks, expression types |
| `src/compilerx/lang/Diagnostic.java`, `Engine.java` | Diagnostics and public compile/run facade |
| `src/compilerx/runtime/Interpreter.java` | Dynamic statement/expression execution, functions, control flow, guards |
| `src/compilerx/runtime/Environment.java`, `Value.java` | Variable cells, scopes, conversions, value formatting |
| `src/compilerx/runtime/ExecutionIO.java`, `InputCursor.java`, `Formats.java` | I/O interface, input extraction, validated C-style formatting |
| `src/compilerx/ui/MainWindow.java`, `WorkspacePanel.java` | Swing window, explorer, console, worker lifecycle, file actions |
| `src/compilerx/ui/CodeEditor.java`, `Theme.java` | Syntax highlighting, line numbers, editing actions, standard-JDK visual styling |
| `src/compilerx/ui/Examples.java` | Editable example source strings |

To add a language feature, extend the AST/parser, add its semantic rules, then implement execution. Keep all application UI changes on Swing's event-dispatch thread; language execution and blocking input belong on the worker thread. The `ExecutionIO` interface keeps the engine independent of Swing and terminal handling.

## Verification and package contents

Verified with the Java 17 compiler: clean source compilation, **366 compiler/runtime checks**, and **22 Swing/editor/example checks**. Checks covered dynamic execution, the eight requested arithmetic operations, precedence, input, functions, scope, numeric behavior, invalid programs, cancellation of blocked input, execution limits, and the shipped editor examples. Swing components were exercised and rendered offscreen at 1220 × 760 and 800 × 570. Native OS window decoration and file chooser interactions were not exercised in the headless verification environment.

The ZIP contains exactly **20 `.java` source files and this one `README.md`**. It contains no compiled classes, JARs, executables, dependencies, test harnesses, build directories, scripts, images, or configuration files. Verification artifacts are intentionally excluded.
