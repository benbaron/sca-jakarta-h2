package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.report.ReportTableModel;
import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormattedReportFxRendererTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    void supplementalRepairUsesStableTransactionAndScrollableTable()
    {
        FxTestSupport.onFx(() ->
        {
            var columns = new java.util.ArrayList<ReportTableModel.Column>();
            columns.add(new ReportTableModel.Column("transaction", "Transaction", ReportTableModel.ValueFormat.TEXT, 150));
            for (int i = 0; i < 10; i++)
            {
                columns.add(new ReportTableModel.Column("c" + i, "Detail " + i, ReportTableModel.ValueFormat.TEXT, 180));
            }
            var rows = new java.util.ArrayList<ReportTableModel.Row>();
            rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.TOTAL, Map.of("c0", "Control account")));
            for (int i = 0; i < 60; i++)
            {
                rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.STATUS_WARNING, Map.of("transaction", 123L, "c0", "Repair allocations")));
            }
            Node rendered = new FormattedReportFxRenderer(FinancialReportDisplayFormat.plain()).render(
                    new ReportTableModel("supplemental", "Allocation review", "NOT READY", columns, rows));
            var root = (VBox) rendered;
            var scene = new javafx.scene.Scene(root, 720, 420);
            root.applyCss();
            root.layout();
            var split = (javafx.scene.control.SplitPane) root.lookup(".split-pane");
            assertNotNull(split);
            var button = (javafx.scene.control.Button) root.lookup("#supplementalReportRepair");
            TableView<?> table = CompanyTableStateBinder.findTables(root).get(0);
            table.getSelectionModel().select(0);
            assertTrue(button.isDisabled());
            java.util.concurrent.atomic.AtomicReference<AppPanelId> destination = new java.util.concurrent.atomic.AtomicReference<>();
            DrillThroughCoordinator.configureOpener(destination::set);
            try
            {
                table.getSelectionModel().select(1);
                button.fire();
                assertEquals(AppPanelId.JOURNAL_PANE, destination.get());
                assertTrue(DrillThroughCoordinator.consumeContext(AppPanelId.JOURNAL_PANE).contains("Txn #123"));
                split.setDividerPositions(0.3);
                root.resize(620, 360);
                root.layout();
                assertTrue(table.getWidth() <= root.getWidth());
                assertTrue(table.getHeight() < root.getHeight());
                assertTrue(table.lookupAll(".scroll-bar").stream().anyMatch(node ->
                        node instanceof javafx.scene.control.ScrollBar bar && bar.isVisible()
                                && bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL));
                assertTrue(table.lookupAll(".scroll-bar").stream().anyMatch(node ->
                        node instanceof javafx.scene.control.ScrollBar bar && bar.isVisible()
                                && bar.getOrientation() == javafx.geometry.Orientation.VERTICAL));
            }
            finally
            {
                DrillThroughCoordinator.configureOpener(null);
            }
            return null;
        });
    }

    @Test
    void rendersInteractiveWorkbookStyledTable()
    {
        ReportTableModel model = new ReportTableModel(
                "test-report",
                "Test Report",
                "As of 2026-03-31",
                List.of(
                        new ReportTableModel.HeaderLine(
                                "Configured Parent",
                                "Configured Legal Entity EXCHEQUER REPORT",
                                ReportTableModel.HeaderStyle.PRIMARY),
                        new ReportTableModel.HeaderLine(
                                "Configured Group",
                                "Q1 Report",
                                ReportTableModel.HeaderStyle.SECONDARY)),
                List.of(
                        new ReportTableModel.Column(
                                "name", "Name", ReportTableModel.ValueFormat.TEXT, 220),
                        new ReportTableModel.Column(
                                "amount", "Amount", ReportTableModel.ValueFormat.MONEY, 140)),
                List.of(
                        new ReportTableModel.Row(
                                ReportTableModel.RowStyle.SECTION,
                                Map.of("name", "Assets")),
                        new ReportTableModel.Row(
                                ReportTableModel.RowStyle.DETAIL,
                                Map.of("name", "Cash", "amount", new BigDecimal("100.00"))),
                        new ReportTableModel.Row(
                                ReportTableModel.RowStyle.TOTAL,
                                Map.of("name", "Total Assets", "amount", new BigDecimal("100.00")))));

        FxTestSupport.onFx(() -> {
            Node rendered = new FormattedReportFxRenderer(
                    FinancialReportDisplayFormat.plain()).render(model);
            VBox root = (VBox) rendered;
            assertTrue(root.getChildren().get(0) instanceof GridPane);
            GridPane metadata = (GridPane) root.getChildren().get(0);
            assertEquals(4, metadata.getChildren().size());
            List<TableView<?>> tables = CompanyTableStateBinder.findTables(rendered);
            assertEquals(1, tables.size());

            TableView<?> table = tables.get(0);
            assertEquals("report-table-test-report", table.getId());
            assertTrue(table.getStyleClass().contains("formatted-report-table"));
            assertEquals(2, table.getColumns().size());
            assertEquals(3, table.getItems().size());
            for (TableColumn<?, ?> column : table.getColumns())
            {
                assertTrue(column.isSortable());
                assertTrue(column.isResizable());
                assertTrue(column.isReorderable());
                assertNotNull(column.getCellFactory());
            }
            assertNotNull(table.getRowFactory());
            return null;
        });
    }
}
