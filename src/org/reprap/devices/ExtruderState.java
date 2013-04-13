package org.reprap.devices;

/**
 * Holds the length of filament that an extruder has extruded so far, and other
 * aspects of the state of the extruder. This is used in the extruder class. All
 * logical extruders that correspond to one physical extruder share a single
 * instance of this class. This means that (for example) setting the temperature
 * of one will be reflected automatically in all the others.
 * 
 * @author Adrian
 */
public class ExtruderState {
    private double extrudedLength;
    private double retractionAmount;
    private double motorSpeed;
    private boolean isReversed;
    private boolean isExtruding;
    private final int physicalExtruderNumber;

    ExtruderState(final int physEx) {
        extrudedLength = 1;
        retractionAmount = 0;
        motorSpeed = 0;
        isReversed = false;
        isExtruding = false;
        physicalExtruderNumber = physEx;
    }

    public int physicalExtruder() {
        return physicalExtruderNumber;
    }

    public double length() {
        return extrudedLength;
    }

    public double speed() {
        return motorSpeed;
    }

    public boolean reverse() {
        return isReversed;
    }

    public boolean isExtruding() {
        return isExtruding;
    }

    public void add(final double e) {
        extrudedLength += e;
    }

    public void zero() {
        extrudedLength = 0;
    }

    public void setSpeed(final double sp) {
        motorSpeed = sp;
    }

    public void setReverse(final boolean rev) {
        isReversed = rev;
    }

    public void setExtruding(final boolean ex) {
        isExtruding = ex;
    }

    public void setRetraction(final double r) {
        retractionAmount = r;
    }

    public double retraction() {
        return retractionAmount;
    }
}
