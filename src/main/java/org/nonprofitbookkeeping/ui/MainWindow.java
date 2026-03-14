package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.UiThemePreference;
import org.nonprofitbookkeeping.service.BankTransactionRecord;
import org.nonprofitbookkeeping.service.CoaCsvMapper;
import org.nonprofitbookkeeping.service.ImportExportOrchestrationService;

import javafx.stage.FileChooser;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Represents the MainWindow component in the nonprofit bookkeeping application.
 */
public class MainWindow extends BorderPane
{
    private static final UiSessionState SESSION_STATE = new UiSessionState();

    private final AppStateStore stateStore;
    private final ImportExportOrchestrationService importExportService = new ImportExportOrchestrationService();
    private final PanelHost panelHost = new PanelHost();
    private final InspectorPane inspectorPane = new InspectorPane();
    private final NavigationPane nav = new NavigationPane(this::openPanel, this::openInspectorForSelection);
    private DateRangeSelector dateRangeSelector;
    private Label activePanelLabel;
    private Label activeCompanyLabel;
    private Label activeDatabaseLabel;

    public MainWindow()
    {
        this(defaultStateStore());
    }

    MainWindow(AppStateStore stateStore)
    {
        this.stateStore = stateStore;

        restoreState();

        setTop(buildTopChrome());
        setLeft(nav);
        setCenter(panelHost);
        setRight(inspectorPane);

        BorderPane.setMargin(panelHost, new Insets(8));
        BorderPane.setMargin(nav, new Insets(8, 4, 8, 8));
        BorderPane.setMargin(inspectorPane, new Insets(8, 8, 8, 4));

        SESSION_STATE.onPreferencesChanged(this::applyPreferences);
        SESSION_STATE.onMultiCompanyChanged(this::applyMultiCompany);
        SESSION_STATE.onDatabaseSelectionChanged(this::applyDatabaseSelection);

        applyPreferences(SESSION_STATE.preferences());
        applyMultiCompany(SESSION_STATE.multiCompany());
        applyDatabaseSelection(SESSION_STATE.databaseSelection());

        openPanel(AppPanelId.LEDGER_REGISTER);
    }

    static UiSessionState sharedSessionState()
    {
        return SESSION_STATE;
    }

    static void resetSessionForTests(AppPreferencesState preferences, MultiCompanyState multiCompany)
    {
        SESSION_STATE.setPreferences(preferences);
        SESSION_STATE.setMultiCompany(multiCompany);
        SESSION_STATE.setDatabaseSelection(new DatabaseSelectionState("data/sca-ledger.mv.db", List.of("data/sca-ledger.mv.db")));
    }

    private static AppStateStore defaultStateStore()
    {
        Path statePath = Path.of(System.getProperty("user.home"), ".sca-ledger", "ui-state.properties");
        return new FileAppStateStore(statePath);
    }

    private void restoreState()
    {
        stateStore.loadPreferences().ifPresent(SESSION_STATE::setPreferences);
        stateStore.loadMultiCompany().ifPresent(SESSION_STATE::setMultiCompany);
        stateStore.loadDatabaseSelection().ifPresent(SESSION_STATE::setDatabaseSelection);
    }

    private VBox buildTopChrome()
    {
        MenuBar menuBar = buildMenuBar();
        ToolBar toolBar = buildToolBar();
        VBox v = new VBox(menuBar, toolBar);
        v.getStyleClass().add("top-chrome");
        return v;
    }

    private MenuBar buildMenuBar()
    {
        Menu file = new Menu("File");
        file.getItems().addAll(
                item("New", "Ctrl+N", this::newItemInActivePanel),
                item("Open…", null, () -> info("Open not wired yet.")),
                item("Select Database File…", null, this::selectDatabaseFile),
                new SeparatorMenuItem(),
                item("Save", "Ctrl+S", this::saveActivePanel),
                item("Export…", null, this::exportDataFromFileMenu),
                new SeparatorMenuItem(),
                item("Exit", null, () -> System.exit(0))
        );

        Menu edit = new Menu("Edit");
        edit.getItems().addAll(
                item("Undo", "Ctrl+Z", () -> info("Undo not wired yet.")),
                item("Redo", "Ctrl+Y", () -> info("Redo not wired yet.")),
                new SeparatorMenuItem(),
                item("Cut", "Ctrl+X", () -> info("Cut not wired yet.")),
                item("Copy", "Ctrl+C", this::copySelection),
                item("Paste", "Ctrl+V", this::paste)
        );

        Menu search = new Menu("Search");
        search.getItems().addAll(
                item("Find…", "Ctrl+F", this::openSearch),
                item("Go to…", "Ctrl+G", () -> info("Go to not wired yet.")),
                new SeparatorMenuItem(),
                item("Date Range…", null, this::focusDateRangeSelector)
        );

        Menu view = new Menu("View");
        view.getItems().addAll(
                item("Theme: Light", null, () -> selectTheme(UiThemePreference.LIGHT)),
                item("Theme: Dark", null, () -> selectTheme(UiThemePreference.DARK)),
                item("Theme: System", null, () -> selectTheme(UiThemePreference.SYSTEM_DEFAULT))
        );

        Menu run = new Menu("Run");
        run.getItems().addAll(
                item("Post / Validate", null, () -> info("Posting not wired in UI yet.")),
                item("Recalculate summaries", null, () -> info("Recalculate not wired yet."))
        );

        Menu tools = new Menu("Tools");
        tools.getItems().addAll(
                item("Import CoA CSV…", null, this::importCoaCsvFromFile),
                item("Import Bank OFX/QFX…", null, this::importBankEnvelopeFromFile),
                item("Preferences…", null, () -> openPanel(AppPanelId.SETTINGS))
        );

        Menu help = new Menu("Help");
        help.getItems().addAll(
                item("Help Topics", null, () -> openPanel(AppPanelId.HELP)),
                item("About", null, () -> info("SCA Ledger prototype shell."))
        );

        return new MenuBar(file, edit, search, view, run, tools, help);
    }

    private ToolBar buildToolBar()
    {
        Button btnNew = new Button("New");
        btnNew.setOnAction(e -> newItemInActivePanel());

        Button btnSave = new Button("Save");
        btnSave.setOnAction(e -> saveActivePanel());

        Button btnFind = new Button("Find");
        btnFind.setOnAction(e -> openSearch());

        Button btnJournal = new Button("Journal");
        btnJournal.setOnAction(e -> openInspectorJournal());

        DateRangeSelector dr = new DateRangeSelector();
        this.dateRangeSelector = dr;

        activePanelLabel = new Label("Panel: (none)");
        activePanelLabel.getStyleClass().add("toolbar-active-panel");

        activeCompanyLabel = new Label("Company: " + SESSION_STATE.multiCompany().activeCompanyCode());
        activeCompanyLabel.getStyleClass().add("toolbar-active-panel");

        activeDatabaseLabel = new Label("DB: " + Path.of(SESSION_STATE.databaseSelection().activeDatabasePath()).getFileName());
        activeDatabaseLabel.getStyleClass().add("toolbar-active-panel");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToolBar tb = new ToolBar(btnNew, btnSave, new Separator(), btnFind, new Separator(), btnJournal,
                new Separator(), dr, spacer, activeDatabaseLabel, new Separator(), activeCompanyLabel, new Separator(), activePanelLabel);
        tb.getStyleClass().add("toolbar");
        return tb;
    }

    private void focusDateRangeSelector()
    {
        if (dateRangeSelector == null)
        {
            return;
        }
        dateRangeSelector.presetBox().requestFocus();
        dateRangeSelector.presetBox().show();
    }


    private void importCoaCsvFromFile()
    {
        chooseFile("Import Chart of Accounts CSV", "CSV Files", "*.csv")
                .ifPresent(path -> {
                    ImportExportOrchestrationService.CoaImportResult result = importExportService.importChartOfAccountsCsvFile(path);
                    info("Imported CoA rows: " + result.rowCount() + " from " + path.getFileName());
                });
    }

    private void importBankEnvelopeFromFile()
    {
        chooseFile("Import Bank OFX/QFX", "Bank Statement Files", "*.ofx", "*.qfx")
                .ifPresent(path -> {
                    ImportExportOrchestrationService.BankImportResult result = importExportService.importBankDataFile(path);
                    info("Imported " + result.format() + " transactions: " + result.transactionCount() + " from " + path.getFileName());
                });
    }

    private Optional<Path> chooseFile(String title, String extensionDescription, String... extensions)
    {
        if (getScene() == null || getScene().getWindow() == null)
        {
            info("Import unavailable: window is not ready.");
            return Optional.empty();
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(extensionDescription, extensions));
        File selected = chooser.showOpenDialog(getScene().getWindow());
        if (selected == null)
        {
            return Optional.empty();
        }
        return Optional.of(selected.toPath());
    }


    private void exportDataFromFileMenu()
    {
        chooseSaveFile("Export Data", "Supported Export Files", "*.csv", "*.ofx", "*.qfx")
                .ifPresent(this::exportByExtension);
    }

    private void exportByExtension(Path path)
    {
        String file = path.getFileName().toString().toLowerCase();
        if (file.endsWith(".csv"))
        {
            importExportService.exportChartOfAccountsCsvFile(List.of(
                    new CoaCsvMapper.CoaCsvRow("1000", "Operating Bank", "ASSET", "DEBIT", ""),
                    new CoaCsvMapper.CoaCsvRow("1100", "Accounts Receivable", "ASSET", "DEBIT", "1000")), path);
            info("Exported CoA CSV to " + path.getFileName());
            return;
        }
        if (file.endsWith(".ofx") || file.endsWith(".qfx"))
        {
            BankingDataFormat format = file.endsWith(".qfx")
                    ? BankingDataFormat.QFX
                    : BankingDataFormat.OFX;
            importExportService.exportBankDataFile(format, List.of(
                    new BankTransactionRecord("FIT-1", "20260313000000", new BigDecimal("-25.00"), "DEBIT", "Stationery Shop", "Paper"),
                    new BankTransactionRecord("FIT-2", "20260314000000", new BigDecimal("100.00"), "CREDIT", "Donation", "Member gift")), path);
            info("Exported " + format + " bank statement to " + path.getFileName());
            return;
        }

        info("Export cancelled: unsupported extension for " + path.getFileName());
    }

    private Optional<Path> chooseSaveFile(String title, String extensionDescription, String... extensions)
    {
        if (getScene() == null || getScene().getWindow() == null)
        {
            info("Export unavailable: window is not ready.");
            return Optional.empty();
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(extensionDescription, extensions));
        File selected = chooser.showSaveDialog(getScene().getWindow());
        if (selected == null)
        {
            return Optional.empty();
        }
        return Optional.of(selected.toPath());
    }

    private void selectDatabaseFile()
    {
        chooseFile("Select Database File", "Database Files", "*.mv.db", "*.db")
                .ifPresent(path -> {
                    String selected = path.toString();
                    List<String> recents = new java.util.ArrayList<>(SESSION_STATE.databaseSelection().recentDatabasePaths());
                    recents.remove(selected);
                    recents.add(0, selected);
                    SESSION_STATE.setDatabaseSelection(new DatabaseSelectionState(selected, recents));
                    info("Database file selected: " + path.getFileName() + " (restart required for runtime datasource switch)");
                });
    }

    void selectTheme(UiThemePreference themePreference)
    {
        AppPreferencesState current = SESSION_STATE.preferences();
        SESSION_STATE.setPreferences(new AppPreferencesState(
                themePreference,
                current.useNativeWindowDecorations(),
                current.rememberWindowState(),
                current.defaultPrivilege()));
        info("Applied theme: " + themePreference);
    }

    private MenuItem item(String text, String accel, Runnable action)
    {
        MenuItem mi = new MenuItem(text);
        if (accel != null)
        {
            mi.setAccelerator(KeyCombination.keyCombination(accel));
        }
        mi.setOnAction(e -> action.run());
        return mi;
    }

    void applyPreferences(AppPreferencesState state)
    {
        getStyleClass().removeAll("theme-light", "theme-dark", "theme-system", "native-window-enabled", "native-window-disabled");
        if (state.themePreference() == UiThemePreference.DARK)
        {
            getStyleClass().add("theme-dark");
        }
        else if (state.themePreference() == UiThemePreference.LIGHT)
        {
            getStyleClass().add("theme-light");
        }
        else
        {
            getStyleClass().add("theme-system");
        }

        getStyleClass().add(state.useNativeWindowDecorations() ? "native-window-enabled" : "native-window-disabled");
    }

    void applyMultiCompany(MultiCompanyState state)
    {
        if (activeCompanyLabel != null)
        {
            activeCompanyLabel.setText("Company: " + state.activeCompanyCode());
        }
    }

    void applyDatabaseSelection(DatabaseSelectionState state)
    {
        if (activeDatabaseLabel != null)
        {
            activeDatabaseLabel.setText("DB: " + Path.of(state.activeDatabasePath()).getFileName());
        }
    }

    String activeCompanyCode()
    {
        return SESSION_STATE.multiCompany().activeCompanyCode();
    }

    String activeDatabasePath()
    {
        return SESSION_STATE.databaseSelection().activeDatabasePath();
    }

    boolean usesNativeDecorationsFlag()
    {
        return getStyleClass().contains("native-window-enabled");
    }

    boolean usesDarkThemeFlag()
    {
        return getStyleClass().contains("theme-dark");
    }

    // --- hooks ---
    public void openPanel(AppPanelId id)
    {
        panelHost.show(id);
        nav.highlight(id);
        if (activePanelLabel != null)
        {
            activePanelLabel.setText("Panel: " + panelHost.getActiveTitle());
        }
    }

    public void openInspectorForSelection(String title, String body)
    {
        inspectorPane.show(title, body);
    }

    public void closeInspector()
    {
        inspectorPane.clear();
    }

    public void saveActivePanel()
    {
        panelHost.saveActive();
        stateStore.savePreferences(SESSION_STATE.preferences());
        stateStore.saveMultiCompany(SESSION_STATE.multiCompany());
        stateStore.saveDatabaseSelection(SESSION_STATE.databaseSelection());
        info("Save: " + panelHost.getActiveTitle());
    }

    public void newItemInActivePanel()
    {
        panelHost.newItemActive();
    }

    public void copySelection()
    {
        panelHost.copySelectionActive();
    }

    public void paste()
    {
        panelHost.pasteActive();
    }

    public void openSearch()
    {
        inspectorPane.show("Search", "Search UI placeholder.\n\n(We’ll decide whether this is a modal dialog or a side pane.)");
    }

    public void openInspectorJournal()
    {
        inspectorPane.show("Journal View", "Journal drawer placeholder.\n\nFrom any panel, this should show derived DR/CR lines for the current selection.");
    }

    private void info(String msg)
    {
        inspectorPane.show("Info", msg);
    }
}
