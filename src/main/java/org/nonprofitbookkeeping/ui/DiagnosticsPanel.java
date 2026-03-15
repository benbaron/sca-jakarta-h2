package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.AccountLookupService;
import org.nonprofitbookkeeping.service.FundLookupService;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Basic runtime diagnostics center for operator troubleshooting.
 */
public class DiagnosticsPanel implements AppPanel
{
    private final VBox root = new VBox(8);
    private final Label runtime = new Label();
    private final Label javaVersion = new Label();
    private final Label activeCompany = new Label();
    private final Label activeDatabase = new Label();
    private final Label datasource = new Label();
    private final Label accountQuality = new Label();
    private final Label fundQuality = new Label();
    private final Label qualitySummary = new Label();
    private final Label duplicateSummary = new Label();
    private final Label status = new Label();
    private final Button reviewAccountDuplicates = new Button("Review account duplicates");
    private final Button reviewFundDuplicates = new Button("Review fund duplicates");

    public DiagnosticsPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Diagnostics Center");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh Diagnostics");
        refresh.setOnAction(e -> reload());

        reviewAccountDuplicates.setDisable(true);
        reviewFundDuplicates.setDisable(true);
        reviewAccountDuplicates.setOnAction(e -> DrillThroughCoordinator.openPanelWithContext(
                AppPanelId.CHART_OF_ACCOUNTS,
                "Diagnostics drill-through: review duplicate account codes."));
        reviewFundDuplicates.setOnAction(e -> DrillThroughCoordinator.openPanelWithContext(
                AppPanelId.FUNDS,
                "Diagnostics drill-through: review duplicate fund codes."));

        root.getChildren().addAll(
                title,
                refresh,
                reviewAccountDuplicates,
                reviewFundDuplicates,
                new Separator(),
                runtime,
                javaVersion,
                activeCompany,
                activeDatabase,
                datasource,
                accountQuality,
                fundQuality,
                qualitySummary,
                duplicateSummary,
                status);

        reload();
    }

    @Override
    public String title()
    {
        return "Diagnostics";
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override
    public void onNew()
    {
        reload();
    }

    private void reload()
    {
        runtime.setText("Runtime timestamp: " + Instant.now());
        javaVersion.setText("Java version: " + System.getProperty("java.version"));
        activeCompany.setText("Active company: " + MainWindow.sharedSessionState().multiCompany().activeCompanyCode());

        String db = MainWindow.sharedSessionState().databaseSelection().activeDatabasePath();
        activeDatabase.setText("Active database file: " + Path.of(db).toAbsolutePath());

        try
        {
            UiDataSources.forCurrentSessionDatabase().getConnection().close();
            datasource.setText("Datasource check: OK");

            AccountLookupService accountLookup = UiServiceRegistry.accountLookup();
            FundLookupService fundLookup = UiServiceRegistry.fundLookup();

            List<org.nonprofitbookkeeping.model.Account> accounts = accountLookup.listPostingAccountsIncludingInactive();
            List<org.nonprofitbookkeeping.model.Fund> funds = fundLookup.listAllFunds();

            int activePostingAccounts = accountLookup.listActivePostingAccounts().size();
            int allPostingAccounts = accounts.size();
            int activeFunds = fundLookup.listActiveFunds().size();
            int allFunds = funds.size();

            accountQuality.setText("Accounts quality: active posting=" + activePostingAccounts + ", total posting=" + allPostingAccounts);
            fundQuality.setText("Funds quality: active=" + activeFunds + ", total=" + allFunds);

            Map<String, Integer> duplicateAccounts = duplicateCodes(accounts.stream().map(org.nonprofitbookkeeping.model.Account::getCode).toList());
            Map<String, Integer> duplicateFunds = duplicateCodes(funds.stream().map(org.nonprofitbookkeeping.model.Fund::getCode).toList());

            if (activePostingAccounts == 0 || activeFunds == 0)
            {
                qualitySummary.setText("Quality warning: missing active posting accounts or active funds.");
            }
            else
            {
                qualitySummary.setText("Quality checks: OK");
            }

            reviewAccountDuplicates.setDisable(duplicateAccounts.isEmpty());
            reviewFundDuplicates.setDisable(duplicateFunds.isEmpty());

            if (!duplicateAccounts.isEmpty() || !duplicateFunds.isEmpty())
            {
                duplicateSummary.setText("Duplicate-code warning: accounts=" + duplicateAccounts.keySet() + ", funds=" + duplicateFunds.keySet());
            }
            else
            {
                duplicateSummary.setText("Duplicate-code checks: OK");
            }

            status.setText("Diagnostics refreshed.");
        }
        catch (Exception ex)
        {
            datasource.setText("Datasource check: FAILED");
            accountQuality.setText("Accounts quality: unavailable");
            fundQuality.setText("Funds quality: unavailable");
            qualitySummary.setText("Quality checks: unavailable");
            duplicateSummary.setText("Duplicate-code checks: unavailable");
            reviewAccountDuplicates.setDisable(true);
            reviewFundDuplicates.setDisable(true);
            status.setText("Datasource issue: " + UiErrors.safeMessage(ex));
        }
    }

    static Map<String, Integer> duplicateCodes(List<String> codes)
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String code : codes)
        {
            if (code == null || code.isBlank())
            {
                continue;
            }
            counts.merge(code, 1, Integer::sum);
        }

        Map<String, Integer> duplicates = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet())
        {
            if (entry.getValue() > 1)
            {
                duplicates.put(entry.getKey(), entry.getValue());
            }
        }
        return duplicates;
    }
}
