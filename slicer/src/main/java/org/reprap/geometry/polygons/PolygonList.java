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
 
 
 RrPolygonList: A collection of 2D polygons
 
 First version 20 May 2005
 This version: 1 May 2006 (Now in CVS - no more comments here)
 
 */

package org.reprap.geometry.polygons;

import java.util.ArrayList;
import java.util.List;

import org.reprap.gcode.GCodeExtruder;
import org.reprap.geometry.LayerRules;
import org.reprap.utilities.Debug;

/**
 * RrPolygonList: A collection of 2D polygons List of polygons class. This too
 * maintains a maximum enclosing rectangle.
 */
public class PolygonList {
    private final List<Polygon> polygons = new ArrayList<Polygon>();
    private final Rectangle box = new Rectangle();

    public PolygonList() {
    }

    /**
     * Deep copy
     * 
     * @param lst
     *            list of polygons to copy
     */
    public PolygonList(final PolygonList lst) {
        box.expand(lst.box);
        for (int i = 0; i < lst.size(); i++) {
            polygons.add(new Polygon(lst.polygon(i)));
        }
    }

    /**
     * Get the data
     * 
     * @param i
     *            index of polygon to return
     * @return polygon at index i
     */
    public Polygon polygon(final int i) {
        return polygons.get(i);
    }

    /**
     * @return number of polygons in the list
     */
    public int size() {
        return polygons.size();
    }

    /**
     * Overwrite one of the polygons
     * 
     * @param i
     *            index of polygon to overwrite
     * @param p
     *            polygon to set at index i
     */
    private void set(final int i, final Polygon p) {
        polygons.set(i, p);
    }

    /**
     * Remove one from the list
     * 
     * @param i
     *            index of polygon to remove
     */
    private void remove(final int i) {
        polygons.remove(i);
    }

    /**
     * Put a new list on the end
     * 
     * @param lst
     *            list to append to existing polygon list
     */
    public void add(final PolygonList lst) {
        for (int i = 0; i < lst.size(); i++) {
            polygons.add(new Polygon(lst.polygon(i)));
        }
        box.expand(lst.box);
    }

    /**
     * Add one new polygon to the list
     * 
     * @param p
     *            polygon to add to the list
     */
    public void add(final Polygon p) {
        polygons.add(p);
        box.expand(p.getBox());
    }

    /**
     * Swap two in the list
     * 
     * @param i
     * @param j
     */
    private void swap(final int i, final int j) {
        final Polygon p = polygons.get(i);
        polygons.set(i, polygons.get(j));
        polygons.set(j, p);
    }

    /**
     * Negate one of the polygons
     * 
     * @param i
     */
    private void negate(final int i) {
        final Polygon p = polygon(i).negate();
        polygons.set(i, p);
    }

    @Override
    public String toString() {
        String result = "Polygon List - polygons: ";
        result += size() + ", enclosing box: ";
        result += box.toString();
        for (int i = 0; i < size(); i++) {
            result += "\n" + polygon(i).toString();
        }
        return result;
    }

    /**
     * Simplify all polygons by length d N.B. this may throw away small ones
     * completely
     * 
     * @return simplified polygon list
     */
    public PolygonList simplify(final double d) {
        final PolygonList r = new PolygonList();
        final double d2 = d * d;

        for (int i = 0; i < size(); i++) {
            final Polygon p = polygon(i);
            if (p.getBox().dSquared() > 2 * d2) {
                r.add(p.simplify(d));
            }
        }

        return r;
    }

    /**
     * Re-order and (if need be) reverse the order of the polygons in a list so
     * the end of the first is near the start of the second and so on. This is a
     * heuristic - it does not do a full travelling salesman... This deals with
     * both open and closed polygons, but it only allows closed ones to be
     * re-ordered if reOrder is true. If any point on a closed polygon is closer
     * to any point on any other than sqrt(linkUp), the two polygons are merged
     * at their closest points. This is suppressed by setting linkUp negative.
     */
    public PolygonList nearEnds(final Point2D startNearHere, final boolean reOrder, final double linkUp) {
        final PolygonList result = new PolygonList();
        if (size() <= 0) {
            return result;
        }

        for (int i = 0; i < size(); i++) {
            result.add(polygon(i));
        }

        // Make the nearest end point on any polygon to startNearHere
        // go to polygon 0 and get it the right way round if it's open.

        // Begin by moving the polygon nearest the specified start point to the head
        // of the list.
        if (startNearHere != null) {
            double d = Double.POSITIVE_INFINITY;
            boolean neg = false;
            int near = -1;
            int nearV = -1;
            for (int i = 0; i < size(); i++) {
                if (result.polygon(i).isClosed() && reOrder) {
                    final int nv = polygon(i).nearestVertex(startNearHere);
                    final double d2 = Point2D.dSquared(startNearHere, polygon(i).point(nv));
                    if (d2 < d) {
                        near = i;
                        nearV = nv;
                        d = d2;
                        neg = false;
                    }
                } else {
                    double d2 = Point2D.dSquared(startNearHere, result.polygon(i).point(0));
                    if (d2 < d) {
                        near = i;
                        nearV = -1;
                        d = d2;
                        neg = false;
                    }
                    if (!result.polygon(i).isClosed()) {
                        d2 = Point2D.dSquared(startNearHere, result.polygon(i).point(result.polygon(i).size() - 1));
                        if (d2 < d) {
                            near = i;
                            nearV = -1;
                            d = d2;
                            neg = true;
                        }
                    }
                }
            }

            if (near < 0) {
                Debug.getInstance().errorMessage("RrPolygonList.nearEnds(): no nearest end found to start point!");
                return result;
            }

            result.swap(0, near);
            if (reOrder && nearV >= 0) {
                set(0, polygon(0).newStart(nearV));
            }
            if (neg) {
                result.negate(0);
            }
        }

        if (reOrder && linkUp >= 0) {
            for (int i = 0; i < result.size() - 1; i++) {
                for (int j = i + 1; j < result.size(); j++) {
                    if (result.polygon(j).isClosed()) {
                        if (result.polygon(i).nearestVertexReorderMerge(result.polygon(j), linkUp)) {
                            result.remove(j);
                        }
                    }
                }
            }
        }

        // Go through the rest of the polygons getting them as close as
        // reasonable.
        for (int i = 0; i < result.size() - 1; i++) {
            final Point2D end;
            if (result.polygon(i).isClosed()) {
                end = result.polygon(i).point(0);
            } else {
                end = result.polygon(i).point(result.polygon(i).size() - 1);
            }
            boolean neg = false;
            int near = -1;
            double d = Double.POSITIVE_INFINITY;
            for (int j = i + 1; j < result.size(); j++) {
                double d2 = Point2D.dSquared(end, result.polygon(j).point(0));
                if (d2 < d) {
                    near = j;
                    d = d2;
                    neg = false;
                }

                if (!result.polygon(j).isClosed()) {
                    d2 = Point2D.dSquared(end, result.polygon(j).point(result.polygon(j).size() - 1));
                    if (d2 < d) {
                        near = j;
                        d = d2;
                        neg = true;
                    }
                }
            }

            if (near > 0) {
                if (neg) {
                    result.negate(near);
                }
                result.swap(i + 1, near);
            }
        }

        return result;
    }

    /**
     * Take all the polygons in a list, both open and closed, and reorder them
     * such that accessible points on any that have a squared distance less than
     * linkUp to accessible points on any others are joined to form single
     * polygons.
     * 
     * For an open polygon the accessible points are just its ends. For a closed
     * polygon all its points are accessible.
     * 
     * This is a fairly radical remove in-air movement strategy.
     * 
     * All the polygons in the list must be plotted with the same physical
     * extruder (otherwise it would be nonsense to join them). It is the calling
     * function's responsibility to make sure this is the case.
     */
    public void radicalReOrder(final double linkUp) {
        if (size() < 2) {
            return;
        }

        // First check that we all have the same physical extruder
        final int physicalExtruder = polygon(0).getAttributes().getExtruder().getPhysicalExtruderNumber();
        for (int i = 1; i < size(); i++) {
            if (polygon(i).getAttributes().getExtruder().getPhysicalExtruderNumber() != physicalExtruder) {
                Debug.getInstance().errorMessage(
                        "RrPolygonList.radicalReOrder(): more than one physical extruder needed by the list!");
                return;
            }
        }

        // Now go through the polygons pairwise
        for (int i = 0; i < size() - 1; i++) {
            Polygon myPolygon = polygon(i);
            for (int j = i + 1; j < size(); j++) {
                double d = Double.POSITIVE_INFINITY;
                double d2;
                int myPoint = -1;
                int itsPoint = -1;
                int myTempPoint, itsTempPoint;
                boolean reverseMe, reverseIt;
                Polygon itsPolygon = polygon(j);

                // Swap the odd half of the asymmetric cases so they're all the same
                if (myPolygon.isClosed() && !itsPolygon.isClosed()) {
                    polygons.set(i, itsPolygon);
                    polygons.set(j, myPolygon);
                    myPolygon = polygon(i);
                    itsPolygon = polygon(j);
                }

                // Three possibilities ...
                if (!myPolygon.isClosed() && !itsPolygon.isClosed()) {
                    // ... both open
                    // Just compare the four ends
                    reverseMe = true;
                    reverseIt = false;
                    d = Point2D.dSquared(myPolygon.point(0), itsPolygon.point(0));

                    d2 = Point2D.dSquared(myPolygon.point(myPolygon.size() - 1), itsPolygon.point(0));
                    if (d2 < d) {
                        reverseMe = false;
                        reverseIt = false;
                        d = d2;
                    }

                    d2 = Point2D.dSquared(myPolygon.point(0), itsPolygon.point(itsPolygon.size() - 1));
                    if (d2 < d) {
                        reverseMe = true;
                        reverseIt = true;
                        d = d2;
                    }

                    d2 = Point2D.dSquared(myPolygon.point(myPolygon.size() - 1), itsPolygon.point(itsPolygon.size() - 1));
                    if (d2 < d) {
                        reverseMe = false;
                        reverseIt = true;
                        d = d2;
                    }

                    if (d < linkUp) {
                        if (reverseMe) {
                            myPolygon = myPolygon.negate();
                        }
                        if (reverseIt) {
                            itsPolygon = itsPolygon.negate();
                        }
                        myPolygon.add(itsPolygon);
                        polygons.set(i, myPolygon);
                        polygons.remove(j);
                    }

                } else if (!myPolygon.isClosed() && itsPolygon.isClosed()) {
                    // ... I'm open, it's closed;
                    // Compare my end points with all its points
                    reverseMe = true;
                    itsPoint = itsPolygon.nearestVertex(myPolygon.point(0));
                    d = Point2D.dSquared(itsPolygon.point(itsPoint), myPolygon.point(0));
                    itsTempPoint = itsPolygon.nearestVertex(myPolygon.point(myPolygon.size() - 1));
                    d2 = Point2D.dSquared(itsPolygon.point(itsTempPoint), myPolygon.point(myPolygon.size() - 1));
                    if (d2 < d) {
                        itsPoint = itsTempPoint;
                        reverseMe = false;
                        d = d2;
                    }

                    if (d < linkUp) {
                        itsPolygon = itsPolygon.newStart(itsPoint);
                        itsPolygon.add(itsPolygon.point(0)); // Make sure the second half really is closed
                        if (reverseMe) {
                            myPolygon = myPolygon.negate();
                        }
                        myPolygon.add(itsPolygon);
                        myPolygon.setOpen(); // We were closed, but we must now be open
                        polygons.set(i, myPolygon);
                        polygons.remove(j);
                    }

                } else if (myPolygon.isClosed() && itsPolygon.isClosed()) {
                    // ... both closed
                    // Compare all my points with all its points
                    for (int k = 0; k < itsPolygon.size(); k++) {
                        myTempPoint = myPolygon.nearestVertex(itsPolygon.point(k));
                        d2 = Point2D.dSquared(myPolygon.point(myTempPoint), itsPolygon.point(k));
                        if (d2 < d) {
                            myPoint = myTempPoint;
                            itsPoint = k;
                            d = d2;
                        }
                    }

                    if (d < linkUp) {
                        myPolygon = myPolygon.newStart(myPoint);
                        myPolygon.add(myPolygon.point(0)); // Make sure we come back to the start
                        itsPolygon = itsPolygon.newStart(itsPoint);
                        itsPolygon.add(itsPolygon.point(0)); // Make sure we come back to the start
                        myPolygon.add(itsPolygon);
                        //myPolygon.setOpen(); // We were closed, but we must now be open
                        polygons.set(i, myPolygon);
                        polygons.remove(j);
                    }

                } else {
                    // ... Horrible impossibility
                    Debug.getInstance().errorMessage("RrPolygonList.radicalReOrder(): Polygons are neither closed nor open!");
                }
            }
        }
    }

    /**
     * Remove polygon pol from the list, replacing it with two polygons, the
     * first being pol's vertices from 0 to st inclusive, and the second being
     * pol's vertices from en to its end inclusive. It is permissible for st ==
     * en, but if st > en, then they are swapped.
     * 
     * The two new polygons are put on the end of the list.
     */
    private void cutPolygon(final int pol, int st, int en) {
        final Polygon old = polygon(pol);
        final Polygon p1 = new Polygon(old.getAttributes(), old.isClosed());
        final Polygon p2 = new Polygon(old.getAttributes(), old.isClosed());
        if (st > en) {
            final int temp = st;
            st = en;
            en = temp;
        }
        if (st > 0) {
            for (int i = 0; i <= st; i++) {
                p1.add(old.point(i));
            }
        }
        if (en < old.size() - 1) {
            for (int i = en; i < old.size(); i++) {
                p2.add(old.point(i));
            }
        }
        remove(pol);
        if (p1.size() > 1) {
            add(p1);
        }
        if (p2.size() > 1) {
            add(p2);
        }
    }

    /**
     * Search a polygon list to find the nearest point on all the polygons
     * within it to the point p. If omit is non-negative, ignore that polygon in
     * the search.
     * 
     * Only polygons with the same physical extruder are compared.
     */
    private PolygonIndexedPoint ppSearch(final Point2D p, final int omit, final int physicalExtruder) {
        double d = Double.POSITIVE_INFINITY;
        PolygonIndexedPoint result = null;

        if (size() <= 0) {
            return result;
        }

        for (int i = 0; i < size(); i++) {
            if (i != omit) {
                final Polygon pgon = polygon(i);
                if (physicalExtruder == pgon.getAttributes().getExtruder().getPhysicalExtruderNumber()) {
                    final int n = pgon.nearestVertex(p);
                    final double d2 = Point2D.dSquared(p, pgon.point(n));
                    if (d2 < d) {
                        result = new PolygonIndexedPoint(n, i, pgon);
                        d = d2;
                    }
                }
            }
        }

        if (result == null) {
            Debug.getInstance().debugMessage("RrPolygonList.ppSearch(): no point found!");
        }

        return result;
    }

    /**
     * This assumes that the RrPolygonList for which it is called is all the
     * closed outline polygons, and that hatching is their infill hatch. It goes
     * through the outlines and the hatch modifying both so that that outlines
     * actually start and end half-way along a hatch line (that half of the
     * hatch line being deleted). When the outlines are then printed, they start
     * and end in the middle of a solid area, thus minimising dribble.
     * 
     * The outline polygons are re-ordered before the start so that their first
     * point is the most extreme one in the current hatch direction.
     * 
     * Only hatches and outlines whose physical extruders match are altered.
     */
    public void middleStarts(final PolygonList hatching, final LayerRules lc, final BooleanGridList slice) {
        for (int i = 0; i < size(); i++) {
            Polygon outline = polygon(i);
            final GCodeExtruder ex = outline.getAttributes().getExtruder();
            if (ex.getMiddleStart()) {
                Line l = lc.getHatchDirection(ex, false).pLine();
                if (i % 2 != 0 ^ lc.getMachineLayer() % 4 > 1) {
                    l = l.neg();
                }
                outline = outline.newStart(outline.maximalVertex(l));

                final Point2D start = outline.point(0);
                final PolygonIndexedPoint pp = hatching.ppSearch(start, -1, outline.getAttributes().getExtruder()
                        .getPhysicalExtruderNumber());
                boolean failed = true;
                if (pp != null) {
                    pp.findLongEnough(10, 30);
                    final int st = pp.near();
                    final int en = pp.end();
                    final Polygon pg = pp.polygon();

                    // Check that the line from the start of the outline polygon to the first point
                    // of the tail-in is in solid.  If not, we have jumped between polygons and don't
                    // want to use that as a lead in.
                    final Point2D pDif = Point2D.sub(pg.point(st), start);
                    final Point2D pq1 = Point2D.add(start, Point2D.mul(0.25, pDif));
                    final Point2D pq2 = Point2D.add(start, Point2D.mul(0.5, pDif));
                    final Point2D pq3 = Point2D.add(start, Point2D.mul(0.5, pDif));

                    if (slice.membership(pq1) & slice.membership(pq2) & slice.membership(pq3)) {
                        outline.add(start);
                        outline.setExtrudeEnd(-1, 0);

                        if (en >= st) {
                            for (int j = st; j <= en; j++) {
                                outline.add(0, pg.point(j)); // Put it on the beginning...
                                if (j < en) {
                                    outline.add(pg.point(j)); // ...and the end.
                                }
                            }
                        } else {
                            for (int j = st; j >= en; j--) {
                                outline.add(0, pg.point(j));
                                if (j > en) {
                                    outline.add(pg.point(j));
                                }
                            }
                        }
                        set(i, outline);
                        hatching.cutPolygon(pp.pIndex(), st, en);
                        failed = false;
                    }
                }
                if (failed) {
                    set(i, outline.randomStart()); // Best we can do.
                }
            }
        }
    }

    /**
     * Offset (some of) the points in the polygons to allow for the fact that
     * extruded circles otherwise don't come out right. See
     * http://reprap.org/bin/view/Main/ArcCompensation.
     */
    public PolygonList arcCompensate() {
        final PolygonList r = new PolygonList();

        for (int i = 0; i < size(); i++) {
            final Polygon p = polygon(i);
            r.add(p.arcCompensate());
        }

        return r;
    }

    /**
     * Remove polygons shorter than 3 times the infillwidth
     */
    public PolygonList cullShorts() {
        final PolygonList r = new PolygonList();

        for (int i = 0; i < size(); i++) {
            final Polygon p = polygon(i);
            if (p.getLength() > p.getAttributes().getExtruder().getExtrusionInfillWidth() * 3) {
                r.add(p);
            }
        }
        return r;
    }

    /**
     * Is polygon i inside CSG polygon j? (Check twice to make sure...)
     * 
     * @return true if the polygon is inside the CSG polygon, false if otherwise
     */
    private boolean inside(final int i, final int j, final List<CSG2D> csgPols) {
        final CSG2D exp = csgPols.get(j);
        Point2D p = polygon(i).point(0);
        final boolean a = (exp.value(p) <= 0);
        p = polygon(i).point(polygon(i).size() / 2);
        final boolean b = (exp.value(p) <= 0);
        if (a != b) {
            Debug.getInstance().errorMessage("RrPolygonList:inside() - i is both inside and outside j!");
            // casting vote...
            p = polygon(i).point(polygon(i).size() / 3);
            return exp.value(p) <= 0;
        }
        return a;
    }

    /**
     * Take a list of CSG expressions, each one corresponding with the entry of
     * the same index in this class, classify each as being inside other(s) (or
     * not), and hence form a single CSG expression representing them all.
     * 
     * @return single CSG expression based on csgPols list
     */
    private CSG2D resolveInsides(final List<CSG2D> csgPols) {
        int i, j;

        final TreeList universe = new TreeList(-1);
        universe.addChild(new TreeList(0));

        // For each polygon construct a list of all the others that
        // are inside it (if any).
        for (i = 0; i < size() - 1; i++) {
            TreeList isList = universe.walkFind(i);
            if (isList == null) {
                isList = new TreeList(i);
                universe.addChild(isList);
            }
            for (j = i + 1; j < size(); j++) {
                TreeList jsList = universe.walkFind(j);
                if (jsList == null) {
                    jsList = new TreeList(j);
                    universe.addChild(jsList);
                }
                if (inside(j, i, csgPols)) {
                    isList.addChild(jsList);
                }
                if (inside(i, j, csgPols)) {
                    jsList.addChild(isList);
                }
            }
        }

        // Set all the parent pointers
        universe.setParents();
        // Eliminate each leaf from every part of the tree except the node immediately above itself
        for (i = 0; i < size(); i++) {
            final TreeList isList = universe.walkFind(i);
            if (isList == null) {
                Debug.getInstance().errorMessage("RrPolygonList.resolveInsides() - can't find list for polygon " + i);
            }
            TreeList parent = isList.getParent();
            if (parent != null) {
                parent = parent.getParent();
                while (parent != null) {
                    parent.remove(isList);
                    parent = parent.getParent();
                }
            }
        }
        return universe.buildCSG(csgPols);
    }

    /**
     * Compute the CSG representation of all the polygons in the list
     * 
     * @return CSG representation
     */
    public CSG2D toCSG() {
        if (size() == 0) {
            return CSG2D.nothing();
        }
        if (size() == 1) {
            return polygon(0).toCSG();
        }

        final List<CSG2D> csgPols = new ArrayList<CSG2D>();
        for (int i = 0; i < size(); i++) {
            csgPols.add(polygon(i).toCSG());
        }

        return resolveInsides(csgPols);
    }
}
