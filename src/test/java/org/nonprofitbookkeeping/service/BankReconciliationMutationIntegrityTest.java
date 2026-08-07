package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.ClearedStatePolicy;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.ImportStatementCommand;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.ManualStatementLineCommand;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.SessionStatus;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.StartCommand;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.StatementSource;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.SuccessorCommand;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BankReconciliationMutationIntegrityTest
{
    private static final long CHECKING_CBA = 3001L;
    private static final long SAVINGS_CBA = 3002L;
    private static final long STATEMENT = 7001L;
    private static final long SAVINGS_STATEMENT = 7002L;
    private static final long OTHER_STATEMENT = 7101L;
    private static final long BANK_SPLIT = 5001L;
    private static final long SAVINGS_SPLIT = 5005L;
    private static final long OTHER_SPLIT = 5101L;

    @Test
    void finalizedSessionRejectsEveryLiveMutationAndLoadIsReadOnly(@TempDir Path tempDir)
    {
        Path db = tempDir.resolve("finalized-read-only");
        long sessionId;
        Object finalizedUpdatedAt;
        long statementCount;
        long matchCount;
        try (Jpa jpa = new Jpa(db))
        {
            seed(jpa);
            BankReconciliationWorkspaceService service = new BankReconciliationWorkspaceService(jpa);
            sessionId = finalizedMarch(service);
            finalizedUpdatedAt = scalar(jpa,
                    "select updated_at from bank_reconciliation_session where id = " + sessionId);
            statementCount = count(jpa, "bank_statement_line");
            matchCount = count(jpa, "bank_reconciliation_match");

            assertReadOnly(() -> service.addManualLine(new ManualStatementLineCommand(
                    sessionId, LocalDate.of(2026, 3, 20), new BigDecimal("1.00"), "late", "manual")));
            assertReadOnly(() -> service.importStatementText(new ImportStatementCommand(
                    sessionId,
                    StatementSource.CSV,
                    "late.csv",
                    "date,amount,description,reference\n2026-03-20,1.00,Late,row-1\n")));
            assertReadOnly(() -> service.autoMatch(sessionId));
            assertReadOnly(() -> service.matchSelected(sessionId, STATEMENT, BANK_SPLIT, true));
            assertReadOnly(() -> service.unmatchSelected(sessionId, STATEMENT, BANK_SPLIT));
            assertReadOnly(() -> service.markCleared(sessionId, BANK_SPLIT));
            assertReadOnly(() -> service.recordDifferenceExplanation(
                    sessionId, STATEMENT, null, "Late explanation"));
            assertReadOnly(() -> service.save(sessionId, false));

            assertEquals(SessionStatus.FINALIZED, service.save(sessionId, true).status());
            assertEquals(SessionStatus.FINALIZED, service.load(sessionId).status());
            assertEquals(finalizedUpdatedAt, scalar(jpa,
                    "select updated_at from bank_reconciliation_session where id = " + sessionId));
            assertEquals(statementCount, count(jpa, "bank_statement_line"));
            assertEquals(matchCount, count(jpa, "bank_reconciliation_match"));
        }

        try (Jpa restarted = new Jpa(db))
        {
            BankReconciliationWorkspaceService service = new BankReconciliationWorkspaceService(restarted);
            assertEquals(SessionStatus.FINALIZED, service.load(sessionId).status());
            assertEquals(finalizedUpdatedAt, scalar(restarted,
                    "select updated_at from bank_reconciliation_session where id = " + sessionId));
            assertReadOnly(() -> service.markCleared(sessionId, BANK_SPLIT));
        }
    }

    @Test
    void unmatchRequiresExactSymmetricPairAndClearsBothRelationshipSides(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("symmetric-unmatch")))
        {
            seed(jpa);
            seedSecondCheckingTransaction(jpa);
            BankReconciliationWorkspaceService service = new BankReconciliationWorkspaceService(jpa);
            long sessionId = startMarch(service);
            service.matchSelected(sessionId, STATEMENT, BANK_SPLIT, true);

            IllegalStateException broken = assertThrows(IllegalStateException.class,
                    () -> service.unmatchSelected(sessionId, STATEMENT, 5003L));
            assertTrue(broken.getMessage().contains("symmetric match"));
            assertEquals(1L, relationshipCount(jpa, sessionId, STATEMENT, BANK_SPLIT));
            try (var em = jpa.em())
            {
                assertEquals(4001L, em.find(BankStatementLine.class, STATEMENT)
                        .getMatchedTransaction().getId());
                assertEquals(STATEMENT, em.find(TxnSplit.class, BANK_SPLIT)
                        .getMatchedBankStatementLine().getId());
            }

            service.unmatchSelected(sessionId, STATEMENT, BANK_SPLIT);
            assertEquals(0L, relationshipCount(jpa, sessionId, STATEMENT, BANK_SPLIT));
            try (var em = jpa.em())
            {
                BankStatementLine line = em.find(BankStatementLine.class, STATEMENT);
                TxnSplit split = em.find(TxnSplit.class, BANK_SPLIT);
                assertNull(line.getMatchedTransaction());
                assertEquals(BankStatementLine.Status.IMPORTED, line.getStatus());
                assertNull(split.getMatchedBankStatementLine());
                assertTrue(split.isBankCleared(),
                        "Unmatch removes the relationship but does not rewrite an established cleared fact");
            }
        }
    }

    @Test
    void crossAccountAndCrossCompanyIdsAreRejectedWithoutMutation(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("scope-rejection")))
        {
            seed(jpa);
            BankReconciliationWorkspaceService service = new BankReconciliationWorkspaceService(jpa);
            long sessionId = startMarch(service);
            long matchCount = count(jpa, "bank_reconciliation_match");

            assertThrows(IllegalArgumentException.class,
                    () -> service.matchSelected(sessionId, SAVINGS_STATEMENT, BANK_SPLIT, true));
            assertThrows(IllegalArgumentException.class,
                    () -> service.markCleared(sessionId, SAVINGS_SPLIT));
            assertThrows(IllegalArgumentException.class,
                    () -> service.recordDifferenceExplanation(
                            sessionId, OTHER_STATEMENT, null, "wrong company"));
            assertThrows(RuntimeException.class,
                    () -> service.matchSelected(sessionId, STATEMENT, OTHER_SPLIT, true));

            assertEquals(matchCount, count(jpa, "bank_reconciliation_match"));
            try (var em = jpa.em())
            {
                assertNull(em.find(BankStatementLine.class, STATEMENT).getMatchedTransaction());
                assertNull(em.find(TxnSplit.class, BANK_SPLIT).getMatchedBankStatementLine());
                assertFalse(em.find(TxnSplit.class, SAVINGS_SPLIT).isBankCleared());
            }
        }
    }

    @Test
    void differenceExplanationIsFactualOnlyAndDoesNotReserveMatchRelationship(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("factual-explanation")))
        {
            seed(jpa);
            BankReconciliationWorkspaceService service = new BankReconciliationWorkspaceService(jpa);
            long sessionId = startMarch(service);
            long txnCount = count(jpa, "txn");
            long splitCount = count(jpa, "txn_split");

            service.recordDifferenceExplanation(sessionId, STATEMENT, null,
                    "Bank description differs; no accounting correction is needed.");

            assertEquals(txnCount, count(jpa, "txn"));
            assertEquals(splitCount, count(jpa, "txn_split"));
            assertEquals(1L, countWhere(jpa, "bank_reconciliation_match",
                    "session_id = " + sessionId + " and match_status = 'RESOLVED'"));
            assertEquals(0L, relationshipCount(jpa, sessionId, STATEMENT, BANK_SPLIT));
            try (var em = jpa.em())
            {
                assertNull(em.find(BankStatementLine.class, STATEMENT).getMatchedTransaction());
                assertFalse(em.find(TxnSplit.class, BANK_SPLIT).isBankCleared());
            }

            service.autoMatch(sessionId);
            assertTrue(relationshipCount(jpa, sessionId, STATEMENT, BANK_SPLIT) > 0,
                    "Factual RESOLVED notes must not reserve a statement/split from later matching");
            try (var em = jpa.em())
            {
                assertEquals(4001L, em.find(BankStatementLine.class, STATEMENT)
                        .getMatchedTransaction().getId());
                assertEquals(STATEMENT, em.find(TxnSplit.class, BANK_SPLIT)
                        .getMatchedBankStatementLine().getId());
            }
        }
    }

    @Test
    void successorCreatesNewMutableSessionAndAuditWithoutChangingFinalizedPredecessor(@TempDir Path tempDir)
    {
        Path db = tempDir.resolve("successor-audit");
        long predecessorId;
        long successorId;
        Object predecessorUpdatedAt;
        try (Jpa jpa = new Jpa(db))
        {
            seed(jpa);
            BankReconciliationWorkspaceService service = new BankReconciliationWorkspaceService(jpa);
            predecessorId = finalizedMarch(service);
            predecessorUpdatedAt = scalar(jpa,
                    "select updated_at from bank_reconciliation_session where id = " + predecessorId);

            var successor = service.startSuccessor(new SuccessorCommand(
                    predecessorId,
                    LocalDate.of(2026, 4, 30),
                    new BigDecimal("-25.75"),
                    ClearedStatePolicy.OVERWRITE_LEDGER_CLEARED_STATE,
                    "April successor",
                    "Owner Tester",
                    "Continue with the next bank statement without reopening March."));
            successorId = successor.sessionId();

            assertNotEquals(predecessorId, successorId);
            assertEquals(SessionStatus.IN_PROGRESS, successor.status());
            assertEquals(LocalDate.of(2026, 4, 1), successor.statementStartDate());
            assertEquals(SessionStatus.FINALIZED, service.load(predecessorId).status());
            assertEquals(predecessorUpdatedAt, scalar(jpa,
                    "select updated_at from bank_reconciliation_session where id = " + predecessorId));
            assertEquals(1L, countWhere(jpa, "audit_event",
                    "action_type = 'RECONCILIATION_SUCCESSOR_STARTED' and entity_id = '" + successorId + "'"));
            String beforeValue = String.valueOf(scalar(jpa,
                    "select before_value from audit_event where action_type = 'RECONCILIATION_SUCCESSOR_STARTED'"));
            assertTrue(beforeValue.contains(String.valueOf(predecessorId)));
            assertThrows(IllegalStateException.class,
                    () -> service.startSuccessor(new SuccessorCommand(
                            successorId,
                            LocalDate.of(2026, 5, 31),
                            new BigDecimal("-25.75"),
                            null,
                            null,
                            "Owner Tester",
                            "Cannot branch from a mutable session.")));
        }

        try (Jpa restarted = new Jpa(db))
        {
            BankReconciliationWorkspaceService service = new BankReconciliationWorkspaceService(restarted);
            assertEquals(SessionStatus.FINALIZED, service.load(predecessorId).status());
            assertEquals(SessionStatus.IN_PROGRESS, service.load(successorId).status());
        }
    }

    private static long startMarch(BankReconciliationWorkspaceService service)
    {
        return service.start(new StartCommand(
                "SCA",
                CHECKING_CBA,
                LocalDate.of(2026, 3, 31),
                new BigDecimal("-25.75"),
                ClearedStatePolicy.OVERWRITE_LEDGER_CLEARED_STATE,
                "March reconciliation")).sessionId();
    }

    private static long finalizedMarch(BankReconciliationWorkspaceService service)
    {
        long sessionId = startMarch(service);
        service.matchSelected(sessionId, STATEMENT, BANK_SPLIT, true);
        assertEquals(SessionStatus.FINALIZED, service.save(sessionId, true).status());
        return sessionId;
    }

    private static void assertReadOnly(Runnable action)
    {
        IllegalStateException ex = assertThrows(IllegalStateException.class, action::run);
        assertTrue(ex.getMessage().contains("read-only"));
    }

    private static void seed(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (100, 'SCA Chart', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO company (id, code, display_name, active_chart_of_accounts_id) VALUES (200, 'SCA', 'SCA Branch', 100)").executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = 200 WHERE id = 100").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (1001, 100, '1000', 'Checking', 'BANK', 'CASH', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (1002, 100, '5000', 'Expense', 'EXPENSE', NULL, 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (1003, 100, '1010', 'Savings', 'BANK', 'CASH', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, company_id, code, name, fund_type) VALUES (2001, 200, 'UNR', 'Unrestricted', 'UNRESTRICTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO bank (id, company_id, name) VALUES (2001, 200, 'Example Bank')").executeUpdate();
            em.createNativeQuery("INSERT INTO company_bank_account (id, company_id, name, bank_id, account_id, opening_date, opening_balance, statement_import_format) VALUES (3001, 200, 'Operating Checking', 2001, 1001, DATE '2026-03-01', 0.0000, 'CSV')").executeUpdate();
            em.createNativeQuery("INSERT INTO company_bank_account (id, company_id, name, bank_id, account_id, opening_date, opening_balance, statement_import_format) VALUES (3002, 200, 'Savings', 2001, 1003, DATE '2026-03-01', 0.0000, 'CSV')").executeUpdate();

            em.createNativeQuery("INSERT INTO txn (id, company_id, txn_date, memo, status) VALUES (4001, 200, DATE '2026-03-15', 'Office supplies', 'ENTERED')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (5001, 4001, 1001, 2001, -25.7500)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (5002, 4001, 1002, 2001, 25.7500)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn (id, company_id, txn_date, memo, status) VALUES (4003, 200, DATE '2026-03-20', 'Savings activity', 'ENTERED')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (5005, 4003, 1003, 2001, -7.0000)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (5006, 4003, 1002, 2001, 7.0000)").executeUpdate();

            em.createNativeQuery("INSERT INTO bank_import_batch (id, company_id, bank_account_id, source_name, source_format, status, total_line_count) VALUES (6001, 200, 3001, 'march.csv', 'CSV', 'IMPORTED', 1)").executeUpdate();
            em.createNativeQuery("INSERT INTO bank_statement_line (id, batch_id, company_id, bank_account_id, source_row_number, deterministic_fingerprint, transaction_date, posted_date, amount, status) VALUES (7001, 6001, 200, 3001, 1, 'fp-checking', DATE '2026-03-15', DATE '2026-03-16', -25.7500, 'IMPORTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO bank_import_batch (id, company_id, bank_account_id, source_name, source_format, status, total_line_count) VALUES (6002, 200, 3002, 'savings.csv', 'CSV', 'IMPORTED', 1)").executeUpdate();
            em.createNativeQuery("INSERT INTO bank_statement_line (id, batch_id, company_id, bank_account_id, source_row_number, deterministic_fingerprint, transaction_date, posted_date, amount, status) VALUES (7002, 6002, 200, 3002, 1, 'fp-savings', DATE '2026-03-20', DATE '2026-03-20', -7.0000, 'IMPORTED')").executeUpdate();

            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (110, 'Other Chart', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO company (id, code, display_name, active_chart_of_accounts_id) VALUES (210, 'OTHER', 'Other Branch', 110)").executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = 210 WHERE id = 110").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (1101, 110, '1000', 'Other Checking', 'BANK', 'CASH', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (1102, 110, '5000', 'Other Expense', 'EXPENSE', NULL, 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, company_id, code, name, fund_type) VALUES (2101, 210, 'UNR', 'Other Unrestricted', 'UNRESTRICTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO bank (id, company_id, name) VALUES (2101, 210, 'Other Bank')").executeUpdate();
            em.createNativeQuery("INSERT INTO company_bank_account (id, company_id, name, bank_id, account_id, opening_date, opening_balance, statement_import_format) VALUES (3101, 210, 'Other Checking', 2101, 1101, DATE '2026-03-01', 0.0000, 'CSV')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn (id, company_id, txn_date, memo, status) VALUES (4101, 210, DATE '2026-03-17', 'Other expense', 'ENTERED')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (5101, 4101, 1101, 2101, -3.0000)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (5102, 4101, 1102, 2101, 3.0000)").executeUpdate();
            em.createNativeQuery("INSERT INTO bank_import_batch (id, company_id, bank_account_id, source_name, source_format, status, total_line_count) VALUES (6101, 210, 3101, 'other.csv', 'CSV', 'IMPORTED', 1)").executeUpdate();
            em.createNativeQuery("INSERT INTO bank_statement_line (id, batch_id, company_id, bank_account_id, source_row_number, deterministic_fingerprint, transaction_date, posted_date, amount, status) VALUES (7101, 6101, 210, 3101, 1, 'fp-other', DATE '2026-03-17', DATE '2026-03-17', -3.0000, 'IMPORTED')").executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static void seedSecondCheckingTransaction(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO txn (id, company_id, txn_date, memo, status) VALUES (4002, 200, DATE '2026-03-18', 'Second checking line', 'ENTERED')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (5003, 4002, 1001, 2001, -10.0000)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (5004, 4002, 1002, 2001, 10.0000)").executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static long relationshipCount(Jpa jpa, long sessionId, long statementId, long splitId)
    {
        return countWhere(jpa, "bank_reconciliation_match",
                "session_id = " + sessionId
                        + " and statement_line_id = " + statementId
                        + " and txn_split_id = " + splitId
                        + " and match_status in ('MATCHED','AMOUNT_MISMATCH','DATE_MISMATCH','CLEARED_STATE_MISMATCH')");
    }

    private static long count(Jpa jpa, String table)
    {
        return countWhere(jpa, table, "1=1");
    }

    private static long countWhere(Jpa jpa, String table, String predicate)
    {
        try (var em = jpa.em())
        {
            return ((Number) em.createNativeQuery(
                    "select count(*) from " + table + " where " + predicate).getSingleResult()).longValue();
        }
    }

    private static Object scalar(Jpa jpa, String sql)
    {
        try (var em = jpa.em())
        {
            return em.createNativeQuery(sql).getSingleResult();
        }
    }
}
