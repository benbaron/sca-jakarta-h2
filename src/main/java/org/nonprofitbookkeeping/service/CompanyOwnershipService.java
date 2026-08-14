package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountingPeriod;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyOwnershipIssue;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.model.Merchant;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Central company-ownership validation used by all P15-capable services. */
@ApplicationScoped
public class CompanyOwnershipService
{
    private static final Set<String> DIRECT_ASSIGNMENT_TYPES = Set.of(
            "CHART_OF_ACCOUNTS", "TXN", "FUND", "BUDGET_CATEGORY", "BUDGET_PLAN",
            "ACTIVITY", "COUNTERPARTY", "MERCHANT", "ACCOUNTING_PERIOD", "AUDIT_EVENT",
            "PERIOD_CLOSE_RANGE", "PERIOD_CLOSE_EVENT");

    private final Jpa jpa;

    @Inject
    public CompanyOwnershipService(Jpa jpa)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
    }

    public Company requireCompany(EntityManager em, String companyCode)
    {
        String code = requireText(companyCode, "Company code").toUpperCase(Locale.ROOT);
        List<Company> matches = em.createQuery(
                        "from Company c where upper(c.code) = :code",
                        Company.class)
                .setParameter("code", code)
                .setMaxResults(2)
                .getResultList();
        if (matches.isEmpty())
        {
            throw new CompanyOwnershipException("Company does not exist: " + code + ".");
        }
        if (matches.size() != 1)
        {
            throw new CompanyOwnershipException("Company code is ambiguous: " + code + ".");
        }
        return matches.get(0);
    }

    public Company requireCompany(String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            return requireCompany(em, companyCode);
        }
    }

    public void requireOwnedBy(Company expected, Company actual, String label)
    {
        if (expected == null || expected.getId() == null)
        {
            throw new CompanyOwnershipException("Expected company is not persisted.");
        }
        if (actual == null || actual.getId() == null)
        {
            throw new CompanyOwnershipException(label + " has no authoritative company owner.");
        }
        if (!expected.getId().equals(actual.getId()))
        {
            throw new CompanyOwnershipException(label + " belongs to company " + actual.getCode()
                    + ", not " + expected.getCode() + ".");
        }
    }

    public void requireOwnedBy(Company expected, ChartOfAccounts chart, String label)
    {
        requireOwnedBy(expected, chart == null ? null : chart.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, Account account, String label)
    {
        requireOwnedBy(expected, account == null ? null : account.getChart(), label);
    }

    public void requireOwnedBy(Company expected, Fund fund, String label)
    {
        requireOwnedBy(expected, fund == null ? null : fund.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, FixedAsset asset, String label)
    {
        requireOwnedBy(expected, asset == null ? null : asset.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, InventoryItem item, String label)
    {
        requireOwnedBy(expected, item == null ? null : item.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, BudgetCategory category, String label)
    {
        requireOwnedBy(expected, category == null ? null : category.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, BudgetPlan plan, String label)
    {
        requireOwnedBy(expected, plan == null ? null : plan.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, Activity activity, String label)
    {
        requireOwnedBy(expected, activity == null ? null : activity.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, Counterparty counterparty, String label)
    {
        requireOwnedBy(expected, counterparty == null ? null : counterparty.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, Merchant merchant, String label)
    {
        requireOwnedBy(expected, merchant == null ? null : merchant.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, Txn transaction, String label)
    {
        requireOwnedBy(expected, transaction == null ? null : transaction.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, AccountingPeriod period, String label)
    {
        requireOwnedBy(expected, period == null ? null : period.getCompany(), label);
    }


    /**
     * Adopts an unowned legacy row only when the database contains exactly one
     * company. That is deterministic database evidence, not the selected UI
     * company. Multi-company ambiguity is always rejected.
     */
    public void ensureOwnedBy(EntityManager em, Company expected, ChartOfAccounts chart, String label)
    {
        if (chart == null)
        {
            throw new CompanyOwnershipException(label + " is required.");
        }
        if (chart.getCompany() == null && isOnlyCompany(em, expected))
        {
            chart.setCompany(expected);
        }
        requireOwnedBy(expected, chart, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, Fund fund, String label)
    {
        if (fund == null)
        {
            throw new CompanyOwnershipException(label + " is required.");
        }
        if (fund.getCompany() == null && isOnlyCompany(em, expected))
        {
            fund.setCompany(expected);
        }
        requireOwnedBy(expected, fund, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, FixedAsset asset, String label)
    {
        if (asset == null)
        {
            throw new CompanyOwnershipException(label + " is required.");
        }
        if (asset.getCompany() == null && isOnlyCompany(em, expected))
        {
            asset.setCompany(expected);
        }
        requireOwnedBy(expected, asset, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, InventoryItem item, String label)
    {
        if (item == null)
        {
            throw new CompanyOwnershipException(label + " is required.");
        }
        if (item.getCompany() == null && isOnlyCompany(em, expected))
        {
            item.setCompany(expected);
        }
        requireOwnedBy(expected, item, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, BudgetCategory category, String label)
    {
        if (category == null)
        {
            return;
        }
        if (category.getCompany() == null && isOnlyCompany(em, expected))
        {
            category.setCompany(expected);
        }
        requireOwnedBy(expected, category, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, BudgetPlan plan, String label)
    {
        if (plan == null)
        {
            throw new CompanyOwnershipException(label + " is required.");
        }
        if (plan.getCompany() == null && isOnlyCompany(em, expected))
        {
            plan.setCompany(expected);
        }
        requireOwnedBy(expected, plan, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, Activity activity, String label)
    {
        if (activity == null)
        {
            return;
        }
        if (activity.getCompany() == null && isOnlyCompany(em, expected))
        {
            activity.setCompany(expected);
        }
        requireOwnedBy(expected, activity, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, Counterparty counterparty, String label)
    {
        if (counterparty == null)
        {
            return;
        }
        if (counterparty.getCompany() == null && isOnlyCompany(em, expected))
        {
            counterparty.setCompany(expected);
        }
        requireOwnedBy(expected, counterparty, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, Merchant merchant, String label)
    {
        if (merchant == null)
        {
            return;
        }
        if (merchant.getCompany() == null && isOnlyCompany(em, expected))
        {
            merchant.setCompany(expected);
        }
        requireOwnedBy(expected, merchant, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, Account account, String label)
    {
        if (account == null)
        {
            throw new CompanyOwnershipException(label + " is required.");
        }
        ensureOwnedBy(em, expected, account.getChart(), label + " chart");
    }

    public void ensureOwnedBy(EntityManager em, Company expected, Txn transaction, String label)
    {
        if (transaction == null)
        {
            throw new CompanyOwnershipException(label + " is required.");
        }
        if (transaction.getCompany() == null && isOnlyCompany(em, expected))
        {
            transaction.setCompany(expected);
        }
        requireOwnedBy(expected, transaction, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, AccountingPeriod period, String label)
    {
        if (period == null)
        {
            throw new CompanyOwnershipException(label + " is required.");
        }
        if (period.getCompany() == null && isOnlyCompany(em, expected))
        {
            period.setCompany(expected);
        }
        requireOwnedBy(expected, period, label);
    }

    private static boolean isOnlyCompany(EntityManager em, Company expected)
    {
        Long count = em.createQuery("select count(c) from Company c", Long.class).getSingleResult();
        if (count != 1L)
        {
            return false;
        }
        Long onlyId = em.createQuery("select min(c.id) from Company c", Long.class).getSingleResult();
        return expected != null && expected.getId() != null && expected.getId().equals(onlyId);
    }

    public List<CompanyOwnershipIssueView> listOpenIssues()
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                            "from CompanyOwnershipIssue i where i.resolvedAt is null order by i.entityType, i.entityId",
                            CompanyOwnershipIssue.class)
                    .getResultList()
                    .stream()
                    .map(issue -> toView(em, issue))
                    .toList();
        }
    }

    public void requireNoOpenOwnershipIssues()
    {
        List<CompanyOwnershipIssueView> issues = listOpenIssues();
        if (!issues.isEmpty())
        {
            CompanyOwnershipIssueView first = issues.get(0);
            throw new CompanyOwnershipException("Company ownership has " + issues.size()
                    + " unresolved diagnostic(s); first is " + first.entityType() + " "
                    + first.entityId() + ": " + first.details());
        }
    }

    /**
     * Assigns one ownerless legacy entity after an administrator explicitly
     * identifies its real company. Cross-company reference diagnostics are not
     * rewritten here because doing so would guess at accounting relationships.
     */
    public CompanyOwnershipRepairResult assignOwner(
            long issueId,
            long companyId,
            String actor,
            String reason)
    {
        String cleanActor = requireBoundedText(actor, "Actor", 200);
        String cleanReason = requireBoundedText(reason, "Reason", 1000);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                CompanyOwnershipIssue issue = em.find(
                        CompanyOwnershipIssue.class, issueId, LockModeType.PESSIMISTIC_WRITE);
                if (issue == null || issue.getResolvedAt() != null)
                {
                    throw new CompanyOwnershipException(
                            "Ownership diagnostic " + issueId + " is no longer open. Refresh the list.");
                }
                if (!"UNRESOLVED_OWNER".equals(issue.getIssueCode())
                        || !supportsDirectAssignment(issue.getEntityType()))
                {
                    throw new CompanyOwnershipException(
                            "Diagnostic " + issueId + " cannot be resolved by assigning one owner. "
                                    + "Correct its conflicting accounting references instead.");
                }

                Company company = em.find(Company.class, companyId, LockModeType.PESSIMISTIC_READ);
                if (company == null)
                {
                    throw new CompanyOwnershipException("Company not found: ID " + companyId + ".");
                }
                if (!company.isActive())
                {
                    throw new CompanyOwnershipException(
                            "Company " + company.getCode() + " is inactive. Select an active company.");
                }

                if (issue.getEntityType().startsWith("PERIOD_CLOSE_"))
                {
                    assignPeriodCloseOwner(em, issue, company);
                }
                else
                {
                    long entityId = parseEntityId(issue);
                    assignDirectOwner(em, issue, entityId, company);
                }
                issue.resolve();
                em.persist(repairAudit(company, issue, cleanActor, cleanReason));
                em.flush();

                long remaining = em.createQuery(
                                "select count(i) from CompanyOwnershipIssue i where i.resolvedAt is null",
                                Long.class)
                        .getSingleResult();
                CompanyOwnershipRepairResult result = new CompanyOwnershipRepairResult(
                        issueId,
                        issue.getEntityType(),
                        issue.getEntityId(),
                        company.getId(),
                        company.getCode(),
                        Math.toIntExact(remaining));
                em.getTransaction().commit();
                return result;
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

    public static boolean supportsDirectAssignment(String entityType)
    {
        return DIRECT_ASSIGNMENT_TYPES.contains(entityType);
    }

    private static long parseEntityId(CompanyOwnershipIssue issue)
    {
        try
        {
            return Long.parseLong(issue.getEntityId());
        }
        catch (NumberFormatException ex)
        {
            throw new CompanyOwnershipException(
                    "Diagnostic " + issue.getId() + " has an invalid " + issue.getEntityType()
                            + " record ID: " + issue.getEntityId() + ".");
        }
    }

    private static void assignDirectOwner(
            EntityManager em,
            CompanyOwnershipIssue issue,
            long entityId,
            Company company)
    {
        Object entity = switch (issue.getEntityType())
        {
            case "CHART_OF_ACCOUNTS" -> em.find(ChartOfAccounts.class, entityId, LockModeType.PESSIMISTIC_WRITE);
            case "TXN" -> em.find(Txn.class, entityId, LockModeType.PESSIMISTIC_WRITE);
            case "FUND" -> em.find(Fund.class, entityId, LockModeType.PESSIMISTIC_WRITE);
            case "BUDGET_CATEGORY" -> em.find(BudgetCategory.class, entityId, LockModeType.PESSIMISTIC_WRITE);
            case "BUDGET_PLAN" -> em.find(BudgetPlan.class, entityId, LockModeType.PESSIMISTIC_WRITE);
            case "ACTIVITY" -> em.find(Activity.class, entityId, LockModeType.PESSIMISTIC_WRITE);
            case "COUNTERPARTY" -> em.find(Counterparty.class, entityId, LockModeType.PESSIMISTIC_WRITE);
            case "MERCHANT" -> em.find(Merchant.class, entityId, LockModeType.PESSIMISTIC_WRITE);
            case "ACCOUNTING_PERIOD" -> em.find(AccountingPeriod.class, entityId, LockModeType.PESSIMISTIC_WRITE);
            case "AUDIT_EVENT" -> em.find(AuditEvent.class, entityId, LockModeType.PESSIMISTIC_WRITE);
            default -> null;
        };
        if (entity == null)
        {
            throw new CompanyOwnershipException(
                    issue.getEntityType() + " record " + issue.getEntityId()
                            + " no longer exists. Restore the database or have an administrator close the stale diagnostic.");
        }

        Company current = companyOf(entity);
        if (current != null)
        {
            throw new CompanyOwnershipException(
                    issue.getEntityType() + " record " + issue.getEntityId() + " already belongs to company "
                            + current.getCode() + ". Refresh the diagnostics before making another change.");
        }
        requireRelationshipEvidenceCompatible(em, issue, entityId, company);
        setCompany(entity, company);
    }

    private static void requireRelationshipEvidenceCompatible(
            EntityManager em,
            CompanyOwnershipIssue issue,
            long entityId,
            Company selectedCompany)
    {
        String query = relationshipOwnerQuery(issue.getEntityType());
        if (query == null)
        {
            return;
        }
        Set<Long> ownerIds = relationshipOwnerIds(em, query, entityId);
        ownerIds.remove(selectedCompany.getId());
        if (ownerIds.isEmpty())
        {
            return;
        }
        List<String> companies = ownerIds.stream()
                .map(id -> {
                    Company value = em.find(Company.class, id);
                    return value == null ? "company ID " + id : value.getCode();
                })
                .toList();
        throw new CompanyOwnershipException(
                issue.getEntityType() + " record " + issue.getEntityId()
                        + " is referenced by records owned by " + String.join(", ", companies)
                        + ". Correct those accounting references before assigning one owner.");
    }

    private static Set<Long> relationshipOwnerIds(EntityManager em, String query, long entityId)
    {
        Set<Long> ownerIds = new LinkedHashSet<>();
        for (Object value : em.createNativeQuery(query)
                .setParameter("entityId", entityId)
                .getResultList())
        {
            if (value instanceof Number number)
            {
                ownerIds.add(number.longValue());
            }
        }
        return ownerIds;
    }

    private static String relationshipOwnerQuery(String entityType)
    {
        return switch (entityType)
        {
            case "CHART_OF_ACCOUNTS" -> """
                    select id from company
                    where active_chart_of_accounts_id = :entityId
                    union
                    select t.company_id from txn_split s
                    join txn t on t.id = s.txn_id
                    join account a on a.id = s.account_id
                    where a.chart_id = :entityId and t.company_id is not null
                    """;
            case "TXN" -> """
                    select chart.company_id from txn_split s
                    join account a on a.id = s.account_id
                    join chart_of_accounts chart on chart.id = a.chart_id
                    where s.txn_id = :entityId and chart.company_id is not null
                    union
                    select fund.company_id from txn_split s
                    join fund on fund.id = s.fund_id
                    where s.txn_id = :entityId and fund.company_id is not null
                    union
                    select category.company_id from txn_split s
                    join budget_category category on category.id = s.budget_category_id
                    where s.txn_id = :entityId and category.company_id is not null
                    union
                    select activity.company_id from txn_split s
                    join activity on activity.id = s.activity_id
                    where s.txn_id = :entityId and activity.company_id is not null
                    union
                    select merchant.company_id from txn_split s
                    join merchant on merchant.id = s.merchant_id
                    where s.txn_id = :entityId and merchant.company_id is not null
                    union
                    select counterparty.company_id from txn t
                    join counterparty on counterparty.id = t.payee_id
                    where t.id = :entityId and counterparty.company_id is not null
                    union
                    select chart.company_id from txn t
                    join account a on a.id = t.bank_account_id
                    join chart_of_accounts chart on chart.id = a.chart_id
                    where t.id = :entityId and chart.company_id is not null
                    """;
            case "FUND" -> """
                    select t.company_id from txn_split s join txn t on t.id = s.txn_id
                    where s.fund_id = :entityId and t.company_id is not null
                    union
                    select p.company_id from budget_line l join budget_plan p on p.id = l.budget_plan_id
                    where l.fund_id = :entityId and p.company_id is not null
                    union
                    select company_id from fixed_asset where fund_id = :entityId and company_id is not null
                    union
                    select company_id from inventory_item where fund_id = :entityId and company_id is not null
                    """;
            case "BUDGET_CATEGORY" -> """
                    select t.company_id from txn_split s join txn t on t.id = s.txn_id
                    where s.budget_category_id = :entityId and t.company_id is not null
                    union
                    select p.company_id from budget_line l join budget_plan p on p.id = l.budget_plan_id
                    where l.budget_category_id = :entityId and p.company_id is not null
                    """;
            case "BUDGET_PLAN" -> """
                    select category.company_id from budget_line l
                    join budget_category category on category.id = l.budget_category_id
                    where l.budget_plan_id = :entityId and category.company_id is not null
                    union
                    select fund.company_id from budget_line l join fund on fund.id = l.fund_id
                    where l.budget_plan_id = :entityId and fund.company_id is not null
                    """;
            case "ACTIVITY" -> """
                    select t.company_id from txn_split s join txn t on t.id = s.txn_id
                    where s.activity_id = :entityId and t.company_id is not null
                    """;
            case "COUNTERPARTY" -> """
                    select company_id from txn
                    where payee_id = :entityId and company_id is not null
                    """;
            case "MERCHANT" -> """
                    select t.company_id from txn_split s join txn t on t.id = s.txn_id
                    where s.merchant_id = :entityId and t.company_id is not null
                    """;
            default -> null;
        };
    }

    private static void assignPeriodCloseOwner(
            EntityManager em,
            CompanyOwnershipIssue issue,
            Company company)
    {
        String table = switch (issue.getEntityType())
        {
            case "PERIOD_CLOSE_RANGE" -> "period_close_range";
            case "PERIOD_CLOSE_EVENT" -> "period_close_event";
            default -> throw new IllegalArgumentException(
                    "Unsupported period-close diagnostic: " + issue.getEntityType());
        };
        try
        {
            UUID.fromString(issue.getEntityId());
        }
        catch (IllegalArgumentException ex)
        {
            throw new CompanyOwnershipException(
                    "Diagnostic " + issue.getId() + " has an invalid " + issue.getEntityType()
                            + " UUID: " + issue.getEntityId() + ".");
        }

        if ("PERIOD_CLOSE_EVENT".equals(issue.getEntityType()))
        {
            List<?> rangeOwners = em.createNativeQuery("""
                            select r.company_id
                            from period_close_event e
                            join period_close_range r on r.id = e.close_range_id
                            where e.id = cast(? as uuid)
                            """)
                    .setParameter(1, issue.getEntityId())
                    .getResultList();
            if (rangeOwners.isEmpty())
            {
                throw new CompanyOwnershipException(
                        "PERIOD_CLOSE_EVENT record " + issue.getEntityId() + " no longer exists.");
            }
            Object rangeOwner = rangeOwners.get(0);
            if (rangeOwner == null)
            {
                throw new CompanyOwnershipException(
                        "Assign the owning PERIOD_CLOSE_RANGE before assigning event "
                                + issue.getEntityId() + ".");
            }
            if (((Number) rangeOwner).longValue() != company.getId())
            {
                throw new CompanyOwnershipException(
                        "The event's close range belongs to another company. Select that company or correct "
                                + "the close-range ownership first.");
            }
        }

        List<?> rows = em.createNativeQuery(
                        "select company_id from " + table + " where id = cast(? as uuid)")
                .setParameter(1, issue.getEntityId())
                .getResultList();
        if (rows.isEmpty())
        {
            throw new CompanyOwnershipException(
                    issue.getEntityType() + " record " + issue.getEntityId() + " no longer exists.");
        }
        Object current = rows.get(0);
        if (current != null)
        {
            throw new CompanyOwnershipException(
                    issue.getEntityType() + " record " + issue.getEntityId()
                            + " already belongs to a company. Refresh the diagnostics.");
        }
        em.createNativeQuery("update " + table
                        + " set company_id = ?, company_code = ? where id = cast(? as uuid) and company_id is null")
                .setParameter(1, company.getId())
                .setParameter(2, company.getCode())
                .setParameter(3, issue.getEntityId())
                .executeUpdate();
    }

    private static Company companyOf(Object entity)
    {
        if (entity instanceof ChartOfAccounts value) return value.getCompany();
        if (entity instanceof Txn value) return value.getCompany();
        if (entity instanceof Fund value) return value.getCompany();
        if (entity instanceof BudgetCategory value) return value.getCompany();
        if (entity instanceof BudgetPlan value) return value.getCompany();
        if (entity instanceof Activity value) return value.getCompany();
        if (entity instanceof Counterparty value) return value.getCompany();
        if (entity instanceof Merchant value) return value.getCompany();
        if (entity instanceof AccountingPeriod value) return value.getCompany();
        if (entity instanceof AuditEvent value) return value.getCompany();
        throw new IllegalArgumentException("Unsupported ownership entity: " + entity.getClass().getSimpleName());
    }

    private static void setCompany(Object entity, Company company)
    {
        if (entity instanceof ChartOfAccounts value) value.setCompany(company);
        else if (entity instanceof Txn value) value.setCompany(company);
        else if (entity instanceof Fund value) value.setCompany(company);
        else if (entity instanceof BudgetCategory value) value.setCompany(company);
        else if (entity instanceof BudgetPlan value) value.setCompany(company);
        else if (entity instanceof Activity value) value.setCompany(company);
        else if (entity instanceof Counterparty value) value.setCompany(company);
        else if (entity instanceof Merchant value) value.setCompany(company);
        else if (entity instanceof AccountingPeriod value) value.setCompany(company);
        else if (entity instanceof AuditEvent value) value.setCompany(company);
        else throw new IllegalArgumentException(
                    "Unsupported ownership entity: " + entity.getClass().getSimpleName());
    }

    private static AuditEvent repairAudit(
            Company company,
            CompanyOwnershipIssue issue,
            String actor,
            String reason)
    {
        AuditEvent audit = new AuditEvent();
        audit.setCompany(company);
        audit.setActor(actor);
        audit.setActionType("COMPANY_OWNERSHIP_ASSIGNED");
        audit.setEntityType(issue.getEntityType());
        audit.setEntityId(issue.getEntityId());
        audit.setSummary("Assigned legacy " + issue.getEntityType() + " record "
                + issue.getEntityId() + " to company " + company.getCode() + ".");
        audit.setBeforeValue("company=null; diagnostic=" + issue.getIssueCode());
        audit.setAfterValue("company=" + company.getCode());
        audit.setReason(reason);
        return audit;
    }

    private static CompanyOwnershipIssueView toView(EntityManager em, CompanyOwnershipIssue issue)
    {
        return new CompanyOwnershipIssueView(
                issue.getId(),
                issue.getEntityType(),
                issue.getEntityId(),
                describeRecord(em, issue),
                relationshipCompanyCodes(em, issue),
                issue.getIssueCode(),
                issue.getCandidateCompanyCount(),
                issue.getDetails(),
                issue.getDetectedAt());
    }

    private static List<String> relationshipCompanyCodes(EntityManager em, CompanyOwnershipIssue issue)
    {
        long entityId;
        try
        {
            entityId = Long.parseLong(issue.getEntityId());
        }
        catch (NumberFormatException ex)
        {
            return List.of();
        }
        String query = relationshipOwnerQuery(issue.getEntityType());
        if (query == null)
        {
            return List.of();
        }
        return relationshipOwnerIds(em, query, entityId).stream()
                .map(id -> em.find(Company.class, id))
                .filter(Objects::nonNull)
                .map(Company::getCode)
                .sorted()
                .toList();
    }

    private static String describeRecord(EntityManager em, CompanyOwnershipIssue issue)
    {
        if (issue.getEntityType().startsWith("PERIOD_CLOSE_"))
        {
            return describePeriodCloseRecord(em, issue);
        }
        long id;
        try
        {
            id = Long.parseLong(issue.getEntityId());
        }
        catch (NumberFormatException ex)
        {
            return issue.getEntityType() + " " + issue.getEntityId();
        }
        return switch (issue.getEntityType())
        {
            case "CHART_OF_ACCOUNTS" -> {
                ChartOfAccounts value = em.find(ChartOfAccounts.class, id);
                yield value == null ? missing(issue) : value.getName() + " (version " + value.getVersion() + ")";
            }
            case "TXN" -> {
                Txn value = em.find(Txn.class, id);
                yield value == null ? missing(issue) : value.getTxnDate() + " — " + textOr(value.getMemo(), "No memo");
            }
            case "FUND" -> {
                Fund value = em.find(Fund.class, id);
                yield value == null ? missing(issue) : value.getCode() + " — " + value.getName();
            }
            case "BUDGET_CATEGORY" -> {
                BudgetCategory value = em.find(BudgetCategory.class, id);
                yield value == null ? missing(issue) : value.getCode() + " — " + value.getName();
            }
            case "BUDGET_PLAN" -> {
                BudgetPlan value = em.find(BudgetPlan.class, id);
                yield value == null ? missing(issue) : value.getName() + " (FY " + value.getFiscalYear() + ")";
            }
            case "ACTIVITY" -> {
                Activity value = em.find(Activity.class, id);
                yield value == null ? missing(issue) : value.getCode() + " — " + value.getName();
            }
            case "COUNTERPARTY" -> {
                Counterparty value = em.find(Counterparty.class, id);
                yield value == null ? missing(issue) : value.getDisplayName();
            }
            case "MERCHANT" -> {
                Merchant value = em.find(Merchant.class, id);
                yield value == null ? missing(issue) : value.getName();
            }
            case "ACCOUNTING_PERIOD" -> {
                AccountingPeriod value = em.find(AccountingPeriod.class, id);
                yield value == null ? missing(issue)
                        : "FY " + value.getFiscalYear() + ", period " + value.getPeriodNumber()
                                + " (" + value.getStartDate() + " to " + value.getEndDate() + ")";
            }
            case "AUDIT_EVENT" -> {
                AuditEvent value = em.find(AuditEvent.class, id);
                yield value == null ? missing(issue) : value.getActionType() + " — " + value.getSummary();
            }
            default -> issue.getEntityType() + " " + issue.getEntityId();
        };
    }

    private static String describePeriodCloseRecord(EntityManager em, CompanyOwnershipIssue issue)
    {
        String table = "PERIOD_CLOSE_RANGE".equals(issue.getEntityType())
                ? "period_close_range" : "period_close_event";
        String columns = "PERIOD_CLOSE_RANGE".equals(issue.getEntityType())
                ? "start_date, end_date" : "event_type, event_at";
        try
        {
            List<?> rows = em.createNativeQuery(
                            "select " + columns + " from " + table + " where id = cast(? as uuid)")
                    .setParameter(1, issue.getEntityId())
                    .getResultList();
            if (rows.isEmpty())
            {
                return missing(issue);
            }
            Object[] values = (Object[]) rows.get(0);
            return values[0] + " — " + values[1];
        }
        catch (RuntimeException ex)
        {
            return issue.getEntityType() + " " + issue.getEntityId();
        }
    }

    private static String missing(CompanyOwnershipIssue issue)
    {
        return issue.getEntityType() + " " + issue.getEntityId() + " (record missing)";
    }

    private static String textOr(String value, String fallback)
    {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String requireText(String value, String label)
    {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty())
        {
            throw new IllegalArgumentException(label + " is required.");
        }
        return text;
    }

    private static String requireBoundedText(String value, String label, int limit)
    {
        String text = requireText(value, label);
        if (text.codePointCount(0, text.length()) > limit)
        {
            throw new IllegalArgumentException(label + " must not exceed " + limit + " characters.");
        }
        return text;
    }
}
