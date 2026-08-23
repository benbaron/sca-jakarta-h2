package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BankConfigurationServiceTest
{
    @Test
    public void createBankAccountLinksBankAndQualifyingChartAccount(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-config")))
        {
            seedCompanyAndAccounts(jpa);
            BankConfigurationService service = new BankConfigurationService(jpa);

            Bank bank = service.createBank(new BankCommand(
                    "SCA", "Example Credit Union", "123456789", "1 Main St", "https://bank.example",
                    "Pat Teller", "555-0100", "pat@example.test", "Primary institution", true));
            CompanyBankAccount account = service.createBankAccount(new BankAccountCommand(
                    "SCA", bank.getId(), 101L, "****1234", "Operating Checking", LocalDate.of(2026, 1, 1),
                    new BigDecimal("25.0000"), BankingDataFormat.CSV, "OFX-BANK", "OFX-ACCT", "Main operating account", true));

            assertNotNull(bank.getId());
            assertNotNull(account.getId());
            assertEquals(bank.getId(), account.getBank().getId());
            assertEquals(101L, account.getAccount().getId());
            assertEquals("Operating Checking", account.getNickname());
            assertEquals("1234", account.getLastFour());
            assertEquals(BankingDataFormat.CSV, account.getStatementImportFormat());
        }
    }

    @Test
    public void createBankAccountRejectsAccountWithoutBankFunction(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-config-invalid")))
        {
            seedCompanyAndAccounts(jpa);
            BankConfigurationService service = new BankConfigurationService(jpa);
            Bank bank = service.createBank(new BankCommand("SCA", "Example Bank", null, null, null, null, null, null, null, true));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.createBankAccount(new BankAccountCommand(
                            "SCA", bank.getId(), 102L, "2222", "Expense", null,
                            BigDecimal.ZERO, BankingDataFormat.OFX, null, null, null, true)));

            assertEquals("Linked chart account must be an ASSET with BANK function and DEBIT normal balance.",
                    ex.getMessage());
        }
    }

    @Test
    public void createBankAccountAllowsBankFunctionWithoutCashSubtype(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-config-noncash")))
        {
            seedCompanyAndAccounts(jpa);
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("INSERT INTO account "
                        + "(id, chart_id, code, name, account_type, account_function, subtype, normal_balance) "
                        + "VALUES (103, 101, '1050', 'Restricted Deposit', 'ASSET', 'BANK', 'OTHER_ASSET', 'DEBIT')")
                        .executeUpdate();
                em.getTransaction().commit();
            }

            BankConfigurationService service = new BankConfigurationService(jpa);
            Bank bank = service.createBank(new BankCommand(
                    "SCA", "Example Bank", null, null, null, null, null, null, null, true));
            CompanyBankAccount configured = service.createBankAccount(new BankAccountCommand(
                    "SCA", bank.getId(), 103L, "3333", "Restricted Deposit", null,
                    BigDecimal.ZERO, BankingDataFormat.OFX, null, null, null, true));

            assertEquals(103L, configured.getAccount().getId());
            assertEquals("BANK", configured.getAccountType());
        }
    }

    @Test
    public void updateBankAndListConfigurationRows(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-config-list")))
        {
            seedCompanyAndAccounts(jpa);
            BankConfigurationService service = new BankConfigurationService(jpa);
            Bank bank = service.createBank(new BankCommand("SCA", "Old Bank", null, null, null, null, null, null, null, true));

            service.updateBank(bank.getId(), new BankCommand("SCA", "Updated Bank", "987654321", null, null, null, null, null, null, false));
            service.createBankAccount(new BankAccountCommand("SCA", bank.getId(), 101L, "9999", "Reserve", null, BigDecimal.ZERO, BankingDataFormat.QIF, null, null, null, true));

            assertEquals("Updated Bank", service.listBanks("SCA").get(0).getName());
            assertEquals(false, service.listBanks("SCA").get(0).isActive());
            assertEquals(1, service.listBankAccounts("SCA").size());
        }
    }

    private static void seedCompanyAndAccounts(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (101, 'SCA Chart', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO company (code, display_name, active_chart_of_accounts_id) VALUES ('SCA', 'SCA Branch', 101)").executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = (SELECT id FROM company WHERE code = 'SCA') WHERE id = 101").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) VALUES (101, 101, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (102, 101, '5000', 'Program Expense', 'EXPENSE', NULL, 'DEBIT')").executeUpdate();
            em.getTransaction().commit();
        }
    }
}
