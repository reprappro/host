/**
 * 
 */
package org.reprap.utilities;

import org.reprap.utilities.Timer;
import org.reprap.Preferences;

/**
 * @author Adrian
 *
 */
public class Debug {
	
	private boolean commsDebug = false;
	
	private boolean debug = false;
	
	static private Debug db = null;
	
	private String gCodeMessage = null;
	
	private Debug() {}
	
	public static void refreshPreferences()
	{
		if(db == null)
			db = new Debug();
		db.gCodeMessage = null;
		try {
			// Try to load debug setting from properties file
			db.debug = Preferences.loadGlobalBool("Debug");
		} catch (Exception ex) {
			// Fall back to non-debug mode if no setting is available
			db.debug = false;
		}
		
//		try {
//			// Try to load debug setting from properties file
//			db.commsDebug = Preferences.loadGlobalBool("CommsDebug");
//		} catch (Exception ex) {
			// Fall back to non-debug mode if no setting is available
			db.commsDebug = false;
//		}			
	}
	
	static public boolean d()
	{
		initialiseIfNeedBe();
		return db.debug;
	}
	
	static private void initialiseIfNeedBe()
	{
		if(db != null) return;
		refreshPreferences();	
	}
	
	static public void d(String s)
	{
		initialiseIfNeedBe();
		if(!db.debug) return;
		System.out.println("DEBUG: " + s + Timer.stamp());
		System.out.flush();
	}
	
	/**
	 * A real hard error...
	 * @param s
	 */
	static public void e(String s)
	{
		initialiseIfNeedBe();
		System.err.println("ERROR: " + s + Timer.stamp());
		System.err.flush();
		if(!db.debug) return;
		Exception e = new Exception();
		e.printStackTrace();
	}
	
	/**
	 * Just print a message anytime
	 * @param s
	 */
	static public void a(String s)
	{
		initialiseIfNeedBe();
		System.out.println("message: " + s + Timer.stamp());
		System.out.flush();
	}
	
	static public void c(String s)
	{
		initialiseIfNeedBe();
		if(!db.commsDebug) return;
		System.out.println("comms: " + s + Timer.stamp());
		System.out.flush();
	}
	
	static public void g(String s)
	{
		initialiseIfNeedBe();
		db.gCodeMessage = s;
	}
	
	static public String g()
	{
		initialiseIfNeedBe();
		String r = db.gCodeMessage;
		db.gCodeMessage = null;
		return r;
	}
}
