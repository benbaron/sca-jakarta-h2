package org.nonprofitbookkeeping.service;

import java.time.LocalDate;
import org.nonprofitbookkeeping.model.TransactionInformation;
import java.util.List;

/**
 * Header, accounting lines, and supplemental details for writing a canonical Txn transaction.
 */
public record TransactionCommand(LocalDate date,
                                 Long payeeId,
                                 String memo,
                                 Long bankAccountId,
                                 List<TransactionLineCommand> lines,
                                 List<TransactionSupplementalLineCommand> supplementalLines,
                                 TransactionInformation information)
{
    public TransactionCommand(LocalDate date, Long payeeId, String memo, Long bankAccountId,
                              List<TransactionLineCommand> lines, List<TransactionSupplementalLineCommand> supplementalLines)
    {
        this(date, payeeId, memo, bankAccountId, lines, supplementalLines, TransactionInformation.empty());
    }

    public TransactionCommand(LocalDate date,
                              Long payeeId,
                              String memo,
                              Long bankAccountId,
                              List<TransactionLineCommand> lines)
    {
        this(date, payeeId, memo, bankAccountId, lines, List.of());
    }

    public TransactionCommand
    {
        information = information == null ? TransactionInformation.empty() : information;
        lines = lines == null ? List.of() : List.copyOf(lines);
        supplementalLines = supplementalLines == null ? List.of() : List.copyOf(supplementalLines);
    }
}
