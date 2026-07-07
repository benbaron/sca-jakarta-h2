package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.ImportIssue;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BankImportReviewServiceTest
{
    @Test
    public void persistsValidInvalidAndDuplicateRowsTogether(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-import-review")))
        {
            seedCompany(jpa);
            BankImportReviewService service = new BankImportReviewService(jpa);

            BankImportReviewResult result = service.createReviewBatch(new BankImportReviewCommand(
                    "SCA",
                    null,
                    "statement.csv",
                    "/tmp/statement.csv",
                    "hash-1",
                    BankImportBatch.SourceFormat.CSV,
                    List.of(
                            new BankTransactionRecord("fit-1", "20260315000000", new BigDecimal("10.00"), "CREDIT", "Donor", "Gift"),
                            new BankTransactionRecord("", "bad-date", BigDecimal.ZERO, "DEBIT", "Store", "Bad row"),
                            new BankTransactionRecord("fit-1", "20260316000000", new BigDecimal("11.00"), "CREDIT", "Duplicate", "Same id")),
                    "Review import"));

            assertNotNull(result.batchId());
            assertEquals(3, result.totalLineCount());
            assertEquals(3, result.issueCount());
            assertEquals(1, result.errorLineCount());
            assertEquals(1, result.duplicateLineCount());

            try (var em = jpa.em())
            {
                BankImportBatch batch = em.find(BankImportBatch.class, result.batchId());
                assertEquals(BankImportBatch.Status.IMPORTED, batch.getStatus());
                assertEquals(3, batch.getTotalLineCount());
                assertEquals(3, batch.getIssueCount());

                List<BankStatementLine> lines = em.createQuery("""
                                select l
                                from BankStatementLine l
                                where l.batch.id = :batchId
                                order by l.sourceRowNumber
                                """, BankStatementLine.class)
                        .setParameter("batchId", result.batchId())
                        .getResultList();
                assertEquals(BankStatementLine.Status.IMPORTED, lines.get(0).getStatus());
                assertEquals(BankStatementLine.Status.ERROR, lines.get(1).getStatus());
                assertEquals(BankStatementLine.Status.DUPLICATE, lines.get(2).getStatus());
                assertEquals(null, lines.get(1).getTransactionDate());
                assertEquals(0, BigDecimal.ZERO.compareTo(lines.get(1).getAmount()));

                List<ImportIssue> issues = em.createQuery("""
                                select i
                                from ImportIssue i
                                where i.batch.id = :batchId
                                order by i.sourceRowNumber, i.code
                                """, ImportIssue.class)
                        .setParameter("batchId", result.batchId())
                        .getResultList();
                assertEquals(List.of("EXACT_DUPLICATE", "INVALID_AMOUNT", "INVALID_DATE"), issues.stream().map(ImportIssue::getCode).sorted().toList());
            }
        }
    }

    @Test
    public void detectsDuplicateRowsFromPreviouslyPersistedBatches(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-import-review-duplicates")))
        {
            seedCompany(jpa);
            BankImportReviewService service = new BankImportReviewService(jpa);
            service.createReviewBatch(new BankImportReviewCommand(
                    "SCA", null, "first.ofx", null, null, BankImportBatch.SourceFormat.OFX,
                    List.of(new BankTransactionRecord("existing-fit", "20260315000000", new BigDecimal("10.00"), "CREDIT", "Donor", "Gift")), null));

            BankImportReviewResult second = service.createReviewBatch(new BankImportReviewCommand(
                    "SCA", null, "second.ofx", null, null, BankImportBatch.SourceFormat.OFX,
                    List.of(new BankTransactionRecord("EXISTING-FIT", "20260317000000", new BigDecimal("11.00"), "CREDIT", "Other", "Gift")), null));

            assertEquals(1, second.duplicateLineCount());
            try (var em = jpa.em())
            {
                BankStatementLine line = em.createQuery("""
                                select l
                                from BankStatementLine l
                                where l.batch.id = :batchId
                                """, BankStatementLine.class)
                        .setParameter("batchId", second.batchId())
                        .getSingleResult();
                assertEquals(BankStatementLine.Status.DUPLICATE, line.getStatus());
            }
        }
    }

    private static void seedCompany(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (101, 'SCA Chart', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO company (code, display_name, active_chart_of_accounts_id) VALUES ('SCA', 'SCA Branch', 101)").executeUpdate();
            em.getTransaction().commit();
        }
    }
}
