package org.nonprofitbookkeeping.ui;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Wizard for database manipulation actions.
 */
final class DatabaseWizardDialog
{
    enum Action
    {
        CREATE_NEW("Create New Database"),
        SWITCH_EXISTING("Switch Existing Database");

        private final String label;

        Action(String label)
        {
            this.label = label;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    record Result(Action action, Path databasePath)
    {
    }

    private DatabaseWizardDialog()
    {
    }

    static Optional<Result> show(Window owner)
    {
        ChoiceDialog<Action> actionDialog = new ChoiceDialog<>(Action.CREATE_NEW, Action.values());
        actionDialog.setTitle("Database Wizard");
        actionDialog.setHeaderText("Step 1 of 2: Choose database action");
        actionDialog.setContentText("Action:");
        if (owner != null)
        {
            actionDialog.initOwner(owner);
        }

        Optional<Action> selected = actionDialog.showAndWait();
        if (selected.isEmpty())
        {
            return Optional.empty();
        }

        Dialog<String> pathDialog = new Dialog<>();
        pathDialog.setTitle("Database Wizard");
        pathDialog.setHeaderText("Step 2 of 2: Enter database file path (.mv.db preferred)");
        if (owner != null)
        {
            pathDialog.initOwner(owner);
        }

        TextField pathField = new TextField("data/sca-ledger.mv.db");
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label("Database path:"), 0, 0);
        grid.add(pathField, 1, 0);
        pathDialog.getDialogPane().setContent(grid);

        ButtonType finish = new ButtonType("Finish", ButtonBar.ButtonData.OK_DONE);
        pathDialog.getDialogPane().getButtonTypes().addAll(finish, ButtonType.CANCEL);
        pathDialog.setResultConverter(bt -> bt == finish ? pathField.getText() : null);

        Optional<String> rawPath = pathDialog.showAndWait();
        if (rawPath.isEmpty() || rawPath.get().isBlank())
        {
            return Optional.empty();
        }

        Path path = Path.of(rawPath.get().trim());
        if (selected.get() == Action.CREATE_NEW && !path.toString().endsWith(".mv.db"))
        {
            path = Path.of(path.toString() + ".mv.db");
        }

        return Optional.of(new Result(selected.get(), path));
    }
}
