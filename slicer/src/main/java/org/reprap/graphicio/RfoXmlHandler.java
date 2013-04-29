package org.reprap.graphicio;

import javax.media.j3d.Transform3D;

import org.reprap.Preferences;
import org.reprap.geometry.polyhedra.STLObject;
import org.reprap.utilities.Debug;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

final class RfoXmlHandler extends DefaultHandler {
    /**
     * The rfo that we are reading in
     */
    private final RFO rfo;

    /**
     * The STL being read
     */
    private STLObject stl;

    /**
     * The first of a list of STLs being read.
     */
    private STLObject firstSTL;
    /**
     * The current XML item
     */
    private String element;

    /**
     * File location for reading (eg for an input STL file).
     */
    private String location;

    /**
     * What type of file (Only STLs supported at the moment).
     */
    private String filetype;

    /**
     * The name of the material (i.e. extruder) that this item is made from.
     */
    private String material;

    /**
     * Transfom matrix to get an item in the right place.
     */
    private final double[] mElements;
    private Transform3D transform;

    private int rowNumber = 0;

    /**
     * Open up legendFile and use it to build RFO rfo.
     * 
     * @param legendFile
     * @param r
     */
    RfoXmlHandler(final String legendFile, final RFO r) {
        super();
        rfo = r;
        element = "";
        location = "";
        filetype = "";
        material = "";
        mElements = new double[16];
        setMToIdentity();

        XMLReader xr = null;
        try {
            xr = XMLReaderFactory.createXMLReader();
        } catch (final Exception e) {
            Debug.getInstance().errorMessage("XMLIn() 1: " + e);
        }

        xr.setContentHandler(this);
        xr.setErrorHandler(this);
        try {
            xr.parse(new InputSource(legendFile));
        } catch (final Exception e) {
            Debug.getInstance().errorMessage("XMLIn() 2: " + e);
        }

    }

    /**
     * Initialise the matrix to the identity matrix.
     */
    private void setMToIdentity() {
        for (rowNumber = 0; rowNumber < 4; rowNumber++) {
            for (int column = 0; column < 4; column++) {
                if (rowNumber == column) {
                    mElements[rowNumber * 4 + column] = 1;
                } else {
                    mElements[rowNumber * 4 + column] = 0;
                }
            }
        }
        transform = new Transform3D(mElements);
        rowNumber = 0;
    }

    /**
     * Start an element
     */
    @Override
    public void startElement(final String uri, final String name, final String qName, final org.xml.sax.Attributes atts) {
        if (uri.equals("")) {
            element = qName;
        } else {
            element = name;
        }

        if (element.equalsIgnoreCase("reprap-fab-at-home-build")) {
        } else if (element.equalsIgnoreCase("object")) {
            stl = new STLObject();
            firstSTL = null;
        } else if (element.equalsIgnoreCase("files")) {
        } else if (element.equalsIgnoreCase("file")) {
            location = atts.getValue("location");
            filetype = atts.getValue("filetype");
            material = atts.getValue("material");
            if (!filetype.equalsIgnoreCase("application/sla")) {
                Debug.getInstance().errorMessage(
                        "XMLIn.startElement(): unreconised object file type (should be \"application/sla\"): " + filetype);
            }
        } else if (element.equalsIgnoreCase("transform3D")) {
            setMToIdentity();
        } else if (element.equalsIgnoreCase("row")) {
            for (int column = 0; column < 4; column++) {
                mElements[rowNumber * 4 + column] = Double.parseDouble(atts.getValue("m" + rowNumber + column));
            }
        } else {
            Debug.getInstance().errorMessage("XMLIn.startElement(): unreconised RFO element: " + element);
        }
    }

    @Override
    public void endElement(final String uri, final String name, final String qName) {
        if (uri.equals("")) {
            element = qName;
        } else {
            element = name;
        }
        if (element.equalsIgnoreCase("reprap-fab-at-home-build")) {

        } else if (element.equalsIgnoreCase("object")) {
            stl.setTransform(transform);
            rfo.getAstl().add(stl);
        } else if (element.equalsIgnoreCase("files")) {

        } else if (element.equalsIgnoreCase("file")) {
            final org.reprap.Attributes att = stl.addSTL(rfo.getRfoDir() + location, null, Preferences.unselectedApp(),
                    firstSTL);
            if (firstSTL == null) {
                firstSTL = stl;
            }
            att.setMaterial(material);
            location = "";
            filetype = "";
            material = "";

        } else if (element.equalsIgnoreCase("transform3D")) {
            if (rowNumber != 4) {
                Debug.getInstance().errorMessage(
                        "XMLIn.endElement(): incomplete Transform3D matrix - last row number is not 4: " + rowNumber);
            }
            transform = new Transform3D(mElements);
        } else if (element.equalsIgnoreCase("row")) {
            rowNumber++;
        } else {
            Debug.getInstance().errorMessage("XMLIn.endElement(): unreconised RFO element: " + element);
        }
    }
}