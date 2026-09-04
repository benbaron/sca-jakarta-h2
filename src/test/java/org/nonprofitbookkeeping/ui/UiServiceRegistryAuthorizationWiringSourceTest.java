package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-route guardrails for production current-session authorization composition. */
class UiServiceRegistryAuthorizationWiringSourceTest
{
    @Test
    void serviceBundleOwnsOneCurrentSessionGuardBoundToItsJpa() throws Exception
    {
        String registry = compact(read("src/main/java/org/nonprofitbookkeeping/ui/UiServiceRegistry.java"));

        assertTrue(registry.contains(
                "AuthorizationGuardauthorizationGuard=newAuthorizationGuard(jpa,ApplicationSessionContext.sharedSessionState()::authenticatedUser);"));
        assertTrue(registry.contains("recordServiceBundle(Jpajpa,AuthorizationGuardauthorizationGuard,"));
        assertTrue(registry.contains("nextServices=buildServices(nextJpa);"));
        assertTrue(registry.contains("services=Objects.requireNonNull(preparedServices,\"preparedServices\");"));
    }

    @Test
    void durableProductionBundleUsesGuardedConstructors() throws Exception
    {
        String registry = compact(read("src/main/java/org/nonprofitbookkeeping/ui/UiServiceRegistry.java"));

        assertTrue(registry.contains(
                "newAccountAdminService(jpa,UiServiceRegistry::activeCompanyCode,authorizationGuard)"));
        assertTrue(registry.contains(
                "newFundAdminService(jpa,UiServiceRegistry::activeCompanyCode,authorizationGuard)"));
        assertTrue(registry.contains(
                "newBudgetCategoryAdminService(jpa,UiServiceRegistry::activeCompanyCode,authorizationGuard)"));
        assertTrue(registry.contains(
                "newBudgetPlanService(jpa,UiServiceRegistry::activeCompanyCode,authorizationGuard)"));
        assertTrue(registry.contains("newBankConfigurationService(jpa,authorizationGuard)"));
        assertTrue(registry.contains(
                "newFixedAssetService(jpa,transactionEntry,UiServiceRegistry::activeCompanyCode,authorizationGuard)"));
        assertTrue(registry.contains(
                "newInventoryService(jpa,transactionEntry,transactionCorrection,UiServiceRegistry::activeCompanyCode,authorizationGuard)"));
        assertTrue(registry.contains("newCompanyAdminService(jpa,authorizationGuard)"));
        assertTrue(registry.contains(
                "newUserAdminService(jpa,UiServiceRegistry::activeCompanyCode,authorizationGuard)"));
        assertTrue(registry.contains(
                "newTransactionEntryService(jpa,UiServiceRegistry::activeCompanyCode,authorizationGuard)"));
        assertTrue(registry.contains(
                "newTransactionCorrectionService(jpa,UiServiceRegistry::activeCompanyCode,authorizationGuard)"));
        assertTrue(registry.contains("newBankReconciliationWorkspaceService(jpa,authorizationGuard)"));
        assertTrue(registry.contains("newPeriodCloseRangeService(jpa,authorizationGuard)"));
    }

    @Test
    void onDemandProductionMutationsReuseCurrentBundleGuard() throws Exception
    {
        String registry = compact(read("src/main/java/org/nonprofitbookkeeping/ui/UiServiceRegistry.java"));

        assertTrue(registry.contains(
                "newCoaCsvImportService(current.jpa(),UiServiceRegistry::activeCompanyCode,current.authorizationGuard())"));
        assertTrue(registry.contains(
                "newChartOfAccountsJsonImportService(current.jpa(),UiServiceRegistry::activeCompanyCode,current.authorizationGuard())"));
        assertTrue(registry.contains(
                "newBankStatementReviewService(current.jpa(),current.authorizationGuard())"));
        assertTrue(registry.contains("newBankCsvReviewService(current.jpa(),current.authorizationGuard())"));
        assertTrue(registry.contains(
                "newBankCsvMappingProfileService(current.jpa(),current.authorizationGuard())"));
        assertTrue(registry.contains(
                "newNormalizedBankCsvReviewService(current.jpa(),current.authorizationGuard())"));
        assertTrue(registry.contains(
                "newCompanyUiPreferencesService(newJdbcCompanyUiPreferenceRepository(UiDataSources.forCurrentSessionDatabase()),current.authorizationGuard())"));
        assertTrue(registry.contains("newSecurityAdminService(current.jpa(),current.authorizationGuard())"));
        assertTrue(registry.contains(
                "newReviewedStatementAcceptanceService(current.jpa(),current.transactionEntry(),UiServiceRegistry::activeCompanyCode,current.authorizationGuard())"));
        assertTrue(registry.contains(
                "newSclxImportCommitService(current.jpa(),()->fixedCompanyCode,current.authorizationGuard())"));
    }

    @Test
    void mappedCsvKeepsBankStatementReviewAsSingleAuthorizationOwner() throws Exception
    {
        String service = compact(read(
                "src/main/java/org/nonprofitbookkeeping/interchange/bank/BankCsvReviewService.java"));

        assertTrue(service.contains(
                "publicBankCsvReviewService(Jpajpa,AuthorizationGuardauthorizationGuard)"));
        assertTrue(service.contains(
                "newBankStatementReviewService(jpa,authorizationGuard)"));
        assertTrue(service.contains("returnreviewService.commit("));
        assertFalse(service.contains("privatefinalAuthorizationGuardauthorizationGuard"));
    }

    @Test
    void productionWorkspaceStillRoutesMutationSuppliersThroughRegistry() throws Exception
    {
        String factory = compact(read(
                "src/main/java/org/nonprofitbookkeeping/ui/WorkspaceServicesFactory.java"));

        assertTrue(factory.contains("UiServiceRegistry::sclxImportCommit"));
        assertTrue(factory.contains("UiServiceRegistry::bankConfiguration"));
        assertTrue(factory.contains("UiServiceRegistry::bankStatementReview"));
        assertTrue(factory.contains("UiServiceRegistry::bankCsvReview"));
        assertTrue(factory.contains("UiServiceRegistry::bankCsvMappingProfiles"));
        assertTrue(factory.contains("UiServiceRegistry::normalizedBankCsvReview"));
        assertTrue(factory.contains("UiServiceRegistry::reviewedStatementAcceptance"));
    }

    private static String read(String path) throws Exception
    {
        return Files.readString(Path.of(path));
    }

    private static String compact(String source)
    {
        return source.replaceAll("\\s+", "");
    }
}
