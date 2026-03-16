package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialReportRendererGoldenFileTest
{
    @Test
    void trialBalanceText_matchesGoldenFile() throws Exception
    {
        FinancialReportService.TrialBalanceReport report = new FinancialReportService.TrialBalanceReport(
                LocalDate.of(2026, 3, 31),
                List.of(new FinancialReportService.TrialBalanceRow("1000", "Cash", new BigDecimal("120.00"), BigDecimal.ZERO)),
                new BigDecimal("120.00"),
                new BigDecimal("120.00"));

        assertGolden("golden/reports/trial-balance.txt", FinancialReportRenderer.renderTrialBalanceText(report));
    }

    @Test
    void generalLedgerText_matchesGoldenFile() throws Exception
    {
        List<FinancialReportService.GeneralLedgerRow> rows = List.of(
                new FinancialReportService.GeneralLedgerRow(LocalDate.of(2026, 3, 1), 1L, "Memo", "Payee", "1000", "Cash", "GEN", "General", new BigDecimal("120.00"), BigDecimal.ZERO));

        assertGolden("golden/reports/general-ledger.txt", FinancialReportRenderer.renderGeneralLedgerText(rows));
    }

    @Test
    void balanceSheetText_matchesGoldenFile() throws Exception
    {
        FinancialReportService.BalanceSheetReport report = new FinancialReportService.BalanceSheetReport(
                LocalDate.of(2026, 3, 31),
                List.of(new FinancialReportService.StatementRow("1000", "Cash", new BigDecimal("120.00"))),
                List.of(new FinancialReportService.StatementRow("2000", "AP", new BigDecimal("20.00"))),
                List.of(new FinancialReportService.StatementRow("3000", "Net Assets", new BigDecimal("100.00"))),
                new BigDecimal("120.00"),
                new BigDecimal("20.00"),
                new BigDecimal("100.00"));

        assertGolden("golden/reports/balance-sheet.txt", FinancialReportRenderer.renderBalanceSheetText(report));
    }

    @Test
    void incomeStatementText_matchesGoldenFile() throws Exception
    {
        FinancialReportService.IncomeStatementReport report = new FinancialReportService.IncomeStatementReport(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                List.of(new FinancialReportService.StatementRow("4000", "Income", new BigDecimal("200.00"))),
                List.of(new FinancialReportService.StatementRow("5000", "Expense", new BigDecimal("50.00"))),
                new BigDecimal("200.00"),
                new BigDecimal("50.00"));

        assertGolden("golden/reports/income-statement.txt", FinancialReportRenderer.renderIncomeStatementText(report));
    }

    private static void assertGolden(String classpathPath, String actual) throws IOException
    {
        byte[] bytes = FinancialReportRendererGoldenFileTest.class.getClassLoader().getResourceAsStream(classpathPath).readAllBytes();
        String expected = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(normalize(expected), normalize(actual));
    }

    private static String normalize(String value)
    {
        String[] lines = value.replace("\r", "").split("\n");
        StringBuilder out = new StringBuilder();
        for (String line : lines)
        {
            String compact = line.strip().replaceAll("\\s+", " ");
            out.append(compact).append("\n");
        }
        return out.toString().trim();
    }
}
