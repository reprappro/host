package org.reprap;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import javax.media.j3d.Appearance;
import javax.media.j3d.Material;
import javax.vecmath.Color3f;

import org.reprap.utilities.Debug;
import org.reprap.utilities.RepRapUtils;

/**
 * A single centralised repository of the user's current preference settings.
 * This also implements (almost) a singleton for easy global access. If there
 * are no current preferences the system-wide ones are copied to the user's
 * space.
 */
public class Preferences {
    private static final String propsFolder = ".reprap";
    private static final String MachineFile = "Machine";
    private static final String propsDirDist = "reprap-configurations";
    private static final String prologueFile = "prologue.gcode";
    private static final String epilogueFile = "epilogue.gcode";
    private static final String baseFile = "base.stl";
    private static final char activeFlag = '*';
    private static final int grid = 100;
    private static final double gridRes = 1.0 / grid;
    private static final double tiny = 1.0e-12; // A small number
    private static final double machineResolution = 0.05; // RepRap step size in mm
    private static final double inToMM = 25.4;
    private static final Color3f black = new Color3f(0, 0, 0);

    private static String propsFile = "reprap.properties";
    private static Preferences globalPrefs = null;
    private static String[] allMachines = null;
    private static boolean displaySimulation = false;

    private final Properties mainPreferences = new Properties();

    public static double gridRes() {
        return gridRes;
    }

    public static double tiny() {
        return tiny;
    }

    public static double machineResolution() {
        return machineResolution;
    }

    public static double inchesToMillimetres() {
        return inToMM;
    }

    public static boolean simulate() {
        return displaySimulation;
    }

    public static void setSimulate(final boolean s) {
        displaySimulation = s;
    }

    public Preferences() throws IOException {
        final File mainDir = new File(getUsersRootDir());
        if (!mainDir.exists()) {
            copySystemConfigurations(mainDir);
        }

        // Construct URL of user properties file
        final String path = getPropertiesPath();
        final File mainFile = new File(path);
        final URL mainUrl = mainFile.toURI().toURL();

        if (mainFile.exists()) {
            final InputStream preferencesStream = mainUrl.openStream();
            try {
                mainPreferences.load(preferencesStream);
            } finally {
                preferencesStream.close();
            }
            comparePreferences();
        } else {
            Debug.e("Can't find your RepRap configurations: " + getPropertiesPath());
        }
    }

    private static void copySystemConfigurations(final File usersDir) throws IOException {
        final String sysConfig = getSystemConfigurationDir();
        RepRapUtils.copyTree(new File(sysConfig), usersDir);
    }

    /**
     * Where the user stores all their configuration files
     */
    public static String getUsersRootDir() {
        return System.getProperty("user.home") + File.separatorChar + propsFolder + File.separatorChar;
    }

    /**
     * The master file that lists the user's machines and flags the active one
     * with a * character at the start of its name.
     */
    public static String getMachineFilePath() {
        return getUsersRootDir() + MachineFile;
    }

    /**
     * List of all the available RepRap machine types
     */
    private static String[] getAllMachines() {
        final File mf = new File(getMachineFilePath());
        String[] result = null;
        try {
            result = new String[RepRapUtils.countLines(mf)];
            final FileInputStream fstream = new FileInputStream(mf);
            final DataInputStream in = new DataInputStream(fstream);
            final BufferedReader br = new BufferedReader(new InputStreamReader(in));
            int i = 0;
            String s;
            while ((s = br.readLine()) != null) {
                result[i] = s;
                i++;
            }
            in.close();
        } catch (final IOException e) {
            Debug.e("Can't read configuration file: " + mf.toString());
            e.printStackTrace();
        }
        return result;
    }

    /**
     * The name of the user's active machine configuration (without the leading
     * *)
     */
    public static String getActiveMachineName() {
        if (allMachines == null) {
            allMachines = getAllMachines();
        }
        for (final String machine : allMachines) {
            if (machine.charAt(0) == activeFlag) {
                return machine.substring(1, machine.length());
            }
        }
        Debug.e("No active RepRap set (add " + activeFlag + " to the start of a line in the file: " + getMachineFilePath()
                + ").");
        return "";
    }

    /**
     * The directory containing all the user's configuration files for their
     * active machine
     */
    public static String getActiveMachineDir() {
        return getUsersRootDir() + getActiveMachineName() + File.separatorChar;
    }

    /**
     * Set the active machine to the one named
     */
    public static void setActiveMachine(final String newActiveMachine) {
        if (allMachines == null) {
            allMachines = getAllMachines();
        }
        try {
            final FileWriter outFile = new FileWriter(getMachineFilePath());
            final PrintWriter out = new PrintWriter(outFile);
            for (int i = 0; i < allMachines.length; i++) {
                if (allMachines[i].charAt(0) == activeFlag) {
                    allMachines[i] = allMachines[i].substring(1, allMachines[i].length());
                }
                if (allMachines[i].contentEquals(newActiveMachine)) {
                    allMachines[i] = activeFlag + newActiveMachine;
                }
                out.println(allMachines[i]);
                i++;
            }
            outFile.close();
        } catch (final Exception e) {
            Debug.e("Can't write to file: " + getMachineFilePath());
        }
    }

    /**
     * Where the user's properties file is
     */
    public static String getPropertiesPath() {
        return getActiveMachineDir() + propsFile;
    }

    /**
     * Where are the system-wide master copies?
     */
    public static String getSystemConfigurationDir() {
        final URL sysConfig = ClassLoader.getSystemResource(propsDirDist);
        if (sysConfig == null) {
            Debug.e("Can't find system RepRap configurations: " + propsDirDist);
            return null;
        }
        return sysConfig.getFile() + File.separator;
    }

    /**
     * Where the system version of the user's properties file is
     */
    public static String getSystemPropertiesDir() {
        return getSystemConfigurationDir() + getActiveMachineName() + File.separator;
    }

    /**
     * Where the user's build-base STL file is
     */
    public static String getBasePath() {
        return getActiveMachineDir() + baseFile;
    }

    /**
     * Where the user's GCode prologue file is
     */
    public static String getProloguePath() {
        return getActiveMachineDir() + prologueFile;
    }

    /**
     * Where the user's GCode epilogue file is
     */
    public static String getEpiloguePath() {
        return getActiveMachineDir() + epilogueFile;
    }

    /**
     * Compare the user's preferences with the distribution one and report any
     * different names.
     */
    private void comparePreferences() {
        final Enumeration<?> usersLot = mainPreferences.propertyNames();

        final String sysProps = getSystemPropertiesDir() + propsFile;
        final File sysFile = new File(sysProps);
        URL sysUrl;
        try {
            sysUrl = sysFile.toURI().toURL();
        } catch (final MalformedURLException e) {
            Debug.e("System preferences location wrong: " + sysProps);
            return;
        }
        final Properties sysPreferences = new Properties();
        if (sysFile.exists()) {
            try {
                sysPreferences.load(sysUrl.openStream());
            } catch (final IOException e) {
                Debug.e("System preferences input error: " + sysProps);
            }
        } else {
            Debug.e("Can't find your System's RepRap configurations: " + sysProps);
        }

        final Enumeration<?> distLot = sysPreferences.propertyNames();

        String result = "";
        int count = 0;
        boolean noDifference = true;

        while (usersLot.hasMoreElements()) {
            final String next = (String) usersLot.nextElement();
            if (!sysPreferences.containsKey(next)) {
                result += " " + next + "\n";
                count++;
            }
        }
        if (count > 0) {
            result = "Your preferences file contains:\n" + result + "which ";
            if (count > 1) {
                result += "are";
            } else {
                result += "is";
            }
            result += " not in the distribution preferences file.";
            Debug.d(result);
            noDifference = false;
        }

        result = "";
        count = 0;
        while (distLot.hasMoreElements()) {
            final String next = (String) distLot.nextElement();
            if (!mainPreferences.containsKey(next)) {
                result += " " + next + "\n";
                count++;
            }
        }

        if (count > 0) {
            result = "The distribution preferences file contains:\n" + result + "which ";
            if (count > 1) {
                result += "are";
            } else {
                result += "is";
            }
            result += " not in your preferences file.";
            Debug.d(result);
            noDifference = false;
        }

        if (noDifference) {
            Debug.d("The distribution preferences file and yours match.  This is good.");
        }
    }

    public void save(final boolean startUp) throws FileNotFoundException, IOException {
        final String savePath = getPropertiesPath();
        final File f = new File(savePath);
        if (!f.exists()) {
            f.createNewFile();
        }

        final OutputStream output = new FileOutputStream(f);
        mainPreferences.store(output, "RepRap machine parameters. See http://reprap.org/wiki/Java_Software_Preferences_File");

        if (!startUp) {
            org.reprap.Main.gui.getPrinter().refreshPreferences();
        }
    }

    public String loadString(final String name) {
        if (mainPreferences.containsKey(name)) {
            return mainPreferences.getProperty(name);
        }
        Debug.e("RepRap preference: " + name + " not found in your preference file: " + getPropertiesPath());
        return null;
    }

    public int loadInt(final String name) {
        final String strVal = loadString(name);
        return Integer.parseInt(strVal);
    }

    public double loadDouble(final String name) {
        final String strVal = loadString(name);
        return Double.parseDouble(strVal);
    }

    public boolean loadBool(final String name) {
        final String strVal = loadString(name);
        if (strVal == null) {
            return false;
        }
        if (strVal.length() == 0) {
            return false;
        }
        if (strVal.compareToIgnoreCase("true") == 0) {
            return true;
        }
        return false;
    }

    public static boolean loadConfig(final String configName) {
        propsFile = configName;

        try {
            globalPrefs = new Preferences();
            return true;
        } catch (final IOException e) {
            return false;
        }

    }

    synchronized private static void initIfNeeded() throws IOException {
        if (globalPrefs == null) {
            globalPrefs = new Preferences();
        }
    }

    public static String loadGlobalString(final String name) throws IOException {
        initIfNeeded();
        return globalPrefs.loadString(name);
    }

    public static int loadGlobalInt(final String name) throws IOException {
        initIfNeeded();
        return globalPrefs.loadInt(name);
    }

    public static double loadGlobalDouble(final String name) throws IOException {
        initIfNeeded();
        return globalPrefs.loadDouble(name);
    }

    public static boolean loadGlobalBool(final String name) throws IOException {
        initIfNeeded();
        return globalPrefs.loadBool(name);
    }

    public static void saveGlobal() throws IOException {
        initIfNeeded();
        globalPrefs.save(false);
    }

    public static Preferences getGlobalPreferences() throws IOException {
        initIfNeeded();
        return globalPrefs;
    }

    public static String getDefaultPropsFile() {
        return propsFile;
    }

    public static void setGlobalString(final String name, final String value) throws IOException {
        initIfNeeded();
        globalPrefs.setString(name, value);
    }

    public static void setGlobalBool(final String name, final boolean value) throws IOException {
        initIfNeeded();
        globalPrefs.setString(name, value ? "true" : "false");
    }

    private void setString(final String name, final String value) {
        mainPreferences.setProperty(name, value);
    }

    /**
     * @return an array of all the names of all the materials in extruders
     */
    public static String[] allMaterials() throws IOException {
        final int extruderCount = globalPrefs.loadInt("NumberOfExtruders");
        final String[] result = new String[extruderCount];

        for (int i = 0; i < extruderCount; i++) {
            final String prefix = "Extruder" + i + "_";
            result[i] = globalPrefs.loadString(prefix + "MaterialType(name)");
        }

        return result;
    }

    public static String[] startsWith(final String prefix) throws IOException {
        initIfNeeded();
        final Enumeration<?> allOfThem = globalPrefs.mainPreferences.propertyNames();
        final List<String> r = new ArrayList<String>();

        while (allOfThem.hasMoreElements()) {
            final String next = (String) allOfThem.nextElement();
            if (next.startsWith(prefix)) {
                r.add(next);
            }
        }
        final String[] result = new String[r.size()];

        for (int i = 0; i < r.size(); i++) {
            result[i] = r.get(i);
        }

        return result;
    }

    public static String[] notStartsWith(final String prefix) throws IOException {
        initIfNeeded();
        final Enumeration<?> allOfThem = globalPrefs.mainPreferences.propertyNames();
        final List<String> r = new ArrayList<String>();

        while (allOfThem.hasMoreElements()) {
            final String next = (String) allOfThem.nextElement();
            if (!next.startsWith(prefix)) {
                r.add(next);
            }
        }

        final String[] result = new String[r.size()];

        for (int i = 0; i < r.size(); i++) {
            result[i] = r.get(i);
        }

        return result;
    }

    public static Appearance unselectedApp() {
        Color3f unselectedColour = null;
        try {
            unselectedColour = new Color3f((float) 0.3, (float) 0.3, (float) 0.3);
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
        final Appearance unselectedApp = new Appearance();
        unselectedApp.setMaterial(new Material(unselectedColour, black, unselectedColour, black, 0f));
        return unselectedApp;
    }
}
