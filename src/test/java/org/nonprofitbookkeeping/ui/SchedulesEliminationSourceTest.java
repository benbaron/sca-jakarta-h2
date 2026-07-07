package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-level guardrails for the eliminated top-level Schedules function. */
class SchedulesEliminationSourceTest
{
    @Test
    void schedulesPanelClassIsRemoved()
    {
        assertFalse(Files.exists(Path.of("src/main/java/org/nonprofitbookkeeping/ui/SchedulesPanel.java")));
    }

    @Test
    void schedulesIsNotAnAppPanelIdFactoryOrNavigationItem() throws Exception
    {
        String appPanelId = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/AppPanelId.java"));
        String panelFactory = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/PanelFactory.java"));
        String navigationPane = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/NavigationPane.java"));

        assertFalse(appPanelId.contains("SCHEDULES"));
        assertFalse(panelFactory.contains("SchedulesPanel"));
        assertFalse(panelFactory.contains("AppPanelId.SCHEDULES"));
        assertFalse(navigationPane.contains("AppPanelId.SCHEDULES"));
        assertFalse(navigationPane.contains("\"Schedules\""));
    }

    @Test
    void scheduleRunbookSidecarIsNotReferenced() throws Exception
    {
        String workspaceStore = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/UiWorkspaceDataStore.java"));
        String runbookPersistence = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/RunbookPersistence.java"));

        assertFalse(workspaceStore.contains("scheduleRunbookEntries"));
        assertFalse(workspaceStore.contains("appendScheduleRunbookEntry"));
        assertFalse(workspaceStore.contains("scheduleRunbookEntries()"));
        assertFalse(runbookPersistence.contains("schedules.log"));
        assertFalse(runbookPersistence.contains("loadScheduleEntries"));
        assertFalse(runbookPersistence.contains("saveScheduleEntries"));
    }

    @Test
    void interfaceOperationMatrixDoesNotListSchedulesPanel() throws Exception
    {
        String matrix = Files.readString(Path.of("doc/interface-operation-matrix.md"));

        assertFalse(matrix.contains("`SCHEDULES`"));
        assertFalse(matrix.contains("`SchedulesPanel`"));
        assertTrue(matrix.contains("former top-level Schedules panel"));
    }
}
