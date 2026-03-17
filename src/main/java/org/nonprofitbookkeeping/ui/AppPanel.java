package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;

import java.util.Optional;

/**
 * Defines the AppPanel contract in the nonprofit bookkeeping application.
 */
public interface AppPanel
{
    enum RunCommand
    {
        POST_VALIDATE
    }

    record RunCommandResult(boolean handled, String message)
    {
    }

    record JournalSelection(long txnId, String sourceLabel)
    {
    }

    String title();
    Node root();

    default void onSave() {}
    default void onNew() {}
    default void onCopy() {}
    default void onPaste() {}

    default RunCommandResult onRunCommand(RunCommand command)
    {
        return new RunCommandResult(false, "Run command not available for panel: " + title());
    }

    default Optional<JournalSelection> activeJournalSelection()
    {
        return Optional.empty();
    }
}
