package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.UiThemePreference;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the SettingsPanel component in the nonprofit bookkeeping application.
 */
public class SettingsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final Label status = new Label("Preferences and company context can be saved for next startup.");

    private final ComboBox<UiThemePreference> theme = new ComboBox<>();
    private final CheckBox nativeWindow = new CheckBox("Use native window decorations when available");
    private final CheckBox rememberState = new CheckBox("Remember window/state on startup");
    private final ComboBox<UserPrivilegeLevel> defaultPrivilege = new ComboBox<>();
    private final ComboBox<String> activeCompany = new ComboBox<>();
    private final ComboBox<String> activeDatabase = new ComboBox<>();

    private final UiSessionState session;

    public SettingsPanel()
    {
        this(MainWindow.sharedSessionState());
    }

    SettingsPanel(UiSessionState session)
    {
        this.session = session;

        root.setPadding(new Insets(8));

        Label title = new Label("Settings");
        title.getStyleClass().add("panel-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(4));

        theme.getItems().addAll(UiThemePreference.values());
        defaultPrivilege.getItems().addAll(UserPrivilegeLevel.values());

        activeCompany.setEditable(true);
        activeCompany.getItems().addAll(session.multiCompany().recentCompanyCodes());

        activeDatabase.setEditable(true);
        activeDatabase.getItems().addAll(session.databaseSelection().recentDatabasePaths());

        int row = 0;
        grid.add(new Label("Theme"), 0, row);
        grid.add(theme, 1, row++);

        grid.add(nativeWindow, 0, row++, 2, 1);
        grid.add(rememberState, 0, row++, 2, 1);

        grid.add(new Label("Default privilege"), 0, row);
        grid.add(defaultPrivilege, 1, row++);

        grid.add(new Label("Active company"), 0, row);
        grid.add(activeCompany, 1, row++);

        grid.add(new Label("Active database file"), 0, row);
        grid.add(activeDatabase, 1, row++);

        Button apply = new Button("Apply");
        apply.setOnAction(e -> applyToSession());

        Button save = new Button("Save");
        save.setOnAction(e -> onSave());

        root.setTop(new VBox(6, title, status, new Separator()));
        root.setCenter(grid);
        root.setBottom(new HBox(8, apply, save));

        syncFromSession();
    }

    private void syncFromSession()
    {
        AppPreferencesState p = session.preferences();
        MultiCompanyState c = session.multiCompany();
        DatabaseSelectionState d = session.databaseSelection();

        theme.getSelectionModel().select(p.themePreference());
        nativeWindow.setSelected(p.useNativeWindowDecorations());
        rememberState.setSelected(p.rememberWindowState());
        defaultPrivilege.getSelectionModel().select(p.defaultPrivilege());

        activeCompany.getItems().setAll(c.recentCompanyCodes());
        if (!c.recentCompanyCodes().contains(c.activeCompanyCode()))
        {
            activeCompany.getItems().add(c.activeCompanyCode());
        }
        activeCompany.getSelectionModel().select(c.activeCompanyCode());

        activeDatabase.getItems().setAll(d.recentDatabasePaths());
        if (!d.recentDatabasePaths().contains(d.activeDatabasePath()))
        {
            activeDatabase.getItems().add(d.activeDatabasePath());
        }
        activeDatabase.getSelectionModel().select(d.activeDatabasePath());
    }

    private void applyToSession()
    {
        session.setPreferences(readPreferences());
        session.setMultiCompany(readMultiCompany());
        session.setDatabaseSelection(readDatabaseSelection());
        status.setText("Applied settings to current session.");
    }

    AppPreferencesState readPreferences()
    {
        return new AppPreferencesState(
                theme.getValue() == null ? UiThemePreference.SYSTEM_DEFAULT : theme.getValue(),
                nativeWindow.isSelected(),
                rememberState.isSelected(),
                defaultPrivilege.getValue() == null ? UserPrivilegeLevel.ACCOUNTANT : defaultPrivilege.getValue());
    }

    MultiCompanyState readMultiCompany()
    {
        String selected = activeCompany.getEditor().getText();
        if (selected == null || selected.isBlank())
        {
            selected = "DEFAULT";
        }
        List<String> recents = new ArrayList<>(activeCompany.getItems());
        if (!recents.contains(selected))
        {
            recents.add(0, selected);
        }
        return new MultiCompanyState(selected, recents);
    }


    DatabaseSelectionState readDatabaseSelection()
    {
        String selected = activeDatabase.getEditor().getText();
        if (selected == null || selected.isBlank())
        {
            selected = "data/sca-ledger.mv.db";
        }

        List<String> recents = new ArrayList<>();
        recents.add(selected);
        for (String candidate : activeDatabase.getItems())
        {
            if (candidate == null || candidate.isBlank() || candidate.equals(selected))
            {
                continue;
            }
            if (!recents.contains(candidate))
            {
                recents.add(candidate);
            }
        }

        return new DatabaseSelectionState(selected, recents);
    }


    void setActiveDatabaseForTests(String value)
    {
        activeDatabase.getEditor().setText(value);
    }

    void setRecentDatabasesForTests(List<String> values)
    {
        activeDatabase.getItems().setAll(values);
    }

    @Override
    public void onSave()
    {
        applyToSession();
        status.setText("Saved settings. They will be restored on next startup.");
    }

    @Override public String title() { return "Settings"; }
    @Override public Node root() { return root; }
}
