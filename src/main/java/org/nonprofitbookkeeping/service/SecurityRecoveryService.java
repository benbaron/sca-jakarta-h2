package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Explicit offline recovery that clears only the singleton ADMIN credential. */
public class SecurityRecoveryService
{
    private final Jpa jpa;
    private final Clock clock;

    public SecurityRecoveryService(Jpa jpa)
    {
        this(jpa, Clock.systemUTC());
    }

    SecurityRecoveryService(Jpa jpa, Clock clock)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static void recoverAdminCredential(Path databaseFile)
    {
        Objects.requireNonNull(databaseFile, "databaseFile");
        try (Jpa jpa = new Jpa(databaseFile))
        {
            new SecurityRecoveryService(jpa).recoverAdminCredential();
        }
    }

    public void recoverAdminCredential()
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                if (!SecurityRepository.settings(em).bootstrapInitialized())
                {
                    throw new IllegalStateException(
                            "Security bootstrap has not been completed; there is no reserved ADMIN credential to recover.");
                }
                AppUser admin = SecurityRepository.findUserByReservedCode(em, ReservedSecurityRole.ADMIN);
                if (admin == null)
                {
                    throw new IllegalStateException("The singleton ADMIN account is missing.");
                }
                SecurityRepository.clearCredential(em, admin.getId());
                Instant now = Instant.now(clock);
                SecurityRepository.markAdminRecoveryPending(em, now);
                SecurityRepository.event(
                        em,
                        null,
                        null,
                        "ADMIN_CREDENTIAL_RECOVERY_REQUESTED",
                        admin.getUsername(),
                        "Offline recovery cleared the ADMIN credential to passwordless login.",
                        "The next successful ADMIN login will acknowledge this recovery.",
                        now);
                em.getTransaction().commit();
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive())
                {
                    em.getTransaction().rollback();
                }
                throw ex;
            }
        }
    }
}
