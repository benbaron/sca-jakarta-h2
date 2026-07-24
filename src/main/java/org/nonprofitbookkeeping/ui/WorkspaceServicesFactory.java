package org.nonprofitbookkeeping.ui;

import javafx.stage.Window;
import org.nonprofitbookkeeping.persistence.DatabaseTransferService;

import java.util.Objects;
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
        return create(sessionState, stateStore, connector, () -> null, () -> { });
    }

    static WorkspaceServices create(
            UiSessionState sessionState,
            AppStateStore stateStore,
            DatabaseSessionController.Connector connector,
            Supplier<Window> ownerWindow,
            Runnable afterSuccessfulDatabaseSwitch)
    {
        Objects.requireNonNull(sessionState, "sessionState");
        Objects.requireNonNull(stateStore, "stateStore");
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(ownerWindow, "ownerWindow");
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
                databaseSessionController::connect);
        DatabaseTransferActions databaseTransferActions = new DatabaseTransferCoordinator(
                databaseTransferService,
                databaseSessionController::activeDatabasePath,
                ownerWindow,
                afterSuccessfulDatabaseSwitch);

        sessionState.onDatabaseSelectionChanged(context::applyDatabaseSelection);
        sessionState.onMultiCompanyChanged(context::applyMultiCompany);
        ActivePeriodContext.activeDateProperty().addListener(
                (observable, oldDate, newDate) -> context.setActivePeriodDate(newDate));
        return new WorkspaceServices(
                context,
                databaseSessionController,
                companySessionController,
                databaseTransferActions,
                UiServiceRegistry::dashboardQuery,
                UiServiceRegistry::diagnosticsQuery);
    }
}
