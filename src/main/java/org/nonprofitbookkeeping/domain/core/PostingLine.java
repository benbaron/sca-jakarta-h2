package org.nonprofitbookkeeping.domain.core;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable journal posting line.
 */
public record PostingLine(String accountCode, String fundCode, EntrySide side, BigDecimal amount)
{
    public PostingLine
    {
        accountCode = requireCode(accountCode, "accountCode");
        fundCode = requireCode(fundCode, "fundCode");
        side = Objects.requireNonNull(side, "side");
        amount = Objects.requireNonNull(amount, "amount");

        if (amount.signum() <= 0)
        {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    private static String requireCode(String value, String fieldName)
    {
        String normalized = Objects.requireNonNull(value, fieldName).trim();
        if (normalized.isEmpty())
        {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return normalized;
    }
}
