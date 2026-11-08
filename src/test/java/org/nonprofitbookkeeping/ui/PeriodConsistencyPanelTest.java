package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.CompanyOwnershipService;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class PeriodConsistencyPanelTest
{
    @TempDir Path directory;

    @Test
    void closeDepreciationAndReportsShareSelectedStartAndFiscalEndDespiteNewPreference() throws Exception
    {
        FxTestSupport.initToolkitOrSkip();
        UiSessionState session = MainWindow.sharedSessionState();
        var previousDatabase = session.databaseSelection();
        var previousCompany = session.multiCompany();
        var previousUser = session.authenticatedUser();
        var previousPreferences = session.preferences();
        LocalDate previousPeriod = ActivePeriodContext.get();
        DateRange previousRange = DateRangeContext.get();
        Path db = directory.resolve("period-panels");
        try
        {
            FxTestSupport.onFx(() ->
            {
                Platform.setImplicitExit(false);
                session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
                session.setAuthenticatedUser(UiPermissionTestSessions.manager());
                session.setDatabaseSelection(new DatabaseSelectionState(db.toString(), List.of(db.toString())));
                UiServiceRegistry.reconnectToDatabase(db);
                return null;
            });
            try (Jpa jpa = new Jpa(db); var em = jpa.em())
            {
                em.getTransaction().begin();
                var company = new CompanyOwnershipService(jpa).requireCompany(em, "DEFAULT");
                company.setFiscalYearStartMonth(7);
                company.setFiscalYearStartDay(1);
                em.getTransaction().commit();
            }
            FxTestSupport.onFx(() ->
            {
                ActivePeriodContext.select(YearMonth.of(2027, 6), 15);
                AppPreferencesState p = session.preferences();
                session.setPreferences(new AppPreferencesState(p.themePreference(), p.useNativeWindowDecorations(),
                        p.rememberWindowState(), p.defaultPrivilege(), p.correctionMethod(), p.closedPeriodPolicy(),
                        p.requireReopenReason(), p.defaultReopenScope(), p.confirmEnteredTransactionDeletion(), 10));
                DateRangeContext.set(DateRange.ALL);
                PeriodCloseRunsPanel close = new PeriodCloseRunsPanel();
                DepreciationRunsPanel depreciation = new DepreciationRunsPanel();
                ReportLibraryPanel report = new ReportLibraryPanel();
                new Scene((Parent) close.root());
                new Scene((Parent) depreciation.root());
                LocalDate selectedStart = LocalDate.of(2027, 6, 15);
                LocalDate expectedEnd = LocalDate.of(2027, 6, 30);
                assertEquals(selectedStart, picker(close, "periodCloseStartDate").getValue());
                assertEquals(expectedEnd, picker(close, "periodCloseEndDate").getValue());
                String period = ((Label) depreciation.root().lookup("#depreciationActivePeriod")).getText();
                CompanyUiFormat format = CompanyUiFormat.activeCompany();
                assertTrue(period.contains(format.formatDate(selectedStart)), period);
                assertTrue(period.contains(format.formatDate(expectedEnd)), period);
                assertEquals(LocalDate.of(2026, 7, 1), report.startDateForTests());
                assertEquals(expectedEnd, report.endDateForTests());

                // A custom close draft is independent until Use Active Period is explicitly invoked.
                picker(close, "periodCloseStartDate").setValue(LocalDate.of(2027, 3, 2));
                picker(close, "periodCloseEndDate").setValue(LocalDate.of(2027, 3, 4));
                ActivePeriodContext.select(YearMonth.of(2027, 8), 10);
                assertEquals(LocalDate.of(2027, 3, 2), picker(close, "periodCloseStartDate").getValue());
                assertEquals(LocalDate.of(2027, 3, 4), picker(close, "periodCloseEndDate").getValue());
                ((Button) close.root().lookup("#periodCloseUseActivePeriod")).fire();
                assertEquals(LocalDate.of(2027, 8, 10), picker(close, "periodCloseStartDate").getValue());
                assertEquals(LocalDate.of(2027, 9, 9), picker(close, "periodCloseEndDate").getValue());
                assertEquals(LocalDate.of(2027, 9, 9), report.endDateForTests());
                return null;
            });
        }
        finally
        {
            FxTestSupport.onFx(() ->
            {
                session.setPreferences(previousPreferences);
                session.setMultiCompany(previousCompany);
                session.setDatabaseSelection(previousDatabase);
                previousUser.ifPresentOrElse(session::setAuthenticatedUser, session::clearAuthenticatedUser);
                DateRangeContext.set(previousRange);
                ActivePeriodContext.set(previousPeriod);
                return null;
            });
        }
    }

    private static DatePicker picker(PeriodCloseRunsPanel panel, String id)
    {
        return (DatePicker) panel.root().lookup("#" + id);
    }
}
