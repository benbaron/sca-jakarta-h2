package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.stage.Window;
import javafx.geometry.Orientation;
import java.util.concurrent.TimeUnit;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class AssetAcquisitionPanelTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private Stage stage;
    private AssetsRegisterPanel panel;
    private AssetAcquisitionTestFixture fixture;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        var session = MainWindow.sharedSessionState();
        previousDatabase = session.databaseSelection();
        previousCompany = session.multiCompany();
        previousUser = session.authenticatedUser();
        Path database = directory.resolve("acquisitions");
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);
        try (Jpa jpa = new Jpa(database))
        {
            fixture = AssetAcquisitionTestFixture.seed(jpa);
        }
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
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
        Thread.sleep(600);
        FxTestSupport.onFx(() ->
        {
            var session = MainWindow.sharedSessionState();
            session.setDatabaseSelection(previousDatabase);
            session.setMultiCompany(previousCompany);
            if (previousUser.isPresent())
            {
                session.setAuthenticatedUser(previousUser.get());
            }
            else
            {
                session.clearAuthenticatedUser();
            }
            DrillThroughCoordinator.configureOpener(null);
            return null;
        });
        UiServiceRegistry.reconnectToDatabase(Path.of(previousDatabase.activeDatabasePath()));
    }

    private void show()
    {
        FxTestSupport.onFx(() ->
        {
            panel = new AssetsRegisterPanel();
            CompanyTableStateBinder.applyProductionPanel(panel.root(), AppPanelId.ASSETS_REGISTER);
            stage = new Stage();
            VBox wrapper = new VBox(panel.root()); VBox.setVgrow(panel.root(), Priority.ALWAYS);
            stage.setScene(new Scene(wrapper, 950, 700)); stage.show(); return null;
        });
        await(() -> !button("assetRegisterSave").isDisabled());
    }

    @Test
    void sharedSavePreviewCancelAcceptancePrecisionRetryAndJournalUseCanonicalService()
    {
        show();
        FxTestSupport.onFx(() ->
        {
            assertNull(combo("assetAcquisitionSource").getValue(), "Acquisition route must be deliberate");
            fill("Equipment", "1000.1234", AssetAcquisitionSource.NEW_PURCHASE);
            assertTrue(panel.hasUnsavedChanges());
            panel.onSave();
            assertTrue(button("assetRegisterSave").isDisabled());
            assertTrue(panel.commandCapabilities().isEmpty());
            panel.onSave(); // a busy retry cannot launch another operation
            return null;
        });
        confirm(ButtonType.CANCEL);
        await(() -> !button("assetRegisterSave").isDisabled());
        FxTestSupport.onFx(() ->
        {
            assertTrue(panel.hasUnsavedChanges()); assertEquals("Equipment", text("assetName").getText());
            assertEquals(0, UiServiceRegistry.fixedAssets().listAssets("DEFAULT").size());
            button("assetRegisterSave").fire(); return null;
        });
        confirm(ButtonType.OK);
        await(() -> panel.commandCapabilities().contains(AppCommand.SAVE_ACTIVE) && table().getItems().size() == 1);
        FxTestSupport.onFx(() ->
        {
            assertFalse(panel.hasUnsavedChanges());
            var saved = table().getItems().get(0);
            assertEquals("1000.1234", saved.acquisitionCost().toPlainString());
            assertEquals(AssetAcquisitionSource.NEW_PURCHASE, saved.acquisition().source());
            text("assetAcquisitionCost").requestFocus(); return null;
        });
        await(() -> text("assetAcquisitionCost").isFocused());
        FxTestSupport.onFx(() ->
        {
            assertEquals("1000.1234", text("assetAcquisitionCost").getText());
            text("assetName").requestFocus(); return null;
        });
        await(() -> text("assetName").isFocused());
        FxTestSupport.onFx(() ->
        {
            assertFalse(panel.hasUnsavedChanges(), "Display formatting cannot round retained command precision");
            button("assetAcquisitionJournal").fire();
            assertEquals(table().getItems().get(0).acquisition().transactionId(),
                    JournalWorkspacePanel.transactionIdFromContext(DrillThroughCoordinator.consumeContext(AppPanelId.JOURNAL_PANE)));
            panel.onSave(); return null;
        });
        await(() -> panel.commandCapabilities().contains(AppCommand.SAVE_ACTIVE));
        FxTestSupport.onFx(() ->
        {
            assertEquals(1, UiServiceRegistry.fixedAssets().listAssets("DEFAULT").size());
            assertEquals("1000.1234", table().getItems().get(0).acquisitionCost().toPlainString());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(button("assetRegisterSave").isDisabled()); assertTrue(button("assetAcquisitionUnlink").isDisabled());
            assertTrue(text("assetName").isDisabled()); assertFalse(button("assetAcquisitionJournal").isDisabled());
            return null;
        });
        try (Jpa jpa = new Jpa(directory.resolve("acquisitions")); var em = jpa.em())
        {
            assertEquals(2L, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
        }
    }

    @Test
    void existingInvoiceAndRegisterOnlyRoutesPreserveAccountingAndFailedDrafts()
    {
        try (Jpa jpa = new Jpa(directory.resolve("acquisitions")))
        {
            new TransactionEntryService(jpa).enter(new TransactionCommand(AssetAcquisitionTestFixture.DATE, null, "Shared invoice", null,
                    List.of(FundTransferTestFixture.line(fixture.asset(), fixture.base().source(), "1000", "0"),
                            FundTransferTestFixture.line(fixture.base().bank(), fixture.base().source(), "0", "1000"))));
        }
        show();
        FxTestSupport.onFx(() ->
        {
            fill("Too much", "1001", AssetAcquisitionSource.EXISTING_ENTRY);
            combo("assetAcquisitionLine").getSelectionModel().select(0);
            panel.onSave(); return null;
        });
        await(() -> !button("assetRegisterSave").isDisabled() && status().contains("remaining"));
        FxTestSupport.onFx(() ->
        {
            assertTrue(panel.hasUnsavedChanges()); assertEquals("Too much", text("assetName").getText());
            text("assetName").setText("Shared asset"); text("assetAcquisitionCost").setText("600");
            panel.onSave(); return null;
        });
        confirm(ButtonType.OK);
        await(() -> !button("assetRegisterSave").isDisabled() && table().getItems().size() == 1);
        FxTestSupport.onFx(() ->
        {
            assertEquals("400.0000", table().getItems().get(0).acquisition().remaining().toPlainString());
            panel.onNew(); fill("Opening register", "800", AssetAcquisitionSource.REGISTER_ONLY);
            panel.onSave(); return null;
        });
        confirm(ButtonType.OK);
        await(() -> !button("assetRegisterSave").isDisabled() && table().getItems().size() == 2);
        try (Jpa jpa = new Jpa(directory.resolve("acquisitions")); var em = jpa.em())
        {
            assertEquals(2L, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
        }
    }

    @Test
    void laptopAndLargeTextRetainIndependentTableEditorAndTwoAxisReach()
    {
        try (Jpa jpa = new Jpa(directory.resolve("acquisitions")))
        {
            fixture.service(jpa).create(fixture.command("Retained equipment for layout", "1000"));
        }
        show();
        // Async data completion precedes the TableView skin's first layout pulse on CI.
        await(() -> table().lookupAll(".scroll-bar").stream().anyMatch(n -> n instanceof ScrollBar b
                && b.isVisible() && b.getOrientation() == Orientation.HORIZONTAL && b.getMax() > b.getMin()));
        FxTestSupport.onFx(() ->
        {
            assertEquals("Retained equipment for layout", table().getColumns().get(0).getCellObservableValue(0).getValue());
            assertFalse(table().getColumns().get(4).getCellObservableValue(0).getValue().toString().isBlank());
            assertTrue(table().lookupAll(".scroll-bar").stream().anyMatch(n -> n instanceof ScrollBar b
                    && b.isVisible() && b.getOrientation() == Orientation.HORIZONTAL && b.getMax() > b.getMin()));
            snapshot("laptop");
            var laptopEditor = (ScrollPane)panel.root().lookup("#assetRegisterEditorScroll");
            assertTrue(laptopEditor.getViewportBounds().getWidth() > 800);
            assertTrue(laptopEditor.getContent().getLayoutBounds().getHeight() > laptopEditor.getViewportBounds().getHeight());
            stage.setWidth(520); stage.setHeight(700); panel.root().setStyle("-fx-font-size: 26px;");
            var split = (SplitPane)panel.root().lookup("#assetRegisterWorkspaceSplit");
            split.setDividerPositions(.7); panel.root().applyCss(); ((javafx.scene.Parent)panel.root()).layout(); return null;
        });
        await(() ->
        {
            var editor = (ScrollPane)panel.root().lookup("#assetRegisterEditorScroll");
            return editor.getContent().getLayoutBounds().getHeight() > editor.getViewportBounds().getHeight()
                    && editor.getContent().getLayoutBounds().getWidth() > editor.getViewportBounds().getWidth();
        });
        FxTestSupport.onFx(() ->
        {
            var actions = (ScrollPane)panel.root().lookup("#assetRegisterActionsScroll");
            assertTrue(actions.getHeight() <= 104.1);
            assertTrue(actions.getContent().getLayoutBounds().getHeight() > actions.getViewportBounds().getHeight());
            actions.setVvalue(1); actions.layout();
            var split = (SplitPane)panel.root().lookup("#assetRegisterWorkspaceSplit");
            double before = split.getDividerPositions()[0]; split.setDividerPositions(.4);
            ((javafx.scene.Parent)panel.root()).layout(); assertTrue(Math.abs(before - split.getDividerPositions()[0]) > .15, "Divider must move: " + before + " -> " + split.getDividerPositions()[0]);
            return null;
        });
        FxTestSupport.onFx(() ->
        {
            var editor = (ScrollPane)panel.root().lookup("#assetRegisterEditorScroll");
            assertEquals(2, editor.lookupAll(".scroll-bar").stream().filter(n -> n instanceof ScrollBar b && b.isVisible()).count());
            editor.setHvalue(1); editor.setVvalue(1); ((javafx.scene.Parent)panel.root()).layout();
            var form = editor.getContent();
            assertTrue(form.localToScene(form.getBoundsInLocal()).getMinY() < editor.localToScene(editor.getBoundsInLocal()).getMinY());
            assertTrue(form.localToScene(form.getBoundsInLocal()).getMinX() < editor.localToScene(editor.getBoundsInLocal()).getMinX());
            snapshot("narrow-scrolled");
            assertTrue(table().getColumns().stream().allMatch(c -> c.isResizable() && c.isReorderable() && c.isSortable()));
            return null;
        });
    }

    private void snapshot(String name)
    {
        String destination = System.getProperty("assetAcquisitionSnapshots");
        if (destination == null) { return; }
        try
        {
            var pixels = stage.getScene().snapshot(null);
            var image = new java.awt.image.BufferedImage((int)pixels.getWidth(), (int)pixels.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++)
            {
                for (int x = 0; x < image.getWidth(); x++) { image.setRGB(x, y, pixels.getPixelReader().getArgb(x, y)); }
            }
            javax.imageio.ImageIO.write(image, "png", Path.of(destination, "p26-s1-" + name + ".png").toFile());
        }
        catch (java.io.IOException ex) { throw new AssertionError(ex); }
    }

    private void fill(String name, String cost, AssetAcquisitionSource source)
    {
        text("assetName").setText(name); text("assetAcquisitionCost").setText(cost);
        select("assetAccount", fixture.asset()); select("assetAccumulatedAccount", fixture.accumulated());
        select("assetExpenseAccount", fixture.base().expense()); select("assetFund", fixture.base().source());
        ((DatePicker)panel.root().lookup("#assetAcquisitionDate")).setValue(AssetAcquisitionTestFixture.DATE);
        combo("assetAcquisitionSource").setValue(source);
        if (source == AssetAcquisitionSource.NEW_PURCHASE) { select("assetAcquisitionBank", fixture.base().bank()); }
    }

    private void select(String id, long value)
    {
        ComboBox<Object> combo = combo(id);
        combo.setValue(combo.getItems().stream().filter(item -> item instanceof Account account ? account.getId() == value
                : item instanceof Fund fund && fund.getId() == value).findFirst().orElseThrow());
    }

    private void confirm(ButtonType choice)
    {
        await(() -> Window.getWindows().stream().anyMatch(w -> w.isShowing() && w.getScene() != null
                && w.getScene().getRoot() instanceof DialogPane p && "assetAcquisitionConfirmation".equals(p.getId())));
        FxTestSupport.onFx(() ->
        {
            var dialog = (DialogPane)Window.getWindows().stream().filter(w -> w.isShowing() && w.getScene() != null
                    && w.getScene().getRoot() instanceof DialogPane p && "assetAcquisitionConfirmation".equals(p.getId())).findFirst().orElseThrow().getScene().getRoot();
            ((Button)dialog.lookupButton(choice)).fire(); return null;
        });
    }

    private void await(java.util.function.BooleanSupplier condition)
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline)
        {
            if (FxTestSupport.onFx(condition::getAsBoolean)) { return; }
            try { Thread.sleep(60); } catch (InterruptedException ex) { throw new AssertionError(ex); }
        }
        fail("Timed out: " + FxTestSupport.onFx(() -> {
            var editor = (ScrollPane)panel.root().lookup("#assetRegisterEditorScroll");
            return status() + "; root=" + panel.root().getLayoutBounds() + "; viewport=" + editor.getViewportBounds()
                    + "; content=" + editor.getContent().getLayoutBounds();
        }));
    }

    private String status() { return ((Label)panel.root().lookup("#assetRegisterStatus")).getText(); }
    private Button button(String id) { return (Button)panel.root().lookup("#" + id); }
    private TextField text(String id) { return (TextField)panel.root().lookup("#" + id); }
    @SuppressWarnings("unchecked") private ComboBox<Object> combo(String id) { return (ComboBox<Object>)panel.root().lookup("#" + id); }
    @SuppressWarnings("unchecked") private TableView<FixedAssetView> table() { return (TableView<FixedAssetView>)panel.root().lookup("#assetRegisterTable"); }
}
