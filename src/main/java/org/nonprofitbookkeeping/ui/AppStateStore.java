package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.ViewPresetState;
import org.nonprofitbookkeeping.model.WorkspaceDividerState;

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

    default Optional<WorkspaceDividerState> loadWorkspaceDividers()
    {
        return Optional.empty();
    }

    default List<ViewPresetState> loadViewPresets()
    {
        return List.of();
    }


    void saveMultiCompany(MultiCompanyState state);


    default void saveWorkspaceDividers(WorkspaceDividerState state)
    {
    }


    default void saveDatabaseSelection(DatabaseSelectionState state)
    {
    }

    /**
     * Persists the database and active-company selection as one session choice.
     * Production stores should override this when they can commit both values in
     * one physical write.
     */
    default void saveDatabaseSession(DatabaseSelectionState database, MultiCompanyState company)
    {
        saveDatabaseSelection(database);
        saveMultiCompany(company);
    }


    default void saveViewPresets(List<ViewPresetState> presets)
    {
    }

}
