package org.nonprofitbookkeeping.ui;

import jakarta.persistence.EntityManager;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.AuthorizationGuard;
import org.nonprofitbookkeeping.service.OpeningConversionService;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("ui-service-registry")
class OpeningConversionPanelTest
{
    private static final LocalDate CUTOFF = LocalDate.of(2026, 1, 31);
    @TempDir Path directory;
    private Jpa jpa;
    private Stage stage;
    private OpeningConversionPanel panel;
    private Optional<org.nonprofitbookkeeping.service.AuthenticatedUserSession> previousUser;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        previousUser = MainWindow.sharedSessionState().authenticatedUser();
        jpa = new Jpa(directory.resolve("opening-conversion-ui"));
        seed();
        AuthorizationGuard guard = new AuthorizationGuard(
                jpa, MainWindow.sharedSessionState()::authenticatedUser);
        OpeningConversionService service = new OpeningConversionService(jpa, () -> "DEFAULT", guard);
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.manager());
            panel = new OpeningConversionPanel(() -> service, () -> true);
            stage = new Stage();
            stage.setScene(new Scene((VBox) panel.root(), 900, 700));
            stage.show();
            return null;
        });
    }

    @AfterEach
    void cleanup()
    {
        FxTestSupport.onFx(() ->
        {
            if (stage != null)
            {
                stage.close();
            }
            previousUser.ifPresentOrElse(
                    MainWindow.sharedSessionState()::setAuthenticatedUser,
                    MainWindow.sharedSessionState()::clearAuthenticatedUser);
            return null;
        });
        if (jpa != null)
        {
            jpa.close();
        }
    }

    @Test
    void previewExposesRealTablesSharedSavePermissionAndRetainedAcceptance()
    {
        FxTestSupport.onFx(() ->
        {
            DatePicker asOf = (DatePicker) panel.root().lookup("#openingConversionAsOf");
            asOf.setValue(CUTOFF);
            ((Button) panel.root().lookup("#openingConversionPreview")).fire();
            assertTrue(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            assertTrue(panel.hasUnsavedChanges());
            assertEquals("Save Conversion Acceptance", panel.commandLabel(AppCommand.SAVE_ACTIVE));
            assertFalse(((TableView<?>) panel.root().lookup("#openingConversionChecks")).getItems().isEmpty());
            assertEquals(2, ((TableView<?>) panel.root().lookup("#openingConversionAccountOpenings")).getItems().size());
            assertEquals(5, ((TabPane) panel.root().lookup("#openingConversionDetailsTabs")).getTabs().size());

            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            assertTrue(((Button) panel.root().lookup("#openingConversionSave")).isDisabled());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.manager());
            assertTrue(panel.supportsCommand(AppCommand.SAVE_ACTIVE));

            assertTrue(panel.executeCommand(AppCommand.SAVE_ACTIVE).handled());
            assertFalse(panel.hasUnsavedChanges());
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            assertTrue(panel.commandResultMessage(AppCommand.SAVE_ACTIVE).contains("No accounting entry was created"));
            assertEquals(1L, auditCount());

            ((Button) panel.root().lookup("#openingConversionPreview")).fire();
            assertTrue(panel.saveUnavailableReason().contains("immutable"));
            return null;
        });
    }

    @Test
    void layoutKeepsReviewRegionsResizableAndScrollable()
    {
        FxTestSupport.onFx(() ->
        {
            SplitPane split = (SplitPane) panel.root().lookup("#openingConversionWorkspaceSplit");
            assertEquals(Orientation.VERTICAL, split.getOrientation());
            assertEquals(2, split.getItems().size());
            split.setDividerPositions(.25);
            stage.setWidth(520);
            panel.root().setStyle("-fx-font-size: 17px;");
            panel.root().applyCss();
            ((VBox) panel.root()).layout();
            assertNotNull(panel.root().lookup("#openingConversionHeaderScroll"));
            for (String id : new String[]{"openingConversionChecks", "openingConversionAccountOpenings",
                    "openingConversionBankOpenings", "openingConversionOutstandingBankItems",
                    "openingConversionOpenItems", "openingConversionOpenItemGaps"})
            {
                TableView<?> table = (TableView<?>) panel.root().lookup("#" + id);
                assertEquals(TableView.UNCONSTRAINED_RESIZE_POLICY, table.getColumnResizePolicy());
            }
            return null;
        });
    }

    private void seed()
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = em.createQuery(
                    "from Company c where c.code = 'DEFAULT'", Company.class).getSingleResult();
            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName("Opening UI chart");
            chart.setVersion("1");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            company.setActiveChartOfAccounts(chart);
            Account bank = account(em, chart, "1000", AccountType.ASSET, NormalBalance.DEBIT, "100");
            bank.setAccountFunction(AccountFunction.BANK);
            account(em, chart, "3000", AccountType.EQUITY, NormalBalance.CREDIT, "100");
            CompanyBankAccount configured = new CompanyBankAccount();
            configured.setCompany(company);
            configured.setAccount(bank);
            configured.setName("Operating");
            configured.setOpeningDate(CUTOFF.minusDays(30));
            configured.setOpeningBalance(new BigDecimal("100"));
            em.persist(configured);
            em.getTransaction().commit();
        }
    }

    private static Account account(EntityManager em, ChartOfAccounts chart, String code,
            AccountType type, NormalBalance normal, String opening)
    {
        Account account = new Account();
        account.setChart(chart);
        account.setCode(code);
        account.setName(code);
        account.setAccountType(type);
        account.setNormalBalance(normal);
        account.setOpeningBalance(new BigDecimal(opening));
        account.setPosting(true);
        account.setActive(true);
        em.persist(account);
        return account;
    }

    private long auditCount()
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(e) from AuditEvent e where e.actionType = 'OPENING_CONVERSION_ACCEPTED'", Long.class)
                    .getSingleResult();
        }
    }
}
