package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.CompanyTaxProfile;
import org.nonprofitbookkeeping.service.CompanyAdminService;

import java.util.List;

/** Company administration foundation panel. */
public class CompanyAdminPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<Company> companies = new TableView<>();
    private final TableView<CompanyBankAccount> bankAccounts = new TableView<>();
    private final Label status = new Label("Ready.");

    private final TextField code = new TextField();
    private final TextField displayName = new TextField();
    private final TextField legalName = new TextField();
    private final TextField branchType = new TextField();
    private final TextField parentOrganization = new TextField();
    private final CheckBox active = new CheckBox("Active");

    public CompanyAdminPanel()
    {
        build();
        refresh();
    }

    private void build()
    {
        Label title = new Label("Company Admin");
        title.getStyleClass().add("panel-title");
        Label help = new Label("Manage company profile, current company context, chart of accounts ownership, bank accounts, tax settings, and reporting defaults.");
        help.setWrapText(true);
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> refresh());
        Button save = new Button("Save Company");
        save.setOnAction(e -> saveCompany());
        HBox actions = new HBox(8, refresh, save, status);
        VBox header = new VBox(6, title, help, actions);
        header.setPadding(new Insets(8));
        root.setTop(header);

        configureCompaniesTable();
        configureBankAccountsTable();

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(6);
        form.setPadding(new Insets(8));
        int r = 0;
        form.addRow(r++, new Label("Code"), code);
        form.addRow(r++, new Label("Display Name"), displayName);
        form.addRow(r++, new Label("Legal Name"), legalName);
        form.addRow(r++, new Label("Branch Type"), branchType);
        form.addRow(r++, new Label("Parent Organization / Kingdom"), parentOrganization);
        form.add(active, 1, r++);
        active.setSelected(true);

        companies.getSelectionModel().selectedItemProperty().addListener((obs, old, next) -> {
            if (next != null) populate(next);
        });

        TabPane tabs = new TabPane();
        tabs.getTabs().add(tab("Companies", new VBox(8, form, companies)));
        tabs.getTabs().add(tab("Bank Accounts", bankAccounts));
        tabs.getTabs().add(tab("Tax / Filing", taxPlaceholder()));
        tabs.getTabs().add(tab("Chart of Accounts", new Label("Company-owned chart-of-accounts assignment is modeled on Company.activeChartOfAccounts and will be wired to the Chart of Accounts panel in a later slice.")));
        tabs.getTabs().add(tab("Reporting Defaults", new Label("Fiscal year and reporting defaults are stored on Company; richer defaults will be added later.")));
        root.setCenter(tabs);
    }

    private Tab tab(String label, Node content)
    {
        Tab tab = new Tab(label, content);
        tab.setClosable(false);
        return tab;
    }

    private Node taxPlaceholder()
    {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8));
        box.getChildren().add(new Label("Tax profile fields are modeled by CompanyTaxProfile: EIN, jurisdiction, filing name, filing address, and notes."));
        Label current = new Label("Select a company to view tax profile summary.");
        companies.getSelectionModel().selectedItemProperty().addListener((obs, old, company) -> {
            if (company == null)
            {
                current.setText("Select a company to view tax profile summary.");
                return;
            }
            CompanyTaxProfile profile = UiServiceRegistry.companyAdmin().taxProfile(company.getCode());
            current.setText(profile == null ? "No tax profile yet for " + company.getCode() + "." : "Tax profile exists for " + company.getCode() + ".");
        });
        box.getChildren().add(current);
        return box;
    }

    private void configureCompaniesTable()
    {
        companies.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        TableColumn<Company, String> c1 = col("Code", c -> c.getCode());
        TableColumn<Company, String> c2 = col("Display Name", c -> c.getDisplayName());
        TableColumn<Company, String> c3 = col("Legal Name", c -> c.getLegalName());
        TableColumn<Company, String> c4 = col("Branch Type", c -> c.getBranchType());
        TableColumn<Company, String> c5 = col("Parent Organization", c -> c.getParentOrganization());
        TableColumn<Company, String> c6 = col("Currency", c -> c.getDefaultCurrency());
        companies.getColumns().setAll(c1, c2, c3, c4, c5, c6);
        companies.setMinHeight(280);
    }

    private void configureBankAccountsTable()
    {
        bankAccounts.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        bankAccounts.getColumns().setAll(
                col("Name", b -> b.getName()),
                col("Institution", b -> b.getInstitutionName()),
                col("Type", b -> b.getAccountType()),
                col("Last Four", b -> b.getLastFour()),
                col("Active", b -> String.valueOf(b.isActive()))
        );
        bankAccounts.setMinHeight(280);
    }

    private <T> TableColumn<T, String> col(String title, java.util.function.Function<T, String> extractor)
    {
        TableColumn<T, String> col = new TableColumn<>(title);
        col.setCellValueFactory(data -> new SimpleStringProperty(nullToBlank(extractor.apply(data.getValue()))));
        col.setMinWidth(120);
        col.setPrefWidth(Math.max(140, title.length() * 12));
        return col;
    }

    private void populate(Company c)
    {
        code.setText(c.getCode());
        displayName.setText(c.getDisplayName());
        legalName.setText(nullToBlank(c.getLegalName()));
        branchType.setText(nullToBlank(c.getBranchType()));
        parentOrganization.setText(nullToBlank(c.getParentOrganization()));
        active.setSelected(c.isActive());
        loadBankAccounts(c.getCode());
    }

    private void refresh()
    {
        try
        {
            CompanyAdminService service = UiServiceRegistry.companyAdmin();
            List<Company> rows = service.listCompanies();
            companies.setItems(FXCollections.observableArrayList(rows));
            if (!rows.isEmpty() && companies.getSelectionModel().getSelectedItem() == null)
            {
                companies.getSelectionModel().select(0);
            }
            status.setText("Loaded " + rows.size() + " company row(s).");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not load companies: " + UiErrors.safeMessage(ex));
        }
    }

    private void loadBankAccounts(String companyCode)
    {
        try
        {
            bankAccounts.setItems(FXCollections.observableArrayList(UiServiceRegistry.companyAdmin().listBankAccounts(companyCode)));
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not load bank accounts: " + UiErrors.safeMessage(ex));
        }
    }

    private void saveCompany()
    {
        try
        {
            Company saved = UiServiceRegistry.companyAdmin().upsertCompany(code.getText(), displayName.getText(), legalName.getText(), branchType.getText(), parentOrganization.getText());
            status.setText("Saved company " + saved.getCode() + ".");
            refresh();
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save company: " + UiErrors.safeMessage(ex));
        }
    }

    private String nullToBlank(String value)
    {
        return value == null ? "" : value;
    }

    @Override public String title() { return "Company Admin"; }
    @Override public Node root() { return root; }
    @Override public void onSave() { saveCompany(); }
}
