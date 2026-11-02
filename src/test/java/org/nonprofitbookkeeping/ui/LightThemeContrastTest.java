package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Protects the Windows Light-theme foreground contract from platform-style drift. */
class LightThemeContrastTest
{
    @Test
    void lightThemeUsesNearBlackTextAcrossProductionControlRoles() throws Exception
    {
        String styles = Files.readString(Path.of("src/main/resources/ui/styles.css"));
        String black = "-fx-text-fill: #111111;";

        assertRuleUses(styles, ".production-workspace.theme-light", black);
        assertRuleUses(styles, ".production-workspace.theme-light",
                "-fx-text-base-color: #111111;");
        assertRuleUses(styles, ".production-workspace.theme-light",
                "-fx-text-inner-color: #111111;");
        assertRuleUses(styles, ".production-workspace.theme-light",
                "-fx-text-background-color: #111111;");
        for (String selector : new String[] {
                ".production-workspace.theme-light .label",
                ".production-workspace.theme-light .button",
                ".production-workspace.theme-light .menu",
                ".production-workspace.theme-light .menu-item",
                ".production-workspace.theme-light .check-box",
                ".production-workspace.theme-light .text-field",
                ".production-workspace.theme-light .combo-box-base",
                ".production-workspace.theme-light .table-cell",
                ".production-workspace.theme-light .workspace-tabs .tab:selected .tab-label",
                ".production-workspace.theme-light .navigation-section",
                ".production-workspace.theme-light .navigation-item-selected",
                ".production-workspace.theme-light .inspector-card-title",
                ".production-workspace.theme-light .hyperlink" })
        {
            assertTrue(styles.contains(selector), selector);
        }

        int lightStart = styles.indexOf(".production-workspace.theme-light");
        int darkStart = styles.indexOf(".production-workspace.theme-dark", lightStart);
        String lightContract = styles.substring(lightStart, darkStart);
        assertTrue(lightContract.contains(black));
    }

    private static void assertRuleUses(String styles, String selector, String declaration)
    {
        int selectorStart = styles.indexOf(selector);
        assertTrue(selectorStart >= 0, selector);
        int blockStart = styles.indexOf('{', selectorStart);
        int blockEnd = styles.indexOf('}', blockStart);
        assertTrue(blockStart >= 0 && blockEnd > blockStart, selector);
        assertTrue(styles.substring(blockStart, blockEnd).contains(declaration), selector);
    }
}
