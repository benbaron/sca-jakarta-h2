package org.nonprofitbookkeeping.report;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.FixedAsset;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportRequestTest
{
    @Test
    void catalogHasOnlyRealCoreOrSemanticDefinitions()
    {
        assertEquals(12, ReportDefinition.catalog().size());
        assertTrue(ReportDefinition.catalog().stream()
                .allMatch(definition -> definition.source() == ReportDefinition.ReportSource.CORE
                        || definition.templateId() != null));
        assertFalse(ReportDefinition.catalog().stream()
                .anyMatch(definition -> definition.displayName().contains("not implemented")));
        assertFalse(ReportDefinition.catalog().stream()
                .anyMatch(definition -> "BalanceStmt".equals(definition.templateId())
                        || "IncomeStmt".equals(definition.templateId())));
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
    void balanceSheetRetainsComparativePeriod()
    {
        ReportRequest request = new ReportRequest(
                ReportDefinition.BALANCE_SHEET,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                ReportFundOption.ALL_FUNDS,
                ReportRequest.DEFAULT_ROW_LIMIT);

        assertEquals(LocalDate.of(2026, 1, 1), request.startDate());
        assertEquals(LocalDate.of(2026, 6, 30), request.endDate());
        assertTrue(request.contextSummary().contains("2026-01-01 through 2026-06-30"));
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
    void ledgerRequestRetainsStableAccountIdentityOrAllAccounts()
    {
        ReportRequest selected = new ReportRequest(
                ReportDefinition.GENERAL_LEDGER_DETAIL,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                ReportFundOption.ALL_FUNDS,
                700,
                new ReportDomainFilter.AccountSelection(42L));
        ReportRequest all = new ReportRequest(
                ReportDefinition.TRANSACTIONS_LIST,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                ReportFundOption.ALL_FUNDS,
                700);

        assertEquals(42L,
                ((ReportDomainFilter.AccountSelection) selected.domainFilter()).accountId());
        assertTrue(selected.contextSummary().contains("account=42"));
        assertNull(((ReportDomainFilter.AccountSelection) all.domainFilter()).accountId());
        assertTrue(all.contextSummary().contains("account=ALL"));
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

    @Test
    void fixedAssetRequestRetainsTypedStableIdentityFilters()
    {
        ReportRequest request = new ReportRequest(
                ReportDefinition.FIXED_ASSET_REGISTER,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                new ReportFundOption(3L, "GEN", "General"),
                250,
                new ReportDomainFilter.FixedAssetSelection(
                        17L, 21L, FixedAsset.Status.ACTIVE));

        ReportDomainFilter.FixedAssetSelection filter =
                (ReportDomainFilter.FixedAssetSelection) request.domainFilter();
        assertEquals(17L, filter.assetId());
        assertEquals(21L, filter.accountId());
        assertEquals(FixedAsset.Status.ACTIVE, filter.status());
        assertEquals(request.endDate(), request.startDate());
        assertTrue(request.contextSummary().contains("asset=17"));
    }

    @Test
    void reportRejectsWrongDomainFilterType()
    {
        assertThrows(IllegalArgumentException.class, () -> new ReportRequest(
                ReportDefinition.INVENTORY_VALUATION,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                ReportFundOption.ALL_FUNDS,
                250,
                new ReportDomainFilter.FixedAssetSelection(null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> new ReportRequest(
                ReportDefinition.BALANCE_SHEET,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                ReportFundOption.ALL_FUNDS,
                250,
                new ReportDomainFilter.AccountSelection(1L)));
    }
}
