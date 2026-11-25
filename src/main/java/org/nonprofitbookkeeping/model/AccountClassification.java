package org.nonprofitbookkeeping.model;

import java.util.Locale;
import java.util.Objects;

/** Shared classification helpers for persisted and portable account vocabulary. */
public final class AccountClassification
{
    private AccountClassification()
    {
    }

    public static boolean isBank(Account account)
    {
        return account != null
                && account.getAccountType() == AccountType.ASSET
                && account.getAccountFunction() == AccountFunction.BANK;
    }

    public static boolean normalBalanceCompatible(AccountType type, NormalBalance balance)
    {
        if (type == null || balance == null) return false;
        return switch (type)
        {
            case ASSET, EXPENSE -> balance == NormalBalance.DEBIT;
            case LIABILITY, EQUITY, INCOME -> balance == NormalBalance.CREDIT;
        };
    }

    public static boolean subtypeCompatible(AccountType type, AccountSubtype subtype)
    {
        if (subtype == null || type == null) return true;
        return switch (subtype)
        {
            case RECEIVABLE, PREPAID, INVENTORY, FIXED_ASSET, OTHER_ASSET, CASH -> type == AccountType.ASSET;
            case PAYABLE, DEFERRED_REVENUE, OTHER_LIABILITY -> type == AccountType.LIABILITY;
        };
    }

    public static boolean isCash(Account account)
    {
        return account != null
                && account.getAccountType() == AccountType.ASSET
                && account.getSubtype() == AccountSubtype.CASH;
    }

    public static boolean isBankLedgerAccount(Account account)
    {
        return isBank(account) && account.getNormalBalance() == NormalBalance.DEBIT;
    }

    /**
     * Returns the stable compatibility token used by existing COA/SCLX interchange.
     * BANK remains a portable token even though it is no longer an internal AccountType.
     */
    public static String portableType(Account account)
    {
        Objects.requireNonNull(account, "account");
        return portableType(account.getAccountType(), account.getAccountFunction());
    }

    public static String portableType(AccountType type, AccountFunction function)
    {
        Objects.requireNonNull(type, "type");
        return function == AccountFunction.BANK ? "BANK" : type.name();
    }

    /** Maps the existing portable BANK pseudo-type onto ASSET + BANK function. */
    public static PortableType parsePortableType(String value)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException("Account type is required.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        if ("BANK".equals(normalized))
        {
            return new PortableType(AccountType.ASSET, AccountFunction.BANK);
        }
        return new PortableType(AccountType.valueOf(normalized), null);
    }

    public record PortableType(AccountType type, AccountFunction function)
    {
        public PortableType
        {
            Objects.requireNonNull(type, "type");
        }
    }
}
