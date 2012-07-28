package org.reprap;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
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
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import javax.media.j3d.Appearance;
import javax.media.j3d.Material;
import javax.vecmath.Color3f;
import org.reprap.utilities.Debug;
import org.reprap.utilities.RepRapUtils;

/**
 * A single centralised repository of the user's current preference settings.  This also
 * implements (almost) a singleton for easy global access.  If there are no current
 * preferences the system-wide ones are copied to the user's space. 
 */
public class Preferences {
	
	private static String propsFile = "reprap.properties";
	private static final String propsFolder = ".reprap";
	private static final String MachineFile = "Machine";
	private static final String propsDirDist = "reprap-configurations";
	private static final String prologueFile = "prologue.gcode";
	private static final String epilogueFile = "epilogue.gcode";
	private static final String baseFile = "base.stl";
	private static final char activeFlag = '*';
	
	private static Preferences globalPrefs = null;
	private static String[] allMachines = null;
	
	//Properties fallbackPreferences;
	Properties mainPreferences;
	
	/*
	 * This section deals with internal (i.e. not RepRap machine, but this code or
	 * physics) precisions and accuracies
	 */
	
	// standard file names for the top and tail for G Code files
	//public static final String prologue = "prologue.gcode";
	//public static final String epilogue = "epilogue.gcode";
	
	private static final int grid = 100;             // Click outline polygons to a...
	public static int grid() { return grid; }
	
	private static final double gridRes = 1.0/grid;  // ...10 micron grid
	public static double gridRes() { return gridRes; }	
	
	private static final double lessGridSquare = gridRes*gridRes*0.01;  // Small squared size of a gridsquare
	public static double lessGridSquare() { return lessGridSquare; }	
	
	private static final double tiny = 1.0e-12;      // A small number
	public static double tiny() { return tiny; }	
	
	private static final double swell = 1.01;        // Quad tree swell factor
	public static double swell() { return swell; }	
	
	private static final double machineResolution = 0.1; // RepRap step size in mm
	public static double machineResolution() { return machineResolution; }
	
	private static final double absoluteZero = -273;
	public static double absoluteZero() { return absoluteZero; }
	
	private static final double inToMM = 25.4;
	public static double inchesToMillimetres() { return inToMM; }
	
	private static final Color3f black = new Color3f(0, 0, 0);
	
	private static boolean displaySimulation = false;
	public static boolean simulate() { return displaySimulation; }
	public static void setSimulate(boolean s) { displaySimulation = s;}
	
	private static boolean subtractive = false;
	public static boolean Subtractive() { return subtractive; }
	public static void setSubtractive(boolean s) { subtractive = s;}
	
	private static boolean gCodeUseSerial = false;
	public static boolean GCodeUseSerial() { return gCodeUseSerial; }
	public static void setGCodeUseSerial(boolean s) { gCodeUseSerial = s;}	
	
	private static String repRapMachine="GCodeRepRap";
	public static String RepRapMachine() { return repRapMachine; }
	public static void setRepRapMachine(String s) { repRapMachine = s; }
	
	// Main preferences constructor
	
	public Preferences() throws IOException 
	{
		// Construct location of user's properties file
		File mainDir = new File(getUsersRootDir());
		// If it's not there copy the system one
		if(!mainDir.exists())
			copySystemConfigurations(mainDir);
		 
		
		mainPreferences = new Properties();

		// Construct URL of user properties file
		String path = getPropertiesPath();
		File mainFile = new File(path);
		URL mainUrl = mainFile.toURI().toURL();
		
		if (mainFile.exists())
		{
			mainPreferences.load(mainUrl.openStream());
			comparePreferences();
		} else
		{
			Debug.e("Can't find your RepRap configurations: " + getPropertiesPath());
		}

	}
	
	/**
	 * Where are the system-wide master copies?
	 * @return
	 */
	public static String getSystemConfigurationDir()
	{
		URL sysConfig = ClassLoader.getSystemResource(propsDirDist);
		if (sysConfig == null)
			Debug.e("Can't find system RepRap configurations: " + propsDirDist);
		return sysConfig.getFile() + File.separator;
	}
	
	/**
	 * Copy the standard RepRap configurations to the user's space
	 * @param usersDir
	 */
	private static void copySystemConfigurations(File usersDir)
	{
		if(usersDir.exists())
			Debug.e("WARNING - copying system RepRap configurations to existing directory: " + usersDir.toString());
		String sysConfig = getSystemConfigurationDir();
		try
		{
			RepRapUtils.copyTree(new File(sysConfig), usersDir);
		} catch (Exception e)
		{
			Debug.e("Error copying RepRap configurations to user's directory: " + usersDir.toString());
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Where the user stores all their configuration files
	 * @return
	 */
	public static String getUsersRootDir()
	{
		return System.getProperty("user.home") + File.separatorChar + propsFolder + File.separatorChar;
	}
	
	/**
	 * The master file that lists the user's machines and flags the active one
	 * with a * character at the start of its name.
	 * @return
	 */
	public static String getMachineFilePath()
	{
		return getUsersRootDir() + MachineFile;
	}
	
	/**
	 * List of all the available RepRap machine types
	 * @return
	 */
	private static String[] getAllMachines()
	{
		File mf = new File(getMachineFilePath());
		String [] result = null;
		try 
		{
			result = new String[RepRapUtils.countLines(mf)];
			FileInputStream fstream = new FileInputStream(mf);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			int i = 0;
			String s;
			while ((s = br.readLine()) != null)
			{
				result[i] = s;
				i++;  
			}
			in.close();
		} catch (IOException e) 
		{
			Debug.e("Can't read configuration file: " + mf.toString());
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * The path to the directory containing the user's active machine configuration
	 * @return
	 */
	public static String getActiveMachineName()
	{
		if(allMachines == null)
			allMachines = getAllMachines();
		for (String machine : allMachines)
		{
			if(machine.charAt(0) == activeFlag)
				return machine.substring(1, machine.length());
		}
		Debug.e("No active RepRap set (add " + activeFlag + " to the start of a line in the file: " + getMachineFilePath() + ").");
		return "";
	}
	
	/**
	 * The directory containing all the user's configuration files for their active machine
	 * @return
	 */
	public static String getActiveMachineDir()
	{
		return getUsersRootDir() + getActiveMachineName() + File.separatorChar;
	}
	
	/**
	 * Set the active machine to the one named
	 * @param newActiveMachine
	 */
	public static void setActiveMachine(String newActiveMachine)
	{
		if(allMachines == null)
			allMachines = getAllMachines();
		try
		{
			FileWriter outFile = new FileWriter(getMachineFilePath());
			PrintWriter out = new PrintWriter(outFile);
			for(int i = 0; i < allMachines.length; i++)
			{
				if(allMachines[i].charAt(0) == activeFlag)
					allMachines[i] = allMachines[i].substring(1, allMachines[i].length());
				if(allMachines[i].contentEquals(newActiveMachine))
					allMachines[i] = activeFlag + newActiveMachine;
				out.println(allMachines[i]);
				i++;
			}
			outFile.close();
		} catch (Exception e)
		{
			Debug.e("Can't write to file: " + getMachineFilePath());
		}
	}
	
	/**
	 * Where the user's properties file is
	 * @return
	 */
	public static String getPropertiesPath()
	{
		return getActiveMachineDir() + propsFile;
	}
	
	/**
	 * Where the system version of the user's properties file is
	 * @return
	 */
	public static String getSystemPropertiesDir()
	{
		return getSystemConfigurationDir() + getActiveMachineName() + File.separator;
	}
	
	/**
	 * Where the user's build-base STL file is
	 * @return
	 */
	public static String getBasePath()
	{
		return getActiveMachineDir() + baseFile;
	}
	
	/**
	 * Where the user's GCode prologue file is
	 * @return
	 */
	public static String getProloguePath()
	{
		return getActiveMachineDir() + prologueFile;
	}
	
	/**
	 * Where the user's GCode epilogue file is
	 * @return
	 */
	public static String getEpiloguePath()
	{
		return getActiveMachineDir() + epilogueFile;
	}
	
	/**
	 * Compare the user's preferences with the distribution one and report any
	 * different names.
	 */
	private void comparePreferences()
	{
		Enumeration<?> usersLot = mainPreferences.propertyNames();
		
		String sysProps = getSystemPropertiesDir() + propsFile;
		File sysFile = new File(sysProps);
		URL sysUrl;
		try 
		{
			sysUrl = sysFile.toURI().toURL();
		} catch (MalformedURLException e) 
		{
			Debug.e("System preferences location wrong: " + sysProps);
			e.printStackTrace();
			return;
		}
		Properties sysPreferences = new Properties();
		if (sysFile.exists())
		{
			try {
				sysPreferences.load(sysUrl.openStream());
			} catch (IOException e) 
			{
				Debug.e("System preferences input error: " + sysProps);
				e.printStackTrace();
			}
		} else
		{
			Debug.e("Can't find your System's RepRap configurations: " + sysProps);
		}
	
		Enumeration<?> distLot = sysPreferences.propertyNames();
		
		String result = "";
		int count = 0;
		boolean noDifference = true;
		
		while(usersLot.hasMoreElements())
		{
			String next = (String)usersLot.nextElement();
			if (!sysPreferences.containsKey(next))
			{
				result += " " + next + "\n";
				count++;
			}
		}
		if(count > 0)
		{
			result = "Your preferences file contains:\n" + result + "which ";
			if(count > 1)
				result += "are";
			else
				result += "is";
			result += " not in the distribution preferences file.";
			Debug.d(result);
			noDifference = false;
		}
		
		result = "";
		count = 0;
		while(distLot.hasMoreElements())
		{
			String next = (String)distLot.nextElement();
			if (!mainPreferences.containsKey(next))
			{
				result += " " + next + "\n";
				count++;
			}
		}
		
		if(count > 0)
		{
			result =  "The distribution preferences file contains:\n" + result + "which ";
			if(count > 1)
				result += "are";
			else
				result += "is";
			result += " not in your preferences file.";
			Debug.d(result);
			noDifference = false;
		}
		
		if(noDifference)
			Debug.d("The distribution preferences file and yours match.  This is good.");
	}
	
	

	public void save(boolean startUp) throws FileNotFoundException, IOException 
	{
		String savePath = getPropertiesPath();
		File f = new File(savePath);
		if (!f.exists()) 
			f.createNewFile();
		
		OutputStream output = new FileOutputStream(f);
		mainPreferences.store(output, "RepRap machine parameters. See http://reprap.org/wiki/Java_Software_Preferences_File");

		if(!startUp)
			org.reprap.Main.gui.getPrinter().refreshPreferences();
	}
		
	public String loadString(String name) {
		if (mainPreferences.containsKey(name))
			return mainPreferences.getProperty(name);
		Debug.e("RepRap preference: " + name + " not found in your preference file: " + getPropertiesPath());
		return null;
	}
	
	public int loadInt(String name) {
		String strVal = loadString(name);
		return Integer.parseInt(strVal);
	}
	
	public double loadDouble(String name) {
		String strVal = loadString(name);
		return Double.parseDouble(strVal);
	}
	
	public boolean loadBool(String name) {
		String strVal = loadString(name);
		if (strVal == null) return false;
		if (strVal.length() == 0) return false;
		if (strVal.compareToIgnoreCase("true") == 0) return true;
		return false;
	}

	public static boolean loadConfig(String configName)
	{
		propsFile = configName;
	
		try
		{
			globalPrefs = new Preferences();
			return true;
		}
		catch(IOException e)
		{
			return false;
		}
		
	}
	synchronized private static void initIfNeeded() throws IOException {
		if (globalPrefs == null)
			globalPrefs = new Preferences();
	}

	public static String loadGlobalString(String name) throws IOException {
		initIfNeeded();
		return globalPrefs.loadString(name);
	}

	public static int loadGlobalInt(String name) throws IOException {
		initIfNeeded();
		return globalPrefs.loadInt(name);
	}
	
	public static double loadGlobalDouble(String name) throws IOException {
		initIfNeeded();
		return globalPrefs.loadDouble(name);
	}
	
	public static boolean loadGlobalBool(String name) throws IOException {
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
	
	public static String getDefaultPropsFile()
	{
		return propsFile;
	}
	/**
	 * Set a new value
	 * @param name
	 * @param value
	 * @throws IOException
	 */
	public static void setGlobalString(String name, String value) throws IOException {
		initIfNeeded();

		//Debug.e("Setting global " + name + ":" + value);
		
		globalPrefs.setString(name, value);
	}

	public static void setGlobalBool(String name, boolean value) throws IOException {
		initIfNeeded();
		globalPrefs.setString(name, value ? "true" : "false");
	}

	/**
	 * @param name
	 * @param value
	 */
	private void setString(String name, String value) {
		
		//Debug.e("Setting " + name + ":" + value);
		
		mainPreferences.setProperty(name, value);
	}
	
	/**
	 * @return an array of all the names of all the materials in extruders
	 * @throws IOException
	 */
	public static String[] allMaterials() throws IOException
	{
		int extruderCount = globalPrefs.loadInt("NumberOfExtruders");
		String[] result = new String[extruderCount];
		
		for(int i = 0; i < extruderCount; i++)
		{
			String prefix = "Extruder" + i + "_";
			result[i] = globalPrefs.loadString(prefix + "MaterialType(name)");	
		}
		
		return result;
	}
	
	public static String[] startsWith(String prefix) throws IOException 
	{
		initIfNeeded();
		Enumeration<?> allOfThem = globalPrefs.mainPreferences.propertyNames();
		List<String> r = new ArrayList<String>();
		
		while(allOfThem.hasMoreElements())
		{
			String next = (String)allOfThem.nextElement();
			if(next.startsWith(prefix))
				r.add(next);
		}
		String[] result = new String[r.size()];
		
		for(int i = 0; i < r.size(); i++)
			result[i] = (String)r.get(i);
		
		return result;		
	}
	
	public static String[] notStartsWith(String prefix) throws IOException 
	{
		initIfNeeded();
		Enumeration<?> allOfThem = globalPrefs.mainPreferences.propertyNames();
		List<String> r = new ArrayList<String>();
		
		while(allOfThem.hasMoreElements())
		{
			String next = (String)allOfThem.nextElement();
			if(!next.startsWith(prefix))
				r.add(next);
		}
		
		String[] result = new String[r.size()];
		
		for(int i = 0; i < r.size(); i++)
			result[i] = (String)r.get(i);
		
		return result;
	}
	
	public static Appearance unselectedApp()
	{
		Color3f unselectedColour = null;
		try
		{
			unselectedColour = new Color3f((float)0.3, (float)0.3, (float)0.3);
		} catch (Exception ex)
		{
			ex.printStackTrace();
		}
		Appearance unselectedApp = new Appearance();
		unselectedApp.setMaterial(new 
				Material(unselectedColour, black, unselectedColour, black, 0f));
		return unselectedApp;
	}
	
}
