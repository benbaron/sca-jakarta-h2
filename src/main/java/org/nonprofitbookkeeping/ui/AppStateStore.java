package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.ViewPresetState;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for shell preferences and multi-company context.
 */
public interface AppStateStore
{
    Optional<AppPreferencesState> loadPreferences();

    Optional<MultiCompanyState> loadMultiCompany();


    default Optional<DatabaseSelectionState> loadDatabaseSelection()
    {
        return Optional.empty();
    }

    void savePreferences(AppPreferencesState state);

    default List<ViewPresetState> loadViewPresets()
    {
        return List.of();
    }


    void saveMultiCompany(MultiCompanyState state);


    default void saveDatabaseSelection(DatabaseSelectionState state)
    {
    }


    default void saveViewPresets(List<ViewPresetState> presets)
    {
    }

}
