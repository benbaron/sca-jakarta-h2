package org.nonprofitbookkeeping.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.UiThemePreference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeTextContrastFxTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @AfterAll
    static void restoreJavaFxTheme()
    {
        FxTestSupport.onFx(() ->
        {
            Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
            return null;
        });
    }

    @Test
    void computesBlackTextForEveryLightThemeAndWhiteTextForEveryDarkTheme()
    {
        for (UiThemePreference preference : UiThemePreference.values())
        {
            assertComputedForeground(preference,
                    AtlantaFxThemeManager.isDark(preference) ? Color.WHITE : Color.BLACK);
        }
    }

    private void assertComputedForeground(UiThemePreference preference, Color expected)
    {
        FxTestSupport.onFx(() ->
        {
            AtlantaFxThemeManager.apply(preference);
            Label label = new Label("Ordinary label");
            Button button = new Button("Ordinary button");
            Hyperlink hyperlink = new Hyperlink("Ordinary link");
            Text text = new Text("Ordinary text node");
            TextField input = new TextField("Input text");
            Label boundStyleLabel = new Label("Skin-owned style");
            var skinStyle = new javafx.beans.property.SimpleStringProperty("-fx-text-fill: green;");
            boundStyleLabel.styleProperty().bind(skinStyle);
            label.setStyle("-fx-text-fill: green; -fx-font-size: 18px;");
            VBox workspace = new VBox(label, button, hyperlink, text, input, boundStyleLabel);
            WorkspaceTextContrastEnforcer.install(workspace);
            WorkspaceTextContrastEnforcer.apply(
                    workspace,
                    AtlantaFxThemeManager.isDark(preference));
            workspace.getStyleClass().addAll(
                    "production-workspace",
                    AtlantaFxThemeManager.isDark(preference) ? "theme-dark" : "theme-light");
            Scene scene = new Scene(workspace, 320, 180);
            scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/ui/competing-accent.css").toExternalForm());
            workspace.applyCss();

            Label dynamicallyAdded = new Label("Dynamically added label");
            dynamicallyAdded.setStyle(null);
            Text dynamicallyAddedText = new Text("Dynamically added text");
            workspace.getChildren().addAll(dynamicallyAdded, dynamicallyAddedText);
            workspace.applyCss();

            assertEquals(expected, label.getTextFill(), preference + " label");
            assertEquals(expected, boundStyleLabel.getTextFill(), preference + " bound skin style");
            assertEquals(expected, button.getTextFill(), preference + " button");
            assertEquals(expected, hyperlink.getTextFill(), preference + " hyperlink");
            assertEquals(expected, text.getFill(), preference + " text node");
            assertEquals(expected, dynamicallyAdded.getTextFill(), preference + " dynamic label");
            assertEquals(expected, dynamicallyAddedText.getFill(), preference + " dynamic text");
            assertEquals(18.0, label.getFont().getSize(), "Unrelated inline styling survives");
            input.lookupAll(".text").stream().filter(Text.class::isInstance)
                    .map(Text.class::cast).forEach(node ->
                            assertEquals(expected, node.getFill(), preference + " input skin text"));

            WorkspaceTextContrastEnforcer.apply(workspace, !AtlantaFxThemeManager.isDark(preference));
            workspace.applyCss();
            Color switched = expected.equals(Color.BLACK) ? Color.WHITE : Color.BLACK;
            assertEquals(switched, label.getTextFill(), preference + " live theme switch");
            assertEquals(switched, boundStyleLabel.getTextFill(), preference + " bound skin theme switch");
            assertEquals(switched, dynamicallyAddedText.getFill(), preference + " dynamic theme switch");
            return null;
        });
    }
}
