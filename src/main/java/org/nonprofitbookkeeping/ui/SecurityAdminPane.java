package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;
import org.nonprofitbookkeeping.service.SecurityAdminService;
import org.nonprofitbookkeeping.service.SecuritySettingsView;

import java.util.Arrays;

/** Real credential/session configuration content for User Admin → Authentication. */
final class SecurityAdminPane
{
    private final VBox root = new VBox(10);
    private final ComboBox<AppUser> account = new ComboBox<>();
    private final PasswordField password = new PasswordField();
    private final Label credentialState = new Label();
    private final Label currentSession = new Label();
    private final Label status = new Label();
    private final Spinner<Integer> inactivityMinutes = new Spinner<>();

    SecurityAdminPane()
    {
        build();
        refresh();
    }

    Node root()
    {
        return root;
    }

    void refresh()
    {
        try
        {
            Long selectedId = account.getValue() == null ? null : account.getValue().getId();
            account.getItems().setAll(UiServiceRegistry.userAdmin().listUsers());
            if (selectedId != null)
            {
                account.getItems().stream()
                        .filter(user -> selectedId.equals(user.getId()))
                        .findFirst()
                        .ifPresent(account::setValue);
            }
            if (account.getValue() == null && !account.getItems().isEmpty())
            {
                account.getSelectionModel().select(0);
            }
            SecuritySettingsView settings = UiServiceRegistry.securityAdmin().settings();
            inactivityMinutes.getValueFactory().setValue(settings.inactivityTimeoutMinutes());
            updateCredentialState();
            updateSessionState();
            status.setText("Security settings loaded. Zero inactivity minutes means no timeout.");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not load authentication settings: " + UiErrors.safeMessage(ex));
        }
    }

    private void build()
    {
        root.setPadding(new Insets(12));
        Label help = new Label(
                "Credentials belong to application accounts, never roles. Only the singleton ADMIN account may set, replace, or clear passwords. Passwordless login is the default.");
        help.setWrapText(true);
        credentialState.setWrapText(true);
        currentSession.setWrapText(true);
        status.setWrapText(true);

        account.setId("securityCredentialAccount");
        account.setConverter(new StringConverter<>()
        {
            @Override
            public String toString(AppUser value)
            {
                return value == null ? "" : value.getUsername() + " — " + value.getDisplayName();
            }

            @Override
            public AppUser fromString(String value)
            {
                return null;
            }
        });
        account.setOnAction(event -> updateCredentialState());
        account.setMaxWidth(Double.MAX_VALUE);

        password.setId("securityCredentialPassword");
        password.setPromptText("New password");
        Button setPassword = new Button("Set / Replace Password");
        setPassword.setId("securitySetPassword");
        setPassword.setOnAction(event -> setPassword());
        Button clearPassword = new Button("Clear Password");
        clearPassword.setId("securityClearPassword");
        clearPassword.setOnAction(event -> clearPassword());

        inactivityMinutes.setId("securityInactivityTimeoutMinutes");
        inactivityMinutes.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 10080, 0));
        inactivityMinutes.setEditable(true);
        Button saveTimeout = new Button("Save Timeout");
        saveTimeout.setOnAction(event -> saveTimeout());
        Button refreshRoles = new Button("Refresh Session Roles");
        refreshRoles.setId("securityRefreshSessionRoles");
        refreshRoles.setOnAction(event -> refreshSessionRoles());
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refresh());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Account"), account);
        form.addRow(1, new Label("Credential state"), credentialState);
        form.addRow(2, new Label("New password"), password);
        form.add(new HBox(8, setPassword, clearPassword), 1, 3);
        form.addRow(4, new Label("Inactivity timeout (minutes)"), inactivityMinutes);
        form.add(saveTimeout, 1, 5);
        GridPane.setHgrow(account, Priority.ALWAYS);
        GridPane.setHgrow(password, Priority.ALWAYS);

        root.getChildren().setAll(
                help,
                currentSession,
                form,
                new HBox(8, refreshRoles, refresh),
                status);
    }

    private void updateCredentialState()
    {
        AppUser selected = account.getValue();
        if (selected == null)
        {
            credentialState.setText("No account selected.");
            return;
        }
        try
        {
            credentialState.setText(UiServiceRegistry.securityAdmin().passwordConfigured(selected.getId())
                    ? "Password configured"
                    : "Passwordless login");
        }
        catch (RuntimeException ex)
        {
            credentialState.setText("Credential state unavailable: " + UiErrors.safeMessage(ex));
        }
    }

    private void updateSessionState()
    {
        currentSession.setText(MainWindow.sharedSessionState().authenticatedUser()
                .map(session -> "Current session: " + session.username() + " in " + session.companyCode()
                        + " with roles " + session.effectiveRoles())
                .orElse("No authenticated session."));
    }

    private AuthenticatedUserSession requireSession()
    {
        return MainWindow.sharedSessionState().authenticatedUser()
                .orElseThrow(() -> new IllegalStateException("Log in before changing authentication settings."));
    }

    private void setPassword()
    {
        AppUser selected = account.getValue();
        if (selected == null)
        {
            status.setText("Choose an account first.");
            return;
        }
        char[] value = password.getText().toCharArray();
        try
        {
            AuthenticatedUserSession session = requireSession();
            UiServiceRegistry.securityAdmin().setPassword(
                    session.userId(), session.companyCode(), selected.getId(), value);
            password.clear();
            updateCredentialState();
            status.setText("Password updated for " + selected.getUsername() + ".");
        }
        catch (RuntimeException ex)
        {
            password.clear();
            status.setText("Could not set password: " + UiErrors.safeMessage(ex));
        }
        finally
        {
            Arrays.fill(value, '\0');
        }
    }

    private void clearPassword()
    {
        AppUser selected = account.getValue();
        if (selected == null)
        {
            status.setText("Choose an account first.");
            return;
        }
        try
        {
            AuthenticatedUserSession session = requireSession();
            UiServiceRegistry.securityAdmin().clearPassword(
                    session.userId(), session.companyCode(), selected.getId());
            password.clear();
            updateCredentialState();
            status.setText("Password cleared for " + selected.getUsername() + "; login is passwordless.");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not clear password: " + UiErrors.safeMessage(ex));
        }
    }

    private void saveTimeout()
    {
        try
        {
            AuthenticatedUserSession session = requireSession();
            int minutes = inactivityMinutes.getValue() == null ? 0 : inactivityMinutes.getValue();
            SecuritySettingsView saved = UiServiceRegistry.securityAdmin().setInactivityTimeoutMinutes(
                    session.userId(), session.companyCode(), minutes);
            inactivityMinutes.getValueFactory().setValue(saved.inactivityTimeoutMinutes());
            status.setText(saved.inactivityTimeoutMinutes() == 0
                    ? "Inactivity timeout disabled."
                    : "Inactivity timeout set to " + saved.inactivityTimeoutMinutes() + " minute(s).");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save inactivity timeout: " + UiErrors.safeMessage(ex));
        }
    }

    private void refreshSessionRoles()
    {
        try
        {
            AuthenticatedUserSession current = requireSession();
            MainWindow.sharedSessionState().setAuthenticatedUser(
                    UiServiceRegistry.authentication().refresh(current));
            updateSessionState();
            status.setText("Effective role state refreshed from H2 assignments.");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not refresh effective roles: " + UiErrors.safeMessage(ex));
        }
    }
}
