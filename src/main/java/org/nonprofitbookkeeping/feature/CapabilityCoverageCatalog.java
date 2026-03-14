package org.nonprofitbookkeeping.feature;

import java.util.EnumMap;
import java.util.Map;

/**
 * Cross-layer coverage map (UI/model/actions/tests) for requested capabilities.
 */
public final class CapabilityCoverageCatalog
{
    private static final Map<Capability, CapabilityCoverage> COVERAGE = new EnumMap<>(Capability.class);

    static
    {
        COVERAGE.put(Capability.MULTI_COMPANY,
                new CapabilityCoverage(
                        Capability.MULTI_COMPANY,
                        "Main shell company context + workspace state",
                        "org.nonprofitbookkeeping.model.MultiCompanyState",
                        "org.nonprofitbookkeeping.app.AppActionId.SWITCH_COMPANY",
                        "org.nonprofitbookkeeping.feature.CapabilityCoverageCatalogTest"));

        COVERAGE.put(Capability.IMPORT_EXPORT,
                new CapabilityCoverage(
                        Capability.IMPORT_EXPORT,
                        "File/Tools import-export entry points",
                        "org.nonprofitbookkeeping.model.ImportExportState",
                        "org.nonprofitbookkeeping.app.AppActionId.OPEN_IMPORT_EXPORT",
                        "org.nonprofitbookkeeping.feature.CapabilityCoverageCatalogTest"));

        COVERAGE.put(Capability.PREFERENCE_AND_STATE_SAVING,
                new CapabilityCoverage(
                        Capability.PREFERENCE_AND_STATE_SAVING,
                        "Settings panel",
                        "org.nonprofitbookkeeping.model.AppPreferencesState",
                        "org.nonprofitbookkeeping.app.AppActionId.SAVE_PREFERENCES",
                        "org.nonprofitbookkeeping.feature.CapabilityCoverageCatalogTest"));

        COVERAGE.put(Capability.JAVA_FX_AND_NATIVE_THEME_SAVING,
                new CapabilityCoverage(
                        Capability.JAVA_FX_AND_NATIVE_THEME_SAVING,
                        "Settings panel theme controls",
                        "org.nonprofitbookkeeping.model.UiThemePreference",
                        "org.nonprofitbookkeeping.app.AppActionId.APPLY_THEME",
                        "org.nonprofitbookkeeping.feature.CapabilityCoverageCatalogTest"));

        COVERAGE.put(Capability.HELP,
                new CapabilityCoverage(
                        Capability.HELP,
                        "Help menu",
                        "org.nonprofitbookkeeping.model.HelpState",
                        "org.nonprofitbookkeeping.app.AppActionId.OPEN_HELP",
                        "org.nonprofitbookkeeping.feature.CapabilityCoverageCatalogTest"));

        COVERAGE.put(Capability.WIZARD_SUPPORT,
                new CapabilityCoverage(
                        Capability.WIZARD_SUPPORT,
                        "Guided setup flow",
                        "org.nonprofitbookkeeping.model.WizardState",
                        "org.nonprofitbookkeeping.app.AppActionId.START_WIZARD",
                        "org.nonprofitbookkeeping.feature.CapabilityCoverageCatalogTest"));

        COVERAGE.put(Capability.PLUGINS,
                new CapabilityCoverage(
                        Capability.PLUGINS,
                        "Tools plugin manager",
                        "org.nonprofitbookkeeping.model.PluginState",
                        "org.nonprofitbookkeeping.app.AppActionId.MANAGE_PLUGINS",
                        "org.nonprofitbookkeeping.feature.CapabilityCoverageCatalogTest"));

        COVERAGE.put(Capability.USER_PRIVILEGE_LEVELS,
                new CapabilityCoverage(
                        Capability.USER_PRIVILEGE_LEVELS,
                        "Role-aware panel/action gating",
                        "org.nonprofitbookkeeping.model.UserPrivilegeLevel",
                        "org.nonprofitbookkeeping.app.AppActionId.ASSIGN_PRIVILEGE",
                        "org.nonprofitbookkeeping.feature.CapabilityCoverageCatalogTest"));

        COVERAGE.put(Capability.CHART_OF_ACCOUNTS_IMPORT_EXPORT,
                new CapabilityCoverage(
                        Capability.CHART_OF_ACCOUNTS_IMPORT_EXPORT,
                        "Chart of Accounts panel import/export actions",
                        "org.nonprofitbookkeeping.model.ChartOfAccountsTransferFormat",
                        "org.nonprofitbookkeeping.app.AppActionId.IMPORT_CHART_OF_ACCOUNTS",
                        "org.nonprofitbookkeeping.feature.CapabilityCoverageCatalogTest"));

        COVERAGE.put(Capability.BANKING_IMPORT_EXPORT_OFX_QFX,
                new CapabilityCoverage(
                        Capability.BANKING_IMPORT_EXPORT_OFX_QFX,
                        "Ledger/bank import workflow",
                        "org.nonprofitbookkeeping.model.BankingDataFormat",
                        "org.nonprofitbookkeeping.app.AppActionId.IMPORT_BANK_DATA",
                        "org.nonprofitbookkeeping.feature.CapabilityCoverageCatalogTest"));
    }

    private CapabilityCoverageCatalog()
    {
    }

    public static Map<Capability, CapabilityCoverage> all()
    {
        return Map.copyOf(COVERAGE);
    }
}
