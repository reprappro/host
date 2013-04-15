package org.reprap;

import java.io.FileOutputStream;
import java.io.PrintStream;

import org.reprap.utilities.Debug;

final class RfoXmlRenderer {
    /**
     * XML stack top. If it gets 100 deep we're in trouble...
     */
    private static final int top = 100;

    private final PrintStream XMLStream;
    private final String[] stack;
    private int sp;

    /**
     * Create an XML file called LegendFile starting with XML entry start.
     * 
     * @param LegendFile
     * @param start
     */
    RfoXmlRenderer(final String LegendFile, final String start) {
        FileOutputStream fileStream = null;
        try {
            fileStream = new FileOutputStream(LegendFile);
        } catch (final Exception e) {
            Debug.getInstance().errorMessage("XMLOut(): " + e);
        }
        XMLStream = new PrintStream(fileStream);
        stack = new String[top];
        sp = 0;
        push(start);
    }

    /**
     * Start item s
     */
    void push(final String s) {
        for (int i = 0; i < sp; i++) {
            XMLStream.print(" ");
        }
        XMLStream.println("<" + s + ">");
        final int end = s.indexOf(" ");
        if (end < 0) {
            stack[sp] = s;
        } else {
            stack[sp] = s.substring(0, end);
        }
        sp++;
        if (sp >= top) {
            Debug.getInstance().errorMessage("RFO: XMLOut stack overflow on " + s);
        }
    }

    /**
     * Output a complete item s all in one go.
     */
    void write(final String s) {
        for (int i = 0; i < sp; i++) {
            XMLStream.print(" ");
        }
        XMLStream.println("<" + s + "/>");
    }

    /**
     * End the current item.
     */
    void pop() {
        sp--;
        for (int i = 0; i < sp; i++) {
            XMLStream.print(" ");
        }
        if (sp < 0) {
            Debug.getInstance().errorMessage("RFO: XMLOut stack underflow.");
        }
        XMLStream.println("</" + stack[sp] + ">");
    }

    /**
     * Wind it up.
     */
    void close() {
        while (sp > 0) {
            pop();
        }
        XMLStream.close();
    }
}