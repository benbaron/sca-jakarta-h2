package org.nonprofitbookkeeping.ui;

import java.util.Objects;

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
        Objects.requireNonNull(sessionState, "sessionState");
        WorkspaceContext context = WorkspaceContext.fromSession(sessionState);
        DatabaseSessionController databaseSessionController = new DatabaseSessionController(
                sessionState,
                stateStore,
                connector);
        CompanySessionController companySessionController = new CompanySessionController(
                sessionState,
                stateStore,
                UiServiceRegistry::companyAdmin);
        sessionState.onDatabaseSelectionChanged(context::applyDatabaseSelection);
        sessionState.onMultiCompanyChanged(context::applyMultiCompany);
        ActivePeriodContext.activeDateProperty().addListener(
                (observable, oldDate, newDate) -> context.setActivePeriodDate(newDate));
        return new WorkspaceServices(
                context,
                databaseSessionController,
                companySessionController,
                UiServiceRegistry::dashboardQuery);
    }
}
