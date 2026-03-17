package org.nonprofitbookkeeping.ui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * DrillThroughCoordinator component.
 */
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
    }

    static void openLedgerWithContext(String context)
    {
        CONTEXT.set(context == null ? "" : context);
        openPanelWithContext(AppPanelId.LEDGER_REGISTER, context);
    }

    static void openPanelWithContext(AppPanelId panelId, String context)
    {
        if (panelId == null)
        {
            return;
        }
        PANEL_CONTEXT.put(panelId, context == null ? "" : context);
        opener.accept(panelId);
    }

    static String consumeContext(AppPanelId panelId)
    {
        if (panelId == null)
        {
            return "";
        }
        String context = PANEL_CONTEXT.remove(panelId);
        return context == null ? "" : context;
    }

    static String consumeContext()
    {
        String context = CONTEXT.getAndSet("");
        return context == null ? "" : context;
    }
}
