package org.reprap.utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Boolean operators and similar
 */
enum Bop {
    ZERO("zero"), ONE("one"), LEAF("leaf"), NOT("not"), LEFT("left"), RIGHT("right"), AND("and"), OR("or"), XOR("xor");

    private String name;

    Bop(final String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * All above NOT are diadic; all including and below monadic
     */
    public boolean diadic() {
        return compareTo(NOT) > 0;
    }
}

/**
 * A single boolean variable with a name
 * 
 * @author ensab
 */
class Variable implements Comparator<Variable> {
    boolean bv;
    boolean init;
    String n;

    public Variable(final String s) {
        init = false;
        n = s;
    }

    public boolean value() {
        if (!init) {
            Debug.getInstance().errorMessage("Variable undefined!");
        }
        return bv;
    }

    public boolean isSet() {
        return init;
    }

    public void set(final boolean b) {
        bv = b;
        init = true;
    }

    public String name() {
        return n;
    }

    public void clean() {
        init = false;
    }

    public Variable(final Variable v) {
        if (!v.init) {
            Debug.getInstance().errorMessage("Variable(Variable v): input Variable undefined!");
        }
        bv = v.bv;
        init = v.init;
        n = new String(v.n);
    }

    public static boolean same(final Variable a, final Variable b) {
        return (a.compare(a, b) == 0);
    }

    /**
     * Compare means compare the lexical order of the names.
     */
    @Override
    public final int compare(final Variable a, final Variable b) {
        return (a.n.compareTo(b.n));
    }
}

/**
 * @author adrian
 */
class BooleanExpression {
    private BooleanExpression c1, c2;
    private Bop leafOp;
    private Variable leaf;
    private Variable[] variables;
    private int leafCount;

    /**
     * Make an expression from three or four atomic expressions in an array. exp
     * decides the expression.
     */
    private void makeFromSeveral(final BooleanExpression[] be, final int exp) {
        BooleanExpression t1;

        switch (be.length) {
        // Bits in exp:  ba
        // Expression = v[0] b (v[1] a v[2])
        // a, b == 0 -> OR
        // a, b == 1 -> AND
        case 3:
            leafCount = -1;
            c1 = be[0];
            if ((exp & 1) == 1) {
                c2 = new BooleanExpression(be[1], be[2], Bop.AND);
            } else {
                c2 = new BooleanExpression(be[1], be[2], Bop.OR);
            }
            if ((exp & 2) == 2) {
                leafOp = Bop.AND;
            } else {
                leafOp = Bop.OR;
            }
            recordVariables();
            break;

        // Bits in exp:  dcba
        // d == 0 -> Expression = v[0] c (v[1] b (v[2] a v[3]))
        // d == 1 -> Expression = (v[0] b v[1]) c (v[2] a v[3])	
        // a, b, c == 0 -> OR
        // a, b, c == 1 -> AND	
        case 4:
            leafCount = -1;

            if ((exp & 8) == 8) {
                if ((exp & 1) == 1) {
                    c2 = new BooleanExpression(be[2], be[3], Bop.AND);
                } else {
                    c2 = new BooleanExpression(be[2], be[3], Bop.OR);
                }
                if ((exp & 2) == 2) {
                    c1 = new BooleanExpression(be[0], be[1], Bop.AND);
                } else {
                    c1 = new BooleanExpression(be[0], be[1], Bop.OR);
                }
            } else {
                c1 = be[0];
                if ((exp & 1) == 1) {
                    t1 = new BooleanExpression(be[2], be[3], Bop.AND);
                } else {
                    t1 = new BooleanExpression(be[2], be[3], Bop.OR);
                }
                if ((exp & 2) == 2) {
                    c2 = new BooleanExpression(be[1], t1, Bop.AND);
                } else {
                    c2 = new BooleanExpression(be[1], t1, Bop.OR);
                }

            }
            if ((exp & 4) == 4) {
                leafOp = Bop.AND;
            } else {
                leafOp = Bop.OR;
            }
            recordVariables();
            break;

        default:
            Debug.getInstance().errorMessage("BooleanExpression(...): variable number not 3 or 4!");
        }
    }

    /**
     * Make an expression from three or four atomic expressions in an array. exp
     * decides the expression.
     */
    public BooleanExpression(final BooleanExpression[] be, final int exp) {
        makeFromSeveral(be, exp);
    }

    /**
     * Make an expression from three or four variables in an array. exp decides
     * the expression.
     */
    public BooleanExpression(final Variable[] v, final int exp) {
        final BooleanExpression[] be = new BooleanExpression[v.length];
        for (int i = 0; i < v.length; i++) {
            be[i] = new BooleanExpression(v[i]);
        }
        makeFromSeveral(be, exp);
    }

    /**
     * Operand and two operators
     */
    public BooleanExpression(final BooleanExpression a, final BooleanExpression b, final Bop op) {
        leafCount = -1;
        if (!op.diadic()) {
            Debug.getInstance().errorMessage("BooleanExpression(a, b): leaf operator or NOT!");
        }

        leafOp = op;
        leaf = null;
        c1 = a;
        c2 = b;
        recordVariables();
    }

    /**
     * Monadic operator
     */
    public BooleanExpression(final BooleanExpression a, final Bop op) {
        leafCount = -1;
        if (op != Bop.NOT) {
            Debug.getInstance().errorMessage("BooleanExpression(..., NOT): op not NOT!");
        }

        leafOp = op;
        leaf = null;
        c1 = a;
        c2 = null;
        recordVariables();
    }

    /**
     * Variable leaf
     */
    public BooleanExpression(final Variable v) {
        leafCount = -1;
        c1 = null;
        c2 = null;
        leafOp = Bop.LEAF;
        leaf = v;
        recordVariables();
    }

    public int leafCount() {
        if (leafCount < 0) {
            if (leafOp == Bop.LEAF) {
                leafCount = 1;
            } else if (leafOp == Bop.NOT) {
                leafCount = c1.leafCount();
            } else {
                leafCount = c1.leafCount() + c2.leafCount();
            }
        }

        return leafCount;
    }

    private void recordVariables() {
        final int vc = leafCount();
        variables = new Variable[vc];
        int i = 0;
        int k;
        if (leafOp == Bop.LEAF) {
            variables[i++] = leaf;
        } else if (leafOp == Bop.NOT) {
            for (k = 0; k < c1.variables.length; k++) {
                variables[i++] = c1.variables[k];
            }
        } else {
            for (k = 0; k < c1.variables.length; k++) {
                variables[i++] = c1.variables[k];
            }
            for (k = 0; k < c2.variables.length; k++) {
                variables[i++] = c2.variables[k];
            }
        }
    }

    public void setAll(final int i) {
        TableRow.setAll(variables, i);
    }

    public Variable[] getVariables() {
        return variables;
    }

    public int getIndex(final Variable v) {
        for (int i = 0; i < variables.length; i++) {
            if (v == variables[i]) {
                return i;
            }
        }
        Debug.getInstance().errorMessage("getIndex(): variable not found!");
        return -1;
    }

    public boolean value() {
        switch (leafOp) {
        case LEAF:
            return leaf.value();

        case ZERO:
            return false;

        case ONE:
            return true;

        case NOT:
            return !c1.value();

        case LEFT:
            return c1.value();

        case RIGHT:
            return c2.value();

        case AND:
            return c1.value() & c2.value();

        case OR:
            return c1.value() | c2.value();

        case XOR:
            return c1.value() ^ c2.value();

        default:
            Debug.getInstance().errorMessage("generateValue_r: dud operator!");
        }
        return false;
    }

    private String toJava_r(String r) {
        switch (leafOp) {
        case LEAF:
            return r + leaf.name();

        case ZERO:
            return r + "RrCSG.nothing()";

        case ONE:
            return r + "RrCSG.universe()";

        case NOT:
            return c1.toJava_r(r) + ".complement()";

        case LEFT:
            return c1.toJava_r(r);

        case RIGHT:
            return c2.toJava_r(r);

        case AND:
            r += "RrCSG.intersection(";
            r = c1.toJava_r(r) + ", ";
            r = c2.toJava_r(r) + ")";
            return r;

        case OR:
            r += "RrCSG.union(";
            r = c1.toJava_r(r) + ", ";
            r = c2.toJava_r(r) + ")";
            return r;

        case XOR:
            Debug.getInstance().errorMessage("toJava(): got to an XOR...");
            break;

        default:
            Debug.getInstance().errorMessage("toJava(): dud operator");
        }

        return r;
    }

    public String toJava() {
        final String r = "r = ";
        return toJava_r(r) + ";";
    }
}

/**
 * A row of variables in a function table, and the table value. Also contains
 * useful functions for variable arrays.
 * 
 * @author ensab
 */
class TableRow implements Comparator<TableRow> {
    private final Variable[] vs;
    private boolean b;

    public TableRow() {
        vs = null;
    }

    public TableRow(final Variable[] vin, final boolean bin) {
        vs = sort(vin);
        b = bin;
    }

    public int length() {
        return vs.length;
    }

    public boolean value() {
        return b;
    }

    public Variable get(final int i) {
        return vs[i];
    }

    public Variable[] all() {
        return vs;
    }

    @Override
    public String toString() {
        String result = "";
        for (final Variable element : vs) {
            result += element.name() + " ";
        }
        return result;
    }

    /**
     * Set all the variables in a list according to the corresponding bits in an
     * integer.
     */
    public static void setAll(final Variable[] vars, final int v) {
        int k = 1;
        for (final Variable var : vars) {
            if ((v & k) == 0) {
                var.set(false);
            } else {
                var.set(true);
            }
            k *= 2;
        }
    }

    /**
     * Remove one variable from a list to make a shorter list
     */
    public static Variable[] eliminateVariable(final Variable[] vars, final Variable remove) {
        final Variable[] result = new Variable[vars.length - 1];
        int k = 0;

        for (final Variable var : vars) {
            if (var != remove) {
                result[k] = new Variable(var);
                k++;
            }
        }

        return result;
    }

    /**
     * Take a list of variables and return a copy lexically sorted by name
     */
    private static Variable[] sort(final Variable[] vins) {
        final Variable[] result = new Variable[vins.length];
        for (int i = 0; i < vins.length; i++) {
            result[i] = new Variable(vins[i]);
        }
        java.util.Arrays.sort(result, new Variable(""));
        return result;
    }

    /**
     * Check if two lists of variables have the same variables in the same order
     */
    public static boolean sameOrder(final Variable[] a, final Variable[] b) {
        if (a.length != b.length) {
            return false;
        }

        for (int i = 0; i < a.length; i++) {
            if (!Variable.same(a[i], b[i])) {
                return false;
            }
        }

        return true;
    }

    /**
     * Find the binary number represented by the list
     */
    public int number() {
        int result = 0;

        for (int i = length() - 1; i >= 0; i--) {
            if (get(i).value()) {
                result |= 1;
            }
            result = result << 1;
        }

        return result;
    }

    /**
     * Compare the binary numbers represented by two lists
     */
    @Override
    public final int compare(final TableRow a, final TableRow b) {
        final int va = a.number();
        final int vb = b.number();

        if (va < vb) {
            return -1;
        } else if (va > vb) {
            return 1;
        }

        return 0;
    }
}

/**
 * @author adrian
 */
class FunctionTable {
    List<TableRow> rows;

    boolean allFalse, allTrue;

    public FunctionTable() {
        rows = new ArrayList<TableRow>();
        allFalse = true;
        allTrue = true;
    }

    /**
     * Add a new row to the function table
     */
    public void addRow(final Variable[] v, final boolean b) {
        if (b) {
            allFalse = false;
        } else {
            allTrue = false;
        }

        final TableRow newOne = new TableRow(v, b);

        //		 Check that each has the same variables as the first
        if (rows.size() > 0) {
            if (!TableRow.sameOrder(newOne.all(), rows.get(0).all())) {
                Debug.getInstance().errorMessage("FunctionTable.addRow() - variable lists different!");
            }
        }

        rows.add(newOne);
    }

    public void tableCheck() {
        // Check we have the right number of entries
        final int vars = rows.get(0).all().length;
        int leng = 1;
        for (int j = 0; j < vars; j++) {
            leng *= 2;
        }

        if (leng != rows.size()) {
            Debug.getInstance().errorMessage("FunctionTable.tableCheck() - incorrect entry count: " + rows.size() + "(should be " + leng + ")");
        }
        Collections.sort(rows, new TableRow());
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i - 1).number() == rows.get(i).number()) {
                Debug.getInstance().errorMessage("FunctionTable.tableDone() - identical rows: " + rows.get(i - 1).toString() + rows.get(i).toString());
            }
        }
    }

    public FunctionTable(final BooleanExpression b) {
        this();

        int i;
        final int inputs = b.leafCount();

        int entries = 1;
        for (i = 0; i < inputs; i++) {
            entries *= 2;
        }

        for (i = 0; i < entries; i++) {
            b.setAll(i);
            addRow(b.getVariables(), b.value());
        }

        tableCheck();
    }

    public FunctionTable(final BooleanExpression b, final Variable v, final Variable equal_v, final boolean opposite) {
        this();

        int i;
        final int inputs = b.leafCount() - 1;

        int entries = 1;
        for (i = 0; i < inputs; i++) {
            entries *= 2;
        }

        for (i = 0; i < entries * 2; i++) {
            b.setAll(i);
            if (opposite ^ (equal_v.value() == v.value())) {
                addRow(TableRow.eliminateVariable(b.getVariables(), equal_v), b.value());
            }
        }

        tableCheck();
    }

    public boolean allOnes() {
        return allTrue;
    }

    public boolean allZeros() {
        return allFalse;
    }

    public int entries() {
        return rows.size();
    }

    static boolean same(final FunctionTable a, final FunctionTable b) {
        if (!TableRow.sameOrder(a.rows.get(0).all(), b.rows.get(0).all())) {
            return false;
        }

        if (a.entries() != b.entries()) {
            return false;
        }
        if (a.allFalse && b.allFalse) {
            return true;
        }
        if (a.allTrue && b.allTrue) {
            return true;
        }
        for (int i = 0; i < a.entries(); i++) {
            if (a.rows.get(i).value() != b.rows.get(i).value()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        String result = "\n\t// " + rows.get(0).toString();
        for (int i = 0; i < entries(); i++) {
            final TableRow tr = rows.get(i);
            final Variable[] vs = tr.all();
            result += "\n\t// ";
            for (final Variable element : vs) {
                if (element.value()) {
                    result += "1 ";
                } else {
                    result += "0 ";
                }
            }

            result += "| ";
            if (tr.value()) {
                result += "1 ";
            } else {
                result += "0 ";
            }
        }
        return result;
    }
}

/**
 * This is a program to automatically generate the Java for dealing with the
 * simplification of CSG expressions. That is to say that it generates
 * simplified expressions when two operands in a more complicated expression are
 * equal, or are complements.
 * 
 * @author adrian
 */
public class CodeGenerator {
    static Variable[] eliminate(final Variable[] v, final int k) {
        final int len = v.length;
        final Variable[] result = new Variable[len - 1];
        int count = 0;
        for (int i = 0; i < len; i++) {
            if (i != k) {
                result[count++] = v[i];
            }
        }
        return result;
    }

    static List<BooleanExpression> generateAllPairs(final BooleanExpression[] b2) {
        if (b2.length != 2) {
            Debug.getInstance().errorMessage("generateAllPairs: array not of length 2: " + b2.length);
        }

        final List<BooleanExpression> bel2 = new ArrayList<BooleanExpression>();

        final Bop[] bopValues = Bop.values();
        for (final Bop bopValue : bopValues) {
            if (bopValue.diadic()) {
                final BooleanExpression be = new BooleanExpression(b2[0], b2[1], bopValue);
                bel2.add(be);
                final BooleanExpression bf = new BooleanExpression(be, Bop.NOT);
                bel2.add(bf);
                final BooleanExpression bg = new BooleanExpression(new BooleanExpression(b2[0], Bop.NOT), b2[1], bopValue);
                bel2.add(bg);
                final BooleanExpression bh = new BooleanExpression(b2[0], new BooleanExpression(b2[1], Bop.NOT), bopValue);
                bel2.add(bh);
                final BooleanExpression bi = new BooleanExpression(new BooleanExpression(b2[0], Bop.NOT),
                        new BooleanExpression(b2[1], Bop.NOT), bopValue);
                bel2.add(bi);
            }
        }
        return bel2;
    }

    static List<BooleanExpression> generateAllTripples(final BooleanExpression[] b3) {
        final BooleanExpression[] b2a = new BooleanExpression[2];
        final BooleanExpression[] b2b = new BooleanExpression[2];
        final List<BooleanExpression> bel3 = new ArrayList<BooleanExpression>();
        List<BooleanExpression> bel2a, bel2b;
        int i, j;

        b2b[0] = b3[0];
        b2a[0] = b3[1];
        b2a[1] = b3[2];
        bel2a = generateAllPairs(b2a);
        for (i = 0; i < bel2a.size(); i++) {
            b2b[1] = bel2a.get(i);
            bel2b = generateAllPairs(b2b);
            for (j = 0; j < bel2b.size(); j++) {
                bel3.add(bel2b.get(i));
            }
        }

        b2b[0] = b3[1];
        b2a[0] = b3[0];
        b2a[1] = b3[2];
        bel2a = generateAllPairs(b2a);
        for (i = 0; i < bel2a.size(); i++) {
            b2b[1] = bel2a.get(i);
            bel2b = generateAllPairs(b2b);
            for (j = 0; j < bel2b.size(); j++) {
                bel3.add(bel2b.get(i));
            }
        }

        b2b[0] = b3[2];
        b2a[0] = b3[0];
        b2a[1] = b3[1];
        bel2a = generateAllPairs(b2a);
        for (i = 0; i < bel2a.size(); i++) {
            b2b[1] = bel2a.get(i);
            bel2b = generateAllPairs(b2b);
            for (j = 0; j < bel2b.size(); j++) {
                bel3.add(bel2b.get(i));
            }
        }

        return bel3;
    }

    static BooleanExpression findEqualTwo(final FunctionTable f, final Variable[] v) {
        if (v.length != 2) {
            Debug.getInstance().errorMessage("findEqualTwo: array not of length 2: " + v.length);
        }
        final BooleanExpression[] b2 = new BooleanExpression[2];
        b2[0] = new BooleanExpression(v[0]);
        b2[1] = new BooleanExpression(v[1]);
        final List<BooleanExpression> bel = generateAllPairs(b2);
        BooleanExpression be;
        FunctionTable g;
        for (int i = 0; i < bel.size(); i++) {
            be = bel.get(i);
            g = new FunctionTable(be);
            if (FunctionTable.same(f, g)) {
                return be;
            }
        }
        return null;
    }

    static BooleanExpression findEqualThree(final FunctionTable f, final Variable[] v) {
        if (v.length != 3) {
            Debug.getInstance().errorMessage("findEqualThree: array not of length 3: " + v.length);
        }
        final BooleanExpression[] b3 = new BooleanExpression[3];
        b3[0] = new BooleanExpression(v[0]);
        b3[1] = new BooleanExpression(v[1]);
        b3[2] = new BooleanExpression(v[2]);
        final List<BooleanExpression> bel = generateAllTripples(b3);
        BooleanExpression be;
        FunctionTable g;
        for (int i = 0; i < bel.size(); i++) {
            be = bel.get(i);
            g = new FunctionTable(be);
            if (FunctionTable.same(f, g)) {
                return be;
            }
        }
        return null;
    }

    private static void oneCase4(final Variable[] variables, final int exp, final int j, final int k, final boolean opposite,
            final boolean fts) {
        final BooleanExpression a = new BooleanExpression(variables, exp);
        final FunctionTable f = new FunctionTable(a, variables[j], variables[k], opposite);
        final BooleanExpression g = findEqualThree(f, eliminate(variables, k));

        int caseVal = 0;
        if (opposite) {
            caseVal |= 1;
        }
        switch (j) {
        case 0:
            if (k == 2) {
                caseVal |= 2;
            } else if (k == 3) {
                caseVal |= 4;
            }
            break;

        case 1:
            if (k == 2) {
                caseVal |= 6;
            } else if (k == 3) {
                caseVal |= 8;
            }
            break;

        case 2:
            if (k == 3) {
                caseVal |= 10;
            }
            break;

        default:

        }

        caseVal |= exp << 4;
        System.out.println("\tcase " + caseVal + ": ");
        if (fts) {
            System.out.println("\t// " + a.toJava());
            System.out.print("\t// " + variables[j].name() + " = ");
            if (opposite) {
                System.out.print("!");
            }
            System.out.println(variables[k].name() + " ->");
            System.out.println(f.toString());
        }

        if (g != null || f.allOnes() || f.allZeros()) {
            if (f.allOnes()) {
                System.out.println("\t\tr = RrCSG.universe();");
            } else if (f.allZeros()) {
                System.out.println("\t\tr = RrCSG.nothing();");
            } else {
                System.out.println("\t\t" + g.toJava());
            }
        } else {
            System.out.println("\t\t// No equivalence." + "\n");
        }
        System.out.println("\t\tbreak;\n");
    }

    private static void allCases4(final Variable[] variables) {
        for (int exp = 0; exp < 16; exp++) {
            for (int j = 0; j < 3; j++) {
                for (int k = j + 1; k < 4; k++) {
                    oneCase4(variables, exp, j, k, false, true);
                    oneCase4(variables, exp, j, k, true, true);
                }
            }
        }
    }

    public static void main(final String[] args) {
        final Variable[] variables = new Variable[4];
        variables[0] = new Variable("a");
        variables[1] = new Variable("b");
        variables[2] = new Variable("c");
        variables[3] = new Variable("d");
        allCases4(variables);
    }
}
