package compilerx.ui;

import compilerx.lang.Diagnostic;
import compilerx.lang.Engine;
import compilerx.runtime.ExecutionIO;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

public final class WorkspacePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final CodeEditor editor = new CodeEditor();
    private final JTextPane console = new JTextPane();
    private final JTextField stdin = new JTextField();
    private final JTextField search = new JTextField(20);
    private final JLabel tab = label("main.cpp", Theme.TEXT);
    private final JLabel breadcrumb = label("workspace  /  main.cpp", Theme.MUTED);
    private final JLabel status = label("Ready", Theme.ACCENT);
    private final JLabel position = label("Ln 1, Col 1", Theme.MUTED);
    private final JLabel execution = label("Local execution", Theme.MUTED);
    private final JButton run = Theme.button("Run", "run", true);
    private final JButton stop = Theme.button("Stop", "stop", false);
    private final JButton send = Theme.button("Send", null, false);
    private final JButton eof = Theme.button("EOF", null, false);
    private final JButton file = Theme.button("main.cpp", "code", false);
    private final JPanel sidebar = panel(Theme.PANEL);
    private final JPanel findbar = panel(Theme.BAR);
    private final List<JButton> fileActions = new ArrayList<>();
    private final BlockingQueue<InputLine> input = new LinkedBlockingQueue<>(1024);
    private final StringBuilder pending = new StringBuilder();
    private final Timer consoleTimer;
    private JSplitPane horizontal;
    private JSplitPane vertical;
    private Thread worker;
    private long session;
    private boolean dirty;
    private boolean inputEnded;
    private String filename = "main.cpp";
    private Path path;

    public WorkspacePanel() {
        super(new BorderLayout());
        setBackground(Theme.BACKGROUND);
        add(toolbar(), BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(Theme.BACKGROUND);
        center.add(activityRail(), BorderLayout.WEST);
        buildSidebar();
        vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorArea(), consoleArea());
        vertical.setBackground(Theme.BORDER);
        vertical.setDividerSize(5);
        vertical.setResizeWeight(0.73);
        vertical.setContinuousLayout(true);
        horizontal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, vertical);
        horizontal.setBackground(Theme.BORDER);
        horizontal.setDividerSize(4);
        horizontal.setDividerLocation(196);
        horizontal.setResizeWeight(0);
        horizontal.setContinuousLayout(true);
        center.add(horizontal, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        add(statusbar(), BorderLayout.SOUTH);
        editor.onChange(() -> { dirty = true; updateFilename(); });
        editor.onCaretChange(() -> position.setText("Ln " + editor.line() + ", Col " + editor.column()));
        editor.setSource(Examples.ALL.get(0).source());
        stop.setEnabled(false);
        stdin.setEnabled(false); send.setEnabled(false); eof.setEnabled(false);
        run.setToolTipText("Analyze and run locally (Ctrl+Enter / F5)");
        stop.setToolTipText("Stop execution (Shift+F5)");
        run.addActionListener(event -> startRun());
        stop.addActionListener(event -> stopRun());
        send.addActionListener(event -> submitInput());
        stdin.addActionListener(event -> submitInput());
        eof.addActionListener(event -> endInput());
        consoleTimer = new Timer(40, event -> flushOutput());
        consoleTimer.start();
        append("Ready. Press Run or Ctrl+Enter to execute your program.\n", Theme.MUTED);
        bind("control ENTER", "run", this::startRun);
        bind("F5", "runF5", this::startRun);
        bind("shift F5", "stop", this::stopRun);
        bind("control S", "save", () -> save(false));
        bind("control shift S", "saveAs", () -> save(true));
        bind("control O", "open", this::open);
        bind("control N", "new", this::newFile);
        bind("control F", "find", this::showFind);
        bind("control B", "sidebar", this::toggleSidebar);
        bind("control L", "clear", this::clearConsole);
    }

    public void arrange() { horizontal.setDividerLocation(sidebar.isVisible() ? 196 : 0); vertical.setDividerLocation(0.70); }

    public void paint(java.awt.Graphics graphics) {
        java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        super.paint(g); g.dispose();
    }

    private JPanel toolbar() {
        JPanel bar = panel(Theme.BAR);
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER), BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JLabel brand = new JLabel("  COMPILER X", new Theme.Glyph("code", Theme.ACCENT, 26), SwingConstants.LEFT);
        brand.setForeground(Theme.TEXT); brand.setFont(Theme.UI.deriveFont(Font.BOLD, 14f));
        bar.add(brand); bar.add(Box.createHorizontalStrut(24));
        addAction(bar, "New", "new", this::newFile, "New file (Ctrl+N)");
        addAction(bar, "Open", "folder", this::open, "Open C/C++ source (Ctrl+O)");
        addAction(bar, "Save", "save", () -> save(false), "Save source (Ctrl+S); Save As: Ctrl+Shift+S");
        bar.add(Box.createHorizontalGlue());
        bar.add(run); bar.add(Box.createHorizontalStrut(8)); bar.add(stop);
        return bar;
    }

    private JPanel activityRail() {
        JPanel rail = panel(new Color(0x242A32));
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        rail.setPreferredSize(new Dimension(48, 300));
        rail.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));
        rail.add(Box.createVerticalStrut(8));
        rail.add(railButton("files", "Toggle explorer (Ctrl+B)", this::toggleSidebar));
        rail.add(railButton("search", "Find in source (Ctrl+F)", this::showFind));
        rail.add(railButton("run", "Run program (F5)", this::startRun));
        rail.add(railButton("terminal", "Focus program input", () -> { if (stdin.isEnabled()) stdin.requestFocusInWindow(); else console.requestFocusInWindow(); }));
        rail.add(Box.createVerticalGlue());
        rail.add(railButton("code", "About Compiler X", this::about));
        rail.add(Box.createVerticalStrut(8));
        return rail;
    }

    private JButton railButton(String icon, String tooltip, Runnable action) {
        JButton button = Theme.button("", icon, false);
        button.setBorder(BorderFactory.createEmptyBorder(12, 13, 12, 13));
        button.setBackground(new Color(0x242A32));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setToolTipText(tooltip); button.getAccessibleContext().setAccessibleName(tooltip);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void buildSidebar() {
        sidebar.setLayout(new BorderLayout());
        sidebar.setMinimumSize(new Dimension(145, 0));
        sidebar.setPreferredSize(new Dimension(196, 400));
        JLabel heading = label("EXPLORER", Theme.MUTED);
        heading.setFont(Theme.UI.deriveFont(Font.BOLD, 11f));
        heading.setBorder(BorderFactory.createEmptyBorder(17, 16, 13, 10));
        sidebar.add(heading, BorderLayout.NORTH);
        JPanel list = panel(Theme.PANEL);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JLabel workspace = label("WORKSPACE", Theme.TEXT);
        workspace.setFont(Theme.UI.deriveFont(Font.BOLD, 11f));
        workspace.setBorder(BorderFactory.createEmptyBorder(5, 16, 10, 10));
        workspace.setAlignmentX(Component.LEFT_ALIGNMENT); list.add(workspace);
        file.setHorizontalAlignment(SwingConstants.LEFT);
        file.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, Theme.ACCENT), BorderFactory.createEmptyBorder(9, 14, 9, 5)));
        file.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        file.setAlignmentX(Component.LEFT_ALIGNMENT);
        file.addActionListener(event -> editor.focus()); list.add(file);
        list.add(Box.createVerticalStrut(28));
        JLabel examples = label("EXAMPLES", Theme.MUTED);
        examples.setFont(Theme.UI.deriveFont(Font.BOLD, 11f));
        examples.setBorder(BorderFactory.createEmptyBorder(0, 16, 11, 10));
        examples.setAlignmentX(Component.LEFT_ALIGNMENT); list.add(examples);
        for (Examples.Example example : Examples.ALL) {
            JButton button = Theme.button(example.title(), null, false);
            button.setBackground(Theme.PANEL);
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setForeground(Theme.MUTED);
            button.setBorder(BorderFactory.createEmptyBorder(9, 17, 9, 5));
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.addActionListener(event -> {
                if (worker == null && confirmDiscard()) load(example.source(), example.filename(), null);
            });
            fileActions.add(button); list.add(button);
        }
        list.add(Box.createVerticalGlue());
        JScrollPane scrolling = new JScrollPane(list);
        Theme.styleScrollPane(scrolling);
        scrolling.setBorder(BorderFactory.createEmptyBorder());
        scrolling.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sidebar.add(scrolling, BorderLayout.CENTER);
        JPanel foot = panel(Theme.PANEL);
        foot.setLayout(new BoxLayout(foot, BoxLayout.Y_AXIS));
        foot.setBorder(BorderFactory.createEmptyBorder(14, 16, 18, 8));
        JLabel engine = label("JAVA ENGINE", Theme.ACCENT); engine.setFont(Theme.UI.deriveFont(Font.BOLD, 10f));
        foot.add(engine); foot.add(Box.createVerticalStrut(5));
        foot.add(label("Offline  /  JDK only", Theme.MUTED));
        sidebar.add(foot, BorderLayout.SOUTH);
    }

    private JPanel editorArea() {
        JPanel area = panel(Theme.EDITOR);
        area.setLayout(new BorderLayout()); area.setMinimumSize(new Dimension(280, 160));
        JPanel headers = panel(Theme.PANEL); headers.setLayout(new BoxLayout(headers, BoxLayout.Y_AXIS));
        JPanel tabs = panel(Theme.PANEL); tabs.setLayout(new BorderLayout());
        JPanel active = panel(Theme.EDITOR); active.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 10));
        active.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, Theme.ACCENT));
        JLabel cpp = label("C++", Theme.BLUE); cpp.setFont(Theme.UI.deriveFont(Font.BOLD, 11f));
        active.add(cpp); active.add(tab); tabs.add(active, BorderLayout.WEST);
        JLabel subset = label("C/C++ subset", Theme.MUTED); subset.setFont(Theme.UI.deriveFont(11f));
        subset.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 16)); tabs.add(subset, BorderLayout.EAST);
        headers.add(tabs);
        breadcrumb.setFont(Theme.UI.deriveFont(11f));
        breadcrumb.setBorder(BorderFactory.createEmptyBorder(7, 18, 7, 5));
        JPanel crumb = panel(Theme.EDITOR); crumb.setLayout(new BorderLayout()); crumb.add(breadcrumb); headers.add(crumb);
        findbar.setLayout(new FlowLayout(FlowLayout.RIGHT, 6, 5));
        search.setBackground(Theme.EDITOR); search.setForeground(Theme.TEXT); search.setCaretColor(Theme.TEXT);
        search.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER), BorderFactory.createEmptyBorder(5, 7, 5, 7)));
        search.setToolTipText("Case-sensitive search; Enter finds next match");
        search.addActionListener(event -> editor.find(search.getText(), true));
        findbar.add(label("Find", Theme.MUTED)); findbar.add(search);
        JButton next = Theme.button("Next", null, false); next.addActionListener(event -> editor.find(search.getText(), true)); findbar.add(next);
        JButton close = Theme.button("", "clear", false); close.setToolTipText("Close find");
        close.addActionListener(event -> { findbar.setVisible(false); editor.focus(); }); findbar.add(close);
        findbar.setVisible(false); headers.add(findbar);
        area.add(headers, BorderLayout.NORTH); area.add(editor, BorderLayout.CENTER);
        return area;
    }

    private JPanel consoleArea() {
        JPanel area = panel(new Color(0x20252D)); area.setLayout(new BorderLayout());
        area.setMinimumSize(new Dimension(280, 152));
        JPanel header = panel(Theme.PANEL); header.setLayout(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(3, 17, 3, 8));
        JLabel title = label("OUTPUT", Theme.TEXT); title.setFont(Theme.UI.deriveFont(Font.BOLD, 11f));
        title.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.ACCENT)); header.add(title, BorderLayout.WEST);
        JPanel actions = panel(Theme.PANEL); actions.setLayout(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        execution.setFont(Theme.UI.deriveFont(11f)); actions.add(execution);
        JButton clear = Theme.button("Clear", "clear", false);
        clear.setToolTipText("Clear console (Ctrl+L)"); clear.addActionListener(event -> clearConsole()); actions.add(clear);
        header.add(actions, BorderLayout.EAST); area.add(header, BorderLayout.NORTH);
        console.setEditable(false); console.setFont(Theme.CODE.deriveFont(13f));
        console.setBackground(new Color(0x20252D)); console.setForeground(Theme.TEXT);
        console.setCaretColor(Theme.TEXT); console.setSelectionColor(new Color(0x3E5873));
        console.setBorder(BorderFactory.createEmptyBorder(12, 17, 12, 15));
        JScrollPane scrolling = new JScrollPane(console); scrolling.setBorder(BorderFactory.createEmptyBorder());
        Theme.styleScrollPane(scrolling);
        area.add(scrolling, BorderLayout.CENTER);
        JPanel inputbar = panel(Theme.PANEL); inputbar.setLayout(new BorderLayout(9, 0));
        inputbar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER), BorderFactory.createEmptyBorder(7, 14, 7, 8)));
        JLabel prompt = label("stdin  >", Theme.ACCENT); prompt.setFont(Theme.CODE.deriveFont(12f)); inputbar.add(prompt, BorderLayout.WEST);
        stdin.setFont(Theme.CODE.deriveFont(13f)); stdin.setBackground(Theme.BACKGROUND); stdin.setForeground(Theme.TEXT);
        stdin.setCaretColor(Theme.TEXT); stdin.setDisabledTextColor(Theme.MUTED);
        stdin.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        stdin.setToolTipText("Type input values and press Enter; multiple values may share a line.");
        stdin.getAccessibleContext().setAccessibleName("Program standard input"); inputbar.add(stdin, BorderLayout.CENTER);
        JPanel buttons = panel(Theme.PANEL); buttons.setLayout(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttons.add(send); buttons.add(eof); eof.setToolTipText("Close program input (end of file)");
        inputbar.add(buttons, BorderLayout.EAST); area.add(inputbar, BorderLayout.SOUTH);
        return area;
    }

    private JPanel statusbar() {
        JPanel bar = panel(Theme.BAR); bar.setLayout(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER), BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        status.setFont(Theme.UI.deriveFont(11f)); bar.add(status, BorderLayout.WEST);
        JPanel right = panel(Theme.BAR); right.setLayout(new FlowLayout(FlowLayout.RIGHT, 18, 0));
        position.setFont(Theme.UI.deriveFont(11f)); right.add(position);
        for (String text : new String[]{"Spaces: 4", "UTF-8", "Java engine"}) { JLabel item = label(text, Theme.MUTED); item.setFont(Theme.UI.deriveFont(11f)); right.add(item); }
        bar.add(right, BorderLayout.EAST); return bar;
    }

    private void startRun() {
        if (worker != null) return;
        String source = editor.source();
        input.clear(); inputEnded = false; stdin.setText("");
        editor.clearError(); clearConsole(); setRunning(true);
        status.setText("Analyzing and running..."); status.setForeground(Theme.ACCENT);
        execution.setText("Running");
        long current = ++session;
        worker = new Thread(() -> {
            long started = System.nanoTime();
            Integer exit = null;
            Diagnostic diagnostic = null;
            String failure = null;
            boolean cancelled = false;
            try {
                exit = new Engine().run(source, new ExecutionIO() {
                    public void write(String text) { synchronized (pending) { pending.append(text); } }
                    public String readLine() throws InterruptedException {
                        if (input.isEmpty()) SwingUtilities.invokeLater(() -> {
                            if (session == current && worker != null) {
                                flushOutput(); status.setText("Waiting for input"); execution.setText("Waiting for stdin");
                                if (!inputEnded) stdin.requestFocusInWindow();
                            }
                        });
                        String line = input.take().value();
                        SwingUtilities.invokeLater(() -> { if (session == current && worker != null) { status.setText("Running..."); execution.setText("Running"); } });
                        return line;
                    }
                });
            } catch (CancellationException exception) { cancelled = true; }
            catch (Diagnostic exception) { diagnostic = exception; }
            catch (RuntimeException exception) { failure = "Execution failed: " + exception.getClass().getSimpleName() + ". " + String.valueOf(exception.getMessage()); }
            final Integer result = exit;
            final Diagnostic problem = diagnostic;
            final String unexpected = failure;
            final boolean stopped = cancelled;
            final double elapsed = (System.nanoTime() - started) / 1_000_000_000.0;
            SwingUtilities.invokeLater(() -> finish(current, result, problem, unexpected, stopped, elapsed));
        }, "compilerx-execution");
        worker.setDaemon(true); worker.start();
    }

    private void finish(long current, Integer exit, Diagnostic diagnostic, String failure, boolean cancelled, double elapsed) {
        if (session != current) return;
        flushOutput(); worker = null; setRunning(false);
        String time = String.format(Locale.ROOT, "%.3f s", elapsed);
        execution.setText(time);
        if (cancelled) {
            append("\n[Execution stopped]\n", Theme.MUTED); status.setText("Stopped"); status.setForeground(Theme.MUTED);
        } else if (diagnostic != null) {
            append("\n" + diagnostic.format() + "\n", Theme.ERROR);
            editor.showError(diagnostic.line(), diagnostic.column());
            status.setText("1 error  |  Line " + diagnostic.line()); status.setForeground(Theme.ERROR);
        } else if (failure != null) {
            append("\n" + failure + "\n", Theme.ERROR); status.setText("Execution error"); status.setForeground(Theme.ERROR);
        } else {
            append("\n[Finished  |  Exit code " + exit + "  |  " + time + "]\n", Theme.MUTED);
            status.setText("Finished  |  Exit code " + exit); status.setForeground(exit != null && exit == 0 ? Theme.ACCENT : Theme.MUTED);
        }
    }

    private void setRunning(boolean running) {
        run.setEnabled(!running); stop.setEnabled(running);
        stdin.setEnabled(running); send.setEnabled(running); eof.setEnabled(running);
        editor.setEditable(!running);
        for (JButton action : fileActions) action.setEnabled(!running);
    }
    private void stopRun() { if (worker != null) { status.setText("Stopping..."); worker.interrupt(); } }
    private void submitInput() {
        if (worker == null || inputEnded) return;
        String line = stdin.getText();
        if (line.length() > 50_000) { append("Input line must be at most 50,000 characters.\n", Theme.ERROR); return; }
        if (!input.offer(new InputLine(line))) { append("Input queue is full; wait for the program to read it.\n", Theme.ERROR); return; }
        flushOutput(); append(line + "\n", Theme.BLUE); stdin.setText(""); stdin.requestFocusInWindow();
    }
    private void endInput() {
        if (worker == null || inputEnded) return;
        if (input.offer(new InputLine(null))) {
            inputEnded = true; stdin.setEnabled(false); send.setEnabled(false); eof.setEnabled(false);
        }
    }
    private void flushOutput() {
        String text;
        synchronized (pending) { text = pending.toString(); pending.setLength(0); }
        if (!text.isEmpty()) append(text, Theme.TEXT);
    }
    private void clearConsole() { synchronized (pending) { pending.setLength(0); } console.setText(""); }
    private void append(String text, Color color) {
        SimpleAttributeSet style = new SimpleAttributeSet(); StyleConstants.setForeground(style, color);
        try {
            var document = console.getStyledDocument();
            document.insertString(document.getLength(), text, style);
            if (document.getLength() > 230_000) document.remove(0, document.getLength() - 220_000);
            console.setCaretPosition(document.getLength());
        } catch (BadLocationException ignored) {}
    }

    private void newFile() { if (worker == null && confirmDiscard()) load("", "untitled.cpp", null); }
    private void open() {
        if (worker != null || !confirmDiscard()) return;
        JFileChooser chooser = chooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path selected = chooser.getSelectedFile().toPath();
        try {
            if (Files.size(selected) > 800_000) throw new IOException("Source exceeds the editor size limit.");
            String source = Files.readString(selected, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
            if (source.startsWith("\uFEFF")) source = source.substring(1);
            if (source.length() > 200_000) throw new IOException("Source exceeds 200,000 characters after tab expansion.");
            load(source, selected.getFileName().toString(), selected);
        } catch (IOException exception) { showFileError(exception); }
    }
    private boolean save(boolean saveAs) {
        Path target = path;
        if (target == null || saveAs) {
            JFileChooser chooser = chooser(); chooser.setSelectedFile(new java.io.File(filename));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return false;
            target = chooser.getSelectedFile().toPath();
            if (!target.getFileName().toString().contains(".")) target = target.resolveSibling(target.getFileName() + ".cpp");
            if (Files.exists(target) && JOptionPane.showConfirmDialog(this, "Replace " + target.getFileName() + "?", "Replace file", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return false;
        }
        try {
            Files.writeString(target, editor.source(), StandardCharsets.UTF_8);
            path = target; filename = target.getFileName().toString(); dirty = false; updateFilename();
            status.setText("Saved " + filename); status.setForeground(Theme.ACCENT); return true;
        } catch (IOException exception) { showFileError(exception); return false; }
    }
    private JFileChooser chooser() {
        JFileChooser chooser = new JFileChooser(path == null ? null : path.toAbsolutePath().getParent().toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("C/C++ source (*.c, *.cpp, *.cc, *.cxx)", "c", "cpp", "cc", "cxx"));
        return chooser;
    }
    private void load(String source, String name, Path selected) {
        editor.setSource(source); path = selected; filename = name; dirty = false;
        updateFilename(); clearConsole(); status.setText("Ready"); status.setForeground(Theme.ACCENT); execution.setText("Local execution"); editor.focus();
    }
    private void updateFilename() {
        tab.setText(filename + (dirty ? "  •" : "")); file.setText(filename);
        breadcrumb.setText("workspace  /  " + filename); breadcrumb.setToolTipText(path == null ? filename : path.toAbsolutePath().toString());
    }
    private boolean confirmDiscard() {
        if (!dirty) return true;
        Object[] choices = {"Save", "Discard", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, "Save changes to " + filename + "?", "Unsaved changes", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
        return choice == 1 || choice == 0 && save(false);
    }
    public boolean canClose() { if (!confirmDiscard()) return false; stopRun(); consoleTimer.stop(); return true; }
    private void showFileError(IOException exception) { JOptionPane.showMessageDialog(this, exception.getMessage(), "File error", JOptionPane.ERROR_MESSAGE); }
    private void showFind() { findbar.setVisible(true); revalidate(); search.requestFocusInWindow(); search.selectAll(); }
    private void toggleSidebar() {
        boolean visible = horizontal.getLeftComponent() != null;
        horizontal.setLeftComponent(visible ? null : sidebar); sidebar.setVisible(!visible);
        horizontal.setDividerSize(visible ? 0 : 4); horizontal.setDividerLocation(visible ? 0 : 196); revalidate();
    }
    private void about() {
        JOptionPane.showMessageDialog(this, "Compiler X\n\nA local educational C/C++ subset interpreter, written in Java.\nLexer → Parser → Semantic analyzer → Execution engine\n\nCtrl+Enter / F5   Run        Shift+F5   Stop\nCtrl+O   Open        Ctrl+S   Save        Ctrl+Shift+S   Save As\nCtrl+F   Find        Ctrl+B   Explorer        Ctrl+L   Clear output\n\nUse pow(a, b) for exponentiation; ^ means bitwise XOR.\nRead README.md for the complete supported language and limits.", "About Compiler X", JOptionPane.INFORMATION_MESSAGE);
    }
    private void addAction(JPanel bar, String text, String icon, Runnable action, String tooltip) {
        JButton button = Theme.button(text, icon, false); button.setToolTipText(tooltip);
        button.addActionListener(event -> action.run()); fileActions.add(button); bar.add(button); bar.add(Box.createHorizontalStrut(7));
    }
    private void bind(String key, String name, Runnable action) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() { public void actionPerformed(ActionEvent event) { action.run(); } });
    }
    private static JPanel panel(Color color) { JPanel panel = new JPanel(); panel.setBackground(color); return panel; }
    private static JLabel label(String text, Color color) { JLabel label = new JLabel(text); label.setFont(Theme.UI); label.setForeground(color); return label; }
    private record InputLine(String value) {}
}
