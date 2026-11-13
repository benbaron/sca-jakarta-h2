package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SclxExportFailureDialogTest
{
    @Test
    void realFailureDialogPreservesSelectableWrappedDetailsAtNarrowAndNormalWidths() throws Exception
    {
        FxTestSupport.initToolkitOrSkip();
        String message = "bank account ledger account is outside the selected company's active chart: 1000. "
                + "Configured bank account 'Historical checking' uses Legacy cash from chart 'Original chart'; "
                + "the selected chart is 'Current chart'. Review Banking / Bank accounts.\n"
                + "Long account and chart context remains available for review. ".repeat(100)
                + "END OF COMPLETE DIAGNOSIS";
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            Platform.runLater(() -> new JavaFxSclxExportDialogs().showFailure(null, message));
            return null;
        });
        try
        {
            Stage stage = FxTestSupport.onFx(SclxExportFailureDialogTest::failureStage);
            for (double width : new double[]{760, 430})
            {
                FxTestSupport.onFx(() ->
                {
                    stage.setWidth(width);
                    stage.setHeight(340);
                    return null;
                });
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
                while (!FxTestSupport.onFx(() -> Math.abs(stage.getScene().getWidth() - width) < 60))
                {
                    assertTrue(System.nanoTime() < deadline, "The shown dialog must actually resize before checking its viewport");
                    Thread.sleep(25);
                }
                FxTestSupport.onFx(() ->
                {
                    assertTrue(stage.isResizable());
                    DialogPane pane = (DialogPane) stage.getScene().getRoot();
                    pane.applyCss();
                    pane.layout();
                    TextArea details = (TextArea) pane.lookup("#sclxExportFailureDetails");
                    assertNotNull(details, "The actual failure must expose complete selectable text, not an ellipsized label");
                    assertEquals(message, details.getText());
                    assertFalse(details.isEditable());
                    assertTrue(details.isWrapText());
                    var bounds = details.localToScene(details.getBoundsInLocal());
                    assertTrue(bounds.getMinX() >= 0);
                    assertTrue(bounds.getMaxX() <= stage.getScene().getWidth() + 1);
                    assertTrue(details.lookupAll(".scroll-bar").stream()
                            .filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast)
                            .anyMatch(bar -> bar.getOrientation() == Orientation.VERTICAL && bar.isVisible()));
                    details.selectAll();
                    assertEquals(message, details.getSelectedText());
                    return null;
                });
            }
        }
        finally
        {
            FxTestSupport.onFx(() ->
            {
                Window.getWindows().stream().filter(Stage.class::isInstance).map(Stage.class::cast)
                        .filter(stage -> "SCLX Export Failed".equals(stage.getTitle())).toList().forEach(Stage::hide);
                return null;
            });
        }
    }

    private static Stage failureStage()
    {
        return Window.getWindows().stream().filter(Stage.class::isInstance).map(Stage.class::cast)
                .filter(stage -> "SCLX Export Failed".equals(stage.getTitle())).findFirst().orElseThrow();
    }
}
