package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartOfAccountsPanelLifecycleSourceTest
{
    @Test
    void panelUsesStableIdLifecycleServiceAndNoDeletePlaceholder() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/ChartOfAccountsPanel.java"));

        assertTrue(source.contains("private Long editingAccountId"));
        assertTrue(source.contains("new AccountCommand("));
        assertTrue(source.contains("accountAdmin().save(command)"));
        assertTrue(source.contains("editingAccountId = null"));
        assertTrue(source.contains("Objects.equals(row.getId(), reselectId)"));
        assertTrue(source.contains("this editor does not physically delete accounts"));
        assertFalse(source.contains("accountAdmin().upsert("));
        assertFalse(source.contains("new Button(\"Delete"));
    }
}
