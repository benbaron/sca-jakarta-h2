package org.nonprofitbookkeeping.interchange.bank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.BankAccountCommand;
import org.nonprofitbookkeeping.service.BankCommand;
import org.nonprofitbookkeeping.service.BankConfigurationService;
import org.nonprofitbookkeeping.service.BankImportNormalizationService;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BankStatementReviewServiceTest
{
    private static final Path FIXTURE = Path.of(
            "src/test/resources/data-exchange/bank-statement/ofx/valid/ofx2-checking.xml");

    @Test
    public void previewsAndCommitsCompleteDurableReviewWithoutLedgerWrites(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-statement-review")))
        {
            long accountId = seed(jpa, "FICTIONAL-4321", "****4321");
            BankStatementReviewService service = new BankStatementReviewService(jpa);

            BankStatementReviewPreview preview = service.preview(FIXTURE, "SCA", accountId);
            assertEquals(BankStatementAccountMatcher.Status.EXACT, preview.accountMatchStatus());
            assertEquals(3, preview.lines().size());
            assertTrue(preview.commitAllowed(false));

            BankStatementReviewResult result = service.commit(preview, false, "Owner Tester");
            assertTrue(result.created());
            assertEquals(3, result.totalLineCount());
            assertEquals(3, result.reviewableLineCount());

            try (var em = jpa.em())
            {
                BankImportBatch batch = em.find(BankImportBatch.class, result.batchId());
                assertEquals("OFX_2_XML", batch.getSourceVariant());
                assertEquals("220", batch.getSourceVersion());
                assertEquals("USD", batch.getCurrency());
                assertEquals("FICTIONAL-4321", batch.getSourceAccountId());
                assertEquals(LocalDate.of(2026, 6, 1), batch.getStatementStartDate());
                assertEquals(0L, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
                assertEquals(1L, em.createQuery("""
                        select count(a) from AuditEvent a
                        where a.actionType = 'BANK_STATEMENT_REVIEW_IMPORTED'
                        """, Long.class).getSingleResult());
                assertEquals(3L, em.createQuery("""
                        select count(l) from BankStatementLine l
                        where l.batch.id = :batchId and l.currency = 'USD'
                        """, Long.class).setParameter("batchId", result.batchId()).getSingleResult());
            }

            BankStatementReviewResult identical = service.commit(preview, false, "Owner Tester");
            assertFalse(identical.created());
            assertEquals(result.batchId(), identical.batchId());
            try (var em = jpa.em())
            {
                assertEquals(1L, em.createQuery("select count(b) from BankImportBatch b", Long.class).getSingleResult());
                assertEquals(1L, em.createQuery("""
                        select count(a) from AuditEvent a
                        where a.actionType = 'BANK_STATEMENT_REVIEW_IMPORTED'
                        """, Long.class).getSingleResult());
            }
        }
    }

    @Test
    public void requiresConfirmationForSuffixOnlyConfiguredIdentity(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-statement-suffix")))
        {
            long accountId = seed(jpa, null, "****4321");
            BankStatementReviewService service = new BankStatementReviewService(jpa);
            BankStatementReviewPreview preview = service.preview(FIXTURE, "SCA", accountId);

            assertEquals(BankStatementAccountMatcher.Status.CONFIRMATION_REQUIRED,
                    preview.accountMatchStatus());
            assertFalse(preview.commitAllowed(false));
            assertThrows(IllegalArgumentException.class,
                    () -> service.commit(preview, false, "Owner Tester"));
            assertTrue(service.commit(preview, true, "Owner Tester").created());
        }
    }

    @Test
    public void blocksMismatchedConfiguredAccountBeforeWrite(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-statement-mismatch")))
        {
            long accountId = seed(jpa, "OTHER-ACCOUNT", "****9999");
            BankStatementReviewService service = new BankStatementReviewService(jpa);
            BankStatementReviewPreview preview = service.preview(FIXTURE, "SCA", accountId);

            assertEquals(BankStatementAccountMatcher.Status.BLOCKING, preview.accountMatchStatus());
            assertTrue(preview.hasBlockingMessages());
            assertThrows(IllegalArgumentException.class,
                    () -> service.commit(preview, true, "Owner Tester"));
            try (var em = jpa.em())
            {
                assertEquals(0L, em.createQuery("select count(b) from BankImportBatch b", Long.class).getSingleResult());
            }
        }
    }

    @Test
    public void lateFailureRollsBackBatchLinesIssuesAndAudit(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-statement-rollback")))
        {
            long accountId = seed(jpa, "FICTIONAL-4321", "****4321");
            BankStatementReviewService previewService = new BankStatementReviewService(jpa);
            BankStatementReviewPreview preview = previewService.preview(FIXTURE, "SCA", accountId);
            BankStatementReviewService failing = new BankStatementReviewService(
                    jpa,
                    new BankStatementParser(),
                    new BankStatementAccountMatcher(),
                    new BankImportNormalizationService(),
                    () -> { throw new IllegalStateException("injected late failure"); });

            assertThrows(IllegalStateException.class,
                    () -> failing.commit(preview, false, "Owner Tester"));
            try (var em = jpa.em())
            {
                assertEquals(0L, em.createQuery("select count(b) from BankImportBatch b", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery("select count(l) from BankStatementLine l", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery("select count(i) from ImportIssue i", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery("select count(a) from AuditEvent a", Long.class).getSingleResult());
            }
        }
    }

    private static long seed(Jpa jpa, String ofxAccountId, String maskedAccount)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (101, 'SCA Chart', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO company (code, display_name, default_currency, active_chart_of_accounts_id) VALUES ('SCA', 'SCA Branch', 'USD', 101)").executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = (SELECT id FROM company WHERE code = 'SCA') WHERE id = 101").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) VALUES (101, 101, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')").executeUpdate();
            em.getTransaction().commit();
        }
        BankConfigurationService configuration = new BankConfigurationService(jpa);
        Bank bank = configuration.createBank(new BankCommand(
                "SCA", "Example Bank", "999000111", null, null, null, null, null, null, true));
        CompanyBankAccount account = configuration.createBankAccount(new BankAccountCommand(
                "SCA", bank.getId(), 101L, maskedAccount, "Operating Checking",
                LocalDate.of(2026, 1, 1), BigDecimal.ZERO, BankingDataFormat.OFX,
                "999000111", ofxAccountId, null, true));
        return account.getId();
    }
}
