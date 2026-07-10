from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one regex match, found {count}")
    return updated


def patch_registry() -> None:
    path = Path("src/main/java/org/nonprofitbookkeeping/ui/UiServiceRegistry.java")
    text = path.read_text()
    if "CompanyUiPreferencesService companyUiPreferences()" in text:
        return
    text = replace_once(
        text,
        "import org.nonprofitbookkeeping.repository.JdbcPeriodCloseRunRepository;\n",
        "import org.nonprofitbookkeeping.repository.JdbcCompanyUiPreferenceRepository;\n"
        "import org.nonprofitbookkeeping.repository.JdbcPeriodCloseRunRepository;\n",
        "registry repository import")
    text = replace_once(
        text,
        "import org.nonprofitbookkeeping.service.CompanyAdminService;\n",
        "import org.nonprofitbookkeeping.service.CompanyAdminService;\n"
        "import org.nonprofitbookkeeping.service.CompanyUiPreferencesService;\n",
        "registry service import")
    text = replace_once(
        text,
        "    public static CompanyAdminService companyAdmin() { return services().companyAdmin(); }\n",
        "    public static CompanyAdminService companyAdmin() { return services().companyAdmin(); }\n"
        "    public static CompanyUiPreferencesService companyUiPreferences()\n"
        "    {\n"
        "        return new CompanyUiPreferencesService(\n"
        "                new JdbcCompanyUiPreferenceRepository(UiDataSources.forCurrentSessionDatabase()));\n"
        "    }\n",
        "registry accessor")
    path.write_text(text)


def patch_settings() -> None:
    path = Path("src/main/java/org/nonprofitbookkeeping/ui/SettingsPanel.java")
    text = path.read_text()
    if "moneyPrintFormat" in text:
        return
    text = replace_once(text,
        "import javafx.scene.control.TabPane;\n",
        "import javafx.scene.control.TabPane;\nimport javafx.scene.control.TextField;\n",
        "settings TextField import")
    text = replace_once(text,
        "import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;\n",
        "import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;\n"
        "import org.nonprofitbookkeeping.model.CompanyUiPreferences;\n"
        "import org.nonprofitbookkeeping.model.DateDisplayFormat;\n",
        "settings preference imports")
    text = replace_once(text,
        "import org.nonprofitbookkeeping.model.MultiCompanyState;\n",
        "import org.nonprofitbookkeeping.model.MultiCompanyState;\n"
        "import org.nonprofitbookkeeping.model.MoneyPrintFormat;\n",
        "settings money import")
    text = replace_once(text,
        "    private final CheckBox confirmDeletion = new CheckBox(\"Confirm before deleting an entered transaction\");\n",
        "    private final CheckBox confirmDeletion = new CheckBox(\"Confirm before deleting an entered transaction\");\n"
        "    private final TextField currencySymbol = new TextField();\n"
        "    private final ComboBox<MoneyPrintFormat> moneyPrintFormat = new ComboBox<>();\n"
        "    private final ComboBox<DateDisplayFormat> dateDisplayFormat = new ComboBox<>();\n",
        "settings fields")
    text = replace_once(text,
        "        periodStartDay.setEditable(true);\n",
        "        periodStartDay.setEditable(true);\n"
        "        currencySymbol.setPromptText(\"$\");\n"
        "        currencySymbol.setPrefColumnCount(5);\n"
        "        moneyPrintFormat.getItems().setAll(MoneyPrintFormat.values());\n"
        "        dateDisplayFormat.getItems().setAll(DateDisplayFormat.values());\n",
        "settings control setup")
    text = replace_once(text,
        "        activeCompany.getItems().addAll(session.multiCompany().recentCompanyCodes());\n",
        "        activeCompany.getItems().addAll(session.multiCompany().recentCompanyCodes());\n"
        "        activeCompany.setOnAction(event -> loadCompanyUiPreferences(activeCompany.getEditor().getText()));\n",
        "settings company listener")
    text = replace_once(text,
        "        grid.add(new Label(\"Period start day\"), 0, row);\n"
        "        grid.add(periodStartDay, 1, row++);\n\n"
        "        grid.add(confirmDeletion, 0, row++, 2, 1);\n",
        "        grid.add(new Label(\"Period start day\"), 0, row);\n"
        "        grid.add(periodStartDay, 1, row++);\n\n"
        "        grid.add(new Label(\"Money symbol\"), 0, row);\n"
        "        grid.add(currencySymbol, 1, row++);\n\n"
        "        grid.add(new Label(\"Money print format\"), 0, row);\n"
        "        grid.add(moneyPrintFormat, 1, row++);\n\n"
        "        grid.add(new Label(\"Date display format\"), 0, row);\n"
        "        grid.add(dateDisplayFormat, 1, row++);\n\n"
        "        grid.add(confirmDeletion, 0, row++, 2, 1);\n",
        "settings display preference rows")
    text = replace_once(text,
        "        activeCompany.getSelectionModel().select(c.activeCompanyCode());\n",
        "        activeCompany.getSelectionModel().select(c.activeCompanyCode());\n"
        "        loadCompanyUiPreferences(c.activeCompanyCode());\n",
        "settings preference load")
    text = replace_once(text,
        "        session.setDatabaseSelection(readDatabaseSelection());\n"
        "        status.setText(\"Applied settings to current session.\");\n",
        "        session.setDatabaseSelection(readDatabaseSelection());\n"
        "        saveCompanyUiPreferences(session.multiCompany().activeCompanyCode());\n"
        "        status.setText(\"Applied settings and company display preferences to the current session.\");\n",
        "settings save display preferences")
    insert = '''\n    CompanyUiPreferences readCompanyUiPreferences()\n    {\n        return new CompanyUiPreferences(\n                currencySymbol.getText(),\n                moneyPrintFormat.getValue() == null ? MoneyPrintFormat.SYMBOL_PREFIX : moneyPrintFormat.getValue(),\n                dateDisplayFormat.getValue() == null ? DateDisplayFormat.MONTH_DAY_YEAR : dateDisplayFormat.getValue());\n    }\n\n    private void loadCompanyUiPreferences(String companyCode)\n    {\n        try\n        {\n            CompanyUiPreferences preferences = UiServiceRegistry.companyUiPreferences().load(companyCode);\n            currencySymbol.setText(preferences.currencySymbol());\n            moneyPrintFormat.setValue(preferences.moneyPrintFormat());\n            dateDisplayFormat.setValue(preferences.dateDisplayFormat());\n        }\n        catch (RuntimeException ex)\n        {\n            CompanyUiPreferences defaults = CompanyUiPreferences.defaults();\n            currencySymbol.setText(defaults.currencySymbol());\n            moneyPrintFormat.setValue(defaults.moneyPrintFormat());\n            dateDisplayFormat.setValue(defaults.dateDisplayFormat());\n            status.setText("Could not load company display preferences: " + UiErrors.safeMessage(ex));\n        }\n    }\n\n    private void saveCompanyUiPreferences(String companyCode)\n    {\n        UiServiceRegistry.companyUiPreferences().save(companyCode, readCompanyUiPreferences());\n    }\n'''
    text = replace_once(text,
        "    AppPreferencesState readPreferences()\n",
        insert + "\n    AppPreferencesState readPreferences()\n",
        "settings preference methods")
    text = text.replace(
        "        status.setText(\"Saved settings. They will be restored on next startup.\");",
        "        status.setText(\"Saved settings and company display preferences. They will be restored for the active company.\");")
    path.write_text(text)


def patch_journal() -> None:
    path = Path("src/main/java/org/nonprofitbookkeeping/ui/JournalWorkspacePanel.java")
    text = path.read_text()
    if "journalWorkspaceEditorScroll" in text:
        return

    text = replace_once(text,
        "package org.nonprofitbookkeeping.ui;\n\n",
        "package org.nonprofitbookkeeping.ui;\n\nimport javafx.animation.PauseTransition;\n",
        "journal animation import")
    text = replace_once(text,
        "import javafx.util.StringConverter;\n",
        "import javafx.util.Duration;\nimport javafx.util.StringConverter;\n",
        "journal Duration import")
    text = replace_once(text,
        "import org.nonprofitbookkeeping.model.CorrectionMethod;\n",
        "import org.nonprofitbookkeeping.model.CompanyUiPreferences;\n"
        "import org.nonprofitbookkeeping.model.CorrectionMethod;\n",
        "journal company prefs import")
    text = replace_once(text,
        "import org.nonprofitbookkeeping.service.TransactionCommand;\n",
        "import org.nonprofitbookkeeping.service.CompanyUiPreferencesService;\n"
        "import org.nonprofitbookkeeping.service.TransactionCommand;\n",
        "journal service import")
    text = replace_once(text,
        "import java.util.EnumMap;\n",
        "import java.util.EnumMap;\nimport java.util.HashMap;\nimport java.util.LinkedHashMap;\n",
        "journal map imports")
    text = text.replace("import java.util.prefs.Preferences;\n", "")
    text = replace_once(text,
        "    private static final Preferences VIEW_STATE = Preferences.userNodeForPackage(JournalWorkspacePanel.class)\n"
        "            .node(\"company-journal-workspace\");\n",
        "    private static final String STATE_PREFIX = \"journal.\";\n",
        "journal state constant")
    text = replace_once(text,
        "    private final Label debitTotal = new Label(\"$0.00\");\n"
        "    private final Label creditTotal = new Label(\"$0.00\");\n"
        "    private final Label differenceTotal = new Label(\"$0.00\");\n",
        "    private final Label debitTotal = new Label(\"0.00\");\n"
        "    private final Label creditTotal = new Label(\"0.00\");\n"
        "    private final Label differenceTotal = new Label(\"0.00\");\n",
        "journal total defaults")
    text = replace_once(text,
        "    private final SplitPane outerSplit = new SplitPane();\n"
        "    private final SplitPane editorSplit = new SplitPane();\n",
        "    private final SplitPane outerSplit = new SplitPane();\n"
        "    private final SplitPane editorSplit = new SplitPane();\n"
        "    private final ScrollPane editorScroll = new ScrollPane();\n",
        "journal editor scroll field")
    text = replace_once(text,
        "    private final TransactionCommandValidator commandValidator = new TransactionCommandValidator();\n",
        "    private final TransactionCommandValidator commandValidator = new TransactionCommandValidator();\n"
        "    private final CompanyUiPreferencesService uiPreferencesService = UiServiceRegistry.companyUiPreferences();\n"
        "    private final Map<String, String> viewState = new HashMap<>();\n"
        "    private final Map<String, String> pendingViewState = new LinkedHashMap<>();\n"
        "    private final PauseTransition viewStateFlushDelay = new PauseTransition(Duration.millis(350));\n"
        "    private CompanyUiFormat companyFormat = new CompanyUiFormat(CompanyUiPreferences.defaults());\n"
        "    private String loadedCompanyCode = \"DEFAULT\";\n",
        "journal preference fields")
    text = replace_once(text,
        "        root.setTop(buildWorkspaceHeader());\n\n"
        "        buildJournalTable();\n",
        "        root.setTop(buildWorkspaceHeader());\n"
        "        loadCompanyDisplayAndState();\n"
        "        configureViewStateFlush();\n\n"
        "        buildJournalTable();\n",
        "journal preference initialization")
    text = replace_once(text,
        "        editorSplit.setDividerPositions(0.22, 0.64);\n"
        "        editorSplit.setMinSize(0, 0);\n\n"
        "        outerSplit.setId(\"journalWorkspaceOuterSplit\");\n"
        "        outerSplit.setOrientation(Orientation.VERTICAL);\n"
        "        outerSplit.getItems().setAll(journalRegion, editorSplit);\n",
        "        editorSplit.setDividerPositions(0.22, 0.64);\n"
        "        editorSplit.setMinSize(0, 700);\n"
        "        editorSplit.setPrefHeight(920);\n\n"
        "        editorScroll.setId(\"journalWorkspaceEditorScroll\");\n"
        "        editorScroll.setContent(editorSplit);\n"
        "        editorScroll.setFitToWidth(true);\n"
        "        editorScroll.setFitToHeight(false);\n"
        "        editorScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);\n"
        "        editorScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);\n"
        "        editorScroll.setPannable(true);\n"
        "        editorScroll.setMinSize(0, 0);\n\n"
        "        outerSplit.setId(\"journalWorkspaceOuterSplit\");\n"
        "        outerSplit.setOrientation(Orientation.VERTICAL);\n"
        "        outerSplit.getItems().setAll(journalRegion, editorScroll);\n",
        "journal overall editor scroll")

    old_journal_region = '''    private Node buildJournalRegion()\n    {\n        Label heading = sectionHeading("Journal Transactions");\n        Label help = new Label("One row represents one canonical transaction. Double-click a row to load it into the editor below.");\n        VBox region = new VBox(6, heading, help, journalTable);\n        region.setPadding(new Insets(4, 0, 4, 0));\n        region.setMinSize(0, 0);\n        VBox.setVgrow(journalTable, Priority.ALWAYS);\n        return region;\n    }\n'''
    new_journal_region = '''    private Node buildJournalRegion()\n    {\n        Label heading = sectionHeading("Journal Transactions");\n        Label help = new Label("One row represents one canonical transaction. Double-click a row to load it into the editor below.");\n        help.setWrapText(true);\n        VBox helpPane = new VBox(4, help);\n        helpPane.setPadding(new Insets(4));\n        SplitPane tableSplit = new SplitPane(journalTable, helpPane);\n        tableSplit.setId("journalWorkspaceJournalTableSplit");\n        tableSplit.setOrientation(Orientation.VERTICAL);\n        tableSplit.setDividerPositions(0.91);\n        tableSplit.setMinSize(0, 0);\n        installDividerState(tableSplit, "journal-table", 0.91);\n        VBox region = new VBox(6, heading, tableSplit);\n        region.setPadding(new Insets(4, 0, 4, 0));\n        region.setMinSize(0, 0);\n        VBox.setVgrow(tableSplit, Priority.ALWAYS);\n        return region;\n    }\n'''
    text = replace_once(text, old_journal_region, new_journal_region, "journal table split")

    text = replace_once(text,
        "        ScrollPane scroll = scrollable(content, true);\n"
        "        scroll.setId(\"journalWorkspaceEntryHeaderScroll\");\n"
        "        return scroll;\n",
        "        content.setId(\"journalWorkspaceEntryHeaderRegion\");\n"
        "        return content;\n",
        "journal header inner scroll removal")

    old_line_region = '''    private Node buildLineRegion()\n    {\n        Button addLine = new Button("Add Line");\n        Button duplicateLine = new Button("Duplicate Line");\n        addLine.setOnAction(event -> addEditorLine(null));\n        duplicateLine.setOnAction(event -> duplicateSelectedLine());\n        removeLineButton.setOnAction(event -> removeSelectedLine());\n        ToolBar tools = new ToolBar(addLine, duplicateLine, removeLineButton);\n        VBox region = new VBox(6, sectionHeading("Entry Lines"), tools, lineTable);\n        region.setPadding(new Insets(4));\n        region.setMinSize(0, 0);\n        VBox.setVgrow(lineTable, Priority.ALWAYS);\n        return region;\n    }\n'''
    new_line_region = '''    private Node buildLineRegion()\n    {\n        Button addLine = new Button("Add Line");\n        Button duplicateLine = new Button("Duplicate Line");\n        addLine.setOnAction(event -> addEditorLine(null));\n        duplicateLine.setOnAction(event -> duplicateSelectedLine());\n        removeLineButton.setOnAction(event -> removeSelectedLine());\n        ToolBar tools = new ToolBar(addLine, duplicateLine, removeLineButton);\n        Label help = new Label("Columns can be sorted, resized, and rearranged. The table retains independent horizontal and vertical scrolling.");\n        help.setWrapText(true);\n        VBox helpPane = new VBox(4, help);\n        helpPane.setPadding(new Insets(4));\n        SplitPane tableSplit = new SplitPane(lineTable, helpPane);\n        tableSplit.setId("journalWorkspaceEntryLineTableSplit");\n        tableSplit.setOrientation(Orientation.VERTICAL);\n        tableSplit.setDividerPositions(0.91);\n        tableSplit.setMinSize(0, 0);\n        installDividerState(tableSplit, "entry-lines-table", 0.91);\n        VBox region = new VBox(6, sectionHeading("Entry Lines"), tools, tableSplit);\n        region.setPadding(new Insets(4));\n        region.setMinSize(0, 0);\n        VBox.setVgrow(tableSplit, Priority.ALWAYS);\n        return region;\n    }\n'''
    text = replace_once(text, old_line_region, new_line_region, "entry line table split")

    text = replace_once(text,
        "        ScrollPane scroll = scrollable(content, true);\n"
        "        scroll.setId(\"journalWorkspaceAdditionalDetailsScroll\");\n"
        "        return scroll;\n",
        "        content.setId(\"journalWorkspaceAdditionalDetailsRegion\");\n"
        "        return content;\n",
        "additional details inner scroll removal")

    text = text.replace("(row, value) -> row.setAmount(normalizeMoney(value))",
                        "(row, value) -> row.setAmount(companyFormat.normalizeMoney(value))")
    text = text.replace("SupplementalRow::dueDateProperty, SupplementalRow::setDueDate",
                        "SupplementalRow::dueDateProperty, (row, value) -> row.setDueDate(companyFormat.normalizeDate(value))")
    text = text.replace("SupplementalRow::startDateProperty, SupplementalRow::setStartDate",
                        "SupplementalRow::startDateProperty, (row, value) -> row.setStartDate(companyFormat.normalizeDate(value))")
    text = text.replace("SupplementalRow::endDateProperty, SupplementalRow::setEndDate",
                        "SupplementalRow::endDateProperty, (row, value) -> row.setEndDate(companyFormat.normalizeDate(value))")
    text = replace_once(text,
        "        VBox panel = new VBox(6, new HBox(8, add, remove), table);\n"
        "        VBox.setVgrow(table, Priority.ALWAYS);\n"
        "        panel.setMinSize(0, 0);\n",
        "        Label validation = new Label(\"Description and a non-negative amount are required for populated rows. Date values are normalized to the active company format.\");\n"
        "        validation.setWrapText(true);\n"
        "        VBox validationPane = new VBox(4, validation);\n"
        "        validationPane.setPadding(new Insets(4));\n"
        "        SplitPane tableSplit = new SplitPane(table, validationPane);\n"
        "        tableSplit.setId(\"journalWorkspaceSupplemental\" + kind.name() + \"TableSplit\");\n"
        "        tableSplit.setOrientation(Orientation.VERTICAL);\n"
        "        tableSplit.setDividerPositions(0.88);\n"
        "        tableSplit.setMinSize(0, 0);\n"
        "        installDividerState(tableSplit, \"supplemental-table-\" + kind.name(), 0.88);\n"
        "        VBox panel = new VBox(6, new HBox(8, add, remove), tableSplit);\n"
        "        VBox.setVgrow(tableSplit, Priority.ALWAYS);\n"
        "        panel.setMinSize(0, 0);\n",
        "supplemental table split")

    text = replace_once(text,
        "                    List<JournalTransactionRow> rows = views.stream().map(JournalTransactionRow::from).toList();\n",
        "                    List<JournalTransactionRow> rows = views.stream()\n"
        "                            .map(view -> JournalTransactionRow.from(view, companyFormat))\n"
        "                            .toList();\n",
        "journal row formatting")
    text = text.replace("normalizeMoney(line.debit().toPlainString())", "companyFormat.normalizeMoney(line.debit().toPlainString())")
    text = text.replace("normalizeMoney(line.credit().toPlainString())", "companyFormat.normalizeMoney(line.credit().toPlainString())")
    text = replace_once(text,
        "            return LocalDate.parse(value.trim());\n",
        "            LocalDate parsed = companyFormat.parseDate(value);\n"
        "            if (parsed == null)\n"
        "            {\n"
        "                throw new IllegalArgumentException(\"invalid date\");\n"
        "            }\n"
        "            return parsed;\n",
        "journal supplemental date parse")
    text = text.replace("SupplementalRow.from(view)", "SupplementalRow.from(view, companyFormat)")
    text = replace_once(text,
        "        debitTotal.setText(money(debit));\n"
        "        creditTotal.setText(money(credit));\n"
        "        differenceTotal.setText(money(difference.abs()));\n",
        "        debitTotal.setText(companyFormat.formatMoney(debit));\n"
        "        creditTotal.setText(companyFormat.formatMoney(credit));\n"
        "        differenceTotal.setText(companyFormat.formatMoney(difference.abs()));\n",
        "journal totals formatting")

    state_pattern = r'''    private <S> void restoreTableState\(TableView<S> table, String tableKey\)\n    \{.*?    private static String companyKey\(\)\n    \{\n        String company = MainWindow\.sharedSessionState\(\)\.multiCompany\(\)\.activeCompanyCode\(\);\n        String value = company == null \|\| company\.isBlank\(\) \? "DEFAULT" : company\.trim\(\)\.toUpperCase\(Locale\.ROOT\);\n        return value\.replaceAll\("\[\^A-Z0-9_-\]", "_"\);\n    \}\n'''
    state_replacement = '''    private void loadCompanyDisplayAndState()\n    {\n        loadedCompanyCode = companyKey();\n        companyFormat = new CompanyUiFormat(uiPreferencesService.load(loadedCompanyCode));\n        companyFormat.install(fromDate);\n        companyFormat.install(toDate);\n        companyFormat.install(entryDate);\n        viewState.clear();\n        viewState.putAll(uiPreferencesService.loadState(loadedCompanyCode, STATE_PREFIX));\n        debitTotal.setText(companyFormat.formatMoney(BigDecimal.ZERO));\n        creditTotal.setText(companyFormat.formatMoney(BigDecimal.ZERO));\n        differenceTotal.setText(companyFormat.formatMoney(BigDecimal.ZERO));\n    }\n\n    private void configureViewStateFlush()\n    {\n        viewStateFlushDelay.setOnFinished(event -> flushViewState());\n    }\n\n    private void queueViewState(String key, String value)\n    {\n        String fullKey = STATE_PREFIX + key;\n        viewState.put(fullKey, value == null ? "" : value);\n        pendingViewState.put(fullKey, value == null ? "" : value);\n        viewStateFlushDelay.playFromStart();\n    }\n\n    private void flushViewState()\n    {\n        if (pendingViewState.isEmpty())\n        {\n            return;\n        }\n        Map<String, String> snapshot = Map.copyOf(pendingViewState);\n        pendingViewState.clear();\n        String companyCode = loadedCompanyCode;\n        UiAsync.run("journal-workspace-state-save",\n                () -> {\n                    uiPreferencesService.saveState(companyCode, snapshot);\n                    return Boolean.TRUE;\n                },\n                ignored -> { },\n                ex -> status.setText("Could not save Journal layout state: " + UiErrors.safeMessage(ex)));\n    }\n\n    private String stateValue(String key, String fallback)\n    {\n        return viewState.getOrDefault(STATE_PREFIX + key, fallback);\n    }\n\n    private double stateDouble(String key, double fallback)\n    {\n        try\n        {\n            return Double.parseDouble(stateValue(key, Double.toString(fallback)));\n        }\n        catch (NumberFormatException ex)\n        {\n            return fallback;\n        }\n    }\n\n    private <S> void restoreTableState(TableView<S> table, String tableKey)\n    {\n        restoringTableState = true;\n        try\n        {\n            String prefix = "table." + tableKey + ".";\n            for (TableColumn<S, ?> column : table.getColumns())\n            {\n                column.setPrefWidth(stateDouble(prefix + columnKey(column) + ".width", column.getPrefWidth()));\n                String sort = stateValue(prefix + columnKey(column) + ".sort", "");\n                if ("ASCENDING".equals(sort))\n                {\n                    column.setSortType(TableColumn.SortType.ASCENDING);\n                }\n                else if ("DESCENDING".equals(sort))\n                {\n                    column.setSortType(TableColumn.SortType.DESCENDING);\n                }\n            }\n            String order = stateValue(prefix + "order", "");\n            if (!order.isBlank())\n            {\n                List<String> keys = List.of(order.split(","));\n                List<TableColumn<S, ?>> columns = new ArrayList<>(table.getColumns());\n                columns.sort(Comparator.comparingInt(column -> {\n                    int index = keys.indexOf(columnKey(column));\n                    return index < 0 ? Integer.MAX_VALUE : index;\n                }));\n                table.getColumns().setAll(columns);\n            }\n            String sortOrder = stateValue(prefix + "sortOrder", "");\n            if (!sortOrder.isBlank())\n            {\n                List<TableColumn<S, ?>> restored = new ArrayList<>();\n                for (String key : sortOrder.split(","))\n                {\n                    table.getColumns().stream()\n                            .filter(column -> Objects.equals(columnKey(column), key))\n                            .findFirst()\n                            .ifPresent(restored::add);\n                }\n                table.getSortOrder().setAll(restored);\n            }\n        }\n        finally\n        {\n            restoringTableState = false;\n        }\n    }\n\n    private <S> void installTableStatePersistence(TableView<S> table, String tableKey)\n    {\n        table.getColumns().addListener((ListChangeListener<TableColumn<S, ?>>) change -> saveTableState(table, tableKey));\n        table.getSortOrder().addListener((ListChangeListener<TableColumn<S, ?>>) change -> saveTableState(table, tableKey));\n        for (TableColumn<S, ?> column : table.getColumns())\n        {\n            column.widthProperty().addListener((obs, oldValue, newValue) -> saveTableState(table, tableKey));\n            column.sortTypeProperty().addListener((obs, oldValue, newValue) -> saveTableState(table, tableKey));\n        }\n    }\n\n    private <S> void saveTableState(TableView<S> table, String tableKey)\n    {\n        if (restoringTableState)\n        {\n            return;\n        }\n        String prefix = "table." + tableKey + ".";\n        queueViewState(prefix + "order", String.join(",", table.getColumns().stream().map(JournalWorkspacePanel::columnKey).toList()));\n        queueViewState(prefix + "sortOrder", String.join(",", table.getSortOrder().stream().map(JournalWorkspacePanel::columnKey).toList()));\n        for (TableColumn<S, ?> column : table.getColumns())\n        {\n            queueViewState(prefix + columnKey(column) + ".width", Double.toString(column.getWidth() > 0 ? column.getWidth() : column.getPrefWidth()));\n            queueViewState(prefix + columnKey(column) + ".sort", column.getSortType() == null ? "" : column.getSortType().name());\n        }\n    }\n\n    private static String columnKey(TableColumn<?, ?> column)\n    {\n        Object key = column.getUserData();\n        return key == null ? column.getText() : String.valueOf(key);\n    }\n\n    private void installDividerState(SplitPane splitPane, String key, double... defaults)\n    {\n        for (int index = 0; index < splitPane.getDividers().size(); index++)\n        {\n            double fallback = index < defaults.length ? defaults[index] : splitPane.getDividers().get(index).getPosition();\n            splitPane.getDividers().get(index).setPosition(stateDouble("divider." + key + "." + index, fallback));\n            int dividerIndex = index;\n            splitPane.getDividers().get(index).positionProperty().addListener((obs, oldValue, newValue) ->\n                    queueViewState("divider." + key + "." + dividerIndex, Double.toString(newValue.doubleValue())));\n        }\n    }\n\n    private static String companyKey()\n    {\n        String company = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();\n        String value = company == null || company.isBlank() ? "DEFAULT" : company.trim().toUpperCase(Locale.ROOT);\n        return value.replaceAll("[^A-Z0-9_-]", "_");\n    }\n'''
    text = regex_once(text, state_pattern, state_replacement, "journal H2 state block")

    money_pattern = r'''    private static BigDecimal parseMoney\(String value\)\n    \{.*?    private static String money\(BigDecimal value\)\n    \{\n        BigDecimal amount = value == null \? BigDecimal\.ZERO : value;\n        return "\\$" \+ amount\.setScale\(2, RoundingMode\.HALF_UP\)\.toPlainString\(\);\n    \}\n'''
    money_replacement = '''    private static BigDecimal parseMoney(String value)\n    {\n        return CompanyUiFormat.parseMoneyLenient(value);\n    }\n\n    private String normalizeMoney(String value)\n    {\n        return companyFormat.normalizeMoney(value);\n    }\n\n    private String money(BigDecimal value)\n    {\n        return companyFormat.formatMoney(value);\n    }\n'''
    text = regex_once(text, money_pattern, money_replacement, "journal money helpers")

    text = replace_once(text,
        "        private JournalTransactionRow(TransactionView view)\n",
        "        private JournalTransactionRow(TransactionView view, CompanyUiFormat format)\n",
        "journal row constructor")
    text = text.replace("append(debitBuilder, line.debit().signum() == 0 ? \"\" : money(line.debit()));",
                        "append(debitBuilder, line.debit().signum() == 0 ? \"\" : format.formatMoney(line.debit()));")
    text = text.replace("append(creditBuilder, line.credit().signum() == 0 ? \"\" : money(line.credit()));",
                        "append(creditBuilder, line.credit().signum() == 0 ? \"\" : format.formatMoney(line.credit()));")
    text = replace_once(text,
        "        static JournalTransactionRow from(TransactionView view)\n"
        "        {\n"
        "            return new JournalTransactionRow(view);\n"
        "        }\n",
        "        static JournalTransactionRow from(TransactionView view, CompanyUiFormat format)\n"
        "        {\n"
        "            return new JournalTransactionRow(view, format);\n"
        "        }\n",
        "journal row factory")
    text = replace_once(text,
        "            return view.date() == null ? \"\" : view.date().toString();\n",
        "            return formatDate();\n",
        "journal row date")
    text = replace_once(text,
        "        String accounts()\n",
        "        private String formatDate()\n"
        "        {\n"
        "            return view.date() == null ? \"\" : new CompanyUiFormat(CompanyUiPreferences.defaults()).formatDate(view.date());\n"
        "        }\n\n"
        "        String accounts()\n",
        "journal row date helper")
    # Replace the helper with a stored formatted date instead of constructing defaults.
    text = replace_once(text,
        "        private final String accounts;\n",
        "        private final String date;\n"
        "        private final String accounts;\n",
        "journal row date field")
    text = replace_once(text,
        "            this.view = view;\n",
        "            this.view = view;\n"
        "            this.date = format.formatDate(view.date());\n",
        "journal row date assignment")
    text = replace_once(text,
        "            return formatDate();\n",
        "            return date;\n",
        "journal row date getter")
    text = regex_once(text,
        r'''        private String formatDate\(\)\n        \{\n            return view\.date\(\) == null \? "" : new CompanyUiFormat\(CompanyUiPreferences\.defaults\(\)\)\.formatDate\(view\.date\(\)\);\n        \}\n\n''',
        "",
        "remove temporary journal date helper")

    text = replace_once(text,
        "        static SupplementalRow from(TransactionSupplementalLineView view)\n",
        "        static SupplementalRow from(TransactionSupplementalLineView view, CompanyUiFormat format)\n",
        "supplemental row factory signature")
    text = text.replace("row.setAmount(normalizeMoney(view.amount().toPlainString()));",
                        "row.setAmount(format.normalizeMoney(view.amount().toPlainString()));")
    text = text.replace("row.setDueDate(view.dueDate() == null ? \"\" : view.dueDate().toString());",
                        "row.setDueDate(format.formatDate(view.dueDate()));")
    text = text.replace("row.setStartDate(view.startDate() == null ? \"\" : view.startDate().toString());",
                        "row.setStartDate(format.formatDate(view.startDate()));")
    text = text.replace("row.setEndDate(view.endDate() == null ? \"\" : view.endDate().toString());",
                        "row.setEndDate(format.formatDate(view.endDate()));")
    text = text.replace("parseMoney(getDebit())", "CompanyUiFormat.parseMoneyLenient(getDebit())")
    text = text.replace("parseMoney(getCredit())", "CompanyUiFormat.parseMoneyLenient(getCredit())")
    text = text.replace("BigDecimal parsedAmount = parseMoney(getAmount());",
                        "BigDecimal parsedAmount = CompanyUiFormat.parseMoneyLenient(getAmount());")
    path.write_text(text)


def patch_source_test() -> None:
    path = Path("src/test/java/org/nonprofitbookkeeping/ui/JournalWorkspacePortSourceTest.java")
    text = path.read_text()
    if "journalWorkspaceEditorScroll" in text:
        return
    text = replace_once(text,
        "        assertTrue(source.contains(\"journalWorkspaceDetailSplit\"));\n",
        "        assertTrue(source.contains(\"journalWorkspaceDetailSplit\"));\n"
        "        assertTrue(source.contains(\"journalWorkspaceEditorScroll\"));\n"
        "        assertTrue(source.contains(\"setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED)\"));\n"
        "        assertTrue(source.contains(\"journalWorkspaceJournalTableSplit\"));\n"
        "        assertTrue(source.contains(\"journalWorkspaceEntryLineTableSplit\"));\n"
        "        assertTrue(source.contains(\"TableSplit\"));\n",
        "source scroll assertions")
    text = replace_once(text,
        "        assertTrue(source.contains(\"setReorderable(true)\"));\n",
        "        assertTrue(source.contains(\"setReorderable(true)\"));\n"
        "        assertTrue(source.contains(\"CompanyUiFormat\"));\n"
        "        assertTrue(source.contains(\"CompanyUiPreferencesService\"));\n"
        "        assertFalse(source.contains(\"java.util.prefs.Preferences\"));\n",
        "source preference assertions")
    path.write_text(text)


def patch_plan_and_docs() -> None:
    plan = Path("doc/PLAN.md")
    text = plan.read_text()
    text = text.replace("plan_version: 24", "plan_version: 25", 1)
    text = text.replace("active_slice: P03-C6", "active_slice: P03-C7", 1)
    text = text.replace("active_status: VERIFYING", "active_status: IN_PROGRESS", 1)
    text = text.replace("active_branch: codex/P03-C6-journal-workspace-port", "active_branch: codex/P03-C7-journal-ui-compliance", 1)
    text = text.replace('active_pull_request: "#151"', "active_pull_request: pending", 1)
    text = re.sub(r"active_head: .*", "active_head: pending", text, count=1)
    text = re.sub(r'next_action: ".*"', 'next_action: "Finish P03-C7 Journal scrolling, table, formatting, and company-owned state compliance; open PR and validate."', text, count=1)
    text = text.replace(
        "This revision records P03-C5 as merged through PR #150 and P03-C6 as VERIFYING in PR #151. P03-C6 replaces the separate Ledger Register, Transaction Editor, and Inspect Journal surfaces with one Journal-based workspace derived from `benbaron/NonprofitAccounting` `Journal*` UI classes.",
        "This revision records P03-C6 as DONE through PR #151 and opens P03-C7 to bring the unified Journal workspace into full scrolling, table-state, money/date-format, and company-owned preference compliance.")
    text = text.replace("| P03 | Journal workspace and canonical transaction operations | P01, P02 | READY; corrective P03-C6 VERIFYING |",
                        "| P03 | Journal workspace and canonical transaction operations | P01, P02 | READY; corrective P03-C7 IN_PROGRESS |")
    text = text.replace("Status: READY; corrective P03-C6 VERIFYING.", "Status: READY; corrective P03-C7 IN_PROGRESS.", 1)
    text = text.replace("- P03-C5 Persisted Transaction Editor supplemental details: DONE through PR #150.\n",
                        "- P03-C5 Persisted Transaction Editor supplemental details: DONE through PR #150.\n"
                        "- P03-C6 Unified Journal workspace port: DONE through PR #151.\n")
    c7 = '''\n### P03-C7 — Journal UI design-rule compliance\n\nStatus: IN_PROGRESS.\nBranch: `codex/P03-C7-journal-ui-compliance`\nPull request: pending\n\nPurpose: correct the merged unified Journal so the complete editor region has an overall vertical scrollbar, every table has the required resize/reorder/sort/scroll and split-pane behavior, money and dates follow company-owned preferences, and table/divider state is stored in H2 by company rather than only in global Java user preferences.\n\nRequired deliverables:\n\n- Wrap the complete middle/editor section in an overall vertical `ScrollPane` while preserving independent table scrolling and draggable section dividers.\n- Put Journal, Entry Lines, and each supplemental table in a dedicated `SplitPane` table region.\n- Keep all columns sortable, resizable, and reorderable, and persist width/order/sort state by active company.\n- Add H2-backed company money symbol, money print format, date display format, and generic company UI state.\n- Apply company money/date parsing and correction to Journal displays, totals, entry-line money cells, supplemental amounts/dates, and DatePickers.\n- Expose company money/date preferences in Settings.\n- Add focused repository, formatter, source-layout, migration, and regression tests.\n- Update `doc/ui/editor-guidelines.md`, `doc/interface-operation-matrix.md`, `doc/persistence-authority-inventory.md`, and this plan.\n\n'''
    text = text.replace("## 7. Active and recent phase contracts\n", c7 + "## 7. Active and recent phase contracts\n")
    plan.write_text(text)

    guidelines = Path("doc/ui/editor-guidelines.md")
    text = guidelines.read_text()
    text = text.replace(
        "Divider positions and qualifying table state are remembered for the active company. Each table uses unconstrained resizing and exposes horizontal and vertical scrolling when content exceeds its viewport.",
        "The complete middle/editor region is wrapped in one overall vertical scroll pane, while each table retains independent horizontal and vertical scrolling. Divider positions and table width/order/sort state are stored in H2 for the active company. Every Journal, Entry Lines, and supplemental table is contained in its own SplitPane table region and uses sortable, resizable, reorderable columns. Money and date displays/editors use the active company's H2-backed symbol, print format, and date-order preference.")
    guidelines.write_text(text)

    matrix = Path("doc/interface-operation-matrix.md")
    text = matrix.read_text()
    text = text.replace("updated through P03-C6 unified Journal workspace", "updated through P03-C7 Journal UI compliance")
    text = text.replace(
        "yes; table and divider state also persist per active company",
        "yes; H2 stores money/date preferences and table/divider state by active company")
    text = text.replace(
        "desktop visual validation at laptop width; cleared display should later use an explicit line-level projection rather than the current aggregate label",
        "desktop visual validation at laptop width after overall editor-scroll and table-region correction; cleared display should later use an explicit line-level projection rather than the current aggregate label")
    matrix.write_text(text)

    inventory = Path("doc/persistence-authority-inventory.md")
    text = inventory.read_text()
    if "company_ui_preference" not in text:
        text += "\n- `company_ui_preference` and `company_ui_state`: H2 authority for per-company money/date display choices and Journal table/divider state; introduced by P03-C7 so company-specific UI behavior is not stored only in global Java user preferences.\n"
    inventory.write_text(text)


patch_registry()
patch_settings()
patch_journal()
patch_source_test()
patch_plan_and_docs()
print("P03-C7 patch applied")
