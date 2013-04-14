/*
 
 RepRap
 ------
 
 The Replicating Rapid Prototyper Project
 
 
 Copyright (C) 2005
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
 Library General Public Licence for more details.
 
 For this purpose the words "software" and "library" in the GNU Library
 General Public Licence are taken to mean any and all computer programs
 computer files data results documents and other copyright information
 available from the RepRap project.
 
 You should have received a copy of the GNU Library General Public
 Licence along with RepRap; if not, write to the Free
 Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA,
 or see
 
 http://www.gnu.org/
 
 =====================================================================
 
 RrRectangle: 2D rectangles
 
 First version 20 May 2005
 This version: 1 May 2006 (Now in CVS - no more comments here)
 
 */

package org.reprap.geometry.polyhedra;

import org.reprap.geometry.polygons.Interval;

/**
 * A 3D box is an X, Y and a Z interval
 */
class Box {
    private Interval x = null;
    private Interval y = null;
    private Interval z = null;

    /**
     * Copy constructor
     */
    private Box(final Box b) {
        x = new Interval(b.x);
        y = new Interval(b.y);
        z = new Interval(b.z);
    }

    /**
     * Make from any diagonal corners
     */
    private Box(final Point3D a, final Point3D b) {
        x = new Interval(Math.min(a.x(), b.x()), Math.max(a.x(), b.x()));
        y = new Interval(Math.min(a.y(), b.y()), Math.max(a.y(), b.y()));
        y = new Interval(Math.min(a.z(), b.z()), Math.max(a.z(), b.z()));
    }

    /**
     * Make from X and Y intervals
     */
    private Box(final Interval xi, final Interval yi, final Interval zi) {
        x = new Interval(xi);
        y = new Interval(yi);
        z = new Interval(zi);
    }

    Interval x() {
        return x;
    }

    Interval y() {
        return y;
    }

    Interval z() {
        return z;
    }

    @Override
    public String toString() {
        return "<BOX x:" + x.toString() + ", y:" + y.toString() + ", z:" + z.toString() + ">";
    }
}
