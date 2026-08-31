package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyUiPreferences;
import org.nonprofitbookkeeping.model.DateDisplayFormat;
import org.nonprofitbookkeeping.model.MoneyPrintFormat;
import org.nonprofitbookkeeping.persistence.DatabaseMigrationService;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.report.ReportDefinition;
import org.nonprofitbookkeeping.repository.JdbcCompanyUiPreferenceRepository;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyAdministrationAuthorizationIntegrationTest
{
    @Test
    void viewerAndAccountantCannotMutateCompanyAdministration(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("company-admin-read-only-authorization")))
        {
            CompanyAdminService setup = new CompanyAdminService(jpa);
            CompanyView company = setup.requireActiveCompany("DEFAULT");
            ChartIds charts = seedOwnedCharts(jpa, company.id());

            CompanyAdminService viewer = guardedCompanies(
                    jpa,
                    () -> Optional.of(session("DEFAULT", ReservedSecurityRole.VIEWER)));
            assertThrows(AuthorizationException.class,
                    () -> viewer.save(profile(company, "Viewer Rewrite", true), "DEFAULT"));
            assertThrows(AuthorizationException.class,
                    () -> viewer.assignActiveChart(company.id(), charts.draftId()));

            CompanyAdminService accountant = guardedCompanies(
                    jpa,
                    () -> Optional.of(session("DEFAULT", ReservedSecurityRole.ACCOUNTANT)));
            assertThrows(AuthorizationException.class,
                    () -> accountant.save(profile(company, "Accountant Rewrite", true), "DEFAULT"));
            assertThrows(AuthorizationException.class,
                    () -> accountant.createCompany("ACCOUNTANT-NEW", "Accountant New Company"));

            assertEquals(company.displayName(), companyDisplayName(jpa, company.id()));
            assertEquals(charts.activeId(), activeChartId(jpa, company.id()));
            assertEquals(1L, companyCount(jpa));
        }
    }

    @Test
    void companyAdminTracksRoleCompanySwitchesAndMultiRoleUnion(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("company-admin-role-switching")))
        {
            CompanyView defaultCompany = new CompanyAdminService(jpa).requireActiveCompany("DEFAULT");
            AtomicReference<Optional<AuthenticatedUserSession>> current =
                    new AtomicReference<>(Optional.of(session("DEFAULT", ReservedSecurityRole.ACCOUNTANT)));
            CompanyAdminService companies = guardedCompanies(jpa, current::get);

            assertThrows(AuthorizationException.class,
                    () -> companies.save(profile(defaultCompany, "Denied", true), "DEFAULT"));

            current.set(Optional.of(session("DEFAULT", ReservedSecurityRole.MANAGER)));
            CompanyView managerUpdated = companies.save(
                    profile(defaultCompany, "Manager Updated", true),
                    "DEFAULT");
            assertEquals("Manager Updated", managerUpdated.displayName());

            CompanyView other = companies.save(new CompanyCommand(
                    null,
                    "OTHER",
                    "Other Company",
                    null,
                    null,
                    null,
                    null,
                    true,
                    1,
                    1,
                    "USD"),
                    "DEFAULT");
            assertEquals(2L, companyCount(jpa));

            current.set(Optional.of(session("OTHER", ReservedSecurityRole.ADMIN)));
            CompanyView adminUpdated = companies.save(profile(other, "Admin Updated Other", true), "OTHER");
            assertEquals("Admin Updated Other", adminUpdated.displayName());

            current.set(Optional.of(session(
                    "OTHER",
                    Set.of(ReservedSecurityRole.VIEWER, ReservedSecurityRole.MANAGER))));
            CompanyView unionUpdated = companies.save(profile(adminUpdated, "Union Updated Other", true), "OTHER");
            assertEquals("Union Updated Other", unionUpdated.displayName());

            current.set(Optional.of(session("OTHER", ReservedSecurityRole.MANAGER)));
            assertThrows(AuthorizationException.class,
                    () -> companies.save(profile(managerUpdated, "Wrong Company", true), "OTHER"));
            assertEquals("Manager Updated", companyDisplayName(jpa, defaultCompany.id()));
        }
    }

    @Test
    void authorizationDoesNotBypassCompanyLifecycleOrChartProtections(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("company-admin-domain-protections")))
        {
            CompanyAdminService companies = guardedCompanies(
                    jpa,
                    () -> Optional.of(session("DEFAULT", ReservedSecurityRole.MANAGER)));
            CompanyView defaultCompany = companies.requireActiveCompany("DEFAULT");
            CompanyView other = companies.save(new CompanyCommand(
                    null,
                    "OTHER",
                    "Other Company",
                    null,
                    null,
                    null,
                    null,
                    true,
                    1,
                    1,
                    "USD"),
                    "DEFAULT");
            ChartProtectionIds charts = seedProtectedCharts(jpa, defaultCompany.id(), other.id());

            IllegalStateException currentCompany = assertThrows(
                    IllegalStateException.class,
                    () -> companies.save(profile(defaultCompany, defaultCompany.displayName(), false), "DEFAULT"));
            assertTrue(currentCompany.getMessage().contains("Select another active company"));

            IllegalStateException retired = assertThrows(
                    IllegalStateException.class,
                    () -> companies.assignActiveChart(defaultCompany.id(), charts.retiredId()));
            assertTrue(retired.getMessage().contains("Retired Chart of Accounts"));

            assertThrows(
                    CompanyOwnershipException.class,
                    () -> companies.assignActiveChart(defaultCompany.id(), charts.foreignId()));
            assertTrue(companies.requireActiveCompany("DEFAULT").active());
        }
    }

    @Test
    void reportingDefaultsRequireCompanyAdminWhileViewerRetainsPresentationPreferences(@TempDir Path tempDir)
    {
        Path database = tempDir.resolve("company-admin-reporting-default-authorization");
        try (Jpa jpa = new Jpa(database))
        {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL(DatabaseMigrationService.jdbcUrlFor(database));
            dataSource.setUser("sa");
            dataSource.setPassword("");

            AtomicReference<Optional<AuthenticatedUserSession>> current =
                    new AtomicReference<>(Optional.of(session("DEFAULT", ReservedSecurityRole.VIEWER)));
            CompanyUiPreferencesService preferences = new CompanyUiPreferencesService(
                    new JdbcCompanyUiPreferenceRepository(dataSource),
                    new AuthorizationGuard(jpa, current::get));
            CompanyReportingDefaults reportingDefaults = new CompanyReportingDefaults(
                    ReportDefinition.BALANCE_SHEET,
                    FinancialReportExportFormat.PDF);

            assertThrows(AuthorizationException.class,
                    () -> preferences.saveReportingDefaults("DEFAULT", reportingDefaults));
            assertThrows(AuthorizationException.class,
                    () -> preferences.saveState("DEFAULT", Map.of(
                            "reportingDefaults.defaultReportId", ReportDefinition.BALANCE_SHEET.id())));

            CompanyUiPreferences displayPreferences = new CompanyUiPreferences(
                    "€",
                    MoneyPrintFormat.SYMBOL_SUFFIX,
                    DateDisplayFormat.DAY_MONTH_YEAR);
            preferences.save("DEFAULT", displayPreferences);
            preferences.saveState("DEFAULT", Map.of("companyAdmin.divider", "0.42"));
            assertEquals(displayPreferences, preferences.load("DEFAULT"));
            assertEquals("0.42", preferences.loadState("DEFAULT", "companyAdmin.")
                    .get("companyAdmin.divider"));

            current.set(Optional.of(session("DEFAULT", ReservedSecurityRole.ACCOUNTANT)));
            assertThrows(AuthorizationException.class,
                    () -> preferences.saveReportingDefaults("DEFAULT", reportingDefaults));

            current.set(Optional.of(session("DEFAULT", ReservedSecurityRole.MANAGER)));
            preferences.saveReportingDefaults("DEFAULT", reportingDefaults);
            assertEquals(reportingDefaults, preferences.loadReportingDefaults("DEFAULT"));

            current.set(Optional.of(session("OTHER", ReservedSecurityRole.MANAGER)));
            assertThrows(AuthorizationException.class,
                    () -> preferences.saveReportingDefaults("DEFAULT", CompanyReportingDefaults.defaults()));
            assertEquals(reportingDefaults, preferences.loadReportingDefaults("DEFAULT"));

            current.set(Optional.empty());
            assertThrows(AuthorizationException.class,
                    () -> preferences.saveState("DEFAULT", Map.of("journal.divider.outer.0", "0.5")));
        }
    }

    private static CompanyAdminService guardedCompanies(
            Jpa jpa,
            java.util.function.Supplier<Optional<AuthenticatedUserSession>> currentSession)
    {
        return new CompanyAdminService(jpa, new AuthorizationGuard(jpa, currentSession));
    }

    private static CompanyCommand profile(CompanyView company, String displayName, boolean active)
    {
        return new CompanyCommand(
                company.id(),
                company.code(),
                displayName,
                company.legalName(),
                company.branchType(),
                company.parentOrganization(),
                company.ein(),
                active,
                company.fiscalYearStartMonth(),
                company.fiscalYearStartDay(),
                company.defaultCurrency());
    }

    private static ChartIds seedOwnedCharts(Jpa jpa, long companyId)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = em.find(Company.class, companyId);
            ChartOfAccounts active = chart(company, "Current Chart", "1", ChartStatus.ACTIVE);
            ChartOfAccounts draft = chart(company, "Draft Chart", "2", ChartStatus.DRAFT);
            em.persist(active);
            em.persist(draft);
            em.flush();
            company.setActiveChartOfAccounts(active);
            em.getTransaction().commit();
            return new ChartIds(active.getId(), draft.getId());
        }
    }

    private static ChartProtectionIds seedProtectedCharts(Jpa jpa, long companyId, long otherCompanyId)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = em.find(Company.class, companyId);
            Company other = em.find(Company.class, otherCompanyId);
            ChartOfAccounts retired = chart(company, "Retired Chart", "1", ChartStatus.RETIRED);
            ChartOfAccounts foreign = chart(other, "Foreign Chart", "1", ChartStatus.ACTIVE);
            em.persist(retired);
            em.persist(foreign);
            em.flush();
            em.getTransaction().commit();
            return new ChartProtectionIds(retired.getId(), foreign.getId());
        }
    }

    private static ChartOfAccounts chart(
            Company company,
            String name,
            String version,
            ChartStatus status)
    {
        ChartOfAccounts chart = new ChartOfAccounts();
        chart.setCompany(company);
        chart.setName(name);
        chart.setVersion(version);
        chart.setStatus(status);
        return chart;
    }

    private static long companyCount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(c) from Company c", Long.class).getSingleResult();
        }
    }

    private static String companyDisplayName(Jpa jpa, long companyId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select c.displayName from Company c where c.id = :id", String.class)
                    .setParameter("id", companyId)
                    .getSingleResult();
        }
    }

    private static Long activeChartId(Jpa jpa, long companyId)
    {
        try (EntityManager em = jpa.em())
        {
            Company company = em.find(Company.class, companyId);
            return company.getActiveChartOfAccounts() == null ? null : company.getActiveChartOfAccounts().getId();
        }
    }

    private static AuthenticatedUserSession session(String companyCode, ReservedSecurityRole role)
    {
        return session(companyCode, Set.of(role));
    }

    private static AuthenticatedUserSession session(
            String companyCode,
            Set<ReservedSecurityRole> roles)
    {
        Instant now = Instant.parse("2026-08-31T03:20:00Z");
        return new AuthenticatedUserSession(
                8L,
                "operator",
                "Operator",
                companyCode,
                roles,
                now,
                now);
    }

    private record ChartIds(Long activeId, Long draftId)
    {
    }

    private record ChartProtectionIds(Long retiredId, Long foreignId)
    {
    }
}
