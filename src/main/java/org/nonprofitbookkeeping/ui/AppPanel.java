package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import org.nonprofitbookkeeping.service.ApplicationPermission;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Defines the AppPanel contract in the nonprofit bookkeeping application.
 */
public interface AppPanel
{
    record RunCommandResult(boolean handled, String message)
    {
    }

    record JournalSelection(long txnId, String sourceLabel)
    {
    }

    String title();
    Node root();

    /** Commands this panel can genuinely perform in its current mode. */
    default Set<AppCommand> commandCapabilities()
    {
        return Set.of();
    }

    default boolean supportsCommand(AppCommand command)
    {
        return commandCapabilities().contains(command);
    }

    /** Permission required for a genuine command in the panel's current mode. */
    default Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return Optional.empty();
    }

    /**
     * Runs a supported panel command and reports whether a real operation was
     * accepted. Unsupported commands never fall through to an empty hook.
     */
    default RunCommandResult executeCommand(AppCommand command)
    {
        if (!supportsCommand(command))
        {
            return new RunCommandResult(false,
                    GlobalCommandRegistry.label(command) + " is not available in " + title() + ".");
        }

        try
        {
            return switch (command)
            {
                case NEW_ACTIVE -> invoke(command, this::onNew);
                case SAVE_ACTIVE -> invoke(command, this::onSave);
                case POST_VALIDATE -> onRunCommand(command);
                case CLOSE_ALL_TABS, CLOSE_INSPECTOR -> new RunCommandResult(false,
                        GlobalCommandRegistry.label(command) + " is owned by the workspace shell.");
            };
        }
        catch (RuntimeException ex)
        {
            String detail = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName()
                    : ex.getMessage();
            return new RunCommandResult(false,
                    GlobalCommandRegistry.label(command) + " failed in " + title() + ": " + detail);
        }
    }

    default void onSave()
    {
        throw new UnsupportedOperationException("Save is not implemented by " + title() + ".");
    }

    default void onNew()
    {
        throw new UnsupportedOperationException("New is not implemented by " + title() + ".");
    }

    default void onPanelShown() {}

    default boolean hasUnsavedChanges()
    {
        return false;
    }

    default RunCommandResult onRunCommand(AppCommand command)
    {
        return new RunCommandResult(false, "Run command not available for panel: " + title());
    }

    /** Returns the factual panel message after a declared New or Save action. */
    default String commandResultMessage(AppCommand command)
    {
        return GlobalCommandRegistry.label(command) + " was accepted by " + title() + ".";
    }

    /** Allows a composite panel to notify the shell when its selected mode changes. */
    default void setCommandCapabilitiesChangedListener(Runnable listener)
    {
        // Stable-capability panels require no listener.
    }

    default Optional<JournalSelection> activeJournalSelection()
    {
        return Optional.empty();
    }

    private RunCommandResult invoke(AppCommand command, Runnable action)
    {
        action.run();
        String message = commandResultMessage(command);
        if (message == null || message.isBlank())
        {
            message = GlobalCommandRegistry.label(command) + " was accepted by " + title() + ".";
        }
        return new RunCommandResult(true, message);
    }

    static Set<AppCommand> capabilities(AppCommand first, AppCommand... remaining)
    {
        EnumSet<AppCommand> capabilities = EnumSet.of(first, remaining);
        return Set.copyOf(capabilities);
    }
}
