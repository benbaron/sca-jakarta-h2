package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One stable request to reallocate resources within the selected company. */
public record FundTransferCommand(UUID requestId, LocalDate date, Long fromFundId, Long toFundId,
        Long allocationAccountId, Long equityAccountId, BigDecimal amount, String explanation)
{
}
