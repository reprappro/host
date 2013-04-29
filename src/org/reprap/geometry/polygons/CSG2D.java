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

import java.util.ArrayList;

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
    public CSG2D(final HalfPlane h) {
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
    public static CSG2D universe() {
        return u;
    }

    /**
     * @return nothing/null set
     */
    public static CSG2D nothing() {
        return n;
    }

    /**
     * Deep copy
     */
    public CSG2D(final CSG2D c) {
        if (c == u || c == n) {
            Debug.e("RrCSG deep copy: copying null or universal set.");
        }

        if (c.hp != null) {
            hp = new HalfPlane(c.hp);
        } else {
            hp = null;
        }

        if (c.c1 != null) {
            c1 = new CSG2D(c.c1);
        } else {
            c1 = null;
        }

        if (c.c2 != null) {
            c2 = new CSG2D(c.c2);
        } else {
            c2 = null;
        }

        comp = null; // This'll be built if it's needed

        op = c.op;
        complexity = c.complexity;
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
            Debug.e("CSG2D constructor from CSG3D: null set in tree!");
            break;

        case UNIVERSE:
            Debug.e("CSG2D constructor from CSG3D: universal set in tree!");
            break;

        case UNION:
            return CSG2D.union(slice(t.c_1(), z), slice(t.c_2(), z));

        case INTERSECTION:
            return CSG2D.intersection(slice(t.c_1(), z), slice(t.c_2(), z));

        default:
            Debug.e("CSG2D constructor from CSG3D: invalid operator " + t.operator());
        }
        return r;
    }

    /**
     * Get children, operator etc
     */
    public CSG2D c_1() {
        return c1;
    }

    public CSG2D c_2() {
        return c2;
    }

    public CSGOp operator() {
        return op;
    }

    public HalfPlane hPlane() {
        return hp;
    }

    public int complexity() {
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
            Debug.e("toString_r(): invalid operator.");
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
    public static CSG2D union(final CSG2D a, final CSG2D b) {
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
    public static CSG2D intersection(final CSG2D a, final CSG2D b) {
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
    public CSG2D complement() {
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
            Debug.e("complement(): invalid operator.");
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
    public static CSG2D difference(final CSG2D a, final CSG2D b) {
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
     * This takes a complicated expression assumed to contain multiple instances
     * of leafA and returns the equivalent CSG expression involving just leafA.
     * 
     * @return equivalent CSG expression involving just leafA
     */
    private CSG2D categorise(final CSG2D leafA) {
        final HalfPlane a = leafA.hPlane();
        final Point2D an = a.normal();
        final Point2D x = Point2D.add(a.pLine().origin(), an);
        if (value(x) <= 0) {
            return leafA.complement();
        } else {
            return leafA;
        }
    }

    /**
     * This takes a complicated expression assumed to contain multiple instances
     * of leafA and leafB and returns the equivalent CSG expression involving at
     * most leafA and leafB once (except for non-manifold shapes).
     * 
     * @return equivalent CSG expression involving at most leafA and leafB once
     */
    private CSG2D crossCategorise(CSG2D leafA, CSG2D leafB) {
        final HalfPlane a = leafA.hPlane();
        final HalfPlane b = leafB.hPlane();
        final Point2D an = a.normal();
        final Point2D bn = b.normal();
        Point2D v02 = Point2D.add(an, bn);
        Point2D v31 = Point2D.sub(bn, an);
        Point2D x, x0, x1, x2, x3;
        int category = 0;
        try {
            x = a.cross_point(b);
            v02 = v02.norm();
            v31 = v31.norm();
            x2 = Point2D.add(x, v02);
            x0 = Point2D.sub(x, v02);
            x1 = Point2D.add(x, v31);
            x3 = Point2D.sub(x, v31);
            if (value(x0) <= 0) {
                category |= 1;
            }
            if (value(x1) <= 0) {
                category |= 2;
            }
            if (value(x2) <= 0) {
                category |= 4;
            }
            if (value(x3) <= 0) {
                category |= 8;
            }

            switch (category) {
            case 0:
                return nothing();
            case 1:
                return intersection(leafA, leafB);
            case 2:
                return intersection(leafA, leafB.complement());
            case 3:
                return leafA;
            case 4:
                return intersection(leafA.complement(), leafB.complement());
            case 5:
                Debug.e("RrCSG crossCategorise: non-manifold shape (case 0101)!");
                return union(intersection(leafA, leafB), intersection(leafA.complement(), leafB.complement()));
            case 6:
                return leafB.complement();
            case 7:
                return union(leafA, leafB.complement());
            case 8:
                return intersection(leafA.complement(), leafB);
            case 9:
                return leafB;
            case 10:
                Debug.e("RrCSG crossCategorise: non-manifold shape (case 1010)!");
                return union(CSG2D.intersection(leafA.complement(), leafB), intersection(leafA, leafB.complement()));
            case 11:
                return union(leafA, leafB);
            case 12:
                return leafA.complement();
            case 13:
                return union(leafA.complement(), leafB);
            case 14:
                return union(leafA.complement(), leafB.complement());
            case 15:
                return universe();
            default:
                Debug.e("RrCSG crossCategorise: bitwise | doesn't seem to work...");
                return this;
            }
        } catch (final Exception e) {
            // leafA and leafB are parallel

            x0 = Point2D.mul(Point2D.add(a.pLine().origin(), b.pLine().origin()), 0.5);
            x1 = Point2D.mul(Point2D.sub(a.pLine().origin(), b.pLine().origin()), 3);
            x2 = Point2D.add(x0, x1);
            x1 = Point2D.sub(x0, x1);
            if (value(x0) <= 0) {
                category |= 1;
            }
            if (value(x1) <= 0) {
                category |= 2;
            }
            if (value(x2) <= 0) {
                category |= 4;
            }

            if (leafA.value(x0) <= 0) {
                leafA = leafA.complement();
            }

            if (leafB.value(x0) <= 0) {
                leafB = leafB.complement();
            }

            switch (category) {
            case 0:
                return nothing();
            case 1:
                return intersection(leafA.complement(), leafB.complement());
            case 2:
                return leafB;
            case 3:
                return leafA.complement();
            case 4:
                return leafA;
            case 5:
                return union(leafA, leafB);
            case 6:
                return leafB.complement();
            case 7:
                return universe();
            default:
                Debug.e("RrCSG crossCategorise: bitwise | doesn't seem to work...");
                return this;
            }
        }

    }

    /**
     * Run through a GSG expression looking at its leaves and return a list of
     * the distinct leaves. Note: leaf and leaf.complement() are not considered
     * distinct. Recursive internal call.
     */
    private void uniqueList_r(final ArrayList<CSG2D> list) {
        switch (op) {
        case LEAF:
            CSG2D entry;
            for (int i = 0; i < list.size(); i++) {
                entry = list.get(i);
                if (this == entry || complement() == entry) {
                    return;
                }
            }
            list.add(this);
            break;

        case NULL:
        case UNIVERSE:
            Debug.e("uniqueList_r: null or universe at a leaf.");
            break;

        case UNION:
        case INTERSECTION:
            c1.uniqueList_r(list);
            c2.uniqueList_r(list);
            break;

        default:
            Debug.e("uniqueList_r: invalid operator.");
        }

        return;
    }

    /**
     * Run through a GSG expression looking at its leaves and return a list of
     * the distinct leaves. Note: leaf and leaf.complement() are not considered
     * distinct.
     */
    private ArrayList<CSG2D> uniqueList() {
        final ArrayList<CSG2D> result = new ArrayList<CSG2D>();
        uniqueList_r(result);
        return result;
    }

    /**
     * Regularise a set with simple contents ( <= 4 ) This assumes simplify has
     * been run over the set
     * 
     * @return regularised CSG object
     */
    public CSG2D regularise() {
        final CSG2D r = this;

        if (complexity < 3 || complexity > 4) {
            return r;
        }

        final ArrayList<CSG2D> list = uniqueList();
        if (list.size() == 1) {
            return categorise(list.get(0));
        }
        if (list.size() == 2) {
            return crossCategorise(list.get(0), list.get(1));
        }

        return r;
    }

    /**
     * Force an approximation to the regulariseing of a set This assumes
     * simplify has been run over the set
     * 
     * @return regularised CSG object
     */
    public CSG2D forceRegularise() {
        final ArrayList<CSG2D> list = uniqueList();
        if (list.size() == 1) {
            return categorise(list.get(0));
        }

        return crossCategorise(list.get(0), list.get(1));
    }

    /**
     * Replace duplicate of leaf with leaf itself TODO: this should also use
     * known complements
     */
    private void replaceAllSameLeaves(final CSG2D leaf, final double tolerance) {
        int same;
        switch (op) {
        case LEAF:
        case NULL:
        case UNIVERSE:
            break;

        case UNION:
        case INTERSECTION:
            final HalfPlane hp = leaf.hp;
            if (c1.op == CSGOp.LEAF) {
                same = HalfPlane.same(hp, c1.hp, tolerance);
                if (same == 0) {
                    c1 = leaf;
                }
                if (same == -1) {
                    c1 = leaf.complement();
                }
            } else {
                c1.replaceAllSameLeaves(leaf, tolerance);
            }

            if (c2.op == CSGOp.LEAF) {
                same = HalfPlane.same(hp, c2.hp, tolerance);
                if (same == 0) {
                    c2 = leaf;
                }
                if (same == -1) {
                    c2 = leaf.complement();
                }
            } else {
                c2.replaceAllSameLeaves(leaf, tolerance);
            }
            break;

        default:
            Debug.e("replace_all_same(): invalid operator.");
        }
    }

    /**
     * Replace duplicate of all leaves with the last instance of each; also link
     * up complements.
     * 
     * @return simplified CSG object
     */
    private void simplify_r(final CSG2D root, final double tolerance) {
        switch (op) {
        case LEAF:
            root.replaceAllSameLeaves(this, tolerance);
            break;

        case NULL:
        case UNIVERSE:
            break;

        case UNION:
        case INTERSECTION:
            c1.simplify_r(root, tolerance);
            c2.simplify_r(root, tolerance);
            break;

        default:
            Debug.e("simplify_r(): invalid operator.");

        }
    }

    /**
     * Replace duplicate of all leaves with the last instance of each
     * 
     * @return simplified CSG object
     */
    public CSG2D simplify(final double tolerance) {
        if (this == u || this == n) {
            return this;
        }

        final CSG2D root = new CSG2D(this);
        simplify_r(root, tolerance);
        return root;
    }

    /**
     * Offset by a distance (+ve or -ve) TODO: this should keep track of
     * complements
     * 
     * @return offset CSG object by distance d
     */
    public CSG2D offset(final double d) {
        CSG2D result;

        switch (op) {
        case LEAF:
            result = new CSG2D(hp.offset(d));
            break;

        case NULL:
        case UNIVERSE:
            result = this;
            break;

        case UNION:
            result = union(c1.offset(d), c2.offset(d));
            break;

        case INTERSECTION:
            result = intersection(c1.offset(d), c2.offset(d));
            break;

        default:
            Debug.e("offset(): invalid operator.");
            result = nothing();
        }
        return result;
    }

    /**
     * leaf find the half-plane that generates the value for a point
     */
    public CSG2D leaf(final Point2D p) {
        CSG2D result, r1, r2;

        switch (op) {
        case LEAF:
            result = this;
            break;

        case NULL:
            result = this;
            break;

        case UNIVERSE:
            result = this;
            break;

        case UNION:
            r1 = c1.leaf(p);
            r2 = c2.leaf(p);
            if (r1.value(p) < r2.value(p)) {
                return r1;
            } else {
                return r2;
            }

        case INTERSECTION:
            r1 = c1.leaf(p);
            r2 = c2.leaf(p);
            if (r1.value(p) > r2.value(p)) {
                return r1;
            } else {
                return r2;
            }

        default:
            Debug.e("leaf(Rr2Point): invalid operator.");
            result = nothing();
        }
        return result;
    }

    /**
     * "Potential" value of a point; i.e. a membership test -ve means inside; 0
     * means on the surface; +ve means outside
     * 
     * @return potential value of a point
     */
    public double value(final Point2D p) {
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
            Debug.e("RrCSG.value(): dud operator.");
        }
        return result;
    }

    /**
     * The interval value of a box (analagous to point)
     * 
     * @return value of a box
     */
    public Interval value(final Rectangle b) {
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
            Debug.e("value(RrBox): invalid operator.");
            result = new Interval();
        }

        return result;
    }

    /**
     * Prune the set to a box
     * 
     * @return pruned box as new CSG object
     */
    public CSG2D prune(final Rectangle b) {
        CSG2D result = this;

        switch (op) {
        case LEAF:
            final Interval i = hp.value(b);
            if (i.empty()) {
                Debug.e("RrCSG.prune(RrBox): empty interval!");
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
            Debug.e("RrCSG.prune(RrBox): dud op value!");
        }

        return result;
    }
}
