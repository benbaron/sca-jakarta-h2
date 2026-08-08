package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.interchange.sclx.SclxCoreSnapshotQueryService;
import org.nonprofitbookkeeping.interchange.sclx.SclxFileExportService;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportPreviewService;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportCommitService;
import org.nonprofitbookkeeping.interchange.bank.BankCsvMappingProfileService;
import org.nonprofitbookkeeping.interchange.bank.BankCsvReviewService;
import org.nonprofitbookkeeping.interchange.bank.BankReviewQueryService;
import org.nonprofitbookkeeping.interchange.bank.BankStatementCsvExportService;
import org.nonprofitbookkeeping.interchange.bank.BankStatementOfxExportService;
import org.nonprofitbookkeeping.interchange.bank.BankStatementReviewService;
import org.nonprofitbookkeeping.interchange.bank.NormalizedBankCsvReviewService;
import org.nonprofitbookkeeping.persistence.DatabaseLocationService;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.repository.ApprovalAuditRepository;
import org.nonprofitbookkeeping.repository.JdbcApprovalAuditRepository;
import org.nonprofitbookkeeping.repository.JdbcCompanyUiPreferenceRepository;
import org.nonprofitbookkeeping.repository.JdbcPeriodCloseRunRepository;
import org.nonprofitbookkeeping.repository.JdbcReconciliationRunRepository;
import org.nonprofitbookkeeping.repository.PeriodCloseRunRepository;
import org.nonprofitbookkeeping.repository.ReconciliationRunRepository;
import org.nonprofitbookkeeping.service.AccountAdminService;
import org.nonprofitbookkeeping.service.AccountLookupService;
import org.nonprofitbookkeeping.service.ApprovalAuditService;
import org.nonprofitbookkeeping.service.BankConfigurationService;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService;
import org.nonprofitbookkeeping.service.BudgetCategoryAdminService;
import org.nonprofitbookkeeping.service.BudgetCategoryLookupService;
import org.nonprofitbookkeeping.service.BudgetPlanService;
import org.nonprofitbookkeeping.service.CompanyAdminService;
import org.nonprofitbookkeeping.service.CompanyView;
import org.nonprofitbookkeeping.service.CompanyUiPreferencesService;
import org.nonprofitbookkeeping.service.CoaCsvImportService;
import org.nonprofitbookkeeping.service.DiagnosticsQueryService;
import org.nonprofitbookkeeping.service.FinancialReportService;
import org.nonprofitbookkeeping.service.FixedAssetService;
import org.nonprofitbookkeeping.service.FundAdminService;
import org.nonprofitbookkeeping.service.FundBalanceService;
import org.nonprofitbookkeeping.service.FundLookupService;
import org.nonprofitbookkeeping.service.InventoryService;
import org.nonprofitbookkeeping.service.LedgerQueryService;
import org.nonprofitbookkeeping.service.PeriodCloseRangeService;
import org.nonprofitbookkeeping.service.PeriodCloseService;
import org.nonprofitbookkeeping.service.ReconciliationComparisonService;
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
import java.util.List;
import java.util.Objects;

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
    public static CoaCsvImportService coaCsvImport()
    {
        return new CoaCsvImportService(services().jpa(), UiServiceRegistry::activeCompanyCode);
    }
    public static FundAdminService fundAdmin() { return services().fundAdmin(); }
    public static BudgetCategoryAdminService budgetCategoryAdmin() { return services().budgetCategoryAdmin(); }
    public static BudgetPlanService budgetPlan() { return services().budgetPlan(); }
    public static BankConfigurationService bankConfiguration() { return services().bankConfiguration(); }
    public static BankStatementReviewService bankStatementReview()
    {
        return new BankStatementReviewService(services().jpa());
    }
    public static BankCsvReviewService bankCsvReview()
    {
        return new BankCsvReviewService(services().jpa());
    }
    public static BankCsvMappingProfileService bankCsvMappingProfiles()
    {
        return new BankCsvMappingProfileService(services().jpa());
    }
    public static NormalizedBankCsvReviewService normalizedBankCsvReview()
    {
        return new NormalizedBankCsvReviewService(services().jpa());
    }
    public static BankReviewQueryService bankReviewQuery()
    {
        return new BankReviewQueryService(services().jpa());
    }
    public static BankStatementCsvExportService bankStatementCsvExport(Path activeDatabasePath)
    {
        Path fixedDatabasePath = Objects.requireNonNull(activeDatabasePath, "activeDatabasePath")
                .toAbsolutePath()
                .normalize();
        return new BankStatementCsvExportService(services().jpa(), () -> fixedDatabasePath);
    }
    public static BankStatementOfxExportService bankStatementOfxExport(Path activeDatabasePath)
    {
        Path fixedDatabasePath = Objects.requireNonNull(activeDatabasePath, "activeDatabasePath")
                .toAbsolutePath()
                .normalize();
        return new BankStatementOfxExportService(services().jpa(), () -> fixedDatabasePath);
    }
    public static FixedAssetService fixedAssets() { return services().fixedAssets(); }
    public static InventoryService inventory() { return services().inventory(); }
    public static CompanyAdminService companyAdmin() { return services().companyAdmin(); }
    public static CompanyUiPreferencesService companyUiPreferences()
    {
        return new CompanyUiPreferencesService(
                new JdbcCompanyUiPreferenceRepository(UiDataSources.forCurrentSessionDatabase()));
    }
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
    public static SclxFileExportService sclxFileExport(String companyCode, Path activeDatabasePath)
    {
        String fixedCompanyCode = Objects.requireNonNull(companyCode, "companyCode").strip();
        if (fixedCompanyCode.isBlank())
        {
            throw new IllegalArgumentException("companyCode must not be blank");
        }
        Path fixedDatabasePath = Objects.requireNonNull(activeDatabasePath, "activeDatabasePath")
                .toAbsolutePath()
                .normalize();
        ServiceBundle current = services();
        return new SclxFileExportService(
                new SclxCoreSnapshotQueryService(current.jpa(), () -> fixedCompanyCode),
                () -> fixedDatabasePath);
    }
    public static SclxImportPreviewService sclxImportPreview()
    {
        return sclxImportPreview(activeCompanyCode());
    }
    public static SclxImportPreviewService sclxImportPreview(String companyCode)
    {
        String fixedCompanyCode = Objects.requireNonNull(companyCode, "companyCode").strip();
        if (fixedCompanyCode.isBlank())
        {
            throw new IllegalArgumentException("companyCode must not be blank");
        }
        return new SclxImportPreviewService(services().jpa(), () -> fixedCompanyCode);
    }
    public static SclxImportCommitService sclxImportCommit(String companyCode)
    {
        String fixedCompanyCode = Objects.requireNonNull(companyCode, "companyCode").strip();
        if (fixedCompanyCode.isBlank())
        {
            throw new IllegalArgumentException("companyCode must not be blank");
        }
        return new SclxImportCommitService(services().jpa(), () -> fixedCompanyCode);
    }
    public static BankReconciliationWorkspaceService bankReconciliationWorkspace() { return services().bankReconciliationWorkspace(); }
    public static PeriodCloseRangeService periodCloseRangeService() { return services().periodCloseRangeService(); }
    public static DiagnosticsQueryService diagnosticsQuery()
    {
        ServiceBundle current = services();
        return new DiagnosticsQueryService(
                UiDataSources.forCurrentSessionDatabase(),
                current.accountLookup(),
                current.fundLookup(),
                UiServiceRegistry::activeCompanyCode,
                () -> DatabaseLocationService.resolveDatabasePath(
                        MainWindow.sharedSessionState().databaseSelection().activeDatabasePath()));
    }

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
        TransactionEntryService transactionEntry = new TransactionEntryService(jpa, UiServiceRegistry::activeCompanyCode);
        PeriodCloseRangeService periodCloseRange = new PeriodCloseRangeService(jpa);
        return new ServiceBundle(
                jpa,
                new AccountLookupService(jpa, UiServiceRegistry::activeCompanyCode),
                new FundLookupService(jpa, UiServiceRegistry::activeCompanyCode),
                new BudgetCategoryLookupService(jpa, UiServiceRegistry::activeCompanyCode),
                new AccountAdminService(jpa, UiServiceRegistry::activeCompanyCode),
                new FundAdminService(jpa, UiServiceRegistry::activeCompanyCode),
                new BudgetCategoryAdminService(jpa, UiServiceRegistry::activeCompanyCode),
                new BudgetPlanService(jpa, UiServiceRegistry::activeCompanyCode),
                new BankConfigurationService(jpa),
                new FixedAssetService(jpa, transactionEntry, UiServiceRegistry::activeCompanyCode),
                new InventoryService(jpa),
                new CompanyAdminService(jpa),
                new UserAdminService(jpa),
                new FundBalanceService(jpa),
                new ScheduleEligibilityService(jpa),
                new LedgerQueryService(jpa),
                transactionEntry,
                new TransactionCorrectionService(jpa, UiServiceRegistry::activeCompanyCode),
                new TransactionReferenceDataService(jpa, UiServiceRegistry::activeCompanyCode),
                new SampleCompanyService(jpa),
                new FinancialReportService(jpa),
                new JpaDashboardQueryService(jpa),
                new BankReconciliationWorkspaceService(jpa),
                periodCloseRange);
    }

    private static String activeCompanyCode()
    {
        String company = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        return company == null || company.isBlank() ? "DEFAULT" : company.trim();
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

    public static ReconciliationComparisonService reconciliationComparison()
    {
        return new ReconciliationComparisonService(services().jpa(), reconciliationService());
    }

    /** Legacy run-artifact service retained for compatibility outside the production Period Close workspace. */
    @Deprecated
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

    /**
     * Prepares a target database completely without changing the currently active
     * service bundle. Migration, JPA construction, service composition, and
     * authoritative active-company resolution must all succeed before the
     * returned connection can be activated.
     */
    static DatabaseSessionController.PreparedConnection prepareDatabaseConnection(
            Path databaseFile,
            String preferredCompanyCode)
    {
        Path resolved = DatabaseLocationService.ensureParentDirectory(databaseFile);
        System.err.println("[NPBK] UiServiceRegistry preparing database: " + resolved.toAbsolutePath());

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

        try
        {
            CompanyView selected = nextServices.companyAdmin().resolveActiveCompany(preferredCompanyCode);
            List<String> activeCompanies = nextServices.companyAdmin().listActiveCompanyViews().stream()
                    .map(CompanyView::code)
                    .toList();
            if (activeCompanies.isEmpty())
            {
                throw new IllegalStateException(
                        "The selected database has no active company and cannot become the active session.");
            }
            return new PreparedServiceBundle(resolved, selected.code(), activeCompanies, nextServices);
        }
        catch (RuntimeException ex)
        {
            nextServices.close();
            throw ex;
        }
    }

    /**
     * Compatibility entry point used outside the production session controller.
     * New production switching goes through {@link DatabaseSessionController}.
     */
    public static void reconnectToDatabase(Path databaseFile)
    {
        try (DatabaseSessionController.PreparedConnection prepared = prepareDatabaseConnection(
                databaseFile,
                activeCompanyCode()))
        {
            prepared.activate();
        }
    }

    private static final class PreparedServiceBundle implements DatabaseSessionController.PreparedConnection
    {
        private final Path databasePath;
        private final String activeCompanyCode;
        private final List<String> activeCompanyCodes;
        private ServiceBundle preparedServices;
        private boolean activated;

        private PreparedServiceBundle(
                Path databasePath,
                String activeCompanyCode,
                List<String> activeCompanyCodes,
                ServiceBundle preparedServices)
        {
            this.databasePath = databasePath;
            this.activeCompanyCode = activeCompanyCode;
            this.activeCompanyCodes = List.copyOf(activeCompanyCodes);
            this.preparedServices = preparedServices;
        }

        @Override
        public Path databasePath()
        {
            return databasePath;
        }

        @Override
        public String activeCompanyCode()
        {
            return activeCompanyCode;
        }

        @Override
        public List<String> activeCompanyCodes()
        {
            return activeCompanyCodes;
        }

        @Override
        public void activate()
        {
            if (activated)
            {
                return;
            }
            synchronized (LOCK)
            {
                ServiceBundle oldServices = services;
                services = Objects.requireNonNull(preparedServices, "preparedServices");
                preparedServices = null;
                lastInitializationFailure = null;
                activated = true;
                if (oldServices != null)
                {
                    try
                    {
                        oldServices.close();
                    }
                    catch (RuntimeException ex)
                    {
                        // The replacement session is already authoritative. A close
                        // failure on the superseded bundle must not undo the swap.
                        System.err.println("[NPBK] Could not close previous database services: "
                                + ex.getMessage());
                    }
                }
            }
            System.err.println("[NPBK] UiServiceRegistry activated database: "
                    + databasePath.toAbsolutePath());
        }

        @Override
        public void close()
        {
            if (!activated && preparedServices != null)
            {
                try
                {
                    preparedServices.close();
                }
                finally
                {
                    preparedServices = null;
                }
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
            BudgetPlanService budgetPlan,
            BankConfigurationService bankConfiguration,
            FixedAssetService fixedAssets,
            InventoryService inventory,
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
            DashboardQueryService dashboardQuery,
            BankReconciliationWorkspaceService bankReconciliationWorkspace,
            PeriodCloseRangeService periodCloseRangeService)
    {
        void close()
        {
            jpa.close();
        }
    }
}
