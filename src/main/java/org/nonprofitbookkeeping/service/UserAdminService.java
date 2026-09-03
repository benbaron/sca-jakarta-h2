package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.AppRole;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.UserCompanyRole;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/** Stable-ID user/role maintenance plus dated company-scoped assignment history. */
@ApplicationScoped
public class UserAdminService
{
    @FunctionalInterface
    interface CommitHook
    {
        void afterDomainWrite();
    }

    @Inject
    Jpa jpa;

    private Supplier<String> companyCodeSupplier = () -> "DEFAULT";
    private Clock clock = Clock.systemDefaultZone();
    private CommitHook commitHook = () -> { };
    private AuthorizationGuard authorizationGuard;

    public UserAdminService()
    {
    }

    public UserAdminService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT");
    }

    public UserAdminService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this(jpa, companyCodeSupplier, null);
    }

    public UserAdminService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            AuthorizationGuard authorizationGuard)
    {
        this(jpa, companyCodeSupplier, Clock.systemDefaultZone(), () -> { }, authorizationGuard);
    }

    UserAdminService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            Clock clock,
            CommitHook commitHook)
    {
        this(jpa, companyCodeSupplier, clock, commitHook, null);
    }

    UserAdminService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            Clock clock,
            CommitHook commitHook,
            AuthorizationGuard authorizationGuard)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.commitHook = Objects.requireNonNull(commitHook, "commitHook");
        this.authorizationGuard = authorizationGuard;
    }

    public List<AppUser> listUsers()
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("from AppUser u order by u.username", AppUser.class).getResultList();
        }
    }

    public List<AppRole> listRoles()
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("from AppRole r order by r.code", AppRole.class).getResultList();
        }
    }

    /** Lists assignment history only for the authoritative active company. */
    public List<UserCompanyRole> listAssignments()
    {
        try (EntityManager em = jpa.em())
        {
            Company company = requireActiveCompany(em);
            return em.createQuery("""
                    from UserCompanyRole a
                    join fetch a.user
                    join fetch a.company
                    join fetch a.role
                    where a.company = :company
                    order by a.user.username, a.role.code, a.startDate desc, a.id desc
                    """, UserCompanyRole.class)
                    .setParameter("company", company)
                    .getResultList();
        }
    }

    /** Creates or updates one user by stable database ID. */
    public AppUser saveUser(AppUserCommand command)
    {
        String authorizedActor = ServiceAuthorization.actor(
                authorizationGuard,
                ApplicationPermission.SECURITY_ADMIN,
                companyCodeSupplier.get(),
                "maintain application user",
                null);
        Objects.requireNonNull(command, "User details are required.");
        String auditActor = authorizedActor == null ? command.actor() : authorizedActor;
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company auditCompany = requireActiveCompany(em);
                AppUser user = command.id() == null
                        ? new AppUser()
                        : require(em, AppUser.class, command.id(), "user", LockModeType.PESSIMISTIC_WRITE);
                String before = user.getId() == null ? null : userSnapshot(user);
                String cleanUsername = normalizeUsername(command.username());
                String reservedCode = SecurityRepository.reservedUserCode(em, user.getId());
                if (reservedCode != null)
                {
                    if (!cleanUsername.equalsIgnoreCase(reservedCode))
                    {
                        throw new IllegalStateException(
                                "Reserved security account " + reservedCode + " cannot be renamed.");
                    }
                    if (!command.active())
                    {
                        throw new IllegalStateException(
                                "Reserved security account " + reservedCode + " must remain active.");
                    }
                    cleanUsername = reservedCode;
                }
                requireUniqueUsername(em, user.getId(), cleanUsername);
                if (!command.active() && user.isActive())
                {
                    long activeAssignments = countActiveAssignmentsForUser(em, user.getId());
                    if (activeAssignments > 0)
                    {
                        throw new IllegalStateException("End or revoke " + activeAssignments
                                + " active assignment(s) before deactivating user " + user.getUsername() + ".");
                    }
                }

                user.setUsername(cleanUsername);
                user.setDisplayName(requireText(command.displayName(), "Display name", 160));
                user.setEmail(optionalText(command.email(), "Email", 254));
                user.setActive(command.active());
                user.touchUpdatedAt();
                if (user.getId() == null)
                {
                    em.persist(user);
                }
                em.flush();
                String action = before == null ? "APP_USER_CREATED"
                        : command.active() ? "APP_USER_UPDATED" : "APP_USER_DEACTIVATED";
                em.persist(audit(auditCompany, auditActor, action, "AppUser", user.getId(),
                        actionSummary(action, user.getUsername()), before, userSnapshot(user), null));
                em.flush();
                commitHook.afterDomainWrite();
                em.getTransaction().commit();
                return user;
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    /** Creates or updates one global role by stable database ID. */
    public AppRole saveRole(AppRoleCommand command)
    {
        String authorizedActor = ServiceAuthorization.actor(
                authorizationGuard,
                ApplicationPermission.SECURITY_ADMIN,
                companyCodeSupplier.get(),
                "maintain application role",
                null);
        Objects.requireNonNull(command, "Role details are required.");
        String auditActor = authorizedActor == null ? command.actor() : authorizedActor;
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company auditCompany = requireActiveCompany(em);
                AppRole role = command.id() == null
                        ? new AppRole()
                        : require(em, AppRole.class, command.id(), "role", LockModeType.PESSIMISTIC_WRITE);
                String before = role.getId() == null ? null : roleSnapshot(role);
                String cleanCode = normalizeRoleCode(command.code());
                String reservedCode = SecurityRepository.reservedRoleCode(em, role.getId());
                if (reservedCode != null)
                {
                    if (!cleanCode.equalsIgnoreCase(reservedCode))
                    {
                        throw new IllegalStateException(
                                "Reserved security role " + reservedCode + " cannot be renamed.");
                    }
                    if (!command.active())
                    {
                        throw new IllegalStateException(
                                "Reserved security role " + reservedCode + " must remain active.");
                    }
                    cleanCode = reservedCode;
                }
                requireUniqueRoleCode(em, role.getId(), cleanCode);
                if (role.getId() != null && !command.active() && role.isActive())
                {
                    AppRoleUsage usage = roleUsage(em, role.getId());
                    if (!usage.canDeactivate())
                    {
                        throw new IllegalStateException("End or revoke " + usage.activeAssignments()
                                + " active assignment(s) before deactivating role " + role.getCode() + ".");
                    }
                }

                role.setCode(cleanCode);
                role.setName(requireText(command.name(), "Role name", 160));
                role.setDescription(optionalText(command.description(), "Role description", 1000));
                role.setActive(command.active());
                role.touchUpdatedAt();
                if (role.getId() == null)
                {
                    em.persist(role);
                }
                em.flush();
                String action = before == null ? "APP_ROLE_CREATED"
                        : command.active() ? "APP_ROLE_UPDATED" : "APP_ROLE_DEACTIVATED";
                em.persist(audit(auditCompany, auditActor, action, "AppRole", role.getId(),
                        actionSummary(action, role.getCode()), before, roleSnapshot(role), null));
                em.flush();
                commitHook.afterDomainWrite();
                em.getTransaction().commit();
                return role;
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    public AppRoleUsage roleUsage(long roleId)
    {
        try (EntityManager em = jpa.em())
        {
            require(em, AppRole.class, roleId, "role", LockModeType.NONE);
            return roleUsage(em, roleId);
        }
    }

    /** Creates a new history row; a previously ended row is never reactivated. */
    public UserCompanyRole assignRole(UserRoleAssignmentCommand command)
    {
        String authorizedActor = ServiceAuthorization.actor(
                authorizationGuard,
                ApplicationPermission.SECURITY_ADMIN,
                companyCodeSupplier.get(),
                "assign application role",
                null);
        Objects.requireNonNull(command, "Assignment details are required.");
        String auditActor = authorizedActor == null ? command.actor() : authorizedActor;
        if (command.userId() == null || command.roleId() == null)
        {
            throw new IllegalArgumentException("User and role are required.");
        }
        LocalDate startDate = Objects.requireNonNull(command.startDate(), "Assignment start date is required.");

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = requireActiveCompany(em);
                AppUser user = require(em, AppUser.class, command.userId(), "user", LockModeType.PESSIMISTIC_WRITE);
                AppRole role = require(em, AppRole.class, command.roleId(), "role", LockModeType.PESSIMISTIC_READ);
                if (!user.isActive())
                {
                    throw new IllegalStateException("Inactive users cannot receive new assignments.");
                }
                if (!role.isActive())
                {
                    throw new IllegalStateException("Inactive roles cannot receive new assignments.");
                }
                String reservedRole = SecurityRepository.reservedRoleCode(em, role.getId());
                if (ReservedSecurityRole.ADMIN.name().equals(reservedRole)
                        && !ReservedSecurityRole.ADMIN.name().equals(
                                SecurityRepository.reservedUserCode(em, user.getId())))
                {
                    throw new IllegalStateException(
                            "ADMIN may be assigned only to the singleton reserved ADMIN account.");
                }
                rejectOverlappingAssignment(em, user, company, role, startDate);

                UserCompanyRole assignment = new UserCompanyRole();
                assignment.setUser(user);
                assignment.setCompany(company);
                assignment.setRole(role);
                assignment.setStartDate(startDate);
                assignment.setActive(true);
                assignment.setRequiredSecurityAssignment(
                        ReservedSecurityRole.ADMIN.name().equals(reservedRole)
                                && ReservedSecurityRole.ADMIN.name().equals(
                                        SecurityRepository.reservedUserCode(em, user.getId())));
                assignment.touchUpdatedAt();
                em.persist(assignment);
                em.flush();
                em.persist(audit(company, auditActor, "USER_ROLE_ASSIGNED", "UserCompanyRole",
                        assignment.getId(), "assigned " + role.getCode() + " to " + user.getUsername()
                                + " for " + company.getCode(), null, assignmentSnapshot(assignment), null));
                em.flush();
                commitHook.afterDomainWrite();
                em.getTransaction().commit();
                return assignment;
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    /** Ends or revokes an assignment while retaining its stable history row. */
    public UserCompanyRole endAssignment(UserRoleAssignmentEndCommand command)
    {
        String authorizedActor = ServiceAuthorization.actor(
                authorizationGuard,
                ApplicationPermission.SECURITY_ADMIN,
                companyCodeSupplier.get(),
                "end or revoke application role assignment",
                null);
        Objects.requireNonNull(command, "Assignment end details are required.");
        String auditActor = authorizedActor == null ? command.actor() : authorizedActor;
        if (command.assignmentId() == null)
        {
            throw new IllegalArgumentException("Select an assignment to end or revoke.");
        }
        LocalDate endDate = Objects.requireNonNull(command.endDate(), "Assignment end date is required.");
        if (endDate.isAfter(LocalDate.now(clock)))
        {
            throw new IllegalArgumentException("Assignment end date cannot be in the future.");
        }
        String reason = optionalText(command.reason(), "Assignment reason", 1000);
        if (command.revoked() && reason == null)
        {
            throw new IllegalArgumentException("A revocation reason is required.");
        }

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = requireActiveCompany(em);
                UserCompanyRole assignment = requireAssignment(
                        em, company, command.assignmentId(), LockModeType.PESSIMISTIC_WRITE);
                if (!assignment.isActive())
                {
                    throw new IllegalStateException("The selected assignment already ended or was revoked.");
                }
                if (assignment.isRequiredSecurityAssignment())
                {
                    throw new IllegalStateException(
                            "The singleton ADMIN assignment is required for every company and cannot be ended or revoked.");
                }
                if (endDate.isBefore(assignment.getStartDate()))
                {
                    throw new IllegalArgumentException("Assignment end date cannot precede its start date.");
                }

                String before = assignmentSnapshot(assignment);
                assignment.setEndDate(endDate);
                assignment.setActive(false);
                assignment.setRevokedAt(command.revoked() ? Instant.now(clock) : null);
                assignment.setEndReason(reason);
                assignment.touchUpdatedAt();
                String action = command.revoked() ? "USER_ROLE_REVOKED" : "USER_ROLE_ENDED";
                em.persist(audit(company, auditActor, action, "UserCompanyRole", assignment.getId(),
                        (command.revoked() ? "revoked " : "ended ") + assignment.getRole().getCode()
                                + " for " + assignment.getUser().getUsername() + " in " + company.getCode(),
                        before, assignmentSnapshot(assignment), reason));
                em.flush();
                commitHook.afterDomainWrite();
                em.getTransaction().commit();
                return assignment;
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    private Company requireActiveCompany(EntityManager em)
    {
        Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCodeSupplier.get());
        if (!company.isActive())
        {
            throw new IllegalStateException("User administration requires an active company.");
        }
        return company;
    }

    private static void rejectOverlappingAssignment(
            EntityManager em,
            AppUser user,
            Company company,
            AppRole role,
            LocalDate startDate)
    {
        boolean overlap = em.createQuery("""
                        select a.id from UserCompanyRole a
                        where a.user = :user and a.company = :company and a.role = :role
                          and (a.endDate is null or a.endDate >= :startDate)
                        """, Long.class)
                .setParameter("user", user)
                .setParameter("company", company)
                .setParameter("role", role)
                .setParameter("startDate", startDate)
                .setMaxResults(1)
                .getResultStream()
                .findAny()
                .isPresent();
        if (overlap)
        {
            throw new IllegalStateException("This user already has an overlapping assignment for that role and company.");
        }
    }

    private static UserCompanyRole requireAssignment(
            EntityManager em,
            Company company,
            long assignmentId,
            LockModeType lockMode)
    {
        return em.createQuery("""
                        from UserCompanyRole a
                        join fetch a.user
                        join fetch a.company
                        join fetch a.role
                        where a.id = :id and a.company = :company
                        """, UserCompanyRole.class)
                .setParameter("id", assignmentId)
                .setParameter("company", company)
                .setLockMode(lockMode)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown assignment for active company: " + assignmentId + "."));
    }

    private static AppRoleUsage roleUsage(EntityManager em, long roleId)
    {
        long active = em.createQuery(
                        "select count(a) from UserCompanyRole a where a.role.id = :id and a.active = true",
                        Long.class)
                .setParameter("id", roleId)
                .getSingleResult();
        long historical = em.createQuery(
                        "select count(a) from UserCompanyRole a where a.role.id = :id and a.active = false",
                        Long.class)
                .setParameter("id", roleId)
                .getSingleResult();
        return new AppRoleUsage(active, historical);
    }

    private static long countActiveAssignmentsForUser(EntityManager em, Long userId)
    {
        if (userId == null)
        {
            return 0;
        }
        return em.createQuery(
                        "select count(a) from UserCompanyRole a where a.user.id = :id and a.active = true",
                        Long.class)
                .setParameter("id", userId)
                .getSingleResult();
    }

    private static void requireUniqueUsername(EntityManager em, Long userId, String username)
    {
        String jpql = userId == null
                ? "select u.id from AppUser u where lower(u.username) = :username"
                : "select u.id from AppUser u where lower(u.username) = :username and u.id <> :id";
        var query = em.createQuery(jpql, Long.class)
                .setParameter("username", username.toLowerCase(Locale.ROOT))
                .setMaxResults(1);
        if (userId != null)
        {
            query.setParameter("id", userId);
        }
        if (query.getResultStream().findAny().isPresent())
        {
            throw new IllegalArgumentException("Username already exists: " + username + ".");
        }
    }

    private static void requireUniqueRoleCode(EntityManager em, Long roleId, String code)
    {
        String jpql = roleId == null
                ? "select r.id from AppRole r where upper(r.code) = :code"
                : "select r.id from AppRole r where upper(r.code) = :code and r.id <> :id";
        var query = em.createQuery(jpql, Long.class)
                .setParameter("code", code.toUpperCase(Locale.ROOT))
                .setMaxResults(1);
        if (roleId != null)
        {
            query.setParameter("id", roleId);
        }
        if (query.getResultStream().findAny().isPresent())
        {
            throw new IllegalArgumentException("Role code already exists: " + code + ".");
        }
    }

    private static <T> T require(
            EntityManager em,
            Class<T> type,
            long id,
            String label,
            LockModeType lockMode)
    {
        T value = em.find(type, id, lockMode);
        if (value == null)
        {
            throw new IllegalArgumentException("Unknown " + label + " ID: " + id + ".");
        }
        return value;
    }

    private static AuditEvent audit(
            Company company,
            String actor,
            String action,
            String entityType,
            Long entityId,
            String summary,
            String before,
            String after,
            String reason)
    {
        AuditEvent event = new AuditEvent();
        event.setCompany(company);
        event.setActor(requireText(actor, "Actor", 200));
        event.setActionType(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId == null ? null : Long.toString(entityId));
        event.setSummary(requireText(summary, "Audit summary", 500));
        event.setBeforeValue(before);
        event.setAfterValue(after);
        event.setReason(reason);
        return event;
    }

    private static String userSnapshot(AppUser user)
    {
        return "id=" + user.getId() + ",username=" + user.getUsername() + ",displayName="
                + user.getDisplayName() + ",email=" + nullToBlank(user.getEmail()) + ",active=" + user.isActive();
    }

    private static String roleSnapshot(AppRole role)
    {
        return "id=" + role.getId() + ",code=" + role.getCode() + ",name=" + role.getName()
                + ",description=" + nullToBlank(role.getDescription()) + ",active=" + role.isActive();
    }

    private static String assignmentSnapshot(UserCompanyRole assignment)
    {
        return "id=" + assignment.getId() + ",userId=" + assignment.getUser().getId()
                + ",companyId=" + assignment.getCompany().getId() + ",roleId=" + assignment.getRole().getId()
                + ",startDate=" + assignment.getStartDate() + ",endDate=" + assignment.getEndDate()
                + ",active=" + assignment.isActive() + ",requiredSecurity="
                + assignment.isRequiredSecurityAssignment() + ",revokedAt=" + assignment.getRevokedAt();
    }

    private static String actionSummary(String action, String subject)
    {
        return action.toLowerCase(Locale.ROOT).replace('_', ' ') + " " + subject;
    }

    private static String normalizeUsername(String value)
    {
        return requireText(value, "Username", 80).toLowerCase(Locale.ROOT);
    }

    private static String normalizeRoleCode(String value)
    {
        return requireText(value, "Role code", 80).toUpperCase(Locale.ROOT);
    }

    private static String requireText(String value, String label, int maxLength)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required.");
        }
        String clean = value.trim();
        if (clean.length() > maxLength)
        {
            throw new IllegalArgumentException(label + " must be at most " + maxLength + " characters.");
        }
        return clean;
    }

    private static String optionalText(String value, String label, int maxLength)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        String clean = value.trim();
        if (clean.length() > maxLength)
        {
            throw new IllegalArgumentException(label + " must be at most " + maxLength + " characters.");
        }
        return clean;
    }

    private static String nullToBlank(String value)
    {
        return value == null ? "" : value;
    }

    private static void rollback(EntityManager em)
    {
        if (em.getTransaction().isActive())
        {
            em.getTransaction().rollback();
        }
    }
}
