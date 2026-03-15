package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TextInputDialog;
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
import org.nonprofitbookkeeping.model.ViewPresetState;
import org.nonprofitbookkeeping.service.BankTransactionRecord;
import org.nonprofitbookkeeping.service.CoaCsvMapper;
import org.nonprofitbookkeeping.service.ImportExportOrchestrationService;

import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private List<CoaCsvMapper.CoaCsvRow> lastImportedCoaRows = List.of();
    private List<BankTransactionRecord> lastImportedBankTransactions = List.of();
    private final Map<String, ViewPreset> viewPresets = new LinkedHashMap<>();

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

        DrillThroughCoordinator.configureOpener(this::openPanel);
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
        loadViewPresetsFromStore();
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
                item("Command Palette…", "Ctrl+K", this::openCommandPalette),
                item("Go to…", "Ctrl+G", () -> info("Go to not wired yet.")),
                new SeparatorMenuItem(),
                item("Date Range…", null, this::focusDateRangeSelector)
        );

        Menu view = new Menu("View");
        view.getItems().addAll(
                item("Theme: Light", null, () -> selectTheme(UiThemePreference.LIGHT)),
                item("Theme: Dark", null, () -> selectTheme(UiThemePreference.DARK)),
                item("Theme: System", null, () -> selectTheme(UiThemePreference.SYSTEM_DEFAULT)),
                new SeparatorMenuItem(),
                item("Save View Preset…", null, this::openSaveViewPresetDialog),
                item("Apply View Preset…", null, this::openApplyViewPresetDialog),
                item("Delete View Preset…", null, this::openDeleteViewPresetDialog)
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
                item("Import Preview…", null, () -> openPanel(AppPanelId.IMPORT_PREVIEW)),
                item("Diagnostics…", null, () -> openPanel(AppPanelId.DIAGNOSTICS)),
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

    void openCommandPalette()
    {
        if (getScene() == null || getScene().getWindow() == null)
        {
            info("Command palette unavailable: window is not ready.");
            return;
        }

        List<PaletteEntry> entries = commandPaletteEntriesForTests();
        ChoiceDialog<PaletteEntry> dialog = new ChoiceDialog<>(entries.get(0), entries);
        dialog.setTitle("Command Palette");
        dialog.setHeaderText("Jump to workspace");
        dialog.setContentText("Command:");
        dialog.initOwner(getScene().getWindow());

        dialog.showAndWait().ifPresent(entry -> openPanel(entry.panelId()));
    }

    static List<PaletteEntry> commandPaletteEntriesForTests()
    {
        List<PaletteEntry> entries = new ArrayList<>();
        for (AppPanelId id : AppPanelId.values())
        {
            entries.add(new PaletteEntry(id, panelLabel(id)));
        }
        entries.sort(Comparator.comparing(PaletteEntry::label));
        return entries;
    }

    private static String panelLabel(AppPanelId id)
    {
        return switch (id)
        {
            case DASHBOARD -> "Dashboard";
            case LEDGER_REGISTER -> "Ledger Register";
            case TXN_EDITOR -> "Transaction Editor";
            case SCHEDULES -> "Outstanding / Schedules";
            case BUDGET_EDITOR -> "Budget Editor";
            case BUDGET_VS_ACTUAL -> "Budget vs Actual";
            case ASSETS_REGISTER -> "Asset Register";
            case DEPRECIATION_RUNS -> "Depreciation Runs";
            case INVENTORY -> "Inventory";
            case RECONCILIATION_RUNS -> "Reconciliation Runs";
            case PERIOD_CLOSE_RUNS -> "Period Close Runs";
            case IMPORT_PREVIEW -> "Import Preview";
            case REPORT_LIBRARY -> "Reports Library";
            case CHART_OF_ACCOUNTS -> "Chart of Accounts";
            case FUNDS -> "Funds";
            case SETTINGS -> "Settings";
            case DIAGNOSTICS -> "Diagnostics";
            case HELP -> "Help";
        };
    }

    void saveViewPresetForTests(String presetName)
    {
        String key = normalizePresetName(presetName);
        AppPanelId panelId = panelHost.activePanelId() == null ? AppPanelId.DASHBOARD : panelHost.activePanelId();
        viewPresets.put(key, new ViewPreset(panelId, DateRangeContext.get()));
    }

    void applyViewPresetForTests(String presetName)
    {
        String key = normalizePresetName(presetName);
        ViewPreset preset = viewPresets.get(key);
        if (preset == null)
        {
            throw new IllegalArgumentException("Unknown view preset: " + key);
        }
        DateRangeContext.set(preset.dateRange());
        openPanel(preset.panelId());
    }

    List<String> viewPresetNamesForTests()
    {
        return new ArrayList<>(viewPresets.keySet());
    }

    void removeViewPresetForTests(String presetName)
    {
        String key = normalizePresetName(presetName);
        viewPresets.remove(key);
    }


    private void loadViewPresetsFromStore()
    {
        viewPresets.clear();
        for (ViewPresetState state : stateStore.loadViewPresets())
        {
            try
            {
                String key = normalizePresetName(state.name());
                AppPanelId panelId = AppPanelId.valueOf(state.panelId());
                DateRange range = parseDateRange(state.startDateIso(), state.endDateIso());
                viewPresets.put(key, new ViewPreset(panelId, range));
            }
            catch (RuntimeException ignored)
            {
                // Skip invalid persisted preset rows defensively.
            }
        }
    }

    private List<ViewPresetState> viewPresetStatesForPersistence()
    {
        List<ViewPresetState> out = new ArrayList<>();
        for (Map.Entry<String, ViewPreset> e : viewPresets.entrySet())
        {
            DateRange range = e.getValue().dateRange();
            out.add(new ViewPresetState(
                    e.getKey(),
                    e.getValue().panelId().name(),
                    range.startInclusive() == null ? "" : range.startInclusive().toString(),
                    range.endInclusive() == null ? "" : range.endInclusive().toString()));
        }
        return out;
    }

    private static DateRange parseDateRange(String startIso, String endIso)
    {
        LocalDate start = (startIso == null || startIso.isBlank()) ? null : LocalDate.parse(startIso);
        LocalDate end = (endIso == null || endIso.isBlank()) ? null : LocalDate.parse(endIso);
        return new DateRange(start, end);
    }

    private void openSaveViewPresetDialog()
    {
        if (getScene() == null || getScene().getWindow() == null)
        {
            info("Save preset unavailable: window is not ready.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog("My View");
        dialog.setTitle("Save View Preset");
        dialog.setHeaderText("Save current panel and date range");
        dialog.setContentText("Preset name:");
        dialog.initOwner(getScene().getWindow());
        dialog.showAndWait().ifPresent(name -> {
            saveViewPresetForTests(name);
            info("Saved view preset: " + normalizePresetName(name));
        });
    }

    private void openApplyViewPresetDialog()
    {
        if (getScene() == null || getScene().getWindow() == null)
        {
            info("Apply preset unavailable: window is not ready.");
            return;
        }
        if (viewPresets.isEmpty())
        {
            info("No saved view presets yet.");
            return;
        }
        List<String> names = new ArrayList<>(viewPresets.keySet());
        ChoiceDialog<String> dialog = new ChoiceDialog<>(names.get(0), names);
        dialog.setTitle("Apply View Preset");
        dialog.setHeaderText("Restore panel and date range");
        dialog.setContentText("Preset:");
        dialog.initOwner(getScene().getWindow());
        dialog.showAndWait().ifPresent(this::applyViewPresetForTests);
    }

    private void openDeleteViewPresetDialog()
    {
        if (getScene() == null || getScene().getWindow() == null)
        {
            info("Delete preset unavailable: window is not ready.");
            return;
        }
        if (viewPresets.isEmpty())
        {
            info("No saved view presets to delete.");
            return;
        }
        List<String> names = new ArrayList<>(viewPresets.keySet());
        ChoiceDialog<String> dialog = new ChoiceDialog<>(names.get(0), names);
        dialog.setTitle("Delete View Preset");
        dialog.setHeaderText("Remove a saved preset");
        dialog.setContentText("Preset:");
        dialog.initOwner(getScene().getWindow());
        dialog.showAndWait().ifPresent(name -> {
            removeViewPresetForTests(name);
            info("Deleted view preset: " + normalizePresetName(name));
        });
    }

    private static String normalizePresetName(String presetName)
    {
        if (presetName == null || presetName.isBlank())
        {
            throw new IllegalArgumentException("Preset name is required.");
        }
        return presetName.trim();
    }

    private record ViewPreset(AppPanelId panelId, DateRange dateRange)
    {
    }

    record PaletteEntry(AppPanelId panelId, String label)
    {
        @Override
        public String toString()
        {
            return label;
        }
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
                    lastImportedCoaRows = List.copyOf(result.rows());
                    info("Imported CoA rows: " + result.rowCount() + " from " + path.getFileName());
                });
    }

    private void importBankEnvelopeFromFile()
    {
        chooseFile("Import Bank OFX/QFX", "Bank Statement Files", "*.ofx", "*.qfx")
                .ifPresent(path -> {
                    ImportExportOrchestrationService.BankImportResult result = importExportService.importBankDataFile(path);
                    lastImportedBankTransactions = List.copyOf(result.transactions());
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
            List<CoaCsvMapper.CoaCsvRow> exportRows = buildCoaExportRows();
            importExportService.exportChartOfAccountsCsvFile(exportRows, path);
            info("Exported CoA CSV rows: " + exportRows.size() + " to " + path.getFileName());
            return;
        }
        if (file.endsWith(".ofx") || file.endsWith(".qfx"))
        {
            BankingDataFormat format = file.endsWith(".qfx")
                    ? BankingDataFormat.QFX
                    : BankingDataFormat.OFX;
            importExportService.exportBankDataFile(format, lastImportedBankTransactions, path);
            info("Exported " + format + " bank statement transactions: " + lastImportedBankTransactions.size() + " to " + path.getFileName());
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
                .ifPresent(this::applySelectedDatabasePath);
    }

    void applySelectedDatabasePath(Path path)
    {
        String selected = path.toString();
        try
        {
            UiServiceRegistry.reconnectToDatabase(path);
        }
        catch (RuntimeException ex)
        {
            info("Database switch failed for " + path.getFileName() + ": " + ex.getMessage());
            return;
        }

        List<String> recents = new ArrayList<>(SESSION_STATE.databaseSelection().recentDatabasePaths());
        recents.remove(selected);
        recents.add(0, selected);
        SESSION_STATE.setDatabaseSelection(new DatabaseSelectionState(selected, recents));
        info("Database switched to: " + path.getFileName());
    }

    private List<CoaCsvMapper.CoaCsvRow> buildCoaExportRows()
    {
        if (!lastImportedCoaRows.isEmpty())
        {
            return lastImportedCoaRows;
        }

        return UiServiceRegistry.accountLookup()
                .listActivePostingAccounts()
                .stream()
                .map(account -> new CoaCsvMapper.CoaCsvRow(
                        account.getCode(),
                        account.getName(),
                        account.getAccountType().name(),
                        account.getNormalBalance().name(),
                        ""))
                .toList();
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
        stateStore.saveViewPresets(viewPresetStatesForPersistence());
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
