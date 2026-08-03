package org.nonprofitbookkeeping.interchange.bank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BankReviewQueryServiceTest
{
    @Test
    public void returnsOnlyDurableRowsOwnedBySelectedCompany(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-review-query")))
        {
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("INSERT INTO company (id, code, display_name) VALUES (101, 'SCA', 'SCA Branch')").executeUpdate();
                em.createNativeQuery("INSERT INTO company (id, code, display_name) VALUES (102, 'OTHER', 'Other Branch')").executeUpdate();
                em.createNativeQuery("""
                        INSERT INTO bank_import_batch
                            (id, company_id, source_name, source_format, status, total_line_count)
                        VALUES (201, 101, 'sca.csv', 'CSV', 'IMPORTED', 1),
                               (202, 102, 'other.csv', 'CSV', 'IMPORTED', 1)
                        """).executeUpdate();
                em.createNativeQuery("""
                        INSERT INTO bank_statement_line
                            (id, batch_id, company_id, source_row_number, source_transaction_id,
                             deterministic_fingerprint, transaction_date, amount, currency, status)
                        VALUES (301, 201, 101, 2, 'SCA-1', 'sca-fp', DATE '2026-06-10', 25.00, 'USD', 'IMPORTED'),
                               (302, 202, 102, 2, 'OTHER-1', 'other-fp', DATE '2026-06-11', 30.00, 'USD', 'DUPLICATE')
                        """).executeUpdate();
                em.createNativeQuery("""
                        INSERT INTO import_issue (batch_id, statement_line_id, source_row_number, severity, code, message)
                        VALUES (201, 301, 2, 'WARNING', 'REVIEW', 'Review row')
                        """).executeUpdate();
                em.getTransaction().commit();
            }

            BankReviewQueryService service = new BankReviewQueryService(jpa);
            var rows = service.listRows("SCA");
            assertEquals(1, rows.size());
            assertEquals("SCA-1", rows.get(0).sourceTransactionId());
            assertEquals("sca.csv", rows.get(0).sourceName());
            var summary = service.summary("SCA");
            assertEquals(1, summary.batchCount());
            assertEquals(1, summary.rowCount());
            assertEquals(1, summary.reviewableCount());
            assertEquals(1, summary.issueCount());
        }
    }
}
