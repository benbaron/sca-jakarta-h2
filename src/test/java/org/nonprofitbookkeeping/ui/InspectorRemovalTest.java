package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectorRemovalTest
{
    @Test
    void shellRetiresInspectorButKeepsDatabaseMessages() throws Exception
    {
        String shell = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/ProductionWorkspaceWindow.java"));
        assertFalse(shell.contains("InspectorPane"));
        assertFalse(shell.contains("Toggle Inspector"));
        assertFalse(shell.contains("setInspectorVisible"));
        assertTrue(shell.contains("showWorkspaceMessage("));
        assertTrue(shell.contains("Database switch failed"));
        assertTrue(shell.contains("Sample company failed"));
        assertFalse(GlobalCommandRegistry.shortcutHelpText().contains("Inspector"));
    }
}
