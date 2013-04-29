package org.reprap.geometry.polygons;

import org.reprap.Preferences;

/**
 * A circle (centre and squared radius)
 */
public class Circle {
    private final Point2D centre;
    private final double radius2;

    /**
     * Constructor makes a circle from three points on its circumference. (See
     * "A Programmer's Geometry" p 65, by Adrian Bowyer and John Woodwark)
     */
    public Circle(final Point2D k, final Point2D l, final Point2D m) throws ParallelException {
        final Point2D lk = Point2D.sub(l, k);
        final Point2D mk = Point2D.sub(m, k);
        final double det = Point2D.op(lk, mk);
        if (Math.abs(det) < Preferences.tiny()) {
            throw new ParallelException("RrCircle: colinear points.");
        }
        final double lk2 = Point2D.mul(lk, lk);
        final double mk2 = Point2D.mul(mk, mk);
        Point2D lkt = new Point2D(lk2, lk.y());
        Point2D mkt = new Point2D(mk2, mk.y());
        final double x = 0.5 * Point2D.op(lkt, mkt) / det;
        lkt = new Point2D(lk.x(), lk2);
        mkt = new Point2D(mk.x(), mk2);
        final double y = 0.5 * Point2D.op(lkt, mkt) / det;
        radius2 = x * x + y * y;
        centre = new Point2D(x + k.x(), y + k.y());
    }

    public Point2D centre() {
        return centre;
    }

    public double radiusSquared() {
        return radius2;
    }
}
