package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyChartAssignmentSourceTest
{
    @Test
    void companyAdminExposesRealGovernedChartAssignmentInsteadOfDeferredPlaceholder() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/CompanyAdminPanel.java"));
        String controller = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/CompanySessionController.java"));

        assertTrue(source.contains("companyChartAssignment"));
        assertTrue(source.contains("Make Active Chart"));
        assertTrue(source.contains("assignActiveChart()"));
        assertTrue(source.contains("Existing charts, accounts, transactions, and historical references"));
        assertFalse(source.contains("Tax filing, chart assignment, and reporting-default editors are deferred"));
        assertTrue(controller.contains("listCompanyCharts(long companyId)"));
        assertTrue(controller.contains("assignActiveChart(long companyId, long chartId)"));
    }
}
