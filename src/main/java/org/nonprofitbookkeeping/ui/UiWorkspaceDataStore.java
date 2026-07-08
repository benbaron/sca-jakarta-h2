package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.service.BankTransactionRecord;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Session-scoped deterministic UI data store for cross-panel projections.
 */
final class UiWorkspaceDataStore
{
    private static final Object LOCK = new Object();
    private static List<BankTransactionRecord> bankTransactions = List.of();
    private static final List<ImportExportJob> jobs = new ArrayList<>();
    private static final List<String> inventoryMovementEntries = new ArrayList<>(RunbookPersistence.loadInventoryEntries());

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

    static void appendJob(ImportExportJob job)
    {
        synchronized (LOCK)
        {
            jobs.add(job);
        }
    }

    static List<ImportExportJob> jobs()
    {
        synchronized (LOCK)
        {
            return List.copyOf(jobs);
        }
    }

    static void clearJobsForTests()
    {
        synchronized (LOCK)
        {
            jobs.clear();
        }
    }

    static void appendInventoryMovementEntry(String line)
    {
        synchronized (LOCK)
        {
            inventoryMovementEntries.add(0, line);
            RunbookPersistence.saveInventoryEntries(inventoryMovementEntries);
        }
    }

    static List<String> inventoryMovementEntries()
    {
        synchronized (LOCK)
        {
            return List.copyOf(inventoryMovementEntries);
        }
    }

    static void clearForTests()
    {
        synchronized (LOCK)
        {
            bankTransactions = List.of();
            jobs.clear();
            inventoryMovementEntries.clear();
        }
    }

    record ImportExportJob(LocalDateTime recordedAt,
                           String operation,
                           String sourcePath,
                           String targetPath,
                           BankingDataFormat format,
                           int rowCount,
                           int transactionCount,
                           String outcome,
                           String error)
    {
    }
}
