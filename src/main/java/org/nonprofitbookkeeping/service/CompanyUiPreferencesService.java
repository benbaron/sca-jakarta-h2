package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.CompanyUiPreferences;
import org.nonprofitbookkeeping.report.ReportDefinition;
import org.nonprofitbookkeeping.repository.CompanyUiPreferenceRepository;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Validated company-owned UI preference and workspace-state boundary. */
public final class CompanyUiPreferencesService
{
    private static final String REPORTING_DEFAULTS_PREFIX = "reportingDefaults.";
    private static final String DEFAULT_REPORT_KEY = REPORTING_DEFAULTS_PREFIX + "defaultReportId";
    private static final String DEFAULT_EXPORT_FORMAT_KEY = REPORTING_DEFAULTS_PREFIX + "defaultExportFormat";

    private final CompanyUiPreferenceRepository repository;
    private final AuthorizationGuard authorizationGuard;

    public CompanyUiPreferencesService(CompanyUiPreferenceRepository repository)
    {
        this(repository, null);
    }

    public CompanyUiPreferencesService(
            CompanyUiPreferenceRepository repository,
            AuthorizationGuard authorizationGuard)
    {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorizationGuard = authorizationGuard;
    }

    public CompanyUiPreferences load(String companyCode)
    {
        return repository.findPreferences(normalizeCompanyCode(companyCode))
                .orElseGet(CompanyUiPreferences::defaults);
    }

    public void save(String companyCode, CompanyUiPreferences preferences)
    {
        if (preferences == null)
        {
            throw new IllegalArgumentException("Company UI preferences are required.");
        }
        String normalizedCompanyCode = normalizeCompanyCode(companyCode);
        ServiceAuthorization.require(
                authorizationGuard,
                ApplicationPermission.UI_PREFERENCE_WRITE,
                normalizedCompanyCode,
                "save company UI preferences");
        repository.savePreferences(normalizedCompanyCode, preferences);
    }

    /**
     * Loads the company-owned defaults used only when opening a new Report Library.
     * Missing or stale values fall back to stable application defaults.
     */
    public CompanyReportingDefaults loadReportingDefaults(String companyCode)
    {
        Map<String, String> values = loadState(companyCode, REPORTING_DEFAULTS_PREFIX);
        return new CompanyReportingDefaults(
                reportDefinition(values.get(DEFAULT_REPORT_KEY)),
                exportFormat(values.get(DEFAULT_EXPORT_FORMAT_KEY)));
    }

    /** Saves the company-owned opening report and export-format defaults. */
    public void saveReportingDefaults(String companyCode, CompanyReportingDefaults defaults)
    {
        if (defaults == null)
        {
            throw new IllegalArgumentException("Company reporting defaults are required.");
        }
        saveState(companyCode, Map.of(
                DEFAULT_REPORT_KEY, defaults.defaultReport().id(),
                DEFAULT_EXPORT_FORMAT_KEY, defaults.defaultExportFormat().name()));
    }

    public Map<String, String> loadState(String companyCode, String keyPrefix)
    {
        String prefix = normalizeKey(keyPrefix, "keyPrefix");
        return repository.findStateByPrefix(normalizeCompanyCode(companyCode), prefix);
    }

    public void saveState(String companyCode, Map<String, String> values)
    {
        if (values == null || values.isEmpty())
        {
            return;
        }
        Map<String, String> safe = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet())
        {
            safe.put(normalizeKey(entry.getKey(), "state key"), entry.getValue() == null ? "" : entry.getValue());
        }
        String normalizedCompanyCode = normalizeCompanyCode(companyCode);
        boolean reportingDefaults = safe.keySet().stream()
                .anyMatch(key -> key.startsWith(REPORTING_DEFAULTS_PREFIX));
        ServiceAuthorization.require(
                authorizationGuard,
                reportingDefaults
                        ? ApplicationPermission.COMPANY_ADMIN
                        : ApplicationPermission.UI_PREFERENCE_WRITE,
                normalizedCompanyCode,
                reportingDefaults ? "save company reporting defaults" : "save company UI state");
        repository.saveState(normalizedCompanyCode, safe);
    }

    static String normalizeCompanyCode(String companyCode)
    {
        String value = companyCode == null ? "" : companyCode.trim().toUpperCase(Locale.ROOT);
        if (value.isBlank())
        {
            return "DEFAULT";
        }
        if (value.length() > 64)
        {
            throw new IllegalArgumentException("Company code must be at most 64 characters.");
        }
        return value;
    }

    private static ReportDefinition reportDefinition(String reportId)
    {
        if (reportId != null && !reportId.isBlank())
        {
            String wanted = reportId.strip();
            for (ReportDefinition definition : ReportDefinition.catalog())
            {
                if (definition.id().equalsIgnoreCase(wanted))
                {
                    return definition;
                }
            }
        }
        return CompanyReportingDefaults.defaults().defaultReport();
    }

    private static FinancialReportExportFormat exportFormat(String value)
    {
        if (value != null && !value.isBlank())
        {
            try
            {
                return FinancialReportExportFormat.valueOf(value.strip().toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException ex)
            {
                // A removed/invalid saved format must not prevent Report Library startup.
            }
        }
        return CompanyReportingDefaults.defaults().defaultExportFormat();
    }

    private static String normalizeKey(String key, String label)
    {
        String value = key == null ? "" : key.trim();
        if (value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (value.length() > 240)
        {
            throw new IllegalArgumentException(label + " must be at most 240 characters.");
        }
        return value;
    }
}
