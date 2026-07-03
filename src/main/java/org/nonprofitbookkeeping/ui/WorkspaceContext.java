package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.persistence.DatabaseLocationService;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Objects;

/** Observable runtime context owned by the production workspace shell. */
public final class WorkspaceContext
{
    private final ObjectProperty<Path> activeDatabasePath = new SimpleObjectProperty<>();
    private final ReadOnlyStringWrapper activeCompanyCode = new ReadOnlyStringWrapper();
    private final ObjectProperty<LocalDate> activePeriodDate = new SimpleObjectProperty<>();
    private final ObjectProperty<RuntimeException> databaseFailure = new SimpleObjectProperty<>();

    public WorkspaceContext(Path databasePath, String companyCode, LocalDate periodDate)
    {
        setActiveDatabasePath(databasePath);
        setActiveCompanyCode(companyCode);
        setActivePeriodDate(periodDate);
    }

    public static WorkspaceContext fromSession(UiSessionState sessionState)
    {
        Objects.requireNonNull(sessionState, "sessionState");
        return new WorkspaceContext(
                DatabaseLocationService.resolveDatabasePath(sessionState.databaseSelection().activeDatabasePath()),
                sessionState.multiCompany().activeCompanyCode(),
                ActivePeriodContext.get());
    }

    public Path activeDatabasePath()
    {
        return activeDatabasePath.get();
    }

    public ReadOnlyObjectProperty<Path> activeDatabasePathProperty()
    {
        return activeDatabasePath;
    }

    public String activeCompanyCode()
    {
        return activeCompanyCode.get();
    }

    public ReadOnlyStringProperty activeCompanyCodeProperty()
    {
        return activeCompanyCode.getReadOnlyProperty();
    }

    public LocalDate activePeriodDate()
    {
        return activePeriodDate.get();
    }

    public ReadOnlyObjectProperty<LocalDate> activePeriodDateProperty()
    {
        return activePeriodDate;
    }

    public RuntimeException databaseFailure()
    {
        return databaseFailure.get();
    }

    public ReadOnlyObjectProperty<RuntimeException> databaseFailureProperty()
    {
        return databaseFailure;
    }

    public boolean databaseAvailable()
    {
        return databaseFailure.get() == null;
    }

    void setActiveDatabasePath(Path databasePath)
    {
        activeDatabasePath.set(Objects.requireNonNull(databasePath, "databasePath"));
    }

    void applyDatabaseSelection(DatabaseSelectionState selection)
    {
        setActiveDatabasePath(DatabaseLocationService.resolveDatabasePath(
                Objects.requireNonNull(selection, "selection").activeDatabasePath()));
    }

    void setActiveCompanyCode(String companyCode)
    {
        activeCompanyCode.set(Objects.requireNonNull(companyCode, "companyCode"));
    }

    void applyMultiCompany(MultiCompanyState multiCompany)
    {
        setActiveCompanyCode(Objects.requireNonNull(multiCompany, "multiCompany").activeCompanyCode());
    }

    void setActivePeriodDate(LocalDate periodDate)
    {
        activePeriodDate.set(Objects.requireNonNull(periodDate, "periodDate"));
    }

    void setDatabaseFailure(RuntimeException failure)
    {
        databaseFailure.set(failure);
    }
}
