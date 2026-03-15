package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.service.BankTransactionRecord;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Session-scoped deterministic UI data store for cross-panel projections.
 */
final class UiWorkspaceDataStore
{
    private static final Object LOCK = new Object();
    private static List<BankTransactionRecord> bankTransactions = List.of();
    private static final List<ImportExportJob> jobs = new ArrayList<>();
    private static final Map<String, java.math.BigDecimal> budgetTargetsByFundCode = new LinkedHashMap<>();

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

    static void upsertBudgetTarget(String fundCode, java.math.BigDecimal target)
    {
        if (fundCode == null || fundCode.isBlank() || target == null)
        {
            return;
        }
        synchronized (LOCK)
        {
            budgetTargetsByFundCode.put(fundCode, target);
        }
    }

    static void removeBudgetTarget(String fundCode)
    {
        if (fundCode == null || fundCode.isBlank())
        {
            return;
        }
        synchronized (LOCK)
        {
            budgetTargetsByFundCode.remove(fundCode);
        }
    }

    static Map<String, java.math.BigDecimal> budgetTargetsByFundCode()
    {
        synchronized (LOCK)
        {
            return Map.copyOf(budgetTargetsByFundCode);
        }
    }

    static void clearForTests()
    {
        synchronized (LOCK)
        {
            bankTransactions = List.of();
            jobs.clear();
            budgetTargetsByFundCode.clear();
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
