package org.nonprofitbookkeeping.interchange.bank;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.BankAccountCommand;
import org.nonprofitbookkeeping.service.BankCommand;
import org.nonprofitbookkeeping.service.BankConfigurationService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NormalizedBankCsvReviewServiceTest
{
    @Test
    public void importsMultipleSourceBatchesAndReexportsSemanticallyIdenticalBytes(@TempDir Path tempDir)
            throws Exception
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("normalized-csv-round-trip")))
        {
            Seed seed = seed(jpa);
            UUID firstBatch = UUID.randomUUID();
            UUID secondBatch = UUID.randomUUID();
            UUID firstLine = UUID.randomUUID();
            UUID secondLine = UUID.randomUUID();
            List<BankStatementExportRow> rows = List.of(
                    row(firstBatch, firstLine, "june-one.ofx", "FIT-ONE",
                            LocalDate.of(2026, 6, 3), new BigDecimal("100.2500"),
                            "IMPORTED", "PROBABLE", "", "PAYEE-ONE"),
                    row(secondBatch, secondLine, "june-two.qfx", "FIT-TWO",
                            LocalDate.of(2026, 6, 20), new BigDecimal("-25.0000"),
                            "MATCHED", "", seed.matchedTransactionId().toString(), "PAYEE-TWO"));
            byte[] sourceBytes = new NormalizedBankCsvSerializer().serialize(rows);
            Path source = tempDir.resolve("round-trip.csv");
            Files.write(source, sourceBytes);

            NormalizedBankCsvReviewService service = new NormalizedBankCsvReviewService(jpa);
            NormalizedBankCsvReviewPreview preview = service.preview(source, "SCA", seed.bankAccountId());
            assertFalse(preview.hasBlockingMessages());
            assertEquals(2, preview.document().batches().size());
            assertEquals(2, preview.document().statement().transactions().size());

            NormalizedBankCsvReviewResult created = service.commit(preview, false, "exchequer");
            assertTrue(created.created());
            assertEquals(2, created.batchCount());
            assertEquals(2, created.totalLineCount());
            assertEquals(1, created.reviewableLineCount());
            assertEquals(1, created.matchedLineCount());
            assertEquals(1, created.issueCount());

            NormalizedBankCsvReviewResult repeated = service.commit(
                    service.preview(source, "SCA", seed.bankAccountId()), false, "exchequer");
            assertFalse(repeated.created());
            assertEquals(created.batchIds(), repeated.batchIds());

            try (EntityManager em = jpa.em())
            {
                List<BankImportBatch> batches = em.createQuery(
                                "select b from BankImportBatch b order by b.sourceExternalId",
                                BankImportBatch.class)
                        .getResultList();
                assertEquals(2, batches.size());
                assertEquals(java.util.Set.of(firstBatch.toString(), secondBatch.toString()),
                        batches.stream().map(BankImportBatch::getSourceExternalId)
                                .collect(java.util.stream.Collectors.toSet()));
                List<BankStatementLine> lines = em.createQuery(
                                "select l from BankStatementLine l order by l.postedDate",
                                BankStatementLine.class)
                        .getResultList();
                assertEquals(List.of(firstLine.toString(), secondLine.toString()),
                        lines.stream().map(BankStatementLine::getSourceExternalId).toList());
                assertEquals(List.of("PAYEE-ONE", "PAYEE-TWO"),
                        lines.stream().map(BankStatementLine::getSourcePayeeId).toList());
                assertEquals(seed.matchedTransactionId(), lines.get(1).getMatchedTransaction().getPortableId());
                assertEquals(1L, em.createQuery("select count(t) from Txn t", Long.class)
                        .getSingleResult());
                assertEquals(1L, em.createQuery(
                                "select count(a) from AuditEvent a where a.actionType = 'NORMALIZED_BANK_CSV_IMPORTED'",
                                Long.class).getSingleResult());
            }

            Path reexport = tempDir.resolve("reexport.csv");
            new BankStatementCsvExportService(jpa, () -> tempDir.resolve("active-database"))
                    .export(new BankStatementExportRequest(
                            "SCA", seed.bankAccountId(), LocalDate.of(2026, 6, 1),
                            LocalDate.of(2026, 6, 30), reexport, false));
            assertArrayEquals(sourceBytes, Files.readAllBytes(reexport));
        }
    }

    @Test
    public void rejectsMalformedIdentityAndRollsBackLateFailure(@TempDir Path tempDir) throws Exception
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("normalized-csv-rollback")))
        {
            Seed seed = seed(jpa);
            BankStatementExportRow value = row(
                    UUID.randomUUID(), UUID.randomUUID(), "source.ofx", "FIT-ONE",
                    LocalDate.of(2026, 6, 3), new BigDecimal("10.00"),
                    "IMPORTED", "", "", "");
            Path source = tempDir.resolve("rollback.csv");
            Files.write(source, new NormalizedBankCsvSerializer().serialize(List.of(value)));
            NormalizedBankCsvReviewService failing = new NormalizedBankCsvReviewService(
                    jpa, new NormalizedBankCsvParser(), new BankStatementAccountMatcher(),
                    () -> { throw new IllegalStateException("late failure"); });
            NormalizedBankCsvReviewPreview preview = failing.preview(source, "SCA", seed.bankAccountId());
            assertThrows(IllegalStateException.class,
                    () -> failing.commit(preview, false, "exchequer"));
            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, em.createQuery("select count(b) from BankImportBatch b", Long.class)
                        .getSingleResult());
                assertEquals(0L, em.createQuery("select count(l) from BankStatementLine l", Long.class)
                        .getSingleResult());
                assertEquals(0L, em.createQuery("select count(a) from AuditEvent a", Long.class)
                        .getSingleResult());
            }

            String malformed = Files.readString(source)
                    .replace("statement_line_external_id", "wrong_header");
            Files.writeString(source, malformed);
            assertThrows(IllegalArgumentException.class,
                    () -> new NormalizedBankCsvReviewService(jpa)
                            .preview(source, "SCA", seed.bankAccountId()));
        }
    }

    @Test
    public void parsesFrozenCompatibilityFixture()
    {
        NormalizedBankCsvDocument document = new NormalizedBankCsvParser().parse(Path.of(
                "src/test/resources/data-exchange/bank-statement/csv/valid/normalized-round-trip.csv"));
        assertEquals(1, document.batches().size());
        assertEquals(2, document.statement().transactions().size());
        assertEquals("payee-fictional-donor", document.batches().get(0).rows().get(0).value().payeeId());
    }

    private static BankStatementExportRow row(
            UUID batchId,
            UUID lineId,
            String sourceName,
            String sourceTransactionId,
            LocalDate postedDate,
            BigDecimal amount,
            String status,
            String duplicateStatus,
            String matchedTransactionId,
            String payeeId)
    {
        return new BankStatementExportRow(
                sourceName.endsWith(".qfx") ? "QFX" : "OFX",
                batchId.toString(), sourceName, lineId.toString(),
                "FI-SCA", "111000111", "SCA-4321", "CHECKING",
                postedDate.minusDays(1), postedDate, amount, "USD", sourceTransactionId,
                amount.signum() > 0 ? "CREDIT" : "DEBIT", payeeId,
                "Payee " + sourceTransactionId, "Memo " + sourceTransactionId,
                "", "REF-" + sourceTransactionId, "", "",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                new BigDecimal("1075.2500"), new BigDecimal("1075.2500"),
                status, duplicateStatus, matchedTransactionId);
    }

    private static Seed seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) "
                    + "VALUES (101, 'SCA Chart', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO company "
                    + "(code, display_name, default_currency, active_chart_of_accounts_id) "
                    + "VALUES ('SCA', 'SCA Branch', 'USD', 101)").executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = "
                    + "(SELECT id FROM company WHERE code = 'SCA') WHERE id = 101").executeUpdate();
            em.createNativeQuery("INSERT INTO account "
                    + "(id, chart_id, code, name, account_type, subtype, normal_balance) "
                    + "VALUES (101, 101, '1000', 'SCA Checking', 'BANK', 'CASH', 'DEBIT')")
                    .executeUpdate();
            em.getTransaction().commit();
        }
        BankConfigurationService configuration = new BankConfigurationService(jpa);
        Bank bank = configuration.createBank(new BankCommand(
                "SCA", "SCA Bank", "111000111", null, null, null, null, null, null, true));
        CompanyBankAccount account = configuration.createBankAccount(new BankAccountCommand(
                "SCA", bank.getId(), 101L, "****4321", "SCA Checking",
                LocalDate.of(2026, 1, 1), BigDecimal.ZERO, BankingDataFormat.OFX,
                "111000111", "SCA-4321", null, true));
        UUID matchedId = UUID.randomUUID();
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = em.createQuery(
                            "select c from Company c where c.code = 'SCA'", Company.class)
                    .getSingleResult();
            Txn matched = new Txn();
            matched.setCompany(company);
            matched.setPortableId(matchedId);
            matched.setTxnDate(LocalDate.of(2026, 6, 20));
            matched.setMemo("Matched target");
            em.persist(matched);
            em.getTransaction().commit();
        }
        return new Seed(account.getId(), matchedId);
    }

    private record Seed(long bankAccountId, UUID matchedTransactionId) { }
}
