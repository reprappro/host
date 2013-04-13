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

package org.reprap.geometry.polyhedra;

import org.reprap.geometry.polygons.Interval;

/**
 * A 2D box is an X and a Y interval
 */
public class Box {
    /**
     * Compass directions
     */
    public static final byte rr_N = 1;
    public static final byte rr_E = 2;
    public static final byte rr_S = 4;
    public static final byte rr_W = 8;
    public static final byte rr_U = 16;
    public static final byte rr_D = 32;
    /**
     * X interval
     */
    private Interval x = null;

    /**
     * Y interval
     */
    private Interval y = null;

    /**
     * Z interval
     */
    private Interval z = null;

    /**
     * Anyone home?
     */
    private boolean empty;

    /**
     * Default is empty
     */
    public Box() {
        empty = true;
    }

    /**
     * Copy constructor
     */
    public Box(final Box b) {
        x = new Interval(b.x);
        y = new Interval(b.y);
        z = new Interval(b.z);
        empty = b.empty;
    }

    /**
     * Make from any diagonal corners
     */
    public Box(final Point3D a, final Point3D b) {
        x = new Interval(Math.min(a.x(), b.x()), Math.max(a.x(), b.x()));
        y = new Interval(Math.min(a.y(), b.y()), Math.max(a.y(), b.y()));
        y = new Interval(Math.min(a.z(), b.z()), Math.max(a.z(), b.z()));
        empty = x.empty() || y.empty() || z.empty();
    }

    /**
     * Make from X and Y intervals
     */
    public Box(final Interval xi, final Interval yi, final Interval zi) {
        x = new Interval(xi);
        y = new Interval(yi);
        z = new Interval(zi);
        empty = x.empty() || y.empty() || z.empty();
    }

    /**
     * @return Return the x interval
     */
    public Interval x() {
        return x;
    }

    /**
     * @return Return the y interval
     */
    public Interval y() {
        return y;
    }

    /**
     * @return Return the z interval
     */
    public Interval z() {
        return z;
    }

    public boolean empty() {
        return empty;
    }

    /**
     * Expand the box to incorporate another box or a point
     */
    public void expand(final Box a) {
        if (a.empty) {
            return;
        }
        if (empty) {
            empty = false;
            x = new Interval(a.x);
            y = new Interval(a.y);
            z = new Interval(a.z);
        } else {
            x.expand(a.x);
            y.expand(a.y);
            z.expand(a.z);
        }
    }

    /**
     * Shrink or grow by a given distance
     */
    public Box offset(final double dist) {
        return new Box(new Interval(x.low() - dist, x.high() + dist), new Interval(y.low() - dist, y.high() + dist),
                new Interval(z.low() - dist, z.high() + dist));
    }

    /**
     * Move somewhere else
     */
    public Box translate(final Point3D p) {
        return new Box(new Interval(x.low() + p.x(), x.high() + p.x()), new Interval(y.low() + p.y(), y.high() + p.y()),
                new Interval(z.low() + p.z(), z.high() + p.z()));
    }

    public void expand(final Point3D a) {
        if (empty) {
            empty = false;
            x = new Interval(a.x(), a.x());
            y = new Interval(a.y(), a.y());
            z = new Interval(a.z(), a.z());
        } else {
            x.expand(a.x());
            y.expand(a.y());
            z.expand(a.z());
        }
    }

    /**
     * Corner points and center
     * 
     * @return NE cornerpoint
     */
    public Point3D nel() {
        return new Point3D(x.high(), y.high(), z.low());
    }

    public Point3D neh() {
        return new Point3D(x.high(), y.high(), z.high());
    }

    /**
     * @return SW cornerpoint
     */
    public Point3D swl() {
        return new Point3D(x.low(), y.low(), z.low());
    }

    public Point3D swh() {
        return new Point3D(x.low(), y.low(), z.high());
    }

    /**
     * @return SE cornerpoint
     */
    public Point3D sel() {
        return new Point3D(x.high(), y.low(), z.low());
    }

    public Point3D seh() {
        return new Point3D(x.high(), y.low(), z.high());
    }

    /**
     * @return NW cornerpoint
     */
    public Point3D nwl() {
        return new Point3D(x.low(), y.high(), z.low());
    }

    public Point3D nwh() {
        return new Point3D(x.low(), y.high(), z.high());
    }

    /**
     * @return Centre point
     */
    public Point3D centre() {
        return new Point3D(x.cen(), y.cen(), z.cen());
    }

    /**
     * Scale the box by a factor about its center
     * 
     * @return scaled box object
     */
    public Box scale(double f) {
        final Box r = new Box();
        if (empty) {
            return r;
        }
        f = 0.5 * f;
        final Point3D p = new Point3D(x.length() * f, y.length() * f, z.length() * f);
        final Point3D c = centre();
        r.expand(Point3D.add(c, p));
        r.expand(Point3D.sub(c, p));
        return r;
    }

    @Override
    public String toString() {
        if (empty) {
            return "<empty>";
        }
        return "<BOX x:" + x.toString() + ", y:" + y.toString() + ", z:" + z.toString() + ">";
    }

    /**
     * Squared diagonal
     * 
     * @return squared diagonal of the box
     */
    public double dSquared() {
        if (empty) {
            return 0;
        }
        return Point3D.dSquared(swl(), neh());
    }

    /**
     * Where is a point relative to a box?
     * 
     * @return relative position of a point p (E, W, N or S)
     */
    public byte pointRelative(final Point3D p) {
        byte result = 0;
        if (p.x() >= x.high()) {
            result |= rr_E;
        }
        if (p.x() < x.low()) {
            result |= rr_W;
        }
        if (p.y() >= y.high()) {
            result |= rr_N;
        }
        if (p.y() < y.low()) {
            result |= rr_S;
        }
        if (p.z() >= z.high()) {
            result |= rr_U;
        }
        if (p.z() < z.low()) {
            result |= rr_D;
        }
        return result;
    }

    public static Box intersection(final Box a, final Box b) {
        if (a.empty) {
            return a;
        }
        if (b.empty) {
            return b;
        }
        return new Box(Interval.intersection(a.x, b.x), Interval.intersection(a.y, b.y), Interval.intersection(a.z, b.z));
    }

    public static Box union(final Box a, final Box b) {
        if (a.empty) {
            return b;
        }
        if (b.empty) {
            return a;
        }
        return new Box(Interval.union(a.x, b.x), Interval.union(a.y, b.y), Interval.union(a.z, b.z));
    }
}
