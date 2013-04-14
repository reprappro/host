/*
 
 RepRap
 ------
 
 The Replicating Rapid Prototyper Project
 
 
 Copyright (C) 2005
 Adrian Bowyer & The University of Bath
 
 http://reprap.org
 
 Principal author:
 
 Adrian Bowyer
 Department of Mechanical Engineering
 Faculty of Engineering and Design
 University of Bath
 Bath BA2 7AY
 U.K.
 
 e-mail: A.Bowyer@bath.ac.uk
 
 RepRap is free; you can redistribute it and/or
 modify it under the terms of the GNU Library General Public
 Licence as published by the Free Software Foundation; either
 version 2 of the Licence, or (at your option) any later version.
 
 RepRap is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Library General Public Licence for more details.
 
 For this purpose the words "software" and "library" in the GNU Library
 General Public Licence are taken to mean any and all computer programs
 computer files data results documents and other copyright information
 available from the RepRap project.
 
 You should have received a copy of the GNU Library General Public
 Licence along with RepRap; if not, write to the Free
 Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA,
 or see
 
 http://www.gnu.org/
 
 =====================================================================
 
 
 RrGraphics: Simple 2D graphics
 
 First version 20 May 2005
 This version: 1 May 2006 (Now in CVS - no more comments here)
 
 */

package org.reprap.utilities;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.media.j3d.Appearance;
import javax.media.j3d.Material;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.vecmath.Color3f;

import org.reprap.Attributes;
import org.reprap.geometry.polygons.BooleanGrid;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polygons.Rectangle;

/**
 * Class to plot images of geometrical structures for debugging.
 * 
 * @author ensab
 */
public class RrGraphics extends JComponent {
    private static final Color BOX_COLOR = Color.blue;

    /**
     * Pixels
     */
    private final int frame = 600;
    private int frameWidth;
    private int frameHeight;
    private PolygonList p_list = null;

    /**
     * The layer being built
     */
    private String layerNumber;
    private final BooleanGrid bg = null;
    private boolean csgSolid = true;
    private double scale;
    private Point2D p_0;
    private Point2D pos;
    private Rectangle scaledBox, originalBox;
    private JFrame jframe;
    private boolean plot_box = false;

    private String title = "RepRap diagnostics";

    private boolean initialised = false;

    /**
     * Constructor for nothing - add stuff later
     */
    public RrGraphics(final String t) {
        p_list = null;
        title = t;
        initialised = false;
        layerNumber = "0";
    }

    public void cleanPolygons(final String ln) {
        p_list = null;
        layerNumber = ln;
    }

    private void setScales(final Rectangle b) {
        scaledBox = b.scale(1.2);

        final double width = scaledBox.x().length();
        final double height = scaledBox.y().length();
        if (width > height) {
            frameWidth = frame;
            frameHeight = (int) (0.5 + (frameWidth * height) / width);
        } else {
            frameHeight = frame;
            frameWidth = (int) (0.5 + (frameHeight * width) / height);
        }
        final double xs = frameWidth / width;
        final double ys = frameHeight / height;

        if (xs < ys) {
            scale = xs;
        } else {
            scale = ys;
        }

        p_0 = new Point2D((frameWidth - (width + 2 * scaledBox.x().low()) * scale) * 0.5,
                (frameHeight - (height + 2 * scaledBox.y().low()) * scale) * 0.5);

        pos = new Point2D(width * 0.5, height * 0.5);
    }

    private void plotBar(final Graphics2D g2d) {
        g2d.setColor(BOX_COLOR);
        Point2D p = new Point2D(scaledBox.ne().x() - 12, scaledBox.sw().y() + 5);
        move(p);
        p = new Point2D(scaledBox.ne().x() - 2, scaledBox.sw().y() + 5);
        plot(g2d, p);
    }

    public void init(final Rectangle b, final String ln) {
        originalBox = b;
        setScales(b);

        jframe = new JFrame();
        jframe.setSize(frameWidth, frameHeight);
        jframe.getContentPane().add(this);
        jframe.setTitle(title);
        jframe.setVisible(true);
        jframe.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        jframe.addMouseListener(new myMouse());
        jframe.addKeyListener(new myKB());
        jframe.setIgnoreRepaint(false);

        initialised = true;

        layerNumber = ln;
    }

    public boolean isInitialised() {
        return initialised;
    }

    public void add(final PolygonList pl) {
        if (pl == null) {
            return;
        }
        if (pl.size() <= 0) {
            return;
        }
        if (p_list == null) {
            p_list = new PolygonList(pl);
        } else {
            p_list.add(pl);
        }
        jframe.repaint();
    }

    /**
     * Real-world coordinates to pixels
     */
    private Point2D transform(final Point2D p) {
        return new Point2D(p_0.x() + scale * p.x(), frameHeight - (p_0.y() + scale * p.y()));
    }

    /**
     * Pixels to real-world coordinates
     */
    private Point2D iTransform(final int x, final int y) {
        return new Point2D((x - p_0.x()) / scale, ((frameHeight - y) - p_0.y()) / scale);
    }

    /**
     * Move invisibly to a point
     */
    private void move(final Point2D p) {
        pos = transform(p);
    }

    /**
     * Draw a straight line to a point
     */
    private void plot(final Graphics2D g2d, final Point2D p) {
        final Point2D a = transform(p);
        g2d.drawLine((int) Math.round(pos.x()), (int) Math.round(pos.y()), (int) Math.round(a.x()), (int) Math.round(a.y()));
        pos = a;
    }

    /**
     * Plot a box
     */
    private void plot(final Graphics2D g2d, final Rectangle b) {
        if (Rectangle.intersection(b, scaledBox).empty()) {
            return;
        }

        g2d.setColor(BOX_COLOR);
        move(b.sw());
        plot(g2d, b.nw());
        plot(g2d, b.ne());
        plot(g2d, b.se());
        plot(g2d, b.sw());
    }

    /**
     * Set the colour from a RepRap attribute
     */
    private void setColour(final Graphics2D g2d, final Attributes at) {
        final Appearance ap = at.getAppearance();
        final Material mt = ap.getMaterial();
        final Color3f col = new Color3f();
        mt.getDiffuseColor(col);
        g2d.setColor(col.get());
    }

    /**
     * Plot a polygon
     */
    private void plot(final Graphics2D g2d, final Polygon p) {
        if (p.size() <= 0) {
            return;
        }
        if (Rectangle.intersection(p.getBox(), scaledBox).empty()) {
            return;
        }
        if (p.getAttributes().getAppearance() == null) {
            Debug.getInstance().errorMessage("RrGraphics: polygon with size > 0 has null appearance.");
            return;
        }

        setColour(g2d, p.getAttributes());
        move(p.point(0));
        for (int i = 1; i < p.size(); i++) {
            plot(g2d, p.point(i));
        }
        if (p.isClosed()) {
            g2d.setColor(Color.RED);
            plot(g2d, p.point(0));
        }
    }

    /**
     * Fill a Boolean Grid where it's solid.
     */
    private void fillBG(final Graphics2D g2d, final BooleanGrid b) {
        if (b.attribute().getAppearance() == null) {
            Debug.getInstance().errorMessage("RrGraphics: booleanGrid has null appearance.");
            return;
        }

        setColour(g2d, b.attribute());
        for (int x = 0; x < frameWidth; x++) {
            for (int y = 0; y < frameHeight; y++) {
                final boolean v = b.get(iTransform(x, y));
                if (v) {
                    g2d.drawRect(x, y, 1, 1); // Is this really the most efficient way?
                }
            }
        }
    }

    /**
     * Master plot function - draw everything
     */
    private void plot(final Graphics2D g2d) {
        plotBar(g2d);
        if (bg != null) {
            fillBG(g2d, bg);
        }

        if (p_list != null) {
            final int leng = p_list.size();
            for (int i = 0; i < leng; i++) {
                plot(g2d, p_list.polygon(i));
            }
            if (plot_box) {
                for (int i = 0; i < leng; i++) {
                    plot(g2d, p_list.polygon(i).getBox());
                }
            }
        }
        jframe.setTitle(title + ", layer: " + layerNumber);
    }

    class myKB implements KeyListener {
        @Override
        public void keyTyped(final KeyEvent k) {
            switch (k.getKeyChar()) {
            case 'b':
            case 'B':
                plot_box = !plot_box;
                break;

            case 's':
            case 'S':
                csgSolid = !csgSolid;

            default:
            }
            jframe.repaint();
        }

        @Override
        public void keyPressed(final KeyEvent k) {
        }

        @Override
        public void keyReleased(final KeyEvent k) {
        }
    }

    /**
     * Clicking the mouse magnifies
     */
    class myMouse implements MouseListener {
        private Rectangle magBox(final Rectangle b, final int ix, final int iy) {
            final Point2D cen = iTransform(ix, iy);
            //System.out.println("Mouse: " + cen.toString() + "; box: " +  scaledBox.toString());
            final Point2D off = new Point2D(b.x().length() * 0.05, b.y().length() * 0.05);
            return new Rectangle(Point2D.sub(cen, off), Point2D.add(cen, off));
        }

        @Override
        public void mousePressed(final MouseEvent e) {
        }

        @Override
        public void mouseReleased(final MouseEvent e) {
        }

        @Override
        public void mouseEntered(final MouseEvent e) {
        }

        @Override
        public void mouseExited(final MouseEvent e) {
        }

        @Override
        public void mouseClicked(final MouseEvent e) {
            final int ix = e.getX() - 5; // Why needed??
            final int iy = e.getY() - 25; //  "     "

            switch (e.getButton()) {
            case MouseEvent.BUTTON1:
                setScales(magBox(scaledBox, ix, iy));
                break;

            case MouseEvent.BUTTON2:

                break;

            case MouseEvent.BUTTON3:

            default:
                setScales(originalBox);
            }
            jframe.repaint();
        }
    }

    @Override
    public void paint(final Graphics g) {
        plot((Graphics2D) g);
    }
}
