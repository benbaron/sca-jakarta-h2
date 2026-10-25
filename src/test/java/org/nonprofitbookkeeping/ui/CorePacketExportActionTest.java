package org.nonprofitbookkeeping.ui;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.CoreAccountingPacketService;
import org.nonprofitbookkeeping.service.NmrItemTestFixture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class CorePacketExportActionTest
{
    @TempDir Path directory;

    @Test
    void actualActionExportsOffThreadShowsIncompleteStatusAndTracksPermission() throws Exception
    {
        FxTestSupport.initToolkitOrSkip();
        Path db = directory.resolve("source");
        try (Jpa jpa = new Jpa(db))
        {
            NmrItemTestFixture.seed(jpa);
            var previous = MainWindow.sharedSessionState().authenticatedUser();
            AtomicReference<String> status = new AtomicReference<>();
            AtomicReference<CorePacketExportAction> action = new AtomicReference<>();
            AtomicReference<Stage> stage = new AtomicReference<>();
            Path target = directory.resolve("core.zip");
            try
            {
                FxTestSupport.onFx(() ->
                {
                    javafx.application.Platform.setImplicitExit(false);
                    MainWindow.sharedSessionState().clearAuthenticatedUser();
                    action.set(new CorePacketExportAction(
                            () -> new CoreAccountingPacketService(() -> db, () -> NmrItemTestFixture.COMPANY, null),
                            () -> LocalDate.of(2026, 1, 1), () -> LocalDate.of(2026, 12, 31),
                            () -> target, status::set));
                    Stage window = new Stage();
                    window.setScene(new Scene(new StackPane(action.get().button()), 700, 160));
                    window.show();
                    stage.set(window);
                    assertTrue(action.get().button().isDisabled());
                    MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.manager());
                    assertFalse(action.get().button().isDisabled());
                    assertTrue(action.get().button().getText().contains("Export Accounting Packet"));
                    action.get().button().fire();
                    assertTrue(status.get().contains("Exporting"));
                    assertTrue(action.get().button().isDisabled());
                    assertNotNull(action.get().completion());
                    return null;
                });
                var result = action.get().completion().get(60, TimeUnit.SECONDS);
                assertTrue(result.sections().containsAll(java.util.List.of("NMR Movements", "Property Custody", "Budget vs Actual", "Exceptions and Notes")));
                assertTrue(Files.isRegularFile(target));
                assertTrue(status.get().contains("INCOMPLETE"));
                FxTestSupport.onFx(() ->
                {
                    assertFalse(action.get().button().isDisabled());
                    return null;
                });
                try (ZipFile zip = new ZipFile(target.toFile())) { assertNotNull(zip.getEntry("manifest.json")); }
            }
            finally
            {
                FxTestSupport.onFx(() ->
                {
                    if (stage.get() != null) stage.get().close();
                    previous.ifPresentOrElse(MainWindow.sharedSessionState()::setAuthenticatedUser,
                            MainWindow.sharedSessionState()::clearAuthenticatedUser);
                    return null;
                });
            }
        }
    }
}
