package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.service.CompanyAdminService;
import org.nonprofitbookkeeping.service.CompanyChartView;
import org.nonprofitbookkeeping.service.CompanyCommand;
import org.nonprofitbookkeeping.service.CompanyView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Coordinates authoritative H2 company lifecycle operations with the
 * non-authoritative recent-company selection convenience.
 */
final class CompanySessionController
{
    @FunctionalInterface
    interface CompanyChangeGuard
    {
        boolean allow(String currentCompanyCode, String requestedCompanyCode);
    }

    record SelectionResult(boolean selected, String message, CompanyView company)
    {
    }

    private final UiSessionState sessionState;
    private final AppStateStore stateStore;
    private final Supplier<CompanyAdminService> serviceSupplier;
    private CompanyChangeGuard changeGuard = (current, requested) -> true;

    CompanySessionController(
            UiSessionState sessionState,
            AppStateStore stateStore,
            Supplier<CompanyAdminService> serviceSupplier)
    {
        this.sessionState = Objects.requireNonNull(sessionState, "sessionState");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.serviceSupplier = Objects.requireNonNull(serviceSupplier, "serviceSupplier");
    }

    void setChangeGuard(CompanyChangeGuard changeGuard)
    {
        this.changeGuard = Objects.requireNonNull(changeGuard, "changeGuard");
    }

    CompanyView restoreAuthoritativeSelection()
    {
        stateStore.loadMultiCompany().ifPresent(sessionState::setMultiCompany);
        CompanyView resolved = service().resolveActiveCompany(
                sessionState.multiCompany().activeCompanyCode());
        applySelectionState(resolved.code());
        return resolved;
    }

    SelectionResult select(String companyCode)
    {
        try
        {
            CompanyView requested = service().requireActiveCompany(companyCode);
            String current = sessionState.multiCompany().activeCompanyCode();
            if (requested.code().equalsIgnoreCase(current))
            {
                applySelectionState(requested.code());
                return new SelectionResult(true, "Company " + requested.code() + " is already active.", requested);
            }
            if (!changeGuard.allow(current, requested.code()))
            {
                return new SelectionResult(false, "Company change cancelled; unsaved edits remain open.", requested);
            }
            applySelectionState(requested.code());
            return new SelectionResult(true, "Selected active company " + requested.code() + ".", requested);
        }
        catch (RuntimeException ex)
        {
            return new SelectionResult(false, UiErrors.safeMessage(ex), null);
        }
    }

    CompanyView save(CompanyCommand command)
    {
        CompanyView current = service().resolveActiveCompany(
                sessionState.multiCompany().activeCompanyCode());
        boolean editingCurrent = command.id() != null && Objects.equals(command.id(), current.id());
        CompanyView saved = service().save(command, current.code());
        if (editingCurrent)
        {
            applySelectionState(saved.code());
        }
        return saved;
    }

    SelectionResult createAndSelect(CompanyCommand command)
    {
        try
        {
            if (command.id() != null)
            {
                throw new IllegalArgumentException("A new company command must not contain an ID.");
            }
            CompanyView saved = service().save(
                    command,
                    sessionState.multiCompany().activeCompanyCode());
            String current = sessionState.multiCompany().activeCompanyCode();
            if (!saved.code().equalsIgnoreCase(current) && !changeGuard.allow(current, saved.code()))
            {
                return new SelectionResult(
                        false,
                        "Created company " + saved.code() + ", but selection was cancelled because unsaved edits remain open.",
                        saved);
            }
            applySelectionState(saved.code());
            return new SelectionResult(true, "Created and selected company " + saved.code() + ".", saved);
        }
        catch (RuntimeException ex)
        {
            return new SelectionResult(false, UiErrors.safeMessage(ex), null);
        }
    }

    List<CompanyView> listCompanies()
    {
        return service().listCompanyViews();
    }

    List<CompanyView> listActiveCompanies()
    {
        return service().listActiveCompanyViews();
    }

    List<CompanyChartView> listCompanyCharts(long companyId)
    {
        return service().listCompanyCharts(companyId);
    }

    CompanyChartView assignActiveChart(long companyId, long chartId)
    {
        return service().assignActiveChart(companyId, chartId);
    }

    private CompanyAdminService service()
    {
        return serviceSupplier.get();
    }

    private void applySelectionState(String selectedCode)
    {
        List<String> activeCodes = service().listActiveCompanyViews().stream()
                .map(CompanyView::code)
                .toList();
        List<String> recents = new ArrayList<>();
        recents.add(selectedCode);
        for (String recent : sessionState.multiCompany().recentCompanyCodes())
        {
            if (recent == null || recent.equalsIgnoreCase(selectedCode))
            {
                continue;
            }
            activeCodes.stream()
                    .filter(code -> code.equalsIgnoreCase(recent))
                    .findFirst()
                    .filter(code -> !recents.contains(code))
                    .ifPresent(recents::add);
        }
        for (String activeCode : activeCodes)
        {
            if (!recents.contains(activeCode))
            {
                recents.add(activeCode);
            }
        }
        MultiCompanyState next = new MultiCompanyState(selectedCode, List.copyOf(recents));
        sessionState.setMultiCompany(next);
        stateStore.saveMultiCompany(next);
    }
}
