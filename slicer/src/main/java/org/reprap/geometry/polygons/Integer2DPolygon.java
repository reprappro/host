package org.reprap.geometry.polygons;

import java.util.ArrayList;
import java.util.List;

import org.reprap.Attributes;

/**
 * Integer-point polygon
 */
final class Integer2DPolygon {
    /**
     * Auto-extending list of points
     */
    private List<Integer2DPoint> points = null;

    /**
     * Does the polygon loop back on itself?
     */
    private final boolean closed;

    Integer2DPolygon(final boolean c) {
        points = new ArrayList<Integer2DPoint>();
        closed = c;
    }

    /**
     * Deep copy
     */
    private Integer2DPolygon(final Integer2DPolygon a) {
        points = new ArrayList<Integer2DPoint>();
        for (int i = 0; i < a.size(); i++) {
            add(a.point(i));
        }
        closed = a.closed;
    }

    /**
     * Return the point at a given index
     */
    Integer2DPoint point(final int i) {
        return points.get(i);
    }

    /**
     * How many points?
     */
    int size() {
        return points.size();
    }

    /**
     * Add a new point on the end
     */
    void add(final Integer2DPoint p) {
        points.add(p);
    }

    /**
     * Add a whole polygon on the end
     */
    void add(final Integer2DPolygon a) {
        for (int i = 0; i < a.size(); i++) {
            add(a.point(i));
        }
    }

    /**
     * Delete a point and close the resulting gap
     */
    void remove(final int i) {
        points.remove(i);
    }

    /**
     * Find the index of the point in the polygon nearest to another point
     * as long as it's less than tooFar2. Set that to Long.MAX_VALUE for a
     * complete search.
     */
    int nearest(final Integer2DPoint a, final long tooFar2) {
        int i = 0;
        int j = -1;
        long d0 = tooFar2;
        while (i < size()) {
            final long d1 = point(i).sub(a).magnitude2();
            if (d1 < d0) {
                j = i;
                d0 = d1;
            }
            i++;
        }
        return j;
    }

    /**
     * Negate (i.e. reverse cyclic order)
     */
    Integer2DPolygon negate() {
        final Integer2DPolygon result = new Integer2DPolygon(closed);
        for (int i = size() - 1; i >= 0; i--) {
            result.add(point(i));
        }
        return result;
    }

    /**
     * Translate by vector t
     */
    Integer2DPolygon translate(final Integer2DPoint t) {
        final Integer2DPolygon result = new Integer2DPolygon(closed);
        for (int i = 0; i < size(); i++) {
            result.add(point(i).add(t));
        }
        return result;
    }

    /**
     * Find the farthest point from point v1 on the polygon such that the
     * polygon between the two can be approximated by a DDA straight line
     * from v1.
     */
    private int findAngleStart(final int v1) {
        int top = size() - 1;
        int bottom = v1;
        final Integer2DPoint p1 = point(v1);
        int offCount = 0;
        while (top - bottom > 1) {
            final int middle = (bottom + top) / 2;
            final DigitalDifferentialAnalyzer line = new DigitalDifferentialAnalyzer(p1, point(middle));
            Integer2DPoint n = line.next();
            offCount = 0;
            int j = v1;

            while (j <= middle && n != null && offCount < 2) {
                if (point(j).coincidesWith(n)) {
                    offCount = 0;
                } else {
                    offCount++;
                }
                n = line.next();
                j++;
            }

            if (offCount < 2) {
                bottom = middle;
            } else {
                top = middle;
            }
        }
        if (offCount < 2) {
            return top;
        } else {
            return bottom;
        }
    }

    /**
     * Generate an equivalent polygon with fewer vertices by removing chains
     * of points that lie in straight lines.
     */
    Integer2DPolygon simplify() {
        if (size() <= 3) {
            return new Integer2DPolygon(this);
        }
        final Integer2DPolygon r = new Integer2DPolygon(closed);
        int v = 0;
        do {
            r.add(point(v));
            v = findAngleStart(v);
        } while (v < size() - 1);
        r.add(point(v));
        return r;
    }

    /**
     * Convert the polygon into a polygon in the real world.
     */
    Polygon realPolygon(final Attributes a, final Integer2DRectangle rec) {
        final Polygon result = new Polygon(a, closed);
        for (int i = 0; i < size(); i++) {
            final Integer2DPoint r = point(i);
            result.add(rec.realPoint(r));
        }
        return result;
    }
}