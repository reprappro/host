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
 
 RrInterval: 1D intervals
 
 First version 20 May 2005
 This version: 1 May 2006 (Now in CVS - no more comments here)
 
 */

package org.reprap.geometry.polygons;

import org.reprap.utilities.Debug;

/**
 * Real 1D intervals
 */
public class Interval {
    private double low;
    private double high;
    private final boolean empty;

    public Interval() {
        empty = true;
    }

    /**
     * Two ends...
     */
    public Interval(final double l, final double h) {
        low = l;
        high = h;
        empty = (low > high);
    }

    /**
     * Deep copy
     */
    public Interval(final Interval i) {
        low = i.low;
        high = i.high;
        empty = i.empty;
    }

    public double low() {
        return low;
    }

    public double high() {
        return high;
    }

    public boolean empty() {
        return empty;
    }

    /**
     * The biggest possible
     * 
     * @return biggest possible interval
     */
    public static Interval bigInterval() {
        return new Interval(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    @Override
    public String toString() {
        if (empty) {
            return "[empty]";
        }
        return "[l:" + Double.toString(low) + ", h:" + Double.toString(high) + "]";
    }

    /**
     * Accommodate v
     */
    public void expand(final double v) {
        if (empty) {
            low = v;
            high = v;
        } else {
            if (v < low) {
                low = v;
            }
            if (v > high) {
                high = v;
            }
        }
    }

    /**
     * Accommodate another interval
     */
    public void expand(final Interval i) {
        expand(i.low);
        expand(i.high);
    }

    public double length() {
        return high - low;
    }

    public double cen() {
        return (high + low) * 0.5;
    }

    /**
     * Interval addition
     * 
     * @return new interval based on addition of intervals a and b
     */
    public static Interval add(final Interval a, final Interval b) {
        if (a.empty || b.empty) {
            Debug.e("RrInterval.add(...): adding empty interval(s).");
        }
        return new Interval(a.low + b.low, a.high + b.high);
    }

    /**
     * @return new interval based on addition of interval a and value b
     */
    public static Interval add(final Interval a, final double b) {
        if (a.empty) {
            Debug.e("RrInterval.add(...): adding an empty interval.");
        }
        return new Interval(a.low + b, a.high + b);
    }

    /**
     * @return new interval based on addition of interval a and value b
     */
    public static Interval add(final double b, final Interval a) {
        return add(a, b);
    }

    /**
     * Interval subtraction
     * 
     * @return new interval based on subtraction of interval a and value b
     */
    public static Interval sub(final Interval a, final Interval b) {
        if (a.empty || b.empty) {
            Debug.e("RrInterval.sub(...): subtracting empty interval(s).");
        }
        return new Interval(a.low - b.high, a.high - b.low);
    }

    /**
     * @return new interval based on subtraction of interval a and value b
     */
    public static Interval sub(final Interval a, final double b) {
        if (a.empty) {
            Debug.e("RrInterval.sub(...): subtracting an empty interval.");
        }
        return new Interval(a.low - b, a.high - b);
    }

    /**
     * @return new interval based on subtraction of interval a and value b
     */
    public static Interval sub(final double b, final Interval a) {
        if (a.empty) {
            Debug.e("RrInterval.sub(...): subtracting an empty interval.");
        }
        return new Interval(b - a.high, b - a.low);
    }

    /**
     * Interval multiplication
     * 
     * @return new interval based on interval multiplication of intervals a and
     *         b
     */
    public static Interval mul(final Interval a, final Interval b) {
        if (a.empty || b.empty) {
            Debug.e("RrInterval.mul(...): multiplying empty intervals.");
        }
        final double d = a.low * b.low;
        final Interval r = new Interval(d, d);
        r.expand(a.low * b.high);
        r.expand(a.high * b.low);
        r.expand(a.high * b.high);
        return r;
    }

    /**
     * @return new interval based on interval multiplication of interval a by
     *         factor f
     */
    public static Interval mul(final Interval a, final double f) {
        if (a.empty) {
            Debug.e("RrInterval.mul(...): multiplying an empty interval.");
        }
        if (f > 0) {
            return new Interval(a.low * f, a.high * f);
        } else {
            return new Interval(a.high * f, a.low * f);
        }
    }

    /**
     * @return new interval based on interval multiplication of interval a by
     *         factor f
     */
    public static Interval mul(final double f, final Interval a) {
        return mul(a, f);
    }

    /**
     * Negative, zero, or positive?
     * 
     * @return true if interval is negative (?)
     */
    public boolean neg() {
        return high < 0;
    }

    /**
     * @return true if interval is positive (?)
     */
    public boolean pos() {
        return low >= 0;
    }

    /**
     * Does the interval _contain_ zero?
     * 
     * @return true if zero is within the interval
     */
    public boolean zero() {
        return (!neg() && !pos());
    }

    /**
     * In or out
     * 
     * @return true if v is within the interval
     */
    public boolean in(final double v) {
        return v >= low && v <= high;
    }

    /**
     * Identical within tolerance
     * 
     * @return true if intervals a and b are identical within the tolerance
     */
    public static boolean same(final Interval a, final Interval b, final double tolerance) {
        if (a.empty() && b.empty()) {
            return true;
        }

        if (Math.abs(a.low - b.low) > tolerance) {
            return false;
        }
        if (Math.abs(a.high - b.high) > tolerance) {
            return false;
        }
        return true;
    }

    /**
     * Absolute value of an interval
     * 
     * @return absolute value of the interval
     */
    public Interval abs() {
        Interval result = new Interval(this);
        double p;

        if (low < 0) {
            if (high <= 0) {
                result = new Interval(-high, -low);
            } else {
                result = new Interval(0, result.high);
                p = -low;
                if (p > high) {
                    result = new Interval(result.low, p);
                }
            }
        }
        return (result);
    }

    /**
     * Sign of an interval
     * 
     * @return sign of the interval
     */
    public Interval sign() {
        return (new Interval(Math.signum(low), Math.signum(high)));
    }

    /**
     * Max
     * 
     * @return max value of the interval
     */
    public static Interval max(final Interval a, final Interval b) {
        Interval result = new Interval(b);
        if (a.low > b.low) {
            result = new Interval(a.low, result.high);
        }
        if (a.high > b.high) {
            result = new Interval(result.low, a.high);
        }
        return (result);
    }

    /**
     * Min
     * 
     * @return minimal value of the interval
     */
    public static Interval min(final Interval a, final Interval b) {
        Interval result = new Interval(b);
        if (a.low < b.low) {
            result = new Interval(a.low, result.high);
        }
        if (a.high < b.high) {
            result = new Interval(result.low, a.high);
        }
        return (result);
    }

    /**
     * Intersection
     */
    public static Interval intersection(final Interval a, final Interval b) {
        if (a.empty()) {
            return a;
        }
        if (b.empty()) {
            return b;
        }
        return new Interval(Math.max(a.low, b.low), Math.min(a.high, b.high));
    }

    /**
     * Union
     */
    public static Interval union(final Interval a, final Interval b) {
        if (a.empty()) {
            return b;
        }
        if (b.empty()) {
            return a;
        }
        return new Interval(Math.min(a.low, b.low), Math.max(a.high, b.high));
    }
}
