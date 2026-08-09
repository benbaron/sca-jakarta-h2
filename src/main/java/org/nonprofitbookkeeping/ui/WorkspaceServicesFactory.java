package org.nonprofitbookkeeping.ui;

import javafx.stage.Window;
import org.nonprofitbookkeeping.persistence.DatabaseTransferService;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Creates lifecycle-owned services for a production workspace instance. */
public final class WorkspaceServicesFactory
{
    private WorkspaceServicesFactory()
    {
    }

    public static WorkspaceServices create(
            UiSessionState sessionState,
            AppStateStore stateStore,
            DatabaseSessionController.Connector connector)
    {
        return create(
                sessionState,
                stateStore,
                connector,
                DatabaseTransferUiRegistry::ownerWindow,
                DatabaseTransferUiRegistry::switchDatabase,
                () -> { });
    }

    static WorkspaceServices create(
            UiSessionState sessionState,
            AppStateStore stateStore,
            DatabaseSessionController.Connector connector,
            Supplier<Window> ownerWindow,
            Consumer<Path> databaseSwitcher,
            Runnable afterSuccessfulDatabaseSwitch)
    {
        Objects.requireNonNull(sessionState, "sessionState");
        Objects.requireNonNull(stateStore, "stateStore");
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(ownerWindow, "ownerWindow");
        Objects.requireNonNull(databaseSwitcher, "databaseSwitcher");
        Objects.requireNonNull(afterSuccessfulDatabaseSwitch, "afterSuccessfulDatabaseSwitch");

        WorkspaceContext context = WorkspaceContext.fromSession(sessionState);
        DatabaseSessionController databaseSessionController = new DatabaseSessionController(
                sessionState,
                stateStore,
                connector);
        CompanySessionController companySessionController = new CompanySessionController(
                sessionState,
                stateStore,
                UiServiceRegistry::companyAdmin);
        DatabaseTransferService databaseTransferService = new DatabaseTransferService(
                databaseSessionController::activeDatabasePath,
                databaseSwitcher);
        DatabaseTransferActions databaseTransferActions = new DatabaseTransferCoordinator(
                databaseTransferService,
                databaseSessionController::activeDatabasePath,
                ownerWindow,
                afterSuccessfulDatabaseSwitch);
        DatabaseTransferUiRegistry.registerActions(databaseTransferActions);
        SclxExportActions sclxExportActions = new SclxExportCoordinator(
                UiServiceRegistry::sclxFileExport,
                context,
                ownerWindow);
        SclxExportUiRegistry.registerActions(sclxExportActions);
        BankStatementExportActions bankStatementExportActions = new BankStatementExportCoordinator(
                () -> UiServiceRegistry.bankStatementCsvExport(
                        databaseSessionController.activeDatabasePath()),
                () -> UiServiceRegistry.bankStatementOfxExport(
                        databaseSessionController.activeDatabasePath()),
                context,
                ownerWindow);

        sessionState.onDatabaseSelectionChanged(context::applyDatabaseSelection);
        sessionState.onMultiCompanyChanged(context::applyMultiCompany);
        ActivePeriodContext.activeDateProperty().addListener(
                (observable, oldDate, newDate) -> context.setActivePeriodDate(newDate));
        return new WorkspaceServices(
                context,
                databaseSessionController,
                companySessionController,
                databaseTransferActions,
                sclxExportActions,
                bankStatementExportActions,
                UiServiceRegistry::dashboardQuery,
                UiServiceRegistry::diagnosticsQuery,
                () -> UiServiceRegistry.sclxImportPreview(context.activeCompanyCode()),
                UiServiceRegistry::sclxImportCommit,
                UiServiceRegistry::bankConfiguration,
                UiServiceRegistry::bankStatementReview,
                UiServiceRegistry::bankCsvReview,
                UiServiceRegistry::bankCsvMappingProfiles,
                UiServiceRegistry::normalizedBankCsvReview,
                UiServiceRegistry::bankReviewQuery,
                UiServiceRegistry::reviewedStatementAcceptance,
                UiServiceRegistry::transactionReferenceData);
    }
}
