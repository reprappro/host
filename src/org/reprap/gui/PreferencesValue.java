package org.reprap.gui;

import java.awt.Component;
import java.awt.GridLayout;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

public class PreferencesValue {

    private JTextField textfieldValue = null;
    private BooleanChoice boolchoiceValue = null;

    public class BooleanChoice extends JPanel {
        private boolean userchoice;
        private JRadioButton trueButton, falseButton;
        private final ButtonGroup bgroup;

        public BooleanChoice(final Boolean boolvalue) {

            if (boolvalue == true) {
                trueButton = new JRadioButton("True", true);
            } else {
                trueButton = new JRadioButton("True", false);
            }

            if (boolvalue == false) {
                falseButton = new JRadioButton("False", true);
            } else {
                falseButton = new JRadioButton("False", false);
            }

            bgroup = new ButtonGroup();
            bgroup.add(trueButton);
            bgroup.add(falseButton);

            setLayout(new GridLayout(1, 2));
            this.add(trueButton);
            this.add(falseButton);

            userchoice = boolvalue;

        }

        public String getText() {

            if (bgroup.isSelected(trueButton.getModel())) {
                userchoice = true;
            } else {
                userchoice = false;
            }

            if (userchoice) {
                return "true";
            } else {
                return "false";
            }
        }

        public void setValue(final boolean boolvalue) {
            if (boolvalue == true) {
                trueButton.setSelected(true);
            } else {
                trueButton.setSelected(false);
            }

            if (boolvalue == false) {
                falseButton.setSelected(true);
            } else {
                falseButton.setSelected(false);
            }
        }
    }

    public PreferencesValue(final JTextField l) {
        textfieldValue = l;
    }

    public PreferencesValue(final BooleanChoice b) {
        boolchoiceValue = b;
    }

    public String getText() {
        if (textfieldValue != null) {
            return textfieldValue.getText();
        }
        if (boolchoiceValue != null) {
            return boolchoiceValue.getText();
        }

        return null;
    }

    public void setText(final String str) {
        if (textfieldValue != null) {
            textfieldValue.setText(str);
        }
        if (boolchoiceValue != null) {
            boolchoiceValue.setValue(getBoolFromString(str));
        }
    }

    public Component getObject() {
        if (textfieldValue != null) {
            return textfieldValue;
        } else {
            return boolchoiceValue;
        }
    }

    private boolean getBoolFromString(final String strVal) {

        if (strVal.compareToIgnoreCase("true") == 0) {
            return true;
        }

        return false;
    }

    public void makeBoolean() {
        final boolean boolvalue = getBoolFromString(getText());
        textfieldValue = null;
        boolchoiceValue = new BooleanChoice(boolvalue);
    }
}
