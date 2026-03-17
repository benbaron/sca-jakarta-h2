package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.AccountLookupService;
import org.nonprofitbookkeeping.service.FundBalanceService;
import org.nonprofitbookkeeping.service.FundLookupService;
import org.nonprofitbookkeeping.service.AccountAdminService;
import org.nonprofitbookkeeping.service.FundAdminService;
import org.nonprofitbookkeeping.service.LedgerQueryService;
import org.nonprofitbookkeeping.service.FinancialReportService;
import org.nonprofitbookkeeping.service.ScheduleEligibilityService;
import org.nonprofitbookkeeping.repository.JdbcPeriodCloseRunRepository;
import org.nonprofitbookkeeping.repository.JdbcReconciliationRunRepository;
import org.nonprofitbookkeeping.repository.PeriodCloseRunRepository;
import org.nonprofitbookkeeping.repository.ReconciliationRunRepository;
import org.nonprofitbookkeeping.service.PeriodCloseService;
import org.nonprofitbookkeeping.service.ReconciliationService;
import org.nonprofitbookkeeping.service.ApprovalAuditService;
import org.nonprofitbookkeeping.repository.JdbcApprovalAuditRepository;
import org.nonprofitbookkeeping.repository.ApprovalAuditRepository;

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
    private static AccountAdminService accountAdmin = new AccountAdminService(jpa);
    private static FundAdminService fundAdmin = new FundAdminService(jpa);
    private static FundBalanceService fundBalance = new FundBalanceService(jpa);
    private static ScheduleEligibilityService schedules = new ScheduleEligibilityService(jpa);
    private static LedgerQueryService ledgerQuery = new LedgerQueryService(jpa);
    private static FinancialReportService financialReports = new FinancialReportService(jpa);

    private UiServiceRegistry() {}

    public static AccountLookupService accountLookup() { return accountLookup; }
    public static FundLookupService fundLookup() { return fundLookup; }
    public static AccountAdminService accountAdmin() { return accountAdmin; }
    public static FundAdminService fundAdmin() { return fundAdmin; }
    public static FundBalanceService fundBalance() { return fundBalance; }
    public static ScheduleEligibilityService schedules() { return schedules; }
    public static LedgerQueryService ledgerQuery() { return ledgerQuery; }
    public static FinancialReportService financialReports() { return financialReports; }


    public static ReconciliationRunRepository reconciliationRunRepository()
    {
        return new JdbcReconciliationRunRepository(UiDataSources.forCurrentSessionDatabase());
    }

    public static PeriodCloseRunRepository periodCloseRunRepository()
    {
        return new JdbcPeriodCloseRunRepository(UiDataSources.forCurrentSessionDatabase());
    }

    public static ReconciliationService reconciliationService()
    {
        return new ReconciliationService(reconciliationRunRepository());
    }

    public static PeriodCloseService periodCloseService()
    {
        return new PeriodCloseService(periodCloseRunRepository());
    }


    public static ApprovalAuditRepository approvalAuditRepository()
    {
        return new JdbcApprovalAuditRepository(UiDataSources.forCurrentSessionDatabase());
    }

    public static ApprovalAuditService approvalAuditService()
    {
        return new ApprovalAuditService(approvalAuditRepository());
    }

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
                accountAdmin = new AccountAdminService(nextJpa);
                fundAdmin = new FundAdminService(nextJpa);
                fundBalance = new FundBalanceService(nextJpa);
                schedules = new ScheduleEligibilityService(nextJpa);
                ledgerQuery = new LedgerQueryService(nextJpa);
                financialReports = new FinancialReportService(nextJpa);
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
