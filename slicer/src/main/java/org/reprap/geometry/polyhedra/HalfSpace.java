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

package org.reprap.geometry.polyhedra;

import javax.vecmath.Matrix4d;

import org.reprap.geometry.polygons.Interval;

/**
 * Class to hold and manipulate linear half-spaces
 */
public class HalfSpace {
    /**
     * The half-plane is normal*(x, y) + offset <= 0
     */
    private Point3D normal = null;
    private double offset;

    /**
     * Make one from three points in it
     */
    HalfSpace(final Point3D a, final Point3D b, final Point3D c) {
        normal = Point3D.op(Point3D.sub(b, a), Point3D.sub(c, a)).norm();
        offset = -Point3D.mul(normal, a);
    }

    /**
     * Make one from a normal and one point in it
     */
    HalfSpace(final Point3D n, final Point3D a) {
        normal = n.norm();
        offset = -Point3D.mul(normal, a);
    }

    /**
     * Deep copy
     */
    HalfSpace(final HalfSpace a) {
        normal = new Point3D(a.normal);
        offset = a.offset;
    }

    @Override
    public String toString() {
        return "|" + normal.toString() + ", " + Double.toString(offset) + "|";
    }

    /**
     * Get the components
     */
    public Point3D normal() {
        return normal;
    }

    public double offset() {
        return offset;
    }

    /**
     * Is another plane the same within a tolerance?
     * 
     * @return 0 if the distance between halfplane a and b is less then the
     *         tolerance, -1 if one is the complement of the other within the
     *         tolerance, otherwise 1
     */
    static int same(final HalfSpace a, final HalfSpace b, final double tolerance) {
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
     * @return complent of half plane
     */
    HalfSpace complement() {
        final HalfSpace r = new HalfSpace(this);
        r.normal = r.normal.neg();
        r.offset = -r.offset;
        return r;
    }

    /**
     * Move somewhere else. NOTE THIS EXPECTS THE INVERSE OF THE TRANSFORM
     */
    HalfSpace transform(final Matrix4d iM) {
        final Point3D n = new Point3D(iM.m00 * normal.x() + iM.m10 * normal.y() + iM.m20 * normal.z() + iM.m30 * offset, iM.m01
                * normal.x() + iM.m11 * normal.y() + iM.m21 * normal.z() + iM.m31 * offset, iM.m02 * normal.x() + iM.m12
                * normal.y() + iM.m22 * normal.z() + iM.m32 * offset);
        final double o = iM.m03 * normal.x() + iM.m13 * normal.y() + iM.m23 * normal.z() + iM.m33 * offset;
        final double d = n.mod();
        final HalfSpace result = new HalfSpace(this);
        result.normal = Point3D.div(n, d);
        result.offset = o / d;
        return result;
    }

    /**
     * Move
     * 
     * @return offset halfplane
     */
    HalfSpace offset(final double d) {
        final HalfSpace r = new HalfSpace(this);
        r.offset = r.offset - d;
        return r;
    }

    /**
     * Find the potential value of a point
     * 
     * @return potential value of point p
     */
    public double value(final Point3D p) {
        return offset + Point3D.mul(normal, p);
    }

    /**
     * Find the potential interval of a box
     * 
     * @return potential interval of box b
     */
    Interval value(final Box b) {
        final Interval x = Interval.mul(b.x(), normal.x());
        final Interval y = Interval.mul(b.y(), normal.y());
        final Interval z = Interval.mul(b.z(), normal.z());
        return Interval.add(Interval.add(Interval.add(x, y), z), offset);
    }
}
