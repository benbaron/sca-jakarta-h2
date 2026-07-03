package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;

/**
 * One input line for canonical transaction entry.
 */
public record TransactionLineCommand(Long accountId,
                                     Long fundId,
                                     Long budgetCategoryId,
                                     Long activityId,
                                     Long merchantId,
                                     BigDecimal debit,
                                     BigDecimal credit,
                                     boolean nmr,
                                     String notes)
{
    public BigDecimal signedAmount()
    {
        BigDecimal debitAmount = debit == null ? BigDecimal.ZERO : debit;
        BigDecimal creditAmount = credit == null ? BigDecimal.ZERO : credit;
        return debitAmount.subtract(creditAmount);
    }
}
