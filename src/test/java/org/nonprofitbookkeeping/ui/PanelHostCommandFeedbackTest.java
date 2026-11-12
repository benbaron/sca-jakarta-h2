package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PanelHostCommandFeedbackTest
{
    @Test
    void forwardsOnlyTheActiveCurrentPanelAndRejectsHiddenReplacedAndResetSenders()
    {
        FxTestSupport.onFx(() ->
        {
            PanelHost host = new PanelHost();
            List<String> messages = new ArrayList<>();
            host.setCommandResultListener(result -> messages.add(result.message()));
            AsyncPanel first = new AsyncPanel(), second = new AsyncPanel(), replacement = new AsyncPanel();
            host.showReplacement(AppPanelId.MERCHANTS, first);
            first.complete("Active first");
            host.showReplacement(AppPanelId.FUNDS, second);
            first.complete("Hidden completion");
            second.complete("Active second");
            host.showReplacement(AppPanelId.FUNDS, replacement);
            second.complete("Replaced completion");
            replacement.complete("Current replacement");
            host.reset();
            replacement.complete("After reset");
            assertEquals(List.of("Active first", "Active second", "Current replacement"), messages);
            return null;
        });
    }

    private static final class AsyncPanel implements AppPanel
    {
        private final Label root = new Label("Test panel");
        private Consumer<RunCommandResult> resultListener;
        @Override public Node root() { return root; }
        @Override public String title() { return "Async test panel"; }
        @Override public void setCommandResultListener(Consumer<RunCommandResult> listener) { resultListener = listener; }
        void complete(String message)
        {
            resultListener.accept(new RunCommandResult(true, message));
        }
    }
}
