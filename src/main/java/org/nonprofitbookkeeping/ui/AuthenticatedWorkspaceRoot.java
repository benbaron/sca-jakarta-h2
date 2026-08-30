package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Production authentication gate around the existing workspace shell.
 * Database selection/recovery remains available before login; protected
 * workspace content is mounted only while an authenticated session exists.
 */
public final class AuthenticatedWorkspaceRoot extends BorderPane
{
    private final UiSessionState sessionState = MainWindow.sharedSessionState();
    private final ProductionWorkspaceWindow workspaceWindow;
    private final AuthenticationPane authenticationPane;
    private final Label sessionLabel = new Label();
    private final Label loginStatus = new Label();

    public AuthenticatedWorkspaceRoot()
    {
        this(UserAppStateStore.create(), UiServiceRegistry::prepareDatabaseConnection);
    }

    AuthenticatedWorkspaceRoot(
            AppStateStore stateStore,
            DatabaseSessionController.Connector connector)
    {
        workspaceWindow = new ProductionWorkspaceWindow(
                Objects.requireNonNull(stateStore, "stateStore"),
                Objects.requireNonNull(connector, "connector"));
        authenticationPane = new AuthenticationPane(
                sessionState,
                UiServiceRegistry::authentication,
                this::showAuthenticatedWorkspace);
        sessionState.onAuthenticationChanged(ignored -> refreshPresentation());
        sessionState.onDatabaseSelectionChanged(ignored ->
        {
            if (!sessionState.isAuthenticated())
            {
                authenticationPane.refresh();
            }
        });
        refreshPresentation();
    }

    ProductionWorkspaceWindow workspaceWindow()
    {
        return workspaceWindow;
    }

    public AppPanel.RunCommandResult executeCommand(AppCommand command)
    {
        if (!sessionState.isAuthenticated())
        {
            return new AppPanel.RunCommandResult(false, "Log in before using workspace commands.");
        }
        recordUserActivity();
        return workspaceWindow.executeCommand(command);
    }

    public void recordUserActivity()
    {
        if (sessionState.isAuthenticated())
        {
            sessionState.touchAuthenticatedActivity(Instant.now());
        }
    }

    public void enforceInactivityTimeout()
    {
        sessionState.authenticatedUser().ifPresent(current ->
        {
            try
            {
                if (UiServiceRegistry.authentication().hasTimedOut(current, Instant.now()))
                {
                    UiServiceRegistry.authentication().recordTimeout(current);
                    sessionState.clearAuthenticatedUser();
                    loginStatus.setText("Session ended because the configured inactivity timeout elapsed.");
                }
            }
            catch (RuntimeException ex)
            {
                loginStatus.setText("Could not evaluate inactivity timeout: " + UiErrors.safeMessage(ex));
            }
        });
    }

    public void logout()
    {
        logout("Explicit logout.");
    }

    public void shutdown()
    {
        logout("Application exit.");
    }

    private void logout(String reason)
    {
        sessionState.authenticatedUser().ifPresent(current ->
        {
            try
            {
                UiServiceRegistry.authentication().logout(current, reason);
            }
            catch (RuntimeException ex)
            {
                System.err.println("[NPBK] Could not record logout event: " + ex.getMessage());
            }
        });
        sessionState.clearAuthenticatedUser();
    }

    private void refreshPresentation()
    {
        if (sessionState.isAuthenticated())
        {
            showAuthenticatedWorkspace();
        }
        else
        {
            showLogin();
        }
    }

    private void showAuthenticatedWorkspace()
    {
        AuthenticatedUserSession session = sessionState.authenticatedUser().orElse(null);
        if (session == null)
        {
            showLogin();
            return;
        }
        sessionLabel.setText("Signed in: " + session.username() + " — "
                + session.companyCode() + " — " + session.effectiveRoles());
        Button logout = new Button("Logout");
        logout.setId("authenticationLogoutButton");
        logout.setOnAction(event -> logout());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox sessionBar = new HBox(8, sessionLabel, spacer, logout);
        sessionBar.setPadding(new Insets(4, 10, 4, 10));
        sessionBar.getStyleClass().add("status-bar");
        setTop(sessionBar);
        setCenter(workspaceWindow);
    }

    private void showLogin()
    {
        authenticationPane.refresh();
        setTop(buildDatabaseBar());
        BorderPane loginRegion = new BorderPane(authenticationPane.root());
        loginRegion.setBottom(loginStatus);
        BorderPane.setMargin(loginStatus, new Insets(6, 12, 12, 12));
        setCenter(loginRegion);
    }

    private HBox buildDatabaseBar()
    {
        Label database = new Label("Database: " + sessionState.databaseSelection().activeDatabasePath());
        Button select = new Button("Select Database…");
        select.setId("authenticationSelectDatabase");
        select.setOnAction(event -> selectDatabase());
        Button create = new Button("Create Database…");
        create.setId("authenticationCreateDatabase");
        create.setOnAction(event -> createDatabase());
        Button retry = new Button("Retry Current Database");
        retry.setId("authenticationRetryDatabase");
        retry.setOnAction(event -> connectDatabase(workspaceWindow.activeDatabasePathForTests()));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, database, spacer, select, create, retry);
        bar.setPadding(new Insets(8));
        bar.getStyleClass().add("top-chrome");
        return bar;
    }

    private void selectDatabase()
    {
        if (getScene() == null || getScene().getWindow() == null)
        {
            loginStatus.setText("Database selection is unavailable until the application window is ready.");
            return;
        }
        FileChooser chooser = databaseFileChooser("Select Database File");
        File selected = chooser.showOpenDialog(getScene().getWindow());
        if (selected != null)
        {
            connectDatabase(selected.toPath());
        }
    }

    private void createDatabase()
    {
        if (getScene() == null || getScene().getWindow() == null)
        {
            loginStatus.setText("Database creation is unavailable until the application window is ready.");
            return;
        }
        FileChooser chooser = databaseFileChooser("Create New Database");
        chooser.setInitialFileName("sca-ledger.mv.db");
        File selected = chooser.showSaveDialog(getScene().getWindow());
        if (selected == null)
        {
            return;
        }
        Path target = ProductionWorkspaceWindow.normalizeNewDatabasePath(selected.toPath());
        if (Files.exists(target))
        {
            loginStatus.setText("Database was not created because a file already exists at "
                    + target.toAbsolutePath() + ".");
            return;
        }
        connectDatabase(target);
    }

    private void connectDatabase(Path databaseFile)
    {
        try
        {
            workspaceWindow.connectDatabaseForTests(databaseFile);
            authenticationPane.refresh();
            loginStatus.setText("Database ready: "
                    + sessionState.databaseSelection().activeDatabasePath() + ". Choose an account to log in.");
        }
        catch (RuntimeException ex)
        {
            loginStatus.setText("Database connection failed: " + UiErrors.safeMessage(ex));
        }
    }

    private static FileChooser databaseFileChooser(String title)
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("H2 Database Files", "*.mv.db", "*.db"));
        return chooser;
    }
}
