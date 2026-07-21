package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialViewComplianceSourceTest
{
    @Test
    void scopedFinancialViewsUseCompanyFormattingAndUnconstrainedTables() throws Exception
    {
        for (String filename : new String[]{
                "DashboardHomePanel.java",
                "BudgetVsActualPanel.java",
                "DepreciationRunsPanel.java",
                "InventoryPanel.java",
                "ReconciliationRunsPanel.java",
                "PeriodCloseRunsPanel.java",
                "ApprovalAuditPanel.java",
                "BankTransactionsPanel.java"})
        {
            String source = source(filename);
            assertTrue(source.contains("CompanyUiFormat"), filename);
            assertFalse(source.contains("TableView.CONSTRAINED_RESIZE_POLICY"), filename);
        }
        assertFalse(source("ImportPreviewPanel.java").contains("TableView.CONSTRAINED_RESIZE_POLICY"));
    }

    @Test
    void multiRegionViewsPersistTopBottomDividerState() throws Exception
    {
        assertSplit("DashboardHomePanel.java", "dashboard-recent-transactions");
        assertSplit("BudgetVsActualPanel.java", "budget-vs-actual");
        assertSplit("DepreciationRunsPanel.java", "depreciation-runs");
        assertSplit("InventoryPanel.java", "inventory-tables");
        assertSplit("ReconciliationRunsPanel.java", "reconciliation-review");
        assertSplit("PeriodCloseRunsPanel.java", "period-close-tables");
        assertSplit("ImportPreviewPanel.java", "import-preview-workspace");
    }

    @Test
    void companyFormatSupportsDatesTimesAndLenientMoney() throws Exception
    {
        String source = source("CompanyUiFormat.java");
        assertTrue(source.contains("formatDateTime(LocalDateTime value)"));
        assertTrue(source.contains("formatDateTime(Instant value)"));
        assertTrue(source.contains("parseMoneyLenient"));
    }

    private static void assertSplit(String filename, String stateKey) throws Exception
    {
        String source = source(filename);
        assertTrue(source.contains("setOrientation(Orientation.VERTICAL)")
                        || source.contains("setOrientation(javafx.geometry.Orientation.VERTICAL)"),
                filename);
        assertTrue(source.contains("CompanySplitPaneStateBinder.bind"), filename);
        assertTrue(source.contains(stateKey), filename);
    }

    private static String source(String filename) throws Exception
    {
        return Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui", filename));
    }
}
