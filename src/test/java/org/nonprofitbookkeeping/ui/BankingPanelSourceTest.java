package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-level guardrails for Banking panel layout and stable-ID account maintenance. */
class BankingPanelSourceTest
{
    @Test
    void financialInstitutionsAndConfiguredAccountsUseTopBottomSplit() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/BankingPanel.java"));

        assertTrue(source.contains("new SplitPane(bankListPane(), bankAccountPane())"));
        assertTrue(source.contains("split.setOrientation(Orientation.VERTICAL)"));
        assertTrue(source.contains("Financial institutions"));
        assertTrue(source.contains("Configured bank accounts"));
        assertTrue(source.contains("bankingInstitutionsSplit"));
        assertTrue(source.contains("bankingAccountsSplit"));
        assertTrue(source.contains("VBox.setVgrow(table, Priority.ALWAYS)"));
        assertTrue(source.contains("CompanySplitPaneStateBinder.bind"));
    }

    @Test
    void configuredBankAccountsLoadAndUpdateByStableId() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/BankingPanel.java"));

        assertTrue(source.contains("private Long editingBankAccountId"));
        assertTrue(source.contains("new Button(\"New Bank Account\")"));
        assertTrue(source.contains("bankAccounts.getSelectionModel().selectedItemProperty().addListener"));
        assertTrue(source.contains("loadBankAccount(newRow)"));
        assertTrue(source.contains("updateBankAccount(editingBankAccountId, command)"));
        assertTrue(source.contains("saveAccount.setText(\"Update Bank Account\")"));
    }

    @Test
    void selectingFinancialInstitutionClearsConfiguredAccountEditState() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/BankingPanel.java"));
        int loadBankStart = source.indexOf("private void loadBank(Bank bank)");
        int loadBankAccountStart = source.indexOf("private void loadBankAccount(", loadBankStart);
        String loadBankSource = source.substring(loadBankStart, loadBankAccountStart);

        assertTrue(loadBankSource.contains("clearAccountForm();"));
        assertTrue(loadBankSource.indexOf("clearAccountForm();")
                < loadBankSource.indexOf("bankSelector.setValue(bankById(bank.getId()));"));
        assertTrue(loadBankSource.contains("accountDirty.markClean();"));
    }

    @Test
    void bankAccountMoneyAndDatesUseCompanyFormatting() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/BankingPanel.java"));

        assertTrue(source.contains("companyFormat().formatMoney(BigDecimal.ZERO)"));
        assertTrue(source.contains("companyFormat().parseMoney(value)"));
        assertTrue(source.contains("companyFormat().parseDate(value)"));
        assertTrue(source.contains("format.formatDate(bankAccount.getOpeningDate())"));
        assertTrue(source.contains("format.formatMoney(bankAccount.getOpeningBalance())"));
        assertFalse(source.contains("openingBalance.setText(\"0.00\")"));
        assertFalse(source.contains("DateTimeFormatter.ISO_LOCAL_DATE"));
    }
}
