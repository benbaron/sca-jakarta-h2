package org.nonprofitbookkeeping.ui;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class FullTextTooltipInstallerTest
{
    @Test
    public void labeledControlsReceiveFullTextTooltips()
    {
        FxTestSupport.onFx(() -> {
            Button button = new Button("Save Current Asset Record");

            FullTextTooltipInstaller.install(button);

            assertEquals("Save Current Asset Record", button.getTooltip().getText());

            button.setText("Save Current Asset Record Now");
            assertEquals("Save Current Asset Record Now", button.getTooltip().getText());
            return null;
        });
    }

    @Test
    public void textInputControlsDoNotReceiveFullTextTooltips()
    {
        FxTestSupport.onFx(() -> {
            TextField textField = new TextField("Do not expose typed text as a tooltip");

            FullTextTooltipInstaller.install(textField);

            assertNull(textField.getTooltip());
            return null;
        });
    }

    @Test
    public void nestedControlsAddedAfterInstallReceiveTooltips()
    {
        FxTestSupport.onFx(() -> {
            VBox root = new VBox();
            FullTextTooltipInstaller.install(root);

            Label label = new Label("Late-added navigation label");
            root.getChildren().add(label);

            assertEquals("Late-added navigation label", label.getTooltip().getText());
            return null;
        });
    }

    @Test
    public void choiceControlsUseTheirDisplayedSelectionText()
    {
        FxTestSupport.onFx(() -> {
            ComboBox<String> comboBox = new ComboBox<>();
            comboBox.getItems().addAll("January 2026", "February 2026");
            comboBox.getSelectionModel().select("February 2026");

            FullTextTooltipInstaller.install(comboBox);

            assertEquals("February 2026", comboBox.getTooltip().getText());
            return null;
        });
    }

    @Test
    public void existingCustomTooltipsAreNotReplaced()
    {
        FxTestSupport.onFx(() -> {
            Button button = new Button("Visible text");
            Tooltip custom = new Tooltip("Custom help text");
            button.setTooltip(custom);

            FullTextTooltipInstaller.install(button);
            FullTextTooltipInstaller.refreshForTests(button);

            assertSame(custom, button.getTooltip());
            assertEquals("Custom help text", button.getTooltip().getText());
            return null;
        });
    }
}
