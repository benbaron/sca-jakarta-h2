package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Prevents compatibility preferences from being presented as authorization. */
class MainWindowPrivilegeGatingTest
{
    @Test
    void shellsDoNotUseDefaultPrivilegeAsEffectiveAuthorization() throws Exception
    {
        String mainWindow = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/MainWindow.java"));
        String productionWorkspace = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/ProductionWorkspaceWindow.java"));

        assertFalse(mainWindow.contains("requiredPrivilegeForPanel"));
        assertFalse(mainWindow.contains("canAccessPanelForPrivilege"));
        assertFalse(mainWindow.contains("refreshPrivilegeGating"));
        assertFalse(mainWindow.contains("gatedItem("));
        assertFalse(productionWorkspace.contains("defaultPrivilege()"));
    }
}
