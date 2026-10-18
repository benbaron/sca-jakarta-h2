package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import org.nonprofitbookkeeping.model.ExpenseFunction;

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
                                     String notes,
                                     PaymentReference payment,
                                     ExpenseFunction expenseFunction)
{
    public TransactionLineCommand(Long accountId, Long fundId, Long budgetCategoryId, Long activityId,
            Long merchantId, BigDecimal debit, BigDecimal credit, boolean nmr, String notes, PaymentReference payment)
    {
        this(accountId, fundId, budgetCategoryId, activityId, merchantId, debit, credit, nmr, notes, payment, null);
    }
    public TransactionLineCommand(Long accountId, Long fundId, Long budgetCategoryId, Long activityId,
            Long merchantId, BigDecimal debit, BigDecimal credit, boolean nmr, String notes)
    {
        this(accountId, fundId, budgetCategoryId, activityId, merchantId, debit, credit, nmr, notes, null);
    }

    public BigDecimal signedAmount()
    {
        BigDecimal debitAmount = debit == null ? BigDecimal.ZERO : debit;
        BigDecimal creditAmount = credit == null ? BigDecimal.ZERO : credit;
        return debitAmount.subtract(creditAmount);
    }
}
