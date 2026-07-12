package org.nonprofitbookkeeping.report;

import java.time.LocalDate;
import java.util.Objects;

/** Immutable validated parameter set shared by preview, export, and drill-through. */
public record ReportRequest(
        ReportDefinition definition,
        LocalDate startDate,
        LocalDate endDate,
        ReportFundOption fund,
        int rowLimit)
{
    public static final int DEFAULT_ROW_LIMIT = 400;
    public static final int MAX_ROW_LIMIT = 5000;

    public ReportRequest
    {
        definition = Objects.requireNonNull(definition, "definition");
        endDate = Objects.requireNonNull(endDate, "endDate");
        fund = fund == null ? ReportFundOption.ALL_FUNDS : fund;

        if (definition.dateMode() == ReportDefinition.DateMode.AS_OF)
        {
            startDate = endDate;
        }
        else
        {
            startDate = Objects.requireNonNull(startDate, "startDate");
            if (endDate.isBefore(startDate))
            {
                throw new IllegalArgumentException("Report end date must be on or after the start date.");
            }
        }

        if (!definition.supportsFund() && !fund.allFunds())
        {
            throw new IllegalArgumentException(definition.displayName() + " does not support a fund filter.");
        }

        if (definition.supportsRowLimit())
        {
            if (rowLimit < 1 || rowLimit > MAX_ROW_LIMIT)
            {
                throw new IllegalArgumentException(
                        "Row limit must be between 1 and " + MAX_ROW_LIMIT + ".");
            }
        }
        else
        {
            rowLimit = DEFAULT_ROW_LIMIT;
        }
    }

    public String fundCode()
    {
        return fund.allFunds() ? null : fund.code();
    }

    public LocalDate asOfDate()
    {
        return endDate;
    }

    public String contextSummary()
    {
        String dates = definition.dateMode() == ReportDefinition.DateMode.AS_OF
                ? "as of " + endDate
                : startDate + " through " + endDate;
        String fundText = fund.allFunds() ? "all funds" : fund.displayLabel();
        String rows = definition.supportsRowLimit() ? ", max " + rowLimit + " rows" : "";
        return definition.displayName() + " | " + dates + " | " + fundText + rows;
    }
}
