package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DashboardValueFormatterTest
{
    @Test
    public void moneyFormatsPositiveNegativeAndNullValues()
    {
        assertEquals("$1,234.50", DashboardValueFormatter.money(new BigDecimal("1234.50")));
        assertEquals("-$12.34", DashboardValueFormatter.money(new BigDecimal("-12.34")));
        assertEquals("$0.00", DashboardValueFormatter.money(null));
    }

    @Test
    public void optionalMoneyDoesNotInventUnavailableValues()
    {
        assertEquals("Not available", DashboardValueFormatter.optionalMoney(Optional.empty()));
        assertEquals("Not available", DashboardValueFormatter.optionalMoney(null));
        assertEquals("$25.00", DashboardValueFormatter.optionalMoney(Optional.of(new BigDecimal("25.00"))));
    }
}
