package org.reprap.gui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import org.reprap.Attributes;
import org.reprap.Preferences;
import org.reprap.geometry.polyhedra.STLObject;
import org.reprap.utilities.Debug;

/**
 * Radio button menu so you can set what material something is to be made from.
 * 
 * @author adrian
 */
class MaterialRadioButtons extends JPanel {
    private static final long serialVersionUID = 1L;
    private static Attributes att;
    private static JDialog dialog;
    private static JTextField copies;
    private static RepRapBuild rrb;
    private static int stlIndex;

    private MaterialRadioButtons(final double volume) {
        super(new BorderLayout());
        JPanel radioPanel;
        final ButtonGroup bGroup = new ButtonGroup();
        String[] names;
        radioPanel = new JPanel(new GridLayout(0, 1));
        radioPanel.setSize(300, 200);

        final JLabel jLabel0 = new JLabel();
        radioPanel.add(jLabel0);
        jLabel0.setText("Volume of object: " + Math.round(volume) + " mm^3");
        jLabel0.setHorizontalAlignment(SwingConstants.CENTER);

        final JLabel jLabel2 = new JLabel();
        radioPanel.add(jLabel2);
        jLabel2.setText(" Number of copies of the object just loaded to print: ");
        jLabel2.setHorizontalAlignment(SwingConstants.CENTER);
        copies = new JTextField("1");
        copies.setSize(20, 10);
        copies.setHorizontalAlignment(SwingConstants.CENTER);
        radioPanel.add(copies);

        final JLabel jLabel1 = new JLabel();
        radioPanel.add(jLabel1);
        jLabel1.setText(" Select the material for the object(s): ");
        jLabel1.setHorizontalAlignment(SwingConstants.CENTER);

        try {
            names = Preferences.allMaterials();
            String matname = att.getMaterial();
            if (matname == null) {
                matname = "";
            }
            int matnumber = -1;
            for (int i = 0; i < names.length; i++) {
                if (matname.contentEquals(names[i])) {
                    matnumber = i;
                }
                final JRadioButton b = new JRadioButton(names[i]);
                b.setActionCommand(names[i]);
                b.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(final ActionEvent e) {
                        att.setMaterial(e.getActionCommand());
                    }
                });
                if (i == matnumber) {
                    b.setSelected(true);
                }
                bGroup.add(b);
                radioPanel.add(b);
            }
            if (matnumber < 0) {
                att.setMaterial(names[0]);
                final JRadioButton b = (JRadioButton) bGroup.getElements().nextElement();
                b.setSelected(true);
            } else {
                copies.setEnabled(false); // If it's already loaded, don't make multiple copies (FUTURE: why not...?)
            }

            final JButton okButton = new JButton();
            radioPanel.add(okButton);
            okButton.setText("OK");
            okButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent evt) {
                    OKHandler();
                }
            });

            add(radioPanel, BorderLayout.LINE_START);
            setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        } catch (final Exception ex) {
            Debug.getInstance().errorMessage(ex.toString());
            ex.printStackTrace();
        }
    }

    private static void OKHandler() {
        final int number = Integer.parseInt(copies.getText().trim()) - 1;
        final STLObject stl = rrb.getSTLs().get(stlIndex);
        rrb.moreCopies(stl, att, number);
        dialog.dispose();
    }

    static void createAndShowGUI(final Attributes a, final RepRapBuild r, final int index, final double volume) {
        att = a;
        rrb = r;
        stlIndex = index;
        //Create and set up the window.
        final JFrame f = new JFrame();
        dialog = new JDialog(f, "Material selector");
        dialog.setLocation(500, 400);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        //Create and set up the content pane.
        final JComponent newContentPane = new MaterialRadioButtons(volume);
        newContentPane.setOpaque(true); //content panes must be opaque
        dialog.setContentPane(newContentPane);

        //Display the window.
        dialog.pack();
        dialog.setModalityType(Dialog.DEFAULT_MODALITY_TYPE);
        dialog.setVisible(true);
    }

    static void createAndShowGUI(final Attributes a, final RepRapBuild r, final STLObject lastPicked) {
        if (lastPicked == null) {
            return;
        }
        int index = -1;
        for (int i = 0; i < r.getSTLs().size(); i++) {
            if (r.getSTLs().get(i) == lastPicked) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            createAndShowGUI(a, r, index, r.getSTLs().get(index).volume());
        }
    }
}