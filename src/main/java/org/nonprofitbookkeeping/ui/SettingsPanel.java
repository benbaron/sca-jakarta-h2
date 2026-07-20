package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;
import org.nonprofitbookkeeping.model.CompanyUiPreferences;
import org.nonprofitbookkeeping.model.DateDisplayFormat;
import org.nonprofitbookkeeping.model.CorrectionMethod;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.MoneyPrintFormat;
import org.nonprofitbookkeeping.model.ReopenScope;
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
    private final Label status = new Label("Preferences and administration settings can be saved for next startup.");

    private final ComboBox<UiThemePreference> theme = new ComboBox<>();
    private final CheckBox nativeWindow = new CheckBox("Use native window decorations when available");
    private final CheckBox rememberState = new CheckBox("Remember window/state on startup");
    private final ComboBox<UserPrivilegeLevel> defaultPrivilege = new ComboBox<>();
    private final ComboBox<CorrectionMethod> correctionMethod = new ComboBox<>();
    private final ComboBox<ClosedPeriodPolicy> closedPeriodPolicy = new ComboBox<>();
    private final CheckBox requireReopenReason = new CheckBox("Require a reason when reopening a closed period");
    private final ComboBox<ReopenScope> defaultReopenScope = new ComboBox<>();
    private final Spinner<Integer> periodStartDay = new Spinner<>();
    private final CheckBox confirmDeletion = new CheckBox("Confirm before deleting an entered transaction");
    private final TextField currencySymbol = new TextField();
    private final ComboBox<MoneyPrintFormat> moneyPrintFormat = new ComboBox<>();
    private final ComboBox<DateDisplayFormat> dateDisplayFormat = new ComboBox<>();
    private final ComboBox<String> activeCompany = new ComboBox<>();
    private final ComboBox<String> activeDatabase = new ComboBox<>();

    private final UiSessionState session;
    private final CompanySessionController companyController;
    private final FormDirtyTracker dirtyState;

    public SettingsPanel()
    {
        this(
                MainWindow.sharedSessionState(),
                new CompanySessionController(
                        MainWindow.sharedSessionState(),
                        UserAppStateStore.create(),
                        UiServiceRegistry::companyAdmin));
    }

    SettingsPanel(UiSessionState session)
    {
        this(
                session,
                new CompanySessionController(
                        session,
                        UserAppStateStore.create(),
                        UiServiceRegistry::companyAdmin));
    }

    SettingsPanel(UiSessionState session, CompanySessionController companyController)
    {
        this.session = session;
        this.companyController = companyController;

        root.setPadding(new Insets(8));

        Label title = new Label("Preferences");
        title.getStyleClass().add("panel-title");

        root.setTop(new VBox(6, title, status, new Separator()));

        ScrollPane scroll = new ScrollPane(buildPreferencesPane());
        scroll.setId("settingsPreferencesScroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        root.setCenter(scroll);

        dirtyState = new FormDirtyTracker(this::formSnapshot);
        syncFromSession();
    }

    private Node buildPreferencesPane()
    {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(4));

        theme.getItems().addAll(UiThemePreference.values());
        defaultPrivilege.getItems().addAll(UserPrivilegeLevel.values());
        correctionMethod.getItems().addAll(CorrectionMethod.values());
        closedPeriodPolicy.getItems().addAll(ClosedPeriodPolicy.values());
        defaultReopenScope.getItems().addAll(ReopenScope.values());
        periodStartDay.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 28, 1));
        periodStartDay.setEditable(true);
        currencySymbol.setPromptText("$");
        currencySymbol.setPrefColumnCount(5);
        moneyPrintFormat.getItems().setAll(MoneyPrintFormat.values());
        dateDisplayFormat.getItems().setAll(DateDisplayFormat.values());

        activeCompany.setEditable(false);
        activeCompany.setOnAction(event -> loadCompanyUiPreferences(activeCompany.getValue()));

        activeDatabase.setEditable(true);
        activeDatabase.getItems().addAll(session.databaseSelection().recentDatabasePaths());

        int row = 0;
        grid.add(new Label("Theme"), 0, row);
        grid.add(theme, 1, row++);

        grid.add(nativeWindow, 0, row++, 2, 1);
        grid.add(rememberState, 0, row++, 2, 1);

        grid.add(new Label("Default privilege"), 0, row);
        grid.add(defaultPrivilege, 1, row++);

        grid.add(new Label("Correction method"), 0, row);
        grid.add(correctionMethod, 1, row++);

        grid.add(new Label("Closed-period policy"), 0, row);
        grid.add(closedPeriodPolicy, 1, row++);

        grid.add(requireReopenReason, 0, row++, 2, 1);

        grid.add(new Label("Default reopening scope"), 0, row);
        grid.add(defaultReopenScope, 1, row++);

        grid.add(new Label("Period start day"), 0, row);
        grid.add(periodStartDay, 1, row++);

        grid.add(new Label("Money symbol"), 0, row);
        grid.add(currencySymbol, 1, row++);

        grid.add(new Label("Money print format"), 0, row);
        grid.add(moneyPrintFormat, 1, row++);

        grid.add(new Label("Date display format"), 0, row);
        grid.add(dateDisplayFormat, 1, row++);

        grid.add(confirmDeletion, 0, row++, 2, 1);

        grid.add(new Label("Active company"), 0, row);
        grid.add(activeCompany, 1, row++);

        grid.add(new Label("Active database file"), 0, row);
        grid.add(activeDatabase, 1, row++);

        Button apply = new Button("Apply");
        apply.setOnAction(e -> applyToSession());

        Button save = new Button("Save");
        save.setOnAction(e -> onSave());

        return new VBox(8, grid, new HBox(8, apply, save));
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
        correctionMethod.getSelectionModel().select(p.correctionMethod());
        closedPeriodPolicy.getSelectionModel().select(p.closedPeriodPolicy());
        requireReopenReason.setSelected(p.requireReopenReason());
        defaultReopenScope.getSelectionModel().select(p.defaultReopenScope());
        periodStartDay.getValueFactory().setValue(p.periodStartDayOfMonth());
        confirmDeletion.setSelected(p.confirmEnteredTransactionDeletion());

        try
        {
            activeCompany.getItems().setAll(companyController.listActiveCompanies().stream()
                    .map(org.nonprofitbookkeeping.service.CompanyView::code)
                    .toList());
        }
        catch (RuntimeException ex)
        {
            activeCompany.getItems().clear();
            status.setText("Could not load active companies: " + UiErrors.safeMessage(ex));
        }
        activeCompany.getSelectionModel().select(c.activeCompanyCode());
        loadCompanyUiPreferences(c.activeCompanyCode());

        activeDatabase.getItems().setAll(d.recentDatabasePaths());
        if (!d.recentDatabasePaths().contains(d.activeDatabasePath()))
        {
            activeDatabase.getItems().add(d.activeDatabasePath());
        }
        activeDatabase.getSelectionModel().select(d.activeDatabasePath());
        dirtyState.markClean();
    }

    private void applyToSession()
    {
        String selectedCompany = activeCompany.getValue();
        if (selectedCompany == null || selectedCompany.isBlank())
        {
            status.setText("Choose an active H2 company before applying preferences.");
            return;
        }
        session.setPreferences(readPreferences());
        session.setDatabaseSelection(readDatabaseSelection());
        saveCompanyUiPreferences(selectedCompany);
        CompanySessionController.SelectionResult selection = companyController.select(selectedCompany);
        status.setText(selection.selected()
                ? "Applied settings and company display preferences. " + selection.message()
                : "Preferences saved, but active-company selection failed: " + selection.message());
        dirtyState.markClean();
    }

    CompanyUiPreferences readCompanyUiPreferences()
    {
        return new CompanyUiPreferences(
                currencySymbol.getText(),
                moneyPrintFormat.getValue() == null ? MoneyPrintFormat.SYMBOL_PREFIX : moneyPrintFormat.getValue(),
                dateDisplayFormat.getValue() == null ? DateDisplayFormat.MONTH_DAY_YEAR : dateDisplayFormat.getValue());
    }

    private void loadCompanyUiPreferences(String companyCode)
    {
        try
        {
            CompanyUiPreferences preferences = UiServiceRegistry.companyUiPreferences().load(companyCode);
            currencySymbol.setText(preferences.currencySymbol());
            moneyPrintFormat.setValue(preferences.moneyPrintFormat());
            dateDisplayFormat.setValue(preferences.dateDisplayFormat());
        }
        catch (RuntimeException ex)
        {
            CompanyUiPreferences defaults = CompanyUiPreferences.defaults();
            currencySymbol.setText(defaults.currencySymbol());
            moneyPrintFormat.setValue(defaults.moneyPrintFormat());
            dateDisplayFormat.setValue(defaults.dateDisplayFormat());
            status.setText("Could not load company display preferences: " + UiErrors.safeMessage(ex));
        }
    }

    private void saveCompanyUiPreferences(String companyCode)
    {
        UiServiceRegistry.companyUiPreferences().save(companyCode, readCompanyUiPreferences());
    }

    AppPreferencesState readPreferences()
    {
        return new AppPreferencesState(
                theme.getValue() == null ? UiThemePreference.SYSTEM_DEFAULT : theme.getValue(),
                nativeWindow.isSelected(),
                rememberState.isSelected(),
                defaultPrivilege.getValue() == null ? UserPrivilegeLevel.ACCOUNTANT : defaultPrivilege.getValue(),
                correctionMethod.getValue() == null ? CorrectionMethod.DIRECT_EDIT : correctionMethod.getValue(),
                closedPeriodPolicy.getValue() == null ? ClosedPeriodPolicy.WARN_AND_REOPEN : closedPeriodPolicy.getValue(),
                requireReopenReason.isSelected(),
                defaultReopenScope.getValue() == null ? ReopenScope.UNTIL_MANUALLY_CLOSED : defaultReopenScope.getValue(),
                confirmDeletion.isSelected(),
                periodStartDay.getValue() == null ? 1 : periodStartDay.getValue());
    }

    MultiCompanyState readMultiCompany()
    {
        String selected = activeCompany.getValue();
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
            selected = org.nonprofitbookkeeping.persistence.DatabaseLocationService.defaultUserDatabasePath().toString();
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
        status.setText("Saved settings and company display preferences. They will be restored for the active company.");
    }

    @Override
    public String title()
    {
        return "Settings";
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override
    public boolean hasUnsavedChanges()
    {
        return dirtyState.isDirty();
    }

    private SettingsSnapshot formSnapshot()
    {
        return new SettingsSnapshot(
                readPreferences(),
                readCompanyUiPreferences(),
                activeCompany.getValue(),
                activeDatabase.getEditor().getText());
    }

    private record SettingsSnapshot(
            AppPreferencesState preferences,
            CompanyUiPreferences companyPreferences,
            String activeCompany,
            String activeDatabase)
    {
    }
}
