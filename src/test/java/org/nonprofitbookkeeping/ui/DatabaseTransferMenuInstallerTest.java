package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.persistence.DatabaseTransferService;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTransferMenuInstallerTest
{
    @Test
    void fileMenuRoutesSharedActionsAndInstallsOnlyOnce()
    {
        FxTestSupport.onFx(() ->
        {
            FakeActions actions = new FakeActions();
            Menu file = new Menu("File");
            file.getItems().setAll(
                    new MenuItem("Select Database File…"),
                    new MenuItem("Create New Database…"),
                    new MenuItem("Retry / Repair Current Database"),
                    new MenuItem("Create / Refresh Sample Company Data"),
                    new MenuItem("Save"),
                    new MenuItem("Exit"));
            MenuBar menuBar = new MenuBar(file);

            DatabaseTransferMenuInstaller.install(menuBar, actions);
            DatabaseTransferMenuInstaller.install(menuBar, actions);

            MenuItem backup = byId(file, DatabaseTransferMenuInstaller.BACKUP_MENU_ID);
            MenuItem restore = byId(file, DatabaseTransferMenuInstaller.RESTORE_MENU_ID);
            MenuItem switchDatabase = byId(file, DatabaseTransferMenuInstaller.SWITCH_MENU_ID);

            assertNotNull(backup);
            assertNotNull(restore);
            assertNotNull(switchDatabase);
            assertEquals(1, file.getItems().stream()
                    .filter(item -> DatabaseTransferMenuInstaller.BACKUP_MENU_ID.equals(item.getId()))
                    .count());
            assertFalse(backup.isDisable());
            assertFalse(restore.isDisable());
            assertTrue(switchDatabase.isDisable());

            backup.fire();
            restore.fire();
            assertEquals(1, actions.backupRequests);
            assertEquals(1, actions.restoreRequests);

            actions.switchAvailable.set(true);
            assertFalse(switchDatabase.isDisable());
            switchDatabase.fire();
            assertEquals(1, actions.switchRequests);

            actions.busy.set(true);
            assertTrue(backup.isDisable());
            assertTrue(restore.isDisable());
            assertTrue(switchDatabase.isDisable());
            return null;
        });
    }

    private static MenuItem byId(Menu menu, String id)
    {
        return menu.getItems().stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst()
                .orElse(null);
    }

    private static final class FakeActions implements DatabaseTransferActions
    {
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

        @Override
        public Path activeDatabasePath()
        {
            return Path.of("data", "active.mv.db").toAbsolutePath().normalize();
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
