
package org.reprap.geometry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintStream;

import org.reprap.Printer;
import org.reprap.Extruder;
import org.reprap.geometry.polygons.HalfSpace2D;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.Preferences;
import org.reprap.utilities.Debug;

/**
 * This stores a set of facts about the layer currently being made, and the
 * rules for such things as infill patterns, support patterns etc.
 */
public class LayerRules 
{
	/**
	 * The coordinates of the first point plotted in a layer
	 */
	private Point2D[] firstPoint;
	
	/**
	 * The extruder first used in a layer
	 */	
	private int[] firstExtruder;
	
	/**
	 * The coordinates of the last point plotted in a layer
	 */
	private Point2D[] lastPoint;
	
	/**
	 * The extruder last used in a layer
	 */	
	private int[] lastExtruder;
	
	/**
	 * The heights of the layers
	 */
	private double[] layerZ;
	
	/**
	 * The name of the first file of output
	 */
	private String prologueFileName;
	
	/**
	 * The name of the last file of output
	 */
	private String epilogueFileName;
	
	/**
	 * The names of all the files for all the layers
	 */
	private String [] layerFileNames;
	
	/**
	 * 
	 */
	private boolean reversing;
	
	/**
	 * Flag to remember if we have reversed the layer ot=rder in the output file
	 */
	private boolean alreadyReversed;
	
	/**
	 * The machine
	 */
	private Printer printer;
	
	/**
	 * How far up the model we are in mm
	 */
	private double modelZ;
	
	/**
	 * How far we are up from machine Z=0
	 */
	private double machineZ;
	
	/**
	 * The count of layers up the model
	 */
	private int modelLayer;
	
	/**
	 * The number of layers the machine has done
	 */
	private int machineLayer;
	
	/**
	 * The top of the model in model coordinates
	 */
	private double modelZMax;
	
	/**
	 * The highest the machine should go this build
	 */
	private double machineZMax;
	
	/**
	 * The number of the last model layer (first = 0)
	 */
	private int modelLayerMax;
	
	/**
	 * The number of the last machine layer (first = 0)
	 */
	private int machineLayerMax;
	
	/**
	 * Putting down foundations?
	 */
	private boolean layingSupport;
	
	/**
	 * The step height of all the extruders
	 */
	private double zStep;
	
	/**
	 * If we take a short step, remember it and add it on next time
	 */
	private double addToStep = 0;
	
	/**
	 * Are we going top to bottom or ground up?
	 */
	private boolean topDown = false;
	
	/**
	 * This is true until it is first read, when it becomes false
	 */
	private boolean notStartedYet;
	
	/**
	 * layers above and below where we are for infill and support calculations
	 */
	//private RrCSGPolygonList [] layerRecord;
	
	/**
	 * The machineLayer value for each entry in layerRecord
	 */
	private int [] recordNumber;
	
	/**
	 * index into layerRecord
	 */
	private int layerPointer;
	
	/**
	 * The XY rectangle that bounds the build
	 */
	private Rectangle bBox;
	
	/**
	 * 
	 * @param p
	 * @param modZMax
	 * @param macZMax
	 * @param modLMax
	 * @param macLMax
	 * @param found
	 */
	public LayerRules(Printer p, double modZMax, double macZMax,
			int modLMax, int macLMax, boolean found, Rectangle bb)
	{
		printer = p;
		reversing = false;
		alreadyReversed = false;
		bBox = new Rectangle(bb);	
		notStartedYet = true;

		topDown = printer.getTopDown();

		modelZMax = modZMax;
		machineZMax = macZMax;
		modelLayerMax = modLMax;
		machineLayerMax = macLMax;
		if(topDown)
		{
			modelZ = modelZMax;
			machineZ = machineZMax;
			modelLayer = modelLayerMax;
			machineLayer = machineLayerMax;			
		} else
		{
			modelZ = 0;
			machineZ = 0;
			modelLayer = -1;
			machineLayer = 0;			
		}
		addToStep = 0;
		
		firstPoint = new Point2D[machineLayerMax+1];
		firstExtruder = new int[machineLayerMax+1];
		lastPoint = new Point2D[machineLayerMax+1];
		lastExtruder = new int[machineLayerMax+1];
		layerZ = new double[machineLayerMax+1];
		layerFileNames = new String[machineLayerMax+1];
		for(int i = 0; i < machineLayerMax+1; i++)
			layerFileNames[i] = null;
		prologueFileName = null;
		epilogueFileName = null;

		layingSupport = found;
		Extruder[] es = printer.getExtruders();
		zStep = es[0].getExtrusionHeight();
		int fineLayers = es[0].getLowerFineLayers();
		if(es.length > 1)
		{
			for(int i = 1; i < es.length; i++)
			{
				if(es[i].getLowerFineLayers() > fineLayers)
					fineLayers = es[i].getLowerFineLayers();
				if(Math.abs(es[i].getExtrusionHeight() - zStep) > Preferences.tiny())
					Debug.e("Not all extruders extrude the same height of filament: " + 
							zStep + " and " + es[i].getExtrusionHeight());
			}
		}
		
		recordNumber = new int[fineLayers+1];
		layerPointer = 0;
	}
	
	public Rectangle getBox()
	{
		return new Rectangle(bBox); // Something horrible happens to return by reference here; hence copy...
	}
	
	public boolean getTopDown() { return topDown; }
	
	public void setPrinter(Printer p) { printer = p; }
	public Printer getPrinter() { return printer; }
	
	public double getModelZ() { return modelZ; }
	
	public boolean getReversing() { return reversing; }
	
	public double getModelZ(int layer) 
	{
		return zStep*layer; 
	}
	
	public double getMachineZ() { return machineZ; }
	
	public int getModelLayer() { return modelLayer; }
	
	public void setFirstAndLast(PolygonList[] pl)
	{
		firstPoint[machineLayer] = null;
		lastPoint[machineLayer] = null;
		firstExtruder[machineLayer] = -1;
		lastExtruder[machineLayer] = -1;
		layerZ[machineLayer] = machineZ;
		if(pl == null)
			return;
		if(pl.length <= 0)
			return;
		int bottom = -1;
		int top = -1;
		for(int i = 0; i < pl.length; i++)
		{
			if(pl[i] != null)
				if(pl[i].size() > 0)
				{
					if(bottom < 0)
						bottom = i;
					top = i;
				}
		}
		if(bottom < 0)
			return;
		firstPoint[machineLayer] = pl[bottom].polygon(0).point(0);
		firstExtruder[machineLayer] = pl[bottom].polygon(0).getAttributes().getExtruder().getID();
		lastPoint[machineLayer] = pl[top].polygon(pl[top].size()-1).point(pl[top].polygon(pl[top].size()-1).size() - 1);
		lastExtruder[machineLayer] = pl[top].polygon(pl[top].size()-1).getAttributes().getExtruder().getID();
	}
	
	public int realTopLayer()
	{
		int rtl = machineLayerMax;
		while(firstPoint[rtl] == null && rtl > 0)
		{
			String s = "LayerRules.realTopLayer(): layer " + rtl + " from " + machineLayerMax + " is empty!";
			if(machineLayerMax - rtl > 1)
				Debug.e(s);
			else
				Debug.d(s);
			rtl--;
		}
		return rtl;
	}
	
	public String getPrologueFileName() { return prologueFileName; }
	public String getEpilogueFileName() { return epilogueFileName; }
	public void setPrologueFileName(String s) { prologueFileName = s; }
	public void setEpilogueFileName(String s) { epilogueFileName = s; }
	
	public String getLayerFileName(int layer) { return layerFileNames[layer]; }
	public String getLayerFileName() { return layerFileNames[machineLayer]; }
	public void setLayerFileName(String s) { layerFileNames[machineLayer] = s; }
	
	public Point2D getFirstPoint(int layer)
	{
		return firstPoint[layer];
	}
	
	public Point2D getLastPoint(int layer)
	{
		return lastPoint[layer];
	}
	
	public int getFirstExtruder(int layer)
	{
		return firstExtruder[layer];
	}
	
	public int getLastExtruder(int layer)
	{
		return lastExtruder[layer];
	}
	
	public double getLayerZ(int layer)
	{
		return layerZ[layer];
	}
	
	public int getModelLayerMax() { return modelLayerMax; }
	
	public int getMachineLayerMax() { return machineLayerMax; }
	
	public int getMachineLayer() { return machineLayer; }
	
	public int getFoundationLayers() { return machineLayerMax - modelLayerMax; }
	
	public double getModelZMAx() { return modelZMax; }
	
	public double getMachineZMAx() { return machineZMax; }
	
	public double getZStep() { return zStep; }
	
	public boolean notStartedYet()
	{
		if(notStartedYet)
		{
			notStartedYet = false;
			return true;
		}
		return false;
	}
	
	
	public void setLayingSupport(boolean lf) { layingSupport = lf; }
	public boolean getLayingSupport() { return layingSupport; }
	
	/**
	 * Does the layer about to be produced need to be recomputed?
	 * @return
	 */
	public boolean recomputeLayer()
	{
		return getFoundationLayers() - getMachineLayer() <= 2;
	}
	
	/**
	 * The hatch pattern is:
	 * 
	 *  Foundation:
	 *   X and Y rectangle
	 *   
	 *  Model:
	 *   Alternate even then odd (which can be set to the same angle if wanted).
	 *   
	 * @return
	 */
	public HalfSpace2D getHatchDirection(Extruder e) 
	{
		double angle;
		
		if(getMachineLayer() < getFoundationLayers())
		{
//			if(getMachineLayer() == getFoundationLayers() - 2)
//				angle = e.getEvenHatchDirection();
//			else
				angle = e.getOddHatchDirection();
		} else
		{
			if(getModelLayer()%2 == 0)
				angle = e.getEvenHatchDirection();
			else
				angle = e.getOddHatchDirection();
		}
		angle = angle*Math.PI/180;
		return new HalfSpace2D(new Point2D(0.0, 0.0), new Point2D(Math.sin(angle), Math.cos(angle)));
	}
	
	/**
	 * The gap in the layer zig-zag is:
	 * 
	 *  Foundation:
	 *   The foundation width for all but...
	 *   ...the penultimate foundation layer, which is half that and..
	 *   ...the last foundation layer, which is the model fill width
	 *   
	 *  Model:
	 *   The model fill width
	 *   
	 * @param e
	 * @return
	 */
	public double getHatchWidth(Extruder e)
	{
		if(getMachineLayer() < getFoundationLayers())
			return e.getExtrusionFoundationWidth();
		
		return e.getExtrusionInfillWidth();
	}
	
	/**
	 * Move the machine up/down, but leave the model's layer where it is.
	 *
	 * @param e
	 */
	public void stepMachine(Extruder e)
	{
		double sZ = e.getExtrusionHeight();
		int ld;
		
		if(topDown)
		{
			//machineZ -= (sZ + addToStep);
			machineLayer--;
			machineZ = sZ*machineLayer + addToStep;
			ld = getFoundationLayers() - getMachineLayer();
			if(ld == 2)
				addToStep = sZ*(1 - e.getSeparationFraction());
			else if(ld == 1)
				addToStep = -sZ*(1 - e.getSeparationFraction());
			else
				addToStep = 0;
		} else
		{
			//machineZ += (sZ + addToStep);
			machineLayer++;
			machineZ = sZ*machineLayer + addToStep;
			ld = getFoundationLayers() - getMachineLayer();
			if(ld == 2)
				addToStep = -sZ*(1 - e.getSeparationFraction());
			else if(ld == 1)
				addToStep = sZ*(1 - e.getSeparationFraction());
			else
				addToStep = 0;
		}
	}
	
	public void moveZAtStartOfLayer(boolean really)
	{
		double z = getMachineZ();

		if(topDown)
		{
			printer.setZ(z - (zStep + addToStep));
		}
		printer.singleMove(printer.getX(), printer.getY(), z, printer.getFastFeedrateZ(), really);
	}
	
	/**
	 * Move both the model and the machine up/down a layer
	 * @param e
	 */
	public void step(Extruder e)
	{		
		double sZ = e.getExtrusionHeight();
		if(topDown)
		{
			//modelZ -= (sZ + addToStep);
			modelLayer--;
		} else
		{
			//modelZ += (sZ + addToStep);
			modelLayer++;
		}
		modelZ = modelLayer*sZ + addToStep;
		addToStep = 0;
		stepMachine(e);
	}
	
	public void setFractionDone()
	{
		// Set -ve to force the system to query the layer rules
		
		org.reprap.gui.botConsole.BotConsoleFrame.getBotConsoleFrame().setFractionDone(-1, -1, -1);
	}
	
	private void copyFile(PrintStream ps, String ip)
	{
		File f = null;
		try 
		{
			f = new File(ip);
			FileReader fr = new FileReader(f);
			int character;
			while ((character = fr.read()) >= 0)
			{
				ps.print((char)character);
				//System.out.print((char)character);
			}
			ps.flush();
			fr.close();
		} catch (Exception e) 
		{  
			Debug.e("Error copying file: " + e.toString());
			e.printStackTrace();
		}
	}
	
	public void reverseLayers()
	{
		//if(opFileArray == null || alreadyReversed)
		//if(getPrologueFileName() == null)
		//	return;
		
		// Stop this being called twice...
		if(alreadyReversed)
		{
			Debug.d("LayerRules.reverseLayers(): called twice.");
			return;
		}
		alreadyReversed = true;
		reversing = true;
		
		String fileName = getPrinter().getOutputFilename();
		
		PrintStream fileOutStream = null;
		try
		{
			FileOutputStream fileStream = new FileOutputStream(fileName);
			fileOutStream = new PrintStream(fileStream);
		} catch (Exception e)
		{
			Debug.e("Can't write to file " + fileName);
			return;
		}
		
		getPrinter().forceOutputFile(fileOutStream);
		//copyFile(fileOutStream,getPrologueFileName());

		try
		{
			getPrinter().startRun(this);
			int top = realTopLayer();
			for(machineLayer = 0; machineLayer <= top; machineLayer++)
			{
				machineZ = layerZ[machineLayer];
				getPrinter().startingLayer(this);
				getPrinter().singleMove(getFirstPoint(machineLayer).x(), getFirstPoint(machineLayer).y(), machineZ, getPrinter().getFastXYFeedrate(), true);
				copyFile(fileOutStream, getLayerFileName(machineLayer));
				//System.out.println("Layer: " + machineLayer + " z: " + machineZ +
				//		" first point: " + getFirstPoint(machineLayer) + " last point: " + getLastPoint(machineLayer)
				//		+ " " + getLayerFileName(machineLayer));
				getPrinter().singleMove(getLastPoint(machineLayer).x(), getLastPoint(machineLayer).y(), machineZ, getPrinter().getSlowXYFeedrate(), false);
				getPrinter().finishedLayer(this);
				getPrinter().betweenLayers(this);
			}
			getPrinter().terminate(this);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		fileOutStream.close();
		reversing = false;
		//copyFile(fileOutStream, getEpilogueFileName());

		//System.out.println("layerRules.reverseLayers(): exception at layer: " + i + " " + e.toString());

	}

	
}
