package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.service.BankTransactionRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UiWorkspaceDataStoreTest component.
 */
public class UiWorkspaceDataStoreTest
{
    @Test
    public void store_tracksBankTransactionsAndImportExportJobs()
    {
        UiWorkspaceDataStore.clearForTests();

        UiWorkspaceDataStore.replaceBankTransactions(List.of(
                new BankTransactionRecord("FIT-1", "2026-04-01", BigDecimal.TEN, "DEBIT", "Vendor", "memo")));
        UiWorkspaceDataStore.appendJob(new UiWorkspaceDataStore.ImportExportJob(
                LocalDateTime.of(2026, 4, 1, 12, 0),
                "IMPORT_BANK",
                "bank.ofx",
                "",
                BankingDataFormat.OFX,
                0,
                1,
                "SUCCESS",
                ""));

        assertEquals(1, UiWorkspaceDataStore.bankTransactions().size());
        assertEquals(1, UiWorkspaceDataStore.jobs().size());
        assertEquals("IMPORT_BANK", UiWorkspaceDataStore.jobs().get(0).operation());
    }
    @Test
    public void clearJobsForTests_keepsBankTransactions()
    {
        UiWorkspaceDataStore.clearForTests();
        UiWorkspaceDataStore.replaceBankTransactions(List.of(
                new BankTransactionRecord("FIT-2", "2026-04-02", BigDecimal.ONE, "CREDIT", "Donor", "memo")));
        UiWorkspaceDataStore.appendJob(new UiWorkspaceDataStore.ImportExportJob(
                LocalDateTime.of(2026, 4, 2, 12, 0),
                "EXPORT_BANK",
                "",
                "out.ofx",
                BankingDataFormat.OFX,
                0,
                1,
                "SUCCESS",
                ""));

        UiWorkspaceDataStore.clearJobsForTests();

        assertEquals(1, UiWorkspaceDataStore.bankTransactions().size());
        assertEquals(0, UiWorkspaceDataStore.jobs().size());
    }

    @Test
    public void store_tracksBudgetTargetsByFundCode()
    {
        UiWorkspaceDataStore.clearForTests();

        UiWorkspaceDataStore.upsertBudgetTarget("GEN", BigDecimal.valueOf(2500));
        UiWorkspaceDataStore.upsertBudgetTarget("PROJ", BigDecimal.valueOf(900));
        UiWorkspaceDataStore.removeBudgetTarget("PROJ");

        assertEquals(Map.of("GEN", BigDecimal.valueOf(2500)), UiWorkspaceDataStore.budgetTargetsByFundCode());
    }

    @Test
    public void store_tracksOperationalRunbookEntriesAcrossPanels()
    {
        UiWorkspaceDataStore.clearForTests();

        UiWorkspaceDataStore.appendScheduleRunbookEntry("s1");
        UiWorkspaceDataStore.appendAssetLifecycleEntry("a1");
        UiWorkspaceDataStore.appendDepreciationRunEntry("d1");
        UiWorkspaceDataStore.appendInventoryMovementEntry("i1");

        assertEquals(List.of("s1"), UiWorkspaceDataStore.scheduleRunbookEntries());
        assertEquals(List.of("a1"), UiWorkspaceDataStore.assetLifecycleEntries());
        assertEquals(List.of("d1"), UiWorkspaceDataStore.depreciationRunEntries());
        assertEquals(List.of("i1"), UiWorkspaceDataStore.inventoryMovementEntries());
    }

}
