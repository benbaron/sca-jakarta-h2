package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationPaneInteractionTest
{
    @Test
    void navigationOpensDestinationWithoutInspectorSideEffect()
    {
        FxTestSupport.onFx(() ->
        {
            AtomicReference<AppPanelId> opened = new AtomicReference<>();
            NavigationPane navigation = new NavigationPane(opened::set);
            ScrollPane scroll = (ScrollPane) navigation.getChildren().get(1);
            var content = (javafx.scene.layout.VBox) scroll.getContent();
            Button diagnostics = content.getChildren().stream().filter(Button.class::isInstance)
                    .map(Button.class::cast).filter(button -> button.getText().equals("Diagnostics"))
                    .findFirst().orElseThrow();
            diagnostics.fire();
            assertEquals(AppPanelId.DIAGNOSTICS, opened.get());
            assertTrue(diagnostics.getStyleClass().contains("navigation-item-selected"));
            assertNull(diagnostics.getOnMouseClicked(), "No right-click inspector handler remains");
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, scroll.getHbarPolicy());
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, scroll.getVbarPolicy());
            return null;
        });
    }
}
