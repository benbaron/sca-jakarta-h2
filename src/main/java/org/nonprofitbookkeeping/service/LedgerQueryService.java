package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.repository.JpaLedgerQueryRepository;
import org.nonprofitbookkeeping.repository.LedgerQueryRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Query service for ledger register and transaction drill-down screens.
 */
public class LedgerQueryService
{
    private final LedgerQueryRepository repository;

    public LedgerQueryService(Jpa jpa)
    {
        this(new JpaLedgerQueryRepository(jpa));
    }

    public LedgerQueryService(LedgerQueryRepository repository)
    {
        this.repository = repository;
    }

    public List<LedgerRow> listRecent(int maxRows)
    {
        List<LedgerQueryRepository.LedgerRecentRow> rows = repository.listRecent(maxRows);

        List<LedgerRow> out = new ArrayList<>();
        for (LedgerQueryRepository.LedgerRecentRow row : rows)
        {
            out.add(new LedgerRow(
                    row.id(),
                    row.txnDate(),
                    row.payee(),
                    row.memo(),
                    row.bank(),
                    (int) row.splitCount()));
        }
        return out;
    }

    public List<JournalLine> journalForTxn(Long txnId)
    {
        List<LedgerQueryRepository.LedgerJournalRow> rows = repository.journalForTxn(txnId);

        List<JournalLine> out = new ArrayList<>();
        for (LedgerQueryRepository.LedgerJournalRow row : rows)
        {
            BigDecimal amount = row.amountSigned();
            NormalBalance normal = row.normalBalance();

            BigDecimal debit = BigDecimal.ZERO;
            BigDecimal credit = BigDecimal.ZERO;
            if (normal == NormalBalance.DEBIT)
            {
                if (amount.compareTo(BigDecimal.ZERO) > 0)
                {
                    debit = amount;
                }
                else
                {
                    credit = amount.abs();
                }
            }
            else
            {
                if (amount.compareTo(BigDecimal.ZERO) > 0)
                {
                    credit = amount;
                }
                else
                {
                    debit = amount.abs();
                }
            }

            out.add(new JournalLine(
                    row.txnDate(),
                    row.txnId(),
                    row.memo(),
                    row.payee(),
                    row.accountCode(),
                    row.accountName(),
                    row.fundCode(),
                    row.fundName(),
                    debit,
                    credit));
        }
        return out;
    }

    public record LedgerRow(Long id, LocalDate date, String payee, String memo, String bank, int splitCount)
    {
    }
}
