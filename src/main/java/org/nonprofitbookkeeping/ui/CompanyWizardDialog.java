package org.nonprofitbookkeeping.ui;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Wizard for company manipulation actions inside the active database.
 */
final class CompanyWizardDialog
{
    enum Action
    {
        ADD_COMPANY("Add Company"),
        SWITCH_ACTIVE_COMPANY("Switch Active Company");

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

    record Result(Action action, String companyCode)
    {
    }

    private CompanyWizardDialog()
    {
    }

    static Optional<Result> show(Window owner, String currentCompany)
    {
        ChoiceDialog<Action> actionDialog = new ChoiceDialog<>(Action.ADD_COMPANY, Action.values());
        actionDialog.setTitle("Company Wizard");
        actionDialog.setHeaderText("Step 1 of 2: Choose company action (within current database)");
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

        Dialog<String> companyDialog = new Dialog<>();
        companyDialog.setTitle("Company Wizard");
        companyDialog.setHeaderText("Step 2 of 2: Enter company code");
        if (owner != null)
        {
            companyDialog.initOwner(owner);
        }

        TextField companyField = new TextField(currentCompany == null || currentCompany.isBlank() ? "DEFAULT" : currentCompany);
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label("Company code:"), 0, 0);
        grid.add(companyField, 1, 0);
        companyDialog.getDialogPane().setContent(grid);

        ButtonType finish = new ButtonType("Finish", ButtonBar.ButtonData.OK_DONE);
        companyDialog.getDialogPane().getButtonTypes().addAll(finish, ButtonType.CANCEL);
        companyDialog.setResultConverter(bt -> bt == finish ? companyField.getText() : null);

        Optional<String> code = companyDialog.showAndWait();
        if (code.isEmpty() || code.get().isBlank())
        {
            return Optional.empty();
        }

        return Optional.of(new Result(selected.get(), code.get().trim().toUpperCase()));
    }
}
