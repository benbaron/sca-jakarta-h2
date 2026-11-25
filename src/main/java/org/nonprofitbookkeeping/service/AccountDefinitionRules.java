package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.*;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Native definition validation shares the classification vocabulary used by compatibility import. */
public final class AccountDefinitionRules
{

    private AccountDefinitionRules()
    {
    }
    public static void validate(String code, String name, AccountType type, AccountFunction function,
        NormalBalance normal, AccountSubtype subtype, AccountDefinitionDetails details)
    {
        bounded(code, "Account code", 64);
        bounded(name, "Account name", 200);
        if (type == null) throw new IllegalArgumentException("Account type is required.");
        if (!AccountClassification.normalBalanceCompatible(type, normal))
        {
            throw new IllegalArgumentException("Normal balance is incompatible with account type " + type + ".");
        }
        if (function == AccountFunction.BANK && type != AccountType.ASSET)
        {
            throw new IllegalArgumentException("BANK function requires an ASSET account with a DEBIT normal balance.");
        }
        if (!AccountClassification.subtypeCompatible(type, subtype))
        {
            throw new IllegalArgumentException("Subtype " + subtype + " is incompatible with account type " + type + ".");
        }
        if (details == null) return;
        for (LocalDate date : java.util.stream.Stream.of(details.effectiveFrom(), details.effectiveTo()).filter(java.util.Objects::nonNull).toList())
        {
            if (date.isBefore(LocalDate.of(1900, 1, 1)) || date.isAfter(LocalDate.of(9999, 12, 31)))
            {
                throw new IllegalArgumentException("Effective dates must be between 1900 and 9999.");
            }
        }
        if (details.effectiveFrom() != null && details.effectiveTo() != null && details.effectiveTo().isBefore(details.effectiveFrom()))
        {
            throw new IllegalArgumentException("Effective to cannot be before Effective from.");
        }
        try
        {
            if (details.openingBalance().setScale(2, RoundingMode.UNNECESSARY).precision() > 19) throw new ArithmeticException();
        }
        catch (ArithmeticException ex)
        {
            throw new IllegalArgumentException("Opening balance must fit 19 digits and 2 decimal places without rounding.");
        }
        if (details.description() != null && details.description().length() > 1_048_576)
        {
            throw new IllegalArgumentException("Account description exceeds the 1 MiB limit.");
        }
    }

    public static String bounded(String value, String label, int maximum)
    {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        String clean = value.trim();
        if (clean.codePointCount(0, clean.length()) > maximum) throw new IllegalArgumentException(label + " exceeds " + maximum + " characters.");
        return clean;
    }
}
