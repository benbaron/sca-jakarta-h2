package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivitiesPanelLifecycleSourceTest
{
    @Test
    void panelUsesStableIdServicePermissionGatingAndTruthfulLifecycle() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/ActivitiesPanel.java"));

        assertTrue(source.contains("private Long editingActivityId"));
        assertTrue(source.contains("new ActivityCommand("));
        assertTrue(source.contains("activityAdmin().save("));
        assertTrue(source.contains("activityAdmin().usage("));
        assertTrue(source.contains("activityAdmin().deleteUnused("));
        assertTrue(source.contains("activityLookup().listAllActivities()"));
        assertTrue(source.contains("ApplicationPermission.BOOKKEEPING_WRITE"));
        assertTrue(source.contains("UiPermissionGate.gate(codeField"));
        assertTrue(source.contains("UiPermissionGate.gate(activeField"));
        assertTrue(source.contains("new SplitPane()"));
        assertTrue(source.contains("split.setOrientation(Orientation.VERTICAL)"));
        assertTrue(source.contains("tableRegion.setMinHeight(0.0)"));
        assertTrue(source.contains("editorScroll.setMinHeight(0.0)"));
        assertTrue(source.contains("preferencesService.saveState"));
        assertTrue(source.contains("hasUnsavedChanges()"));
        assertTrue(source.contains("Inactive Activities remain visible here"));
        assertTrue(source.contains("permanent deletion is unavailable"));
        assertFalse(source.contains("Delete disabled"));
        assertFalse(source.contains("AccountType.BANK"));
    }
}
