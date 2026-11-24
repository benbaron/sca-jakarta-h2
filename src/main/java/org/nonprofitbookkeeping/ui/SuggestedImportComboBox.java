package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import java.util.List;

/** A real reference dropdown whose empty value can display an unresolved CSV hint. */
final class SuggestedImportComboBox extends ComboBox<TransactionLineEditorModel.Option>
{
    private final boolean optional;
    private final Runnable changed;
    private final Button clear = new Button("Clear");
    private final Button add = new Button("Add…");
    private final ListCell<TransactionLineEditorModel.Option> display;
    private ImportReferenceSuggestion suggestion;
    private boolean updating;

    SuggestedImportComboBox(boolean optional, Runnable changed)
    {
        this.optional = optional;
        this.changed = changed;
        getStyleClass().add("import-reference-combo");
        setPrefWidth(140);
        setMinWidth(0);
        setCellFactory(ignored -> new ListCell<>()
        {
            @Override
            protected void updateItem(TransactionLineEditorModel.Option item, boolean empty)
            {
                super.updateItem(item, empty);
                String text = empty || item == null ? "" : item.label();
                setText(text);
                setTooltip(text.isBlank() ? null : new Tooltip(text));
            }
        });
        display = new ListCell<>()
        {
            @Override
            protected void updateItem(TransactionLineEditorModel.Option item, boolean empty)
            {
                super.updateItem(item, empty);
                render();
            }
        };
        setButtonCell(display);
        valueProperty().addListener((o, before, after) ->
        {
            if (!updating && suggestion != null)
            {
                suggestion.choose(after);
                render();
                changed.run();
            }
        });
        clear.setTooltip(new Tooltip("Do not import this lookup value. The original CSV text remains in source evidence."));
        clear.setOnAction(event ->
        {
            if (suggestion != null)
            {
                suggestion.clear();
                show(suggestion);
                changed.run();
            }
        });
    }

    Node field()
    {
        clear.setId(getId() + "Clear");
        add.setId(getId() + "Add");
        HBox box = optional ? new HBox(5, this, clear, add) : new HBox(5, this, add);
        HBox.setHgrow(this, Priority.ALWAYS);
        return box;
    }

    void configureAdd(String destination, Runnable action)
    {
        add.setTooltip(new Tooltip("Open " + destination + " with the unresolved suggestion copied literally into Name. Code and other required details remain manual. Existing unsaved destination edits are retained. Return to Import to reload choices; this draft stays here. Available for a selected, editable draft in its original company while no operation is running."));
        add.setAccessibleText("Add a record in " + destination);
        add.setOnAction(event -> action.run());
        UiPermissionGate.gate(add, org.nonprofitbookkeeping.service.ApplicationPermission.BOOKKEEPING_WRITE,
                "Add a record in " + destination);
    }

    void setAddUnavailable(boolean unavailable)
    {
        add.setDisable(unavailable);
    }

    String nameForNewRecord()
    {
        return suggestion == null ? "" : suggestion.nameForNewRecord();
    }

    void show(ImportReferenceSuggestion value)
    {
        updating = true;
        suggestion = value;
        setValue(value == null ? null : value.value());
        updating = false;
        render();
    }

    void replaceChoices(List<TransactionLineEditorModel.Option> choices)
    {
        updating = true;
        getItems().setAll(choices);
        setValue(suggestion == null ? null : suggestion.value());
        updating = false;
        render();
    }

    private void render()
    {
        if (display == null)
        {
            return;
        }
        boolean proposed = suggestion != null && suggestion.suggested();
        String text = suggestion == null ? "" : suggestion.displayText();
        if (text.isBlank() && optional && suggestion != null)
        {
            text = "None";
        }
        display.setText(text);
        display.getStyleClass().remove("import-source-suggestion");
        if (proposed)
        {
            display.getStyleClass().add("import-source-suggestion");
        }
        String explanation = proposed ? "Unmapped CSV value: " + text
                + ". Select a saved record" + (optional ? " or Clear to omit this value." : ".") : text;
        display.setTooltip(explanation.isBlank() ? null : new Tooltip(explanation));
        setTooltip(explanation.isBlank() ? null : new Tooltip(explanation));
        setAccessibleText(explanation);
        clear.setDisable(suggestion == null || suggestion.value() == null && !suggestion.suggested());
    }
}
