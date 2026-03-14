package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.AccountLookupService;
import org.nonprofitbookkeeping.service.FundBalanceService;
import org.nonprofitbookkeeping.service.FundLookupService;
import org.nonprofitbookkeeping.service.LedgerQueryService;
import org.nonprofitbookkeeping.service.ScheduleEligibilityService;

import java.nio.file.Path;

/**
 * Lightweight service wiring for JavaFX runtime (without CDI bootstrap).
 */
public final class UiServiceRegistry
{
    private static final Object LOCK = new Object();

    private static Jpa jpa = new Jpa();
    private static AccountLookupService accountLookup = new AccountLookupService(jpa);
    private static FundLookupService fundLookup = new FundLookupService(jpa);
    private static FundBalanceService fundBalance = new FundBalanceService(jpa);
    private static ScheduleEligibilityService schedules = new ScheduleEligibilityService(jpa);
    private static LedgerQueryService ledgerQuery = new LedgerQueryService(jpa);

    private UiServiceRegistry() {}

    public static AccountLookupService accountLookup() { return accountLookup; }
    public static FundLookupService fundLookup() { return fundLookup; }
    public static FundBalanceService fundBalance() { return fundBalance; }
    public static ScheduleEligibilityService schedules() { return schedules; }
    public static LedgerQueryService ledgerQuery() { return ledgerQuery; }

    public static void reconnectToDatabase(Path databaseFile)
    {
        synchronized (LOCK)
        {
            Jpa oldJpa = jpa;
            Jpa nextJpa = new Jpa(databaseFile);
            try
            {
                accountLookup = new AccountLookupService(nextJpa);
                fundLookup = new FundLookupService(nextJpa);
                fundBalance = new FundBalanceService(nextJpa);
                schedules = new ScheduleEligibilityService(nextJpa);
                ledgerQuery = new LedgerQueryService(nextJpa);
                jpa = nextJpa;
            }
            catch (RuntimeException ex)
            {
                nextJpa.close();
                throw ex;
            }

            oldJpa.close();
        }
    }
}
