package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
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
    void rendersInteractiveWorkbookStyledTable()
    {
        ReportTableModel model = new ReportTableModel(
                "test-report",
                "Test Report",
                "As of 2026-03-31",
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
