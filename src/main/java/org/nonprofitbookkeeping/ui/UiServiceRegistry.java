package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.AccountLookupService;
import org.nonprofitbookkeeping.service.BudgetCategoryAdminService;
import org.nonprofitbookkeeping.service.BudgetCategoryLookupService;
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
 *
 * Services are initialized lazily so a database/JPA startup failure does not
 * poison this class during static initialization and then appear only as a
 * misleading NoClassDefFoundError on later JavaFX callbacks.
 */
public final class UiServiceRegistry
{
    private static final Object LOCK = new Object();

    private static ServiceBundle services;

    private UiServiceRegistry() {}

    public static AccountLookupService accountLookup() { return services().accountLookup(); }
    public static FundLookupService fundLookup() { return services().fundLookup(); }
    public static BudgetCategoryLookupService budgetCategoryLookup() { return services().budgetCategoryLookup(); }
    public static AccountAdminService accountAdmin() { return services().accountAdmin(); }
    public static FundAdminService fundAdmin() { return services().fundAdmin(); }
    public static BudgetCategoryAdminService budgetCategoryAdmin() { return services().budgetCategoryAdmin(); }
    public static FundBalanceService fundBalance() { return services().fundBalance(); }
    public static ScheduleEligibilityService schedules() { return services().schedules(); }
    public static LedgerQueryService ledgerQuery() { return services().ledgerQuery(); }
    public static FinancialReportService financialReports() { return services().financialReports(); }

    private static ServiceBundle services()
    {
        ServiceBundle current = services;
        if (current != null)
        {
            return current;
        }
        synchronized (LOCK)
        {
            if (services == null)
            {
                services = buildServices(defaultJpa());
            }
            return services;
        }
    }

    private static Jpa defaultJpa()
    {
        try
        {
            return new Jpa(Path.of(MainWindow.sharedSessionState().databaseSelection().activeDatabasePath()));
        }
        catch (RuntimeException ex)
        {
            throw new IllegalStateException("Could not initialize services for the selected database. "
                    + "Use File > Database Wizard or Select Database File after fixing the database schema. "
                    + "Underlying error: " + ex.getMessage(), ex);
        }
    }

    private static ServiceBundle buildServices(Jpa jpa)
    {
        return new ServiceBundle(
                jpa,
                new AccountLookupService(jpa),
                new FundLookupService(jpa),
                new BudgetCategoryLookupService(jpa),
                new AccountAdminService(jpa),
                new FundAdminService(jpa),
                new BudgetCategoryAdminService(jpa),
                new FundBalanceService(jpa),
                new ScheduleEligibilityService(jpa),
                new LedgerQueryService(jpa),
                new FinancialReportService(jpa)
        );
    }


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
            ServiceBundle oldServices = services;
            Jpa nextJpa = new Jpa(databaseFile);
            ServiceBundle nextServices = buildServices(nextJpa);
            services = nextServices;
            if (oldServices != null)
            {
                oldServices.close();
            }
        }
    }

    private record ServiceBundle(
            Jpa jpa,
            AccountLookupService accountLookup,
            FundLookupService fundLookup,
            BudgetCategoryLookupService budgetCategoryLookup,
            AccountAdminService accountAdmin,
            FundAdminService fundAdmin,
            BudgetCategoryAdminService budgetCategoryAdmin,
            FundBalanceService fundBalance,
            ScheduleEligibilityService schedules,
            LedgerQueryService ledgerQuery,
            FinancialReportService financialReports)
    {
        void close()
        {
            jpa.close();
        }
    }
}
