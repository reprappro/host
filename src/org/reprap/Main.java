package org.reprap;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileFilter;

import org.reprap.geometry.Producer;
import org.reprap.gui.RepRapBuild;
import org.reprap.gui.Utility;
import org.reprap.gui.botConsole.SlicerFrame;
import org.reprap.machines.GCodePrinter;
import org.reprap.utilities.Debug;
import org.reprap.utilities.ExtensionFileFilter;
import org.reprap.utilities.RrDeleteOnExit;

/**
 * Main RepRapProSlicer software overview. Please see http://reprap.org/ for
 * more details.
 */
public class Main {
    public static Main gui;
    public static RrDeleteOnExit cleanUpFiles = new RrDeleteOnExit();

    private Producer producer = null;
    private GCodePrinter printer = null;
    private final JFileChooser chooser = new JFileChooser();
    private JFrame mainFrame;
    private RepRapBuild builder;
    private JCheckBoxMenuItem layerPause;
    private JMenuItem cancelMenuItem;
    private JMenuItem produceProduceB;
    private JSplitPane panel;

    public Main() throws IOException {
        if (listStlFilesOnly()) {
            final FileFilter filter = new ExtensionFileFilter("STL", new String[] { "STL" });
            chooser.setFileFilter(filter);
        }
        printer = new GCodePrinter();
    }

    public void setLayerPause(final boolean state) {
        layerPause.setState(state);
    }

    private boolean listStlFilesOnly() {
        return true;
    }

    private void createAndShowGUI() throws IOException {
        JFrame.setDefaultLookAndFeelDecorated(false);
        mainFrame = new JFrame(
                "RepRap build bed    |     mouse:  left - rotate   middle - zoom   right - translate     |    grid: 20 mm");
        mainFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        // Required so menus float over Java3D
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);

        final JMenuBar menubar = new JMenuBar();
        menubar.add(createMenu());

        produceProduceB = new JMenuItem("Start build...", KeyEvent.VK_B);
        cancelMenuItem = new JMenuItem("Cancel", KeyEvent.VK_P);
        cancelMenuItem.setEnabled(false);
        layerPause = new JCheckBoxMenuItem("Pause before layer");

        // Create the main window area
        // This is a horizontal box layout that includes
        // both the builder and preview screens, one of
        // which may be invisible.

        final Box builderFrame = new Box(BoxLayout.Y_AXIS);
        builderFrame.add(new JLabel("Arrange items to print on the build bed"));
        builder = new RepRapBuild();
        builderFrame.setMinimumSize(new Dimension(0, 0));
        builderFrame.add(builder);

        panel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        panel.setPreferredSize(Utility.getDefaultAppSize());
        panel.setMinimumSize(new Dimension(0, 0));
        panel.setResizeWeight(0.5);
        panel.setOneTouchExpandable(true);
        panel.setContinuousLayout(true);
        panel.setLeftComponent(builderFrame);

        panel.setDividerLocation(panel.getPreferredSize().width);

        mainFrame.getContentPane().add(panel);

        mainFrame.setJMenuBar(menubar);

        mainFrame.pack();
        Utility.positionWindowOnScreen(mainFrame);
        mainFrame.setVisible(true);
    }

    private JMenu createMenu() {
        final JMenu manipMenu = new JMenu("Click here for help");
        manipMenu.setMnemonic(KeyEvent.VK_M);

        final JMenuItem manipX = new JMenuItem("Rotate X 90", KeyEvent.VK_X);
        manipX.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK));
        manipX.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                onRotateX();
            }
        });
        manipMenu.add(manipX);

        final JMenuItem manipY = new JMenuItem("Rotate Y 90", KeyEvent.VK_Y);
        manipY.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, ActionEvent.CTRL_MASK));
        manipY.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                onRotateY();
            }
        });
        manipMenu.add(manipY);

        final JMenuItem manipZ45 = new JMenuItem("Rotate Z 45", KeyEvent.VK_Z);
        manipZ45.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ActionEvent.CTRL_MASK));
        manipZ45.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                onRotateZ(45);
            }
        });
        manipMenu.add(manipZ45);

        final JMenuItem manipZp25 = new JMenuItem("Z Anticlockwise 2.5", KeyEvent.VK_A);
        manipZp25.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, ActionEvent.CTRL_MASK));
        manipZp25.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                onRotateZ(2.5);
            }
        });
        manipMenu.add(manipZp25);

        final JMenuItem manipZm25 = new JMenuItem("Z Clockwise 2.5", KeyEvent.VK_C);
        manipZm25.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        manipZm25.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                onRotateZ(-2.5);
            }
        });
        manipMenu.add(manipZm25);

        final JMenuItem inToMM = new JMenuItem("Scale by 25.4 (in -> mm)", KeyEvent.VK_I);
        inToMM.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, ActionEvent.CTRL_MASK));
        inToMM.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                oninToMM();
            }
        });
        manipMenu.add(inToMM);

        final JMenuItem changeMaterial = new JMenuItem("Change material", KeyEvent.VK_M);
        changeMaterial.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, ActionEvent.CTRL_MASK));
        changeMaterial.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                onChangeMaterial();
            }
        });
        manipMenu.add(changeMaterial);

        final JMenuItem nextP = new JMenuItem("Select next object that will be built", KeyEvent.VK_N);
        nextP.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.CTRL_MASK));
        nextP.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                onNextPicked();
            }
        });
        manipMenu.add(nextP);

        final JMenuItem reorder = new JMenuItem("Reorder the building sequence", KeyEvent.VK_R);
        reorder.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.CTRL_MASK));
        reorder.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                onReorder();
            }
        });
        manipMenu.add(reorder);

        final JMenuItem deleteSTL = new JMenuItem("Delete selected object", KeyEvent.VK_DELETE);
        deleteSTL.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        deleteSTL.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                onDelete();
            }
        });
        manipMenu.add(deleteSTL);
        return manipMenu;
    }

    public GCodePrinter getPrinter() {
        return printer;
    }

    public RepRapBuild getBuilder() {
        return builder;
    }

    public int getLayers() {
        if (producer == null) {
            return 0;
        }
        return producer.getLayers();
    }

    public int getLayer() {
        if (producer == null) {
            return 0;
        }
        return producer.getLayer();
    }

    public void onProduceB() {
        cancelMenuItem.setEnabled(true);
        produceProduceB.setEnabled(false);
        final Thread t = new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("Producer");
                try {

                    if (printer == null) {
                        Debug.e("Production attempted with null printer.");
                    }
                    producer = new Producer(printer, builder);
                    producer.setLayerPause(layerPause);
                    producer.produce();
                    producer = null;
                    cancelMenuItem.setEnabled(false);
                    produceProduceB.setEnabled(true);
                    JOptionPane.showMessageDialog(mainFrame, "Slicing complete - Exit");
                    System.exit(0);
                } catch (final Exception ex) {
                    JOptionPane.showMessageDialog(mainFrame, "Production exception: " + ex);
                    ex.printStackTrace();
                }
            }
        };
        t.start();
    }

    public File onOpen(final String description, final String[] extensions, final String defaultRoot) {
        String result = null;
        File f;
        final FileFilter filter = new ExtensionFileFilter(description, extensions);

        chooser.setFileFilter(filter);
        if (!defaultRoot.contentEquals("") && extensions.length == 1) {
            final File defaultFile = new File(defaultRoot + "." + extensions[0]);
            chooser.setSelectedFile(defaultFile);
        }

        final int returnVal = chooser.showOpenDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            f = chooser.getSelectedFile();
            result = f.getAbsolutePath();
            if (extensions[0].toUpperCase().contentEquals("RFO")) {
                builder.addRFOFile(result);
            }
            if (extensions[0].toUpperCase().contentEquals("STL")) {
                builder.anotherSTLFile(result, true);
            }
            return f;
        }
        return null;
    }

    public String saveRFO(final String fileRoot) throws IOException {
        String result = null;
        File f;
        FileFilter filter;

        final File defaultFile = new File(fileRoot + ".rfo");
        final JFileChooser rfoChooser = new JFileChooser();
        rfoChooser.setSelectedFile(defaultFile);
        filter = new ExtensionFileFilter("RFO file to write to", new String[] { "rfo" });
        rfoChooser.setFileFilter(filter);
        rfoChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        rfoChooser.setFileFilter(filter);

        final int returnVal = rfoChooser.showSaveDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            f = rfoChooser.getSelectedFile();
            result = "file:" + f.getAbsolutePath();

            builder.saveRFOFile(result);
            return f.getName();
        }
        return "";
    }

    public String saveSCAD(final String fileRoot) {
        String result = null;
        File f;
        FileFilter filter;

        final File defaultFile = new File(fileRoot + ".scad");
        final JFileChooser scadChooser = new JFileChooser();
        scadChooser.setSelectedFile(defaultFile);
        filter = new ExtensionFileFilter("Directory to put OpenSCAD files in", new String[] { "" });
        scadChooser.setFileFilter(filter);
        scadChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        scadChooser.setFileFilter(filter);

        final int returnVal = scadChooser.showSaveDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            f = scadChooser.getSelectedFile();
            result = "file:" + f.getAbsolutePath();
            builder.saveSCADFile(result);
            return f.getName();
        }
        return "";
    }

    private void onRotateX() {
        builder.xRotate();
    }

    private void onRotateY() {
        builder.yRotate();
    }

    private void onRotateZ(final double angle) {
        builder.zRotate(angle);
    }

    private void oninToMM() {
        builder.inToMM();
    }

    private void onChangeMaterial() {
        builder.changeMaterial();
    }

    private void onNextPicked() {
        builder.nextPicked();
    }

    private void onReorder() {
        builder.doReorder();
    }

    private void onDelete() {
        builder.deleteSTL();
    }

    public void mouseToWorld() {
        builder.mouseToWorld();
    }

    public static void main(final String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                cleanUpFiles.killThem();
            }
        });
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("RepRap");
                try {
                    gui = new Main();
                    gui.createAndShowGUI();
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
                gui.mainFrame.setFocusable(true);
                gui.mainFrame.requestFocus();
                SlicerFrame.main(null);
            }
        });
    }
}
