package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Saved operational fact and its exact canonical accounting relationship. */
public record FundTransferView(Long id, LocalDate date, Long transactionId, String fromFund, String toFund,
        BigDecimal amount, String explanation, String state)
{
}
