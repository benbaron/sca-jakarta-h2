package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.service.BankTransactionRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

}
