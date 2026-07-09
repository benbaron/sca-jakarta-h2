package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-level guardrails for Banking panel layout. */
class BankingPanelSourceTest
{
    @Test
    void financialInstitutionsAndConfiguredAccountsUseTopBottomSplit() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/BankingPanel.java"));

        assertTrue(source.contains("new SplitPane(bankListPane(), bankAccountPane())"));
        assertTrue(source.contains("split.setOrientation(Orientation.VERTICAL)"));
        assertTrue(source.contains("Financial institutions"));
        assertTrue(source.contains("Configured bank accounts"));
        assertTrue(source.contains("VBox.setVgrow(banks, Priority.ALWAYS)"));
        assertTrue(source.contains("VBox.setVgrow(bankAccounts, Priority.ALWAYS)"));
    }
}
