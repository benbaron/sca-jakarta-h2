package org.nonprofitbookkeeping.report;

import org.nonprofitbookkeeping.service.CompanyView;
import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;

/** Active-company headings and fiscal context used only at the report presentation boundary. */
public record ReportPresentationMetadata(
        String parentOrganization,
        String companyName,
        String legalName,
        String branchType,
        String currency,
        int fiscalYearStartMonth,
        int fiscalYearStartDay)
{
    public static final ReportPresentationMetadata EMPTY =
            new ReportPresentationMetadata("", "", "", "", "", 1, 1);

    public ReportPresentationMetadata
    {
        parentOrganization = clean(parentOrganization);
        companyName = clean(companyName);
        legalName = clean(legalName);
        branchType = clean(branchType);
        currency = clean(currency);
        fiscalYearStartMonth = fiscalYearStartMonth < 1 || fiscalYearStartMonth > 12
                ? 1 : fiscalYearStartMonth;
        fiscalYearStartDay = fiscalYearStartDay < 1 || fiscalYearStartDay > 31
                ? 1 : fiscalYearStartDay;
    }

    public static ReportPresentationMetadata from(CompanyView company)
    {
        if (company == null)
        {
            return EMPTY;
        }
        return new ReportPresentationMetadata(
                company.parentOrganization(),
                company.displayName(),
                company.legalName(),
                company.branchType(),
                company.defaultCurrency(),
                company.fiscalYearStartMonth(),
                company.fiscalYearStartDay());
    }

    public String organizationHeading()
    {
        return firstNonBlank(parentOrganization, legalName, companyName);
    }

    public String companyHeading()
    {
        return firstNonBlank(companyName, branchType, legalName, parentOrganization);
    }

    public String exchequerReportHeading()
    {
        String owner = firstNonBlank(legalName, parentOrganization, companyName);
        return owner.isBlank() ? "EXCHEQUER REPORT" : owner + " EXCHEQUER REPORT";
    }

    public String periodHeading(
            LocalDate start,
            LocalDate end,
            FinancialReportDisplayFormat format)
    {
        FinancialReportDisplayFormat display = format == null
                ? FinancialReportDisplayFormat.plain() : format;
        String quarter = end == null ? "" : "Q" + fiscalQuarter(end) + " Report";
        String dates = start == null || end == null
                ? ""
                : display.formatDate(start) + " to " + display.formatDate(end);
        if (!quarter.isBlank() && !dates.isBlank())
        {
            return quarter + " — " + dates;
        }
        return quarter.isBlank() ? dates : quarter;
    }

    public int fiscalQuarter(LocalDate date)
    {
        if (date == null)
        {
            return 1;
        }
        LocalDate start = fiscalYearStart(date);
        int monthOffset = (date.getYear() - start.getYear()) * 12
                + date.getMonthValue() - start.getMonthValue();
        if (date.getDayOfMonth() < start.getDayOfMonth())
        {
            monthOffset--;
        }
        return Math.max(1, Math.min(4, monthOffset / 3 + 1));
    }

    private LocalDate fiscalYearStart(LocalDate date)
    {
        MonthDay configured = safeMonthDay(fiscalYearStartMonth, fiscalYearStartDay);
        int year = date.getYear();
        LocalDate candidate = configured.atYear(year);
        if (date.isBefore(candidate))
        {
            candidate = configured.atYear(year - 1);
        }
        return candidate;
    }

    private static MonthDay safeMonthDay(int month, int day)
    {
        int candidate = day;
        while (candidate > 28)
        {
            try
            {
                return MonthDay.of(month, candidate);
            }
            catch (DateTimeException ignored)
            {
                candidate--;
            }
        }
        return MonthDay.of(month, candidate);
    }

    private static String firstNonBlank(String... values)
    {
        for (String value : values)
        {
            if (value != null && !value.isBlank())
            {
                return value;
            }
        }
        return "";
    }

    private static String clean(String value)
    {
        return value == null ? "" : value.trim();
    }
}
