package org.nonprofitbookkeeping.interchange.bank;

import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.model.BankingDataFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Immutable, normalized preview of one governed OFX/QFX statement. */
public record BankStatementDocument(
        String sourceName,
        BankingDataFormat format,
        Variant variant,
        String version,
        String encoding,
        AccountIdentity account,
        String currency,
        LocalDate statementStartDate,
        LocalDate statementEndDate,
        BigDecimal ledgerBalance,
        BigDecimal availableBalance,
        List<Transaction> transactions,
        List<InterchangeValidationMessage> messages)
{
    public BankStatementDocument
    {
        sourceName = text(sourceName);
        if (format == null)
        {
            throw new IllegalArgumentException("Bank statement format is required");
        }
        if (variant == null)
        {
            throw new IllegalArgumentException("Bank statement variant is required");
        }
        version = text(version);
        encoding = text(encoding);
        if (account == null)
        {
            throw new IllegalArgumentException("Bank statement account identity is required");
        }
        currency = text(currency);
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    private static String text(String value)
    {
        return value == null ? "" : value.trim();
    }

    public enum Variant
    {
        OFX_2_XML,
        QFX_2_XML,
        QFX_1_SGML,
        MAPPED_CSV,
        NORMALIZED_CSV
    }

    public record AccountIdentity(
            String institutionId,
            String bankId,
            String accountId,
            String accountType)
    {
        public AccountIdentity
        {
            institutionId = text(institutionId);
            bankId = text(bankId);
            accountId = text(accountId);
            accountType = text(accountType);
            if (accountId.isBlank())
            {
                throw new IllegalArgumentException("Bank statement account ID is required");
            }
        }

        public String maskedAccountId()
        {
            if (accountId.length() <= 4)
            {
                return accountId;
            }
            return "…" + accountId.substring(accountId.length() - 4);
        }
    }

    public record Transaction(
            int sourceRowNumber,
            LocalDate transactionDate,
            LocalDate postedDate,
            BigDecimal amount,
            String sourceTransactionId,
            String transactionType,
            String payeeName,
            String memo,
            String checkNumber,
            String reference,
            String correctionAction,
            String correctedSourceTransactionId)
    {
        public Transaction
        {
            if (sourceRowNumber < 1)
            {
                throw new IllegalArgumentException("Bank statement source row must be positive");
            }
            if (transactionDate == null && postedDate == null)
            {
                throw new IllegalArgumentException("Bank statement transaction date is required");
            }
            if (amount == null || amount.signum() == 0)
            {
                throw new IllegalArgumentException("Bank statement amount must be nonzero");
            }
            sourceTransactionId = text(sourceTransactionId);
            transactionType = text(transactionType);
            payeeName = text(payeeName);
            memo = text(memo);
            checkNumber = text(checkNumber);
            reference = text(reference);
            correctionAction = text(correctionAction);
            correctedSourceTransactionId = text(correctedSourceTransactionId);
        }
    }
}
