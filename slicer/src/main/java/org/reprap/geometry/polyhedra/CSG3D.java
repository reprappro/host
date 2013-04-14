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
 
 */

package org.reprap.geometry.polyhedra;

import javax.vecmath.Matrix4d;

import org.reprap.CSGOp;
import org.reprap.utilities.Debug;

/**
 * RepRap Constructive Solid Geometry class
 * 
 * RrCSG: 2D polygons as boolean combinations of half-planes First version 14
 * November 2005
 */
public class CSG3D {
    /**
     * Universal set
     */
    private static final CSG3D u = new CSG3D(true);

    /**
     * Null set
     */
    private static final CSG3D n = new CSG3D(false);

    /**
     * Leaf half plane
     */
    private HalfSpace hp = null;

    /**
     * Type of set
     */
    private CSGOp op;

    /**
     * Non-leaf child operands
     */
    private CSG3D c1 = null;
    private CSG3D c2 = null;

    /**
     * The complement (if there is one)
     */
    private CSG3D comp = null;

    /**
     * How much is in here (leaf count)?
     */
    private final int complexity;

    /**
     * Make a leaf from a single half-plane
     */
    CSG3D(final HalfSpace h) {
        hp = new HalfSpace(h);
        op = CSGOp.LEAF;
        c1 = null;
        c2 = null;
        comp = null;
        complexity = 1;
    }

    /**
     * One off constructor for the universal and null sets
     */
    private CSG3D(final boolean b) {
        hp = null;
        if (b) {
            op = CSGOp.UNIVERSE;
        } else {
            op = CSGOp.NULL;
        }
        c1 = null;
        c2 = null;
        comp = null; // Resist temptation to be clever here
        complexity = 0;
    }

    /**
     * Universal or null set
     * 
     * @return universal or null set
     */
    static CSG3D universe() {
        return u;
    }

    /**
     * @return nothing/null set
     */
    private static CSG3D nothing() {
        return n;
    }

    /**
     * Get children, operator etc
     * 
     * @return children
     */
    public CSG3D c_1() {
        return c1;
    }

    public CSG3D c_2() {
        return c2;
    }

    public CSGOp operator() {
        return op;
    }

    public HalfSpace hSpace() {
        return hp;
    }

    /**
     * Convert to a string
     */
    private String toString_r(String result, String white) {
        switch (op) {
        case LEAF:
            result = result + white + hp.toString() + "\n";
            break;

        case NULL:
            result = result + white + "0\n";
            break;

        case UNIVERSE:
            result = result + white + "U\n";
            break;

        case UNION:
            result = result + white + "+\n";
            white = white + " ";
            result = c1.toString_r(result, white);
            result = c2.toString_r(result, white);
            break;

        case INTERSECTION:
            result = result + white + "&\n";
            white = white + " ";
            result = c1.toString_r(result, white);
            result = c2.toString_r(result, white);
            break;

        default:
            Debug.getInstance().errorMessage("toString_r(): invalid operator.");
        }
        return result;
    }

    @Override
    public String toString() {
        String result = "3D RrCSG: complexity = " + Integer.toString(complexity) + "\n";
        result = toString_r(result, " ");
        return result;
    }

    /**
     * Private constructor for common work setting up booleans
     */
    private CSG3D(final CSG3D a, final CSG3D b) {
        hp = null;
        comp = null;
        if (a.complexity <= b.complexity) // So we know the 1st child is the simplest
        {
            c1 = a;
            c2 = b;
        } else {
            c1 = b;
            c2 = a;
        }
        complexity = c1.complexity + c2.complexity;
    }

    /**
     * Boolean operations, with de Morgan simplifications
     * 
     * @return union of passed CSG objects
     */
    static CSG3D union(final CSG3D a, final CSG3D b) {
        if (a == b) {
            return a;
        }
        if (a.op == CSGOp.NULL) {
            return b;
        }
        if (b.op == CSGOp.NULL) {
            return a;
        }
        if ((a.op == CSGOp.UNIVERSE) || (b.op == CSGOp.UNIVERSE)) {
            return universe();
        }

        if (a.comp != null && b.comp != null) {
            if (a.comp == b) {
                return universe();
            }
        }

        final CSG3D r = new CSG3D(a, b);
        r.op = CSGOp.UNION;
        return r;
    }

    /**
     * Boolean operation to perform an intersection
     * 
     * @return intersection of passed CSG objects
     */
    static CSG3D intersection(final CSG3D a, final CSG3D b) {
        if (a == b) {
            return a;
        }
        if (a.op == CSGOp.UNIVERSE) {
            return b;
        }
        if (b.op == CSGOp.UNIVERSE) {
            return a;
        }
        if ((a.op == CSGOp.NULL) || (b.op == CSGOp.NULL)) {
            return nothing();
        }

        if (a.comp != null && b.comp != null) {
            if (a.comp == b) {
                return nothing();
            }
        }

        final CSG3D r = new CSG3D(a, b);
        r.op = CSGOp.INTERSECTION;
        return r;
    }

    /**
     * Lazy evaluation for complement.
     * 
     * @return complement
     */
    private CSG3D complement() {
        if (comp != null) {
            return comp;
        }

        CSG3D result;

        switch (op) {
        case LEAF:
            result = new CSG3D(hp.complement());
            break;

        case NULL:
            return universe();

        case UNIVERSE:
            return nothing();

        case UNION:
            result = intersection(c1.complement(), c2.complement());
            break;

        case INTERSECTION:
            result = union(c1.complement(), c2.complement());
            break;

        default:
            Debug.getInstance().errorMessage("complement(): invalid operator.");
            return nothing();
        }

        comp = result;
        result.comp = this;

        return comp;
    }

    /**
     * Move somewhere else. Note - this expects the inverse transform
     */
    private CSG3D xform(final Matrix4d iM) {
        CSG3D result;

        switch (op) {
        case LEAF:
            result = new CSG3D(hp.transform(iM));
            break;

        case NULL:
            return nothing();

        case UNIVERSE:
            return universe();

        case UNION:
            result = union(c1.xform(iM), c2.xform(iM));
            break;

        case INTERSECTION:
            result = intersection(c1.xform(iM), c2.xform(iM));
            break;

        default:
            Debug.getInstance().errorMessage("transform(): invalid operator.");
            return nothing();
        }

        return result;
    }

    /**
     * Go somewhere else
     */
    CSG3D transform(final Matrix4d m) {
        final Matrix4d iM = new Matrix4d(m);
        iM.invert();
        return xform(iM);
    }

    /**
     * Set difference is intersection with complement
     * 
     * @return set difference as CSG object based on input CSG objects
     */
    static CSG3D difference(final CSG3D a, final CSG3D b) {
        return intersection(a, b.complement());
    }
}
