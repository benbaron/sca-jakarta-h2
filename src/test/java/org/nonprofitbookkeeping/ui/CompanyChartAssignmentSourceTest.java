package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyChartAssignmentSourceTest
{
    @Test
    void chartWorkspaceOwnsActivationAndCompanyAdminHasNoDuplicateSelector() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/CompanyAdminPanel.java"));
        String workspace = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/ChartOfAccountsPanel.java"));
        String controller = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/CompanySessionController.java"));

        assertFalse(source.contains("companyChartAssignment"));
        assertFalse(source.contains("Make Active Chart"));
        assertTrue(workspace.contains("Make Active Chart"));
        assertTrue(workspace.contains("companyAdmin().assignActiveChart(companyId, chartId)"));
        assertTrue(workspace.contains("Existing charts, accounts, transactions, and historical references"));
        assertFalse(source.contains("Tax filing, chart assignment, and reporting-default editors are deferred"));
        assertFalse(controller.contains("listCompanyCharts(long companyId)"));
        assertFalse(controller.contains("assignActiveChart(long companyId, long chartId)"));
    }
}
