package org.nonprofitbookkeeping.report;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;
import org.nonprofitbookkeeping.service.FinancialReportService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreFinancialReportTableBuilderTest
{
    @Test
    void trialBalanceHasNamedMoneyColumnsTotalAndColoredStatus()
    {
        FinancialReportService.TrialBalanceReport report =
                new FinancialReportService.TrialBalanceReport(
                        LocalDate.of(2026, 3, 31),
                        List.of(new FinancialReportService.TrialBalanceRow(
                                "1000", "Cash", new BigDecimal("125.00"), BigDecimal.ZERO)),
                        new BigDecimal("125.00"),
                        new BigDecimal("125.00"));

        ReportTableModel table = CoreFinancialReportTableBuilder.trialBalance(
                report, FinancialReportDisplayFormat.plain());

        assertEquals(List.of("Account", "Account Name", "Debit", "Credit"),
                table.columns().stream().map(ReportTableModel.Column::label).toList());
        assertEquals(ReportTableModel.RowStyle.TOTAL,
                table.rows().get(table.rows().size() - 2).style());
        assertEquals(ReportTableModel.RowStyle.STATUS_SUCCESS,
                table.rows().get(table.rows().size() - 1).style());
        assertEquals(new BigDecimal("125.00"), table.rows().get(0).value("debit"));
    }

    @Test
    void generalLedgerExposesEveryProjectionColumnWithoutTextTruncation()
    {
        List<FinancialReportService.GeneralLedgerRow> values = List.of(
                new FinancialReportService.GeneralLedgerRow(
                        LocalDate.of(2026, 3, 1),
                        42L,
                        "Complete memo text",
                        "Complete payee text",
                        "1000",
                        "Cash in Checking",
                        "GEN",
                        "General Fund",
                        new BigDecimal("25.00"),
                        BigDecimal.ZERO));

        ReportTableModel table = CoreFinancialReportTableBuilder.generalLedger(values);

        assertEquals(List.of(
                        "Date", "Transaction", "Account", "Account Name", "Fund", "Fund Name",
                        "Payee", "Memo", "Debit", "Credit"),
                table.columns().stream().map(ReportTableModel.Column::label).toList());
        assertEquals("Complete memo text", table.rows().get(0).value("memo"));
        assertEquals("Complete payee text", table.rows().get(0).value("payee"));
    }

    @Test
    void balanceSheetUsesSectionTotalAndStatusRows()
    {
        FinancialReportService.BalanceSheetReport report =
                new FinancialReportService.BalanceSheetReport(
                        LocalDate.of(2026, 3, 31),
                        List.of(statement("1000", "Cash", "100.00")),
                        List.of(statement("2000", "Payable", "40.00")),
                        List.of(statement("3000", "Net Assets", "60.00")),
                        new BigDecimal("100.00"),
                        new BigDecimal("40.00"),
                        new BigDecimal("60.00"));

        ReportTableModel table = CoreFinancialReportTableBuilder.balanceSheet(
                report, FinancialReportDisplayFormat.plain());

        assertEquals(3, table.rows().stream()
                .filter(row -> row.style() == ReportTableModel.RowStyle.SECTION)
                .count());
        assertEquals(4, table.rows().stream()
                .filter(row -> row.style() == ReportTableModel.RowStyle.TOTAL)
                .count());
        assertEquals(ReportTableModel.RowStyle.STATUS_SUCCESS,
                table.rows().get(table.rows().size() - 1).style());
    }

    @Test
    void incomeStatementEndsWithFormattedNetIncomeTotal()
    {
        FinancialReportService.IncomeStatementReport report =
                new FinancialReportService.IncomeStatementReport(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 3, 31),
                        List.of(statement("4000", "Donations", "300.00")),
                        List.of(statement("5000", "Supplies", "75.00")),
                        new BigDecimal("300.00"),
                        new BigDecimal("75.00"));

        ReportTableModel table = CoreFinancialReportTableBuilder.incomeStatement(
                report, FinancialReportDisplayFormat.plain());
        ReportTableModel.Row netIncome = table.rows().get(table.rows().size() - 1);

        assertEquals(ReportTableModel.RowStyle.TOTAL, netIncome.style());
        assertEquals("Net Income", netIncome.value("name"));
        assertEquals(new BigDecimal("225.00"), netIncome.value("amount"));
        assertTrue(table.subtitle().contains("2026-01-01"));
        assertTrue(table.subtitle().contains("2026-03-31"));
    }

    private static FinancialReportService.StatementRow statement(
            String code,
            String name,
            String amount)
    {
        return new FinancialReportService.StatementRow(code, name, new BigDecimal(amount));
    }
}
