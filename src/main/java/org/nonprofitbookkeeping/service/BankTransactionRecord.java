package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;

/**
 * Deterministic extracted banking transaction projection from OFX/QFX.
 */
public record BankTransactionRecord(String fitId,
                                    String postedOn,
                                    BigDecimal amount,
                                    String transactionType,
                                    String name,
                                    String memo)
{
}
