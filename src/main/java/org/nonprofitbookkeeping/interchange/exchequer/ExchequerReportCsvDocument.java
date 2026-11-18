package org.nonprofitbookkeeping.interchange.exchequer;

import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/** Immutable, transient worksheet facts. Nothing here is an accepted native ledger entry. */
public record ExchequerReportCsvDocument(
        Path source,
        String sourceSha256,
        String encoding,
        Metadata metadata,
        int sourceRecordCount,
        int numberedRowCount,
        int skippedTemplateRows,
        int skippedHelperRows,
        List<Row> rows,
        List<InterchangeValidationMessage> messages)
{
    public ExchequerReportCsvDocument
    {
        rows = List.copyOf(rows);
        messages = List.copyOf(messages);
    }

    public boolean hasBlockingErrors()
    {
        return messages.stream().anyMatch(InterchangeValidationMessage::blocking);
    }

    public List<Row> postingRows()
    {
        return rows.stream().filter(row -> row.kind() == RowKind.POSTING).toList();
    }

    public enum RowKind
    {
        POSTING, NON_POSTING, INVALID
    }
    public enum Timing
    {
        NOW, FUTURE, PREVIOUSLY, NONE
    }
    public enum CategorySide
    {
        INCOME, EXPENSE
    }

    public record OpeningBalance(int sourceLine, String bankAccount, LocalDate date, BigDecimal amount,
                                 List<String> originalFields)
    {
        public OpeningBalance
        {
            originalFields = List.copyOf(originalFields);
        }
    }

    public record Metadata(String organizationName, String reportLabel, String templateVersion,
                           OpeningBalance openingBalance, List<List<String>> headerRows)
    {
        public Metadata
        {
            headerRows = headerRows.stream().map(List::copyOf).toList();
        }
    }

    public record Row(int sourceLine, Integer worksheetRow, LocalDate transactionDate, LocalDate statementDate,
                      LocalDate incomingDate, LocalDate reversalDate, BigDecimal netAmount, BigDecimal runningBalance,
                      String reference, String payee, String notes, String bankAccount, Timing bankTiming,
                      String budgetCategory, Timing budgetTiming, String fund, String merchant, Integer linkedRow,
                      List<Split> splits, RowKind kind, String originalRecord, List<String> originalFields)
    {
        public Row
        {
            splits = List.copyOf(splits);
            originalFields = List.copyOf(originalFields);
        }

        public String statementDateText()
        {
            return originalFields.get(4);
        }
    }

    public record Split(int ordinal, Integer worksheetRow, BigDecimal amount, String incomeCategory,
                        String expenseCategory, String usedFor, String expenseFunctionCode,
                        String itemNumber, BigDecimal quantity)
    {
        public CategorySide side()
        {
            if (!incomeCategory.isBlank() && expenseCategory.isBlank())
            {
                return CategorySide.INCOME;
            }
            if (!expenseCategory.isBlank() && incomeCategory.isBlank())
            {
                return CategorySide.EXPENSE;
            }
            return null;
        }

        public String category()
        {
            return side() == CategorySide.INCOME ? incomeCategory : side() == CategorySide.EXPENSE ? expenseCategory : "";
        }

        /** Signed cash-side effect of this source split, preserving negative adjustments. */
        public BigDecimal bankEffect()
        {
            if (amount == null || side() == null)
            {
                return null;
            }
            return side() == CategorySide.INCOME ? amount : amount.negate();
        }
    }
}
