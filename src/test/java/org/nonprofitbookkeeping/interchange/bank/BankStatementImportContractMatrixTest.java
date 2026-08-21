package org.nonprofitbookkeeping.interchange.bank;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.BankAccountCommand;
import org.nonprofitbookkeeping.service.BankCommand;
import org.nonprofitbookkeeping.service.BankConfigurationService;
import org.nonprofitbookkeeping.service.PeriodCloseRangeService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end contract matrix for every governed bank-statement import profile. */
public class BankStatementImportContractMatrixTest
{
    private static final Path BANK_FIXTURES = Path.of(
            "src/test/resources/data-exchange/bank-statement");

    @Test
    public void importsEveryGovernedProfileIdempotentlyWithoutLedgerWrites(@TempDir Path tempDir)
            throws Exception
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("all-bank-import-profiles")))
        {
            Seed seed = seed(jpa);
            BankStatementReviewService statementReview = new BankStatementReviewService(jpa);

            commitTwice(statementReview, valid("ofx/ofx2-checking.xml"), seed.scaAccountId());
            commitTwice(statementReview, valid("qfx/qfx-xml-header.qfx"), seed.scaAccountId());
            commitTwice(statementReview, valid("qfx/qfx-sgml-v1.qfx"), seed.scaAccountId());

            BankCsvMappingProfileService profiles = new BankCsvMappingProfileService(jpa);
            var signed = profiles.create(
                    "SCA", seed.scaAccountId(), profile("mapping-profile-signed.json"));
            var debitCredit = profiles.create(
                    "SCA", seed.scaAccountId(), profile("mapping-profile-debit-credit.json"));
            BankCsvReviewService csvReview = new BankCsvReviewService(jpa);
            commitTwice(csvReview, valid("csv/mapped-signed.csv"), seed.scaAccountId(), signed.id());
            commitTwice(csvReview, valid("csv/mapped-debit-credit.csv"),
                    seed.scaAccountId(), debitCredit.id());

            Path normalized = tempDir.resolve("normalized-bank.csv");
            Files.write(normalized, new NormalizedBankCsvSerializer().serialize(List.of(
                    normalizedRow())));
            NormalizedBankCsvReviewService normalizedReview =
                    new NormalizedBankCsvReviewService(jpa);
            NormalizedBankCsvReviewPreview normalizedPreview =
                    normalizedReview.preview(normalized, "SCA", seed.scaAccountId());
            assertTrue(normalizedPreview.commitAllowed(false));
            NormalizedBankCsvReviewResult normalizedCreated =
                    normalizedReview.commit(normalizedPreview, false, "Contract Matrix");
            assertTrue(normalizedCreated.created());
            NormalizedBankCsvReviewResult normalizedRepeated = normalizedReview.commit(
                    normalizedReview.preview(normalized, "SCA", seed.scaAccountId()),
                    false, "Contract Matrix");
            assertFalse(normalizedRepeated.created());
            assertEquals(normalizedCreated.batchIds(), normalizedRepeated.batchIds());

            assertThrows(IllegalArgumentException.class,
                    () -> statementReview.preview(
                            valid("ofx/ofx2-checking.xml"), "SCA", seed.otherAccountId()));

            try (EntityManager em = jpa.em())
            {
                assertEquals(6L, count(em, "BankImportBatch"));
                assertEquals(10L, count(em, "BankStatementLine"));
                assertEquals(2L, count(em, "BankCsvMappingProfile"));
                assertEquals(0L, count(em, "Txn"));
                assertEquals(6L, count(em, "AuditEvent"));
                assertEquals(0L, em.createQuery("""
                                select count(b) from BankImportBatch b
                                 where b.company.code = 'OTHER'
                                """, Long.class).getSingleResult());
                assertEquals(3L, em.createQuery("""
                                select count(b) from BankImportBatch b
                                 where b.sourceVariant in ('OFX_2_XML', 'QFX_2_XML', 'QFX_1_SGML')
                                """, Long.class).getSingleResult());
                assertEquals(2L, em.createQuery("""
                                select count(b) from BankImportBatch b
                                 where b.sourceVariant = 'MAPPED_CSV'
                                """, Long.class).getSingleResult());
                assertEquals(1L, em.createQuery("""
                                select count(b) from BankImportBatch b
                                 where b.sourceVariant = 'NORMALIZED_CSV_1_0'
                                """, Long.class).getSingleResult());
            }
        }
    }

    @Test
    public void rejectsMalformedUnsafeAndUnsupportedInputsWithoutDurableFacts(@TempDir Path tempDir)
            throws Exception
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("invalid-bank-import-matrix")))
        {
            Seed seed = seed(jpa);
            BankStatementReviewService statementReview = new BankStatementReviewService(jpa);
            for (String fixture : List.of(
                    "ofx/malformed.xml",
                    "ofx/unsupported-version.xml",
                    "ofx/unsupported-message-set.xml",
                    "ofx/multi-account.xml",
                    "ofx/xml-external-entity.xml",
                    "ofx/xml-entity-expansion.xml",
                    "qfx/encrypted.qfx",
                    "qfx/unsupported-compression.qfx",
                    "qfx/malformed-header.qfx"))
            {
                assertThrows(IllegalArgumentException.class,
                        () -> statementReview.preview(
                                invalid(fixture), "SCA", seed.scaAccountId()), fixture);
            }

            BankCsvMappingProfileService profiles = new BankCsvMappingProfileService(jpa);
            var signed = profiles.create(
                    "SCA", seed.scaAccountId(), profile("mapping-profile-signed.json"));
            var debitCredit = profiles.create(
                    "SCA", seed.scaAccountId(), profile("mapping-profile-debit-credit.json"));
            BankCsvReviewService csvReview = new BankCsvReviewService(jpa);
            assertThrows(IllegalArgumentException.class, () -> csvReview.preview(
                    invalid("csv/malformed.csv"), "SCA", seed.scaAccountId(), signed.id()));
            assertThrows(IllegalArgumentException.class, () -> csvReview.preview(
                    invalid("csv/duplicate-headers.csv"), "SCA", seed.scaAccountId(), signed.id()));
            assertThrows(IllegalArgumentException.class, () -> csvReview.preview(
                    invalid("csv/missing-amount.csv"), "SCA", seed.scaAccountId(), signed.id()));
            assertThrows(IllegalArgumentException.class, () -> csvReview.preview(
                    invalid("csv/both-debit-credit.csv"),
                    "SCA", seed.scaAccountId(), debitCredit.id()));

            Path invalidNormalized = tempDir.resolve("invalid-normalized.csv");
            Files.writeString(invalidNormalized, "record_version,wrong_header\n1.0,value\n");
            assertThrows(IllegalArgumentException.class,
                    () -> new NormalizedBankCsvReviewService(jpa).preview(
                            invalidNormalized, "SCA", seed.scaAccountId()));

            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, count(em, "BankImportBatch"));
                assertEquals(0L, count(em, "BankStatementLine"));
                assertEquals(0L, count(em, "ImportIssue"));
                assertEquals(0L, count(em, "AuditEvent"));
                assertEquals(0L, count(em, "Txn"));
                assertEquals(2L, count(em, "BankCsvMappingProfile"));
            }
        }
    }

    @Test
    public void exportReimportPreservesSemanticsWithoutTouchingClosedOrReconciledLedger(
            @TempDir Path tempDir) throws Exception
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-round-trip-protection")))
        {
            Seed seed = seed(jpa);
            seedClosedAndReconciledLedger(jpa);
            BankStatementReviewService review = new BankStatementReviewService(jpa);
            BankStatementReviewResult source = review.commit(
                    review.preview(valid("ofx/ofx2-checking.xml"), "SCA", seed.scaAccountId()),
                    false, "Round Trip");
            assertTrue(source.created());

            BankStatementOfxExportService exporter = new BankStatementOfxExportService(
                    jpa, () -> tempDir.resolve("active-database"));
            Path ofx = tempDir.resolve("review.ofx");
            Path qfx = tempDir.resolve("review.qfx");
            exporter.export(new BankStatementOfxExportRequest(
                    "SCA", seed.scaAccountId(), LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 6, 30), ofx, false,
                    BankStatementOfxExportRequest.Profile.OFX_2_XML));
            exporter.export(new BankStatementOfxExportRequest(
                    "SCA", seed.scaAccountId(), LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 6, 30), qfx, false,
                    BankStatementOfxExportRequest.Profile.QFX_2_XML));

            BankStatementDocument ofxDocument = new BankStatementParser().parse(ofx);
            BankStatementDocument qfxDocument = new BankStatementParser().parse(qfx);
            assertEquals(ofxDocument.transactions(), qfxDocument.transactions());
            assertEquals(ofxDocument.account(), qfxDocument.account());
            assertEquals(ofxDocument.currency(), qfxDocument.currency());
            assertEquals(ofxDocument.ledgerBalance(), qfxDocument.ledgerBalance());

            BankStatementReviewResult importedOfx = review.commit(
                    review.preview(ofx, "OTHER", seed.otherAccountId()),
                    false, "Round Trip");
            BankStatementReviewResult importedQfx = review.commit(
                    review.preview(qfx, "OTHER", seed.otherAccountId()),
                    false, "Round Trip");
            assertTrue(importedOfx.created());
            assertTrue(importedQfx.created());
            assertEquals(3, importedOfx.reviewableLineCount());
            assertEquals(3, importedQfx.duplicateLineCount());

            try (EntityManager em = jpa.em())
            {
                assertEquals(1L, count(em, "Txn"));
                assertEquals(2L, count(em, "TxnSplit"));
                assertEquals(1L, nativeCount(em, "txn_reconciliation_protection"));
                assertEquals(1L, nativeCount(em, "period_close_range"));
                assertEquals(1L, nativeCount(em, "period_close_event"));
                assertEquals(2L, em.createQuery("""
                                select count(b) from BankImportBatch b
                                 where b.company.code = 'OTHER'
                                """, Long.class).getSingleResult());
                assertEquals(6L, em.createQuery("""
                                select count(l) from BankStatementLine l
                                 where l.company.code = 'OTHER'
                                """, Long.class).getSingleResult());
            }
        }
    }

    private static void commitTwice(
            BankStatementReviewService service, Path source, long accountId)
    {
        BankStatementReviewPreview preview = service.preview(source, "SCA", accountId);
        assertTrue(preview.commitAllowed(false));
        BankStatementReviewResult created = service.commit(preview, false, "Contract Matrix");
        assertTrue(created.created());
        BankStatementReviewResult repeated = service.commit(preview, false, "Contract Matrix");
        assertFalse(repeated.created());
        assertEquals(created.batchId(), repeated.batchId());
    }

    private static void commitTwice(
            BankCsvReviewService service, Path source, long accountId, long profileId)
    {
        BankCsvReviewPreview preview = service.preview(source, "SCA", accountId, profileId);
        assertTrue(preview.review().commitAllowed(false));
        BankStatementReviewResult created = service.commit(preview, false, "Contract Matrix");
        assertTrue(created.created());
        BankStatementReviewResult repeated = service.commit(preview, false, "Contract Matrix");
        assertFalse(repeated.created());
        assertEquals(created.batchId(), repeated.batchId());
    }

    private static BankStatementExportRow normalizedRow()
    {
        return new BankStatementExportRow(
                "CSV", UUID.randomUUID().toString(), "normalized-source.csv",
                UUID.randomUUID().toString(), "FICTIONAL-COMMUNITY", "999000111",
                "FICTIONAL-4321", "CHECKING", LocalDate.of(2026, 7, 4),
                LocalDate.of(2026, 7, 5), new BigDecimal("12.3400"), "USD",
                "NORMALIZED-FICTIONAL-0001", "CREDIT", "PAYEE-NORMALIZED-1",
                "Fictional Normalized Payee", "Normalized round-trip row", "", "NORM-1",
                "", "", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                new BigDecimal("352.5900"), new BigDecimal("352.5900"),
                "IMPORTED", "UNIQUE", "");
    }

    private static long count(EntityManager em, String entity)
    {
        return em.createQuery("select count(e) from " + entity + " e", Long.class)
                .getSingleResult();
    }

    private static long nativeCount(EntityManager em, String table)
    {
        return ((Number) em.createNativeQuery("select count(*) from " + table)
                .getSingleResult()).longValue();
    }

    private static void seedClosedAndReconciledLedger(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO account "
                    + "(id, chart_id, code, name, account_type, normal_balance) VALUES "
                    + "(103, 101, '5000', 'Program Expense', 'EXPENSE', 'DEBIT')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO fund "
                    + "(id, company_id, code, name, fund_type) VALUES "
                    + "(201, (SELECT id FROM company WHERE code = 'SCA'), "
                    + "'OPERATING', 'Operating', 'UNRESTRICTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn "
                    + "(id, company_id, txn_date, memo, status) VALUES "
                    + "(501, (SELECT id FROM company WHERE code = 'SCA'), "
                    + "DATE '2026-06-15', 'Protected bank expense', 'ENTERED')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split "
                    + "(id, txn_id, account_id, fund_id, amount_signed) VALUES "
                    + "(501, 501, 101, 201, -75.2500), (502, 501, 103, 201, 75.2500)")
                    .executeUpdate();
            UUID runId = UUID.randomUUID();
            em.createNativeQuery("INSERT INTO reconciliation_run "
                    + "(id, group_code, statement_ending_on, bank_format, "
                    + "imported_transaction_count, status, notes) VALUES "
                    + "(?, 'SCA', DATE '2026-06-30', 'OFX', 1, 'COMPLETED', 'June')")
                    .setParameter(1, runId).executeUpdate();
            em.createNativeQuery("INSERT INTO txn_reconciliation_protection "
                    + "(txn_id, reconciliation_run_id, protected_by, notes) VALUES "
                    + "(501, ?, 'treasurer', 'Completed June reconciliation')")
                    .setParameter(1, runId).executeUpdate();
            em.getTransaction().commit();
        }
        new PeriodCloseRangeService(jpa).closeRange(
                "SCA", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                "CALCULATED", "treasurer", "June closed");
    }

    private static String profile(String name) throws Exception
    {
        return Files.readString(BANK_FIXTURES.resolve("csv/valid").resolve(name));
    }

    private static Path valid(String relative)
    {
        return BANK_FIXTURES.resolve(relative.replaceFirst("/", "/valid/")).normalize();
    }

    private static Path invalid(String relative)
    {
        return BANK_FIXTURES.resolve(relative.replaceFirst("/", "/invalid/")).normalize();
    }

    private static Seed seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES "
                    + "(101, 'SCA Chart', '1', 'ACTIVE'), "
                    + "(102, 'Other Chart', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO company "
                    + "(code, display_name, default_currency, active_chart_of_accounts_id) VALUES "
                    + "('SCA', 'SCA Branch', 'USD', 101), "
                    + "('OTHER', 'Other Branch', 'USD', 102)").executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = "
                    + "(SELECT id FROM company WHERE code = 'SCA') WHERE id = 101")
                    .executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = "
                    + "(SELECT id FROM company WHERE code = 'OTHER') WHERE id = 102")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO account "
                    + "(id, chart_id, code, name, account_type, account_function, subtype, normal_balance) VALUES "
                    + "(101, 101, '1000', 'SCA Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT'), "
                    + "(102, 102, '1000', 'Other Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .executeUpdate();
            em.getTransaction().commit();
        }

        BankConfigurationService configuration = new BankConfigurationService(jpa);
        Bank scaBank = configuration.createBank(new BankCommand(
                "SCA", "Example Bank", "999000111", null, null, null, null, null, null, true));
        CompanyBankAccount sca = configuration.createBankAccount(new BankAccountCommand(
                "SCA", scaBank.getId(), 101L, "****4321", "Operating Checking",
                LocalDate.of(2026, 1, 1), BigDecimal.ZERO, BankingDataFormat.OFX,
                "999000111", "FICTIONAL-4321", null, true));
        Bank otherBank = configuration.createBank(new BankCommand(
                "OTHER", "Other Example Bank", "999000111",
                null, null, null, null, null, null, true));
        CompanyBankAccount other = configuration.createBankAccount(new BankAccountCommand(
                "OTHER", otherBank.getId(), 102L, "****9876", "Other Checking",
                LocalDate.of(2026, 1, 1), BigDecimal.ZERO, BankingDataFormat.OFX,
                "999000111", "FICTIONAL-4321", null, true));
        return new Seed(sca.getId(), other.getId());
    }

    private record Seed(long scaAccountId, long otherAccountId) { }
}
