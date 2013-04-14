package org.reprap.gui;

import java.awt.Button;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

import org.reprap.utilities.Debug;

/**
 * Boxes must contain one of three types
 */
enum Category {
    number, string, bool;
}

/**
 * This reads in the preferences file and constructs a set of menus from it to
 * allow entries to be edited.
 * 
 * Preference keys either start with the string "Extruder" followed by a number
 * and an underscore (that is, they look like "Extruder3_temp(C)") in which case
 * they are assumed to be a characteristic of the extruder with that number; or
 * they don't, in which case they are assumed to be global characteristics of
 * the entire machine.
 * 
 * The keys should end with their dimensions: "Extruder3_temp(C)",
 * "Axis2Scale(steps/mm)", but regrettably can't contain un-escaped space
 * characters (see java.util.Properties).
 * 
 * Some weak type checking is done to prevent obvious crassness being put in the
 * edit boxes. This is done at save time and prevents the junk being written,
 * but doesn't give a chance to correct it.
 * 
 * Extensively adapted from Simon's old version by Adrian to construct itself
 * from the preferences file.
 * 
 */
public class Preferences extends JFrame {
    private static final long serialVersionUID = 1L;
    private int extruderCount;
    private JLabel[] globals; // Array of JLabels for the general key names
    private PreferencesValue[] globalValues; // Array of JTextFields for the general variables
    private Category[] globalCats; // What are they?
    private final JLabel[][] extruders; // Array of Arrays of JLabels for the extruders' key names
    private final PreferencesValue[][] extruderValues; // Array of Arrays of JTextFields for the extruders' variables
    private final Category[][] extruderCats; // What are they?

    public static void main(final String[] args) {
        new Preferences();
    }

    /**
     * Get the value corresponding to name from the preferences file
     */
    private String loadString(final String name) throws IOException {
        return org.reprap.Preferences.loadGlobalString(name);
    }

    /**
     * Save the value corresponding to name to the preferences file
     */
    private void saveString(final String name, final String value) throws IOException {
        org.reprap.Preferences.setGlobalString(name, value);
    }

    public void updatePreferencesValues() {
        try {

            for (int i = 0; i < globals.length; i++) {
                globalValues[i].setText(loadString(globals[i].getText()));
            }

            for (int j = 0; j < extruderCount; j++) {

                final JLabel[] enames = extruders[j];
                for (int i = 0; i < enames.length; i++) {
                    extruderValues[j][i].setText(loadString(enames[i].getText()));
                }
            }

        } catch (final Exception ex) {
            JOptionPane.showMessageDialog(null, "Updating preferences: " + ex);
            ex.printStackTrace();
        }
    }

    /**
     * Save the lot to the preferences file
     */
    public void savePreferences() {
        try {
            for (int i = 0; i < globals.length; i++) {
                final String s = globalValues[i].getText();
                if (category(s) != globalCats[i]) {
                    Debug.getInstance().errorMessage("Preferences window: Dud format for " + globals[i].getText() + ": " + s);
                } else {
                    saveString(globals[i].getText(), s);
                }
            }

            for (int j = 0; j < extruderCount; j++) {
                final JLabel[] enames = extruders[j];
                final PreferencesValue[] evals = extruderValues[j];
                final Category[] cats = extruderCats[j];
                for (int i = 0; i < enames.length; i++) {
                    final String s = evals[i].getText();
                    if (category(s) != cats[i]) {
                        Debug.getInstance().errorMessage("Preferences window: Dud format for " + enames[i].getText() + ": " + s);
                    } else {
                        saveString(enames[i].getText(), s);
                    }
                }
            }

            org.reprap.Preferences.saveGlobal();
        } catch (final Exception ex) {
            JOptionPane.showMessageDialog(null, "Saving preferences: " + ex);
            ex.printStackTrace();
        }
    }

    /**
     * Constructor loads all the information from the preferences file, converts
     * it into arrays of JPanels and JTextFields, then builds the menus from
     * them.
     */
    public Preferences() {
        // Start with everything that isn't an extruder value.
        try {
            final String[] g = org.reprap.Preferences.notStartsWith("Extruder");
            Arrays.sort(g);
            globals = makeLabels(g);
            globalValues = makeValues(globals);
            globalCats = categorise(globalValues);
        } catch (final Exception ex) {
            Debug.getInstance().errorMessage("Preferences window: Can't load the globals!");
            ex.printStackTrace();
        }

        // Next we need to know how many extruders we've got.
        try {
            extruderCount = Integer.parseInt(loadString("NumberOfExtruders"));
        } catch (final Exception ex) {
            Debug.getInstance().errorMessage("Preferences window: Can't load the extruder count!");
            ex.printStackTrace();
        }

        // Now build a set of arrays for each extruder in turn.
        extruders = new JLabel[extruderCount][];
        extruderValues = new PreferencesValue[extruderCount][];
        extruderCats = new Category[extruderCount][];
        try {
            for (int i = 0; i < extruderCount; i++) {
                final String[] a = org.reprap.Preferences.startsWith("Extruder" + i);
                Arrays.sort(a);
                extruders[i] = makeLabels(a);
                extruderValues[i] = makeValues(extruders[i]);
                extruderCats[i] = categorise(extruderValues[i]);
            }
        } catch (final Exception ex) {
            Debug.getInstance().errorMessage("Preferences window: Can't load extruder(s)!");
            ex.printStackTrace();
        }

        // Paint the lot on the screen...
        initGUI();
    }

    private JButton OKButton() {
        final JButton jButtonOK = new JButton();
        jButtonOK.setText("OK");
        jButtonOK.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent evt) {
                jButtonOKMouseClicked();
            }
        });
        return jButtonOK;
    }

    private JButton CancelButton() {
        final JButton jButtonCancel = new JButton();
        jButtonCancel.setText("Cancel");
        jButtonCancel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent evt) {
                jButtonCancelMouseClicked();
            }
        });
        return jButtonCancel;
    }

    private void addValueToPanel(final PreferencesValue value, final JPanel panel) {

        if (isBoolean(value.getText())) {

            value.makeBoolean();

            panel.add(value.getObject());
        } else {
            panel.add(value.getObject());
        }
    }

    /**
     * Set up the panels with all the right boxes in
     */
    private void initGUI() {
        setSize(400, 500);
        try {
            final JPanel panel = new JPanel();
            String[] configfiles = { "reprap.properties" };
            final File dir = new File(org.reprap.Preferences.getMachineFilePath());
            if (dir.list() != null) {
                configfiles = dir.list();
                for (int i = 0; i < configfiles.length; i++) {
                    if (configfiles[i].indexOf(".properties") != -1) {
                        configfiles[i] = configfiles[i].substring(0, configfiles[i].indexOf(".properties"));
                    }
                }
            }

            final JComboBox<String> configfileList = new JComboBox<String>(configfiles);
            configfileList.setEditable(true);

            String configName = org.reprap.Preferences.getDefaultPropsFile();
            configName = configName.substring(0, configName.indexOf(".properties"));
            configfileList.setSelectedItem(configName);
            configfileList.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent e) {
                    if ("comboBoxChanged".equals(e.getActionCommand())) {
                        final String configToLoad = (String) configfileList.getSelectedItem() + ".properties";
                        final String configPath = org.reprap.Preferences.getUsersRootDir() + configToLoad;
                        if ((new File(configPath)).exists()) {
                            Debug.getInstance().debugMessage("loading config " + configToLoad);
                            org.reprap.Preferences.loadConfig(configToLoad);
                            updatePreferencesValues();
                        }
                    }
                }
            });

            panel.add(new JLabel("preferences file:"));
            final Button prefCreateButton = new Button("create");
            prefCreateButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent e) {
                    final String configToLoad = (String) configfileList.getSelectedItem() + ".properties";
                    final String configPath = org.reprap.Preferences.getUsersRootDir() + configToLoad;
                    final File configFileObj = new File(configPath);
                    if (!configFileObj.exists()) {
                        configfileList.addItem((String) configfileList.getSelectedItem());
                        Debug.getInstance().printMessage("loading config " + configToLoad);
                        org.reprap.Preferences.loadConfig(configToLoad);
                        updatePreferencesValues();
                    }
                }
            });

            final Button prefDeleteButton = new Button("delete");
            prefDeleteButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent e) {
                    String configToDelete = (String) configfileList.getSelectedItem() + ".properties";
                    if (!configToDelete.equals("reprap.properties")) {
                        final String configPath = org.reprap.Preferences.getUsersRootDir() + configToDelete;
                        final File configFileObj = new File(configPath);
                        if (configFileObj.exists()) {
                            configFileObj.delete();
                            configfileList.removeItem(configfileList.getSelectedItem());
                            updatePreferencesValues();
                        } else {
                            configToDelete = org.reprap.Preferences.getDefaultPropsFile();
                            configToDelete = configToDelete.substring(0, configToDelete.indexOf(".properties"));
                            configfileList.setSelectedItem(configToDelete);
                        }
                    }
                }
            });

            panel.add(configfileList);
            panel.add(prefCreateButton);
            panel.add(prefDeleteButton);

            // We'll have a tab for the globals, then one for each extruder
            final Box prefDiffBox = new Box(1);
            final JTabbedPane jTabbedPane1 = new JTabbedPane();
            prefDiffBox.add(panel);
            prefDiffBox.add(jTabbedPane1);
            add(prefDiffBox);

            // Do the global panel
            final JPanel jPanelGeneral = new JPanel();
            final JScrollPane jScrollPaneGeneral = new JScrollPane(jPanelGeneral);

            boolean odd = globals.length % 2 != 0;
            int rows;
            if (odd) {
                rows = globals.length / 2 + 2;
            } else {
                rows = globals.length / 2 + 1;
            }
            jPanelGeneral.setLayout(new GridLayout(rows, 4, 5, 5));

            jTabbedPane1.addTab("Globals", null, jScrollPaneGeneral, null);

            // Do it in two chunks, so they're vertically ordered, not horizontally
            int half = globals.length / 2;
            int next;
            int i;
            for (i = 0; i < half; i++) {
                jPanelGeneral.add(globals[i]);
                addValueToPanel(globalValues[i], jPanelGeneral);

                next = i + half;
                if (next < globals.length) {
                    jPanelGeneral.add(globals[next]);
                    addValueToPanel(globalValues[next], jPanelGeneral);
                }
            }

            if (odd) {
                jPanelGeneral.add(globals[globals.length - 1]);
                jPanelGeneral.add(globalValues[globals.length - 1].getObject());
                jPanelGeneral.add(new JLabel());
                jPanelGeneral.add(new JLabel());
            }
            jPanelGeneral.add(OKButton());
            jPanelGeneral.add(new JLabel());
            jPanelGeneral.add(new JLabel());
            jPanelGeneral.add(CancelButton());
            jPanelGeneral.setSize(600, 700);

            // Do all the extruder panels
            for (int j = 0; j < extruderCount; j++) {
                final JLabel[] keys = extruders[j];
                final PreferencesValue[] values = extruderValues[j];

                final JPanel jPanelExtruder = new JPanel();
                final JScrollPane jScrollPaneExtruder = new JScrollPane(jPanelExtruder);

                odd = keys.length % 2 != 0;
                if (odd) {
                    rows = keys.length / 2 + 2;
                } else {
                    rows = keys.length / 2 + 1;
                }
                jPanelExtruder.setLayout(new GridLayout(rows, 4, 5, 5));
                jTabbedPane1.addTab(loadString("Extruder" + j + "_MaterialType(name)"), null, jScrollPaneExtruder, null);
                // Do it in two chunks, so they're vertically ordered, not horizontally
                half = keys.length / 2;
                for (i = 0; i < keys.length / 2; i++) {
                    jPanelExtruder.add(keys[i]);
                    addValueToPanel(values[i], jPanelExtruder);
                    next = i + half;
                    if (next < keys.length) {
                        jPanelExtruder.add(keys[next]);
                        addValueToPanel(values[next], jPanelExtruder);
                    }
                }

                if (odd) {
                    jPanelExtruder.add(keys[keys.length - 1]);
                    jPanelExtruder.add(values[keys.length - 1].getObject());
                    jPanelExtruder.add(new JLabel());
                    jPanelExtruder.add(new JLabel());
                }
                jPanelExtruder.add(OKButton());
                jPanelExtruder.add(new JLabel());
                jPanelExtruder.add(new JLabel());
                jPanelExtruder.add(CancelButton());
                jPanelExtruder.setSize(600, 700);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }

        // Wrap it all up
        setTitle("RepRap Preferences");
        pack();
    }

    /**
     * What to do when OK is clicked
     */
    private void jButtonOKMouseClicked() {
        savePreferences();
        dispose();
    }

    /**
     * What to do when Cancel is clicked
     */
    private void jButtonCancelMouseClicked() {
        dispose();
    }

    private String removeExtruder(final String s) {
        return s;
    }

    /**
     * Take an array of strings and turn them into labels (right justified).
     */
    private JLabel[] makeLabels(final String[] a) {
        final JLabel[] result = new JLabel[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = new JLabel();
            result[i].setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
            result[i].setText(removeExtruder(a[i]));
        }
        return result;
    }

    /**
     * Take an array of labels and use their string values as keys to look up
     * the corresponding values. Make those into an array of editable boxes.
     */
    private PreferencesValue[] makeValues(final JLabel[] a) {
        final PreferencesValue[] result = new PreferencesValue[a.length];
        String value;
        for (int i = 0; i < a.length; i++) {
            try {
                value = loadString(a[i].getText());

                result[i] = new PreferencesValue(new JTextField());
                result[i].setText(value);

            } catch (final Exception ex) {
                ex.printStackTrace();
            }
        }
        return result;
    }

    /**
     * Is a string saying a boolean?
     */
    private boolean isBoolean(final String s) {
        if (s.equalsIgnoreCase("true")) {
            return true;
        }
        if (s.equalsIgnoreCase("false")) {
            return true;
        }
        return false;
    }

    /**
     * Is a string a number (int or double)?
     * 
     * There must be a better way to do this; also this doesn't allow for
     * 1.3e-5...
     */
    private boolean isNumber(final String s) {
        if ((s == null) || (s.length() == 0)) {
            return false;
        }

        int start = 0;
        while (Character.isSpaceChar(s.charAt(start))) {
            start++;
        }
        if (s.charAt(start) == '-' || s.charAt(start) == '+') {
            start++;
        }
        // Last we checked, only one decimal point allowed per number.
        int dotCount = 0;
        for (int i = start; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (!Character.isDigit(c)) {
                if (c != '.') {
                    return false;
                } else {
                    dotCount++;
                    if (dotCount > 1) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Find if a string is a boolean, a number, or a string
     */
    private Category category(final String s) {
        if (isBoolean(s)) {
            return Category.bool;
        }

        if (isNumber(s)) {
            return Category.number;
        }

        return Category.string;
    }

    /**
     * Generate an array of categories corresponsing to the text in an array of
     * edit boxes so they can be checked later.
     */
    private Category[] categorise(final PreferencesValue[] a) {
        final Category[] result = new Category[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = category(a[i].getText());
        }

        return result;
    }
}
