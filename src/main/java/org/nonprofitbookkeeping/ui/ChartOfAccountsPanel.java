package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;

import java.util.Objects;

/**
 * Represents the ChartOfAccountsPanel component in the nonprofit bookkeeping application.
 */
public class ChartOfAccountsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<Account> table = new TableView<>();
    private final Label status = new Label();
    private final TextField codeField = new TextField();
    private final TextField nameField = new TextField();
    private final ComboBox<AccountType> typeField = new ComboBox<>();
    private final ComboBox<NormalBalance> balanceField = new ComboBox<>();
    private final ComboBox<AccountSubtype> subtypeField = new ComboBox<>();
    private final TextField parentCodeField = new TextField();
    private final CheckBox activeField = new CheckBox("Active");
    private Button refresh;
    private String pendingDrillContext = "";

    public ChartOfAccountsPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Chart of Accounts");
        title.getStyleClass().add("panel-title");

        Button add = new Button("+ Add");
        add.setOnAction(e -> clearFormForNew());

        Button save = new Button("Save");
        save.setOnAction(e -> saveForm());

        refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());

        HBox actions = new HBox(8, add, save, refresh);
        VBox header = new VBox(6, title, actions, buildEditorForm(), status, new Separator());

        root.setTop(header);

        TableColumn<Account, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getCode()));

        TableColumn<Account, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getName()));

        TableColumn<Account, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getAccountType())));

        TableColumn<Account, String> normalBalance = new TableColumn<>("Normal");
        normalBalance.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getNormalBalance())));

        TableColumn<Account, String> subtype = new TableColumn<>("Subtype");
        subtype.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getSubtype())));

        TableColumn<Account, String> parentCode = new TableColumn<>("Parent");
        parentCode.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getParent() == null ? "" : v.getValue().getParent().getCode()));

        TableColumn<Account, String> active = new TableColumn<>("Active");
        active.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().isActive() ? "Y" : "N"));

        table.getColumns().addAll(code, name, type, normalBalance, subtype, parentCode, active);
        table.setPlaceholder(new Label("No accounts found. Use the form above to create a posting account."));
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> loadRowIntoForm(newRow));
        root.setCenter(table);

        clearFormForNew();
        reload();
    }

    @Override public String title() { return "Chart of Accounts"; }
    @Override public Node root() { return root; }

    @Override
    public void onNew()
    {
        clearFormForNew();
        status.setText("Create mode: enter account details and click Save.");
    }

    private Node buildEditorForm()
    {
        typeField.getItems().setAll(AccountType.values());
        balanceField.getItems().setAll(NormalBalance.values());
        subtypeField.getItems().setAll(AccountSubtype.values());
        activeField.setSelected(true);

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);

        int row = 0;
        form.add(new Label("Code"), 0, row);
        form.add(codeField, 1, row);
        form.add(new Label("Name"), 2, row);
        form.add(nameField, 3, row);
        row++;
        form.add(new Label("Type"), 0, row);
        form.add(typeField, 1, row);
        form.add(new Label("Normal"), 2, row);
        form.add(balanceField, 3, row);
        row++;
        form.add(new Label("Subtype"), 0, row);
        form.add(subtypeField, 1, row);
        form.add(new Label("Parent code"), 2, row);
        form.add(parentCodeField, 3, row);
        row++;
        form.add(activeField, 0, row, 2, 1);

        return form;
    }

    private void loadRowIntoForm(Account row)
    {
        if (row == null)
        {
            return;
        }
        codeField.setText(row.getCode());
        nameField.setText(row.getName());
        typeField.setValue(row.getAccountType());
        balanceField.setValue(row.getNormalBalance());
        activeField.setSelected(row.isActive());
        subtypeField.setValue(row.getSubtype());
        parentCodeField.setText(row.getParent() == null ? "" : row.getParent().getCode());
        status.setText("Edit mode for account " + row.getCode() + ".");
    }

    private void clearFormForNew()
    {
        table.getSelectionModel().clearSelection();
        codeField.clear();
        nameField.clear();
        typeField.getSelectionModel().clearSelection();
        balanceField.getSelectionModel().clearSelection();
        subtypeField.getSelectionModel().clearSelection();
        parentCodeField.clear();
        activeField.setSelected(true);
    }

    private void saveForm()
    {
        try
        {
            UiServiceRegistry.accountAdmin().upsert(
                    codeField.getText(),
                    nameField.getText(),
                    typeField.getValue(),
                    balanceField.getValue(),
                    subtypeField.getValue(),
                    parentCodeField.getText(),
                    activeField.isSelected());
            status.setText("Saved account " + codeField.getText().trim() + ".");
            reload();
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save account: " + UiErrors.safeMessage(ex));
        }
    }


    FormState readFormStateForTests()
    {
        return new FormState(
                codeField.getText(),
                nameField.getText(),
                typeField.getValue(),
                balanceField.getValue(),
                subtypeField.getValue(),
                parentCodeField.getText(),
                activeField.isSelected());
    }

    void setFormStateForTests(FormState formState)
    {
        codeField.setText(formState.code());
        nameField.setText(formState.name());
        typeField.setValue(formState.accountType());
        balanceField.setValue(formState.normalBalance());
        subtypeField.setValue(formState.subtype());
        parentCodeField.setText(formState.parentCode());
        activeField.setSelected(formState.active());
    }

    record FormState(String code,
                     String name,
                     AccountType accountType,
                     NormalBalance normalBalance,
                     AccountSubtype subtype,
                     String parentCode,
                     boolean active)
    {
    }


    private String formatStatus(String message)
    {
        if (pendingDrillContext == null || pendingDrillContext.isBlank())
        {
            return message;
        }
        String combined = message + " | " + pendingDrillContext;
        pendingDrillContext = "";
        return combined;
    }

    private void reload()
    {
        refresh.setDisable(true);
        String incomingContext = DrillThroughCoordinator.consumeContext(AppPanelId.CHART_OF_ACCOUNTS);
        if (!incomingContext.isBlank())
        {
            pendingDrillContext = incomingContext;
        }
        status.setText(formatStatus("Loading accounts..."));

        UiAsync.run("coa-load",
            () -> UiServiceRegistry.accountLookup().listPostingAccountsIncludingInactive(),
            rows -> {
                table.getItems().setAll(rows);
                status.setText(formatStatus("Loaded " + rows.size() + " posting account(s) (active + inactive)."));
                if (!rows.isEmpty())
                {
                    Account selected = table.getSelectionModel().getSelectedItem();
                    if (selected != null)
                    {
                        rows.stream()
                                .filter(row -> Objects.equals(row.getCode(), selected.getCode()))
                                .findFirst()
                                .ifPresent(table.getSelectionModel()::select);
                    }
                }
                refresh.setDisable(false);
            },
            ex -> {
                status.setText(formatStatus("Failed to load accounts: " + UiErrors.safeMessage(ex)));
                refresh.setDisable(false);
            });
    }
}
