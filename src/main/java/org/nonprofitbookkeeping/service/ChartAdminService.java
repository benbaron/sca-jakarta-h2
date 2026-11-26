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
        Objects.requireNonNull(command, "Chart details are required");
        return saveChart(command.id(), command, null).chart();
    }

    public record ChartSaveResult(ChartDetails chart, Long accountId)
    {
    }

    /** Commits the selected chart and any pending account definition in one transaction. */
    public ChartSaveResult saveExistingChart(long chartId, ChartCommand details, AccountCommand account)
    {
        return saveChart(chartId, details, account);
    }

    private ChartSaveResult saveChart(Long chartId, ChartCommand command, AccountCommand account)
    {
        if (command != null && !Objects.equals(chartId, command.id()))
        {
            throw new IllegalArgumentException("The chart details belong to a different chart.");
        }
        String target = companyCode.get();
        String actor = ServiceAuthorization.actor(authorization, ApplicationPermission.BOOKKEEPING_WRITE, target, "save chart", "SYSTEM");
        String name = command == null ? null : AccountDefinitionRules.bounded(command.name(), "Chart name", 200);
        String version = command == null ? null : AccountDefinitionRules.bounded(command.version(), "Chart version", 50);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = requireTarget(em, target);
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);
                ChartOfAccounts chart;
                String before = null;
                if (chartId == null)
                {
                    if (command.retired()) throw new IllegalArgumentException("New charts start as DRAFT.");
                    unique(em, company, name, version, null);
                    chart = new ChartOfAccounts();
                    chart.setCompany(company);
                    chart.setStatus(ChartStatus.DRAFT);
                }
                else
                {
                    chart = requireChart(em, chartId);
                    em.lock(chart, LockModeType.PESSIMISTIC_WRITE);
                    new CompanyOwnershipService(jpa).requireOwnedBy(company, chart, "Chart of Accounts");
                    before = describe(chart);
                    if (command != null)
                    {
                        ChartDetails saved = snapshot(em, chart).details();
                        requireRevision(command.expectedRevision(), saved.revision());
                        if (command.retired() && saved.currentForCompany())
                        {
                            throw new IllegalStateException("The current chart cannot be retired. Make another chart active first.");
                        }
                        if (!chart.getName().equals(name) || !chart.getVersion().equals(version))
                        {
                            unique(em, company, name, version, chart.getId());
                        }
                    }
                }
                Long savedAccountId = account == null ? null : AccountAdminService.saveInChart(
                        em, new CompanyOwnershipService(jpa), company, chart, account, actor).getId();
                if (command != null)
                {
                    chart.setName(name);
                    chart.setVersion(version);
                    if (command.retired()) chart.setStatus(ChartStatus.RETIRED);
                    else if (chart.getStatus() == ChartStatus.RETIRED) chart.setStatus(ChartStatus.DRAFT);
                }
                chart.touchUpdatedAt();
                if (chart.getId() == null) em.persist(chart);
                em.flush();
                audit(em, company, actor, before == null ? "CHART_CREATED" : "CHART_UPDATED", chart, before, describe(chart));
                checkpoint.accept("AUDIT_WRITTEN");
                em.flush();
                em.refresh(chart);
                ChartDetails result = snapshot(em, chart).details();
                em.getTransaction().commit();
                return new ChartSaveResult(result, savedAccountId);
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
    /** Read-only eligibility; deletion repeats all checks under the company/chart locks. */

    public AccountDeletionPreview previewAccountDeletion(long chartId, long accountId)
    {
        String target = companyCode.get();
        ServiceAuthorization.require(authorization, ApplicationPermission.BOOKKEEPING_WRITE, target, "inspect draft account deletion");
        try (EntityManager em = jpa.em())
        {
            Company company = requireTarget(em, target);
            ChartOfAccounts chart = requireChart(em, chartId);
            new CompanyOwnershipService(jpa).requireOwnedBy(company, chart, "Chart of Accounts");
            return deletion(em, chart, accountId).preview();
        }
    }
    /** Deletes one unused draft definition, adjuncts and account receipts atomically; never cascades history/children. */

    public void deleteAccount(long chartId, long accountId, String expectedRevision)
    {
        String target = companyCode.get();
        String actor = ServiceAuthorization.actor(authorization, ApplicationPermission.BOOKKEEPING_WRITE, target, "delete draft account", "SYSTEM");
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = requireTarget(em, target);
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);
                ChartOfAccounts chart = requireChart(em, chartId);
                new CompanyOwnershipService(jpa).requireOwnedBy(company, chart, "Chart of Accounts");
                em.lock(chart, LockModeType.PESSIMISTIC_WRITE);
                Deletion state = deletion(em, chart, accountId);
                AccountDeletionPreview preview = state.preview();
                if (!preview.canDelete())
                {
                    throw new IllegalStateException("Cannot delete account " + preview.accountCode() + ": " + String.join("; ", preview.blockers()));
                }
                if (expectedRevision == null || !expectedRevision.equals(preview.revision()))
                {
                    throw new IllegalStateException("Draft account, chart or import mappings changed. Refresh and review Delete Account again.");
                }
                AuditEvent event = new AuditEvent();
                event.setCompany(company);
                event.setActor(actor);
                event.setActionType("ACCOUNT_DELETED");
                event.setEntityType("ACCOUNT");
                event.setEntityId(Long.toString(accountId));
                event.setSummary("Deleted unused draft account " + preview.accountCode() + " from chart " + chartId);
                event.setBeforeValue(state.facts());
                em.persist(event);
                em.createQuery("delete from AccountAlias a where a.account.id = :id").setParameter("id", accountId).executeUpdate();
                em.createQuery("delete from AccountReportSection a where a.account.id = :id").setParameter("id", accountId).executeUpdate();
                em.createQuery("delete from AccountScheduleRequirement a where a.account.id = :id").setParameter("id", accountId).executeUpdate();
                state.identities().forEach(em::remove);
                em.remove(state.account());
                chart.touchUpdatedAt();
                em.flush();
                checkpoint.accept("ACCOUNT_DELETION_WRITTEN");
                em.getTransaction().commit();
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw ex;
            }
        }
    }

    private Deletion deletion(EntityManager em, ChartOfAccounts chart, long accountId)
    {
        Account account = em.find(Account.class, accountId);
        if (account == null || !Objects.equals(account.getChart().getId(), chart.getId()))
        {
            throw new IllegalArgumentException("The account does not belong to the viewed chart or no longer exists.");
        }
        Snapshot snapshot = snapshot(em, chart);
        List<String> blockers = new ArrayList<>();
        if (chart.getStatus() != ChartStatus.DRAFT || snapshot.details().currentForCompany())
        {
            blockers.add("Only unused accounts in a non-current DRAFT chart can be deleted. Deactivate accounts in other charts.");
        }
        reference(em, blockers, accountId, "Account", "a.parent.id = :id", "child accounts (delete or reparent children first)");
        reference(em, blockers, accountId, "TxnSplit", "a.account.id = :id", "journal lines");
        reference(em, blockers, accountId, "Txn", "a.bankAccount.id = :id", "journal bank references");
        reference(em, blockers, accountId, "CompanyBankAccount", "a.account.id = :id", "configured bank accounts");
        reference(em, blockers, accountId, "FixedAsset", "a.assetAccount.id = :id or a.accumulatedDepreciationAccount.id = :id or a.depreciationExpenseAccount.id = :id", "fixed assets");
        reference(em, blockers, accountId, "FixedAssetLifecycleEvent", "a.proceedsAccount.id = :id or a.gainAccount.id = :id or a.lossAccount.id = :id", "asset lifecycle records");
        reference(em, blockers, accountId, "InventoryItem", "a.inventoryAccount.id = :id", "inventory items");
        List<InterchangeIdentity> identities = em.createQuery("from InterchangeIdentity i where upper(i.entityType) = 'ACCOUNT' and i.localEntityId = :id order by i.id", InterchangeIdentity.class)
        .setParameter("id", Long.toString(accountId)).getResultList();
        if (identities.stream().anyMatch(i -> !Objects.equals(i.getCompany().getId(), chart.getCompany().getId())))
        {
            blockers.add("Account import mappings owned by another company require ownership repair.");
        }
        long aliases = snapshot.aliases().stream().filter(a -> a.getAccount().getId() == accountId).count();
        long reports = snapshot.reports().stream().filter(a -> a.getAccount().getId() == accountId).count();
        long schedules = snapshot.schedules().stream().filter(a -> a.getAccount().getId() == accountId).count();
        StringBuilder facts = new StringBuilder();
        fields(facts, snapshot.details().revision(), "ACCOUNT", accountId, account.getCode(), account.getName());
        blockers.forEach(b -> fields(facts, b));
        identities.forEach(i -> fields(facts, "IMPORT", i.getId(), i.getCompany().getId(), i.getFormatCode(), i.getSourceSystem(),
            i.getExternalId(), i.getNormalizedContentHash(), i.getUpdatedAt()));
        // The complete definition fingerprint plus receipt facts binds the confirmation to saved content.
        String revision = hash(facts.toString());
        fields(facts, "chart", describe(chart), "definition", account.getAccountType(), account.getAccountFunction(), account.getSubtype(),
            account.getNormalBalance(), account.getParent() == null ? null : account.getParent().getId(), account.isPosting(), account.isActive(),
            account.getEffectiveFrom(), account.getEffectiveTo(), account.getOpeningBalance(), account.getDescription(),
            "removed aliases/report mappings/schedule requirements", aliases, reports, schedules);
        snapshot.aliases().stream().filter(a -> a.getAccount().getId() == accountId).forEach(a -> fields(facts, "ALIAS", a.getId(), a.getAliasText(), a.getSource(), a.isActive()));
        snapshot.reports().stream().filter(a -> a.getAccount().getId() == accountId).forEach(a -> fields(facts, "REPORT", a.getId(), a.getReportSection().getId(), a.getSortOrder(), a.getSignPolicy()));
        snapshot.schedules().stream().filter(a -> a.getAccount().getId() == accountId).forEach(a -> fields(facts, "SCHEDULE", a.getId(), a.getScheduleKind().getId(), a.isRequired(), a.getNotes()));
        return new Deletion(account, identities, new AccountDeletionPreview(chart.getId(), accountId, chart.getName(), account.getCode(), account.getName(),
            revision, blockers, aliases, reports, schedules, identities.size()), facts.toString());
    }

    private static void reference(EntityManager em, List<String> blockers, long id, String entity, String condition, String description)
    {
        long count = em.createQuery("select count(a) from " + entity + " a where " + condition, Long.class).setParameter("id", id).getSingleResult();
        if (count != 0) blockers.add(count + " " + description);
    }

    private record Deletion(Account account, List<InterchangeIdentity> identities, AccountDeletionPreview preview, String facts)
    {
    }
    /** Read-only preview for an entire empty or unused partial draft, not an account delete. */
    public ChartDeletionPreview previewChartDeletion(long chartId)
    {
        String target = companyCode.get();
        ServiceAuthorization.require(authorization, ApplicationPermission.BOOKKEEPING_WRITE, target, "inspect draft chart deletion");
        try (EntityManager em = jpa.em())
        {
            Company company = requireTarget(em, target);
            ChartOfAccounts chart = requireChart(em, chartId);
            new CompanyOwnershipService(jpa).requireOwnedBy(company, chart, "Chart of Accounts");
            return chartDeletion(em, chart).preview();
        }
    }
    /** Removes only unused non-current draft definitions and their mappings in one audited transaction. */

    public void deleteChart(long chartId, String expectedRevision)
    {
        String target = companyCode.get();
        String actor = ServiceAuthorization.actor(authorization, ApplicationPermission.BOOKKEEPING_WRITE, target, "delete draft chart", "SYSTEM");
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = requireTarget(em, target);
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);
                ChartOfAccounts chart = requireChart(em, chartId);
                new CompanyOwnershipService(jpa).requireOwnedBy(company, chart, "Chart of Accounts");
                em.lock(chart, LockModeType.PESSIMISTIC_WRITE);
                ChartDeletion state = chartDeletion(em, chart);
                if (!state.preview().canDelete())
                {
                    throw new IllegalStateException("Cannot delete chart '" + chart.getName() + "': " + String.join("; ",state.preview().blockers()));
                }
                if (expectedRevision == null || !expectedRevision.equals(state.preview().revision()))
                {
                    throw new IllegalStateException("Chart definitions or mappings changed. Review Delete Draft Chart again.");
                }
                audit(em,company,actor,"CHART_DELETED",chart,state.facts(),null);
                em.createQuery("delete from AccountAlias a where a.account.chart = :chart").setParameter("chart",chart).executeUpdate();
                em.createQuery("delete from AccountReportSection a where a.account.chart = :chart").setParameter("chart",chart).executeUpdate();
                em.createQuery("delete from AccountScheduleRequirement a where a.account.chart = :chart").setParameter("chart",chart).executeUpdate();
                state.identities().forEach(em::remove);
                // Break only links inside the unused draft. External children are blockers above.
                em.createQuery("update Account a set a.parent = null where a.chart = :chart").setParameter("chart",chart).executeUpdate();
                em.createQuery("delete from Account a where a.chart = :chart").setParameter("chart",chart).executeUpdate();
                em.remove(chart);
                em.flush();
                checkpoint.accept("CHART_DELETION_WRITTEN");
                em.getTransaction().commit();
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw ex;
            }
        }
    }

    private ChartDeletion chartDeletion(EntityManager em, ChartOfAccounts chart)
    {
        Snapshot source = snapshot(em,chart);
        List<String> blockers = new ArrayList<>();
        if (chart.getStatus() != ChartStatus.DRAFT) blockers.add("Only a DRAFT chart can be deleted. Retain ACTIVE/RETIRED charts.");
        chartReference(em,blockers,chart,"Company","e.activeChartOfAccounts = :chart","current company selections (select another current chart first)");
        chartReference(em,blockers,chart,"Account","e.parent in (select a from Account a where a.chart = :chart) and e.chart <> :chart","child accounts in another chart");
        chartReference(em,blockers,chart,"TxnSplit",chartAccountCondition("account"),"journal lines");
        chartReference(em,blockers,chart,"Txn",chartAccountCondition("bankAccount"),"journal bank references");
        chartReference(em,blockers,chart,"CompanyBankAccount",chartAccountCondition("account"),"configured bank accounts");
        chartReference(em,blockers,chart,"FixedAsset",chartAccountCondition("assetAccount","accumulatedDepreciationAccount","depreciationExpenseAccount"),"fixed assets");
        chartReference(em,blockers,chart,"FixedAssetLifecycleEvent",chartAccountCondition("proceedsAccount","gainAccount","lossAccount"),"asset lifecycle records");
        chartReference(em,blockers,chart,"InventoryItem",chartAccountCondition("inventoryAccount"),"inventory items");
        List<InterchangeIdentity> identities = em.createQuery("from InterchangeIdentity i where "
            + "(upper(i.entityType) in ('CHART','CHART_OF_ACCOUNTS') and i.localEntityId = :id) or "
            + "(upper(i.entityType) = 'ACCOUNT' and i.localEntityId in (select cast(a.id as string) from Account a where a.chart = :chart)) order by i.id",InterchangeIdentity.class)
        .setParameter("id",chart.getId().toString()).setParameter("chart",chart).getResultList();
        if (identities.stream().anyMatch(i -> !Objects.equals(i.getCompany().getId(),chart.getCompany().getId())))
            blockers.add("Chart/account import mappings owned by another company require ownership repair.");
        StringBuilder state = new StringBuilder();
        fields(state,source.details().revision());
        blockers.forEach(b -> fields(state,b));
        identities.forEach(i -> fields(state,"IMPORT",i.getId(),i.getCompany().getId(),i.getEntityType(),i.getLocalEntityId(),
            i.getFormatCode(),i.getSourceSystem(),i.getExternalId(),i.getNormalizedContentHash(),i.getUpdatedAt()));
        ChartDeletionPreview preview = new ChartDeletionPreview(chart.getId(),chart.getCompany().getCode(),chart.getName(),chart.getVersion(),
            source.accounts().size(),source.aliases().size(),source.reports().size(),source.schedules().size(),identities.size(),hash(state.toString()),blockers);
        // Audit definitions and mapping facts, while keeping local connection paths only in the digest.
        StringBuilder facts = new StringBuilder();
        fields(facts,"owner",chart.getCompany().getId(),chart.getCompany().getCode(),"definitions",source.definitionFacts(),"effects",preview.effects());
        identities.forEach(i -> fields(facts,"IMPORT",i.getId(),i.getEntityType(),i.getLocalEntityId(),i.getFormatCode(),i.getSourceSystem(),i.getExternalId(),i.getNormalizedContentHash()));
        return new ChartDeletion(identities,preview,facts.toString());
    }

    private static String chartAccountCondition(String... properties)
    {
        return Arrays.stream(properties).map(p -> "e."+p+" in (select a from Account a where a.chart = :chart)").collect(java.util.stream.Collectors.joining(" or "));
    }

    private static void chartReference(EntityManager em, List<String> blockers, ChartOfAccounts chart, String entity, String condition, String description)
    {
        long count = em.createQuery("select count(e) from "+entity+" e where "+condition,Long.class).setParameter("chart",chart).getSingleResult();
        if (count != 0) blockers.add(count+" "+description);
    }

    private record ChartDeletion(List<InterchangeIdentity> identities, ChartDeletionPreview preview, String facts)
    {
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
        StringBuilder scope = new StringBuilder();
        fields(scope, em.getEntityManagerFactory().getProperties().get("jakarta.persistence.jdbc.url"), company.getId(), company.getCode(),
            company.getDisplayName(), company.getDefaultCurrency(), company.isActive(), company.getActiveChartOfAccounts() == null ? null : company.getActiveChartOfAccounts().getId());
        StringBuilder state = new StringBuilder();
        fields(state,
            chart.getId(), chart.getPortableId(), chart.getName(), chart.getVersion(), chart.getStatus(), chart.getUpdatedAt());
        accounts.forEach(a -> fields(state, "ACCOUNT", a.getId(), a.getCode(), a.getName(), a.getAccountType(), a.getAccountFunction(), a.getSubtype(),
            a.getNormalBalance(), a.getParent() == null ? null : a.getParent().getId(), a.isPosting(), a.isActive(), a.getEffectiveFrom(), a.getEffectiveTo(),
            a.getOpeningBalance().stripTrailingZeros().toPlainString(), a.getDescription()));
        aliases.forEach(a -> fields(state, "ALIAS", a.getId(), a.getAccount().getId(), a.getAliasText(), a.getSource(), a.isActive()));
        reports.forEach(a -> fields(state, "REPORT", a.getId(), a.getAccount().getId(), a.getReportSection().getId(), a.getSortOrder(), a.getSignPolicy()));
        schedules.forEach(a -> fields(state, "SCHEDULE", a.getId(), a.getAccount().getId(), a.getScheduleKind().getId(), a.isRequired(), a.getNotes()));
        boolean current = company.getActiveChartOfAccounts() != null && Objects.equals(company.getActiveChartOfAccounts().getId(),chart.getId());
        ChartDetails details = new ChartDetails(chart.getId(), company.getCode(), company.getDisplayName(), chart.getName(), chart.getVersion(), chart.getStatus(),
            current, accounts.size(), accounts.stream().filter(a -> a.getOpeningBalance().signum() != 0).count(), hash(scope.toString()+state));
        return new Snapshot(details, accounts, aliases, reports, schedules, state.toString());
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
        List<AccountReportSection> reports, List<AccountScheduleRequirement> schedules, String definitionFacts)
    {
    }
}
