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
 
 RrHalfPlane: 2D planar half-spaces
 
 First version 20 May 2005
 This version: 9 March 2006
 
 */

package org.reprap.geometry.polygons;

import org.reprap.geometry.polyhedra.HalfSpace;

/**
 * Class to hold and manipulate linear half-planes
 */
public class HalfPlane {

    /**
     * The half-plane is normal*(x, y) + offset <= 0
     */
    private Point2D normal = null;
    private double offset;

    /**
     * Keep the parametric equivalent to save computing it
     */
    private Line p = null;

    /**
     * Convert a parametric line
     */
    public HalfPlane(final Line l) {
        p = new Line(l);
        p.norm();
        normal = new Point2D(-p.direction().y(), p.direction().x());
        offset = -Point2D.mul(l.origin(), normal());
    }

    /**
     * Make one from two points on its edge
     */
    public HalfPlane(final Point2D a, final Point2D b) {
        this(new Line(a, b));
    }

    /**
     * Deep copy
     */
    public HalfPlane(final HalfPlane a) {
        normal = new Point2D(a.normal);
        offset = a.offset;
        p = new Line(a.p);
    }

    /**
     * Construct a half-plane from a 3D half-space cutting across a z plane
     * 
     * @param hs
     * @param z
     */
    public HalfPlane(final HalfSpace hs, final double z) throws ParallelException {
        normal = new Point2D(hs.normal().x(), hs.normal().y());
        final double m = normal.mod();
        if (m < 1.0e-10) {
            throw new ParallelException("HalfPlane from HalfSpace - z parallel");
        }
        offset = (hs.normal().z() * z + hs.offset()) / m;
        normal = Point2D.div(normal, m);
        Point2D p0, p1;
        if (Math.abs(normal.x()) < 0.1) {
            p0 = new Point2D(0, -offset / normal.y());
        } else {
            p0 = new Point2D(-offset / normal.x(), 0);
        }
        p1 = Point2D.add(p0, normal.orthogonal());
        p = new Line(p0, p1);
        p.norm();
    }

    /**
     * Get the parametric equivalent
     * 
     * @return parametric equivalent of a line
     */
    public Line pLine() {
        return p;
    }

    /**
     * Return the plane as a string
     */
    @Override
    public String toString() {
        return "|" + normal.toString() + ", " + Double.toString(offset) + "|";
    }

    /**
     * Get the components
     */
    public Point2D normal() {
        return normal;
    }

    public double offset() {
        return offset;
    }

    /**
     * Is another line the same within a tolerance?
     * 
     * @return 0 if the distance between halfplane a and b is less then the
     *         tolerance, -1 if one is the complement of the other within the
     *         tolerance, otherwise 1
     */
    public static int same(final HalfPlane a, final HalfPlane b, final double tolerance) {
        if (a == b) {
            return 0;
        }

        int result = 0;
        if (Math.abs(a.normal.x() - b.normal.x()) > tolerance) {
            if (Math.abs(a.normal.x() + b.normal.x()) > tolerance) {
                return 1;
            }
            result = -1;
        }
        if (Math.abs(a.normal.y() - b.normal.y()) > tolerance) {
            if (Math.abs(a.normal.y() + b.normal.y()) > tolerance || result != -1) {
                return 1;
            }
        }
        final double rms = Math.sqrt((a.offset * a.offset + b.offset * b.offset) * 0.5);
        if (Math.abs(a.offset - b.offset) > tolerance * rms) {
            if (Math.abs(a.offset + b.offset) > tolerance * rms || result != -1) {
                return 1;
            }
        }

        return result;
    }

    /**
     * Change the sense
     * 
     * @return complement of half plane
     */
    public HalfPlane complement() {
        final HalfPlane r = new HalfPlane(this);
        r.normal = r.normal.neg();
        r.offset = -r.offset;
        r.p = r.p.neg();
        return r;
    }

    /**
     * Move
     * 
     * @return offset halfplane
     */
    public HalfPlane offset(final double d) {
        final HalfPlane r = new HalfPlane(this);
        r.offset = r.offset - d;
        r.p = p.offset(d);
        return r;
    }

    /**
     * Find the potential value of a point
     * 
     * @return potential value of point p
     */
    public double value(final Point2D p) {
        return offset + Point2D.mul(normal, p);
    }

    /**
     * Find the potential interval of a box
     * 
     * @return potential interval of box b
     */
    public Interval value(final Rectangle b) {
        return Interval.add(Interval.add((Interval.mul(b.x(), normal.x())), (Interval.mul(b.y(), normal.y()))), offset);
    }

    /**
     * The point where another line crosses
     * 
     * @return cross point
     * @throws ParallelException
     */
    public Point2D cross_point(final HalfPlane a) throws ParallelException {
        double det = Point2D.op(normal, a.normal);
        if (det == 0) {
            throw new ParallelException("cross_point: parallel lines.");
        }
        det = 1 / det;
        final double x = normal.y() * a.offset - a.normal.y() * offset;
        final double y = a.normal.x() * offset - normal.x() * a.offset;
        return new Point2D(x * det, y * det);
    }

    /**
     * Parameter value where a line crosses
     * 
     * @return parameter value
     * @throws ParallelException
     */
    public double cross_t(final Line a) throws ParallelException {
        final double det = Point2D.mul(a.direction(), normal);
        if (det == 0) {
            throw new ParallelException("cross_t: parallel lines.");
        }
        return -value(a.origin()) / det;
    }

    /**
     * Point where a parametric line crosses
     * 
     * @return cross point
     * @throws ParallelException
     */
    public Point2D cross_point(final Line a) throws ParallelException {
        return a.point(cross_t(a));
    }

    /**
     * Take a range of parameter values and a line, and find the intersection of
     * that range with the part of the line (if any) on the solid side of the
     * half-plane.
     * 
     * @return intersection interval
     */
    public Interval wipe(final Line a, final Interval range) {
        if (range.empty()) {
            return range;
        }

        // Which way is the line pointing relative to our normal?
        final boolean wipe_down = (Point2D.mul(a.direction(), normal) >= 0);
        double t;
        try {
            t = cross_t(a);
            if (t >= range.high()) {
                if (wipe_down) {
                    return range;
                } else {
                    return new Interval();
                }
            } else if (t <= range.low()) {
                if (wipe_down) {
                    return new Interval();
                } else {
                    return range;
                }
            } else {
                if (wipe_down) {
                    return new Interval(range.low(), t);
                } else {
                    return new Interval(t, range.high());
                }
            }
        } catch (final ParallelException ple) {
            t = value(a.origin());
            if (t <= 0) {
                return range;
            } else {
                return new Interval();
            }
        }
    }
}
