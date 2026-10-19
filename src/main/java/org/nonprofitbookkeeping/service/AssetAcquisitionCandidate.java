package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read-only existing debit and full claimed/remaining capacity, identified by its stable native split ID. */
public record AssetAcquisitionCandidate(long splitId, long transactionId, LocalDate date,
                                       long accountId, long fundId, String account, String fund,
                                       BigDecimal debit, BigDecimal claimed, BigDecimal remaining, String memo)
{
}
