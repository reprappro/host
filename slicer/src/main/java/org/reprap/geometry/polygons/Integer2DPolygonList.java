package org.reprap.geometry.polygons;

import java.util.ArrayList;
import java.util.List;

import org.reprap.Attributes;

/**
 * A list of polygons
 */
final class Integer2DPolygonList {
    private List<Integer2DPolygon> polygons = null;

    Integer2DPolygonList() {
        polygons = new ArrayList<Integer2DPolygon>();
    }

    /**
     * Return the ith polygon
     */
    Integer2DPolygon polygon(final int i) {
        return polygons.get(i);
    }

    /**
     * How many polygons are there in the list?
     */
    int size() {
        return polygons.size();
    }

    /**
     * Add a polygon on the end
     */
    void add(final Integer2DPolygon p) {
        polygons.add(p);
    }

    /**
     * Replace a polygon in the list
     */
    void set(final int i, final Integer2DPolygon p) {
        polygons.set(i, p);
    }

    /**
     * Get rid of a polygon from the list
     */
    void remove(final int i) {
        polygons.remove(i);
    }

    /**
     * Translate by vector t
     */
    Integer2DPolygonList translate(final Integer2DPoint t) {
        final Integer2DPolygonList result = new Integer2DPolygonList();
        for (int i = 0; i < size(); i++) {
            result.add(polygon(i).translate(t));
        }
        return result;
    }

    /**
     * Turn all the polygons into real-world polygons
     * 
     * @param rec
     *            TODO
     */
    PolygonList realPolygons(final Attributes a, final Integer2DRectangle rec) {
        final PolygonList result = new PolygonList();
        for (int i = 0; i < size(); i++) {
            result.add(polygon(i).realPolygon(a, rec));
        }
        return result;
    }

    /**
     * Simplify all the polygons
     */
    Integer2DPolygonList simplify() {
        final Integer2DPolygonList result = new Integer2DPolygonList();
        for (int i = 0; i < size(); i++) {
            result.add(polygon(i).simplify());
        }
        return result;
    }
}