package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.MultiCompanyState;

import java.util.Optional;

/**
 * Persistence contract for shell preferences and multi-company context.
 */
public interface AppStateStore
{
    Optional<AppPreferencesState> loadPreferences();

    Optional<MultiCompanyState> loadMultiCompany();

    void savePreferences(AppPreferencesState state);

    void saveMultiCompany(MultiCompanyState state);
}
