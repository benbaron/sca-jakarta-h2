package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationGuardTest
{
    @Test
    void permissionDecisionTracksCurrentSessionRoleStateAndCompany(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("authorization-role-switch")))
        {
            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(Optional.empty());
            AuthorizationGuard guard = new AuthorizationGuard(jpa, current::get);

            assertFalse(guard.allows(ApplicationPermission.BOOKKEEPING_WRITE));
            assertThrows(AuthorizationException.class,
                    () -> guard.require(ApplicationPermission.BOOKKEEPING_WRITE, "DEFAULT", "save journal entry"));

            current.set(Optional.of(session(7L, "officer", "DEFAULT", ReservedSecurityRole.ACCOUNTANT)));
            assertTrue(guard.allows(ApplicationPermission.BOOKKEEPING_WRITE));
            assertEquals("officer", guard.requireActor(
                    ApplicationPermission.BOOKKEEPING_WRITE, "DEFAULT", "save journal entry"));
            assertThrows(AuthorizationException.class,
                    () -> guard.require(ApplicationPermission.COMPANY_ADMIN, "DEFAULT", "save company settings"));

            current.set(Optional.of(session(7L, "officer", "DEFAULT", ReservedSecurityRole.VIEWER)));
            assertFalse(guard.allows(ApplicationPermission.BOOKKEEPING_WRITE));
            assertTrue(guard.allows(ApplicationPermission.EXPORT));
            assertThrows(AuthorizationException.class,
                    () -> guard.require(ApplicationPermission.BOOKKEEPING_WRITE, "DEFAULT", "save journal entry"));

            current.set(Optional.of(session(7L, "officer", "OTHER", ReservedSecurityRole.MANAGER)));
            assertThrows(AuthorizationException.class,
                    () -> guard.require(ApplicationPermission.COMPANY_ADMIN, "DEFAULT", "save company settings"));
        }
    }

    @Test
    void deniedRequestWritesFactualSecurityEventWithoutInventingAuthority(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("authorization-denial-audit")))
        {
            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(Optional.empty());
            AuthorizationGuard guard = new AuthorizationGuard(jpa, current::get);

            assertThrows(AuthorizationException.class,
                    () -> guard.require(ApplicationPermission.SECURITY_ADMIN, "DEFAULT", "change security settings"));

            try (EntityManager em = jpa.em())
            {
                Number count = (Number) em.createNativeQuery(
                                "select count(*) from security_event where action_type = 'AUTHORIZATION_DENIED'")
                        .getSingleResult();
                assertEquals(1L, count.longValue());
                Object[] event = (Object[]) em.createNativeQuery("""
                                select subject_username, summary, details
                                from security_event
                                where action_type = 'AUTHORIZATION_DENIED'
                                order by id desc
                                fetch first 1 row only
                                """).getSingleResult();
                assertEquals(null, event[0]);
                assertTrue(event[1].toString().contains("SECURITY_ADMIN"));
                assertTrue(event[2].toString().contains("No authenticated session"));
            }
        }
    }

    private static AuthenticatedUserSession session(
            long userId,
            String username,
            String companyCode,
            ReservedSecurityRole... roles)
    {
        Instant now = Instant.parse("2026-08-30T04:00:00Z");
        return new AuthenticatedUserSession(
                userId,
                username,
                username,
                companyCode,
                Set.of(roles),
                now,
                now);
    }
}
