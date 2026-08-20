package org.nonprofitbookkeeping.report;

import java.time.LocalDate;
import java.util.Objects;

/** Immutable validated parameter set shared by preview, export, and drill-through. */
public record ReportRequest(
        ReportDefinition definition,
        LocalDate startDate,
        LocalDate endDate,
        ReportFundOption fund,
        int rowLimit,
        ReportDomainFilter domainFilter)
{
    public static final int DEFAULT_ROW_LIMIT = 400;
    public static final int MAX_ROW_LIMIT = 5000;

    public ReportRequest(
            ReportDefinition definition,
            LocalDate startDate,
            LocalDate endDate,
            ReportFundOption fund,
            int rowLimit)
    {
        this(definition, startDate, endDate, fund, rowLimit, ReportDomainFilter.NONE);
    }

    public ReportRequest
    {
        definition = Objects.requireNonNull(definition, "definition");
        endDate = Objects.requireNonNull(endDate, "endDate");
        fund = fund == null ? ReportFundOption.ALL_FUNDS : fund;
        domainFilter = normalizeDomainFilter(definition, domainFilter);

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
        String domain = domainFilter.summary().isBlank() ? "" : " | " + domainFilter.summary();
        return definition.displayName() + " | " + dates + " | " + fundText + domain + rows;
    }

    private static ReportDomainFilter normalizeDomainFilter(
            ReportDefinition definition,
            ReportDomainFilter supplied)
    {
        ReportDomainFilter value = supplied == null ? ReportDomainFilter.NONE : supplied;
        return switch (definition.domainFilterMode())
        {
            case NONE -> {
                if (!(value instanceof ReportDomainFilter.None))
                {
                    throw new IllegalArgumentException(
                            definition.displayName() + " does not support domain filters.");
                }
                yield ReportDomainFilter.NONE;
            }
            case ACCOUNT -> {
                if (value instanceof ReportDomainFilter.None)
                {
                    yield new ReportDomainFilter.AccountSelection(null);
                }
                if (!(value instanceof ReportDomainFilter.AccountSelection))
                {
                    throw new IllegalArgumentException(
                            definition.displayName() + " requires an account filter.");
                }
                yield value;
            }
            case FIXED_ASSET -> {
                if (value instanceof ReportDomainFilter.None)
                {
                    yield new ReportDomainFilter.FixedAssetSelection(null, null, null);
                }
                if (!(value instanceof ReportDomainFilter.FixedAssetSelection))
                {
                    throw new IllegalArgumentException(
                            definition.displayName() + " requires fixed-asset filters.");
                }
                yield value;
            }
            case INVENTORY -> {
                if (value instanceof ReportDomainFilter.None)
                {
                    yield new ReportDomainFilter.InventorySelection(null, null, null);
                }
                if (!(value instanceof ReportDomainFilter.InventorySelection))
                {
                    throw new IllegalArgumentException(
                            definition.displayName() + " requires inventory filters.");
                }
                yield value;
            }
        };
    }
}
