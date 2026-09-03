package org.nonprofitbookkeeping.interchange.sclx;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;
import org.nonprofitbookkeeping.service.AuthorizationException;
import org.nonprofitbookkeeping.service.AuthorizationGuard;
import org.nonprofitbookkeeping.service.ReservedSecurityRole;
import org.nonprofitbookkeeping.service.SecurityBootstrapService;
import org.nonprofitbookkeeping.service.UserAdminService;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxImportCommitAuthorizationTest
{
    private static final String TARGET = "SCLX_TARGET";

    @Test
    void outerCommitRequiresBookkeepingWriteBeforeValidationAndUsesCurrentSession(@TempDir Path tempDir)
    {
        Path source = Path.of("src/test/resources/compatibility/sclx/donor-sclx-1.3.json");
        try (Jpa jpa = new Jpa(tempDir.resolve("sclx-import-authorization")))
        {
            seedEmptyTarget(jpa);
            new SecurityBootstrapService(jpa).initializeIfUnambiguous();
            UserAdminService users = new UserAdminService(jpa, () -> TARGET);
            long viewerId = reservedUserId(users, ReservedSecurityRole.VIEWER);
            long accountantId = reservedUserId(users, ReservedSecurityRole.ACCOUNTANT);

            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(
                    Optional.of(session(viewerId, TARGET, Set.of(ReservedSecurityRole.VIEWER))));
            AuthorizationGuard guard = new AuthorizationGuard(jpa, current::get);
            SclxImportCommitService service = new SclxImportCommitService(jpa, () -> TARGET, guard);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);

            assertThrows(AuthorizationException.class, () -> service.commit(null, null, null));
            assertEquals(0L, transactionCount(jpa));
            assertEquals(1L, authorizationDenialCount(jpa));

            current.set(Optional.of(session(
                    accountantId, TARGET, Set.of(ReservedSecurityRole.ACCOUNTANT))));
            SclxImportResult result = service.commit(source, preview, "spoofable-compatibility-actor");
            assertTrue(result.committed(), () -> result.messages().toString());
            assertTrue(transactionCount(jpa) > 0L);

            current.set(Optional.of(session(
                    accountantId, "OTHER", Set.of(ReservedSecurityRole.ACCOUNTANT))));
            assertThrows(AuthorizationException.class, () -> service.commit(null, null, null));
            current.set(Optional.empty());
            assertThrows(AuthorizationException.class, () -> service.commit(null, null, null));
            assertEquals(3L, authorizationDenialCount(jpa));
        }
    }

    private static void seedEmptyTarget(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company();
            company.setCode(TARGET);
            company.setDisplayName("Empty Target");
            company.setDefaultCurrency("USD");
            em.persist(company);

            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName("Empty Chart");
            chart.setVersion("EMPTY");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            company.setActiveChartOfAccounts(chart);
            em.getTransaction().commit();
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

    private static long authorizationDenialCount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            Number count = (Number) em.createNativeQuery(
                    "select count(*) from security_event where action_type = 'AUTHORIZATION_DENIED'")
                    .getSingleResult();
            return count.longValue();
        }
    }

    private static long transactionCount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(t) from Txn t", Long.class).getSingleResult();
        }
    }
}
