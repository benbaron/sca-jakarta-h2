package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetEditorLifecycleSourceTest
{
    @Test
    void editorUsesRetainedArchiveLifecycleInsteadOfPlaceholderDelete() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/BudgetEditorPanel.java"));

        assertTrue(source.contains("new Button(\"Archive Draft\")"));
        assertTrue(source.contains("budgetEditorArchiveDraftButton"));
        assertTrue(source.contains("budgetEditorLifecycleHint"));
        assertTrue(source.contains("versionsForFiscalYear"));
        assertTrue(source.contains("budgetPlan().archive(planId)"));
        assertTrue(source.contains("CompanyDialogUiCompliance.install(confirmation.getDialogPane(), AppPanelId.BUDGET_EDITOR)"));
        assertTrue(source.contains("Nothing is physically deleted."));
        assertTrue(source.contains("Archived versions are read-only history."));
        assertTrue(source.contains("boolean archived = currentPlan != null && currentPlan.status() == BudgetPlan.Status.ARCHIVED"));
        assertTrue(source.contains("rows.put(line.budgetCategoryId()"));
        assertTrue(source.contains("line.budgetCategoryCode()"));
        assertFalse(source.contains("new Button(\"Delete"));
        assertFalse(source.contains("Delete Selected"));
    }
}
