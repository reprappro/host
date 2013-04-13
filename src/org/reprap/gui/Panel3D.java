package org.reprap.gui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.GraphicsConfigTemplate;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.media.j3d.Appearance;
import javax.media.j3d.AudioDevice;
import javax.media.j3d.Background;
import javax.media.j3d.BoundingSphere;
import javax.media.j3d.Bounds;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.GraphicsConfigTemplate3D;
import javax.media.j3d.Material;
import javax.media.j3d.PhysicalBody;
import javax.media.j3d.PhysicalEnvironment;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.View;
import javax.media.j3d.ViewPlatform;
import javax.media.j3d.VirtualUniverse;
import javax.swing.JPanel;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.reprap.Preferences;
import org.reprap.geometry.polyhedra.STLObject;

import com.sun.j3d.audioengines.javasound.JavaSoundMixer;

abstract public class Panel3D extends JPanel {
    private static final long serialVersionUID = 1L;

    // What follows are defaults.  These values should be overwritten from
    // the reprap.properties file.
    protected String wv_location = null;

    // Translate and zoom scaling factors
    protected double mouse_tf = 50;
    protected double mouse_zf = 50;

    protected double xwv = 300; // The RepRap machine...
    protected double ywv = 300; // ...working volume in mm.
    protected double zwv = 300;

    // Factors for front and back clipping planes and so on
    protected double RadiusFactor = 0.7;
    protected double BackFactor = 2.0;
    protected double FrontFactor = 0.001;
    protected double BoundFactor = 3.0;

    protected String worldName = "RepRap World";
    protected Vector3d wv_offset = new Vector3d(-17.3, -24.85, -2);

    // The background, and other colours
    protected Color3f bgColour = new Color3f(0.9f, 0.9f, 0.9f);
    protected Color3f selectedColour = new Color3f(0.6f, 0.2f, 0.2f);
    protected Color3f machineColour = new Color3f(0.7f, 0.7f, 0.7f);
    protected Color3f unselectedColour = new Color3f(0.3f, 0.3f, 0.3f);

    protected static final Color3f black = new Color3f(0, 0, 0);
    protected Appearance picked_app = null; // Colour for the selected part
    protected Appearance wv_app = null; // Colour for the working volume
    protected BranchGroup wv_and_stls = new BranchGroup(); // Where in the scene

    protected STLObject world = null;
    protected STLObject workingVolume = null;

    // The world in the Applet
    protected VirtualUniverse universe = null;
    protected BranchGroup sceneBranchGroup = null;
    protected Bounds applicationBounds = null;

    // Set up the RepRap working volume
    abstract protected BranchGroup createSceneBranchGroup();

    // Set bg light grey
    abstract protected Background createBackground();

    abstract protected BranchGroup createViewBranchGroup(TransformGroup[] tgArray, ViewPlatform vp);

    public void refreshPreferences() throws IOException {
        // Set everything up from the properties file
        // All this needs to go into Preferences.java
        wv_location = Preferences.getBasePath();
        // Translate and zoom scaling factors

        mouse_tf = 50;
        mouse_zf = 50;

        RadiusFactor = 0.7;
        BackFactor = 2.0;
        FrontFactor = 0.001;
        BoundFactor = 3.0;

        xwv = Preferences.loadGlobalDouble("WorkingX(mm)");
        ywv = Preferences.loadGlobalDouble("WorkingY(mm)");
        zwv = Preferences.loadGlobalDouble("WorkingZ(mm)");

        worldName = "RepRap-World";
        wv_offset = new Vector3d(0, 0, 0);

        bgColour = new Color3f((float) 0.9, (float) 0.9, (float) 0.9);
        selectedColour = new Color3f((float) 0.6, (float) 0.2, (float) 0.2);
        machineColour = new Color3f((float) 0.3, (float) 0.3, (float) 0.3);
        unselectedColour = new Color3f((float) 0.3, (float) 0.3, (float) 0.3);
    }

    protected void initialise() throws IOException {
        refreshPreferences();
        picked_app = new Appearance();
        picked_app.setMaterial(new Material(selectedColour, black, selectedColour, black, 0f));

        wv_app = new Appearance();
        wv_app.setMaterial(new Material(machineColour, black, machineColour, black, 0f));

        initJava3d();
    }

    protected double getBackClipDistance() {
        return BackFactor * getViewPlatformActivationRadius();
    }

    protected double getFrontClipDistance() {
        return FrontFactor * getViewPlatformActivationRadius();
    }

    protected Bounds createApplicationBounds() {
        applicationBounds = new BoundingSphere(new Point3d(xwv * 0.5, ywv * 0.5, zwv * 0.5), BoundFactor
                * getViewPlatformActivationRadius());
        return applicationBounds;
    }

    protected float getViewPlatformActivationRadius() {
        return (float) (RadiusFactor * Math.sqrt(xwv * xwv + ywv * ywv + zwv * zwv));
    }

    public Color3f getObjectColour() {
        return unselectedColour;
    }

    public static URL getWorkingDirectory() {
        final File file = new File(System.getProperty("user.dir"));
        try {
            return file.toURI().toURL();
        } catch (final MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public VirtualUniverse getVirtualUniverse() {
        return universe;
    }

    protected View createView(final ViewPlatform vp) {
        final View view = new View();

        final PhysicalBody pb = createPhysicalBody();
        final PhysicalEnvironment pe = createPhysicalEnvironment();

        final AudioDevice audioDevice = createAudioDevice(pe);

        if (audioDevice != null) {
            pe.setAudioDevice(audioDevice);
            audioDevice.initialize();
        }

        view.setPhysicalEnvironment(pe);
        view.setPhysicalBody(pb);

        if (vp != null) {
            view.attachViewPlatform(vp);
        }

        view.setBackClipDistance(getBackClipDistance());
        view.setFrontClipDistance(getFrontClipDistance());

        final Canvas3D c3d = createCanvas3D();
        view.addCanvas3D(c3d);
        addCanvas3D(c3d);

        return view;
    }

    protected Canvas3D createCanvas3D() {
        final GraphicsConfigTemplate3D gc3D = new GraphicsConfigTemplate3D();
        gc3D.setSceneAntialiasing(GraphicsConfigTemplate.PREFERRED);
        final GraphicsDevice gd[] = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();

        return new Canvas3D(gd[0].getBestConfiguration(gc3D));
    }

    public javax.media.j3d.Locale getFirstLocale() {
        final java.util.Enumeration<?> en = universe.getAllLocales();

        if (en.hasMoreElements() != false) {
            return (javax.media.j3d.Locale) en.nextElement();
        }

        return null;
    }

    protected Bounds getApplicationBounds() {
        if (applicationBounds == null) {
            applicationBounds = createApplicationBounds();
        }

        return applicationBounds;
    }

    public void initJava3d() {
        universe = createVirtualUniverse();

        final javax.media.j3d.Locale locale = createLocale(universe);

        final BranchGroup sceneBranchGroup = createSceneBranchGroup();

        final ViewPlatform vp = createViewPlatform();
        final BranchGroup viewBranchGroup = createViewBranchGroup(getViewTransformGroupArray(), vp);

        createView(vp);

        final Background background = createBackground();

        if (background != null) {
            sceneBranchGroup.addChild(background);
        }

        locale.addBranchGraph(sceneBranchGroup);
        addViewBranchGroup(locale, viewBranchGroup);
    }

    protected PhysicalBody createPhysicalBody() {
        return new PhysicalBody();
    }

    protected AudioDevice createAudioDevice(final PhysicalEnvironment pe) {
        return new JavaSoundMixer(pe);
    }

    protected PhysicalEnvironment createPhysicalEnvironment() {
        return new PhysicalEnvironment();
    }

    protected ViewPlatform createViewPlatform() {
        final ViewPlatform vp = new ViewPlatform();
        vp.setViewAttachPolicy(View.RELATIVE_TO_FIELD_OF_VIEW);
        vp.setActivationRadius(getViewPlatformActivationRadius());

        return vp;
    }

    protected int getCanvas3dWidth(final Canvas3D c3d) {
        return getWidth();
    }

    protected int getCanvas3dHeight(final Canvas3D c3d) {
        return getHeight();
    }

    protected VirtualUniverse createVirtualUniverse() {
        return new VirtualUniverse();
    }

    protected void addViewBranchGroup(final javax.media.j3d.Locale locale, final BranchGroup bg) {
        locale.addBranchGraph(bg);
    }

    protected javax.media.j3d.Locale createLocale(final VirtualUniverse u) {
        return new javax.media.j3d.Locale(u);
    }

    public TransformGroup[] getViewTransformGroupArray() {
        final TransformGroup[] tgArray = new TransformGroup[1];
        tgArray[0] = new TransformGroup();

        final Transform3D viewTrans = new Transform3D();
        final Transform3D eyeTrans = new Transform3D();

        final BoundingSphere sceneBounds = (BoundingSphere) sceneBranchGroup.getBounds();

        // point the view at the center of the object
        final Point3d center = new Point3d();
        sceneBounds.getCenter(center);
        final double radius = sceneBounds.getRadius();
        final Vector3d temp = new Vector3d(center);
        viewTrans.set(temp);

        // pull the eye back far enough to see the whole object
        final double eyeDist = radius / Math.tan(Math.toRadians(40) / 2.0);
        temp.x = 0.0;
        temp.y = 0.0;
        temp.z = eyeDist;
        eyeTrans.set(temp);
        viewTrans.mul(eyeTrans);

        tgArray[0].setTransform(viewTrans);

        return tgArray;
    }

    protected void addCanvas3D(final Canvas3D c3d) {
        setLayout(new BorderLayout());
        add(c3d, BorderLayout.CENTER);
        doLayout();
        c3d.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }

    protected double getScale() {
        return 1.0;
    }

    protected String getStlBackground() {
        return wv_location;
    }

}