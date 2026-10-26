package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.AccountingReadiness;
import org.nonprofitbookkeeping.service.CoreAccountingPacketService;
import org.nonprofitbookkeeping.service.NmrItemTestFixture;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.DATE;

@ResourceLock("ui-service-registry")
class AccountingReadinessPaneTest
{
    @TempDir Path directory;
    private Stage stage;
    private DatePicker from, through;
    private AccountingReadinessPane pane;
    private java.util.Optional<org.nonprofitbookkeeping.service.AuthenticatedUserSession> previous;
    private final AtomicReference<String> scope = new AtomicReference<>("company/database");

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        previous = MainWindow.sharedSessionState().authenticatedUser();
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.manager());
            UiServiceRegistry.reconnectToDatabase(directory.resolve("ui-state"));
            return null;
        });
    }

    @AfterEach
    void cleanup()
    {
        FxTestSupport.onFx(() ->
        {
            if (stage != null) stage.close();
            DrillThroughCoordinator.configureOpener(null);
            for (var destination : AccountingReadiness.Destination.values())
            {
                DrillThroughCoordinator.consumeContext(AccountingReadinessPane.destination(destination));
            }
            previous.ifPresentOrElse(MainWindow.sharedSessionState()::setAuthenticatedUser,
                    MainWindow.sharedSessionState()::clearAuthenticatedUser);
            return null;
        });
    }

    @Test
    void realSnapshotRunsOffThreadAndFindingsNavigateWithLivePermissionGating() throws Exception
    {
        Path db = directory.resolve("facts");
        try (Jpa jpa = new Jpa(db))
        {
            NmrItemTestFixture.seed(jpa);
            var service = new CoreAccountingPacketService(() -> db, () -> NmrItemTestFixture.COMPANY, null);
            show((a, b) ->
            {
                assertFalse(Platform.isFxApplicationThread());
                return service.previewReadiness(a, b);
            });
            FxTestSupport.onFx(() ->
            {
                MainWindow.sharedSessionState().clearAuthenticatedUser();
                assertTrue(button("periodClosePreviewReadiness").isDisabled());
                MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.manager());
                button("periodClosePreviewReadiness").fire();
                assertTrue(button("periodClosePreviewReadiness").isDisabled());
                return null;
            });
            var result = pane.completion().get(60, TimeUnit.SECONDS);
            FxTestSupport.onFx(() ->
            {
                assertFalse(button("periodClosePreviewReadiness").isDisabled());
                assertEquals(result.findings().size(), table().getItems().size());
                assertTrue(status().contains("Content revision: " + result.dataRevision()));
                assertTrue(status().contains("external evidence not independently verified"));
                AtomicReference<AppPanelId> opened = new AtomicReference<>();
                DrillThroughCoordinator.configureOpener(opened::set);
                table().getSelectionModel().select(0);
                var selected = table().getSelectionModel().getSelectedItem();
                button("periodCloseOpenFinding").fire();
                assertEquals(AccountingReadinessPane.destination(selected.destination()), opened.get());
                assertTrue(DrillThroughCoordinator.consumeContext(opened.get()).contains(selected.area()));
                assertTrue(table().getColumns().stream().allMatch(c -> c.isSortable() && c.isResizable() && c.isReorderable()));
                MainWindow.sharedSessionState().clearAuthenticatedUser();
                assertTrue(table().getItems().isEmpty());
                assertTrue(button("periodCloseOpenFinding").isDisabled());
                return null;
            });
        }
    }

    @Test
    void editsAwayAndBackDiscardPendingResultsRatherThanReusingOldScope() throws Exception
    {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        show((a, b) ->
        {
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return sample(a, b);
        });
        FxTestSupport.onFx(() -> { button("periodClosePreviewReadiness").fire(); return null; });
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        var completion = pane.completion();
        try
        {
            FxTestSupport.onFx(() ->
            {
                through.setValue(DATE.plusDays(1));
                through.setValue(DATE);
                return null;
            });
        }
        finally { release.countDown(); }
        assertThrows(java.util.concurrent.CancellationException.class, () -> completion.get(10, TimeUnit.SECONDS));
        FxTestSupport.onFx(() ->
        {
            assertTrue(table().getItems().isEmpty());
            assertTrue(status().contains("Preview again"));
            assertFalse(button("periodClosePreviewReadiness").isDisabled());
            return null;
        });
    }

    @Test
    void companyDatabaseSwitchDiscardsPendingResultAndFailureClearsPreviousFindings() throws Exception
    {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        show((a, b) ->
        {
            int call = calls.incrementAndGet();
            if (call == 1)
            {
                entered.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
            }
            if (call == 3) throw new java.io.IOException("Synthetic unavailable source");
            return sample(a, b);
        });
        FxTestSupport.onFx(() -> { button("periodClosePreviewReadiness").fire(); return null; });
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        scope.set("different company/database");
        release.countDown();
        assertThrows(java.util.concurrent.CancellationException.class, () -> pane.completion().get(10, TimeUnit.SECONDS));
        FxTestSupport.onFx(() -> { button("periodClosePreviewReadiness").fire(); return null; });
        pane.completion().get(10, TimeUnit.SECONDS);
        FxTestSupport.onFx(() ->
        {
            assertFalse(table().getItems().isEmpty());
            button("periodClosePreviewReadiness").fire();
            assertTrue(table().getItems().isEmpty());
            return null;
        });
        assertThrows(java.util.concurrent.ExecutionException.class, () -> pane.completion().get(10, TimeUnit.SECONDS));
        FxTestSupport.onFx(() ->
        {
            assertTrue(table().getItems().isEmpty());
            assertTrue(status().contains("Could not preview readiness"));
            assertFalse(button("periodClosePreviewReadiness").isDisabled());
            return null;
        });
    }

    private void show(AccountingReadinessPane.Capture capture)
    {
        FxTestSupport.onFx(() ->
        {
            from = new DatePicker(DATE); through = new DatePicker(DATE);
            pane = new AccountingReadinessPane(from, through, () -> capture, scope::get);
            stage = new Stage();
            stage.setScene(new Scene(new BorderPane(pane), 900, 650));
            stage.show();
            return null;
        });
    }

    private static AccountingReadiness sample(java.time.LocalDate start, java.time.LocalDate end)
    {
        return new AccountingReadiness("DEFAULT", start, end, Instant.EPOCH, "snapshot", "revision", List.of("Facts"),
                List.of(new AccountingReadiness.Finding("id", AccountingReadiness.Severity.WARNING, "Custody", "1", "Review count", AccountingReadiness.Destination.CUSTODY)));
    }

    private Button button(String id)
    {
        return (Button) pane.lookup("#" + id);
    }

    @SuppressWarnings("unchecked")
    private TableView<AccountingReadiness.Finding> table()
    {
        return (TableView<AccountingReadiness.Finding>) pane.lookup("#periodCloseReadinessFindings");
    }

    private String status()
    {
        return ((Label) pane.lookup("#periodCloseReadinessStatus")).getText();
    }
}
