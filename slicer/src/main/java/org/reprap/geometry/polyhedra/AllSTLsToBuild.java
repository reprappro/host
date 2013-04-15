package org.reprap.geometry.polyhedra;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.Group;
import javax.media.j3d.SceneGraphObject;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Transform3D;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.reprap.Attributes;
import org.reprap.Main;
import org.reprap.Preferences;
import org.reprap.gcode.GCodeExtruder;
import org.reprap.geometry.LayerRules;
import org.reprap.geometry.polygons.BooleanGrid;
import org.reprap.geometry.polygons.BooleanGridList;
import org.reprap.geometry.polygons.CSG2D;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.graphicio.RFO;
import org.reprap.utilities.Debug;

/**
 * This class holds a list of STLObjects that represents everything that is to
 * be built.
 * 
 * An STLObject may consist of items from several STL files, possible of
 * different materials. But they are all tied together relative to each other in
 * space.
 * 
 * @author Adrian
 */
public class AllSTLsToBuild {
    /**
     * Ring buffer cache to hold previously computed slices for doing infill and
     * support material calculations.
     * 
     * @author ensab
     */
    private final class SliceCache {
        private final BooleanGridList[][] sliceRing;
        private final BooleanGridList[][] supportRing;
        private final int[] layerNumber;
        private int ringPointer;
        private final int noLayer = Integer.MIN_VALUE;
        private int ringSize = 10;

        private SliceCache(final LayerRules lr) {
            if (lr == null) {
                Debug.getInstance().errorMessage("SliceCache(): null LayerRules!");
            }
            ringSize = lr.sliceCacheSize();
            sliceRing = new BooleanGridList[ringSize][stls.size()];
            supportRing = new BooleanGridList[ringSize][stls.size()];
            layerNumber = new int[ringSize];
            ringPointer = 0;
            for (int layer = 0; layer < ringSize; layer++) {
                for (int stl = 0; stl < stls.size(); stl++) {
                    sliceRing[layer][stl] = null;
                    supportRing[layer][stl] = null;
                    layerNumber[layer] = noLayer;
                }
            }
        }

        private int getTheRingLocationForWrite(final int layer) {
            for (int i = 0; i < ringSize; i++) {
                if (layerNumber[i] == layer) {
                    return i;
                }
            }

            final int rp = ringPointer;
            for (int s = 0; s < stls.size(); s++) {
                sliceRing[rp][s] = null;
                supportRing[rp][s] = null;
            }
            ringPointer++;
            if (ringPointer >= ringSize) {
                ringPointer = 0;
            }
            return rp;
        }

        private void setSlice(final BooleanGridList slice, final int layer, final int stl) {
            final int rp = getTheRingLocationForWrite(layer);
            layerNumber[rp] = layer;
            sliceRing[rp][stl] = slice;
        }

        private void setSupport(final BooleanGridList support, final int layer, final int stl) {
            final int rp = getTheRingLocationForWrite(layer);
            layerNumber[rp] = layer;
            supportRing[rp][stl] = support;
        }

        private int getTheRingLocationForRead(final int layer) {
            int rp = ringPointer;
            for (int i = 0; i < ringSize; i++) {
                rp--;
                if (rp < 0) {
                    rp = ringSize - 1;
                }
                if (layerNumber[rp] == layer) {
                    return rp;
                }
            }
            return -1;
        }

        private BooleanGridList getSlice(final int layer, final int stl) {
            final int rp = getTheRingLocationForRead(layer);
            if (rp >= 0) {
                return sliceRing[rp][stl];
            }
            return null;
        }

        private BooleanGridList getSupport(final int layer, final int stl) {
            final int rp = getTheRingLocationForRead(layer);
            if (rp >= 0) {
                return supportRing[rp][stl];
            }
            return null;
        }
    }

    /**
     * OpenSCAD file extension
     */
    private static final String scad = ".scad";

    /**
     * The list of things to be built
     */
    private List<STLObject> stls;

    /**
     * The building layer rules
     */
    private LayerRules layerRules = null;

    /**
     * A plan box round each item
     */
    private List<Rectangle> rectangles;

    /**
     * New list of things to be built for reordering
     */
    private List<STLObject> newstls;

    /**
     * The XYZ box around everything
     */
    private BoundingBox XYZbox;

    /**
     * Is the list editable?
     */
    private boolean frozen;

    /**
     * Recently computed slices
     */
    private SliceCache cache;

    public AllSTLsToBuild() {
        stls = new ArrayList<STLObject>();
        rectangles = null;
        newstls = null;
        XYZbox = null;
        frozen = false;
        cache = null;
        layerRules = null;
    }

    public void add(final STLObject s) {
        if (frozen) {
            Debug.getInstance().errorMessage("AllSTLsToBuild.add(): adding an item to a frozen list.");
        }
        stls.add(s);
    }

    /**
     * Add a new STLObject somewhere in the list
     */
    public void add(final int index, final STLObject s) {
        if (frozen) {
            Debug.getInstance().errorMessage("AllSTLsToBuild.add(): adding an item to a frozen list.");
        }
        stls.add(index, s);
    }

    /**
     * Add a new collection
     */
    public void add(final AllSTLsToBuild a) {
        if (frozen) {
            Debug.getInstance().errorMessage("AllSTLsToBuild.add(): adding a collection to a frozen list.");
        }
        for (int i = 0; i < a.size(); i++) {
            stls.add(a.get(i));
        }
    }

    /**
     * Get the i-th STLObject
     */
    public STLObject get(final int i) {
        return stls.get(i);
    }

    public void remove(final int i) {
        if (frozen) {
            Debug.getInstance().errorMessage("AllSTLsToBuild.remove(): removing an item from a frozen list.");
        }
        stls.remove(i);
    }

    /**
     * Find an object in the list
     */
    private int findSTL(final STLObject st) {
        if (size() <= 0) {
            Debug.getInstance().errorMessage("AllSTLsToBuild.findSTL(): no objects to pick from!");
            return -1;
        }
        int index = -1;
        for (int i = 0; i < size(); i++) {
            if (get(i) == st) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            Debug.getInstance().errorMessage("AllSTLsToBuild.findSTL(): dud object submitted.");
            return -1;
        }
        return index;
    }

    /**
     * Find an object in the list and return the next one.
     */
    public STLObject getNextOne(final STLObject st) {
        int index = findSTL(st);
        index++;
        if (index >= size()) {
            index = 0;
        }
        return get(index);
    }

    /**
     * Return the number of objects.
     */
    public int size() {
        return stls.size();
    }

    /**
     * Create an OpenSCAD (http://www.openscad.org/) program that will read
     * everything in in the same pattern as it is stored here. It can then be
     * written by OpenSCAD as a single STL.
     */
    private String toSCAD() {
        String result = "union()\n{\n";
        for (int i = 0; i < size(); i++) {
            result += get(i).toSCAD();
        }
        result += "}";
        return result;
    }

    /**
     * Write everything to an OpenSCAD program.
     * 
     * @param fn
     *            the directory to write into
     */
    public void saveSCAD(String fn) {
        if (fn.charAt(fn.length() - 1) == File.separator.charAt(0)) {
            fn = fn.substring(0, fn.length() - 1);
        }
        final int sepIndex = fn.lastIndexOf(File.separator);
        final int fIndex = fn.indexOf("file:");
        String name = fn.substring(sepIndex + 1, fn.length());
        String path;
        if (sepIndex >= 0) {
            if (fIndex >= 0) {
                path = fn.substring(fIndex + 5, sepIndex + 1);
            } else {
                path = fn.substring(0, sepIndex + 1);
            }
        } else {
            path = "";
        }
        path += name + File.separator;
        name += scad;
        if (!RFO.checkFile(path, name)) {
            return;
        }
        final File file = new File(path);
        if (!file.exists()) {
            file.mkdir();
        }
        RFO.copySTLs(this, path);
        try {
            final PrintWriter out = new PrintWriter(new FileWriter(path + name));
            out.println(toSCAD());
            out.close();
        } catch (final Exception e) {
            Debug.getInstance().errorMessage("AllSTLsToBuild.saveSCAD(): can't open file: " + path + name);
        }
    }

    /**
     * Reorder the list under user control. The user sends items from the old
     * list one by one. These are added to a new list in that order. When
     * there's only one left that is added last automatically.
     * 
     * Needless to say, this process must be carried through to completion. The
     * function returns true while the process is ongoing, false when it's
     * complete.
     */
    public boolean reorderAdd(final STLObject st) {
        if (frozen) {
            Debug.getInstance().debugMessage("AllSTLsToBuild.reorderAdd(): attempting to reorder a frozen list.");
        }

        if (newstls == null) {
            newstls = new ArrayList<STLObject>();
        }

        final int index = findSTL(st);
        newstls.add(get(index));
        stls.remove(index);

        if (stls.size() > 1) {
            return true;
        }

        newstls.add(get(0));
        stls = newstls;
        newstls = null;
        cache = null; // Just in case...

        return false;
    }

    /**
     * Scan everything loaded and set up the bounding boxes
     */
    public void setBoxes() {
        rectangles = new ArrayList<Rectangle>();
        for (int i = 0; i < stls.size(); i++) {
            rectangles.add(null);
        }

        for (int i = 0; i < stls.size(); i++) {
            final STLObject stl = stls.get(i);
            final Transform3D trans = stl.getTransform();

            final BranchGroup bg = stl.getSTL();

            final java.util.Enumeration<?> enumKids = bg.getAllChildren();

            while (enumKids.hasMoreElements()) {
                final Object ob = enumKids.nextElement();

                if (ob instanceof BranchGroup) {
                    final BranchGroup bg1 = (BranchGroup) ob;
                    final Attributes att = (Attributes) (bg1.getUserData());
                    if (XYZbox == null) {
                        XYZbox = BBox(att.getPart(), trans);
                        if (rectangles.get(i) == null) {
                            rectangles.set(i, new Rectangle(XYZbox.XYbox));
                        } else {
                            rectangles.set(i, Rectangle.union(rectangles.get(i), XYZbox.XYbox));
                        }
                    } else {
                        final BoundingBox s = BBox(att.getPart(), trans);
                        if (s != null) {
                            XYZbox.expand(s);
                            if (rectangles.get(i) == null) {
                                rectangles.set(i, new Rectangle(s.XYbox));
                            } else {
                                rectangles.set(i, Rectangle.union(rectangles.get(i), s.XYbox));
                            }
                        }
                    }
                }
            }
            if (rectangles.get(i) == null) {
                Debug.getInstance().errorMessage("AllSTLsToBuild:ObjectPlanRectangle(): object " + i + " is empty");
            }
        }
    }

    /**
     * Freeze the list - no more editing. Also compute the XY box round
     * everything. Also compute the individual plan boxes round each STLObject.
     */
    private void freeze() {
        if (frozen) {
            return;
        }
        if (layerRules == null) {
            Debug.getInstance().errorMessage("AllSTLsToBuild.freeze(): layerRules not set!");
        }
        frozen = true;

        if (cache == null) {
            cache = new SliceCache(layerRules);
        }
        setBoxes();
    }

    /**
     * Run through a Shape3D and find its enclosing XYZ box
     */
    private BoundingBox BBoxPoints(final Shape3D shape, final Transform3D trans) {
        BoundingBox b = null;
        final GeometryArray g = (GeometryArray) shape.getGeometry();
        final Point3d p1 = new Point3d();
        final Point3d q1 = new Point3d();

        if (g != null) {
            for (int i = 0; i < g.getVertexCount(); i++) {
                g.getCoordinate(i, p1);
                trans.transform(p1, q1);
                if (b == null) {
                    b = new BoundingBox(q1);
                } else {
                    b.expand(q1);
                }
            }
        }
        return b;
    }

    /**
     * Unpack the Shape3D(s) from value and find their enclosing XYZ box
     */
    private BoundingBox BBox(final Object value, final Transform3D trans) {
        BoundingBox b = null;

        if (value instanceof SceneGraphObject) {
            final SceneGraphObject sg = (SceneGraphObject) value;
            if (sg instanceof Group) {
                final Group g = (Group) sg;
                final java.util.Enumeration<?> enumKids = g.getAllChildren();
                while (enumKids.hasMoreElements()) {
                    if (b == null) {
                        b = BBox(enumKids.nextElement(), trans);
                    } else {
                        final BoundingBox s = BBox(enumKids.nextElement(), trans);
                        if (s != null) {
                            b.expand(s);
                        }
                    }
                }
            } else if (sg instanceof Shape3D) {
                b = BBoxPoints((Shape3D) sg, trans);
            }
        }

        return b;
    }

    /**
     * Return the XY box round everything
     */
    public Rectangle ObjectPlanRectangle() {
        if (XYZbox == null) {
            Debug.getInstance().errorMessage("AllSTLsToBuild.ObjectPlanRectangle(): null XYZbox!");
        }
        return XYZbox.XYbox;
    }

    /**
     * Find the top of the highest object.
     */
    public double maxZ() {
        if (XYZbox == null) {
            Debug.getInstance().errorMessage("AllSTLsToBuild.maxZ(): null XYZbox!");
        }
        return XYZbox.Zint.high();
    }

    /**
     * Make sure the list starts with and edge longer than 1.5mm (or the longest
     * if not)
     */
    private void startLong(final ArrayList<LineSegment> edges) {
        if (edges.size() <= 0) {
            return;
        }
        double d = -1;
        int swap = -1;
        LineSegment temp;
        for (int i = 0; i < edges.size(); i++) {
            final double d2 = Point2D.dSquared(edges.get(i).getA(), edges.get(i).getB());
            if (d2 > 2.25) {
                temp = edges.get(0);
                edges.set(0, edges.get(i));
                edges.set(i, temp);
                return;
            }
            if (d2 > d) {
                d = d2;
                swap = i;
            }
        }
        if (swap < 0) {
            Debug.getInstance().errorMessage("AllSTLsToBuild.startLong(): no edges found!");
            return;
        }
        temp = edges.get(0);
        edges.set(0, edges.get(swap));
        edges.set(swap, temp);
        if (Math.sqrt(d) < Preferences.gridRes()) {
            Debug.getInstance().debugMessage("AllSTLsToBuild.startLong(): edge length: " + Math.sqrt(d) + " is the longest.");
        }
    }

    /**
     * Stitch together the some of the edges to form a polygon.
     */
    private Polygon getNextPolygon(final ArrayList<LineSegment> edges) {
        if (!frozen) {
            Debug.getInstance().errorMessage("AllSTLsToBuild:getNextPolygon() called for an unfrozen list!");
            freeze();
        }
        if (edges.size() <= 0) {
            return null;
        }
        startLong(edges);
        LineSegment next = edges.get(0);
        edges.remove(0);
        final Polygon result = new Polygon(next.getAttribute(), true);
        result.add(next.getA());
        result.add(next.getB());
        final Point2D start = next.getA();
        Point2D end = next.getB();

        boolean first = true;
        while (edges.size() > 0) {
            double d2 = Point2D.dSquared(start, end);
            if (first) {
                d2 = Math.max(d2, 1);
            }
            first = false;
            boolean aEnd = false;
            int index = -1;
            for (int i = 0; i < edges.size(); i++) {
                double dd = Point2D.dSquared(edges.get(i).getA(), end);
                if (dd < d2) {
                    d2 = dd;
                    aEnd = true;
                    index = i;
                }
                dd = Point2D.dSquared(edges.get(i).getB(), end);
                if (dd < d2) {
                    d2 = dd;
                    aEnd = false;
                    index = i;
                }
            }

            if (index >= 0) {
                next = edges.get(index);
                edges.remove(index);
                final int ipt = result.size() - 1;
                if (aEnd) {
                    result.set(ipt, Point2D.mul(Point2D.add(next.getA(), result.point(ipt)), 0.5));
                    result.add(next.getB());
                    end = next.getB();
                } else {
                    result.set(ipt, Point2D.mul(Point2D.add(next.getB(), result.point(ipt)), 0.5));
                    result.add(next.getA());
                    end = next.getA();
                }
            } else {
                return result;
            }
        }

        Debug.getInstance().debugMessage("AllSTLsToBuild.getNextPolygon(): exhausted edge list!");

        return result;
    }

    /**
     * Get all the polygons represented by the edges.
     */
    private PolygonList simpleCull(final ArrayList<LineSegment> edges) {
        if (!frozen) {
            Debug.getInstance().errorMessage("AllSTLsToBuild:simpleCull() called for an unfrozen list!");
            freeze();
        }
        final PolygonList result = new PolygonList();
        Polygon next = getNextPolygon(edges);
        while (next != null) {
            if (next.size() >= 3) {
                result.add(next);
            }
            next = getNextPolygon(edges);
        }

        return result;
    }

    /**
     * Compute the support hatching polygons for this set of patterns
     */
    public PolygonList computeSupport(final int stl) {
        // No more additions or movements, please
        freeze();

        // We start by computing the union of everything in this layer because
        // that is everywhere that support _isn't_ needed.
        // We give the union the attribute of the first thing found, though
        // clearly it will - in general - represent many different substances.
        // But it's only going to be subtracted from other shapes, so what it's made
        // from doesn't matter.

        final int layer = layerRules.getModelLayer();

        final BooleanGridList thisLayer = slice(stl, layer);

        BooleanGrid unionOfThisLayer;
        Attributes a;

        if (thisLayer.size() > 0) {
            unionOfThisLayer = thisLayer.get(0);
            a = unionOfThisLayer.attribute();
        } else {
            a = stls.get(stl).attributes(0);
            unionOfThisLayer = BooleanGrid.nullBooleanGrid();
        }
        for (int i = 1; i < thisLayer.size(); i++) {
            unionOfThisLayer = BooleanGrid.union(unionOfThisLayer, thisLayer.get(i), a);
        }

        // Expand the union of this layer a bit, so that any support is a little clear of 
        // this layer's boundaries.

        BooleanGridList allThis = new BooleanGridList();
        allThis.add(unionOfThisLayer);
        allThis = allThis.offset(layerRules, true, 2); // 2mm gap is a bit of a hack...
        if (allThis.size() > 0) {
            unionOfThisLayer = allThis.get(0);
        } else {
            unionOfThisLayer = BooleanGrid.nullBooleanGrid();
        }

        // Get the layer above and union it with this layer.  That's what needs
        // support on the next layer down.

        final BooleanGridList previousSupport = cache.getSupport(layer + 1, stl);

        cache.setSupport(BooleanGridList.unions(previousSupport, thisLayer), layer, stl);

        // Now we subtract the union of this layer from all the stuff requiring support in the layer above.

        BooleanGridList support = new BooleanGridList();

        if (previousSupport != null) {
            for (int i = 0; i < previousSupport.size(); i++) {
                final BooleanGrid above = previousSupport.get(i);
                a = above.attribute();
                final GCodeExtruder e = a.getExtruder().getSupportExtruder();
                if (e != null) {
                    if (layerRules.extruderLiveThisLayer(e.getID())) {
                        support.add(BooleanGrid.difference(above, unionOfThisLayer, a));
                    }
                }
            }
            support = support.unionDuplicates();
        }

        // Now force the attributes of the support pattern to be the support extruders
        // for all the materials in it.  If the material isn't active in this layer, remove it from the list

        for (int i = 0; i < support.size(); i++) {
            final GCodeExtruder e = support.attribute(i).getExtruder().getSupportExtruder();
            if (e == null) {
                Debug.getInstance().errorMessage("AllSTLsToBuild.computeSupport(): null support extruder specified!");
                continue;
            }
            support.get(i).forceAttribute(new Attributes(e.getMaterial(), null, null, e.getAppearance()));
        }

        return support.hatch(layerRules, false, null, true);
    }

    /**
     * Set the building layer rules as soon as we know them
     */
    public void setLayerRules(final LayerRules lr) {
        layerRules = lr;
    }

    /**
     * Select from a slice (allLayer) just those parts of it that will be
     * plotted this layer
     */
    BooleanGridList neededThisLayer(final BooleanGridList allLayer, final boolean infill, final boolean support) {
        final BooleanGridList neededSlice = new BooleanGridList();
        for (int i = 0; i < allLayer.size(); i++) {
            GCodeExtruder e;
            if (infill) {
                e = allLayer.get(i).attribute().getExtruder().getInfillExtruder();
            } else if (support) {
                e = allLayer.get(i).attribute().getExtruder().getSupportExtruder();
            } else {
                e = allLayer.get(i).attribute().getExtruder();
            }
            if (e != null) {
                if (layerRules.extruderLiveThisLayer(e.getID())) {
                    neededSlice.add(allLayer.get(i));
                }
            }
        }
        return neededSlice;
    }

    /**
     * Compute the infill hatching polygons for this set of patterns
     */
    public PolygonList computeInfill(final int stl) {
        final InFillPatterns infill = new InFillPatterns();
        freeze();
        return infill.computeHatchedPolygons(stl, layerRules, this);
    }

    public void setUpShield() {
        if (frozen) {
            Debug.getInstance().errorMessage("AllSTLsToBuild.setUpShield() called when frozen!");
        }

        try {
            if (!Preferences.loadGlobalBool("Shield")) {
                return;
            }
        } catch (final IOException e) {
            Debug.getInstance().errorMessage(e.toString());
        }

        setBoxes();
        final double modelZMax = maxZ();

        final STLObject s = new STLObject();
        final Attributes att = s.addSTL(Preferences.getActiveMachineDir() + "shield.stl", null, Preferences.unselectedApp(),
                null);

        final Vector3d shieldSize = s.extent();

        final Point2D shieldPos = layerRules.getPurgeMiddle();
        double xOff = shieldPos.x();
        double yOff = shieldPos.y();

        final double zScale = modelZMax / shieldSize.z;
        final double zOff = 0.5 * (modelZMax - shieldSize.z);

        s.rScale(zScale, true);

        if (!layerRules.purgeXOriented()) {
            s.translate(new Vector3d(-0.5 * shieldSize.x, -0.5 * shieldSize.y, 0));
            final Transform3D t3d1 = s.getTransform();
            final Transform3D t3d2 = new Transform3D();
            t3d2.rotZ(0.5 * Math.PI);
            t3d1.mul(t3d2);
            s.setTransform(t3d1);
            s.translate(new Vector3d(yOff, -xOff, zOff));
        } else {
            xOff -= 0.5 * shieldSize.x;
            yOff -= shieldSize.y;
            s.translate(new Vector3d(xOff, yOff, zOff));
        }

        try {
            att.setMaterial(Preferences.allMaterials()[0]);
        } catch (final IOException e) {
            Debug.getInstance().errorMessage(e.toString());
        }

        Main.gui.getBuilder().anotherSTL(s, att, 0);
    }

    /**
     * Compute the outline polygons for this set of patterns.
     */
    public PolygonList computeOutlines(final int stl, final PolygonList hatchedPolygons) {
        freeze();

        // The shapes to outline.
        BooleanGridList slice = slice(stl, layerRules.getModelLayer());

        // Pick out the ones we need to do at this height
        slice = neededThisLayer(slice, false, false);

        if (slice.size() <= 0) {
            return new PolygonList();
        }

        PolygonList borderPolygons;

        // Are we building the raft under things?  If so, there is no border.
        if (layerRules.getLayingSupport()) {
            borderPolygons = null;
        } else {
            final BooleanGridList offBorder = slice.offset(layerRules, true, -1);
            borderPolygons = offBorder.borders();
        }

        // If we've got polygons to plot, maybe amend them so they start in the middle 
        // of a hatch (this gives cleaner boundaries).  
        if (borderPolygons != null && borderPolygons.size() > 0) {
            borderPolygons.middleStarts(hatchedPolygons, layerRules, slice);
        }

        return borderPolygons;
    }

    /**
     * Generate a set of pixel-map representations, one for each extruder, for
     * STLObject stl at height z.
     */
    BooleanGridList slice(final int stlIndex, final int layer) {
        if (!frozen) {
            Debug.getInstance().errorMessage("AllSTLsToBuild.slice() called when unfrozen!");
            freeze();
        }

        if (layer < 0) {
            return new BooleanGridList();
        }

        // Is the result in the cache?  If so, just use that.
        BooleanGridList result = cache.getSlice(layer, stlIndex);
        if (result != null) {
            return result;
        }

        // Haven't got it in the cache, so we need to compute it
        // Anything there?
        if (rectangles.get(stlIndex) == null) {
            return new BooleanGridList();
        }

        // Probably...
        final double z = layerRules.getModelZ(layer) + layerRules.getZStep() * 0.5;
        final GCodeExtruder[] extruders = layerRules.getPrinter().getExtruders();
        result = new BooleanGridList();
        CSG2D csgp = null;
        PolygonList pgl = new PolygonList();
        int extruderID;

        // Bin the edges and CSGs (if any) by extruder ID.
        @SuppressWarnings("unchecked")
        final ArrayList<LineSegment>[] edges = new ArrayList[extruders.length];
        @SuppressWarnings("unchecked")
        final ArrayList<CSG3D>[] csgs = new ArrayList[extruders.length];
        final Attributes[] atts = new Attributes[extruders.length];

        for (extruderID = 0; extruderID < extruders.length; extruderID++) {
            if (extruders[extruderID].getID() != extruderID) {
                Debug.getInstance().errorMessage(
                        "AllSTLsToBuild.slice(): extruder " + extruderID + "out of sequence: " + extruders[extruderID].getID());
            }
            edges[extruderID] = new ArrayList<LineSegment>();
            csgs[extruderID] = new ArrayList<CSG3D>();
        }

        // Generate all the edges for STLObject i at this z
        final STLObject stlObject = stls.get(stlIndex);
        final Transform3D trans = stlObject.getTransform();
        final Matrix4d m4 = new Matrix4d();
        trans.get(m4);

        //BranchGroup bg = stlObject.getSTL();
        for (int i = 0; i < stlObject.getCount(); i++) {
            final BranchGroup bg1 = stlObject.getSTL(i);
            final Attributes attr = (Attributes) (bg1.getUserData());
            atts[attr.getExtruder().getID()] = attr;
            final CSG3D csg = stlObject.getCSG(i);
            if (csg != null) {
                csgs[attr.getExtruder().getID()].add(csg.transform(m4));
            } else {
                recursiveSetEdges(attr.getPart(), trans, z, attr, edges);
            }
        }

        // Turn them into lists of polygons, one for each extruder, then
        // turn those into pixelmaps.
        for (extruderID = 0; extruderID < edges.length; extruderID++) {
            // Deal with CSG shapes (much simpler and faster).
            for (int i = 0; i < csgs[extruderID].size(); i++) {
                csgp = CSG2D.slice(csgs[extruderID].get(i), z);
                result.add(new BooleanGrid(csgp, rectangles.get(stlIndex), atts[extruderID]));
            }

            // Deal with STL-generated edges
            if (edges[extruderID].size() > 0) {
                pgl = simpleCull(edges[extruderID]);

                if (pgl.size() > 0) {
                    // Remove wrinkles
                    pgl = pgl.simplify(Preferences.gridRes() * 1.5);

                    // Fix small radii
                    pgl = pgl.arcCompensate();

                    csgp = pgl.toCSG();

                    // We use the plan rectangle of the entire stl object to store the bitmap, even though this slice may be
                    // much smaller than the whole.  This allows booleans on slices to be computed much more
                    // quickly as each is in the same rectangle so the bit patterns match exactly.  But it does use more memory.
                    result.add(new BooleanGrid(csgp, rectangles.get(stlIndex), pgl.polygon(0).getAttributes()));
                }
            }
        }

        cache.setSlice(result, layer, stlIndex);
        return result;
    }

    /**
     * Add the edge where the plane z cuts the triangle (p, q, r) (if it does).
     * Also update the triangulation of the object below the current slice used
     * for the simulation window.
     */
    private void addEdge(final Point3d p, final Point3d q, final Point3d r, final double z, final Attributes att,
            final ArrayList<LineSegment> edges[]) {
        Point3d odd = null, even1 = null, even2 = null;
        int pat = 0;

        if (p.z < z) {
            pat = pat | 1;
        }
        if (q.z < z) {
            pat = pat | 2;
        }
        if (r.z < z) {
            pat = pat | 4;
        }

        switch (pat) {
        // All above
        case 0:
            return;
            // All below
        case 7:
            return;
            // q, r below, p above	
        case 6:
            //twoBelow = true;
            // p below, q, r above
        case 1:
            odd = p;
            even1 = q;
            even2 = r;
            break;
        // p, r below, q above	
        case 5:
            //twoBelow = true;
            // q below, p, r above	
        case 2:
            odd = q;
            even1 = r;
            even2 = p;
            break;
        // p, q below, r above	
        case 3:
            //twoBelow = true;
            // r below, p, q above	
        case 4:
            odd = r;
            even1 = p;
            even2 = q;
            break;
        default:
            Debug.getInstance().errorMessage("addEdge(): the | function doesn't seem to work...");
        }

        // Work out the intersection line segment (e1 -> e2) between the z plane and the triangle
        even1.sub(odd);
        even2.sub(odd);
        double t = (z - odd.z) / even1.z;
        Point2D e1 = new Point2D(odd.x + t * even1.x, odd.y + t * even1.y);
        e1 = new Point2D(e1.x(), e1.y());
        t = (z - odd.z) / even2.z;
        Point2D e2 = new Point2D(odd.x + t * even2.x, odd.y + t * even2.y);
        e2 = new Point2D(e2.x(), e2.y());

        // Too short?
        edges[att.getExtruder().getID()].add(new LineSegment(e1, e2, att));
    }

    /**
     * Run through a Shape3D and set edges from it at plane z Apply the
     * transform first
     */
    private void addAllEdges(final Shape3D shape, final Transform3D trans, final double z, final Attributes att,
            final ArrayList<LineSegment> edges[]) {
        final GeometryArray g = (GeometryArray) shape.getGeometry();
        final Point3d p1 = new Point3d();
        final Point3d p2 = new Point3d();
        final Point3d p3 = new Point3d();
        final Point3d q1 = new Point3d();
        final Point3d q2 = new Point3d();
        final Point3d q3 = new Point3d();

        if (g.getVertexCount() % 3 != 0) {
            Debug.getInstance().errorMessage("addAllEdges(): shape3D with vertices not a multiple of 3!");
        }
        for (int i = 0; i < g.getVertexCount(); i += 3) {
            g.getCoordinate(i, p1);
            g.getCoordinate(i + 1, p2);
            g.getCoordinate(i + 2, p3);
            trans.transform(p1, q1);
            trans.transform(p2, q2);
            trans.transform(p3, q3);
            addEdge(q1, q2, q3, z, att, edges);
        }
    }

    /**
     * Unpack the Shape3D(s) from value and set edges from them
     */
    private void recursiveSetEdges(final Object value, final Transform3D trans, final double z, final Attributes att,
            final ArrayList<LineSegment> edges[]) {
        if (value instanceof SceneGraphObject) {
            final SceneGraphObject sg = (SceneGraphObject) value;
            if (sg instanceof Group) {
                final Group g = (Group) sg;
                final java.util.Enumeration<?> enumKids = g.getAllChildren();
                while (enumKids.hasMoreElements()) {
                    recursiveSetEdges(enumKids.nextElement(), trans, z, att, edges);
                }
            } else if (sg instanceof Shape3D) {
                addAllEdges((Shape3D) sg, trans, z, att, edges);
            }
        }
    }

}
