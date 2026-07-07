package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BankClearedStateServiceTest
{
    @Test
    public void mapsReviewedStatementLineToClearedCanonicalBankSplit(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("cleared-state")))
        {
            seed(jpa);
            BankClearedStateService service = new BankClearedStateService(jpa);

            BankClearedStateResult result = service.markMatchedAndCleared(501L, 401L);

            assertEquals(501L, result.statementLineId());
            assertEquals(301L, result.transactionId());
            assertEquals(401L, result.splitId());
            assertEquals(LocalDate.of(2026, 3, 16), result.clearedOn());
            try (var em = jpa.em())
            {
                TxnSplit split = em.find(TxnSplit.class, 401L);
                BankStatementLine line = em.find(BankStatementLine.class, 501L);
                assertTrue(split.isBankCleared());
                assertEquals(LocalDate.of(2026, 3, 16), split.getBankClearedOn());
                assertEquals(501L, split.getMatchedBankStatementLine().getId());
                assertEquals(BankStatementLine.Status.MATCHED, line.getStatus());
                assertEquals(301L, line.getMatchedTransaction().getId());
            }
        }
    }

    @Test
    public void rejectsMatchToNonConfiguredBankLedgerAccount(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("cleared-state-invalid")))
        {
            seed(jpa);
            BankClearedStateService service = new BankClearedStateService(jpa);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.markMatchedAndCleared(501L, 402L));

            assertEquals("Matched split must use the configured bank ledger account.", ex.getMessage());
        }
    }

    private static void seed(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (101, 'SCA Chart', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO company (id, code, display_name, active_chart_of_accounts_id) VALUES (201, 'SCA', 'SCA Branch', 101)").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (101, 101, '1000', 'Checking', 'BANK', 'CASH', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (102, 101, '5000', 'Expense', 'EXPENSE', NULL, 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, code, name, fund_type) VALUES (201, 'UNR', 'Unrestricted', 'UNRESTRICTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO bank (id, company_id, name) VALUES (201, 201, 'Example Bank')").executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO company_bank_account (id, company_id, name, bank_id, account_id, statement_import_format)
                    VALUES (201, 201, 'Operating Checking', 201, 101, 'CSV')
                    """).executeUpdate();
            em.createNativeQuery("INSERT INTO txn (id, txn_date, memo, status) VALUES (301, DATE '2026-03-15', 'Office supplies', 'ENTERED')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (401, 301, 101, 201, -25.7500)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (402, 301, 102, 201, 25.7500)").executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO bank_import_batch (id, company_id, bank_account_id, source_name, source_format, status, total_line_count)
                    VALUES (501, 201, 201, 'march.csv', 'CSV', 'IMPORTED', 1)
                    """).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO bank_statement_line (id, batch_id, company_id, bank_account_id, source_row_number, deterministic_fingerprint, transaction_date, posted_date, amount, status)
                    VALUES (501, 501, 201, 201, 1, 'fp-1', DATE '2026-03-15', DATE '2026-03-16', -25.7500, 'IMPORTED')
                    """).executeUpdate();
            em.getTransaction().commit();
        }
    }
}
