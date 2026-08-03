package org.nonprofitbookkeeping.interchange.bank;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Read-only company-scoped projection of durable bank review facts for desktop workspaces. */
public final class BankReviewQueryService
{
    private final Jpa jpa;

    public BankReviewQueryService(Jpa jpa)
    {
        this.jpa = java.util.Objects.requireNonNull(jpa, "jpa");
    }

    public List<ReviewRow> listRows(String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("""
                            select l.id, b.id, b.sourceName, a.name,
                                   l.sourceTransactionId, l.transactionDate, l.postedDate,
                                   l.amount, l.currency, l.transactionType, l.name, l.memo,
                                   l.status, mt.id
                              from BankStatementLine l
                              join l.batch b
                              left join l.bankAccount a
                              left join l.matchedTransaction mt
                             where l.company.code = :companyCode
                             order by coalesce(l.postedDate, l.transactionDate) desc,
                                      l.sourceTransactionId, l.deterministicFingerprint, l.id
                            """, Object[].class)
                    .setParameter("companyCode", requiredCompany(companyCode))
                    .setMaxResults(1_000_000)
                    .getResultList().stream()
                    .map(row -> new ReviewRow(
                            (Long) row[0], (Long) row[1], (String) row[2], (String) row[3],
                            (String) row[4], (LocalDate) row[5], (LocalDate) row[6],
                            (BigDecimal) row[7], (String) row[8], (String) row[9],
                            (String) row[10], (String) row[11],
                            ((BankStatementLine.Status) row[12]).name(), (Long) row[13]))
                    .toList();
        }
    }

    public ReviewSummary summary(String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            String company = requiredCompany(companyCode);
            long batches = em.createQuery(
                            "select count(b) from BankImportBatch b where b.company.code = :company",
                            Long.class)
                    .setParameter("company", company).getSingleResult();
            List<BankStatementLine.Status> statuses = em.createQuery("""
                            select l.status from BankStatementLine l
                            where l.company.code = :company
                            """, BankStatementLine.Status.class)
                    .setParameter("company", company).getResultList();
            long issues = em.createQuery("""
                            select count(i) from ImportIssue i
                            where i.batch.company.code = :company
                            """, Long.class)
                    .setParameter("company", company).getSingleResult();
            return new ReviewSummary(
                    batches,
                    statuses.size(),
                    statuses.stream().filter(value -> value == BankStatementLine.Status.IMPORTED).count(),
                    statuses.stream().filter(value -> value == BankStatementLine.Status.DUPLICATE).count(),
                    statuses.stream().filter(value -> value == BankStatementLine.Status.ERROR).count(),
                    issues);
        }
    }

    private static String requiredCompany(String companyCode)
    {
        if (companyCode == null || companyCode.isBlank())
        {
            throw new IllegalArgumentException("Company code is required.");
        }
        return companyCode.trim();
    }

    public record ReviewRow(
            long statementLineId,
            long batchId,
            String sourceName,
            String bankAccountName,
            String sourceTransactionId,
            LocalDate transactionDate,
            LocalDate postedDate,
            BigDecimal amount,
            String currency,
            String transactionType,
            String payeeName,
            String memo,
            String status,
            Long matchedTransactionId) { }

    public record ReviewSummary(
            long batchCount,
            long rowCount,
            long reviewableCount,
            long duplicateCount,
            long errorCount,
            long issueCount) { }
}
