package org.reprap.machines;

/*
 * TODO: fixup warmup segments GCode (forgets to turn on extruder) 
 * TODO: fixup all the RR: println commands 
 * TODO: find a better place for the code. You cannot even detect a layer change without hacking now. 
 * TODO: read Zach's GCode examples to check if I messed up. 
 * TODO: make GCodeWriter a subclass of NullCartesian, so I don't have to fix code all over the place.
 */

import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JOptionPane;

import org.reprap.Attributes;
import org.reprap.Preferences;
import org.reprap.comms.GCodeWriter;
import org.reprap.devices.GCodeExtruder;
import org.reprap.geometry.LayerRules;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.utilities.Debug;

public class GCodePrinter {

    private final GCodeWriter gcode;
    private LayerRules layerRules = null;
    /**
     * Force an extruder to be selected on startup
     */
    private boolean forceSelection;
    private boolean XYEAtZero;
    /**
     * Have we actually used this extruder?
     */
    protected boolean physicalExtruderUsed[];
    protected JCheckBoxMenuItem layerPauseCheckbox = null;
    protected JCheckBoxMenuItem segmentPauseCheckbox = null;
    /**
     * Current X, Y and Z position of the extruder
     */
    private double currentX;
    /**
     * Current X, Y and Z position of the extruder
     */
    private double currentY;
    /**
     * Current X, Y and Z position of the extruder
     */
    private double currentZ;
    /**
     * Maximum feedrate for Z axis
     */
    private double maxFeedrateZ;
    /**
     * Current feedrate for the machine.
     */
    private double currentFeedrate;
    /**
     * Feedrate for fast XY moves on the machine.
     */
    private double fastXYFeedrate;
    /**
     * The fastest the machine can accelerate in X and Y
     */
    private double maxXYAcceleration;
    /**
     * The speed from which the machine can do a standing start
     */
    private double slowXYFeedrate;
    /**
     * The fastest the machine can accelerate in Z
     */
    private double maxZAcceleration;
    /**
     * The speed from which the machine can do a standing start in Z
     */
    private double slowZFeedrate;
    /**
     * Feedrate for fast Z moves on the machine.
     */
    private double fastFeedrateZ;
    /**
     * Array containing the extruders on the 3D printer
     */
    private GCodeExtruder extruders[];
    /**
     * Current extruder?
     */
    private int extruder;
    private long startedCooling = -1L;
    private int foundationLayers = 0;
    /**
     * The maximum X and Y point we can move to
     */
    private Point2D bedNorthEast;

    public GCodePrinter() throws IOException {
        XYEAtZero = false;
        forceSelection = true;

        //load extruder prefs
        final int extruderCount = Preferences.loadGlobalInt("NumberOfExtruders");
        if (extruderCount < 1) {
            throw new IllegalStateException("A Reprap printer must contain at least one extruder.");
        }

        //load our actual extruders.
        extruders = new GCodeExtruder[extruderCount];
        loadExtruders();

        //load our prefs
        refreshPreferences();

        //init our stuff.
        currentX = 0;
        currentY = 0;
        currentZ = 0;
        currentFeedrate = 0;

        gcode = new GCodeWriter();
        String s = "M110";
        if (Debug.getInstance().isDebug()) {
            s += " ; Reset the line numbers";
        }
        gcode.queue(s);
        loadExtruders();

        forceSelection = true;
    }

    public void loadExtruders() {
        try {
            final int extruderCount = Preferences.loadGlobalInt("NumberOfExtruders");
            extruders = new GCodeExtruder[extruderCount];
        } catch (final Exception e) {
            Debug.getInstance().errorMessage(e.toString());
        }

        int pe;
        int physExCount = -1;

        for (int i = 0; i < extruders.length; i++) {
            extruders[i] = new GCodeExtruder(gcode, i, this);

            // Make sure all instances of each physical extruder share the same
            // ExtrudedLength instance
            pe = extruders[i].getPhysicalExtruderNumber();
            if (pe > physExCount) {
                physExCount = pe;
            }
            for (int j = 0; j < i; j++) {
                if (extruders[j].getPhysicalExtruderNumber() == pe) {
                    extruders[i].setExtrudeState(extruders[j].getExtruderState());
                    break;
                }
            }

            extruders[i].setPrinter(this);
        }
        physicalExtruderUsed = new boolean[physExCount + 1];
        for (int i = 0; i <= physExCount; i++) {
            physicalExtruderUsed[i] = false;
        }
        extruder = 0;
    }

    private void qFeedrate(final double feedrate) throws Exception {
        if (currentFeedrate == feedrate) {
            return;
        }
        String s = "G1 F" + feedrate;
        if (Debug.getInstance().isDebug()) {
            s += " ; feed for start of next move";
        }
        gcode.queue(s);
        currentFeedrate = feedrate;
    }

    private void qXYMove(final double x, final double y, double feedrate) throws Exception {
        final double dx = x - currentX;
        final double dy = y - currentY;

        double extrudeLength = extruders[extruder].getDistance(Math.sqrt(dx * dx + dy * dy));
        String se = "";

        if (extrudeLength > 0) {
            if (extruders[extruder].getReversing()) {
                extrudeLength = -extrudeLength;
            }
            extruders[extruder].getExtruderState().add(extrudeLength);
            if (extruders[extruder].get5D()) {
                if (Preferences.loadGlobalBool("ExtrusionRelative")) {
                    se = " E" + round(extrudeLength, 3);
                } else {
                    se = " E" + round(extruders[extruder].getExtruderState().length(), 3);
                }
            }
        }

        final double xyFeedrate = round(extruders[extruder].getFastXYFeedrate(), 1);

        if (xyFeedrate < feedrate && Math.abs(extrudeLength) > Preferences.tiny()) {
            Debug.getInstance().debugMessage("GCodeRepRap().qXYMove: extruding feedrate (" + feedrate + ") exceeds maximum (" + xyFeedrate + ").");
            feedrate = xyFeedrate;
        }

        if (getExtruder().getMaxAcceleration() <= 0) {
            qFeedrate(feedrate);
        }

        if (dx == 0.0 && dy == 0.0) {
            if (currentFeedrate != feedrate) {
                qFeedrate(feedrate);
            }
            return;
        }

        String s = "G1 ";

        if (dx != 0) {
            s += "X" + x;
        }
        if (dy != 0) {
            s += " Y" + y;
        }

        s += se;

        if (currentFeedrate != feedrate) {
            s += " F" + feedrate;
            currentFeedrate = feedrate;
        }

        if (Debug.getInstance().isDebug()) {
            s += " ;horizontal move";
        }
        gcode.queue(s);
        currentX = x;
        currentY = y;
    }

    private void qZMove(final double z, double feedrate) throws Exception {
        // note we set the feedrate whether we move or not

        final double zFeedrate = round(getMaxFeedrateZ(), 1);

        if (zFeedrate < feedrate) {
            Debug.getInstance().debugMessage("GCodeRepRap().qZMove: feedrate (" + feedrate + ") exceeds maximum (" + zFeedrate + ").");
            feedrate = zFeedrate;
        }

        if (getMaxZAcceleration() <= 0) {
            qFeedrate(feedrate);
        }

        final double dz = z - currentZ;

        if (dz == 0.0) {
            return;
        }

        double extrudeLength;
        String s = "G1 Z" + z;
        extrudeLength = extruders[extruder].getDistance(dz);

        if (extrudeLength > 0) {
            if (extruders[extruder].getReversing()) {
                extrudeLength = -extrudeLength;
            }
            extruders[extruder].getExtruderState().add(extrudeLength);
            if (extruders[extruder].get5D()) {
                if (Preferences.loadGlobalBool("ExtrusionRelative")) {
                    s += " E" + round(extrudeLength, 3);
                } else {
                    s += " E" + round(extruders[extruder].getExtruderState().length(), 3);
                }
            }
        }

        if (currentFeedrate != feedrate) {
            s += " F" + feedrate;
            currentFeedrate = feedrate;
        }

        if (Debug.getInstance().isDebug()) {
            s += " ;z move";
        }
        gcode.queue(s);
        currentZ = z;
    }

    public void moveTo(double x, double y, double z, double feedrate, final boolean startUp, final boolean endUp)
            throws Exception {
        try {
            if (x > Preferences.loadGlobalDouble("WorkingX(mm)") || x < 0) {
                Debug.getInstance().errorMessage("Attempt to move x to " + x + " which is outside [0, " + Preferences.loadGlobalDouble("WorkingX(mm)")
                + "]");
                x = Math.max(0, Math.min(x, Preferences.loadGlobalDouble("WorkingX(mm)")));
            }
            if (y > Preferences.loadGlobalDouble("WorkingY(mm)") || y < 0) {
                Debug.getInstance().errorMessage("Attempt to move y to " + y + " which is outside [0, " + Preferences.loadGlobalDouble("WorkingY(mm)")
                + "]");
                y = Math.max(0, Math.min(y, Preferences.loadGlobalDouble("WorkingY(mm)")));
            }
            if (z > Preferences.loadGlobalDouble("WorkingZ(mm)") || z < 0) {
                Debug.getInstance().debugMessage("Attempt to move z to " + z + " which is outside [0, " + Preferences.loadGlobalDouble("WorkingZ(mm)")
                + "]");
                z = Math.max(0, Math.min(z, Preferences.loadGlobalDouble("WorkingZ(mm)")));
            }
        } catch (final Exception e) {
        }

        x = round(x, 2);
        y = round(y, 2);
        z = round(z, 4);
        feedrate = round(feedrate, 1);

        final double dx = x - currentX;
        final double dy = y - currentY;
        final double dz = z - currentZ;

        if (dx == 0.0 && dy == 0.0 && dz == 0.0) {
            return;
        }

        // This should either be a Z move or an XY move, but not all three
        final boolean zMove = dz != 0;
        final boolean xyMove = dx != 0 || dy != 0;

        if (zMove && xyMove) {
            Debug.getInstance().debugMessage("GcodeRepRap.moveTo(): attempt to move in X|Y and Z simultaneously: (x, y, z) = (" + currentX + "->" + x
            + ", " + currentY + "->" + y + ", " + currentZ + "->" + z + ", " + ")");
        }

        final double zFeedrate = round(getMaxFeedrateZ(), 1);

        final double liftIncrement = extruders[extruder].getLift(); //extruders[extruder].getExtrusionHeight()/2;
        final double liftedZ = round(currentZ + liftIncrement, 4);

        //go up first?
        if (startUp) {
            qZMove(liftedZ, zFeedrate);
            qFeedrate(feedrate);
        }

        if (dz > 0) {
            if (zMove) {
                qZMove(z, feedrate);
            }
            if (xyMove) {
                qXYMove(x, y, feedrate);
            }
        } else {
            if (xyMove) {
                qXYMove(x, y, feedrate);
            }
            if (zMove) {
                qZMove(z, feedrate);
            }
        }

        if (endUp && !startUp) {
            qZMove(liftedZ, zFeedrate);
            qFeedrate(feedrate);
        }

        if (!endUp && startUp) {
            qZMove(liftedZ - liftIncrement, zFeedrate);
            qFeedrate(feedrate);
        }

        checkCoordinates(x, y, z);

        currentX = x;
        currentY = y;
        currentZ = z;
        XYEAtZero = false;
    }

    private void checkCoordinates(final double x, final double y, final double z) {
        try {
            if (x > Preferences.loadGlobalDouble("WorkingX(mm)") || x < 0) {
                Debug.getInstance().errorMessage("Attempt to move x to " + x + " which is outside [0, " + Preferences.loadGlobalDouble("WorkingX(mm)")
                + "]");
            }
            if (y > Preferences.loadGlobalDouble("WorkingY(mm)") || y < 0) {
                Debug.getInstance().errorMessage("Attempt to move y to " + y + " which is outside [0, " + Preferences.loadGlobalDouble("WorkingY(mm)")
                + "]");
            }
            if (z > Preferences.loadGlobalDouble("WorkingZ(mm)") || z < 0) {
                Debug.getInstance().errorMessage("Attempt to move z to " + z + " which is outside [0, " + Preferences.loadGlobalDouble("WorkingZ(mm)")
                + "]");
            }
        } catch (final Exception e) {
        }
    }

    /**
     * make a single, usually non-building move (between plots, or zeroing an
     * axis etc.)
     */
    public void singleMove(double x, double y, double z, final double feedrate, final boolean really) {
        final double x0 = getX();
        final double y0 = getY();
        final double z0 = getZ();
        x = round(x, 2);
        y = round(y, 2);
        z = round(z, 4);
        final double dx = x - x0;
        final double dy = y - y0;
        final double dz = z - z0;

        final boolean zMove = dz != 0;
        final boolean xyMove = dx != 0 || dy != 0;

        if (zMove && xyMove) {
            Debug.getInstance().debugMessage("GcodeRepRap.singleMove(): attempt to move in X|Y and Z simultaneously: (x, y, z) = (" + x + ", " + y
            + ", " + z + ")");
        }

        if (!really) {
            currentX = x;
            currentY = y;
            currentZ = z;
            currentFeedrate = feedrate;
            return;
        }

        try {
            if (!Preferences.loadGlobalBool("RepRapAccelerations")) {
                moveTo(x, y, z, feedrate, false, false);
                return;
            }
        } catch (final Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        try {
            if (xyMove && getExtruder().getMaxAcceleration() <= 0) {
                moveTo(x, y, z, feedrate, false, false);
                return;
            }

            if (xyMove) {
                final double s = Math.sqrt(dx * dx + dy * dy);

                final VelocityProfile vp = new VelocityProfile(s, getExtruder().getSlowXYFeedrate(), feedrate, getExtruder()
                        .getSlowXYFeedrate(), getExtruder().getMaxAcceleration());
                switch (vp.flat()) {
                case 0:
                    qFeedrate(feedrate);
                    moveTo(x, y, z0, feedrate, false, false);
                    break;

                case 1:
                    qFeedrate(getExtruder().getSlowXYFeedrate());
                    moveTo(x0 + dx * vp.s1() / s, y0 + dy * vp.s1() / s, z0, vp.v(), false, false);
                    moveTo(x, y, z0, getExtruder().getSlowXYFeedrate(), false, false);
                    break;

                case 2:
                    qFeedrate(getExtruder().getSlowXYFeedrate());
                    moveTo(x0 + dx * vp.s1() / s, y0 + dy * vp.s1() / s, z0, feedrate, false, false);
                    moveTo(x0 + dx * vp.s2() / s, y0 + dy * vp.s2() / s, z0, feedrate, false, false);
                    moveTo(x, y, z0, getExtruder().getSlowXYFeedrate(), false, false);
                    break;

                default:
                    Debug.getInstance().errorMessage("GCodeRepRap.singleMove(): dud VelocityProfile XY flat value.");
                }
            }

            if (zMove) {
                final VelocityProfile vp = new VelocityProfile(Math.abs(dz), getSlowZFeedrate(), feedrate, getSlowZFeedrate(),
                        getMaxZAcceleration());
                double s = 1;
                if (dz < 0) {
                    s = -1;
                }
                switch (vp.flat()) {
                case 0:
                    qFeedrate(feedrate);
                    moveTo(x0, y0, z, feedrate, false, false);
                    break;

                case 1:
                    qFeedrate(getSlowZFeedrate());
                    moveTo(x0, y0, z0 + s * vp.s1(), vp.v(), false, false);
                    moveTo(x0, y0, z, getSlowZFeedrate(), false, false);
                    break;

                case 2:
                    qFeedrate(getSlowZFeedrate());
                    moveTo(x0, y0, z0 + s * vp.s1(), feedrate, false, false);
                    moveTo(x0, y0, z0 + s * vp.s2(), feedrate, false, false);
                    moveTo(x0, y0, z, getSlowZFeedrate(), false, false);
                    break;

                default:
                    Debug.getInstance().errorMessage("GCodeRepRap.singleMove(): dud VelocityProfile Z flat value.");
                }
            }
        } catch (final Exception e) {
            Debug.getInstance().errorMessage(e.toString());
        }
    }

    public void printTo(final double x, final double y, final double z, final double feedrate, final boolean stopExtruder,
            final boolean closeValve) throws Exception {
        moveTo(x, y, z, feedrate, false, false);

        if (stopExtruder) {
            getExtruder().stopExtruding();
        }
        if (closeValve) {
            getExtruder().setValve(false);
        }
    }

    public void startRun(final LayerRules lc) throws Exception {
        gcode.queue("; GCode generated by RepRap Java Host Software");
        final Date myDate = new Date();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd:HH-mm-ss");
        final String myDateString = sdf.format(myDate);
        gcode.queue("; Created: " + myDateString);
        gcode.queue(";#!RECTANGLE: " + lc.getBox() + ", height: " + lc.getMachineZMAx());
        if (Debug.getInstance().isDebug()) {
            gcode.queue("; Prologue:");
        }
        gcode.copyFile(Preferences.getProloguePath());
        if (Debug.getInstance().isDebug()) {
            gcode.queue("; ------");
        }
        currentX = 0;
        currentY = 0;
        currentZ = 0;
        currentFeedrate = -100; // Force it to set the feedrate at the start

        forceSelection = true; // Force it to set the extruder to use at the start

        try {
            if (Preferences.loadGlobalBool("StartRectangle")) {
                // plot the outline
                plotOutlines(lc);
            }
        } catch (final Exception E) {
            Debug.getInstance().debugMessage("Initialization error: " + E.toString());
        }
    }

    /**
     * Plot rectangles round the build on layer 0 or above
     * 
     * @param lc
     */
    private void plotOutlines(final LayerRules lc) {
        boolean zRight = false;
        try {
            Rectangle r = lc.getBox();

            for (int e = extruders.length - 1; e >= 0; e--) // Count down so we end with the one most likely to start the rest
            {
                final int pe = extruders[e].getPhysicalExtruderNumber();
                if (physicalExtruderUsed[pe]) {
                    if (!zRight) {
                        singleMove(currentX, currentY, getExtruder().getExtrusionHeight(), getFastFeedrateZ(), true);
                        currentZ = lc.getMachineZ();
                    }
                    zRight = true;
                    selectExtruder(e, true, false);
                    singleMove(r.x().low(), r.y().low(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    printStartDelay(true);
                    getExtruder().setExtrusion(getExtruder().getExtruderSpeed(), false);
                    singleMove(r.x().high(), r.y().low(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    singleMove(r.x().high(), r.y().high(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    singleMove(r.x().low(), r.y().high(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    singleMove(r.x().low(), r.y().low(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    currentX = r.x().low();
                    currentY = r.y().low();
                    printEndReverse();
                    getExtruder().stopExtruding();
                    getExtruder().setValve(false);
                    r = r.offset(2 * getExtruder().getExtrusionSize());
                    physicalExtruderUsed[pe] = false; // Stop us doing it again
                }
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void startingLayer(final LayerRules lc) throws Exception {
        currentFeedrate = -1; // Force it to set the feedrate
        gcode.startingLayer(lc);
        if (lc.getReversing()) {
            gcode.queue(";#!LAYER: " + (lc.getMachineLayer()) + "/" + (lc.getMachineLayerMax() - 1));
        }
        lc.setFractionDone();

        // Don't home the first layer
        // The startup procedure has already done that

        if (lc.getMachineLayer() > 0 && Preferences.loadGlobalBool("InterLayerCooling")) {
            double liftZ = -1;
            for (final GCodeExtruder extruder2 : extruders) {
                if (extruder2.getLift() > liftZ) {
                    liftZ = extruder2.getLift();
                }
            }
            if (liftZ > 0) {
                singleMove(getX(), getY(), currentZ + liftZ, getFastFeedrateZ(), lc.getReversing());
            }
            homeToZeroXYE(lc.getReversing());
            if (liftZ > 0) {
                singleMove(getX(), getY(), currentZ, getFastFeedrateZ(), lc.getReversing());
            }
        } else {
            getExtruder().zeroExtrudedLength(lc.getReversing());
        }

        if (layerPauseCheckbox != null && layerPauseCheckbox.isSelected()) {
            layerPause();
        }

        final double coolDuration = getExtruder().getCoolingPeriod() * 1000L;
        if (startedCooling >= 0) {
            final long fanHasBeenOnDuration = System.currentTimeMillis() - startedCooling;
            machineWait((long) coolDuration - fanHasBeenOnDuration, false, lc.getReversing());
        }
        if (coolDuration > 0) {
            // Fan off
            getExtruder().setCooler(false, lc.getReversing());
        }

        lc.moveZAtStartOfLayer(lc.getReversing());
    }

    public void finishedLayer(final LayerRules lc) throws Exception {
        final double coolTime = getExtruder().getCoolingPeriod();
        startedCooling = -1;

        if (coolTime > 0 && !lc.notStartedYet()) {
            getExtruder().setCooler(true, lc.getReversing());
            Debug.getInstance().debugMessage("Start of cooling period");
            // Go home. Seek (0,0) then callibrate X first
            homeToZeroXYE(lc.getReversing());
            startedCooling = System.currentTimeMillis();
        }
        gcode.finishedLayer(lc);
    }

    public void terminate(final LayerRules lc) throws Exception {
        final int topLayer = lc.realTopLayer();
        final Point2D p = lc.getLastPoint(topLayer);
        currentX = round(p.x(), 2);
        currentY = round(p.y(), 2);
        currentZ = round(lc.getLayerZ(topLayer), 1);

        if (Debug.getInstance().isDebug()) {
            gcode.queue("; Epilogue:");
        }
        gcode.copyFile(Preferences.getEpiloguePath());
        if (Debug.getInstance().isDebug()) {
            gcode.queue("; ------");
        }
    }

    private void delay(final long millis, final boolean fastExtrude, final boolean really) throws Exception {
        double extrudeLength = getExtruder().getDistanceFromTime(millis);

        String s;

        if (extrudeLength > 0) {
            if (extruders[extruder].get5D()) {
                double fr;
                if (fastExtrude) {
                    fr = getExtruder().getFastEFeedrate();
                } else {
                    fr = getExtruder().getFastXYFeedrate();
                }

                currentFeedrate = fr;
                // Fix the value for possible feedrate change
                extrudeLength = getExtruder().getDistanceFromTime(millis);

                if (getExtruder().getFeedDiameter() > 0) {
                    fr = fr * getExtruder().getExtrusionHeight() * getExtruder().getExtrusionSize()
                            / (getExtruder().getFeedDiameter() * getExtruder().getFeedDiameter() * Math.PI / 4);
                }

                fr = round(fr, 1);

                if (really) {
                    currentFeedrate = 0; // force it to output feedrate
                    qFeedrate(fr);
                }
            }

            if (extruders[extruder].getReversing()) {
                extrudeLength = -extrudeLength;
            }

            extruders[extruder].getExtruderState().add(extrudeLength);

            if (extruders[extruder].get5D()) {
                if (Preferences.loadGlobalBool("ExtrusionRelative")) {
                    s = "G1 E" + round(extrudeLength, 3);
                } else {
                    s = "G1 E" + round(extruders[extruder].getExtruderState().length(), 3);
                }
                if (Debug.getInstance().isDebug()) {
                    if (extruders[extruder].getReversing()) {
                        s += " ; extruder retraction";
                    } else {
                        s += " ; extruder dwell";
                    }
                }
                double fr;
                if (Preferences.loadGlobalBool("RepRapAccelerations")) {
                    fr = getExtruder().getSlowXYFeedrate();
                } else {
                    fr = getExtruder().getFastXYFeedrate();
                }
                if (really) {
                    gcode.queue(s);
                    qFeedrate(fr);
                } else {
                    currentFeedrate = fr;
                }
                return;
            }
        }

        s = "G4 P" + millis;
        if (Debug.getInstance().isDebug()) {
            s += " ; delay";
        }
        gcode.queue(s);
    }

    public void homeToZeroX() throws Exception {
        String s = "G28 X0";
        if (Debug.getInstance().isDebug()) {
            s += " ; set x 0";
        }
        gcode.queue(s);
        currentX = 0.0;
    }

    public void homeToZeroY() throws Exception {
        String s = "G28 Y0";
        if (Debug.getInstance().isDebug()) {
            s += " ; set y 0";
        }
        gcode.queue(s);
        currentY = 0.0;
    }

    public void homeToZeroXYE(final boolean really) throws Exception {
        if (XYEAtZero) {
            return;
        }
        if (really) {
            homeToZeroX();
            homeToZeroY();
        } else {
            currentX = 0;
            currentY = 0;
        }
        final int extruderNow = extruder;
        for (int i = 0; i < extruders.length; i++) {
            selectExtruder(i, really, false);
            extruders[i].zeroExtrudedLength(really);
        }
        selectExtruder(extruderNow, really, false);
        XYEAtZero = true;
    }

    public static double round(final double c, final double d) {
        final double power = Math.pow(10.0, d);

        return Math.round(c * power) / power;
    }

    public void stopMotor() throws Exception {
        getExtruder().stopExtruding();
    }

    public void stopValve() throws Exception {
        getExtruder().setValve(false);
    }

    /**
     * All machine dwells and delays are routed via this function, rather than
     * calling Thread.sleep - this allows them to generate the right G codes
     * (G4) etc.
     * 
     * The RS232/USB etc comms system doesn't use this - it sets its own delays.
     * 
     * @param milliseconds
     * @throws Exception
     */
    public void machineWait(final double milliseconds, final boolean fastExtrude, final boolean really) throws Exception {
        if (milliseconds <= 0) {
            return;
        }
        delay((long) milliseconds, fastExtrude, really);
    }

    public String setGCodeFileForOutput(final String fileRoot) {
        return gcode.setGCodeFileForOutput(true, fileRoot);
    }

    /**
     * Tell the printer class it's Z position. Only to be used if you know what
     * you're doing...
     */
    public void setZ(final double z) {
        currentZ = round(z, 4);
    }

    public void selectExtruder(final int materialIndex, final boolean really, final boolean update) throws Exception {
        final int oldPhysicalExtruder = getExtruder().getPhysicalExtruderNumber();
        final GCodeExtruder oldExtruder = getExtruder();
        final int newPhysicalExtruder = extruders[materialIndex].getPhysicalExtruderNumber();
        final boolean shield = Preferences.loadGlobalBool("Shield");
        Point2D purge;

        if (newPhysicalExtruder != oldPhysicalExtruder || forceSelection) {
            if (really) {
                oldExtruder.stopExtruding();

                if (!false) {
                    if (materialIndex < 0 || materialIndex >= extruders.length) {
                        Debug.getInstance().errorMessage("Selected material (" + materialIndex + ") is out of range.");
                        extruder = 0;
                    } else {
                        extruder = materialIndex;
                    }
                }

                if (update) {
                    physicalExtruderUsed[newPhysicalExtruder] = true;
                }
                getExtruder().stopExtruding(); // Make sure we are off

                if (shield) {
                    purge = layerRules.getPurgeEnd(true, 0);
                    singleMove(purge.x(), purge.y(), currentZ, getFastXYFeedrate(), true);
                    currentX = purge.x();
                    currentY = purge.y();
                }

                // Now tell the GCodes to select the new extruder and stabilise all temperatures
                String s = "T" + newPhysicalExtruder;
                if (Debug.getInstance().isDebug()) {
                    s += " ; select new extruder";
                }
                gcode.queue(s);

                if (shield) {
                    // Plot the purge pattern with the new extruder
                    printStartDelay(true);
                    getExtruder().setExtrusion(getExtruder().getExtruderSpeed(), false);

                    purge = layerRules.getPurgeEnd(false, 0);
                    singleMove(purge.x(), purge.y(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    currentX = purge.x();
                    currentY = purge.y();

                    purge = layerRules.getPurgeEnd(false, 1);
                    singleMove(purge.x(), purge.y(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    currentX = purge.x();
                    currentY = purge.y();

                    purge = layerRules.getPurgeEnd(true, 1);
                    singleMove(purge.x(), purge.y(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    currentX = purge.x();
                    currentY = purge.y();

                    purge = layerRules.getPurgeEnd(true, 2);
                    singleMove(purge.x(), purge.y(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    currentX = purge.x();
                    currentY = purge.y();

                    purge = layerRules.getPurgeEnd(false, 2);
                    singleMove(purge.x(), purge.y(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    currentX = purge.x();
                    currentY = purge.y();

                    printEndReverse();
                    getExtruder().stopExtruding();
                    getExtruder().setValve(false);
                }
            }
            forceSelection = false;
        }
    }

    /**
     * Force the output stream to be some value - use with caution
     */
    public void forceOutputFile(final PrintStream fos) {
        gcode.forceOutputFile(fos);
    }

    /**
     * Return the name if the gcode file
     */
    public String getOutputFilename() {
        return gcode.getOutputFilename();
    }

    public void setLayerRules(final LayerRules l) {
        layerRules = l;
    }

    public void refreshPreferences() {
        try {
            final double xNE = Preferences.loadGlobalDouble("WorkingX(mm)");
            final double yNE = Preferences.loadGlobalDouble("WorkingY(mm)");
            bedNorthEast = new Point2D(xNE, yNE);

            // Load our maximum feedrate variables
            final double maxFeedrateX = Preferences.loadGlobalDouble("MaximumFeedrateX(mm/minute)");
            final double maxFeedrateY = Preferences.loadGlobalDouble("MaximumFeedrateY(mm/minute)");
            maxFeedrateZ = Preferences.loadGlobalDouble("MaximumFeedrateZ(mm/minute)");

            maxXYAcceleration = Preferences.loadGlobalDouble("MaxXYAcceleration(mm/mininute/minute)");
            slowXYFeedrate = Preferences.loadGlobalDouble("SlowXYFeedrate(mm/minute)");

            maxZAcceleration = Preferences.loadGlobalDouble("MaxZAcceleration(mm/mininute/minute)");
            slowZFeedrate = Preferences.loadGlobalDouble("SlowZFeedrate(mm/minute)");

            //set our standard feedrates.
            fastXYFeedrate = Math.min(maxFeedrateX, maxFeedrateY);
            setFastFeedrateZ(maxFeedrateZ);

            foundationLayers = Preferences.loadGlobalInt("FoundationLayers");

            final int extruderCount = Preferences.loadGlobalInt("NumberOfExtruders");
            if (extruderCount < 1) {
                throw new Exception("A Reprap printer must contain at least one extruder.");
            }

            extruders = new GCodeExtruder[extruderCount];
            loadExtruders();
        } catch (final Exception ex) {
            Debug.getInstance().errorMessage("Refresh Reprap preferences: " + ex.toString());
        }

        for (final GCodeExtruder extruder2 : extruders) {
            extruder2.refreshPreferences();
        }
    }

    public void selectExtruder(final Attributes att) throws Exception {
        for (int i = 0; i < extruders.length; i++) {
            if (att.getMaterial().equals(extruders[i].getMaterial())) {
                selectExtruder(i, true, true);
                return;
            }
        }
        Debug.getInstance().errorMessage("selectExtruder() - extruder not found for: " + att.getMaterial());
    }

    public double getX() {
        return currentX;
    }

    public double getY() {
        return currentY;
    }

    public double getZ() {
        return currentZ;
    }

    public GCodeExtruder getExtruder(final String name) {
        for (final GCodeExtruder extruder2 : extruders) {
            if (name.equals(extruder2.toString())) {
                return extruder2;
            }
        }
        return null;
    }

    public GCodeExtruder getExtruder() {
        return extruders[extruder];
    }

    public GCodeExtruder[] getExtruders() {
        return extruders;
    }

    /**
     * Extrude for the given time in milliseconds, so that polymer is flowing
     * before we try to move the extruder. But first take up the slack from any
     * previous reverse.
     */
    public void printStartDelay(final boolean firstOneInLayer) throws Exception {
        final double rDelay = getExtruder().getExtruderState().retraction();

        if (rDelay > 0) {
            getExtruder().setMotor(true);
            machineWait(rDelay, true, true);
            getExtruder().getExtruderState().setRetraction(0);
        }

        // Extrude motor and valve delays (ms)
        double eDelay, vDelay;

        if (firstOneInLayer) {
            eDelay = getExtruder().getExtrusionDelayForLayer();
            vDelay = getExtruder().getValveDelayForLayer();
        } else {
            eDelay = getExtruder().getExtrusionDelayForPolygon();
            vDelay = getExtruder().getValveDelayForPolygon();
        }

        if (eDelay >= vDelay) {
            getExtruder().setMotor(true);
            machineWait(eDelay - vDelay, false, true);
            getExtruder().setValve(true);
            machineWait(vDelay, false, true);
        } else {
            getExtruder().setValve(true);
            machineWait(vDelay - eDelay, false, true);
            getExtruder().setMotor(true);
            machineWait(eDelay, false, true);
        }
    }

    /**
     * Extrude backwards for the given time in milliseconds, so that polymer is
     * stopped flowing at the end of a track. Return the amount reversed.
     */
    public double printEndReverse() throws Exception {
        final double delay = getExtruder().getExtrusionReverseDelay();

        if (delay <= 0) {
            return 0;
        }

        getExtruder().setExtrusion(getExtruder().getExtruderSpeed(), true);
        machineWait(delay, true, true);
        getExtruder().stopExtruding();
        getExtruder().getExtruderState().setRetraction(getExtruder().getExtruderState().retraction() + delay);
        return getExtruder().getExtruderState().retraction();
    }

    private void setFastFeedrateZ(final double feedrate) {
        fastFeedrateZ = feedrate;
    }

    public double getFastFeedrateZ() {
        return fastFeedrateZ;
    }

    private double getMaxFeedrateZ() {
        return maxFeedrateZ;
    }

    /**
     * Display a message indicating a segment is about to be printed and wait
     * for the user to acknowledge
     */
    protected void segmentPause() {
        try {
            getExtruder().setValve(false);
            getExtruder().setMotor(false);
        } catch (final Exception ex) {
        }
        JOptionPane.showMessageDialog(null, "A new segment is about to be produced");
        try {
            getExtruder().setExtrusion(getExtruder().getExtruderSpeed(), false);
            getExtruder().setValve(false);
        } catch (final Exception ex) {
        }
    }

    /**
     * Display a message indicating a layer is about to be printed and wait for
     * the user to acknowledge
     */
    private void layerPause() {
        JOptionPane.showMessageDialog(null, "A new layer is about to be produced");
    }

    /**
     * Set the source checkbox used to determine if there should be a pause
     * between layers.
     * 
     * @param layerPause
     *            The source checkbox used to determine if there should be a
     *            pause. This is a checkbox rather than a boolean so it can be
     *            changed on the fly.
     */
    public void setLayerPause(final JCheckBoxMenuItem layerPause) {
        layerPauseCheckbox = layerPause;
    }

    public int getFoundationLayers() {
        return foundationLayers;
    }

    /**
     * Set all the extruders' separating mode
     */
    public void setSeparating(final boolean s) {
        for (final GCodeExtruder extruder2 : extruders) {
            extruder2.setSeparating(s);
        }
    }

    /**
     * Get the feedrate currently being used
     */
    public double getCurrentFeedrate() {
        return currentFeedrate;
    }

    /**
     * @return fast XY movement feedrate in mm/minute
     */
    public double getFastXYFeedrate() {
        return fastXYFeedrate;
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
    public double getMaxXYAcceleration() {
        return maxXYAcceleration;
    }

    private double getSlowZFeedrate() {
        return slowZFeedrate;
    }

    private double getMaxZAcceleration() {
        return maxZAcceleration;
    }

    public void forceNextExtruder() {
        forceSelection = true;
    }

    /**
     * Return the current layer rules
     */
    public LayerRules getLayerRules() {
        return layerRules;
    }

    /**
     * The XY point furthest from the origin
     */
    public Point2D getBedNorthEast() {
        return bedNorthEast;
    }
}
