package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AppRole;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.UserCompanyRole;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Establishes the four reserved accounts/roles without guessing over user-owned identity conflicts. */
public class SecurityBootstrapService
{
    private final Jpa jpa;
    private final Clock clock;

    public SecurityBootstrapService(Jpa jpa)
    {
        this(jpa, Clock.systemDefaultZone());
    }

    SecurityBootstrapService(Jpa jpa, Clock clock)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SecurityBootstrapStatus status()
    {
        try (EntityManager em = jpa.em())
        {
            SecuritySettingsView settings = SecurityRepository.settings(em);
            if (settings.bootstrapInitialized())
            {
                return new SecurityBootstrapStatus(true, List.of());
            }
            return new SecurityBootstrapStatus(false, conflicts(em));
        }
    }

    public SecurityBootstrapStatus initializeIfUnambiguous()
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                SecuritySettingsView settings = SecurityRepository.settings(em);
                if (settings.bootstrapInitialized())
                {
                    em.getTransaction().commit();
                    return new SecurityBootstrapStatus(true, List.of());
                }
                List<String> conflicts = conflicts(em);
                if (!conflicts.isEmpty())
                {
                    em.getTransaction().rollback();
                    return new SecurityBootstrapStatus(false, conflicts);
                }
                initialize(em, false);
                em.getTransaction().commit();
                return new SecurityBootstrapStatus(true, List.of());
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    public SecurityBootstrapStatus adoptExistingReservedAccounts()
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                if (!SecurityRepository.settings(em).bootstrapInitialized())
                {
                    initialize(em, true);
                }
                em.getTransaction().commit();
                return new SecurityBootstrapStatus(true, List.of());
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    private void initialize(EntityManager em, boolean adoptExisting)
    {
        ensureReservedRoles(em);
        for (ReservedSecurityRole role : ReservedSecurityRole.values())
        {
            AppUser user = SecurityRepository.findUserByReservedCode(em, role);
            if (user == null)
            {
                AppUser matching = SecurityRepository.findUserByUsername(em, role.reservedUsername());
                if (matching != null)
                {
                    if (!adoptExisting)
                    {
                        throw new IllegalStateException(
                                "Existing account " + matching.getUsername()
                                        + " must be explicitly adopted as reserved " + role.name() + ".");
                    }
                    if (!matching.isActive())
                    {
                        matching.setActive(true);
                        matching.touchUpdatedAt();
                    }
                    em.flush();
                    SecurityRepository.markReservedUser(em, matching.getId(), role);
                    user = matching;
                }
                else
                {
                    user = new AppUser();
                    user.setUsername(role.reservedUsername());
                    user.setDisplayName(role.displayName());
                    user.setActive(true);
                    user.touchUpdatedAt();
                    em.persist(user);
                    em.flush();
                    SecurityRepository.markReservedUser(em, user.getId(), role);
                }
            }
        }

        em.flush();
        for (Company company : em.createQuery("from Company c order by c.id", Company.class).getResultList())
        {
            ensureDefaultAssignments(em, company, LocalDate.now(clock));
        }
        SecurityRepository.setBootstrapInitialized(em, true);
        SecurityRepository.event(
                em,
                null,
                null,
                adoptExisting ? "SECURITY_BOOTSTRAP_ADOPTED" : "SECURITY_BOOTSTRAP_COMPLETED",
                null,
                adoptExisting
                        ? "Adopted existing matching accounts into the reserved security bootstrap."
                        : "Created the reserved security accounts and company assignments.",
                "Reserved accounts: ADMIN, MANAGER, ACCOUNTANT, VIEWER.",
                Instant.now(clock));
        em.flush();
    }

    private static List<String> conflicts(EntityManager em)
    {
        List<String> conflicts = new ArrayList<>();
        for (ReservedSecurityRole role : ReservedSecurityRole.values())
        {
            AppUser reserved = SecurityRepository.findUserByReservedCode(em, role);
            AppUser matching = SecurityRepository.findUserByUsername(em, role.reservedUsername());
            if (reserved == null && matching != null)
            {
                conflicts.add("Existing account '" + matching.getUsername()
                        + "' must be adopted as reserved " + role.name() + ".");
            }
        }
        return List.copyOf(conflicts);
    }

    private static void ensureReservedRoles(EntityManager em)
    {
        for (ReservedSecurityRole reserved : ReservedSecurityRole.values())
        {
            AppRole role = SecurityRepository.findRoleByReservedCode(em, reserved);
            if (role == null)
            {
                role = SecurityRepository.findRoleByCode(em, reserved.roleCode());
                if (role == null)
                {
                    role = new AppRole();
                    role.setCode(reserved.roleCode());
                    role.setName(reserved.displayName());
                    role.setDescription("Reserved runtime security role: " + reserved.name() + ".");
                    role.setActive(true);
                    role.touchUpdatedAt();
                    em.persist(role);
                    em.flush();
                }
                SecurityRepository.markReservedRole(em, role.getId(), reserved);
            }
            if (!role.isActive())
            {
                role.setActive(true);
                role.touchUpdatedAt();
            }
        }
    }

    static void ensureDefaultAssignmentsIfInitialized(EntityManager em, Company company)
    {
        if (!SecurityRepository.settings(em).bootstrapInitialized())
        {
            return;
        }
        ensureDefaultAssignments(em, company, LocalDate.now());
        SecurityRepository.event(
                em,
                null,
                company.getId(),
                "SECURITY_COMPANY_DEFAULT_ASSIGNMENTS_CREATED",
                null,
                "Ensured default reserved security assignments for company " + company.getCode() + ".",
                null,
                Instant.now());
    }

    private static void ensureDefaultAssignments(EntityManager em, Company company, LocalDate startDate)
    {
        for (ReservedSecurityRole reserved : ReservedSecurityRole.values())
        {
            AppUser user = SecurityRepository.findUserByReservedCode(em, reserved);
            AppRole role = SecurityRepository.findRoleByReservedCode(em, reserved);
            if (user == null || role == null)
            {
                throw new IllegalStateException("Reserved security bootstrap is incomplete for " + reserved.name() + ".");
            }
            UserCompanyRole assignment = SecurityRepository.activeAssignment(em, user, company, role);
            if (assignment == null)
            {
                assignment = new UserCompanyRole();
                assignment.setUser(user);
                assignment.setCompany(company);
                assignment.setRole(role);
                assignment.setStartDate(startDate);
                assignment.setActive(true);
                assignment.setRequiredSecurityAssignment(reserved == ReservedSecurityRole.ADMIN);
                assignment.touchUpdatedAt();
                em.persist(assignment);
            }
            else if (reserved == ReservedSecurityRole.ADMIN && !assignment.isRequiredSecurityAssignment())
            {
                assignment.setRequiredSecurityAssignment(true);
                assignment.touchUpdatedAt();
            }
        }
        em.flush();
    }

    private static void rollback(EntityManager em)
    {
        if (em.getTransaction().isActive())
        {
            em.getTransaction().rollback();
        }
    }
}
