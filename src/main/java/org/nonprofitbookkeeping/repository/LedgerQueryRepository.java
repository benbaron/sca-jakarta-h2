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

    /**
     * Returns canonical bank-account ledger lines for configured bank accounts.
     * A null configuredBankAccountId means all configured bank accounts owned by
     * the selected company.
     */
    List<BankLedgerActivityRow> listBankLedgerActivity(
            String companyCode,
            Long configuredBankAccountId,
            int maxRows);

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

    record BankLedgerActivityRow(Long splitId,
                                 Long txnId,
                                 LocalDate txnDate,
                                 Long configuredBankAccountId,
                                 String configuredBankAccountName,
                                 String accountCode,
                                 String accountName,
                                 String fundCode,
                                 String fundName,
                                 String payee,
                                 String memo,
                                 NormalBalance normalBalance,
                                 BigDecimal amountSigned,
                                 boolean bankCleared,
                                 LocalDate bankClearedOn)
    {
    }
}
