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

package org.reprap.geometry.polygons;

import org.reprap.CSGOp;
import org.reprap.geometry.polyhedra.CSG3D;
import org.reprap.geometry.polyhedra.Point3D;
import org.reprap.utilities.Debug;

/**
 * RepRap Constructive Solid Geometry class
 * 
 * RrCSG: 2D polygons as boolean combinations of half-planes First version 14
 * November 2005
 */
public class CSG2D {
    /**
     * Universal set
     */
    private static final CSG2D u = new CSG2D(true);

    /**
     * Null set
     */
    private static final CSG2D n = new CSG2D(false);

    /**
     * Leaf half plane
     */
    private HalfPlane hp = null;

    /**
     * Type of set
     */
    private CSGOp op;

    /**
     * Non-leaf child operands
     */
    private CSG2D c1 = null;
    private CSG2D c2 = null;

    /**
     * The complement (if there is one)
     */
    private CSG2D comp = null;

    /**
     * How much is in here (leaf count)?
     */
    private int complexity;

    private CSG2D() {
        hp = null;
        op = CSGOp.LEAF;
        c1 = null;
        c2 = null;
        comp = null;
        complexity = 1;
    }

    /**
     * Make a leaf from a single half-plane
     */
    CSG2D(final HalfPlane h) {
        hp = new HalfPlane(h);
        op = CSGOp.LEAF;
        c1 = null;
        c2 = null;
        comp = null;
        complexity = 1;
    }

    /**
     * One off constructor for the universal and null sets
     */
    private CSG2D(final boolean b) {
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
     */
    static CSG2D universe() {
        return u;
    }

    /**
     * @return nothing/null set
     */
    static CSG2D nothing() {
        return n;
    }

    /**
     * Compute a 2D slice of a 3D CSG at a given Z value
     */
    public static CSG2D slice(final CSG3D t, final double z) {
        final CSG2D r = new CSG2D();

        switch (t.operator()) {
        case LEAF:
            r.op = CSGOp.LEAF;
            r.complexity = 1;
            try {
                r.hp = new HalfPlane(t.hSpace(), z);
            } catch (final ParallelException e) {
                if (t.hSpace().value(new Point3D(0, 0, z)) <= 0) {
                    return universe();
                } else {
                    return nothing();
                }
            }
            break;

        case NULL:
            Debug.getInstance().errorMessage("CSG2D constructor from CSG3D: null set in tree!");
            break;

        case UNIVERSE:
            Debug.getInstance().errorMessage("CSG2D constructor from CSG3D: universal set in tree!");
            break;

        case UNION:
            return CSG2D.union(slice(t.c_1(), z), slice(t.c_2(), z));

        case INTERSECTION:
            return CSG2D.intersection(slice(t.c_1(), z), slice(t.c_2(), z));

        default:
            Debug.getInstance().errorMessage("CSG2D constructor from CSG3D: invalid operator " + t.operator());
        }
        return r;
    }

    int complexity() {
        return complexity;
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
        String result = "2D RrCSG: complexity = " + Integer.toString(complexity) + "\n";
        result = toString_r(result, " ");
        return result;
    }

    /**
     * Private constructor for common work setting up booleans
     */
    private CSG2D(final CSG2D a, final CSG2D b) {
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
     * @return union of passed CSG objects a and b
     */
    static CSG2D union(final CSG2D a, final CSG2D b) {
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

        final CSG2D r = new CSG2D(a, b);
        r.op = CSGOp.UNION;
        return r;
    }

    /**
     * Boolean operation to perform an intersection
     * 
     * @return intersection of passed CSG objects a and b
     */
    static CSG2D intersection(final CSG2D a, final CSG2D b) {
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

        final CSG2D r = new CSG2D(a, b);
        r.op = CSGOp.INTERSECTION;
        return r;
    }

    /**
     * Lazy evaluation for complement.
     * 
     * @return complement
     */
    private CSG2D complement() {
        if (comp != null) {
            return comp;
        }

        CSG2D result;

        switch (op) {
        case LEAF:
            result = new CSG2D(hp.complement());
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
     * Set difference is intersection with complement
     * 
     * @return set difference as CSG object based on input CSG objects a and b
     */
    static CSG2D difference(final CSG2D a, final CSG2D b) {
        return intersection(a, b.complement());
    }

    /**
     * Make a rectangle
     */
    public static CSG2D RrCSGFromBox(final Rectangle b) {
        CSG2D r = new CSG2D(new HalfPlane(b.nw(), b.ne()));
        r = CSG2D.intersection(r, new CSG2D(new HalfPlane(b.ne(), b.se())));
        r = CSG2D.intersection(r, new CSG2D(new HalfPlane(b.se(), b.sw())));
        r = CSG2D.intersection(r, new CSG2D(new HalfPlane(b.sw(), b.nw())));
        return r;
    }

    /**
     * "Potential" value of a point; i.e. a membership test -ve means inside; 0
     * means on the surface; +ve means outside
     * 
     * @return potential value of a point
     */
    double value(final Point2D p) {
        double result = 1;
        switch (op) {
        case LEAF:
            result = hp.value(p);
            break;

        case NULL:
            result = 1;
            break;

        case UNIVERSE:
            result = -1;
            break;

        case UNION:
            result = Math.min(c1.value(p), c2.value(p));
            break;

        case INTERSECTION:
            result = Math.max(c1.value(p), c2.value(p));
            break;

        default:
            Debug.getInstance().errorMessage("RrCSG.value(): dud operator.");
        }
        return result;
    }

    /**
     * The interval value of a box (analagous to point)
     * 
     * @return value of a box
     */
    Interval value(final Rectangle b) {
        Interval result;

        switch (op) {
        case LEAF:
            result = hp.value(b);
            break;

        case NULL:
            result = new Interval(1, 1.01); // Is this clever?  Or dumb?
            break;

        case UNIVERSE:
            result = new Interval(-1.01, -1); // Ditto.
            break;

        case UNION:
            result = Interval.min(c1.value(b), c2.value(b));
            break;

        case INTERSECTION:
            result = Interval.max(c1.value(b), c2.value(b));
            break;

        default:
            Debug.getInstance().errorMessage("value(RrBox): invalid operator.");
            result = new Interval();
        }

        return result;
    }

    /**
     * Prune the set to a box
     * 
     * @return pruned box as new CSG object
     */
    CSG2D prune(final Rectangle b) {
        CSG2D result = this;

        switch (op) {
        case LEAF:
            final Interval i = hp.value(b);
            if (i.empty()) {
                Debug.getInstance().errorMessage("RrCSG.prune(RrBox): empty interval!");
            } else if (i.neg()) {
                result = universe();
            } else if (i.pos()) {
                result = nothing();
            }
            break;

        case NULL:
        case UNIVERSE:
            break;

        case UNION:
            result = union(c1.prune(b), c2.prune(b));
            break;

        case INTERSECTION:
            result = intersection(c1.prune(b), c2.prune(b));
            break;

        default:
            Debug.getInstance().errorMessage("RrCSG.prune(RrBox): dud op value!");
        }

        return result;
    }
}
