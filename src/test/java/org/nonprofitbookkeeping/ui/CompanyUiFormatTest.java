package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.CompanyUiPreferences;
import org.nonprofitbookkeeping.model.DateDisplayFormat;
import org.nonprofitbookkeeping.model.MoneyPrintFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CompanyUiFormatTest
{
    @Test
    void moneyFormattingUsesCompanySymbolAndPrintChoice()
    {
        CompanyUiFormat prefix = new CompanyUiFormat(new CompanyUiPreferences("€", MoneyPrintFormat.SYMBOL_PREFIX, DateDisplayFormat.DAY_MONTH_YEAR));
        CompanyUiFormat suffix = new CompanyUiFormat(new CompanyUiPreferences("kr", MoneyPrintFormat.SYMBOL_SUFFIX, DateDisplayFormat.YEAR_MONTH_DAY));

        assertEquals("€1,234.50", prefix.formatMoney(new BigDecimal("1234.5")));
        assertEquals(new BigDecimal("1234.50"), prefix.parseMoney("€1,234.50"));
        assertEquals("1,234.50 kr", suffix.formatMoney(new BigDecimal("1234.5")));
        assertEquals(new BigDecimal("1234.50"), suffix.parseMoney("1,234.50 kr"));
        assertNull(prefix.parseMoney("not money"));
    }

    @Test
    void dateParsingUsesPreferredOrderingButAcceptsCommonForms()
    {
        CompanyUiFormat dmy = new CompanyUiFormat(new CompanyUiPreferences("£", MoneyPrintFormat.SYMBOL_PREFIX, DateDisplayFormat.DAY_MONTH_YEAR));
        CompanyUiFormat mdy = new CompanyUiFormat(new CompanyUiPreferences("$", MoneyPrintFormat.SYMBOL_PREFIX, DateDisplayFormat.MONTH_DAY_YEAR));

        assertEquals(LocalDate.of(2026, 7, 10), dmy.parseDate("10/7/2026"));
        assertEquals("10/7/2026", dmy.formatDate(LocalDate.of(2026, 7, 10)));
        assertEquals(LocalDate.of(2026, 10, 7), mdy.parseDate("10/7/2026"));
        assertEquals(LocalDate.of(2026, 7, 10), mdy.parseDate("2026-07-10"));
        assertNull(dmy.parseDate("31/31/2026"));
    }
}
