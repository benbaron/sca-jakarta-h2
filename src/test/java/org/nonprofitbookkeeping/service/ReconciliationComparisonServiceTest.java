package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.repository.ReconciliationRunRecord;
import org.nonprofitbookkeeping.repository.ReconciliationRunRepository;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReconciliationComparisonServiceTest
{
    @Test
    public void compareConfiguredBankAccountBuildsBalancesIssuesAndSavedReport(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("reconciliation-comparison")))
        {
            seed(jpa);
            CapturingReconciliationRunRepository runRepository = new CapturingReconciliationRunRepository();
            ReconciliationComparisonService service = new ReconciliationComparisonService(
                    jpa,
                    new ReconciliationService(runRepository));

            ReconciliationComparisonReport report = service.compare(new ReconciliationComparisonCommand(
                    "SCA",
                    201L,
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    true));

            assertNotNull(report.savedRunId());
            assertEquals(0, new BigDecimal("105.0000").compareTo(report.beginningBalance()));
            assertEquals(0, new BigDecimal("-35.7500").compareTo(report.activity()));
            assertEquals(0, new BigDecimal("69.2500").compareTo(report.endingBookBalance()));
            assertEquals(1, report.matchedLineCount());
            assertTrue(report.unresolvedCount() >= 3);
            assertTrue(hasKind(report, ReconciliationComparisonLine.Kind.MATCHED));
            assertTrue(hasKind(report, ReconciliationComparisonLine.Kind.CLEARED_STATE_MISMATCH));
            assertTrue(hasKind(report, ReconciliationComparisonLine.Kind.AMOUNT_MISMATCH));
            assertTrue(hasKind(report, ReconciliationComparisonLine.Kind.UNMATCHED_STATEMENT));

            ReconciliationRunRecord saved = runRepository.findById(report.savedRunId()).orElseThrow();
            assertEquals(BankingDataFormat.OFX, saved.bankFormat());
            assertEquals(3, saved.importedTransactionCount());
            assertTrue(saved.notes().contains("UNRESOLVED reconciliation report"));
            assertTrue(saved.notes().contains("unresolved="));
        }
    }

    private static boolean hasKind(ReconciliationComparisonReport report, ReconciliationComparisonLine.Kind kind)
    {
        return report.lines().stream().anyMatch(line -> line.kind() == kind);
    }

    private static void seed(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (101, 'SCA Chart', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO company (id, code, display_name, active_chart_of_accounts_id) VALUES (201, 'SCA', 'SCA Branch', 101)").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) VALUES (101, 101, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (102, 101, '5000', 'Expense', 'EXPENSE', NULL, 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, code, name, fund_type) VALUES (201, 'UNR', 'Unrestricted', 'UNRESTRICTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO bank (id, company_id, name) VALUES (201, 201, 'Example Bank')").executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO company_bank_account
                    (id, company_id, name, bank_id, account_id, statement_import_format, opening_date, opening_balance)
                    VALUES (201, 201, 'Operating Checking', 201, 101, 'CSV', DATE '2026-03-01', 100.0000)
                    """).executeUpdate();
            em.createNativeQuery("INSERT INTO txn (id, txn_date, memo, status) VALUES (300, DATE '2026-02-15', 'Opening activity', 'ENTERED')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (400, 300, 101, 201, 5.0000)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn (id, txn_date, memo, status) VALUES (301, DATE '2026-03-15', 'Office supplies', 'ENTERED')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (401, 301, 101, 201, -25.7500)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (402, 301, 102, 201, 25.7500)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn (id, txn_date, memo, status) VALUES (302, DATE '2026-03-20', 'Unmatched ledger', 'ENTERED')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (403, 302, 101, 201, -10.0000)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (404, 302, 102, 201, 10.0000)").executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO bank_import_batch (id, company_id, bank_account_id, source_name, source_format, status, total_line_count)
                    VALUES (501, 201, 201, 'march.csv', 'CSV', 'IMPORTED', 3)
                    """).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO bank_statement_line (id, batch_id, company_id, bank_account_id, source_row_number, deterministic_fingerprint, transaction_date, posted_date, amount, status)
                    VALUES (501, 501, 201, 201, 1, 'fp-1', DATE '2026-03-15', DATE '2026-03-16', -25.7500, 'IMPORTED')
                    """).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO bank_statement_line (id, batch_id, company_id, bank_account_id, source_row_number, deterministic_fingerprint, transaction_date, posted_date, amount, status)
                    VALUES (502, 501, 201, 201, 2, 'fp-2', DATE '2026-03-20', DATE '2026-03-20', -30.0000, 'IMPORTED')
                    """).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO bank_statement_line (id, batch_id, company_id, bank_account_id, source_row_number, deterministic_fingerprint, transaction_date, posted_date, amount, status)
                    VALUES (503, 501, 201, 201, 3, 'fp-3', DATE '2026-03-25', DATE '2026-03-25', -99.0000, 'IMPORTED')
                    """).executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static final class CapturingReconciliationRunRepository implements ReconciliationRunRepository
    {
        private final List<ReconciliationRunRecord> records = new ArrayList<>();

        @Override
        public void append(ReconciliationRunRecord record)
        {
            records.add(record);
        }

        @Override
        public Optional<ReconciliationRunRecord> findById(UUID id)
        {
            return records.stream().filter(record -> record.id().equals(id)).findFirst();
        }

        @Override
        public List<ReconciliationRunRecord> findByGroupAndDateRange(String groupCode, LocalDate fromDate, LocalDate toDate)
        {
            return records.stream()
                    .filter(record -> record.groupCode().equals(groupCode))
                    .filter(record -> !record.statementEndingOn().isBefore(fromDate))
                    .filter(record -> !record.statementEndingOn().isAfter(toDate))
                    .toList();
        }
    }
}
