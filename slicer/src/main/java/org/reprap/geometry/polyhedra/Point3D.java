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
 
 
 Rr2Point: 2D vectors
 
 First version 20 May 2005
 This version: 1 May 2006 (Now in CVS - no more comments here)
 
 
 */

package org.reprap.geometry.polyhedra;

/**
 * Class for (x, y, z) points and vectors
 */
public class Point3D {
    private double x, y, z;

    public Point3D(final double a, final double b, final double c) {
        x = a;
        y = b;
        z = c;
    }

    /**
     * Copy
     */
    Point3D(final Point3D r) {
        this(r.x, r.y, r.z);
    }

    @Override
    public String toString() {
        return Double.toString(x) + " " + Double.toString(y) + " " + Double.toString(z);
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    Point3D neg() {
        return new Point3D(-x, -y, -z);
    }

    /**
     * @return a new point based on a vector addition of points a and b
     */
    static Point3D add(final Point3D a, final Point3D b) {
        final Point3D r = new Point3D(a);
        r.x += b.x;
        r.y += b.y;
        r.z += b.z;
        return r;
    }

    /**
     * @return a new point based on a vector subtraction of a - b
     */
    static Point3D sub(final Point3D a, final Point3D b) {
        return add(a, b.neg());
    }

    /**
     * @return The point Rr2Point scaled by a factor of factor
     */
    private static Point3D mul(final Point3D b, final double factor) {
        return new Point3D(b.x * factor, b.y * factor, b.z * factor);
    }

    /**
     * Downscale a point
     * 
     * @param b
     *            An R2rPoint
     * @param factor
     *            A scale factor
     * @return The point Rr2Point divided by a factor of a
     */
    static Point3D div(final Point3D b, final double factor) {
        return mul(b, 1 / factor);
    }

    /**
     * Inner product
     * 
     * @return The scalar product of the points
     */
    static double mul(final Point3D a, final Point3D b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    /**
     * Modulus
     * 
     * @return modulus
     */
    double mod() {
        return Math.sqrt(mul(this, this));
    }

    /**
     * Unit length normalization
     * 
     * @return normalized unit length
     */
    Point3D norm() {
        return div(this, mod());
    }

    /**
     * @return outer product
     */
    static Point3D op(final Point3D a, final Point3D b) {
        return new Point3D(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
    }

    /**
     * Squared distance
     * 
     * @return squared distance
     */
    static double dSquared(final Point3D a, final Point3D b) {
        final Point3D c = sub(a, b);
        return mul(c, c);
    }
}
