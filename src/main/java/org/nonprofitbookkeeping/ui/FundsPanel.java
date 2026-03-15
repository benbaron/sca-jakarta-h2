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
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;

import java.util.Objects;

/**
 * Represents the FundsPanel component in the nonprofit bookkeeping application.
 */
public class FundsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<Fund> table = new TableView<>();
    private final Label status = new Label();
    private final TextField codeField = new TextField();
    private final TextField nameField = new TextField();
    private final ComboBox<FundType> typeField = new ComboBox<>();
    private final CheckBox activeField = new CheckBox("Active");
    private Button refresh;
    private String pendingDrillContext = "";

    public FundsPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Funds");
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

        TableColumn<Fund, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getCode()));

        TableColumn<Fund, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getName()));

        TableColumn<Fund, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getFundType())));

        TableColumn<Fund, String> active = new TableColumn<>("Active");
        active.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().isActive() ? "Y" : "N"));

        table.getColumns().addAll(code, name, type, active);
        table.setPlaceholder(new Label("No funds found. Use the form above to create a fund."));
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> loadRowIntoForm(newRow));
        root.setCenter(table);

        clearFormForNew();
        reload();
    }

    @Override public String title() { return "Funds"; }
    @Override public Node root() { return root; }

    @Override
    public void onNew()
    {
        clearFormForNew();
        status.setText("Create mode: enter fund details and click Save.");
    }

    private Node buildEditorForm()
    {
        typeField.getItems().setAll(FundType.values());
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
        form.add(activeField, 2, row, 2, 1);

        return form;
    }

    private void loadRowIntoForm(Fund row)
    {
        if (row == null)
        {
            return;
        }
        codeField.setText(row.getCode());
        nameField.setText(row.getName());
        typeField.setValue(row.getFundType());
        activeField.setSelected(row.isActive());
        status.setText("Edit mode for fund " + row.getCode() + ".");
    }

    private void clearFormForNew()
    {
        table.getSelectionModel().clearSelection();
        codeField.clear();
        nameField.clear();
        typeField.getSelectionModel().clearSelection();
        activeField.setSelected(true);
    }

    private void saveForm()
    {
        try
        {
            UiServiceRegistry.fundAdmin().upsert(
                    codeField.getText(),
                    nameField.getText(),
                    typeField.getValue(),
                    activeField.isSelected());
            status.setText("Saved fund " + codeField.getText().trim() + ".");
            reload();
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save fund: " + UiErrors.safeMessage(ex));
        }
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
        String incomingContext = DrillThroughCoordinator.consumeContext(AppPanelId.FUNDS);
        if (!incomingContext.isBlank())
        {
            pendingDrillContext = incomingContext;
        }
        status.setText(formatStatus("Loading funds..."));

        UiAsync.run("fund-load",
            () -> UiServiceRegistry.fundLookup().listAllFunds(),
            rows -> {
                table.getItems().setAll(rows);
                status.setText(formatStatus("Loaded " + rows.size() + " fund(s) (active + inactive)."));
                if (!rows.isEmpty())
                {
                    Fund selected = table.getSelectionModel().getSelectedItem();
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
                status.setText(formatStatus("Failed to load funds: " + UiErrors.safeMessage(ex)));
                refresh.setDisable(false);
            });
    }
}
