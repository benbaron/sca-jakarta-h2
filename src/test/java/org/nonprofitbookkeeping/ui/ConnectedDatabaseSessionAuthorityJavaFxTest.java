package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("javafx-runtime")
@ResourceLock("ui-service-registry")
class ConnectedDatabaseSessionAuthorityJavaFxTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    void dirtyCancellationDoesNotPrepareOrChangeDatabase(@TempDir Path tempDir)
    {
        Path source = tempDir.resolve("source.mv.db").toAbsolutePath().normalize();
        Path target = tempDir.resolve("target.mv.db").toAbsolutePath().normalize();
        UiSessionState session = MainWindow.sharedSessionState();
        DatabaseSelectionState originalDatabase = session.databaseSelection();
        MultiCompanyState originalCompany = session.multiCompany();

        try
        {
            session.setDatabaseSelection(new DatabaseSelectionState(source.toString(), List.of(source.toString())));
            session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
            UiServiceRegistry.reconnectToDatabase(source);
            FileAppStateStore store = new FileAppStateStore(tempDir.resolve("state.properties"));
            store.saveDatabaseSession(session.databaseSelection(), session.multiCompany());
            AtomicInteger prepareCount = new AtomicInteger();

            ProductionWorkspaceWindow window = FxTestSupport.onFx(() -> {
                ProductionWorkspaceWindow created = new ProductionWorkspaceWindow(
                        store,
                        (path, preferred) -> {
                            prepareCount.incrementAndGet();
                            throw new IllegalStateException("must not prepare after cancellation");
                        });
                created.panelHost().showReplacement(AppPanelId.HELP, new DirtyPanel());
                created.databaseChangePromptForTests((from, to, dirtyTitles) -> false);
                created.connectDatabaseForTests(target);
                return created;
            });

            assertEquals(0, prepareCount.get());
            assertEquals(source, window.activeDatabasePathForTests());
            assertFalse(window.databaseRecoveryVisibleForTests());
        }
        finally
        {
            session.setDatabaseSelection(originalDatabase);
            session.setMultiCompany(originalCompany);
            restoreRegistry(originalDatabase);
        }
    }

    @Test
    void failedTargetPreparationKeepsHealthySourceSession(@TempDir Path tempDir)
    {
        Path source = tempDir.resolve("source.mv.db").toAbsolutePath().normalize();
        Path target = tempDir.resolve("broken.mv.db").toAbsolutePath().normalize();
        UiSessionState session = MainWindow.sharedSessionState();
        DatabaseSelectionState originalDatabase = session.databaseSelection();
        MultiCompanyState originalCompany = session.multiCompany();

        try
        {
            session.setDatabaseSelection(new DatabaseSelectionState(source.toString(), List.of(source.toString())));
            session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
            UiServiceRegistry.reconnectToDatabase(source);
            FileAppStateStore store = new FileAppStateStore(tempDir.resolve("state.properties"));
            store.saveDatabaseSession(session.databaseSelection(), session.multiCompany());

            ProductionWorkspaceWindow window = FxTestSupport.onFx(() -> {
                ProductionWorkspaceWindow created = new ProductionWorkspaceWindow(
                        store,
                        (path, preferred) -> {
                            throw new IllegalStateException("target validation failed");
                        });
                created.connectDatabaseForTests(target);
                return created;
            });

            assertEquals(source, window.activeDatabasePathForTests());
            assertEquals(source.toString(), session.databaseSelection().activeDatabasePath());
            assertFalse(window.databaseRecoveryVisibleForTests());
        }
        finally
        {
            session.setDatabaseSelection(originalDatabase);
            session.setMultiCompany(originalCompany);
            restoreRegistry(originalDatabase);
        }
    }

    private static void restoreRegistry(DatabaseSelectionState originalDatabase)
    {
        try
        {
            UiServiceRegistry.reconnectToDatabase(Path.of(originalDatabase.activeDatabasePath()));
        }
        catch (RuntimeException ignored)
        {
            // The next isolated UI test establishes its own disposable registry state.
        }
    }

    private record DirtyPanel() implements AppPanel
    {
        @Override
        public String title()
        {
            return "Dirty test panel";
        }

        @Override
        public Node root()
        {
            return new Label("dirty");
        }

        @Override
        public boolean hasUnsavedChanges()
        {
            return true;
        }
    }
}
