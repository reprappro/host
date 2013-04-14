/*
 
 RepRap
 ------
 
 The Replicating Rapid Prototyper Project
 
 
 Copyright (C) 2006
 Adrian Bowyer & The University of Bath
 
 http://reprap.org
 
 Principal author:
 
 Adrian Bowyer
 Department of Mechanical Engineering
 Faculty of Engineering and Design
 University of Bath
 Bath BA2 7AY
 U.K.
 
 e-mail: A.Bowyer@bath.ac.uk
 
 RepRap is free; you can redistribute it and/or
 modify it under the terms of the GNU Library General Public
 Licence as published by the Free Software Foundation; either
 version 2 of the Licence, or (at your option) any later version.
 
 RepRap is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Library General private Licence for more details.
 
 For this purpose the words "software" and "library" in the GNU Library
 General private Licence are taken to mean any and all computer programs
 computer files data results documents and other copyright information
 available from the RepRap project.
 
 You should have received a copy of the GNU Library General Public
 Licence along with RepRap; if not, write to the Free
 Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA,
 or see
 
 http://www.gnu.org/
 
 =====================================================================
 
 This program loads STL files of objects, orients them, and builds them
 in the RepRap machine.
 
 It is based on one of the open-source examples in Daniel Selman's excellent
 Java3D book, and his notice is immediately below.
 
 First version 2 April 2006
 This version: 16 April 2006
 
 */

/*******************************************************************************
 * VrmlPickingTest.java Copyright (C) 2001 Daniel Selman
 * 
 * First distributed with the book "Java 3D Programming" by Daniel Selman and
 * published by Manning Publications. http://manning.com/selman
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General private License as published by the Free Software
 * Foundation, version 2.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General private License for more
 * details.
 * 
 * The license can be found on the WWW at: http://www.fsf.org/copyleft/gpl.html
 * 
 * Or by writing to: Free Software Foundation, Inc., 59 Temple Place - Suite
 * 330, Boston, MA 02111-1307, USA.
 * 
 * Authors can be contacted at: Daniel Selman: daniel@selman.org
 * 
 * If you make changes you think others would like, please contact one of the
 * authors or someone at the www.j3d.org web site.
 ******************************************************************************/

package org.reprap.gui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GraphicsConfigTemplate;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;

import javax.media.j3d.AmbientLight;
import javax.media.j3d.Appearance;
import javax.media.j3d.Background;
import javax.media.j3d.BoundingSphere;
import javax.media.j3d.Bounds;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.DirectionalLight;
import javax.media.j3d.GraphicsConfigTemplate3D;
import javax.media.j3d.Group;
import javax.media.j3d.Material;
import javax.media.j3d.Node;
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

import org.reprap.Attributes;
import org.reprap.Preferences;
import org.reprap.RFO;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polyhedra.AllSTLsToBuild;
import org.reprap.geometry.polyhedra.STLObject;

import com.sun.j3d.utils.picking.PickCanvas;
import com.sun.j3d.utils.picking.PickResult;
import com.sun.j3d.utils.picking.PickTool;

/**
 * This is the main private class that creates a virtual world of the RepRap
 * working volume, allows you to put STL-file objects in it, move them about to
 * arrange them, and build them in the machine.
 */
public class RepRapBuild extends JPanel implements MouseListener {
    private static final long serialVersionUID = 1L;
    private MouseObject mouse = null;
    private PickCanvas pickCanvas = null; // The thing picked by a mouse click
    private STLObject lastPicked = null; // The last thing picked
    private final AllSTLsToBuild stls;
    private boolean reordering;
    private String wv_location = null;
    private double mouse_tf = 50;
    private double mouse_zf = 50;
    private double xwv = 300;
    private double ywv = 300;
    private double zwv = 300;
    private double RadiusFactor = 0.7;
    private double BackFactor = 2.0;
    private double FrontFactor = 0.001;
    private double BoundFactor = 3.0;
    private Vector3d wv_offset = new Vector3d(-17.3, -24.85, -2);
    private Color3f bgColour = new Color3f(0.9f, 0.9f, 0.9f);
    private Color3f selectedColour = new Color3f(0.6f, 0.2f, 0.2f);
    private Color3f machineColour = new Color3f(0.7f, 0.7f, 0.7f);
    private Appearance picked_app = null;
    private Appearance wv_app = null;
    private final BranchGroup wv_and_stls = new BranchGroup();
    private STLObject world = null;
    private STLObject workingVolume = null;
    private VirtualUniverse universe = null;
    private BranchGroup sceneBranchGroup = null;
    private Bounds applicationBounds = null;
    private static final Color3f black = new Color3f(0, 0, 0);

    public RepRapBuild() throws IOException {
        initialise();
        stls = new AllSTLsToBuild();
        reordering = false;
        setPreferredSize(new Dimension(600, 400));
    }

    public AllSTLsToBuild getSTLs() {
        return stls;
    }

    private Background createBackground() {
        final Background back = new Background(bgColour);
        back.setApplicationBounds(createApplicationBounds());
        return back;
    }

    private BranchGroup createViewBranchGroup(final TransformGroup[] tgArray, final ViewPlatform vp) {
        final BranchGroup vpBranchGroup = new BranchGroup();

        if (tgArray != null && tgArray.length > 0) {
            Group parentGroup = vpBranchGroup;
            TransformGroup curTg = null;

            for (final TransformGroup element : tgArray) {
                curTg = element;
                parentGroup.addChild(curTg);
                parentGroup = curTg;
            }

            tgArray[tgArray.length - 1].addChild(vp);
        } else {
            vpBranchGroup.addChild(vp);
        }

        return vpBranchGroup;
    }

    private BranchGroup createSceneBranchGroup() {
        final BranchGroup objRoot = new BranchGroup();
        final Bounds lightBounds = getApplicationBounds();
        final AmbientLight ambLight = new AmbientLight(true, new Color3f(1.0f, 1.0f, 1.0f));
        ambLight.setInfluencingBounds(lightBounds);
        objRoot.addChild(ambLight);

        final DirectionalLight headLight = new DirectionalLight();
        headLight.setInfluencingBounds(lightBounds);
        objRoot.addChild(headLight);

        mouse = new MouseObject(getApplicationBounds(), mouse_tf, mouse_zf);

        wv_and_stls.setCapability(Group.ALLOW_CHILDREN_EXTEND);
        wv_and_stls.setCapability(Group.ALLOW_CHILDREN_WRITE);
        wv_and_stls.setCapability(Group.ALLOW_CHILDREN_READ);

        // Load the STL file for the working volume
        world = new STLObject(wv_and_stls);

        final String stlFile = getStlBackground();

        workingVolume = new STLObject();
        workingVolume.addSTL(stlFile, wv_offset, wv_app, null);
        wv_and_stls.addChild(workingVolume.top());

        // Set the mouse to move everything
        mouse.move(world, false);
        objRoot.addChild(world.top());

        return objRoot;
    }

    // Action on mouse click
    @Override
    public void mouseClicked(final MouseEvent e) {
        pickCanvas.setShapeLocation(e);

        final PickResult pickResult = pickCanvas.pickClosest();
        STLObject picked = null;

        if (pickResult != null) {
            final Node actualNode = pickResult.getObject();

            final Attributes att = (Attributes) actualNode.getUserData();
            picked = att.getParent();
            if (picked != null) {
                if (picked != workingVolume) {
                    picked.setAppearance(picked_app); // Highlight it
                    if (lastPicked != null && !reordering) {
                        lastPicked.restoreAppearance(); // lowlight
                    }
                    if (!reordering) {
                        mouse.move(picked, true); // Set the mouse to move it
                    }
                    lastPicked = picked;
                    reorder();
                } else {
                    if (!reordering) {
                        mouseToWorld();
                    }
                }
            }
        }
    }

    public void mouseToWorld() {
        if (lastPicked != null) {
            lastPicked.restoreAppearance();
        }
        mouse.move(world, false);
        lastPicked = null;
    }

    @Override
    public void mouseEntered(final MouseEvent e) {
    }

    @Override
    public void mouseExited(final MouseEvent e) {
    }

    @Override
    public void mousePressed(final MouseEvent e) {
    }

    @Override
    public void mouseReleased(final MouseEvent e) {
    }

    void moreCopies(final STLObject original, final Attributes originalAttributes, final int number) {
        if (number <= 0) {
            return;
        }
        final String fileName = original.fileAndDirectioryItCameFrom(0);
        final double increment = original.extent().x + 5;
        final Vector3d offset = new Vector3d();
        offset.x = increment;
        offset.y = 0;
        offset.z = 0;
        for (int i = 0; i < number; i++) {
            final STLObject stl = new STLObject();
            final Attributes newAtt = stl.addSTL(fileName, null, original.getAppearance(), null);
            if (newAtt != null) {
                newAtt.setMaterial(originalAttributes.getMaterial());
                stl.translate(offset);
                if (stl.numChildren() > 0) {
                    wv_and_stls.addChild(stl.top());
                    stls.add(stl);
                }
            }
            offset.x += increment;
        }
    }

    public void anotherSTLFile(final String s, final boolean centre) {
        if (s == null) {
            return;
        }
        final STLObject stl = new STLObject();
        final Attributes att = stl.addSTL(s, null, Preferences.unselectedApp(), lastPicked);
        if (lastPicked == null && centre) {

            final Point2D middle = Point2D.mul(0.5, new Point2D(200, 200));
            final Vector3d v = new Vector3d(middle.x(), middle.y(), 0);
            final Vector3d e = stl.extent();
            e.z = 0;
            e.x = -0.5 * e.x;
            e.y = -0.5 * e.y;
            v.add(e);
            stl.translate(v);
        }
        if (att != null) {
            // New separate object, or just appended to lastPicked?
            if (stl.numChildren() > 0) {
                wv_and_stls.addChild(stl.top());
                stls.add(stl);
            }

            MaterialRadioButtons.createAndShowGUI(att, this, stls.size() - 1, stl.volume());
        }
    }

    // Callback for when the user has a pre-loaded STL and attribute
    public void anotherSTL(final STLObject stl, final Attributes att, final int index) {
        if (stl == null || att == null) {
            return;
        }

        // New separate object, or just appended to lastPicked?
        if (stl.numChildren() > 0) {
            wv_and_stls.addChild(stl.top());
            stls.add(index, stl);
        }
    }

    public void changeMaterial() {
        if (lastPicked == null) {
            return;
        }
        MaterialRadioButtons.createAndShowGUI(lastPicked.attributes(0), this, lastPicked);
    }

    // Callback for when the user selects an RFO file to load
    public void addRFOFile(final String s) {
        if (s == null) {
            return;
        }
        final AllSTLsToBuild newStls = RFO.load(s);
        for (int i = 0; i < newStls.size(); i++) {
            wv_and_stls.addChild(newStls.get(i).top());
        }
        stls.add(newStls);
    }

    public void saveRFOFile(final String s) throws IOException {
        RFO.save(s, stls);
    }

    public void saveSCADFile(final String s) {
        stls.saveSCAD(s);
    }

    private void addCanvas3D(final Canvas3D c3d) {
        setLayout(new BorderLayout());
        add(c3d, BorderLayout.CENTER);
        doLayout();

        if (sceneBranchGroup != null) {
            c3d.addMouseListener(this);

            pickCanvas = new PickCanvas(c3d, sceneBranchGroup);
            pickCanvas.setMode(PickTool.GEOMETRY_INTERSECT_INFO);
            pickCanvas.setTolerance(4.0f);
        }

        c3d.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }

    // Callbacks for when the user rotates the selected object
    public void xRotate() {
        if (lastPicked != null) {
            lastPicked.xClick();
        }
    }

    public void yRotate() {
        if (lastPicked != null) {
            lastPicked.yClick();
        }
    }

    public void zRotate(final double angle) {
        if (lastPicked != null) {
            lastPicked.zClick(angle);
        }
    }

    // Callback for a request to convert units
    public void inToMM() {
        if (lastPicked != null) {
            lastPicked.inToMM();
        }
    }

    public void doReorder() {
        if (lastPicked != null) {
            lastPicked.restoreAppearance();
            mouseToWorld();
            lastPicked = null;
        }
        reordering = true;
    }

    private void reorder() {
        if (!reordering) {
            return;
        }
        if (stls.reorderAdd(lastPicked)) {
            return;
        }
        for (int i = 0; i < stls.size(); i++) {
            stls.get(i).restoreAppearance();
        }
        lastPicked = null;
        reordering = false;
    }

    public void nextPicked() {
        if (lastPicked == null) {
            lastPicked = stls.get(0);
        } else {
            lastPicked.restoreAppearance();
            lastPicked = stls.getNextOne(lastPicked);
        }
        lastPicked.setAppearance(picked_app);
        mouse.move(lastPicked, true);
    }

    public void deleteSTL() {
        if (lastPicked == null) {
            return;
        }
        int index = -1;
        for (int i = 0; i < stls.size(); i++) {
            if (stls.get(i) == lastPicked) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            stls.remove(index);
            index = wv_and_stls.indexOfChild(lastPicked.top());
            mouseToWorld();
            wv_and_stls.removeChild(index);
            lastPicked = null;
        }
    }

    private void refreshPreferences() throws IOException {
        // Set everything up from the properties file
        // All this needs to go into Preferences.java
        wv_location = Preferences.getBasePath();
        mouse_tf = 50;
        mouse_zf = 50;
        RadiusFactor = 0.7;
        BackFactor = 2.0;
        FrontFactor = 0.001;
        BoundFactor = 3.0;

        xwv = Preferences.loadGlobalDouble("WorkingX(mm)");
        ywv = Preferences.loadGlobalDouble("WorkingY(mm)");
        zwv = Preferences.loadGlobalDouble("WorkingZ(mm)");

        wv_offset = new Vector3d(0, 0, 0);

        bgColour = new Color3f((float) 0.9, (float) 0.9, (float) 0.9);
        selectedColour = new Color3f((float) 0.6, (float) 0.2, (float) 0.2);
        machineColour = new Color3f((float) 0.3, (float) 0.3, (float) 0.3);
    }

    private void initialise() throws IOException {
        refreshPreferences();
        picked_app = new Appearance();
        picked_app.setMaterial(new Material(selectedColour, RepRapBuild.black, selectedColour, RepRapBuild.black, 0f));

        wv_app = new Appearance();
        wv_app.setMaterial(new Material(machineColour, RepRapBuild.black, machineColour, RepRapBuild.black, 0f));

        initJava3d();
    }

    private double getBackClipDistance() {
        return BackFactor * getViewPlatformActivationRadius();
    }

    private double getFrontClipDistance() {
        return FrontFactor * getViewPlatformActivationRadius();
    }

    private Bounds createApplicationBounds() {
        applicationBounds = new BoundingSphere(new Point3d(xwv * 0.5, ywv * 0.5, zwv * 0.5), BoundFactor
                * getViewPlatformActivationRadius());
        return applicationBounds;
    }

    private float getViewPlatformActivationRadius() {
        return (float) (RadiusFactor * Math.sqrt(xwv * xwv + ywv * ywv + zwv * zwv));
    }

    private View createView(final ViewPlatform vp) {
        final View view = new View();

        final PhysicalBody pb = createPhysicalBody();
        final PhysicalEnvironment pe = createPhysicalEnvironment();

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

    private Canvas3D createCanvas3D() {
        final GraphicsConfigTemplate3D gc3D = new GraphicsConfigTemplate3D();
        gc3D.setSceneAntialiasing(GraphicsConfigTemplate.PREFERRED);
        final GraphicsDevice gd[] = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();

        return new Canvas3D(gd[0].getBestConfiguration(gc3D));
    }

    private Bounds getApplicationBounds() {
        if (applicationBounds == null) {
            applicationBounds = createApplicationBounds();
        }

        return applicationBounds;
    }

    private void initJava3d() {
        universe = createVirtualUniverse();

        final javax.media.j3d.Locale locale = createLocale(universe);

        sceneBranchGroup = createSceneBranchGroup();

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

    private PhysicalBody createPhysicalBody() {
        return new PhysicalBody();
    }

    private PhysicalEnvironment createPhysicalEnvironment() {
        return new PhysicalEnvironment();
    }

    private ViewPlatform createViewPlatform() {
        final ViewPlatform vp = new ViewPlatform();
        vp.setViewAttachPolicy(View.RELATIVE_TO_FIELD_OF_VIEW);
        vp.setActivationRadius(getViewPlatformActivationRadius());

        return vp;
    }

    private VirtualUniverse createVirtualUniverse() {
        return new VirtualUniverse();
    }

    private void addViewBranchGroup(final javax.media.j3d.Locale locale, final BranchGroup bg) {
        locale.addBranchGraph(bg);
    }

    private javax.media.j3d.Locale createLocale(final VirtualUniverse u) {
        return new javax.media.j3d.Locale(u);
    }

    private TransformGroup[] getViewTransformGroupArray() {
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

    private String getStlBackground() {
        return wv_location;
    }

}