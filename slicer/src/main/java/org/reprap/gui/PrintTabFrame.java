package org.reprap.gui;

import java.awt.Color;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;

import org.reprap.Main;
import org.reprap.Preferences;

/**
 * @author ensab
 */
public class PrintTabFrame extends JInternalFrame {
    private static final long serialVersionUID = 1L;
    private SlicerFrame parentBotConsoleFrame = null;
    private long startTime = -1;
    private int oldLayer = -1;
    private String loadedFiles = "";
    private boolean loadedFilesLong = false;
    private boolean gcodeLoaded = false;
    private boolean slicing = false;
    private Thread printerFilePlay;
    private boolean SLoadOK = false;
    private JLabel currentLayerOutOfN;
    private JProgressBar progressBar;
    private JLabel expectedBuildTime;
    private JLabel expectedFinishTime;
    private AbstractButton sliceButton;
    private AbstractButton layerPauseCheck;
    private JLabel fileNameBox;
    private JButton loadSTL;
    private JButton loadRFO;
    private JButton saveRFO;
    private JButton saveSCAD;
    private AbstractButton displayPathsCheck;

    /** Creates new form PrintTabFrame */
    PrintTabFrame() {
        initComponents();
        printerFilePlay = null;
        enableSLoad();
    }

    /**
     * Keep the user amused. If fractionDone is negative, the function queries
     * the layer statistics. If it is 0 or positive, the function uses it.
     * 
     * @param fractionDone
     */
    void updateProgress(double fractionDone, int layer, int layers) {
        if (layer >= 0) {
            currentLayerOutOfN.setText("" + layer + "/" + layers);
        }

        if (layers < 0) {
            layers = org.reprap.Main.gui.getLayers();
        }

        if (layer < 0) {
            layer = org.reprap.Main.gui.getLayer();
            if (layer >= 0) {
                currentLayerOutOfN.setText("" + layer + "/" + layers);
            }
        }

        if (fractionDone < 0) {
            if (layer == oldLayer) {
                return;
            }

            final boolean topDown = layer < oldLayer;
            oldLayer = layer;
            if (topDown) {
                fractionDone = (double) (layers - layer) / (double) layers;
            } else {
                fractionDone = (double) layer / (double) layers;
            }
        }

        progressBar.setMinimum(0);
        progressBar.setMaximum(100);
        progressBar.setValue((int) (100 * fractionDone));

        final GregorianCalendar cal = new GregorianCalendar();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("EE HH:mm:ss Z");
        final Date d = cal.getTime();
        final long e = d.getTime();
        if (startTime < 0) {
            startTime = e;
            return;
        }

        final long f = (long) ((e - startTime) / fractionDone);
        final int h = (int) (f / 60000) / 60;
        final int m = (int) (f / 60000) % 60;

        if (m > 9) {
            expectedBuildTime.setText("" + h + ":" + m);
        } else {
            expectedBuildTime.setText("" + h + ":0" + m);
        }
        expectedFinishTime.setText(dateFormat.format(new Date(startTime + f)));

        if (printerFilePlay != null) {
            if (!printerFilePlay.isAlive()) {
                printDone();
            }
        }
    }

    /**
     * So the BotConsoleFrame can let us know who it is
     * 
     * @param b
     */
    void setConsoleFrame(final SlicerFrame b) {
        parentBotConsoleFrame = b;
    }

    private void initComponents() {
        final JButton variablesButton = new JButton();
        variablesButton.setActionCommand("preferences");
        variablesButton.setBackground(new java.awt.Color(255, 102, 255));
        variablesButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                preferences();
            }
        });
        variablesButton.setText("Variables");

        final JButton helpButton = new JButton();
        helpButton.setActionCommand("Help");
        helpButton.setBackground(new java.awt.Color(255, 102, 255));
        helpButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                help();
            }
        });
        helpButton.setText("   Help   ");

        sliceButton = new JButton();
        final JButton exitButton = new JButton();
        loadSTL = new JButton();
        loadRFO = new JButton();
        saveRFO = new JButton();
        saveSCAD = new JButton();

        sliceButton.setText("Slice");
        exitButton.setText("Exit");
        loadSTL.setText("Load STL/CSG");
        loadRFO.setText("Load RFO");
        saveRFO.setText("Save RFO");
        saveSCAD.setText("Save SCAD");

        layerPauseCheck = new JCheckBox();
        layerPause(false);
        final JButton getWebPage = new JButton();
        final JLabel expectedBuildTimeLabel = new JLabel();
        final JLabel filesLabel = new JLabel();
        expectedBuildTime = new JLabel();
        final JLabel expectedFinishTimeLabel = new JLabel();
        final JLabel changeMachineLabel = new JLabel();
        expectedFinishTime = new JLabel();
        final JLabel progressLabel = new JLabel();
        currentLayerOutOfN = new JLabel();
        progressBar = new JProgressBar();
        fileNameBox = new JLabel();

        displayPathsCheck = new JCheckBox();
        displayPaths(false);

        sliceButton.setBackground(new java.awt.Color(51, 204, 0));
        sliceButton.setFont(sliceButton.getFont());
        sliceButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                try {
                    sliceButtonActionPerformed();
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        exitButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                try {
                    exitButtonActionPerformed();
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        layerPauseCheck.setText("Pause between layers");
        layerPauseCheck.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                layerPauseCheckActionPerformed();
            }
        });

        getWebPage.setIcon(new ImageIcon(ClassLoader.getSystemResource("reprappro_logo-0.5.png")));
        getWebPage.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                getWebPageActionPerformed();
            }
        });

        expectedBuildTimeLabel.setFont(new java.awt.Font("Tahoma", 0, 12));
        expectedBuildTimeLabel.setText("Expected slice time:");

        filesLabel.setFont(new java.awt.Font("Tahoma", 0, 12));
        filesLabel.setText("File(s): ");

        expectedBuildTime.setFont(new java.awt.Font("Tahoma", 0, 12));
        expectedBuildTime.setText("00:00");

        expectedFinishTimeLabel.setFont(new java.awt.Font("Tahoma", 0, 12));
        expectedFinishTimeLabel.setText("Expected to finish at:");

        expectedFinishTime.setFont(new java.awt.Font("Tahoma", 0, 12));
        expectedFinishTime.setText("    -");

        changeMachineLabel.setFont(new java.awt.Font("Tahoma", 0, 15));
        changeMachineLabel.setText("RepRap in use: " + Preferences.getActiveMachineName());

        progressLabel.setFont(new java.awt.Font("Tahoma", 0, 12));
        progressLabel.setText("Top down layer progress:");

        currentLayerOutOfN.setFont(new java.awt.Font("Tahoma", 0, 12));
        currentLayerOutOfN.setHorizontalAlignment(SwingConstants.RIGHT);
        currentLayerOutOfN.setText("000/000");

        loadSTL.setActionCommand("loadSTL");
        loadSTL.setBackground(new java.awt.Color(0, 204, 255));
        loadSTL.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                loadSTL();
            }
        });

        loadRFO.setActionCommand("loadRFO");
        loadRFO.setBackground(new java.awt.Color(0, 204, 255));
        loadRFO.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                loadRFO();
            }
        });

        fileNameBox.setFont(new java.awt.Font("Tahoma", 0, 12));
        fileNameBox.setText(" - ");

        saveRFO.setActionCommand("saveRFO");
        saveRFO.setBackground(new java.awt.Color(153, 153, 153));
        saveRFO.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                try {
                    saveRFO();
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        saveSCAD.setActionCommand("saveSCAD");
        saveSCAD.setBackground(new java.awt.Color(153, 153, 153));
        saveSCAD.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                saveSCAD();
            }
        });

        displayPathsCheck.setText("Show paths when slicing");
        displayPathsCheck.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(final java.awt.event.MouseEvent evt) {
                displayPathsCheckMouseClicked();
            }
        });

        createLayout(variablesButton, helpButton, exitButton, getWebPage, expectedBuildTimeLabel, filesLabel,
                expectedFinishTimeLabel, changeMachineLabel, progressLabel);
    }

    private void createLayout(final JButton variablesButton, final JButton helpButton, final JButton exitButton,
            final JButton getWebPage, final JLabel expectedBuildTimeLabel, final JLabel filesLabel,
            final JLabel expectedFinishTimeLabel, final JLabel changeMachineLabel, final JLabel progressLabel) {
        final org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);

        layout.setHorizontalGroup(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(
                layout.createSequentialGroup()
                        .addContainerGap()
                        .add(layout
                                .createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                .add(layout
                                        .createSequentialGroup()
                                        .add(layout
                                                .createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                                .add(exitButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 130,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                .add(sliceButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 130,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                .add(saveRFO, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 130,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                .add(saveSCAD, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 130,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                .add(loadRFO, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 130,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                .add(loadSTL, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 130,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                        .add(50, 50, 50)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(layout
                                                .createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                                .add(layerPauseCheck)
                                                .add(displayPathsCheck)
                                                .add(changeMachineLabel)
                                                .add(layout
                                                        .createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                                                        .add(layout
                                                                .createSequentialGroup()
                                                                .add(variablesButton,
                                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 130,
                                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED,
                                                                        org.jdesktop.layout.GroupLayout.DEFAULT_SIZE,
                                                                        Short.MAX_VALUE)
                                                                .add(20, 20, 20)
                                                                .add(helpButton,
                                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE,
                                                                        org.jdesktop.layout.GroupLayout.DEFAULT_SIZE,
                                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                                .add(100, 100, 100)
                                                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED,
                                                                        org.jdesktop.layout.GroupLayout.DEFAULT_SIZE,
                                                                        Short.MAX_VALUE)
                                                                .add(getWebPage,
                                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 110,
                                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED))
                                .add(layout
                                        .createSequentialGroup()
                                        .add(layout
                                                .createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                                .add(layout.createSequentialGroup().add(expectedBuildTimeLabel)
                                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                                        .add(expectedBuildTime))
                                                .add(layout.createSequentialGroup().add(expectedFinishTimeLabel).add(7, 7, 7)
                                                        .add(expectedFinishTime))
                                                .add(layout.createSequentialGroup().add(progressLabel).add(7, 7, 7)
                                                        .add(currentLayerOutOfN)))
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(layout
                                                .createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                                                .add(layout
                                                        .createSequentialGroup()

                                                        .add(50, 50, 50)
                                                        .add(filesLabel)
                                                        .add(fileNameBox, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE,
                                                                org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                                .add(progressBar, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 350,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                        .addContainerGap(29, Short.MAX_VALUE)));

        layout.setVerticalGroup(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(
                layout.createSequentialGroup()
                        .addContainerGap()
                        .add(layout
                                .createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                .add(layout
                                        .createSequentialGroup()
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(getWebPage, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 72,
                                                org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                .add(layout
                                        .createSequentialGroup()
                                        .add(layout
                                                .createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                                .add(layout
                                                        .createSequentialGroup()
                                                        .add(loadRFO, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 41,
                                                                org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                                        .add(saveRFO, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 41,
                                                                org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                                        .add(layout
                                                                .createParallelGroup()
                                                                .add(loadSTL, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE,
                                                                        41, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                                .add(layout
                                                                        .createSequentialGroup()
                                                                        .add(layerPauseCheck)
                                                                        .addPreferredGap(
                                                                                org.jdesktop.layout.LayoutStyle.RELATED)
                                                                        .add(displayPathsCheck)))
                                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                                        .add(saveSCAD, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 41,
                                                                org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                                        .add(layout
                                                                .createParallelGroup()
                                                                .add(sliceButton,
                                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 39,
                                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                                .add(layout.createSequentialGroup().add(changeMachineLabel)))
                                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                                        .add(exitButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 39,
                                                                org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                                .add(variablesButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 45,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                .add(helpButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 45,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(
                                                layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING).add(
                                                        layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                                                .add(layout.createSequentialGroup()))))))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout
                                .createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                                .add(expectedBuildTimeLabel)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                        .add(expectedBuildTime).add(filesLabel).add(fileNameBox)))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING).add(expectedFinishTimeLabel)
                                .add(expectedFinishTime))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout
                                .createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING).add(progressLabel)
                                        .add(currentLayerOutOfN))
                                .add(progressBar, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE,
                                        org.jdesktop.layout.GroupLayout.DEFAULT_SIZE,
                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)).addContainerGap(24, Short.MAX_VALUE)));
    }

    private void printLive(final boolean p) {
        slicing = true;
        if (p) {
            sliceButton.setText("Printing...");
        } else {
            sliceButton.setText("Slicing...");
        }
        sliceButton.setBackground(Color.gray);
    }

    private void restoreSliceButton() {
        slicing = false;
        sliceButton.setText("Slice");
        sliceButton.setBackground(new java.awt.Color(51, 204, 0));
        printerFilePlay = null;
    }

    private void printDone() {
        restoreSliceButton();
        final String[] options = { "Exit" };
        JOptionPane.showOptionDialog(null, "The file has been processed.", "Message", JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
    }

    private boolean worthSaving() {
        return true;
    }

    private void sliceButtonActionPerformed() throws IOException {
        if (slicing) {
            return;
        }

        if (worthSaving()) {
            final int toDo = JOptionPane.showConfirmDialog(null, "First save the build as an RFO file?");
            switch (toDo) {
            case JOptionPane.YES_OPTION:
                saveRFO();
                break;

            case JOptionPane.NO_OPTION:
                break;

            case JOptionPane.CANCEL_OPTION:
                return;

            default:
                saveRFO();
            }
        }

        printLive(false);

        parentBotConsoleFrame.suspendPolling();
        parentBotConsoleFrame.setFractionDone(-1, -1, -1);
        org.reprap.Main.gui.mouseToWorld();
        int sp = -1;
        if (loadedFiles != null) {
            sp = loadedFiles.length();
        }
        if (sp <= 0) {
            JOptionPane.showMessageDialog(null, "There are no STLs/RFOs loaded to slice to file.");
            restoreSliceButton();
            return;
        }
        sp = Math.max(loadedFiles.indexOf(".stl"),
                Math.max(loadedFiles.indexOf(".STL"), Math.max(loadedFiles.indexOf(".rfo"), loadedFiles.indexOf(".RFO"))));
        if (sp <= 0) {
            JOptionPane.showMessageDialog(null, "The loaded file is not an STL or an RFO file.");
        }
        if (Main.gui.getPrinter().setGCodeFileForOutput(loadedFiles.substring(0, sp)) == null) {
            restoreSliceButton();
            return;
        }
        org.reprap.Main.gui.onProduceB();
    }

    private void exitButtonActionPerformed() throws IOException {
        if (worthSaving()) {
            final int toDo = JOptionPane.showConfirmDialog(null, "First save the build as an RFO file?");
            switch (toDo) {
            case JOptionPane.YES_OPTION:
                saveRFO();
                break;

            case JOptionPane.NO_OPTION:
                break;

            case JOptionPane.CANCEL_OPTION:
                return;

            default:
                saveRFO();
            }
        }
        System.exit(0);
    }

    private void layerPause(final boolean p) {
        org.reprap.Main.gui.setLayerPause(p);
    }

    private void layerPauseCheckActionPerformed() {
        org.reprap.Main.gui.setLayerPause(layerPauseCheck.isSelected());
    }

    private void getWebPageActionPerformed() {
        try {
            final URI url = new URI("http://reprappro.com");
            Desktop.getDesktop().browse(url);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } catch (final URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadSTL() {
        if (!SLoadOK) {
            return;
        }
        if (gcodeLoaded) {
            final int response = JOptionPane
                    .showOptionDialog(null, "This will abandon the G Code file you loaded.", "Load STL",
                            JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null, new String[] { "OK", "Cancel" }, "");
            if (response == 1) {
                return;
            }
            loadedFiles = "";
        }
        final File stlFile = Main.gui.onOpen("STL triangulation file", new String[] { "stl" }, "");
        if (loadedFilesLong) {
            return;
        }
        if (loadedFiles.length() > 50) {
            loadedFiles += "...";
            loadedFilesLong = true;
        } else {
            loadedFiles += stlFile.getName() + " ";
        }

        fileNameBox.setText(loadedFiles);
        gcodeLoaded = false;
    }

    private void loadRFO() {
        if (!SLoadOK) {
            return;
        }
        if (gcodeLoaded) {
            final int response = JOptionPane
                    .showOptionDialog(null, "This will abandon the previous GCode file you loaded.", "Load RFO",
                            JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null, new String[] { "OK", "Cancel" }, "");
            if (response == 1) {
                return;
            }
            loadedFiles = "";
        }

        final File rfoFile = Main.gui.onOpen("RFO multiple-object file", new String[] { "rfo" }, "");

        if (loadedFilesLong) {
            return;
        }
        if (loadedFiles.length() > 50) {
            loadedFiles += "...";
            loadedFilesLong = true;
        } else {
            loadedFiles += rfoFile.getName() + " ";
        }

        fileNameBox.setText(loadedFiles);
        gcodeLoaded = false;
    }

    private void preferences() {
        final org.reprap.gui.Preferences prefs = new org.reprap.gui.Preferences();
        prefs.setVisible(true);
    }

    private void help() {
        try {
            final URI url = new URI("http://reprap.org/wiki/RepRapPro_Slicer");
            Desktop.getDesktop().browse(url);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private void saveRFO() throws IOException {
        if (!SLoadOK) {
            return;
        }
        if (loadedFiles.contentEquals("")) {
            JOptionPane.showMessageDialog(null, "There's nothing to save...");
            return;
        }
        final int sp = Math.max(loadedFiles.indexOf(".stl"),
                Math.max(loadedFiles.indexOf(".STL"), Math.max(loadedFiles.indexOf(".rfo"), loadedFiles.indexOf(".RFO"))));
        if (sp <= 0) {
            JOptionPane.showMessageDialog(null, "The loaded file is not an STL or an RFO file.");
        }
        Main.gui.saveRFO(loadedFiles.substring(0, sp));
    }

    private void saveSCAD() {
        if (!SLoadOK) {
            return;
        }
        if (loadedFiles.contentEquals("")) {
            JOptionPane.showMessageDialog(null, "There's nothing to save...");
            return;
        }
        final int sp = Math.max(loadedFiles.indexOf(".stl"),
                Math.max(loadedFiles.indexOf(".STL"), Math.max(loadedFiles.indexOf(".rfo"), loadedFiles.indexOf(".RFO"))));
        if (sp <= 0) {
            JOptionPane.showMessageDialog(null, "The loaded file is not an STL or an RFO file.");
        }
        org.reprap.Main.gui.saveSCAD(loadedFiles.substring(0, sp));
    }

    private void displayPaths(final boolean disp) {
        org.reprap.Preferences.setSimulate(disp);
    }

    private void displayPathsCheckMouseClicked() {
        displayPaths(displayPathsCheck.isSelected());
    }

    private void enableSLoad() {
        SLoadOK = true;
        loadSTL.setBackground(new java.awt.Color(0, 204, 255));
        loadRFO.setBackground(new java.awt.Color(0, 204, 255));
        saveRFO.setBackground(new java.awt.Color(0, 204, 255));
        saveSCAD.setBackground(new java.awt.Color(0, 204, 255));
    }
}
