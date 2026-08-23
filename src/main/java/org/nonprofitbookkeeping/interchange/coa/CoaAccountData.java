package org.nonprofitbookkeeping.interchange.coa;

import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Framework-independent account definition parsed from Chart of Accounts JSON. */
public record CoaAccountData(
        String sourceCode,
        String code,
        String name,
        AccountType type,
        AccountFunction function,
        AccountSubtype subtype,
        NormalBalance normalBalance,
        String parentCode,
        boolean posting,
        boolean active,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        BigDecimal openingBalance,
        String description,
        String currency,
        List<String> associatedFundIds,
        List<String> supplementalLineKinds,
        Map<String, String> unsupportedFields)
{
    public CoaAccountData
    {
        sourceCode = normalize(sourceCode);
        code = normalize(code);
        name = normalize(name);
        parentCode = optional(parentCode);
        description = optional(description);
        currency = optional(currency);
        openingBalance = openingBalance == null ? BigDecimal.ZERO : openingBalance;
        associatedFundIds = associatedFundIds == null ? List.of() : List.copyOf(associatedFundIds);
        supplementalLineKinds = supplementalLineKinds == null ? List.of() : List.copyOf(supplementalLineKinds);
        unsupportedFields = unsupportedFields == null ? Map.of() : Map.copyOf(unsupportedFields);
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.trim();
    }

    private static String optional(String value)
    {
        String text = normalize(value);
        return text.isEmpty() ? null : text;
    }
}
