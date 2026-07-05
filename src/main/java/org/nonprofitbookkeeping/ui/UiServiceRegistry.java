package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.persistence.DatabaseLocationService;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.repository.ApprovalAuditRepository;
import org.nonprofitbookkeeping.repository.JdbcApprovalAuditRepository;
import org.nonprofitbookkeeping.repository.JdbcPeriodCloseRunRepository;
import org.nonprofitbookkeeping.repository.JdbcReconciliationRunRepository;
import org.nonprofitbookkeeping.repository.PeriodCloseRunRepository;
import org.nonprofitbookkeeping.repository.ReconciliationRunRepository;
import org.nonprofitbookkeeping.service.AccountAdminService;
import org.nonprofitbookkeeping.service.AccountLookupService;
import org.nonprofitbookkeeping.service.ApprovalAuditService;
import org.nonprofitbookkeeping.service.BudgetCategoryAdminService;
import org.nonprofitbookkeeping.service.BudgetCategoryLookupService;
import org.nonprofitbookkeeping.service.BudgetPlanService;
import org.nonprofitbookkeeping.service.CompanyAdminService;
import org.nonprofitbookkeeping.service.FinancialReportService;
import org.nonprofitbookkeeping.service.FundAdminService;
import org.nonprofitbookkeeping.service.FundBalanceService;
import org.nonprofitbookkeeping.service.FundLookupService;
import org.nonprofitbookkeeping.service.LedgerQueryService;
import org.nonprofitbookkeeping.service.PeriodCloseService;
import org.nonprofitbookkeeping.service.ReconciliationService;
import org.nonprofitbookkeeping.service.ScheduleEligibilityService;
import org.nonprofitbookkeeping.service.SampleCompanyService;
import org.nonprofitbookkeeping.service.TransactionEntryService;
import org.nonprofitbookkeeping.service.TransactionCorrectionService;
import org.nonprofitbookkeeping.service.TransactionReferenceDataService;
import org.nonprofitbookkeeping.service.UserAdminService;
import org.nonprofitbookkeeping.service.dashboard.DashboardQueryService;
import org.nonprofitbookkeeping.service.dashboard.JpaDashboardQueryService;

import java.nio.file.Path;

/**
 * Lightweight service wiring for JavaFX runtime without CDI bootstrap.
 */
public final class UiServiceRegistry
{
    private static final Object LOCK = new Object();

    private static ServiceBundle services;
    private static RuntimeException lastInitializationFailure;

    private UiServiceRegistry()
    {
    }

    public static AccountLookupService accountLookup() { return services().accountLookup(); }
    public static FundLookupService fundLookup() { return services().fundLookup(); }
    public static BudgetCategoryLookupService budgetCategoryLookup() { return services().budgetCategoryLookup(); }
    public static AccountAdminService accountAdmin() { return services().accountAdmin(); }
    public static FundAdminService fundAdmin() { return services().fundAdmin(); }
    public static BudgetCategoryAdminService budgetCategoryAdmin() { return services().budgetCategoryAdmin(); }
    public static BudgetPlanService budgetPlan() { return services().budgetPlan(); }
    public static CompanyAdminService companyAdmin() { return services().companyAdmin(); }
    public static UserAdminService userAdmin() { return services().userAdmin(); }
    public static FundBalanceService fundBalance() { return services().fundBalance(); }
    public static ScheduleEligibilityService schedules() { return services().schedules(); }
    public static LedgerQueryService ledgerQuery() { return services().ledgerQuery(); }
    public static TransactionEntryService transactionEntry() { return services().transactionEntry(); }
    public static TransactionCorrectionService transactionCorrection() { return services().transactionCorrection(); }
    public static TransactionReferenceDataService transactionReferenceData() { return services().transactionReferenceData(); }
    public static SampleCompanyService sampleCompany() { return services().sampleCompany(); }
    public static FinancialReportService financialReports() { return services().financialReports(); }
    public static DashboardQueryService dashboardQuery() { return services().dashboardQuery(); }

    private static ServiceBundle services()
    {
        ServiceBundle current = services;
        if (current != null)
        {
            return current;
        }
        synchronized (LOCK)
        {
            if (services != null)
            {
                return services;
            }
            if (lastInitializationFailure != null)
            {
                System.err.println("[NPBK] Previous UiServiceRegistry initialization failure will be rethrown.");
                lastInitializationFailure.printStackTrace(System.err);
                throw lastInitializationFailure;
            }
            try
            {
                System.err.println("[NPBK] UiServiceRegistry initializing services.");
                services = buildServices(defaultJpa());
                System.err.println("[NPBK] UiServiceRegistry services initialized.");
                return services;
            }
            catch (RuntimeException ex)
            {
                lastInitializationFailure = ex;
                System.err.println("[NPBK] UiServiceRegistry service initialization failed: "
                        + ex.getClass().getName() + ": " + ex.getMessage());
                ex.printStackTrace(System.err);
                throw ex;
            }
        }
    }

    private static Jpa defaultJpa()
    {
        Path databasePath = DatabaseLocationService.resolveDatabasePath(
                MainWindow.sharedSessionState().databaseSelection().activeDatabasePath());
        System.err.println("[NPBK] UiServiceRegistry selected database path: " + databasePath.toAbsolutePath());
        try
        {
            return new Jpa(databasePath);
        }
        catch (RuntimeException ex)
        {
            throw new IllegalStateException("Could not initialize services for selected database "
                    + databasePath.toAbsolutePath() + ". Underlying error: " + ex.getMessage(), ex);
        }
    }

    private static ServiceBundle buildServices(Jpa jpa)
    {
        System.err.println("[NPBK] Building UI service bundle.");
        return new ServiceBundle(
                jpa,
                new AccountLookupService(jpa),
                new FundLookupService(jpa),
                new BudgetCategoryLookupService(jpa),
                new AccountAdminService(jpa),
                new FundAdminService(jpa),
                new BudgetCategoryAdminService(jpa),
                new BudgetPlanService(jpa),
                new CompanyAdminService(jpa),
                new UserAdminService(jpa),
                new FundBalanceService(jpa),
                new ScheduleEligibilityService(jpa),
                new LedgerQueryService(jpa),
                new TransactionEntryService(jpa),
                new TransactionCorrectionService(jpa),
                new TransactionReferenceDataService(jpa),
                new SampleCompanyService(jpa),
                new FinancialReportService(jpa),
                new JpaDashboardQueryService(jpa));
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
            Path resolved = DatabaseLocationService.ensureParentDirectory(databaseFile);
            System.err.println("[NPBK] UiServiceRegistry reconnecting to database: " + resolved.toAbsolutePath());
            ServiceBundle oldServices = services;
            Jpa nextJpa = new Jpa(resolved);
            ServiceBundle nextServices;
            try
            {
                nextServices = buildServices(nextJpa);
            }
            catch (RuntimeException ex)
            {
                nextJpa.close();
                throw ex;
            }
            services = nextServices;
            lastInitializationFailure = null;
            if (oldServices != null)
            {
                oldServices.close();
            }
            System.err.println("[NPBK] UiServiceRegistry reconnected to database: " + resolved.toAbsolutePath());
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
            BudgetPlanService budgetPlan,
            CompanyAdminService companyAdmin,
            UserAdminService userAdmin,
            FundBalanceService fundBalance,
            ScheduleEligibilityService schedules,
            LedgerQueryService ledgerQuery,
            TransactionEntryService transactionEntry,
            TransactionCorrectionService transactionCorrection,
            TransactionReferenceDataService transactionReferenceData,
            SampleCompanyService sampleCompany,
            FinancialReportService financialReports,
            DashboardQueryService dashboardQuery)
    {
        void close()
        {
            jpa.close();
        }
    }
}
