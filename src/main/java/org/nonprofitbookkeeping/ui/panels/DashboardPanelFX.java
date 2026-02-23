package org.nonprofitbookkeeping.ui.panels;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.bridge.dashboard.DashboardDataBridge;
import org.nonprofitbookkeeping.service.FundBalanceRow;
import org.nonprofitbookkeeping.ui.UiAsync;
import org.nonprofitbookkeeping.ui.UiErrors;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Function;

/**
 * Imported-style Dashboard panel retained under original package name.
 * Public API kept constructor-only, with local bridge-backed data wiring.
 */
public class DashboardPanelFX extends BorderPane
{
    private final Label companyLbl = new Label("Current data context");
    private final Button reloadBtn = new Button("Reload");
    private final ComboBox<String> accountSelector = new ComboBox<>();
    private final TextField memoFilter = new TextField();
    private final TextField amountFilter = new TextField();

    private final TableView<Row> table = new TableView<>();
    private final DashboardDataBridge bridge = new DashboardDataBridge();

    public DashboardPanelFX()
    {
        setPadding(new Insets(10));
        buildTopBanner();
        buildTopFilters();
        buildTable();
        setCenter(new TitledPane("Fund dashboard rows", this.table) {{ setCollapsible(false); }});
        reloadBtn.setOnAction(e -> refresh());
        refresh();
    }

    private void buildTopBanner()
    {
        HBox banner = new HBox(10, new Label("Context:"), companyLbl, reloadBtn);
        banner.setPadding(new Insets(4));
        banner.setStyle("-fx-background-color:#f0f0f0; -fx-border-color:lightgray;");
        setTop(banner);
    }

    private void buildTopFilters()
    {
        accountSelector.setPromptText("Filter by fund code");
        memoFilter.setPromptText("Filter by fund name");
        amountFilter.setPromptText("Min balance");
        Button apply = new Button("Apply");
        apply.setOnAction(e -> refresh());

        HBox filterBox = new HBox(10,
            new Label("Fund:"), accountSelector,
            new Label("Name:"), memoFilter,
            new Label("Min:"), amountFilter,
            apply);
        filterBox.setPadding(new Insets(5));
        filterBox.setStyle("-fx-border-color: lightgray;");

        Node currentTop = getTop();
        VBox topControls = new VBox(currentTop, filterBox);
        setTop(topControls);
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    private void buildTable()
    {
        TableColumn<Row, Object> codeCol = mkCol("Fund", r -> r.fundCode);
        TableColumn<Row, Object> nameCol = mkCol("Name", r -> r.fundName);
        TableColumn<Row, Object> balCol = mkCol("Balance", r -> r.balance);
        table.getColumns().addAll(codeCol, nameCol, balCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private static <T> TableColumn<Row, T> mkCol(String n, Function<Row, T> f)
    {
        TableColumn<Row, T> c = new TableColumn<>(n);
        c.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(f.apply(cd.getValue())));
        return c;
    }

    public void reloadData()
    {
        refresh();
    }

    private void refresh()
    {
        reloadBtn.setDisable(true);
        UiAsync.run("imported-dashboard-load",
            bridge::load,
            snapshot -> {
                String selected = accountSelector.getValue();
                accountSelector.getItems().setAll(snapshot.rows().stream().map(FundBalanceRow::getFundCode).filter(Objects::nonNull).sorted().toList());
                if (selected != null && accountSelector.getItems().contains(selected)) {
                    accountSelector.setValue(selected);
                }

                String nameLike = memoFilter.getText() == null ? "" : memoFilter.getText().trim().toLowerCase();
                BigDecimal min = parseMin(amountFilter.getText());
                String fundCode = accountSelector.getValue();

                table.getItems().setAll(snapshot.rows().stream()
                    .filter(r -> fundCode == null || fundCode.isBlank() || fundCode.equals(r.getFundCode()))
                    .filter(r -> {
                        String fundName = r.getFundName() == null ? "" : r.getFundName();
                        return nameLike.isBlank() || fundName.toLowerCase().contains(nameLike);
                    })
                    .filter(r -> min == null || r.getBalance().compareTo(min) >= 0)
                    .map(r -> new Row(r.getFundCode(), r.getFundName(), r.getBalance().toPlainString()))
                    .toList());

                companyLbl.setText("Funds=" + snapshot.fundCount() + " | Accounts=" + snapshot.accountCount());
                reloadBtn.setDisable(false);
            },
            ex -> {
                companyLbl.setText("Dashboard error: " + UiErrors.safeMessage(ex));
                reloadBtn.setDisable(false);
            });
    }

    private BigDecimal parseMin(String value)
    {
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record Row(String fundCode, String fundName, String balance) {}
}
