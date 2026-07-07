package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One line in a reconciliation comparison report. */
public record ReconciliationComparisonLine(Kind kind,
                                           Long transactionId,
                                           Long splitId,
                                           Long statementLineId,
                                           LocalDate ledgerDate,
                                           LocalDate statementDate,
                                           BigDecimal ledgerAmount,
                                           BigDecimal statementAmount,
                                           String description)
{
    public enum Kind
    {
        MATCHED,
        UNMATCHED_LEDGER,
        UNMATCHED_STATEMENT,
        AMOUNT_MISMATCH,
        DATE_MISMATCH,
        CLEARED_STATE_MISMATCH
    }
}
