package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-level guardrails for reachable company and user administration. */
class AdministrationNavigationSourceTest
{
    @Test
    void settingsDestinationRoutesToAdministrationHub() throws Exception
    {
        String panelFactory = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/PanelFactory.java"));
        String navigationPane = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/NavigationPane.java"));
        String administrationPanel = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/AdministrationPanel.java"));

        assertTrue(panelFactory.contains("AppPanelId.SETTINGS, AdministrationPanel::new"));
        assertTrue(navigationPane.contains("AppPanelId.SETTINGS, \"Administration\""));
        assertTrue(administrationPanel.contains("tab(\"Preferences\", settings)"));
        assertTrue(administrationPanel.contains("tab(\"Company Admin\", companies)"));
        assertTrue(administrationPanel.contains("tab(\"User Admin\", users)"));
    }
}
