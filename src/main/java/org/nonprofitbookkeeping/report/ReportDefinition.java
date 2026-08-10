package org.nonprofitbookkeeping.report;

import java.util.Arrays;
import java.util.List;

/** Typed catalog entry for every selectable Report Library report. */
public enum ReportDefinition
{
    TRIAL_BALANCE(
            "trial-balance",
            "Trial Balance",
            ReportSource.CORE,
            null,
            DateMode.AS_OF,
            true,
            false),
    GENERAL_LEDGER_DETAIL(
            "general-ledger-detail",
            "General Ledger Detail",
            ReportSource.CORE,
            null,
            DateMode.RANGE,
            true,
            true),
    BALANCE_SHEET(
            "balance-sheet",
            "Balance Sheet",
            ReportSource.CORE,
            null,
            DateMode.AS_OF,
            true,
            false),
    INCOME_STATEMENT(
            "income-statement",
            "Income Statement",
            ReportSource.CORE,
            null,
            DateMode.RANGE,
            true,
            false),
    BALANCE_STMT(
            "balance-stmt",
            "BalanceStmt (SCA workbook)",
            ReportSource.SEMANTIC,
            "BalanceStmt",
            DateMode.AS_OF,
            true,
            false),
    INCOME_STMT(
            "income-stmt",
            "IncomeStmt (SCA workbook)",
            ReportSource.SEMANTIC,
            "IncomeStmt",
            DateMode.RANGE,
            true,
            false),
    WORKBOOK_SUMMARY(
            "workbook-summary",
            "WorkbookSummary (SCA workbook)",
            ReportSource.SEMANTIC,
            "WorkbookSummary",
            DateMode.RANGE,
            true,
            false),
    TRANSACTIONS_LIST(
            "transactions-list",
            "TransactionsList (SCA workbook)",
            ReportSource.SEMANTIC,
            "TransactionsList",
            DateMode.RANGE,
            true,
            true),
    ALL_CHECKS_TFRS(
            "all-checks-transfers",
            "Bank Account Activity (SCA workbook)",
            ReportSource.SEMANTIC,
            "AllChecksTfrs",
            DateMode.RANGE,
            true,
            true),
    FUND_TRANSFERS(
            "fund-transfers",
            "FundTransfers (SCA workbook)",
            ReportSource.SEMANTIC,
            "FundTransfers",
            DateMode.RANGE,
            false,
            true);

    private final String id;
    private final String displayName;
    private final ReportSource source;
    private final String templateId;
    private final DateMode dateMode;
    private final boolean supportsFund;
    private final boolean supportsRowLimit;

    ReportDefinition(
            String id,
            String displayName,
            ReportSource source,
            String templateId,
            DateMode dateMode,
            boolean supportsFund,
            boolean supportsRowLimit)
    {
        this.id = id;
        this.displayName = displayName;
        this.source = source;
        this.templateId = templateId;
        this.dateMode = dateMode;
        this.supportsFund = supportsFund;
        this.supportsRowLimit = supportsRowLimit;
    }

    public String id()
    {
        return id;
    }

    public String displayName()
    {
        return displayName;
    }

    public ReportSource source()
    {
        return source;
    }

    public String templateId()
    {
        return templateId;
    }

    public DateMode dateMode()
    {
        return dateMode;
    }

    public boolean supportsFund()
    {
        return supportsFund;
    }

    public boolean supportsRowLimit()
    {
        return supportsRowLimit;
    }

    public static List<ReportDefinition> catalog()
    {
        return List.copyOf(Arrays.asList(values()));
    }

    public enum ReportSource
    {
        CORE,
        SEMANTIC
    }

    public enum DateMode
    {
        AS_OF,
        RANGE
    }
}
