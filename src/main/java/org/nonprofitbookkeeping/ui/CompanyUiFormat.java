package org.nonprofitbookkeeping.ui;

import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.model.CompanyUiPreferences;
import org.nonprofitbookkeeping.model.DateDisplayFormat;
import org.nonprofitbookkeeping.model.MoneyPrintFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Company-aware presentation and lenient editing support for money and dates. */
public final class CompanyUiFormat
{
    private final CompanyUiPreferences preferences;
    private final DecimalFormat numberFormat;
    private final DateTimeFormatter displayDateFormatter;
    private final List<DateTimeFormatter> acceptedDateFormatters;

    public CompanyUiFormat(CompanyUiPreferences preferences)
    {
        this.preferences = preferences == null ? CompanyUiPreferences.defaults() : preferences;
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        numberFormat = new DecimalFormat("#,##0.00", symbols);
        numberFormat.setParseBigDecimal(true);
        numberFormat.setRoundingMode(RoundingMode.HALF_UP);
        displayDateFormatter = DateTimeFormatter.ofPattern(this.preferences.dateDisplayFormat().pattern(), Locale.US);
        acceptedDateFormatters = acceptedDateFormatters(this.preferences.dateDisplayFormat());
    }

    public CompanyUiPreferences preferences()
    {
        return preferences;
    }

    public String formatMoney(BigDecimal value)
    {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
        String number = numberFormat.format(amount);
        return switch (preferences.moneyPrintFormat())
        {
            case SYMBOL_PREFIX -> preferences.currencySymbol() + number;
            case SYMBOL_SUFFIX -> number + " " + preferences.currencySymbol();
            case NUMBER_ONLY -> number;
        };
    }

    public String normalizeMoney(String value)
    {
        BigDecimal parsed = parseMoney(value);
        return parsed == null ? safe(value).trim() : formatMoney(parsed);
    }

    public BigDecimal parseMoney(String value)
    {
        if (value == null || value.isBlank())
        {
            return BigDecimal.ZERO;
        }
        String normalized = value.trim()
                .replace(preferences.currencySymbol(), "")
                .replace("$", "")
                .replace("€", "")
                .replace("£", "")
                .replace("¥", "")
                .replace(" ", "")
                .replace(",", "");
        boolean parentheses = normalized.startsWith("(") && normalized.endsWith(")");
        if (parentheses)
        {
            normalized = "-" + normalized.substring(1, normalized.length() - 1);
        }
        try
        {
            return new BigDecimal(normalized);
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    public String formatDate(LocalDate value)
    {
        return value == null ? "" : displayDateFormatter.format(value);
    }

    public String normalizeDate(String value)
    {
        LocalDate parsed = parseDate(value);
        return parsed == null ? safe(value).trim() : formatDate(parsed);
    }

    public LocalDate parseDate(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        String candidate = value.trim();
        for (DateTimeFormatter formatter : acceptedDateFormatters)
        {
            try
            {
                return LocalDate.parse(candidate, formatter);
            }
            catch (DateTimeParseException ignored)
            {
                // Try the next accepted format. Preferred ordering remains first.
            }
        }
        return null;
    }

    public void install(DatePicker picker)
    {
        picker.setPromptText(preferences.dateDisplayFormat().pattern().replace("uuuu", "yyyy"));
        picker.setConverter(new StringConverter<>()
        {
            @Override
            public String toString(LocalDate value)
            {
                return formatDate(value);
            }

            @Override
            public LocalDate fromString(String value)
            {
                if (value == null || value.isBlank())
                {
                    return null;
                }
                LocalDate parsed = parseDate(value);
                if (parsed == null)
                {
                    throw new IllegalArgumentException("Enter a valid date using " + picker.getPromptText() + " ordering.");
                }
                return parsed;
            }
        });
        picker.getEditor().focusedProperty().addListener((obs, oldValue, focused) -> {
            if (focused)
            {
                return;
            }
            String text = picker.getEditor().getText();
            if (text == null || text.isBlank())
            {
                picker.setValue(null);
                picker.getEditor().clear();
                return;
            }
            LocalDate parsed = parseDate(text);
            if (parsed != null)
            {
                picker.setValue(parsed);
                picker.getEditor().setText(formatDate(parsed));
            }
        });
    }

    private static List<DateTimeFormatter> acceptedDateFormatters(DateDisplayFormat preferred)
    {
        Set<String> patterns = new LinkedHashSet<>();
        switch (preferred)
        {
            case MONTH_DAY_YEAR -> {
                patterns.add("M/d/uuuu");
                patterns.add("M-d-uuuu");
            }
            case DAY_MONTH_YEAR -> {
                patterns.add("d/M/uuuu");
                patterns.add("d-M-uuuu");
            }
            case YEAR_MONTH_DAY -> {
                patterns.add("uuuu-MM-dd");
                patterns.add("uuuu/M/d");
            }
        }
        patterns.add("uuuu-MM-dd");
        patterns.add("M/d/uuuu");
        patterns.add("M-d-uuuu");
        patterns.add("d/M/uuuu");
        patterns.add("d-M-uuuu");
        List<DateTimeFormatter> formatters = new ArrayList<>();
        for (String pattern : patterns)
        {
            formatters.add(DateTimeFormatter.ofPattern(pattern, Locale.US));
        }
        return List.copyOf(formatters);
    }

    private static String safe(String value)
    {
        return value == null ? "" : value;
    }
}
