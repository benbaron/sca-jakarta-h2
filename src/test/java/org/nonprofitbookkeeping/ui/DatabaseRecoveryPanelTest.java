package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DatabaseRecoveryPanelTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    void recoveryButtonsRouteExplicitTypedCommands()
    {
        AtomicReference<DatabaseRecoveryCommand> command = new AtomicReference<>();
        DatabaseRecoveryPanel panel = FxTestSupport.onFx(() -> new DatabaseRecoveryPanel(
                Path.of("broken.mv.db"),
                new IllegalStateException("unavailable"),
                command::set));

        FxTestSupport.onFx(() ->
        {
            button(panel.root(), "Retry / Repair Current Database").fire();
            assertEquals(DatabaseRecoveryCommand.RETRY_CURRENT, command.get());
            button(panel.root(), "Select Existing Database…").fire();
            assertEquals(DatabaseRecoveryCommand.SELECT_EXISTING, command.get());
            button(panel.root(), "Create New Database…").fire();
            assertEquals(DatabaseRecoveryCommand.CREATE_NEW, command.get());
            return null;
        });
    }

    @Test
    void recoveryPanelDoesNotRegressToOpaqueRunnableCallbacks() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/DatabaseRecoveryPanel.java"));

        assertFalse(source.contains("Runnable repairCurrent"));
        assertFalse(source.contains("Runnable selectExisting"));
        assertFalse(source.contains("Runnable createNew"));
    }

    private static Button button(Node node, String text)
    {
        if (node instanceof Button button && text.equals(button.getText()))
        {
            return button;
        }
        if (node instanceof Parent parent)
        {
            for (Node child : parent.getChildrenUnmodifiable())
            {
                try
                {
                    return button(child, text);
                }
                catch (IllegalStateException ignored)
                {
                    // Continue searching sibling nodes.
                }
            }
        }
        throw new IllegalStateException("No button named " + text + " found");
    }
}
