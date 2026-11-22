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
            stage.getScene().getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
            stage.getScene().getRoot().setStyle("-fx-font-family: 'DejaVu Sans'; -fx-font-size: 12px;");
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
            proposals.getSelectionModel().clearAndSelect(0);
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
            button("exchequerEntryPayeeClear").fire();
            button("applyExchequerEntryChanges").fire();
            ((TabPane) panel.root().lookup("#exchequerReviewPages")).getSelectionModel().select(0);
            proposals.getSelectionModel().clearAndSelect(1);
            button("deleteExchequerEntry").fire();
            assertEquals(1, proposals.getItems().size());
            proposals.getSelectionModel().clearAndSelect(0);
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
            assertEquals("Apply Entry Changes", panel.commandLabel(AppCommand.SAVE_ACTIVE));
            panel.onSave();
            assertEquals(1, UiServiceRegistry.transactionEntry().search(null, null, "", 100).size(),
                    "Applying an editor draft does not write Journal");
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
            button("applyExchequerEntryChanges").fire();
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
    void sourceHintsApplyCancelAndRefreshKeepAcceptedChangesAndShowRowState() throws Exception
    {
        byte[] sourceBytes = java.nio.file.Files.readAllBytes(csvFile);
        FxTestSupport.onFx(() ->
        {
            table("exchequerProposedEntries").getSelectionModel().clearAndSelect(0);
            button("editExchequerEntry").fire();
            var account = suggestion("exchequerLineAccount");
            assertNull(account.getValue());
            assertEquals("Checking", account.getButtonCell().getText());
            assertTrue(account.getButtonCell().getStyleClass().contains("import-source-suggestion"));
            layout();
            assertFalse(account.isDisabled(), "Grey source hints remain editable through the dropdown");
            assertEquals(javafx.scene.paint.Color.web("#5f6b7a"), account.getButtonCell().getTextFill());
            assertEquals("General Fund", suggestion("exchequerLineFund").getButtonCell().getText());
            assertTrue(suggestion("exchequerEntryPayee").getButtonCell().getText().contains("Example source payee"));
            assertTrue(account.getItems().stream().allMatch(choice -> choice.id() != null));
            button("exchequerEntryPayeeClear").fire();
            choose("exchequerLineAccount", fixture.bank());
            choose("exchequerLineFund", fixture.source());
            assertFalse(account.getButtonCell().getStyleClass().contains("import-source-suggestion"));
            table("exchequerProposedLines").getSelectionModel().select(1);
            choose("exchequerLineAccount", fixture.income());
            choose("exchequerLineFund", fixture.source());
            assertFalse(button("refreshExchequerChoices").isDisabled(), "Refresh retains unapplied edits while reloading choices");
            assertTrue(button("commitExchequerEntries").isDisabled());
            ((TextArea) panel.root().lookup("#exchequerEntryMemo")).setText("Accepted source correction");
            button("applyExchequerEntryChanges").fire();
            assertEquals(0, ((TabPane) panel.root().lookup("#exchequerReviewPages")).getSelectionModel().getSelectedIndex());
            layout();
            assertEquals("Updated", reviewStatus(0));
            assertTrue(proposalRow(0).getStyleClass().contains("import-entry-updated"));
            assertTrue(proposalRow(0).lookupAll(".table-cell").stream().filter(TableCell.class::isInstance)
                    .map(TableCell.class::cast).anyMatch(cell -> cell.getFont().getStyle().toLowerCase().contains("bold")));
            assertEquals("Needs attention", reviewStatus(1));
            assertTrue(proposalRow(1).getStyleClass().contains("import-entry-invalid"));
            saveScreenshot("sca.importProposedUpdatedScreenshot");
            assertEquals(1, UiServiceRegistry.transactionEntry().search(null, null, "", 100).size());
            button("editExchequerEntry").fire();
            ((TextArea) panel.root().lookup("#exchequerEntryMemo")).setText("Discard this change");
            table("exchequerProposedLines").getSelectionModel().select(0);
            choose("exchequerLineAccount", fixture.income());
            button("cancelExchequerEntryChanges").fire();
            assertEquals("Accepted source correction", ((TextArea) panel.root().lookup("#exchequerEntryMemo")).getText());
            assertEquals(fixture.bank(), suggestion("exchequerLineAccount").getValue().id());
            assertEquals("None", suggestion("exchequerEntryPayee").getButtonCell().getText());
            ((TabPane) panel.root().lookup("#exchequerReviewPages")).getSelectionModel().select(0);
            button("refreshExchequerChoices").fire();
            return null;
        });
        await(() -> ((Label) panel.root().lookup("#exchequerReviewStatus")).getText().startsWith("Choices refreshed"));
        FxTestSupport.onFx(() ->
        {
            layout();
            assertEquals("Ready", reviewStatus(0));
            assertFalse(proposalRow(0).getStyleClass().contains("import-entry-updated"));
            assertTrue(proposalRow(0).lookupAll(".table-cell").stream().filter(TableCell.class::isInstance)
                    .map(TableCell.class::cast).noneMatch(cell -> cell.getFont().getStyle().toLowerCase().contains("bold")));
            saveScreenshot("sca.importProposedAcceptedScreenshot");
            button("editExchequerEntry").fire();
            assertEquals("Accepted source correction", ((TextArea) panel.root().lookup("#exchequerEntryMemo")).getText());
            assertEquals("None", suggestion("exchequerEntryPayee").getButtonCell().getText());
            saveScreenshot("sca.importSuggestionScreenshot");
            return null;
        });
        assertArrayEquals(sourceBytes, java.nio.file.Files.readAllBytes(csvFile));
    }

    @Test
    void multiDeleteRemovesOnlySelectedProposalsAndRetainsSourceAndJournal() throws Exception
    {
        byte[] bytes = java.nio.file.Files.readAllBytes(csvFile);
        FxTestSupport.onFx(() ->
        {
            var proposals = table("exchequerProposedEntries");
            assertEquals(SelectionMode.MULTIPLE, proposals.getSelectionModel().getSelectionMode());
            proposals.getSelectionModel().selectIndices(0, 1);
            assertEquals(2, proposals.getSelectionModel().getSelectedItems().size());
            assertTrue(button("deleteExchequerEntry").getText().contains("(2)"));
            button("deleteExchequerEntry").fire();
            assertTrue(proposals.getItems().isEmpty());
            assertTrue(button("deleteExchequerEntry").isDisabled());
            assertFalse(panel.hasUnsavedChanges());
            assertEquals(1, UiServiceRegistry.transactionEntry().search(null, null, "", 100).size());
            return null;
        });
        assertArrayEquals(bytes, java.nio.file.Files.readAllBytes(csvFile));
    }

    @Test
    void exactMatchesResolveAllSuppliedLookupsAndRefreshNeverSwitchesAStaleAccountIdentity() throws Exception
    {
        Path db = Path.of(MainWindow.sharedSessionState().databaseSelection().activeDatabasePath());
        long payeeId, budgetId, merchantId;
        try (Jpa jpa = new Jpa(db); var em = jpa.em())
        {
            em.getTransaction().begin();
            var company = em.createQuery("from Company c where c.code='DEFAULT'", Company.class).getSingleResult();
            em.find(Account.class, fixture.bank()).setName("Checking");
            em.find(Account.class, fixture.income()).setName("Example Income");
            em.find(Fund.class, fixture.source()).setName("General Fund");
            var payee = new Counterparty(); payee.setCompany(company); payee.setDisplayName("Known Payee"); em.persist(payee);
            var budget = new BudgetCategory(); budget.setCompany(company); budget.setCode("CSV"); budget.setName("Known Budget"); em.persist(budget);
            var merchant = new Merchant(); merchant.setCompany(company); merchant.setName("Known Merchant"); em.persist(merchant);
            em.flush(); payeeId = payee.getId(); budgetId = budget.getId(); merchantId = merchant.getId();
            em.getTransaction().commit();
        }
        var records = ExchequerCsvFixture.header();
        var row = ExchequerCsvFixture.row(1, "3/1/2026", "10");
        row.set(7, "Known Payee"); row.set(11, "Known Budget"); row.set(14, "Known Merchant");
        ExchequerCsvFixture.split(row, 0, "10", "Example Income", ""); records.add(row);
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
            button("editExchequerEntry").fire();
            assertEquals(fixture.bank(), suggestion("exchequerLineAccount").getValue().id());
            assertEquals(fixture.source(), suggestion("exchequerLineFund").getValue().id());
            assertEquals(payeeId, suggestion("exchequerEntryPayee").getValue().id());
            assertEquals(budgetId, suggestion("exchequerLineBudget").getValue().id());
            assertEquals(merchantId, suggestion("exchequerLineMerchant").getValue().id());
            assertFalse(suggestion("exchequerLineAccount").getButtonCell().getStyleClass().contains("import-source-suggestion"));
            table("exchequerProposedLines").getSelectionModel().select(1);
            assertEquals(fixture.income(), suggestion("exchequerLineAccount").getValue().id());
            assertEquals("Ready", reviewStatus(0));
            return null;
        });
        try (Jpa jpa = new Jpa(db); var em = jpa.em())
        {
            em.getTransaction().begin();
            var previous = em.find(Account.class, fixture.bank());
            previous.setActive(false);
            var replacement = new Account(); replacement.setChart(previous.getChart()); replacement.setCode("1001");
            replacement.setName("Checking"); replacement.setAccountType(AccountType.ASSET);
            replacement.setAccountFunction(AccountFunction.BANK); replacement.setNormalBalance(NormalBalance.DEBIT);
            em.persist(replacement); em.getTransaction().commit();
        }
        FxTestSupport.onFx(() -> { button("refreshExchequerChoices").fire(); return null; });
        await(() -> ((Label) panel.root().lookup("#exchequerReviewStatus")).getText().startsWith("Choices refreshed"));
        FxTestSupport.onFx(() ->
        {
            table("exchequerProposedLines").getSelectionModel().select(0);
            assertNull(suggestion("exchequerLineAccount").getValue());
            assertEquals("Checking", suggestion("exchequerLineAccount").getButtonCell().getText());
            assertTrue(suggestion("exchequerLineAccount").getButtonCell().getStyleClass().contains("import-source-suggestion"));
            assertFalse(suggestion("exchequerLineAccount").getItems().stream().anyMatch(a -> a.id() == fixture.bank()));
            assertEquals("Needs attention", reviewStatus(0));
            assertTrue(button("commitExchequerEntries").isDisabled());
            suggestion("exchequerLineAccount").show();
            assertTrue(suggestion("exchequerLineAccount").isShowing());
            suggestion("exchequerLineAccount").hide();
            saveScreenshot("sca.importUnmappedScreenshot");
            return null;
        });
    }

    @Test
    void bulkMappingsReuseChosenReferencesAndOnlyClearTheSameOptionalSourceLabel() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            table("exchequerProposedEntries").getSelectionModel().clearAndSelect(0);
            button("editExchequerEntry").fire();
            choose("exchequerLineAccount", fixture.bank());
            choose("exchequerLineFund", fixture.source());
            button("exchequerEntryPayeeClear").fire();
            button("mapExchequerLabels").fire();
            ((TabPane) panel.root().lookup("#exchequerReviewPages")).getSelectionModel().select(0);
            table("exchequerProposedEntries").getSelectionModel().clearAndSelect(1);
            button("editExchequerEntry").fire();
            assertEquals(fixture.bank(), suggestion("exchequerLineAccount").getValue().id());
            assertEquals(fixture.source(), suggestion("exchequerLineFund").getValue().id());
            assertNull(suggestion("exchequerEntryPayee").getValue());
            assertTrue(suggestion("exchequerEntryPayee").getButtonCell().getText().contains("payee 2"));
            assertTrue(suggestion("exchequerEntryPayee").getButtonCell().getStyleClass().contains("import-source-suggestion"));
            table("exchequerProposedLines").getSelectionModel().select(1);
            assertEquals(fixture.source(), suggestion("exchequerLineFund").getValue().id());
            assertNull(suggestion("exchequerLineAccount").getValue(), "A bank mapping does not overwrite a category mapping");
            return null;
        });
    }

    @Test
    void refreshPreservesUnappliedRecordAndCancelStillRestoresTheLastAppliedVersion() throws Exception
    {
        String originalMemo = FxTestSupport.onFx(() ->
        {
            button("editExchequerEntry").fire();
            String before = ((TextArea) panel.root().lookup("#exchequerEntryMemo")).getText();
            ((TextArea) panel.root().lookup("#exchequerEntryMemo")).setText("Unfinished correction");
            choose("exchequerLineAccount", fixture.bank());
            choose("exchequerLineFund", fixture.source());
            button("exchequerEntryPayeeClear").fire();
            button("refreshExchequerChoices").fire();
            return before;
        });
        await(() -> ((Label) panel.root().lookup("#exchequerReviewStatus")).getText().startsWith("Choices refreshed"));
        FxTestSupport.onFx(() ->
        {
            assertEquals("Unfinished correction", ((TextArea) panel.root().lookup("#exchequerEntryMemo")).getText());
            assertEquals(fixture.bank(), suggestion("exchequerLineAccount").getValue().id());
            assertEquals("None", suggestion("exchequerEntryPayee").getButtonCell().getText());
            assertEquals("Unapplied edits", reviewStatus(0));
            assertTrue(button("commitExchequerEntries").isDisabled());
            button("cancelExchequerEntryChanges").fire();
            assertEquals(originalMemo, ((TextArea) panel.root().lookup("#exchequerEntryMemo")).getText());
            assertNull(suggestion("exchequerLineAccount").getValue());
            assertTrue(suggestion("exchequerEntryPayee").getButtonCell().getStyleClass().contains("import-source-suggestion"));
            return null;
        });
    }

    private void layout()
    {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    private SuggestedImportComboBox suggestion(String id)
    {
        return (SuggestedImportComboBox) panel.root().lookup("#" + id);
    }

    private String reviewStatus(int index)
    {
        return table("exchequerProposedEntries").getColumns().stream()
                .filter(column -> column.getText().equals("Review status")).findFirst().orElseThrow().getCellData(index).toString();
    }

    private TableRow<?> proposalRow(int index)
    {
        return table("exchequerProposedEntries").lookupAll(".table-row-cell").stream().filter(TableRow.class::isInstance)
                .map(TableRow.class::cast).filter(row -> !row.isEmpty() && row.getIndex() == index).findFirst().orElseThrow();
    }

    private void saveScreenshot(String property) throws Exception
    {
        String path = System.getProperty(property);
        if (path != null)
        {
            layout();
            var image = stage.getScene().snapshot(null);
            var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++)
            {
                for (int x = 0; x < image.getWidth(); x++)
                {
                    output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                }
            }
            javax.imageio.ImageIO.write(output, "png", Path.of(path).toFile());
        }
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
