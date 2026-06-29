package org.nonprofitbookkeeping.ui;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Formats dashboard values consistently without inventing unavailable data.
 */
public final class DashboardValueFormatter
{
    private DashboardValueFormatter()
    {
    }

    public static String money(BigDecimal value)
    {
        BigDecimal safeValue = value == null ? BigDecimal.ZERO : value;
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(safeValue);
    }

    public static String optionalMoney(Optional<BigDecimal> value)
    {
        return value == null || value.isEmpty() ? "Not available" : money(value.get());
    }
}
