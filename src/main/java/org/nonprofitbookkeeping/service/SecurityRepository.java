package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.AppRole;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.UserCompanyRole;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared H2/JPA persistence helpers for the P20 security authority. */
final class SecurityRepository
{
    static final long CONFIGURATION_ID = 1L;

    record CredentialData(
            String algorithm,
            int iterations,
            String saltBase64,
            String hashBase64,
            int version)
    {
    }

    private SecurityRepository()
    {
    }

    static SecuritySettingsView settings(EntityManager em)
    {
        Object[] row = (Object[]) em.createNativeQuery("""
                select inactivity_timeout_minutes, admin_recovery_pending, bootstrap_initialized
                from security_configuration where id = 1
                """).getSingleResult();
        return new SecuritySettingsView(
                ((Number) row[0]).intValue(),
                Boolean.TRUE.equals(row[1]),
                Boolean.TRUE.equals(row[2]));
    }

    static void setBootstrapInitialized(EntityManager em, boolean initialized)
    {
        em.createNativeQuery("""
                update security_configuration
                set bootstrap_initialized = :initialized, updated_at = CURRENT_TIMESTAMP
                where id = 1
                """)
                .setParameter("initialized", initialized)
                .executeUpdate();
    }

    static void setInactivityTimeout(EntityManager em, int minutes)
    {
        em.createNativeQuery("""
                update security_configuration
                set inactivity_timeout_minutes = :minutes, updated_at = CURRENT_TIMESTAMP
                where id = 1
                """)
                .setParameter("minutes", minutes)
                .executeUpdate();
    }

    static void markAdminRecoveryPending(EntityManager em, Instant at)
    {
        em.createNativeQuery("""
                update security_configuration
                set admin_recovery_pending = true,
                    admin_recovery_at = :at,
                    updated_at = CURRENT_TIMESTAMP
                where id = 1
                """)
                .setParameter("at", at)
                .executeUpdate();
    }

    static void clearAdminRecoveryPending(EntityManager em)
    {
        em.createNativeQuery("""
                update security_configuration
                set admin_recovery_pending = false, updated_at = CURRENT_TIMESTAMP
                where id = 1
                """)
                .executeUpdate();
    }

    static AppUser findUserByReservedCode(EntityManager em, ReservedSecurityRole role)
    {
        List<?> ids = em.createNativeQuery("""
                select id from app_user where reserved_security_code = :code
                """)
                .setParameter("code", role.name())
                .setMaxResults(1)
                .getResultList();
        return ids.isEmpty() ? null : em.find(AppUser.class, ((Number) ids.get(0)).longValue());
    }

    static AppUser findUserByUsername(EntityManager em, String username)
    {
        return em.createQuery("from AppUser u where lower(u.username) = :username", AppUser.class)
                .setParameter("username", username.strip().toLowerCase(Locale.ROOT))
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    static AppRole findRoleByReservedCode(EntityManager em, ReservedSecurityRole role)
    {
        List<?> ids = em.createNativeQuery("""
                select id from app_role where reserved_security_code = :code
                """)
                .setParameter("code", role.name())
                .setMaxResults(1)
                .getResultList();
        return ids.isEmpty() ? null : em.find(AppRole.class, ((Number) ids.get(0)).longValue());
    }

    static AppRole findRoleByCode(EntityManager em, String code)
    {
        return em.createQuery("from AppRole r where upper(r.code) = :code", AppRole.class)
                .setParameter("code", code.strip().toUpperCase(Locale.ROOT))
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    static String reservedUserCode(EntityManager em, Long userId)
    {
        if (userId == null)
        {
            return null;
        }
        List<?> values = em.createNativeQuery("select reserved_security_code from app_user where id = :id")
                .setParameter("id", userId)
                .getResultList();
        return values.isEmpty() || values.get(0) == null ? null : values.get(0).toString();
    }

    static String reservedRoleCode(EntityManager em, Long roleId)
    {
        if (roleId == null)
        {
            return null;
        }
        List<?> values = em.createNativeQuery("select reserved_security_code from app_role where id = :id")
                .setParameter("id", roleId)
                .getResultList();
        return values.isEmpty() || values.get(0) == null ? null : values.get(0).toString();
    }

    static void markReservedUser(EntityManager em, long userId, ReservedSecurityRole role)
    {
        em.createNativeQuery("""
                update app_user set reserved_security_code = :code, updated_at = CURRENT_TIMESTAMP where id = :id
                """)
                .setParameter("code", role.name())
                .setParameter("id", userId)
                .executeUpdate();
    }

    static void markReservedRole(EntityManager em, long roleId, ReservedSecurityRole role)
    {
        em.createNativeQuery("""
                update app_role set reserved_security_code = :code, updated_at = CURRENT_TIMESTAMP where id = :id
                """)
                .setParameter("code", role.name())
                .setParameter("id", roleId)
                .executeUpdate();
    }

    static boolean credentialConfigured(EntityManager em, long userId)
    {
        Number count = (Number) em.createNativeQuery(
                        "select count(*) from app_user_credential where user_id = :userId")
                .setParameter("userId", userId)
                .getSingleResult();
        return count.longValue() > 0L;
    }

    static CredentialData credential(EntityManager em, long userId)
    {
        List<?> rows = em.createNativeQuery("""
                select algorithm, iteration_count, salt_base64, hash_base64, credential_version
                from app_user_credential where user_id = :userId
                """)
                .setParameter("userId", userId)
                .setMaxResults(1)
                .getResultList();
        if (rows.isEmpty())
        {
            return null;
        }
        Object[] row = (Object[]) rows.get(0);
        return new CredentialData(
                row[0].toString(),
                ((Number) row[1]).intValue(),
                row[2].toString(),
                row[3].toString(),
                ((Number) row[4]).intValue());
    }

    static void saveCredential(EntityManager em, long userId, CredentialData credential)
    {
        if (credentialConfigured(em, userId))
        {
            em.createNativeQuery("""
                    update app_user_credential
                    set algorithm = :algorithm,
                        iteration_count = :iterations,
                        salt_base64 = :salt,
                        hash_base64 = :hash,
                        credential_version = :version,
                        updated_at = CURRENT_TIMESTAMP
                    where user_id = :userId
                    """)
                    .setParameter("algorithm", credential.algorithm())
                    .setParameter("iterations", credential.iterations())
                    .setParameter("salt", credential.saltBase64())
                    .setParameter("hash", credential.hashBase64())
                    .setParameter("version", credential.version())
                    .setParameter("userId", userId)
                    .executeUpdate();
            return;
        }
        em.createNativeQuery("""
                insert into app_user_credential
                    (user_id, algorithm, iteration_count, salt_base64, hash_base64,
                     credential_version, created_at, updated_at)
                values
                    (:userId, :algorithm, :iterations, :salt, :hash, :version,
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)
                .setParameter("userId", userId)
                .setParameter("algorithm", credential.algorithm())
                .setParameter("iterations", credential.iterations())
                .setParameter("salt", credential.saltBase64())
                .setParameter("hash", credential.hashBase64())
                .setParameter("version", credential.version())
                .executeUpdate();
    }

    static void clearCredential(EntityManager em, long userId)
    {
        em.createNativeQuery("delete from app_user_credential where user_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }

    static Company requireCompany(EntityManager em, String companyCode)
    {
        return em.createQuery("from Company c where lower(c.code) = :code", Company.class)
                .setParameter("code", companyCode.strip().toLowerCase(Locale.ROOT))
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown company: " + companyCode));
    }

    static Set<ReservedSecurityRole> effectiveRoles(
            EntityManager em,
            AppUser user,
            Company company,
            LocalDate date)
    {
        if (user == null || company == null || date == null || !user.isActive() || !company.isActive())
        {
            return Set.of();
        }
        List<UserCompanyRole> assignments = em.createQuery("""
                from UserCompanyRole a
                join fetch a.role
                where a.user = :user
                  and a.company = :company
                  and a.active = true
                  and a.role.active = true
                  and a.startDate <= :today
                  and (a.endDate is null or a.endDate >= :today)
                """, UserCompanyRole.class)
                .setParameter("user", user)
                .setParameter("company", company)
                .setParameter("today", date)
                .getResultList();

        EnumSet<ReservedSecurityRole> roles = EnumSet.noneOf(ReservedSecurityRole.class);
        String reservedUser = reservedUserCode(em, user.getId());
        for (UserCompanyRole assignment : assignments)
        {
            ReservedSecurityRole.fromCode(reservedRoleCode(em, assignment.getRole().getId()))
                    .ifPresent(role ->
                    {
                        if (role != ReservedSecurityRole.ADMIN
                                || ReservedSecurityRole.ADMIN.name().equals(reservedUser))
                        {
                            roles.add(role);
                        }
                    });
        }
        return roles.isEmpty() ? Set.of() : Set.copyOf(roles);
    }

    static UserCompanyRole activeAssignment(
            EntityManager em,
            AppUser user,
            Company company,
            AppRole role)
    {
        return em.createQuery("""
                from UserCompanyRole a
                where a.user = :user and a.company = :company and a.role = :role and a.active = true
                order by a.startDate desc, a.id desc
                """, UserCompanyRole.class)
                .setParameter("user", user)
                .setParameter("company", company)
                .setParameter("role", role)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    static void event(
            EntityManager em,
            Long userId,
            Long companyId,
            String actionType,
            String subjectUsername,
            String summary,
            String details,
            Instant occurredAt)
    {
        em.createNativeQuery("""
                insert into security_event
                    (user_id, company_id, occurred_at, action_type, subject_username, summary, details)
                values
                    (:userId, :companyId, :occurredAt, :actionType, :subjectUsername, :summary, :details)
                """)
                .setParameter("userId", userId)
                .setParameter("companyId", companyId)
                .setParameter("occurredAt", occurredAt)
                .setParameter("actionType", actionType)
                .setParameter("subjectUsername", subjectUsername)
                .setParameter("summary", summary)
                .setParameter("details", details)
                .executeUpdate();
    }

    static AppUser lockUser(EntityManager em, long userId)
    {
        AppUser user = em.find(AppUser.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (user == null)
        {
            throw new IllegalArgumentException("Unknown user ID: " + userId);
        }
        return user;
    }
}
