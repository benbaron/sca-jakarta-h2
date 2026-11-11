package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.beans.binding.Bindings;

/** Responsive non-cell text and intrinsic form widths; never changes entered values. */
final class PanelTextLayout
{
    private PanelTextLayout()
    {
    }

    static void prepare(Node node)
    {
        if (node instanceof Label label)
        {
            label.setWrapText(true);
            label.setMaxWidth(Double.MAX_VALUE);
            if (!label.minHeightProperty().isBound() && label.getMinHeight() == Region.USE_COMPUTED_SIZE)
            {
                label.setMinHeight(Region.USE_PREF_SIZE);
            }
        }
        else if (node instanceof ButtonBase button)
        {
            button.setWrapText(true);
            button.setMinHeight(Region.USE_PREF_SIZE);
            if (!button.minWidthProperty().isBound() && button.getMinWidth() == Region.USE_COMPUTED_SIZE)
            {
                button.minWidthProperty().bind(Bindings.createDoubleBinding(() ->
                {
                    double longest = 0;
                    for (String word : java.util.Objects.toString(button.getText(), "").split("\\s+"))
                    {
                        Text text = new Text(word);
                        text.setFont(button.getFont());
                        longest = Math.max(longest, text.getLayoutBounds().getWidth());
                    }
                    double chrome = Math.max(0, button.prefWidth(-1) - textWidth(button));
                    return Math.ceil(longest + chrome);
                }, button.textProperty(), button.fontProperty(), button.insetsProperty(), button.graphicProperty()));
            }
        }
        else if (node instanceof GridPane grid && !grid.minWidthProperty().isBound())
        {
            grid.setMinWidth(Region.USE_PREF_SIZE);
        }
    }

    private static double textWidth(ButtonBase button)
    {
        Text text = new Text(java.util.Objects.toString(button.getText(), ""));
        text.setFont(button.getFont());
        return text.getLayoutBounds().getWidth();
    }
}
