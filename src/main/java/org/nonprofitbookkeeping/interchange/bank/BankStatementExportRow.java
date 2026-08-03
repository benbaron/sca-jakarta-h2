package org.nonprofitbookkeeping.interchange.bank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** One immutable normalized bank-statement CSV 1.0 row reconstructed from durable review facts. */
public record BankStatementExportRow(
        String sourceFormat,
        String sourceBatchExternalId,
        String sourceFileName,
        String statementLineExternalId,
        String institutionId,
        String bankId,
        String accountId,
        String accountType,
        LocalDate transactionDate,
        LocalDate postedDate,
        BigDecimal amount,
        String currency,
        String sourceTransactionId,
        String transactionType,
        String payeeId,
        String payeeName,
        String memo,
        String checkNumber,
        String reference,
        String correctionAction,
        String correctedSourceTransactionId,
        LocalDate statementStartDate,
        LocalDate statementEndDate,
        BigDecimal ledgerBalance,
        BigDecimal availableBalance,
        String reviewStatus,
        String duplicateStatus,
        String matchedTransactionExternalId)
{
    public BankStatementExportRow
    {
        sourceFormat = required(sourceFormat, "sourceFormat");
        sourceBatchExternalId = required(sourceBatchExternalId, "sourceBatchExternalId");
        sourceFileName = required(sourceFileName, "sourceFileName");
        statementLineExternalId = required(statementLineExternalId, "statementLineExternalId");
        institutionId = optional(institutionId);
        bankId = optional(bankId);
        accountId = optional(accountId);
        accountType = optional(accountType);
        if (transactionDate == null && postedDate == null)
        {
            throw new IllegalArgumentException("An exported bank row requires a transaction or posted date");
        }
        amount = Objects.requireNonNull(amount, "amount");
        if (amount.signum() == 0 || amount.scale() > 4 || amount.precision() - amount.scale() > 15)
        {
            throw new IllegalArgumentException("amount must be a nonzero DECIMAL(19,4) value");
        }
        currency = required(currency, "currency").toUpperCase(java.util.Locale.ROOT);
        sourceTransactionId = optional(sourceTransactionId);
        transactionType = optional(transactionType);
        payeeId = optional(payeeId);
        payeeName = optional(payeeName);
        memo = optional(memo);
        checkNumber = optional(checkNumber);
        reference = optional(reference);
        correctionAction = optional(correctionAction);
        correctedSourceTransactionId = optional(correctedSourceTransactionId);
        reviewStatus = required(reviewStatus, "reviewStatus");
        duplicateStatus = optional(duplicateStatus);
        matchedTransactionExternalId = optional(matchedTransactionExternalId);
    }

    private static String required(String value, String field)
    {
        String normalized = optional(value);
        if (normalized.isBlank())
        {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String optional(String value)
    {
        return value == null ? "" : value.trim();
    }
}
