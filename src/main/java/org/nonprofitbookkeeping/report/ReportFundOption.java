package org.nonprofitbookkeeping.report;

import org.nonprofitbookkeeping.model.Fund;

/** Stable fund identity used by Report Library requests. */
public record ReportFundOption(Long id, String code, String name)
{
    public static final ReportFundOption ALL_FUNDS = new ReportFundOption(null, null, "All Funds");

    public ReportFundOption
    {
        if (id == null)
        {
            code = null;
            name = "All Funds";
        }
        else
        {
            code = requireText(code, "code");
            name = requireText(name, "name");
        }
    }

    public static ReportFundOption from(Fund fund)
    {
        if (fund == null || fund.getId() == null)
        {
            throw new IllegalArgumentException("A persisted fund is required.");
        }
        return new ReportFundOption(fund.getId(), fund.getCode(), fund.getName());
    }

    public boolean allFunds()
    {
        return id == null;
    }

    public String displayLabel()
    {
        return allFunds() ? name : code + " — " + name;
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }
}
