package org.nonprofitbookkeeping.ui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Coordinates cross-panel navigation and one-time context handoff. */
final class DrillThroughCoordinator
{
    private static final AtomicReference<String> CONTEXT = new AtomicReference<>();
    private static final Map<AppPanelId, String> PANEL_CONTEXT = new ConcurrentHashMap<>();
    private static volatile Consumer<AppPanelId> opener = id -> {};

    private DrillThroughCoordinator()
    {
    }

    static void configureOpener(Consumer<AppPanelId> panelOpener)
    {
        opener = panelOpener == null ? (id -> {}) : panelOpener;
        debug("Configured opener: " + (panelOpener == null ? "no-op" : panelOpener));
    }

    static void openLedgerWithContext(String context)
    {
        CONTEXT.set(context == null ? "" : context);
        debug("Stored legacy ledger context '" + safeContext(context) + "'.");
        openPanelWithContext(AppPanelId.JOURNAL_PANE, context);
    }

    static void openTransactionEditorWithContext(String context)
    {
        openPanelWithContext(AppPanelId.JOURNAL_PANE, context);
    }

    static void openPanelWithContext(AppPanelId requestedPanelId, String context)
    {
        if (requestedPanelId == null)
        {
            debug("Ignored open request with null panel id and context '" + safeContext(context) + "'.");
            return;
        }
        AppPanelId panelId = AppPanelId.canonical(requestedPanelId);
        String normalizedContext = context == null ? "" : context;
        PANEL_CONTEXT.put(panelId, normalizedContext);
        debug("Opening " + panelId + " with context '" + safeContext(normalizedContext) + "'.");
        opener.accept(panelId);
        debug("Open request dispatched for " + panelId + ".");
    }

    static String consumeContext(AppPanelId requestedPanelId)
    {
        if (requestedPanelId == null)
        {
            return "";
        }
        AppPanelId panelId = AppPanelId.canonical(requestedPanelId);
        String context = PANEL_CONTEXT.remove(panelId);
        debug("Consumed context for " + panelId + ": '" + safeContext(context) + "'.");
        return context == null ? "" : context;
    }

    static String consumeContext()
    {
        String context = CONTEXT.getAndSet("");
        debug("Consumed legacy context: '" + safeContext(context) + "'.");
        return context == null ? "" : context;
    }

    private static void debug(String message)
    {
        UiDebug.log("drill-through", message);
    }

    private static String safeContext(String context)
    {
        return context == null ? "" : context;
    }
}
