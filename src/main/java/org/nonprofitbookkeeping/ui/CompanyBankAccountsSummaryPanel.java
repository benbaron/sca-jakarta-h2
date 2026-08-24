package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.CompanyBankAccount;

import java.util.function.Function;

/**
 * Read-only company bank-account summary backed by BankConfigurationService.
 *
 * This intentionally reuses the configured bank-account model that is tested by
 * BankConfigurationServiceTest rather than introducing a parallel company-bank
 * property model.
 */
final class CompanyBankAccountsSummaryPanel extends BorderPane
{
    private final TableView<CompanyBankAccount> accounts = new TableView<>();
    private final Label status = new Label("Ready.");

    CompanyBankAccountsSummaryPanel()
    {
        build();
        refresh();
    }

    private void build()
    {
        setPadding(new Insets(8));
        Label title = new Label("Configured Bank Accounts");
        title.getStyleClass().add("panel-title");
        Label help = new Label("Bank accounts shown here are the existing configured bank accounts managed by BankConfigurationService. Use the Banking workspace to create or edit banks and configured accounts.");
        help.setWrapText(true);
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refresh());
        setTop(new VBox(6, title, help, new HBox(8, refresh, status)));

        accounts.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        accounts.setPlaceholder(new Label("No configured bank accounts for the active company."));
        accounts.getColumns().setAll(
                column("Name", CompanyBankAccount::getName, 190),
                column("Nickname", CompanyBankAccount::getNickname, 160),
                column("Bank", account -> account.getBank() == null ? "" : account.getBank().getName(), 190),
                column("Ledger Account", account -> account.getAccount() == null ? "" : account.getAccount().getCode() + " — " + account.getAccount().getName(), 260),
                column("Type", CompanyBankAccount::getAccountType, 120),
                column("Last Four", CompanyBankAccount::getLastFour, 100),
                column("Import Format", account -> account.getStatementImportFormat() == null ? "" : account.getStatementImportFormat().name(), 130),
                column("Active", account -> account.isActive() ? "Yes" : "No", 90));
        VBox.setVgrow(accounts, Priority.ALWAYS);
        setCenter(accounts);
    }

    private TableColumn<CompanyBankAccount, String> column(
            String title,
            Function<CompanyBankAccount, String> extractor,
            double preferredWidth)
    {
        TableColumn<CompanyBankAccount, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(nullToBlank(extractor.apply(data.getValue()))));
        column.setMinWidth(80);
        column.setPrefWidth(preferredWidth);
        column.setSortable(true);
        column.setResizable(true);
        return column;
    }

    private void refresh()
    {
        String companyCode = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        try
        {
            accounts.setItems(FXCollections.observableArrayList(
                    UiServiceRegistry.bankConfiguration().listBankAccounts(companyCode)));
            status.setText("Loaded configured bank accounts for " + companyCode + ".");
        }
        catch (RuntimeException ex)
        {
            accounts.getItems().clear();
            status.setText("Could not load configured bank accounts: " + UiErrors.safeMessage(ex));
        }
    }

    private static String nullToBlank(String value)
    {
        return value == null ? "" : value;
    }
}
