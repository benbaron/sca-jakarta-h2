package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.DepreciationRunView;
import org.nonprofitbookkeeping.service.FixedAssetView;

import java.time.LocalDate;

/** H2-backed depreciation run panel. */
public class DepreciationRunsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<FixedAssetView> assets = new TableView<>();
    private final TableView<DepreciationRunView> runs = new TableView<>();
    private final DatePicker runDate = new DatePicker(LocalDate.now());
    private final TextField notes = new TextField();
    private final Label status = new Label();

    public DepreciationRunsPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Depreciation Runs");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());
        Button run = new Button("Run Monthly Depreciation");
        run.setOnAction(e -> runDepreciation());
        Button deleteUnavailable = new Button("Delete unavailable — depreciation runs preserve ledger history");
        deleteUnavailable.setDisable(true);
        HBox actions = new HBox(8, refresh, new Label("Run date"), runDate, new Label("Notes"), notes, run, deleteUnavailable);

        root.setTop(new VBox(6, title, actions, status, new Separator()));
        configureAssetTable();
        configureRunTable();
        root.setCenter(new VBox(8, new Label("Depreciation Basis"), assets, new Label("Completed Depreciation Runs"), runs));
        reload();
    }

    private void configureAssetTable()
    {
        assets.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        assets.setPlaceholder(new Label("No active fixed assets are available for depreciation."));
        assetColumn("Asset", a -> a.name(), 180);
        assetColumn("Asset Account", a -> a.assetAccountCode(), 120);
        assetColumn("Cost", a -> a.acquisitionCost().toPlainString(), 110);
        assetColumn("Accum. Dep.", a -> a.accumulatedDepreciation().toPlainString(), 110);
        assetColumn("Book Value", a -> a.currentBookValue().toPlainString(), 110);
        assetColumn("Next Dep.", a -> a.nextDepreciationAmount().toPlainString(), 110);
        assetColumn("Status", a -> a.status().name(), 100);
    }

    private void configureRunTable()
    {
        runs.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        runs.setPlaceholder(new Label("No depreciation runs have been recorded."));
        runColumn("Run Date", r -> String.valueOf(r.runDate()), 110);
        runColumn("Asset", DepreciationRunView::assetName, 180);
        runColumn("Amount", r -> r.depreciationAmount().toPlainString(), 110);
        runColumn("Txn", r -> String.valueOf(r.transactionId()), 90);
        runColumn("Notes", DepreciationRunView::notes, 180);
    }

    private void assetColumn(String title, java.util.function.Function<FixedAssetView, String> value, double width)
    {
        TableColumn<FixedAssetView, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new SimpleStringProperty(value.apply(row.getValue())));
        column.setPrefWidth(width);
        column.setMinWidth(72);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        assets.getColumns().add(column);
    }

    private void runColumn(String title, java.util.function.Function<DepreciationRunView, String> value, double width)
    {
        TableColumn<DepreciationRunView, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new SimpleStringProperty(value.apply(row.getValue())));
        column.setPrefWidth(width);
        column.setMinWidth(72);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        runs.getColumns().add(column);
    }

    private void reload()
    {
        status.setText("Loading fixed assets and depreciation runs...");
        UiAsync.run("depreciation-run-load",
                () -> new DepreciationPanelData(
                        UiServiceRegistry.fixedAssets().listAssets(activeCompanyCode()),
                        UiServiceRegistry.fixedAssets().listDepreciationRuns(activeCompanyCode())),
                data -> {
                    assets.getItems().setAll(data.assets());
                    runs.getItems().setAll(data.runs());
                    status.setText("Loaded " + data.assets().size() + " fixed asset(s) and " + data.runs().size() + " depreciation run(s).");
                },
                ex -> status.setText("Could not load depreciation data: " + UiErrors.safeMessage(ex)));
    }

    private void runDepreciation()
    {
        FixedAssetView selected = assets.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a fixed asset first.");
            return;
        }
        try
        {
            DepreciationRunView run = UiServiceRegistry.fixedAssets().runMonthlyDepreciation(selected.id(), runDate.getValue(), notes.getText());
            reload();
            status.setText("Created depreciation transaction #" + run.transactionId() + " for " + run.assetName() + ".");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not run depreciation: " + UiErrors.safeMessage(ex));
        }
    }

    private static String activeCompanyCode()
    {
        return MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
    }

    private record DepreciationPanelData(java.util.List<FixedAssetView> assets, java.util.List<DepreciationRunView> runs)
    {
    }

    @Override public String title() { return "Depreciation Runs"; }
    @Override public Node root() { return root; }
}
