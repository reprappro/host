package org.reprap.utilities;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;

/**
 * @author Adrian
 */
public class Debug {
    private static final NumberFormat MILLI_FORMATTER = createNumberFormater();
    private static final DateFormat DATE_FORMATER = DateFormat.getDateTimeInstance();

    private static Debug instance = new Debug(false, false);

    public static synchronized Debug getInstance() {
        return instance;
    }

    public static synchronized void refreshPreferences(final boolean debug, final boolean commsDebug) {
        Debug.instance = new Debug(debug, commsDebug);
    }

    private static NumberFormat createNumberFormater() {
        final NumberFormat result = NumberFormat.getIntegerInstance();
        result.setMinimumIntegerDigits(3);
        result.setMaximumIntegerDigits(3);
        return result;
    }

    private final boolean commsDebug;
    private final boolean debug;

    private Debug(final boolean debug, final boolean commsDebug) {
        this.debug = debug;
        this.commsDebug = commsDebug;
    }

    public boolean isDebug() {
        return debug;
    }

    public void debugMessage(final String s) {
        if (debug) {
            System.out.println("DEBUG: " + s + stamp());
        }
    }

    public void errorMessage(final String message) {
        System.err.println("ERROR: " + message + stamp());
        if (debug) {
            new Exception().printStackTrace();
        }
    }

    public void printMessage(final String message) {
        System.out.println("message: " + message + stamp());
    }

    public void gcodeDebugMessage(final String message) {
        if (commsDebug) {
            System.out.println("comms: " + message + stamp());
        }
    }

    private String stamp() {
        final Date now = new Date();
        final long millis = now.getTime() % 1000L;
        return "[" + DATE_FORMATER.format(now) + " + " + MILLI_FORMATTER.format(millis) + " ms]";
    }
}
