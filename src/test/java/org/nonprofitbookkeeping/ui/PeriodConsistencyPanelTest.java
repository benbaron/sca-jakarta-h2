package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.stage.Stage;
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
    private Stage stage;

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
                PanelViewport.prepare(close.root());
                PanelViewport viewport = PanelViewport.wrap(close.root());
                stage = new Stage();
                stage.setScene(new Scene(viewport, 480, 400));
                stage.show();
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
                @SuppressWarnings("unchecked")
                ComboBox<YearMonth> selector = (ComboBox<YearMonth>) close.root().lookup("#periodClosePeriodSelector");
                selector.setValue(YearMonth.of(2027, 8));
                assertEquals(selectedStart, ActivePeriodContext.get(), "Choosing a candidate must not apply it");
                ((Button) close.root().lookup("#periodCloseSetActivePeriod")).fire();
                assertEquals(LocalDate.of(2027, 8, 10), ActivePeriodContext.get());
                String applied = ((Label) close.root().lookup("#periodCloseActivePeriodRange")).getText();
                assertTrue(applied.contains(format.formatDate(LocalDate.of(2027, 8, 10))), applied);
                assertTrue(applied.contains(format.formatDate(LocalDate.of(2027, 9, 9))), applied);
                assertEquals(LocalDate.of(2027, 3, 2), picker(close, "periodCloseStartDate").getValue());
                assertEquals(LocalDate.of(2027, 3, 4), picker(close, "periodCloseEndDate").getValue());
                ((Button) close.root().lookup("#periodCloseUseActivePeriod")).fire();
                assertEquals(LocalDate.of(2027, 8, 10), picker(close, "periodCloseStartDate").getValue());
                assertEquals(LocalDate.of(2027, 9, 9), picker(close, "periodCloseEndDate").getValue());
                assertEquals(LocalDate.of(2027, 9, 9), report.endDateForTests());
                for (int fontSize : new int[] {13, 18})
                {
                    viewport.setStyle("-fx-font-size: " + fontSize + "px;");
                    viewport.applyCss();
                    viewport.layout();
                    var headerSplit = (javafx.scene.control.SplitPane) close.root().lookup(".resizable-control-header-split");
                    var headerScroll = (javafx.scene.control.ScrollPane) headerSplit.getItems().get(0);
                    ScrollBar horizontal = headerScroll.lookupAll(".scroll-bar").stream()
                            .map(ScrollBar.class::cast)
                            .filter(bar -> bar.getParent() == headerScroll
                                    && bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL)
                            .findFirst().orElseThrow();
                    assertTrue(horizontal.isVisible(), "Narrow form must expose horizontal scrolling");
                    Button apply = (Button) close.root().lookup("#periodCloseSetActivePeriod");
                    Label range = (Label) close.root().lookup("#periodCloseActivePeriodRange");
                    assertTrue(apply.getWidth() >= apply.prefWidth(-1) - 1, "Apply text must not be shortened");
                    assertTrue(range.getWidth() >= range.prefWidth(-1) - 1, "Range text must not be shortened");
                    headerScroll.setHvalue(headerScroll.getHmax());
                    viewport.layout();
                    var visible = headerScroll.lookup(".viewport");
                    var clip = visible.localToScene(visible.getLayoutBounds());
                    var bounds = range.localToScene(range.getBoundsInLocal());
                    assertTrue(bounds.getMinX() >= clip.getMinX() - 1
                            && bounds.getMaxX() <= clip.getMaxX() + 1, "Applied range must be reachable by scrolling");
                }
                return null;
            });
        }
        finally
        {
            FxTestSupport.onFx(() ->
            {
                if (stage != null) stage.close();
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
