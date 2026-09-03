package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.nonprofitbookkeeping.persistence.DatabaseTransferService;
import org.nonprofitbookkeeping.service.DatabaseAdministrationService;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Coordinates File-menu and Administration database-transfer interactions. */
final class DatabaseTransferCoordinator implements DatabaseTransferActions
{
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DatabaseAdministrationService transferService;
    private final Supplier<Path> activeDatabasePath;
    private final Supplier<Window> ownerWindow;
    private final Runnable afterSuccessfulSwitch;
    private final Executor executor;

    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper switchAvailable = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper status = new ReadOnlyStringWrapper(
            "Choose Backup Database or Restore Database Copy. Restores never overwrite the active database.");
    private final ReadOnlyObjectWrapper<DatabaseTransferService.BackupResult> lastBackup =
            new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<DatabaseTransferService.RestoreResult> lastRestore =
            new ReadOnlyObjectWrapper<>();

    DatabaseTransferCoordinator(
            DatabaseAdministrationService transferService,
            Supplier<Path> activeDatabasePath,
            Supplier<Window> ownerWindow,
            Runnable afterSuccessfulSwitch)
    {
        this(
                transferService,
                activeDatabasePath,
                ownerWindow,
                afterSuccessfulSwitch,
                DatabaseTransferCoordinator::startDaemonThread);
    }

    DatabaseTransferCoordinator(
            DatabaseAdministrationService transferService,
            Supplier<Path> activeDatabasePath,
            Supplier<Window> ownerWindow,
            Runnable afterSuccessfulSwitch,
            Executor executor)
    {
        this.transferService = Objects.requireNonNull(transferService, "transferService");
        this.activeDatabasePath = Objects.requireNonNull(activeDatabasePath, "activeDatabasePath");
        this.ownerWindow = Objects.requireNonNull(ownerWindow, "ownerWindow");
        this.afterSuccessfulSwitch = Objects.requireNonNull(afterSuccessfulSwitch, "afterSuccessfulSwitch");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public Path activeDatabasePath()
    {
        return activeDatabasePath.get().toAbsolutePath().normalize();
    }

    @Override
    public ReadOnlyBooleanProperty busyProperty()
    {
        return busy.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyBooleanProperty switchAvailableProperty()
    {
        return switchAvailable.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyStringProperty statusProperty()
    {
        return status.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyObjectProperty<DatabaseTransferService.BackupResult> lastBackupProperty()
    {
        return lastBackup.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyObjectProperty<DatabaseTransferService.RestoreResult> lastRestoreProperty()
    {
        return lastRestore.getReadOnlyProperty();
    }

    @Override
    public void requestBackup()
    {
        if (busy.get())
        {
            status.set("Another database transfer operation is already running.");
            return;
        }

        Window owner = ownerWindow.get();
        if (owner == null)
        {
            status.set("The application window is not ready for database backup.");
            return;
        }

        Optional<Path> selected = chooseBackupDestination(owner);
        if (selected.isEmpty())
        {
            status.set("Database backup cancelled.");
            return;
        }

        Path destination = normalizeBackupPath(selected.get());
        Path source = activeDatabasePath();
        if (!confirm(
                owner,
                "Back Up Database",
                "Create a consistent backup of the active database?",
                "Source database:\n" + source + "\n\nBackup file:\n" + destination
                        + "\n\nThe active database remains selected and is not copied directly."))
        {
            status.set("Database backup cancelled.");
            return;
        }

        runAsync(
                "Creating database backup at " + destination + "...",
                () -> transferService.backUpDatabase(destination),
                result ->
                {
                    lastBackup.set(result);
                    status.set("Database backup completed: " + result.backupFile());
                    showInformation(
                            owner,
                            "Database Backup Complete",
                            "The active database was backed up successfully.",
                            backupSummary(result));
                });
    }

    @Override
    public void requestRestore()
    {
        if (busy.get())
        {
            status.set("Another database transfer operation is already running.");
            return;
        }

        Window owner = ownerWindow.get();
        if (owner == null)
        {
            status.set("The application window is not ready for database restore.");
            return;
        }

        Optional<Path> backup = chooseBackupFile(owner);
        if (backup.isEmpty())
        {
            status.set("Database restore cancelled before a backup file was selected.");
            return;
        }

        Optional<Path> targetSelection = chooseRestoreTarget(owner);
        if (targetSelection.isEmpty())
        {
            status.set("Database restore cancelled before a target database was selected.");
            return;
        }

        Path targetFile = normalizeDatabaseFile(targetSelection.get());
        Path targetBase = databaseBase(targetFile);
        Path active = activeDatabasePath();
        if (!confirm(
                owner,
                "Restore Database Copy",
                "Create and validate a new database copy?",
                "Active database (will not be overwritten):\n" + active
                        + "\n\nBackup file:\n" + backup.get().toAbsolutePath().normalize()
                        + "\n\nNew target database:\n" + targetFile.toAbsolutePath().normalize()
                        + "\n\nThe restored copy will be migrated and validated before it is offered for switching."))
        {
            status.set("Database restore cancelled.");
            return;
        }

        runAsync(
                "Restoring and validating database copy at " + targetFile + "...",
                () -> transferService.restoreDatabaseCopy(backup.get(), targetBase),
                result ->
                {
                    lastRestore.set(result);
                    switchAvailable.set(result.validated());
                    status.set("Restored database copy validated: " + restoredDatabaseFile(result));
                    offerValidatedSwitch(owner, result);
                });
    }

    @Override
    public void requestSwitchToValidatedCopy()
    {
        DatabaseTransferService.RestoreResult result = lastRestore.get();
        if (result == null || !result.validated() || !switchAvailable.get())
        {
            status.set("Restore and validate a database copy before switching.");
            return;
        }

        Window owner = ownerWindow.get();
        if (owner == null)
        {
            status.set("The application window is not ready to switch databases.");
            return;
        }

        if (confirmSwitch(owner, result))
        {
            switchTo(result, owner);
        }
        else
        {
            status.set("Database switch postponed. The validated copy remains available at "
                    + restoredDatabaseFile(result) + ".");
        }
    }

    private Optional<Path> chooseBackupDestination(Window owner)
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Back Up Database");
        chooser.setInitialFileName("sca-ledger-backup-"
                + BACKUP_TIMESTAMP.format(LocalDateTime.now()) + ".zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("H2 Backup Archives", "*.zip"));
        File selected = chooser.showSaveDialog(owner);
        return selected == null ? Optional.empty() : Optional.of(selected.toPath());
    }

    private Optional<Path> chooseBackupFile(Window owner)
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Database Backup");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("H2 Backup Archives", "*.zip"));
        File selected = chooser.showOpenDialog(owner);
        return selected == null ? Optional.empty() : Optional.of(selected.toPath().toAbsolutePath().normalize());
    }

    private Optional<Path> chooseRestoreTarget(Window owner)
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Create Restored Database Copy");
        chooser.setInitialFileName("sca-ledger-restored.mv.db");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("H2 Database Files", "*.mv.db", "*.db"));
        File selected = chooser.showSaveDialog(owner);
        return selected == null ? Optional.empty() : Optional.of(selected.toPath());
    }

    private <T> void runAsync(String runningStatus, Callable<T> operation, Consumer<T> success)
    {
        busy.set(true);
        status.set(runningStatus);

        Task<T> task = new Task<>()
        {
            @Override
            protected T call() throws Exception
            {
                return operation.call();
            }
        };
        task.setOnSucceeded(event ->
        {
            busy.set(false);
            success.accept(task.getValue());
        });
        task.setOnFailed(event ->
        {
            busy.set(false);
            Throwable failure = task.getException();
            status.set("Database transfer failed: " + UiErrors.safeMessage(failure));
            showError(
                    ownerWindow.get(),
                    "Database Transfer Failed",
                    "The database transfer operation did not complete.",
                    UiErrors.safeMessage(failure));
        });
        executor.execute(task);
    }

    private void offerValidatedSwitch(Window owner, DatabaseTransferService.RestoreResult result)
    {
        ButtonType switchNow = new ButtonType("Switch Now", ButtonBar.ButtonData.OK_DONE);
        ButtonType later = new ButtonType("Later", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Restored Database Validated");
        alert.setHeaderText("The restored copy passed migration and validation.");
        alert.setContentText(restoreSummary(result)
                + "\n\nSwitching is optional. The active database will remain unchanged until you choose Switch Now.");
        alert.getButtonTypes().setAll(switchNow, later);
        initOwner(alert, owner);
        if (alert.showAndWait().orElse(later) == switchNow)
        {
            switchTo(result, owner);
        }
        else
        {
            status.set("Restored database copy validated. Switch is available when you are ready: "
                    + restoredDatabaseFile(result));
        }
    }

    private boolean confirmSwitch(Window owner, DatabaseTransferService.RestoreResult result)
    {
        return confirm(
                owner,
                "Switch Database",
                "Switch the production workspace to the validated restored copy?",
                "Current active database:\n" + activeDatabasePath()
                        + "\n\nValidated restored database:\n" + restoredDatabaseFile(result)
                        + "\n\nThe restored copy has already passed migration and Hibernate validation.");
    }

    private void switchTo(DatabaseTransferService.RestoreResult result, Window owner)
    {
        if (busy.get())
        {
            status.set("Another database transfer operation is already running.");
            return;
        }

        busy.set(true);
        status.set("Switching to validated database copy " + restoredDatabaseFile(result) + "...");
        try
        {
            transferService.switchToValidatedCopy(result);
            afterSuccessfulSwitch.run();
            switchAvailable.set(false);
            status.set("Active database switched to validated copy: " + activeDatabasePath());
            showInformation(
                    owner,
                    "Database Switched",
                    "The production workspace is now using the validated restored database.",
                    "Active database:\n" + activeDatabasePath());
        }
        catch (RuntimeException ex)
        {
            status.set("Database switch failed: " + UiErrors.safeMessage(ex));
            showError(
                    owner,
                    "Database Switch Failed",
                    "The validated copy could not be made active.",
                    UiErrors.safeMessage(ex));
        }
        finally
        {
            busy.set(false);
        }
    }

    static Path normalizeBackupPath(Path selected)
    {
        Path absolute = selected.toAbsolutePath().normalize();
        String value = absolute.toString();
        return value.toLowerCase(Locale.ROOT).endsWith(".zip")
                ? absolute
                : Path.of(value + ".zip");
    }

    static Path normalizeDatabaseFile(Path selected)
    {
        Path absolute = selected.toAbsolutePath().normalize();
        String value = absolute.toString();
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mv.db") || lower.endsWith(".db")
                ? absolute
                : Path.of(value + ".mv.db");
    }

    static Path restoredDatabaseFile(DatabaseTransferService.RestoreResult result)
    {
        return normalizeDatabaseFile(result.targetDatabase());
    }

    private static Path databaseBase(Path databaseFile)
    {
        String value = databaseFile.toAbsolutePath().normalize().toString();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mv.db"))
        {
            return Path.of(value.substring(0, value.length() - ".mv.db".length()));
        }
        if (lower.endsWith(".db"))
        {
            return Path.of(value.substring(0, value.length() - ".db".length()));
        }
        return Path.of(value);
    }

    private static String backupSummary(DatabaseTransferService.BackupResult result)
    {
        return "Source database:\n" + result.sourceDatabase()
                + "\n\nBackup file:\n" + result.backupFile()
                + "\n\nSize: " + result.byteCount() + " bytes"
                + "\nSHA-256: " + result.sha256()
                + "\nCompanies: " + result.counts().companies()
                + "\nTransactions: " + result.counts().transactions()
                + "\nTransaction splits: " + result.counts().transactionSplits();
    }

    private static String restoreSummary(DatabaseTransferService.RestoreResult result)
    {
        return "Backup file:\n" + result.backupFile()
                + "\n\nValidated target database:\n" + restoredDatabaseFile(result)
                + "\n\nBackup SHA-256: " + result.backupSha256()
                + "\nCompanies: " + result.counts().companies()
                + "\nTransactions: " + result.counts().transactions()
                + "\nTransaction splits: " + result.counts().transactionSplits();
    }

    private static boolean confirm(Window owner, String title, String header, String content)
    {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        initOwner(alert, owner);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static void showInformation(Window owner, String title, String header, String content)
    {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        initOwner(alert, owner);
        alert.showAndWait();
    }

    private static void showError(Window owner, String title, String header, String content)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        initOwner(alert, owner);
        alert.showAndWait();
    }

    private static void initOwner(Alert alert, Window owner)
    {
        if (owner != null)
        {
            alert.initOwner(owner);
        }
    }

    private static void startDaemonThread(Runnable command)
    {
        Thread thread = new Thread(command, "npbk-database-transfer");
        thread.setDaemon(true);
        thread.start();
    }
}
