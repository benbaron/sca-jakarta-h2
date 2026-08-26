package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetFiscalAuthoritySourceTest
{
    @Test
    void editorUsesExplicitStableVersionsAndSelectedFiscalPeriod() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/BudgetEditorPanel.java"));

        assertTrue(source.contains("budgetEditorPlanSelector"));
        assertTrue(source.contains("New Draft"));
        assertTrue(source.contains("Create Revision"));
        assertTrue(source.contains("versionsForFiscalYear"));
        assertTrue(source.contains("reloadPreferred(plan.id())"));
        assertTrue(source.contains("ActivePeriodContext.get()"));
        assertFalse(source.contains("LocalDate.now()"));
        assertFalse(source.contains("System.currentTimeMillis()"));
        assertFalse(source.contains("ensureDraftPlan"));
        assertFalse(source.contains("orElseGet(() -> service.createDraft"));
    }

    @Test
    void budgetVsActualUsesFiscalRangeAndActivePeriodInsteadOfClockDate() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/BudgetVsActualPanel.java"));

        assertTrue(source.contains("ActivePeriodContext.get()"));
        assertTrue(source.contains("fiscalRange(selectedPeriodStart)"));
        assertTrue(source.contains("activeVariance(range)"));
        assertFalse(source.contains("LocalDate.now()"));
    }

    @Test
    void reportDefaultsUseSameFiscalRangeWhenNoExplicitDateRangeExists() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/ReportLibraryPanel.java"));

        assertTrue(source.contains("FiscalPeriodRange fiscal = UiServiceRegistry.budgetPlan().fiscalRange(ActivePeriodContext.get())"));
        assertTrue(source.contains("startDate.setValue(fiscal.fiscalYearStart())"));
        assertTrue(source.contains("endDate.setValue(fiscal.periodEnd())"));
    }
}
