package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceAuthorizationIntegrationTest
{
    @Test
    void fundMutationFailsClosedAndTracksImmediateRoleAndCompanySwitches(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("service-authorization")))
        {
            AtomicReference<Optional<AuthenticatedUserSession>> current =
                    new AtomicReference<>(Optional.of(session("DEFAULT", ReservedSecurityRole.VIEWER)));
            AuthorizationGuard guard = new AuthorizationGuard(jpa, current::get);
            FundAdminService funds = new FundAdminService(jpa, () -> "DEFAULT", guard);

            assertThrows(AuthorizationException.class,
                    () -> funds.upsert("SEC", "Security Test", FundType.UNRESTRICTED, true));
            assertEquals(0L, fundCount(jpa, "SEC"));

            current.set(Optional.of(session("DEFAULT", ReservedSecurityRole.ACCOUNTANT)));
            funds.upsert("SEC", "Security Test", FundType.UNRESTRICTED, true);
            assertEquals(1L, fundCount(jpa, "SEC"));

            current.set(Optional.of(session("DEFAULT", ReservedSecurityRole.VIEWER)));
            assertThrows(AuthorizationException.class,
                    () -> funds.upsert("SEC", "Viewer Rewrite", FundType.UNRESTRICTED, true));
            assertEquals("Security Test", fundName(jpa, "SEC"));

            current.set(Optional.of(session("OTHER", ReservedSecurityRole.ACCOUNTANT)));
            assertThrows(AuthorizationException.class,
                    () -> funds.upsert("SEC", "Wrong Company", FundType.UNRESTRICTED, true));
            assertEquals("Security Test", fundName(jpa, "SEC"));
        }
    }

    private static long fundCount(Jpa jpa, String code)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(f) from Fund f where f.code = :code", Long.class)
                    .setParameter("code", code)
                    .getSingleResult();
        }
    }

    private static String fundName(Jpa jpa, String code)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select f.name from Fund f where f.code = :code", String.class)
                    .setParameter("code", code)
                    .getSingleResult();
        }
    }

    private static AuthenticatedUserSession session(String companyCode, ReservedSecurityRole role)
    {
        Instant now = Instant.parse("2026-08-30T18:00:00Z");
        return new AuthenticatedUserSession(7L, "operator", "Operator", companyCode,
                Set.of(role), now, now);
    }
}
