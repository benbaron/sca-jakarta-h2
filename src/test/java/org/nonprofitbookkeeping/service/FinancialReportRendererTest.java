package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialReportRendererTest
{
    @Test
    void rendersCsvForAllM1M2Reports()
    {
        FinancialReportService.TrialBalanceReport trial = new FinancialReportService.TrialBalanceReport(
                LocalDate.of(2026, 3, 31),
                List.of(new FinancialReportService.TrialBalanceRow("1000", "Cash", new BigDecimal("100.00"), BigDecimal.ZERO)),
                new BigDecimal("100.00"),
                new BigDecimal("100.00"));

        List<FinancialReportService.GeneralLedgerRow> gl = List.of(
                new FinancialReportService.GeneralLedgerRow(LocalDate.of(2026, 3, 1), 1L, "Memo", "Payee", "1000", "Cash", "GEN", "General", new BigDecimal("100.00"), BigDecimal.ZERO));

        FinancialReportService.BalanceSheetReport bs = new FinancialReportService.BalanceSheetReport(
                LocalDate.of(2026, 3, 31),
                List.of(new FinancialReportService.StatementRow("1000", "Cash", new BigDecimal("100.00"))),
                List.of(new FinancialReportService.StatementRow("2000", "AP", new BigDecimal("40.00"))),
                List.of(new FinancialReportService.StatementRow("3000", "Net Assets", new BigDecimal("60.00"))),
                new BigDecimal("100.00"), new BigDecimal("40.00"), new BigDecimal("60.00"));

        FinancialReportService.IncomeStatementReport is = new FinancialReportService.IncomeStatementReport(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                List.of(new FinancialReportService.StatementRow("4000", "Income", new BigDecimal("200.00"))),
                List.of(new FinancialReportService.StatementRow("5000", "Expense", new BigDecimal("50.00"))),
                new BigDecimal("200.00"), new BigDecimal("50.00"));

        assertTrue(FinancialReportRenderer.renderTrialBalanceCsv(trial).startsWith("account_code,account_name,debit,credit"));
        assertTrue(FinancialReportRenderer.renderGeneralLedgerCsv(gl).startsWith("txn_date,txn_id,memo,payee"));
        assertTrue(FinancialReportRenderer.renderBalanceSheetCsv(bs).contains("ASSET,1000"));
        assertTrue(FinancialReportRenderer.renderIncomeStatementCsv(is).contains("INCOME,4000"));
    }
}
