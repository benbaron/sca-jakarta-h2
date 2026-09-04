package org.nonprofitbookkeeping.interchange.coa;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;
import org.nonprofitbookkeeping.service.AuthorizationException;
import org.nonprofitbookkeeping.service.AuthorizationGuard;
import org.nonprofitbookkeeping.service.ReservedSecurityRole;
import org.nonprofitbookkeeping.service.SecurityBootstrapService;
import org.nonprofitbookkeeping.service.UserAdminService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartOfAccountsJsonAuthorizationIntegrationTest
{
    private static final String COMPANY_CODE = "ALPHA";

    @Test
    void guardedCommitRequiresCurrentBookkeepingWriteBeforeValidation(@TempDir Path tempDir) throws Exception
    {
        Path db = tempDir.resolve("coa-json-authorization.mv.db");
        runMigrations(db);
        try (Jpa jpa = new Jpa(db))
        {
            seedCompany(jpa);
            new SecurityBootstrapService(jpa).initializeIfUnambiguous();
            UserAdminService users = new UserAdminService(jpa, () -> COMPANY_CODE);
            long viewerId = reservedUserId(users, ReservedSecurityRole.VIEWER);
            long accountantId = reservedUserId(users, ReservedSecurityRole.ACCOUNTANT);

            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(
                    Optional.of(session(viewerId, COMPANY_CODE, Set.of(ReservedSecurityRole.VIEWER))));
            AuthorizationGuard guard = new AuthorizationGuard(jpa, current::get);
            ChartOfAccountsJsonImportService service = new ChartOfAccountsJsonImportService(
                    jpa,
                    () -> COMPANY_CODE,
                    guard);
            Path source = writeSource(tempDir.resolve("coa.json"));
            CoaImportPreview preview = new ChartOfAccountsJsonService(jpa, () -> COMPANY_CODE).preview(
                    new CoaImportRequest(source, CoaImportMode.MERGE_BY_CODE, "", "", Map.of(), true));

            assertThrows(AuthorizationException.class, () -> service.commit(null));
            assertEquals(0L, importedAccountCount(jpa));

            current.set(Optional.of(session(
                    accountantId,
                    COMPANY_CODE,
                    Set.of(ReservedSecurityRole.ACCOUNTANT))));
            assertTrue(service.commit(preview).committed());
            assertEquals(1L, importedAccountCount(jpa));

            current.set(Optional.of(session(
                    accountantId,
                    "OTHER",
                    Set.of(ReservedSecurityRole.ACCOUNTANT))));
            assertThrows(AuthorizationException.class, () -> service.commit(null));
            current.set(Optional.empty());
            assertThrows(AuthorizationException.class, () -> service.commit(null));

            assertEquals(1L, importedAccountCount(jpa));
            assertEquals(3L, authorizationDenialCount(jpa));
        }
    }

    private static Path writeSource(Path target) throws Exception
    {
        Files.writeString(target, """
                {
                  "format" : "SCA-COA",
                  "version" : "1.0",
                  "chart" : {
                    "name" : "Portable Chart",
                    "chartVersion" : "2027",
                    "status" : "DRAFT",
                    "currency" : "USD"
                  },
                  "accounts" : [ {
                    "code" : "1000",
                    "name" : "Cash",
                    "type" : "ASSET",
                    "normalBalance" : "DEBIT",
                    "posting" : true,
                    "active" : true,
                    "openingBalance" : "0.00"
                  } ]
                }
                """);
        return target;
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
        Instant now = Instant.parse("2026-09-03T19:00:00Z");
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

    private static void seedCompany(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company();
            company.setCode(COMPANY_CODE);
            company.setDisplayName("Alpha Company");
            company.setDefaultCurrency("USD");
            company.setActive(true);
            em.persist(company);
            em.flush();

            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName("Default Chart");
            chart.setVersion("2026");
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
