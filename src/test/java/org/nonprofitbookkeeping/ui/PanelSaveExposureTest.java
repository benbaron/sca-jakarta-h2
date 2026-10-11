package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that every production editor routes the workspace Save command. */
public class PanelSaveExposureTest
{
    private static final Path UI_SOURCE = Path.of("src/main/java/org/nonprofitbookkeeping/ui");

    @Test
    public void durableRecordEditorsExposeLocalAndWorkspaceSaveActions() throws Exception
    {
        for (String panel : List.of(
                "BankingPanel.java",
                "BudgetEditorPanel.java",
                "ChartOfAccountsPanel.java",
                "FundsPanel.java",
                "SettingsPanel.java",
                "TransactionEditorPanel.java"))
        {
            String source = Files.readString(UI_SOURCE.resolve(panel));
            assertTrue(source.contains("Save"), panel + " must expose a visible Save action");
            assertTrue(source.contains("void onSave()"), panel + " must route the workspace Save command");
        }
    }

    @Test
    public void bankingSubpanesExposeDistinctSaveActions() throws Exception
    {
        String source = Files.readString(UI_SOURCE.resolve("BankingPanel.java"));

        assertTrue(source.contains("new Button(\"Save Bank\")"));
        assertTrue(source.contains("new Button(\"Save Bank Account\")"));
        assertTrue(source.contains("activeSaveTarget == SaveTarget.BANK_ACCOUNT"));
    }
}
