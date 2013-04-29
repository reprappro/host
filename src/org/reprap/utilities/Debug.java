package org.reprap.utilities;

import org.reprap.Preferences;

/**
 * @author Adrian
 */
public class Debug {
    private boolean commsDebug = false;
    private boolean debug = false;
    static private Debug db = null;

    private Debug() {
    }

    public static void refreshPreferences() {
        if (db == null) {
            db = new Debug();
        }
        try {
            // Try to load debug setting from properties file
            db.debug = Preferences.loadGlobalBool("Debug");
        } catch (final Exception ex) {
            // Fall back to non-debug mode if no setting is available
            db.debug = false;
        }

        db.commsDebug = false;
    }

    static public boolean d() {
        initialiseIfNeedBe();
        return db.debug;
    }

    static private void initialiseIfNeedBe() {
        if (db != null) {
            return;
        }
        refreshPreferences();
    }

    static public void d(final String s) {
        initialiseIfNeedBe();
        if (!db.debug) {
            return;
        }
        System.out.println("DEBUG: " + s + Timer.stamp());
        System.out.flush();
    }

    /**
     * A real hard error...
     */
    static public void e(final String s) {
        initialiseIfNeedBe();
        System.err.println("ERROR: " + s + Timer.stamp());
        System.err.flush();
        if (!db.debug) {
            return;
        }
        final Exception e = new Exception();
        e.printStackTrace();
    }

    /**
     * Just print a message anytime
     */
    static public void a(final String s) {
        initialiseIfNeedBe();
        System.out.println("message: " + s + Timer.stamp());
        System.out.flush();
    }

    static public void c(final String s) {
        initialiseIfNeedBe();
        if (!db.commsDebug) {
            return;
        }
        System.out.println("comms: " + s + Timer.stamp());
        System.out.flush();
    }
}
