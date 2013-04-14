package org.reprap.utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.Shape3D;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;

import com.sun.j3d.loaders.IncorrectFormatException;
import com.sun.j3d.loaders.Loader;
import com.sun.j3d.loaders.ParsingErrorException;
import com.sun.j3d.loaders.Scene;
import com.sun.j3d.loaders.SceneBase;
import com.sun.j3d.utils.geometry.GeometryInfo;
import com.sun.j3d.utils.geometry.NormalGenerator;

/**
 * Title: STL Loader Description: STL files loader (Supports ASCII and binary
 * files) for Java3D Needs JDK 1.4 due to endian problems Company: Universidad
 * del Pais Vasco (UPV/EHU)
 * 
 * @author: Carlos Pedrinaci Godoy
 * @version: 1.0
 * 
 *           Contact : xenicp@yahoo.es
 * 
 *           Things TODO: 1.-We can't read binary files over the net. 2.-For
 *           binary files if size is lower than expected (calculated with the
 *           number of faces) the program will block. 3.-Improve the way for
 *           detecting the kind of stl file? Can give us problems if the comment
 *           of the binary file begins by "solid"
 * 
 *           ----
 * 
 *           Modified by Adrian Bowyer to compute normals from triangles, rather
 *           than rely on the read-in values (which seem to be all wrong...).
 *           See the function
 * 
 *           private SceneBase makeScene()
 */
public class StlFile implements Loader {
    private int flag;
    private URL baseUrl = null; // Reading files over Internet
    private String basePath = null; // For local files
    private boolean fromUrl = false; // Useful for binary files
    private boolean isAscii = true; // File type Ascii -> true o binary -> false
    private String fileName = null;
    private List<Point3f> coordList;
    private List<Vector3f> normList;
    private Point3f[] coordArray = null;
    private Vector3f[] normArray = null;
    private int[] stripCounts = null;
    private String objectName = new String("Not available");

    public StlFile() {
    }

    /**
     * Method that reads the EOL Needed for verifying that the file has a
     * correct format
     * 
     * @param parser
     *            The file parser. An instance of StlFileParser.
     */
    private void readEOL(final StlFileParser parser) {
        do {
            try {
                parser.nextToken();
            } catch (final IOException e) {
                System.err.println("IO Error on line " + parser.lineno() + ": " + e.getMessage());
            }
        } while (parser.ttype != StreamTokenizer.TT_EOL);
    }

    /**
     * Method that reads the word "solid" and stores the object name. It also
     * detects what kind of file it is TODO: 1.- Better way control of
     * exceptions? 2.- Better way to decide between Ascii and Binary?
     * 
     * @param parser
     *            The file parser. An instance of StlFileParser.
     */
    private void readSolid(final StlFileParser parser) {
        if (parser.sval == null) {
            setAscii(false);
            return;
        }
        if (!parser.sval.equals("solid")) {
            // If the first word is not "solid" then we consider the file is binary
            // Can give us problems if the comment of the binary file begins by "solid"
            setAscii(false);
        } else {
            try {
                parser.nextToken();
            } catch (final IOException e) {
                System.err.println("IO Error on line " + parser.lineno() + ": " + e.getMessage());
            }
            if (parser.sval.equals("binary")) // Deal with annoying CAD systems that start files with "solid binary"
            {
                setAscii(false);
                try {
                    parser.nextToken();
                } catch (final IOException e) {
                    throw new RuntimeException("IO Error on line " + parser.lineno() + ": " + e.getMessage());
                }
            } else {
                if (parser.ttype != StreamTokenizer.TT_WORD) {
                    // Is the object name always provided???
                    System.err.println("Format Error:expecting the object name on line " + parser.lineno());
                } else {
                    setObjectName(new String(parser.sval));
                    readEOL(parser);
                }
            }
        }
    }

    /**
     * Method that reads a normal
     * 
     * @param parser
     *            The file parser. An instance of StlFileParser.
     */
    private void readNormal(final StlFileParser parser) {
        final Vector3f v = new Vector3f();

        if (!(parser.ttype == StreamTokenizer.TT_WORD && parser.sval.equals("normal"))) {
            System.err.println("Format Error:expecting 'normal' on line " + parser.lineno());
        } else {
            if (parser.getNumber()) {
                v.x = (float) parser.nval;

                if (parser.getNumber()) {
                    v.y = (float) parser.nval;
                    if (parser.getNumber()) {
                        v.z = (float) parser.nval;
                        normList.add(v);
                        readEOL(parser);
                    } else {
                        System.err.println("Format Error:expecting coordinate on line " + parser.lineno());
                    }
                } else {
                    System.err.println("Format Error:expecting coordinate on line " + parser.lineno());
                }
            } else {
                System.err.println("Format Error:expecting coordinate on line " + parser.lineno());
            }
        }
    }

    /**
     * Method that reads the coordinates of a vector
     * 
     * @param parser
     *            The file parser. An instance of StlFileParser.
     */
    private void readVertex(final StlFileParser parser) {
        final Point3f p = new Point3f();

        if (!(parser.ttype == StreamTokenizer.TT_WORD && parser.sval.equals("vertex"))) {
            System.err.println("Format Error:expecting 'vertex' on line " + parser.lineno());
        } else {
            if (parser.getNumber()) {
                p.x = (float) parser.nval;

                if (parser.getNumber()) {
                    p.y = (float) parser.nval;
                    if (parser.getNumber()) {
                        p.z = (float) parser.nval;
                        coordList.add(p);
                        readEOL(parser);
                    } else {
                        System.err.println("Format Error: expecting coordinate on line " + parser.lineno());
                    }
                } else {
                    System.err.println("Format Error: expecting coordinate on line " + parser.lineno());
                }
            } else {
                System.err.println("Format Error: expecting coordinate on line " + parser.lineno());
            }
        }
    }

    /**
     * Method that reads "outer loop" and then EOL
     * 
     * @param parser
     *            The file parser. An instance of StlFileParser.
     */
    private void readLoop(final StlFileParser parser) {
        if (!(parser.ttype == StreamTokenizer.TT_WORD && parser.sval.equals("outer"))) {
            System.err.println("Format Error:expecting 'outer' on line " + parser.lineno());
        } else {
            try {
                parser.nextToken();
            } catch (final IOException e) {
                System.err.println("IO error on line " + parser.lineno() + ": " + e.getMessage());
            }
            if (!(parser.ttype == StreamTokenizer.TT_WORD && parser.sval.equals("loop"))) {
                System.err.println("Format Error:expecting 'loop' on line " + parser.lineno());
            } else {
                readEOL(parser);
            }
        }
    }

    /**
     * Method that reads "endloop" then EOL
     * 
     * @param parser
     *            The file parser. An instance of StlFileParser.
     */
    private void readEndLoop(final StlFileParser parser) {
        if (!(parser.ttype == StreamTokenizer.TT_WORD && parser.sval.equals("endloop"))) {
            System.err.println("Format Error:expecting 'endloop' on line " + parser.lineno());
        } else {
            readEOL(parser);
        }
    }

    /**
     * Method that reads "endfacet" then EOL
     * 
     * @param parser
     *            The file parser. An instance of StlFileParser.
     */
    private void readEndFacet(final StlFileParser parser) {
        if (!(parser.ttype == StreamTokenizer.TT_WORD && parser.sval.equals("endfacet"))) {
            System.err.println("Format Error:expecting 'endfacet' on line " + parser.lineno());
        } else {
            readEOL(parser);
        }
    }

    /**
     * Method that reads a face of the object (Cares about the format)
     * 
     * @param parser
     *            The file parser. An instance of StlFileParser.
     */
    private void readFacet(final StlFileParser parser) {
        if (!(parser.ttype == StreamTokenizer.TT_WORD && parser.sval.equals("facet"))) {
            System.err.println("Format Error:expecting 'facet' on line " + parser.lineno());
        } else {
            try {
                parser.nextToken();
                readNormal(parser);

                parser.nextToken();
                readLoop(parser);

                parser.nextToken();
                readVertex(parser);

                parser.nextToken();
                readVertex(parser);

                parser.nextToken();
                readVertex(parser);

                parser.nextToken();
                readEndLoop(parser);

                parser.nextToken();
                readEndFacet(parser);
            } catch (final IOException e) {
                System.err.println("IO Error on line " + parser.lineno() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Method that reads a face in binary files All binary versions of the
     * methods end by 'B' As in binary files we can read the number of faces, we
     * don't need to use coordArray and normArray (reading binary files should
     * be faster)
     * 
     * @param in
     *            The ByteBuffer with the data of the object.
     * @param index
     *            The facet index
     * 
     * @throws IOException
     */
    private void readFacetB(final ByteBuffer in, final int index) throws IOException {
        // Read the Normal
        normArray[index] = new Vector3f();
        normArray[index].x = in.getFloat();
        normArray[index].y = in.getFloat();
        normArray[index].z = in.getFloat();

        // Read vertex1
        coordArray[index * 3] = new Point3f();
        coordArray[index * 3].x = in.getFloat();
        coordArray[index * 3].y = in.getFloat();
        coordArray[index * 3].z = in.getFloat();

        // Read vertex2
        coordArray[index * 3 + 1] = new Point3f();
        coordArray[index * 3 + 1].x = in.getFloat();
        coordArray[index * 3 + 1].y = in.getFloat();
        coordArray[index * 3 + 1].z = in.getFloat();

        // Read vertex3
        coordArray[index * 3 + 2] = new Point3f();
        coordArray[index * 3 + 2].x = in.getFloat();
        coordArray[index * 3 + 2].y = in.getFloat();
        coordArray[index * 3 + 2].z = in.getFloat();
    }

    /**
     * Method for reading binary files Execution is completly different It uses
     * ByteBuffer for reading data and ByteOrder for retrieving the machine's
     * endian (Needs JDK 1.4)
     * 
     * TODO: 1.-Be able to read files over Internet 2.-If the amount of data
     * expected is bigger than what is on the file then the program will block
     * forever
     * 
     * @param file
     *            The name of the file
     * 
     * @throws IOException
     */
    private void readBinaryFile(final String file) throws IOException {
        FileInputStream data;
        ByteBuffer dataBuffer;
        final byte[] Info = new byte[80]; // Header data
        final byte[] Array_number = new byte[4]; // Holds the number of faces
        byte[] Temp_Info; // Intermediate array

        int Number_faces; // First info (after the header) on the file

        if (fromUrl) {
            // FileInputStream can only read local files!?
            System.out.println("This version doesn't support reading binary files from internet");
        } else { // It's a local file
            data = new FileInputStream(file);

            // First 80 bytes aren't important
            if (80 != data.read(Info)) { // File is incorrect
                                         //System.out.println("Format Error: 80 bytes expected");
                throw new IncorrectFormatException();
            } else { // We must first read the number of faces -> 4 bytes int
                     // It depends on the endian so..

                data.read(Array_number); // We get the 4 bytes
                dataBuffer = ByteBuffer.wrap(Array_number); // ByteBuffer for reading correctly the int
                dataBuffer.order(ByteOrder.nativeOrder()); // Set the right order
                Number_faces = dataBuffer.getInt();

                Temp_Info = new byte[50 * Number_faces]; // Each face has 50 bytes of data

                data.read(Temp_Info); // We get the rest of the file

                dataBuffer = ByteBuffer.wrap(Temp_Info); // Now we have all the data in this ByteBuffer
                dataBuffer.order(ByteOrder.nativeOrder());

                // We can create that array directly as we know how big it's going to be
                coordArray = new Point3f[Number_faces * 3]; // Each face has 3 vertex
                normArray = new Vector3f[Number_faces];
                stripCounts = new int[Number_faces];

                for (int i = 0; i < Number_faces; i++) {
                    stripCounts[i] = 3;
                    try {
                        readFacetB(dataBuffer, i);
                        // After each facet there are 2 bytes without information
                        // In the last iteration we dont have to skip those bytes..
                        if (i != Number_faces - 1) {
                            dataBuffer.get();
                            dataBuffer.get();
                        }
                    } catch (final IOException e) {
                        throw new IncorrectFormatException("Format Error: iteration number " + i);
                    }
                }
            }
        }
    }

    /**
     * Method that reads ASCII files Uses StlFileParser for correct reading and
     * format checking The begining of that method is common to binary and ASCII
     * files We try to detect what king of file it is
     * 
     * TODO: 1.- Find a best way to decide what kind of file it is 2.- Is that
     * return (first catch) the best thing to do?
     * 
     * @param parser
     *            The file parser. An instance of StlFileParser.
     */
    private void readFile(final StlFileParser parser) {
        try {
            parser.nextToken();
        } catch (final IOException e) {
            throw new RuntimeException("IO Error on line " + parser.lineno() + ": " + e.getMessage());
        }

        // Here we try to detect what kind of file it is (see readSolid)
        readSolid(parser);

        if (getAscii()) {
            try {
                parser.nextToken();
            } catch (final IOException e) {
                System.err.println("IO Error on line " + parser.lineno() + ": " + e.getMessage());
            }

            // Read all the facets of the object
            while (parser.ttype != StreamTokenizer.TT_EOF && !parser.sval.equals("endsolid")) {
                readFacet(parser);
                try {
                    parser.nextToken();
                } catch (final IOException e) {
                    System.err.println("IO Error on line " + parser.lineno() + ": " + e.getMessage());
                }
            }

            // Why are we out of the while?: EOF or endsolid
            if (parser.ttype == StreamTokenizer.TT_EOF) {
                System.err.println("Format Error:expecting 'endsolid', line " + parser.lineno());
            }
        } else { // Binary file
            try {
                readBinaryFile(getFileName());
            } catch (final IOException e) {
                System.err.println("Format Error: reading the binary file");
            }
        }
    }

    /**
     * The Stl File is loaded from the .stl file specified by the filename. To
     * attach the model to your scene, call getSceneGroup() on the Scene object
     * passed back, and attach the returned BranchGroup to your scene graph. For
     * an example, see $J3D/programs/examples/ObjLoad/ObjLoad.java.
     * 
     * @param filename
     *            The name of the file with the object to load
     * 
     * @return Scene The scene with the object loaded.
     * 
     * @throws FileNotFoundException
     * @throws IncorrectFormatException
     * @throws ParsingErrorException
     */
    @Override
    public Scene load(final String filename) throws FileNotFoundException, IncorrectFormatException, ParsingErrorException {
        setBasePath(new File(filename).getParent());
        setFileName(filename); // For binary files

        final Reader reader = new BufferedReader(new FileReader(filename));
        return load(reader);
    }

    /**
     * The Stl file is loaded off of the web. To attach the model to your scene,
     * call getSceneGroup() on the Scene object passed back, and attach the
     * returned BranchGroup to your scene graph. For an example, see
     * $J3D/programs/examples/ObjLoad/ObjLoad.java.
     * 
     * @param url
     *            The url to load the onject from
     * 
     * @return Scene The scene with the object loaded.
     * 
     * @throws FileNotFoundException
     * @throws IncorrectFormatException
     * @throws ParsingErrorException
     */
    @Override
    public Scene load(final URL url) throws FileNotFoundException, IncorrectFormatException, ParsingErrorException {
        BufferedReader reader;

        setBaseUrlFromUrl(url);

        try {
            reader = new BufferedReader(new InputStreamReader(url.openStream()));
        } catch (final IOException e) {
            throw new FileNotFoundException();
        }
        fromUrl = true;
        return load(reader);
    }

    /**
     * The Stl File is loaded from the already opened file. To attach the model
     * to your scene, call getSceneGroup() on the Scene object passed back, and
     * attach the returned BranchGroup to your scene graph. For an example, see
     * $J3D/programs/examples/ObjLoad/ObjLoad.java.
     * 
     * @param reader
     *            The reader to read the object from
     * 
     * @return Scene The scene with the object loaded.
     * 
     * @throws FileNotFoundException
     * @throws IncorrectFormatException
     * @throws ParsingErrorException
     */
    @Override
    public Scene load(final Reader reader) throws FileNotFoundException, IncorrectFormatException, ParsingErrorException {
        // That method calls the method that loads the file for real..
        // Even if the Stl format is not complicated I've decided to use
        // a parser as in the Obj's loader included in Java3D

        final StlFileParser st = new StlFileParser(reader);
        coordList = new ArrayList<Point3f>();
        normList = new ArrayList<Vector3f>();
        setAscii(true);
        readFile(st);
        return makeScene();
    }

    /**
     * Method that creates the SceneBase with the stl file info
     * 
     * @return SceneBase The scene
     */
    private SceneBase makeScene() {
        final SceneBase scene = new SceneBase();
        final BranchGroup group = new BranchGroup();
        scene.setSceneGroup(group);

        // Store the scene info on a GeometryInfo
        final GeometryInfo gi = new GeometryInfo(GeometryInfo.TRIANGLE_STRIP_ARRAY);

        // Convert ArrayLists to arrays: only needed if file was not binary
        if (isAscii) {
            coordArray = coordList.toArray(new Point3f[(coordList).size()]);
            normArray = normList.toArray(new Vector3f[normList.size()]);
            stripCounts = new int[normList.size()];
            Arrays.fill(stripCounts, 3);
        }

        gi.setCoordinates(coordArray);
        gi.setStripCounts(stripCounts);
        final NormalGenerator ng = new NormalGenerator();
        ng.generateNormals(gi);

        // Put geometry into Shape3d
        final Shape3D shape = new Shape3D();
        shape.setGeometry(gi.getGeometryArray());

        group.addChild(shape);

        scene.addNamedObject(objectName, shape);

        return scene;
    }

    @Override
    public URL getBaseUrl() {
        return baseUrl;
    }

    /**
     * Modifier for baseUrl, if accessing internet.
     * 
     * @param url
     *            The new url
     */
    @Override
    public void setBaseUrl(final URL url) {
        baseUrl = url;
    }

    private void setBaseUrlFromUrl(final URL url) {
        final StringTokenizer stok = new StringTokenizer(url.toString(), "/\\", true);
        final int tocount = stok.countTokens() - 1;
        final StringBuffer sb = new StringBuffer();
        for (int i = 0; i < tocount; i++) {
            final String a = stok.nextToken();
            sb.append(a);
        }
        try {
            baseUrl = new URL(sb.toString());
        } catch (final MalformedURLException e) {
            System.err.println("Error setting base URL: " + e.getMessage());
        }
    }

    @Override
    public String getBasePath() {
        return basePath;
    }

    /**
     * Set the path where files associated with this .stl file are located. Only
     * needs to be called to set it to a different directory from that
     * containing the .stl file.
     * 
     * @param pathName
     *            The new Path to the file
     */
    @Override
    public void setBasePath(final String pathName) {
        basePath = pathName;
        if (basePath == null || basePath == "") {
            basePath = "." + java.io.File.separator;
        }
        basePath = basePath.replace('/', java.io.File.separatorChar);
        basePath = basePath.replace('\\', java.io.File.separatorChar);
        if (!basePath.endsWith(java.io.File.separator)) {
            basePath = basePath + java.io.File.separator;
        }
    }

    @Override
    public int getFlags() {
        return flag;
    }

    @Override
    public void setFlags(final int parm) {
        flag = parm;
    }

    private boolean getAscii() {
        return isAscii;
    }

    private void setAscii(final boolean tipo) {
        isAscii = tipo;
    }

    private String getFileName() {
        return fileName;
    }

    private void setFileName(final String filename) {
        fileName = new String(filename);
    }

    private void setObjectName(final String name) {
        objectName = name;
    }

}
