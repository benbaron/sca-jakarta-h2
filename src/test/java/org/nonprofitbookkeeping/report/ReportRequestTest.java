package org.nonprofitbookkeeping.report;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportRequestTest
{
    @Test
    void catalogHasOnlyRealCoreOrSemanticDefinitions()
    {
        assertEquals(10, ReportDefinition.catalog().size());
        assertTrue(ReportDefinition.catalog().stream()
                .allMatch(definition -> definition.source() == ReportDefinition.ReportSource.CORE
                        || definition.templateId() != null));
        assertFalse(ReportDefinition.catalog().stream()
                .anyMatch(definition -> definition.displayName().contains("not implemented")));
    }

    @Test
    void asOfRequestNormalizesStartAndUnsupportedRowLimit()
    {
        LocalDate asOf = LocalDate.of(2026, 6, 30);
        ReportRequest request = new ReportRequest(
                ReportDefinition.TRIAL_BALANCE,
                LocalDate.of(2026, 1, 1),
                asOf,
                ReportFundOption.ALL_FUNDS,
                999);

        assertEquals(asOf, request.startDate());
        assertEquals(asOf, request.asOfDate());
        assertEquals(ReportRequest.DEFAULT_ROW_LIMIT, request.rowLimit());
    }

    @Test
    void rangeRequestRejectsBackwardsDates()
    {
        assertThrows(IllegalArgumentException.class, () -> new ReportRequest(
                ReportDefinition.INCOME_STATEMENT,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 6, 30),
                ReportFundOption.ALL_FUNDS,
                ReportRequest.DEFAULT_ROW_LIMIT));
    }

    @Test
    void fundSelectionRetainsStableIdentityAndCode()
    {
        ReportFundOption fund = new ReportFundOption(42L, "RESTRICTED", "Restricted Fund");
        ReportRequest request = new ReportRequest(
                ReportDefinition.GENERAL_LEDGER_DETAIL,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                fund,
                700);

        assertEquals(42L, request.fund().id());
        assertEquals("RESTRICTED", request.fundCode());
        assertEquals(700, request.rowLimit());
    }

    @Test
    void reportWithoutFundFilterRejectsSpecificFund()
    {
        ReportFundOption fund = new ReportFundOption(7L, "OPERATING", "Operating");
        assertThrows(IllegalArgumentException.class, () -> new ReportRequest(
                ReportDefinition.FUND_TRANSFERS,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                fund,
                500));
    }
}
