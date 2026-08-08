package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ApprovalAuditPanelSourceTest
{
    @Test
    public void productionPanelUsesFactualAuditEventAuthorityOnly() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/ApprovalAuditPanel.java"));

        assertTrue(source.contains("UiServiceRegistry.auditHistory().listRecent"));
        assertTrue(source.contains("AuditHistoryFilter"));
        assertTrue(source.contains("Factual events"));
        assertTrue(source.contains("Selected factual event"));
        assertTrue(source.contains("setEditable(false)"));
        assertTrue(source.contains("CompanySplitPaneStateBinder.bind"));
        assertFalse(source.contains("approvalAuditService"));
        assertFalse(source.contains("ApprovalAuditRecord"));
        assertFalse(source.contains("ApprovalDecision"));
        assertFalse(source.contains("Decision"));
        assertFalse(source.contains("Run ID"));
    }

    @Test
    public void serviceRegistryDoesNotExposeLegacyApprovalAuditAsProductionPanelAuthority() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/UiServiceRegistry.java"));

        assertTrue(source.contains("AuditHistoryService auditHistory"));
        assertFalse(source.contains("approvalAuditService()"));
        assertFalse(source.contains("approvalAuditRepository()"));
    }
}
