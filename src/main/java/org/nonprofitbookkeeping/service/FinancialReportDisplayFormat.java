package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Presentation-only date and money formatting used by visible financial reports. */
public interface FinancialReportDisplayFormat
{
    String formatDate(LocalDate value);

    String formatMoney(BigDecimal value);

    static FinancialReportDisplayFormat plain()
    {
        return new FinancialReportDisplayFormat()
        {
            @Override
            public String formatDate(LocalDate value)
            {
                return value == null ? "" : value.toString();
            }

            @Override
            public String formatMoney(BigDecimal value)
            {
                return value == null ? "0" : value.toPlainString();
            }
        };
    }
}
