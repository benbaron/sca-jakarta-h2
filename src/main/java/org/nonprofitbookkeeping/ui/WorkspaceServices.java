package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.interchange.sclx.SclxImportCommitService;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportPreviewService;
import org.nonprofitbookkeeping.interchange.bank.BankCsvMappingProfileService;
import org.nonprofitbookkeeping.interchange.bank.BankCsvReviewService;
import org.nonprofitbookkeeping.interchange.bank.BankReviewQueryService;
import org.nonprofitbookkeeping.interchange.bank.BankStatementReviewService;
import org.nonprofitbookkeeping.service.BankConfigurationService;
import org.nonprofitbookkeeping.service.dashboard.DashboardQueryService;
import org.nonprofitbookkeeping.service.DiagnosticsQueryService;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Shell-owned services and observable context for workspace construction. */
public final class WorkspaceServices
{
    private final WorkspaceContext context;
    private final DatabaseSessionController databaseSessionController;
    private final CompanySessionController companySessionController;
    private final DatabaseTransferActions databaseTransferActions;
    private final SclxExportActions sclxExportActions;
    private final PanelFactory panelFactory;
    private final Supplier<DashboardQueryService> dashboardQueryService;
    private final Supplier<DiagnosticsQueryService> diagnosticsQueryService;
    private final Supplier<SclxImportPreviewService> sclxImportPreviewService;
    private final Function<String, SclxImportCommitService> sclxImportCommitService;
    private final Supplier<BankConfigurationService> bankConfigurationService;
    private final Supplier<BankStatementReviewService> bankStatementReviewService;
    private final Supplier<BankCsvReviewService> bankCsvReviewService;
    private final Supplier<BankCsvMappingProfileService> bankCsvMappingProfileService;
    private final Supplier<BankReviewQueryService> bankReviewQueryService;

    WorkspaceServices(
            WorkspaceContext context,
            DatabaseSessionController databaseSessionController,
            CompanySessionController companySessionController,
            DatabaseTransferActions databaseTransferActions,
            SclxExportActions sclxExportActions,
            Supplier<DashboardQueryService> dashboardQueryService,
            Supplier<DiagnosticsQueryService> diagnosticsQueryService,
            Supplier<SclxImportPreviewService> sclxImportPreviewService,
            Function<String, SclxImportCommitService> sclxImportCommitService,
            Supplier<BankConfigurationService> bankConfigurationService,
            Supplier<BankStatementReviewService> bankStatementReviewService,
            Supplier<BankCsvReviewService> bankCsvReviewService,
            Supplier<BankCsvMappingProfileService> bankCsvMappingProfileService,
            Supplier<BankReviewQueryService> bankReviewQueryService)
    {
        this.context = Objects.requireNonNull(context, "context");
        this.databaseSessionController = Objects.requireNonNull(databaseSessionController, "databaseSessionController");
        this.companySessionController = Objects.requireNonNull(companySessionController, "companySessionController");
        this.databaseTransferActions = Objects.requireNonNull(databaseTransferActions, "databaseTransferActions");
        this.sclxExportActions = Objects.requireNonNull(sclxExportActions, "sclxExportActions");
        this.dashboardQueryService = Objects.requireNonNull(dashboardQueryService, "dashboardQueryService");
        this.diagnosticsQueryService = Objects.requireNonNull(diagnosticsQueryService, "diagnosticsQueryService");
        this.sclxImportPreviewService = Objects.requireNonNull(
                sclxImportPreviewService, "sclxImportPreviewService");
        this.sclxImportCommitService = Objects.requireNonNull(
                sclxImportCommitService, "sclxImportCommitService");
        this.bankConfigurationService = Objects.requireNonNull(bankConfigurationService, "bankConfigurationService");
        this.bankStatementReviewService = Objects.requireNonNull(bankStatementReviewService, "bankStatementReviewService");
        this.bankCsvReviewService = Objects.requireNonNull(bankCsvReviewService, "bankCsvReviewService");
        this.bankCsvMappingProfileService = Objects.requireNonNull(bankCsvMappingProfileService, "bankCsvMappingProfileService");
        this.bankReviewQueryService = Objects.requireNonNull(bankReviewQueryService, "bankReviewQueryService");
        this.panelFactory = new PanelFactory(this);
    }

    public WorkspaceContext context()
    {
        return context;
    }

    DatabaseSessionController databaseSessionController()
    {
        return databaseSessionController;
    }

    CompanySessionController companySessionController()
    {
        return companySessionController;
    }

    DatabaseTransferActions databaseTransferActions()
    {
        return databaseTransferActions;
    }

    SclxExportActions sclxExportActions()
    {
        return sclxExportActions;
    }

    PanelFactory panelFactory()
    {
        return panelFactory;
    }

    DashboardQueryService dashboardQueryService()
    {
        return dashboardQueryService.get();
    }

    DiagnosticsQueryService diagnosticsQueryService()
    {
        return diagnosticsQueryService.get();
    }

    SclxImportPreviewService sclxImportPreviewService()
    {
        return sclxImportPreviewService.get();
    }

    SclxImportCommitService sclxImportCommitService(String companyCode)
    {
        return sclxImportCommitService.apply(companyCode);
    }

    BankConfigurationService bankConfigurationService() { return bankConfigurationService.get(); }
    BankStatementReviewService bankStatementReviewService() { return bankStatementReviewService.get(); }
    BankCsvReviewService bankCsvReviewService() { return bankCsvReviewService.get(); }
    BankCsvMappingProfileService bankCsvMappingProfileService() { return bankCsvMappingProfileService.get(); }
    BankReviewQueryService bankReviewQueryService() { return bankReviewQueryService.get(); }
}
