package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.util.List;

/** Optional facts on an existing receivable allocation; line references are zero-based. */
public record MerchantSettlementCommand(String role, BigDecimal withheldUnconfirmed,
                                        String evidenceReference, List<Component> components)
{
    public record Component(String kind, Integer transactionLineIndex)
    {
    }

    public MerchantSettlementCommand
    {
        components = components == null ? List.of() : List.copyOf(components);
    }
}
