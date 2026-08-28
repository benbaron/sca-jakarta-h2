package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyShellAuthoritySourceTest
{
    private static final Path MAIN = Path.of("src/main/java/org/nonprofitbookkeeping/ui");
    private static final Path TEST = Path.of("src/test/java/org/nonprofitbookkeeping/ui");

    @Test
    void mainWindowIsOnlyACompatibilityFacadeAndLegacyDateUtilitiesAreGone() throws Exception
    {
        String mainWindow = Files.readString(MAIN.resolve("MainWindow.java"));
        String sessionContext = Files.readString(MAIN.resolve("ApplicationSessionContext.java"));

        assertTrue(mainWindow.contains("ApplicationSessionContext.sharedSessionState()"));
        assertFalse(mainWindow.contains("extends BorderPane"));
        assertFalse(mainWindow.contains("DateRangeSelector"));
        assertFalse(mainWindow.contains("Command Palette"));
        assertFalse(mainWindow.contains("Find…"));

        assertTrue(sessionContext.contains("private static final UiSessionState SESSION_STATE = new UiSessionState()"));
        assertFalse(Files.exists(MAIN.resolve("DateRangeSelector.java")));
        assertFalse(Files.exists(MAIN.resolve("DateRangeUtil.java")));

        assertTrue(Files.exists(MAIN.resolve("DateRange.java")));
        assertTrue(Files.exists(MAIN.resolve("DateRangeContext.java")));
        String reportLibrary = Files.readString(MAIN.resolve("ReportLibraryPanel.java"));
        assertTrue(reportLibrary.contains("DateRangeContext.get()"));
    }

    @Test
    void oldShellTestsAreRetiredWhileProductionShellTestsRemain()
    {
        assertFalse(Files.exists(TEST.resolve("MainWindowStateWiringTest.java")));
        assertFalse(Files.exists(TEST.resolve("MainWindowPhase1FollowupTest.java")));
        assertFalse(Files.exists(TEST.resolve("MainWindowWizardAndLayoutTest.java")));
        assertFalse(Files.exists(TEST.resolve("MainWindowCommandPaletteTest.java")));
        assertFalse(Files.exists(TEST.resolve("ProductionDesignRulesTestFxTest.java")));

        assertTrue(Files.exists(TEST.resolve("ProductionWorkspaceCommandRoutingTest.java")));
        assertTrue(Files.exists(TEST.resolve("ProductionPanelRouteComplianceTest.java")));
        assertTrue(Files.exists(TEST.resolve("ConnectedDatabaseSessionAuthorityJavaFxTest.java")));
    }

    @Test
    void residualRunServicesAreRetainedAsCompatibilityAndSchedulesAreNotRouted() throws Exception
    {
        String registry = Files.readString(MAIN.resolve("UiServiceRegistry.java"));
        String panelFactory = Files.readString(MAIN.resolve("PanelFactory.java"));
        String composition = Files.readString(Path.of("doc/architecture/application-composition.md"));

        assertTrue(registry.contains("ReconciliationService"));
        assertTrue(registry.contains("PeriodCloseService"));
        assertTrue(registry.contains("ScheduleEligibilityService"));
        assertFalse(panelFactory.contains("case SCHEDULES ->"));

        assertTrue(composition.contains("ScheduleEligibilityService"));
        assertTrue(composition.contains("ReconciliationService"));
        assertTrue(composition.contains("PeriodCloseService"));
        assertTrue(composition.contains("no historical H2 run tables are removed"));
    }
}
