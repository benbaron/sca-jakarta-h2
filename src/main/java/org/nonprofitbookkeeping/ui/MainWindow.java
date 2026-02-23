package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;

/**
 * Represents the MainWindow component in the nonprofit bookkeeping application.
 */
public class MainWindow extends BorderPane
{
    private final PanelHost panelHost = new PanelHost();
    private final InspectorPane inspectorPane = new InspectorPane();
    private final NavigationPane nav = new NavigationPane(this::openPanel, this::openInspectorForSelection);
    private DateRangeSelector dateRangeSelector;

    public MainWindow()
    {
        setTop(buildTopChrome());
        setLeft(nav);
        setCenter(panelHost);
        setRight(inspectorPane);

        BorderPane.setMargin(panelHost, new Insets(8));
        BorderPane.setMargin(nav, new Insets(8, 4, 8, 8));
        BorderPane.setMargin(inspectorPane, new Insets(8, 8, 8, 4));

        openPanel(AppPanelId.LEDGER_REGISTER);
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
            new SeparatorMenuItem(),
            item("Save", "Ctrl+S", this::saveActivePanel),
            item("Export…", null, () -> info("Export not wired yet.")),
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

        Menu run = new Menu("Run");
        run.getItems().addAll(
            item("Post / Validate", null, () -> info("Posting not wired in UI yet.")),
            item("Recalculate summaries", null, () -> info("Recalculate not wired yet."))
        );

        Menu tools = new Menu("Tools");
        tools.getItems().addAll(
            item("Import/Export…", null, () -> info("Tools not wired yet.")),
            item("Preferences…", null, () -> openPanel(AppPanelId.SETTINGS))
        );

        Menu help = new Menu("Help");
        help.getItems().addAll(
            item("About", null, () -> info("SCA Ledger prototype shell."))
        );

        return new MenuBar(file, edit, search, run, tools, help);
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

        ToolBar tb = new ToolBar(btnNew, btnSave, new Separator(), btnFind, new Separator(), btnJournal, new Separator(), dr);
        tb.getStyleClass().add("toolbar");
        return tb;
    }

    
    private void focusDateRangeSelector()
    {
        if (dateRangeSelector == null) return;
        dateRangeSelector.presetBox().requestFocus();
        dateRangeSelector.presetBox().show();
    }

private MenuItem item(String text, String accel, Runnable action)
    {
        MenuItem mi = new MenuItem(text);
        if (accel != null) mi.setAccelerator(KeyCombination.keyCombination(accel));
        mi.setOnAction(e -> action.run());
        return mi;
    }

    // --- hooks ---
    public void openPanel(AppPanelId id)
    {
        panelHost.show(id);
        nav.highlight(id);
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
