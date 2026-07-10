package org.nonprofitbookkeeping.repository;

import org.nonprofitbookkeeping.model.CompanyUiPreferences;

import java.util.Map;
import java.util.Optional;

/** H2 authority for company-specific display preferences and workspace state. */
public interface CompanyUiPreferenceRepository
{
    Optional<CompanyUiPreferences> findPreferences(String companyCode);

    void savePreferences(String companyCode, CompanyUiPreferences preferences);

    Map<String, String> findStateByPrefix(String companyCode, String keyPrefix);

    void saveState(String companyCode, Map<String, String> values);
}
