package org.nonprofitbookkeeping.repository;

import org.nonprofitbookkeeping.model.NormalBalance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Persistence abstraction for ledger read/query projections.
 */
public interface LedgerQueryRepository
{
    List<LedgerRecentRow> listRecent(int maxRows);

    List<LedgerJournalRow> journalForTxn(Long txnId);

    record LedgerRecentRow(Long id,
                           LocalDate txnDate,
                           String payee,
                           String memo,
                           String bank,
                           long splitCount)
    {
    }

    record LedgerJournalRow(LocalDate txnDate,
                            Long txnId,
                            String memo,
                            String payee,
                            String accountCode,
                            String accountName,
                            String fundCode,
                            String fundName,
                            NormalBalance normalBalance,
                            BigDecimal amountSigned)
    {
    }
}
