package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.service.ApplicationPermission;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.persistence.DatabaseLocationService;
import org.nonprofitbookkeeping.persistence.DatabaseTransferService;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Administration surface for whole-database backup, restore-copy validation, and guarded switching. */
public final class DatabaseTransferPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final DatabaseTransferActions actions;

    private final TextField activeDatabase = readOnlyField("databaseTransferActivePath");
    private final TextField lastBackup = readOnlyField("databaseTransferLastBackupPath");
    private final TextField backupHash = readOnlyField("databaseTransferBackupHash");
    private final TextField restoredDatabase = readOnlyField("databaseTransferRestoreTargetPath");
    private final TextField restoreHash = readOnlyField("databaseTransferRestoreHash");
    private final Label counts = new Label("No validated restored database is available.");
    private final Label status = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Button backupButton = new Button("Backup Database…");
    private final Button restoreButton = new Button("Restore Database Copy…");
    private final Button switchButton = new Button("Switch to Validated Copy");

    public DatabaseTransferPanel()
    {
        this(DatabaseTransferActions.unavailable(() -> DatabaseLocationService.resolveDatabasePath(
                MainWindow.sharedSessionState().databaseSelection().activeDatabasePath())));
    }

    DatabaseTransferPanel(DatabaseTransferActions actions)
    {
        this.actions = Objects.requireNonNull(actions, "actions");

        root.setPadding(new Insets(8));
        Label title = new Label("Database Transfer");
        title.getStyleClass().add("panel-title");
        Label guidance = new Label(
                "Backups use H2's supported live backup operation. Restore always creates and validates a new database copy; it never overwrites the active database or switches automatically.");
        guidance.setWrapText(true);
        root.setTop(new VBox(6, title, guidance, new Separator()));

        status.setWrapText(true);
        status.textProperty().bind(actions.statusProperty());
        counts.setWrapText(true);

        progress.setMaxSize(24, 24);
        progress.visibleProperty().bind(actions.busyProperty());
        progress.managedProperty().bind(actions.busyProperty());

        backupButton.setOnAction(event -> actions.requestBackup());
        restoreButton.setOnAction(event -> actions.requestRestore());
        switchButton.setOnAction(event -> actions.requestSwitchToValidatedCopy());
        backupButton.disableProperty().bind(
                actions.busyProperty().or(UiPermissionGate.deniedProperty(ApplicationPermission.DATABASE_ADMIN)));
        restoreButton.disableProperty().bind(
                actions.busyProperty().or(UiPermissionGate.deniedProperty(ApplicationPermission.DATABASE_ADMIN)));
        switchButton.disableProperty().bind(
                actions.busyProperty()
                        .or(actions.switchAvailableProperty().not())
                        .or(UiPermissionGate.deniedProperty(ApplicationPermission.DATABASE_ADMIN)));

        ScrollPane scroll = new ScrollPane(buildContent());
        scroll.setId("databaseTransferScroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        root.setCenter(scroll);

        actions.lastBackupProperty().addListener((observable, oldResult, newResult) -> refreshBackup(newResult));
        actions.lastRestoreProperty().addListener((observable, oldResult, newResult) -> refreshRestore(newResult));
        actions.statusProperty().addListener((observable, oldStatus, newStatus) -> refreshActiveDatabase());
        refreshAll();
    }

    private Node buildContent()
    {
        GridPane paths = new GridPane();
        paths.setHgap(10);
        paths.setVgap(10);
        paths.setPadding(new Insets(4));

        List<TextField> expanding = List.of(activeDatabase, lastBackup, backupHash, restoredDatabase, restoreHash);
        expanding.forEach(field -> GridPane.setHgrow(field, Priority.ALWAYS));

        int row = 0;
        paths.add(new Label("Active database"), 0, row);
        paths.add(activeDatabase, 1, row++);
        paths.add(new Label("Last backup file"), 0, row);
        paths.add(lastBackup, 1, row++);
        paths.add(new Label("Backup SHA-256"), 0, row);
        paths.add(backupHash, 1, row++);
        paths.add(new Label("Validated restore target"), 0, row);
        paths.add(restoredDatabase, 1, row++);
        paths.add(new Label("Restore backup SHA-256"), 0, row);
        paths.add(restoreHash, 1, row++);
        paths.add(new Label("Validated record counts"), 0, row);
        paths.add(counts, 1, row);

        HBox actionsRow = new HBox(8, backupButton, restoreButton, switchButton, progress);
        return new VBox(12, paths, new Separator(), actionsRow, status);
    }

    private void refreshAll()
    {
        refreshActiveDatabase();
        refreshBackup(actions.lastBackupProperty().get());
        refreshRestore(actions.lastRestoreProperty().get());
    }

    private void refreshActiveDatabase()
    {
        try
        {
            activeDatabase.setText(actions.activeDatabasePath().toString());
        }
        catch (RuntimeException ex)
        {
            activeDatabase.setText("Unavailable: " + UiErrors.safeMessage(ex));
        }
    }

    private void refreshBackup(DatabaseTransferService.BackupResult result)
    {
        if (result == null)
        {
            lastBackup.clear();
            backupHash.clear();
            return;
        }
        lastBackup.setText(result.backupFile().toAbsolutePath().normalize().toString());
        backupHash.setText(result.sha256());
    }

    private void refreshRestore(DatabaseTransferService.RestoreResult result)
    {
        if (result == null)
        {
            restoredDatabase.clear();
            restoreHash.clear();
            counts.setText("No validated restored database is available.");
            return;
        }
        Path restoredFile = DatabaseTransferCoordinator.restoredDatabaseFile(result);
        restoredDatabase.setText(restoredFile.toAbsolutePath().normalize().toString());
        restoreHash.setText(result.backupSha256());
        counts.setText("Companies: " + result.counts().companies()
                + "; Transactions: " + result.counts().transactions()
                + "; Transaction splits: " + result.counts().transactionSplits());
    }

    private static TextField readOnlyField(String id)
    {
        TextField field = new TextField();
        field.setId(id);
        field.setEditable(false);
        field.setFocusTraversable(true);
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    @Override
    public String title()
    {
        return "Database Transfer";
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override
    public void onPanelShown()
    {
        refreshAll();
    }

    Button backupButtonForTests()
    {
        return backupButton;
    }

    Button restoreButtonForTests()
    {
        return restoreButton;
    }

    Button switchButtonForTests()
    {
        return switchButton;
    }

    TextField activeDatabaseForTests()
    {
        return activeDatabase;
    }

    TextField restoredDatabaseForTests()
    {
        return restoredDatabase;
    }
}
