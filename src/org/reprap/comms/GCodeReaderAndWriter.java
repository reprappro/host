package org.reprap.comms;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.Date;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import org.reprap.Preferences;
import org.reprap.geometry.LayerRules;
import org.reprap.utilities.Debug;
import org.reprap.utilities.ExtensionFileFilter;
import org.reprap.utilities.RrGraphics;

public class GCodeReaderAndWriter {
    // Codes for responses from the machine
    // A positive number returned is a request for that line number
    // to be resent.
    private static final long shutDown = -3;
    private static final long allSentOK = -1;
    private double eTemp;
    private double bTemp;
    private String[] sdFiles = new String[0];
    private double xValue, yValue, zValue, eValue;
    private RrGraphics simulationPlot = null;
    private String lastResp;
    /**
     * Stop sending a file (if we are).
     */
    private boolean paused = false;
    private boolean iAmPaused = false;

    /**
     * The name of the port talking to the RepRap machine
     */
    String portName;

    /**
     * Flag to tell it we've finished
     */
    private boolean exhaustBuffer = false;

    private final PrintStream serialOutStream = null;
    private final InputStream serialInStream = null;

    /**
     * The root file name for output (without ".gcode" on the end)
     */
    private String opFileName;

    private String layerFileNames;

    private static final String GCODE_EXTENSION = ".gcode";

    /**
     * How does the first file name in a multiple set end?
     */
    private static final String FIRST_ENDING = "_prologue";

    /**
     * Flag for temporary files
     */
    private static final String TMP_STRING = "_TeMpOrArY_";

    private BufferedReader fileInStream = null;
    private long fileInStreamLength = -1;
    private PrintStream fileOutStream = null;

    private long lineNumber;

    /**
     * The ring buffer that stores the commands for possible resend requests.
     */
    private int head;
    private static final int buflen = 10; // No too long, or pause doesn't work well
    private String[] ringBuffer;
    private long[] ringLines;

    /**
     * The transmission to the RepRap machine is handled by a separate thread.
     * These control that.
     */
    private Thread bufferThread = null;
    private int myPriority;

    public GCodeReaderAndWriter() {
        init();
    }

    /**
     * constructor for when we definitely want to send GCodes to a known file
     */
    public GCodeReaderAndWriter(final PrintStream fos) {
        init();
        fileOutStream = fos;
    }

    private void init() {
        resetReceived();
        paused = false;
        iAmPaused = false;
        ringBuffer = new String[buflen];
        ringLines = new long[buflen];
        head = 0;
        lineNumber = 0;
        exhaustBuffer = false;
        lastResp = "";
        portName = "/dev/ttyUSB0";

        openSerialConnection(portName);

        myPriority = Thread.currentThread().getPriority();

        bufferThread = null;
    }

    private void nonRunningWarning(final String s) {
    }

    public boolean buildingFromFile() {
        return fileInStream != null;
    }

    public boolean savingToFile() {
        return fileOutStream != null;
    }

    /**
     * Stop the printer building. This _shouldn't_ also stop it being controlled
     * interactively.
     */
    public void pause() {
        paused = true;
    }

    /**
     * Resume building.
     */
    public void resume() {
        paused = false;
    }

    public boolean iAmPaused() {
        return iAmPaused;
    }

    /**
     * Send a GCode file to the machine if that's what we have to do, and return
     * true. Otherwise return false.
     * 
     */
    public Thread filePlay() {
        if (fileInStream == null) {
            return null;
        }
        simulationPlot = null;
        if (Preferences.simulate()) {
            simulationPlot = new RrGraphics("RepRap building simulation");
        }

        // Launch a thread to run through the file, so we can return control
        // to the user
        final Thread playFile = new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("GCode file printer");
                String line;
                long bytes = 0;
                double fractionDone = 0;
                try {
                    while ((line = fileInStream.readLine()) != null) {
                        bufferQueue(line);
                        bytes += line.length();
                        fractionDone = (double) bytes / (double) fileInStreamLength;
                        setFractionDone(fractionDone, -1, -1);
                        while (paused) {
                            iAmPaused = true;
                            sleep(239);
                        }
                        iAmPaused = false;
                    }
                    fileInStream.close();
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        playFile.start();
        return playFile;
    }

    public void setFractionDone(final double fractionDone, final int layer, final int outOf) {
        org.reprap.gui.botConsole.SlicerFrame.getBotConsoleFrame().setFractionDone(fractionDone, layer, outOf);
    }

    /**
     * Between layers nothing will be queued. Use the next two functions to slow
     * and speed the buffer's spinning.
     * 
     */
    public void slowBufferThread() {
        if (bufferThread != null) {
            bufferThread.setPriority(1);
        }
    }

    public void speedBufferThread() {
        if (bufferThread != null) {
            bufferThread.setPriority(myPriority);
        }
    }

    /**
     * Force the output stream - use with caution
     */
    public void forceOutputFile(final PrintStream fos) {
        fileOutStream = fos;
    }

    /**
     * Compute the checksum of a GCode string.
     * 
     * @param cmd
     * @return
     */
    private String checkSum(final String cmd) {
        int cs = 0;
        for (int i = 0; i < cmd.length(); i++) {
            cs = cs ^ cmd.charAt(i);
        }
        cs &= 0xff;
        return "*" + cs;
    }

    private void ringAdd(final long ln, final String cmd) {
        head++;
        if (head >= ringBuffer.length) {
            head = 0;
        }
        ringBuffer[head] = cmd;
        ringLines[head] = ln;
    }

    private String ringGet(final long ln) {
        int h = head;
        do {
            if (ringLines[h] == ln) {
                return ringBuffer[h];
            }
            h--;
            if (h < 0) {
                h = ringBuffer.length - 1;
            }
        } while (h != head);
        Debug.e("ringGet: line " + ln + " not stored");
        return "";
    }

    /**
     * Send a command to the machine. Return true if a response is expected;
     * false if not.
     * 
     * @param cmd
     * @return
     */
    private boolean sendLine(String cmd) {
        final int com = cmd.indexOf(';');
        if (com > 0) {
            cmd = cmd.substring(0, com);
        }
        if (com != 0) {
            cmd = cmd.trim();
            if (cmd.length() > 0) {
                ringAdd(lineNumber, cmd);
                cmd = "N" + lineNumber + " " + cmd + " ";
                cmd += checkSum(cmd);
                serialOutStream.print(cmd + "\n");
                serialOutStream.flush();
                Debug.c("G-code: " + cmd + " dequeued and sent");
                return true;
            }
            return false;
        }

        Debug.c("G-code: " + cmd + " not sent");
        if (cmd.startsWith(";#!LAYER:")) {
            final int l = Integer.parseInt(cmd.substring(cmd.indexOf(" ") + 1, cmd.indexOf("/")));
            final int o = Integer.parseInt(cmd.substring(cmd.indexOf("/") + 1));
            setFractionDone(-1, l, o + 1);
        }
        return false;
    }

    /**
     * Queue a command.
     * 
     * @throws IOException
     */
    private void bufferQueue(final String cmd) throws IOException {
        if (simulationPlot != null) {
            simulationPlot.add(cmd);
        }

        if (serialOutStream == null) {
            nonRunningWarning("queue: \"" + cmd + "\" to");
            return;
        }

        if (sendLine(cmd)) {
            long resp = waitForResponse();
            if (resp == shutDown) {
                throw new RuntimeException("The RepRap machine has flagged a hard error!");
            } else if (resp == allSentOK) {
                lineNumber++;
                return;
            } else {
                final long gotTo = lineNumber;
                lineNumber = resp;
                String rCmd = " ";
                while (lineNumber <= gotTo && !rCmd.contentEquals("")) {
                    rCmd = ringGet(lineNumber);
                    if (sendLine(rCmd)) {
                        resp = waitForResponse();
                        if (resp == allSentOK) {
                            return;
                        }
                        if (resp == shutDown) {
                            throw new RuntimeException("The RepRap machine has flagged a hard error!");
                        }
                    }
                }
            }
        }
        Debug.d("bufferQueue(): did not send " + cmd);
    }

    private void resetReceived() {
        eTemp = Double.NEGATIVE_INFINITY;
        bTemp = Double.NEGATIVE_INFINITY;
        xValue = Double.NEGATIVE_INFINITY;
        yValue = Double.NEGATIVE_INFINITY;
        zValue = Double.NEGATIVE_INFINITY;
        eValue = Double.NEGATIVE_INFINITY;
    }

    /**
     * Pick out a value from the returned string
     * 
     * @param s
     * @param coord
     * @return
     */
    private double parseReturnedValue(final String s, final String cue) {
        int i = s.indexOf(cue);
        if (i < 0) {
            return Double.NEGATIVE_INFINITY;
        }
        i += cue.length();
        final String ss = s.substring(i);
        final int j = ss.indexOf(" ");
        if (j < 0) {
            return Double.parseDouble(ss);
        } else {
            return Double.parseDouble(ss.substring(0, j));
        }
    }

    /**
     * Pick up a list of comma-separated names and transliterate them to
     * lower-case This code allows file names to have spaces in, but this is
     * ***highly*** depricated.
     * 
     * @param s
     * @param cue
     * @return
     */
    private String[] parseReturnedNames(final String s, final String cue) {
        int i = s.indexOf(cue);
        if (i < 0) {
            return new String[0];
        }
        i += cue.length();
        String ss = s.substring(i);
        final int j = ss.indexOf(",}");
        if (j < 0) {
            ss = ss.substring(0, ss.indexOf("}"));
        } else {
            ss = ss.substring(0, j);
        }
        final String[] result = ss.split(",");
        for (i = 0; i < result.length; i++) {
            result[i] = result[i].toLowerCase();
        }
        return result;
    }

    /**
     * If the machine has just returned an extruder temperature, return its
     * value
     * 
     * @return
     */
    public double getETemp() {
        if (serialOutStream == null) {
            nonRunningWarning("getETemp() from ");
            return 0;
        }
        if (eTemp == Double.NEGATIVE_INFINITY) {
            Debug.d("GCodeReaderAndWriter.getETemp() - no value stored!");
            return 0;
        }
        return eTemp;
    }

    /**
     * If the machine has just returned a bed temperature, return its value
     * 
     * @return
     */
    public double getBTemp() {
        if (serialOutStream == null) {
            nonRunningWarning("getBTemp() from ");
            return 0;
        }
        if (bTemp == Double.NEGATIVE_INFINITY) {
            Debug.d("GCodeReaderAndWriter.getBTemp() - no value stored!");
            return 0;
        }
        return bTemp;
    }

    public String[] getSDFileNames() {
        if (serialOutStream == null) {
            nonRunningWarning("getSDFileNames() from ");
            return sdFiles;
        }
        if (sdFiles.length <= 0) {
            Debug.e("GCodeReaderAndWriter.getSDFileNames() - no value stored!");
        }
        return sdFiles;
    }

    /**
     * If the machine has just returned an x coordinate, return its value
     * 
     * @return
     */
    public double getX() {
        if (serialOutStream == null) {
            nonRunningWarning("getX() from ");
            return 0;
        }
        if (xValue == Double.NEGATIVE_INFINITY) {
            Debug.e("GCodeReaderAndWriter.getX() - no value stored!");
            return 0;
        }
        return xValue;
    }

    /**
     * If the machine has just returned a y coordinate, return its value
     * 
     * @return
     */
    public double getY() {
        if (serialOutStream == null) {
            nonRunningWarning("getY() from ");
            return 0;
        }
        if (yValue == Double.NEGATIVE_INFINITY) {
            Debug.e("GCodeReaderAndWriter.getY() - no value stored!");
            return 0;
        }
        return yValue;
    }

    /**
     * If the machine has just returned a z coordinate, return its value
     * 
     * @return
     */
    public double getZ() {
        if (serialOutStream == null) {
            nonRunningWarning("getZ() from ");
            return 0;
        }
        if (zValue == Double.NEGATIVE_INFINITY) {
            Debug.e("GCodeReaderAndWriter.getZ() - no value stored!");
            return 0;
        }
        return zValue;
    }

    /**
     * If the machine has just returned an e coordinate, return its value
     * 
     * @return
     */
    public double getE() {
        if (serialOutStream == null) {
            nonRunningWarning("getE() from ");
            return 0;
        }
        if (eValue == Double.NEGATIVE_INFINITY) {
            Debug.e("GCodeReaderAndWriter.getE() - no value stored!");
            return 0;
        }
        return eValue;
    }

    public String lastResponse() {
        return lastResp;
    }

    public String toHex(final String arg) {
        return String.format("%x", new BigInteger(1, arg.getBytes(/*YOUR_CHARSET?*/)));
    }

    /**
     * Parse the string sent back from the RepRap machine.
     * 
     * @throws IOException
     * 
     */
    private long waitForResponse() throws IOException {
        int i;
        String resp = "";
        long result = allSentOK;
        String lns;
        resetReceived();
        boolean goAgain;
        final Date timer = new Date();
        final long startWait = timer.getTime();
        long timeNow;
        long increment = 2000;
        final long longWait = 10 * 60 * 1000; // 10 mins...

        for (;;) {
            timeNow = timer.getTime() - startWait;
            if (timeNow > increment) {
                Debug.d("GCodeReaderAndWriter().waitForResponse(): waited for " + timeNow / 1000 + " seconds.");
                increment = 2 * increment;
            }

            if (timeNow > longWait) {
                Debug.e("GCodeReaderAndWriter().waitForResponse(): waited for " + timeNow / 1000 + " seconds.");
                queue("M0 ;shut RepRap down");
            }

            i = serialInStream.read();

            //anything found?
            if (i >= 0) {
                final char c = (char) i;

                //is it at the end of the line?
                if (c == '\n' || c == '\r') {
                    goAgain = false;
                    if (resp.startsWith("start") || resp.contentEquals("")) // Startup or null string...
                    {
                        resp = "";
                        goAgain = true;
                    } else if (resp.startsWith("!!")) // Horrible hard fault?
                    {
                        result = shutDown;
                        Debug.e("GCodeWriter.waitForResponse(): RepRap hard fault!  RepRap said: " + resp);

                    } else if (resp.startsWith("//")) // immediate DEBUG "comment" from the firmware  ( like C++ )
                    {
                        Debug.d("GCodeWriter.waitForResponse(): " + resp);
                        resp = "";
                        goAgain = true;
                    } else if (resp.endsWith("\\")) // lines ending in a single backslash are considered "continued" to the next line, like "C"
                    {
                        //	Debug.d("GCodeWriter.waitForResponse(): " + resp);
                        //	resp = ""; don't clear the previuos response...
                        goAgain = true; // but do "go again"
                    } else if (resp.startsWith("rs")) // Re-send request?
                    {
                        lns = resp.substring(3);
                        final int sp = lns.indexOf(" ");
                        if (sp > 0) {
                            lns = lns.substring(0, sp);
                        }
                        result = Long.parseLong(lns);
                        Debug.e("GCodeWriter.waitForResponse() - request to resend from line " + result + ".  RepRap said: "
                                + resp);
                    } else if (!resp.startsWith("ok")) // Must be "ok" if not those - check
                    {
                        Debug.e("GCodeWriter.waitForResponse() - dud response from RepRap:" + resp + " (hex: " + toHex(resp)
                                + ")");
                        result = lineNumber; // Try to send the last line again
                    }

                    // Have we got temperatures and/or coordinates and/or filenames?

                    eTemp = parseReturnedValue(resp, " T:");
                    bTemp = parseReturnedValue(resp, " B:");
                    sdFiles = parseReturnedNames(resp, " Files: {");
                    if (resp.indexOf(" C:") >= 0) {
                        xValue = parseReturnedValue(resp, " X:");
                        yValue = parseReturnedValue(resp, " Y:");
                        zValue = parseReturnedValue(resp, " Z:");
                        eValue = parseReturnedValue(resp, " E:");
                    }

                    if (!goAgain) {
                        Debug.c("Response: " + resp);
                        lastResp = resp.substring(2);
                        return result;
                    }
                } else {
                    resp += c;
                }
            }
        }
    }

    /**
     * Send a G-code command to the machine or into a file.
     * 
     * @param cmd
     * @throws IOException
     */
    public void queue(String cmd) throws IOException {
        cmd = cmd.trim();
        cmd = cmd.replaceAll("  ", " ");

        if (fileOutStream != null) {
            fileOutStream.println(cmd);
            Debug.c("G-code: " + cmd + " written to file");
        } else {
            bufferQueue(cmd);
        }
    }

    /**
     * Copy a file of G Codes straight to output - generally used for canned
     * cycles
     */
    public void copyFile(final String fileName) {
        final File f = new File(fileName);
        if (!f.exists()) {
            Debug.e("GCodeReaderAndWriter().copyFile: can't find file " + fileName);
            return;
        }
        try {
            final FileReader fr = new FileReader(f);
            final BufferedReader br = new BufferedReader(fr);
            String s;
            while ((s = br.readLine()) != null) {
                queue(s);
            }
            fr.close();
        } catch (final Exception e) {
            Debug.e("GCodeReaderAndWriter().copyFile: exception reading file " + fileName);
            return;
        }
    }

    private void openSerialConnection(final String portName) {
    }

    public String loadGCodeFileForMaking() {
        final JFileChooser chooser = new JFileChooser();
        FileFilter filter;
        filter = new ExtensionFileFilter("G Code file to be read", new String[] { "gcode" });
        chooser.setFileFilter(filter);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        final int result = chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            final String name = chooser.getSelectedFile().getAbsolutePath();
            try {
                Debug.d("opening: " + name);
                fileInStreamLength = chooser.getSelectedFile().length();
                fileInStream = new BufferedReader(new FileReader(chooser.getSelectedFile()));
                return chooser.getSelectedFile().getName();
            } catch (final FileNotFoundException e) {
                Debug.e("Can't read file " + name);
                fileInStream = null;
                return null;
            }
        } else {
            Debug.e("Can't read file.");
            fileInStream = null;
        }

        return null;
    }

    public String setGCodeFileForOutput(final boolean topDown, final String fileRoot) {
        final File defaultFile = new File(fileRoot + ".gcode");
        final JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(defaultFile);
        FileFilter filter;
        filter = new ExtensionFileFilter("G Code file to write to", new String[] { "gcode" });
        chooser.setFileFilter(filter);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        opFileName = null;
        final int result = chooser.showSaveDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            opFileName = chooser.getSelectedFile().getAbsolutePath();
            if (opFileName.endsWith(GCODE_EXTENSION)) {
                opFileName = opFileName.substring(0, opFileName.length() - 6);
            }

            try {
                boolean doe = false;
                String fn = opFileName;
                if (topDown) {
                    fn += FIRST_ENDING;
                    fn += TMP_STRING;
                    doe = true;
                }
                fn += GCODE_EXTENSION;

                Debug.d("opening: " + fn);
                final File fl = new File(fn);
                if (doe) {
                    fl.deleteOnExit();
                }
                final FileOutputStream fileStream = new FileOutputStream(fl);
                fileOutStream = new PrintStream(fileStream);
                String shortName = chooser.getSelectedFile().getName();
                if (!shortName.endsWith(GCODE_EXTENSION)) {
                    shortName += GCODE_EXTENSION;
                }
                layerFileNames = System.getProperty("java.io.tmpdir") + File.separator + shortName;
                final File rfod = new File(layerFileNames);
                if (!rfod.mkdir()) {
                    throw new RuntimeException(layerFileNames);
                }
                rfod.deleteOnExit();
                layerFileNames += File.separator;
                return shortName;
            } catch (final FileNotFoundException e) {
                Debug.e("Can't write to file '" + opFileName);
                opFileName = null;
                fileOutStream = null;
            }
        } else {
            fileOutStream = null;
        }
        return null;
    }

    /**
     * Start the production run (as opposed to driving the machine
     * interactively).
     */
    public void startRun() {
        if (fileOutStream != null) {
            if (bufferThread != null) {
                exhaustBuffer = true;
                while (exhaustBuffer) {
                    try {
                        Thread.sleep(200);
                    } catch (final Exception ex) {
                    }
                }
            }
            bufferThread = null;
            head = 0;
        }
    }

    public void startingLayer(final LayerRules lc) {
        lc.setLayerFileName(layerFileNames + "reprap" + lc.getMachineLayer() + TMP_STRING + GCODE_EXTENSION);
        if (!lc.getReversing()) {
            try {
                final File fl = new File(lc.getLayerFileName());
                fl.deleteOnExit();
                final FileOutputStream fileStream = new FileOutputStream(fl);
                fileOutStream = new PrintStream(fileStream);
            } catch (final Exception e) {
                Debug.e("Can't write to file " + lc.getLayerFileName());
            }
        }
    }

    public void finishedLayer(final LayerRules lc) {
        if (!lc.getReversing()) {
            fileOutStream.close();
        }
    }

    public void startingEpilogue(final LayerRules lc) {
    }

    /**
     * All done.
     */
    public void finish(final LayerRules lc) {
        Debug.d("disposing of GCodeReaderAndWriter.");
        try {
            if (serialInStream != null) {
                serialInStream.close();
            }

            if (serialOutStream != null) {
                serialOutStream.close();
            }

            if (fileInStream != null) {
                fileInStream.close();
            }

        } catch (final Exception e) {
        }
    }

    public String getOutputFilename() {
        return opFileName + GCODE_EXTENSION;
    }
}