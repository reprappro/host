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
 
 RrRectangle: 2D rectangles
 
 First version 20 May 2005
 This version: 1 May 2006 (Now in CVS - no more comments here)
 
 */

package org.reprap.geometry.polygons;

/**
 * A 2D box is an X and a Y interval
 */
public class Rectangle {
    private Interval x;
    private Interval y;

    private Rectangle(final Interval xi, final Interval yi, @SuppressWarnings("unused") final boolean internal) {
        x = xi;
        y = yi;
    }

    /**
     * Copy constructor
     */
    public Rectangle(final Rectangle b) {
        this(new Interval(b.x), new Interval(b.y), true);
    }

    /**
     * Make from any diagonal corners
     */
    public Rectangle(final Point2D a, final Point2D b) {
        this(new Interval(Math.min(a.x(), b.x()), Math.max(a.x(), b.x())), new Interval(Math.min(a.y(), b.y()), Math.max(a.y(),
                b.y())), true);
    }

    /**
     * Make from X and Y intervals
     */
    public Rectangle(final Interval xi, final Interval yi) {
        this(new Interval(xi), new Interval(yi), true);
    }

    Rectangle() {
        this(new Interval(), new Interval(), true);
    }

    public Interval x() {
        return x;
    }

    public Interval y() {
        return y;
    }

    public boolean isEmpty() {
        return x.empty() || y.empty();
    }

    /**
     * Expand the box to incorporate another box or a point
     * 
     * @param a
     */
    public void expand(final Rectangle a) {
        if (a.isEmpty()) {
            return;
        }
        x.expand(a.x);
        y.expand(a.y);
    }

    /**
     * Shrink or grow by a given distance
     * 
     * @param dist
     * @return
     */
    public Rectangle offset(final double dist) {
        return new Rectangle(new Interval(x.low() - dist, x.high() + dist), new Interval(y.low() - dist, y.high() + dist));
    }

    public void expand(final Point2D a) {
        if (isEmpty()) {
            x = new Interval(a.x(), a.x());
            y = new Interval(a.y(), a.y());
        } else {
            x.expand(a.x());
            y.expand(a.y());
        }
    }

    /**
     * Corner points and center
     * 
     * @return NE cornerpoint
     */
    public Point2D ne() {
        return new Point2D(x.high(), y.high());
    }

    /**
     * @return SW cornerpoint
     */
    public Point2D sw() {
        return new Point2D(x.low(), y.low());
    }

    /**
     * @return SE cornerpoint
     */
    public Point2D se() {
        return new Point2D(x.high(), y.low());
    }

    /**
     * @return NW cornerpoint
     */
    public Point2D nw() {
        return new Point2D(x.low(), y.high());
    }

    /**
     * @return Centre point
     */
    private Point2D centre() {
        return new Point2D(x.center(), y.center());
    }

    /**
     * Scale the box by a factor about its center
     * 
     * @param f
     * @return scaled box object
     */
    public Rectangle scale(double f) {
        final Rectangle r = new Rectangle();
        if (isEmpty()) {
            return r;
        }
        f = 0.5 * f;
        final Point2D p = new Point2D(x.length() * f, y.length() * f);
        final Point2D c = centre();
        r.expand(Point2D.add(c, p));
        r.expand(Point2D.sub(c, p));
        return r;
    }

    /**
     * Convert to a string
     */
    @Override
    public String toString() {
        if (isEmpty()) {
            return "<empty>";
        }
        return "<BOX x:" + x.toString() + ", y:" + y.toString() + ">";
    }

    /**
     * Squared diagonal
     * 
     * @return squared diagonal of the box
     */
    double dSquared() {
        if (isEmpty()) {
            return 0;
        }
        return Point2D.dSquared(sw(), ne());
    }

    /**
     * Take a range of parameter values and a line, and find the intersection of
     * that range with the part of the line (if any) in the box.
     * 
     * @param a
     * @param oldRange
     * @return intersection interval
     */
    Interval wipe(final Line a, final Interval oldRange) {
        if (oldRange.empty()) {
            return oldRange;
        }

        Interval range = new Interval(oldRange);

        HalfPlane hp = new HalfPlane(sw(), nw());
        range = hp.wipe(a, range);
        if (range.empty()) {
            return range;
        }

        hp = new HalfPlane(nw(), ne());
        range = hp.wipe(a, range);
        if (range.empty()) {
            return range;
        }

        hp = new HalfPlane(ne(), se());
        range = hp.wipe(a, range);
        if (range.empty()) {
            return range;
        }

        hp = new HalfPlane(se(), sw());
        range = hp.wipe(a, range);
        return range;
    }

    public static Rectangle intersection(final Rectangle a, final Rectangle b) {
        if (a.isEmpty()) {
            return a;
        }
        if (b.isEmpty()) {
            return b;
        }
        return new Rectangle(Interval.intersection(a.x, b.x), Interval.intersection(a.y, b.y));
    }

    public static Rectangle union(final Rectangle a, final Rectangle b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return new Rectangle(Interval.union(a.x, b.x), Interval.union(a.y, b.y));
    }
}
