package org.reprap.geometry.polygons;

import org.reprap.utilities.Debug;

/**
 * Holds a polygon index and the index of a point within it
 * 
 * @author ensab
 */
class PolygonIndexedPoint {
    private final int pNear;
    private int pEnd;
    private final int pg;
    private final Polygon pol;

    PolygonIndexedPoint(final int pnr, final int pgn, final Polygon poly) {
        pNear = pnr;
        pg = pgn;
        pol = poly;
    }

    int near() {
        return pNear;
    }

    int end() {
        return pEnd;
    }

    int pIndex() {
        return pg;
    }

    Polygon polygon() {
        return pol;
    }

    private void midPoint(int i, int j) {
        if (i > j) {
            final int temp = i;
            i = j;
            j = temp;
        }

        if (i < 0 || i > pol.size() - 1 || j < 0 || j > pol.size() - 1) {
            Debug.getInstance().errorMessage("RrPolygonList.midPoint(): i and/or j wrong: i = " + i + ", j = " + j);
        }

        Point2D p = Point2D.add(pol.point(i), pol.point(j));
        p = Point2D.mul(p, 0.5);
        pol.add(j, p);
        pEnd = j;
    }

    /**
     * Find the a long-enough polygon edge away from point pNear and put its
     * index in pNext.
     */
    void findLongEnough(final double longEnough, final double searchFor) {
        double d;
        double sum = 0;
        double longest = -1;
        Point2D p = pol.point(pNear);
        Point2D q;
        int inc = 1;
        if (pNear > pol.size() / 2 - 1) {
            inc = -1;
        }
        int i = pNear;
        int iLongest = i;
        int jLongest = i;
        int j = i;
        while (i > 0 && i < pol.size() - 1 && sum < searchFor) {
            i += inc;
            q = pol.point(i);
            d = Point2D.d(p, q);
            if (d >= longEnough) {
                midPoint(i, j);
                return;
            }
            if (d > longest) {
                longest = d;
                iLongest = i;
                jLongest = j;
            }
            sum += d;
            p = q;
            j = i;
        }
        midPoint(iLongest, jLongest);
    }
}