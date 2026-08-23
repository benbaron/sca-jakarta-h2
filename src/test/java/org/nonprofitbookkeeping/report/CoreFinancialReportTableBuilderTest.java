package org.nonprofitbookkeeping.report;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
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
    void balanceSheetUsesMetadataDynamicAccountsAndComparativeColumns()
    {
        FinancialReportService.BalanceSheetReport opening =
                new FinancialReportService.BalanceSheetReport(
                        LocalDate.of(2025, 12, 31),
                        List.of(
                                statement("1010", "Treasury Account", AccountType.ASSET,
                                        AccountSubtype.CASH, "100.00"),
                                statement("1200", "Custom Asset", AccountType.ASSET,
                                        null, "40.00")),
                        List.of(statement("2010", "Custom Liability", AccountType.LIABILITY,
                                null, "40.00")),
                        List.of(),
                        new BigDecimal("140.00"),
                        new BigDecimal("40.00"),
                        BigDecimal.ZERO);
        FinancialReportService.BalanceSheetReport closing =
                new FinancialReportService.BalanceSheetReport(
                        LocalDate.of(2026, 6, 30),
                        List.of(
                                statement("1010", "Treasury Account", AccountType.ASSET,
                                        AccountSubtype.CASH, "125.00"),
                                statement("1200", "Custom Asset", AccountType.ASSET,
                                        null, "40.00")),
                        List.of(statement("2010", "Custom Liability", AccountType.LIABILITY,
                                null, "45.00")),
                        List.of(),
                        new BigDecimal("165.00"),
                        new BigDecimal("45.00"),
                        BigDecimal.ZERO);
        FinancialReportService.IncomeStatementReport income =
                new FinancialReportService.IncomeStatementReport(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 6, 30),
                        List.of(),
                        List.of(),
                        new BigDecimal("20.00"),
                        BigDecimal.ZERO);
        ReportPresentationMetadata metadata = new ReportPresentationMetadata(
                "Parent Organization",
                "Local Group",
                "Legal Entity",
                "Branch",
                "USD",
                1,
                1);

        ReportTableModel table = CoreFinancialReportTableBuilder.balanceSheet(
                opening,
                closing,
                income,
                FinancialReportDisplayFormat.plain(),
                metadata);

        assertEquals(List.of(
                        "Line", "Account", "Category / Description",
                        "2025-12-31", "2026-06-30", "Difference"),
                table.columns().stream().map(ReportTableModel.Column::label).toList());
        assertEquals("Parent Organization", table.headerLines().get(0).left());
        assertEquals("Local Group", table.headerLines().get(1).left());
        assertTrue(table.rows().stream()
                .anyMatch(row -> "Treasury Account".equals(row.value("description"))));
        assertTrue(table.rows().stream()
                .anyMatch(row -> "Custom Asset".equals(row.value("description"))));
        assertEquals(ReportTableModel.RowStyle.STATUS_SUCCESS,
                table.rows().get(table.rows().size() - 1).style());
    }

    @Test
    void incomeStatementPivotsChartHierarchyIntoDynamicAllocationColumns()
    {
        FinancialReportService.IncomeStatementReport report =
                new FinancialReportService.IncomeStatementReport(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 6, 30),
                        List.of(statement("4010", "Custom Income", AccountType.INCOME,
                                null, "300.00")),
                        List.of(
                                allocatedExpense("5110", "Operations", "5100", "Advertising", "50.00"),
                                allocatedExpense("5120", "Activities", "5100", "Advertising", "25.00"),
                                allocatedExpense("5210", "Operations", "5200", "Food", "10.00"),
                                allocatedExpense("5220", "Activities", "5200", "Food", "15.00"),
                                statement("5900", "Standalone Expense", AccountType.EXPENSE,
                                        null, "20.00")),
                        new BigDecimal("300.00"),
                        new BigDecimal("120.00"));
        FinancialReportService.BalanceSheetReport opening = balance(
                LocalDate.of(2025, 12, 31), "100.00", "0.00");
        FinancialReportService.BalanceSheetReport closing = balance(
                LocalDate.of(2026, 6, 30), "280.00", "0.00");
        ReportPresentationMetadata metadata = new ReportPresentationMetadata(
                "Configured Parent",
                "Configured Group",
                "Configured Legal Name",
                "Branch",
                "USD",
                1,
                1);

        ReportTableModel table = CoreFinancialReportTableBuilder.incomeStatement(
                report,
                opening,
                closing,
                FinancialReportDisplayFormat.plain(),
                metadata);

        assertTrue(table.columns().stream().anyMatch(column -> "Operations".equals(column.label())));
        assertTrue(table.columns().stream().anyMatch(column -> "Activities".equals(column.label())));
        assertTrue(table.columns().stream().noneMatch(column -> "Fund Raising".equals(column.label())));
        ReportTableModel.Row advertising = table.rows().stream()
                .filter(row -> "Advertising".equals(row.value("description")))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("50.00"), advertising.value("allocation0"));
        assertEquals(new BigDecimal("25.00"), advertising.value("allocation1"));
        assertEquals(new BigDecimal("75.00"), advertising.value("total"));
        assertEquals("Configured Parent", table.headerLines().get(0).left());
        assertEquals("Configured Group", table.headerLines().get(1).left());
        assertEquals(ReportTableModel.RowStyle.STATUS_SUCCESS,
                table.rows().get(table.rows().size() - 1).style());
    }

    @Test
    void legacyIncomeBuilderStillEndsWithNetIncomeReconciliation()
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
        ReportTableModel.Row netIncome = table.rows().stream()
                .filter(row -> "NET INCOME".equals(row.value("description")))
                .findFirst()
                .orElseThrow();

        assertEquals(ReportTableModel.RowStyle.TOTAL, netIncome.style());
        assertEquals(new BigDecimal("225.00"), netIncome.value("total"));
        assertTrue(table.title().contains("2026-01-01"));
        assertTrue(table.title().contains("2026-03-31"));
    }

    private static FinancialReportService.BalanceSheetReport balance(
            LocalDate date,
            String assets,
            String liabilities)
    {
        return new FinancialReportService.BalanceSheetReport(
                date,
                List.of(),
                List.of(),
                List.of(),
                new BigDecimal(assets),
                new BigDecimal(liabilities),
                BigDecimal.ZERO);
    }

    private static FinancialReportService.StatementRow allocatedExpense(
            String code,
            String allocation,
            String categoryCode,
            String category,
            String amount)
    {
        return new FinancialReportService.StatementRow(
                code,
                allocation,
                AccountType.EXPENSE,
                null,
                categoryCode,
                category,
                "5000",
                "Expenses",
                new BigDecimal(amount));
    }

    private static FinancialReportService.StatementRow statement(
            String code,
            String name,
            AccountType type,
            AccountSubtype subtype,
            String amount)
    {
        return new FinancialReportService.StatementRow(
                code,
                name,
                type,
                subtype,
                null,
                null,
                null,
                null,
                new BigDecimal(amount));
    }

    private static FinancialReportService.StatementRow statement(
            String code,
            String name,
            String amount)
    {
        return new FinancialReportService.StatementRow(code, name, new BigDecimal(amount));
    }
}
