package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class PeriodCloseStatePanelTest
{
    @TempDir Path directory;
    private UiSessionState session;
    private DatabaseSelectionState oldDatabase;
    private MultiCompanyState oldCompany;
    private java.util.Optional<AuthenticatedUserSession> oldUser;
    private LocalDate oldPeriod;
    private javafx.stage.Stage stage;
    private Jpa jpa;
    private PeriodCloseRunsPanel panel;
    private ControlledService service;
    private PeriodCloseRangeView first;
    private final AtomicReference<PeriodCloseRangeService> authority = new AtomicReference<>();

    @BeforeEach
    void setup() throws Exception
    {
        FxTestSupport.initToolkitOrSkip();
        session = MainWindow.sharedSessionState();
        oldDatabase = session.databaseSelection(); oldCompany = session.multiCompany();
        oldUser = session.authenticatedUser(); oldPeriod = ActivePeriodContext.get();
        Path db = directory.resolve("period-state");
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT", "OTHER")));
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            session.setDatabaseSelection(new DatabaseSelectionState(db.toString(), List.of(db.toString())));
            UiServiceRegistry.reconnectToDatabase(db);
            ActivePeriodContext.set(LocalDate.of(2026, 3, 1));
            return null;
        });
        jpa = new Jpa(db);
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            Company other = new Company(); other.setCode("OTHER"); other.setDisplayName("Other company");
            em.persist(other);
            em.getTransaction().commit();
        }
        first = PeriodCloseTestFixture.historical(jpa, "DEFAULT", LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 15), "CUSTOM", "tester", "First half");
        PeriodCloseTestFixture.historical(jpa, "DEFAULT", LocalDate.of(2026, 3, 16),
                LocalDate.of(2026, 3, 31), "CUSTOM", "tester", "Second half");
        service = new ControlledService(jpa); authority.set(service);
        FxTestSupport.onFx(() ->
        {
            panel = new PeriodCloseRunsPanel(authority::get);
            Scene scene = new Scene((Parent) panel.root(), 1280, 800);
            scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
            stage = new javafx.stage.Stage(); stage.setScene(scene); stage.show();
            scene.getRoot().applyCss(); scene.getRoot().layout();
            return null;
        });
        awaitLoad();
    }

    @AfterEach
    void cleanup() throws Exception
    {
        if (session == null) return;
        if (service != null) { service.releaseReopen.countDown(); service.releaseLoad.countDown(); }
        FxTestSupport.onFx(() ->
        {
            if (stage != null) stage.close();
            DrillThroughCoordinator.configureOpener(null);
            session.setDatabaseSelection(oldDatabase); session.setMultiCompany(oldCompany);
            oldUser.ifPresentOrElse(session::setAuthenticatedUser, session::clearAuthenticatedUser);
            ActivePeriodContext.set(oldPeriod);
            return null;
        });
        if (jpa != null) jpa.close();
        UiServiceRegistry.reconnectToDatabase(Path.of(oldDatabase.activeDatabasePath()));
    }

    @Test
    void reopenCapturesInputsAndAuthorityBlocksDuplicatesAndRefreshesCoverage() throws Exception
    {
        service.holdReopen = true;
        FxTestSupport.onFx(() ->
        {
            assertTrue(label().contains("Closed"), label());
            table().getSelectionModel().select(first);
            reason().setText("Original reason");
            policy().setValue(ClosedPeriodPolicy.REQUIRE_REASON);
            ((CheckBox) panel.root().lookup("#periodCloseRequireReason")).setSelected(true);
            button("periodCloseReopenSelected").fire();
            assertTrue(button("periodCloseReopenSelected").isDisabled());
            assertTrue(button("periodCloseSaveClosedRange").isDisabled());
            assertTrue(button("periodCloseUseActivePeriod").isDisabled());
            assertTrue(reason().isDisabled()); assertTrue(policy().isDisabled());
            button("periodCloseReopenSelected").fire();
            // Programmatic edits prove the worker does not read live controls.
            reason().setText("Later draft"); policy().setValue(ClosedPeriodPolicy.WARN_AND_REOPEN);
            authority.set(new PeriodCloseRangeService(jpa));
            return null;
        });
        assertTrue(service.enteredReopen.await(10, TimeUnit.SECONDS));
        assertFalse(service.workerWasFx);
        assertEquals("Original reason", service.capturedReason);
        assertEquals(ClosedPeriodPolicy.REQUIRE_REASON, service.capturedPolicy);
        assertTrue(service.capturedRequired);
        service.releaseReopen.countDown();
        FxTestSupport.onFx(panel::reopenCompletionForTests).get(20, TimeUnit.SECONDS);
        awaitLoad();
        FxTestSupport.onFx(() ->
        {
            assertEquals(1, service.reopenCalls.get());
            assertFalse(button("periodCloseReopenSelected").isDisabled());
            assertTrue(label().contains("Partially closed"), label());
            assertTrue(((Label) panel.root().lookup("#periodCloseStatus")).getText().startsWith("Reopened"));
            assertEquals("", reason().getText());
            return null;
        });
        assertEquals("Original reason", service.loadRange(first.id()).reopenReason());
        assertEquals(3, service.listEvents("DEFAULT").size());
    }

    @Test
    void policyFailureRetainsReasonAndReleasesBusyStateWithoutChangingHistory() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            table().getSelectionModel().select(first);
            reason().setText(" "); policy().setValue(ClosedPeriodPolicy.REQUIRE_REASON);
            button("periodCloseReopenSelected").fire();
            return null;
        });
        assertThrows(ExecutionException.class,
                () -> FxTestSupport.onFx(panel::reopenCompletionForTests).get(20, TimeUnit.SECONDS));
        FxTestSupport.onFx(() ->
        {
            assertFalse(button("periodCloseReopenSelected").isDisabled());
            assertEquals(" ", reason().getText());
            assertTrue(((Label) panel.root().lookup("#periodCloseStatus")).getText().startsWith("Could not reopen"));
            session.setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(button("periodCloseReopenSelected").isDisabled());
            return null;
        });
        assertTrue(service.loadRange(first.id()).closed());
        assertEquals(2, service.listEvents("DEFAULT").size());
    }

    @Test
    void dashboardUsesSameCoverageAndNavigatesToAllAffectedRows() throws Exception
    {
        Scene closeScene = FxTestSupport.onFx(stage::getScene);
        DashboardHomePanel dashboard = FxTestSupport.onFx(() ->
        {
            snapshot("period-close");
            var view = new DashboardHomePanel(UiServiceRegistry.dashboardQuery(), ActivePeriodContext::get,
                    () -> "DEFAULT", CompanyUiFormat.activeCompany());
            PanelViewport.prepare(view.root());
            var scene = new Scene(PanelViewport.wrap(view.root()), 900, 650);
            scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
            stage.setScene(scene); stage.setWidth(900); stage.setHeight(650);
            return view;
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!FxTestSupport.onFx(() ->
                !((Hyperlink) dashboard.root().lookup("#dashboardPeriodCloseLink")).isDisabled()))
        {
            if (System.nanoTime() > deadline) fail("Dashboard period state did not load");
            Thread.sleep(25);
        }
        FxTestSupport.onFx(() ->
        {
            Hyperlink link = (Hyperlink) dashboard.root().lookup("#dashboardPeriodCloseLink");
            assertTrue(link.getText().contains("Closed"), link.getText());
            assertTrue(link.isWrapText());
            stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
            assertTrue(link.getHeight() >= link.prefHeight(link.getWidth()) - 1,
                    "The whole period-state link must fit or wrap");
            snapshot("dashboard");
            DrillThroughCoordinator.configureOpener(id ->
            {
                assertEquals(AppPanelId.PERIOD_CLOSE_RUNS, id);
                stage.setScene(closeScene); panel.onPanelShown();
            });
            link.fire();
            return null;
        });
        awaitLoad();
        FxTestSupport.onFx(() ->
        {
            assertEquals(2, table().getSelectionModel().getSelectedItems().size());
            assertEquals(LocalDate.of(2026, 3, 1), ActivePeriodContext.get());
            assertTrue(PeriodCloseNavigation.parse("period-close|broken").isEmpty());
            return null;
        });
        assertEquals(2, service.listEvents("DEFAULT").size());
    }

    private void snapshot(String name)
    {
        String output = System.getProperty("periodClosePreviewDir");
        if (output == null) return;
        try
        {
            java.nio.file.Files.createDirectories(Path.of(output));
            var root = stage.getScene().getRoot(); root.applyCss(); root.layout();
            var image = root.snapshot(null, null);
            var pixels = image.getPixelReader();
            var buffered = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < buffered.getHeight(); y++)
            {
                for (int x = 0; x < buffered.getWidth(); x++) buffered.setRGB(x, y, pixels.getArgb(x, y));
            }
            javax.imageio.ImageIO.write(buffered, "png", Path.of(output).resolve(name + ".png").toFile());
        }
        catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
    }

    @Test
    void scopeChangeDuringReopenCannotClearAnotherCompanyDraft() throws Exception
    {
        service.holdReopen = true;
        FxTestSupport.onFx(() ->
        {
            table().getSelectionModel().select(first); reason().setText("Original scope");
            button("periodCloseReopenSelected").fire();
            return null;
        });
        assertTrue(service.enteredReopen.await(10, TimeUnit.SECONDS));
        FxTestSupport.onFx(() ->
        {
            session.setMultiCompany(new MultiCompanyState("OTHER", List.of("DEFAULT", "OTHER")));
            reason().setText("New scope draft");
            return null;
        });
        service.releaseReopen.countDown();
        FxTestSupport.onFx(panel::reopenCompletionForTests).get(20, TimeUnit.SECONDS);
        awaitLoad();
        FxTestSupport.onFx(() ->
        {
            assertEquals("New scope draft", reason().getText());
            assertTrue(table().getItems().isEmpty());
            assertTrue(label().endsWith("Open"));
            return null;
        });
    }

    @Test
    void oldCompanyResponseCannotReplaceCurrentRowsOrSummary() throws Exception
    {
        service.holdNextLoad.set(true);
        FxTestSupport.onFx(() -> { button("periodCloseRefresh").fire(); return null; });
        var oldLoad = FxTestSupport.onFx(panel::loadCompletionForTests);
        assertTrue(service.enteredLoad.await(10, TimeUnit.SECONDS));
        FxTestSupport.onFx(() ->
        {
            session.setMultiCompany(new MultiCompanyState("OTHER", List.of("DEFAULT", "OTHER")));
            panel.onPanelShown();
            return null;
        });
        awaitLoad();
        service.releaseLoad.countDown();
        assertThrows(CancellationException.class, () -> oldLoad.get(20, TimeUnit.SECONDS));
        FxTestSupport.onFx(() ->
        {
            assertTrue(table().getItems().isEmpty());
            assertTrue(label().endsWith("Open"), label());
            return null;
        });
    }

    @Test
    void explicitFailureNavigationSelectsExactRangeAndPreservesDraftsWithoutReopening() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            picker("periodCloseStartDate").setValue(LocalDate.of(2026, 1, 3));
            picker("periodCloseEndDate").setValue(LocalDate.of(2026, 1, 5));
            reason().setText("Keep this close draft");
            policy().setValue(ClosedPeriodPolicy.REQUIRE_REASON);
            DrillThroughCoordinator.configureOpener(id ->
            {
                assertEquals(AppPanelId.PERIOD_CLOSE_RUNS, id);
                panel.onPanelShown();
            });
            Hyperlink link = PeriodCloseNavigation.failureLink("test-affected-range");
            var error = new RuntimeException(new ClosedPeriodRangeException(first.id(), "DEFAULT",
                    LocalDate.of(2026, 3, 10), "save transaction"));
            PeriodCloseNavigation.showFailure(link, error, PeriodCloseNavigation.database());
            assertTrue(link.isVisible()); link.fire();
            return null;
        });
        awaitLoad();
        FxTestSupport.onFx(() ->
        {
            assertEquals(first.id(), table().getSelectionModel().getSelectedItem().id());
            assertEquals(LocalDate.of(2026, 1, 3), picker("periodCloseStartDate").getValue());
            assertEquals(LocalDate.of(2026, 1, 5), picker("periodCloseEndDate").getValue());
            assertEquals("Keep this close draft", reason().getText());
            assertEquals(ClosedPeriodPolicy.REQUIRE_REASON, policy().getValue());
            assertEquals(LocalDate.of(2026, 3, 1), ActivePeriodContext.get());
            Hyperlink stale = PeriodCloseNavigation.failureLink("stale");
            PeriodCloseNavigation.showFailure(stale, new ClosedPeriodRangeException(first.id(), "DEFAULT",
                    LocalDate.of(2026, 3, 10), "save"), "another-database");
            assertFalse(stale.isVisible());
            return null;
        });
        assertTrue(service.loadRange(first.id()).closed());
        assertEquals(2, service.listEvents("DEFAULT").size());
    }

    private void awaitLoad() throws Exception { FxTestSupport.onFx(panel::loadCompletionForTests).get(20, TimeUnit.SECONDS); }
    private Button button(String id) { return (Button) panel.root().lookup("#" + id); }
    private DatePicker picker(String id) { return (DatePicker) panel.root().lookup("#" + id); }
    private TextField reason() { return (TextField) panel.root().lookup("#periodCloseReason"); }
    private String label() { return ((Label) panel.root().lookup("#periodCloseActivePeriodRange")).getText(); }
    @SuppressWarnings("unchecked") private ComboBox<ClosedPeriodPolicy> policy() { return (ComboBox<ClosedPeriodPolicy>) panel.root().lookup("#periodCloseReopenPolicy"); }
    @SuppressWarnings("unchecked") private TableView<PeriodCloseRangeView> table() { return (TableView<PeriodCloseRangeView>) panel.root().lookup("#periodCloseRangeTable"); }

    private static final class ControlledService extends PeriodCloseRangeService
    {
        final AtomicInteger reopenCalls = new AtomicInteger();
        final AtomicBoolean holdNextLoad = new AtomicBoolean();
        final CountDownLatch enteredReopen = new CountDownLatch(1), releaseReopen = new CountDownLatch(1);
        final CountDownLatch enteredLoad = new CountDownLatch(1), releaseLoad = new CountDownLatch(1);
        volatile boolean holdReopen, workerWasFx, capturedRequired;
        volatile String capturedReason;
        volatile ClosedPeriodPolicy capturedPolicy;
        ControlledService(Jpa jpa) { super(jpa); }
        @Override public PeriodCloseRangeView reopenRange(UUID id, String actor, String reason, ClosedPeriodPolicy policy, boolean required)
        {
            reopenCalls.incrementAndGet(); workerWasFx = Platform.isFxApplicationThread();
            capturedReason = reason; capturedPolicy = policy; capturedRequired = required;
            enteredReopen.countDown();
            if (holdReopen) waitFor(releaseReopen);
            return super.reopenRange(id, actor, reason, policy, required);
        }
        @Override public List<PeriodCloseRangeView> listRanges(String company)
        {
            var rows = super.listRanges(company);
            if (holdNextLoad.compareAndSet(true, false))
            {
                enteredLoad.countDown(); waitFor(releaseLoad);
            }
            return rows;
        }
        private static void waitFor(CountDownLatch latch)
        {
            try { if (!latch.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Test gate timed out"); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
        }
    }
}
