package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.interchange.exchequer.ExchequerCsvFixture;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class ExchequerImportReviewPaneTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private FundTransferTestFixture fixture;
    private ImportPreviewPanel panel;
    private Stage stage;
    private Path csvFile;

    @BeforeEach
    void setup() throws Exception
    {
        FxTestSupport.initToolkitOrSkip();
        var session = MainWindow.sharedSessionState();
        previousDatabase = session.databaseSelection();
        previousCompany = session.multiCompany();
        previousUser = session.authenticatedUser();
        Path db = directory.resolve("review-ui");
        Path csv = ExchequerCsvFixture.write(directory.resolve("synthetic.csv"), "12/31/2025", "1/1/2027");
        csvFile = csv;
        try (Jpa jpa = new Jpa(db))
        {
            fixture = FundTransferTestFixture.seed(jpa);
        }
        session.setDatabaseSelection(new DatabaseSelectionState(db.toString(), List.of(db.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(db);
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            panel = new ImportPreviewPanel((chooser, owner) -> csv.toFile());
            PanelViewport.prepare(panel.root(), "panel-IMPORT_PREVIEW");
            stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent) panel.root(), 950, 750));
            stage.show();
            button("previewExchequerCsvButton").fire();
            return null;
        });
        await(() -> table("exchequerProposedEntries") != null && table("exchequerProposedEntries").getItems().size() == 2);
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
    void realImportRoutePreservesEditsDeletesProposalsAndCommitsThroughJournal() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            assertTrue(panel.hasUnsavedChanges());
            button("backFromExchequerReview").fire();
            assertNotNull(button("previewExchequerCsvButton"));
            button("previewExchequerCsvButton").fire();
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            assertTrue(panel.hasUnsavedChanges());
            assertTrue(button("commitExchequerEntries").isDisabled());
            assertEquals(1, UiServiceRegistry.transactionEntry().search(null, null, "", 100).size());
            TableView<?> proposals = table("exchequerProposedEntries");
            proposals.getSelectionModel().select(0);
            button("editExchequerEntry").fire();
            ((TextField) panel.root().lookup("#exchequerEntryDate")).setText("12/30/2025");
            ((TextArea) panel.root().lookup("#exchequerEntryMemo")).setText("Edited reviewed entry");
            choose("exchequerLineAccount", fixture.bank());
            choose("exchequerLineFund", fixture.source());
            ((TextField) panel.root().lookup("#exchequerLineDebit")).setText("12.34");
            table("exchequerProposedLines").getSelectionModel().select(1);
            choose("exchequerLineAccount", fixture.income());
            choose("exchequerLineFund", fixture.source());
            ((TextField) panel.root().lookup("#exchequerLineCredit")).setText("12.34");
            ((TabPane) panel.root().lookup("#exchequerReviewPages")).getSelectionModel().select(0);
            proposals.getSelectionModel().select(1);
            button("deleteExchequerEntry").fire();
            assertEquals(1, proposals.getItems().size());
            proposals.getSelectionModel().select(0);
            button("editExchequerEntry").fire();
            assertEquals("Edited reviewed entry", ((TextArea) panel.root().lookup("#exchequerEntryMemo")).getText());
            CheckBox reviewed = (CheckBox) panel.root().lookup("#confirmExchequerReview");
            reviewed.setSelected(true);
            assertFalse(button("commitExchequerEntries").isDisabled());
            // An edit invalidates the explicit review acknowledgement.
            ((TextArea) panel.root().lookup("#exchequerEntryMemo")).setText("Final reviewed entry");
            assertFalse(reviewed.isSelected());
            assertTrue(button("commitExchequerEntries").isDisabled());
            button("addExchequerLine").fire();
            assertEquals(3, table("exchequerProposedLines").getItems().size());
            assertTrue(button("commitExchequerEntries").isDisabled());
            button("removeExchequerLine").fire();
            assertEquals(2, table("exchequerProposedLines").getItems().size());
            reviewed.setSelected(true);
            assertTrue(panel.commandCapabilities().contains(AppCommand.SAVE_ACTIVE));
            Platform.runLater(() -> javafx.stage.Window.getWindows().stream().filter(window -> window != stage)
                    .filter(window -> window.getScene() != null && window.getScene().getRoot() instanceof DialogPane)
                    .findFirst().ifPresent(window -> ((Button) ((DialogPane) window.getScene().getRoot()).lookupButton(ButtonType.OK)).fire()));
            panel.onSave();
            assertEquals("Saving reviewed entries…", panel.commandResultMessage(AppCommand.SAVE_ACTIVE));
            return null;
        });
        await(() -> ((Label) panel.root().lookup("#exchequerReviewStatus")).getText().startsWith("Saved 1 entries"));
        FxTestSupport.onFx(() ->
        {
            assertFalse(panel.hasUnsavedChanges());
            assertTrue(button("commitExchequerEntries").isDisabled());
            var saved = UiServiceRegistry.transactionEntry().search(null, null, "Final reviewed entry", 100).get(0);
            assertEquals(java.time.LocalDate.of(2025, 12, 30), saved.date());
            assertEquals(new BigDecimal("12.3400"), UiServiceRegistry.transactionEntry().load(saved.id()).debitTotal());
            return null;
        });
    }

    @Test
    void allFourSplitsAndEditableInformationReachTheProposedAndSavedJournalEntry() throws Exception
    {
        var records = ExchequerCsvFixture.header();
        var row = ExchequerCsvFixture.row(1, "2/1/2026", "20");
        row.set(4, "2/6/2026");
        row.set(5, "000042");
        row.set(6, "1/30/2026");
        ExchequerCsvFixture.split(row, 0, "20", "Income A", "");
        ExchequerCsvFixture.split(row, 1, "30", "Income B", "");
        ExchequerCsvFixture.split(row, 2, "25", "", "Expense C");
        ExchequerCsvFixture.split(row, 3, "5", "", "Expense D");
        records.add(row);
        java.nio.file.Files.writeString(csvFile, ExchequerCsvFixture.csv(records));
        FxTestSupport.onFx(() ->
        {
            Platform.runLater(ExchequerImportReviewPaneTest::acceptConfirmation);
            panel.root().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getText().startsWith("Choose Another Exchequer")).findFirst().orElseThrow().fire();
            return null;
        });
        await(() -> table("exchequerProposedEntries").getItems().size() == 1);
        FxTestSupport.onFx(() ->
        {
            var proposals = table("exchequerProposedEntries");
            for (int split = 1; split <= 4; split++)
            {
                final int ordinal = split;
                var column = proposals.getColumns().stream().filter(value -> value.getText().equals("Split " + ordinal)).findFirst().orElseThrow();
                assertFalse(column.getCellData(0).toString().isBlank());
            }
            button("editExchequerEntry").fire();
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            assertEquals("000042", ((TextField) panel.root().lookup("#exchequerTransferCheckReference")).getText());
            var lines = table("exchequerProposedLines");
            assertEquals(5, lines.getItems().size());
            for (int index = 0; index < 5; index++)
            {
                lines.getSelectionModel().select(index);
                choose("exchequerLineAccount", index == 0 ? fixture.bank() : index <= 2 ? fixture.income() : fixture.expense());
                choose("exchequerLineFund", fixture.source());
            }
            ((TextField) panel.root().lookup("#exchequerTransferCheckReference")).setText("000043");
            ((TextField) panel.root().lookup("#exchequerInformationStatementDate")).setText("2/7/2026");
            ((TextField) panel.root().lookup("#exchequerInformationIncomingDate")).setText("1/29/2026");
            ((TabPane) panel.root().lookup("#exchequerReviewPages")).getSelectionModel().select(0);
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            button("showExchequerSplits").fire();
            stage.getScene().getRoot().layout();
            String screenshot = System.getProperty("sca.exchequerFourSplitScreenshot");
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
            ((CheckBox) panel.root().lookup("#confirmExchequerReview")).setSelected(true);
            assertFalse(button("commitExchequerEntries").isDisabled());
            Platform.runLater(ExchequerImportReviewPaneTest::acceptConfirmation);
            panel.onSave();
            return null;
        });
        await(() -> ((Label) panel.root().lookup("#exchequerReviewStatus")).getText().startsWith("Saved 1 entries"));
        var saved = UiServiceRegistry.transactionEntry().search(null, null, "000043", 100).get(0);
        assertEquals(5, saved.lines().size());
        assertEquals(java.time.LocalDate.of(2026, 2, 7), saved.information().statementDate());
        assertEquals(java.time.LocalDate.of(2026, 1, 29), saved.information().incomingDate());
        assertTrue(saved.lines().stream().noneMatch(line -> line.bankCleared() || line.bankClearedOn() != null));
    }

    private static void acceptConfirmation()
    {
        javafx.stage.Window.getWindows().stream()
                .filter(window -> window.getScene() != null && window.getScene().getRoot() instanceof DialogPane)
                .findFirst().ifPresent(window -> ((Button) ((DialogPane) window.getScene().getRoot()).lookupButton(ButtonType.OK)).fire());
    }

    @Test
    void reviewTablesAndEditorHaveLocalBothAxisOverflowAndDraggableDividers() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            TableView<?> table = table("exchequerProposedEntries");
            assertEquals(TableView.UNCONSTRAINED_RESIZE_POLICY, table.getColumnResizePolicy());
            assertTrue(table.lookupAll(".scroll-bar").stream().filter(ScrollBar.class::isInstance)
                    .map(ScrollBar.class::cast).anyMatch(bar -> bar.getOrientation() == Orientation.HORIZONTAL && bar.isVisible()));
            button("editExchequerEntry").fire();
            stage.setWidth(520);
            return null;
        });
        await(() -> stage.getScene().getWidth() <= 520);
        FxTestSupport.onFx(() ->
        {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            SplitPane split = (SplitPane) panel.root().lookup("#exchequerEntryEditorSplit");
            double before = split.getDividerPositions()[0];
            split.setDividerPositions(.35);
            stage.getScene().getRoot().layout();
            assertNotEquals(before, split.getDividerPositions()[0], .03);
            ScrollPane form = (ScrollPane) split.getItems().get(0);
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, form.getHbarPolicy());
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, form.getVbarPolicy());
            assertTrue(form.getContent().getBoundsInLocal().getWidth() > form.getViewportBounds().getWidth(),
                    "Editor width " + form.getContent().getBoundsInLocal().getWidth() + " viewport " + form.getViewportBounds().getWidth());
            assertTrue(table("exchequerProposedLines").getColumns().stream().allMatch(column -> column.isResizable() && column.isSortable() && column.isReorderable()));
            String screenshot = System.getProperty("sca.exchequerScreenshot");
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

    private void await(java.util.function.BooleanSupplier condition) throws Exception
    {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!FxTestSupport.onFx(() ->
        {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            return condition.getAsBoolean();
        }))
        {
            assertTrue(System.nanoTime() < until, "Import task did not finish: " + FxTestSupport.onFx(() -> panel.root().lookup("#exchequerReviewStatus") instanceof Label label ? label.getText() : "Review page not attached"));
            Thread.sleep(25);
        }
    }

    private Button button(String id)
    {
        return (Button) panel.root().lookup("#" + id);
    }

    private TableView<?> table(String id)
    {
        return (TableView<?>) panel.root().lookup("#" + id);
    }

    @SuppressWarnings("unchecked")
    private void choose(String id, long value)
    {
        ComboBox<TransactionLineEditorModel.Option> combo = (ComboBox<TransactionLineEditorModel.Option>) panel.root().lookup("#" + id);
        combo.setValue(combo.getItems().stream().filter(java.util.Objects::nonNull).filter(option -> option.id() == value).findFirst().orElseThrow());
    }
}
