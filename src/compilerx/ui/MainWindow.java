package compilerx.ui;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public final class MainWindow extends JFrame {
    private static final long serialVersionUID = 1L;
    public MainWindow() {
        super("Compiler X — C/C++ Workspace");
        WorkspacePanel workspace = new WorkspacePanel();
        setContentPane(workspace);
        setIconImage(Theme.appIcon());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(800, 570));
        setSize(1220, 800);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent event) { if (workspace.canClose()) dispose(); }
        });
        SwingUtilities.invokeLater(workspace::arrange);
    }
}
