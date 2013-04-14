package org.reprap.geometry.polyhedra;

import javax.vecmath.AxisAngle4d;
import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;

class Primitives {
    /**
     * This should really be called cuboid, but let's not be pedantic...
     */
    static CSG3D cube(double x, double y, double z, final boolean centre) {
        CSG3D result;
        if (centre) {
            x = 0.5 * x;
            y = 0.5 * y;
            z = 0.5 * z;
            result = new CSG3D(new HalfSpace(new Point3D(1, 0, 0), new Point3D(x, 0, 0)));
            result = CSG3D.intersection(result, new CSG3D(new HalfSpace(new Point3D(0, 1, 0), new Point3D(0, y, 0))));
            result = CSG3D.intersection(result, new CSG3D(new HalfSpace(new Point3D(0, 0, 1), new Point3D(0, 0, z))));
            result = CSG3D.intersection(result, new CSG3D(new HalfSpace(new Point3D(-1, 0, 0), new Point3D(-x, 0, 0))));
            result = CSG3D.intersection(result, new CSG3D(new HalfSpace(new Point3D(0, -1, 0), new Point3D(0, -y, 0))));
            result = CSG3D.intersection(result, new CSG3D(new HalfSpace(new Point3D(0, 0, -1), new Point3D(0, 0, -z))));
        } else {
            result = new CSG3D(new HalfSpace(new Point3D(1, 0, 0), new Point3D(x, 0, 0)));
            result = CSG3D.intersection(result, new CSG3D(new HalfSpace(new Point3D(0, 1, 0), new Point3D(0, y, 0))));
            result = CSG3D.intersection(result, new CSG3D(new HalfSpace(new Point3D(0, 0, 1), new Point3D(0, 0, z))));
            result = CSG3D.intersection(result, new CSG3D(new HalfSpace(new Point3D(-1, 0, 0), new Point3D(0, 0, 0))));
            result = CSG3D.intersection(result, new CSG3D(new HalfSpace(new Point3D(0, -1, 0), new Point3D(0, 0, 0))));
            result = CSG3D.intersection(result, new CSG3D(new HalfSpace(new Point3D(0, 0, -1), new Point3D(0, 0, 0))));
        }
        return result;
    }

    /**
     * See http://en.wikibooks.org/wiki/OpenSCAD_User_Manual/
     * Other_Language_Features
     */
    private static int getFragmentsFromR(final double r, final int fn, final double fs, final double fa) {
        if (fn > 0) {
            return fn;
        }
        return (int) Math.ceil(Math.max(Math.min(360.0 / fa, r * Math.PI / fs), 5));
    }

    /**
     * A cylinder without the top and the bottom
     */
    private static CSG3D openCylinder(int fn, final double fa, final double fs, final double h, final double r1, final double r2) {
        fn = getFragmentsFromR(Math.min(r1, r2), fn, fs, fa);
        final Point3D p1 = new Point3D(r1, 0, 0);
        final Point3D p2 = new Point3D(r1, 10, 0);
        final Point3D p3 = new Point3D(r2, 0, h);
        CSG3D s = new CSG3D(new HalfSpace(p1, p2, p3));
        final double a = 2.0 * Math.PI / fn;
        final Matrix4d m = new Matrix4d();
        m.setIdentity();
        m.set(new AxisAngle4d(0, 0, 1, a));
        CSG3D result = CSG3D.universe();
        for (int i = 0; i < fn; i++) {
            result = CSG3D.intersection(result, s);
            s = s.transform(m);
        }
        return result;
    }

    /**
     * Cylinder with ends
     * 
     * @param fn
     *            is usually 0. When this variable has a value greater than
     *            zero, the other two variables are ignored and full circle is
     *            rendered using this number of fragments.
     * @param fa
     *            is the minimum angle for a fragment.
     * @param fs
     *            is the minimum size of a fragment.
     * @param h
     *            length in Z
     * @param r1
     *            bottom (low Z) radius of frustum
     * @param r2
     *            top radius
     * @param centre
     *            Z goes from -h/2 to + h/2; or 0 to h
     */
    static CSG3D cylinder(final int fn, final double fa, final double fs, final double h, final double r1, final double r2,
            final boolean centre) {
        CSG3D result = openCylinder(fn, fa, fs, h, r1, r2);
        final Point3D p1 = new Point3D(0, 0, h);
        result = CSG3D.intersection(result, new CSG3D(new HalfSpace(p1, p1)));
        final Point3D p2 = new Point3D(0, 0, 0);
        result = CSG3D.intersection(result, new CSG3D(new HalfSpace(p1.neg(), p2)));
        if (centre) {
            final Matrix4d m = new Matrix4d();
            m.setIdentity();
            m.setTranslation(new Vector3d(0, 0, -h / 2));
            result = result.transform(m);
        }
        return result;
    }

    /**
     * Sphere. The f arguments do the same as for Cylinder
     */
    static CSG3D sphere(final int fn, final double fa, final double fs, final double r) {
        final int fnl = getFragmentsFromR(r, fn, fs, fa);
        final double a = 2.0 * Math.PI / fnl;
        final Matrix4d m = new Matrix4d();
        m.setIdentity();
        CSG3D result = CSG3D.universe();
        CSG3D cyl;
        double ang = 0;
        double angp = a;
        for (int i = 0; i < fnl / 4; i++) {
            cyl = openCylinder(fn, fa, fs, r * (Math.sin(angp) - Math.sin(ang)), r * Math.cos(ang), r * Math.cos(angp));
            m.setTranslation(new Vector3d(0, 0, r * Math.sin(ang)));
            cyl = cyl.transform(m);
            result = CSG3D.intersection(result, cyl);
            cyl = openCylinder(fn, fa, fs, r * (Math.sin(angp) - Math.sin(ang)), r * Math.cos(angp), r * Math.cos(ang));
            m.setTranslation(new Vector3d(0, 0, -r * Math.sin(ang)));
            cyl = cyl.transform(m);
            result = CSG3D.intersection(result, cyl);
            ang = angp;
            angp += a;
        }
        Point3D p1 = new Point3D(0, 0, r);
        result = CSG3D.intersection(result, new CSG3D(new HalfSpace(p1, p1)));
        p1 = new Point3D(0, 0, -r);
        result = CSG3D.intersection(result, new CSG3D(new HalfSpace(p1, p1)));
        return result;
    }
}
