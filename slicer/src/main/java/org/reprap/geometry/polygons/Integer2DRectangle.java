package org.reprap.geometry.polygons;

/**
 * Holds rectangles represented by the sw point and the size.
 * 
 */
final class Integer2DRectangle {
    Integer2DPoint swCorner;
    Integer2DPoint size;

    /**
     * Construct from the corner points
     */
    Integer2DRectangle(final Integer2DPoint min, final Integer2DPoint max) {
        swCorner = new Integer2DPoint(min);
        size = max.sub(min);
        size.x++;
        size.y++;
    }

    /**
     * Copy constructor
     */
    Integer2DRectangle(final Integer2DRectangle r) {
        swCorner = new Integer2DPoint(r.swCorner);
        size = new Integer2DPoint(r.size);
    }

    /**
     * Useful to have a single-pixel at the origin
     */
    Integer2DRectangle() {
        swCorner = new Integer2DPoint(0, 0);
        size = new Integer2DPoint(1, 1);
    }

    /**
     * Are two rectangles the same?
     */
    boolean coincidesWith(final Integer2DRectangle b) {
        return swCorner.coincidesWith(b.swCorner) && size.coincidesWith(b.size);
    }

    /**
     * This rectangle in the real world
     */
    Rectangle realRectangle() {
        final Integer2DPoint r = new Integer2DPoint(swCorner.x + size.x - 1, swCorner.y + size.y - 1);
        return new Rectangle(realPoint(swCorner), realPoint(r));
    }

    /**
     * Big rectangle containing the union of two.
     */
    Integer2DRectangle union(final Integer2DRectangle b) {
        final Integer2DRectangle result = new Integer2DRectangle(this);
        result.swCorner.x = Math.min(result.swCorner.x, b.swCorner.x);
        result.swCorner.y = Math.min(result.swCorner.y, b.swCorner.y);
        int sx = result.swCorner.x + result.size.x - 1;
        sx = Math.max(sx, b.swCorner.x + b.size.x - 1) - result.swCorner.x + 1;
        int sy = result.swCorner.y + result.size.y - 1;
        sy = Math.max(sy, b.swCorner.y + b.size.y - 1) - result.swCorner.y + 1;
        result.size = new Integer2DPoint(sx, sy);
        return result;
    }

    /**
     * Rectangle containing the intersection of two.
     */
    Integer2DRectangle intersection(final Integer2DRectangle b) {
        final Integer2DRectangle result = new Integer2DRectangle(this);
        result.swCorner.x = Math.max(result.swCorner.x, b.swCorner.x);
        result.swCorner.y = Math.max(result.swCorner.y, b.swCorner.y);
        int sx = result.swCorner.x + result.size.x - 1;
        sx = Math.min(sx, b.swCorner.x + b.size.x - 1) - result.swCorner.x + 1;
        int sy = result.swCorner.y + result.size.y - 1;
        sy = Math.min(sy, b.swCorner.y + b.size.y - 1) - result.swCorner.y + 1;
        result.size = new Integer2DPoint(sx, sy);
        return result;
    }

    /**
     * Grow (dist +ve) or shrink (dist -ve).
     */
    Integer2DRectangle offset(final int dist) {
        final Integer2DRectangle result = new Integer2DRectangle(this);
        result.swCorner.x = result.swCorner.x - dist;
        result.swCorner.y = result.swCorner.y - dist;
        result.size.x = result.size.x + 2 * dist;
        result.size.y = result.size.y + 2 * dist;
        return result;
    }

    /**
     * Anything there?
     */
    boolean isEmpty() {
        return size.x < 0 | size.y < 0;
    }

    Point2D realPoint(final Integer2DPoint point) {
        return new Point2D(BooleanGrid.scale(swCorner.x + point.x), BooleanGrid.scale(swCorner.y + point.y));
    }

    /**
     * Convert real-world point to integer relative to this rectangle
     */
    Integer2DPoint convertToInteger2DPoint(final Point2D a) {
        return new Integer2DPoint(BooleanGrid.iScale(a.x()) - swCorner.x, BooleanGrid.iScale(a.y()) - swCorner.y);
    }

}