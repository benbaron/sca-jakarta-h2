package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AppRole;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.UserCompanyRole;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;

@ApplicationScoped
public class UserAdminService
{
    @Inject
    Jpa jpa;

    public UserAdminService() {}

    public UserAdminService(Jpa jpa)
    {
        this.jpa = jpa;
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

    public List<UserCompanyRole> listAssignments()
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("""
                    from UserCompanyRole a
                    join fetch a.user
                    join fetch a.company
                    join fetch a.role
                    order by a.company.code, a.user.username, a.role.code
                    """, UserCompanyRole.class).getResultList();
        }
    }

    public AppUser upsertUser(String username, String displayName, String email, boolean active)
    {
        String cleanUsername = requireText(username, "Username").toLowerCase();
        String cleanDisplayName = requireText(displayName, "Display name");
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                AppUser user = em.createQuery("from AppUser u where u.username = :username", AppUser.class)
                        .setParameter("username", cleanUsername)
                        .setMaxResults(1)
                        .getResultStream()
                        .findFirst()
                        .orElseGet(AppUser::new);
                user.setUsername(cleanUsername);
                user.setDisplayName(cleanDisplayName);
                user.setEmail(blankToNull(email));
                user.setActive(active);
                user.touchUpdatedAt();
                if (user.getId() == null)
                {
                    em.persist(user);
                }
                else
                {
                    user = em.merge(user);
                }
                em.getTransaction().commit();
                return user;
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw ex;
            }
        }
    }

    public UserCompanyRole assignRole(String username, String companyCode, String roleCode)
    {
        String cleanUsername = requireText(username, "Username").toLowerCase();
        String cleanCompany = requireText(companyCode, "Company code").toUpperCase();
        String cleanRole = requireText(roleCode, "Role code").toUpperCase();
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                AppUser user = single(em, "from AppUser u where u.username = :value", AppUser.class, cleanUsername, "user");
                Company company = single(em, "from Company c where c.code = :value", Company.class, cleanCompany, "company");
                AppRole role = single(em, "from AppRole r where r.code = :value", AppRole.class, cleanRole, "role");

                UserCompanyRole assignment = em.createQuery("""
                        from UserCompanyRole a
                        where a.user = :user and a.company = :company and a.role = :role
                        """, UserCompanyRole.class)
                        .setParameter("user", user)
                        .setParameter("company", company)
                        .setParameter("role", role)
                        .setMaxResults(1)
                        .getResultStream()
                        .findFirst()
                        .orElseGet(UserCompanyRole::new);
                assignment.setUser(user);
                assignment.setCompany(company);
                assignment.setRole(role);
                assignment.setActive(true);
                if (assignment.getId() == null)
                {
                    em.persist(assignment);
                }
                else
                {
                    assignment = em.merge(assignment);
                }
                em.getTransaction().commit();
                return assignment;
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw ex;
            }
        }
    }

    private static <T> T single(EntityManager em, String jpql, Class<T> type, String value, String label)
    {
        return em.createQuery(jpql, type)
                .setParameter("value", value)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + label + ": " + value));
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
