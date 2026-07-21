package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpComplianceSourceTest
{
    @Test
    void helpMatchesCurrentNavigationAndAuthoritativeLinks() throws Exception
    {
        String source = source("HelpPanel.java");

        assertTrue(source.contains("https://github.com/benbaron/sca-jakarta-h2"));
        assertTrue(source.contains("/blob/main/doc/PLAN.md"));
        assertTrue(source.contains("/blob/main/doc/ui_design_rules.md"));
        assertTrue(source.contains("/blob/main/doc/workflow/development-workflow.md"));
        assertTrue(source.contains("Journal, Banking, Bank Reconciliation, Bank Transactions"));
        assertTrue(source.contains("Import Preview, Audit History, Period Close"));
        assertTrue(source.contains("Administration -> Company Admin"));
        assertTrue(source.contains("helpContentScroll"));

        assertFalse(source.contains("nonprofitbookkeeping/sca-jakarta-h2"));
        assertFalse(source.contains("docs/repo-local-build.md"));
        assertFalse(source.contains("docs/progress-report-next-pass.md"));
        assertFalse(source.contains("Open Settings"));

        assertTrue(Files.exists(Path.of("doc/PLAN.md")));
        assertTrue(Files.exists(Path.of("doc/ui_design_rules.md")));
        assertTrue(Files.exists(Path.of("doc/workflow/development-workflow.md")));
    }

    @Test
    void productionDestinationMenuUsesCanonicalNames() throws Exception
    {
        String source = source("ProductionWorkspaceWindow.java");

        assertTrue(source.contains("new MenuItem(\"Journal\")"));
        assertTrue(source.contains("openPanel(AppPanelId.JOURNAL_PANE)"));
        assertTrue(source.contains("new MenuItem(\"Administration\")"));
        assertFalse(source.contains("new MenuItem(\"Ledger Register\")"));
        assertFalse(source.contains("new MenuItem(\"Transaction Editor\")"));
    }

    private static String source(String filename) throws Exception
    {
        return Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui", filename));
    }
}
