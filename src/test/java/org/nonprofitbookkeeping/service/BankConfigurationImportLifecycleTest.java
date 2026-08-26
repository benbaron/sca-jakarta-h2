package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BankConfigurationImportLifecycleTest
{
    @Test
    public void importCannotCreateActiveConfiguredAccountUnderInactiveBank(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-import-lifecycle")))
        {
            seedCompanyAndAccount(jpa);
            BankConfigurationService service = new BankConfigurationService(jpa);
            Bank bank = service.createBank(new BankCommand(
                    "SCA", "Inactive Import Bank", null, null, null, null, null, null, null, false));

            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                Company company = em.createQuery(
                                "select c from Company c where c.code = :code", Company.class)
                        .setParameter("code", "SCA")
                        .getSingleResult();
                Bank managedBank = em.find(Bank.class, bank.getId());
                Account account = em.find(Account.class, 101L);
                BankAccountImportCommand command = new BankAccountImportCommand(
                        "Imported Checking", "Checking", "Inactive Import Bank", "BANK", "1234", "****1234",
                        null, BankingDataFormat.OFX, null, null, BigDecimal.ZERO, true, null);

                IllegalStateException ex = assertThrows(IllegalStateException.class,
                        () -> service.createBankAccountForImport(
                                em, company, managedBank, account, command, UUID.randomUUID()));

                assertEquals(
                        "An active configured bank account must belong to an active Bank. Reactivate the Bank first.",
                        ex.getMessage());
                em.getTransaction().rollback();
            }

            assertEquals(0, service.listBankAccounts("SCA").size());
        }
    }

    private static void seedCompanyAndAccount(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery(
                            "INSERT INTO chart_of_accounts (id, name, version, status) VALUES (101, 'SCA Chart', '1', 'ACTIVE')")
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO company (code, display_name, active_chart_of_accounts_id) VALUES ('SCA', 'SCA Branch', 101)")
                    .executeUpdate();
            em.createNativeQuery(
                            "UPDATE chart_of_accounts SET company_id = (SELECT id FROM company WHERE code = 'SCA') WHERE id = 101")
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) "
                                    + "VALUES (101, 101, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }
}
