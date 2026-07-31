package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.interchange.InterchangeFormat;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;
import org.nonprofitbookkeeping.interchange.InterchangeMessageSeverity;
import org.nonprofitbookkeeping.interchange.InterchangeOperationCounts;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.CompanyOwnershipService;
import org.nonprofitbookkeeping.service.InterchangeIdentityService;
import org.nonprofitbookkeeping.service.TransactionCommand;
import org.nonprofitbookkeeping.service.TransactionEntryService;
import org.nonprofitbookkeeping.service.TransactionLineCommand;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * First governed SCLX commit boundary.
 *
 * <p>This slice imports the organization profile, active chart/accounts, funds,
 * and balanced canonical transactions into an empty selected company. Budgets,
 * parties, activities, supplemental details, banking, assets, inventory,
 * period-close facts, and imported audit history remain blocked until their
 * canonical section writers are added. No production UI commit action is exposed
 * while that section coverage remains incomplete.</p>
 */
public final class SclxImportCommitService
{
    private static final Set<String> CORE_ENTITY_TYPES = Set.of(
            "ORGANIZATION", "ACCOUNT", "FUND", "TRANSACTION", "TRANSACTION_LINE");

    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;
    private final SclxImportPreviewService previewService;
    private final SclxDocumentParser parser;
    private final CompanyOwnershipService ownership;
    private final InterchangeIdentityService identityService;
    private final TransactionEntryService transactionEntryService;
    private final IntConsumer afterBusinessWrite;

    public SclxImportCommitService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this(jpa, companyCodeSupplier, ignored -> { });
    }

    SclxImportCommitService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            IntConsumer afterBusinessWrite)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.afterBusinessWrite = Objects.requireNonNull(afterBusinessWrite, "afterBusinessWrite");
        this.previewService = new SclxImportPreviewService(jpa, companyCodeSupplier);
        this.parser = new SclxDocumentParser();
        this.ownership = new CompanyOwnershipService(jpa);
        this.identityService = new InterchangeIdentityService(jpa, ownership);
        this.transactionEntryService = new TransactionEntryService(jpa, companyCodeSupplier);
    }

    /**
     * Re-reads and re-previews the source immediately before entering the
     * caller-owned transaction. A changed file or newly blocking target state
     * cannot cross the commit boundary.
     */
    public SclxImportResult commit(
            Path source,
            SclxImportPreview approvedPreview,
            String actor)
    {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(approvedPreview, "approvedPreview");
        SclxImportPreview current = previewService.preview(source);
        if (!approvedPreview.operation().sourceSha256().equals(current.operation().sourceSha256()))
        {
            throw new IllegalStateException("SCLX source changed after preview; preview it again before importing.");
        }
        if (!approvedPreview.targetCompanyCode().equalsIgnoreCase(current.targetCompanyCode()))
        {
            throw new IllegalStateException("The selected SCLX target changed after preview.");
        }
        if (current.hasBlockingErrors())
        {
            throw new IllegalStateException("SCLX has blocking validation or target conflicts.");
        }
        requireCoreOnly(current);

        SclxParsedDocument parsed = parser.parse(source);
        if (!parsed.sha256().equals(current.operation().sourceSha256()))
        {
            throw new IllegalStateException("SCLX source changed while commit validation was running.");
        }
        JsonNode root = parsed.root();
        requireSupportedCoreShape(root);
        ownership.requireNoOpenOwnershipIssues();

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = ownership.requireCompany(em, companyCodeSupplier.get());
                if (!company.isActive())
                {
                    throw new IllegalStateException("SCLX target company is inactive: " + company.getCode() + ".");
                }
                if (!company.getCode().equalsIgnoreCase(current.targetCompanyCode()))
                {
                    throw new IllegalStateException("The SCLX target company changed before commit.");
                }

                Map<EntityKey, SclxImportEntityPreview> previews = previewItems(current);
                boolean allIdentical = current.operation().items().stream()
                        .allMatch(item -> item.identityMatch() == InterchangeIdentityMatch.IDENTICAL);
                if (allIdentical)
                {
                    em.getTransaction().commit();
                    return successfulResult(current, 0L, current.operation().items().size(),
                            "SCLX_IDENTICAL_REIMPORT", "Every governed core identity was identical; no data changed.");
                }
                requireEmptyTarget(em, company);

                int writes = 0;
                applyOrganization(company, root.path("organization"));
                afterBusinessWrite.accept(++writes);

                ChartOfAccounts chart = targetChart(em, company, root);
                Map<String, Account> accounts = writeAccounts(
                        em, chart, root.path("chartOfAccounts"), previews, writes);
                writes += accounts.size();
                Map<String, Fund> funds = writeFunds(
                        em, company, root.path("funds"), previews, writes);
                writes += funds.size();
                TransactionWrite transactions = writeTransactions(
                        em, company, root.path("transactions"), accounts, funds, previews, actor, writes);
                writes += transactions.transactionCount();

                em.flush();
                recordIdentity(em, company, current, previews, "ORGANIZATION",
                        text(root.path("organization"), "organizationId"), String.valueOf(company.getId()));
                recordMasterIdentities(em, company, current, previews, root.path("chartOfAccounts"),
                        "ACCOUNT", "accountId", accounts);
                recordMasterIdentities(em, company, current, previews, root.path("funds"),
                        "FUND", "fundId", funds);
                recordTransactionIdentities(em, company, current, previews, transactions);

                AuditEvent operationAudit = new AuditEvent();
                operationAudit.setCompany(company);
                operationAudit.setActor(cleanActor(actor));
                operationAudit.setActionType("SCLX_CORE_IMPORTED");
                operationAudit.setEntityType("Company");
                operationAudit.setEntityId(String.valueOf(company.getId()));
                operationAudit.setSummary("Imported governed SCLX core company data");
                operationAudit.setAfterValue("source=" + current.operation().sourceName()
                        + ",version=" + current.version().externalValue()
                        + ",sha256=" + current.operation().sourceSha256()
                        + ",created=" + actualCreatedCount(current));
                operationAudit.setReason("Atomic SCLX core import; later section families were absent.");
                em.persist(operationAudit);
                afterBusinessWrite.accept(++writes);

                em.getTransaction().commit();
                return successfulResult(current, actualCreatedCount(current),
                        current.operation().counts().identical(),
                        "SCLX_CORE_COMMIT_COMPLETED",
                        "SCLX organization, chart/accounts, funds, and canonical transactions committed atomically.");
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive())
                {
                    em.getTransaction().rollback();
                }
                List<InterchangeValidationMessage> messages = new ArrayList<>(current.operation().messages());
                messages.add(message("SCLX_COMMIT_ROLLED_BACK", "commit", safeMessage(ex), true));
                InterchangeOperationCounts counts = new InterchangeOperationCounts(
                        current.operation().counts().total(),
                        0L,
                        0L,
                        current.operation().counts().identical(),
                        current.operation().counts().skipped(),
                        current.operation().counts().warnings(),
                        current.operation().counts().errors() + 1L);
                return new SclxImportResult(
                        false,
                        true,
                        current.operation().targetLabel(),
                        current.operation().sourceSha256(),
                        current.operation().items(),
                        messages,
                        counts);
            }
        }
    }

    private void requireCoreOnly(SclxImportPreview preview)
    {
        Set<String> unsupportedTypes = new HashSet<>();
        for (SclxImportEntityPreview item : preview.operation().items())
        {
            if (!CORE_ENTITY_TYPES.contains(item.entityType()))
            {
                unsupportedTypes.add(item.entityType());
            }
        }
        if (!unsupportedTypes.isEmpty()
                || preview.sectionCounts().count("budgets") > 0L
                || preview.sectionCounts().count("budgetLines") > 0L
                || preview.sectionCounts().relationshipCount() > 0L
                || preview.sectionCounts().unsupportedSectionCount() > 0L)
        {
            throw new IllegalStateException("P15-S5-C2 core commit does not yet import budgets, relationships, "
                    + "unsupported root sections, or extension entities: " + unsupportedTypes + ".");
        }
    }

    private static void requireSupportedCoreShape(JsonNode root)
    {
        for (JsonNode transaction : root.path("transactions"))
        {
            if (!"ENTERED".equals(text(transaction, "status")))
            {
                throw new IllegalStateException("Core SCLX commit currently requires ENTERED transactions.");
            }
            if (presentText(transaction, "reference") || presentText(transaction, "correctionType")
                    || presentText(transaction, "correctionOfTransactionId"))
            {
                throw new IllegalStateException("Transaction references and correction relationships require a later SCLX slice.");
            }
            for (JsonNode line : transaction.path("lines"))
            {
                if (!presentText(line, "fundId"))
                {
                    throw new IllegalStateException("Every committed canonical transaction line requires a fundId.");
                }
                if (presentText(line, "activityId") || presentText(line, "counterpartyId"))
                {
                    throw new IllegalStateException("Transaction party and activity references require a later SCLX slice.");
                }
            }
        }
    }

    private static void requireEmptyTarget(EntityManager em, Company company)
    {
        ChartOfAccounts chart = company.getActiveChartOfAccounts();
        long accounts = chart == null ? 0L : em.createQuery(
                        "select count(a) from Account a where a.chart = :chart", Long.class)
                .setParameter("chart", chart)
                .getSingleResult();
        long funds = em.createQuery("select count(f) from Fund f where f.company = :company", Long.class)
                .setParameter("company", company)
                .getSingleResult();
        long transactions = em.createQuery("select count(t) from Txn t where t.company = :company", Long.class)
                .setParameter("company", company)
                .getSingleResult();
        long budgets = em.createQuery("select count(p) from BudgetPlan p where p.company = :company", Long.class)
                .setParameter("company", company)
                .getSingleResult();
        if (accounts + funds + transactions + budgets != 0L)
        {
            throw new IllegalStateException("SCLX core commit requires an empty target company.");
        }
    }

    private static void applyOrganization(Company company, JsonNode organization)
    {
        company.setDisplayName(text(organization, "name"));
        company.setDefaultCurrency(text(organization, "baseCurrency").toUpperCase(Locale.ROOT));
        LocalDate fiscalStart = LocalDate.parse(text(organization, "fiscalYearStart"));
        company.setFiscalYearStartMonth(fiscalStart.getMonthValue());
        company.setFiscalYearStartDay(fiscalStart.getDayOfMonth());
        company.touchUpdatedAt();
    }

    private ChartOfAccounts targetChart(EntityManager em, Company company, JsonNode root)
    {
        ChartOfAccounts chart = company.getActiveChartOfAccounts();
        JsonNode app = root.path("extensions").path("scaJakartaH2");
        String chartName = optionalText(app, "activeChartName");
        String chartVersion = optionalText(app, "activeChartVersion");
        if (chart == null)
        {
            chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            company.setActiveChartOfAccounts(chart);
        }
        ownership.ensureOwnedBy(em, company, chart, "Active Chart of Accounts");
        chart.setName(chartName == null ? company.getDisplayName() + " Chart of Accounts" : chartName);
        chart.setVersion(chartVersion == null ? "SCLX-" + SclxVersion.writerVersion().externalValue() : chartVersion);
        chart.setStatus(ChartStatus.ACTIVE);
        chart.touchUpdatedAt();
        return chart;
    }

    private Map<String, Account> writeAccounts(
            EntityManager em,
            ChartOfAccounts chart,
            JsonNode values,
            Map<EntityKey, SclxImportEntityPreview> previews,
            int writesBefore)
    {
        List<JsonNode> ordered = parentFirst(values, "accountId", "parentAccountId", "account");
        Map<String, Account> result = new LinkedHashMap<>();
        int writes = writesBefore;
        for (JsonNode value : ordered)
        {
            String externalId = text(value, "accountId");
            requireNew(previews, "ACCOUNT", externalId);
            Account account = new Account();
            account.setChart(chart);
            account.setCode(text(value, "code"));
            account.setName(text(value, "name"));
            account.setAccountType(enumValue(AccountType.class, text(value, "type"), "account type"));
            String subtype = optionalText(value, "subtype");
            account.setSubtype(subtype == null ? null : enumValue(AccountSubtype.class, subtype, "account subtype"));
            account.setNormalBalance(enumValue(NormalBalance.class, text(value, "increaseSide"), "increase side"));
            String parentId = optionalText(value, "parentAccountId");
            account.setParent(parentId == null ? null : required(result, parentId, "parent account"));
            account.setOpeningBalance(decimal(value, "openingBalance"));
            account.setPosting(requiredBoolean(value, "posting"));
            account.setActive(requiredBoolean(value, "active"));
            em.persist(account);
            result.put(externalId, account);
            afterBusinessWrite.accept(++writes);
        }
        return result;
    }

    private Map<String, Fund> writeFunds(
            EntityManager em,
            Company company,
            JsonNode values,
            Map<EntityKey, SclxImportEntityPreview> previews,
            int writesBefore)
    {
        List<JsonNode> ordered = parentFirst(values, "fundId", "parentFundId", "fund");
        Map<String, Fund> result = new LinkedHashMap<>();
        int writes = writesBefore;
        for (JsonNode value : ordered)
        {
            String externalId = text(value, "fundId");
            requireNew(previews, "FUND", externalId);
            Fund fund = new Fund();
            fund.setCompany(company);
            fund.setCode(text(value, "code"));
            fund.setName(text(value, "name"));
            fund.setFundType(enumValue(FundType.class, text(value, "type"), "fund type"));
            String parentId = optionalText(value, "parentFundId");
            fund.setParent(parentId == null ? null : required(result, parentId, "parent fund"));
            fund.setActive(requiredBoolean(value, "active"));
            fund.setEffectiveFrom(optionalDate(value, "effectiveFrom"));
            fund.setEffectiveTo(optionalDate(value, "effectiveTo"));
            fund.setRestrictionText(optionalText(value, "restrictionText"));
            em.persist(fund);
            result.put(externalId, fund);
            afterBusinessWrite.accept(++writes);
        }
        return result;
    }

    private TransactionWrite writeTransactions(
            EntityManager em,
            Company company,
            JsonNode values,
            Map<String, Account> accounts,
            Map<String, Fund> funds,
            Map<EntityKey, SclxImportEntityPreview> previews,
            String actor,
            int writesBefore)
    {
        Map<String, Txn> transactions = new LinkedHashMap<>();
        Map<String, TxnSplit> lines = new LinkedHashMap<>();
        Set<String> skippedLines = new HashSet<>();
        int writes = writesBefore;
        for (JsonNode value : values)
        {
            String transactionId = text(value, "transactionId");
            requireNew(previews, "TRANSACTION", transactionId);
            List<TransactionLineCommand> commands = new ArrayList<>();
            List<String> postingLineIds = new ArrayList<>();
            for (JsonNode line : value.path("lines"))
            {
                String lineId = text(line, "lineId");
                requireNew(previews, "TRANSACTION_LINE", lineId);
                BigDecimal debit = decimal(line, "debit");
                BigDecimal credit = decimal(line, "credit");
                if (debit.signum() == 0 && credit.signum() == 0)
                {
                    skippedLines.add(lineId);
                    continue;
                }
                Account account = required(accounts, text(line, "accountId"), "transaction account");
                Fund fund = required(funds, text(line, "fundId"), "transaction fund");
                commands.add(new TransactionLineCommand(
                        account.getId(), fund.getId(), null, null, null,
                        debit, credit, false, optionalText(line, "memo")));
                postingLineIds.add(lineId);
            }
            TransactionCommand command = new TransactionCommand(
                    LocalDate.parse(text(value, "transactionDate")),
                    null,
                    text(value, "description"),
                    null,
                    commands);
            Txn transaction = transactionEntryService.enter(
                    em, company, command, portableUuid(transactionId), cleanActor(actor));
            transaction.setStatus(text(value, "status"));
            transactions.put(transactionId, transaction);
            em.flush();
            List<TxnSplit> persistedLines = em.createQuery(
                            "from TxnSplit s where s.txn = :txn order by s.id", TxnSplit.class)
                    .setParameter("txn", transaction)
                    .getResultList();
            if (persistedLines.size() != postingLineIds.size())
            {
                throw new IllegalStateException("Canonical transaction line count changed during SCLX import.");
            }
            for (int index = 0; index < persistedLines.size(); index++)
            {
                lines.put(postingLineIds.get(index), persistedLines.get(index));
            }
            afterBusinessWrite.accept(++writes);
        }
        return new TransactionWrite(transactions, lines, skippedLines, transactions.size());
    }

    private static UUID portableUuid(String externalId)
    {
        int colon = externalId.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < externalId.length())
        {
            String decoded = URLDecoder.decode(externalId.substring(colon + 1), StandardCharsets.UTF_8);
            try
            {
                return UUID.fromString(decoded);
            }
            catch (IllegalArgumentException ignored)
            {
                // Older/donor identities receive a deterministic local durable UUID below.
            }
        }
        return UUID.nameUUIDFromBytes(("SCLX:" + externalId).getBytes(StandardCharsets.UTF_8));
    }

    private void recordMasterIdentities(
            EntityManager em,
            Company company,
            SclxImportPreview preview,
            Map<EntityKey, SclxImportEntityPreview> previews,
            JsonNode values,
            String type,
            String idField,
            Map<String, ?> entities)
    {
        for (JsonNode value : values)
        {
            String externalId = text(value, idField);
            Object entity = required(entities, externalId, type.toLowerCase(Locale.ROOT));
            String localId;
            if (entity instanceof Account account)
            {
                localId = String.valueOf(account.getId());
            }
            else if (entity instanceof Fund fund)
            {
                localId = String.valueOf(fund.getId());
            }
            else
            {
                throw new IllegalStateException("Unsupported SCLX core identity entity: " + entity.getClass());
            }
            recordIdentity(em, company, preview, previews, type, externalId, localId);
        }
    }

    private void recordTransactionIdentities(
            EntityManager em,
            Company company,
            SclxImportPreview preview,
            Map<EntityKey, SclxImportEntityPreview> previews,
            TransactionWrite written)
    {
        for (Map.Entry<String, Txn> entry : written.transactions().entrySet())
        {
            recordIdentity(em, company, preview, previews, "TRANSACTION",
                    entry.getKey(), String.valueOf(entry.getValue().getId()));
        }
        for (Map.Entry<String, TxnSplit> entry : written.lines().entrySet())
        {
            recordIdentity(em, company, preview, previews, "TRANSACTION_LINE",
                    entry.getKey(), String.valueOf(entry.getValue().getId()));
        }
        for (String externalId : written.skippedLines())
        {
            recordIdentity(em, company, preview, previews, "TRANSACTION_LINE", externalId, null);
        }
    }

    private void recordIdentity(
            EntityManager em,
            Company company,
            SclxImportPreview preview,
            Map<EntityKey, SclxImportEntityPreview> previews,
            String type,
            String externalId,
            String localId)
    {
        SclxImportEntityPreview item = required(previews, new EntityKey(type, externalId), "preview identity");
        identityService.record(
                em,
                company,
                InterchangeFormat.SCLX,
                preview.sourceSystem(),
                type,
                externalId,
                item.normalizedContentHash(),
                localId);
    }

    private static Map<EntityKey, SclxImportEntityPreview> previewItems(SclxImportPreview preview)
    {
        Map<EntityKey, SclxImportEntityPreview> result = new HashMap<>();
        for (SclxImportEntityPreview item : preview.operation().items())
        {
            EntityKey key = new EntityKey(item.entityType(), item.externalId());
            if (result.put(key, item) != null)
            {
                throw new IllegalStateException("Duplicate preview identity: " + key + ".");
            }
        }
        return Map.copyOf(result);
    }

    private static void requireNew(
            Map<EntityKey, SclxImportEntityPreview> previews,
            String type,
            String externalId)
    {
        SclxImportEntityPreview item = required(previews, new EntityKey(type, externalId), "preview identity");
        if (item.identityMatch() != InterchangeIdentityMatch.NEW)
        {
            throw new IllegalStateException("Mixed new/identical SCLX core import is not supported: "
                    + type + " " + externalId + ".");
        }
    }

    private static List<JsonNode> parentFirst(
            JsonNode values,
            String idField,
            String parentField,
            String label)
    {
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        for (JsonNode value : values)
        {
            byId.put(text(value, idField), value);
        }
        List<JsonNode> result = new ArrayList<>();
        Set<String> complete = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        byId.keySet().stream().sorted().forEach(id -> visit(
                id, byId, idField, parentField, label, complete, visiting, result));
        return List.copyOf(result);
    }

    private static void visit(
            String id,
            Map<String, JsonNode> byId,
            String idField,
            String parentField,
            String label,
            Set<String> complete,
            Set<String> visiting,
            List<JsonNode> result)
    {
        if (complete.contains(id))
        {
            return;
        }
        if (!visiting.add(id))
        {
            throw new IllegalStateException("SCLX " + label + " hierarchy contains a cycle at " + id + ".");
        }
        JsonNode value = required(byId, id, label);
        String parentId = optionalText(value, parentField);
        if (parentId != null)
        {
            if (!byId.containsKey(parentId))
            {
                throw new IllegalStateException("SCLX " + label + " parent does not resolve: " + parentId + ".");
            }
            visit(parentId, byId, idField, parentField, label, complete, visiting, result);
        }
        visiting.remove(id);
        complete.add(id);
        result.add(value);
    }

    private static SclxImportResult successfulResult(
            SclxImportPreview preview,
            long created,
            long identical,
            String code,
            String detail)
    {
        List<InterchangeValidationMessage> messages = new ArrayList<>(preview.operation().messages());
        messages.add(new InterchangeValidationMessage(
                InterchangeMessageSeverity.INFO, code, "commit", detail, false));
        InterchangeOperationCounts counts = new InterchangeOperationCounts(
                preview.operation().counts().total(),
                created,
                0L,
                identical,
                preview.operation().counts().skipped(),
                preview.operation().counts().warnings(),
                0L);
        return new SclxImportResult(
                true,
                false,
                preview.operation().targetLabel(),
                preview.operation().sourceSha256(),
                preview.operation().items(),
                messages,
                counts);
    }

    private static long actualCreatedCount(SclxImportPreview preview)
    {
        long skippedZeroLines = preview.transactions().stream()
                .mapToLong(SclxImportTransactionPreview::zeroValueLineCount)
                .sum();
        return Math.max(0L, preview.operation().counts().created() - skippedZeroLines);
    }

    private static InterchangeValidationMessage message(
            String code,
            String path,
            String detail,
            boolean blocking)
    {
        return new InterchangeValidationMessage(
                blocking ? InterchangeMessageSeverity.ERROR : InterchangeMessageSeverity.INFO,
                code,
                path,
                detail,
                blocking);
    }

    private static <K, V> V required(Map<K, V> values, K key, String label)
    {
        V value = values.get(key);
        if (value == null)
        {
            throw new IllegalStateException("Required " + label + " was not available: " + key + ".");
        }
        return value;
    }

    private static String text(JsonNode value, String field)
    {
        JsonNode node = value.get(field);
        if (node == null || !node.isTextual() || node.textValue().isBlank())
        {
            throw new IllegalStateException(field + " is required and must be a nonblank string.");
        }
        return node.textValue().trim();
    }

    private static String optionalText(JsonNode value, String field)
    {
        JsonNode node = value.get(field);
        return node == null || node.isNull() || !node.isTextual() || node.textValue().isBlank()
                ? null
                : node.textValue().trim();
    }

    private static boolean presentText(JsonNode value, String field)
    {
        return optionalText(value, field) != null;
    }

    private static BigDecimal decimal(JsonNode value, String field)
    {
        JsonNode node = value.get(field);
        if (node == null || (!node.isTextual() && !node.isNumber()))
        {
            throw new IllegalStateException(field + " must be a decimal value.");
        }
        return new BigDecimal(node.asText());
    }

    private static boolean requiredBoolean(JsonNode value, String field)
    {
        JsonNode node = value.get(field);
        if (node == null || !node.isBoolean())
        {
            throw new IllegalStateException(field + " must be a boolean.");
        }
        return node.booleanValue();
    }

    private static LocalDate optionalDate(JsonNode value, String field)
    {
        String text = optionalText(value, field);
        return text == null ? null : LocalDate.parse(text);
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> type,
            String value,
            String label)
    {
        try
        {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        }
        catch (RuntimeException ex)
        {
            throw new IllegalStateException("Unsupported " + label + ": " + value + ".", ex);
        }
    }

    private static String cleanActor(String actor)
    {
        return actor == null || actor.isBlank() ? "system" : actor.trim();
    }

    private static String safeMessage(Throwable ex)
    {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private record EntityKey(String type, String externalId)
    {
        private EntityKey
        {
            type = Objects.requireNonNull(type, "type").toUpperCase(Locale.ROOT);
            externalId = Objects.requireNonNull(externalId, "externalId");
        }
    }

    private record TransactionWrite(
            Map<String, Txn> transactions,
            Map<String, TxnSplit> lines,
            Set<String> skippedLines,
            int transactionCount)
    {
        private TransactionWrite
        {
            transactions = Map.copyOf(transactions);
            lines = Map.copyOf(lines);
            skippedLines = Set.copyOf(skippedLines);
        }
    }
}
