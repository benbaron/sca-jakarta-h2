package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import org.nonprofitbookkeeping.persistence.DatabaseTransferService;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/** Shared shell and Administration actions for whole-database transfer. */
interface DatabaseTransferActions
{
    Path activeDatabasePath();

    ReadOnlyBooleanProperty busyProperty();

    ReadOnlyStringProperty statusProperty();

    ReadOnlyObjectProperty<DatabaseTransferService.BackupResult> lastBackupProperty();

    ReadOnlyObjectProperty<DatabaseTransferService.RestoreResult> lastRestoreProperty();

    void requestBackup();

    void requestRestore();

    void requestSwitchToValidatedCopy();

    static DatabaseTransferActions unavailable(Supplier<Path> activeDatabasePath)
    {
        Objects.requireNonNull(activeDatabasePath, "activeDatabasePath");
        return new DatabaseTransferActions()
        {
            private final SimpleBooleanProperty busy = new SimpleBooleanProperty(false);
            private final SimpleStringProperty status = new SimpleStringProperty(
                    "Database transfer is available from the production workspace.");
            private final SimpleObjectProperty<DatabaseTransferService.BackupResult> lastBackup =
                    new SimpleObjectProperty<>();
            private final SimpleObjectProperty<DatabaseTransferService.RestoreResult> lastRestore =
                    new SimpleObjectProperty<>();

            @Override
            public Path activeDatabasePath()
            {
                return activeDatabasePath.get().toAbsolutePath().normalize();
            }

            @Override
            public ReadOnlyBooleanProperty busyProperty()
            {
                return busy;
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
                status.set("Database backup requires the production workspace window.");
            }

            @Override
            public void requestRestore()
            {
                status.set("Database restore requires the production workspace window.");
            }

            @Override
            public void requestSwitchToValidatedCopy()
            {
                status.set("No validated restored database is available to switch to.");
            }
        };
    }
}
