package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read projection for a canonical transaction, its accounting lines, and supplemental details.
 */
public record TransactionView(Long id,
                              LocalDate date,
                              Long payeeId,
                              String payeeName,
                              String memo,
                              Long bankAccountId,
                              String bankAccountName,
                              String status,
                              List<Line> lines,
                              List<TransactionSupplementalLineView> supplementalLines)
{
    public TransactionView(Long id,
                           LocalDate date,
                           Long payeeId,
                           String payeeName,
                           String memo,
                           Long bankAccountId,
                           String bankAccountName,
                           String status,
                           List<Line> lines)
    {
        this(id, date, payeeId, payeeName, memo, bankAccountId, bankAccountName, status, lines, List.of());
    }

    public TransactionView
    {
        lines = lines == null ? List.of() : List.copyOf(lines);
        supplementalLines = supplementalLines == null ? List.of() : List.copyOf(supplementalLines);
    }

    public BigDecimal debitTotal()
    {
        return lines.stream().map(Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal creditTotal()
    {
        return lines.stream().map(Line::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record Line(Long id,
                       Long accountId,
                       String accountCode,
                       String accountName,
                       Long fundId,
                       String fundCode,
                       String fundName,
                       Long budgetCategoryId,
                       Long activityId,
                       Long merchantId,
                       BigDecimal debit,
                       BigDecimal credit,
                       boolean nmr,
                       String notes)
    {
        public Line
        {
            debit = debit == null ? BigDecimal.ZERO : debit;
            credit = credit == null ? BigDecimal.ZERO : credit;
        }
    }
}
