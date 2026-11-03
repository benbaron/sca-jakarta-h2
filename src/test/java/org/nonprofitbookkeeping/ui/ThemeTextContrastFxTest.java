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
    void computesReadableTextForWhiteAndDarkSurfacesUnderEveryTheme()
    {
        for (UiThemePreference preference : UiThemePreference.values())
        {
            assertComputedForeground(preference, Color.BLACK);
            assertComputedForeground(preference, Color.WHITE);
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
            input.setStyle("-fx-background-color: "
                    + (expected.equals(Color.BLACK) ? "white" : "black") + ";");
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
            workspace.setStyle("-fx-background-color: "
                    + (expected.equals(Color.BLACK) ? "white" : "black") + ";");
            Scene scene = new Scene(workspace, 320, 180);
            scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/ui/competing-accent.css").toExternalForm());
            settle(workspace);

            Label dynamicallyAdded = new Label("Dynamically added label");
            dynamicallyAdded.setStyle(null);
            Text dynamicallyAddedText = new Text("Dynamically added text");
            workspace.getChildren().addAll(dynamicallyAdded, dynamicallyAddedText);
            settle(workspace);

            assertEquals(expected, label.getTextFill(), preference + " label");
            assertEquals(expected, boundStyleLabel.getTextFill(), preference + " bound skin style");
            SurfaceContrastAssertions.assertReadable(button);
            assertEquals(expected, hyperlink.getTextFill(), preference + " hyperlink");
            assertEquals(expected, text.getFill(), preference + " text node");
            assertEquals(expected, dynamicallyAdded.getTextFill(), preference + " dynamic label");
            assertEquals(expected, dynamicallyAddedText.getFill(), preference + " dynamic text");
            assertEquals(18.0, label.getFont().getSize(), "Unrelated inline styling survives");
            input.lookupAll(".text").stream().filter(Text.class::isInstance)
                    .map(Text.class::cast).forEach(node ->
                            assertEquals(expected, node.getFill(), preference + " input skin text"));

            workspace.setStyle("-fx-background-color: "
                    + (expected.equals(Color.BLACK) ? "black" : "white") + ";");
            WorkspaceTextContrastEnforcer.apply(workspace, !AtlantaFxThemeManager.isDark(preference));
            settle(workspace);
            Color switched = expected.equals(Color.BLACK) ? Color.WHITE : Color.BLACK;
            assertEquals(switched, label.getTextFill(), preference + " live theme switch");
            assertEquals(switched, boundStyleLabel.getTextFill(), preference + " bound skin theme switch");
            assertEquals(switched, dynamicallyAddedText.getFill(), preference + " dynamic theme switch");
            return null;
        });
    }
    @Test
    void whiteReportAndDarkSurfaceRemainReadableInTheSameDarkTheme()
    {
        FxTestSupport.onFx(() ->
        {
            AtlantaFxThemeManager.apply(UiThemePreference.DARK);
            var values = new org.nonprofitbookkeeping.report.template.SemanticReportValueSet();
            var template = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                    "{\"title\":\"White workbook\",\"sections\":[]}");
            var report = (javafx.scene.Parent) new SemanticReportFxRenderer().render(template, values);
            Label outside = new Label("Dark surface");
            var area = new javafx.scene.control.TextArea("White editor content");
            VBox root = new VBox(outside, area, report);
            root.setStyle("-fx-background-color: #24292f;");
            WorkspaceTextContrastEnforcer.install(root);
            WorkspaceTextContrastEnforcer.apply(root, true);
            new Scene(root, 640, 480);
            settle(root);
            area.lookup(".content").setStyle("-fx-background-color: white;");
            settle(root);
            area.lookupAll(".text").stream().filter(Text.class::isInstance)
                    .map(Text.class::cast).forEach(node ->
                            assertEquals(Color.BLACK, node.getFill(), "White editor content uses black text"));
            assertEquals(Color.WHITE, outside.getTextFill());
            var title = (Label) root.lookup(".workbook-report-title");
            assertEquals(Color.BLACK, title.getTextFill(), "White report stays black under a dark theme");
            SurfaceContrastAssertions.assertReadable(title);
            return null;
        });
    }

    static void settle(javafx.scene.Parent root)
    {
        for (int pass = 0; pass < 3; pass++)
        {
            root.applyCss();
            root.layout();
            WorkspaceTextContrastEnforcer.refresh(root);
        }
        root.applyCss();
    }

}
