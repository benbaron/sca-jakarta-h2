package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;
import org.nonprofitbookkeeping.service.FundTransferTestFixture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class CoaJsonReviewPaneTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private Stage stage;
    private ChartOfAccountsInterchangePanel panel;
    private ChartOfAccountsPanel maintenance;
    private Path file;
    private Path database;

    @BeforeEach
    void setup() throws Exception
    {
        FxTestSupport.initToolkitOrSkip();
        var session = MainWindow.sharedSessionState();
        previousDatabase = session.databaseSelection();
        previousCompany = session.multiCompany();
        previousUser = session.authenticatedUser();
        database = directory.resolve("chart-review-ui");
        try (Jpa jpa = new Jpa(database))
        {
            FundTransferTestFixture.seed(jpa);
        }
        file = Files.writeString(directory.resolve("chart.json"), """
                {"format":"SCA-COA","version":"1.0","chart":{"name":"UI review fixture","chartVersion":"1","status":"DRAFT","currency":"USD"},
                 "accounts":[
                  {"code":"9000","name":"Parent assets","type":"ASSET","normalBalance":"DEBIT","posting":false},
                  {"code":"9010","name":"Checking proposal","type":"BANK","subtype":"CASH","normalBalance":"DEBIT","parentCode":"9000"},
                  {"code":"9300","name":"Net assets proposal","type":"EQUITY","subtype":"INVENTORY","normalBalance":"CREDIT"}]}
                """);
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            maintenance = new ChartOfAccountsPanel();
            panel = new ChartOfAccountsInterchangePanel(maintenance, (chooser, owner) -> file.toFile());
            PanelHost host = new PanelHost();
            stage = new Stage();
            stage.setScene(new Scene(host, 1050, 780));
            stage.getScene().getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
            host.getStyleClass().addAll("production-workspace", "theme-modena");
            host.setStyle("-fx-font-family: 'DejaVu Sans'; -fx-font-size: 12px;");
            WorkspaceTextContrastEnforcer.install(host);
            stage.show();
            host.showReplacement(AppPanelId.CHART_OF_ACCOUNTS, panel);
            host.applyCss();
            host.layout();
            button("coaImportJsonButton").fire();
            host.applyCss();
            host.layout();
            button("coaReviewPreview").fire();
            return null;
        });
        await(() -> proposals() != null && proposals().getItems().size() == 3 && !button("coaReviewRefresh").isDisabled());
    }

    @AfterEach
    void cleanup()
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
            var session = MainWindow.sharedSessionState();
            session.setDatabaseSelection(previousDatabase);
            session.setMultiCompany(previousCompany);
            previousUser.ifPresentOrElse(session::setAuthenticatedUser, session::clearAuthenticatedUser);
            return null;
        });
        UiServiceRegistry.reconnectToDatabase(Path.of(previousDatabase.activeDatabasePath()));
    }

    @Test
    void reportedConflictCanBeCorrectedAppliedAcknowledgedAndImportedInTheProductionWorkspace() throws Exception
    {
        String original = Files.readString(file);
        long before = accountCount();
        FxTestSupport.onFx(() ->
        {
            assertEquals(1, Window.getWindows().stream().filter(Window::isShowing).count(), "The review is inline");
            assertTrue(button("coaReviewCommit").isDisabled());
            assertTrue(proposalRow(2).getStyleClass().contains("import-entry-invalid"));
            ThemeTextContrastFxTest.settle(stage.getScene().getRoot());
            Labeled invalidCell = proposalRow(2).lookupAll(".table-cell").stream().filter(Labeled.class::isInstance)
                    .map(Labeled.class::cast).filter(cell -> "9300".equals(cell.getText())).findFirst().orElseThrow();
            assertNotEquals(javafx.scene.paint.Color.BLACK, invalidCell.getTextFill());
            assertNotEquals(javafx.scene.paint.Color.WHITE, invalidCell.getTextFill());
            assertEquals(invalidCell.getTextFill(), ((javafx.scene.text.Text) invalidCell.lookup(".text")).getFill());
            SurfaceContrastAssertions.assertReadable(invalidCell);
            edit(2);
            assertTrue(((Label) panel.root().lookup("#coaReviewEditorValidation")).getText().contains("INVENTORY"));
            ThemeTextContrastFxTest.settle(stage.getScene().getRoot());
            assertTrue(combo("coaReviewSubtype").getButtonCell().getStyleClass().contains("import-source-suggestion"));
            assertNotEquals(javafx.scene.paint.Color.BLACK, combo("coaReviewSubtype").getButtonCell().getTextFill());
            button("coaReviewClearSubtype").fire();
            assertEquals("Apply Account Changes", panel.commandLabel(AppCommand.SAVE_ACTIVE));
            assertTrue(panel.commandCapabilities().contains(AppCommand.SAVE_ACTIVE));
            panel.executeCommand(AppCommand.SAVE_ACTIVE);
            return null;
        });
        await(() -> status().startsWith("Account changes applied"));
        assertEquals(before, accountCount(), "Apply only changes the review");
        FxTestSupport.onFx(() ->
        {
            assertTrue(proposalRow(2).getStyleClass().contains("import-entry-updated"));
            assertTrue(proposalRow(2).lookupAll(".table-cell").stream().filter(Labeled.class::isInstance)
                    .map(Labeled.class::cast).anyMatch(cell -> cell.getFont().getStyle().toLowerCase().contains("bold")));
            assertTrue(button("coaReviewCommit").isDisabled());
            check("coaReviewAcknowledged").setSelected(true);
            assertFalse(button("coaReviewCommit").isDisabled());
            panel.executeCommand(AppCommand.SAVE_ACTIVE);
            assertFalse(panel.commandCapabilities().contains(AppCommand.SAVE_ACTIVE), "Save cannot run twice while busy");
            return null;
        });
        await(() -> status().startsWith("Imported reviewed accounts"));
        assertEquals(before + 3, accountCount());
        assertEquals(original, Files.readString(file));
        FxTestSupport.onFx(() ->
        {
            assertTrue(button("coaReviewCommit").isDisabled());
            assertFalse(panel.hasUnsavedChanges());
            return null;
        });
    }

    @Test
    void refreshAndNavigationRetainUnappliedRawFieldsAndCancelRestoresTheAppliedProposal() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            edit(2);
            field("coaReviewName").setText("Unapplied literal name");
            field("coaReviewOpeningBalance").setText("12.345");
            button("coaReviewApply").fire();
            assertEquals("12.345", field("coaReviewOpeningBalance").getText());
            assertTrue(button("coaReviewCommit").isDisabled());
            button("coaReviewRefresh").fire();
            return null;
        });
        await(() -> status().startsWith("Review refreshed"));
        FxTestSupport.onFx(() ->
        {
            assertEquals("Unapplied literal name", field("coaReviewName").getText());
            assertEquals("12.345", field("coaReviewOpeningBalance").getText());
            button("coaReviewBack").fire();
            assertNotNull(panel.root().lookup("#chartOfAccountsSelection"));
            button("coaImportJsonButton").fire();
            assertEquals("Unapplied literal name", field("coaReviewName").getText());
            button("coaReviewCancel").fire();
            edit(2);
            assertEquals("Net assets proposal", field("coaReviewName").getText());
            assertEquals(AccountSubtype.INVENTORY, combo("coaReviewSubtype").getValue());
            return null;
        });
    }

    @Test
    void multiDeleteOnlyRemovesProposalsAndRefreshClearsTheAcceptedUpdateMarker() throws Exception
    {
        long before = accountCount();
        FxTestSupport.onFx(() ->
        {
            proposals().getSelectionModel().selectIndices(0, 2);
            button("coaReviewDelete").fire();
            return null;
        });
        await(() -> status().startsWith("Removed 2"));
        FxTestSupport.onFx(() ->
        {
            assertEquals(1, proposals().getItems().size());
            assertTrue(button("coaReviewCommit").isDisabled(), "Removing the parent leaves a missing-parent error");
            edit(0);
            field("coaReviewParent").clear();
            button("coaReviewApply").fire();
            return null;
        });
        await(() -> status().startsWith("Account changes applied"));
        FxTestSupport.onFx(() ->
        {
            assertTrue(proposalRow(0).getStyleClass().contains("import-entry-updated"));
            button("coaReviewRefresh").fire();
            return null;
        });
        await(() -> status().startsWith("Review refreshed"));
        FxTestSupport.onFx(() ->
        {
            assertFalse(proposalRow(0).getStyleClass().contains("import-entry-updated"));
            assertFalse(proposalRow(0).getStyleClass().contains("import-entry-invalid"));
            return null;
        });
        assertEquals(before, accountCount());
    }

    @Test
    void changedSourceFailsInlineAndPermissionsAndChangedOptionsPreventCommit() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            edit(2);
            combo("coaReviewSubtype").setValue(null);
            button("coaReviewApply").fire();
            return null;
        });
        await(() -> status().startsWith("Account changes applied"));
        FxTestSupport.onFx(() ->
        {
            check("coaReviewAcknowledged").setSelected(true);
            assertFalse(button("coaReviewCommit").isDisabled());
            MainWindow.sharedSessionState().setMultiCompany(new MultiCompanyState("OTHER", List.of("DEFAULT", "OTHER")));
            assertTrue(button("coaReviewCommit").isDisabled());
            assertFalse(panel.commandCapabilities().contains(AppCommand.SAVE_ACTIVE));
            MainWindow.sharedSessionState().setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
            assertTrue(button("coaReviewCommit").isDisabled(), "Returning to the original company still requires refresh");
            pages().getSelectionModel().select(0);
            assertFalse(panel.commandCapabilities().contains(AppCommand.SAVE_ACTIVE));
            field("coaJsonTargetChartName").setText("Changed options");
            pages().getSelectionModel().select(1);
            check("coaReviewAcknowledged").setSelected(true);
            assertTrue(button("coaReviewCommit").isDisabled());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(button("coaReviewApply").isDisabled());
            assertTrue(button("coaReviewPreview").isDisabled());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.manager());
            return null;
        });
        long before = accountCount();
        Files.writeString(file, Files.readString(file).replace("Checking proposal", "Changed at source"));
        FxTestSupport.onFx(() -> { button("coaReviewRefresh").fire(); return null; });
        await(() -> status().contains("changed after preview"));
        FxTestSupport.onFx(() ->
        {
            assertEquals(3, proposals().getItems().size());
            check("coaReviewAcknowledged").setSelected(true);
            assertTrue(button("coaReviewCommit").isDisabled());
            assertEquals(1, Window.getWindows().stream().filter(Window::isShowing).count());
            return null;
        });
        assertEquals(before, accountCount());
    }

    @Test
    void tablesEditorAndEvidenceRemainScrollableAtNarrowWidthsAndDividerMoves() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            assertHorizontalBar(proposals());
            pages().getSelectionModel().select(3);
            return null;
        });
        await(() -> panel.root().lookup("#coaJsonValidationMessages") != null);
        FxTestSupport.onFx(() ->
        {
            SplitPane split = (SplitPane) panel.root().lookup("#coaReviewSplit");
            double old = split.getDividerPositions()[0];
            split.setDividerPositions(.38);
            stage.getScene().getRoot().layout();
            assertTrue(split.getDividerPositions()[0] > old + .05);
            assertHorizontalBar((TableView<?>) panel.root().lookup("#coaJsonValidationMessages"));
            ((TabPane) panel.root().lookup("#coaReviewEvidencePages")).getSelectionModel().select(1);
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            assertTrue(((TextArea) panel.root().lookup("#coaReviewOriginalSource")).getText().contains("INVENTORY"));
            pages().getSelectionModel().select(1);
            edit(2);
            stage.setWidth(550);
            stage.setHeight(590);
            stage.getScene().getRoot().setStyle("-fx-font-size: 18px;");
            return null;
        });
        await(() -> stage.getWidth() <= 550 && panel.root().lookup("#coaReviewApply").getBoundsInLocal().getWidth() > 0);
        FxTestSupport.onFx(() ->
        {
            javafx.scene.Node cursor = button("coaReviewApply");
            while (!(cursor instanceof ScrollPane)) cursor = cursor.getParent();
            ScrollPane form = (ScrollPane) cursor;
            var bars = form.lookupAll(".scroll-bar").stream().filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast).filter(ScrollBar::isVisible).toList();
            assertTrue(bars.stream().anyMatch(b -> b.getOrientation() == Orientation.HORIZONTAL && b.getMax() > b.getMin()));
            assertTrue(bars.stream().anyMatch(b -> b.getOrientation() == Orientation.VERTICAL && b.getMax() > b.getMin()));
            assertTrue(form.getContent().getBoundsInLocal().getWidth() > form.getViewportBounds().getWidth());
            assertTrue(form.getContent().getBoundsInLocal().getHeight() > form.getViewportBounds().getHeight());
            form.setHvalue(1);
            form.setVvalue(1);
            stage.getScene().getRoot().layout();
            assertEquals(1, form.getHvalue());
            assertEquals(1, form.getVvalue());
            form.setHvalue(0);
            form.setVvalue(0);
            String screenshot = System.getProperty("coa.review.screenshot");
            if (screenshot != null)
            {
                var image = stage.getScene().snapshot(null);
                var buffered = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < image.getHeight(); y++)
                {
                    for (int x = 0; x < image.getWidth(); x++)
                    {
                        buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                    }
                }
                javax.imageio.ImageIO.write(buffered, "png", Path.of(screenshot).toFile());
            }
            return null;
        });
    }

    @Test
    void maintenanceDraftAndJsonDraftBothSurviveTheImportAddRoute() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            edit(2);
            field("coaReviewName").setText("Pending JSON proposal name");
            button("coaReviewBack").fire();
            return null;
        });
        await(() -> !((ComboBox<?>) panel.root().lookup("#chartOfAccountsSelection")).isDisabled());
        FxTestSupport.onFx(() ->
        {
            panel.onImportAddSuggestion(ImportAddSuggestion.capture("Literal maintenance name"));
            return null;
        });
        await(() -> "Literal maintenance name".equals(maintenance.readFormStateForTests().name()));
        FxTestSupport.onFx(() ->
        {
            assertEquals("Literal maintenance name", maintenance.readFormStateForTests().name());
            assertEquals("", maintenance.readFormStateForTests().code());
            assertTrue(maintenance.hasUnsavedChanges());
            button("coaImportJsonButton").fire();
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            assertEquals("Pending JSON proposal name", field("coaReviewName").getText());
            button("coaReviewBack").fire();
            assertEquals("Literal maintenance name", maintenance.readFormStateForTests().name());
            return null;
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void showAccountUsesSourceIdentityAfterTheProposalTableIsSorted()
    {
        FxTestSupport.onFx(() ->
        {
            TableView<Object> table = (TableView<Object>) proposals();
            TableColumn<Object, ?> name = table.getColumns().get(2);
            name.setSortType(TableColumn.SortType.DESCENDING);
            table.getSortOrder().add(name);
            table.sort();
            pages().getSelectionModel().select(3);
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            TableView<org.nonprofitbookkeeping.interchange.InterchangeValidationMessage> diagnostics =
                    (TableView<org.nonprofitbookkeeping.interchange.InterchangeValidationMessage>) panel.root().lookup("#coaJsonValidationMessages");
            var error = diagnostics.getItems().stream().filter(message -> message.code().equals("COA_SUBTYPE_INCOMPATIBLE")).findFirst().orElseThrow();
            diagnostics.getSelectionModel().select(error);
            button("coaReviewShowDiagnosticAccount").fire();
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            assertEquals("9300", field("coaReviewCode").getText());
            assertEquals("Net assets proposal", field("coaReviewName").getText());
            return null;
        });
    }

    private void edit(int row)
    {
        proposals().getSelectionModel().clearAndSelect(row);
        button("coaReviewEdit").fire();
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    private void await(BooleanSupplier condition) throws Exception
    {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
        while (!FxTestSupport.onFx(() ->
        {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            return condition.getAsBoolean();
        }))
        {
            assertTrue(System.nanoTime() < until, "Chart review did not finish: " + FxTestSupport.onFx(this::status));
            Thread.sleep(25);
        }
    }

    private long accountCount()
    {
        try (Jpa jpa = new Jpa(database); var em = jpa.em())
        {
            return em.createQuery("select count(a) from Account a", Long.class).getSingleResult();
        }
    }

    private String status()
    {
        return panel.root().lookup("#coaReviewStatus") instanceof Label label ? label.getText() : "Account maintenance";
    }
    private Button button(String id) { return (Button) panel.root().lookup("#" + id); }
    private TextField field(String id) { return (TextField) panel.root().lookup("#" + id); }
    private CheckBox check(String id) { return (CheckBox) panel.root().lookup("#" + id); }
    private TabPane pages() { return (TabPane) panel.root().lookup("#coaReviewPages"); }
    private TableView<?> proposals() { return (TableView<?>) panel.root().lookup("#coaJsonPreviewTable"); }
    @SuppressWarnings("unchecked")
    private ComboBox<Object> combo(String id) { return (ComboBox<Object>) panel.root().lookup("#" + id); }

    private TableRow<?> proposalRow(int index)
    {
        return proposals().lookupAll(".table-row-cell").stream().filter(TableRow.class::isInstance).map(TableRow.class::cast)
                .filter(row -> !row.isEmpty() && row.getIndex() == index).findFirst().orElseThrow();
    }

    private static void assertHorizontalBar(TableView<?> table)
    {
        assertTrue(table.lookupAll(".scroll-bar").stream().filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast)
                .anyMatch(bar -> bar.getOrientation() == Orientation.HORIZONTAL && bar.isVisible() && bar.getMax() > bar.getMin()));
    }
}
