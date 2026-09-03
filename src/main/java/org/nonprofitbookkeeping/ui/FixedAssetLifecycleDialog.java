package org.nonprofitbookkeeping.ui;

import javafx.collections.FXCollections;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.FixedAssetLifecycleEvent;
import org.nonprofitbookkeeping.service.FixedAssetLifecycleCommand;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Collects lifecycle facts; authoritative calculation remains in {@code FixedAssetService}. */
final class FixedAssetLifecycleDialog
{
    private FixedAssetLifecycleDialog()
    {
    }

    static Request show(List<Account> postingAccounts, CompanyUiFormat companyFormat)
    {
        Dialog<Request> dialog = new Dialog<>();
        dialog.setTitle("Fixed-Asset Lifecycle Event");
        dialog.setHeaderText("Preview a Sale, Retirement, or Impairment before committing accounting.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<FixedAssetLifecycleEvent.EventType> type = new ComboBox<>(
                FXCollections.observableArrayList(FixedAssetLifecycleEvent.EventType.values()));
        type.setValue(FixedAssetLifecycleEvent.EventType.SALE);
        DatePicker date = new DatePicker(LocalDate.now());
        TextField proceeds = new TextField(companyFormat.formatMoney(BigDecimal.ZERO));
        TextField impairment = new TextField(companyFormat.formatMoney(BigDecimal.ZERO));
        ComboBox<Account> proceedsAccount = accountBox(postingAccounts.stream()
                .filter(a -> a.getAccountType() == AccountType.ASSET)
                .toList());
        ComboBox<Account> gainAccount = accountBox(postingAccounts.stream()
                .filter(a -> a.getAccountType() == AccountType.INCOME)
                .toList());
        ComboBox<Account> lossAccount = accountBox(postingAccounts.stream()
                .filter(a -> a.getAccountType() == AccountType.EXPENSE)
                .toList());
        TextField actor = new TextField(DesktopActorIdentity.current());
        actor.setEditable(false);
        TextArea notes = new TextArea();
        notes.setPrefRowCount(3);

        Runnable applyMode = () -> {
            FixedAssetLifecycleEvent.EventType selected = type.getValue();
            boolean sale = selected == FixedAssetLifecycleEvent.EventType.SALE;
            boolean impairmentEvent = selected == FixedAssetLifecycleEvent.EventType.IMPAIRMENT;
            proceeds.setDisable(!sale);
            proceedsAccount.setDisable(!sale);
            gainAccount.setDisable(!sale);
            impairment.setDisable(!impairmentEvent);
            if (!sale)
            {
                proceeds.setText(companyFormat.formatMoney(BigDecimal.ZERO));
                proceedsAccount.getSelectionModel().clearSelection();
                gainAccount.getSelectionModel().clearSelection();
            }
            if (!impairmentEvent)
            {
                impairment.setText(companyFormat.formatMoney(BigDecimal.ZERO));
            }
        };
        type.valueProperty().addListener((obs, old, value) -> applyMode.run());
        applyMode.run();

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        int row = 0;
        row = addRow(grid, row, "Operation", type);
        row = addRow(grid, row, "Date", date);
        row = addRow(grid, row, "Proceeds", proceeds);
        row = addRow(grid, row, "Proceeds account", proceedsAccount);
        row = addRow(grid, row, "Gain account", gainAccount);
        row = addRow(grid, row, "Loss / impairment account", lossAccount);
        row = addRow(grid, row, "Impairment amount", impairment);
        row = addRow(grid, row, "Actor", actor);
        addRow(grid, row, "Notes / reason", notes);
        companyFormat.install(date);
        companyFormat.installMoney(proceeds);
        companyFormat.installMoney(impairment);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(620);
        CompanyDialogUiCompliance.install(dialog.getDialogPane(), AppPanelId.ASSETS_REGISTER);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK)
            {
                return null;
            }
            FixedAssetLifecycleCommand command = new FixedAssetLifecycleCommand(
                    type.getValue(),
                    date.getValue(),
                    money(companyFormat, proceeds.getText()),
                    money(companyFormat, impairment.getText()),
                    selectedId(proceedsAccount),
                    selectedId(gainAccount),
                    selectedId(lossAccount),
                    notes.getText());
            return new Request(command, required(actor.getText(), "Actor"));
        });
        return dialog.showAndWait().orElse(null);
    }

    static ReversalRequest showReversal(CompanyUiFormat companyFormat)
    {
        Dialog<ReversalRequest> dialog = new Dialog<>();
        dialog.setTitle("Reverse Fixed-Asset Lifecycle Event");
        dialog.setHeaderText("Create a canonical reversal and restore the governed asset state.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DatePicker date = new DatePicker(LocalDate.now());
        TextField reason = new TextField();
        TextField actor = new TextField(DesktopActorIdentity.current());
        actor.setEditable(false);
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        int row = 0;
        row = addRow(grid, row, "Reversal date", date);
        row = addRow(grid, row, "Reason", reason);
        addRow(grid, row, "Actor", actor);
        companyFormat.install(date);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(540);
        CompanyDialogUiCompliance.install(dialog.getDialogPane(), AppPanelId.ASSETS_REGISTER);
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new ReversalRequest(
                date.getValue(),
                required(reason.getText(), "Reason"),
                required(actor.getText(), "Actor"))
                : null);
        return dialog.showAndWait().orElse(null);
    }

    private static ComboBox<Account> accountBox(List<Account> accounts)
    {
        ComboBox<Account> box = new ComboBox<>(FXCollections.observableArrayList(accounts));
        box.setMaxWidth(Double.MAX_VALUE);
        box.setConverter(new StringConverter<>()
        {
            @Override
            public String toString(Account account)
            {
                return account == null ? "" : AssetsRegisterPanel.accountLabel(account);
            }

            @Override
            public Account fromString(String value)
            {
                return null;
            }
        });
        return box;
    }

    private static int addRow(GridPane grid, int row, String label, javafx.scene.Node value)
    {
        grid.add(new Label(label), 0, row);
        grid.add(value, 1, row);
        GridPane.setHgrow(value, Priority.ALWAYS);
        return row + 1;
    }

    private static Long selectedId(ComboBox<Account> box)
    {
        return box.getValue() == null ? null : box.getValue().getId();
    }

    private static BigDecimal money(CompanyUiFormat companyFormat, String value)
    {
        BigDecimal parsed = companyFormat.parseMoney(value);
        if (parsed == null)
        {
            throw new IllegalArgumentException("Enter a valid monetary amount");
        }
        return parsed;
    }

    private static String required(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    record Request(FixedAssetLifecycleCommand command, String actor)
    {
    }

    record ReversalRequest(LocalDate date, String reason, String actor)
    {
    }
}
