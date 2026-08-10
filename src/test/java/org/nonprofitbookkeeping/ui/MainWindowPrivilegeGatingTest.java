package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Prevents compatibility preferences from being presented as authorization. */
class MainWindowPrivilegeGatingTest
{
    @Test
    void retiredShellDoesNotUseDefaultPrivilegeAsEffectiveAuthorization() throws Exception
    {
        String mainWindow = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/MainWindow.java"));
        String referenceWorkspace = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/ReferenceWorkspaceWindow.java"));

        assertFalse(mainWindow.contains("requiredPrivilegeForPanel"));
        assertFalse(mainWindow.contains("canAccessPanelForPrivilege"));
        assertFalse(mainWindow.contains("refreshPrivilegeGating"));
        assertFalse(mainWindow.contains("gatedItem("));
        assertFalse(referenceWorkspace.contains("defaultPrivilege()"));
    }
}
