package org.reprap.geometry.polygons;

import java.util.ArrayList;
import java.util.List;

import org.reprap.utilities.Debug;

/**
 * tree - class to hold lists to build a containment tree (that is a
 * representation of which polygon is inside which, like a Venn diagram).
 */
class TreeList {
    /**
     * Index of this polygon in the list
     */
    private final int index;

    /**
     * The polygons inside this one
     */
    private List<TreeList> children = null;

    /**
     * The polygon that contains this one
     */
    private TreeList parent = null;

    /**
     * Constructor builds from a polygon index
     */
    TreeList(final int i) {
        index = i;
        children = null;
        parent = null;
    }

    /**
     * Add a polygon as a child of this one
     */
    void addChild(final TreeList t) {
        if (children == null) {
            children = new ArrayList<TreeList>();
        }
        children.add(t);
    }

    /**
     * Get the ith polygon child of this one
     */
    private TreeList getChild(final int i) {
        if (children == null) {
            Debug.getInstance().errorMessage("treeList: attempt to get child from null list!");
            return null;
        }
        return children.get(i);
    }

    /**
     * Get the parent
     */
    TreeList getParent() {
        return parent;
    }

    /**
     * How long is the list (if any)
     */
    private int size() {
        if (children != null) {
            return children.size();
        } else {
            return 0;
        }
    }

    @Override
    public String toString() {
        String result;

        if (parent != null) {
            result = Integer.toString(index) + "(^" + parent.index + "): ";
        } else {
            result = Integer.toString(index) + "(^null): ";
        }

        for (int i = 0; i < size(); i++) {
            result += getChild(i).polygonIndex() + " ";
        }
        result += "\n";
        for (int i = 0; i < size(); i++) {
            result += getChild(i).toString();
        }
        return result;
    }

    /**
     * Remove every instance of polygon t from the list
     */
    void remove(final TreeList t) {
        for (int i = size() - 1; i >= 0; i--) {
            if (getChild(i) == t) {
                children.remove(i);
            }
        }
    }

    /**
     * Recursively walk the tree from here to find polygon target.
     */
    TreeList walkFind(final int target) {
        if (polygonIndex() == target) {
            return this;
        }

        for (int i = 0; i < size(); i++) {
            final TreeList result = getChild(i).walkFind(target);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Walk the tree building a CSG expression to represent all the polygons as
     * one thing.
     */
    CSG2D buildCSG(final List<CSG2D> csgPols) {
        if (size() == 0) {
            return csgPols.get(index);
        }

        CSG2D offspring = CSG2D.nothing();

        for (int i = 0; i < size(); i++) {
            final TreeList iEntry = getChild(i);
            final CSG2D iCSG = iEntry.buildCSG(csgPols);
            offspring = CSG2D.union(offspring, iCSG);
        }

        if (index < 0) {
            return offspring;
        } else {
            return CSG2D.difference(csgPols.get(index), offspring);
        }
    }

    /**
     * Do a depth-first walk setting parents. Any node that appears in more than
     * one list should have the deepest possible parent set as its parent, which
     * is what we want.
     */
    void setParents() {
        TreeList child;
        int i;
        for (i = 0; i < size(); i++) {
            child = getChild(i);
            child.parent = this;
        }
        for (i = 0; i < size(); i++) {
            child = getChild(i);
            child.setParents();
        }
    }

    /**
     * get the index of the polygon
     */
    private int polygonIndex() {
        return index;
    }
}