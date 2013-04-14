package org.reprap.geometry;

import java.io.IOException;

import org.reprap.Attributes;
import org.reprap.Preferences;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;
import org.reprap.geometry.polygons.PolygonAttributes;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.machines.GCodePrinter;
import org.reprap.utilities.Debug;
import org.reprap.utilities.RrGraphics;

public class LayerProducer {
    private RrGraphics simulationPlot = null;
    private LayerRules layerConditions = null;
    private final PolygonList allPolygons[];
    private double currentFeedrate;

    /**
     * Set up a normal layer
     */
    public LayerProducer(final PolygonList ap[], final LayerRules lc, final RrGraphics simPlot) throws IOException {
        layerConditions = lc;
        simulationPlot = simPlot;

        allPolygons = ap;

        if (simulationPlot != null) {
            if (!simulationPlot.isInitialised()) {
                final Rectangle rec = lc.getBox();
                if (Preferences.loadGlobalBool("Shield")) {
                    rec.expand(Point2D.add(rec.sw(), new Point2D(-7, -7))); // TODO: Yuk - this should be a parameter
                }
                simulationPlot.init(rec, "" + lc.getModelLayer() + " (z=" + lc.getModelZ() + ")");
            } else {
                simulationPlot.cleanPolygons("" + lc.getModelLayer() + " (z=" + lc.getModelZ() + ")");
            }
        }
    }

    private Point2D posNow() {
        return new Point2D(layerConditions.getPrinter().getX(), layerConditions.getPrinter().getY());
    }

    /**
     * speed up for short lines
     */
    private boolean shortLine(final Point2D p, final boolean stopExtruder, final boolean closeValve) throws Exception {
        final GCodePrinter printer = layerConditions.getPrinter();
        final double shortLen = printer.getExtruder().getShortLength();
        if (shortLen < 0) {
            return false;
        }
        final Point2D a = Point2D.sub(posNow(), p);
        final double amod = a.mod();
        if (amod > shortLen) {
            return false;
        }

        // TODO: FIX THIS
        //		printer.setSpeed(LinePrinter.speedFix(printer.getExtruder().getXYSpeed(), 
        //				printer.getExtruder().getShortSpeed()));
        printer.printTo(p.x(), p.y(), layerConditions.getMachineZ(), printer.getExtruder().getShortLineFeedrate(),
                stopExtruder, closeValve);
        return true;
    }

    /**
     * @param first
     *            First point, the end of the line segment to be plotted to from
     *            the current position.
     * @param second
     *            Second point, the end of the next line segment; used for angle
     *            calculations
     * @param turnOff
     *            True if the extruder should be turned off at the end of this
     *            segment.
     * @throws Exception
     */
    private void plot(final Point2D first, final Point2D second, final boolean stopExtruder, final boolean closeValve)
            throws Exception {
        if (shortLine(first, stopExtruder, closeValve)) {
            return;
        }

        final GCodePrinter printer = layerConditions.getPrinter();
        final double z = layerConditions.getMachineZ();

        final double speedUpLength = printer.getExtruder().getAngleSpeedUpLength();
        if (speedUpLength > 0) {
            final SegmentSpeeds ss = SegmentSpeeds.createSegmentSpeeds(posNow(), first, speedUpLength);
            if (ss == null) {
                return;
            }

            printer.printTo(ss.getP1().x(), ss.getP1().y(), z, currentFeedrate, false, false);

            if (ss.isPlotMiddle()) {
                //TODO: FIX THIS.
                //				int straightSpeed = LinePrinter.speedFix(currentSpeed, (1 - 
                //						printer.getExtruder().getAngleSpeedFactor()));
                //printer.setFeedrate(printer.getExtruder().getAngleFeedrate());
                printer.printTo(ss.getP2().x(), ss.getP2().y(), z, printer.getExtruder().getAngleFeedrate(), false, false);
            }

            printer.printTo(ss.getP3().x(), ss.getP3().y(), z, printer.getExtruder().getAngleFeedrate(), stopExtruder,
                    closeValve);
            // Leave speed set for the start of the next line.
        } else {
            printer.printTo(first.x(), first.y(), z, currentFeedrate, stopExtruder, closeValve);
        }
    }

    private void singleMove(final Point2D p) {
        final GCodePrinter pt = layerConditions.getPrinter();
        pt.singleMove(p.x(), p.y(), pt.getZ(), pt.getFastXYFeedrate(), true);
    }

    private void move(final Point2D first, final boolean startUp, final boolean endUp, final boolean fast) throws Exception {
        final GCodePrinter printer = layerConditions.getPrinter();
        final double z = layerConditions.getMachineZ();
        if (fast) {
            printer.moveTo(first.x(), first.y(), z, printer.getExtruder().getFastXYFeedrate(), startUp, endUp);
            return;
        }

        final double speedUpLength = printer.getExtruder().getAngleSpeedUpLength();
        if (speedUpLength > 0) {
            final SegmentSpeeds ss = SegmentSpeeds.createSegmentSpeeds(posNow(), first, speedUpLength);
            if (ss == null) {
                return;
            }

            printer.moveTo(ss.getP1().x(), ss.getP1().y(), z, printer.getCurrentFeedrate(), startUp, startUp);

            if (ss.isPlotMiddle()) {
                printer.moveTo(ss.getP2().x(), ss.getP2().y(), z, currentFeedrate, startUp, startUp);
            }

            //TODO: FIX ME!
            //printer.setSpeed(ss.speed(currentSpeed, printer.getExtruder().getAngleSpeedFactor()));

            //printer.setFeedrate(printer.getExtruder().getAngleFeedrate());
            printer.moveTo(ss.getP3().x(), ss.getP3().y(), z, printer.getExtruder().getAngleFeedrate(), startUp, endUp);
            // Leave speed set for the start of the next movement.
        } else {
            printer.moveTo(first.x(), first.y(), z, currentFeedrate, startUp, endUp);
        }
    }

    /**
     * Plot a polygon
     */
    private void plot(final Polygon p, final boolean firstOneInLayer) throws Exception {
        final Attributes att = p.getAttributes();
        final PolygonAttributes pAtt = p.getPolygonAttribute();
        final GCodePrinter printer = layerConditions.getPrinter();
        final double outlineFeedrate = att.getExtruder().getOutlineFeedrate();
        final double infillFeedrate = att.getExtruder().getInfillFeedrate();

        final boolean acc = att.getExtruder().getMaxAcceleration() > 0;

        if (p.size() <= 1) {
            return;
        }

        // If the length of the plot is <0.05mm, don't bother with it.
        // This will not spot an attempt to plot 10,000 points in 1mm.
        double plotDist = 0;
        Point2D lastPoint = p.point(0);
        for (int i = 1; i < p.size(); i++) {
            final Point2D n = p.point(i);
            plotDist += Point2D.d(lastPoint, n);
            lastPoint = n;
        }
        if (plotDist < Preferences.machineResolution() * 0.5) {
            Debug.getInstance().debugMessage("Rejected line with " + p.size() + " points, length: " + plotDist);
            //startNearHere = null;
            return;
        }

        final double currentZ = printer.getZ();

        if (firstOneInLayer) {
            // The next line tells the printer that it is already at the first point.  It is not, but code will be added just before this
            // to put it there by the LayerRules function that reverses the top-down order of the layers.
            if (Preferences.loadGlobalBool("RepRapAccelerations")) {
                printer.singleMove(p.point(0).x(), p.point(0).y(), currentZ, printer.getSlowXYFeedrate(), false);
            } else {
                printer.singleMove(p.point(0).x(), p.point(0).y(), currentZ, printer.getFastXYFeedrate(), false);
            }
            printer.forceNextExtruder();
        }
        printer.selectExtruder(att);

        // If getMinLiftedZ() is negative, never lift the head
        final double liftZ = att.getExtruder().getLift();
        final Boolean lift = att.getExtruder().getMinLiftedZ() >= 0 || liftZ > 0;

        if (acc) {
            if (Preferences.loadGlobalBool("RepRapAccelerations")) {
                p.setSpeeds(printer.getFastXYFeedrate(), att.getExtruder().getSlowXYFeedrate(), p.isClosed() ? outlineFeedrate
                        : infillFeedrate, att.getExtruder().getMaxAcceleration());
            } else {
                p.setSpeeds(printer.getFastXYFeedrate(), att.getExtruder().getFastXYFeedrate(), p.isClosed() ? outlineFeedrate
                        : infillFeedrate, att.getExtruder().getMaxAcceleration());
            }
        }

        final double extrudeBackLength = att.getExtruder().getExtrusionOverRun();
        final double valveBackLength = att.getExtruder().getValveOverRun();
        if (extrudeBackLength > 0 && valveBackLength > 0) {
            Debug.getInstance().errorMessage("LayerProducer.plot(): extruder has both valve backoff and extrude backoff specified.");
        }

        p.backStepExtrude(extrudeBackLength);
        p.backStepValve(valveBackLength);

        if (liftZ > 0) {
            printer.singleMove(printer.getX(), printer.getY(), currentZ + liftZ, printer.getFastFeedrateZ(), true);
        }

        currentFeedrate = printer.getFastXYFeedrate();
        singleMove(p.point(0));

        if (liftZ > 0) {
            printer.singleMove(printer.getX(), printer.getY(), currentZ, printer.getFastFeedrateZ(), true);
        }

        if (acc | (!Preferences.loadGlobalBool("RepRapAccelerations"))) {
            currentFeedrate = p.speed(0);
        } else {
            if (p.isClosed()) {
                currentFeedrate = outlineFeedrate;
            } else {
                currentFeedrate = infillFeedrate;
            }
        }

        plot(p.point(0), p.point(1), false, false);

        // Print any lead-in.
        printer.printStartDelay(firstOneInLayer);

        boolean extrudeOff = false;
        boolean valveOff = false;
        boolean oldexoff;

        final double oldFeedFactor = att.getExtruder().getExtrudeRatio();

        if (pAtt != null) {
            att.getExtruder().setExtrudeRatio(oldFeedFactor * pAtt.getBridgeThin());
        }

        for (int i = 1; i < p.size(); i++) {
            final Point2D next = p.point((i + 1) % p.size());
            if (acc) {
                currentFeedrate = p.speed(i);
            }
            oldexoff = extrudeOff;
            extrudeOff = (i > p.extrudeEnd() && extrudeBackLength > 0) || i == p.size() - 1;
            valveOff = (i > p.valveEnd() && valveBackLength > 0) || i == p.size() - 1;
            plot(p.point(i), next, extrudeOff, valveOff);
            if (oldexoff ^ extrudeOff) {
                printer.printEndReverse();
            }
        }

        // Restore sanity
        att.getExtruder().setExtrudeRatio(oldFeedFactor);
        if (p.isClosed()) {
            move(p.point(0), false, false, true);
        }
        move(posNow(), lift, lift, true);

        if (simulationPlot != null) {
            final PolygonList pgl = new PolygonList();
            pgl.add(p);
            simulationPlot.add(pgl);
        }
    }

    /**
     * Master plot function - draw everything. Supress border and/or hatch by
     * setting borderPolygons and/or hatchedPolygons null
     */
    public void plot() throws Exception {
        boolean firstOneInLayer = true;

        for (final PolygonList pl : allPolygons) {
            for (int j = 0; j < pl.size(); j++) {
                plot(pl.polygon(j), firstOneInLayer);
                firstOneInLayer = false;
            }
        }
    }

}
