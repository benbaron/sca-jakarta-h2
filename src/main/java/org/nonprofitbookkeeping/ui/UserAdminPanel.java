package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.AppRole;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.UserCompanyRole;

import java.util.List;

/** User, role, and company-assignment administration foundation panel. */
public class UserAdminPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<AppUser> users = new TableView<>();
    private final TableView<AppRole> roles = new TableView<>();
    private final TableView<UserCompanyRole> assignments = new TableView<>();
    private final Label status = new Label("Ready.");

    private final TextField username = new TextField();
    private final TextField displayName = new TextField();
    private final TextField email = new TextField();
    private final CheckBox active = new CheckBox("Active");
    private final ComboBox<String> assignUser = new ComboBox<>();
    private final ComboBox<String> assignCompany = new ComboBox<>();
    private final ComboBox<String> assignRole = new ComboBox<>();

    public UserAdminPanel()
    {
        build();
        refresh();
    }

    private void build()
    {
        Label title = new Label("User Admin");
        title.getStyleClass().add("panel-title");
        Label help = new Label("Manage application users, roles, and company-specific role assignments. Authentication enforcement is a later slice.");
        help.setWrapText(true);
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> refresh());
        Button saveUser = new Button("Save User");
        saveUser.setOnAction(e -> saveUser());
        Button assign = new Button("Assign Role");
        assign.setOnAction(e -> assignRole());
        HBox actions = new HBox(8, refresh, saveUser, assign, status);
        VBox header = new VBox(6, title, help, actions);
        header.setPadding(new Insets(8));
        root.setTop(header);

        configureUsersTable();
        configureRolesTable();
        configureAssignmentsTable();

        GridPane userForm = new GridPane();
        userForm.setHgap(8);
        userForm.setVgap(6);
        userForm.setPadding(new Insets(8));
        int r = 0;
        userForm.addRow(r++, new Label("Username"), username);
        userForm.addRow(r++, new Label("Display Name"), displayName);
        userForm.addRow(r++, new Label("Email"), email);
        userForm.add(active, 1, r++);
        active.setSelected(true);

        GridPane assignForm = new GridPane();
        assignForm.setHgap(8);
        assignForm.setVgap(6);
        assignForm.setPadding(new Insets(8));
        int ar = 0;
        assignForm.addRow(ar++, new Label("User"), assignUser);
        assignForm.addRow(ar++, new Label("Company"), assignCompany);
        assignForm.addRow(ar++, new Label("Role"), assignRole);

        users.getSelectionModel().selectedItemProperty().addListener((obs, old, next) -> {
            if (next != null) populate(next);
        });

        TabPane tabs = new TabPane();
        tabs.getTabs().add(tab("Users", new VBox(8, userForm, users)));
        tabs.getTabs().add(tab("Roles", roles));
        tabs.getTabs().add(tab("Company Assignments", new VBox(8, assignForm, assignments)));
        tabs.getTabs().add(tab("Authentication", new Label("Password hashing, login policy, and local/external authentication are intentionally deferred to a later security slice.")));
        root.setCenter(tabs);
    }

    private Tab tab(String label, Node content)
    {
        Tab tab = new Tab(label, content);
        tab.setClosable(false);
        return tab;
    }

    private void configureUsersTable()
    {
        users.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        users.getColumns().setAll(
                col("Username", AppUser::getUsername),
                col("Display Name", AppUser::getDisplayName),
                col("Email", AppUser::getEmail),
                col("Active", u -> String.valueOf(u.isActive()))
        );
        users.setMinHeight(280);
    }

    private void configureRolesTable()
    {
        roles.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        roles.getColumns().setAll(
                col("Code", AppRole::getCode),
                col("Name", AppRole::getName),
                col("Description", AppRole::getDescription)
        );
        roles.setMinHeight(280);
    }

    private void configureAssignmentsTable()
    {
        assignments.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        assignments.getColumns().setAll(
                col("User", a -> a.getUser().getUsername()),
                col("Company", a -> a.getCompany().getCode()),
                col("Role", a -> a.getRole().getCode()),
                col("Active", a -> String.valueOf(a.isActive()))
        );
        assignments.setMinHeight(280);
    }

    private <T> TableColumn<T, String> col(String title, java.util.function.Function<T, String> extractor)
    {
        TableColumn<T, String> col = new TableColumn<>(title);
        col.setCellValueFactory(data -> new SimpleStringProperty(nullToBlank(extractor.apply(data.getValue()))));
        col.setMinWidth(120);
        col.setPrefWidth(Math.max(140, title.length() * 12));
        return col;
    }

    private void populate(AppUser user)
    {
        username.setText(user.getUsername());
        displayName.setText(user.getDisplayName());
        email.setText(nullToBlank(user.getEmail()));
        active.setSelected(user.isActive());
    }

    private void refresh()
    {
        try
        {
            List<AppUser> userRows = UiServiceRegistry.userAdmin().listUsers();
            List<AppRole> roleRows = UiServiceRegistry.userAdmin().listRoles();
            List<Company> companyRows = UiServiceRegistry.companyAdmin().listCompanies();
            List<UserCompanyRole> assignmentRows = UiServiceRegistry.userAdmin().listAssignments();
            users.setItems(FXCollections.observableArrayList(userRows));
            roles.setItems(FXCollections.observableArrayList(roleRows));
            assignments.setItems(FXCollections.observableArrayList(assignmentRows));
            assignUser.setItems(FXCollections.observableArrayList(userRows.stream().map(AppUser::getUsername).toList()));
            assignRole.setItems(FXCollections.observableArrayList(roleRows.stream().map(AppRole::getCode).toList()));
            assignCompany.setItems(FXCollections.observableArrayList(companyRows.stream().map(Company::getCode).toList()));
            if (!userRows.isEmpty() && users.getSelectionModel().getSelectedItem() == null)
            {
                users.getSelectionModel().select(0);
            }
            status.setText("Loaded " + userRows.size() + " user(s), " + assignmentRows.size() + " assignment(s).");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not load users: " + UiErrors.safeMessage(ex));
        }
    }

    private void saveUser()
    {
        try
        {
            AppUser saved = UiServiceRegistry.userAdmin().upsertUser(username.getText(), displayName.getText(), email.getText(), active.isSelected());
            status.setText("Saved user " + saved.getUsername() + ".");
            refresh();
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save user: " + UiErrors.safeMessage(ex));
        }
    }

    private void assignRole()
    {
        try
        {
            UserCompanyRole assignment = UiServiceRegistry.userAdmin().assignRole(assignUser.getValue(), assignCompany.getValue(), assignRole.getValue());
            status.setText("Assigned " + assignment.getRole().getCode() + " to " + assignment.getUser().getUsername() + " for " + assignment.getCompany().getCode() + ".");
            refresh();
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not assign role: " + UiErrors.safeMessage(ex));
        }
    }

    private String nullToBlank(String value)
    {
        return value == null ? "" : value;
    }

    @Override public String title() { return "User Admin"; }
    @Override public Node root() { return root; }
    @Override public void onSave() { saveUser(); }
}
