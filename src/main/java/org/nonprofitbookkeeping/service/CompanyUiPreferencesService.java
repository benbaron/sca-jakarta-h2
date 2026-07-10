package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.CompanyUiPreferences;
import org.nonprofitbookkeeping.repository.CompanyUiPreferenceRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/** Validated company-owned UI preference and workspace-state boundary. */
public final class CompanyUiPreferencesService
{
    private final CompanyUiPreferenceRepository repository;

    public CompanyUiPreferencesService(CompanyUiPreferenceRepository repository)
    {
        this.repository = repository;
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
        repository.savePreferences(normalizeCompanyCode(companyCode), preferences);
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
        repository.saveState(normalizeCompanyCode(companyCode), safe);
    }

    static String normalizeCompanyCode(String companyCode)
    {
        String value = companyCode == null ? "" : companyCode.trim().toUpperCase();
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
