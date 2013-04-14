package org.reprap.geometry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;

import org.reprap.Preferences;
import org.reprap.devices.GCodeExtruder;
import org.reprap.geometry.polygons.HalfPlane;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.geometry.polyhedra.AllSTLsToBuild;
import org.reprap.machines.GCodePrinter;
import org.reprap.utilities.Debug;

/**
 * This stores a set of facts about the layer currently being made, and the
 * rules for such things as infill patterns, support patterns etc.
 */
public class LayerRules {
    /**
     * The coordinates of the first point plotted in a layer
     */
    private final Point2D[] firstPoint;

    /**
     * The extruder first used in a layer
     */
    private final int[] firstExtruder;

    /**
     * The coordinates of the last point plotted in a layer
     */
    private final Point2D[] lastPoint;

    /**
     * The extruder last used in a layer
     */
    private final int[] lastExtruder;

    /**
     * Record extruder usage in each layer for planning
     */
    private final boolean[][] extruderUsedThisLayer;

    /**
     * The heights of the layers
     */
    private final double[] layerZ;

    /**
     * The names of all the files for all the layers
     */
    private final String[] layerFileNames;

    /**
     * Are we reversing the layer orders?
     */
    private boolean reversing;

    /**
     * Flag to remember if we have reversed the layer order in the output file
     */
    private boolean alreadyReversed;

    /**
     * The machine
     */
    private final GCodePrinter printer;

    /**
     * How far up the model we are in mm
     */
    private double modelZ;

    /**
     * How far we are up from machine Z=0
     */
    private double machineZ;

    /**
     * The count of layers up the model
     */
    private int modelLayer;

    /**
     * The number of layers the machine has done
     */
    private int machineLayer;

    /**
     * The top of the model in model coordinates
     */
    private final double modelZMax;

    /**
     * The highest the machine should go this build
     */
    private final double machineZMax;

    /**
     * The number of the last model layer (first = 0)
     */
    private final int modelLayerMax;

    /**
     * The number of the last machine layer (first = 0)
     */
    private final int machineLayerMax;

    /**
     * Putting down foundations?
     */
    private boolean layingSupport;

    /**
     * The smallest step height of all the extruders
     */
    private double zStep;

    /**
     * The biggest step height of all the extruders
     */
    private double thickestZStep;

    /**
     * If we take a short step, remember it and add it on next time
     */
    private double addToStep = 0;

    /**
     * This is true until it is first read, when it becomes false
     */
    private boolean notStartedYet;

    /**
     * The XY rectangle that bounds the build
     */
    private Rectangle bBox;

    /**
     * The maximum number of surface layers requested by any extruder
     */
    private int maxSurfaceLayers = 2;

    /**
     * The point at which to purge extruders
     */
    private Point2D purge;

    /**
     * The length of the purge trail in mm
     */
    private final double purgeL = 25;

    /**
     * How many physical extruders?
     */
    private int maxAddress = -1;

    public LayerRules(final GCodePrinter p, final AllSTLsToBuild astls, final boolean found) {
        printer = p;
        reversing = false;
        alreadyReversed = false;
        notStartedYet = true;

        astls.setBoxes();
        astls.setLayerRules(this);

        try {
            purge = new Point2D(Preferences.loadGlobalDouble("DumpX(mm)"), Preferences.loadGlobalDouble("DumpY(mm)"));
        } catch (final IOException e) {
            Debug.getInstance().errorMessage(e.toString());
        }

        Rectangle gp = astls.ObjectPlanRectangle();
        bBox = new Rectangle(new Point2D(gp.x().low() - 6, gp.y().low() - 6), new Point2D(gp.x().high() + 6, gp.y().high() + 6));

        modelZMax = astls.maxZ();

        // Run through the extruders checking their layer heights and the
        // Actual physical extruder used.

        layingSupport = found;
        final GCodeExtruder[] es = printer.getExtruders();
        zStep = es[0].getExtrusionHeight();
        thickestZStep = zStep;
        int fineLayers = es[0].getLowerFineLayers();

        if (es.length > 1) {
            for (int i = 1; i < es.length; i++) {
                if (es[i].getLowerFineLayers() > fineLayers) {
                    fineLayers = es[i].getLowerFineLayers();
                }
                if (es[i].getExtrusionHeight() > thickestZStep) {
                    thickestZStep = es[i].getExtrusionHeight();
                }
                if (es[i].getExtrusionHeight() < zStep) {
                    zStep = es[i].getExtrusionHeight();
                }
                if (es[i].getSurfaceLayers() > maxSurfaceLayers) {
                    maxSurfaceLayers = es[i].getSurfaceLayers();
                }
                if (es[i].getPhysicalExtruderNumber() > maxAddress) {
                    maxAddress = es[i].getPhysicalExtruderNumber();
                }
            }
        }

        final long thick = Math.round(thickestZStep * 1000.0);
        for (int i = 0; i < es.length; i++) {
            final long thin = Math.round(es[i].getExtrusionHeight() * 1000.0);
            if (thick % thin != 0) {
                Debug.getInstance().errorMessage("LayerRules(): the layer height for extruder " + i + "(" + es[i].getLowerFineLayers()
                + ") is not an integer divisor of the layer height for layer height " + thickestZStep);
            }
        }

        final int foundationLayers = Math.max(0, printer.getFoundationLayers());
        modelLayerMax = (int) (modelZMax / zStep) + 1;
        machineLayerMax = modelLayerMax + foundationLayers;
        machineZMax = modelZMax + foundationLayers * zStep;
        modelZ = modelZMax;
        machineZ = machineZMax;
        modelLayer = modelLayerMax;
        machineLayer = machineLayerMax;
        addToStep = 0;

        // Set up the records of the layers for later reversing (top->down ==>> bottom->up)

        firstPoint = new Point2D[machineLayerMax + 1];
        firstExtruder = new int[machineLayerMax + 1];
        lastPoint = new Point2D[machineLayerMax + 1];
        lastExtruder = new int[machineLayerMax + 1];
        layerZ = new double[machineLayerMax + 1];
        layerFileNames = new String[machineLayerMax + 1];
        extruderUsedThisLayer = new boolean[machineLayerMax + 1][maxAddress];
        for (int i = 0; i < machineLayerMax + 1; i++) {
            layerFileNames[i] = null;
            for (int j = 0; j < maxAddress; j++) {
                extruderUsedThisLayer[i][j] = false;
            }
        }
        astls.setUpShield();
        astls.setBoxes();
        gp = astls.ObjectPlanRectangle();
        bBox = new Rectangle(new Point2D(gp.x().low() - 6, gp.y().low() - 6), new Point2D(gp.x().high() + 6, gp.y().high() + 6));
    }

    public boolean purgeXOriented() {
        final Point2D middle = Point2D.mul(0.5, printer.getBedNorthEast());
        return Math.abs(middle.y() - purge.y()) > Math.abs(middle.x() - purge.x());
    }

    public Point2D getPurgeEnd(final boolean low, final int pass) {
        double a = purgeL * 0.5;
        if (low) {
            a = -a;
        }
        final double b = 4 * printer.getExtruder().getExtrusionSize()
                - (printer.getExtruder().getPhysicalExtruderNumber() * 3 + pass) * printer.getExtruder().getExtrusionSize();
        if (purgeXOriented()) {
            return Point2D.add(purge, new Point2D(a, b));
        } else {
            return Point2D.add(purge, new Point2D(b, a));
        }
    }

    public Point2D getPurgeMiddle() {
        return purge;
    }

    public Rectangle getBox() {
        return new Rectangle(bBox); // Something horrible happens to return by reference here; hence copy...
    }

    public GCodePrinter getPrinter() {
        return printer;
    }

    public double getModelZ() {
        return modelZ;
    }

    public boolean getReversing() {
        return reversing;
    }

    public double getModelZ(final int layer) {
        return zStep * layer;
    }

    public double getMachineZ() {
        return machineZ;
    }

    public int getModelLayer() {
        return modelLayer;
    }

    /**
     * MIGHT an extruder be used in this layer. I.e. is this layer the correct
     * multiple of the microlayering heights for this extruder possibly to be
     * required.
     */
    public boolean extruderLiveThisLayer(final int e) {
        final GCodeExtruder[] es = printer.getExtruders();
        final double myHeight = es[e].getExtrusionHeight();
        final double eFraction = machineZ / myHeight;
        double delta = eFraction - Math.floor(eFraction);
        if (delta > 0.5) {
            delta = Math.ceil(eFraction) - eFraction;
        }
        delta = myHeight * delta;
        return (delta < zStep * 0.5);
    }

    public int sliceCacheSize() {
        return (int) Math.ceil(2 * (maxSurfaceLayers * 2 + 1) * thickestZStep / zStep);
    }

    public void setFirstAndLast(final PolygonList[] pl) {
        firstPoint[machineLayer] = null;
        lastPoint[machineLayer] = null;
        firstExtruder[machineLayer] = -1;
        lastExtruder[machineLayer] = -1;
        layerZ[machineLayer] = machineZ;
        if (pl == null) {
            return;
        }
        if (pl.length <= 0) {
            return;
        }
        int bottom = -1;
        int top = -1;
        for (int i = 0; i < pl.length; i++) {
            if (pl[i] != null) {
                if (pl[i].size() > 0) {
                    if (bottom < 0) {
                        bottom = i;
                    }
                    top = i;
                }
            }
        }
        if (bottom < 0) {
            return;
        }
        firstPoint[machineLayer] = pl[bottom].polygon(0).point(0);
        firstExtruder[machineLayer] = pl[bottom].polygon(0).getAttributes().getExtruder().getID();
        lastPoint[machineLayer] = pl[top].polygon(pl[top].size() - 1).point(pl[top].polygon(pl[top].size() - 1).size() - 1);
        lastExtruder[machineLayer] = pl[top].polygon(pl[top].size() - 1).getAttributes().getExtruder().getID();
    }

    public int realTopLayer() {
        int rtl = machineLayerMax;
        while (firstPoint[rtl] == null && rtl > 0) {
            final String s = "LayerRules.realTopLayer(): layer " + rtl + " from " + machineLayerMax + " is empty!";
            if (machineLayerMax - rtl > 1) {
                Debug.getInstance().errorMessage(s);
            } else {
                Debug.getInstance().debugMessage(s);
            }
            rtl--;
        }
        return rtl;
    }

    private String getLayerFileName(final int layer) {
        return layerFileNames[layer];
    }

    public String getLayerFileName() {
        return layerFileNames[machineLayer];
    }

    public void setLayerFileName(final String s) {
        layerFileNames[machineLayer] = s;
    }

    private Point2D getFirstPoint(final int layer) {
        return firstPoint[layer];
    }

    public Point2D getLastPoint(final int layer) {
        return lastPoint[layer];
    }

    public double getLayerZ(final int layer) {
        return layerZ[layer];
    }

    public int getMachineLayerMax() {
        return machineLayerMax;
    }

    public int getMachineLayer() {
        return machineLayer;
    }

    public int getFoundationLayers() {
        return machineLayerMax - modelLayerMax;
    }

    public double getMachineZMAx() {
        return machineZMax;
    }

    public double getZStep() {
        return zStep;
    }

    public boolean notStartedYet() {
        if (notStartedYet) {
            notStartedYet = false;
            return true;
        }
        return false;
    }

    public void setLayingSupport(final boolean lf) {
        layingSupport = lf;
    }

    public boolean getLayingSupport() {
        return layingSupport;
    }

    /**
     * The hatch pattern is:
     * 
     * Foundation: X and Y rectangle
     * 
     * Model: Alternate even then odd (which can be set to the same angle if
     * wanted).
     */
    public HalfPlane getHatchDirection(final GCodeExtruder e, final boolean support) {
        final double myHeight = e.getExtrusionHeight();
        final double eFraction = machineZ / myHeight;
        final int mylayer = (int) Math.round(eFraction);

        double angle;

        if (getMachineLayer() < getFoundationLayers()) {
            angle = e.getOddHatchDirection();
        } else {
            if (mylayer % 2 == 0) {
                angle = e.getEvenHatchDirection();
            } else {
                angle = e.getOddHatchDirection();
            }
        }
        angle = angle * Math.PI / 180;
        HalfPlane result = new HalfPlane(new Point2D(0.0, 0.0), new Point2D(Math.sin(angle), Math.cos(angle)));

        if (((mylayer / 2) % 2 == 0) && !support) {
            result = result.offset(0.5 * getHatchWidth(e));
        }

        return result;
    }

    /**
     * The gap in the layer zig-zag is:
     * 
     * Foundation: The foundation width for all but... ...the penultimate
     * foundation layer, which is half that and.. ...the last foundation layer,
     * which is the model fill width
     * 
     * Model: The model fill width
     */
    public double getHatchWidth(final GCodeExtruder e) {
        if (getMachineLayer() < getFoundationLayers()) {
            return e.getExtrusionFoundationWidth();
        }

        return e.getExtrusionInfillWidth();
    }

    /**
     * Move the machine up/down, but leave the model's layer where it is.
     */
    public void stepMachine() {
        machineLayer--;
        machineZ = zStep * machineLayer + addToStep;
    }

    public void moveZAtStartOfLayer(final boolean really) {
        final double z = getMachineZ();
        printer.setZ(z - (zStep + addToStep));
        printer.singleMove(printer.getX(), printer.getY(), z, printer.getFastFeedrateZ(), really);
    }

    /**
     * Move both the model and the machine up/down a layer
     */
    public void step() {
        modelLayer--;
        modelZ = modelLayer * zStep + addToStep;
        addToStep = 0;
        stepMachine();
    }

    public void setFractionDone() {
        org.reprap.gui.botConsole.SlicerFrame.getBotConsoleFrame().setFractionDone(-1, -1, -1);
    }

    private void copyFile(final PrintStream ps, final String ip) {
        File f = null;
        try {
            f = new File(ip);
            final FileReader fr = new FileReader(f);
            int character;
            while ((character = fr.read()) >= 0) {
                ps.print((char) character);
            }
            ps.flush();
            fr.close();
        } catch (final Exception e) {
            Debug.getInstance().errorMessage("Error copying file: " + e.toString());
            e.printStackTrace();
        }
    }

    public void reverseLayers() {
        // Stop this being called twice...
        if (alreadyReversed) {
            Debug.getInstance().debugMessage("LayerRules.reverseLayers(): called twice.");
            return;
        }
        alreadyReversed = true;
        reversing = true;

        final String fileName = getPrinter().getOutputFilename();

        PrintStream fileOutStream = null;
        try {
            final FileOutputStream fileStream = new FileOutputStream(fileName);
            fileOutStream = new PrintStream(fileStream);
        } catch (final Exception e) {
            Debug.getInstance().errorMessage("Can't write to file " + fileName);
            return;
        }

        getPrinter().forceOutputFile(fileOutStream);

        try {
            getPrinter().startRun(this); // Sets current X, Y, Z to 0 and optionally plots an outline
            final int top = realTopLayer();

            for (machineLayer = 1; machineLayer <= top; machineLayer++) {
                machineZ = layerZ[machineLayer];
                getPrinter().startingLayer(this);

                getPrinter().singleMove(getFirstPoint(machineLayer).x(), getFirstPoint(machineLayer).y(), machineZ,
                        getPrinter().getFastXYFeedrate(), true);
                copyFile(fileOutStream, getLayerFileName(machineLayer));

                if (Preferences.loadGlobalBool("RepRapAccelerations")) {
                    getPrinter().singleMove(getLastPoint(machineLayer).x(), getLastPoint(machineLayer).y(), machineZ,
                            getPrinter().getSlowXYFeedrate(), false);
                } else {
                    getPrinter().singleMove(getLastPoint(machineLayer).x(), getLastPoint(machineLayer).y(), machineZ,
                            getPrinter().getFastXYFeedrate(), false);
                }
                getPrinter().finishedLayer(this);
            }
            getPrinter().terminate(this);
        } catch (final Exception e) {
            e.printStackTrace();
        }
        fileOutStream.close();
        reversing = false;
    }

}
