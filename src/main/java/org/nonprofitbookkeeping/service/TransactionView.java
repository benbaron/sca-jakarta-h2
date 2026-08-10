package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read projection for a canonical transaction, its accounting lines, and supplemental details.
 */
public record TransactionView(Long id,
                              LocalDate date,
                              Long payeeId,
                              String payeeName,
                              String memo,
                              Long bankAccountId,
                              String bankAccountName,
                              String status,
                              List<Line> lines,
                              List<TransactionSupplementalLineView> supplementalLines)
{
    public TransactionView(Long id,
                           LocalDate date,
                           Long payeeId,
                           String payeeName,
                           String memo,
                           Long bankAccountId,
                           String bankAccountName,
                           String status,
                           List<Line> lines)
    {
        this(id, date, payeeId, payeeName, memo, bankAccountId, bankAccountName, status, lines, List.of());
    }

    public TransactionView
    {
        lines = lines == null ? List.of() : List.copyOf(lines);
        supplementalLines = supplementalLines == null ? List.of() : List.copyOf(supplementalLines);
    }

    public BigDecimal debitTotal()
    {
        return lines.stream().map(Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal creditTotal()
    {
        return lines.stream().map(Line::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public ClearedState clearedState()
    {
        return summarizeClearedState(lines);
    }

    private static ClearedState summarizeClearedState(List<Line> lines)
    {
        List<Line> bankLines = lines == null ? List.of() : lines.stream().filter(Line::bankAccount).toList();
        if (bankLines.isEmpty())
        {
            return ClearedState.NOT_BANK;
        }
        long cleared = bankLines.stream().filter(Line::bankCleared).count();
        if (cleared == 0)
        {
            return ClearedState.UNCLEARED;
        }
        return cleared == bankLines.size() ? ClearedState.CLEARED : ClearedState.MIXED;
    }

    public enum ClearedState
    {
        NOT_BANK("Not bank"),
        UNCLEARED("Uncleared"),
        CLEARED("Cleared"),
        MIXED("Mixed");

        private final String displayText;

        ClearedState(String displayText)
        {
            this.displayText = displayText;
        }

        public String displayText()
        {
            return displayText;
        }
    }

    public record Line(Long id,
                       Long accountId,
                       String accountCode,
                       String accountName,
                       Long fundId,
                       String fundCode,
                       String fundName,
                       Long budgetCategoryId,
                       Long activityId,
                       Long merchantId,
                       BigDecimal debit,
                       BigDecimal credit,
                       boolean nmr,
                       String notes,
                       boolean bankAccount,
                       boolean bankCleared,
                       LocalDate bankClearedOn,
                       Long reconciliationSessionId)
    {
        public Line(Long id,
                    Long accountId,
                    String accountCode,
                    String accountName,
                    Long fundId,
                    String fundCode,
                    String fundName,
                    Long budgetCategoryId,
                    Long activityId,
                    Long merchantId,
                    BigDecimal debit,
                    BigDecimal credit,
                    boolean nmr,
                    String notes)
        {
            this(id, accountId, accountCode, accountName, fundId, fundCode, fundName,
                    budgetCategoryId, activityId, merchantId, debit, credit, nmr, notes,
                    false, false, null, null);
        }

        public Line
        {
            debit = debit == null ? BigDecimal.ZERO : debit;
            credit = credit == null ? BigDecimal.ZERO : credit;
            if (!bankAccount)
            {
                bankCleared = false;
                bankClearedOn = null;
                reconciliationSessionId = null;
            }
        }

        public String clearedDisplay()
        {
            if (!bankAccount)
            {
                return ClearedState.NOT_BANK.displayText();
            }
            return bankCleared ? ClearedState.CLEARED.displayText() : ClearedState.UNCLEARED.displayText();
        }
    }
}
