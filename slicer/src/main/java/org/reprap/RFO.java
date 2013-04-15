package org.reprap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.swing.JOptionPane;
import javax.vecmath.Matrix4d;

import org.reprap.geometry.polyhedra.AllSTLsToBuild;
import org.reprap.geometry.polyhedra.CSGReader;
import org.reprap.geometry.polyhedra.STLObject;
import org.reprap.utilities.Debug;

/**
 * A .rfo file is a compressed archive containing multiple objects that are all
 * to be built in a RepRap machine at once. See this web page:
 * 
 * http://reprap.org/bin/view/Main/MultipleMaterialsFiles
 * 
 * for details.
 * 
 * This is the class that handles .rfo files.
 */
public class RFO {
    private static final String legendName = "legend.xml";

    /**
     * The name of the RFO file.
     */
    private final String fileName;
    /**
     * The unique file names;
     */
    private List<String> uNames;
    /**
     * The directory in which it is.
     */
    private String path;
    /**
     * The temporary directory
     */
    private String tempDir;
    /**
     * The location of the temporary RFO directory
     */
    private String rfoDir;
    /**
     * The unique temporary directory name
     */
    private final String uniqueName;
    /**
     * The collection of objects being written out or read in.
     */
    private AllSTLsToBuild astl;
    /**
     * The XML output for the legend file.
     */
    private RfoXmlRenderer xml;

    /**
     * The constructor is the same whether we're reading or writing. fn is where
     * to put or get the rfo file from. as is all the things to write; set that
     * null when reading.
     * 
     * @param fn
     * @param as
     */
    private RFO(final String fn, final AllSTLsToBuild as) {
        astl = as;
        uNames = null;
        final int sepIndex = fn.lastIndexOf(File.separator);
        final int fIndex = fn.indexOf("file:");
        fileName = fn.substring(sepIndex + 1, fn.length());
        if (sepIndex >= 0) {
            if (fIndex >= 0) {
                path = fn.substring(fIndex + 5, sepIndex + 1);
            } else {
                path = fn.substring(0, sepIndex + 1);
            }
        } else {
            path = "";
        }

        uniqueName = "rfo" + Long.toString(System.nanoTime());

        tempDir = System.getProperty("java.io.tmpdir") + File.separator + uniqueName;

        File rfod = new File(tempDir);
        if (!rfod.mkdir()) {
            throw new RuntimeException(tempDir);
        }
        tempDir += File.separator;
        rfoDir = tempDir + "rfo";
        rfod = new File(rfoDir);
        if (!rfod.mkdir()) {
            throw new RuntimeException(rfoDir);
        }
        rfoDir += File.separator;
    }

    private static boolean recursiveDelete(final File fileOrDir) {
        if (fileOrDir.isDirectory()) {
            // recursively delete contents
            for (final File innerFile : fileOrDir.listFiles()) {
                if (!recursiveDelete(innerFile)) {
                    return false;
                }
            }
        }

        return fileOrDir.delete();
    }

    private static void copyFile(final File in, final File out) {
        try {
            final FileChannel inChannel = new FileInputStream(in).getChannel();
            final FileChannel outChannel = new FileOutputStream(out).getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
            inChannel.close();
            outChannel.close();
        } catch (final Exception e) {
            Debug.getInstance().errorMessage("RFO.copyFile(): " + e);
        }

    }

    private static void copyFile(final String from, final String to) {
        File inputFile;
        File outputFile;
        final int fIndex = from.indexOf("file:");
        final int tIndex = to.indexOf("file:");
        if (fIndex < 0) {
            inputFile = new File(from);
        } else {
            inputFile = new File(from.substring(fIndex + 5, from.length()));
        }
        if (tIndex < 0) {
            outputFile = new File(to);
        } else {
            outputFile = new File(to.substring(tIndex + 5, to.length()));
        }
        copyFile(inputFile, outputFile);
    }

    /**
     * Copy each unique STL file to a directory. Files used more than once are
     * only copied once.
     */
    public static List<String> copySTLs(final AllSTLsToBuild astltb, final String rfod) {
        int u = 0;
        final List<String> uniqueNames = new ArrayList<String>();
        for (int i = 0; i < astltb.size(); i++) {
            for (int subMod1 = 0; subMod1 < astltb.get(i).size(); subMod1++) {
                final String s = astltb.get(i).fileAndDirectioryItCameFrom(subMod1);
                astltb.get(i).setUnique(subMod1, u);
                for (int j = 0; j < i; j++) {
                    for (int subMod2 = 0; subMod2 < astltb.get(j).size(); subMod2++) {
                        if (s.equals(astltb.get(j).fileAndDirectioryItCameFrom(subMod2))) {
                            astltb.get(i).setUnique(subMod1, astltb.get(j).getUnique(subMod2));
                            break;
                        }
                    }
                }
                if (astltb.get(i).getUnique(subMod1) == u) {
                    final String un = astltb.get(i).fileItCameFrom(subMod1);
                    copyFile(s, rfod + un);
                    uniqueNames.add(un);

                    final String csgFile = CSGReader.CSGFileExists(s);
                    if (csgFile != null) {
                        final int sepIndex = csgFile.lastIndexOf(File.separator);
                        final String justFile = csgFile.substring(sepIndex + 1, csgFile.length());
                        copyFile(csgFile, rfod + justFile);
                    }

                    u++;
                }
            }
        }
        return uniqueNames;
    }

    /**
     * Write a 4x4 homogeneous transform in XML format.
     */
    private void writeTransform(final TransformGroup trans) {
        final Transform3D t = new Transform3D();
        final Matrix4d m = new Matrix4d();
        trans.getTransform(t);
        t.get(m);
        xml.push("transform3D");
        xml.write("row m00=\"" + m.m00 + "\" m01=\"" + m.m01 + "\" m02=\"" + m.m02 + "\" m03=\"" + m.m03 + "\"");
        xml.write("row m10=\"" + m.m10 + "\" m11=\"" + m.m11 + "\" m12=\"" + m.m12 + "\" m13=\"" + m.m13 + "\"");
        xml.write("row m20=\"" + m.m20 + "\" m21=\"" + m.m21 + "\" m22=\"" + m.m22 + "\" m23=\"" + m.m23 + "\"");
        xml.write("row m30=\"" + m.m30 + "\" m31=\"" + m.m31 + "\" m32=\"" + m.m32 + "\" m33=\"" + m.m33 + "\"");
        xml.pop();
    }

    /**
     * Create the legend file
     */
    private void createLegend() {
        if (uNames == null) {
            Debug.getInstance().errorMessage("RFO.createLegend(): no list of unique names saved.");
            return;
        }
        xml = new RfoXmlRenderer(rfoDir + legendName, "reprap-fab-at-home-build version=\"0.1\"");
        for (int i = 0; i < astl.size(); i++) {
            xml.push("object name=\"object-" + i + "\"");
            xml.push("files");
            final STLObject stlo = astl.get(i);
            for (int subObj = 0; subObj < stlo.size(); subObj++) {
                xml.push("file location=\"" + uNames.get(stlo.getUnique(subObj))
                        + "\" filetype=\"application/sla\" material=\"" + stlo.attributes(subObj).getMaterial() + "\"");
                xml.pop();
            }
            xml.pop();
            writeTransform(stlo.trans());
            xml.pop();
        }
        xml.close();
    }

    /**
     * The entire temporary directory with the legend file and ann the STLs is
     * complete. Compress it into the required rfo file using zip. Note we
     * delete the temporary files as we go along, ending up by deleting the
     * directory containing them.
     */
    private void compress() throws IOException {
        final ZipOutputStream rfoFile = new ZipOutputStream(new FileOutputStream(path + fileName));
        try {
            final File dirToZip = new File(rfoDir);
            final String[] fileList = dirToZip.list();
            final byte[] buffer = new byte[4096];
            int bytesIn = 0;

            for (final String element : fileList) {
                final File f = new File(dirToZip, element);
                final FileInputStream fis = new FileInputStream(f);
                try {
                    String zEntry = f.getPath();
                    final int start = zEntry.indexOf(uniqueName);
                    zEntry = zEntry.substring(start + uniqueName.length() + 1, zEntry.length());
                    final ZipEntry entry = new ZipEntry(zEntry);
                    rfoFile.putNextEntry(entry);
                    while ((bytesIn = fis.read(buffer)) != -1) {
                        rfoFile.write(buffer, 0, bytesIn);
                    }
                } finally {
                    fis.close();
                }
            }
        } finally {
            rfoFile.close();
        }
    }

    /**
     * Warn the user of an overwrite
     * 
     * @return
     */
    public static boolean checkFile(final String pth, final String nm) {
        final File file = new File(pth + nm);
        if (file.exists()) {
            final String[] options = { "OK", "Cancel" };
            final int r = JOptionPane.showOptionDialog(null, "The file " + nm + " exists.  Overwrite it?", "Warning",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
            return r == 0;
        }
        return true;
    }

    /**
     * This is what gets called to write an rfo file. It saves all the parts of
     * allSTL in rfo file fn.
     */
    public static void save(String fn, final AllSTLsToBuild allSTL) throws IOException {
        if (!fn.endsWith(".rfo")) {
            fn += ".rfo";
        }
        final RFO rfo = new RFO(fn, allSTL);
        if (!RFO.checkFile(rfo.path, rfo.fileName)) {
            return;
        }
        rfo.uNames = RFO.copySTLs(allSTL, rfo.rfoDir);
        rfo.createLegend();
        rfo.compress();
        final File t = new File(rfo.tempDir);
        recursiveDelete(t);
    }

    /**
     * Arrghhh!!!!
     */
    private String processSeparators(final String is) {
        String result = "";
        for (int i = 0; i < is.length(); i++) {
            if (is.charAt(i) == '\\') {
                if (File.separator.charAt(0) == '/') {
                    result += '/';
                } else {
                    result += '\\';
                }
            } else if (is.charAt(i) == '/') {
                if (File.separator.charAt(0) == '\\') {
                    result += '\\';
                } else {
                    result += '/';
                }
            } else {
                result += is.charAt(i);
            }
        }

        return result;
    }

    /**
     * This uncompresses the zip that is the rfo file into the temporary
     * directory.
     */
    private void unCompress() {
        try {
            final byte[] buffer = new byte[4096];
            int bytesIn;
            final ZipFile rfoFile = new ZipFile(path + fileName);
            final Enumeration<? extends ZipEntry> allFiles = rfoFile.entries();
            while (allFiles.hasMoreElements()) {
                final ZipEntry ze = allFiles.nextElement();
                final InputStream is = rfoFile.getInputStream(ze);
                final String fName = processSeparators(ze.getName());
                final File element = new File(tempDir + fName);
                org.reprap.Main.cleanUpFiles.add(element);
                final FileOutputStream os = new FileOutputStream(element);
                while ((bytesIn = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesIn);
                }
                os.close();
            }
        } catch (final Exception e) {
            Debug.getInstance().errorMessage("RFO.unCompress(): " + e);
        }
    }

    /**
     * This reads the legend file and does what it says.
     */
    private void interpretLegend() {
        @SuppressWarnings("unused")
        final RfoXmlHandler xi = new RfoXmlHandler(rfoDir + legendName, this);
    }

    /**
     * This is what gets called to read an rfo file from filename fn.
     */
    public static AllSTLsToBuild load(String fn) {
        if (!fn.endsWith(".rfo")) {
            fn += ".rfo";
        }
        final RFO rfo = new RFO(fn, null);
        File rfod = new File(rfo.tempDir);
        org.reprap.Main.cleanUpFiles.add(rfod);
        rfod = new File(rfo.rfoDir);
        org.reprap.Main.cleanUpFiles.add(rfod);

        rfo.astl = new AllSTLsToBuild();
        rfo.unCompress();
        try {
            rfo.interpretLegend();
        } catch (final Exception e) {
            Debug.getInstance().errorMessage("RFO.load(): exception - " + e.toString());
        }

        return rfo.astl;
    }

    AllSTLsToBuild getAstl() {
        return astl;
    }

    String getRfoDir() {
        return rfoDir;
    }
}
