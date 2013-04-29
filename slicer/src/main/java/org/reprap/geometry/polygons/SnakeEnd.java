package org.reprap.geometry.polygons;

/**
 * Holds the ends of hatching patterns. Snakes are a combination of the
 * hatching lines that infill a shape plus the bits of boundary that join
 * their ends to make a zig-zag pattern.
 * 
 * @author ensab
 */
final class SnakeEnd {
    final Integer2DPolygon track;
    final int hitPlaneIndex;

    SnakeEnd(final Integer2DPolygon t, final int h) {
        track = t;
        hitPlaneIndex = h;
    }
}