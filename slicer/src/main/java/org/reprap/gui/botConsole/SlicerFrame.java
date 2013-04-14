package org.reprap.gui.botConsole;

import java.io.IOException;

import javax.swing.JOptionPane;

import org.reprap.Preferences;
import org.reprap.utilities.Debug;

/**
 * @author Ed Sells, March 2008
 */
public class SlicerFrame extends javax.swing.JFrame {
    private static final long serialVersionUID = 1L;
    private static SlicerFrame bcf = null;
    private Thread pollThread = null;
    private double fractionDone = -1;
    private int layer = -1;
    private int outOf = -1;
    private javax.swing.JTabbedPane jTabbedPane1;
    private org.reprap.gui.botConsole.PrintTabFrame printTabFrame1;
    private int extruderCount;

    public SlicerFrame() {
        try {
            checkPrefs();
        } catch (final Exception e) {
            Debug.e("Failure trying to initialise comms in botConsole: " + e);
            JOptionPane.showMessageDialog(null, e.getMessage());
            return;
        }
        initComponents();
        setTitle("RepRapPro Slicer");

        /*
         * Fork off a thread to keep the panels up-to-date
         */
        pollThread = new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("GUI Poll");
                try {
                    while (true) {
                        Thread.sleep(1500);
                        updateProgress();
                    }
                } catch (final InterruptedException e) {
                    Thread.interrupted();
                }
            }
        };

        pollThread.start();
    }

    public void handleException(final Exception e) {
        throw new RuntimeException(e);
    }

    /**
     * The update thread calls this to update everything that is independent of
     * the RepRap machine.
     * 
     * @param fractionDone
     */
    private void updateProgress() {
        printTabFrame1.updateProgress(fractionDone, layer, outOf);
    }

    public void setFractionDone(final double f, final int l, final int o) {
        if (f >= 0) {
            fractionDone = f;
        }
        if (l >= 0) {
            layer = l;
        }
        if (o >= 0) {
            outOf = o;
        }
    }

    /**
     * "Suspend" and "resume" the poll thread. We don't use the actual suspend
     * call (deprecated anyway) to prevent resource locking.
     */
    public void suspendPolling() {
        try {
            Thread.sleep(200);
        } catch (final InterruptedException ex) {
            Thread.interrupted();
        }
    }

    public void resumePolling() {
        try {
            Thread.sleep(200);
        } catch (final InterruptedException ex) {
            Thread.interrupted();
        }
    }

    private void checkPrefs() throws IOException {
        extruderCount = Preferences.loadGlobalInt("NumberOfExtruders");
        if (extruderCount < 1) {
            throw new RuntimeException("A Reprap printer must contain at least one extruder");
        }
    }

    private void initComponents() {
        jTabbedPane1 = new javax.swing.JTabbedPane();
        printTabFrame1 = new PrintTabFrame();
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        jTabbedPane1.setRequestFocusEnabled(false);
        jTabbedPane1.addTab("Slice", printTabFrame1);

        final org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(
                layout.createSequentialGroup()
                        .add(jTabbedPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 670,
                                org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).addContainerGap(5, Short.MAX_VALUE)));
        layout.setVerticalGroup(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(
                layout.createSequentialGroup()
                        .add(jTabbedPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 430,
                                org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).addContainerGap(5, Short.MAX_VALUE)));

        pack();
    }

    public static void main(final String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                bcf = new SlicerFrame();
                bcf.setVisible(true);
                bcf.printTabFrame1.setConsoleFrame(bcf);
            }
        });
    }

    public static SlicerFrame getBotConsoleFrame() {
        return bcf;
    }

    public static PrintTabFrame getPrintTabFrame() {
        return bcf.printTabFrame1;
    }
}
