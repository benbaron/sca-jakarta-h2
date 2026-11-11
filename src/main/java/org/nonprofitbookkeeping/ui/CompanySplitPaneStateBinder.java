package org.nonprofitbookkeeping.ui;

import javafx.animation.PauseTransition;
import javafx.scene.control.SplitPane;
import javafx.util.Duration;
import org.nonprofitbookkeeping.service.CompanyUiPreferencesService;

import java.util.Locale;
import java.util.Map;

/** Persists one production split-pane divider through company-owned H2 UI state. */
final class CompanySplitPaneStateBinder
{
    private static final String BINDING_PROPERTY = "sca.companySplitPaneStateBinding";

    private CompanySplitPaneStateBinder()
    {
    }

    static void bind(SplitPane split, String stableKey, double defaultPosition)
    {
        bind(split, stableKey, defaultPosition, UiServiceRegistry.companyUiPreferences(), MainWindow.sharedSessionState());
    }

    static void bind(SplitPane split, String stableKey, double defaultPosition,
                     CompanyUiPreferencesService service, UiSessionState session)
    {
        bind(split, stableKey, defaultPosition, service, session, .20, .80);
    }

    static void bindHeader(SplitPane split, String stableKey, double defaultPosition)
    {
        bind(split, stableKey, defaultPosition, UiServiceRegistry.companyUiPreferences(),
                MainWindow.sharedSessionState(), .08, .85);
    }

    private static void bind(SplitPane split, String stableKey, double defaultPosition,
                             CompanyUiPreferencesService service, UiSessionState session, double minimum, double maximum)
    {
        String company = activeCompanyCode(session);
        UiStateWriteContext context = UiStateWriteContext.capture(session, company);
        String stateKey = "ui.split." + safeKey(stableKey) + ".divider";
        Map<String, String> state = service.loadState(company, stateKey);
        double saved = parseDouble(state.get(stateKey), defaultPosition);
        split.setDividerPositions(Double.isFinite(saved)
                ? Math.max(minimum, Math.min(maximum, saved)) : defaultPosition);

        PauseTransition delay = new PauseTransition(Duration.millis(350));
        delay.setOnFinished(event ->
        {
            if (context.canSave())
            {
                service.saveState(company,
                        Map.of(stateKey, Double.toString(split.getDividers().get(0).getPosition())));
            }
        });
        split.getDividers().get(0).positionProperty().addListener(
                (observable, oldPosition, newPosition) -> delay.playFromStart());
        split.getProperties().put(BINDING_PROPERTY, delay);
    }

    private static String activeCompanyCode(UiSessionState session)
    {
        String company = session.multiCompany().activeCompanyCode();
        return company == null || company.isBlank() ? "DEFAULT" : company.trim().toUpperCase(Locale.ROOT);
    }

    private static String safeKey(String value)
    {
        return value.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private static double parseDouble(String value, double fallback)
    {
        try
        {
            return value == null || value.isBlank() ? fallback : Double.parseDouble(value);
        }
        catch (NumberFormatException ex)
        {
            return fallback;
        }
    }

}
