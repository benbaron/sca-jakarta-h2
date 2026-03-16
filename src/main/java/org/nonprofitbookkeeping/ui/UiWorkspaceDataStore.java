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
    private static final Map<String, java.math.BigDecimal> budgetTargetsByFundCode = new LinkedHashMap<>(BudgetTargetPersistence.load());
    private static final List<String> scheduleRunbookEntries = new ArrayList<>(RunbookPersistence.loadScheduleEntries());
    private static final List<String> assetLifecycleEntries = new ArrayList<>(RunbookPersistence.loadAssetEntries());
    private static final List<String> depreciationRunEntries = new ArrayList<>(RunbookPersistence.loadDepreciationEntries());
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

    static void appendScheduleRunbookEntry(String line)
    {
        synchronized (LOCK)
        {
            scheduleRunbookEntries.add(0, line);
            RunbookPersistence.saveScheduleEntries(scheduleRunbookEntries);
        }
    }

    static List<String> scheduleRunbookEntries()
    {
        synchronized (LOCK)
        {
            return List.copyOf(scheduleRunbookEntries);
        }
    }

    static void appendAssetLifecycleEntry(String line)
    {
        synchronized (LOCK)
        {
            assetLifecycleEntries.add(0, line);
            RunbookPersistence.saveAssetEntries(assetLifecycleEntries);
        }
    }

    static List<String> assetLifecycleEntries()
    {
        synchronized (LOCK)
        {
            return List.copyOf(assetLifecycleEntries);
        }
    }

    static void appendDepreciationRunEntry(String line)
    {
        synchronized (LOCK)
        {
            depreciationRunEntries.add(0, line);
            RunbookPersistence.saveDepreciationEntries(depreciationRunEntries);
        }
    }

    static List<String> depreciationRunEntries()
    {
        synchronized (LOCK)
        {
            return List.copyOf(depreciationRunEntries);
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

    static void upsertBudgetTarget(String fundCode, java.math.BigDecimal target)
    {
        if (fundCode == null || fundCode.isBlank() || target == null)
        {
            return;
        }
        synchronized (LOCK)
        {
            budgetTargetsByFundCode.put(fundCode, target);
            BudgetTargetPersistence.save(budgetTargetsByFundCode);
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
            BudgetTargetPersistence.save(budgetTargetsByFundCode);
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
            scheduleRunbookEntries.clear();
            assetLifecycleEntries.clear();
            depreciationRunEntries.clear();
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
