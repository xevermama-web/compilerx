package compilerx.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicScrollBarUI;

public final class Theme {
    public static final Color BACKGROUND = new Color(0x20252C);
    public static final Color EDITOR = new Color(0x262C34);
    public static final Color PANEL = new Color(0x22272E);
    public static final Color BAR = new Color(0x2B313A);
    public static final Color BORDER = new Color(0x373E48);
    public static final Color TEXT = new Color(0xDCE2EB);
    public static final Color MUTED = new Color(0x9BA6B5);
    public static final Color ACCENT = new Color(0x73C7B0);
    public static final Color BLUE = new Color(0x8EBDF0);
    public static final Color ERROR = new Color(0xF19B9B);
    public static final Font UI = new Font("Dialog", Font.PLAIN, 13);
    public static final Font CODE = new Font("Monospaced", Font.PLAIN, 15);

    private Theme() {}

    public static void install() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) {}
        for (String key : new String[]{"Panel.background", "OptionPane.background", "FileChooser.background", "Viewport.background"}) UIManager.put(key, PANEL);
        for (String key : new String[]{"Label.foreground", "OptionPane.messageForeground", "Button.foreground", "Menu.foreground", "MenuItem.foreground", "CheckBox.foreground"}) UIManager.put(key, TEXT);
        for (String key : new String[]{"Button.background", "ComboBox.background", "TextField.background", "List.background", "Tree.background", "Menu.background", "MenuItem.background", "PopupMenu.background"}) UIManager.put(key, BAR);
        for (String key : new String[]{"TextField.foreground", "TextField.caretForeground", "List.foreground", "Tree.foreground", "ComboBox.foreground"}) UIManager.put(key, TEXT);
        UIManager.put("List.selectionBackground", new Color(0x3C5166));
        UIManager.put("Tree.selectionBackground", new Color(0x3C5166));
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());
        UIManager.put("ScrollBar.width", 11);
        UIManager.put("ScrollBar.background", PANEL);
        UIManager.put("ScrollBar.thumb", new Color(0x49515E));
        UIManager.put("ScrollBar.thumbDarkShadow", new Color(0x49515E));
        UIManager.put("ScrollBar.thumbHighlight", new Color(0x49515E));
        UIManager.put("ToolTip.background", BAR);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("Button.font", UI);
        UIManager.put("Label.font", UI);
        UIManager.put("TextField.font", UI);
        UIManager.put("OptionPane.messageFont", UI);
        UIManager.put("OptionPane.buttonFont", UI);
    }

    public static void styleScrollPane(JScrollPane pane) {
        pane.getViewport().setBackground(EDITOR);
        for (JScrollBar bar : new JScrollBar[]{pane.getVerticalScrollBar(), pane.getHorizontalScrollBar()}) {
            bar.setUI(new BasicScrollBarUI() {
                protected JButton createDecreaseButton(int orientation) { return emptyButton(); }
                protected JButton createIncreaseButton(int orientation) { return emptyButton(); }
                private JButton emptyButton() {
                    JButton button = new JButton();
                    button.setPreferredSize(new java.awt.Dimension(0, 0));
                    button.setMinimumSize(new java.awt.Dimension(0, 0));
                    button.setMaximumSize(new java.awt.Dimension(0, 0));
                    return button;
                }
                protected void paintTrack(Graphics graphics, JComponent component, java.awt.Rectangle bounds) {
                    graphics.setColor(EDITOR); graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                }
                protected void paintThumb(Graphics graphics, JComponent component, java.awt.Rectangle bounds) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(isThumbRollover() ? new Color(0x667180) : new Color(0x4B5562));
                    g.fillRoundRect(bounds.x + 2, bounds.y + 2, Math.max(0, bounds.width - 4), Math.max(0, bounds.height - 4), 6, 6);
                    g.dispose();
                }
            });
        }
        javax.swing.JPanel corner = new javax.swing.JPanel(); corner.setBackground(EDITOR);
        pane.setCorner(JScrollPane.LOWER_RIGHT_CORNER, corner);
    }

    public static JButton button(String title, String icon, boolean primary) {
        JButton button = new JButton(title, icon == null ? null : new Glyph(icon, primary ? BACKGROUND : MUTED, 18));
        button.setFont(UI.deriveFont(primary ? Font.BOLD : Font.PLAIN));
        button.setForeground(primary ? BACKGROUND : TEXT);
        button.setBackground(primary ? ACCENT : BAR);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(primary ? ACCENT : BORDER), BorderFactory.createEmptyBorder(7, 11, 7, 11)));
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        return button;
    }

    public static BufferedImage appIcon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(ACCENT); g.fillRoundRect(0, 0, 64, 64, 16, 16);
        g.setColor(BACKGROUND); g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(23, 21, 12, 32); g.drawLine(12, 32, 23, 43);
        g.drawLine(41, 21, 52, 32); g.drawLine(52, 32, 41, 43); g.drawLine(36, 16, 28, 48);
        g.dispose(); return image;
    }

    public static final class Glyph implements Icon {
        private final String name;
        private final Color color;
        private final int size;
        public Glyph(String name, Color color, int size) { this.name = name; this.color = color; this.size = size; }
        public int getIconWidth() { return size; }
        public int getIconHeight() { return size; }
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(x, y); g.scale(size / 20.0, size / 20.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(component.isEnabled() ? color : new Color(0x65707E));
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (name) {
                case "run" -> { g.drawPolygon(new int[]{6, 6, 15}, new int[]{4, 16, 10}, 3); }
                case "stop" -> g.drawRoundRect(5, 5, 10, 10, 1, 1);
                case "search" -> { g.drawOval(3, 3, 10, 10); g.drawLine(12, 12, 17, 17); }
                case "folder" -> { g.drawPolyline(new int[]{2, 2, 7, 9, 17, 17, 2}, new int[]{16, 5, 5, 7, 7, 16, 16}, 7); g.drawLine(2, 9, 17, 9); }
                case "save" -> { g.drawRect(3, 3, 14, 14); g.drawRect(6, 3, 8, 5); g.drawRect(6, 11, 8, 6); }
                case "new" -> { g.drawPolyline(new int[]{5, 5, 12, 15, 15, 5}, new int[]{17, 3, 3, 6, 17, 17}, 6); g.drawLine(7, 11, 13, 11); g.drawLine(10, 8, 10, 14); }
                case "files" -> { g.drawRect(6, 3, 10, 13); g.drawPolyline(new int[]{4, 3, 3, 12}, new int[]{6, 6, 18, 18}, 4); }
                case "clear" -> { g.drawLine(6, 6, 14, 14); g.drawLine(14, 6, 6, 14); }
                case "terminal" -> { g.drawPolyline(new int[]{4, 8, 4}, new int[]{6, 10, 14}, 3); g.drawLine(10, 15, 16, 15); }
                case "menu" -> { for (int row = 5; row < 17; row += 5) g.drawLine(4, row, 16, row); }
                default -> { g.drawPolyline(new int[]{7, 3, 7}, new int[]{5, 10, 15}, 3); g.drawPolyline(new int[]{13, 17, 13}, new int[]{5, 10, 15}, 3); }
            }
            g.dispose();
        }
    }
}
