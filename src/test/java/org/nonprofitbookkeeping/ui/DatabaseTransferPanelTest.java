package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.DatabaseTransferService;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTransferPanelTest
{
    @TempDir
    Path tempDir;

    @Test
    void panelDisplaysExactPathsAndRoutesAllActions()
    {
        FakeActions actions = new FakeActions(tempDir.resolve("active.mv.db"));

        FxTestSupport.onFx(() ->
        {
            DatabaseTransferPanel panel = new DatabaseTransferPanel(actions);

            assertEquals(actions.activeDatabasePath().toString(), panel.activeDatabaseForTests().getText());
            assertFalse(panel.backupButtonForTests().isDisabled());
            assertFalse(panel.restoreButtonForTests().isDisabled());
            assertTrue(panel.switchButtonForTests().isDisabled());

            panel.backupButtonForTests().fire();
            panel.restoreButtonForTests().fire();
            assertEquals(1, actions.backupRequests);
            assertEquals(1, actions.restoreRequests);

            DatabaseTransferService.RestoreResult result = new DatabaseTransferService.RestoreResult(
                    tempDir.resolve("backup.zip"),
                    tempDir.resolve("restored"),
                    Instant.parse("2026-07-24T03:00:00Z"),
                    Instant.parse("2026-07-24T03:01:00Z"),
                    true,
                    new DatabaseTransferService.DatabaseCounts(2, 3, 6),
                    "a".repeat(64));
            actions.lastRestore.set(result);
            actions.switchAvailable.set(true);

            assertEquals(
                    tempDir.resolve("restored.mv.db").toAbsolutePath().normalize().toString(),
                    panel.restoredDatabaseForTests().getText());
            assertFalse(panel.switchButtonForTests().isDisabled());
            panel.switchButtonForTests().fire();
            assertEquals(1, actions.switchRequests);
            return null;
        });
    }

    @Test
    void busyStateDisablesEveryTransferAction()
    {
        FakeActions actions = new FakeActions(tempDir.resolve("active.mv.db"));
        actions.switchAvailable.set(true);

        FxTestSupport.onFx(() ->
        {
            DatabaseTransferPanel panel = new DatabaseTransferPanel(actions);
            actions.busy.set(true);

            assertTrue(panel.backupButtonForTests().isDisabled());
            assertTrue(panel.restoreButtonForTests().isDisabled());
            assertTrue(panel.switchButtonForTests().isDisabled());
            return null;
        });
    }

    private static final class FakeActions implements DatabaseTransferActions
    {
        private final Path activeDatabase;
        private final SimpleBooleanProperty busy = new SimpleBooleanProperty(false);
        private final SimpleBooleanProperty switchAvailable = new SimpleBooleanProperty(false);
        private final SimpleStringProperty status = new SimpleStringProperty("Ready");
        private final SimpleObjectProperty<DatabaseTransferService.BackupResult> lastBackup =
                new SimpleObjectProperty<>();
        private final SimpleObjectProperty<DatabaseTransferService.RestoreResult> lastRestore =
                new SimpleObjectProperty<>();
        private int backupRequests;
        private int restoreRequests;
        private int switchRequests;

        private FakeActions(Path activeDatabase)
        {
            this.activeDatabase = activeDatabase.toAbsolutePath().normalize();
        }

        @Override
        public Path activeDatabasePath()
        {
            return activeDatabase;
        }

        @Override
        public ReadOnlyBooleanProperty busyProperty()
        {
            return busy;
        }

        @Override
        public ReadOnlyBooleanProperty switchAvailableProperty()
        {
            return switchAvailable;
        }

        @Override
        public ReadOnlyStringProperty statusProperty()
        {
            return status;
        }

        @Override
        public ReadOnlyObjectProperty<DatabaseTransferService.BackupResult> lastBackupProperty()
        {
            return lastBackup;
        }

        @Override
        public ReadOnlyObjectProperty<DatabaseTransferService.RestoreResult> lastRestoreProperty()
        {
            return lastRestore;
        }

        @Override
        public void requestBackup()
        {
            backupRequests++;
        }

        @Override
        public void requestRestore()
        {
            restoreRequests++;
        }

        @Override
        public void requestSwitchToValidatedCopy()
        {
            switchRequests++;
        }
    }
}
