package org.reprap.gui;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;

import javax.swing.JFrame;

public class Utility {

    public static void centerWindowOnScreen(final Window w) {
        final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        w.setLocation((screenSize.width - w.getSize().width) / 2, (screenSize.height - w.getSize().height) / 2);
    }

    public static Dimension getDefaultAppSize() {
        return new Dimension(1000, 800);
    }

    public static void positionWindowOnScreen(final Window w) {
        centerWindowOnScreen(w);
    }

    public static void centerWindowOnParent(final Window w, final JFrame parent) {
        final Rectangle bounds = parent.getBounds();
        final int cx = bounds.x + bounds.width / 2;
        final int cy = bounds.y + bounds.height / 2;
        final Dimension mySize = w.getSize();
        w.setLocation(cx - mySize.width / 2, cy - mySize.height / 2);
    }
}
