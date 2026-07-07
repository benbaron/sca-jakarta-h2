package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Result of comparing configured bank-account ledger lines to reviewed statement facts. */
public record ReconciliationComparisonReport(UUID savedRunId,
                                             String companyCode,
                                             Long bankAccountId,
                                             String bankAccountName,
                                             LocalDate fromDate,
                                             LocalDate statementEndingOn,
                                             BigDecimal beginningBalance,
                                             BigDecimal activity,
                                             BigDecimal endingBookBalance,
                                             BigDecimal clearedBookBalance,
                                             int ledgerLineCount,
                                             int statementLineCount,
                                             int matchedLineCount,
                                             List<ReconciliationComparisonLine> lines)
{
    public int unresolvedCount()
    {
        int count = 0;
        for (ReconciliationComparisonLine line : lines)
        {
            if (line.kind() != ReconciliationComparisonLine.Kind.MATCHED)
            {
                count++;
            }
        }
        return count;
    }
}
