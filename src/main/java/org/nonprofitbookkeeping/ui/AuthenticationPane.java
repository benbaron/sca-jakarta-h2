package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.AuthenticationService;
import org.nonprofitbookkeeping.service.LoginAccountView;
import org.nonprofitbookkeeping.service.SecurityBootstrapStatus;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

/** Explicit account-selection/login surface for the active H2 company. */
final class AuthenticationPane
{
    private final VBox root = new VBox(10);
    private final ComboBox<LoginAccountView> account = new ComboBox<>();
    private final PasswordField password = new PasswordField();
    private final Label roles = new Label();
    private final Label status = new Label();
    private final Label bootstrapMessage = new Label();
    private final Button login = new Button("Login");
    private final Button adopt = new Button("Adopt Existing Matching Accounts");

    private final UiSessionState session;
    private final Supplier<AuthenticationService> authenticationSupplier;
    private final Runnable onLogin;

    AuthenticationPane(
            UiSessionState session,
            Supplier<AuthenticationService> authenticationSupplier,
            Runnable onLogin)
    {
        this.session = Objects.requireNonNull(session, "session");
        this.authenticationSupplier = Objects.requireNonNull(authenticationSupplier, "authenticationSupplier");
        this.onLogin = Objects.requireNonNull(onLogin, "onLogin");
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
            AuthenticationService authentication = authenticationSupplier.get();
            SecurityBootstrapStatus bootstrap = authentication.initializeSecurityIfUnambiguous();
            if (bootstrap.hasConflicts())
            {
                account.getItems().clear();
                login.setDisable(true);
                adopt.setVisible(true);
                adopt.setManaged(true);
                bootstrapMessage.setText("Security setup needs an explicit decision: "
                        + String.join(" ", bootstrap.conflicts()));
                status.setText("No account was changed automatically.");
                return;
            }
            adopt.setVisible(false);
            adopt.setManaged(false);
            bootstrapMessage.setText("Security accounts are ready for "
                    + session.multiCompany().activeCompanyCode() + ".");
            Long selectedId = account.getValue() == null ? null : account.getValue().userId();
            account.getItems().setAll(authentication.loginAccounts(session.multiCompany().activeCompanyCode()));
            if (selectedId != null)
            {
                account.getItems().stream()
                        .filter(item -> item.userId() == selectedId)
                        .findFirst()
                        .ifPresent(account::setValue);
            }
            if (account.getValue() == null && !account.getItems().isEmpty())
            {
                account.getSelectionModel().select(0);
            }
            updateAccountState();
            status.setText(account.getItems().isEmpty()
                    ? "No account has an effective reserved role for this company."
                    : "Select an account and log in.");
        }
        catch (RuntimeException ex)
        {
            account.getItems().clear();
            login.setDisable(true);
            status.setText("Could not prepare login: " + UiErrors.safeMessage(ex));
        }
    }

    private void build()
    {
        root.setPadding(new Insets(24));
        root.setMaxWidth(680);
        Label title = new Label("Sign in");
        title.getStyleClass().add("panel-title");
        Label help = new Label(
                "Choose an application account for the active company. Built-in accounts are passwordless until ADMIN deliberately sets a password.");
        help.setWrapText(true);
        bootstrapMessage.setWrapText(true);
        roles.setWrapText(true);
        status.setWrapText(true);

        account.setId("authenticationAccountSelector");
        account.setMaxWidth(Double.MAX_VALUE);
        account.setOnAction(event -> updateAccountState());
        password.setId("authenticationPassword");
        password.setPromptText("Password");
        password.setMaxWidth(Double.MAX_VALUE);
        login.setId("authenticationLoginButton");
        login.setOnAction(event -> login());
        adopt.setId("authenticationAdoptExistingButton");
        adopt.setOnAction(event -> adoptExisting());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Account"), account);
        form.addRow(1, new Label("Password"), password);
        form.addRow(2, new Label("Effective roles"), roles);
        GridPane.setHgrow(account, Priority.ALWAYS);
        GridPane.setHgrow(password, Priority.ALWAYS);

        root.getChildren().setAll(
                title,
                help,
                bootstrapMessage,
                form,
                new HBox(8, login, adopt),
                status);
    }

    private void updateAccountState()
    {
        LoginAccountView selected = account.getValue();
        boolean configured = selected != null && selected.passwordConfigured();
        password.clear();
        password.setDisable(!configured);
        password.setPromptText(configured ? "Password required" : "No password configured");
        roles.setText(selected == null ? "" : selected.effectiveRoles().toString());
        login.setDisable(selected == null);
    }

    private void login()
    {
        LoginAccountView selected = account.getValue();
        if (selected == null)
        {
            status.setText("Choose an account first.");
            return;
        }
        char[] attemptedPassword = password.getText().toCharArray();
        try
        {
            session.setAuthenticatedUser(authenticationSupplier.get().authenticate(
                    session.multiCompany().activeCompanyCode(),
                    selected.userId(),
                    attemptedPassword));
            password.clear();
            status.setText("Logged in as " + selected.username() + ".");
            onLogin.run();
        }
        catch (RuntimeException ex)
        {
            password.clear();
            status.setText(UiErrors.safeMessage(ex));
        }
        finally
        {
            Arrays.fill(attemptedPassword, '\0');
        }
    }

    private void adoptExisting()
    {
        SecurityBootstrapStatus current = authenticationSupplier.get().bootstrapStatus();
        if (!current.hasConflicts())
        {
            refresh();
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Adopt existing accounts");
        confirmation.setHeaderText("Use the existing matching account records as the reserved security accounts?");
        confirmation.setContentText(
                String.join("\n", current.conflicts())
                        + "\n\nStable user IDs and assignment history will be preserved. No password will be created.");
        if (root.getScene() != null && root.getScene().getWindow() != null)
        {
            confirmation.initOwner(root.getScene().getWindow());
        }
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
        {
            status.setText("Existing accounts were not changed.");
            return;
        }
        try
        {
            authenticationSupplier.get().adoptExistingReservedAccounts();
            refresh();
            status.setText("Existing matching accounts adopted; all reserved accounts remain passwordless by default.");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not adopt existing accounts: " + UiErrors.safeMessage(ex));
        }
    }
}
