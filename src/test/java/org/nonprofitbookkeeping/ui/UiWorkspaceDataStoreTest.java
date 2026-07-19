package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.BankTransactionRecord;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UiWorkspaceDataStoreTest component.
 */
public class UiWorkspaceDataStoreTest
{
    @Test
    public void store_tracksOnlyTemporaryBankTransactions()
    {
        UiWorkspaceDataStore.clearForTests();

        UiWorkspaceDataStore.replaceBankTransactions(List.of(
                new BankTransactionRecord("FIT-1", "2026-04-01", BigDecimal.TEN, "DEBIT", "Vendor", "memo")));

        assertEquals(1, UiWorkspaceDataStore.bankTransactions().size());
    }

    @Test
    public void clearForTests_removesTemporaryBankTransactions()
    {
        UiWorkspaceDataStore.clearForTests();
        UiWorkspaceDataStore.replaceBankTransactions(List.of(
                new BankTransactionRecord("FIT-2", "2026-04-02", BigDecimal.ONE, "CREDIT", "Donor", "memo")));
        UiWorkspaceDataStore.clearForTests();

        assertEquals(0, UiWorkspaceDataStore.bankTransactions().size());
    }
}
