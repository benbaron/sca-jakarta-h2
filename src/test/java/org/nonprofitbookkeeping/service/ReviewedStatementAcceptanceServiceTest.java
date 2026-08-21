package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.bank.BankReviewQueryService;
import org.nonprofitbookkeeping.interchange.bank.BankStatementReviewService;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.ImportIssue;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReviewedStatementAcceptanceServiceTest
{
    private static final Path FIXTURE = Path.of(
            "src/test/resources/data-exchange/bank-statement/ofx/valid/ofx2-checking.xml");

    @Test
    public void explicitAcceptanceCreatesExactlyOneCanonicalTransactionAndDurableLink(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("review-accept-success")))
        {
            Seed seed = seedReviewedRows(jpa);
            ReviewedStatementAcceptanceService service = service(jpa, () -> "SCA");
            ReviewedStatementAcceptanceService.AcceptancePreview preview = service.preview(seed.statementLineId());
            TransactionCommand command = balancedCommand(preview);

            assertTrue(preview.eligible());
            assertEquals(0L, count(jpa, "select count(t) from Txn t"));

            ReviewedStatementAcceptanceService.AcceptanceResult result =
                    service.accept(preview, command, false, "Owner Tester");

            assertFalse(result.reusedExisting());
            assertEquals(1L, count(jpa, "select count(t) from Txn t"));
            assertEquals(2L, count(jpa, "select count(s) from TxnSplit s"));
            assertEquals(1L, count(jpa, "select count(a) from AuditEvent a where a.actionType = 'BANK_STATEMENT_ROW_ACCEPTED'"));
            try (var em = jpa.em())
            {
                BankStatementLine line = em.find(BankStatementLine.class, seed.statementLineId());
                assertEquals(BankStatementLine.Status.ACCEPTED, line.getStatus());
                assertNotNull(line.getAcceptedTransaction());
                assertEquals(result.transactionId(), line.getAcceptedTransaction().getId());
                assertNull(line.getMatchedTransaction());
                BigDecimal bankAmount = em.createQuery("""
                                select s.amountSigned from TxnSplit s
                                where s.txn.id = :txnId and s.account.id = :accountId
                                """, BigDecimal.class)
                        .setParameter("txnId", result.transactionId())
                        .setParameter("accountId", preview.ledgerAccountId())
                        .getSingleResult();
                assertEquals(preview.amount().setScale(4), bankAmount);
            }

            ReviewedStatementAcceptanceService.AcceptanceResult retry =
                    service.accept(preview, command, false, "Owner Tester");
            assertTrue(retry.reusedExisting());
            assertEquals(result.transactionId(), retry.transactionId());
            assertEquals(1L, count(jpa, "select count(t) from Txn t"));
            assertEquals(1L, count(jpa, "select count(a) from AuditEvent a where a.actionType = 'BANK_STATEMENT_ROW_ACCEPTED'"));

            BankReviewQueryService.ReviewRow row = new BankReviewQueryService(jpa).listRows("SCA").stream()
                    .filter(value -> value.statementLineId() == seed.statementLineId())
                    .findFirst().orElseThrow();
            assertEquals(result.transactionId(), row.acceptedTransactionId());
        }
    }

    @Test
    public void injectedLateFailureRollsBackTransactionLinkStateAndAcceptanceAudit(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("review-accept-rollback")))
        {
            Seed seed = seedReviewedRows(jpa);
            TransactionEntryService entry = new TransactionEntryService(jpa, () -> "SCA");
            ReviewedStatementAcceptanceService service = new ReviewedStatementAcceptanceService(
                    jpa, entry, () -> "SCA", () -> { throw new IllegalStateException("injected late failure"); });
            ReviewedStatementAcceptanceService.AcceptancePreview preview = service.preview(seed.statementLineId());

            assertThrows(IllegalStateException.class,
                    () -> service.accept(preview, balancedCommand(preview), false, "Owner Tester"));
            assertEquals(0L, count(jpa, "select count(t) from Txn t"));
            assertEquals(0L, count(jpa, "select count(a) from AuditEvent a where a.actionType = 'TRANSACTION_ENTERED'"));
            assertEquals(0L, count(jpa, "select count(a) from AuditEvent a where a.actionType = 'BANK_STATEMENT_ROW_ACCEPTED'"));
            try (var em = jpa.em())
            {
                BankStatementLine line = em.find(BankStatementLine.class, seed.statementLineId());
                assertEquals(BankStatementLine.Status.IMPORTED, line.getStatus());
                assertNull(line.getAcceptedTransaction());
            }
        }
    }

    @Test
    public void probableDuplicateRequiresExplicitConfirmationButExactDuplicateIsBlocked(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("review-accept-duplicates")))
        {
            Seed seed = seedReviewedRows(jpa);
            addIssue(jpa, seed.statementLineId(), ImportIssue.Severity.WARNING,
                    "PROBABLE_DUPLICATE", "Probable duplicate for owner review.");
            ReviewedStatementAcceptanceService service = service(jpa, () -> "SCA");
            ReviewedStatementAcceptanceService.AcceptancePreview preview = service.preview(seed.statementLineId());
            assertTrue(preview.eligible());
            assertTrue(preview.probableDuplicate());

            assertThrows(IllegalStateException.class,
                    () -> service.accept(preview, balancedCommand(preview), false, "Owner Tester"));
            assertEquals(0L, count(jpa, "select count(t) from Txn t"));
            assertFalse(service.accept(preview, balancedCommand(preview), true, "Owner Tester").reusedExisting());
        }

        try (Jpa jpa = new Jpa(tempDir.resolve("review-accept-exact-duplicate")))
        {
            Seed seed = seedReviewedRows(jpa);
            setLineStatus(jpa, seed.statementLineId(), BankStatementLine.Status.DUPLICATE);
            ReviewedStatementAcceptanceService service = service(jpa, () -> "SCA");
            ReviewedStatementAcceptanceService.AcceptancePreview preview = service.preview(seed.statementLineId());
            assertFalse(preview.eligible());
            assertTrue(preview.eligibilityMessage().toLowerCase().contains("duplicate"));
            assertThrows(IllegalStateException.class,
                    () -> service.accept(preview, balancedCommand(preview), false, "Owner Tester"));
            assertEquals(0L, count(jpa, "select count(t) from Txn t"));
        }
    }

    @Test
    public void closedPeriodFinalizedReconciliationAndCompanyChangeAreRevalidatedBeforeCommit(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("review-accept-guards")))
        {
            Seed seed = seedReviewedRows(jpa);
            AtomicReference<String> activeCompany = new AtomicReference<>("SCA");
            ReviewedStatementAcceptanceService service = service(jpa, activeCompany::get);
            ReviewedStatementAcceptanceService.AcceptancePreview preview = service.preview(seed.statementLineId());

            activeCompany.set("OTHER");
            assertThrows(IllegalStateException.class,
                    () -> service.accept(preview, balancedCommand(preview), false, "Owner Tester"));
            assertEquals(0L, count(jpa, "select count(t) from Txn t"));
            activeCompany.set("SCA");

            new PeriodCloseRangeService(jpa).closeRange(
                    "SCA", preview.effectiveSourceDate(), preview.effectiveSourceDate(),
                    "CUSTOM", "Owner Tester", "Test close protection");
            assertThrows(RuntimeException.class,
                    () -> service.accept(preview, balancedCommand(preview), false, "Owner Tester"));
            assertEquals(0L, count(jpa, "select count(t) from Txn t"));
        }

        try (Jpa jpa = new Jpa(tempDir.resolve("review-accept-finalized-reconciliation")))
        {
            Seed seed = seedReviewedRows(jpa);
            ReviewedStatementAcceptanceService service = service(jpa, () -> "SCA");
            ReviewedStatementAcceptanceService.AcceptancePreview before = service.preview(seed.statementLineId());
            LocalDate sourceDate = before.effectiveSourceDate();
            insertFinalizedReconciliation(jpa, seed.bankAccountId(), sourceDate);
            ReviewedStatementAcceptanceService.AcceptancePreview protectedPreview = service.preview(seed.statementLineId());
            assertFalse(protectedPreview.eligible());
            assertTrue(protectedPreview.eligibilityMessage().toLowerCase().contains("finalized"));
            assertThrows(IllegalStateException.class,
                    () -> service.accept(protectedPreview, balancedCommand(protectedPreview), false, "Owner Tester"));
            assertEquals(0L, count(jpa, "select count(t) from Txn t"));
        }

        try (Jpa jpa = new Jpa(tempDir.resolve("review-accept-command-date-finalized")))
        {
            Seed seed = seedReviewedRows(jpa);
            ReviewedStatementAcceptanceService service = service(jpa, () -> "SCA");
            ReviewedStatementAcceptanceService.AcceptancePreview preview = service.preview(seed.statementLineId());
            LocalDate protectedDate = preview.effectiveSourceDate().minusDays(1);
            insertFinalizedReconciliation(jpa, seed.bankAccountId(), protectedDate);
            TransactionCommand base = balancedCommand(preview);
            TransactionCommand commandInFinalizedRange = new TransactionCommand(
                    protectedDate, base.payeeId(), base.memo(), base.bankAccountId(), base.lines());
            assertThrows(IllegalStateException.class,
                    () -> service.accept(preview, commandInFinalizedRange, false, "Owner Tester"));
            assertEquals(0L, count(jpa, "select count(t) from Txn t"));
        }
    }

    private static ReviewedStatementAcceptanceService service(Jpa jpa, java.util.function.Supplier<String> company)
    {
        return new ReviewedStatementAcceptanceService(
                jpa, new TransactionEntryService(jpa, company), company);
    }

    private static TransactionCommand balancedCommand(ReviewedStatementAcceptanceService.AcceptancePreview preview)
    {
        BigDecimal amount = preview.amount().abs();
        boolean positive = preview.amount().signum() > 0;
        long counterAccount = positive ? 102L : 103L;
        TransactionLineCommand bank = new TransactionLineCommand(
                preview.ledgerAccountId(), 101L, null, null, null,
                positive ? amount : BigDecimal.ZERO,
                positive ? BigDecimal.ZERO : amount,
                false, "bank source");
        TransactionLineCommand counter = new TransactionLineCommand(
                counterAccount, 101L, null, null, null,
                positive ? BigDecimal.ZERO : amount,
                positive ? amount : BigDecimal.ZERO,
                false, "counter split");
        return new TransactionCommand(
                preview.effectiveSourceDate(), null, "Accepted reviewed row", preview.ledgerAccountId(),
                List.of(bank, counter));
    }

    private static Seed seedReviewedRows(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (101, 'SCA Chart', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO company (code, display_name, default_currency, active_chart_of_accounts_id) VALUES ('SCA', 'SCA Branch', 'USD', 101)").executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = (SELECT id FROM company WHERE code = 'SCA') WHERE id = 101").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) VALUES (101, 101, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (102, 101, '4000', 'Event Income', 'INCOME', 'CREDIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (103, 101, '5000', 'Event Expense', 'EXPENSE', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, company_id, code, name, fund_type) VALUES (101, (SELECT id FROM company WHERE code = 'SCA'), 'OPERATING', 'Operating', 'UNRESTRICTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (201, 'Other Chart', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO company (code, display_name, default_currency, active_chart_of_accounts_id) VALUES ('OTHER', 'Other Branch', 'USD', 201)").executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = (SELECT id FROM company WHERE code = 'OTHER') WHERE id = 201").executeUpdate();
            em.getTransaction().commit();
        }
        BankConfigurationService configuration = new BankConfigurationService(jpa);
        Bank bank = configuration.createBank(new BankCommand(
                "SCA", "Example Bank", "999000111", null, null, null, null, null, null, true));
        CompanyBankAccount account = configuration.createBankAccount(new BankAccountCommand(
                "SCA", bank.getId(), 101L, "****4321", "Operating Checking",
                LocalDate.of(2026, 1, 1), BigDecimal.ZERO, BankingDataFormat.OFX,
                "999000111", "FICTIONAL-4321", null, true));
        new BankStatementReviewService(jpa)
                .commit(new BankStatementReviewService(jpa).preview(FIXTURE, "SCA", account.getId()), false, "Owner Tester");
        try (var em = jpa.em())
        {
            long lineId = em.createQuery(
                            "select l.id from BankStatementLine l where l.company.code = 'SCA' and l.status = :status order by l.id",
                            Long.class)
                    .setParameter("status", BankStatementLine.Status.IMPORTED)
                    .setMaxResults(1)
                    .getSingleResult();
            return new Seed(account.getId(), lineId);
        }
    }

    private static void addIssue(Jpa jpa, long lineId, ImportIssue.Severity severity, String code, String message)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            BankStatementLine line = em.find(BankStatementLine.class, lineId);
            ImportIssue issue = new ImportIssue();
            issue.setBatch(line.getBatch());
            issue.setStatementLine(line);
            issue.setSourceRowNumber(line.getSourceRowNumber());
            issue.setSeverity(severity);
            issue.setCode(code);
            issue.setMessage(message);
            em.persist(issue);
            em.getTransaction().commit();
        }
    }

    private static void setLineStatus(Jpa jpa, long lineId, BankStatementLine.Status status)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            BankStatementLine line = em.find(BankStatementLine.class, lineId);
            line.setStatus(status);
            line.touchUpdatedAt();
            em.getTransaction().commit();
        }
    }

    private static void insertFinalizedReconciliation(Jpa jpa, long bankAccountId, LocalDate date)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("""
                    insert into bank_reconciliation_session
                        (company_id, bank_account_id, statement_start_date, statement_end_date,
                         statement_ending_balance, mismatch_policy, status)
                    values ((select id from company where code = 'SCA'), ?, ?, ?, 0, 'WARN_ONLY', 'FINALIZED')
                    """)
                    .setParameter(1, bankAccountId)
                    .setParameter(2, date)
                    .setParameter(3, date)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static long count(Jpa jpa, String jpql)
    {
        try (var em = jpa.em())
        {
            return em.createQuery(jpql, Long.class).getSingleResult();
        }
    }

    private record Seed(long bankAccountId, long statementLineId) { }
}
