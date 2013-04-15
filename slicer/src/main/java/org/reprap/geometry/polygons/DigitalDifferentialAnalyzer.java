package org.reprap.geometry.polygons;

/**
 * A digital differential analyzer (DDA), calculates the points of a
 * straight line.
 * 
 * @author ensab
 */
final class DigitalDifferentialAnalyzer {
    private final Integer2DPoint delta;
    private Integer2DPoint count;
    private final Integer2DPoint p;
    private final int steps;
    private int taken;
    private final boolean xPlus, yPlus;
    private boolean finished;

    /**
     * Set up the DDA between a start and an end point
     */
    DigitalDifferentialAnalyzer(final Integer2DPoint s, final Integer2DPoint e) {
        delta = e.sub(s).abs();

        steps = Math.max(delta.x, delta.y);
        taken = 0;

        xPlus = e.x >= s.x;
        yPlus = e.y >= s.y;

        count = new Integer2DPoint(-steps / 2, -steps / 2);

        p = new Integer2DPoint(s);

        finished = false;
    }

    /**
     * Return the next point along the line, or null if the last point
     * returned was the final one.
     */
    Integer2DPoint next() {
        if (finished) {
            return null;
        }

        final Integer2DPoint result = new Integer2DPoint(p);

        finished = taken >= steps;

        if (!finished) {
            taken++;
            count = count.add(delta);

            if (count.x > 0) {
                count.x -= steps;
                if (xPlus) {
                    p.x++;
                } else {
                    p.x--;
                }
            }

            if (count.y > 0) {
                count.y -= steps;
                if (yPlus) {
                    p.y++;
                } else {
                    p.y--;
                }
            }
        }

        return result;
    }
}