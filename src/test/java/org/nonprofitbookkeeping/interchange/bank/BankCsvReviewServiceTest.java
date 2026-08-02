package org.nonprofitbookkeeping.interchange.bank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankCsvMappingProfile;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.BankAccountCommand;
import org.nonprofitbookkeeping.service.BankCommand;
import org.nonprofitbookkeeping.service.BankConfigurationService;
import org.nonprofitbookkeeping.service.BankImportNormalizationService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BankCsvReviewServiceTest
{
    private static final Path FIXTURES = Path.of(
            "src/test/resources/data-exchange/bank-statement/csv");

    @Test
    public void persistsCompanyProfileAndCompleteSignedCsvReview(@TempDir Path tempDir) throws Exception
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-csv-signed")))
        {
            long accountId = seed(jpa);
            BankCsvMappingProfileService profiles = new BankCsvMappingProfileService(jpa);
            var profile = profiles.create("SCA", accountId, profile("mapping-profile-signed.json"));
            assertEquals(1, profiles.list("SCA").size());

            BankCsvReviewService service = new BankCsvReviewService(jpa);
            BankCsvReviewPreview preview = service.preview(
                    csv("mapped-signed.csv"), "SCA", accountId, profile.id());
            assertEquals(BankStatementAccountMatcher.Status.EXACT,
                    preview.review().accountMatchStatus());
            assertEquals(2, preview.originalRows().size());
            assertTrue(preview.originalRows().get(1).originalText().contains("Program supplies, brushes and paper"));
            assertEquals(new BigDecimal("-75.25"), preview.review().lines().get(1).amount());

            BankStatementReviewResult result = service.commit(preview, false, "Owner Tester");
            assertTrue(result.created());
            assertEquals(2, result.reviewableLineCount());
            BankStatementReviewResult identical = service.commit(preview, false, "Owner Tester");
            assertFalse(identical.created());
            assertEquals(result.batchId(), identical.batchId());

            try (var em = jpa.em())
            {
                BankImportBatch batch = em.find(BankImportBatch.class, result.batchId());
                assertEquals(BankImportBatch.SourceFormat.CSV, batch.getSourceFormat());
                assertEquals("MAPPED_CSV", batch.getSourceVariant());
                assertEquals("FICTIONAL-4321", batch.getSourceAccountId());
                assertEquals(LocalDate.of(2026, 6, 10), batch.getStatementStartDate());
                assertEquals(0L, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
                assertEquals(1L, em.createQuery("""
                        select count(a) from AuditEvent a
                        where a.actionType = 'BANK_STATEMENT_REVIEW_IMPORTED'
                        """, Long.class).getSingleResult());
            }
        }
    }

    @Test
    public void mapsDebitCreditAmountsAndBlocksMalformedInputs(@TempDir Path tempDir) throws Exception
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-csv-debit-credit")))
        {
            long accountId = seed(jpa);
            var profile = new BankCsvMappingProfileService(jpa)
                    .create("SCA", accountId, profile("mapping-profile-debit-credit.json"));
            BankCsvReviewService service = new BankCsvReviewService(jpa);
            BankCsvReviewPreview preview = service.preview(
                    csv("mapped-debit-credit.csv"), "SCA", accountId, profile.id());
            assertEquals(new BigDecimal("-75.25"), preview.review().lines().get(0).amount());
            assertEquals(new BigDecimal("85.00"), preview.review().lines().get(1).amount());

            assertThrows(IllegalArgumentException.class,
                    () -> service.preview(csv("../invalid/both-debit-credit.csv"), "SCA", accountId, profile.id()));
            assertThrows(IllegalArgumentException.class,
                    () -> service.preview(csv("../invalid/malformed.csv"), "SCA", accountId, profile.id()));
        }
    }

    @Test
    public void changedOrInactiveProfileInvalidatesApprovedPreview(@TempDir Path tempDir) throws Exception
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-csv-profile-change")))
        {
            long accountId = seed(jpa);
            BankCsvMappingProfileService profiles = new BankCsvMappingProfileService(jpa);
            var profile = profiles.create("SCA", accountId, profile("mapping-profile-signed.json"));
            BankCsvReviewService service = new BankCsvReviewService(jpa);
            BankCsvReviewPreview preview = service.preview(
                    csv("mapped-signed.csv"), "SCA", accountId, profile.id());

            profiles.setActive(profile.id(), "SCA", false);
            assertThrows(IllegalArgumentException.class,
                    () -> service.commit(preview, false, "Owner Tester"));
            try (var em = jpa.em())
            {
                assertEquals(0L, em.createQuery("select count(b) from BankImportBatch b", Long.class).getSingleResult());
            }
        }
    }

    @Test
    public void lateFailureRollsBackCsvReviewFacts(@TempDir Path tempDir) throws Exception
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-csv-rollback")))
        {
            long accountId = seed(jpa);
            var profile = new BankCsvMappingProfileService(jpa)
                    .create("SCA", accountId, profile("mapping-profile-signed.json"));
            BankCsvReviewService previewService = new BankCsvReviewService(jpa);
            BankCsvReviewPreview preview = previewService.preview(
                    csv("mapped-signed.csv"), "SCA", accountId, profile.id());
            BankStatementReviewService failingReview = new BankStatementReviewService(
                    jpa, new BankStatementParser(), new BankStatementAccountMatcher(),
                    new BankImportNormalizationService(),
                    () -> { throw new IllegalStateException("injected CSV late failure"); });
            BankCsvReviewService failing = new BankCsvReviewService(
                    jpa, new BankCsvParser(), failingReview);

            assertThrows(IllegalStateException.class,
                    () -> failing.commit(preview, false, "Owner Tester"));
            try (var em = jpa.em())
            {
                assertEquals(0L, em.createQuery("select count(b) from BankImportBatch b", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery("select count(l) from BankStatementLine l", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery("select count(i) from ImportIssue i", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery("select count(a) from AuditEvent a", Long.class).getSingleResult());
                assertEquals(1L, em.createQuery("select count(p) from BankCsvMappingProfile p", Long.class).getSingleResult());
            }
        }
    }

    @Test
    public void profileValidationRejectsDuplicateHeadersAndMissingMappedColumns() throws Exception
    {
        BankCsvMappingProfileDefinition definition = BankCsvMappingProfileDefinition.parse(
                profile("mapping-profile-signed.json"));
        BankCsvParser parser = new BankCsvParser();
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(csv("../invalid/duplicate-headers.csv"), definition));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(csv("../invalid/missing-amount.csv"), definition));
    }

    private static String profile(String name) throws Exception
    {
        return Files.readString(FIXTURES.resolve("valid").resolve(name));
    }

    private static Path csv(String name)
    {
        return FIXTURES.resolve("valid").resolve(name).normalize();
    }

    private static long seed(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (101, 'SCA Chart', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO company (code, display_name, default_currency, active_chart_of_accounts_id) VALUES ('SCA', 'SCA Branch', 'USD', 101)").executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = (SELECT id FROM company WHERE code = 'SCA') WHERE id = 101").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (101, 101, '1000', 'Checking', 'BANK', 'CASH', 'DEBIT')").executeUpdate();
            em.getTransaction().commit();
        }
        BankConfigurationService configuration = new BankConfigurationService(jpa);
        Bank bank = configuration.createBank(new BankCommand(
                "SCA", "Example Bank", "999000111", null, null, null, null, null, null, true));
        CompanyBankAccount account = configuration.createBankAccount(new BankAccountCommand(
                "SCA", bank.getId(), 101L, "****4321", "Operating Checking",
                LocalDate.of(2026, 1, 1), BigDecimal.ZERO, BankingDataFormat.CSV,
                "999000111", "FICTIONAL-4321", null, true));
        return account.getId();
    }
}
