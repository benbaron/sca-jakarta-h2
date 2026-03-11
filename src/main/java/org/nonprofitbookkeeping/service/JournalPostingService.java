package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.domain.core.EntrySide;
import org.nonprofitbookkeeping.domain.core.JournalTransaction;
import org.nonprofitbookkeeping.domain.core.PostingLine;
import org.nonprofitbookkeeping.domain.timing.TimingPosition;
import org.nonprofitbookkeeping.repository.JournalTransactionRepository;
import org.nonprofitbookkeeping.repository.OpenItemKind;
import org.nonprofitbookkeeping.repository.OpenItemSnapshotRecord;
import org.nonprofitbookkeeping.repository.OpenItemSnapshotRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Projects journal transactions into initial open-item schedules.
 */
public class JournalPostingService
{
    private static final String RECEIVABLE_OPEN = "OPEN";
    private static final String RECEIVABLE_SETTLED = "SETTLED_BY_CASH";
    private static final String PREPAID_OPEN = "OPEN";
    private static final String PREPAID_FULLY_RECOGNIZED = "FULLY_RECOGNIZED";

    private final JournalTransactionRepository journalTransactionRepository;
    private final OpenItemSnapshotRepository openItemSnapshotRepository;

    public JournalPostingService(JournalTransactionRepository journalTransactionRepository,
                                 OpenItemSnapshotRepository openItemSnapshotRepository)
    {
        this.journalTransactionRepository = Objects.requireNonNull(journalTransactionRepository, "journalTransactionRepository");
        this.openItemSnapshotRepository = Objects.requireNonNull(openItemSnapshotRepository, "openItemSnapshotRepository");
    }

    public void post(JournalTransaction transaction)
    {
        Objects.requireNonNull(transaction, "transaction");
        journalTransactionRepository.append(transaction);

        deriveReceivableProjection(transaction);
        derivePrepaidProjection(transaction);
    }

    private void deriveReceivableProjection(JournalTransaction transaction)
    {
        if (transaction.timing().bankTiming() == TimingPosition.FUTURE
                && transaction.timing().budgetTiming() == TimingPosition.NOW)
        {
            for (PostingLine line : transaction.lines())
            {
                if (isReceivableLine(line) && line.side() == EntrySide.DEBIT)
                {
                    createIfAbsent(transaction, OpenItemKind.RECEIVABLE, line, RECEIVABLE_OPEN);
                }
            }
            return;
        }

        if (transaction.timing().bankTiming() == TimingPosition.NOW
                && transaction.timing().budgetTiming() == TimingPosition.PREVIOUSLY)
        {
            for (PostingLine line : transaction.lines())
            {
                if (isReceivableLine(line) && line.side() == EntrySide.CREDIT)
                {
                    transitionIfPresent(transaction, OpenItemKind.RECEIVABLE, line, RECEIVABLE_SETTLED,
                            "Receivable settled by bank movement");
                }
            }
        }
    }

    private void derivePrepaidProjection(JournalTransaction transaction)
    {
        if (transaction.timing().bankTiming() == TimingPosition.NOW
                && transaction.timing().budgetTiming() == TimingPosition.FUTURE)
        {
            for (PostingLine line : transaction.lines())
            {
                if (isPrepaidLine(line) && line.side() == EntrySide.DEBIT)
                {
                    createIfAbsent(transaction, OpenItemKind.PREPAID_EXPENSE, line, PREPAID_OPEN);
                }
            }
            return;
        }

        if (transaction.timing().bankTiming() == TimingPosition.PREVIOUSLY
                && transaction.timing().budgetTiming() == TimingPosition.NOW)
        {
            for (PostingLine line : transaction.lines())
            {
                if (isPrepaidLine(line) && line.side() == EntrySide.CREDIT)
                {
                    transitionIfPresent(transaction, OpenItemKind.PREPAID_EXPENSE, line, PREPAID_FULLY_RECOGNIZED,
                            "Prepaid fully recognized into budget period");
                }
            }
        }
    }

    private void createIfAbsent(JournalTransaction transaction, OpenItemKind kind, PostingLine line, String state)
    {
        String itemRef = itemRef(line);
        Optional<OpenItemSnapshotRecord> existing = findSnapshot(transaction.groupCode(), kind, itemRef);
        if (existing.isPresent())
        {
            return;
        }

        OpenItemSnapshotRecord record = new OpenItemSnapshotRecord(
                projectionId(transaction.transactionId(), kind, itemRef),
                transaction.groupCode(),
                kind,
                itemRef,
                state,
                line.amount(),
                line.amount(),
                transaction.transactionId(),
                transaction.postedOn(),
                0);

        openItemSnapshotRepository.create(record);
    }

    private void transitionIfPresent(JournalTransaction transaction, OpenItemKind kind, PostingLine line,
                                     String toState, String notes)
    {
        String itemRef = itemRef(line);
        Optional<OpenItemSnapshotRecord> existing = findSnapshot(transaction.groupCode(), kind, itemRef);
        if (existing.isEmpty())
        {
            return;
        }

        OpenItemSnapshotRecord snapshot = existing.get();
        openItemSnapshotRepository.transition(
                snapshot.id(),
                snapshot.state(),
                toState,
                transaction.transactionId(),
                notes,
                transaction.postedOn(),
                snapshot.version());
    }

    private Optional<OpenItemSnapshotRecord> findSnapshot(String groupCode, OpenItemKind kind, String itemRef)
    {
        return openItemSnapshotRepository.findByGroupAndKind(groupCode, kind)
                .stream()
                .filter(row -> row.itemRef().equals(itemRef))
                .findFirst();
    }

    private boolean isReceivableLine(PostingLine line)
    {
        String account = line.accountCode().toUpperCase();
        return account.contains("RECEIVABLE") || account.startsWith("1100") || account.contains("-AR");
    }

    private boolean isPrepaidLine(PostingLine line)
    {
        String account = line.accountCode().toUpperCase();
        return account.contains("PREPAID") || account.startsWith("1200");
    }

    private String itemRef(PostingLine line)
    {
        return line.accountCode() + "|" + line.fundCode();
    }

    private UUID projectionId(UUID transactionId, OpenItemKind kind, String itemRef)
    {
        String key = transactionId + "|" + kind.name() + "|" + itemRef;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
