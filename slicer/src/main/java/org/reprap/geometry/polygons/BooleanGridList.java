package org.reprap.geometry.polygons;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.reprap.Attributes;
import org.reprap.devices.GCodeExtruder;
import org.reprap.geometry.LayerRules;
import org.reprap.utilities.Debug;

/**
 * Class to hold a list of BooleanGrids with associated attributes for each
 * 
 * @author ensab
 */
public class BooleanGridList implements Iterable<BooleanGrid> {

    private final List<BooleanGrid> shapes = new ArrayList<BooleanGrid>();

    public BooleanGridList() {
    }

    /**
     * Deep copy
     */
    public BooleanGridList(final BooleanGridList a) {
        for (final BooleanGrid booleanGrid : a) {
            shapes.add(new BooleanGrid(booleanGrid));
        }
    }

    /**
     * Return the ith shape
     */
    public BooleanGrid get(final int i) {
        return shapes.get(i);
    }

    /**
     * Is a point in any of the shapes?
     */
    public boolean membership(final Point2D p) {
        for (int i = 0; i < size(); i++) {
            if (get(i).get(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return the ith attribute
     */
    public Attributes attribute(final int i) {
        return shapes.get(i).attribute();
    }

    /**
     * How many shapes are there in the list?
     */
    public int size() {
        return shapes.size();
    }

    /**
     * Remove an entry and close the gap
     */
    public void remove(final int i) {
        shapes.remove(i);
    }

    /**
     * Add a shape on the end
     */
    public void add(final BooleanGrid b) {
        if (b == null) {
            Debug.getInstance().errorMessage("BooleanGridList.add(): attempt to add null BooleanGrid.");
            return;
        }
        if (b != BooleanGrid.nullBooleanGrid()) {
            shapes.add(b);
        }
    }

    /**
     * Add another list of shapes on the end
     */
    public void add(final BooleanGridList aa) {
        for (int i = 0; i < aa.size(); i++) {
            add(aa.get(i));
        }
    }

    /**
     * Reverse the order of the list
     */
    public BooleanGridList reverse() {
        final BooleanGridList result = new BooleanGridList();
        for (int i = size() - 1; i >= 0; i--) {
            result.add(get(i));
        }
        return result;
    }

    /**
     * Offset all the shapes in the list for this layer
     */
    public BooleanGridList offset(final LayerRules lc, final boolean outline, final double multiplier) //, int shellOverride)
    {
        final boolean foundation = lc.getLayingSupport();
        if (outline && foundation) {
            Debug.getInstance().errorMessage("Offsetting a foundation outline!");
        }

        BooleanGridList result = new BooleanGridList();
        for (int i = 0; i < size(); i++) {
            final Attributes att = attribute(i);
            if (att == null) {
                Debug.getInstance().errorMessage("BooleanGridList.offset(): null attribute!");
            } else {
                final GCodeExtruder[] es = lc.getPrinter().getExtruders();
                GCodeExtruder e;
                int shells;
                if (foundation) {
                    e = es[0]; // By convention extruder 0 builds the foundation
                    shells = 1;
                } else {
                    e = att.getExtruder();
                    shells = e.getShells();
                }
                if (outline) {
                    int shell = 0;
                    boolean carryOn = true;
                    while (carryOn && shell < shells) {
                        final double d = multiplier * (shell + 0.5) * e.getExtrusionSize();
                        final BooleanGrid thisOne = get(i).offset(d);
                        if (thisOne.isEmpty()) {
                            carryOn = false;
                        } else {
                            if (shell == 0 && e.getSingleLine()) {
                                final BooleanGrid lines = get(i).lines(thisOne, d);
                                lines.setThin(true);
                                result.add(lines);
                            }
                            result.add(thisOne);
                        }
                        shell++;
                    }
                    if (e.getInsideOut()) {
                        result = result.reverse(); // Plot from the inside out?
                    }
                } else {
                    // Must be a hatch.  Only do it if the gap is +ve or we're building the foundation
                    double offSize;
                    final int ei = e.getInfillExtruderNumber();
                    GCodeExtruder ife = e;
                    if (ei >= 0) {
                        ife = es[ei];
                    }
                    if (foundation) {
                        offSize = 3;
                    } else if (multiplier < 0) {
                        offSize = multiplier * (shells + 0.5) * e.getExtrusionSize() + ife.getInfillOverlap();
                    } else {
                        offSize = multiplier * (shells + 0.5) * e.getExtrusionSize();
                    }
                    if (e.getExtrusionInfillWidth() > 0 || foundation) {
                        result.add(get(i).offset(offSize));
                    }
                }
            }
        }
        return result;
    }

    /**
     * Work out all the polygons forming a set of borders
     */
    public PolygonList borders() {
        final PolygonList result = new PolygonList();
        for (int i = 0; i < size(); i++) {
            result.add(get(i).allPerimiters(attribute(i)));
        }
        return result;
    }

    /**
     * Work out all the open polygons forming a set of infill hatches. If
     * surface is true, these polygons are on the outside (top or bottom). If
     * it's false they are in the interior. If overrideDirection is not null,
     * that is used as the hatch direction. Otherwise the hatch is provided by
     * layerConditions.
     */
    public PolygonList hatch(final LayerRules layerConditions, final boolean surface, final HalfPlane overrideDirection,
            final boolean support) {
        final PolygonList result = new PolygonList();
        final boolean foundation = layerConditions.getLayingSupport();
        final GCodeExtruder[] es = layerConditions.getPrinter().getExtruders();
        for (int i = 0; i < size(); i++) {
            GCodeExtruder e;
            Attributes att = attribute(i);
            if (foundation) {
                e = es[0]; // Extruder 0 is used for foundations
            } else {
                e = att.getExtruder();
            }
            GCodeExtruder ei;
            if (!surface) {
                ei = e.getInfillExtruder();
                if (ei != null) {
                    att = new Attributes(ei.getMaterial(), null, null, ei.getAppearance());
                }
            } else {
                ei = e;
            }
            if (ei != null) {
                HalfPlane hatchLine;
                if (overrideDirection != null) {
                    hatchLine = overrideDirection;
                } else {
                    hatchLine = layerConditions.getHatchDirection(ei, support);
                }
                result.add(get(i).hatch(hatchLine, layerConditions.getHatchWidth(ei), att));

            }
        }
        return result;
    }

    /**
     * Run through the list, unioning entries in it that share the same material
     * so that the result has just one entry per material.
     */
    public BooleanGridList unionDuplicates() {
        final BooleanGridList result = new BooleanGridList();

        if (size() <= 0) {
            return result;
        }

        if (size() == 1) {
            return this;
        }

        final boolean[] usedUp = new boolean[size()];
        for (int i = 0; i < usedUp.length; i++) {
            usedUp[i] = false;
        }

        for (int i = 0; i < size() - 1; i++) {
            if (!usedUp[i]) {
                BooleanGrid union = get(i);
                final int iExId = union.attribute().getExtruder().getID();
                for (int j = i + 1; j < size(); j++) {
                    if (!usedUp[j]) {
                        final BooleanGrid jg = get(j);
                        if (iExId == jg.attribute().getExtruder().getID()) {
                            union = BooleanGrid.union(union, jg);
                            usedUp[j] = true;
                        }
                    }
                }
                result.add(union);
            }
        }

        if (!usedUp[size() - 1]) {
            result.add(get(size() - 1));
        }

        return result;
    }

    /**
     * Return a list of unions between the entries in a and b. Only pairs with
     * the same extruder are unioned. If an element of a has no corresponding
     * element in b, or vice versa, then those elements are returned unmodified
     * in the result.
     */
    public static BooleanGridList unions(final BooleanGridList a, final BooleanGridList b) {
        final BooleanGridList result = new BooleanGridList();

        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        if (a == b) {
            return a;
        }
        if (a.size() <= 0) {
            return b;
        }
        if (b.size() <= 0) {
            return a;
        }

        final boolean[] bMatched = new boolean[b.size()];
        for (int i = 0; i < bMatched.length; i++) {
            bMatched[i] = false;
        }

        for (int i = 0; i < a.size(); i++) {
            final BooleanGrid abg = a.get(i);
            boolean aMatched = false;
            for (int j = 0; j < b.size(); j++) {
                if (abg.attribute().getExtruder().getID() == b.attribute(j).getExtruder().getID()) {
                    result.add(BooleanGrid.union(abg, b.get(j)));
                    bMatched[j] = true;
                    aMatched = true;
                    break;
                }
            }
            if (!aMatched) {
                result.add(abg);
            }
        }

        for (int i = 0; i < bMatched.length; i++) {
            if (!bMatched[i]) {
                result.add(b.get(i));
            }
        }

        return result.unionDuplicates();
    }

    /**
     * Return a list of intersections between the entries in a and b. Only pairs
     * with the same extruder are intersected. If an element of a has no
     * corresponding element in b, or vice versa, then no entry is returned for
     * them.
     */
    public static BooleanGridList intersections(final BooleanGridList a, final BooleanGridList b) {
        final BooleanGridList result = new BooleanGridList();
        if (a == null || b == null) {
            return result;
        }
        if (a == b) {
            return a;
        }
        if (a.size() <= 0 || b.size() <= 0) {
            return result;
        }

        for (int i = 0; i < a.size(); i++) {
            final BooleanGrid abg = a.get(i);
            for (int j = 0; j < b.size(); j++) {
                if (abg.attribute().getExtruder().getID() == b.attribute(j).getExtruder().getID()) {
                    result.add(BooleanGrid.intersection(abg, b.get(j)));
                    break;
                }
            }
        }
        return result.unionDuplicates();
    }

    /**
     * Return only those elements in the list that have no support material
     * specified
     */
    public BooleanGridList cullNoSupport() {
        final BooleanGridList result = new BooleanGridList();

        for (int i = 0; i < size(); i++) {
            if (get(i).attribute().getExtruder().getSupportExtruderNumber() < 0) {
                result.add(get(i));
            }
        }

        return result;
    }

    /**
     * Return only those elements in the list that have support material
     * specified
     */
    public BooleanGridList cullSupport() {
        final BooleanGridList result = new BooleanGridList();

        for (int i = 0; i < size(); i++) {
            if (get(i).attribute().getExtruder().getSupportExtruderNumber() >= 0) {
                result.add(get(i));
            }
        }

        return result;
    }

    /**
     * Return a list of differences between the entries in a and b. Only pairs
     * with the same attribute are subtracted unless ignoreAttributes is true,
     * whereupon everything in b is subtracted from everything in a. If
     * attributes are considered and an element of a has no corresponding
     * element in b, then an entry equal to a is returned for that.
     * 
     * If onlyNullSupport is true then only entries in a with support equal to
     * null are considered. Otherwise ordinary set difference is returned.
     */
    public static BooleanGridList differences(final BooleanGridList a, final BooleanGridList b, final boolean ignoreAttributes) {
        final BooleanGridList result = new BooleanGridList();

        if (a == null) {
            return result;
        }
        if (b == null) {
            return a;
        }
        if (a == b) {
            return result;
        }
        if (a.size() <= 0) {
            return result;
        }
        if (b.size() <= 0) {
            return a;
        }

        for (int i = 0; i < a.size(); i++) {
            final BooleanGrid abg = a.get(i);
            boolean aMatched = false;
            for (int j = 0; j < b.size(); j++) {
                if (ignoreAttributes || (abg.attribute().getExtruder().getID() == b.attribute(j).getExtruder().getID())) {
                    result.add(BooleanGrid.difference(abg, b.get(j), abg.attribute()));
                    if (!ignoreAttributes) {
                        aMatched = true;
                        break;
                    }
                }
            }
            if (!aMatched && !ignoreAttributes) {
                result.add(abg);
            }

        }
        return result.unionDuplicates();
    }

    @Override
    public Iterator<BooleanGrid> iterator() {
        return shapes.iterator();
    }
}
