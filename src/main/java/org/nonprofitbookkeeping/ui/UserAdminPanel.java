package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.model.AppRole;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.UserCompanyRole;
import org.nonprofitbookkeeping.service.AppRoleCommand;
import org.nonprofitbookkeeping.service.AppRoleUsage;
import org.nonprofitbookkeeping.service.AppUserCommand;
import org.nonprofitbookkeeping.service.UserRoleAssignmentCommand;
import org.nonprofitbookkeeping.service.UserRoleAssignmentEndCommand;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Durable user, role, assignment-history, and authentication maintenance. */
public class UserAdminPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TabPane tabs = new TabPane();
    private final TableView<AppUser> users = new TableView<>();
    private final TableView<AppRole> roles = new TableView<>();
    private final TableView<UserCompanyRole> assignments = new TableView<>();
    private final Label status = new Label("Ready.");
    private final Label assignmentCompany = new Label(activeCompanyCode());
    private final CompanyUiFormat companyFormat = CompanyUiFormat.activeCompany();

    private final TextField actor = new TextField(DesktopActorIdentity.current());
    private final TextField username = new TextField();
    private final TextField displayName = new TextField();
    private final TextField email = new TextField();
    private final CheckBox userActive = new CheckBox("Active");
    private final TextField roleCode = new TextField();
    private final TextField roleName = new TextField();
    private final TextArea roleDescription = new TextArea();
    private final CheckBox roleActive = new CheckBox("Active");
    private final ComboBox<AppUser> assignUser = new ComboBox<>();
    private final ComboBox<AppRole> assignRole = new ComboBox<>();
    private final DatePicker assignmentStart = new DatePicker(LocalDate.now());
    private final DatePicker assignmentEnd = new DatePicker(LocalDate.now());
    private final TextField assignmentReason = new TextField();
    private final Button endAssignment = new Button("End Selected");
    private final Button revokeAssignment = new Button("Revoke Selected");

    private final FormDirtyTracker userDirty;
    private final FormDirtyTracker roleDirty;
    private final FormDirtyTracker assignmentDirty;
    private SecurityAdminPane securityAdmin;
    private Long editingUserId;
    private Long editingRoleId;
    private boolean suppressUserSelection;
    private boolean suppressRoleSelection;
    private Runnable commandCapabilitiesChangedListener = () -> { };

    public UserAdminPanel()
    {
        userDirty = new FormDirtyTracker(this::userSnapshot);
        roleDirty = new FormDirtyTracker(this::roleSnapshot);
        assignmentDirty = new FormDirtyTracker(this::assignmentSnapshot);
        build();
        refresh();
    }

    private void build()
    {
        Label title = new Label("User Admin");
        title.getStyleClass().add("panel-title");
        Label help = new Label(
                "Maintain durable users, global role definitions, dated company assignments, and account credentials. Effective role state is derived from H2 assignments; runtime operation enforcement completes in P20-S3.");
        help.setWrapText(true);
        actor.setPromptText("Authenticated audit actor");
        actor.setEditable(false);
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refreshWithDiscardProtection());
        HBox actions = new HBox(8, refresh, new Label("Authenticated audit actor"), actor, status);
        HBox.setHgrow(actor, Priority.ALWAYS);
        VBox header = new VBox(6, title, help, actions);
        header.setPadding(new Insets(8));
        root.setTop(header);

        configureUsersTable();
        configureRolesTable();
        configureAssignmentsTable();
        companyFormat.install(assignmentStart);
        companyFormat.install(assignmentEnd);
        configureConverters();
        securityAdmin = new SecurityAdminPane();

        tabs.setId("userAdminMaintenanceTabs");
        tabs.getTabs().setAll(
                tab("Users", tableEditorSplit("userAdminUsersSplit", "Users", users,
                        userEditor(), "user-admin-users")),
                tab("Roles", tableEditorSplit("userAdminRolesSplit", "Roles", roles,
                        roleEditor(), "user-admin-roles")),
                tab("Company Assignments", tableEditorSplit(
                        "userAdminAssignmentsSplit", "Assignment history for " + activeCompanyCode(),
                        assignments, assignmentEditor(), "user-admin-assignments")),
                tab("Authentication", securityAdmin.root()));
        tabs.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) ->
        {
            if (tabs.getSelectionModel().getSelectedIndex() == 3)
            {
                securityAdmin.refresh();
            }
            commandCapabilitiesChangedListener.run();
        });
        root.setCenter(tabs);

        users.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
                selectUser(oldValue, newValue));
        roles.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
                selectRole(oldValue, newValue));
        assignments.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            updateAssignmentActions(newValue);
            assignmentEnd.setValue(LocalDate.now());
            assignmentReason.clear();
            assignmentDirty.markClean();
        });
    }

    private Node userEditor()
    {
        GridPane form = formGrid();
        int row = 0;
        form.addRow(row++, new Label("Username"), username);
        form.addRow(row++, new Label("Display Name"), displayName);
        form.addRow(row++, new Label("Email"), email);
        form.add(userActive, 1, row++);
        Label lifecycle = explanatoryLabel(
                "Users are never hard-deleted. Reserved security accounts keep their fixed username and active state. End or revoke every active non-reserved assignment before deactivating another user.");
        form.add(lifecycle, 0, row++, 2, 1);
        Button add = new Button("New User");
        add.setOnAction(event -> newUser());
        Button save = new Button("Save User");
        save.setOnAction(event -> saveUser());
        form.add(new HBox(8, add, save), 1, row);
        return form;
    }

    private Node roleEditor()
    {
        GridPane form = formGrid();
        int row = 0;
        form.addRow(row++, new Label("Code"), roleCode);
        form.addRow(row++, new Label("Name"), roleName);
        form.addRow(row++, new Label("Description"), roleDescription);
        form.add(roleActive, 1, row++);
        roleDescription.setPrefRowCount(4);
        Label lifecycle = explanatoryLabel(
                "Roles are global definitions and are never hard-deleted. ADMIN, MANAGER, ACCOUNTANT, and VIEWER are fixed runtime security roles and cannot be renamed or deactivated. Historical assignments remain.");
        form.add(lifecycle, 0, row++, 2, 1);
        Button add = new Button("New Role");
        add.setOnAction(event -> newRole());
        Button save = new Button("Save Role");
        save.setOnAction(event -> saveRole());
        form.add(new HBox(8, add, save), 1, row);
        return form;
    }

    private Node assignmentEditor()
    {
        GridPane form = formGrid();
        int row = 0;
        form.addRow(row++, new Label("Company"), assignmentCompany);
        form.addRow(row++, new Label("User"), assignUser);
        form.addRow(row++, new Label("Role"), assignRole);
        form.addRow(row++, new Label("Start Date"), assignmentStart);
        Label boundary = explanatoryLabel(
                "Assignments are scoped to the active company and determine effective reserved-role state. The singleton ADMIN assignment is permanent; other changes create dated history rows. Ending and revocation dates cannot be in the future.");
        form.add(boundary, 0, row++, 2, 1);
        Button add = new Button("New Assignment");
        add.setOnAction(event -> newAssignment());
        Button assign = new Button("Assign Role");
        assign.setOnAction(event -> assignRole());
        form.add(new HBox(8, add, assign), 1, row++);
        form.addRow(row++, new Label("End / Revoke Date"), assignmentEnd);
        form.addRow(row++, new Label("Reason"), assignmentReason);
        endAssignment.setDisable(true);
        endAssignment.setOnAction(event -> endAssignment(false));
        revokeAssignment.setDisable(true);
        revokeAssignment.setOnAction(event -> endAssignment(true));
        form.add(new HBox(8, endAssignment, revokeAssignment), 1, row);
        return form;
    }

    private GridPane formGrid()
    {
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(6);
        form.setPadding(new Insets(8));
        GridPane.setHgrow(username, Priority.ALWAYS);
        GridPane.setHgrow(displayName, Priority.ALWAYS);
        GridPane.setHgrow(email, Priority.ALWAYS);
        GridPane.setHgrow(roleCode, Priority.ALWAYS);
        GridPane.setHgrow(roleName, Priority.ALWAYS);
        GridPane.setHgrow(roleDescription, Priority.ALWAYS);
        GridPane.setHgrow(assignUser, Priority.ALWAYS);
        GridPane.setHgrow(assignRole, Priority.ALWAYS);
        GridPane.setHgrow(assignmentStart, Priority.ALWAYS);
        GridPane.setHgrow(assignmentEnd, Priority.ALWAYS);
        GridPane.setHgrow(assignmentReason, Priority.ALWAYS);
        return form;
    }

    private Label explanatoryLabel(String text)
    {
        Label label = new Label(text);
        label.setWrapText(true);
        GridPane.setHgrow(label, Priority.ALWAYS);
        return label;
    }

    private void configureUsersTable()
    {
        users.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        users.getColumns().setAll(
                column("Username", AppUser::getUsername),
                column("Display Name", AppUser::getDisplayName),
                column("Email", AppUser::getEmail),
                column("Status", user -> user.isActive() ? "Active" : "Inactive"));
        users.setMinHeight(0.0);
    }

    private void configureRolesTable()
    {
        roles.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        roles.getColumns().setAll(
                column("Code", AppRole::getCode),
                column("Name", AppRole::getName),
                column("Description", AppRole::getDescription),
                column("Status", role -> role.isActive() ? "Active" : "Inactive"));
        roles.setMinHeight(0.0);
    }

    private void configureAssignmentsTable()
    {
        assignments.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        assignments.getColumns().setAll(
                column("User", assignment -> assignment.getUser().getUsername()),
                column("Company", assignment -> assignment.getCompany().getCode()),
                column("Role", assignment -> assignment.getRole().getCode()),
                column("Start", assignment -> companyFormat.formatDate(assignment.getStartDate())),
                column("End", assignment -> companyFormat.formatDate(assignment.getEndDate())),
                column("Status", this::assignmentStatus),
                column("Reason", UserCompanyRole::getEndReason));
        assignments.setMinHeight(0.0);
    }

    private void configureConverters()
    {
        assignUser.setConverter(new StringConverter<>()
        {
            @Override public String toString(AppUser value)
            {
                return value == null ? "" : value.getUsername() + " — " + value.getDisplayName();
            }

            @Override public AppUser fromString(String value)
            {
                return null;
            }
        });
        assignRole.setConverter(new StringConverter<>()
        {
            @Override public String toString(AppRole value)
            {
                return value == null ? "" : value.getCode() + " — " + value.getName();
            }

            @Override public AppRole fromString(String value)
            {
                return null;
            }
        });
    }

    private <T> TableColumn<T, String> column(
            String title,
            java.util.function.Function<T, String> extractor)
    {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(
                nullToBlank(extractor.apply(data.getValue()))));
        column.setMinWidth(100);
        column.setPrefWidth(Math.max(130, title.length() * 12));
        return column;
    }

    private void refresh()
    {
        Long selectedUserId = selectedId(users.getSelectionModel().getSelectedItem());
        Long selectedRoleId = selectedId(roles.getSelectionModel().getSelectedItem());
        Long selectedAssignmentId = selectedId(assignments.getSelectionModel().getSelectedItem());
        try
        {
            userDirty.markClean();
            roleDirty.markClean();
            assignmentDirty.markClean();
            List<AppUser> userRows = UiServiceRegistry.userAdmin().listUsers();
            List<AppRole> roleRows = UiServiceRegistry.userAdmin().listRoles();
            List<UserCompanyRole> assignmentRows = UiServiceRegistry.userAdmin().listAssignments();
            users.setItems(FXCollections.observableArrayList(userRows));
            roles.setItems(FXCollections.observableArrayList(roleRows));
            assignments.setItems(FXCollections.observableArrayList(assignmentRows));
            assignUser.setItems(FXCollections.observableArrayList(
                    userRows.stream().filter(AppUser::isActive).toList()));
            assignRole.setItems(FXCollections.observableArrayList(
                    roleRows.stream().filter(AppRole::isActive).toList()));
            selectById(users, selectedUserId);
            selectById(roles, selectedRoleId);
            selectById(assignments, selectedAssignmentId);
            if (users.getSelectionModel().getSelectedItem() == null && !userRows.isEmpty())
            {
                users.getSelectionModel().select(0);
            }
            if (roles.getSelectionModel().getSelectedItem() == null && !roleRows.isEmpty())
            {
                roles.getSelectionModel().select(0);
            }
            status.setText("Loaded " + userRows.size() + " user(s), " + roleRows.size()
                    + " role(s), and " + assignmentRows.size() + " assignment history row(s) for "
                    + activeCompanyCode() + ".");
            userDirty.markClean();
            roleDirty.markClean();
            assignmentDirty.markClean();
            updateAssignmentActions(assignments.getSelectionModel().getSelectedItem());
            if (securityAdmin != null)
            {
                securityAdmin.refresh();
            }
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not load User Admin: " + UiErrors.safeMessage(ex));
        }
    }

    private void saveUser()
    {
        try
        {
            AppUser saved = UiServiceRegistry.userAdmin().saveUser(new AppUserCommand(
                    editingUserId, username.getText(), displayName.getText(), email.getText(),
                    userActive.isSelected(), actor.getText()));
            editingUserId = saved.getId();
            refreshAuthenticatedRoleState();
            status.setText("Saved user " + saved.getUsername() + " by stable ID " + saved.getId() + ".");
            refresh();
            selectById(users, saved.getId());
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save user: " + UiErrors.safeMessage(ex));
        }
    }

    private void saveRole()
    {
        try
        {
            AppRole saved = UiServiceRegistry.userAdmin().saveRole(new AppRoleCommand(
                    editingRoleId, roleCode.getText(), roleName.getText(), roleDescription.getText(),
                    roleActive.isSelected(), actor.getText()));
            editingRoleId = saved.getId();
            AppRoleUsage usage = UiServiceRegistry.userAdmin().roleUsage(saved.getId());
            refreshAuthenticatedRoleState();
            status.setText("Saved role " + saved.getCode() + " by stable ID " + saved.getId()
                    + "; " + usage.activeAssignments() + " active and "
                    + usage.historicalAssignments() + " historical assignment(s).");
            refresh();
            selectById(roles, saved.getId());
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save role: " + UiErrors.safeMessage(ex));
        }
    }

    private void assignRole()
    {
        try
        {
            AppUser user = assignUser.getValue();
            AppRole role = assignRole.getValue();
            UserCompanyRole saved = UiServiceRegistry.userAdmin().assignRole(
                    new UserRoleAssignmentCommand(
                            user == null ? null : user.getId(),
                            role == null ? null : role.getId(),
                            assignmentStart.getValue(),
                            actor.getText()));
            refreshAuthenticatedRoleState();
            status.setText("Assigned " + saved.getRole().getCode() + " to "
                    + saved.getUser().getUsername() + " for " + activeCompanyCode() + ".");
            refresh();
            selectById(assignments, saved.getId());
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not assign role: " + UiErrors.safeMessage(ex));
        }
    }

    private void endAssignment(boolean revoked)
    {
        UserCompanyRole selected = assignments.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select an active assignment to end or revoke.");
            return;
        }
        try
        {
            UserCompanyRole saved = UiServiceRegistry.userAdmin().endAssignment(
                    new UserRoleAssignmentEndCommand(
                            selected.getId(), assignmentEnd.getValue(), revoked,
                            assignmentReason.getText(), actor.getText()));
            refreshAuthenticatedRoleState();
            status.setText((revoked ? "Revoked " : "Ended ") + saved.getRole().getCode()
                    + " for " + saved.getUser().getUsername() + "; history row "
                    + saved.getId() + " was retained.");
            refresh();
            selectById(assignments, saved.getId());
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not " + (revoked ? "revoke" : "end")
                    + " assignment: " + UiErrors.safeMessage(ex));
        }
    }

    private void refreshAuthenticatedRoleState()
    {
        MainWindow.sharedSessionState().authenticatedUser().ifPresent(current ->
        {
            try
            {
                MainWindow.sharedSessionState().setAuthenticatedUser(
                        UiServiceRegistry.authentication().refresh(current));
            }
            catch (RuntimeException ex)
            {
                MainWindow.sharedSessionState().clearAuthenticatedUser();
            }
        });
    }

    private void selectUser(AppUser oldValue, AppUser newValue)
    {
        if (suppressUserSelection || newValue == null)
        {
            return;
        }
        if (userDirty.isDirty() && !confirmDiscard("user"))
        {
            suppressUserSelection = true;
            users.getSelectionModel().select(oldValue);
            suppressUserSelection = false;
            return;
        }
        editingUserId = newValue.getId();
        username.setText(newValue.getUsername());
        displayName.setText(newValue.getDisplayName());
        email.setText(nullToBlank(newValue.getEmail()));
        userActive.setSelected(newValue.isActive());
        userDirty.markClean();
    }

    private void selectRole(AppRole oldValue, AppRole newValue)
    {
        if (suppressRoleSelection || newValue == null)
        {
            return;
        }
        if (roleDirty.isDirty() && !confirmDiscard("role"))
        {
            suppressRoleSelection = true;
            roles.getSelectionModel().select(oldValue);
            suppressRoleSelection = false;
            return;
        }
        editingRoleId = newValue.getId();
        roleCode.setText(newValue.getCode());
        roleName.setText(newValue.getName());
        roleDescription.setText(nullToBlank(newValue.getDescription()));
        roleActive.setSelected(newValue.isActive());
        roleDirty.markClean();
    }

    private void newUser()
    {
        if (userDirty.isDirty() && !confirmDiscard("user"))
        {
            status.setText("New user cancelled; unsaved changes remain.");
            return;
        }
        suppressUserSelection = true;
        users.getSelectionModel().clearSelection();
        suppressUserSelection = false;
        editingUserId = null;
        username.clear();
        displayName.clear();
        email.clear();
        userActive.setSelected(true);
        userDirty.markClean();
        status.setText("Ready to create a user.");
    }

    private void newRole()
    {
        if (roleDirty.isDirty() && !confirmDiscard("role"))
        {
            status.setText("New role cancelled; unsaved changes remain.");
            return;
        }
        suppressRoleSelection = true;
        roles.getSelectionModel().clearSelection();
        suppressRoleSelection = false;
        editingRoleId = null;
        roleCode.clear();
        roleName.clear();
        roleDescription.clear();
        roleActive.setSelected(true);
        roleDirty.markClean();
        status.setText("Ready to create a global role definition.");
    }

    private void newAssignment()
    {
        assignments.getSelectionModel().clearSelection();
        assignUser.getSelectionModel().clearSelection();
        assignRole.getSelectionModel().clearSelection();
        assignmentStart.setValue(LocalDate.now());
        assignmentEnd.setValue(LocalDate.now());
        assignmentReason.clear();
        assignmentDirty.markClean();
        updateAssignmentActions(null);
        status.setText("Ready to create an assignment for " + activeCompanyCode() + ".");
    }

    private void updateAssignmentActions(UserCompanyRole selected)
    {
        boolean unavailable = selected == null || !selected.isActive();
        endAssignment.setDisable(unavailable);
        revokeAssignment.setDisable(unavailable);
    }

    private String assignmentStatus(UserCompanyRole assignment)
    {
        if (assignment.isActive())
        {
            return assignment.getStartDate().isAfter(LocalDate.now()) ? "Scheduled" : "Active";
        }
        return assignment.getRevokedAt() == null ? "Ended" : "Revoked";
    }

    private Tab tab(String label, Node content)
    {
        Tab tab = new Tab(label, content);
        tab.setClosable(false);
        return tab;
    }

    private Node tableEditorSplit(
            String id,
            String tableLabel,
            TableView<?> table,
            Node editor,
            String stateKey)
    {
        VBox tableRegion = new VBox(6, new Label(tableLabel), table);
        tableRegion.setMinHeight(0.0);
        VBox.setVgrow(table, Priority.ALWAYS);
        ScrollPane editorScroll = new ScrollPane(editor);
        editorScroll.setFitToWidth(true);
        editorScroll.setMinHeight(0.0);
        editorScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        editorScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        SplitPane split = new SplitPane(tableRegion, editorScroll);
        split.setId(id);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.65);
        CompanySplitPaneStateBinder.bind(split, stateKey, 0.65);
        return split;
    }

    private UserSnapshot userSnapshot()
    {
        return new UserSnapshot(
                editingUserId, username.getText(), displayName.getText(), email.getText(), userActive.isSelected());
    }

    private RoleSnapshot roleSnapshot()
    {
        return new RoleSnapshot(
                editingRoleId, roleCode.getText(), roleName.getText(), roleDescription.getText(),
                roleActive.isSelected());
    }

    private AssignmentSnapshot assignmentSnapshot()
    {
        return new AssignmentSnapshot(
                selectedId(assignUser.getValue()), selectedId(assignRole.getValue()), assignmentStart.getValue());
    }

    private void refreshWithDiscardProtection()
    {
        if (!hasUnsavedChanges() || confirmDiscard("user, role, or assignment"))
        {
            refresh();
        }
    }

    private boolean confirmDiscard(String editor)
    {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Discard administration edits");
        confirmation.setHeaderText("Discard unsaved " + editor + " changes?");
        confirmation.setContentText("Choose Cancel to remain in User Admin.");
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    @Override public String title() { return "User Admin"; }
    @Override public Node root() { return root; }

    @Override
    public Set<AppCommand> commandCapabilities()
    {
        return selectedMaintenanceTab() ? AppPanel.capabilities(AppCommand.NEW_ACTIVE, AppCommand.SAVE_ACTIVE)
                : Set.of();
    }

    @Override
    public void onSave()
    {
        switch (selectedTabIndex())
        {
            case 0 -> saveUser();
            case 1 -> saveRole();
            case 2 -> assignRole();
            default -> throw new UnsupportedOperationException("Save is not available on Authentication.");
        }
    }

    @Override
    public void onNew()
    {
        switch (selectedTabIndex())
        {
            case 0 -> newUser();
            case 1 -> newRole();
            case 2 -> newAssignment();
            default -> throw new UnsupportedOperationException("New is not available on Authentication.");
        }
    }

    @Override
    public void setCommandCapabilitiesChangedListener(Runnable listener)
    {
        commandCapabilitiesChangedListener = Objects.requireNonNull(listener, "listener");
        commandCapabilitiesChangedListener.run();
    }

    @Override public String commandResultMessage(AppCommand command) { return status.getText(); }
    @Override public boolean hasUnsavedChanges()
    {
        return userDirty.isDirty() || roleDirty.isDirty() || assignmentDirty.isDirty();
    }

    private boolean selectedMaintenanceTab()
    {
        return selectedTabIndex() >= 0 && selectedTabIndex() <= 2;
    }

    private int selectedTabIndex()
    {
        return tabs.getSelectionModel().getSelectedIndex();
    }

    private static Long selectedId(Object value)
    {
        if (value instanceof AppUser user)
        {
            return user.getId();
        }
        if (value instanceof AppRole role)
        {
            return role.getId();
        }
        if (value instanceof UserCompanyRole assignment)
        {
            return assignment.getId();
        }
        return null;
    }

    private static <T> void selectById(TableView<T> table, Long id)
    {
        if (id == null)
        {
            return;
        }
        table.getItems().stream()
                .filter(value -> id.equals(selectedId(value)))
                .findFirst()
                .ifPresent(value -> table.getSelectionModel().select(value));
    }

    private static String activeCompanyCode()
    {
        String value = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        return value == null || value.isBlank() ? "DEFAULT" : value.trim().toUpperCase();
    }

    private static String nullToBlank(String value)
    {
        return value == null ? "" : value;
    }

    private record UserSnapshot(Long id, String username, String displayName, String email, boolean active)
    {
    }

    private record RoleSnapshot(Long id, String code, String name, String description, boolean active)
    {
    }

    private record AssignmentSnapshot(Long userId, Long roleId, LocalDate startDate)
    {
    }

    void setUsernameForTests(String value)
    {
        username.setText(value);
    }

    TabPane tabsForTests()
    {
        return tabs;
    }
}
