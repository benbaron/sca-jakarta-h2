package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@ApplicationScoped
/** Company-scoped account create/update service for the selected chart. */
public class AccountAdminService
{
    @Inject
    Jpa jpa;

    private Supplier<String> companyCodeSupplier = () -> "DEFAULT";
    private AuthorizationGuard authorizationGuard;

    public AccountAdminService()
    {
    }

    public AccountAdminService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT");
    }

    public AccountAdminService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this(jpa, companyCodeSupplier, null);
    }

    public AccountAdminService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            AuthorizationGuard authorizationGuard)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.authorizationGuard = authorizationGuard;
    }

    /**
     * Creates a new account when {@link AccountCommand#id()} is null and updates
     * that exact durable row otherwise. Account code is editable business data;
     * changing it never selects or creates a different account.
     */
    public Account save(AccountCommand command)
    {
        String companyCode = companyCodeSupplier.get();
        String actor = ServiceAuthorization.actor(authorizationGuard, ApplicationPermission.BOOKKEEPING_WRITE,
                companyCode, "save account", "SYSTEM");
        if (command == null)
        {
            throw new IllegalArgumentException("Account details are required.");
        }

        String cleanCode = requireText(command.code(), "Account code");
        String cleanName = requireText(command.name(), "Account name");
        if (command.accountType() == null)
        {
            throw new IllegalArgumentException("Account type is required.");
        }
        if (command.normalBalance() == null)
        {
            throw new IllegalArgumentException("Normal balance is required.");
        }
        validateClassification(command.accountType(), command.accountFunction(), command.normalBalance());
        if (command.details() != null)
        {
            AccountDefinitionRules.validate(cleanCode, cleanName, command.accountType(), command.accountFunction(),
                    command.normalBalance(), command.subtype(), command.details());
        }

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, companyCode);
                SupplementalIntegrityService.lock(em, company);
                ChartOfAccounts chart = command.chartId() == null ? resolveChart(em, company, ownership)
                        : requireEditableChart(em, company, command.chartId(), ownership);

                Account account;
                if (command.id() == null)
                {
                    account = new Account();
                    account.setChart(chart);
                    account.setPosting(true);
                }
                else
                {
                    account = requireAccount(em, command.id());
                    ownership.ensureOwnedBy(em, company, account, "Account");
                    if (account.getChart() == null
                            || account.getChart().getId() == null
                            || !account.getChart().getId().equals(chart.getId()))
                    {
                        throw new CompanyOwnershipException(
                                "Account ID " + command.id() + " does not belong to the selected Chart of Accounts.");
                    }
                    requireAcquisitionClassification(em, account, command.accountType(), command.subtype(), command.normalBalance());
                    validateConfiguredBankClassification(
                            em,
                            company,
                            account,
                            command.accountType(),
                            command.accountFunction(),
                            command.normalBalance());
                }

                requireUniqueCode(em, chart, command.id(), cleanCode);
                Account parent = resolveParent(em, chart, cleanCode, command.parentCode());
                if (parent != null && command.id() != null && command.id().equals(parent.getId()))
                {
                    throw new IllegalArgumentException("Parent account cannot be the account being edited.");
                }
                if (parent != null)
                {
                    ownership.requireOwnedBy(company, parent, "Parent account");
                }

                requireAcyclicParent(account, parent, chart);
                if (command.details() != null)
                {
                    requireDefinitionChanges(em, account, command, parent);
                }
                String before = account.getId() == null ? null : describe(account);

                applyAccount(
                        account,
                        cleanCode,
                        cleanName,
                        command.accountType(),
                        command.accountFunction(),
                        command.normalBalance(),
                        command.subtype(),
                        parent,
                        command.active());

                if (command.details() != null)
                {
                    var details = command.details();
                    account.setPosting(details.posting());
                    account.setEffectiveFrom(details.effectiveFrom());
                    account.setEffectiveTo(details.effectiveTo());
                    account.setOpeningBalance(details.openingBalance().setScale(2));
                    account.setDescription(details.description());
                }

                if (account.getId() == null)
                {
                    em.persist(account);
                }
                if (command.chartId() != null || command.details() != null)
                {
                    em.flush();
                    AuditEvent event = new AuditEvent();
                    event.setCompany(company);
                    event.setActor(actor);
                    event.setEntityType("ACCOUNT");
                    event.setEntityId(account.getId().toString());
                    event.setActionType(before == null ? "ACCOUNT_CREATED" : "ACCOUNT_UPDATED");
                    event.setSummary((before == null ? "Created account " : "Updated account ") + cleanCode);
                    event.setBeforeValue(before);
                    event.setAfterValue(describe(account));
                    em.persist(event);
                }
                em.getTransaction().commit();
                return account;
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive())
                {
                    em.getTransaction().rollback();
                }
                throw mapPersistenceError(ex, cleanCode);
            }
        }
    }

    private static ChartOfAccounts requireEditableChart(EntityManager em, Company company, long id, CompanyOwnershipService ownership)
    {
        if (!company.isActive()) throw new IllegalStateException("The company is inactive.");
        ChartOfAccounts chart = em.find(ChartOfAccounts.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (chart == null) throw new IllegalArgumentException("Chart of Accounts no longer exists.");
        ownership.requireOwnedBy(company, chart, "Chart of Accounts");
        if (chart.getStatus() == ChartStatus.RETIRED) throw new IllegalStateException("Retired charts are read-only; restore the chart to DRAFT before editing accounts.");
        return chart;
    }

    private static void requireAcyclicParent(Account account, Account parent, ChartOfAccounts chart)
    {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (Account ancestor = parent; ancestor != null; ancestor = ancestor.getParent())
        {
            if (!Objects.equals(ancestor.getChart().getId(), chart.getId())) throw new IllegalArgumentException("Parent hierarchy crosses charts.");
            if (Objects.equals(ancestor.getId(), account.getId()) || !seen.add(ancestor.getId()))
            {
                throw new IllegalArgumentException("Parent account would create a hierarchy cycle.");
            }
        }
    }

    private static void requireDefinitionChanges(EntityManager em, Account account, AccountCommand command, Account parent)
    {
        var details = command.details();
        boolean parentChanged = !Objects.equals(account.getParent() == null ? null : account.getParent().getId(), parent == null ? null : parent.getId());
        if (parent != null && parent.isPosting() && (account.getId() == null || parentChanged))
        {
            throw new IllegalArgumentException("Choose a non-posting group as the parent account.");
        }
        if (account.getId() != null && !account.isPosting() && details.posting()
                && em.createQuery("select count(a) from Account a where a.parent = :account", Long.class).setParameter("account", account).getSingleResult() > 0)
        {
            throw new IllegalArgumentException("A group with child accounts cannot become a posting account.");
        }
        boolean structural = account.getAccountType() != command.accountType() || account.getAccountFunction() != command.accountFunction()
                || account.getNormalBalance() != command.normalBalance() || account.getSubtype() != command.subtype()
                || account.isPosting() != details.posting() || parentChanged
                || account.getOpeningBalance().compareTo(details.openingBalance()) != 0;
        if (account.getId() != null && structural
                && em.createQuery("select count(s) from TxnSplit s where s.account = :account", Long.class).setParameter("account", account).getSingleResult() > 0)
        {
            throw new IllegalArgumentException("This account has transaction history. Preserve its classification, parent, posting state and opening balance; use a new account for a structural change.");
        }
    }

    private static String describe(Account account)
    {
        return account.getCode() + " | " + account.getName() + " | " + account.getAccountType() + " | "
                + account.getAccountFunction() + " | " + account.getNormalBalance() + " | " + account.getSubtype()
                + " | parent=" + (account.getParent() == null ? "" : account.getParent().getId())
                + " | posting=" + account.isPosting() + " | active=" + account.isActive()
                + " | from=" + account.getEffectiveFrom() + " | to=" + account.getEffectiveTo()
                + " | opening=" + account.getOpeningBalance() + " | " + account.getDescription();
    }

    /** Compatibility boundary for callers that intentionally address an account by code. */
    public Account upsert(String code,
                          String name,
                          AccountType accountType,
                          NormalBalance normalBalance,
                          AccountSubtype subtype,
                          String parentCode,
                          boolean active)
    {
        return upsert(code, name, accountType, null, normalBalance, subtype, parentCode, active);
    }

    /** Compatibility boundary for callers that intentionally address an account by code. */
    public Account upsert(String code,
                          String name,
                          AccountType accountType,
                          AccountFunction accountFunction,
                          NormalBalance normalBalance,
                          AccountSubtype subtype,
                          String parentCode,
                          boolean active)
    {
        ServiceAuthorization.require(authorizationGuard, ApplicationPermission.BOOKKEEPING_WRITE,
                companyCodeSupplier.get(), "save account");
        // Validate the public command contract before opening persistence. In particular,
        // do not allow an uninitialized/default-constructed service to mask invalid
        // arguments with a NullPointerException from the JPA dependency.
        String cleanCode = requireText(code, "Account code");
        String cleanName = requireText(name, "Account name");
        if (accountType == null)
        {
            throw new IllegalArgumentException("Account type is required.");
        }
        if (normalBalance == null)
        {
            throw new IllegalArgumentException("Normal balance is required.");
        }
        validateClassification(accountType, accountFunction, normalBalance);

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, companyCodeSupplier.get());
                SupplementalIntegrityService.lock(em, company);
                ChartOfAccounts chart = resolveChart(em, company, ownership);
                Account account = upsert(
                        em,
                        company,
                        chart,
                        cleanCode,
                        cleanName,
                        accountType,
                        accountFunction,
                        normalBalance,
                        subtype,
                        parentCode,
                        active);
                em.getTransaction().commit();
                return account;
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive())
                {
                    em.getTransaction().rollback();
                }
                throw mapPersistenceError(ex, cleanCode);
            }
        }
    }

    /**
     * Caller-owned transaction seam for governed batch operations. This method never commits or rolls back.
     */
    public Account upsert(EntityManager em,
                          Company company,
                          ChartOfAccounts chart,
                          String code,
                          String name,
                          AccountType accountType,
                          NormalBalance normalBalance,
                          AccountSubtype subtype,
                          String parentCode,
                          boolean active)
    {
        return upsert(em, company, chart, code, name, accountType, null, normalBalance,
                subtype, parentCode, active);
    }

    public Account upsert(EntityManager em,
                          Company company,
                          ChartOfAccounts chart,
                          String code,
                          String name,
                          AccountType accountType,
                          AccountFunction accountFunction,
                          NormalBalance normalBalance,
                          AccountSubtype subtype,
                          String parentCode,
                          boolean active)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(chart, "chart");
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Caller-owned account upsert requires an active transaction.");
        }

        String cleanCode = requireText(code, "Account code");
        String cleanName = requireText(name, "Account name");
        if (accountType == null)
        {
            throw new IllegalArgumentException("Account type is required.");
        }
        if (normalBalance == null)
        {
            throw new IllegalArgumentException("Normal balance is required.");
        }
        validateClassification(accountType, accountFunction, normalBalance);

        Company managedCompany = em.find(Company.class, company.getId());
        ChartOfAccounts managedChart = em.find(ChartOfAccounts.class, chart.getId());
        if (managedCompany == null)
        {
            throw new CompanyOwnershipException("Company no longer exists.");
        }
        if (managedChart == null)
        {
            throw new CompanyOwnershipException("Chart of Accounts no longer exists.");
        }
        CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
        ownership.requireOwnedBy(managedCompany, managedChart, "Chart of Accounts");
        SupplementalIntegrityService.lock(em, managedCompany);

        List<Account> existingMatches = em.createQuery(
                        "from Account a where a.chart = :chart and a.code = :code",
                        Account.class)
                .setParameter("chart", managedChart)
                .setParameter("code", cleanCode)
                .setMaxResults(2)
                .getResultList();
        if (existingMatches.size() > 1)
        {
            throw new IllegalStateException("Account code is ambiguous in active chart: " + cleanCode + ".");
        }

        Account account;
        if (existingMatches.isEmpty())
        {
            account = new Account();
            account.setChart(managedChart);
            account.setPosting(true);
        }
        else
        {
            account = existingMatches.get(0);
            requireAcquisitionClassification(em, account, accountType, subtype, normalBalance);
            validateConfiguredBankClassification(
                    em,
                    managedCompany,
                    account,
                    accountType,
                    accountFunction,
                    normalBalance);
        }

        Account parent = resolveParent(em, managedChart, cleanCode, parentCode);
        if (parent != null)
        {
            ownership.requireOwnedBy(managedCompany, parent, "Parent account");
        }

        applyAccount(
                account,
                cleanCode,
                cleanName,
                accountType,
                accountFunction,
                normalBalance,
                subtype,
                parent,
                active);

        if (account.getId() == null)
        {
            em.persist(account);
        }
        else
        {
            account = em.merge(account);
        }
        return account;
    }

    private static void applyAccount(
            Account account,
            String code,
            String name,
            AccountType accountType,
            AccountFunction accountFunction,
            NormalBalance normalBalance,
            AccountSubtype subtype,
            Account parent,
            boolean active)
    {
        account.setCode(code);
        account.setName(name);
        account.setAccountType(accountType);
        account.setAccountFunction(accountFunction);
        account.setNormalBalance(normalBalance);
        account.setSubtype(subtype);
        account.setParent(parent);
        account.setActive(active);
    }

    private static void requireUniqueCode(EntityManager em, ChartOfAccounts chart, Long accountId, String code)
    {
        String jpql = accountId == null
                ? "select a.id from Account a where a.chart = :chart and a.code = :code"
                : "select a.id from Account a where a.chart = :chart and a.code = :code and a.id <> :id";
        var query = em.createQuery(jpql, Long.class)
                .setParameter("chart", chart)
                .setParameter("code", code)
                .setMaxResults(1);
        if (accountId != null)
        {
            query.setParameter("id", accountId);
        }
        if (query.getResultStream().findAny().isPresent())
        {
            throw new IllegalArgumentException("Account code already exists: " + code + ".");
        }
    }

    private static Account requireAccount(EntityManager em, long accountId)
    {
        Account account = em.find(Account.class, accountId);
        if (account == null)
        {
            throw new IllegalArgumentException("Unknown account ID: " + accountId + ".");
        }
        return account;
    }

    private static void validateClassification(
            AccountType accountType,
            AccountFunction accountFunction,
            NormalBalance normalBalance)
    {
        if (accountFunction == AccountFunction.BANK
                && (accountType != AccountType.ASSET || normalBalance != NormalBalance.DEBIT))
        {
            throw new IllegalArgumentException(
                    "BANK function requires an ASSET account with a DEBIT normal balance.");
        }
    }

    private static void requireAcquisitionClassification(EntityManager em, Account account,
            AccountType type, AccountSubtype subtype, NormalBalance balance)
    {
        if ((type != AccountType.ASSET || subtype != AccountSubtype.FIXED_ASSET || balance != NormalBalance.DEBIT)
                && em.createQuery("select count(a) from FixedAsset a where a.acquisitionSplit.account = :account", Long.class)
                        .setParameter("account", account).getSingleResult() > 0)
        {
            throw new IllegalArgumentException("This account supports linked acquisitions; preserve ASSET/FIXED_ASSET with DEBIT normal balance.");
        }
    }

    private static void validateConfiguredBankClassification(
            EntityManager em,
            Company company,
            Account account,
            AccountType requestedType,
            AccountFunction requestedFunction,
            NormalBalance requestedBalance)
    {
        if ((requestedType != AccountType.EXPENSE || requestedBalance != account.getNormalBalance())
                && em.createQuery("select count(s) from TxnSplit s where s.account = :account and s.expenseFunction is not null", Long.class)
                        .setParameter("account", account).getSingleResult() > 0)
        {
            throw new IllegalArgumentException("This account has classified expense history. Preserve its EXPENSE type and normal balance; use a new account for a different classification.");
        }
        if (requestedType == AccountType.ASSET
                && requestedFunction == AccountFunction.BANK
                && requestedBalance == NormalBalance.DEBIT)
        {
            return;
        }

        long configuredCount = em.createQuery("""
                select count(cba)
                from CompanyBankAccount cba
                where cba.company = :company
                  and cba.account = :account
                """, Long.class)
                .setParameter("company", company)
                .setParameter("account", account)
                .getSingleResult();
        if (configuredCount > 0)
        {
            throw new IllegalArgumentException(
                    "This account is configured for banking. Remove or change its Banking configuration before changing its ASSET / BANK / DEBIT classification.");
        }
    }

    private static RuntimeException mapPersistenceError(RuntimeException ex, String code)
    {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("uq_account_code") || message.contains("unique") || message.contains("constraint"))
        {
            return new IllegalArgumentException("Account code already exists: " + code + ".", ex);
        }
        return ex;
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Account resolveParent(EntityManager em,
                                  ChartOfAccounts chart,
                                  String currentCode,
                                  String requestedParentCode)
    {
        String parentCode = normalizeOptional(requestedParentCode);
        if (parentCode == null)
        {
            return null;
        }
        if (parentCode.equals(currentCode))
        {
            throw new IllegalArgumentException("Parent account cannot be the same as account code.");
        }

        List<Account> parentMatches = em.createQuery(
                        "from Account a where a.chart = :chart and a.code = :code",
                        Account.class)
                .setParameter("chart", chart)
                .setParameter("code", parentCode)
                .setMaxResults(1)
                .getResultList();

        if (parentMatches.isEmpty())
        {
            throw new IllegalArgumentException("Parent account code does not exist in active chart: " + parentCode + ".");
        }

        return parentMatches.get(0);
    }

    private ChartOfAccounts resolveChart(
            EntityManager em,
            Company company,
            CompanyOwnershipService ownership)
    {
        Company managedCompany = em.find(Company.class, company.getId());
        if (managedCompany.getActiveChartOfAccounts() != null)
        {
            ChartOfAccounts active = managedCompany.getActiveChartOfAccounts();
            ownership.ensureOwnedBy(em, managedCompany, active, "Active Chart of Accounts");
            return active;
        }

        List<ChartOfAccounts> ownedActive = em.createQuery(
                        "from ChartOfAccounts c where c.company = :company and c.status = :status order by c.id",
                        ChartOfAccounts.class)
                .setParameter("company", managedCompany)
                .setParameter("status", ChartStatus.ACTIVE)
                .setMaxResults(2)
                .getResultList();
        if (ownedActive.size() == 1)
        {
            managedCompany.setActiveChartOfAccounts(ownedActive.get(0));
            return ownedActive.get(0);
        }
        if (ownedActive.size() > 1)
        {
            throw new CompanyOwnershipException("Company has multiple active Charts of Accounts.");
        }

        List<ChartOfAccounts> unowned = em.createQuery(
                        "from ChartOfAccounts c where c.company is null order by c.id",
                        ChartOfAccounts.class)
                .setMaxResults(2)
                .getResultList();
        if (unowned.size() == 1)
        {
            ChartOfAccounts chart = unowned.get(0);
            ownership.ensureOwnedBy(em, managedCompany, chart, "Chart of Accounts");
            managedCompany.setActiveChartOfAccounts(chart);
            return chart;
        }

        throw new IllegalStateException("No unambiguous Chart of Accounts exists for company "
                + managedCompany.getCode() + ".");
    }
}
