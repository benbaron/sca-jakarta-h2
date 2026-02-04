package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.time.LocalDate;

/**
 * Preset selector + date pickers for CUSTOM.
 */
public class DateRangeSelector extends HBox
{
    private final ComboBox<DateRangePreset> preset = new ComboBox<>();
    private final DatePicker start = new DatePicker();
    private final DatePicker end = new DatePicker();
    private final Label summary = new Label();

    public DateRangeSelector()
    {
        setSpacing(8);
        setPadding(new Insets(0, 0, 0, 6));

        preset.getItems().setAll(DateRangePreset.values());
        preset.getSelectionModel().select(DateRangePreset.ALL);

        start.setPrefWidth(140);
        end.setPrefWidth(140);

        getChildren().addAll(new Label("Range:"), preset, start, new Label("to"), end, summary);

        preset.valueProperty().addListener((o, a, b) -> recompute());
        start.valueProperty().addListener((o, a, b) -> recompute());
        end.valueProperty().addListener((o, a, b) -> recompute());

        recompute();
    }

    private void recompute()
    {
        DateRangePreset p = preset.getValue();
        boolean custom = p == DateRangePreset.CUSTOM;

        start.setDisable(!custom);
        end.setDisable(!custom);

        LocalDate s = start.getValue();
        LocalDate e = end.getValue();

        DateRange computed = DateRangeUtil.compute(p, LocalDate.now(), s, e);
        DateRangeContext.set(computed);

        summary.setText(computed.toString());
    }

    public ComboBox<DateRangePreset> presetBox() { return preset; }
}
