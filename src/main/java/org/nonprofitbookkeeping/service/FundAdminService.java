package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Objects;
import java.util.function.Supplier;

/** Service-owned Fund create, edit, deactivate, usage, and protected-delete boundary. */
@ApplicationScoped
public class FundAdminService
{
    @Inject
    Jpa jpa;

    private Supplier<String> companyCodeSupplier = () -> "DEFAULT";
    private AuthorizationGuard authorizationGuard;

    public FundAdminService()
    {
    }

    public FundAdminService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT");
    }

    public FundAdminService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this(jpa, companyCodeSupplier, null);
    }

    public FundAdminService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            AuthorizationGuard authorizationGuard)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.authorizationGuard = authorizationGuard;
    }

    /**
     * Creates a new fund when {@link FundCommand#id()} is null and updates the
     * identified fund otherwise. Code changes never select a different row.
     */
    public Fund save(FundCommand command)
    {
        ServiceAuthorization.require(authorizationGuard, ApplicationPermission.BOOKKEEPING_WRITE,
                companyCodeSupplier.get(), "save fund");
        if (command == null)
        {
            throw new IllegalArgumentException("Fund details are required.");
        }

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, companyCodeSupplier.get());
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);
                Fund fund = command.id() == null
                        ? new Fund()
                        : requireFund(em, command.id());
                if (fund.getCompany() == null)
                {
                    fund.setCompany(company);
                }
                ownership.ensureOwnedBy(em, company, fund, "Fund");
                apply(em, ownership, company, fund, command);
                if (fund.getId() == null)
                {
                    em.persist(fund);
                }
                em.getTransaction().commit();
                return fund;
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw mapPersistenceError(ex, normalizeCode(command.code()));
            }
        }
    }

    /** Compatibility boundary for older callers that intentionally address a fund by code. */
    public Fund upsert(String code, String name, FundType fundType, boolean active)
    {
        ServiceAuthorization.require(authorizationGuard, ApplicationPermission.BOOKKEEPING_WRITE,
                companyCodeSupplier.get(), "save fund");
        String cleanCode = normalizeCode(code);
        String cleanName = requireText(name, "Fund name", 200);
        if (fundType == null)
        {
            throw new IllegalArgumentException("Fund type is required.");
        }

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, companyCodeSupplier.get());
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);
                Fund existing = em.createQuery(
                                "from Fund f left join fetch f.parent where (f.company = :company or f.company is null) and upper(f.code) = :code",
                                Fund.class)
                        .setParameter("company", company)
                        .setParameter("code", cleanCode)
                        .setMaxResults(1)
                        .getResultStream()
                        .findFirst()
                        .orElse(null);
                FundCommand command = new FundCommand(
                        existing == null ? null : existing.getId(),
                        cleanCode,
                        cleanName,
                        fundType,
                        active,
                        existing == null || existing.getParent() == null ? null : existing.getParent().getId(),
                        existing == null ? null : existing.getEffectiveFrom(),
                        existing == null ? null : existing.getEffectiveTo(),
                        existing == null ? null : existing.getRestrictionText());
                Fund fund = existing == null ? new Fund() : existing;
                if (fund.getCompany() == null)
                {
                    fund.setCompany(company);
                }
                ownership.ensureOwnedBy(em, company, fund, "Fund");
                apply(em, ownership, company, fund, command);
                if (fund.getId() == null)
                {
                    em.persist(fund);
                }
                em.getTransaction().commit();
                return fund;
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw mapPersistenceError(ex, cleanCode);
            }
        }
    }

    public FundUsage usage(long fundId)
    {
        try (EntityManager em = jpa.em())
        {
            Fund fund = requireFund(em, fundId);
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCodeSupplier.get());
            new CompanyOwnershipService(jpa).ensureOwnedBy(em, company, fund, "Fund");
            return usage(em, fundId);
        }
    }

    /**
     * Physically removes only a fund that has never been referenced. Referenced
     * funds remain part of accounting history and must instead be deactivated.
     */
    public void deleteUnused(long fundId)
    {
        ServiceAuthorization.require(authorizationGuard, ApplicationPermission.BOOKKEEPING_WRITE,
                companyCodeSupplier.get(), "delete unused fund");
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, companyCodeSupplier.get());
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);
                Fund fund = requireFund(em, fundId);
                ownership.ensureOwnedBy(em, company, fund, "Fund");
                FundUsage usage = usage(em, fundId);
                if (!usage.canDelete())
                {
                    throw new IllegalStateException(
                            "Fund " + fund.getCode() + " is referenced by "
                                    + usage.describeReferences()
                                    + ". Deactivate it instead of deleting it.");
                }
                em.remove(fund);
                em.getTransaction().commit();
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    private static void apply(EntityManager em, CompanyOwnershipService ownership, Company company, Fund fund, FundCommand command)
    {
        String code = normalizeCode(command.code());
        String name = requireText(command.name(), "Fund name", 200);
        if (command.fundType() == null)
        {
            throw new IllegalArgumentException("Fund type is required.");
        }
        validateDates(command.effectiveFrom(), command.effectiveTo());
        requireUniqueCode(em, company, command.id(), code);

        Fund parent = loadValidatedParent(em, ownership, company, command.id(), command.parentFundId());
        validateHierarchyLifecycle(em, company, fund, parent, command.active());
        fund.setCode(code);
        fund.setName(name);
        fund.setFundType(command.fundType());
        fund.setParent(parent);
        fund.setActive(command.active());
        fund.setEffectiveFrom(command.effectiveFrom());
        fund.setEffectiveTo(command.effectiveTo());
        fund.setRestrictionText(blankToNull(command.restrictionText()));
        fund.touchUpdatedAt();
    }

    private static void validateHierarchyLifecycle(
            EntityManager em,
            Company company,
            Fund fund,
            Fund parent,
            boolean active)
    {
        if (active)
        {
            Fund cursor = parent;
            Set<Long> visited = new HashSet<>();
            while (cursor != null)
            {
                Long cursorId = cursor.getId();
                if (cursorId != null && !visited.add(cursorId))
                {
                    throw new IllegalArgumentException("The selected parent belongs to an existing circular fund hierarchy.");
                }
                if (!cursor.isActive())
                {
                    throw new IllegalStateException(
                            "Active fund requires an active parent hierarchy. Reactivate parent fund "
                                    + cursor.getCode() + " first.");
                }
                cursor = cursor.getParent();
            }
            return;
        }

        if (fund.getId() == null)
        {
            return;
        }
        Long activeChildren = em.createQuery(
                        "select count(f) from Fund f where f.company = :company and f.parent = :parent and f.active = true",
                        Long.class)
                .setParameter("company", company)
                .setParameter("parent", fund)
                .getSingleResult();
        if (activeChildren > 0L)
        {
            throw new IllegalStateException(
                    "Deactivate or reparent active child funds before deactivating fund "
                            + fund.getCode() + ".");
        }
    }

    private static Fund loadValidatedParent(EntityManager em, CompanyOwnershipService ownership, Company company, Long fundId, Long parentFundId)
    {
        if (parentFundId == null)
        {
            return null;
        }
        if (fundId != null && fundId.equals(parentFundId))
        {
            throw new IllegalArgumentException("A fund cannot be its own parent.");
        }

        Fund parent = requireFund(em, parentFundId);
        ownership.ensureOwnedBy(em, company, parent, "Parent fund");
        Set<Long> visited = new HashSet<>();
        Fund cursor = parent;
        while (cursor != null)
        {
            Long cursorId = cursor.getId();
            if (cursorId != null && !visited.add(cursorId))
            {
                throw new IllegalArgumentException("The selected parent belongs to an existing circular fund hierarchy.");
            }
            if (fundId != null && fundId.equals(cursorId))
            {
                throw new IllegalArgumentException("The selected parent would create a circular fund hierarchy.");
            }
            cursor = cursor.getParent();
        }
        return parent;
    }

    private static FundUsage usage(EntityManager em, long fundId)
    {
        return new FundUsage(
                count(em, "select count(s) from TxnSplit s where s.fund.id = :id", fundId),
                count(em, "select count(b) from BudgetLine b where b.fund.id = :id", fundId),
                count(em, "select count(a) from FixedAsset a where a.fund.id = :id", fundId),
                count(em, "select count(i) from InventoryItem i where i.fund.id = :id", fundId),
                count(em, "select count(a) from FundAlias a where a.fund.id = :id", fundId),
                count(em, "select count(t) from FundTransfer t where t.fromFund.id = :id or t.toFund.id = :id", fundId),
                count(em, "select count(f) from Fund f where f.parent.id = :id", fundId));
    }

    private static long count(EntityManager em, String jpql, long fundId)
    {
        return em.createQuery(jpql, Long.class)
                .setParameter("id", fundId)
                .getSingleResult();
    }

    private static void requireUniqueCode(EntityManager em, Company company, Long fundId, String code)
    {
        String jpql = fundId == null
                ? "select f.id from Fund f where f.company = :company and upper(f.code) = :code"
                : "select f.id from Fund f where f.company = :company and upper(f.code) = :code and f.id <> :id";
        var query = em.createQuery(jpql, Long.class)
                .setParameter("company", company)
                .setParameter("code", code)
                .setMaxResults(1);
        if (fundId != null)
        {
            query.setParameter("id", fundId);
        }
        if (query.getResultStream().findAny().isPresent())
        {
            throw new IllegalArgumentException("Fund code already exists: " + code + ".");
        }
    }

    private static Fund requireFund(EntityManager em, long fundId)
    {
        Fund fund = em.find(Fund.class, fundId);
        if (fund == null)
        {
            throw new IllegalArgumentException("Unknown fund ID: " + fundId + ".");
        }
        return fund;
    }

    private static void validateDates(LocalDate effectiveFrom, LocalDate effectiveTo)
    {
        if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom))
        {
            throw new IllegalArgumentException("Effective through date cannot be before effective from date.");
        }
    }

    private static String normalizeCode(String value)
    {
        return requireText(value, "Fund code", 64).toUpperCase(Locale.ROOT);
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

    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static RuntimeException mapPersistenceError(RuntimeException ex, String code)
    {
        if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException)
        {
            return ex;
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("uq_fund_company_code") || message.contains("unique") || message.contains("constraint"))
        {
            return new IllegalArgumentException("Fund code already exists: " + code + ".", ex);
        }
        return ex;
    }

    private static void rollback(EntityManager em)
    {
        if (em.getTransaction().isActive())
        {
            em.getTransaction().rollback();
        }
    }
}
