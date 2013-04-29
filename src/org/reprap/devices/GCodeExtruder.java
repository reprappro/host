package org.reprap.devices;

import java.io.IOException;

import javax.media.j3d.Appearance;
import javax.media.j3d.Material;
import javax.vecmath.Color3f;

import org.reprap.Preferences;
import org.reprap.comms.GCodeWriter;
import org.reprap.machines.GCodePrinter;
import org.reprap.utilities.Debug;

public class GCodeExtruder {
    private final GCodeWriter gcode;
    /**
     * Flag to decide extrude speed
     */
    private boolean separating;
    /**
     * How far we have extruded plus other things like temperature
     */
    private ExtruderState es;
    /**
     * Flag to decide if the machine implements 4D extruding (i.e. it processes
     * extrude lengths along with X, Y, and Z lengths in a 4D Bressenham DDA)
     */
    private boolean fiveD;
    /**
     * The extrusion width in XY
     */
    private double extrusionSize;
    /**
     * The extrusion height in Z TODO: Should this be a machine-wide constant? -
     * AB
     */
    private double extrusionHeight;
    /**
     * The step between infill tracks
     */
    private double extrusionInfillWidth;
    /**
     * below this infill finely
     */
    private int lowerFineLayers;
    /**
     * The number of seconds to cool between layers
     */
    private double coolingPeriod;
    /**
     * The fastest speed of movement in XY when depositing
     */
    private double fastXYFeedrate;
    /**
     * The fastest the extruder can extrude
     */
    private double fastEFeedrate;
    /**
     * The speed from which that machine can do a standing start with this
     * extruder
     */
    private double slowXYFeedrate;
    /**
     * The fastest the machine can accelerate with this extruder
     */
    private double maxAcceleration;
    /**
     * Infill speed [0,1]*maxSpeed
     */
    private double iSpeed;
    /**
     * Outline speed [0,1]*maxSpeed
     */
    private double oSpeed;
    /**
     * Length (mm) to speed up round corners
     */
    private double asLength;
    /**
     * Factor by which to speed up round corners
     */
    private double asFactor;
    /**
     * Line length below which to plot faster
     */
    private double shortLength;
    /**
     * Factor for short line speeds
     */
    private double shortSpeed;
    /**
     * The name of this extruder's material
     */
    private String material;
    /**
     * Number of mm to overlap the hatching infill with the outline. 0 gives
     * none; -ve will leave a gap between the two
     */
    private double infillOverlap = 0;
    /**
     * The diameter of the feedstock (if any)
     */
    private double feedDiameter = -1;
    /**
     * Identifier of the extruder
     */
    private final int myExtruderID;
    /**
     * prefix for our preferences.
     */
    private String prefName;
    /**
     * The colour of the material to use in the simulation windows
     */
    private Appearance materialColour;
    /**
     * The number of ms to pulse the valve to open or close it -ve to supress
     */
    private double valvePulseTime;
    /**
     * The number of milliseconds to wait before starting a border track
     */
    private double extrusionDelayForLayer = 0;
    /**
     * How high to move above the surface for non-extruding movements
     */
    private double lift = 0;
    /**
     * The number of milliseconds to wait before starting a hatch track
     */
    private double extrusionDelayForPolygon = 0;
    /**
     * The number of milliseconds to reverse at the end of a track
     */
    private double extrusionReverseDelay = -1;
    /**
     * The number of milliseconds to wait before starting a border track
     */
    private double valveDelayForLayer = 0;
    /**
     * The number of milliseconds to wait before starting a hatch track
     */
    private double valveDelayForPolygon = 0;
    /**
     * The number of mm to stop extruding before the end of a track
     */
    private double extrusionOverRun;
    /**
     * The number of mm to stop extruding before the end of a track
     */
    private double valveOverRun;
    /**
     * The smallest allowable free-movement height above the base
     */
    private double minLiftedZ = 1;
    /**
     * The number of outlines to plot
     */
    private int shells = 1;
    /**
     * This decides how many layers to fine-infill for areas that are upward- or
     * downward-facing surfaces of the object.
     */
    private int surfaceLayers = 2;
    private double extrusionFoundationWidth;
    private double arcCompensationFactor;
    private double arcShortSides;
    private double evenHatchDirection;
    private double oddHatchDirection;
    private String supportMaterial;
    private String inFillMaterial;
    private double extrudeRatio = 1;
    private boolean middleStart;
    private boolean singleLine = false;
    private boolean insideOut = false;
    /**
     * Our printer object.
     */
    private GCodePrinter printer = null;
    /**
     * The colour black
     */
    private static final Color3f black = new Color3f(0, 0, 0);

    /**
     * @param prefs
     * @param extruderId
     */
    public GCodeExtruder(final GCodeWriter writer, final int extruderId, final GCodePrinter p) {
        printer = p;
        try {
            fiveD = Preferences.loadGlobalBool("FiveD");
        } catch (final Exception e) {
            fiveD = false;
        }
        myExtruderID = extruderId;
        separating = false;
        es = new ExtruderState(refreshPreferences());
        es.setReverse(false);
        final double delay = getExtrusionReverseDelay(); // when we are first called (top down calculation means at our top layer) the
        es.setRetraction(es.retraction() + delay); // layer below will have reversed us at its end on the way up in the actual build.
        es.setSpeed(0);
        gcode = writer;
    }

    /**
     * Zero the extruded length
     */
    public void zeroExtrudedLength(final boolean really) throws Exception {
        es.zero();
        if (really) {
            String s = "G92 E0";
            if (Debug.d()) {
                s += " ; zero the extruded length";
            }
            gcode.queue(s);
        }
    }

    public void setExtrusion(final double speed, final boolean reverse) throws Exception {
        if (getExtruderSpeed() < 0) {
            return;
        }
        String s;
        if (speed < Preferences.tiny()) {
            if (!fiveD) {
                s = "M103";
                if (Debug.d()) {
                    s += " ; extruder off";
                }
                gcode.queue(s);
            }
        } else {
            if (!fiveD) {
                if (speed != es.speed()) {
                    s = "M108 S" + speed;
                    if (Debug.d()) {
                        s += " ; extruder speed in RPM";
                    }
                    gcode.queue(s);
                }

                if (es.reverse()) {
                    s = "M102";
                    if (Debug.d()) {
                        s += " ; extruder on, reverse";
                    }
                    gcode.queue(s);
                } else {
                    s = "M101";
                    if (Debug.d()) {
                        s += " ; extruder on, forward";
                    }
                    gcode.queue(s);
                }
            }
        }
        if (speed > 0) {
            es.setExtruding(true);
        } else {
            es.setExtruding(false);
        }

        es.setSpeed(speed);
        es.setReverse(reverse);
    }

    //TODO: make these real G codes.
    public void setCooler(final boolean coolerOn, final boolean really) throws Exception {
        if (really) {
            String s;
            if (coolerOn) {
                s = "M106";
                if (Debug.d()) {
                    s += " ; cooler on";
                }
                gcode.queue(s);
            } else {
                s = "M107";
                if (Debug.d()) {
                    s += " ; cooler off";
                }
                gcode.queue(s);
            }
        }
    }

    public void setValve(final boolean valveOpen) throws Exception {
        if (valvePulseTime <= 0) {
            return;
        }
        String s;
        if (valveOpen) {
            s = "M126 P" + valvePulseTime;
            if (Debug.d()) {
                s += " ; valve open";
            }
            gcode.queue(s);
        } else {
            s = "M127 P" + valvePulseTime;
            if (Debug.d()) {
                s += " ; valve closed";
            }
            gcode.queue(s);
        }
    }

    /**
     * Allow others to set our extrude length so that all logical extruders
     * talking to one physical extruder can use the same length instance.
     */
    public void setExtrudeState(final ExtruderState e) {
        es = e;
    }

    public int refreshPreferences() {
        prefName = "Extruder" + myExtruderID + "_";
        int result = -1;
        try {
            result = Preferences.loadGlobalInt(prefName + "Address");
            extrusionSize = Preferences.loadGlobalDouble(prefName + "ExtrusionSize(mm)");
            extrusionHeight = Preferences.loadGlobalDouble(prefName + "ExtrusionHeight(mm)");
            extrusionInfillWidth = Preferences.loadGlobalDouble(prefName + "ExtrusionInfillWidth(mm)");
            lowerFineLayers = 2;
            coolingPeriod = Preferences.loadGlobalDouble(prefName + "CoolingPeriod(s)");
            fastXYFeedrate = Preferences.loadGlobalDouble(prefName + "FastXYFeedrate(mm/minute)");
            fastEFeedrate = Preferences.loadGlobalDouble(prefName + "FastEFeedrate(mm/minute)");
            slowXYFeedrate = Preferences.loadGlobalDouble(prefName + "SlowXYFeedrate(mm/minute)");
            maxAcceleration = Preferences.loadGlobalDouble(prefName + "MaxAcceleration(mm/minute/minute)");
            middleStart = Preferences.loadGlobalBool(prefName + "MiddleStart");
            iSpeed = Preferences.loadGlobalDouble(prefName + "InfillSpeed(0..1)");
            oSpeed = Preferences.loadGlobalDouble(prefName + "OutlineSpeed(0..1)");
            asLength = -1;
            asFactor = 0.5;
            material = Preferences.loadGlobalString(prefName + "MaterialType(name)");
            supportMaterial = Preferences.loadGlobalString(prefName + "SupportMaterialType(name)");
            inFillMaterial = Preferences.loadGlobalString(prefName + "InFillMaterialType(name)");
            shortLength = -1;
            shortSpeed = 1;
            infillOverlap = Preferences.loadGlobalDouble(prefName + "InfillOverlap(mm)");
            extrusionDelayForLayer = Preferences.loadGlobalDouble(prefName + "ExtrusionDelayForLayer(ms)");
            extrusionDelayForPolygon = Preferences.loadGlobalDouble(prefName + "ExtrusionDelayForPolygon(ms)");
            extrusionOverRun = Preferences.loadGlobalDouble(prefName + "ExtrusionOverRun(mm)");
            valveDelayForLayer = Preferences.loadGlobalDouble(prefName + "ValveDelayForLayer(ms)");
            valveDelayForPolygon = Preferences.loadGlobalDouble(prefName + "ValveDelayForPolygon(ms)");
            extrusionReverseDelay = Preferences.loadGlobalDouble(prefName + "Reverse(ms)");
            valveOverRun = Preferences.loadGlobalDouble(prefName + "ValveOverRun(mm)");
            minLiftedZ = -1;
            valvePulseTime = 0.5 * Preferences.loadGlobalDouble(prefName + "ValvePulseTime(ms)");
            shells = Preferences.loadGlobalInt(prefName + "NumberOfShells(0..N)");
            extrusionFoundationWidth = Preferences.loadGlobalDouble(prefName + "ExtrusionFoundationWidth(mm)");
            arcCompensationFactor = Preferences.loadGlobalDouble(prefName + "ArcCompensationFactor(0..)");
            arcShortSides = Preferences.loadGlobalDouble(prefName + "ArcShortSides(0..)");
            extrudeRatio = Preferences.loadGlobalDouble(prefName + "ExtrudeRatio(0..)");
            lift = Preferences.loadGlobalDouble(prefName + "Lift(mm)");

            evenHatchDirection = Preferences.loadGlobalDouble(prefName + "EvenHatchDirection(degrees)");
            oddHatchDirection = Preferences.loadGlobalDouble(prefName + "OddHatchDirection(degrees)");

            final Color3f col = new Color3f((float) Preferences.loadGlobalDouble(prefName + "ColourR(0..1)"),
                    (float) Preferences.loadGlobalDouble(prefName + "ColourG(0..1)"),
                    (float) Preferences.loadGlobalDouble(prefName + "ColourB(0..1)"));
            materialColour = new Appearance();
            materialColour.setMaterial(new Material(col, black, col, black, 101f));
            surfaceLayers = Preferences.loadGlobalInt(prefName + "SurfaceLayers(0..N)");
            singleLine = Preferences.loadGlobalBool(prefName + "SingleLine");
            feedDiameter = Preferences.loadGlobalDouble(prefName + "FeedDiameter(mm)");
            insideOut = Preferences.loadGlobalBool(prefName + "InsideOut");
        } catch (final Exception ex) {
            Debug.e("Refresh extruder preferences: " + ex.toString());
        }

        if (printer == null) {
            Debug.e("GenericExtruder(): printer is null!");
        } else {
            fastXYFeedrate = Math.min(printer.getFastXYFeedrate(), fastXYFeedrate);
            slowXYFeedrate = Math.min(printer.getSlowXYFeedrate(), slowXYFeedrate);
            maxAcceleration = Math.min(printer.getMaxXYAcceleration(), maxAcceleration);
        }

        return result;
    }

    public void setPrinter(final GCodePrinter p) {
        printer = p;
    }

    public void stopExtruding() {
        if (es.isExtruding()) {
            try {
                setExtrusion(0, false);
            } catch (final Exception e) {
                //hmm.
            }
            es.setExtruding(false);
        }
    }

    public void setMotor(final boolean motorOn) throws Exception {
        if (getExtruderSpeed() < 0) {
            return;
        }

        if (motorOn) {
            setExtrusion(getExtruderSpeed(), false);
            es.setSpeed(getExtruderSpeed());
        } else {
            setExtrusion(0, false);
            es.setSpeed(0);
        }
        es.setReverse(false);
    }

    public double getInfillFeedrate() {
        return iSpeed * getFastXYFeedrate();
    }

    public double getOutlineFeedrate() {
        return oSpeed * getFastXYFeedrate();
    }

    /**
     * The length in mm to speed up when going round corners (non-Javadoc)
     */
    public double getAngleSpeedUpLength() {
        return asLength;
    }

    public double getAngleFeedrate() {
        return asFactor * getFastXYFeedrate();
    }

    public double getFastXYFeedrate() {
        return fastXYFeedrate;
    }

    public double getFastEFeedrate() {
        return fastEFeedrate;
    }

    /**
     * @return slow XY movement feedrate in mm/minute
     */
    public double getSlowXYFeedrate() {
        return slowXYFeedrate;
    }

    /**
     * @return the fastest the machine can accelerate
     */
    public double getMaxAcceleration() {
        return maxAcceleration;
    }

    public void setSeparating(final boolean s) {
        separating = s;
    }

    public double getExtruderSpeed() throws IOException {
        if (separating) {
            return 3000;
        } else {
            return Preferences.loadGlobalDouble(prefName + "ExtrusionSpeed(mm/minute)");
        }

    }

    public double getExtrusionSize() {
        return extrusionSize;
    }

    public double getExtrusionHeight() {
        return extrusionHeight;
    }

    /**
     * At the top and bottom return the fine width; in between return the braod
     * one. If the braod one is negative, just do fine.
     */
    public double getExtrusionInfillWidth() {
        return extrusionInfillWidth;
    }

    public int getLowerFineLayers() {
        return lowerFineLayers;
    }

    public double getCoolingPeriod() {
        return coolingPeriod;
    }

    public Appearance getAppearance() {
        return materialColour;
    }

    /**
     * @return the name of the material TODO: should this give more information?
     */
    @Override
    public String toString() {
        return material;
    }

    /**
     * get short lengths which need to be plotted faster set -ve to turn this
     * off.
     */
    public double getShortLength() {
        return shortLength;
    }

    /**
     * Feedrate for short lines in mm/minute
     */
    public double getShortLineFeedrate() {
        return shortSpeed * getFastXYFeedrate();
    }

    /**
     * Number of mm to overlap the hatching infill with the outline. 0 gives
     * none; -ve will leave a gap between the two
     */
    public double getInfillOverlap() {
        return infillOverlap;
    }

    /**
     * Gets the number of milliseconds to wait before starting a border track
     */
    public double getExtrusionDelayForLayer() {
        return extrusionDelayForLayer;
    }

    /**
     * Gets the number of milliseconds to wait before starting a hatch track
     */
    public double getExtrusionDelayForPolygon() {
        return extrusionDelayForPolygon;
    }

    /**
     * Gets the number of milliseconds to reverse the extrude motor at the end
     * of a track
     */
    public double getExtrusionReverseDelay() {
        return extrusionReverseDelay;
    }

    /**
     * Gets the number of milliseconds to wait before opening the valve for the
     * first track of a layer
     */
    public double getValveDelayForLayer() {
        if (valvePulseTime < 0) {
            return 0;
        }
        return valveDelayForLayer;
    }

    /**
     * Gets the number of milliseconds to wait before opening the valve for any
     * other track
     */
    public double getValveDelayForPolygon() {
        if (valvePulseTime < 0) {
            return 0;
        }
        return valveDelayForPolygon;
    }

    public double getExtrusionOverRun() {
        return extrusionOverRun;
    }

    /**
     * @return the valve overrun in millimeters (i.e. how many mm before the end
     *         of a track to turn off the extrude motor)
     */
    public double getValveOverRun() {
        if (valvePulseTime < 0) {
            return 0;
        }
        return valveOverRun;
    }

    /**
     * The smallest allowable free-movement height above the base
     */
    public double getMinLiftedZ() {
        return minLiftedZ;
    }

    /**
     * The number of times to go round the outline (0 to supress)
     */
    public int getShells() {
        return shells;
    }

    /**
     * What stuff are we working with?
     */
    public String getMaterial() {
        return material;
    }

    public int getSupportExtruderNumber() {
        return GCodeExtruder.getNumberFromMaterial(supportMaterial);
    }

    public GCodeExtruder getSupportExtruder() {
        return org.reprap.Main.gui.getPrinter().getExtruder(supportMaterial);
    }

    public int getInfillExtruderNumber() {
        return GCodeExtruder.getNumberFromMaterial(inFillMaterial);
    }

    public GCodeExtruder getInfillExtruder() {
        return org.reprap.Main.gui.getPrinter().getExtruder(inFillMaterial);
    }

    public double getExtrusionFoundationWidth() {
        return extrusionFoundationWidth;
    }

    /**
     * The arc compensation factor.
     */
    public double getArcCompensationFactor() {
        return arcCompensationFactor;
    }

    /**
     * The arc short sides.
     */
    public double getArcShortSides() {
        return arcShortSides;
    }

    /**
     * The direction to hatch even-numbered layers in degrees anticlockwise from
     * the X axis
     */
    public double getEvenHatchDirection() {
        return evenHatchDirection;
    }

    /**
     * The direction to hatch odd-numbered layers in degrees anticlockwise from
     * the X axis
     */
    public double getOddHatchDirection() {
        return oddHatchDirection;
    }

    /**
     * Find out if we are currently in reverse
     */
    public boolean getReversing() {
        return es.reverse();
    }

    /**
     * If we are working with feedstock lengths, compute that from the actual
     * length we want to extrude from the nozzle, otherwise just return the
     * extruded length.
     */
    private double filamentDistance(final double distance) {
        if (getFeedDiameter() < 0) {
            return distance;
        }

        return distance * getExtrusionHeight() * getExtrusionSize() / (getFeedDiameter() * getFeedDiameter() * Math.PI / 4);
    }

    /**
     * Get how much extrudate is deposited in a given time (in milliseconds)
     * currentSpeed is in mm per minute. Valve extruders cannot know, so return
     * 0.
     */
    public double getDistanceFromTime(final double time) {
        if (!es.isExtruding() || valvePulseTime > 0) {
            return 0;
        }

        return filamentDistance(extrudeRatio * es.speed() * time / 60000.0);
    }

    /**
     * Get how much extrudate is deposited for a given xy movement currentSpeed
     * is in mm per minute. Valve extruders cannot know, so return 0.
     */
    public double getDistance(final double distance) {
        if (!es.isExtruding() || valvePulseTime > 0) {
            return 0;
        }
        if (printer.getLayerRules().getModelLayer() == 0) {
            return filamentDistance(distance); // Ignore extrude ratio on the bottom layer
        } else {
            return filamentDistance(extrudeRatio * distance);
        }
    }

    /**
     * Get the extrude ratio
     */
    public double getExtrudeRatio() {
        return extrudeRatio;
    }

    /**
     * Set the extrude ratio. Only to be used if you know what you are doing.
     * It's a good idea to set it back when you've finished...
     */
    public void setExtrudeRatio(final double er) {
        extrudeRatio = er;
    }

    /**
     * Find out if we're working in 5D
     */
    public boolean get5D() {
        return fiveD;
    }

    /**
     * Find out how far we have extruded so far
     */
    public ExtruderState getExtruderState() {
        return es;
    }

    /**
     * Each logical extruder has a unique ID
     */
    public int getID() {
        return myExtruderID;
    }

    /**
     * Several logical extruders can share one physical extruder This number is
     * unique to each physical extruder
     */
    public int getPhysicalExtruderNumber() {
        return es.physicalExtruder();
    }

    /**
     * If this is true, plot outlines from the middle of their infilling hatch
     * to reduce dribble at their starts and ends. If false, plot the outline as
     * the outline.
     */
    public boolean getMiddleStart() {
        return middleStart;
    }

    public double getLift() {
        return lift;
    }

    /**
     * This decides how many layers to fine-infill for areas that are upward- or
     * downward-facing surfaces of the object.
     */
    public int getSurfaceLayers() {
        return surfaceLayers;
    }

    /**
     * Are the extruder's models ones that (may) include single-width vectors to
     * be plotted?
     */
    public boolean getSingleLine() {
        return singleLine;
    }

    /**
     * The diameter of the input filament
     */
    public double getFeedDiameter() {
        return feedDiameter;
    }

    /**
     * Plot perimiters inside out or outside in?
     */
    public boolean getInsideOut() {
        return insideOut;
    }

    private static Appearance getAppearanceFromNumber(final int n) {
        final String prefName = "Extruder" + n + "_";
        Color3f col = null;
        try {
            col = new Color3f((float) Preferences.loadGlobalDouble(prefName + "ColourR(0..1)"),
                    (float) Preferences.loadGlobalDouble(prefName + "ColourG(0..1)"),
                    (float) Preferences.loadGlobalDouble(prefName + "ColourB(0..1)"));
        } catch (final Exception ex) {
            Debug.e(ex.toString());
        }
        final Appearance a = new Appearance();
        a.setMaterial(new Material(col, black, col, black, 101f));
        return a;
    }

    public static Appearance getAppearanceFromMaterial(final String material) {
        return (getAppearanceFromNumber(getNumberFromMaterial(material)));
    }

    private static int getNumberFromMaterial(final String material) {
        if (material.equalsIgnoreCase("null")) {
            return -1;
        }

        String[] names;
        try {
            names = Preferences.allMaterials();
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(material)) {
                    return i;
                }
            }
            throw new Exception("getNumberFromMaterial - can't find " + material);
        } catch (final Exception ex) {
            Debug.d(ex.toString());
        }
        return -1;
    }

}