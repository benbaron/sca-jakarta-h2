package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.skin.TableColumnHeader;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.service.EventAccountingQueryService.ReviewFact;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class PanelOverflowTest
{
    @TempDir Path directory;
    private Stage stage;
    private UiSessionState session;
    private DatabaseSelectionState oldDatabase;
    private MultiCompanyState oldCompany;
    private java.util.Optional<org.nonprofitbookkeeping.service.AuthenticatedUserSession> oldUser;

    @BeforeEach void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        session = MainWindow.sharedSessionState();
        oldDatabase = session.databaseSelection(); oldCompany = session.multiCompany(); oldUser = session.authenticatedUser();
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            Path db = directory.resolve("overflow");
            session.setDatabaseSelection(new DatabaseSelectionState(db.toString(), List.of(db.toString())));
            session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            UiServiceRegistry.reconnectToDatabase(db);
            stage = new Stage();
            return null;
        });
    }

    @AfterEach void cleanup() throws Exception
    {
        if (session == null) return;
        FxTestSupport.onFx(() -> { stage.close(); return null; });
        Thread.sleep(500);
        FxTestSupport.onFx(() ->
        {
            session.setDatabaseSelection(oldDatabase); session.setMultiCompany(oldCompany);
            oldUser.ifPresentOrElse(session::setAuthenticatedUser, session::clearAuthenticatedUser);
            return null;
        });
        UiServiceRegistry.reconnectToDatabase(Path.of(oldDatabase.activeDatabasePath()));
    }

    @Test void emptyAndPopulatedTablesKeepOneHorizontalOwnerAndNativeVerticalScrolling()
    {
        FxTestSupport.onFx(() ->
        {
            TableView<String> table = new TableView<>();
            table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
            for (int i = 0; i < 5; i++)
            {
                TableColumn<String, String> column = new TableColumn<>("Column " + i);
                column.setPrefWidth(250); column.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue()));
                table.getColumns().add(column);
            }
            BorderPane root = new BorderPane(table); PanelViewport.prepare(root, "table-overflow-regression");
            show(root, 480, 260); layout();
            assertHorizontalAccess(table);
            assertEquals(0, bars(table, Orientation.VERTICAL).size());
            double oldMax = bars(table, Orientation.HORIZONTAL).get(0).getMax();
            table.getColumns().get(4).setVisible(false); layout();
            assertTrue(bars(table, Orientation.HORIZONTAL).get(0).getMax() < oldMax);
            for (int i = 0; i < 40; i++) table.getItems().add("Row " + i);
            layout();
            assertEquals(1, bars(table, Orientation.HORIZONTAL).size());
            assertEquals(1, bars(table, Orientation.VERTICAL).size());
            ScrollBar vertical = bars(table, Orientation.VERTICAL).get(0);
            var vBounds = vertical.localToScene(vertical.getBoundsInLocal());
            var tBounds = table.localToScene(table.getBoundsInLocal());
            assertTrue(vBounds.getMaxX() <= tBounds.getMaxX() + 1 && vBounds.getMaxX() > tBounds.getMaxX() - 35,
                    "Native row scrollbar must stay at the viewport edge");
            assertHorizontalAccess(table);
            table.getItems().clear(); layout();
            assertHorizontalAccess(table);
            table.getColumns().forEach(column -> column.setPrefWidth(40)); layout();
            assertEquals(0, bars(table, Orientation.HORIZONTAL).size(), "No horizontal bar when columns fit");
            return null;
        });
    }

    @Test void settlementDraftHasOneFormOwnerAndBothAxesWhileNmrAndReceivablesScroll()
    {
        FxTestSupport.onFx(() ->
        {
            var panel = new PanelFactory().create(AppPanelId.MERCHANT_SETTLEMENT_ASSISTANT);
            show(panel.root(), 480, 420); panel.root().setStyle("-fx-font-size: 18px;"); layout();
            TabPane tabs = (TabPane) panel.root().lookup("#merchantSettlementWorkPanes");
            ScrollPane form = (ScrollPane) panel.root().lookup("#merchantSettlementFormScroll");
            assertSame(form, tabs.getTabs().get(0).getContent(), "Reuse the existing form rather than adding another ScrollPane");
            assertEquals(1, ownBars(form, Orientation.VERTICAL).size());
            assertEquals(1, ownBars(form, Orientation.HORIZONTAL).size());
            form.setHvalue(form.getHmax()); form.setVvalue(form.getVmax()); layout();
            var clip = form.lookup(".viewport").localToScene(form.lookup(".viewport").getLayoutBounds());
            var content = form.getContent().localToScene(form.getContent().getBoundsInLocal());
            assertTrue(content.getMaxX() <= clip.getMaxX() + 1 && content.getMaxY() <= clip.getMaxY() + 1,
                    "Last form fields must be reachable on both axes");
            assertHorizontalAccess((TableView<?>) panel.root().lookup("#merchantSettlementReceivables"));
            snapshot("settlement-draft");
            tabs.getSelectionModel().select(1); layout();
            assertHorizontalAccess((TableView<?>) panel.root().lookup("#merchantSettlementNmrItems"));
            snapshot("settlement-nmr");
            return null;
        });
    }

    @Test void namedBankingAndReconciliationPanesKeepLocalHorizontalAccess()
    {
        FxTestSupport.onFx(() ->
        {
            var banking = new PanelFactory().create(AppPanelId.BANKING);
            show(banking.root(), 480, 560); banking.root().setStyle("-fx-font-size: 18px;"); layout();
            TabPane bankTabs = (TabPane) banking.root().lookup(".work-pane-tabs");
            for (int index = 0; index < bankTabs.getTabs().size(); index++)
            {
                bankTabs.getSelectionModel().select(index); layout();
                List<ScrollPane> forms = banking.root().lookupAll(".scroll-pane").stream().filter(ScrollPane.class::isInstance)
                        .map(ScrollPane.class::cast).filter(scroll -> scroll.getContent() instanceof javafx.scene.layout.GridPane && scroll.isVisible()).toList();
                assertFalse(forms.isEmpty());
                for (ScrollPane form : forms)
                {
                    if (shown(form) && form.getContent().getLayoutBounds().getWidth() <= form.getViewportBounds().getWidth())
                    {
                        ((javafx.scene.layout.GridPane) form.getContent()).getChildren().stream()
                                .filter(TextInputControl.class::isInstance).map(TextInputControl.class::cast)
                                .findFirst().orElseThrow().setPrefWidth(700);
                        layout();
                    }
                }
                assertTrue(forms.stream().anyMatch(scroll -> !ownBars(scroll, Orientation.HORIZONTAL).isEmpty()),
                        "Each Banking editor needs its own horizontal scrollbar");
            }
            snapshot("banking-editor");
            Label status = (Label) banking.root().lookup("#bankingStatus");
            assertTrue(status.isWrapText());
            var reconciliation = new PanelFactory().create(AppPanelId.RECONCILIATION_RUNS);
            show(reconciliation.root(), 600, 650); layout();
            TabPane workflow = (TabPane) reconciliation.root().lookup("#reconciliationWorkflowTabs");
            workflow.getSelectionModel().select(0); layout();
            SplitPane setup = (SplitPane) reconciliation.root().lookup("#reconciliationSetupSplit");
            assertHorizontalAccess((TableView<?>) setup.getItems().get(1).lookup(".table-view"));
            workflow.getSelectionModel().select(2); layout();
            SplitPane match = (SplitPane) reconciliation.root().lookup("#reconciliationMatchSplit");
            for (Node pane : match.getItems()) assertHorizontalAccess((TableView<?>) pane.lookup(".table-view"));
            snapshot("reconciliation-match");
            return null;
        });
    }

    @Test void eventFactsKeepAttentionVisibleWithAllDetailsAvailableOnDemand()
    {
        FxTestSupport.onFx(() ->
        {
            EventAccountingPanel panel = (EventAccountingPanel) new PanelFactory().create(AppPanelId.EVENT_ACCOUNTING);
            show(panel.root(), 620, 600); layout();
            TitledPane details = (TitledPane) panel.root().lookup("#eventAccountingReviewDetails");
            assertFalse(details.isVisible(), "No empty closeout block before an event is selected");
            List<ReviewFact> facts = List.of(new ReviewFact("Journal linkage", "Linked", "Two canonical transactions."),
                    new ReviewFact("Related bank clearing", "Attention", "One related bank movement remains uncleared."),
                    new ReviewFact("Activity lifecycle", "Active", "Available for new Journal lines."),
                    new ReviewFact("Selected scope", "Read only", "Explicit selected dates and funds."));
            panel.renderReviewFacts(facts); layout();
            assertTrue(details.isVisible()); assertFalse(details.isExpanded());
            Node ancestor = details.getParent();
            while (!(ancestor instanceof ScrollPane)) ancestor = ancestor.getParent();
            ScrollPane header = (ScrollPane) ancestor;
            header.setVvalue(header.getVmax()); layout();
            snapshot("event-facts-compact");
            Node summary = panel.root().lookup("#eventAccountingReviewStatuses");
            assertTrue(summary.lookupAll(".label").stream().map(Label.class::cast)
                    .anyMatch(label -> label.getText().contains("Attention") && label.isVisible()));
            VBox content = (VBox) details.getContent(); assertEquals(4, content.getChildren().size());
            for (int i = 0; i < facts.size(); i++) assertTrue(((Label) content.getChildren().get(i)).getText().contains(facts.get(i).detail()));
            details.setExpanded(true); layout();
            assertTrue(details.isExpanded());
            TabPane views = (TabPane) panel.root().lookup("#eventAccountingWorkPanes");
            views.getSelectionModel().select(0); layout();
            assertHorizontalAccess((TableView<?>) panel.root().lookup("#eventAccountingLinkedSplitsTable"));
            return null;
        });
    }

    @Test void descriptiveTextAndActionButtonsWrapWithoutClipping()
    {
        FxTestSupport.onFx(() ->
        {
            Label status = new Label("A long status message must remain readable when this panel becomes narrow and text is enlarged.");
            Button action = new Button("Review and export bank statement transactions");
            Button unmatch = new Button("Unmatch");
            javafx.scene.layout.HBox crowded = new javafx.scene.layout.HBox(8, new Button("Auto Match"),
                    new Button("Match Selected"), unmatch, new Button("Mark Cleared"), new Button("Save Difference Explanation"));
            VBox root = new VBox(8, status, action, crowded);
            PanelViewport.prepare(root, "text-wrap-regression");
            show(root, 220, 300);
            for (int font : new int[] {13, 18})
            {
                root.setStyle("-fx-font-size: " + font + "px;"); layout();
                assertTrue(status.isWrapText() && action.isWrapText());
                assertTrue(status.getHeight() > 30 && action.getHeight() > 30,
                        "Long text and button captions must occupy multiple lines");
                assertTrue(status.getHeight() >= status.prefHeight(status.getWidth()) - 1);
                assertTrue(action.getHeight() >= action.prefHeight(action.getWidth()) - 1,
                        "Wrapped button text must not be vertically truncated");
                javafx.scene.text.Text word = new javafx.scene.text.Text(unmatch.getText());
                word.setFont(unmatch.getFont());
                assertTrue(unmatch.getWidth() >= word.getLayoutBounds().getWidth() + unmatch.getInsets().getLeft()
                        + unmatch.getInsets().getRight() - 1, "A button must not wrap a single word into individual letters");
            }
            return null;
        });
    }

    private void show(Node root, double width, double height)
    {
        Scene scene = new Scene((Parent) root, width, height);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
        stage.setScene(scene); stage.sizeToScene(); stage.show();
    }
    private void snapshot(String name)
    {
        String output = System.getProperty("overflowPreviewDir");
        if (output == null) return;
        try
        {
            java.nio.file.Files.createDirectories(Path.of(output));
            var pixels = stage.getScene().snapshot(null);
            var image = new java.awt.image.BufferedImage((int) pixels.getWidth(), (int) pixels.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++)
            {
                for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, pixels.getPixelReader().getArgb(x, y));
            }
            javax.imageio.ImageIO.write(image, "png", Path.of(output).resolve(name + ".png").toFile());
        }
        catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
    }

    private void layout() { stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout(); }
    private static List<ScrollBar> bars(Node node, Orientation orientation)
    {
        return node.lookupAll(".scroll-bar").stream().filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast)
                .filter(bar -> shown(bar) && bar.getOrientation() == orientation).toList();
    }
    private static boolean shown(Node node)
    {
        for (Node current = node; current != null; current = current.getParent()) if (!current.isVisible()) return false;
        return node.getBoundsInLocal().getHeight() > 0 && node.getBoundsInLocal().getWidth() > 0;
    }
    private static List<ScrollBar> ownBars(ScrollPane pane, Orientation orientation)
    {
        return bars(pane, orientation).stream().filter(bar -> bar.getParent() == pane).toList();
    }
    private void assertHorizontalAccess(TableView<?> table)
    {
        assertNotNull(table); layout();
        if (table.getVisibleLeafColumns().stream().mapToDouble(TableColumn::getWidth).sum() <= table.getWidth())
        {
            table.getVisibleLeafColumns().get(table.getVisibleLeafColumns().size() - 1).setPrefWidth(table.getWidth() + 200);
            layout();
        }
        List<ScrollBar> bars = bars(table, Orientation.HORIZONTAL);
        assertEquals(1, bars.size(), "Exactly one visible horizontal column scrollbar for " + table.getId());
        bars.get(0).setValue(bars.get(0).getMax()); layout();
        var last = table.getVisibleLeafColumns().get(table.getVisibleLeafColumns().size() - 1);
        var header = table.lookupAll(".column-header").stream().filter(TableColumnHeader.class::isInstance)
                .map(TableColumnHeader.class::cast).filter(item -> item.getTableColumn() == last).findFirst().orElseThrow();
        var h = header.localToScene(header.getBoundsInLocal()); var t = table.localToScene(table.getBoundsInLocal());
        assertTrue(h.getMaxX() <= t.getMaxX() + 2 && h.getMaxX() > t.getMinX(), "The last column must be reachable");
    }
}
