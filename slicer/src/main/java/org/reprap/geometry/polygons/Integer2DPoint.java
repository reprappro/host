package org.reprap.geometry.polygons;

/**
 * Integer 2D point
 * 
 * @author ensab
 */
final class Integer2DPoint {
    int x;
    int y;

    Integer2DPoint(final int xa, final int ya) {
        x = xa;
        y = ya;
    }

    /**
     * Copy constructor
     */
    Integer2DPoint(final Integer2DPoint a) {
        x = a.x;
        y = a.y;
    }

    /**
     * Are two points the same?
     */
    boolean coincidesWith(final Integer2DPoint b) {
        return x == b.x && y == b.y;
    }

    /**
     * Vector addition
     */
    Integer2DPoint add(final Integer2DPoint b) {
        return new Integer2DPoint(x + b.x, y + b.y);
    }

    /**
     * Vector subtraction
     */
    Integer2DPoint sub(final Integer2DPoint b) {
        return new Integer2DPoint(x - b.x, y - b.y);
    }

    /**
     * Opposite direction
     */
    Integer2DPoint neg() {
        return new Integer2DPoint(-x, -y);
    }

    /**
     * Absolute value
     */
    Integer2DPoint abs() {
        return new Integer2DPoint(Math.abs(x), Math.abs(y));
    }

    /**
     * Squared length
     */
    long magnitude2() {
        return x * x + y * y;
    }

    @Override
    public String toString() {
        return ": " + x + ", " + y + " :";
    }
}