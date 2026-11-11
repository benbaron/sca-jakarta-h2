package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.nonprofitbookkeeping.interchange.bank.*;
import org.nonprofitbookkeeping.interchange.sclx.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.provider.EnumSource;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises production controls through real asynchronous services and temporary H2 authority. */
@ResourceLock("ui-service-registry")
class ImportWorkflowPanelTest
{
    private static final String COMPANY = "IMPORT_TEST";
    private static final Path BANK = Path.of("src/test/resources/data-exchange/bank-statement");
    @TempDir Path directory;
    private UiSessionState session;
    private DatabaseSelectionState oldDatabase;
    private MultiCompanyState oldCompany;
    private Optional<AuthenticatedUserSession> oldUser;
    private Jpa jpa;
    private long userId;
    private Stage stage;
    private ImportPreviewPanel panel;
    private Path selected;
    private final List<String> chooserTitles = new ArrayList<>();
    private final List<List<String>> chooserFilters = new ArrayList<>();

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        session = MainWindow.sharedSessionState();
        oldDatabase = session.databaseSelection(); oldCompany = session.multiCompany(); oldUser = session.authenticatedUser();
        Path database = directory.resolve("import-ui");
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
            UiServiceRegistry.reconnectToDatabase(database);
            return null;
        });
        jpa = new Jpa(database);
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company(); company.setCode(COMPANY); company.setDisplayName("Import workflow fixture");
            company.setDefaultCurrency("USD"); em.persist(company);
            ChartOfAccounts chart = new ChartOfAccounts(); chart.setCompany(company); chart.setName("Empty fixture chart");
            chart.setVersion("1"); chart.setStatus(ChartStatus.ACTIVE); em.persist(chart);
            company.setActiveChartOfAccounts(chart);
            AppUser user = new AppUser(); user.setUsername("import-operator"); user.setDisplayName("Import fixture operator");
            em.persist(user);
            em.getTransaction().commit();
            userId = user.getId();
        }
        FxTestSupport.onFx(() ->
        {
            session.setMultiCompany(new MultiCompanyState(COMPANY, List.of(COMPANY, "DEFAULT")));
            authenticate(ReservedSecurityRole.MANAGER);
            stage = new Stage();
            show(new ImportPreviewPanel(this::choose));
            return null;
        });
    }

    @AfterEach
    void cleanup()
    {
        if (session == null) return;
        FxTestSupport.onFx(() ->
        {
            for (Window window : List.copyOf(Window.getWindows()))
            {
                if (window instanceof Stage open && open != stage && open.getOwner() == stage) open.close();
            }
            stage.close(); DrillThroughCoordinator.configureOpener(ignored -> { });
            session.setDatabaseSelection(oldDatabase); session.setMultiCompany(oldCompany);
            oldUser.ifPresentOrElse(session::setAuthenticatedUser, session::clearAuthenticatedUser);
            return null;
        });
        jpa.close();
        UiServiceRegistry.reconnectToDatabase(Path.of(oldDatabase.activeDatabasePath()));
    }

    @Test
    void chooserCancellationAndMissingTargetsNeverWriteOrStartOperations()
    {
        FxTestSupport.onFx(() ->
        {
            for (String id : List.of("previewBankStatementButton", "previewBankCsvButton", "previewNormalizedBankCsvButton", "saveBankCsvProfileButton"))
            {
                button(id).fire(); assertTrue(status().contains("Select an active configured bank account"));
            }
            assertTrue(chooserTitles.isEmpty());
            button("previewSclxButton").fire(); button("previewCoaCsvButton").fire();
            assertEquals(List.of("Preview SCLX Active Company File", "Preview COA CSV"), chooserTitles);
            assertEquals(List.of("*.sclx", "*.json"), chooserFilters.get(0));
            assertFalse(button("previewSclxButton").isDisabled());
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            ImportPreviewPanel unshown = new ImportPreviewPanel(this::choose);
            new Scene((Parent) unshown.root(), 1100, 800);
            unshown.root().applyCss(); ((Parent) unshown.root()).layout();
            ((Button) unshown.root().lookup("#previewCoaCsvButton")).fire();
            assertTrue(((Label) unshown.root().lookup("#importPreviewStatus")).getText().contains("window is not ready"));
            return null;
        });
        assertEquals(0, count("BankImportBatch")); assertEquals(0, count("Txn"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ofx", "qfx"})
    void bankChooserPreviewConfirmationCommitAndRepeatAreAtomic(String format) throws Exception
    {
        configureBank();
        Path source = BANK.resolve(format.equals("ofx") ? "ofx/valid/ofx2-checking.xml" : "qfx/valid/qfx-xml-header.qfx");
        preview("previewBankStatementButton", source, "Previewed");
        assertEquals(0, count("BankImportBatch"));
        FxTestSupport.onFx(() ->
        {
            assertTrue(table("bankReviewPreviewRows").getItems().size() > 0);
            assertTrue(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            return null;
        });
        confirm("commitPreviewedBankReviewButton", ButtonType.CANCEL);
        assertEquals(0, count("BankImportBatch"));
        confirm("commitPreviewedBankReviewButton", ButtonType.OK);
        await(() -> status().startsWith("Committed durable"));
        assertEquals(1, count("BankImportBatch")); assertTrue(count("BankStatementLine") > 0); assertEquals(0, count("Txn"));
        preview("previewBankStatementButton", source, "Previewed");
        confirm("commitPreviewedBankReviewButton", ButtonType.OK);
        await(() -> status().startsWith("Identical source already committed"));
        assertEquals(1, count("BankImportBatch")); assertEquals(0, count("Txn"));
    }

    @Test
    void csvProfileSaveAndMappedPreviewPreserveSelectedProfileAndOriginalRows() throws Exception
    {
        configureBank();
        preview("saveBankCsvProfileButton", BANK.resolve("csv/valid/mapping-profile-signed.json"), "Saved CSV profile");
        assertEquals(1, count("BankCsvMappingProfile"));
        FxTestSupport.onFx(() ->
        {
            assertNotNull(combo("bankCsvMappingProfile").getValue());
            return null;
        });
        preview("previewBankCsvButton", BANK.resolve("csv/valid/mapped-signed.csv"), "Previewed MAPPED_CSV");
        FxTestSupport.onFx(() ->
        {
            ((TabPane) node("importPreviewResultTabs")).getSelectionModel().select(6);
            panel.root().applyCss(); ((Parent) panel.root()).layout();
            assertTrue(table("bankCsvOriginalPreviewRows").getItems().size() > 0);
            assertTrue(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            return null;
        });
        confirm("commitPreviewedBankReviewButton", ButtonType.OK);
        await(() -> status().startsWith("Committed durable"));
        assertEquals(1, count("BankImportBatch")); assertEquals(0, count("Txn"));
        preview("previewBankCsvButton", BANK.resolve("csv/valid/mapped-signed.csv"), "Previewed");
        confirm("commitPreviewedBankReviewButton", ButtonType.OK);
        await(() -> status().startsWith("Identical source already committed"));
        assertEquals(1, count("BankImportBatch")); assertEquals(0, count("Txn"));
    }

    @Test
    void normalizedCsvUsesItsOwnCommitAndReturnsToCapturedReconciliation() throws Exception
    {
        long account = configureBank();
        Path source = normalized();
        AtomicReference<AppPanelId> destination = new AtomicReference<>();
        FxTestSupport.onFx(() ->
        {
            DrillThroughCoordinator.configureOpener(destination::set);
            DrillThroughCoordinator.openPanelWithContext(AppPanelId.IMPORT_PREVIEW,
                    BankImportNavigationContext.forReconciliation(account, 321L));
            panel.onPanelShown();
            assertTrue(combo("bankReviewConfiguredAccount").isDisabled());
            return null;
        });
        preview("previewNormalizedBankCsvButton", source, "Previewed normalized");
        assertEquals(0, count("BankImportBatch"));
        confirm("commitPreviewedBankReviewButton", ButtonType.CANCEL);
        assertEquals(0, count("BankImportBatch"));
        confirm("commitPreviewedBankReviewButton", ButtonType.OK);
        await(() -> status().startsWith("Committed normalized"));
        assertEquals(AppPanelId.RECONCILIATION_RUNS, destination.get());
        FxTestSupport.onFx(() ->
        {
            assertEquals(321L, BankImportNavigationContext.parseReconciliationReturn(
                    DrillThroughCoordinator.consumeContext(AppPanelId.RECONCILIATION_RUNS)).orElseThrow());
            return null;
        });
        assertEquals(1, count("BankImportBatch")); assertEquals(1, count("BankStatementLine")); assertEquals(0, count("Txn"));
        preview("previewNormalizedBankCsvButton", source, "Previewed normalized");
        confirm("commitPreviewedBankReviewButton", ButtonType.OK);
        await(() -> status().startsWith("Identical source already committed as normalized"));
        assertEquals(1, count("BankImportBatch")); assertEquals(1, count("BankStatementLine")); assertEquals(0, count("Txn"));
    }

    @Test
    void malformedBankAndProfileInputsRestoreControlsWithoutDurableWrites() throws Exception
    {
        configureBank();
        preview("previewBankStatementButton", BANK.resolve("ofx/invalid/malformed.xml"), "Could not preview bank statement");
        Path bad = directory.resolve("bad.json"); Files.writeString(bad, "not JSON");
        preview("saveBankCsvProfileButton", bad, "Could not save bank CSV profile");
        preview("saveBankCsvProfileButton", directory.resolve("missing-profile.json"), "Could not save bank CSV profile");
        preview("previewNormalizedBankCsvButton", bad, "Could not preview normalized");
        FxTestSupport.onFx(() ->
        {
            assertFalse(button("previewBankStatementButton").isDisabled());
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            return null;
        });
        assertEquals(0, count("BankCsvMappingProfile")); assertEquals(0, count("BankImportBatch")); assertEquals(0, count("Txn"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ofx", "mapped", "normalized"})
    void changedBankSourceRejectsCommitAndRequiresFreshPreview(String format) throws Exception
    {
        configureBank();
        String action;
        Path fixture;
        if (format.equals("mapped"))
        {
            preview("saveBankCsvProfileButton", BANK.resolve("csv/valid/mapping-profile-signed.json"), "Saved CSV profile");
            fixture = BANK.resolve("csv/valid/mapped-signed.csv"); action = "previewBankCsvButton";
        }
        else if (format.equals("normalized"))
        {
            fixture = normalized(); action = "previewNormalizedBankCsvButton";
        }
        else
        {
            fixture = BANK.resolve("ofx/valid/ofx2-checking.xml"); action = "previewBankStatementButton";
        }
        Path source = directory.resolve("changed-" + fixture.getFileName());
        Files.copy(fixture, source);
        preview(action, source, "Previewed");
        Files.writeString(source, Files.readString(source) + "\n");
        confirm("commitPreviewedBankReviewButton", ButtonType.OK);
        await(() -> status().contains("rolled back or was rejected"));
        assertEquals(0, count("BankImportBatch")); assertEquals(0, count("BankStatementLine"));
        FxTestSupport.onFx(() ->
        {
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            return null;
        });
    }

    @Test
    void sclxChooserAsyncPreviewRepreviewCancellationAndCommitRetainExactSource() throws Exception
    {
        Path source = sclx();
        preview("previewSclxButton", source, "Previewed SCLX");
        assertEquals(0, count("Txn"));
        int choices = chooserTitles.size();
        FxTestSupport.onFx(() -> { button("repreviewSameSclxButton").fire(); return null; });
        await(() -> status().startsWith("Previewed SCLX"));
        assertEquals(choices, chooserTitles.size());
        FxTestSupport.onFx(() ->
        {
            for (String id : List.of("confirmSclxMappings", "confirmExistingCompanySclxImport", "confirmLegacySclxAllocations"))
            {
                ((CheckBox) node(id)).setSelected(true);
            }
            assertTrue(panel.supportsCommand(AppCommand.SAVE_ACTIVE), status());
            return null;
        });
        confirm("commitPreviewedSclxButton", ButtonType.CANCEL);
        assertEquals(0, count("Txn"));
        Files.writeString(source, Files.readString(source) + "\n");
        confirm("commitPreviewedSclxButton", ButtonType.OK);
        await(() -> status().startsWith("Could not import SCLX"));
        assertEquals(0, count("Txn"));
        FxTestSupport.onFx(() -> { button("repreviewSameSclxButton").fire(); return null; });
        await(() -> status().startsWith("Previewed SCLX"));
        approveSclx();
        confirm("commitPreviewedSclxButton", ButtonType.OK);
        await(() -> status().contains("Imported SCLX") || status().contains("Committed SCLX"));
        assertEquals(1, count("Txn")); assertEquals(2, count("TxnSplit"));
        preview("previewSclxButton", source, "Previewed SCLX");
        approveSclx(); confirm("commitPreviewedSclxButton", ButtonType.OK);
        await(() -> status().startsWith("Imported SCLX"));
        assertEquals(1, count("Txn")); assertEquals(2, count("TxnSplit"));
    }

    @Test
    void sclxFailureCanRetryAndPreviewCancellationDiscardsLateResults() throws Exception
    {
        Path bad = directory.resolve("bad.sclx"); Files.writeString(bad, "invalid JSON");
        preview("previewSclxButton", bad, "Could not preview SCLX");
        Path source = sclx();
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
        AtomicBoolean onFx = new AtomicBoolean(true);
        FxTestSupport.onFx(() ->
        {
            show(new ImportPreviewPanel(new ImportPreviewService(), file ->
            {
                onFx.set(Platform.isFxApplicationThread()); started.countDown();
                try
                {
                    while (release.getCount() > 0)
                    {
                        try
                        {
                            release.await();
                        }
                        catch (InterruptedException ignored)
                        {
                            // Model a reader finishing late; the cancelled Task must discard its result.
                        }
                    }
                    return UiServiceRegistry.sclxImportPreview().preview(file);
                }
                finally
                {
                    finished.countDown();
                }
            }, this::choose));
            selected = source; button("previewSclxButton").fire();
            assertTrue(button("previewCoaCsvButton").isDisabled());
            assertFalse(button("cancelImportPreviewOperationButton").isDisabled());
            assertTrue(((ProgressBar) node("importPreviewOperationProgress")).isVisible());
            return null;
        });
        assertTrue(started.await(10, TimeUnit.SECONDS)); assertFalse(onFx.get());
        try
        {
            FxTestSupport.onFx(() -> { button("cancelImportPreviewOperationButton").fire(); return null; });
            await(() -> status().contains("preview cancelled before commit"));
        }
        finally
        {
            release.countDown();
        }
        assertTrue(finished.await(10, TimeUnit.SECONDS));
        FxTestSupport.onFx(() ->
        {
            assertTrue(status().contains("preview cancelled before commit"));
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            assertFalse(button("previewSclxButton").isDisabled());
            return null;
        });
        assertEquals(0, count("Txn"));
        FxTestSupport.onFx(() -> { show(new ImportPreviewPanel(this::choose)); return null; });
        preview("previewSclxButton", source, "Previewed SCLX");
    }

    @Test
    void switchingBankTargetsClearsIncompatibleProfileAndPreview() throws Exception
    {
        long first = configureBank();
        preview("saveBankCsvProfileButton", BANK.resolve("csv/valid/mapping-profile-signed.json"), "Saved CSV profile");
        long second = configureBank("1001");
        FxTestSupport.onFx(() ->
        {
            @SuppressWarnings("unchecked")
            ComboBox<CompanyBankAccount> accounts = (ComboBox<CompanyBankAccount>) combo("bankReviewConfiguredAccount");
            accounts.show(); accounts.hide();
            assertNotNull(combo("bankCsvMappingProfile").getValue(), "Refreshing the same target retains its active profile");
            accounts.setValue(accounts.getItems().stream().filter(row -> row.getId() == second).findFirst().orElseThrow());
            assertNull(combo("bankCsvMappingProfile").getValue());
            assertTrue(combo("bankCsvMappingProfile").getItems().isEmpty());
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            accounts.setValue(accounts.getItems().stream().filter(row -> row.getId() == first).findFirst().orElseThrow());
            assertEquals(1, combo("bankCsvMappingProfile").getItems().size());
            return null;
        });
    }

    @Test
    void malformedMappedCsvCannotLeaveAnEarlierCommittablePreview() throws Exception
    {
        configureBank();
        preview("saveBankCsvProfileButton", BANK.resolve("csv/valid/mapping-profile-signed.json"), "Saved CSV profile");
        preview("previewBankCsvButton", BANK.resolve("csv/valid/mapped-signed.csv"), "Previewed");
        Path malformed = directory.resolve("broken.csv"); Files.writeString(malformed, "not,the,required,columns\n1,2,3,4\n");
        preview("previewBankCsvButton", malformed, "Could not preview mapped bank CSV");
        FxTestSupport.onFx(() ->
        {
            assertTrue(table("bankReviewPreviewRows").getItems().isEmpty());
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            assertFalse(button("previewBankCsvButton").isDisabled());
            return null;
        });
        assertEquals(0, count("BankImportBatch")); assertEquals(0, count("Txn"));
    }

    @Test
    void viewerMayPreviewButCannotCommitOrSaveProfiles() throws Exception
    {
        configureBank();
        FxTestSupport.onFx(() -> { authenticate(ReservedSecurityRole.VIEWER); return null; });
        preview("previewBankStatementButton", BANK.resolve("ofx/valid/ofx2-checking.xml"), "Previewed");
        FxTestSupport.onFx(() ->
        {
            assertTrue(button("commitPreviewedBankReviewButton").isDisabled());
            assertTrue(button("saveBankCsvProfileButton").isDisabled());
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            button("commitPreviewedBankReviewButton").fire();
            authenticate(ReservedSecurityRole.MANAGER);
            assertTrue(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            return null;
        });
        assertEquals(0, count("BankImportBatch")); assertEquals(0, count("Txn"));
    }

    @Test
    void profileRefreshNeverRestoresAnInactiveProfile() throws Exception
    {
        configureBank();
        preview("saveBankCsvProfileButton", BANK.resolve("csv/valid/mapping-profile-signed.json"), "Saved CSV profile");
        long profile = FxTestSupport.onFx(() -> ((BankCsvMappingProfileService.ProfileSummary) combo("bankCsvMappingProfile").getValue()).id());
        UiServiceRegistry.bankCsvMappingProfiles().setActive(profile, COMPANY, false);
        FxTestSupport.onFx(() ->
        {
            combo("bankCsvMappingProfile").show(); combo("bankCsvMappingProfile").hide();
            assertTrue(combo("bankCsvMappingProfile").getItems().isEmpty());
            assertNull(combo("bankCsvMappingProfile").getValue());
            int requests = chooserTitles.size();
            button("previewBankCsvButton").fire();
            assertEquals(requests, chooserTitles.size());
            assertTrue(status().contains("saved CSV profile"));
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            return null;
        });
        assertEquals(0, count("BankImportBatch")); assertEquals(0, count("Txn"));
    }

    @Test
    void cancellingEachConfiguredFileChooserKeepsExistingFactsAndSelections() throws Exception
    {
        configureBank();
        preview("saveBankCsvProfileButton", BANK.resolve("csv/valid/mapping-profile-signed.json"), "Saved CSV profile");
        FxTestSupport.onFx(() ->
        {
            selected = null;
            int before = chooserTitles.size();
            for (String id : List.of("previewSclxButton", "previewCoaCsvButton", "previewBankStatementButton",
                    "previewNormalizedBankCsvButton", "previewBankCsvButton", "saveBankCsvProfileButton"))
            {
                button(id).fire();
                assertFalse(button("previewSclxButton").isDisabled());
                assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            }
            assertEquals(before + 6, chooserTitles.size());
            assertNotNull(combo("bankCsvMappingProfile").getValue());
            assertTrue(chooserFilters.contains(List.of("*.ofx", "*.qfx")));
            assertTrue(chooserFilters.contains(List.of("*.csv")));
            assertTrue(chooserFilters.contains(List.of("*.json")));
            return null;
        });
        assertEquals(1, count("BankCsvMappingProfile")); assertEquals(0, count("BankImportBatch")); assertEquals(0, count("Txn"));
    }

    @Test
    void sclxMappingChoiceRepreviewsAndCommitsToTheSelectedLedgerAccount() throws Exception
    {
        addCashAccount("1000"); addCashAccount("1001");
        Path source = sclx();
        preview("previewSclxButton", source, "Previewed SCLX");
        FxTestSupport.onFx(() ->
        {
            ((TabPane) node("importPreviewResultTabs")).getSelectionModel().select(3);
            selectCellChoice("sclxPreviewMappings", "1001");
            assertTrue(button("commitPreviewedSclxButton").isDisabled());
            assertTrue(status().contains("Re-preview"));
            button("applySclxMappingsButton").fire();
            return null;
        });
        await(() -> status().startsWith("Previewed SCLX"));
        FxTestSupport.onFx(() ->
        {
            assertTrue(table("sclxPreviewMappings").getItems().stream().map(SclxImportMappingRequirement.class::cast)
                    .anyMatch(mapping -> "1000".equals(mapping.sourceCode()) && "1001".equals(mapping.targetCode())));
            return null;
        });
        approveSclx(); confirm("commitPreviewedSclxButton", ButtonType.OK);
        await(() -> status().startsWith("Imported SCLX"));
        try (var em = jpa.em())
        {
            assertEquals(1L, em.createQuery("select count(s) from TxnSplit s where s.account.code='1001' and s.amountSigned > 0", Long.class).getSingleResult());
        }
    }

    @ParameterizedTest
    @EnumSource(SclxImportConflictChoice.class)
    void sclxConflictWinnerIsExplicitlyRepreviewedBeforeCommit(SclxImportConflictChoice choice) throws Exception
    {
        UUID identity = UUID.randomUUID();
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            Counterparty party = new Counterparty(); party.setPortableId(identity); party.setDisplayName("Target party");
            party.setKind(CounterpartyKind.ORG); party.setCompany(em.createQuery("select c from Company c where c.code=:code", Company.class)
                    .setParameter("code", COMPANY).getSingleResult());
            em.persist(party); em.getTransaction().commit();
        }
        Path source = sclx();
        ObjectMapper json = new ObjectMapper(); ObjectNode document = (ObjectNode) json.readTree(source.toFile());
        document.putObject("extensions").put("version", 1).putObject("scaJakartaH2").putObject("counterparties")
                .putArray("counterparties").addObject()
                .put("counterpartyId", SclxPortableIdentity.counterparty("SOURCE", identity.toString()))
                .put("displayName", "Source party").put("kind", "ORG").put("active", true)
                .putNull("email").putNull("phone").putNull("notes");
        ObjectNode counterparties = (ObjectNode) document.path("extensions").path("scaJakartaH2").path("counterparties");
        counterparties.putArray("merchants"); counterparties.putArray("transactionLineMerchants");
        json.writeValue(source.toFile(), document);
        preview("previewSclxButton", source, "Previewed SCLX");
        FxTestSupport.onFx(() ->
        {
            assertTrue(status().contains("BLOCKED"));
            assertTrue(button("commitPreviewedSclxButton").isDisabled());
            ((TabPane) node("importPreviewResultTabs")).getSelectionModel().select(2);
            panel.root().applyCss(); ((Parent) panel.root()).layout();
            TableView<?> entities = table("sclxPreviewEntities");
            for (int index = 0; index < entities.getItems().size(); index++)
            {
                if (((SclxImportEntityPreview) entities.getItems().get(index)).entityType().equals("COUNTERPARTY")) entities.scrollTo(index);
            }
            selectCellChoice("sclxPreviewEntities", choice);
            button("applySclxMappingsButton").fire();
            return null;
        });
        await(() -> status().startsWith("Previewed SCLX") && !status().contains("BLOCKED"));
        approveSclx(); confirm("commitPreviewedSclxButton", ButtonType.OK);
        await(() -> status().startsWith("Imported SCLX"));
        try (var em = jpa.em())
        {
            Counterparty party = em.createQuery("select p from Counterparty p where p.portableId=:id", Counterparty.class)
                    .setParameter("id", identity).getSingleResult();
            assertEquals(choice == SclxImportConflictChoice.TAKE_SOURCE ? "Source party" : "Target party", party.getDisplayName());
        }
        assertEquals(1, count("Counterparty")); assertEquals(1, count("Txn"));
    }

    @Test
    void sclxMessageDispositionCorrectsOnlyTheUndatedNonpostingRecord() throws Exception
    {
        Path source = sclx(); ObjectMapper json = new ObjectMapper();
        ObjectNode document = (ObjectNode) json.readTree(source.toFile());
        ((com.fasterxml.jackson.databind.node.ArrayNode) document.get("transactions")).addObject()
                .put("transactionId", SclxPortableIdentity.transaction("SOURCE", UUID.randomUUID().toString()))
                .put("status", "ENTERED").put("description", "Nonposting annotation").putArray("lines");
        json.writeValue(source.toFile(), document);
        byte[] original = Files.readAllBytes(source);
        preview("previewSclxButton", source, "Previewed SCLX");
        FxTestSupport.onFx(() ->
        {
            assertTrue(status().contains("BLOCKED"));
            selectCellChoice("importPreviewMessages", SclxImportDisposition.MAKE_SUGGESTED_CORRECTION);
            assertTrue(button("commitPreviewedSclxButton").isDisabled());
            button("applySclxMappingsButton").fire();
            return null;
        });
        await(() -> status().startsWith("Previewed SCLX") && !status().contains("BLOCKED"));
        assertArrayEquals(original, Files.readAllBytes(source), "Preview corrections must never rewrite the selected source");
        assertEquals(0, count("Txn"));
        approveSclx(); confirm("commitPreviewedSclxButton", ButtonType.OK);
        await(() -> status().startsWith("Imported SCLX"));
        assertEquals(1, count("Txn")); assertEquals(2, count("TxnSplit"));
    }

    @Test
    void sclxDatabaseFailureRollsBackAllWritesAndRetainsAnActionableDiagnostic() throws Exception
    {
        Path source = sclx();
        long accounts = count("Account"), funds = count("Fund");
        preview("previewSclxButton", source, "Previewed SCLX");
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("alter table txn add constraint c10_test_reject check (1 = 0)").executeUpdate();
            em.getTransaction().commit();
        }
        approveSclx(); confirm("commitPreviewedSclxButton", ButtonType.OK);
        await(() -> status().startsWith("SCLX import rolled back"));
        assertEquals(accounts, count("Account")); assertEquals(funds, count("Fund"));
        assertEquals(0, count("Txn")); assertEquals(0, count("TxnSplit"));
        FxTestSupport.onFx(() ->
        {
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            assertTrue(table("importPreviewMessages").getItems().toString().contains("SCLX_COMMIT_ROLLED_BACK"));
            return null;
        });
    }

    private void addCashAccount(String code)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = em.createQuery("select c from Company c where c.code=:code", Company.class).setParameter("code", COMPANY).getSingleResult();
            Account cash = new Account(); cash.setChart(company.getActiveChartOfAccounts()); cash.setCode(code); cash.setName("Fixture cash " + code);
            cash.setAccountType(AccountType.ASSET); cash.setAccountFunction(AccountFunction.BANK); cash.setSubtype(AccountSubtype.CASH);
            cash.setNormalBalance(NormalBalance.DEBIT); em.persist(cash); em.getTransaction().commit();
        }
    }

    @SuppressWarnings("unchecked")
    private void selectCellChoice(String tableId, Object choice)
    {
        TableView<?> table = table(tableId);
        table.scrollToColumnIndex(table.getColumns().size() - 1);
        panel.root().applyCss(); ((Parent) panel.root()).layout();
        ComboBox<Object> selector = table.lookupAll(".combo-box").stream().filter(ComboBox.class::isInstance)
                .map(node -> (ComboBox<Object>) node).filter(combo -> combo.getItems().contains(choice) && !combo.isDisabled())
                .findFirst().orElseThrow(() -> new AssertionError("Missing visible cell choice " + choice + " in " + tableId));
        selector.setValue(choice);
    }

    private void approveSclx()
    {
        FxTestSupport.onFx(() ->
        {
            for (String id : List.of("confirmSclxMappings", "confirmExistingCompanySclxImport", "confirmLegacySclxAllocations"))
            {
                ((CheckBox) node(id)).setSelected(true);
            }
            assertTrue(panel.supportsCommand(AppCommand.SAVE_ACTIVE), status());
            return null;
        });
    }

    @Test
    void coaRejectedRowsAreVisibleAndMissingSourceClearsAnEarlierPreview() throws Exception
    {
        Path csv = directory.resolve("mixed-coa.csv");
        Files.writeString(csv, "code,name,account_type,normal_balance,parent_code\n8810,Valid fixture asset,ASSET,DEBIT,\n8811,Rejected fixture,UNSUPPORTED,DEBIT,\n");
        long before = count("Account");
        preview("previewCoaCsvButton", csv, "Previewed");
        FxTestSupport.onFx(() ->
        {
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE), "An invalid account type blocks the batch");
            assertTrue(table("importPreviewMessages").getItems().toString().contains("UNSUPPORTED"));
            return null;
        });
        assertEquals(before, count("Account"));
        Files.writeString(csv, "code,name,account_type,normal_balance,parent_code\n8810,Valid fixture asset,ASSET,DEBIT,\n8811,,ASSET,DEBIT,\n");
        preview("previewCoaCsvButton", csv, "Previewed");
        FxTestSupport.onFx(() ->
        {
            TabPane rows = (TabPane) node("importPreviewCoaWorkPanes");
            assertEquals(1, CompanyTableStateBinder.findTables(rows.getTabs().get(0).getContent()).get(0).getItems().size());
            rows.getSelectionModel().select(1);
            panel.root().applyCss(); ((Parent) panel.root()).layout();
            TableView<?> rejected = CompanyTableStateBinder.findTables(rows.getTabs().get(1).getContent()).get(0);
            assertEquals(1, rejected.getItems().size());
            assertEquals("3", rejected.getColumns().get(0).getCellData(0));
            assertTrue(rejected.getColumns().get(1).getCellData(0).toString().contains("8811"));
            assertTrue(rejected.getColumns().get(2).getCellData(0).toString().contains("Missing required value for column: name"));
            return null;
        });
        assertEquals(before, count("Account"));
        confirm("commitAcceptedCoaRowsButton", ButtonType.OK);
        await(() -> status().startsWith("Committed accepted COA batch"));
        assertEquals(before + 1, count("Account")); assertEquals(0, count("Txn"));
        try (var em = jpa.em())
        {
            assertEquals(0L, em.createQuery("select count(a) from Account a where a.code='8811'", Long.class).getSingleResult());
        }
        preview("previewCoaCsvButton", directory.resolve("disappeared.csv"), "Could not preview COA CSV");
        FxTestSupport.onFx(() ->
        {
            TabPane rows = (TabPane) node("importPreviewCoaWorkPanes");
            for (Tab tab : rows.getTabs())
            {
                assertTrue(CompanyTableStateBinder.findTables(tab.getContent()).get(0).getItems().isEmpty());
            }
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            return null;
        });
        assertEquals(before + 1, count("Account"));
    }

    @Test
    void coaChangedSourceIsRejectedAndFreshPreviewCanCommit() throws Exception
    {
        Path csv = directory.resolve("changing-coa.csv");
        Files.writeString(csv, "code,name,account_type,normal_balance,parent_code\n8820,Reviewed fixture asset,ASSET,DEBIT,\n");
        long before = count("Account");
        preview("previewCoaCsvButton", csv, "Previewed");
        Files.writeString(csv, Files.readString(csv) + "\n");
        confirm("commitAcceptedCoaRowsButton", ButtonType.OK);
        await(() -> status().startsWith("COA CSV commit did not change data"));
        assertEquals(before, count("Account")); assertEquals(0, count("Txn"));
        preview("previewCoaCsvButton", csv, "Previewed");
        confirm("commitAcceptedCoaRowsButton", ButtonType.OK);
        await(() -> status().startsWith("Committed accepted COA batch"));
        assertEquals(before + 1, count("Account")); assertEquals(0, count("Txn"));
    }

    private File choose(FileChooser chooser, Window owner)
    {
        assertSame(stage, owner);
        chooserTitles.add(chooser.getTitle());
        chooserFilters.add(List.copyOf(chooser.getExtensionFilters().get(0).getExtensions()));
        return selected == null ? null : selected.toFile();
    }

    private void show(ImportPreviewPanel value)
    {
        panel = value;
        stage.setScene(new Scene((Parent) panel.root(), 1100, 800)); stage.show();
        panel.root().applyCss(); ((Parent) panel.root()).layout();
    }

    private long configureBank()
    {
        return configureBank("1000");
    }

    private long configureBank(String code)
    {
        long ledger;
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = em.createQuery("select c from Company c where c.code=:code", Company.class).setParameter("code", COMPANY).getSingleResult();
            Account cash = new Account(); cash.setChart(company.getActiveChartOfAccounts()); cash.setCode(code); cash.setName("Fixture checking");
            cash.setAccountType(AccountType.ASSET); cash.setAccountFunction(AccountFunction.BANK); cash.setSubtype(AccountSubtype.CASH);
            cash.setNormalBalance(NormalBalance.DEBIT); em.persist(cash); em.getTransaction().commit(); ledger = cash.getId();
        }
        BankConfigurationService service = new BankConfigurationService(jpa);
        Bank bank = service.createBank(new BankCommand(COMPANY, "Fixture bank " + code, "999000111", null, null, null, null, null, null, true));
        return service.createBankAccount(new BankAccountCommand(COMPANY, bank.getId(), ledger, "****4321", "Fixture checking " + code,
                LocalDate.of(2026, 1, 1), BigDecimal.ZERO, BankingDataFormat.OFX, "999000111", "FICTIONAL-4321", null, true)).getId();
    }

    private Path normalized() throws Exception
    {
        Path file = directory.resolve("normalized.csv");
        Files.writeString(file, String.join("\n", Files.readAllLines(BANK.resolve("csv/valid/normalized-round-trip.csv")).subList(0, 2)) + "\n");
        return file;
    }

    private Path sclx() throws Exception
    {
        String cash = SclxPortableIdentity.account("SOURCE", "1000"), income = SclxPortableIdentity.account("SOURCE", "4000");
        String fund = SclxPortableIdentity.fund("SOURCE", "GENERAL");
        String txn = SclxPortableIdentity.transaction("SOURCE", "11111111-2222-3333-4444-555555555555");
        Path file = directory.resolve("source.sclx");
        Files.writeString(file, """
                {"format":"SCLX","version":"1.3","exportedAt":"2026-07-31T12:00:00Z",
                 "organization":{"organizationId":"%s","code":"SOURCE","name":"Fixture source","baseCurrency":"USD","fiscalYearStart":"2026-01-01"},
                 "chartOfAccounts":[
                  {"accountId":"%s","code":"1000","name":"Cash","type":"BANK","subtype":"CASH","increaseSide":"DEBIT","currency":"USD","openingBalance":"0.00","posting":true,"active":true},
                  {"accountId":"%s","code":"4000","name":"Income","type":"INCOME","increaseSide":"CREDIT","currency":"USD","openingBalance":"0.00","posting":true,"active":true}],
                 "funds":[{"fundId":"%s","code":"GENERAL","name":"General","type":"UNRESTRICTED","active":true}],
                 "transactions":[{"transactionId":"%s","transactionDate":"2026-07-04","status":"ENTERED","description":"Fixture income","lines":[
                  {"lineId":"%s","accountId":"%s","fundId":"%s","debit":"55.00","credit":"0.00"},
                  {"lineId":"%s","accountId":"%s","fundId":"%s","debit":"0.00","credit":"55.00"}]}]}
                """.formatted(SclxPortableIdentity.organization("SOURCE"), cash, income, fund, txn,
                SclxPortableIdentity.transactionLine(txn, 1), cash, fund, SclxPortableIdentity.transactionLine(txn, 2), income, fund));
        return file;
    }

    private void preview(String button, Path source, String expected) throws Exception
    {
        FxTestSupport.onFx(() -> { selected = source; button(button).fire(); return null; });
        await(() -> status().startsWith(expected));
    }

    private void confirm(String button, ButtonType choice)
    {
        FxTestSupport.onFx(() ->
        {
            assertFalse(button(button).isDisabled(), status());
            Platform.runLater(() -> List.copyOf(Window.getWindows()).stream().filter(Window::isShowing).filter(window -> window != stage)
                    .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                    .forEach(dialog -> ((Button) dialog.lookupButton(choice)).fire()));
            button(button).fire();
            if (choice == ButtonType.OK)
            {
                assertTrue(button("cancelImportPreviewOperationButton").isDisabled(), "Durable commit cannot be cancelled");
            }
            return null;
        });
    }

    private void await(BooleanSupplier condition) throws Exception
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline)
        {
            if (FxTestSupport.onFx(condition::getAsBoolean)) return;
            Thread.sleep(25);
        }
        fail("UI did not reach expected state: " + FxTestSupport.onFx(() -> status() + "; " + table("importPreviewMessages").getItems()));
    }

    private void authenticate(ReservedSecurityRole role)
    {
        var now = java.time.Instant.now();
        session.setAuthenticatedUser(new AuthenticatedUserSession(userId, "import-operator", "Import operator", COMPANY,
                Set.of(role), now, now));
    }

    private long count(String entity)
    {
        try (var em = jpa.em())
        {
            return em.createQuery("select count(x) from " + entity + " x", Long.class).getSingleResult();
        }
    }
    private Node node(String id)
    {
        return Objects.requireNonNull(panel.root().lookup("#" + id), id);
    }
    private Button button(String id)
    {
        return (Button) node(id);
    }
    private ComboBox<?> combo(String id)
    {
        return (ComboBox<?>) node(id);
    }
    private TableView<?> table(String id)
    {
        return (TableView<?>) node(id);
    }
    private String status()
    {
        return ((Label) node("importPreviewStatus")).getText();
    }
}
