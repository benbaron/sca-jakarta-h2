package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
            var workspace = stage.getScene().getRoot();
            workspace.getStyleClass().add("production-workspace");
            WorkspaceTextContrastEnforcer.install(workspace);
            applyTheme(UiThemePreference.LIGHT);
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
                javafx.application.Application.setUserAgentStylesheet(javafx.application.Application.STYLESHEET_MODENA);
            }
            DrillThroughCoordinator.configureOpener(null);
            for (var id : List.of(AppPanelId.CHART_OF_ACCOUNTS, AppPanelId.FUNDS, AppPanelId.BUDGET_CATEGORIES,
                    AppPanelId.ACTIVITIES, AppPanelId.MERCHANTS, AppPanelId.COUNTERPARTIES))
            {
                DrillThroughCoordinator.consumeContext(id);
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

    @Test
    void productionGreySurvivesAllThemesSelectionAndLookupResolution() throws Exception
    {
        var records = ExchequerCsvFixture.header();
        for (int ordinal = 1; ordinal <= 3; ordinal++)
        {
            var row = ExchequerCsvFixture.row(ordinal, "3/" + ordinal + "/2026", "10");
            row.set(7, "Unmapped payee");
            row.set(11, "Unmapped budget");
            row.set(14, "Unmapped merchant");
            ExchequerCsvFixture.split(row, 0, "10", "Example Income", "");
            records.add(row);
        }
        java.nio.file.Files.writeString(csvFile, ExchequerCsvFixture.csv(records));
        FxTestSupport.onFx(() ->
        {
            Platform.runLater(ExchequerImportReviewPaneTest::acceptConfirmation);
            panel.root().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(b -> b.getText().startsWith("Choose Another Exchequer")).findFirst().orElseThrow().fire();
            return null;
        });
        await(() -> table("exchequerProposedEntries").getItems().size() == 3);
        FxTestSupport.onFx(() ->
        {
            for (var theme : UiThemePreference.values())
            {
                applyTheme(theme);
                table("exchequerProposedEntries").getSelectionModel().clearAndSelect(0);
                button("editExchequerEntry").fire();
                table("exchequerProposedLines").getSelectionModel().select(1);
                layout();
                for (String id : List.of("exchequerLineAccount", "exchequerLineFund", "exchequerLineBudget",
                        "exchequerLineMerchant", "exchequerEntryPayee"))
                {
                    var combo = suggestion(id);
                    assertNull(combo.getValue(), theme + " unresolved " + id);
                    assertMuted(combo.getButtonCell());
                }
                for (String text : List.of("Checking", "Example Income"))
                {
                    var cell = table("exchequerProposedLines").lookupAll(".table-cell").stream()
                            .filter(TableCell.class::isInstance).map(TableCell.class::cast)
                            .filter(c -> c.getStyleClass().contains("import-source-suggestion") && text.equals(c.getText()))
                            .findFirst().orElseThrow();
                    assertMuted(cell);
                }
                if (theme == UiThemePreference.LIGHT)
                {
                    saveScreenshot("sca.importContrastEditorScreenshot");
                }
                ((TabPane) panel.root().lookup("#exchequerReviewPages")).getSelectionModel().select(0);
                table("exchequerProposedEntries").getSelectionModel().selectIndices(0, 1);
                layout();
                for (int index : new int[] {0, 2})
                {
                    var cell = proposalRow(index).lookupAll(".table-cell").stream()
                            .filter(TableCell.class::isInstance).map(TableCell.class::cast)
                            .filter(c -> "Needs attention".equals(c.getText())).findFirst().orElseThrow();
                    assertMuted(cell);
                }
                if (theme == UiThemePreference.LIGHT)
                {
                    saveScreenshot("sca.importContrastRowsScreenshot");
                }
            }
            applyTheme(UiThemePreference.LIGHT);
            table("exchequerProposedEntries").getSelectionModel().clearAndSelect(0);
            button("editExchequerEntry").fire();
            table("exchequerProposedLines").getSelectionModel().select(0);
            choose("exchequerLineAccount", fixture.bank());
            button("exchequerEntryPayeeClear").fire();
            layout();
            assertNormal(suggestion("exchequerLineAccount").getButtonCell());
            assertNormal(suggestion("exchequerEntryPayee").getButtonCell());
            table("exchequerProposedLines").getSelectionModel().select(1);
            layout();
            assertMuted(suggestion("exchequerLineAccount").getButtonCell());
            table("exchequerProposedLines").getSelectionModel().select(0);
            layout();
            assertNormal(suggestion("exchequerLineAccount").getButtonCell());
            button("cancelExchequerEntryChanges").fire();
            layout();
            assertMuted(suggestion("exchequerLineAccount").getButtonCell());
            assertMuted(suggestion("exchequerEntryPayee").getButtonCell());
            assertTrue(button("commitExchequerEntries").isDisabled());
            return null;
        });
    }

    @Test
    void addLinksRouteEveryMaintainedLookupAndRespectPermissionAndScope() throws Exception
    {
        var destinations = new java.util.ArrayList<AppPanelId>();
        FxTestSupport.onFx(() ->
        {
            DrillThroughCoordinator.configureOpener(destinations::add);
            button("editExchequerEntry").fire();
            ((TextArea) panel.root().lookup("#exchequerEntryMemo")).setText("Keep this pending import edit");
            var routes = java.util.Map.of(
                    "exchequerLineAccountAdd", AppPanelId.CHART_OF_ACCOUNTS,
                    "exchequerLineFundAdd", AppPanelId.FUNDS,
                    "exchequerLineBudgetAdd", AppPanelId.BUDGET_CATEGORIES,
                    "exchequerLineEventAdd", AppPanelId.ACTIVITIES,
                    "exchequerLineMerchantAdd", AppPanelId.MERCHANTS,
                    "exchequerEntryPayeeAdd", AppPanelId.COUNTERPARTIES);
            for (var route : routes.entrySet())
            {
                assertFalse(button(route.getKey()).isDisabled());
                button(route.getKey()).fire();
                assertEquals(route.getValue(), destinations.get(destinations.size() - 1));
            }
            assertEquals(6, destinations.size());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            for (var id : routes.keySet())
            {
                assertTrue(button(id).isDisabled());
                assertTrue(button(id).getTooltip().getText().contains("BOOKKEEPING_WRITE"));
                button(id).fire();
            }
            assertEquals(6, destinations.size());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.manager());
            MainWindow.sharedSessionState().setMultiCompany(new MultiCompanyState("OTHER", List.of("DEFAULT", "OTHER")));
            panel.onPanelShown();
            assertTrue(button("exchequerLineBudgetAdd").isDisabled());
            button("exchequerLineBudgetAdd").fire();
            assertEquals(6, destinations.size());
            MainWindow.sharedSessionState().setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
            var reviewedDatabase = MainWindow.sharedSessionState().databaseSelection();
            MainWindow.sharedSessionState().setDatabaseSelection(new DatabaseSelectionState(
                    directory.resolve("another-database").toString(), List.of(directory.resolve("another-database").toString())));
            panel.onPanelShown();
            assertTrue(button("exchequerLineBudgetAdd").isDisabled());
            button("exchequerLineBudgetAdd").fire();
            assertEquals(6, destinations.size());
            MainWindow.sharedSessionState().setDatabaseSelection(reviewedDatabase);
            panel.onPanelShown();
            return null;
        });
        await(() -> ((Label) panel.root().lookup("#exchequerReviewStatus")).getText().startsWith("Choices reloaded after Add"));
        FxTestSupport.onFx(() ->
        {
            assertEquals("Keep this pending import edit", ((TextArea) panel.root().lookup("#exchequerEntryMemo")).getText());
            assertEquals("Unapplied edits", reviewStatus(0));
            assertTrue(button("commitExchequerEntries").isDisabled());
            assertEquals(1, UiServiceRegistry.transactionEntry().search(null, null, "", 100).size());
            return null;
        });
    }

    @Test
    void addBudgetUsesRealMaintenanceTabPreservesBothDraftsAndReloadsSavedRecord() throws Exception
    {
        UiServiceRegistry.budgetCategoryAdmin().save(new BudgetCategoryCommand(null, "RETIRED", "Retired category", false, null, null, ""));
        PanelHost host = FxTestSupport.onFx(() ->
        {
            stage.getScene().setRoot(new javafx.scene.layout.VBox());
            var value = new PanelHost();
            value.getStyleClass().addAll("production-workspace", "theme-light", "theme-atlantafx");
            WorkspaceTextContrastEnforcer.install(value);
            WorkspaceTextContrastEnforcer.apply(value, false);
            value.showReplacement(AppPanelId.IMPORT_PREVIEW, panel);
            stage.getScene().setRoot(value);
            DrillThroughCoordinator.configureOpener(value::show);
            value.show(AppPanelId.BUDGET_CATEGORIES);
            layout();
            ((TextField) value.lookup("#budgetCategoriesCode")).setText("C17");
            ((TextField) value.lookup("#budgetCategoriesName")).setText("Preserve existing category draft");
            value.show(AppPanelId.IMPORT_PREVIEW);
            button("editExchequerEntry").fire();
            table("exchequerProposedLines").getSelectionModel().select(1);
            ((TextArea) panel.root().lookup("#exchequerEntryMemo")).setText("Retain import header");
            ((TextField) panel.root().lookup("#exchequerLineDebit")).setText("invalid raw amount");
            button("exchequerLineBudgetAdd").fire();
            assertEquals(AppPanelId.BUDGET_CATEGORIES, value.activePanelId());
            assertTrue(value.isOpen(AppPanelId.IMPORT_PREVIEW));
            layout();
            assertEquals("Preserve existing category draft", ((TextField) value.lookup("#budgetCategoriesName")).getText());
            ((TextField) value.lookup("#budgetCategoriesName")).setText("Created from Import");
            value.saveActive();
            var back = (Button) value.lookup("#budgetCategoriesJournal");
            assertEquals("Return to Import", back.getText());
            @SuppressWarnings("unchecked")
            var visibleCategories = (TableView<BudgetCategoryView>) value.lookup("#budgetCategoriesTable");
            assertTrue(visibleCategories.getItems().stream().anyMatch(category -> "RETIRED".equals(category.code()) && !category.active()));
            saveScreenshot("sca.budgetCategoriesCallerScreenshot");
            back.fire();
            assertEquals(AppPanelId.IMPORT_PREVIEW, value.activePanelId());
            return value;
        });
        await(() -> ((Label) panel.root().lookup("#exchequerReviewStatus")).getText().startsWith("Choices reloaded after Add"));
        FxTestSupport.onFx(() ->
        {
            assertEquals(AppPanelId.IMPORT_PREVIEW, host.activePanelId());
            assertEquals("Retain import header", ((TextArea) panel.root().lookup("#exchequerEntryMemo")).getText());
            assertEquals("invalid raw amount", ((TextField) panel.root().lookup("#exchequerLineDebit")).getText());
            assertEquals(1, table("exchequerProposedLines").getSelectionModel().getSelectedIndex());
            var created = suggestion("exchequerLineBudget").getItems().stream()
                    .filter(option -> option.name().equals("Created from Import")).findFirst().orElseThrow();
            assertNotNull(created.id());
            assertNull(suggestion("exchequerLineBudget").getValue(), "A blank source does not guess the new record");
            choose("exchequerLineBudget", created.id());
            layout();
            assertNormal(suggestion("exchequerLineBudget").getButtonCell());
            assertTrue(button("commitExchequerEntries").isDisabled());
            saveScreenshot("sca.importLookupAddScreenshot");
            button("cancelExchequerEntryChanges").fire();
            assertFalse(((TextArea) panel.root().lookup("#exchequerEntryMemo")).getText().equals("Retain import header"));
            assertEquals(1, UiServiceRegistry.transactionEntry().search(null, null, "", 100).size());
            return null;
        });
        assertTrue(UiServiceRegistry.budgetCategoryLookup().listForMaintenance().stream()
                .anyMatch(category -> "C17".equals(category.code()) && "Created from Import".equals(category.name())));
    }

    @ParameterizedTest
    @EnumSource(value = AppPanelId.class, names = {"CHART_OF_ACCOUNTS", "FUNDS", "ACTIVITIES", "MERCHANTS", "COUNTERPARTIES"})
    void everyAddDestinationRefreshesAllRowsRetainsDraftsAndReturnsToImport(AppPanelId destination) throws Exception
    {
        MaintenanceRoute route = maintenanceRoute(destination);
        long retiredId = seedRetiredMaintenanceRecord(destination);
        int journalCount = UiServiceRegistry.transactionEntry().search(null, null, "", 100).size();
        PanelHost host = FxTestSupport.onFx(() ->
        {
            stage.getScene().setRoot(new javafx.scene.layout.VBox());
            var value = new PanelHost();
            value.getStyleClass().addAll("production-workspace", "theme-light", "theme-atlantafx");
            WorkspaceTextContrastEnforcer.install(value);
            value.showReplacement(AppPanelId.IMPORT_PREVIEW, panel);
            stage.getScene().setRoot(value);
            DrillThroughCoordinator.configureOpener(value::show);
            value.show(destination);
            return value;
        });
        await(() -> maintenanceReady(host, route));
        FxTestSupport.onFx(() ->
        {
            layout();
            assertTrue(maintenanceTable(host, route).getItems().stream().anyMatch(row -> maintenanceId(row) == retiredId));
            ((TextField) host.lookup("#" + route.prefix() + "Name")).setText("Retain destination draft");
            if (destination == AppPanelId.CHART_OF_ACCOUNTS)
            {
                ((TextField) host.lookup("#chartOfAccountsCode")).setText("1998");
                maintenanceCombo(host, "chartOfAccountsType").setValue(AccountType.ASSET);
                maintenanceCombo(host, "chartOfAccountsBalance").setValue(NormalBalance.DEBIT);
            }
            else if (destination == AppPanelId.FUNDS)
            {
                ((TextField) host.lookup("#fundsCode")).setText("C18NEW");
                maintenanceCombo(host, "fundsType").setValue(FundType.UNRESTRICTED);
            }
            else if (destination == AppPanelId.ACTIVITIES)
            {
                ((TextField) host.lookup("#activitiesCode")).setText("C18NEW");
            }
            if (route.searchable())
            {
                ((TextField) host.lookup("#" + route.prefix() + "Search")).setText("missing category");
                assertTrue(maintenanceTable(host, route).getItems().isEmpty());
                assertTrue(((Label) maintenanceTable(host, route).getPlaceholder()).getText().contains("No matching"));
            }
            host.show(AppPanelId.IMPORT_PREVIEW);
            button("editExchequerEntry").fire();
            table("exchequerProposedLines").getSelectionModel().select(1);
            ((TextArea) panel.root().lookup("#exchequerEntryMemo")).setText("Keep source header");
            ((TextField) panel.root().lookup("#exchequerLineDebit")).setText("raw pending amount");
            button(route.lookup() + "Add").fire();
            assertEquals(destination, host.activePanelId());
            return null;
        });
        await(() -> maintenanceReady(host, route));
        FxTestSupport.onFx(() ->
        {
            layout();
            assertEquals("Return to Import", ((Button) host.lookup("#" + route.returnId())).getText());
            assertEquals("Retain destination draft", ((TextField) host.lookup("#" + route.prefix() + "Name")).getText());
            if (route.searchable()) assertEquals("", ((TextField) host.lookup("#" + route.prefix() + "Search")).getText());
            assertTrue(maintenanceTable(host, route).getItems().stream().anyMatch(row -> maintenanceId(row) == retiredId));
            ((Button) host.lookup("#" + route.prefix() + "Refresh")).fire();
            // Simulate typing while the real asynchronous load is in flight as well.
            ((TextField) host.lookup("#" + route.prefix() + "Name")).setText("Created through Import");
            return null;
        });
        await(() -> maintenanceReady(host, route));
        FxTestSupport.onFx(() ->
        {
            assertEquals("Created through Import", ((TextField) host.lookup("#" + route.prefix() + "Name")).getText());
            if (route.searchable()) ((TextField) host.lookup("#" + route.prefix() + "Search")).setText("missing again");
            ((Button) host.lookup("#" + route.prefix() + "ShowAll")).fire();
            return null;
        });
        await(() -> maintenanceReady(host, route));
        FxTestSupport.onFx(() ->
        {
            assertEquals("Created through Import", ((TextField) host.lookup("#" + route.prefix() + "Name")).getText());
            if (route.searchable()) ((TextField) host.lookup("#" + route.prefix() + "Search")).setText("hide new record");
            host.saveActive();
            return null;
        });
        await(() -> maintenanceReady(host, route));
        long savedId = FxTestSupport.onFx(() ->
        {
            var saved = maintenanceTable(host, route).getSelectionModel().getSelectedItem();
            assertNotNull(saved, "Save reveals and selects its record");
            assertEquals("Created through Import", maintenanceName(saved));
            if (route.searchable()) assertEquals("", ((TextField) host.lookup("#" + route.prefix() + "Search")).getText());
            layout();
            assertMaintenanceActionVisible(host, route.returnId());
            assertMaintenanceActionVisible(host, route.prefix() + "ShowAll");
            saveScreenshot("sca.maintenanceScreenshot." + destination.name());
            ((Button) host.lookup("#" + route.returnId())).fire();
            assertEquals(AppPanelId.IMPORT_PREVIEW, host.activePanelId());
            return maintenanceId(saved);
        });
        await(() -> ((Label) panel.root().lookup("#exchequerReviewStatus")).getText().startsWith("Choices reloaded after Add"));
        FxTestSupport.onFx(() ->
        {
            assertEquals("Keep source header", ((TextArea) panel.root().lookup("#exchequerEntryMemo")).getText());
            assertEquals("raw pending amount", ((TextField) panel.root().lookup("#exchequerLineDebit")).getText());
            assertEquals(1, table("exchequerProposedLines").getSelectionModel().getSelectedIndex());
            assertTrue(suggestion(route.lookup()).getItems().stream().anyMatch(option -> option.id() != null && option.id() == savedId));
            assertTrue(suggestion(route.lookup()).getItems().stream().noneMatch(option -> option.id() != null && option.id() == retiredId));
            // Changing callers changes the same button; reselecting the same tab does not.
            host.showReplacement(AppPanelId.JOURNAL_PANE, new AppPanel()
            {
                private final javafx.scene.layout.VBox content = new javafx.scene.layout.VBox();
                @Override public String title() { return "Journal"; }
                @Override public javafx.scene.Node root() { return content; }
            });
            host.show(destination);
            host.show(destination);
            layout();
            assertEquals("Return to Journal", ((Button) host.lookup("#" + route.returnId())).getText());
            ((Button) host.lookup("#" + route.returnId())).fire();
            assertEquals(AppPanelId.JOURNAL_PANE, host.activePanelId());
            return null;
        });
        assertEquals(journalCount, UiServiceRegistry.transactionEntry().search(null, null, "", 100).size());
    }

    @ParameterizedTest
    @EnumSource(value = AppPanelId.class, names = {"CHART_OF_ACCOUNTS", "FUNDS", "ACTIVITIES", "MERCHANTS", "COUNTERPARTIES"})
    void maintenanceLoadFailuresAreDistinctAndRecoveryRetainsEditor(AppPanelId destination) throws Exception
    {
        MaintenanceRoute route = maintenanceRoute(destination);
        PanelHost host = FxTestSupport.onFx(() ->
        {
            stage.getScene().setRoot(new javafx.scene.layout.VBox());
            var value = new PanelHost();
            value.showReplacement(AppPanelId.IMPORT_PREVIEW, panel);
            stage.getScene().setRoot(value);
            value.show(destination);
            return value;
        });
        await(() -> maintenanceReady(host, route));
        FxTestSupport.onFx(() ->
        {
            layout();
            if (route.searchable())
            {
                assertTrue(maintenanceTable(host, route).getItems().isEmpty());
                assertTrue(((Label) maintenanceTable(host, route).getPlaceholder()).getText().contains("have been created"));
            }
            ((TextField) host.lookup("#" + route.prefix() + "Name")).setText("Keep during recovery");
            MainWindow.sharedSessionState().setMultiCompany(new MultiCompanyState("MISSING", List.of("DEFAULT", "MISSING")));
            host.show(destination);
            return null;
        });
        await(() -> maintenanceReady(host, route));
        FxTestSupport.onFx(() ->
        {
            assertTrue(((Label) maintenanceTable(host, route).getPlaceholder()).getText().contains("Could not load"));
            assertEquals("Keep during recovery", ((TextField) host.lookup("#" + route.prefix() + "Name")).getText());
            MainWindow.sharedSessionState().setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
            host.show(destination);
            return null;
        });
        await(() -> maintenanceReady(host, route));
        FxTestSupport.onFx(() ->
        {
            assertEquals("Keep during recovery", ((TextField) host.lookup("#" + route.prefix() + "Name")).getText());
            assertFalse(((Label) maintenanceTable(host, route).getPlaceholder()).getText().contains("Could not load"));
            if (route.searchable()) assertTrue(((Label) maintenanceTable(host, route).getPlaceholder()).getText().contains("have been created"));
            return null;
        });
        assertEquals(1, UiServiceRegistry.transactionEntry().search(null, null, "", 100).size());
    }

    @ParameterizedTest
    @EnumSource(value = AppPanelId.class, names = {"CHART_OF_ACCOUNTS", "FUNDS", "BUDGET_CATEGORIES", "ACTIVITIES", "MERCHANTS", "COUNTERPARTIES"})
    void addPrefillsLiteralNameInADistinctNewRecordWithoutGeneratingCodesOrSaving(AppPanelId destination) throws Exception
    {
        MaintenanceRoute route = maintenanceRoute(destination);
        long oldId = seedRetiredMaintenanceRecord(destination);
        int before = maintenanceRecords(destination).size();
        String literal = "January's Coronation / MiXeD & café #2";
        PanelHost host = mountImportForPrefill();
        FxTestSupport.onFx(() -> { host.show(destination); return null; });
        await(() -> maintenanceReady(host, route));
        FxTestSupport.onFx(() ->
        {
            selectMaintenanceRecord(host, route, oldId);
            assertEquals("Retired choice", ((TextField) host.lookup("#" + route.prefix() + "Name")).getText());
            showImportHintAndAdd(host, route, destination, literal);
            return null;
        });
        await(() -> maintenanceReady(host, route));
        FxTestSupport.onFx(() ->
        {
            assertLiteralNewDraft(host, route, literal);
            assertNull(maintenanceTable(host, route).getSelectionModel().getSelectedItem(), "New draft is not the previously selected record");
            assertEquals(before, maintenanceRecords(destination).size(), "Add does not save master data");
            assertEquals("Retired choice", maintenanceRecords(destination).stream().filter(row -> maintenanceId(row) == oldId).map(ExchequerImportReviewPaneTest::maintenanceName).findFirst().orElseThrow());
            saveScreenshot("sca.importPrefillScreenshot." + destination.name());
            enterRequiredMaintenanceFields(host, route, destination);
            host.saveActive();
            return null;
        });
        await(() -> maintenanceReady(host, route));
        long newId = FxTestSupport.onFx(() ->
        {
            var saved = maintenanceTable(host, route).getSelectionModel().getSelectedItem();
            assertNotNull(saved);
            assertEquals(literal, maintenanceName(saved));
            assertNotEquals(oldId, maintenanceId(saved), "Explicit Save creates a distinct stable ID");
            ((Button) host.lookup("#" + route.returnId())).fire();
            return maintenanceId(saved);
        });
        await(() -> ((Label) panel.root().lookup("#exchequerReviewStatus")).getText().startsWith("Choices reloaded after Add"));
        FxTestSupport.onFx(() ->
        {
            assertEquals("Retain source while adding", ((TextArea) panel.root().lookup("#exchequerEntryMemo")).getText());
            assertEquals("raw pending amount", ((TextField) panel.root().lookup("#exchequerLineDebit")).getText());
            assertTrue(suggestion(route.lookup()).getItems().stream().anyMatch(option -> option.id() != null && option.id() == newId));
            return null;
        });
        assertEquals(before + 1, maintenanceRecords(destination).size());
        assertEquals(1, UiServiceRegistry.transactionEntry().search(null, null, "", 100).size());
    }

    @ParameterizedTest
    @EnumSource(value = AppPanelId.class, names = {"CHART_OF_ACCOUNTS", "FUNDS", "BUDGET_CATEGORIES", "ACTIVITIES", "MERCHANTS", "COUNTERPARTIES"})
    void addPreservesDirtyMasterAndQueuesItsLiteralNameForExplicitNew(AppPanelId destination) throws Exception
    {
        MaintenanceRoute route = maintenanceRoute(destination);
        long oldId = seedRetiredMaintenanceRecord(destination);
        int before = maintenanceRecords(destination).size();
        String literal = "Unchanged Literal / Second suggestion";
        PanelHost host = mountImportForPrefill();
        FxTestSupport.onFx(() -> { host.show(destination); return null; });
        await(() -> maintenanceReady(host, route));
        FxTestSupport.onFx(() ->
        {
            selectMaintenanceRecord(host, route, oldId);
            ((TextField) host.lookup("#" + route.prefix() + "Name")).setText("Keep unsaved master");
            showImportHintAndAdd(host, route, destination, literal);
            return null;
        });
        await(() -> maintenanceReady(host, route));
        FxTestSupport.onFx(() ->
        {
            assertEquals("Keep unsaved master", ((TextField) host.lookup("#" + route.prefix() + "Name")).getText());
            var notice = (Label) host.lookup("#" + route.prefix() + "ImportSuggestion");
            assertTrue(notice.isVisible());
            assertTrue(notice.getText().contains(literal));
            assertEquals(before, maintenanceRecords(destination).size());
            host.saveActive();
            return null;
        });
        await(() -> maintenanceReady(host, route));
        FxTestSupport.onFx(() ->
        {
            assertEquals("Keep unsaved master", ((TextField) host.lookup("#" + route.prefix() + "Name")).getText(), "Saving/refreshing the old draft never accepts the queued suggestion");
            assertTrue(host.newItemActive().handled());
            assertLiteralNewDraft(host, route, literal);
            assertFalse(host.lookup("#" + route.prefix() + "ImportSuggestion").isVisible());
            ((TextField) host.lookup("#" + route.prefix() + "Name")).setText("User revised the literal Name");
            ((Button) host.lookup("#" + route.prefix() + "Refresh")).fire();
            return null;
        });
        await(() -> maintenanceReady(host, route));
        FxTestSupport.onFx(() ->
        {
            assertEquals("User revised the literal Name", ((TextField) host.lookup("#" + route.prefix() + "Name")).getText(), "A consumed hint never reapplies over user edits");
            return null;
        });
        assertEquals(before, maintenanceRecords(destination).size(), "Queued New is still an unsaved draft");
        assertEquals(1, UiServiceRegistry.transactionEntry().search(null, null, "", 100).size());
    }

    @Test
    void budgetNameComesFromTheActualCsvSuggestionAndResolvesOnlyAfterExplicitSave() throws Exception
    {
        String literal = "January Coronation / MiXeD & café #2";
        var records = ExchequerCsvFixture.header();
        var row = ExchequerCsvFixture.row(1, "3/1/2026", "10");
        row.set(11, literal);
        ExchequerCsvFixture.split(row, 0, "10", "Example Income", "");
        records.add(row);
        java.nio.file.Files.writeString(csvFile, ExchequerCsvFixture.csv(records));
        byte[] original = java.nio.file.Files.readAllBytes(csvFile);
        FxTestSupport.onFx(() ->
        {
            Platform.runLater(ExchequerImportReviewPaneTest::acceptConfirmation);
            panel.root().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getText().startsWith("Choose Another Exchequer")).findFirst().orElseThrow().fire();
            return null;
        });
        await(() -> table("exchequerProposedEntries").getItems().size() == 1);
        PanelHost host = mountImportForPrefill();
        FxTestSupport.onFx(() ->
        {
            button("editExchequerEntry").fire();
            table("exchequerProposedLines").getSelectionModel().select(0);
            assertEquals(literal, suggestion("exchequerLineBudget").nameForNewRecord());
            button("exchequerLineBudgetAdd").fire();
            return null;
        });
        await(() -> host.lookup("#budgetCategoriesName") != null);
        FxTestSupport.onFx(() ->
        {
            assertLiteralNewDraft(host, maintenanceRoute(AppPanelId.BUDGET_CATEGORIES), literal);
            assertTrue(UiServiceRegistry.budgetCategoryLookup().listForMaintenance().isEmpty());
            ((TextField) host.lookup("#budgetCategoriesCode")).setText("JAN-CORONATION");
            host.saveActive();
            ((Button) host.lookup("#budgetCategoriesJournal")).fire();
            return null;
        });
        await(() -> ((Label) panel.root().lookup("#exchequerReviewStatus")).getText().startsWith("Choices reloaded after Add"));
        FxTestSupport.onFx(() ->
        {
            assertNotNull(suggestion("exchequerLineBudget").getValue());
            assertEquals(literal, suggestion("exchequerLineBudget").getValue().name());
            return null;
        });
        assertArrayEquals(original, java.nio.file.Files.readAllBytes(csvFile));
        assertEquals(1, UiServiceRegistry.transactionEntry().search(null, null, "", 100).size());
    }

    private PanelHost mountImportForPrefill()
    {
        return FxTestSupport.onFx(() ->
        {
            stage.getScene().setRoot(new javafx.scene.layout.VBox());
            var host = new PanelHost();
            host.getStyleClass().addAll("production-workspace", "theme-light", "theme-atlantafx");
            WorkspaceTextContrastEnforcer.install(host);
            host.showReplacement(AppPanelId.IMPORT_PREVIEW, panel);
            stage.getScene().setRoot(host);
            DrillThroughCoordinator.configureOpener(host::show);
            return host;
        });
    }

    private void showImportHintAndAdd(PanelHost host, MaintenanceRoute route, AppPanelId destination, String literal)
    {
        host.show(AppPanelId.IMPORT_PREVIEW);
        button("editExchequerEntry").fire();
        table("exchequerProposedLines").getSelectionModel().select(1);
        ((TextArea) panel.root().lookup("#exchequerEntryMemo")).setText("Retain source while adding");
        ((TextField) panel.root().lookup("#exchequerLineDebit")).setText("raw pending amount");
        suggestion(route.lookup()).show(new ImportReferenceSuggestion(literal,
                destination == AppPanelId.CHART_OF_ACCOUNTS || destination == AppPanelId.FUNDS));
        button(route.lookup() + "Add").fire();
        assertEquals(destination, host.activePanelId());
    }

    private static void selectMaintenanceRecord(PanelHost host, MaintenanceRoute route, long id)
    {
        var table = maintenanceTable(host, route);
        for (int i = 0; i < table.getItems().size(); i++)
        {
            if (maintenanceId(table.getItems().get(i)) == id)
            {
                table.getSelectionModel().select(i);
                return;
            }
        }
        fail("Missing seeded maintenance record");
    }

    private static void assertLiteralNewDraft(PanelHost host, MaintenanceRoute route, String literal)
    {
        assertEquals(literal, ((TextField) host.lookup("#" + route.prefix() + "Name")).getText());
        if (host.lookup("#" + route.prefix() + "Code") instanceof TextField code)
        {
            assertEquals("", code.getText(), "Only Name is copied; Code is never generated");
        }
        assertTrue(((CheckBox) host.lookup("#" + route.prefix() + "Active")).isSelected(), "A clean New draft does not inherit an inactive saved record");
    }

    private static void enterRequiredMaintenanceFields(PanelHost host, MaintenanceRoute route, AppPanelId destination)
    {
        if (host.lookup("#" + route.prefix() + "Code") instanceof TextField code)
        {
            code.setText(destination == AppPanelId.CHART_OF_ACCOUNTS ? "1998" : "C19NEW");
        }
        if (destination == AppPanelId.CHART_OF_ACCOUNTS)
        {
            maintenanceCombo(host, "chartOfAccountsType").setValue(AccountType.ASSET);
            maintenanceCombo(host, "chartOfAccountsBalance").setValue(NormalBalance.DEBIT);
        }
        if (destination == AppPanelId.FUNDS) maintenanceCombo(host, "fundsType").setValue(FundType.UNRESTRICTED);
    }

    private static java.util.List<?> maintenanceRecords(AppPanelId destination)
    {
        return switch (destination)
        {
            case CHART_OF_ACCOUNTS -> UiServiceRegistry.accountLookup().listPostingAccountsIncludingInactive();
            case FUNDS -> UiServiceRegistry.fundLookup().listAllFunds();
            case BUDGET_CATEGORIES -> UiServiceRegistry.budgetCategoryLookup().listForMaintenance();
            case ACTIVITIES -> UiServiceRegistry.activityLookup().listAllActivities();
            case MERCHANTS -> UiServiceRegistry.merchantAdmin().listForMaintenance();
            case COUNTERPARTIES -> UiServiceRegistry.counterpartyAdmin().listForMaintenance();
            default -> throw new IllegalArgumentException();
        };
    }

    private record MaintenanceRoute(String prefix, String lookup, String returnId, boolean searchable) {}

    private static MaintenanceRoute maintenanceRoute(AppPanelId destination)
    {
        return switch (destination)
        {
            case BUDGET_CATEGORIES -> new MaintenanceRoute("budgetCategories", "exchequerLineBudget", "budgetCategoriesJournal", true);
            case CHART_OF_ACCOUNTS -> new MaintenanceRoute("chartOfAccounts", "exchequerLineAccount", "chartOfAccountsReturn", false);
            case FUNDS -> new MaintenanceRoute("funds", "exchequerLineFund", "fundsReturn", false);
            case ACTIVITIES -> new MaintenanceRoute("activities", "exchequerLineEvent", "activitiesReturn", true);
            case MERCHANTS -> new MaintenanceRoute("merchants", "exchequerLineMerchant", "merchantsJournal", true);
            case COUNTERPARTIES -> new MaintenanceRoute("counterparties", "exchequerEntryPayee", "counterpartiesJournal", true);
            default -> throw new IllegalArgumentException("Not an additional Import maintenance destination");
        };
    }

    private static long seedRetiredMaintenanceRecord(AppPanelId destination)
    {
        return switch (destination)
        {
            case BUDGET_CATEGORIES -> UiServiceRegistry.budgetCategoryAdmin().save(new BudgetCategoryCommand(null, "C18OLD", "Retired choice", false, null, null, "")).id();
            case CHART_OF_ACCOUNTS -> UiServiceRegistry.accountAdmin().save(new AccountCommand(null, "1999", "Retired choice", AccountType.ASSET, null, NormalBalance.DEBIT, null, "", false)).getId();
            case FUNDS -> UiServiceRegistry.fundAdmin().save(new FundCommand(null, "C18OLD", "Retired choice", FundType.UNRESTRICTED, false, null, null, null, "")).getId();
            case ACTIVITIES -> UiServiceRegistry.activityAdmin().save(new ActivityCommand(null, "C18OLD", "Retired choice", false)).id();
            case MERCHANTS -> UiServiceRegistry.merchantAdmin().save(new MerchantCommand(null, "Retired choice", "", false)).id();
            case COUNTERPARTIES -> UiServiceRegistry.counterpartyAdmin().save(new CounterpartyCommand(null, "Retired choice", CounterpartyKind.ORG, "", "", "", false)).id();
            default -> throw new IllegalArgumentException();
        };
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<Object> maintenanceCombo(PanelHost host, String id)
    {
        return (ComboBox<Object>) host.lookup("#" + id);
    }

    private static boolean maintenanceReady(PanelHost host, MaintenanceRoute route)
    {
        var refresh = host.lookup("#" + route.prefix() + "Refresh");
        return refresh instanceof Button button && !button.isDisabled();
    }

    private static TableView<?> maintenanceTable(PanelHost host, MaintenanceRoute route)
    {
        if ("chartOfAccounts".equals(route.prefix()))
        {
            // Keep the existing anonymous company table-state key unchanged.
            return (TableView<?>) host.lookup("#chartOfAccountsWorkspaceSplit").lookup(".table-view");
        }
        return (TableView<?>) host.lookup("#" + route.prefix() + "Table");
    }

    private static void assertMaintenanceActionVisible(PanelHost host, String controlId)
    {
        var action = host.lookup("#" + controlId);
        javafx.scene.Node ancestor = action.getParent();
        while (ancestor != null && !(ancestor instanceof ScrollPane)) ancestor = ancestor.getParent();
        assertNotNull(ancestor, "Maintenance tools retain a local overflow viewport");
        var viewport = ancestor.lookup(".viewport");
        assertNotNull(viewport);
        var visible = viewport.localToScene(viewport.getBoundsInLocal());
        var bounds = action.localToScene(action.getBoundsInLocal());
        assertTrue(bounds.getMinX() >= visible.getMinX() - 1 && bounds.getMaxX() <= visible.getMaxX() + 1, controlId + " fits the header width");
        assertTrue(bounds.getMinY() >= visible.getMinY() - 1 && bounds.getMaxY() <= visible.getMaxY() + 1, controlId + " is visible without scrolling the normal-size header");
    }

    private static long maintenanceId(Object record)
    {
        if (record instanceof BudgetCategoryView value) return value.id();
        if (record instanceof Account value) return value.getId();
        if (record instanceof Fund value) return value.getId();
        if (record instanceof ActivityView value) return value.id();
        if (record instanceof MerchantView value) return value.id();
        return ((CounterpartyView) record).id();
    }

    private static String maintenanceName(Object record)
    {
        if (record instanceof BudgetCategoryView value) return value.name();
        if (record instanceof Account value) return value.getName();
        if (record instanceof Fund value) return value.getName();
        if (record instanceof ActivityView value) return value.name();
        if (record instanceof MerchantView value) return value.name();
        return ((CounterpartyView) record).name();
    }

    @Test
    void returningFromAddRetainsAppliedUpdatedMarker() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            DrillThroughCoordinator.configureOpener(id -> { });
            button("editExchequerEntry").fire();
            choose("exchequerLineAccount", fixture.bank());
            choose("exchequerLineFund", fixture.source());
            button("exchequerEntryPayeeClear").fire();
            table("exchequerProposedLines").getSelectionModel().select(1);
            choose("exchequerLineAccount", fixture.income());
            choose("exchequerLineFund", fixture.source());
            button("applyExchequerEntryChanges").fire();
            assertEquals("Updated", reviewStatus(0));
            button("editExchequerEntry").fire();
            button("exchequerLineFundAdd").fire();
            panel.onPanelShown();
            return null;
        });
        await(() -> ((Label) panel.root().lookup("#exchequerReviewStatus")).getText().startsWith("Choices reloaded after Add"));
        FxTestSupport.onFx(() ->
        {
            layout();
            assertEquals("Updated", reviewStatus(0));
            assertEquals(1, table("exchequerProposedLines").getSelectionModel().getSelectedIndex());
            assertEquals(fixture.income(), suggestion("exchequerLineAccount").getValue().id());
            assertEquals("None", suggestion("exchequerEntryPayee").getButtonCell().getText());
            ((TabPane) panel.root().lookup("#exchequerReviewPages")).getSelectionModel().select(0);
            layout();
            assertTrue(proposalRow(0).getStyleClass().contains("import-entry-updated"));
            return null;
        });
    }

    private void applyTheme(UiThemePreference theme)
    {
        AtlantaFxThemeManager.apply(theme);
        var root = stage.getScene().getRoot();
        root.getStyleClass().removeAll("theme-light", "theme-dark", "theme-system", "theme-atlantafx");
        root.getStyleClass().add(AtlantaFxThemeManager.isDark(theme) ? "theme-dark" : "theme-light");
        root.getStyleClass().add(theme == UiThemePreference.SYSTEM_DEFAULT ? "theme-system" : "theme-atlantafx");
        WorkspaceTextContrastEnforcer.apply(root, AtlantaFxThemeManager.isDark(theme));
    }

    private static void assertMuted(Labeled label)
    {
        assertNotEquals(javafx.scene.paint.Color.BLACK, label.getTextFill(), label.getText());
        assertNotEquals(javafx.scene.paint.Color.WHITE, label.getTextFill(), label.getText());
        assertPaintedAndReadable(label);
    }

    private static void assertNormal(Labeled label)
    {
        assertEquals(javafx.scene.paint.Color.BLACK, label.getTextFill(), label.getText());
        assertPaintedAndReadable(label);
    }

    private static void assertPaintedAndReadable(Labeled label)
    {
        var text = (javafx.scene.text.Text) label.lookup(".text");
        assertNotNull(text, label.getText());
        assertEquals(label.getTextFill(), text.getFill(), label.getText() + " painted foreground");
        SurfaceContrastAssertions.assertReadable(label);
    }

    private void layout()
    {
        ThemeTextContrastFxTest.settle(stage.getScene().getRoot());
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
            layout();
            SplitPane editor = (SplitPane) panel.root().lookup("#exchequerEntryEditorSplit");
            ScrollPane editorForm = (ScrollPane) editor.getItems().get(0);
            var viewport = editorForm.lookup(".viewport");
            double rightEdge = viewport.localToScene(viewport.getBoundsInLocal()).getMaxX();
            for (String id : List.of("exchequerLineAccountAdd", "exchequerLineFundAdd", "exchequerEntryPayeeAdd"))
            {
                var action = button(id);
                assertTrue(action.localToScene(action.getBoundsInLocal()).getMaxX() <= rightEdge + 1,
                        id + " fits the normal-width editor viewport");
            }
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
