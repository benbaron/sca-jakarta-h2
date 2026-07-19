package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.DiagnosticsQueryService;

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
    private final DiagnosticsQueryService diagnostics;

    public DiagnosticsPanel()
    {
        this(UiServiceRegistry.diagnosticsQuery());
    }

    DiagnosticsPanel(DiagnosticsQueryService diagnostics)
    {
        this.diagnostics = java.util.Objects.requireNonNull(diagnostics, "diagnostics");
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
        DiagnosticsQueryService.Report report = diagnostics.query();
        runtime.setText("Runtime timestamp: " + report.runtimeTimestamp());
        javaVersion.setText("Java version: " + report.javaVersion());
        activeCompany.setText("Active company: " + report.activeCompanyCode());
        activeDatabase.setText("Active database file: " + report.activeDatabasePath());

        if (report.available())
        {
            datasource.setText("Datasource check: OK");
            accountQuality.setText("Accounts quality: active posting=" + report.accounts().active()
                    + ", total posting=" + report.accounts().total());
            fundQuality.setText("Funds quality: active=" + report.funds().active()
                    + ", total=" + report.funds().total());

            if (report.accounts().active() == 0 || report.funds().active() == 0)
            {
                qualitySummary.setText("Quality warning: missing active posting accounts or active funds.");
            }
            else
            {
                qualitySummary.setText("Quality checks: OK");
            }

            reviewAccountDuplicates.setDisable(report.duplicateAccountCodes().isEmpty());
            reviewFundDuplicates.setDisable(report.duplicateFundCodes().isEmpty());

            if (!report.duplicateAccountCodes().isEmpty() || !report.duplicateFundCodes().isEmpty())
            {
                duplicateSummary.setText("Duplicate-code warning: accounts="
                        + report.duplicateAccountCodes().keySet()
                        + ", funds=" + report.duplicateFundCodes().keySet());
            }
            else
            {
                duplicateSummary.setText("Duplicate-code checks: OK");
            }

            status.setText("Diagnostics refreshed.");
        }
        else
        {
            datasource.setText("Datasource check: FAILED");
            accountQuality.setText("Accounts quality: unavailable");
            fundQuality.setText("Funds quality: unavailable");
            qualitySummary.setText("Quality checks: unavailable");
            duplicateSummary.setText("Duplicate-code checks: unavailable");
            reviewAccountDuplicates.setDisable(true);
            reviewFundDuplicates.setDisable(true);
            status.setText("Datasource issue: " + report.failureMessage());
        }
    }
}
