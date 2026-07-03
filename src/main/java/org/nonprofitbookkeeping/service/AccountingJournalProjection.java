package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * General-journal style projection derived from the canonical Txn ledger.
 */
public record AccountingJournalProjection(Long transactionId,
                                          LocalDate date,
                                          String payeeName,
                                          String memo,
                                          List<Line> lines)
{
    public AccountingJournalProjection
    {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public BigDecimal debitTotal()
    {
        return lines.stream().map(Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal creditTotal()
    {
        return lines.stream().map(Line::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record Line(String accountCode,
                       String accountName,
                       String fundCode,
                       String fundName,
                       BigDecimal debit,
                       BigDecimal credit,
                       String notes)
    {
        public Line
        {
            debit = debit == null ? BigDecimal.ZERO : debit;
            credit = credit == null ? BigDecimal.ZERO : credit;
        }
    }
}
