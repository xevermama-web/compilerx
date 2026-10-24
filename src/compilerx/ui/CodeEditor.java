package compilerx.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.undo.UndoManager;

public final class CodeEditor extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final Set<String> KEYWORDS = Set.of("if", "else", "for", "while", "do", "return", "break", "continue", "using", "namespace", "const");
    private static final Set<String> TYPES = Set.of("int", "long", "short", "float", "double", "char", "bool", "string", "void", "true", "false");
    private static final Pattern TOKENS = Pattern.compile("(?m)//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|\\z)|\"(?:\\\\.|[^\"\\\\\\n])*\"?|'(?:\\\\.|[^'\\\\\\n])*'?|^\\h*#[^\\n]*|\\b(?:0[xX][0-9a-fA-F]+|[0-9]+(?:\\.[0-9]*)?(?:[eE][+-]?[0-9]+)?)[fFlL]*\\b|\\b[A-Za-z_][A-Za-z_0-9]*\\b");
    private final JTextPane text = new JTextPane() {
        public boolean getScrollableTracksViewportWidth() {
            return getParent() != null && getUI().getPreferredSize(this).width <= getParent().getWidth();
        }
    };
    private final JScrollPane scroll = new JScrollPane(text);
    private final Gutter gutter = new Gutter();
    private final UndoManager undo = new UndoManager();
    private final Timer syntaxTimer;
    private boolean styling;
    private boolean loading;
    private int errorLine = -1;
    private Object errorHighlight;
    private Runnable changed = () -> {};
    private Runnable caretChanged = () -> {};

    public CodeEditor() {
        super(new BorderLayout());
        setBackground(Theme.EDITOR);
        text.setFont(Theme.CODE);
        text.setBackground(Theme.EDITOR);
        text.setForeground(Theme.TEXT);
        text.setCaretColor(Theme.TEXT);
        text.setSelectionColor(new Color(0x3E5873));
        text.setSelectedTextColor(Color.WHITE);
        text.setBorder(BorderFactory.createEmptyBorder(12, 12, 30, 24));
        text.setFocusTraversalKeysEnabled(false);
        text.putClientProperty(JTextPane.HONOR_DISPLAY_PROPERTIES, true);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.EDITOR);
        scroll.setRowHeaderView(gutter);
        Theme.styleScrollPane(scroll);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        add(scroll);
        syntaxTimer = new Timer(180, event -> highlight());
        syntaxTimer.setRepeats(false);
        text.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { update(); }
            public void removeUpdate(DocumentEvent event) { update(); }
            public void changedUpdate(DocumentEvent event) {}
            private void update() {
                syntaxTimer.restart(); gutter.revalidate(); gutter.repaint();
                clearError();
                if (!loading) changed.run();
            }
        });
        text.getDocument().addUndoableEditListener(event -> { if (!styling && !loading) undo.addEdit(event.getEdit()); });
        text.addCaretListener(event -> { gutter.repaint(); caretChanged.run(); });
        ((AbstractDocument) text.getDocument()).setDocumentFilter(new DocumentFilter() {
            public void insertString(FilterBypass bypass, int offset, String value, AttributeSet attributes) throws BadLocationException {
                replace(bypass, offset, 0, value, attributes);
            }
            public void replace(FilterBypass bypass, int offset, int length, String value, AttributeSet attributes) throws BadLocationException {
                String next = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
                if (bypass.getDocument().getLength() - length + next.length() > 200_000) { java.awt.Toolkit.getDefaultToolkit().beep(); return; }
                bypass.replace(offset, length, next, attributes);
            }
        });
        bind("control Z", "undo", () -> { if (text.isEditable() && undo.canUndo()) undo.undo(); });
        bind("control Y", "redo", () -> { if (text.isEditable() && undo.canRedo()) undo.redo(); });
        bind("control shift Z", "redoAlt", () -> { if (text.isEditable() && undo.canRedo()) undo.redo(); });
        bind("TAB", "indent", () -> indent(false));
        bind("shift TAB", "outdent", () -> indent(true));
        bind("ENTER", "newline", this::newline);
    }

    public JTextPane textPane() { return text; }
    public void onChange(Runnable callback) { changed = callback; }
    public void onCaretChange(Runnable callback) { caretChanged = callback; }
    public String source() { return text.getText(); }
    public void setSource(String source) {
        loading = true;
        try { text.setText(source); text.setCaretPosition(0); undo.discardAllEdits(); highlight(); }
        finally { loading = false; }
    }
    public int line() { return text.getDocument().getDefaultRootElement().getElementIndex(text.getCaretPosition()) + 1; }
    public int column() { return text.getCaretPosition() - text.getDocument().getDefaultRootElement().getElement(line() - 1).getStartOffset() + 1; }
    public void setEditable(boolean editable) { text.setEditable(editable); }
    public void focus() { text.requestFocusInWindow(); }

    public void find(String query, boolean forward) {
        if (query.isEmpty()) return;
        String source = source();
        int index = forward ? source.indexOf(query, text.getSelectionEnd()) : source.lastIndexOf(query, text.getSelectionStart() - 1);
        if (index < 0) index = forward ? source.indexOf(query) : source.lastIndexOf(query);
        if (index >= 0) { text.select(index, index + query.length()); text.getCaret().setSelectionVisible(true); }
    }

    public void showError(int line, int column) {
        clearError();
        Element root = text.getDocument().getDefaultRootElement();
        if (line < 1 || line > root.getElementCount()) return;
        Element element = root.getElement(line - 1);
        errorLine = line;
        try {
            int end = Math.min(element.getEndOffset(), text.getDocument().getLength());
            errorHighlight = text.getHighlighter().addHighlight(element.getStartOffset(), end, new DefaultHighlighter.DefaultHighlightPainter(new Color(0x583B43)));
            text.setCaretPosition(Math.min(end, element.getStartOffset() + Math.max(0, column - 1)));
            Rectangle2D caret = text.modelToView2D(text.getCaretPosition());
            if (caret != null) text.scrollRectToVisible(caret.getBounds());
        } catch (BadLocationException ignored) {}
        gutter.repaint();
    }

    public void clearError() {
        if (errorHighlight != null) text.getHighlighter().removeHighlight(errorHighlight);
        errorHighlight = null; errorLine = -1; gutter.repaint();
    }

    private void highlight() {
        styling = true;
        try {
            StyledDocument document = text.getStyledDocument();
            String source = source();
            document.setCharacterAttributes(0, source.length(), style(Theme.TEXT), true);
            Matcher matcher = TOKENS.matcher(source);
            while (matcher.find()) {
                String token = matcher.group();
                Color color = null;
                if (token.startsWith("//") || token.startsWith("/*")) color = new Color(0x82917E);
                else if (token.startsWith("\"") || token.startsWith("'")) color = new Color(0xD8BE8C);
                else if (token.stripLeading().startsWith("#")) color = new Color(0xC39BDB);
                else if (Character.isDigit(token.charAt(0))) color = new Color(0xC4A2E8);
                else if (KEYWORDS.contains(token)) color = new Color(0xC598CA);
                else if (TYPES.contains(token)) color = new Color(0x79B8DE);
                else if (Set.of("cout", "cin", "endl", "std").contains(token)) color = new Color(0x82C8BF);
                else {
                    int after = matcher.end();
                    while (after < source.length() && Character.isWhitespace(source.charAt(after))) after++;
                    if (after < source.length() && source.charAt(after) == '(') color = new Color(0xDFCA8F);
                }
                if (color != null) document.setCharacterAttributes(matcher.start(), matcher.end() - matcher.start(), style(color), true);
            }
        } finally { styling = false; }
    }

    private SimpleAttributeSet style(Color color) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, color);
        return attributes;
    }
    private void bind(String key, String name, Runnable action) {
        text.getInputMap().put(KeyStroke.getKeyStroke(key), name);
        text.getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private void newline() {
        if (!text.isEditable()) return;
        String source = source();
        int caret = text.getSelectionStart();
        int start = source.lastIndexOf('\n', caret - 1) + 1;
        int end = start;
        while (end < caret && source.charAt(end) == ' ') end++;
        String indent = source.substring(start, end);
        String before = source.substring(start, caret).stripTrailing();
        boolean brace = before.endsWith("{");
        String insertion = "\n" + indent + (brace ? "    " : "");
        int desired = caret + insertion.length();
        if (brace && text.getSelectionEnd() < source.length() && source.charAt(text.getSelectionEnd()) == '}') insertion += "\n" + indent;
        text.replaceSelection(insertion);
        text.setCaretPosition(Math.min(desired, text.getDocument().getLength()));
    }

    private void indent(boolean remove) {
        if (!text.isEditable()) return;
        if (text.getSelectionStart() == text.getSelectionEnd() && !remove) { text.replaceSelection("    "); return; }
        Element root = text.getDocument().getDefaultRootElement();
        int first = root.getElementIndex(text.getSelectionStart());
        int last = root.getElementIndex(Math.max(text.getSelectionStart(), text.getSelectionEnd() - 1));
        int start = root.getElement(first).getStartOffset();
        int end = Math.min(root.getElement(last).getEndOffset(), text.getDocument().getLength());
        String selected = source().substring(start, end);
        String[] lines = selected.split("\n", -1);
        StringBuilder replacement = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i == lines.length - 1 && line.isEmpty() && selected.endsWith("\n")) break;
            if (remove) { int count = 0; while (count < Math.min(4, line.length()) && line.charAt(count) == ' ') count++; line = line.substring(count); }
            else line = "    " + line;
            replacement.append(line);
            if (i < lines.length - 1) replacement.append('\n');
        }
        text.select(start, end); text.replaceSelection(replacement.toString()); text.select(start, start + replacement.length());
    }

    private final class Gutter extends JComponent {
        private static final long serialVersionUID = 1L;
        Gutter() { setFont(Theme.CODE.deriveFont(13f)); setOpaque(true); setBackground(Theme.EDITOR); }
        public Dimension getPreferredSize() {
            int digits = Integer.toString(text.getDocument().getDefaultRootElement().getElementCount()).length();
            return new Dimension(26 + getFontMetrics(getFont()).charWidth('0') * Math.max(3, digits), text.getPreferredSize().height);
        }
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(Theme.EDITOR); g.fillRect(0, 0, getWidth(), getHeight());
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(getFont());
            Element root = text.getDocument().getDefaultRootElement();
            int first = root.getElementIndex(text.viewToModel2D(new Point2D.Double(0, g.getClipBounds().y)));
            FontMetrics metrics = g.getFontMetrics();
            for (int i = first; i < root.getElementCount(); i++) {
                try {
                    Rectangle2D rectangle = text.modelToView2D(root.getElement(i).getStartOffset());
                    if (rectangle == null || rectangle.getY() > g.getClipBounds().getMaxY()) break;
                    String number = Integer.toString(i + 1);
                    g.setColor(i + 1 == errorLine ? Theme.ERROR : i + 1 == line() ? Theme.TEXT : new Color(0x768291));
                    int baseline = (int) rectangle.getY() + text.getFontMetrics(text.getFont()).getAscent();
                    g.drawString(number, getWidth() - metrics.stringWidth(number) - 13, baseline);
                    if (i + 1 == errorLine) g.fillOval(5, baseline - 8, 5, 5);
                } catch (BadLocationException ignored) {}
            }
            g.setColor(new Color(0x303740)); g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            g.dispose();
        }
    }
}
