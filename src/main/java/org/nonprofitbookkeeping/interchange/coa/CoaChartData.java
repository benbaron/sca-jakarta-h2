package org.nonprofitbookkeeping.interchange.coa;

import org.nonprofitbookkeeping.model.ChartStatus;

import java.util.List;

/** Framework-independent Chart of Accounts document payload. */
public record CoaChartData(
        SourceFamily sourceFamily,
        String sourceVersion,
        String name,
        String chartVersion,
        ChartStatus status,
        String currency,
        List<CoaAccountData> accounts)
{
    public CoaChartData
    {
        if (sourceFamily == null)
        {
            throw new IllegalArgumentException("sourceFamily is required");
        }
        sourceVersion = sourceVersion == null ? "" : sourceVersion.trim();
        name = name == null ? "" : name.trim();
        chartVersion = chartVersion == null ? "" : chartVersion.trim();
        currency = currency == null ? "" : currency.trim().toUpperCase();
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
    }

    public enum SourceFamily
    {
        DONOR_COMPATIBILITY,
        SCA_COA_1_0
    }
}
