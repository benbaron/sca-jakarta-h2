package org.nonprofitbookkeeping.ui;

import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.InterchangeMessageSeverity;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.interchange.sclx.SclxExportCounts;
import org.nonprofitbookkeeping.interchange.sclx.SclxExportRequest;
import org.nonprofitbookkeeping.interchange.sclx.SclxExportResult;
import org.nonprofitbookkeeping.interchange.sclx.SclxExportSection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxExportCoordinatorTest
{
    @TempDir
    Path tempDir;

    @Test
    void normalizesExtensionAndBuildsSafeDefaultFilename()
    {
        Path selected = tempDir.resolve("company-export");
        assertEquals(
                tempDir.resolve("company-export.sclx").toAbsolutePath().normalize(),
                SclxExportCoordinator.normalizeSclxPath(selected));
        assertEquals(
                tempDir.resolve("already.SCLX").toAbsolutePath().normalize(),
                SclxExportCoordinator.normalizeSclxPath(tempDir.resolve("already.SCLX")));
        assertEquals("CAER_GALEN-active-company.sclx",
                SclxExportCoordinator.defaultFilename("CAER/GALEN"));
    }

    @Test
    void availabilityTracksSelectedCompanyAndDatabaseFailure()
    {
        WorkspaceContext context = new WorkspaceContext(
                tempDir.resolve("ledger.mv.db"),
                "ALPHA",
                LocalDate.of(2026, 7, 27));
        SclxExportCoordinator coordinator = coordinator(context, request -> null, new RecordingDialogs());

        assertTrue(coordinator.availableProperty().get());
        context.setDatabaseFailure(new IllegalStateException("database unavailable"));
        assertFalse(coordinator.availableProperty().get());
        context.setDatabaseFailure(null);
        context.setActiveCompanyCode("   ");
        assertFalse(coordinator.availableProperty().get());
    }


    @Test
    void capturesCompanyAndDatabaseScopeBeforeBackgroundExecution()
    {
        FxTestSupport.onFx(() ->
        {
            WorkspaceContext context = context();
            RecordingDialogs dialogs = new RecordingDialogs();
            dialogs.selection = Optional.of(tempDir.resolve("alpha.sclx"));
            AtomicReference<String> capturedCompany = new AtomicReference<>();
            AtomicReference<Path> capturedDatabase = new AtomicReference<>();
            AtomicReference<Runnable> queuedTask = new AtomicReference<>();
            Stage owner = new Stage();
            SclxExportCoordinator coordinator = new SclxExportCoordinator(
                    (companyCode, databasePath) ->
                    {
                        capturedCompany.set(companyCode);
                        capturedDatabase.set(databasePath);
                        return request -> null;
                    },
                    context,
                    () -> owner,
                    dialogs,
                    queuedTask::set,
                    () -> Instant.parse("2026-07-27T04:30:00Z"));

            coordinator.requestExport();
            context.setActiveCompanyCode("BETA");
            context.setActiveDatabasePath(tempDir.resolve("other.mv.db"));

            assertEquals("ALPHA", capturedCompany.get());
            assertEquals(tempDir.resolve("ledger.mv.db").toAbsolutePath().normalize(), capturedDatabase.get());
            assertTrue(queuedTask.get() != null);
            owner.close();
            return null;
        });
    }

    @Test
    void cancelBeforeSelectionDoesNotInvokeExport()
    {
        FxTestSupport.onFx(() ->
        {
            WorkspaceContext context = context();
            AtomicInteger calls = new AtomicInteger();
            RecordingDialogs dialogs = new RecordingDialogs();
            dialogs.selection = Optional.empty();
            Stage owner = new Stage();
            SclxExportCoordinator coordinator = new SclxExportCoordinator(
                    request ->
                    {
                        calls.incrementAndGet();
                        return null;
                    },
                    context,
                    () -> owner,
                    dialogs,
                    Runnable::run,
                    () -> Instant.parse("2026-07-27T04:30:00Z"));

            coordinator.requestExport();

            assertEquals(0, calls.get());
            assertTrue(coordinator.statusProperty().get().contains("cancelled"));
            owner.close();
            return null;
        });
    }

    @Test
    void declinedOverwriteLeavesExistingFileUnchanged() throws Exception
    {
        Path existing = tempDir.resolve("active-company.sclx");
        Files.writeString(existing, "original");
        FxTestSupport.onFx(() ->
        {
            WorkspaceContext context = context();
            AtomicInteger calls = new AtomicInteger();
            RecordingDialogs dialogs = new RecordingDialogs();
            dialogs.selection = Optional.of(existing);
            dialogs.overwriteConfirmed = false;
            Stage owner = new Stage();
            SclxExportCoordinator coordinator = new SclxExportCoordinator(
                    request ->
                    {
                        calls.incrementAndGet();
                        return null;
                    },
                    context,
                    () -> owner,
                    dialogs,
                    Runnable::run,
                    () -> Instant.parse("2026-07-27T04:30:00Z"));

            coordinator.requestExport();

            assertEquals(0, calls.get());
            assertTrue(dialogs.overwriteAsked);
            owner.close();
            return null;
        });
        assertEquals("original", Files.readString(existing));
    }

    @Test
    void resultSummaryNamesScopeCountsWarningsExclusionsAndHash()
    {
        SclxExportResult result = new SclxExportResult(
                tempDir.resolve("alpha.sclx"),
                "SCLX",
                "1.3",
                Instant.parse("2026-07-27T04:30:00Z"),
                "organization:ALPHA",
                "ALPHA",
                1234L,
                "a".repeat(64),
                new SclxExportCounts(1, 4, 2, 1, 3, 2, 3, 5, 10, 2, 2, 1, 31),
                List.of(new InterchangeValidationMessage(
                        InterchangeMessageSeverity.WARNING,
                        "SCLX_DEFERRED_SECTION",
                        "extensions.scaJakartaH2.supplementalDetails",
                        "Supplemental details are deferred.",
                        false)),
                List.of(SclxExportSection.SUPPLEMENTAL_DETAILS),
                List.of(SclxExportSection.UI_STATE));

        String summary = SclxExportCoordinator.resultSummary(result);

        assertTrue(summary.contains("Active company: ALPHA"));
        assertTrue(summary.contains("Accounts: 4"));
        assertTrue(summary.contains("Activities: 1"));
        assertTrue(summary.contains("Counterparties: 3"));
        assertTrue(summary.contains("Merchants: 2"));
        assertTrue(summary.contains("Transactions: 5"));
        assertTrue(summary.contains("Warnings: 2"));
        assertTrue(summary.contains("Supplemental details are deferred."));
        assertTrue(summary.contains("extensions.scaJakartaH2.supplementalDetails"));
        assertTrue(summary.contains("UI_STATE"));
        assertTrue(summary.contains("a".repeat(64)));
    }

    private WorkspaceContext context()
    {
        return new WorkspaceContext(
                tempDir.resolve("ledger.mv.db"),
                "ALPHA",
                LocalDate.of(2026, 7, 27));
    }

    private static SclxExportCoordinator coordinator(
            WorkspaceContext context,
            java.util.function.Function<SclxExportRequest, SclxExportResult> operation,
            SclxExportDialogs dialogs)
    {
        return new SclxExportCoordinator(
                operation,
                context,
                () -> null,
                dialogs,
                Runnable::run,
                () -> Instant.parse("2026-07-27T04:30:00Z"));
    }

    private static final class RecordingDialogs implements SclxExportDialogs
    {
        private Optional<Path> selection = Optional.empty();
        private boolean overwriteConfirmed;
        private boolean overwriteAsked;

        @Override
        public Optional<Path> chooseDestination(Window owner, String companyCode)
        {
            return selection;
        }

        @Override
        public boolean confirmOverwrite(Window owner, Path destination)
        {
            overwriteAsked = true;
            return overwriteConfirmed;
        }

        @Override
        public void showCompleted(Window owner, SclxExportResult result, String details)
        {
        }

        @Override
        public void showFailure(Window owner, String message)
        {
        }
    }
}
