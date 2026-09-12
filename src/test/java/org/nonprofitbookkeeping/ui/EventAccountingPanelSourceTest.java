package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-route guardrails for the P21-S2 read-only Event Accounting workspace. */
class EventAccountingPanelSourceTest
{
    @Test
    void productionRouteIsReadOnlyAndUsesCanonicalAuthorities() throws Exception
    {
        String panelId = read("src/main/java/org/nonprofitbookkeeping/ui/AppPanelId.java");
        String navigation = read("src/main/java/org/nonprofitbookkeeping/ui/NavigationPane.java");
        String factory = read("src/main/java/org/nonprofitbookkeeping/ui/PanelFactory.java");
        String registry = read("src/main/java/org/nonprofitbookkeeping/ui/UiServiceRegistry.java");
        String panel = read("src/main/java/org/nonprofitbookkeeping/ui/EventAccountingPanel.java");
        String service = read("src/main/java/org/nonprofitbookkeeping/service/EventAccountingQueryService.java");

        assertTrue(panelId.contains("EVENT_ACCOUNTING"));
        assertTrue(navigation.contains("AppPanelId.EVENT_ACCOUNTING, \"Event Accounting\""));
        assertTrue(factory.contains("AppPanelId.EVENT_ACCOUNTING, EventAccountingPanel::new"));
        assertTrue(registry.contains("EventAccountingQueryService eventAccounting()"));
        assertTrue(registry.contains("new EventAccountingQueryService(services().jpa(), UiServiceRegistry::activeCompanyCode)"));

        assertTrue(panel.contains("DrillThroughCoordinator.openPanelWithContext"));
        assertTrue(panel.contains("\"Txn #\" + transactionId + \" from Event Accounting\""));
        assertFalse(panel.contains("BOOKKEEPING_WRITE"));
        assertFalse(panel.contains("requiredPermission("));
        assertFalse(panel.contains("createNativeQuery"));
        assertFalse(panel.contains("EntityManager"));

        assertTrue(service.contains("s.activity.id = :activityId"));
        assertTrue(service.contains("a.accountType = :assetType"));
        assertTrue(service.contains("a.accountFunction = :bankFunction"));
        assertTrue(service.contains("a.normalBalance = :debitNormal"));
        assertTrue(service.contains("join CompanyBankAccount cba"));
        assertTrue(service.contains("income.subtract(expenses)"));
        assertFalse(service.contains("AccountType.BANK"));
        assertFalse(service.contains("getTransaction().begin"));
        assertFalse(service.contains("persist("));
        assertFalse(service.contains("merge("));
        assertFalse(service.contains("remove("));
    }

    @Test
    void tablesAndPeriodBehaviorUseCurrentProductionUiAuthorities() throws Exception
    {
        String panel = read("src/main/java/org/nonprofitbookkeeping/ui/EventAccountingPanel.java");

        assertTrue(panel.contains("TableView.UNCONSTRAINED_RESIZE_POLICY"));
        assertTrue(panel.contains("CompanySplitPaneStateBinder.bind"));
        assertTrue(panel.contains("CompanyUiFormat.activeCompany()"));
        assertTrue(panel.contains("ActivePeriodContext.activeDateProperty()"));
        assertTrue(panel.contains("service.fiscalRange(selectedPeriodStart)"));
        assertTrue(panel.contains("dateScopeDetached"));
    }

    private static String read(String path) throws Exception
    {
        return Files.readString(Path.of(path));
    }
}
