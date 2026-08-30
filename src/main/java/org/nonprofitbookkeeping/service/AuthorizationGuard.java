package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Production authorization boundary backed only by the current authenticated session and H2 security audit facts.
 * This class owns no independent identity, role, or permission state.
 */
public class AuthorizationGuard
{
    private final Jpa jpa;
    private final Supplier<Optional<AuthenticatedUserSession>> sessionSupplier;
    private final Clock clock;

    public AuthorizationGuard(
            Jpa jpa,
            Supplier<Optional<AuthenticatedUserSession>> sessionSupplier)
    {
        this(jpa, sessionSupplier, Clock.systemDefaultZone());
    }

    AuthorizationGuard(
            Jpa jpa,
            Supplier<Optional<AuthenticatedUserSession>> sessionSupplier,
            Clock clock)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.sessionSupplier = Objects.requireNonNull(sessionSupplier, "sessionSupplier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<AuthenticatedUserSession> currentSession()
    {
        Optional<AuthenticatedUserSession> current = sessionSupplier.get();
        return current == null ? Optional.empty() : current;
    }

    public boolean allows(ApplicationPermission permission)
    {
        Objects.requireNonNull(permission, "permission");
        return currentSession()
                .map(session -> AuthorizationPolicy.allows(session.effectiveRoles(), permission))
                .orElse(false);
    }

    public AuthenticatedUserSession require(ApplicationPermission permission, String operation)
    {
        return require(permission, null, operation);
    }

    public AuthenticatedUserSession require(
            ApplicationPermission permission,
            String companyCode,
            String operation)
    {
        Objects.requireNonNull(permission, "permission");
        String fixedOperation = requireText(operation, "operation");
        Optional<AuthenticatedUserSession> current = currentSession();
        if (current.isEmpty())
        {
            deny(null, companyCode, permission, fixedOperation, "No authenticated session.");
        }

        AuthenticatedUserSession session = current.orElseThrow();
        if (companyCode != null && !companyCode.isBlank()
                && !session.companyCode().equalsIgnoreCase(companyCode.strip()))
        {
            deny(session, companyCode, permission, fixedOperation,
                    "Authenticated session is bound to company " + session.companyCode() + ".");
        }
        if (!AuthorizationPolicy.allows(session.effectiveRoles(), permission))
        {
            deny(session, companyCode, permission, fixedOperation,
                    "Effective roles are " + session.effectiveRoles() + ".");
        }
        return session;
    }

    public String requireActor(
            ApplicationPermission permission,
            String companyCode,
            String operation)
    {
        return require(permission, companyCode, operation).username();
    }

    public String authenticatedActor()
    {
        return currentSession()
                .map(AuthenticatedUserSession::username)
                .orElseThrow(() -> new AuthorizationException(
                        null, "An authenticated user is required for this operation."));
    }

    private void deny(
            AuthenticatedUserSession session,
            String companyCode,
            ApplicationPermission permission,
            String operation,
            String detail)
    {
        String identity = session == null ? "Unauthenticated session" : "Account " + session.username();
        AuthorizationException failure = new AuthorizationException(
                permission,
                identity + " is not permitted to perform " + operation
                        + " (requires " + permission + ").");
        try
        {
            recordDenial(session, companyCode, permission, operation, detail);
        }
        catch (RuntimeException auditFailure)
        {
            failure.addSuppressed(auditFailure);
        }
        throw failure;
    }

    private void recordDenial(
            AuthenticatedUserSession session,
            String companyCode,
            ApplicationPermission permission,
            String operation,
            String detail)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Long companyId = findCompanyId(em, companyCode);
                SecurityRepository.event(
                        em,
                        session == null ? null : session.userId(),
                        companyId,
                        "AUTHORIZATION_DENIED",
                        session == null ? null : session.username(),
                        "Denied " + operation + "; required permission " + permission + ".",
                        detail,
                        Instant.now(clock));
                em.getTransaction().commit();
            }
            catch (RuntimeException auditFailure)
            {
                if (em.getTransaction().isActive())
                {
                    em.getTransaction().rollback();
                }
                throw auditFailure;
            }
        }
    }

    private static Long findCompanyId(EntityManager em, String companyCode)
    {
        if (companyCode == null || companyCode.isBlank())
        {
            return null;
        }
        return em.createQuery("from Company c where lower(c.code) = :code", Company.class)
                .setParameter("code", companyCode.strip().toLowerCase(Locale.ROOT))
                .setMaxResults(1)
                .getResultStream()
                .map(Company::getId)
                .findFirst()
                .orElse(null);
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.strip();
    }
}
