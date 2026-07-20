package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.NormalBalance;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("ui-service-registry")
class CoreEditorComplianceTest
{
    @Test
    void everyP14S2EditorReportsChangedFormState(@TempDir Path tempDir)
    {
        Path database = tempDir.resolve("core-editor-compliance");
        UiSessionState session = MainWindow.sharedSessionState();
        DatabaseSelectionState originalDatabase = session.databaseSelection();
        MultiCompanyState originalCompany = session.multiCompany();
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);

        try
        {
            FxTestSupport.onFx(() ->
            {
                AssetsRegisterPanel assets = new AssetsRegisterPanel();
                assets.setNameForTests("Changed asset");
                assertTrue(assets.hasUnsavedChanges());

                BankingPanel banking = new BankingPanel();
                banking.setBankNameForTests("Changed bank");
                assertTrue(banking.hasUnsavedChanges());

                BudgetEditorPanel budget = new BudgetEditorPanel();
                budget.setAmountForTests("123.45");
                assertTrue(budget.hasUnsavedChanges());

                ChartOfAccountsPanel accounts = new ChartOfAccountsPanel();
                accounts.setFormStateForTests(new ChartOfAccountsPanel.FormState(
                        "1999", "Changed account", AccountType.ASSET, NormalBalance.DEBIT,
                        AccountSubtype.OTHER_ASSET, "", true));
                assertTrue(accounts.hasUnsavedChanges());

                SettingsPanel settings = new SettingsPanel(session);
                settings.setActiveDatabaseForTests(database.resolveSibling("changed").toString());
                assertTrue(settings.hasUnsavedChanges());

                UserAdminPanel users = new UserAdminPanel();
                users.setUsernameForTests("changed-user");
                assertTrue(users.hasUnsavedChanges());

                AdministrationPanel administration = new AdministrationPanel();
                administration.settingsForTests().setActiveDatabaseForTests(
                        database.resolveSibling("administration-changed").toString());
                assertTrue(administration.hasUnsavedChanges(),
                        "Administration must aggregate dirty state from non-selected child tabs.");
                return null;
            });
        }
        finally
        {
            session.setDatabaseSelection(originalDatabase);
            session.setMultiCompany(originalCompany);
            UiServiceRegistry.reconnectToDatabase(Path.of(originalDatabase.activeDatabasePath()));
        }
    }
}
