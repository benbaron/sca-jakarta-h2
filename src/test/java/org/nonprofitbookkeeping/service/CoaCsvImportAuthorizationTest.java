package org.nonprofitbookkeeping.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoaCsvImportAuthorizationTest
{
    @Test
    void outerCommitRequiresBookkeepingWriteAndRespondsToCurrentSession(@TempDir Path tempDir) throws Exception
    {
        Path db = tempDir.resolve("coa-csv-authorization.mv.db");
        runMigrations(db);
        try (Jpa jpa = new Jpa(db))
        {
            seedCompany(jpa, "ALPHA");
            new SecurityBootstrapService(jpa).initializeIfUnambiguous();
            UserAdminService users = new UserAdminService(jpa, () -> "ALPHA");
            long viewerId = reservedUserId(users, ReservedSecurityRole.VIEWER);
            long accountantId = reservedUserId(users, ReservedSecurityRole.ACCOUNTANT);
            long managerId = reservedUserId(users, ReservedSecurityRole.MANAGER);
            long adminId = reservedUserId(users, ReservedSecurityRole.ADMIN);

            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(
                    Optional.of(session(viewerId, "ALPHA", Set.of(ReservedSecurityRole.VIEWER))));
            AuthorizationGuard guard = new AuthorizationGuard(jpa, current::get);
            CoaCsvImportService service = new CoaCsvImportService(jpa, () -> "ALPHA", guard);
            Path source = tempDir.resolve("coa.csv");
            Files.writeString(source, """
                    code,name,account_type,normal_balance,parent_code
                    1000,Cash,ASSET,DEBIT,
                    """);
            CoaCsvImportService.CoaCsvBatchPreview preview = service.preview(source);

            assertThrows(AuthorizationException.class, () -> service.commit(null, null));
            assertEquals(0L, importedAccountCount(jpa));

            current.set(Optional.of(session(
                    accountantId, "ALPHA", Set.of(ReservedSecurityRole.ACCOUNTANT))));
            assertTrue(service.commit(preview.confirmedCopy(), "compatibility-actor").committed());

            current.set(Optional.of(session(
                    managerId, "ALPHA", Set.of(ReservedSecurityRole.MANAGER))));
            assertTrue(service.commit(service.preview(source).confirmedCopy(), "manager").committed());

            current.set(Optional.of(session(
                    adminId, "ALPHA", Set.of(ReservedSecurityRole.ADMIN))));
            assertTrue(service.commit(service.preview(source).confirmedCopy(), "admin").committed());

            current.set(Optional.of(session(
                    accountantId, "ALPHA",
                    Set.of(ReservedSecurityRole.VIEWER, ReservedSecurityRole.ACCOUNTANT))));
            assertTrue(service.commit(service.preview(source).confirmedCopy(), "union").committed());

            current.set(Optional.of(session(
                    accountantId, "OTHER", Set.of(ReservedSecurityRole.ACCOUNTANT))));
            assertThrows(AuthorizationException.class, () -> service.commit(null, null));
            current.set(Optional.empty());
            assertThrows(AuthorizationException.class, () -> service.commit(null, null));

            assertEquals(1L, importedAccountCount(jpa));
            assertEquals(3L, authorizationDenialCount(jpa));
        }
    }

    private static long reservedUserId(UserAdminService users, ReservedSecurityRole role)
    {
        return users.listUsers().stream()
                .filter(user -> role.name().equalsIgnoreCase(user.getUsername()))
                .map(AppUser::getId)
                .findFirst()
                .orElseThrow();
    }

    private static AuthenticatedUserSession session(
            long userId,
            String companyCode,
            Set<ReservedSecurityRole> roles)
    {
        Instant now = Instant.parse("2026-09-02T16:30:00Z");
        return new AuthenticatedUserSession(
                userId, "operator", "Operator", companyCode, roles, now, now);
    }

    private static long importedAccountCount(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            return em.createQuery(
                    "select count(a) from Account a where a.code = '1000'", Long.class)
                    .getSingleResult();
        }
    }

    private static long authorizationDenialCount(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            Number count = (Number) em.createNativeQuery(
                    "select count(*) from security_event where action_type = 'AUTHORIZATION_DENIED'")
                    .getSingleResult();
            return count.longValue();
        }
    }

    private static void seedCompany(Jpa jpa, String code)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company();
            company.setCode(code);
            company.setDisplayName(code + " Company");
            company.setActive(true);
            em.persist(company);
            em.flush();

            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName(code + " Chart");
            chart.setVersion("v1");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            em.flush();
            company.setActiveChartOfAccounts(chart);
            em.getTransaction().commit();
        }
    }

    private static void runMigrations(Path databaseFile)
    {
        String raw = databaseFile.toString();
        String normalized = raw.endsWith(".mv.db") ? raw.substring(0, raw.length() - 6) : raw;
        String jdbc = "jdbc:h2:file:" + normalized
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        Flyway.configure().dataSource(jdbc, "sa", "").load().migrate();
    }
}
