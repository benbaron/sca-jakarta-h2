package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/** ADMIN-only credential and security-setting administration. */
public class SecurityAdminService
{
    private final Jpa jpa;
    private final Clock clock;
    private final PasswordHasher passwordHasher;

    public SecurityAdminService(Jpa jpa)
    {
        this(jpa, Clock.systemDefaultZone(), new PasswordHasher());
    }

    SecurityAdminService(Jpa jpa, Clock clock, PasswordHasher passwordHasher)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    }

    public boolean passwordConfigured(long userId)
    {
        try (EntityManager em = jpa.em())
        {
            return SecurityRepository.credentialConfigured(em, userId);
        }
    }

    public SecuritySettingsView settings()
    {
        try (EntityManager em = jpa.em())
        {
            return SecurityRepository.settings(em);
        }
    }

    public void setPassword(
            long adminUserId,
            String companyCode,
            long targetUserId,
            char[] password)
    {
        PasswordHasher.requirePassword(password);
        char[] copy = Arrays.copyOf(password, password.length);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = SecurityRepository.requireCompany(em, companyCode);
                AppUser admin = requireEffectiveAdmin(em, adminUserId, company);
                AppUser target = SecurityRepository.lockUser(em, targetUserId);
                if (!target.isActive())
                {
                    throw new IllegalStateException("A password cannot be set on an inactive account.");
                }
                boolean replacing = SecurityRepository.credentialConfigured(em, targetUserId);
                SecurityRepository.saveCredential(em, targetUserId, passwordHasher.hash(copy));
                SecurityRepository.event(
                        em,
                        admin.getId(),
                        company.getId(),
                        replacing ? "PASSWORD_REPLACED" : "PASSWORD_SET",
                        target.getUsername(),
                        (replacing ? "Replaced" : "Set") + " the login password for account "
                                + target.getUsername() + ".",
                        null,
                        Instant.now(clock));
                em.getTransaction().commit();
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
            finally
            {
                Arrays.fill(copy, '\0');
            }
        }
    }

    public void clearPassword(long adminUserId, String companyCode, long targetUserId)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = SecurityRepository.requireCompany(em, companyCode);
                AppUser admin = requireEffectiveAdmin(em, adminUserId, company);
                AppUser target = SecurityRepository.lockUser(em, targetUserId);
                SecurityRepository.clearCredential(em, targetUserId);
                SecurityRepository.event(
                        em,
                        admin.getId(),
                        company.getId(),
                        "PASSWORD_CLEARED",
                        target.getUsername(),
                        "Cleared the login password for account " + target.getUsername()
                                + "; the account is passwordless.",
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

    public SecuritySettingsView setInactivityTimeoutMinutes(
            long adminUserId,
            String companyCode,
            int minutes)
    {
        if (minutes < 0 || minutes > 10_080)
        {
            throw new IllegalArgumentException("Inactivity timeout must be 0 through 10080 minutes; 0 disables it.");
        }
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = SecurityRepository.requireCompany(em, companyCode);
                AppUser admin = requireEffectiveAdmin(em, adminUserId, company);
                SecurityRepository.setInactivityTimeout(em, minutes);
                SecurityRepository.event(
                        em,
                        admin.getId(),
                        company.getId(),
                        "INACTIVITY_TIMEOUT_CHANGED",
                        admin.getUsername(),
                        minutes == 0
                                ? "Disabled the inactivity timeout."
                                : "Set the inactivity timeout to " + minutes + " minute(s).",
                        null,
                        Instant.now(clock));
                SecuritySettingsView result = SecurityRepository.settings(em);
                em.getTransaction().commit();
                return result;
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    private AppUser requireEffectiveAdmin(EntityManager em, long userId, Company company)
    {
        AppUser user = em.find(AppUser.class, userId);
        if (user == null || !user.isActive())
        {
            throw new SecurityException("Only the active ADMIN account may change security settings.");
        }
        Set<ReservedSecurityRole> roles = SecurityRepository.effectiveRoles(
                em, user, company, LocalDate.now(clock));
        if (!ReservedSecurityRole.ADMIN.name().equals(SecurityRepository.reservedUserCode(em, user.getId()))
                || !roles.contains(ReservedSecurityRole.ADMIN))
        {
            throw new SecurityException("Only the singleton ADMIN account may change security settings.");
        }
        return user;
    }

    private static void rollback(EntityManager em)
    {
        if (em.getTransaction().isActive())
        {
            em.getTransaction().rollback();
        }
    }
}
