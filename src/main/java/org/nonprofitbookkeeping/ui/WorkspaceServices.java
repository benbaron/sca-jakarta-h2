package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.interchange.sclx.SclxImportCommitService;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportPreviewService;
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

    WorkspaceServices(
            WorkspaceContext context,
            DatabaseSessionController databaseSessionController,
            CompanySessionController companySessionController,
            DatabaseTransferActions databaseTransferActions,
            SclxExportActions sclxExportActions,
            Supplier<DashboardQueryService> dashboardQueryService,
            Supplier<DiagnosticsQueryService> diagnosticsQueryService,
            Supplier<SclxImportPreviewService> sclxImportPreviewService,
            Function<String, SclxImportCommitService> sclxImportCommitService)
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
}
