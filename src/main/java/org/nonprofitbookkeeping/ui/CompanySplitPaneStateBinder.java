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
        String company = activeCompanyCode();
        CompanyUiPreferencesService service = UiServiceRegistry.companyUiPreferences();
        String stateKey = "ui.split." + safeKey(stableKey) + ".divider";
        Map<String, String> state = service.loadState(company, stateKey);
        split.setDividerPositions(clamp(parseDouble(state.get(stateKey), defaultPosition)));

        PauseTransition delay = new PauseTransition(Duration.millis(350));
        delay.setOnFinished(event -> service.saveState(
                company,
                Map.of(stateKey, Double.toString(split.getDividers().get(0).getPosition()))));
        split.getDividers().get(0).positionProperty().addListener(
                (observable, oldPosition, newPosition) -> delay.playFromStart());
        split.getProperties().put(BINDING_PROPERTY, delay);
    }

    private static String activeCompanyCode()
    {
        String company = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
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

    private static double clamp(double value)
    {
        return Math.max(0.20, Math.min(0.80, value));
    }
}
