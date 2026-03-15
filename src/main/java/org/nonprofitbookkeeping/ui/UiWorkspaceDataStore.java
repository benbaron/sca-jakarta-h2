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

    static void clearForTests()
    {
        synchronized (LOCK)
        {
            bankTransactions = List.of();
            jobs.clear();
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
