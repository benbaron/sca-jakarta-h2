package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
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
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;
import org.nonprofitbookkeeping.model.ViewPresetState;
import org.nonprofitbookkeeping.service.BankTransactionRecord;
import org.nonprofitbookkeeping.service.CoaCsvMapper;
import org.nonprofitbookkeeping.service.ImportExportOrchestrationService;
import org.nonprofitbookkeeping.service.JournalLine;
import org.nonprofitbookkeeping.service.LedgerQueryService;

import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
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
    private final NavigationPane nav = new NavigationPane(this::openPanel, this::openInspectorForSelection, this::navigationInspectorContext);
    private DateRangeSelector dateRangeSelector;
    private Label activePanelLabel;
    private Label activeCompanyLabel;
    private Label activeDatabaseLabel;
    private Label authStatusLabel;
    private List<CoaCsvMapper.CoaCsvRow> lastImportedCoaRows = List.of();
    private List<BankTransactionRecord> lastImportedBankTransactions = List.of();
    private final Map<String, ViewPreset> viewPresets = new LinkedHashMap<>();
    private final Map<MenuItem, UserPrivilegeLevel> gatedMenuItems = new LinkedHashMap<>();
    private final Map<ButtonBase, UserPrivilegeLevel> gatedButtons = new LinkedHashMap<>();

    public MainWindow()
    {
        this(defaultStateStore());
    }

    MainWindow(AppStateStore stateStore)
    {
        this.stateStore = stateStore;

        restoreState();

        setTop(buildTopChrome());
        SplitPane shellPanes = new SplitPane(nav, panelHost, inspectorPane);
        shellPanes.setDividerPositions(0.20, 0.78);
        setCenter(shellPanes);

        BorderPane.setMargin(shellPanes, new Insets(8));

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
                item("Open…", null, () -> openPanel(AppPanelId.DASHBOARD)),
                item("Database Wizard…", null, this::openDatabaseWizard),
                item("Select Database File…", null, this::selectDatabaseFile),
                item("Create New Database…", null, this::createNewDatabase),
                new SeparatorMenuItem(),
                item("Save", "Ctrl+S", this::saveActivePanel),
                item("Export…", null, this::exportDataFromFileMenu),
                new SeparatorMenuItem(),
                item("Exit", null, () -> System.exit(0))
        );

        Menu edit = new Menu("Edit");
        edit.getItems().addAll(
                disabledItem("Undo", "Ctrl+Z"),
                disabledItem("Redo", "Ctrl+Y"),
                new SeparatorMenuItem(),
                disabledItem("Cut", "Ctrl+X"),
                item("Copy", "Ctrl+C", this::copySelection),
                item("Paste", "Ctrl+V", this::paste)
        );

        Menu search = new Menu("Search");
        search.getItems().addAll(
                item("Find…", "Ctrl+F", this::openSearch),
                item("Command Palette…", "Ctrl+K", this::openCommandPalette),
                item("Go to…", "Ctrl+G", this::openCommandPalette),
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
                item("Post / Validate", null, this::runPostValidate),
                item("Recalculate summaries", null, this::recalculateSummaries)
        );

        Menu tools = new Menu("Tools");
        tools.getItems().addAll(
                item("Import CoA CSV…", null, this::importCoaCsvFromFile),
                item("Import Bank OFX/QFX…", null, this::importBankEnvelopeFromFile),
                item("Import Preview…", null, () -> openPanel(AppPanelId.IMPORT_PREVIEW)),
                item("Import / Export Jobs…", null, () -> openPanel(AppPanelId.IMPORT_EXPORT_JOBS)),
                item("Bank Transactions…", null, () -> openPanel(AppPanelId.BANK_TRANSACTIONS)),
                gatedItem("Approval Audit…", null, () -> openPanel(AppPanelId.APPROVAL_AUDIT), UserPrivilegeLevel.MANAGER),
                gatedItem("Diagnostics…", null, () -> openPanel(AppPanelId.DIAGNOSTICS), UserPrivilegeLevel.ADMIN),
                gatedItem("Preferences…", null, () -> openPanel(AppPanelId.SETTINGS), UserPrivilegeLevel.ADMIN)
        );

        Menu account = new Menu("Account");
        account.getItems().addAll(
                item("Set Password…", null, this::setSessionPassword),
                item("Log In…", null, this::login),
                item("Log Out", null, this::logout),
                item("Company Wizard…", null, this::openCompanyWizard),
                item("Add Company…", null, this::addNewCompany)
        );

        Menu help = new Menu("Help");
        help.getItems().addAll(
                item("Help Topics", null, () -> openPanel(AppPanelId.HELP)),
                item("About", null, () -> info("SCA Ledger (H2 + Jakarta): operational workspace shell with panel run contracts and cross-panel inspectors."))
        );

        return new MenuBar(file, edit, search, view, run, tools, account, help);
    }

    private ToolBar buildToolBar()
    {
        Button btnNew = new Button("New");
        btnNew.setOnAction(e -> newItemInActivePanel());
        gate(btnNew, UserPrivilegeLevel.ACCOUNTANT);

        Button btnSave = new Button("Save");
        btnSave.setOnAction(e -> saveActivePanel());
        gate(btnSave, UserPrivilegeLevel.ACCOUNTANT);

        Button btnFind = new Button("Find");
        btnFind.setOnAction(e -> openSearch());
        gate(btnFind, UserPrivilegeLevel.ACCOUNTANT);

        Button btnJournal = new Button("Journal");
        btnJournal.setOnAction(e -> openInspectorJournal());
        gate(btnJournal, UserPrivilegeLevel.ACCOUNTANT);

        DateRangeSelector dr = new DateRangeSelector();
        this.dateRangeSelector = dr;

        activePanelLabel = new Label("Panel: (none)");
        activePanelLabel.getStyleClass().add("toolbar-active-panel");

        activeCompanyLabel = new Label("Company: " + SESSION_STATE.multiCompany().activeCompanyCode());
        activeCompanyLabel.getStyleClass().add("toolbar-active-panel");

        activeDatabaseLabel = new Label("DB: " + Path.of(SESSION_STATE.databaseSelection().activeDatabasePath()).getFileName());
        activeDatabaseLabel.getStyleClass().add("toolbar-active-panel");

        authStatusLabel = new Label(SESSION_STATE.isLoggedIn() ? "Auth: logged in" : "Auth: logged out");
        authStatusLabel.getStyleClass().add("toolbar-active-panel");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToolBar tb = new ToolBar(btnNew, btnSave, new Separator(), btnFind, new Separator(), btnJournal,
                new Separator(), dr, spacer, authStatusLabel, new Separator(), activeDatabaseLabel, new Separator(), activeCompanyLabel, new Separator(), activePanelLabel);
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


    private NavigationPane.InspectorContext navigationInspectorContext()
    {
        AppPanelId activeId = panelHost.activePanelId();
        String capabilities = activeId == null ? "(no active panel)" : panelCapabilities(activeId);
        return new NavigationPane.InspectorContext(
                SESSION_STATE.multiCompany().activeCompanyCode(),
                String.valueOf(DateRangeContext.get()),
                capabilities);
    }

    static String panelCapabilities(AppPanelId id)
    {
        return switch (id)
        {
            case TXN_EDITOR -> "Save, New line edits, Post/Validate run command, Journal preview";
            case LEDGER_REGISTER -> "Refresh, inspect journal, expose active journal selection";
            case IMPORT_PREVIEW -> "Import review and preview workflow";
            case APPROVAL_AUDIT -> "Audit filters by workflow/decision/actor/date; run-id visibility";
            case IMPORT_EXPORT_JOBS -> "Unified import/export job history and error tracking";
            case BANK_TRANSACTIONS -> "Imported bank transactions, drill to ledger, export selected";
            case SETTINGS -> "Preferences management";
            case DIAGNOSTICS -> "Health checks and duplicate-code diagnostics";
            default -> "Open panel, inspect context, panel-local actions";
        };
    }

    static UserPrivilegeLevel requiredPrivilegeForPanel(AppPanelId id)
    {
        return switch (id)
        {
            case APPROVAL_AUDIT, PERIOD_CLOSE_RUNS -> UserPrivilegeLevel.MANAGER;
            case SETTINGS, DIAGNOSTICS -> UserPrivilegeLevel.ADMIN;
            default -> UserPrivilegeLevel.ACCOUNTANT;
        };
    }

    static boolean canAccessPanelForPrivilege(AppPanelId id, UserPrivilegeLevel privilege)
    {
        return privilege.ordinal() >= requiredPrivilegeForPanel(id).ordinal();
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
            case APPROVAL_AUDIT -> "Approval Audit";
            case IMPORT_EXPORT_JOBS -> "Import / Export Jobs";
            case BANK_TRANSACTIONS -> "Bank Transactions";
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
                    try
                    {
                        ImportExportOrchestrationService.CoaImportResult result = importExportService.importChartOfAccountsCsvFile(path);
                        lastImportedCoaRows = List.copyOf(result.rows());
                        UiWorkspaceDataStore.appendJob(new UiWorkspaceDataStore.ImportExportJob(
                                java.time.LocalDateTime.now(),
                                "IMPORT_COA",
                                path.toString(),
                                "",
                                null,
                                result.rowCount(),
                                0,
                                "SUCCESS",
                                ""));
                        info("Imported CoA rows: " + result.rowCount() + " from " + path.getFileName());
                    }
                    catch (RuntimeException ex)
                    {
                        UiWorkspaceDataStore.appendJob(new UiWorkspaceDataStore.ImportExportJob(
                                java.time.LocalDateTime.now(),
                                "IMPORT_COA",
                                path.toString(),
                                "",
                                null,
                                0,
                                0,
                                "FAILED",
                                UiErrors.safeMessage(ex)));
                        info("Import failed for CoA CSV " + path.getFileName() + ": " + UiErrors.safeMessage(ex));
                    }
                });
    }

    private void importBankEnvelopeFromFile()
    {
        chooseFile("Import Bank OFX/QFX", "Bank Statement Files", "*.ofx", "*.qfx")
                .ifPresent(path -> {
                    try
                    {
                        ImportExportOrchestrationService.BankImportResult result = importExportService.importBankDataFile(path);
                        lastImportedBankTransactions = List.copyOf(result.transactions());
                        UiWorkspaceDataStore.replaceBankTransactions(lastImportedBankTransactions);
                        UiWorkspaceDataStore.appendJob(new UiWorkspaceDataStore.ImportExportJob(
                                java.time.LocalDateTime.now(),
                                "IMPORT_BANK",
                                path.toString(),
                                "",
                                result.format(),
                                0,
                                result.transactionCount(),
                                "SUCCESS",
                                ""));
                        info("Imported " + result.format() + " transactions: " + result.transactionCount() + " from " + path.getFileName());
                    }
                    catch (RuntimeException ex)
                    {
                        UiWorkspaceDataStore.appendJob(new UiWorkspaceDataStore.ImportExportJob(
                                java.time.LocalDateTime.now(),
                                "IMPORT_BANK",
                                path.toString(),
                                "",
                                null,
                                0,
                                0,
                                "FAILED",
                                UiErrors.safeMessage(ex)));
                        info("Import failed for bank file " + path.getFileName() + ": " + UiErrors.safeMessage(ex));
                    }
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
        String file = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (file.endsWith(".csv"))
        {
            List<CoaCsvMapper.CoaCsvRow> exportRows = buildCoaExportRows();
            importExportService.exportChartOfAccountsCsvFile(exportRows, path);
            UiWorkspaceDataStore.appendJob(new UiWorkspaceDataStore.ImportExportJob(java.time.LocalDateTime.now(), "EXPORT_COA", "(active chart)", path.toString(), null, exportRows.size(), 0, "SUCCESS", ""));
            info("Exported CoA CSV rows: " + exportRows.size() + " to " + path.getFileName());
            return;
        }
        if (file.endsWith(".ofx") || file.endsWith(".qfx"))
        {
            BankingDataFormat format = file.endsWith(".qfx")
                    ? BankingDataFormat.QFX
                    : BankingDataFormat.OFX;
            importExportService.exportBankDataFile(format, lastImportedBankTransactions, path);
            UiWorkspaceDataStore.appendJob(new UiWorkspaceDataStore.ImportExportJob(java.time.LocalDateTime.now(), "EXPORT_BANK", "(session bank transactions)", path.toString(), format, 0, lastImportedBankTransactions.size(), "SUCCESS", ""));
            info("Exported " + format + " bank statement transactions: " + lastImportedBankTransactions.size() + " to " + path.getFileName());
            return;
        }

        UiWorkspaceDataStore.appendJob(new UiWorkspaceDataStore.ImportExportJob(java.time.LocalDateTime.now(), "EXPORT_UNKNOWN", "", path.toString(), null, 0, 0, "FAILED", "Unsupported extension"));
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


    private void openDatabaseWizard()
    {
        var owner = getScene() == null ? null : getScene().getWindow();
        DatabaseWizardDialog.show(owner).ifPresent(result -> {
            if (result.action() == DatabaseWizardDialog.Action.CREATE_NEW)
            {
                applySelectedDatabasePath(result.databasePath());
                initializeSampleCompany();
                info("Database wizard: created database and initialized sample company.");
            }
            else
            {
                applySelectedDatabasePath(result.databasePath());
                info("Database wizard: switched active database.");
            }
        });
    }

    private void openCompanyWizard()
    {
        var owner = getScene() == null ? null : getScene().getWindow();
        String current = SESSION_STATE.multiCompany().activeCompanyCode();
        CompanyWizardDialog.show(owner, current).ifPresent(result -> {
            applyCompanySelection(result.companyCode());
            if (result.action() == CompanyWizardDialog.Action.ADD_COMPANY)
            {
                info("Company wizard: added company " + result.companyCode() + " in current database.");
            }
            else
            {
                info("Company wizard: switched active company to " + result.companyCode() + ".");
            }
        });
    }

    private void selectDatabaseFile()
    {
        chooseFile("Select Database File", "Database Files", "*.mv.db", "*.db")
                .ifPresent(this::applySelectedDatabasePath);
    }

    private void createNewDatabase()
    {
        Optional<Path> target = chooseSaveFile("Create Database", "Database Files", "*.mv.db");
        if (target.isEmpty())
        {
            return;
        }
        Path path = target.get();
        if (!path.toString().endsWith(".mv.db"))
        {
            path = Path.of(path.toString() + ".mv.db");
        }
        applySelectedDatabasePath(path);
        initializeSampleCompany();
        info("Created database and initialized sample company.");
    }

    private void addNewCompany()
    {
        TextInputDialog dialog = new TextInputDialog("NEW-COMPANY");
        dialog.setTitle("Add Company");
        dialog.setHeaderText("Add company code");
        dialog.setContentText("Company:");
        if (getScene() != null && getScene().getWindow() != null)
        {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.showAndWait().ifPresent(code -> {
            String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
            if (normalized.isBlank())
            {
                info("Company add cancelled: blank code.");
                return;
            }
            applyCompanySelection(normalized);
            info("Added company: " + normalized + " in current database.");
        });
    }

    private void setSessionPassword()
    {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Set Password");
        dialog.setHeaderText("Set session password");
        dialog.setContentText("Password:");
        if (getScene() != null && getScene().getWindow() != null)
        {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.showAndWait().ifPresent(value -> {
            SESSION_STATE.setPassword(value);
            refreshAuthStatus();
            info(SESSION_STATE.hasPassword() ? "Session password set." : "Session password cleared.");
        });
    }

    private void login()
    {
        if (!SESSION_STATE.hasPassword())
        {
            SESSION_STATE.login("");
            refreshAuthStatus();
            info("Login complete (no password set).");
            return;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Log In");
        dialog.setHeaderText("Enter password");
        PasswordField field = new PasswordField();
        dialog.getDialogPane().setContent(field);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (getScene() != null && getScene().getWindow() != null)
        {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.setResultConverter(bt -> bt == ButtonType.OK ? field.getText() : null);
        dialog.showAndWait().ifPresent(attempt -> {
            boolean ok = SESSION_STATE.login(attempt);
            refreshAuthStatus();
            info(ok ? "Login successful." : "Login failed.");
        });
    }

    private void logout()
    {
        SESSION_STATE.logout();
        refreshAuthStatus();
        info("Logged out.");
    }


    private void applyCompanySelection(String companyCode)
    {
        String normalized = companyCode == null ? "" : companyCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank())
        {
            return;
        }
        List<String> recents = new ArrayList<>(SESSION_STATE.multiCompany().recentCompanyCodes());
        recents.remove(normalized);
        recents.add(0, normalized);
        SESSION_STATE.setMultiCompany(new MultiCompanyState(normalized, recents));
        stateStore.saveMultiCompany(SESSION_STATE.multiCompany());
    }

    private void initializeSampleCompany()
    {
        applyCompanySelection("SAMPLE-CO");
    }

    private void refreshAuthStatus()
    {
        if (authStatusLabel != null)
        {
            authStatusLabel.setText(SESSION_STATE.isLoggedIn() ? "Auth: logged in" : "Auth: logged out");
        }
    }

    void applySelectedDatabasePath(Path path)
    {
        String selected = path.toString();
        try
        {
            DatabaseBootstrap.migrate(path);
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

    private MenuItem disabledItem(String text, String accel)
    {
        MenuItem mi = new MenuItem(text + " (disabled)");
        if (accel != null)
        {
            mi.setAccelerator(KeyCombination.keyCombination(accel));
        }
        mi.setDisable(true);
        return mi;
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

    private MenuItem gatedItem(String text, String accel, Runnable action, UserPrivilegeLevel required)
    {
        MenuItem item = item(text, accel, action);
        gatedMenuItems.put(item, required);
        return item;
    }

    private void gate(ButtonBase button, UserPrivilegeLevel required)
    {
        gatedButtons.put(button, required);
    }

    private void refreshPrivilegeGating()
    {
        UserPrivilegeLevel privilege = SESSION_STATE.preferences().defaultPrivilege();
        gatedMenuItems.forEach((item, required) -> item.setDisable(privilege.ordinal() < required.ordinal()));
        gatedButtons.forEach((button, required) -> button.setDisable(privilege.ordinal() < required.ordinal()));
    }


    List<String> menuItemTextsForTests()
    {
        if (!(getTop() instanceof VBox vbox) || vbox.getChildren().isEmpty() || !(vbox.getChildren().get(0) instanceof MenuBar menuBar))
        {
            return List.of();
        }
        List<String> texts = new ArrayList<>();
        for (Menu menu : menuBar.getMenus())
        {
            for (MenuItem item : menu.getItems())
            {
                if (item.getText() != null)
                {
                    texts.add(item.getText());
                }
            }
        }
        return texts;
    }

    double[] shellDividerPositionsForTests()
    {
        if (getCenter() instanceof SplitPane splitPane)
        {
            return splitPane.getDividerPositions();
        }
        return new double[0];
    }

    String authStatusTextForTests()
    {
        return authStatusLabel == null ? "" : authStatusLabel.getText();
    }

    Map<String, Boolean> gatedToolItemDisabledStatesForTests()
    {
        Map<String, Boolean> states = new LinkedHashMap<>();
        gatedMenuItems.forEach((item, required) -> states.put(item.getText(), item.isDisable()));
        return states;
    }

    Map<String, Boolean> gatedToolbarDisabledStatesForTests()
    {
        Map<String, Boolean> states = new LinkedHashMap<>();
        gatedButtons.forEach((button, required) -> states.put(button.getText(), button.isDisable()));
        return states;
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
        refreshPrivilegeGating();
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

    PanelHost panelHostForTests()
    {
        return panelHost;
    }

    // --- hooks ---
    public void openPanel(AppPanelId id)
    {
        UserPrivilegeLevel privilege = SESSION_STATE.preferences().defaultPrivilege();
        if (!canAccessPanelForPrivilege(id, privilege))
        {
            info("Access denied: " + panelLabel(id) + " requires " + requiredPrivilegeForPanel(id) + " privilege.");
            return;
        }
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
        openSearch("");
    }

    public void openSearch(String query)
    {
        String normalized = normalizeSearchQuery(query);
        inspectorPane.show("Search", "Running workspace search for query: " + (normalized.isBlank() ? "(all)" : normalized));
        UiAsync.run("search-query", () -> buildSearchResults(normalized),
                body -> inspectorPane.show("Search", body),
                ex -> inspectorPane.show("Search", "Could not run search: " + UiErrors.safeMessage(ex)));
    }

    public void jumpToPanelFromSearch(AppPanelId panelId)
    {
        openPanel(panelId);
        inspectorPane.show("Search", "Jumped to panel: " + panelLabel(panelId) + " (" + panelId.name() + ")");
    }

    static String normalizeSearchQuery(String query)
    {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    static boolean searchMatches(String normalizedQuery, String... haystacks)
    {
        if (normalizedQuery == null || normalizedQuery.isBlank())
        {
            return true;
        }
        for (String haystack : haystacks)
        {
            if (haystack != null && haystack.toLowerCase(Locale.ROOT).contains(normalizedQuery))
            {
                return true;
            }
        }
        return false;
    }

    String buildSearchResultsForTests(String query)
    {
        return buildSearchResults(normalizeSearchQuery(query));
    }

    private String buildSearchResults(String normalizedQuery)
    {
        List<AppPanelId> panelMatches = commandPaletteEntriesForTests().stream()
                .filter(e -> searchMatches(normalizedQuery, e.label(), e.panelId().name()))
                .map(PaletteEntry::panelId)
                .toList();

        List<String> accountMatches = UiServiceRegistry.accountLookup()
                .listActivePostingAccounts()
                .stream()
                .map(a -> a.getCode() + " — " + a.getName())
                .filter(row -> searchMatches(normalizedQuery, row))
                .limit(20)
                .toList();

        List<String> fundMatches = UiServiceRegistry.fundLookup()
                .listActiveFunds()
                .stream()
                .map(f -> f.getCode() + " — " + f.getName())
                .filter(row -> searchMatches(normalizedQuery, row))
                .limit(20)
                .toList();

        StringBuilder body = new StringBuilder();
        body.append("Search Results\n");
        body.append("Query: ").append(normalizedQuery.isBlank() ? "(all)" : normalizedQuery).append("\n");
        body.append("Active company: ").append(SESSION_STATE.multiCompany().activeCompanyCode()).append("\n");
        body.append("Active panel: ").append(panelHost.getActiveTitle()).append("\n\n");

        body.append("Panels (jump using Go to… or jumpToPanelFromSearch):\n");
        if (panelMatches.isEmpty())
        {
            body.append("- none\n");
        }
        else
        {
            panelMatches.forEach(id -> body.append("- ").append(panelLabel(id)).append(" [").append(id.name()).append("]\n"));
        }

        body.append("\nAccounts:\n");
        if (accountMatches.isEmpty())
        {
            body.append("- none\n");
        }
        else
        {
            accountMatches.forEach(code -> body.append("- ").append(code).append("\n"));
        }

        body.append("\nFunds:\n");
        if (fundMatches.isEmpty())
        {
            body.append("- none\n");
        }
        else
        {
            fundMatches.forEach(code -> body.append("- ").append(code).append("\n"));
        }

        return body.toString();
    }

    public void openInspectorJournal()
    {
        inspectorPane.show("Journal View", "Loading journal context...");
        UiAsync.run("journal-inspector", this::buildJournalInspectorPreview,
                body -> inspectorPane.show("Journal View", body),
                ex -> inspectorPane.show("Journal View", "Could not load journal preview: " + UiErrors.safeMessage(ex)));
    }

    String buildJournalInspectorPreviewForTests()
    {
        return buildJournalInspectorPreview();
    }

    private String buildJournalInspectorPreview()
    {
        return buildJournalInspectorPreview(panelHost.activeJournalSelection());
    }

    String buildJournalInspectorPreview(Optional<AppPanel.JournalSelection> selected)
    {
        if (selected.isPresent())
        {
            return buildJournalPreviewForTransaction(selected.get().txnId(), "Active selection: " + selected.get().sourceLabel());
        }
        return buildRecentJournalPreview();
    }

    private String buildRecentJournalPreview()
    {
        LedgerQueryService ledger = UiServiceRegistry.ledgerQuery();
        List<LedgerQueryService.LedgerRow> recent = ledger.listRecent(1);
        if (recent.isEmpty())
        {
            return "No posted transactions found for journal preview.";
        }

        LedgerQueryService.LedgerRow row = recent.get(0);
        List<JournalLine> lines = ledger.journalForTxn(row.id());

        StringBuilder body = new StringBuilder();
        body.append("Most Recent Transaction\n")
                .append("Txn #").append(row.id())
                .append(" on ").append(row.date())
                .append(" (splits: ").append(row.splitCount()).append(")\n")
                .append("Payee: ").append(row.payee() == null ? "" : row.payee()).append("\n")
                .append("Memo: ").append(row.memo() == null ? "" : row.memo()).append("\n\n")
                .append("Journal lines:\n");

        if (lines.isEmpty())
        {
            body.append("- none");
            return body.toString();
        }

        lines.forEach(line -> body.append("- ")
                .append(line.getAccountCode()).append("/").append(line.getFundCode() == null ? "" : line.getFundCode())
                .append(" DR=").append(line.getDebit().toPlainString())
                .append(" CR=").append(line.getCredit().toPlainString())
                .append("\n"));
        return body.toString();
    }

    private String buildJournalPreviewForTransaction(long transactionId, String heading)
    {
        LedgerQueryService ledger = UiServiceRegistry.ledgerQuery();
        List<LedgerQueryService.LedgerRow> recent = ledger.listRecent(250);
        Optional<LedgerQueryService.LedgerRow> match = recent.stream().filter(r -> r.id() == transactionId).findFirst();
        if (match.isEmpty())
        {
            return heading + "\nTransaction #" + transactionId + " not found in recent ledger rows.";
        }

        LedgerQueryService.LedgerRow row = match.get();
        List<JournalLine> lines = ledger.journalForTxn(row.id());

        StringBuilder body = new StringBuilder();
        body.append(heading).append("\n")
                .append("Txn #").append(row.id())
                .append(" on ").append(row.date())
                .append(" (splits: ").append(row.splitCount()).append(")\n")
                .append("Payee: ").append(row.payee() == null ? "" : row.payee()).append("\n")
                .append("Memo: ").append(row.memo() == null ? "" : row.memo()).append("\n\n")
                .append("Journal lines:\n");

        if (lines.isEmpty())
        {
            body.append("- none");
            return body.toString();
        }

        lines.forEach(line -> body.append("- ")
                .append(line.getAccountCode()).append("/").append(line.getFundCode() == null ? "" : line.getFundCode())
                .append(" DR=").append(line.getDebit().toPlainString())
                .append(" CR=").append(line.getCredit().toPlainString())
                .append("\n"));
        return body.toString();
    }

    private void runPostValidate()
    {
        AppPanel.RunCommandResult result = panelHost.runCommandActive(AppPanel.RunCommand.POST_VALIDATE);
        info(result.message());
    }

    private void recalculateSummaries()
    {
        info("Recalculating dashboard/report summaries...");
        UiAsync.run("recalculate-summaries", () -> UiServiceRegistry.fundBalance().balancesAsOf(LocalDate.now()).size(),
                count -> info("Recalculated summaries from " + count + " fund balance row(s)."),
                ex -> info("Recalculate failed: " + UiErrors.safeMessage(ex)));
    }

    private void info(String msg)
    {
        inspectorPane.show("Info", msg);
    }
}
