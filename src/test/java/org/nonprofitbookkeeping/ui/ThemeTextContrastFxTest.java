package org.nonprofitbookkeeping.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
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
            VBox workspace = new VBox(label, button, hyperlink, text);
            workspace.getStyleClass().addAll(
                    "production-workspace",
                    AtlantaFxThemeManager.isDark(preference) ? "theme-dark" : "theme-light");
            Scene scene = new Scene(workspace, 320, 180);
            scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
            workspace.applyCss();

            assertEquals(expected, label.getTextFill(), preference + " label");
            assertEquals(expected, button.getTextFill(), preference + " button");
            assertEquals(expected, hyperlink.getTextFill(), preference + " hyperlink");
            assertEquals(expected, text.getFill(), preference + " text node");
            return null;
        });
    }
}
