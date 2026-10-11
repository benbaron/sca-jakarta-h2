package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.BankImportBatch;

import java.util.List;

/** Command for persisting one normalized bank import review batch. */
public record BankImportReviewCommand(String companyCode,
                                      Long bankAccountId,
                                      String sourceName,
                                      String sourcePath,
                                      String sourceHash,
                                      BankImportBatch.SourceFormat sourceFormat,
                                      List<BankTransactionRecord> records,
                                      String notes)
{
    public BankImportReviewCommand
    {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
