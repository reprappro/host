package org.reprap.devices;

import java.io.IOException;

import javax.media.j3d.Appearance;
import javax.media.j3d.Material;
import javax.vecmath.Color3f;

import org.reprap.Preferences;
import org.reprap.comms.GCodeReaderAndWriter;
import org.reprap.geometry.LayerRules;
import org.reprap.machines.GCodePrinter;
import org.reprap.utilities.Debug;

public class GCodeExtruder {
    GCodeReaderAndWriter gcode;
    /**
     * Flag to decide extrude speed
     */
    protected boolean separating;
    /**
     * How far we have extruded plus other things like temperature
     */
    protected ExtruderState es;
    /**
     * Flag to decide if the machine implements 4D extruding (i.e. it processes
     * extrude lengths along with X, Y, and Z lengths in a 4D Bressenham DDA)
     */
    protected boolean fiveD;
    /**
     * Maximum motor speed (value between 0-255)
     */
    protected int maxExtruderSpeed;
    /**
     * The time to run the extruder at the start -ve values supress
     */
    protected double purgeTime;
    /**
     * The PWM into stepper extruders
     */
    protected double extrusionPWM;
    /**
     * The extrusion temperature
     */
    protected double extrusionTemp;
    /**
     * The extrusion width in XY
     */
    protected double extrusionSize;
    /**
     * The extrusion height in Z TODO: Should this be a machine-wide constant? -
     * AB
     */
    protected double extrusionHeight;
    /**
     * The step between infill tracks
     */
    protected double extrusionInfillWidth;
    /**
     * below this infill finely
     */
    protected int lowerFineLayers;
    /**
     * Above this infill finely
     */
    protected int upperFineLayers;
    /**
     * Use this for broad infill in the middle; if negative, always infill fine.
     */
    protected double extrusionBroadWidth;
    /**
     * The number of seconds to cool between layers
     */
    protected double coolingPeriod;
    /**
     * The fastest speed of movement in XY when depositing
     */
    protected double fastXYFeedrate;
    /**
     * The fastest the extruder can extrude
     */
    protected double fastEFeedrate;
    /**
     * The speed from which that machine can do a standing start with this
     * extruder
     */
    protected double slowXYFeedrate;
    /**
     * The fastest the machine can accelerate with this extruder
     */
    protected double maxAcceleration;
    /**
     * Zero torque speed
     */
    protected int t0;
    /**
     * Infill speed [0,1]*maxSpeed
     */
    protected double iSpeed;
    /**
     * Outline speed [0,1]*maxSpeed
     */
    protected double oSpeed;
    /**
     * Length (mm) to speed up round corners
     */
    protected double asLength;
    /**
     * Factor by which to speed up round corners
     */
    protected double asFactor;
    /**
     * Line length below which to plot faster
     */
    protected double shortLength;
    /**
     * Factor for short line speeds
     */
    protected double shortSpeed;
    /**
     * The name of this extruder's material
     */
    protected String material;
    /**
     * Number of mm to overlap the hatching infill with the outline. 0 gives
     * none; -ve will leave a gap between the two
     */
    protected double infillOverlap = 0;
    /**
     * The diameter of the feedstock (if any)
     */
    protected double feedDiameter = -1;
    /**
     * Identifier of the extruder
     */
    protected int myExtruderID;
    /**
     * prefix for our preferences.
     */
    protected String prefName;
    /**
     * Start polygons at random perimiter points
     */
    protected boolean randSt = false;
    /**
     * Start polygons at incremented perimiter points
     */
    protected boolean incrementedSt = false;
    /**
     * The colour of the material to use in the simulation windows
     */
    protected Appearance materialColour;
    /**
     * Enable wiping procedure for nozzle
     */
    protected boolean nozzleWipeEnabled;
    /**
     * Co-ordinates for the nozzle wiper
     */
    protected double nozzleWipeDatumX;
    protected double nozzleWipeDatumY;
    /**
     * X Distance to move nozzle over wiper
     */
    protected double nozzleWipeStrokeX;
    /**
     * Y Distance to move nozzle over wiper
     */
    protected double nozzleWipeStrokeY;
    /**
     * Number of wipe cycles per method call
     */
    protected int nozzleWipeFreq;
    /**
     * Number of seconds to run to re-start the nozzle before a wipe
     */
    protected double nozzleClearTime;
    /**
     * Number of seconds to wait after restarting the nozzle
     */
    protected double nozzleWaitTime;
    /**
     * The current coordinate to wipe at
     */
    protected double wipeX;
    /**
     * The number of ms to pulse the valve to open or close it -ve to supress
     */
    protected double valvePulseTime;
    /**
     * The number of milliseconds to wait before starting a border track
     */
    protected double extrusionDelayForLayer = 0;
    /**
     * How high to move above the surface for non-extruding movements
     */
    protected double lift = 0;
    /**
     * The number of milliseconds to wait before starting a hatch track
     */
    protected double extrusionDelayForPolygon = 0;
    /**
     * The number of milliseconds to reverse at the end of a track
     */
    protected double extrusionReverseDelay = -1;
    /**
     * The number of milliseconds to wait before starting a border track
     */
    protected double valveDelayForLayer = 0;
    /**
     * The number of milliseconds to wait before starting a hatch track
     */
    protected double valveDelayForPolygon = 0;
    /**
     * The number of mm to stop extruding before the end of a track
     */
    protected double extrusionOverRun;
    /**
     * The number of mm to stop extruding before the end of a track
     */
    protected double valveOverRun;
    /**
     * The smallest allowable free-movement height above the base
     */
    protected double minLiftedZ = 1;
    /**
     * The number of outlines to plot
     */
    protected int shells = 1;
    /**
     * Stop the extrude motor between segments?
     */
    protected boolean pauseBetweenSegments = true;
    /**
     * This decides how many layers to fine-infill for areas that are upward- or
     * downward-facing surfaces of the object.
     * 
     * @return
     */
    protected int surfaceLayers = 2;
    /**
     * Are we currently extruding?
     */
    private double extrusionFoundationWidth;
    private double extrusionLastFoundationWidth;
    private double separationFraction;
    private double arcCompensationFactor;
    private double arcShortSides;
    private double evenHatchDirection;
    private double oddHatchDirection;
    private String supportMaterial;
    private String inFillMaterial;
    protected double extrudeRatio = 1;
    protected boolean middleStart;
    protected boolean singleLine = false;
    protected boolean insideOut = false;
    /**
     * Our printer object.
     */
    protected GCodePrinter printer = null;
    /**
     * The colour black
     */
    protected static final Color3f black = new Color3f(0, 0, 0);

    /**
     * @param prefs
     * @param extruderId
     */
    public GCodeExtruder(final GCodeReaderAndWriter writer, final int extruderId, final GCodePrinter p) {
        printer = p;
        try {
            fiveD = Preferences.loadGlobalBool("FiveD");
        } catch (final Exception e) {
            fiveD = false;
        }
        myExtruderID = extruderId;
        separating = false;
        es = new ExtruderState(refreshPreferences());
        es.setReverse(false); // We are not reversing...
        final double delay = getExtrusionReverseDelay(); // ...but when we are first called (top down calculation means at our top layer) the
        es.setRetraction(es.retraction() + delay); // layer below will have reversed us at its end on the way up in the actual build.
        es.setSpeed(0);
        gcode = writer;
    }

    /**
     * Zero the extruded length
     * 
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

    public void setTemperature(final double temperature, final boolean wait) throws Exception {
        String s;
        if (wait) {
            s = "M109 S" + temperature;
            if (Debug.d()) {
                s += " ; set temperature and wait";
            }
        } else {
            s = "M104 S" + temperature;
            if (Debug.d()) {
                s += " ; set temperature and return";
            }
        }
        gcode.queue(s);
        es.zero();
    }

    public double getTemperature() throws Exception {
        String s = "M105";
        if (Debug.d()) {
            s += " ; get temperature";
        }
        gcode.queue(s);
        es.setCurrentTemperature(gcode.getETemp());
        return es.currentTemperature();
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

    public boolean isEmpty() {
        return false;
    }

    /**
     * Allow others to set our extrude length so that all logical extruders
     * talking to one physical extruder can use the same length instance.
     * 
     * @param e
     */
    public void setExtrudeState(final ExtruderState e) {
        es = e;
    }

    public int refreshPreferences() {
        prefName = "Extruder" + myExtruderID + "_";
        int result = -1;
        try {
            result = Preferences.loadGlobalInt(prefName + "Address");
            maxExtruderSpeed = 255; //Preferences.loadGlobalInt(prefName + "MaxSpeed(0..255)");
            //			extrusionSpeed = Preferences.loadGlobalDouble(prefName + "ExtrusionSpeed(mm/minute)");
            purgeTime = Preferences.loadGlobalDouble(prefName + "Purge(ms)");
            //extrusionPWM = Preferences.loadGlobalDouble(prefName + "ExtrusionPWM(0..1)");
            extrusionPWM = -1;
            extrusionTemp = Preferences.loadGlobalDouble(prefName + "ExtrusionTemp(C)");
            extrusionSize = Preferences.loadGlobalDouble(prefName + "ExtrusionSize(mm)");
            extrusionHeight = Preferences.loadGlobalDouble(prefName + "ExtrusionHeight(mm)");
            extrusionInfillWidth = Preferences.loadGlobalDouble(prefName + "ExtrusionInfillWidth(mm)");
            lowerFineLayers = 2; //Preferences.loadGlobalInt(prefName + "LowerFineLayers(0...)");
            upperFineLayers = 2; //Preferences.loadGlobalInt(prefName + "UpperFineLayers(0...)");
            extrusionBroadWidth = Preferences.loadGlobalDouble(prefName + "ExtrusionBroadWidth(mm)");
            coolingPeriod = Preferences.loadGlobalDouble(prefName + "CoolingPeriod(s)");
            fastXYFeedrate = Preferences.loadGlobalDouble(prefName + "FastXYFeedrate(mm/minute)");
            fastEFeedrate = Preferences.loadGlobalDouble(prefName + "FastEFeedrate(mm/minute)");
            slowXYFeedrate = Preferences.loadGlobalDouble(prefName + "SlowXYFeedrate(mm/minute)");
            maxAcceleration = Preferences.loadGlobalDouble(prefName + "MaxAcceleration(mm/minute/minute)");
            middleStart = Preferences.loadGlobalBool(prefName + "MiddleStart");
            t0 = 0; //Preferences.loadGlobalInt(prefName + "t0(0..255)");
            iSpeed = Preferences.loadGlobalDouble(prefName + "InfillSpeed(0..1)");
            oSpeed = Preferences.loadGlobalDouble(prefName + "OutlineSpeed(0..1)");
            asLength = -1; //Preferences.loadGlobalDouble(prefName + "AngleSpeedLength(mm)");
            asFactor = 0.5; //Preferences.loadGlobalDouble(prefName + "AngleSpeedFactor(0..1)");
            material = Preferences.loadGlobalString(prefName + "MaterialType(name)");
            supportMaterial = Preferences.loadGlobalString(prefName + "SupportMaterialType(name)");
            inFillMaterial = Preferences.loadGlobalString(prefName + "InFillMaterialType(name)");
            //			offsetX = Preferences.loadGlobalDouble(prefName + "OffsetX(mm)");
            //			offsetY = Preferences.loadGlobalDouble(prefName + "OffsetY(mm)");
            //			offsetZ = Preferences.loadGlobalDouble(prefName + "OffsetZ(mm)");
            nozzleWipeEnabled = false; //Preferences.loadGlobalBool(prefName + "NozzleWipeEnabled");
            nozzleWipeDatumX = 22.4; //Preferences.loadGlobalDouble(prefName + "NozzleWipeDatumX(mm)");
            nozzleWipeDatumY = 4; //Preferences.loadGlobalDouble(prefName + "NozzleWipeDatumY(mm)");
            nozzleWipeStrokeX = 0; //Preferences.loadGlobalDouble(prefName + "NozzleWipeStrokeX(mm)");
            nozzleWipeStrokeY = 10; //Preferences.loadGlobalDouble(prefName + "NozzleWipeStrokeY(mm)");
            nozzleWipeFreq = 1; //Preferences.loadGlobalInt(prefName + "NozzleWipeFreq");
            nozzleClearTime = 10; //Preferences.loadGlobalDouble(prefName + "NozzleClearTime(s)");
            nozzleWaitTime = 0; //Preferences.loadGlobalDouble(prefName + "NozzleWaitTime(s)");
            randSt = false; //Preferences.loadGlobalBool(prefName + "RandomStart");
            incrementedSt = false; //Preferences.loadGlobalBool(prefName + "IncrementedStart");
            shortLength = -1; //Preferences.loadGlobalDouble(prefName + "ShortLength(mm)");
            shortSpeed = 1; //Preferences.loadGlobalDouble(prefName + "ShortSpeed(0..1)");
            infillOverlap = Preferences.loadGlobalDouble(prefName + "InfillOverlap(mm)");
            extrusionDelayForLayer = Preferences.loadGlobalDouble(prefName + "ExtrusionDelayForLayer(ms)");
            extrusionDelayForPolygon = Preferences.loadGlobalDouble(prefName + "ExtrusionDelayForPolygon(ms)");
            extrusionOverRun = Preferences.loadGlobalDouble(prefName + "ExtrusionOverRun(mm)");
            valveDelayForLayer = Preferences.loadGlobalDouble(prefName + "ValveDelayForLayer(ms)");
            valveDelayForPolygon = Preferences.loadGlobalDouble(prefName + "ValveDelayForPolygon(ms)");
            extrusionReverseDelay = Preferences.loadGlobalDouble(prefName + "Reverse(ms)");
            valveOverRun = Preferences.loadGlobalDouble(prefName + "ValveOverRun(mm)");
            minLiftedZ = -1; //Preferences.loadGlobalDouble(prefName + "MinimumZClearance(mm)");
            // NB - store as 2ms ticks to allow longer pulses
            valvePulseTime = 0.5 * Preferences.loadGlobalDouble(prefName + "ValvePulseTime(ms)");
            shells = Preferences.loadGlobalInt(prefName + "NumberOfShells(0..N)");
            pauseBetweenSegments = false; //Preferences.loadGlobalBool(prefName + "PauseBetweenSegments");
            extrusionFoundationWidth = Preferences.loadGlobalDouble(prefName + "ExtrusionFoundationWidth(mm)");
            extrusionLastFoundationWidth = Preferences.loadGlobalDouble(prefName + "ExtrusionLastFoundationWidth(mm)");
            separationFraction = 0.8; //Preferences.loadGlobalDouble(prefName + "SeparationFraction(0..1)");
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

    /**
     * Wait while the motors move about
     * 
     * @throws IOException
     */
    public void waitTillNotBusy() throws IOException {
        if (printer == null) {
            return;
        }
        printer.waitTillNotBusy();
    }

    public void setPrinter(final GCodePrinter p) {
        printer = p;
    }

    public GCodePrinter getPrinter() {
        return printer;
    }

    public void startExtruding() {
        if (!es.isExtruding()) {
            try {
                setExtrusion(getExtruderSpeed(), false);
            } catch (final Exception e) {
                //hmm.
            }
            es.setExtruding(true);
        }
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

    public void heatOn(final boolean wait) throws Exception {
        setTemperature(extrusionTemp, wait);
    }

    public void heatOff() throws Exception {
        setTemperature(0, false);
    }

    public double getTemperatureTarget() {
        return es.targetTemperature();
    }

    public double getDefaultTemperature() {
        return extrusionTemp;
    }

    /**
     * The the outline speed and the infill speed [0,1]
     */
    public double getInfillSpeedFactor() {
        return iSpeed;
    }

    public double getInfillFeedrate() {
        return getInfillSpeedFactor() * getFastXYFeedrate();
    }

    public double getOutlineSpeedFactor() {
        return oSpeed;
    }

    public double getOutlineFeedrate() {
        return getOutlineSpeedFactor() * getFastXYFeedrate();
    }

    /**
     * The length in mm to speed up when going round corners (non-Javadoc)
     * 
     * @see org.reprap.Extruder#getAngleSpeedUpLength()
     */
    public double getAngleSpeedUpLength() {
        return asLength;
    }

    /**
     * The factor by which to speed up when going round a corner. The formula is
     * speed = baseSpeed*[1 - 0.5*(1 + ca)*getAngleSpeedFactor()] where ca is
     * the cos of the angle between the lines. So it goes fastest when the line
     * doubles back on itself (returning 1), and slowest when it continues
     * straight (returning 1 - getAngleSpeedFactor()). (non-Javadoc)
     * 
     * @see org.reprap.Extruder#getAngleSpeedFactor()
     */
    public double getAngleSpeedFactor() {
        return asFactor;
    }

    public double getAngleFeedrate() {
        return getAngleSpeedFactor() * getFastXYFeedrate();
    }

    public boolean isAvailable() {
        return true;
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

    private double getRegularExtruderSpeed() {
        try {
            return Preferences.loadGlobalDouble(prefName + "ExtrusionSpeed(mm/minute)");
        } catch (final Exception e) {
            Debug.e(e.toString());
            return 200; //Hack
        }
    }

    private double getSeparationSpeed() {
        try {
            return 3000; //Preferences.loadGlobalDouble(prefName + "SeparationSpeed(mm/minute)");
        } catch (final Exception e) {
            Debug.e(e.toString());
            return 200; //Hack
        }
    }

    public void setSeparating(final boolean s) {
        separating = s;
    }

    public double getExtruderSpeed() {
        if (separating) {
            return getSeparationSpeed();
        } else {
            return getRegularExtruderSpeed();
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

    public double getExtrusionBroadWidth() {
        return extrusionBroadWidth;
    }

    public int getLowerFineLayers() {
        return lowerFineLayers;
    }

    public int getUpperFineLayers() {
        return upperFineLayers;
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
     * @return determine whether nozzle wipe method is enabled or not
     */

    public boolean getNozzleWipeEnabled() {
        return nozzleWipeEnabled;
    }

    /**
     * @return the X-cord for the nozzle wiper
     */

    public double getNozzleWipeDatumX() {
        return nozzleWipeDatumX;
    }

    /**
     * @return the Y-cord for the nozzle wiper
     */

    public double getNozzleWipeDatumY() {
        return nozzleWipeDatumY;
    }

    /**
     * @return the length of the nozzle movement over the wiper
     */

    public double getNozzleWipeStrokeX() {
        return nozzleWipeStrokeX;
    }

    /**
     * @return the length of the nozzle movement over the wiper
     */

    public double getNozzleWipeStrokeY() {
        return nozzleWipeStrokeY;
    }

    /**
     * @return the number of times the nozzle moves over the wiper
     */

    public int getNozzleWipeFreq() {
        return nozzleWipeFreq;
    }

    /**
     * @return the time to extrude before wiping the nozzle
     */

    public double getNozzleClearTime() {
        return nozzleClearTime;
    }

    /**
     * @return the time to wait after extruding before wiping the nozzle
     */

    public double getNozzleWaitTime() {
        return nozzleWaitTime;
    }

    /**
     * Start polygons at a random location round their perimiter
     * 
     * @return
     */

    public boolean getRandomStart() {
        return randSt;
    }

    /**
     * Start polygons at an incremented location round their perimiter
     * 
     * @return
     */

    public boolean getIncrementedStart() {
        return incrementedSt;
    }

    /**
     * get short lengths which need to be plotted faster set -ve to turn this
     * off.
     * 
     * @return
     */

    public double getShortLength() {
        return shortLength;
    }

    /**
     * Factor (between 0 and 1) to use to set the speed for short lines.
     * 
     * @return
     */

    public double getShortLineSpeedFactor() {
        return shortSpeed;
    }

    /**
     * Feedrate for short lines in mm/minute
     * 
     * @return
     */

    public double getShortLineFeedrate() {
        return getShortLineSpeedFactor() * getFastXYFeedrate();
    }

    /**
     * Number of mm to overlap the hatching infill with the outline. 0 gives
     * none; -ve will leave a gap between the two
     * 
     * @return
     */

    public double getInfillOverlap() {
        return infillOverlap;
    }

    /**
     * Gets the number of milliseconds to wait before starting a border track
     * 
     * @return
     */

    public double getExtrusionDelayForLayer() {
        return extrusionDelayForLayer;
    }

    /**
     * Gets the number of milliseconds to wait before starting a hatch track
     * 
     * @return
     */

    public double getExtrusionDelayForPolygon() {
        return extrusionDelayForPolygon;
    }

    /**
     * Gets the number of milliseconds to reverse the extrude motor at the end
     * of a track
     * 
     * @return
     */

    public double getExtrusionReverseDelay() {
        return extrusionReverseDelay;
    }

    /**
     * Gets the number of milliseconds to wait before opening the valve for the
     * first track of a layer
     * 
     * @return
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
     * 
     * @return
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
     * 
     * @return
     */

    public double getMinLiftedZ() {
        return minLiftedZ;
    }

    /**
     * The number of times to go round the outline (0 to supress)
     * 
     * @return
     */

    public int getShells() {
        return shells;
    }

    /**
     * Stop the extrude motor between segments?
     * 
     * @return
     */

    public boolean getPauseBetweenSegments() {
        return pauseBetweenSegments;
    }

    /**
     * What stuff are we working with?
     * 
     * @return
     */

    public String getMaterial() {
        return material;
    }

    /**
     * What stuff are we holding up with?
     * 
     * @return
     */

    public String getSupportMaterial() {
        return supportMaterial;
    }

    public int getSupportExtruderNumber() {
        return GCodeExtruder.getNumberFromMaterial(supportMaterial);
    }

    public GCodeExtruder getSupportExtruder() {
        return org.reprap.Main.gui.getPrinter().getExtruder(supportMaterial);
    }

    /**
     * What stuff are we infilling with?
     * 
     * @return
     */
    public String getInfillMaterial() {
        return inFillMaterial;
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

    public double getLastFoundationWidth(final LayerRules lc) {
        return extrusionLastFoundationWidth;
    }

    /**
     * At the support layer before a layer is to be separated, how far up the
     * normal Z movement do we go to make a bigger gap to form a weak join?
     * 
     * @return
     */
    public double getSeparationFraction() {
        return separationFraction;
    }

    /**
     * The arc compensation factor. See
     * org.reprap.geometry.polygons.RrPolygon.arcCompensate(...)
     * 
     * @return
     */

    public double getArcCompensationFactor() {
        return arcCompensationFactor;
    }

    /**
     * The arc short sides. See
     * org.reprap.geometry.polygons.RrPolygon.arcCompensate(...)
     * 
     * @return
     */

    public double getArcShortSides() {
        return arcShortSides;
    }

    /**
     * The direction to hatch even-numbered layers in degrees anticlockwise from
     * the X axis
     * 
     * @return
     */

    public double getEvenHatchDirection() {
        return evenHatchDirection;
    }

    /**
     * The direction to hatch odd-numbered layers in degrees anticlockwise from
     * the X axis
     * 
     * @return
     */

    public double getOddHatchDirection() {
        return oddHatchDirection;
    }

    /**
     * Find out what our current speed is
     * 
     * @return
     */
    public double getCurrentSpeed() {
        return es.speed();
    }

    /**
     * Find out if we are currently in reverse
     * 
     * @return
     */

    public boolean getReversing() {
        return es.reverse();
    }

    /**
     * If we are working with feedstock lengths, compute that from the actual
     * length we want to extrude from the nozzle, otherwise just return the
     * extruded length.
     * 
     * @param distance
     * @return
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
     * 
     * @param time
     *            (ms)
     * @return
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
     * 
     * @param xyDistance
     *            (mm)
     * @param xyFeedrate
     *            (mm/minute)
     * @return
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
     * 
     * @return
     */

    public double getExtrudeRatio() {
        return extrudeRatio;
    }

    /**
     * Set the extrude ratio. Only to be used if you know what you are doing.
     * It's a good idea to set it back when you've finished...
     * 
     * @param er
     */

    public void setExtrudeRatio(final double er) {
        extrudeRatio = er;
    }

    /**
     * Find out if we're working in 5D
     * 
     * @return
     */

    public boolean get5D() {
        return fiveD;
    }

    /**
     * Find out how far we have extruded so far
     * 
     * @return
     */

    public ExtruderState getExtruderState() {
        return es;
    }

    /**
     * Add some extruded length
     * 
     * @param l
     */
    public void addExtrudedLength(final double l) {
        es.add(l);
    }

    /**
     * Each logical extruder has a unique ID
     * 
     * @return
     */

    public int getID() {
        return myExtruderID;
    }

    /**
     * Several logical extruders can share one physical extruder This number is
     * unique to each physical extruder
     * 
     * @return
     */

    public int getPhysicalExtruderNumber() {
        return es.physicalExtruder();
    }

    /**
     * Return the PWM for the motor. -ve values mean this feature is unavailable
     */

    public double getPWM() {
        return extrusionPWM;
    }

    /**
     * Time to purge the extruder -ve values supress
     * 
     * @return
     */

    public double getPurgeTime() {
        return purgeTime;
    }

    /**
     * Purge the extruder
     */

    public void purge(final double liftZ) throws Exception {
        getPrinter().moveToPurge(liftZ);
        try {
            //if(homeZ)
            //	getPrinter().homeToZeroZ();
            //heatOn(true);
            if (getPurgeTime() > 0) {
                setExtrusion(getFastXYFeedrate(), false);
                getPrinter().machineWait(getPurgeTime(), false, true);
                setExtrusion(0, false);
                getPrinter().printEndReverse();
                zeroExtrudedLength(true);
            }
        } catch (final Exception e) {
        }
    }

    /**
     * If this is true, plot outlines from the middle of their infilling hatch
     * to reduce dribble at their starts and ends. If false, plot the outline as
     * the outline.
     * 
     * @return
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
     * 
     * @return
     */

    public int getSurfaceLayers() {
        return surfaceLayers;
    }

    /**
     * Are the extruder's models ones that (may) include single-width vectors to
     * be plotted?
     * 
     * @return
     */

    public boolean getSingleLine() {
        return singleLine;
    }

    /**
     * The diameter of the input filament
     * 
     * @return
     */

    public double getFeedDiameter() {
        return feedDiameter;
    }

    /**
     * Plot perimiters inside out or outside in?
     * 
     * @return
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

    /**
     * What are the dimensions for infill?
     * 
     * @return
     */
    //	public String getBroadInfillMaterial()
    //	{
    //		return inFillMaterial;
    //	}

    public static int getNumberFromMaterial(final String material) {
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