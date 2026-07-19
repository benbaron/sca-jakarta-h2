package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.service.BankTransactionRecord;

import java.util.List;

/**
 * Session-scoped deterministic UI data store for unfinished bank-transaction staging.
 */
final class UiWorkspaceDataStore
{
    private static final Object LOCK = new Object();
    private static List<BankTransactionRecord> bankTransactions = List.of();

    private UiWorkspaceDataStore()
    {
    }

    static void replaceBankTransactions(List<BankTransactionRecord> rows)
    {
        synchronized (LOCK)
        {
            bankTransactions = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    static List<BankTransactionRecord> bankTransactions()
    {
        synchronized (LOCK)
        {
            return List.copyOf(bankTransactions);
        }
    }

    static void clearForTests()
    {
        synchronized (LOCK)
        {
            bankTransactions = List.of();
        }
    }
}
