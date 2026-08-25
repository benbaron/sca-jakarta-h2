package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** H2 regression for P05-C8 configured-bank canonical ledger activity. */
class BankLedgerActivityQueryIntegrationTest
{
    @Test
    void listsOnlyCanonicalSplitsForConfiguredBankAccountsAndKeepsHistoricalInactiveAccountActivity(
            @TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-ledger-activity")))
        {
            seed(jpa);
            LedgerQueryService service = new LedgerQueryService(jpa);

            List<LedgerQueryService.BankLedgerRow> all =
                    service.listBankLedgerActivity("SCA", null, 100);

            assertEquals(2, all.size());

            LedgerQueryService.BankLedgerRow restrictedDeposit = all.get(0);
            assertEquals(303L, restrictedDeposit.transactionId());
            assertEquals(202L, restrictedDeposit.configuredBankAccountId());
            assertEquals("Restricted Deposit", restrictedDeposit.configuredBankAccountName());
            assertEquals("1050", restrictedDeposit.accountCode());
            assertEquals(new BigDecimal("100.0000"), restrictedDeposit.debit());
            assertEquals(BigDecimal.ZERO, restrictedDeposit.credit());
            assertFalse(restrictedDeposit.cleared());

            LedgerQueryService.BankLedgerRow checking = all.get(1);
            assertEquals(301L, checking.transactionId());
            assertEquals(201L, checking.configuredBankAccountId());
            assertEquals(new BigDecimal("25.7500"), checking.credit());
            assertEquals(BigDecimal.ZERO, checking.debit());
            assertTrue(checking.cleared());
            assertEquals(LocalDate.of(2026, 3, 16), checking.clearedOn());

            List<LedgerQueryService.BankLedgerRow> selected =
                    service.listBankLedgerActivity("SCA", 201L, 100);
            assertEquals(1, selected.size());
            assertEquals(401L, selected.get(0).splitId());

            // Transaction 302 uses an ASSET/BANK/DEBIT account that is deliberately
            // not linked through CompanyBankAccount, so it is not configured-bank activity.
            assertTrue(all.stream().noneMatch(row -> row.transactionId() == 302L));
        }
    }

    private static void seed(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) "
                    + "VALUES (101, 'SCA Chart', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO company (id, code, display_name, active_chart_of_accounts_id) "
                    + "VALUES (201, 'SCA', 'SCA Branch', 101)").executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = 201 WHERE id = 101").executeUpdate();
            em.createNativeQuery("INSERT INTO account "
                    + "(id, chart_id, code, name, account_type, account_function, subtype, normal_balance) "
                    + "VALUES (101, 101, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO account "
                    + "(id, chart_id, code, name, account_type, subtype, normal_balance) "
                    + "VALUES (102, 101, '5000', 'Expense', 'EXPENSE', NULL, 'DEBIT')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO account "
                    + "(id, chart_id, code, name, account_type, account_function, subtype, normal_balance) "
                    + "VALUES (103, 101, '1040', 'Unconfigured Bank Asset', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO account "
                    + "(id, chart_id, code, name, account_type, account_function, subtype, normal_balance) "
                    + "VALUES (104, 101, '1050', 'Restricted Deposit', 'ASSET', 'BANK', 'OTHER_ASSET', 'DEBIT')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, code, name, fund_type) "
                    + "VALUES (201, 'UNR', 'Unrestricted', 'UNRESTRICTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO bank (id, company_id, name) VALUES (201, 201, 'Example Bank')")
                    .executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO company_bank_account
                        (id, company_id, name, bank_id, account_id, statement_import_format, is_active)
                    VALUES (201, 201, 'Operating Checking', 201, 101, 'CSV', TRUE)
                    """).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO company_bank_account
                        (id, company_id, name, bank_id, account_id, statement_import_format, is_active)
                    VALUES (202, 201, 'Restricted Deposit', 201, 104, 'OFX', FALSE)
                    """).executeUpdate();

            em.createNativeQuery("INSERT INTO txn (id, company_id, txn_date, memo, status) "
                    + "VALUES (301, 201, DATE '2026-03-15', 'Office supplies', 'ENTERED')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split "
                    + "(id, txn_id, account_id, fund_id, amount_signed, bank_cleared, bank_cleared_on) "
                    + "VALUES (401, 301, 101, 201, -25.7500, TRUE, DATE '2026-03-16')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) "
                    + "VALUES (402, 301, 102, 201, 25.7500)").executeUpdate();

            em.createNativeQuery("INSERT INTO txn (id, company_id, txn_date, memo, status) "
                    + "VALUES (302, 201, DATE '2026-03-16', 'Unconfigured bank-like account', 'ENTERED')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) "
                    + "VALUES (403, 302, 103, 201, -10.0000)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) "
                    + "VALUES (404, 302, 102, 201, 10.0000)").executeUpdate();

            em.createNativeQuery("INSERT INTO txn (id, company_id, txn_date, memo, status) "
                    + "VALUES (303, 201, DATE '2026-03-17', 'Restricted deposit', 'ENTERED')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) "
                    + "VALUES (405, 303, 104, 201, 100.0000)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) "
                    + "VALUES (406, 303, 102, 201, -100.0000)").executeUpdate();
            em.getTransaction().commit();
        }
    }
}
