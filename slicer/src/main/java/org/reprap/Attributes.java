package org.reprap;

import javax.media.j3d.Appearance;
import javax.media.j3d.BranchGroup;

import org.reprap.devices.GCodeExtruder;
import org.reprap.geometry.polyhedra.STLObject;
import org.reprap.machines.GCodePrinter;
import org.reprap.utilities.Debug;

/**
 * Holds RepRap attributes that are attached to Java3D shapes as user data,
 * primarily to record the material that things are made from.
 * 
 * @author adrian
 */
public class Attributes {
    /**
     * The name of the material
     */
    private String material;

    /**
     * The STLObject of which this is a part
     */
    private final STLObject parent;

    /**
     * Where this is in the STLObject of which it is a part
     */
    private BranchGroup part;

    /**
     * The appearance (colour) in the loading and simulation windows
     */
    private Appearance app;

    /**
     * The extruder corresponsing to this material. This is lazily evaluated
     * (I.e. it is not set until there are some extruders around to use).
     */
    private GCodeExtruder extruder;

    /**
     * Constructor - it is permissible to set any argument null. If you know
     * what you're doing of course...
     * 
     * @param s
     *            The name of the material
     * @param p
     *            Parent STLObject
     * @param b
     *            Where in p
     * @param a
     *            what it looks like
     */
    public Attributes(final String s, final STLObject p, final BranchGroup b, final Appearance a) {
        material = s;
        parent = p;
        part = b;
        app = a;
        extruder = null;
    }

    @Override
    public String toString() {
        return "Attributes: material is " + material;
    }

    /**
     * @return the name of the material
     */
    public String getMaterial() {
        return material;
    }

    /**
     * @return the parent object
     */
    public STLObject getParent() {
        return parent;
    }

    /**
     * @return the bit of the STLObject that this is
     */
    public BranchGroup getPart() {
        return part;
    }

    /**
     * @return what colour am I?
     */
    public Appearance getAppearance() {
        return app;
    }

    /**
     * Find my extruder in the list (if not known) or just return it (if known).
     * 
     * @param es
     *            The extruders currently in the printer.
     * @return my extruder
     */
    public GCodeExtruder getExtruder() {
        if (extruder == null) {
            final GCodePrinter p = org.reprap.Main.gui.getPrinter();
            if (p == null) {
                Debug.getInstance().errorMessage("Attributes.getExtruder(): null printer!");
                return null;
            }
            extruder = p.getExtruder(material);
            if (extruder == null) {
                Debug.getInstance().errorMessage("Attributes.getExtruder(): null extruder for " + material);
                return null;
            }
        }
        return extruder;
    }

    /**
     * Change the material name
     */
    public void setMaterial(final String s) {
        material = s;
        extruder = null;
        app = GCodeExtruder.getAppearanceFromMaterial(material);
        if (parent != null) {
            parent.restoreAppearance();
        }
    }

    /**
     * To be used in conjunction with changing the parent
     */
    public void setPart(final BranchGroup b) {
        part = b;
    }
}
