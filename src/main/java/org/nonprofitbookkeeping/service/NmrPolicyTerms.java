package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Explicit source policy values; free-text rules are not evaluated. */
public record NmrPolicyTerms(
        String policyCode,
        String category,
        LocalDate validFrom,
        LocalDate validTo,
        BigDecimal rate,
        String currency,
        String dateBasis,
        String roundingRule,
        String eligibility,
        String exemptions,
        String recipient,
        String reporting,
        String merchantPath,
        String sourceReference,
        String authority,
        LocalDate sourceDate)
{
}
