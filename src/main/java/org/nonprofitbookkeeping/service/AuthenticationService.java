package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** H2-backed account authentication and effective-role resolution. */
public class AuthenticationService
{
    private final Jpa jpa;
    private final Clock clock;
    private final PasswordHasher passwordHasher;

    public AuthenticationService(Jpa jpa)
    {
        this(jpa, Clock.systemDefaultZone(), new PasswordHasher());
    }

    AuthenticationService(Jpa jpa, Clock clock, PasswordHasher passwordHasher)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    }

    public SecurityBootstrapStatus initializeSecurityIfUnambiguous()
    {
        return new SecurityBootstrapService(jpa, clock).initializeIfUnambiguous();
    }

    public SecurityBootstrapStatus bootstrapStatus()
    {
        return new SecurityBootstrapService(jpa, clock).status();
    }

    public SecurityBootstrapStatus adoptExistingReservedAccounts()
    {
        return new SecurityBootstrapService(jpa, clock).adoptExistingReservedAccounts();
    }

    public List<LoginAccountView> loginAccounts(String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            if (!SecurityRepository.settings(em).bootstrapInitialized())
            {
                return List.of();
            }
            Company company = SecurityRepository.requireCompany(em, companyCode);
            LocalDate today = LocalDate.now(clock);
            List<LoginAccountView> result = new ArrayList<>();
            for (AppUser user : em.createQuery("from AppUser u where u.active = true order by lower(u.username)", AppUser.class)
                    .getResultList())
            {
                Set<ReservedSecurityRole> roles = SecurityRepository.effectiveRoles(em, user, company, today);
                if (!roles.isEmpty())
                {
                    result.add(new LoginAccountView(
                            user.getId(),
                            user.getUsername(),
                            user.getDisplayName(),
                            SecurityRepository.credentialConfigured(em, user.getId()),
                            roles));
                }
            }
            result.sort(Comparator.comparing(LoginAccountView::username, String.CASE_INSENSITIVE_ORDER));
            return List.copyOf(result);
        }
    }

    public AuthenticatedUserSession authenticate(String companyCode, long userId, char[] password)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                if (!SecurityRepository.settings(em).bootstrapInitialized())
                {
                    throw new AuthenticationException("Security setup must be completed before login.");
                }
                Company company = SecurityRepository.requireCompany(em, companyCode);
                AppUser user = em.find(AppUser.class, userId, LockModeType.PESSIMISTIC_READ);
                if (user == null || !user.isActive())
                {
                    recordFailure(em, null, company, "Unknown or inactive account.");
                    em.getTransaction().commit();
                    throw new AuthenticationException("Login failed. Select an active account for this company.");
                }

                Set<ReservedSecurityRole> roles = SecurityRepository.effectiveRoles(
                        em, user, company, LocalDate.now(clock));
                if (roles.isEmpty())
                {
                    recordFailure(em, user, company, "Account has no effective reserved role for this company.");
                    em.getTransaction().commit();
                    throw new AuthenticationException("Login failed. This account has no access to the active company.");
                }

                SecurityRepository.CredentialData credential = SecurityRepository.credential(em, userId);
                if (credential != null && !passwordHasher.verify(password, credential))
                {
                    recordFailure(em, user, company, "Password verification failed.");
                    em.getTransaction().commit();
                    throw new AuthenticationException("Login failed. The password is incorrect.");
                }

                Instant now = Instant.now(clock);
                SecurityRepository.event(
                        em,
                        user.getId(),
                        company.getId(),
                        "LOGIN_SUCCEEDED",
                        user.getUsername(),
                        "Authenticated account " + user.getUsername() + " for company " + company.getCode() + ".",
                        "Effective roles: " + roles,
                        now);
                if (roles.contains(ReservedSecurityRole.ADMIN)
                        && SecurityRepository.settings(em).adminRecoveryPending())
                {
                    SecurityRepository.event(
                            em,
                            user.getId(),
                            company.getId(),
                            "ADMIN_CREDENTIAL_RECOVERY_ACKNOWLEDGED",
                            user.getUsername(),
                            "Administrator logged in after offline credential recovery.",
                            null,
                            now);
                    SecurityRepository.clearAdminRecoveryPending(em);
                }
                em.getTransaction().commit();
                return new AuthenticatedUserSession(
                        user.getId(),
                        user.getUsername(),
                        user.getDisplayName(),
                        company.getCode(),
                        roles,
                        now,
                        now);
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    /** Recomputes role state for a company switch without mutating the supplied session. */
    public AuthenticatedUserSession rebind(AuthenticatedUserSession session, String targetCompanyCode)
    {
        Objects.requireNonNull(session, "session");
        try (EntityManager em = jpa.em())
        {
            AppUser user = em.find(AppUser.class, session.userId());
            Company company = SecurityRepository.requireCompany(em, targetCompanyCode);
            Set<ReservedSecurityRole> roles = SecurityRepository.effectiveRoles(
                    em, user, company, LocalDate.now(clock));
            if (roles.isEmpty())
            {
                throw new AuthenticationException(
                        "Account " + session.username() + " has no access to company " + company.getCode() + ".");
            }
            return session.withCompany(company.getCode(), roles, Instant.now(clock));
        }
    }

    /** Refreshes role state for the current company after assignment/configuration changes. */
    public AuthenticatedUserSession refresh(AuthenticatedUserSession session)
    {
        return rebind(session, session.companyCode());
    }

    public void logout(AuthenticatedUserSession session, String reason)
    {
        if (session == null)
        {
            return;
        }
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                AppUser user = em.find(AppUser.class, session.userId());
                Company company = null;
                try
                {
                    company = SecurityRepository.requireCompany(em, session.companyCode());
                }
                catch (RuntimeException ignored)
                {
                    // A database/company lifecycle transition may already have invalidated the old company.
                }
                SecurityRepository.event(
                        em,
                        user == null ? session.userId() : user.getId(),
                        company == null ? null : company.getId(),
                        "LOGOUT",
                        session.username(),
                        "Logged out account " + session.username() + ".",
                        reason == null ? null : reason.strip(),
                        Instant.now(clock));
                em.getTransaction().commit();
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    public void recordTimeout(AuthenticatedUserSession session)
    {
        if (session == null)
        {
            return;
        }
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = SecurityRepository.requireCompany(em, session.companyCode());
                SecurityRepository.event(
                        em,
                        session.userId(),
                        company.getId(),
                        "INACTIVITY_TIMEOUT",
                        session.username(),
                        "Session expired because the configured inactivity timeout elapsed.",
                        null,
                        Instant.now(clock));
                em.getTransaction().commit();
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    public int inactivityTimeoutMinutes()
    {
        try (EntityManager em = jpa.em())
        {
            return SecurityRepository.settings(em).inactivityTimeoutMinutes();
        }
    }

    public boolean hasTimedOut(AuthenticatedUserSession session, Instant now)
    {
        Objects.requireNonNull(now, "now");
        if (session == null)
        {
            return false;
        }
        int timeout = inactivityTimeoutMinutes();
        return timeout > 0
                && !now.isBefore(session.lastActivityAt())
                && Duration.between(session.lastActivityAt(), now).compareTo(Duration.ofMinutes(timeout)) >= 0;
    }

    private void recordFailure(EntityManager em, AppUser user, Company company, String details)
    {
        SecurityRepository.event(
                em,
                user == null ? null : user.getId(),
                company == null ? null : company.getId(),
                "LOGIN_FAILED",
                user == null ? null : user.getUsername(),
                "Login attempt failed.",
                details,
                Instant.now(clock));
    }

    private static void rollback(EntityManager em)
    {
        if (em.getTransaction().isActive())
        {
            em.getTransaction().rollback();
        }
    }
}
