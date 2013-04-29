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

    private static final class BooleanChoice extends JPanel {
        private boolean userchoice;
        private JRadioButton trueButton, falseButton;
        private final ButtonGroup bgroup;

        private BooleanChoice(final Boolean boolvalue) {
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

        private String getText() {
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

        private void setValue(final boolean boolvalue) {
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

    PreferencesValue(final JTextField l) {
        textfieldValue = l;
    }

    String getText() {
        if (textfieldValue != null) {
            return textfieldValue.getText();
        }
        if (boolchoiceValue != null) {
            return boolchoiceValue.getText();
        }

        return null;
    }

    void setText(final String str) {
        if (textfieldValue != null) {
            textfieldValue.setText(str);
        }
        if (boolchoiceValue != null) {
            boolchoiceValue.setValue(getBoolFromString(str));
        }
    }

    Component getObject() {
        if (textfieldValue != null) {
            return textfieldValue;
        } else {
            return boolchoiceValue;
        }
    }

    private boolean getBoolFromString(final String strVal) {
        return strVal.compareToIgnoreCase("true") == 0;
    }

    void makeBoolean() {
        final boolean boolvalue = getBoolFromString(getText());
        textfieldValue = null;
        boolchoiceValue = new BooleanChoice(boolvalue);
    }
}
