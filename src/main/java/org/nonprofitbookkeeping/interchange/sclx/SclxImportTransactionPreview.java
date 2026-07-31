package org.nonprofitbookkeeping.interchange.sclx;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Non-mutating posting and protection diagnostics for one incoming SCLX transaction. */
public record SclxImportTransactionPreview(
        String transactionId,
        LocalDate transactionDate,
        String description,
        int sourceLineCount,
        int postingLineCount,
        int zeroValueLineCount,
        BigDecimal debitTotal,
        BigDecimal creditTotal,
        boolean balanced,
        boolean requiresBalancingAccount,
        boolean closedPeriodConflict,
        boolean finalizedReconciliationConflict)
{
    public SclxImportTransactionPreview
    {
        transactionId = requireText(transactionId, "transactionId");
        Objects.requireNonNull(transactionDate, "transactionDate");
        description = description == null ? "" : description.trim();
        if (sourceLineCount < 0 || postingLineCount < 0 || zeroValueLineCount < 0
                || postingLineCount > sourceLineCount || zeroValueLineCount > sourceLineCount)
        {
            throw new IllegalArgumentException("transaction preview line counts are invalid");
        }
        debitTotal = Objects.requireNonNull(debitTotal, "debitTotal");
        creditTotal = Objects.requireNonNull(creditTotal, "creditTotal");
        if (debitTotal.signum() < 0 || creditTotal.signum() < 0)
        {
            throw new IllegalArgumentException("transaction preview totals must not be negative");
        }
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
