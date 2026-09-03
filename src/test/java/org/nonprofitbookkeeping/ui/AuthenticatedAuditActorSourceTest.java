package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-route guardrails for P20-S3 authenticated audit provenance. */
class AuthenticatedAuditActorSourceTest
{
    @Test
    void guardedAuditProducingServicesDeriveActorFromCurrentAuthenticatedSession() throws Exception
    {
        String transactionEntry = service("TransactionEntryService.java");
        String transactionCorrection = service("TransactionCorrectionService.java");
        String fixedAsset = service("FixedAssetService.java");
        String inventory = service("InventoryService.java");
        String periodClose = service("PeriodCloseRangeService.java");
        String reconciliation = service("BankReconciliationWorkspaceService.java");
        String acceptance = service("ReviewedStatementAcceptanceService.java");
        String userAdmin = service("UserAdminService.java");
        String coa = service("CoaCsvImportService.java");
        String sclx = interchange("sclx/SclxImportCommitService.java");
        String bankReview = interchange("bank/BankStatementReviewService.java");
        String normalizedBank = interchange("bank/NormalizedBankCsvReviewService.java");

        assertTrue(transactionEntry.contains("ServiceAuthorization.actor("));
        assertFalse(transactionEntry.contains("audit(company, \"system\", \"TRANSACTION_UPDATED\""));
        assertFalse(transactionEntry.contains("audit(company, \"system\", \"TRANSACTION_ENTERED\""));
        assertTrue(transactionCorrection.contains("ServiceAuthorization.actor("));
        assertTrue(fixedAsset.contains("String authorizedActor = ServiceAuthorization.actor("));
        assertTrue(fixedAsset.contains("String normalizedActor = requireText(ServiceAuthorization.actor("));
        assertTrue(inventory.contains("String normalizedActor = requireText(ServiceAuthorization.actor("));
        assertTrue(periodClose.contains("String cleanActor = requireText(ServiceAuthorization.actor("));
        assertTrue(reconciliation.contains("bookkeepingActorForSession("));
        assertTrue(acceptance.contains("normalizedActor(ServiceAuthorization.actor("));
        assertTrue(userAdmin.contains("String authorizedActor = ServiceAuthorization.actor("));
        assertTrue(coa.contains("String authorizedActor = ServiceAuthorization.actor("));
        assertTrue(sclx.contains("authorizationGuard.requireActor("));
        assertTrue(sclx.contains("operationAudit.setActor(commitActor)"));
        assertTrue(bankReview.contains("authorizationGuard.requireActor("));
        assertTrue(bankReview.contains("audit.setActor(auditActor.trim())"));
        assertTrue(normalizedBank.contains("authorizationGuard.requireActor("));
        assertTrue(normalizedBank.contains("audit.setActor(auditActor.trim())"));

        // Historical source facts restored by SCLX keep their original historical actors.
        assertTrue(sclx.contains("value.actor()"));
    }

    @Test
    void productionActorDisplaysAreAuthenticatedAndNotEditable() throws Exception
    {
        String desktopActor = ui("DesktopActorIdentity.java");
        String importPreview = ui("ImportPreviewPanel.java");
        String userAdmin = ui("UserAdminPanel.java");
        String assets = ui("AssetsRegisterPanel.java");
        String inventory = ui("InventoryPanel.java");
        String periodClose = ui("PeriodCloseRunsPanel.java");
        String fixedAssetDialog = ui("FixedAssetLifecycleDialog.java");
        String bankTransactions = ui("BankTransactionsPanel.java");

        assertTrue(desktopActor.contains("authenticatedUser()"));
        assertTrue(desktopActor.contains("session.username()"));
        assertTrue(importPreview.contains("new TextField(DesktopActorIdentity.current())"));
        assertTrue(importPreview.contains("sclxActor.setEditable(false)"));
        assertFalse(importPreview.contains("new TextField(\"ui-operator\")"));
        assertTrue(userAdmin.contains("actor.setEditable(false)"));
        assertFalse(userAdmin.contains("System.getProperty(\"user.name\")"));
        assertTrue(assets.contains("lifecycleActor.setEditable(false)"));
        assertFalse(assets.contains("System.getProperty(\"user.name\""));
        assertTrue(inventory.contains("movementActor.setEditable(false)"));
        assertTrue(inventory.contains("lifecycleActor.setEditable(false)"));
        assertFalse(inventory.contains("new TextField(\"ui\")"));
        assertTrue(periodClose.contains("actor.setEditable(false)"));
        assertTrue(fixedAssetDialog.contains("actor.setEditable(false)"));
        assertTrue(bankTransactions.contains("DesktopActorIdentity.current()"));
        assertFalse(bankTransactions.contains("probableDuplicateConfirmed(), \"ui\")"));
    }

    private static String service(String filename) throws Exception
    {
        return Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/service", filename));
    }

    private static String interchange(String relativePath) throws Exception
    {
        return Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/interchange", relativePath));
    }

    private static String ui(String filename) throws Exception
    {
        return Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui", filename));
    }
}
