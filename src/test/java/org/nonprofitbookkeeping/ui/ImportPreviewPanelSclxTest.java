package org.nonprofitbookkeeping.ui;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.interchange.InterchangeFormat;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;
import org.nonprofitbookkeeping.interchange.InterchangeMessageSeverity;
import org.nonprofitbookkeeping.interchange.InterchangeOperationCounts;
import org.nonprofitbookkeeping.interchange.InterchangeOperationMode;
import org.nonprofitbookkeeping.interchange.InterchangePreview;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.interchange.sclx.SclxAccountMode;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportEntityPreview;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportMappingRequirement;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportPreview;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportPreviewCounts;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportTransactionPreview;
import org.nonprofitbookkeeping.interchange.sclx.SclxVersion;
import org.nonprofitbookkeeping.service.ImportPreviewService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("javafx-runtime")
class ImportPreviewPanelSclxTest
{
    @Test
    void rendersReadOnlyCountsMappingsIdentityAndTransactionDiagnostics()
    {
        FxTestSupport.onFx(() -> {
            ImportPreviewPanel panel = new ImportPreviewPanel(
                    new ImportPreviewService(), source -> preview());
            new Scene((javafx.scene.Parent) panel.root(), 1000, 700);

            panel.applySclxPreview(preview());

            Label status = (Label) panel.root().lookup("#importPreviewStatus");
            ListView<?> counts = (ListView<?>) panel.root().lookup("#sclxPreviewCounts");
            TableView<?> entities = (TableView<?>) panel.root().lookup("#sclxPreviewEntities");
            TableView<?> mappings = (TableView<?>) panel.root().lookup("#sclxPreviewMappings");
            TableView<?> transactions = (TableView<?>) panel.root().lookup("#sclxPreviewTransactions");
            Button commitCoa = (Button) panel.root().lookup("#commitAcceptedCoaRowsButton");
            Button commitSclx = (Button) panel.root().lookup("#commitPreviewedSclxButton");
            Button applyMappings = (Button) panel.root().lookup("#applySclxMappingsButton");

            assertTrue(status.getText().contains("READY TO IMPORT"));
            assertTrue(status.getText().contains("No data was changed"));
            assertEquals(6, counts.getItems().size());
            assertEquals(1, entities.getItems().size());
            assertEquals(1, mappings.getItems().size());
            assertEquals(1, transactions.getItems().size());
            assertTrue(commitCoa.isDisabled());
            assertTrue(commitSclx.isDisabled(), "a rendered result without its exact source cannot commit");
            assertTrue(applyMappings.isDisabled());
            return null;
        });
    }

    @Test
    void confirmationNamesDonorCompatibilityAssignments()
    {
        String text = ImportPreviewPanel.sclxCompatibilityConfirmationText(preview());

        assertTrue(text.contains("Donor compatibility decisions applied"));
        assertTrue(text.contains("32 fundless transaction line(s) to General Fund"));
    }

    @Test
    void existingCompanyMappingPreviewRequiresExplicitApproval()
    {
        FxTestSupport.onFx(() -> {
            ImportPreviewPanel panel = new ImportPreviewPanel(
                    new ImportPreviewService(), source -> mappedPreview());
            new Scene((javafx.scene.Parent) panel.root(), 1000, 700);

            panel.applySclxPreview(mappedPreview());

            Label status = (Label) panel.root().lookup("#importPreviewStatus");
            CheckBox approval = (CheckBox) panel.root().lookup("#confirmSclxMappings");
            CheckBox existingCompany =
                    (CheckBox) panel.root().lookup("#confirmExistingCompanySclxImport");
            Button commit = (Button) panel.root().lookup("#commitPreviewedSclxButton");
            assertTrue(status.getText().contains("APPROVE EXISTING-COMPANY IMPORT"));
            assertTrue(approval.isVisible());
            assertTrue(approval.isManaged());
            assertTrue(existingCompany.isVisible());
            assertTrue(existingCompany.isManaged());
            assertTrue(commit.isDisabled());
            return null;
        });
    }

    @Test
    void blockingMessageIsSelectedAndItsResolutionRemainsVisible()
    {
        FxTestSupport.onFx(() -> {
            SclxImportPreview blocked = ownershipBlockedPreview();
            ImportPreviewPanel panel = new ImportPreviewPanel(
                    new ImportPreviewService(), source -> blocked);
            new Scene((javafx.scene.Parent) panel.root(), 1000, 700);

            panel.applySclxPreview(blocked);

            Label status = (Label) panel.root().lookup("#importPreviewStatus");
            ListView<?> messages = (ListView<?>) panel.root().lookup("#importPreviewMessages");
            TextArea resolution = (TextArea) panel.root().lookup("#importPreviewMessageResolution");
            assertTrue(status.getText().contains("BLOCKED"));
            assertEquals(0, messages.getSelectionModel().getSelectedIndex());
            assertTrue(resolution.getText().contains("Administration -> Company Ownership Diagnostics"));
            assertTrue(resolution.getText().contains("Resolution:"));
            return null;
        });
    }

    private static SclxImportPreview mappedPreview()
    {
        SclxImportPreview base = preview();
        SclxImportMappingRequirement mapping = new SclxImportMappingRequirement(
                SclxImportMappingRequirement.Kind.ACCOUNT,
                "account:SOURCE:1010", "1010", "account:TEST:1010", "1010",
                true, SclxImportMappingRequirement.Resolution.MAPPED,
                "Approve the compatible target account.", false, List.of("1010", "1020"));
        InterchangePreview<SclxImportEntityPreview> operation = new InterchangePreview<>(
                base.operation().format(), base.operation().mode(), base.operation().sourceName(),
                base.operation().targetLabel(), base.operation().sourceSha256(),
                base.operation().items(), base.operation().messages(), base.operation().confirmations(),
                new InterchangeOperationCounts(1, 0, 1, 0, 0, 1, 0));
        return new SclxImportPreview(
                operation, base.version(), base.exportedAt(), base.sourceOrganizationId(),
                base.sourceOrganizationCode(), base.sourceOrganizationName(), base.sourceSystem(),
                base.targetCompanyCode(), base.targetCompanyName(), true, SclxAccountMode.MAPPED,
                base.sectionCounts(), List.of(mapping), base.transactions());
    }

    private static SclxImportPreview preview()
    {
        SclxImportEntityPreview entity = new SclxImportEntityPreview(
                "ORGANIZATION", "organization:TEST", "$.organization", "a".repeat(64),
                InterchangeIdentityMatch.NEW, null);
        InterchangeValidationMessage warning = new InterchangeValidationMessage(
                InterchangeMessageSeverity.WARNING, "SCLX_DONOR_GENERAL_FUND_ASSIGNED",
                "$.transactions[*].lines[*].fundId",
                "Assigned 32 fundless transaction line(s) to General Fund (General Fund).", false);
        InterchangePreview<SclxImportEntityPreview> operation = new InterchangePreview<>(
                InterchangeFormat.SCLX,
                InterchangeOperationMode.PREVIEW_ONLY,
                "sample.sclx",
                "TEST — Test Company",
                "b".repeat(64),
                List.of(entity),
                List.of(warning),
                List.of(),
                new InterchangeOperationCounts(1, 1, 0, 0, 0, 1, 0));
        SclxImportMappingRequirement mapping = new SclxImportMappingRequirement(
                SclxImportMappingRequirement.Kind.ACCOUNT,
                "account:SOURCE:1010", "1010", "account:TEST:1010", "1010",
                true, SclxImportMappingRequirement.Resolution.CREATE,
                "The import will create this account in the target chart.", false);
        SclxImportTransactionPreview transaction = new SclxImportTransactionPreview(
                "transaction:SOURCE:1", LocalDate.of(2026, 7, 1), "Test transaction",
                2, 2, 0, new BigDecimal("10.00"), new BigDecimal("10.00"),
                true, false, false, false);

        return new SclxImportPreview(
                operation,
                SclxVersion.V1_3,
                Instant.EPOCH,
                "organization:SOURCE",
                "SOURCE",
                "Source Company",
                "organization:SOURCE",
                "TEST",
                "Test Company",
                false,
                SclxAccountMode.AS_IS,
                new SclxImportPreviewCounts(Map.of("accounts", 1L, "organizations", 1L), 2, 3, 0, 0),
                List.of(mapping),
                List.of(transaction));
    }

    private static SclxImportPreview ownershipBlockedPreview()
    {
        SclxImportPreview base = preview();
        InterchangeValidationMessage error = new InterchangeValidationMessage(
                InterchangeMessageSeverity.ERROR,
                "SCLX_COMPANY_OWNERSHIP_UNRESOLVED",
                "companyOwnership.ACTIVITY.1",
                "Activity has no deterministic company owner. Resolution: Select the actual owner in "
                        + "Administration -> Company Ownership Diagnostics.",
                true);
        InterchangePreview<SclxImportEntityPreview> operation = new InterchangePreview<>(
                base.operation().format(), base.operation().mode(), base.operation().sourceName(),
                base.operation().targetLabel(), base.operation().sourceSha256(),
                base.operation().items(), List.of(error), base.operation().confirmations(),
                new InterchangeOperationCounts(1, 1, 0, 0, 0, 0, 1));
        return new SclxImportPreview(
                operation, base.version(), base.exportedAt(), base.sourceOrganizationId(),
                base.sourceOrganizationCode(), base.sourceOrganizationName(), base.sourceSystem(),
                base.targetCompanyCode(), base.targetCompanyName(), false, base.recommendedAccountMode(),
                base.sectionCounts(), base.mappings(), base.transactions());
    }
}
