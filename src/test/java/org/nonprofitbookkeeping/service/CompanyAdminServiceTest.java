package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyAdminServiceTest
{
    @Test
    void savePersistsCompleteProfileByStableIdAcrossRestart(@TempDir Path tempDir)
    {
        Path database = tempDir.resolve("company-lifecycle");
        Long companyId;
        try (Jpa jpa = new Jpa(database))
        {
            CompanyAdminService service = new CompanyAdminService(jpa);
            CompanyView created = service.save(new CompanyCommand(
                    null,
                    "sca-one",
                    "Barony of One",
                    "Barony of One, Incorporated",
                    "Barony",
                    "Kingdom of the Test",
                    "12-3456789",
                    true,
                    7,
                    1,
                    "usd"),
                    "DEFAULT");
            companyId = created.id();
            assertEquals("12-3456789", created.ein());

            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("""
                        insert into company_ui_state (company_code, state_key, state_value)
                        values ('SCA-ONE', 'companyAdmin.divider', '0.55')
                        """).executeUpdate();
                em.createNativeQuery("""
                        insert into period_close_range
                            (id, company_code, start_date, end_date, range_kind, status, closed_by)
                        values
                            (random_uuid(), 'SCA-ONE', DATE '2025-01-01', DATE '2025-01-31', 'CUSTOM', 'CLOSED', 'test')
                        """).executeUpdate();
                em.getTransaction().commit();
            }

            CompanyView updated = service.save(new CompanyCommand(
                    companyId,
                    "SCA-RENAMED",
                    "Barony of One",
                    "Barony of One, Incorporated",
                    "Barony",
                    "Kingdom of the Test",
                    "98-7654321",
                    true,
                    10,
                    15,
                    "CAD"),
                    "DEFAULT");

            assertEquals(companyId, updated.id());
            assertEquals("SCA-RENAMED", updated.code());
            assertEquals("98-7654321", updated.ein());
            assertEquals(10, updated.fiscalYearStartMonth());
            assertEquals(15, updated.fiscalYearStartDay());
            assertEquals("CAD", updated.defaultCurrency());
            assertEquals(1L, service.listCompanyViews().stream()
                    .filter(company -> company.id().equals(companyId))
                    .count());
            try (var em = jpa.em())
            {
                assertEquals(1L, ((Number) em.createNativeQuery(
                                "select count(*) from company_ui_state where company_code = 'SCA-RENAMED'")
                        .getSingleResult()).longValue());
                assertEquals(1L, ((Number) em.createNativeQuery(
                                "select count(*) from period_close_range where company_code = 'SCA-RENAMED'")
                        .getSingleResult()).longValue());
            }
        }

        try (Jpa jpa = new Jpa(database))
        {
            CompanyView reloaded = new CompanyAdminService(jpa)
                    .findCompany("sca-renamed")
                    .orElseThrow();
            assertEquals(companyId, reloaded.id());
            assertEquals("Barony of One, Incorporated", reloaded.legalName());
            assertEquals("Barony", reloaded.branchType());
            assertEquals("Kingdom of the Test", reloaded.parentOrganization());
            assertEquals("98-7654321", reloaded.ein());
            assertEquals("CAD", reloaded.defaultCurrency());
        }
    }

    @Test
    void activeLifecycleProtectsCurrentAndLastActiveCompany(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("company-active-rules")))
        {
            CompanyAdminService service = new CompanyAdminService(jpa);
            CompanyView other = service.createCompany("OTHER", "Other Company");
            CompanyView defaultCompany = service.requireActiveCompany("DEFAULT");

            IllegalStateException currentFailure = assertThrows(IllegalStateException.class, () -> service.save(
                    commandFor(defaultCompany, false),
                    "DEFAULT"));
            assertTrue(currentFailure.getMessage().contains("Select another active company"));

            CompanyView inactiveDefault = service.save(commandFor(defaultCompany, false), "OTHER");
            assertFalse(inactiveDefault.active());
            assertThrows(IllegalStateException.class, () -> service.requireActiveCompany("DEFAULT"));

            IllegalStateException lastFailure = assertThrows(IllegalStateException.class, () -> service.save(
                    commandFor(other, false),
                    null));
            assertTrue(lastFailure.getMessage().contains("At least one company"));
            assertTrue(service.requireActiveCompany("OTHER").active());
        }
    }

    @Test
    void validationAndSelectionRejectFictionalInactiveDuplicateAndOversizedCompanies(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("company-validation")))
        {
            CompanyAdminService service = new CompanyAdminService(jpa);
            CompanyView created = service.createCompany("UNIQUE", "Unique Company");

            assertThrows(IllegalArgumentException.class, () -> service.createCompany("unique", "Duplicate"));
            assertThrows(IllegalArgumentException.class, () -> service.requireActiveCompany("FICTIONAL"));
            assertThrows(IllegalArgumentException.class, () -> service.save(new CompanyCommand(
                    null, "BAD-DATE", "Bad Date", null, null, null, true, 2, 30, "USD"), "DEFAULT"));
            assertThrows(IllegalArgumentException.class, () -> service.save(new CompanyCommand(
                    null, "BAD-CURRENCY", "Bad Currency", null, null, null, true, 1, 1, "ZZZ"), "DEFAULT"));
            IllegalArgumentException einFailure = assertThrows(IllegalArgumentException.class, () -> service.save(
                    new CompanyCommand(
                            null,
                            "BAD-EIN",
                            "Bad EIN",
                            null,
                            null,
                            null,
                            "X".repeat(41),
                            true,
                            1,
                            1,
                            "USD"),
                    "DEFAULT"));
            assertTrue(einFailure.getMessage().contains("EIN must not exceed 40 characters"));

            assertEquals(created.id(), service.requireActiveCompany("unique").id());
            assertEquals("DEFAULT", service.resolveActiveCompany("FICTIONAL").code());
        }
    }

    private static CompanyCommand commandFor(CompanyView company, boolean active)
    {
        return new CompanyCommand(
                company.id(),
                company.code(),
                company.displayName(),
                company.legalName(),
                company.branchType(),
                company.parentOrganization(),
                company.ein(),
                active,
                company.fiscalYearStartMonth(),
                company.fiscalYearStartDay(),
                company.defaultCurrency());
    }
}
