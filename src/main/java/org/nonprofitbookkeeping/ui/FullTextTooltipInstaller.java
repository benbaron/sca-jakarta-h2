package org.nonprofitbookkeeping.ui;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Labeled;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.util.StringConverter;

import java.util.Objects;

/**
 * Installs hover tooltips that expose the full displayed text for non-text-box controls.
 */
final class FullTextTooltipInstaller
{
    private static final String INSTALLED_KEY = FullTextTooltipInstaller.class.getName() + ".installed";
    private static final String AUTO_TOOLTIP_KEY = FullTextTooltipInstaller.class.getName() + ".tooltip";
    private static final double MAX_TOOLTIP_WIDTH = 700.0;

    private FullTextTooltipInstaller()
    {
    }

    static void install(Node root)
    {
        if (root == null)
        {
            return;
        }

        installRecursive(root);
        root.addEventFilter(MouseEvent.MOUSE_ENTERED_TARGET, event -> refreshFromPickedNode(event.getPickResult().getIntersectedNode()));
    }

    static boolean refreshForTests(Node node)
    {
        return refresh(node);
    }

    private static void installRecursive(Node node)
    {
        if (node == null || Boolean.TRUE.equals(node.getProperties().get(INSTALLED_KEY)))
        {
            return;
        }
        node.getProperties().put(INSTALLED_KEY, Boolean.TRUE);

        installTextListeners(node);
        refresh(node);

        if (node instanceof Parent parent)
        {
            for (Node child : parent.getChildrenUnmodifiable())
            {
                installRecursive(child);
            }
            parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
                while (change.next())
                {
                    if (change.wasAdded())
                    {
                        for (Node added : change.getAddedSubList())
                        {
                            installRecursive(added);
                        }
                    }
                }
            });
        }
    }

    private static void installTextListeners(Node node)
    {
        if (node instanceof TextInputControl)
        {
            return;
        }
        if (node instanceof Labeled labeled)
        {
            labeled.textProperty().addListener((observable, oldText, newText) -> refresh(node));
        }
        if (node instanceof ComboBox<?> comboBox)
        {
            comboBox.valueProperty().addListener((observable, oldValue, newValue) -> refresh(node));
            comboBox.buttonCellProperty().addListener((observable, oldCell, newCell) -> refresh(node));
            if (comboBox.getEditor() != null)
            {
                comboBox.getEditor().textProperty().addListener((observable, oldText, newText) -> refresh(node));
            }
        }
        else if (node instanceof DatePicker datePicker)
        {
            datePicker.valueProperty().addListener((observable, oldValue, newValue) -> refresh(node));
            datePicker.getEditor().textProperty().addListener((observable, oldText, newText) -> refresh(node));
        }
        else if (node instanceof ComboBoxBase<?> comboBoxBase)
        {
            comboBoxBase.valueProperty().addListener((observable, oldValue, newValue) -> refresh(node));
            if (comboBoxBase.getEditor() != null)
            {
                comboBoxBase.getEditor().textProperty().addListener((observable, oldText, newText) -> refresh(node));
            }
        }
        if (node instanceof ChoiceBox<?> choiceBox)
        {
            choiceBox.valueProperty().addListener((observable, oldValue, newValue) -> refresh(node));
        }
        if (node instanceof Spinner<?> spinner)
        {
            spinner.valueProperty().addListener((observable, oldValue, newValue) -> refresh(node));
        }
    }

    private static void refreshFromPickedNode(Node picked)
    {
        Node current = picked;
        while (current != null)
        {
            if (refresh(current))
            {
                return;
            }
            current = current.getParent();
        }
    }

    private static boolean refresh(Node node)
    {
        if (!(node instanceof Control control) || node instanceof TextInputControl)
        {
            return false;
        }

        String text = displayedText(node);
        if (text.isBlank())
        {
            clearAutoTooltip(control, node);
            return false;
        }

        Tooltip autoTooltip = autoTooltip(node);
        Tooltip current = control.getTooltip();
        if (current != null && current != autoTooltip)
        {
            return false;
        }

        if (!Objects.equals(autoTooltip.getText(), text))
        {
            autoTooltip.setText(text);
        }
        control.setTooltip(autoTooltip);
        return true;
    }

    private static String displayedText(Node node)
    {
        if (node instanceof Labeled labeled)
        {
            return normalized(labeled.getText());
        }
        if (node instanceof ComboBox<?> comboBox)
        {
            String editorText = comboBox.isEditable() && comboBox.getEditor() != null
                    ? normalized(comboBox.getEditor().getText())
                    : "";
            if (!editorText.isBlank())
            {
                return editorText;
            }
            String buttonCellText = comboBox.getButtonCell() == null
                    ? ""
                    : normalized(comboBox.getButtonCell().getText());
            if (!buttonCellText.isBlank())
            {
                return buttonCellText;
            }
            return converted(comboBox.getConverter(), comboBox.getValue());
        }
        if (node instanceof DatePicker datePicker)
        {
            String editorText = datePicker.getEditor() == null
                    ? ""
                    : normalized(datePicker.getEditor().getText());
            if (!editorText.isBlank())
            {
                return editorText;
            }
            return converted(datePicker.getConverter(), datePicker.getValue());
        }
        if (node instanceof ComboBoxBase<?> comboBoxBase)
        {
            String editorText = comboBoxBase.getEditor() == null
                    ? ""
                    : normalized(comboBoxBase.getEditor().getText());
            if (!editorText.isBlank())
            {
                return editorText;
            }
            return normalized(String.valueOf(comboBoxBase.getValue()));
        }
        if (node instanceof ChoiceBox<?> choiceBox)
        {
            return converted(choiceBox.getConverter(), choiceBox.getValue());
        }
        if (node instanceof Spinner<?> spinner)
        {
            return normalized(String.valueOf(spinner.getValue()));
        }
        return "";
    }

    private static Tooltip autoTooltip(Node node)
    {
        Object existing = node.getProperties().get(AUTO_TOOLTIP_KEY);
        if (existing instanceof Tooltip tooltip)
        {
            return tooltip;
        }

        Tooltip tooltip = new Tooltip();
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(MAX_TOOLTIP_WIDTH);
        node.getProperties().put(AUTO_TOOLTIP_KEY, tooltip);
        return tooltip;
    }

    private static void clearAutoTooltip(Control control, Node node)
    {
        Object existing = node.getProperties().get(AUTO_TOOLTIP_KEY);
        if (existing instanceof Tooltip tooltip && control.getTooltip() == tooltip)
        {
            control.setTooltip(null);
        }
    }

    private static String normalized(String text)
    {
        return text == null ? "" : text.strip();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String converted(StringConverter converter, Object value)
    {
        if (value == null)
        {
            return "";
        }
        if (converter == null)
        {
            return normalized(String.valueOf(value));
        }
        return normalized(converter.toString(value));
    }
}
