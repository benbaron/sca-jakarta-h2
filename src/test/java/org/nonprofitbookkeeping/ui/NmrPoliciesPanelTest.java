package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;
import org.nonprofitbookkeeping.service.NmrPolicyView;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class NmrPoliciesPanelTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private Stage stage;
    private NmrPoliciesPanel panel;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        var session = MainWindow.sharedSessionState();
        previousDatabase = session.databaseSelection();
        previousCompany = session.multiCompany();
        previousUser = session.authenticatedUser();
        Path database = directory.resolve("nmr-ui");
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);
        UiServiceRegistry.authentication().initializeSecurityIfUnambiguous();
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            panel = new NmrPoliciesPanel(() -> false);
            stage = new Stage();
            stage.setScene(new Scene((VBox) panel.root(), 900, 740));
            stage.show();
            return null;
        });
    }

    @AfterEach
    void cleanup() throws Exception
    {
        if (previousDatabase == null)
        {
            return;
        }
        FxTestSupport.onFx(() ->
        {
            if (stage != null)
            {
                stage.close();
            }
            return null;
        });
        Thread.sleep(500);
        FxTestSupport.onFx(() ->
        {
            var session = MainWindow.sharedSessionState();
            session.setDatabaseSelection(previousDatabase);
            session.setMultiCompany(previousCompany);
            previousUser.ifPresentOrElse(session::setAuthenticatedUser, session::clearAuthenticatedUser);
            return null;
        });
        UiServiceRegistry.reconnectToDatabase(Path.of(previousDatabase.activeDatabasePath()));
    }

    static void fill(javafx.scene.Node root, String category, String rate)
    {
        for (String key : List.of("Code", "Category", "Currency", "DateBasis", "RoundingRule", "Eligibility", "Exemptions",
                "Recipient", "Reporting", "MerchantPath", "SourceReference", "Authority"))
        {
            ((TextInputControl) root.lookup("#nmrPolicy" + key)).setText("Explicit test " + key);
        }
        ((TextInputControl) root.lookup("#nmrPolicyCode")).setText("TEST-POLICY");
        ((TextInputControl) root.lookup("#nmrPolicyCategory")).setText(category);
        ((TextInputControl) root.lookup("#nmrPolicyCurrency")).setText("USD");
        ((DatePicker) root.lookup("#nmrPolicyFrom")).setValue(LocalDate.of(2026, 1, 1));
        ((DatePicker) root.lookup("#nmrPolicySourceDate")).setValue(LocalDate.of(2025, 12, 31));
        ((TextField) root.lookup("#nmrPolicyRate")).setText(rate);
    }

    @Test
    void blankInitialStateValidationDirtyCancellationLivePermissionsAndReadOnlyHistory()
    {
        FxTestSupport.onFx(() ->
        {
            assertTrue(UiServiceRegistry.nmrPolicies().list().isEmpty());
            assertEquals("", text("Rate").getText());
            assertEquals("", text("Currency").getText());
            assertNull(((DatePicker) panel.root().lookup("#nmrPolicyFrom")).getValue());
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            button("New").fire();
            fill(panel.root(), "STANDARD", "");
            button("Save").fire();
            assertTrue(panel.commandResultMessage(AppCommand.SAVE_ACTIVE).contains("explicit nonnegative rate"));
            assertTrue(UiServiceRegistry.nmrPolicies().list().isEmpty());
            text("Rate").setText("15.1250");
            assertTrue(panel.hasUnsavedChanges());
            button("Refresh").fire();
            button("New").fire();
            assertEquals("15.1250", text("Rate").getText());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(button("Save").isDisabled());
            assertTrue(button("New").isDisabled());
            assertTrue(text("Rate").isDisabled());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.manager());
            assertFalse(button("Save").isDisabled());
            button("Save").fire();
            assertEquals(1, UiServiceRegistry.nmrPolicies().list().size(), panel.commandResultMessage(AppCommand.SAVE_ACTIVE));
            assertEquals(new BigDecimal("15.1250"), UiServiceRegistry.nmrPolicies().list().get(0).terms().rate());
            assertFalse(panel.hasUnsavedChanges());
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            assertTrue(text("Rate").isDisabled());
            assertTrue(((Label) panel.root().lookup("#nmrPolicyRetention")).getText().contains("cannot be edited or deleted"));
            button("New").fire();
            text("Rate").setText("99");
            ((TableView<?>) panel.root().lookup("#nmrPolicyTable")).getSelectionModel().selectFirst();
            assertEquals("99", text("Rate").getText(), "Declined history selection preserves draft");
            assertTrue(panel.hasUnsavedChanges());
            return null;
        });
    }

    @Test
    void focusNormalizationPreservesPrecisionAndEndDialogUsesRealSave()
    {
        FxTestSupport.onFx(() ->
        {
            button("New").fire();
            fill(panel.root(), "STANDARD", "15.1250");
            text("Rate").requestFocus();
            return null;
        });
        FxTestSupport.onFx(() ->
        {
            text("Currency").requestFocus();
            return null;
        });
        FxTestSupport.onFx(() ->
        {
            assertTrue(text("Rate").getText().contains("15.13"));
            button("Save").fire();
            var saved = UiServiceRegistry.nmrPolicies().list().get(0);
            assertEquals(new BigDecimal("15.1250"), saved.terms().rate());
            Platform.runLater(() -> Window.getWindows().stream().filter(Window::isShowing)
                    .filter(window -> window != stage).toList().forEach(window ->
                    {
                        DialogPane dialog = (DialogPane) window.getScene().getRoot();
                        ((DatePicker) dialog.lookup("#nmrPolicyEndDate")).setValue(LocalDate.of(2026, 1, 31));
                        ButtonType saveEnd = dialog.getButtonTypes().stream().filter(type -> type.getText().equals("Save End Date")).findFirst().orElseThrow();
                        ((Button) dialog.lookupButton(saveEnd)).fire();
                        assertTrue(window.isShowing(), "An invalid Save must preserve the dialog draft.");
                        assertTrue(((Label) dialog.lookup("#nmrPolicyEndError")).getText().contains("End reason"));
                        ((TextField) dialog.lookup("#nmrPolicyEndReason")).setText("Test source superseded");
                        ((Button) dialog.lookupButton(saveEnd)).fire();
                    }));
            button("End").fire();
            assertEquals(LocalDate.of(2026, 1, 31), UiServiceRegistry.nmrPolicies().list().get(0).terms().validTo(), panel.commandResultMessage(AppCommand.SAVE_ACTIVE));
            assertEquals(saved.terms().sourceReference(), UiServiceRegistry.nmrPolicies().list().get(0).terms().sourceReference());
            return null;
        });
    }

    @Test
    void scaledGeometryExposesBothAxesIndependentDividersAndCompanyState() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            button("New").fire();
            fill(panel.root(), "STANDARD", "1");
            button("Save").fire();
            stage.setWidth(520);
            stage.setHeight(550);
            panel.root().setStyle("-fx-font-size: 17px;");
            ((SplitPane) panel.root().lookup("#nmrPolicyMainSplit")).setDividerPositions(.25);
            ((SplitPane) panel.root().lookup("#nmrPolicyDetailSplit")).setDividerPositions(.80);
            panel.root().applyCss();
            ((VBox) panel.root()).layout();
            return null;
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean overflow = false;
        while (System.nanoTime() < deadline)
        {
            overflow = FxTestSupport.onFx(() ->
            {
                ScrollPane editor = (ScrollPane) panel.root().lookup("#nmrPolicyEditorScroll");
                return editor.getContent().getLayoutBounds().getWidth() > editor.getViewportBounds().getWidth()
                        && editor.getContent().getLayoutBounds().getHeight() > editor.getViewportBounds().getHeight()
                        && editor.lookupAll(".scroll-bar").stream().filter(node -> node instanceof ScrollBar bar && bar.isVisible()).count() == 2;
            });
            if (overflow)
            {
                break;
            }
            Thread.sleep(30);
        }
        assertTrue(overflow, "Editor must provide real horizontal and vertical overflow at increased font size");
        FxTestSupport.onFx(() ->
        {
            TableView<?> table = (TableView<?>) panel.root().lookup("#nmrPolicyTable");
            assertTrue(CompanyTableStateBinder.isCompanyStateOwned(table));
            CompanyTableStateBinder.saveNow(table);
            assertFalse(UiServiceRegistry.companyUiPreferences().loadState("DEFAULT", "ui.table.settings.").isEmpty());
            assertTrue(table.lookupAll(".scroll-bar").stream().anyMatch(node -> node instanceof ScrollBar bar
                    && bar.isVisible() && bar.getOrientation() == Orientation.HORIZONTAL));
            assertEquals(2, ((SplitPane) panel.root().lookup("#nmrPolicyDetailSplit")).getItems().size());
            return null;
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void typedRateSortingAndColumnStateRestoreForActiveCompany()
    {
        FxTestSupport.onFx(() ->
        {
            button("New").fire();
            fill(panel.root(), "FIRST", "9");
            button("Save").fire();
            button("New").fire();
            fill(panel.root(), "SECOND", "100");
            button("Save").fire();
            TableView<NmrPolicyView> table = (TableView<NmrPolicyView>) panel.root().lookup("#nmrPolicyTable");
            TableColumn<NmrPolicyView, BigDecimal> rateColumn = (TableColumn<NmrPolicyView, BigDecimal>) table.getColumns().get(4);
            rateColumn.setSortType(TableColumn.SortType.ASCENDING);
            table.getSortOrder().setAll(rateColumn);
            assertEquals(new BigDecimal("9.0000"), table.getItems().get(0).terms().rate());
            assertInstanceOf(LocalDate.class, table.getColumns().get(2).getCellData(0));
            rateColumn.setPrefWidth(234);
            CompanyTableStateBinder.saveNow(table);
            NmrPoliciesPanel reopened = new NmrPoliciesPanel(() -> false);
            stage.getScene().setRoot((VBox) reopened.root());
            reopened.root().applyCss();
            ((VBox) reopened.root()).layout();
            TableView<?> restored = (TableView<?>) reopened.root().lookup("#nmrPolicyTable");
            assertEquals(234, restored.getColumns().get(4).getPrefWidth(), .01);
            assertEquals("nmrPolicyColumnrate", restored.getSortOrder().get(0).getId());
            return null;
        });
    }

    private Button button(String key)
    {
        return (Button) panel.root().lookup("#nmrPolicy" + key);
    }

    private TextInputControl text(String key)
    {
        return (TextInputControl) panel.root().lookup("#nmrPolicy" + key);
    }
}
