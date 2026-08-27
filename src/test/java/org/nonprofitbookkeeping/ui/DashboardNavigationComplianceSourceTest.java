package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardNavigationComplianceSourceTest
{
    @Test
    void recentTransactionsUsesCanonicalJournalDestinationAndTerminology() throws Exception
    {
        String source = dashboardSource();

        assertTrue(source.contains("link(\"View Journal  →\", AppPanelId.JOURNAL_PANE)"));
        assertFalse(source.contains("View Ledger Register"));
        assertFalse(source.contains("opening Transaction Editor"));
    }

    @Test
    void quickLinksContainOnlyGenuineDestinationsAndNoDashboardSelfLink() throws Exception
    {
        String source = dashboardSource();

        assertTrue(source.contains("return card(\"Quick Links\", links, null);"));
        assertTrue(source.contains("AppPanelId.IMPORT_PREVIEW"));
        assertTrue(source.contains("AppPanelId.RECONCILIATION_RUNS"));
        assertFalse(source.contains("All Quick Links"));
        assertFalse(source.contains("AppPanelId.DASHBOARD"));
    }

    @Test
    void sclxQuickLinkUsesCurrentFilePreviewTerminology() throws Exception
    {
        String source = dashboardSource();

        assertTrue(source.contains("\"Import SCLX File\""));
        assertTrue(source.contains("\"Preview and import an SCLX file\""));
        assertFalse(source.contains("\"Import SCLX Workbook\""));
        assertFalse(source.contains("bookkeeping workbook"));
    }

    private static String dashboardSource() throws Exception
    {
        return Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/DashboardHomePanel.java"));
    }
}
