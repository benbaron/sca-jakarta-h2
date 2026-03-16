package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.service.AccountLookupService;
import org.nonprofitbookkeeping.service.ScheduleEligibilityService;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One schedules panel with tabs. Tabs are enabled/disabled based on the selected account's subtype
 * (plus any per-account overrides).
 */
public class SchedulesPanel implements AppPanel
{
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BorderPane root = new BorderPane();

    private final ComboBox<Account> accountSelect = new ComboBox<>();
    private final TabPane tabs = new TabPane();
    private final Label status = new Label();
    private final ListView<String> runbook = new ListView<>();

    private final Map<String, Tab> tabIndex = new LinkedHashMap<>();
    private final Map<Tab, String> tabCodes = new LinkedHashMap<>();

    private final ScheduleEligibilityService eligibility = UiServiceRegistry.schedules();

    public SchedulesPanel()
    {
        Label title = new Label("Schedules / Outstanding Details");
        title.getStyleClass().add("h1");

        Label help = new Label("Select an account, then use lifecycle actions to record schedule events.");
        help.setWrapText(true);

        configureAccountCombo();

        buildTabs();

        Button openItem = new Button("Record Open Item");
        openItem.setOnAction(e -> recordScheduleAction("OPEN"));
        Button settleItem = new Button("Record Settlement");
        settleItem.setOnAction(e -> recordScheduleAction("SETTLE"));
        Button writeOffItem = new Button("Record Write-off");
        writeOffItem.setOnAction(e -> recordScheduleAction("WRITE_OFF"));

        HBox top = new HBox(10,
                new Label("Account:"), accountSelect,
                openItem, settleItem, writeOffItem);
        top.setPadding(new Insets(6, 6, 6, 6));

        VBox header = new VBox(6, title, help, top, status, new Separator());
        header.setPadding(new Insets(6, 6, 6, 6));

        runbook.setItems(FXCollections.observableArrayList());
        runbook.setPlaceholder(new Label("No schedule lifecycle actions recorded in this session."));
        runbook.getItems().setAll(UiWorkspaceDataStore.scheduleRunbookEntries());

        root.setTop(header);
        root.setCenter(new VBox(8, tabs, new Label("Schedule Runbook"), runbook));

        loadAccounts();

        accountSelect.valueProperty().addListener((obs, oldV, newV) -> applyGating(newV));
        if (!accountSelect.getItems().isEmpty())
        {
            accountSelect.getSelectionModel().select(0);
        }
    }

    @Override public String title() { return "Schedules"; }

    public Node node() { return root; }

    private void configureAccountCombo()
    {
        accountSelect.setPrefWidth(560);
        accountSelect.setCellFactory(cb -> new ListCell<>()
        {
            @Override protected void updateItem(Account a, boolean empty)
            {
                super.updateItem(a, empty);
                if (empty || a == null) { setText(null); return; }
                String sub = a.getSubtype() == null ? "" : ("  [" + a.getSubtype().name() + "]");
                setText(a.getCode() + " — " + a.getName() + sub);
            }
        });
        accountSelect.setButtonCell(accountSelect.getCellFactory().call(null));
    }

    private void buildTabs()
    {
        addTab("RECEIVABLE", "Receivables");
        addTab("PAYABLE", "Payables");
        addTab("PREPAID", "Prepaids");
        addTab("DEFERRED_REVENUE", "Deferred Revenue");
        addTab("OTHER_ASSET", "Other Assets / Deposits");
        addTab("OTHER_LIABILITY", "Other Liabilities");
        addTab("INVENTORY", "Inventory");
        addTab("FIXED_ASSET", "Fixed Assets");

        for (Tab t : tabs.getTabs()) t.setDisable(true);
    }

    private void addTab(String scheduleCode, String label)
    {
        Tab t = new Tab(label);
        t.setClosable(false);
        TextArea content = new TextArea();
        content.setEditable(false);
        content.setWrapText(true);
        content.setText(label + " schedule runbook details are shown for the selected account when this tab is enabled.");
        t.setContent(content);
        tabs.getTabs().add(t);
        tabIndex.put(scheduleCode, t);
        tabCodes.put(t, scheduleCode);
    }

    private void loadAccounts()
    {
        status.setText("Loading accounts...");

        UiAsync.run("schedule-accounts-load",
            this::loadDbAccounts,
            accounts -> {
                if (!accounts.isEmpty())
                {
                    accountSelect.getItems().setAll(accounts);
                    status.setText("Loaded " + accounts.size() + " account(s).");
                    accountSelect.getSelectionModel().select(0);
                    return;
                }
                status.setText("No active posting accounts found. Seed chart data to enable schedules.");
            },
            ex -> status.setText("Could not load accounts: " + UiErrors.safeMessage(ex)));
    }

    private List<Account> loadDbAccounts()
    {
        AccountLookupService lookup = UiServiceRegistry.accountLookup();
        return lookup.listActivePostingAccounts();
    }

    private void applyGating(Account account)
    {
        for (Tab t : tabs.getTabs())
        {
            t.setDisable(true);
            if (t.getContent() instanceof TextArea ta)
            {
                ta.setText("Select an account to view schedule details.");
            }
        }

        if (account == null)
        {
            status.setText("No account selected.");
            return;
        }

        Set<String> allowed = eligibility.allowedScheduleKindCodes(account);
        for (String code : allowed)
        {
            Tab t = tabIndex.get(code);
            if (t != null)
            {
                t.setDisable(false);
                if (t.getContent() instanceof TextArea ta)
                {
                    ta.setText("Account: " + account.getCode() + " — " + account.getName() +
                            "\nSubtype: " + account.getSubtype() +
                            "\nSchedule kind: " + code +
                            "\n\nUse lifecycle actions above to record open/settle/write-off transitions.");
                }
            }
        }

        status.setText("Enabled " + allowed.size() + " schedule tab(s) for account " + account.getCode() + ".");
    }

    private void recordScheduleAction(String action)
    {
        Account selected = accountSelect.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select an account before recording schedule actions.");
            return;
        }
        Tab activeTab = tabs.getSelectionModel().getSelectedItem();
        if (activeTab == null || activeTab.isDisable())
        {
            status.setText("Select an enabled schedule tab before recording an action.");
            return;
        }
        String scheduleKind = tabCodes.getOrDefault(activeTab, "UNKNOWN");
        String line = formatRunbookEntry(action, scheduleKind, selected.getCode(), selected.getName(), LocalDateTime.now());
        UiWorkspaceDataStore.appendScheduleRunbookEntry(line);
        runbook.getItems().setAll(UiWorkspaceDataStore.scheduleRunbookEntries());
        status.setText("Recorded " + action + " for schedule " + scheduleKind + " on account " + selected.getCode() + ".");
    }

    static String formatRunbookEntry(String action, String scheduleKind, String accountCode, String accountName, LocalDateTime at)
    {
        return at.format(TS) + " | " + action + " | " + scheduleKind + " | " + accountCode + " | " + accountName;
    }

    @Override public Node root() { return root; }
}
