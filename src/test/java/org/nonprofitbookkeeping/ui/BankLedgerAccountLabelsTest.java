package org.nonprofitbookkeeping.ui;

import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class BankLedgerAccountLabelsTest
{
    @Test
    void detachedChoicesAndConfiguredLinksDistinguishIdenticalAccountsWithoutChangingIds(@TempDir Path directory)
    {
        try (Jpa jpa = new Jpa(directory.resolve("detached-labels")))
        {
            seed(jpa);
            var choices = new AccountLookupService(jpa).listPostingAccountsIncludingInactive();
            assertEquals(2, choices.size());
            var labels = choices.stream().map(BankLedgerAccountLabels::choice).toList();
            assertTrue(labels.stream().allMatch(text -> text.contains("1000 — Operating Checking (account ID ")));
            assertTrue(labels.stream().anyMatch(text -> text.contains("Sample Chart (version P03-S00; chart ID ")));
            assertTrue(labels.stream().anyMatch(text -> text.contains("Caer Galen Chart (version SCLX-1.3; chart ID ")));
            assertNotEquals(labels.get(0), labels.get(1));
            assertTrue(labels.stream().anyMatch(text -> text.contains("[inactive]")));
            var configured = new BankConfigurationService(jpa).listBankAccounts("DEFAULT").get(0);
            assertTrue(BankLedgerAccountLabels.configured(configured).contains("Sample Chart"));
            try (var em = jpa.em())
            {
                var persisted = em.find(CompanyBankAccount.class, configured.getId());
                assertEquals(configured.getAccount().getId(), persisted.getAccount().getId());
                assertEquals("Sample Chart", persisted.getAccount().getChart().getName());
            }
        }
    }

    @Test
    void foreignChartNameIsNotDisclosedForInvalidConfiguredLink(@TempDir Path directory)
    {
        try (Jpa jpa = new Jpa(directory.resolve("foreign-label")))
        {
            seed(jpa);
            var service = new BankConfigurationService(jpa);
            var configured = service.listBankAccounts("DEFAULT").get(0);
            var other = new CompanyAdminService(jpa).createCompany("OTHER", "Other company");
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                var chart = chart(em.find(Company.class, other.id()), "PRIVATE FOREIGN CHART", "secret");
                em.persist(chart);
                var foreign = account(chart, true);
                em.persist(foreign);
                em.find(CompanyBankAccount.class, configured.getId()).setAccount(foreign);
                em.getTransaction().commit();
            }
            String text = BankLedgerAccountLabels.configured(service.listBankAccounts("DEFAULT").get(0));
            assertTrue(text.contains("Chart ownership requires review"));
            assertFalse(text.contains("PRIVATE FOREIGN CHART"));
            assertFalse(text.contains("secret"));
            assertEquals(2, new AccountLookupService(jpa).listPostingAccountsIncludingInactive().size());
            assertEquals("", BankLedgerAccountLabels.choice(null));
            assertEquals("No ledger account linked", BankLedgerAccountLabels.configured(new CompanyBankAccount()));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shownBankingChoicesAndSelectedDetailExposeChartIdentity(@TempDir Path directory)
    {
        FxTestSupport.initToolkitOrSkip();
        var session = MainWindow.sharedSessionState();
        var priorDatabase = session.databaseSelection();
        var priorCompany = session.multiCompany();
        var priorUser = session.authenticatedUser();
        Path database = directory.resolve("banking-label-ui");
        try (Jpa jpa = new Jpa(database))
        {
            seed(jpa);
        }
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);
        try
        {
            FxTestSupport.onFx(() ->
            {
                javafx.application.Platform.setImplicitExit(false);
                session.setAuthenticatedUser(UiPermissionTestSessions.admin());
                var panel = new BankingPanel();
                var stage = new Stage();
                stage.setScene(new Scene((javafx.scene.Parent) panel.root(), 760, 700));
                try
                {
                    stage.show();
                    ((TabPane) panel.root().lookup("#bankingWorkPanes")).getSelectionModel().select(1);
                    panel.root().applyCss();
                    ((javafx.scene.Parent) panel.root()).layout();
                    var selector = (ComboBox<Account>) panel.root().lookup("#bankingExistingLedgerAccount");
                    assertEquals(2, selector.getItems().size());
                    var detail = (Label) panel.root().lookup("#bankingSelectedLedgerDetails");
                    for (var account : selector.getItems())
                    {
                        selector.setValue(account);
                        assertEquals(account.getId(), selector.getValue().getId());
                        assertTrue(selector.getConverter().toString(account).contains(account.getChart().getName()));
                        assertEquals(BankLedgerAccountLabels.choice(account), detail.getText());
                    }
                    assertTrue(detail.isWrapText());
                    ((javafx.scene.layout.Region) panel.root()).resize(430, 700);
                    ((javafx.scene.Parent) panel.root()).layout();
                    assertTrue(detail.getWidth() <= selector.getWidth() + 1);
                    assertTrue(detail.getHeight() > detail.getFont().getSize() * 1.5,
                            "Full selected identity must wrap across readable lines");
                    selector.show();
                    panel.root().applyCss();
                    assertTrue(selector.isShowing());
                    var popupTexts = new java.util.ArrayList<String>();
                    for (var window : javafx.stage.Window.getWindows())
                    {
                        if (window != stage && window.isShowing() && window.getScene() != null)
                        {
                            window.getScene().getRoot().applyCss();
                            window.getScene().getRoot().layout();
                            for (var node : window.getScene().getRoot().lookupAll(".list-cell"))
                            {
                                if (node instanceof javafx.scene.control.ListCell<?> popupCell && popupCell.getItem() instanceof Account)
                                {
                                    assertTrue(popupCell.isWrapText());
                                    popupTexts.add(popupCell.getText());
                                }
                            }
                        }
                    }
                    assertTrue(popupTexts.stream().anyMatch(text -> text.contains("Sample Chart")));
                    assertTrue(popupTexts.stream().anyMatch(text -> text.contains("Caer Galen Chart")));
                    selector.hide();
                    var rows = (TableView<CompanyBankAccount>) CompanyTableStateBinder.findTables(panel.root()).get(1);
                    String cell = String.valueOf(rows.getColumns().stream().filter(column -> "chart".equals(column.getId()))
                            .findFirst().orElseThrow().getCellObservableValue(0).getValue());
                    assertTrue(cell.contains("Sample Chart"));
                    assertTrue(cell.contains("P03-S00"));
                    var activeColumn = rows.getColumns().stream().filter(column -> "active".equals(column.getId()))
                            .findFirst().orElseThrow();
                    assertEquals("Active", activeColumn.getText());
                    assertEquals("Yes", activeColumn.getCellObservableValue(0).getValue());
                    var inactive = new CompanyBankAccount();
                    inactive.setActive(false);
                    rows.getItems().add(inactive);
                    assertEquals("No", activeColumn.getCellObservableValue(1).getValue());
                    assertTrue(activeColumn.isSortable());
                    assertTrue(activeColumn.isResizable());
                    assertTrue(activeColumn.isReorderable());
                    return null;
                }
                finally
                {
                    stage.close();
                }
            });
            assertEquals("Sample Chart", UiServiceRegistry.bankConfiguration().listBankAccounts("DEFAULT")
                    .get(0).getAccount().getChart().getName(), "Selecting a choice must not save or relink it");
        }
        finally
        {
            session.setDatabaseSelection(priorDatabase);
            session.setMultiCompany(priorCompany);
            UiServiceRegistry.reconnectToDatabase(Path.of(priorDatabase.activeDatabasePath()));
            FxTestSupport.onFx(() ->
            {
                if (priorUser.isPresent())
                {
                    session.setAuthenticatedUser(priorUser.get());
                }
                else
                {
                    session.clearAuthenticatedUser();
                }
                return null;
            });
        }
    }

    private static void seed(Jpa jpa)
    {
        var company = new CompanyAdminService(jpa).requireActiveCompany("DEFAULT");
        Long priorAccount;
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            var owner = em.find(Company.class, company.id());
            var sample = chart(owner, "Sample Chart", "P03-S00");
            var current = chart(owner, "Caer Galen Chart", "SCLX-1.3");
            em.persist(sample);
            em.persist(current);
            var oldAccount = account(sample, true);
            em.persist(oldAccount);
            em.persist(account(current, false));
            owner.setActiveChartOfAccounts(current);
            em.flush();
            priorAccount = oldAccount.getId();
            em.getTransaction().commit();
        }
        var service = new BankConfigurationService(jpa);
        var bank = service.createBank(new BankCommand("DEFAULT", "Fixture Bank", null, null, null, null, null, null, null, true));
        service.createBankAccount(new BankAccountCommand("DEFAULT", bank.getId(), priorAccount, "1234", "Operating Checking",
                null, BigDecimal.ZERO, BankingDataFormat.OFX, null, null, null, true));
    }

    private static ChartOfAccounts chart(Company owner, String name, String version)
    {
        var chart = new ChartOfAccounts();
        chart.setCompany(owner);
        chart.setName(name);
        chart.setVersion(version);
        chart.setStatus(ChartStatus.ACTIVE);
        return chart;
    }

    private static Account account(ChartOfAccounts chart, boolean active)
    {
        var account = new Account();
        account.setChart(chart);
        account.setCode("1000");
        account.setName("Operating Checking");
        account.setAccountType(AccountType.ASSET);
        account.setAccountFunction(AccountFunction.BANK);
        account.setSubtype(AccountSubtype.CASH);
        account.setNormalBalance(NormalBalance.DEBIT);
        account.setPosting(true);
        account.setActive(active);
        return account;
    }
}
