package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Native chart definition commands. All chart/account/adjunct/audit writes share one transaction. */
public final class ChartAdminService
{
    private final Jpa jpa;
    private final Supplier<String> companyCode;
    private final AuthorizationGuard authorization;
    private final Consumer<String> checkpoint;

    public ChartAdminService(Jpa jpa, Supplier<String> companyCode, AuthorizationGuard authorization)
    {
        this(jpa, companyCode, authorization, ignored ->
        {
        });
    }
    ChartAdminService(Jpa jpa, Supplier<String> companyCode, AuthorizationGuard authorization, Consumer<String> checkpoint)
    {
        this.jpa = Objects.requireNonNull(jpa);
        this.companyCode = Objects.requireNonNull(companyCode);
        this.authorization = authorization;
        this.checkpoint = Objects.requireNonNull(checkpoint);
    }
    /** Authorized chart-template metadata only; no source financial history is exposed. */

    public List<ChartSource> listCloneSources()
    {
        String target = companyCode.get();
        ServiceAuthorization.require(authorization, ApplicationPermission.BOOKKEEPING_WRITE, target, "list chart clone sources");
        try (EntityManager em = jpa.em())
        {
            requireTarget(em, target);
            return em.createQuery("select c from ChartOfAccounts c join fetch c.company "
                + "order by c.company.code, c.name, c.version, c.id", ChartOfAccounts.class)
            .getResultList().stream().map(c -> new ChartSource(c.getId(), c.getCompany().getCode(),
                c.getCompany().getDisplayName(), c.getName(), c.getVersion(), c.getStatus())).toList();
        }
    }

    public ChartDetails load(long chartId)
    {
        return inspect(chartId, false);
    }

    public ChartDetails inspectCloneSource(long chartId)
    {
        return inspect(chartId, true);
    }

    private ChartDetails inspect(long chartId, boolean cloneSource)
    {
        String target = companyCode.get();
        ServiceAuthorization.require(authorization, ApplicationPermission.BOOKKEEPING_WRITE, target, "inspect chart definitions");
        try (EntityManager em = jpa.em())
        {
            Company company = requireTarget(em, target);
            ChartOfAccounts chart = requireChart(em, chartId);
            if (!cloneSource) new CompanyOwnershipService(jpa).requireOwnedBy(company, chart, "Chart of Accounts");
            return snapshot(em, chart).details();
        }
    }

    public ChartDetails save(ChartCommand command)
    {
        String target = companyCode.get();
        String actor = ServiceAuthorization.actor(authorization, ApplicationPermission.BOOKKEEPING_WRITE, target, "save chart", "SYSTEM");
        Objects.requireNonNull(command, "Chart details are required");
        String name = AccountDefinitionRules.bounded(command.name(), "Chart name", 200);
        String version = AccountDefinitionRules.bounded(command.version(), "Chart version", 50);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = requireTarget(em, target);
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);
                ChartOfAccounts chart;
                String before = null;
                if (command.id() == null)
                {
                    if (command.retired()) throw new IllegalArgumentException("New charts start as DRAFT.");
                    unique(em, company, name, version, null);
                    chart = new ChartOfAccounts();
                    chart.setCompany(company);
                    chart.setStatus(ChartStatus.DRAFT);
                }
                else
                {
                    chart = requireChart(em, command.id());
                    em.lock(chart, LockModeType.PESSIMISTIC_WRITE);
                    new CompanyOwnershipService(jpa).requireOwnedBy(company, chart, "Chart of Accounts");
                    Snapshot saved = snapshot(em, chart);
                    requireRevision(command.expectedRevision(), saved.details().revision());
                    before = describe(chart);
                    if (command.retired() && saved.details().currentForCompany())
                    {
                        throw new IllegalStateException("The current chart cannot be retired. Make another chart active first.");
                    }
                    if (!chart.getName().equals(name) || !chart.getVersion().equals(version)) unique(em, company, name, version, chart.getId());
                    if (command.retired()) chart.setStatus(ChartStatus.RETIRED);
                    else if (chart.getStatus() == ChartStatus.RETIRED) chart.setStatus(ChartStatus.DRAFT);
                }
                chart.setName(name);
                chart.setVersion(version);
                chart.touchUpdatedAt();
                if (chart.getId() == null) em.persist(chart);
                em.flush();
                audit(em, company, actor, before == null ? "CHART_CREATED" : "CHART_UPDATED", chart, before, describe(chart));
                checkpoint.accept("AUDIT_WRITTEN");
                em.flush();
                em.refresh(chart);
                ChartDetails result = snapshot(em, chart).details();
                em.getTransaction().commit();
                return result;
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw ex;
            }
        }
    }
    /** Source may belong to another company; only fresh definitions are written to the target. */

    public ChartDetails cloneChart(ChartCloneCommand command)
    {
        String target = companyCode.get();
        String actor = ServiceAuthorization.actor(authorization, ApplicationPermission.BOOKKEEPING_WRITE, target, "clone chart", "SYSTEM");
        Objects.requireNonNull(command, "Clone details are required");
        String name = AccountDefinitionRules.bounded(command.name(), "Chart name", 200);
        String version = AccountDefinitionRules.bounded(command.version(), "Chart version", 50);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = requireTarget(em, target);
                ChartOfAccounts source = requireChart(em, command.sourceChartId());
                // The same company locks guard native/import account writes. Stable order avoids cross-clone deadlocks.
                var owners = new TreeMap<Long,Company>();
                owners.put(company.getId(), company);
                owners.put(source.getCompany().getId(), source.getCompany());
                owners.values().forEach(owner -> em.lock(owner, LockModeType.PESSIMISTIC_WRITE));
                em.lock(source, LockModeType.PESSIMISTIC_WRITE);
                Snapshot original = snapshot(em, source);
                requireRevision(command.sourceRevision(), original.details().revision());
                unique(em, company, name, version, null);
                validateGraph(original.accounts());
                ChartOfAccounts chart = new ChartOfAccounts();
                chart.setCompany(company);
                chart.setName(name);
                chart.setVersion(version);
                chart.setStatus(ChartStatus.DRAFT);
                em.persist(chart);
                Map<Long,Account> copies = new HashMap<>();
                for (Account old : original.accounts())
                {
                    Account copy = new Account();
                    copy.setChart(chart);
                    copy.setCode(old.getCode());
                    copy.setName(old.getName());
                    copy.setAccountType(old.getAccountType());
                    copy.setAccountFunction(old.getAccountFunction());
                    copy.setSubtype(old.getSubtype());
                    copy.setNormalBalance(old.getNormalBalance());
                    copy.setPosting(old.isPosting());
                    copy.setActive(old.isActive());
                    copy.setEffectiveFrom(old.getEffectiveFrom());
                    copy.setEffectiveTo(old.getEffectiveTo());
                    copy.setDescription(old.getDescription());
                    copy.setOpeningBalance(BigDecimal.ZERO.setScale(2));
                    em.persist(copy);
                    copies.put(old.getId(), copy);
                }
                for (Account old : original.accounts())
                {
                    if (old.getParent() != null) copies.get(old.getId()).setParent(copies.get(old.getParent().getId()));
                }
                checkpoint.accept("ACCOUNTS_WRITTEN");
                for (AccountAlias old : original.aliases())
                {
                    AccountAlias copy = new AccountAlias();
                    copy.setAccount(copies.get(old.getAccount().getId()));
                    copy.setAliasText(old.getAliasText());
                    copy.setSource(old.getSource());
                    copy.setActive(old.isActive());
                    em.persist(copy);
                }
                for (AccountReportSection old : original.reports())
                {
                    AccountReportSection copy = new AccountReportSection();
                    copy.setAccount(copies.get(old.getAccount().getId()));
                    copy.setReportSection(old.getReportSection());
                    copy.setSortOrder(old.getSortOrder());
                    copy.setSignPolicy(old.getSignPolicy());
                    em.persist(copy);
                }
                for (AccountScheduleRequirement old : original.schedules())
                {
                    AccountScheduleRequirement copy = new AccountScheduleRequirement();
                    copy.setAccount(copies.get(old.getAccount().getId()));
                    copy.setScheduleKind(old.getScheduleKind());
                    copy.setRequired(old.isRequired());
                    copy.setNotes(old.getNotes());
                    em.persist(copy);
                }
                em.flush();
                audit(em, company, actor, "CHART_CLONED", chart, describe(source) + " | source company=" + source.getCompany().getCode()
                    + " | revision=" + command.sourceRevision(), describe(chart) + " | accounts=" + copies.size()
                    + " | opening balances reset=" + original.details().nonzeroOpeningCount());
                checkpoint.accept("AUDIT_WRITTEN");
                em.flush();
                em.refresh(chart);
                ChartDetails result = snapshot(em, chart).details();
                em.getTransaction().commit();
                return result;
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw ex;
            }
        }
    }

    private Company requireTarget(EntityManager em, String code)
    {
        Company company = new CompanyOwnershipService(jpa).requireCompany(em, code);
        if (!company.isActive()) throw new IllegalStateException("The target company is inactive.");
        return company;
    }

    private static ChartOfAccounts requireChart(EntityManager em, long id)
    {
        ChartOfAccounts chart = em.find(ChartOfAccounts.class, id);
        if (chart == null || chart.getCompany() == null) throw new IllegalArgumentException("Chart is missing or has no company owner.");
        return chart;
    }

    private static void unique(EntityManager em, Company company, String name, String version, Long id)
    {
        var matches = em.createQuery("select c.id from ChartOfAccounts c where c.company = :company "
            + "and upper(trim(c.name)) = :name and upper(trim(c.version)) = :version", Long.class)
        .setParameter("company", company).setParameter("name", name.toUpperCase(Locale.ROOT))
        .setParameter("version", version.toUpperCase(Locale.ROOT)).getResultList();
        if (matches.stream().anyMatch(match -> !Objects.equals(id, match)))
        {
            throw new IllegalArgumentException("A chart with that name and version already exists in this company.");
        }
    }

    private static void requireRevision(String expected, String actual)
    {
        if (expected == null || !expected.equals(actual)) throw new IllegalStateException("Chart definitions or database changed. Refresh and review before saving.");
    }

    private static void validateGraph(List<Account> accounts)
    {
        Map<Long,Account> byId = new HashMap<>();
        accounts.forEach(a -> byId.put(a.getId(),a));
        Set<Long> complete = new HashSet<>();
        for (Account account : accounts)
        {
            try
            {
                AccountDefinitionRules.validate(account.getCode(), account.getName(), account.getAccountType(), account.getAccountFunction(),
                    account.getNormalBalance(), account.getSubtype(), new AccountDefinitionDetails(account.isPosting(), account.getEffectiveFrom(),
                    account.getEffectiveTo(), BigDecimal.ZERO, account.getDescription()));
            }
            catch (IllegalArgumentException ex)
            {
                throw new IllegalArgumentException("Source account " + account.getCode() + " — " + account.getName() + ": " + ex.getMessage(), ex);
            }
            Set<Long> path = new HashSet<>();
            Account cursor = account;
            while (cursor != null && !complete.contains(cursor.getId()))
            {
                if (!byId.containsKey(cursor.getId())) throw new IllegalStateException("The source parent hierarchy crosses charts.");
                if (!path.add(cursor.getId())) throw new IllegalStateException("The source parent hierarchy has a cycle.");
                cursor = cursor.getParent();
            }
            complete.addAll(path);
        }
    }

    private static void audit(EntityManager em, Company company, String actor, String action, ChartOfAccounts chart, String before, String after)
    {
        AuditEvent event = new AuditEvent();
        event.setCompany(company);
        event.setActor(actor);
        event.setActionType(action);
        event.setEntityType("CHART_OF_ACCOUNTS");
        event.setEntityId(chart.getId().toString());
        event.setSummary(action.replace('_',' ') + ": " + chart.getName());
        event.setBeforeValue(before);
        event.setAfterValue(after);
        em.persist(event);
    }

    private static String describe(ChartOfAccounts chart)
    {
        return "Chart " + chart.getId() + " | " + chart.getPortableId() + " | " + chart.getName() + " | " + chart.getVersion() + " | " + chart.getStatus();
    }

    private Snapshot snapshot(EntityManager em, ChartOfAccounts chart)
    {
        Company company = chart.getCompany();
        List<Account> accounts = em.createQuery("select a from Account a left join fetch a.parent where a.chart = :chart order by a.id", Account.class)
        .setParameter("chart", chart).getResultList();
        List<AccountAlias> aliases = em.createQuery("from AccountAlias a where a.account.chart = :chart order by a.id", AccountAlias.class).setParameter("chart",chart).getResultList();
        List<AccountReportSection> reports = em.createQuery("from AccountReportSection a where a.account.chart = :chart order by a.id", AccountReportSection.class).setParameter("chart",chart).getResultList();
        List<AccountScheduleRequirement> schedules = em.createQuery("from AccountScheduleRequirement a where a.account.chart = :chart order by a.id", AccountScheduleRequirement.class).setParameter("chart",chart).getResultList();
        StringBuilder state = new StringBuilder();
        fields(state, em.getEntityManagerFactory().getProperties().get("jakarta.persistence.jdbc.url"), company.getId(), company.getCode(),
            company.getDisplayName(), company.getDefaultCurrency(), company.isActive(), company.getActiveChartOfAccounts() == null ? null : company.getActiveChartOfAccounts().getId(),
            chart.getId(), chart.getPortableId(), chart.getName(), chart.getVersion(), chart.getStatus(), chart.getUpdatedAt());
        accounts.forEach(a -> fields(state, "ACCOUNT", a.getId(), a.getCode(), a.getName(), a.getAccountType(), a.getAccountFunction(), a.getSubtype(),
            a.getNormalBalance(), a.getParent() == null ? null : a.getParent().getId(), a.isPosting(), a.isActive(), a.getEffectiveFrom(), a.getEffectiveTo(),
            a.getOpeningBalance().stripTrailingZeros().toPlainString(), a.getDescription()));
        aliases.forEach(a -> fields(state, "ALIAS", a.getId(), a.getAccount().getId(), a.getAliasText(), a.getSource(), a.isActive()));
        reports.forEach(a -> fields(state, "REPORT", a.getId(), a.getAccount().getId(), a.getReportSection().getId(), a.getSortOrder(), a.getSignPolicy()));
        schedules.forEach(a -> fields(state, "SCHEDULE", a.getId(), a.getAccount().getId(), a.getScheduleKind().getId(), a.isRequired(), a.getNotes()));
        boolean current = company.getActiveChartOfAccounts() != null && Objects.equals(company.getActiveChartOfAccounts().getId(),chart.getId());
        ChartDetails details = new ChartDetails(chart.getId(), company.getCode(), company.getDisplayName(), chart.getName(), chart.getVersion(), chart.getStatus(),
            current, accounts.size(), accounts.stream().filter(a -> a.getOpeningBalance().signum() != 0).count(), hash(state.toString()));
        return new Snapshot(details, accounts, aliases, reports, schedules);
    }

    private static void fields(StringBuilder state, Object... values)
    {
        for (Object value : values)
        {
            String text = value == null ? "" : value.toString();
            state.append(text.length()).append(':').append(text).append(';');
        }
    }

    private static String hash(String value)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable",ex);
        }
    }

    private record Snapshot(ChartDetails details, List<Account> accounts, List<AccountAlias> aliases,
        List<AccountReportSection> reports, List<AccountScheduleRequirement> schedules)
    {
    }
}
