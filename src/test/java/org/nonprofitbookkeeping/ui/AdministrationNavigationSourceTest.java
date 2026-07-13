package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-level guardrails for reachable company and user administration. */
class AdministrationNavigationSourceTest
{
    @Test
    void companyAndUserAdministrationHaveStableWorkspaceRoutes() throws Exception
    {
        String appPanelId = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/AppPanelId.java"));
        String panelFactory = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/PanelFactory.java"));
        String navigationPane = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/NavigationPane.java"));

        assertTrue(appPanelId.contains("COMPANY_ADMIN"));
        assertTrue(appPanelId.contains("USER_ADMIN"));

        assertTrue(panelFactory.contains("AppPanelId.COMPANY_ADMIN, CompanyAdminPanel::new"));
        assertTrue(panelFactory.contains("AppPanelId.USER_ADMIN, UserAdminPanel::new"));

        assertTrue(navigationPane.contains("AppPanelId.COMPANY_ADMIN, \"Company Admin\""));
        assertTrue(navigationPane.contains("AppPanelId.USER_ADMIN, \"User Admin\""));
    }
}
