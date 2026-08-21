package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionClearedStateProjectionTest
{
    private static final String COMPANY = "SCA";
    private static final LocalDate DATE = LocalDate.of(2026, 7, 15);

    @Test
    void projectsExactBankLineStatesDatesAndReconciliationAfterRestart(@TempDir Path tempDir)
    {
        Path database = tempDir.resolve("journal-cleared-state");
        long nonBankTransactionId;
        long clearedTransactionId;
        long mixedTransactionId;
        try (Jpa jpa = new Jpa(database))
        {
            seed(jpa);
            TransactionEntryService service = new TransactionEntryService(jpa, () -> COMPANY);

            TransactionView nonBank = service.enter(transaction(
                    "Non-bank entry",
                    null,
                    List.of(line(201L, "25.0000", true), line(301L, "25.0000", false))));
            nonBankTransactionId = nonBank.id();
            assertEquals(TransactionView.ClearedState.NOT_BANK, nonBank.clearedState());
            assertTrue(nonBank.lines().stream().noneMatch(TransactionView.Line::bankAccount));

            TransactionView uncleared = service.enter(transaction(
                    "Single bank line",
                    101L,
                    List.of(line(101L, "40.0000", true), line(301L, "40.0000", false))));
            clearedTransactionId = uncleared.id();
            assertEquals(TransactionView.ClearedState.UNCLEARED, uncleared.clearedState());
            TransactionView.Line unclearedBankLine = bankLine(uncleared, 101L);
            assertFalse(unclearedBankLine.bankCleared());
            assertNull(unclearedBankLine.bankClearedOn());

            long clearedSplitId = unclearedBankLine.id();
            markClearedAndMatched(jpa, clearedSplitId, 901L, DATE.plusDays(1));
            TransactionView cleared = service.load(clearedTransactionId);
            assertEquals(TransactionView.ClearedState.CLEARED, cleared.clearedState());
            TransactionView.Line clearedBankLine = bankLine(cleared, 101L);
            assertTrue(clearedBankLine.bankCleared());
            assertEquals(DATE.plusDays(1), clearedBankLine.bankClearedOn());
            assertEquals(901L, clearedBankLine.reconciliationSessionId());

            TransactionView mixed = service.enter(transaction(
                    "Two bank lines",
                    101L,
                    List.of(
                            line(101L, "60.0000", true),
                            line(102L, "40.0000", true),
                            line(301L, "100.0000", false))));
            mixedTransactionId = mixed.id();
            markCleared(jpa, bankLine(mixed, 101L).id(), DATE.plusDays(2));

            TransactionView oneCleared = service.load(mixedTransactionId);
            assertEquals(TransactionView.ClearedState.MIXED, oneCleared.clearedState());
            assertTrue(bankLine(oneCleared, 101L).bankCleared());
            assertFalse(bankLine(oneCleared, 102L).bankCleared());

            markCleared(jpa, bankLine(oneCleared, 102L).id(), DATE.plusDays(3));
            assertEquals(TransactionView.ClearedState.CLEARED, service.load(mixedTransactionId).clearedState());
        }

        try (Jpa reopened = new Jpa(database))
        {
            TransactionEntryService service = new TransactionEntryService(reopened, () -> COMPANY);
            assertEquals(TransactionView.ClearedState.NOT_BANK, service.load(nonBankTransactionId).clearedState());
            TransactionView cleared = service.load(clearedTransactionId);
            assertEquals(TransactionView.ClearedState.CLEARED, cleared.clearedState());
            assertEquals(DATE.plusDays(1), bankLine(cleared, 101L).bankClearedOn());
            assertEquals(901L, bankLine(cleared, 101L).reconciliationSessionId());
            assertEquals(TransactionView.ClearedState.CLEARED, service.load(mixedTransactionId).clearedState());
        }
    }

    private static TransactionCommand transaction(
            String memo,
            Long headerBankAccountId,
            List<TransactionLineCommand> lines)
    {
        return new TransactionCommand(DATE, null, memo, headerBankAccountId, lines);
    }

    private static TransactionLineCommand line(long accountId, String amount, boolean debit)
    {
        BigDecimal value = new BigDecimal(amount);
        return new TransactionLineCommand(
                accountId,
                501L,
                null,
                null,
                null,
                debit ? value : BigDecimal.ZERO,
                debit ? BigDecimal.ZERO : value,
                false,
                null);
    }

    private static TransactionView.Line bankLine(TransactionView view, long accountId)
    {
        return view.lines().stream()
                .filter(line -> line.accountId() == accountId)
                .findFirst()
                .orElseThrow();
    }

    private static void markCleared(Jpa jpa, long splitId, LocalDate clearedOn)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("update txn_split set bank_cleared = true, bank_cleared_on = ? where id = ?")
                    .setParameter(1, java.sql.Date.valueOf(clearedOn))
                    .setParameter(2, splitId)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static void markClearedAndMatched(Jpa jpa, long splitId, long sessionId, LocalDate clearedOn)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("update txn_split set bank_cleared = true, bank_cleared_on = ? where id = ?")
                    .setParameter(1, java.sql.Date.valueOf(clearedOn))
                    .setParameter(2, splitId)
                    .executeUpdate();
            em.createNativeQuery("""
                    insert into bank_reconciliation_match
                        (session_id, txn_split_id, match_status)
                    values (?, ?, 'MATCHED')
                    """)
                    .setParameter(1, sessionId)
                    .setParameter(2, splitId)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("insert into chart_of_accounts (id, name, version, status) values (401, 'SCA Chart', '1', 'ACTIVE')")
                    .executeUpdate();
            em.createNativeQuery("insert into company (id, code, display_name, active_chart_of_accounts_id) values (401, ?, 'SCA Branch', 401)")
                    .setParameter(1, COMPANY)
                    .executeUpdate();
            em.createNativeQuery("update chart_of_accounts set company_id = 401 where id = 401").executeUpdate();
            em.createNativeQuery("insert into fund (id, company_id, code, name, fund_type) values (501, 401, 'GENERAL', 'General', 'UNRESTRICTED')")
                    .executeUpdate();
            em.createNativeQuery("insert into account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) values (101, 401, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .executeUpdate();
            em.createNativeQuery("insert into account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) values (102, 401, '1010', 'Savings', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .executeUpdate();
            em.createNativeQuery("insert into account (id, chart_id, code, name, account_type, normal_balance) values (201, 401, '5000', 'Expense', 'EXPENSE', 'DEBIT')")
                    .executeUpdate();
            em.createNativeQuery("insert into account (id, chart_id, code, name, account_type, normal_balance) values (301, 401, '4000', 'Income', 'INCOME', 'CREDIT')")
                    .executeUpdate();
            em.createNativeQuery("insert into company_bank_account (id, company_id, name, account_type, account_id) values (801, 401, 'Checking', 'CHECKING', 101)")
                    .executeUpdate();
            em.createNativeQuery("""
                    insert into bank_reconciliation_session
                        (id, company_id, bank_account_id, statement_start_date, statement_end_date,
                         mismatch_policy, status)
                    values (901, 401, 801, DATE '2026-07-01', DATE '2026-07-31', 'WARN_ONLY', 'FINALIZED')
                    """).executeUpdate();
            em.getTransaction().commit();
        }
    }
}
