package org.reprap.geometry;

import org.reprap.geometry.polygons.Point2D;

class SegmentSpeeds {
    private final Point2D p1;
    private final Point2D p2;
    private final Point2D p3;
    private boolean plotMiddle;

    static SegmentSpeeds createSegmentSpeeds(final Point2D before, final Point2D now, final double fastLength) {
        final Point2D difference = Point2D.sub(now, before);
        final double length = difference.mod();
        if (length == 0) {
            return null;
        }
        final Point2D direction = difference.norm();
        return new SegmentSpeeds(before, direction, length, fastLength);
    }

    private SegmentSpeeds(final Point2D before, final Point2D direction, final double length, double fastLength) {
        plotMiddle = true;
        if (length <= 2 * fastLength) {
            fastLength = length * 0.5;
            plotMiddle = false;
        }
        p1 = Point2D.add(before, Point2D.mul(direction, fastLength));
        p2 = Point2D.add(p1, Point2D.mul(direction, length - 2 * fastLength));
        p3 = Point2D.add(p2, Point2D.mul(direction, fastLength));
    }

    Point2D getP1() {
        return p1;
    }

    Point2D getP2() {
        return p2;
    }

    Point2D getP3() {
        return p3;
    }

    boolean isPlotMiddle() {
        return plotMiddle;
    }
}