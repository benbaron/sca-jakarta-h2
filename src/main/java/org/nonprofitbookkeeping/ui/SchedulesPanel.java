package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.service.AccountLookupService;
import org.nonprofitbookkeeping.service.ScheduleEligibilityService;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * One schedules panel with tabs. Tabs are enabled/disabled based on the selected account's subtype
 * (plus any per-account overrides).
 *
 * This is an intentionally light UI skeleton; actual schedule grids will be added later.
 */
public class SchedulesPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();

    private final ComboBox<Account> accountSelect = new ComboBox<>();
    private final TabPane tabs = new TabPane();

    private final Map<String, Tab> tabIndex = new LinkedHashMap<>();

    private final ScheduleEligibilityService eligibility = UiServiceRegistry.schedules();

    public SchedulesPanel()
    {
        Label title = new Label("Schedules / Outstanding Details");
        title.getStyleClass().add("h1");

        Label help = new Label("Select an account; the applicable schedule tabs will enable (Excel-like gating).");
        help.setWrapText(true);

        configureAccountCombo();

        buildTabs();

        HBox top = new HBox(10, new Label("Account:"), accountSelect);
        top.setPadding(new Insets(6, 6, 6, 6));

        VBox header = new VBox(6, title, help, top, new Separator());
        header.setPadding(new Insets(6, 6, 6, 6));

        root.setTop(header);
        root.setCenter(tabs);

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

        // Default: disabled until account selection happens
        for (Tab t : tabs.getTabs()) t.setDisable(true);
    }

    private void addTab(String scheduleCode, String label)
    {
        Tab t = new Tab(label);
        t.setClosable(false);
        t.setContent(new Label(label + " schedule UI not wired yet."));
        tabs.getTabs().add(t);
        tabIndex.put(scheduleCode, t);
    }

    private void loadAccounts()
    {
        // Preferred: DB
        try
        {
            AccountLookupService lookup = UiServiceRegistry.accountLookup();
            accountSelect.getItems().setAll(lookup.listActivePostingAccounts());
            if (!accountSelect.getItems().isEmpty()) return;
        }
        catch (Throwable ignored)
        {
            // fall back
        }

        // Fallback demo list
        accountSelect.getItems().setAll(
            demoAccount("I.c", "Receivables", AccountSubtype.RECEIVABLE),
            demoAccount("II.b", "Payables", AccountSubtype.PAYABLE),
            demoAccount("I.i", "Prepaid Expenses", AccountSubtype.PREPAID),
            demoAccount("II.c", "Other Liabilities", AccountSubtype.OTHER_LIABILITY),
            demoAccount("I.a", "Checking / Cash", AccountSubtype.CASH)
        );
    }

    private Account demoAccount(String code, String name, AccountSubtype subtype)
    {
        Account a = new Account();
        a.setCode(code);
        a.setName(name);
        a.setSubtype(subtype);
        a.setActive(true);
        a.setPosting(true);
        return a;
    }

    private void applyGating(Account account)
    {
        for (Tab t : tabs.getTabs()) t.setDisable(true);
        if (account == null) return;

        Set<String> allowed = eligibility.allowedScheduleKindCodes(account);
        for (String code : allowed)
        {
            Tab t = tabIndex.get(code);
            if (t != null) t.setDisable(false);
        }
    }

    @Override public Node root() { return root; }
}
