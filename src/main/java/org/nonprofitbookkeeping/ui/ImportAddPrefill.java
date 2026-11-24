package org.nonprofitbookkeeping.ui;

import javafx.scene.control.Label;
import org.nonprofitbookkeeping.service.ApplicationPermission;

import java.util.function.Consumer;

/** Keeps a literal Import hint until a new master draft can safely receive it. */
final class ImportAddPrefill
{
    private final Label notice = new Label();
    private ImportAddSuggestion pending;
    private boolean automaticPending;

    ImportAddPrefill(String controlPrefix)
    {
        notice.setId(controlPrefix + "ImportSuggestion");
        notice.setWrapText(true);
        hide();
    }

    Label notice()
    {
        return notice;
    }

    void offer(ImportAddSuggestion suggestion, boolean hasDraft, boolean readyForNew, Consumer<String> populateNew)
    {
        pending = suggestion;
        automaticPending = !hasDraft;
        if (!valid())
        {
            pending = null;
            hide();
            return;
        }
        if (!applyIfReady(!hasDraft && readyForNew, populateNew))
        {
            notice.setText("Import suggests Name: " + pending.name()
                    + ". Current editor changes are retained. Choose New when ready to start that record.");
            notice.setVisible(true);
            notice.setManaged(true);
        }
    }

    boolean applyWhenReady(boolean readyForNew, Consumer<String> populateNew)
    {
        return automaticPending && applyIfReady(readyForNew, populateNew);
    }

    boolean applyIfReady(boolean readyForNew, Consumer<String> populateNew)
    {
        if (!valid())
        {
            pending = null;
            hide();
            return false;
        }
        if (!readyForNew)
        {
            return false;
        }
        String literalName = pending.name();
        pending = null;
        hide();
        populateNew.accept(literalName);
        return true;
    }

    private boolean valid()
    {
        return pending != null && pending.name() != null && !pending.name().isBlank()
                && pending.matchesCurrentScope()
                && UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE);
    }

    private void hide()
    {
        notice.setText("");
        notice.setVisible(false);
        notice.setManaged(false);
    }
}
