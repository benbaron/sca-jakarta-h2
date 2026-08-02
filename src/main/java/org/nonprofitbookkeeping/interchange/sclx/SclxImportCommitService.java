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
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.BudgetLine;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.FixedAssetDepreciationRun;
import org.nonprofitbookkeeping.model.Merchant;
import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.model.InventoryMovement;
import org.nonprofitbookkeeping.model.ImportIssue;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.model.TxnSupplementalLine;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.BudgetCategoryAdminService;
import org.nonprofitbookkeeping.service.AuditHistoryService;
import org.nonprofitbookkeeping.service.BankAccountImportCommand;
import org.nonprofitbookkeeping.service.BankClearedStateService;
import org.nonprofitbookkeeping.service.BankCommand;
import org.nonprofitbookkeeping.service.BankConfigurationService;
import org.nonprofitbookkeeping.service.BankImportReviewService;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService;
import org.nonprofitbookkeeping.service.BudgetLineCommand;
import org.nonprofitbookkeeping.service.BudgetPlanCommand;
import org.nonprofitbookkeeping.service.BudgetPlanService;
import org.nonprofitbookkeeping.service.CompanyOwnershipService;
import org.nonprofitbookkeeping.service.InterchangeIdentityService;
import org.nonprofitbookkeeping.service.FixedAssetCommand;
import org.nonprofitbookkeeping.service.FixedAssetService;
import org.nonprofitbookkeeping.service.InventoryItemCommand;
import org.nonprofitbookkeeping.service.InventoryService;
import org.nonprofitbookkeeping.service.PeriodCloseRangeService;
import org.nonprofitbookkeeping.service.TransactionCommand;
import org.nonprofitbookkeeping.service.TransactionCorrectionService;
import org.nonprofitbookkeeping.service.TransactionEntryService;
import org.nonprofitbookkeeping.service.TransactionLineCommand;
import org.nonprofitbookkeeping.service.TransactionSupplementalLineCommand;

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
 * Governed SCLX commit boundary for the core graph and supported application extensions.
 *
 * <p>This slice imports the organization profile, active chart/accounts, funds,
 * activities, counterparties, merchants, normalized budgets, balanced canonical
 * transactions, supplemental transaction details, fixed assets, and completed
 * depreciation runs, inventory, banking, reconciliation, period-close facts, and factual audit
 * history and correction relationships into an empty selected company.</p>
 */
public final class SclxImportCommitService
{
    private static final Set<String> SUPPORTED_ENTITY_TYPES = Set.of(
            "ORGANIZATION", "ACCOUNT", "FUND", "ACTIVITY", "COUNTERPARTY", "MERCHANT",
            "BUDGET", "BUDGET_LINE", "TRANSACTION", "TRANSACTION_LINE", "SUPPLEMENTAL_DETAIL",
            "FIXED_ASSET", "DEPRECIATION_RUN", "INVENTORY_ITEM", "INVENTORY_MOVEMENT",
            "BANK", "BANK_ACCOUNT", "BANK_IMPORT_BATCH", "BANK_STATEMENT_LINE",
            "BANK_IMPORT_ISSUE", "RECONCILIATION_SESSION", "RECONCILIATION_MATCH",
            "PERIOD_CLOSE_RANGE", "PERIOD_CLOSE_EVENT", "AUDIT_EVENT");
    private static final Set<String> SUPPORTED_EXTENSION_KEYS = Set.of(
            "activeChartName", "activeChartVersion", "activities", "counterparties",
            "supplementalDetails", "fixedAssets", "inventory", "bankConfiguration",
            "bankStatementFacts", "reconciliation", "periodClose", "auditHistory");

    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;
    private final SclxImportPreviewService previewService;
    private final SclxDocumentParser parser;
    private final CompanyOwnershipService ownership;
    private final InterchangeIdentityService identityService;
    private final BudgetCategoryAdminService budgetCategoryAdminService;
    private final BudgetPlanService budgetPlanService;
    private final FixedAssetService fixedAssetService;
    private final InventoryService inventoryService;
    private final BankConfigurationService bankConfigurationService;
    private final BankImportReviewService bankImportReviewService;
    private final BankClearedStateService bankClearedStateService;
    private final BankReconciliationWorkspaceService bankReconciliationService;
    private final PeriodCloseRangeService periodCloseRangeService;
    private final AuditHistoryService auditHistoryService;
    private final TransactionEntryService transactionEntryService;
    private final TransactionCorrectionService transactionCorrectionService;
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
        this.budgetCategoryAdminService = new BudgetCategoryAdminService(jpa, companyCodeSupplier);
        this.budgetPlanService = new BudgetPlanService(jpa, companyCodeSupplier);
        this.fixedAssetService = new FixedAssetService(jpa);
        this.inventoryService = new InventoryService(jpa);
        this.bankConfigurationService = new BankConfigurationService(jpa);
        this.bankImportReviewService = new BankImportReviewService(jpa);
        this.bankClearedStateService = new BankClearedStateService(jpa);
        this.bankReconciliationService = new BankReconciliationWorkspaceService(jpa);
        this.periodCloseRangeService = new PeriodCloseRangeService(jpa);
        this.auditHistoryService = new AuditHistoryService();
        this.transactionEntryService = new TransactionEntryService(jpa, companyCodeSupplier);
        this.transactionCorrectionService = new TransactionCorrectionService(jpa, companyCodeSupplier);
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
        SclxParsedDocument parsed = parser.parse(source);
        if (!parsed.sha256().equals(current.operation().sourceSha256()))
        {
            throw new IllegalStateException("SCLX source changed while commit validation was running.");
        }
        JsonNode root = parsed.root();
        requireSupportedSections(current, root);
        SclxBudgetImportData budgets = SclxBudgetImportData.parse(root.path("budgets"));
        SclxFixedAssetImportData fixedAssets = SclxFixedAssetImportData.parse(root);
        SclxInventoryImportData inventory = SclxInventoryImportData.parse(root);
        SclxBankingImportData banking = SclxBankingImportData.parse(root);
        SclxPeriodCloseImportData periodClose = SclxPeriodCloseImportData.parse(root);
        SclxAuditHistoryImportData auditHistory = SclxAuditHistoryImportData.parse(root);
        SclxTransactionDetailImportData details = SclxTransactionDetailImportData.parse(root);
        SclxCorrectionImportData corrections = SclxCorrectionImportData.parse(root);
        requireSupportedTransactionShape(root, details);
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
                            "SCLX_IDENTICAL_REIMPORT",
                            "Every governed core, extension, banking, reconciliation, period-close, audit-history, and correction fact was identical; no data changed.");
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
                BudgetWrite writtenBudgets = writeBudgets(
                        em, company, budgets, funds, previews, writes);
                writes += writtenBudgets.businessWriteCount();
                Map<String, Activity> activities = writeActivities(
                        em, company, details.activities(), previews, writes);
                writes += activities.size();
                Map<String, Counterparty> counterparties = writeCounterparties(
                        em, company, details.counterparties(), previews, writes);
                writes += counterparties.size();
                Map<String, Merchant> merchants = writeMerchants(
                        em, company, details.merchants(), previews, writes);
                writes += merchants.size();
                TransactionWrite transactions = writeTransactions(
                        em, company, root.path("transactions"), accounts, funds, activities,
                        counterparties, merchants, details, previews, actor, writes);
                writes += transactions.transactionCount();
                writes += writeCorrectionRelationships(
                        em, company, corrections, transactions.transactions(), writes);
                FixedAssetWrite writtenFixedAssets = writeFixedAssets(
                        em, company, fixedAssets, accounts, funds, transactions.transactions(), previews, writes);
                writes += writtenFixedAssets.businessWriteCount();
                InventoryWrite writtenInventory = writeInventory(
                        em, company, inventory, accounts, funds, transactions.transactions(), previews, writes);
                writes += writtenInventory.businessWriteCount();
                BankingWrite writtenBanking = writeBanking(
                        em, company, banking, accounts, transactions, previews, writes);
                writes += writtenBanking.businessWriteCount();
                PeriodCloseWrite writtenPeriodClose = writePeriodClose(
                        em, company, periodClose, previews, writes);
                writes += writtenPeriodClose.businessWriteCount();
                AuditHistoryWrite writtenAuditHistory = writeAuditHistory(
                        em, company, auditHistory, previews, writes);
                writes += writtenAuditHistory.businessWriteCount();

                em.flush();
                recordIdentity(em, company, current, previews, "ORGANIZATION",
                        text(root.path("organization"), "organizationId"), String.valueOf(company.getId()));
                recordMasterIdentities(em, company, current, previews, root.path("chartOfAccounts"),
                        "ACCOUNT", "accountId", accounts);
                recordMasterIdentities(em, company, current, previews, root.path("funds"),
                        "FUND", "fundId", funds);
                recordBudgetIdentities(em, company, current, previews, writtenBudgets);
                recordEntityIdentities(em, company, current, previews, activities, "ACTIVITY");
                recordEntityIdentities(em, company, current, previews, counterparties, "COUNTERPARTY");
                recordEntityIdentities(em, company, current, previews, merchants, "MERCHANT");
                recordTransactionIdentities(em, company, current, previews, transactions);
                recordFixedAssetIdentities(em, company, current, previews, writtenFixedAssets);
                recordInventoryIdentities(em, company, current, previews, writtenInventory);
                recordBankingIdentities(em, company, current, previews, writtenBanking);
                recordPeriodCloseIdentities(em, company, current, previews, writtenPeriodClose);
                recordAuditHistoryIdentities(em, company, current, previews, writtenAuditHistory);

                AuditEvent operationAudit = new AuditEvent();
                operationAudit.setCompany(company);
                operationAudit.setActor(cleanActor(actor));
                operationAudit.setActionType("SCLX_IMPORTED");
                operationAudit.setEntityType("Company");
                operationAudit.setEntityId(String.valueOf(company.getId()));
                operationAudit.setSummary("Imported governed SCLX active-company business data");
                operationAudit.setAfterValue("source=" + current.operation().sourceName()
                        + ",version=" + current.version().externalValue()
                        + ",sha256=" + current.operation().sourceSha256()
                        + ",created=" + actualCreatedCount(current));
                operationAudit.setReason(
                        "Atomic SCLX core, supported extension, banking, reconciliation, period-close, factual audit-history, and correction-relationship import.");
                em.persist(operationAudit);
                afterBusinessWrite.accept(++writes);

                em.getTransaction().commit();
                return successfulResult(current, actualCreatedCount(current),
                        current.operation().counts().identical(),
                        "SCLX_COMMIT_COMPLETED",
                        "SCLX organization, chart/accounts, funds, normalized budgets, transaction-linked "
                                + "masters, canonical transactions, supplemental details, fixed assets, and completed "
                                + "depreciation runs, inventory, bank configuration, reviewed statement facts, "
                                + "clearance, reconciliation, period-close history, factual audit history, and correction relationships committed atomically.");
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

    private static void requireSupportedSections(SclxImportPreview preview, JsonNode root)
    {
        Set<String> unsupportedTypes = new HashSet<>();
        for (SclxImportEntityPreview item : preview.operation().items())
        {
            if (!SUPPORTED_ENTITY_TYPES.contains(item.entityType()))
            {
                unsupportedTypes.add(item.entityType());
            }
        }
        if (!unsupportedTypes.isEmpty() || preview.sectionCounts().unsupportedSectionCount() > 0L)
        {
            throw new IllegalStateException("P15-S5-C10 import cannot discard unsupported root sections "
                    + "or later application-extension entities: " + unsupportedTypes + ".");
        }
        JsonNode app = root.path("extensions").path("scaJakartaH2");
        if (app.isObject())
        {
            app.fields().forEachRemaining(entry ->
            {
                if (!SUPPORTED_EXTENSION_KEYS.contains(entry.getKey()) && !emptyExtensionValue(entry.getValue()))
                {
                    throw new IllegalStateException(
                            "P15-S5-C10 cannot discard populated extension " + entry.getKey() + ".");
                }
            });
        }
    }

    private static boolean emptyExtensionValue(JsonNode value)
    {
        if (value == null || value.isNull() || value.isMissingNode())
        {
            return true;
        }
        if (value.isArray())
        {
            return value.isEmpty();
        }
        if (value.isObject())
        {
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext())
            {
                Map.Entry<String, JsonNode> field = fields.next();
                if ("version".equals(field.getKey()) && field.getValue().isIntegralNumber())
                {
                    continue;
                }
                if (!emptyExtensionValue(field.getValue()))
                {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static void requireSupportedTransactionShape(
            JsonNode root,
            SclxTransactionDetailImportData details)
    {
        for (JsonNode transaction : root.path("transactions"))
        {
            if (presentText(transaction, "reference"))
            {
                throw new IllegalStateException(
                        "Transaction reference cannot be preserved by the current canonical transaction model.");
            }
            for (JsonNode line : transaction.path("lines"))
            {
                if (!presentText(line, "fundId"))
                {
                    throw new IllegalStateException("Every committed canonical transaction line requires a fundId.");
                }
                BigDecimal debit = decimal(line, "debit");
                BigDecimal credit = decimal(line, "credit");
                if (debit.signum() == 0 && credit.signum() == 0
                        && (presentText(line, "activityId")
                        || presentText(line, "counterpartyId")
                        || details.merchantForLine(text(line, "lineId")) != null))
                {
                    throw new IllegalStateException(
                            "A skipped zero-value line cannot carry activity, counterparty, or merchant facts.");
                }
            }
        }
    }

    private int writeCorrectionRelationships(
            EntityManager em,
            Company company,
            SclxCorrectionImportData source,
            Map<String, Txn> transactions,
            int writesBefore)
    {
        int writes = writesBefore;
        for (SclxCorrectionImportData.CorrectionValue value : source.relationships())
        {
            Txn correction = required(transactions, value.transactionId(), "correction transaction");
            Txn corrected = required(
                    transactions, value.correctedTransactionId(), "corrected transaction");
            transactionCorrectionService.restoreRelationshipForImport(
                    em, company, correction, value.correctionType(), corrected);
            afterBusinessWrite.accept(++writes);
        }
        return source.relationships().size();
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
        long budgetCategories = em.createQuery(
                        "select count(c) from BudgetCategory c where c.company = :company", Long.class)
                .setParameter("company", company)
                .getSingleResult();
        long activities = em.createQuery("select count(a) from Activity a where a.company = :company", Long.class)
                .setParameter("company", company)
                .getSingleResult();
        long counterparties = em.createQuery(
                        "select count(c) from Counterparty c where c.company = :company", Long.class)
                .setParameter("company", company)
                .getSingleResult();
        long merchants = em.createQuery("select count(m) from Merchant m where m.company = :company", Long.class)
                .setParameter("company", company)
                .getSingleResult();
        long fixedAssets = em.createQuery(
                        "select count(a) from FixedAsset a where a.company = :company", Long.class)
                .setParameter("company", company)
                .getSingleResult();
        long inventoryItems = em.createQuery(
                        "select count(i) from InventoryItem i where i.company = :company", Long.class)
                .setParameter("company", company)
                .getSingleResult();
        long banks = em.createQuery("select count(b) from Bank b where b.company = :company", Long.class)
                .setParameter("company", company)
                .getSingleResult();
        long bankAccounts = em.createQuery(
                        "select count(a) from CompanyBankAccount a where a.company = :company", Long.class)
                .setParameter("company", company)
                .getSingleResult();
        long importBatches = em.createQuery(
                        "select count(b) from BankImportBatch b where b.company = :company", Long.class)
                .setParameter("company", company)
                .getSingleResult();
        long reconciliationSessions = ((Number) em.createNativeQuery(
                        "select count(*) from bank_reconciliation_session where company_id = ?")
                .setParameter(1, company.getId())
                .getSingleResult()).longValue();
        long periodCloseRanges = ((Number) em.createNativeQuery(
                        "select count(*) from period_close_range where company_id = ?")
                .setParameter(1, company.getId())
                .getSingleResult()).longValue();
        long periodCloseEvents = ((Number) em.createNativeQuery(
                        "select count(*) from period_close_event where company_id = ?")
                .setParameter(1, company.getId())
                .getSingleResult()).longValue();
        long auditEvents = em.createQuery(
                        "select count(a) from AuditEvent a where a.company = :company", Long.class)
                .setParameter("company", company)
                .getSingleResult();
        if (accounts + funds + transactions + budgets + budgetCategories
                + activities + counterparties + merchants + fixedAssets + inventoryItems
                + banks + bankAccounts + importBatches + reconciliationSessions
                + periodCloseRanges + periodCloseEvents + auditEvents != 0L)
        {
            throw new IllegalStateException("SCLX import requires an empty target company.");
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

    private Map<String, Activity> writeActivities(
            EntityManager em,
            Company company,
            List<SclxTransactionDetailImportData.ActivityValue> values,
            Map<EntityKey, SclxImportEntityPreview> previews,
            int writesBefore)
    {
        Map<String, Activity> result = new LinkedHashMap<>();
        int writes = writesBefore;
        for (SclxTransactionDetailImportData.ActivityValue value : values)
        {
            requireNew(previews, "ACTIVITY", value.externalId());
            Activity activity = new Activity();
            activity.setCompany(company);
            activity.setCode(value.code());
            activity.setName(value.name());
            activity.setActive(value.active());
            em.persist(activity);
            result.put(value.externalId(), activity);
            afterBusinessWrite.accept(++writes);
        }
        return result;
    }

    private BudgetWrite writeBudgets(
            EntityManager em,
            Company company,
            SclxBudgetImportData source,
            Map<String, Fund> funds,
            Map<EntityKey, SclxImportEntityPreview> previews,
            int writesBefore)
    {
        Set<String> categoryCodes = new HashSet<>();
        for (SclxBudgetImportData.BudgetValue budget : source.budgets())
        {
            for (SclxBudgetImportData.LineValue line : budget.lines())
            {
                categoryCodes.add(line.categoryCode());
            }
        }

        int writes = writesBefore;
        Map<String, BudgetCategory> categories = new LinkedHashMap<>();
        for (String code : categoryCodes.stream().sorted().toList())
        {
            BudgetCategory category = budgetCategoryAdminService.createForImport(em, company, code);
            categories.put(code, category);
            afterBusinessWrite.accept(++writes);
        }
        em.flush();

        Map<String, BudgetPlan> plans = new LinkedHashMap<>();
        Map<String, BudgetLine> lines = new LinkedHashMap<>();
        for (SclxBudgetImportData.BudgetValue budget : source.budgets())
        {
            requireNew(previews, "BUDGET", budget.externalId());
            List<BudgetLineCommand> commands = new ArrayList<>();
            for (SclxBudgetImportData.LineValue line : budget.lines())
            {
                requireNew(previews, "BUDGET_LINE", line.externalId());
                BudgetCategory category = required(
                        categories, line.categoryCode(), "budget category");
                Fund fund = line.fundId() == null
                        ? null
                        : required(funds, line.fundId(), "budget fund");
                commands.add(new BudgetLineCommand(
                        category.getId(),
                        fund == null ? null : fund.getId(),
                        line.periodMonth(),
                        line.amount(),
                        ""));
            }

            BudgetPlan plan = budgetPlanService.createForImport(
                    em,
                    company,
                    new BudgetPlanCommand(
                            budget.name(),
                            budget.fiscalYear(),
                            budget.version(),
                            LocalDate.of(budget.fiscalYear(), 1, 1),
                            LocalDate.of(budget.fiscalYear(), 12, 31),
                            ""),
                    budget.active() ? BudgetPlan.Status.ACTIVE : BudgetPlan.Status.DRAFT,
                    commands);
            plans.put(budget.externalId(), plan);
            em.flush();
            List<BudgetLine> persistedLines = em.createQuery(
                            "from BudgetLine l where l.budgetPlan = :plan order by l.id", BudgetLine.class)
                    .setParameter("plan", plan)
                    .getResultList();
            if (persistedLines.size() != budget.lines().size())
            {
                throw new IllegalStateException("Canonical budget line count changed during SCLX import.");
            }
            for (int index = 0; index < persistedLines.size(); index++)
            {
                lines.put(budget.lines().get(index).externalId(), persistedLines.get(index));
            }
            afterBusinessWrite.accept(++writes);
            for (int index = 0; index < persistedLines.size(); index++)
            {
                afterBusinessWrite.accept(++writes);
            }
        }
        return new BudgetWrite(
                plans,
                lines,
                categories.size() + plans.size() + lines.size());
    }

    private Map<String, Counterparty> writeCounterparties(
            EntityManager em,
            Company company,
            List<SclxTransactionDetailImportData.CounterpartyValue> values,
            Map<EntityKey, SclxImportEntityPreview> previews,
            int writesBefore)
    {
        Map<String, Counterparty> result = new LinkedHashMap<>();
        int writes = writesBefore;
        for (SclxTransactionDetailImportData.CounterpartyValue value : values)
        {
            requireNew(previews, "COUNTERPARTY", value.externalId());
            Counterparty counterparty = new Counterparty();
            counterparty.setPortableId(portableUuid(value.externalId()));
            counterparty.setCompany(company);
            counterparty.setDisplayName(value.displayName());
            counterparty.setKind(value.kind());
            counterparty.setEmail(value.email());
            counterparty.setPhone(value.phone());
            counterparty.setNotes(value.notes());
            counterparty.setActive(value.active());
            em.persist(counterparty);
            result.put(value.externalId(), counterparty);
            afterBusinessWrite.accept(++writes);
        }
        return result;
    }

    private Map<String, Merchant> writeMerchants(
            EntityManager em,
            Company company,
            List<SclxTransactionDetailImportData.MerchantValue> values,
            Map<EntityKey, SclxImportEntityPreview> previews,
            int writesBefore)
    {
        Map<String, Merchant> result = new LinkedHashMap<>();
        int writes = writesBefore;
        for (SclxTransactionDetailImportData.MerchantValue value : values)
        {
            requireNew(previews, "MERCHANT", value.externalId());
            Merchant merchant = new Merchant();
            merchant.setPortableId(portableUuid(value.externalId()));
            merchant.setCompany(company);
            merchant.setName(value.name());
            merchant.setNotes(value.notes());
            merchant.setActive(value.active());
            em.persist(merchant);
            result.put(value.externalId(), merchant);
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
            Map<String, Activity> activities,
            Map<String, Counterparty> counterparties,
            Map<String, Merchant> merchants,
            SclxTransactionDetailImportData details,
            Map<EntityKey, SclxImportEntityPreview> previews,
            String actor,
            int writesBefore)
    {
        Map<String, Txn> transactions = new LinkedHashMap<>();
        Map<String, TxnSplit> lines = new LinkedHashMap<>();
        Map<String, TxnSupplementalLine> supplementalLines = new LinkedHashMap<>();
        Set<String> skippedLines = new HashSet<>();
        int writes = writesBefore;
        for (JsonNode value : values)
        {
            String transactionId = text(value, "transactionId");
            requireNew(previews, "TRANSACTION", transactionId);
            List<TransactionLineCommand> commands = new ArrayList<>();
            List<String> postingLineIds = new ArrayList<>();
            Set<String> counterpartyIds = new HashSet<>();
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
                String activityId = optionalText(line, "activityId");
                Activity activity = activityId == null ? null
                        : required(activities, activityId, "transaction activity");
                String counterpartyId = optionalText(line, "counterpartyId");
                if (counterpartyId != null)
                {
                    required(counterparties, counterpartyId, "transaction counterparty");
                    counterpartyIds.add(counterpartyId);
                }
                String merchantId = details.merchantForLine(lineId);
                Merchant merchant = merchantId == null ? null
                        : required(merchants, merchantId, "transaction merchant");
                commands.add(new TransactionLineCommand(
                        account.getId(), fund.getId(), null,
                        activity == null ? null : activity.getId(),
                        merchant == null ? null : merchant.getId(),
                        debit, credit, false, optionalText(line, "memo")));
                postingLineIds.add(lineId);
            }
            if (counterpartyIds.size() > 1)
            {
                throw new IllegalStateException(
                        "One canonical transaction cannot import more than one header counterparty: "
                                + transactionId + ".");
            }
            Counterparty counterparty = counterpartyIds.isEmpty()
                    ? null
                    : required(counterparties, counterpartyIds.iterator().next(), "transaction counterparty");
            List<SclxTransactionDetailImportData.SupplementalValue> sourceDetails =
                    details.supplementalForTransaction(transactionId);
            List<TransactionSupplementalLineCommand> supplementalCommands = sourceDetails.stream()
                    .map(detail -> new TransactionSupplementalLineCommand(
                            detail.kind(), detail.entryRef(), detail.counterparty(), detail.description(),
                            detail.reference(), detail.amount(), detail.dueDate(), detail.startDate(),
                            detail.endDate(), detail.notes(), detail.lineOrder()))
                    .toList();
            TransactionCommand command = new TransactionCommand(
                    LocalDate.parse(text(value, "transactionDate")),
                    counterparty == null ? null : counterparty.getId(),
                    text(value, "description"),
                    null,
                    commands,
                    supplementalCommands);
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
            List<TxnSupplementalLine> persistedDetails = em.createQuery(
                            "from TxnSupplementalLine s where s.txn = :txn order by s.id",
                            TxnSupplementalLine.class)
                    .setParameter("txn", transaction)
                    .getResultList();
            if (persistedDetails.size() != sourceDetails.size())
            {
                throw new IllegalStateException(
                        "Canonical supplemental-detail count changed during SCLX import.");
            }
            for (int index = 0; index < persistedDetails.size(); index++)
            {
                supplementalLines.put(sourceDetails.get(index).externalId(), persistedDetails.get(index));
            }
            afterBusinessWrite.accept(++writes);
        }
        return new TransactionWrite(
                transactions, lines, supplementalLines, skippedLines, transactions.size());
    }

    private FixedAssetWrite writeFixedAssets(
            EntityManager em,
            Company company,
            SclxFixedAssetImportData source,
            Map<String, Account> accounts,
            Map<String, Fund> funds,
            Map<String, Txn> transactions,
            Map<EntityKey, SclxImportEntityPreview> previews,
            int writesBefore)
    {
        int writes = writesBefore;
        Map<String, FixedAsset> assets = new LinkedHashMap<>();
        for (SclxFixedAssetImportData.AssetValue value : source.assets())
        {
            requireNew(previews, "FIXED_ASSET", value.externalId());
            Account assetAccount = required(accounts, value.assetAccountId(), "fixed-asset account");
            Account accumulatedAccount = required(
                    accounts, value.accumulatedDepreciationAccountId(), "accumulated-depreciation account");
            Account expenseAccount = required(
                    accounts, value.depreciationExpenseAccountId(), "depreciation-expense account");
            Fund fund = required(funds, value.fundId(), "fixed-asset fund");
            FixedAsset asset = fixedAssetService.createForImport(
                    em,
                    company,
                    new FixedAssetCommand(
                            company.getCode(),
                            assetAccount.getId(),
                            accumulatedAccount.getId(),
                            expenseAccount.getId(),
                            fund.getId(),
                            value.name(),
                            value.acquisitionDate(),
                            value.acquisitionCost(),
                            value.salvageValue(),
                            value.usefulLifeMonths(),
                            value.depreciationMethod(),
                            value.openingAccumulatedDepreciation(),
                            value.status(),
                            value.notes()),
                    portableUuid(value.externalId()),
                    value.createdAt(),
                    value.updatedAt());
            assets.put(value.externalId(), asset);
            afterBusinessWrite.accept(++writes);
        }
        em.flush();

        Map<String, FixedAssetDepreciationRun> runs = new LinkedHashMap<>();
        for (SclxFixedAssetImportData.RunValue value : source.runs())
        {
            requireNew(previews, "DEPRECIATION_RUN", value.externalId());
            FixedAsset asset = required(assets, value.assetId(), "depreciation-run fixed asset");
            Txn transaction = required(transactions, value.transactionId(), "depreciation-run transaction");
            FixedAssetDepreciationRun run = fixedAssetService.recordCompletedRunForImport(
                    em,
                    company,
                    asset,
                    value.runDate(),
                    value.amount(),
                    transaction,
                    value.notes(),
                    portableUuid(value.externalId()),
                    value.createdAt());
            runs.put(value.externalId(), run);
            afterBusinessWrite.accept(++writes);
        }
        return new FixedAssetWrite(assets, runs, assets.size() + runs.size());
    }

    private InventoryWrite writeInventory(
            EntityManager em,
            Company company,
            SclxInventoryImportData source,
            Map<String, Account> accounts,
            Map<String, Fund> funds,
            Map<String, Txn> transactions,
            Map<EntityKey, SclxImportEntityPreview> previews,
            int writesBefore)
    {
        int writes = writesBefore;
        Map<String, InventoryItem> items = new LinkedHashMap<>();
        for (SclxInventoryImportData.ItemValue value : source.items())
        {
            requireNew(previews, "INVENTORY_ITEM", value.externalId());
            Account account = required(accounts, value.inventoryAccountId(), "inventory account");
            Fund fund = required(funds, value.fundId(), "inventory fund");
            InventoryItem item = inventoryService.createForImport(
                    em,
                    company,
                    new InventoryItemCommand(
                            company.getCode(),
                            account.getId(),
                            fund.getId(),
                            value.name(),
                            value.itemType(),
                            value.quantity(),
                            value.unit(),
                            value.unitValue(),
                            value.acquisitionDate(),
                            value.custodian(),
                            value.storageLocation(),
                            value.condition(),
                            value.status(),
                            value.notes()),
                    portableUuid(value.externalId()),
                    value.createdAt(),
                    value.updatedAt());
            items.put(value.externalId(), item);
            afterBusinessWrite.accept(++writes);
        }
        em.flush();

        Map<String, InventoryMovement> movements = new LinkedHashMap<>();
        for (SclxInventoryImportData.MovementValue value : source.movements())
        {
            requireNew(previews, "INVENTORY_MOVEMENT", value.externalId());
            InventoryItem item = required(items, value.itemId(), "inventory movement item");
            Txn transaction = value.transactionId() == null
                    ? null
                    : required(transactions, value.transactionId(), "inventory movement transaction");
            InventoryMovement movement = inventoryService.recordMovementForImport(
                    em,
                    company,
                    item,
                    value.movementDate(),
                    value.movementType(),
                    value.quantityChange(),
                    value.resultingQuantity(),
                    value.unitValue(),
                    transaction,
                    value.notes(),
                    portableUuid(value.externalId()),
                    value.createdAt());
            movements.put(value.externalId(), movement);
            afterBusinessWrite.accept(++writes);
        }
        return new InventoryWrite(items, movements, items.size() + movements.size());
    }

    private BankingWrite writeBanking(
            EntityManager em,
            Company company,
            SclxBankingImportData source,
            Map<String, Account> accounts,
            TransactionWrite transactions,
            Map<EntityKey, SclxImportEntityPreview> previews,
            int writesBefore)
    {
        int writes = writesBefore;
        Map<String, Bank> banks = new LinkedHashMap<>();
        for (SclxBankingImportData.BankValue value : source.banks())
        {
            requireNew(previews, "BANK", value.externalId());
            Bank bank = bankConfigurationService.createBankForImport(
                    em,
                    company,
                    new BankCommand(
                            company.getCode(), value.name(), value.routingNumber(), value.address(),
                            value.website(), value.contactName(), value.contactPhone(), value.contactEmail(),
                            value.notes(), value.active()),
                    portableUuid(value.externalId()));
            banks.put(value.externalId(), bank);
            afterBusinessWrite.accept(++writes);
        }
        em.flush();

        Map<String, CompanyBankAccount> bankAccounts = new LinkedHashMap<>();
        for (SclxBankingImportData.BankAccountValue value : source.bankAccounts())
        {
            requireNew(previews, "BANK_ACCOUNT", value.externalId());
            Bank bank = value.bankId() == null ? null : required(banks, value.bankId(), "bank");
            Account account = value.ledgerAccountId() == null
                    ? null : required(accounts, value.ledgerAccountId(), "bank ledger account");
            CompanyBankAccount bankAccount = bankConfigurationService.createBankAccountForImport(
                    em,
                    company,
                    bank,
                    account,
                    new BankAccountImportCommand(
                            value.name(), value.nickname(), value.institutionName(), value.accountType(),
                            value.lastFour(), value.maskedAccountNumber(), value.openingDate(),
                            value.statementImportFormat(), value.ofxBankId(), value.ofxAccountId(),
                            value.openingBalance(), value.active(), value.notes()),
                    portableUuid(value.externalId()));
            bankAccounts.put(value.externalId(), bankAccount);
            afterBusinessWrite.accept(++writes);
        }
        em.flush();

        source.batches().forEach(value -> requireNew(previews, "BANK_IMPORT_BATCH", value.externalId()));
        source.statementLines().forEach(
                value -> requireNew(previews, "BANK_STATEMENT_LINE", value.externalId()));
        source.issues().forEach(value -> requireNew(previews, "BANK_IMPORT_ISSUE", value.externalId()));
        BankImportReviewService.ImportedFacts importedFacts = bankImportReviewService.importForInterchange(
                em,
                company,
                source.batches().stream().map(value -> new BankImportReviewService.BatchImport(
                        value.externalId(), portableUuid(value.externalId()), value.bankAccountId(),
                        value.sourceName(), value.sourceHash(), value.sourceFormat(),
                        value.sourceVariant(), value.sourceVersion(), value.sourceEncoding(),
                        value.sourceInstitutionId(), value.sourceBankId(), value.sourceAccountId(),
                        value.sourceAccountType(), value.currency(), value.statementStartDate(),
                        value.statementEndDate(), value.ledgerBalance(), value.availableBalance(),
                        value.accountMatchStatus(), value.accountIdentityConfirmed(), value.status(),
                        value.importedAt(), value.completedAt(), value.totalLineCount(),
                        value.acceptedLineCount(), value.rejectedLineCount(), value.issueCount(),
                        value.notes())).toList(),
                source.statementLines().stream().map(value -> new BankImportReviewService.StatementLineImport(
                        value.externalId(), portableUuid(value.externalId()), value.importBatchId(),
                        value.bankAccountId(), value.sourceRowNumber(), value.sourceTransactionId(),
                        value.deterministicFingerprint(), value.statementAccountIdentifier(),
                        value.transactionDate(), value.postedDate(), value.amount(), value.transactionType(),
                        value.name(), value.memo(), value.checkNumber(), value.reference(), value.currency(),
                        value.correctionAction(), value.correctedSourceTransactionId(), value.status(),
                        value.dispositionNote(), value.acceptedTransactionId(),
                        value.matchedTransactionId())).toList(),
                source.issues().stream().map(value -> new BankImportReviewService.IssueImport(
                        value.externalId(), portableUuid(value.externalId()), value.importBatchId(),
                        value.statementLineId(), value.sourceRowNumber(), value.severity(), value.code(),
                        value.message(), value.createdAt())).toList(),
                bankAccounts,
                transactions.transactions());
        if (!source.batches().isEmpty() || !source.statementLines().isEmpty() || !source.issues().isEmpty())
        {
            afterBusinessWrite.accept(++writes);
        }
        em.flush();

        for (SclxBankingImportData.ClearanceValue value : source.clearances())
        {
            TxnSplit line = required(
                    transactions.lines(), value.transactionLineId(), "cleared transaction line");
            BankStatementLine statementLine = value.statementLineId() == null
                    ? null
                    : required(importedFacts.lines(), value.statementLineId(), "cleared statement line");
            bankClearedStateService.applyForImport(em, company, line, statementLine, value.clearedOn());
            afterBusinessWrite.accept(++writes);
        }

        source.sessions().forEach(
                value -> requireNew(previews, "RECONCILIATION_SESSION", value.externalId()));
        source.matches().forEach(
                value -> requireNew(previews, "RECONCILIATION_MATCH", value.externalId()));
        BankReconciliationWorkspaceService.ImportedReconciliation reconciliations =
                bankReconciliationService.importForInterchange(
                        em,
                        company,
                        source.sessions().stream().map(value ->
                                new BankReconciliationWorkspaceService.SessionImport(
                                        value.externalId(), portableUuid(value.externalId()), value.bankAccountId(),
                                        value.statementStartDate(), value.statementEndDate(),
                                        value.statementEndingBalance(), value.mismatchPolicy(), value.status(),
                                        value.notes(), value.beginningBalance(), value.bookBalanceAll(),
                                        value.bookBalanceCleared(), value.differenceAmount(), value.createdAt(),
                                        value.updatedAt())).toList(),
                        source.matches().stream().map(value ->
                                new BankReconciliationWorkspaceService.MatchImport(
                                        value.externalId(), portableUuid(value.externalId()),
                                        value.reconciliationSessionId(), value.statementLineId(),
                                        value.transactionLineId(), value.matchStatus(), value.resolutionNote(),
                                        value.createdAt(), value.updatedAt())).toList(),
                        bankAccounts,
                        importedFacts.lines(),
                        transactions.lines());
        if (!source.sessions().isEmpty() || !source.matches().isEmpty())
        {
            afterBusinessWrite.accept(++writes);
        }
        return new BankingWrite(
                banks, bankAccounts, importedFacts.batches(), importedFacts.lines(), importedFacts.issues(),
                reconciliations.sessions(), reconciliations.matches(), writes - writesBefore);
    }

    private PeriodCloseWrite writePeriodClose(
            EntityManager em,
            Company company,
            SclxPeriodCloseImportData source,
            Map<EntityKey, SclxImportEntityPreview> previews,
            int writesBefore)
    {
        int writes = writesBefore;
        source.ranges().forEach(value -> requireNew(
                previews, "PERIOD_CLOSE_RANGE", value.externalId()));
        source.events().forEach(value -> requireNew(
                previews, "PERIOD_CLOSE_EVENT", value.externalId()));
        PeriodCloseRangeService.ImportedPeriodClose imported = periodCloseRangeService.importForInterchange(
                em,
                company,
                source.ranges().stream().map(value -> new PeriodCloseRangeService.RangeImport(
                        value.externalId(),
                        portableUuid(value.externalId()),
                        value.startDate(),
                        value.endDate(),
                        value.rangeKind(),
                        value.status(),
                        value.closedAt(),
                        value.closedBy(),
                        value.closeReason(),
                        value.reopenedAt(),
                        value.reopenedBy(),
                        value.reopenReason())).toList(),
                source.events().stream().map(value -> new PeriodCloseRangeService.EventImport(
                        value.externalId(),
                        portableUuid(value.externalId()),
                        value.rangeId(),
                        value.eventType(),
                        value.actor(),
                        value.reason(),
                        value.eventAt())).toList());
        for (int index = 0; index < source.ranges().size() + source.events().size(); index++)
        {
            afterBusinessWrite.accept(++writes);
        }
        return new PeriodCloseWrite(
                imported.ranges(), imported.events(), writes - writesBefore);
    }

    private AuditHistoryWrite writeAuditHistory(
            EntityManager em,
            Company company,
            SclxAuditHistoryImportData source,
            Map<EntityKey, SclxImportEntityPreview> previews,
            int writesBefore)
    {
        int writes = writesBefore;
        source.events().forEach(value -> requireNew(previews, "AUDIT_EVENT", value.externalId()));
        AuditHistoryService.ImportedAuditHistory imported = auditHistoryService.importForInterchange(
                em,
                company,
                source.events().stream().map(value -> new AuditHistoryService.AuditEventImport(
                        value.externalId(),
                        portableUuid(value.externalId()),
                        value.occurredAt(),
                        value.actor(),
                        value.actionType(),
                        value.entityType(),
                        value.entityId(),
                        value.summary(),
                        value.beforeValue(),
                        value.afterValue(),
                        value.reason())).toList());
        for (int index = 0; index < source.events().size(); index++)
        {
            afterBusinessWrite.accept(++writes);
        }
        return new AuditHistoryWrite(imported.events(), writes - writesBefore);
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
        for (Map.Entry<String, TxnSupplementalLine> entry : written.supplementalLines().entrySet())
        {
            recordIdentity(em, company, preview, previews, "SUPPLEMENTAL_DETAIL",
                    entry.getKey(), String.valueOf(entry.getValue().getId()));
        }
    }

    private void recordBudgetIdentities(
            EntityManager em,
            Company company,
            SclxImportPreview preview,
            Map<EntityKey, SclxImportEntityPreview> previews,
            BudgetWrite written)
    {
        for (Map.Entry<String, BudgetPlan> entry : written.plans().entrySet())
        {
            recordIdentity(em, company, preview, previews, "BUDGET",
                    entry.getKey(), String.valueOf(entry.getValue().getId()));
        }
        for (Map.Entry<String, BudgetLine> entry : written.lines().entrySet())
        {
            recordIdentity(em, company, preview, previews, "BUDGET_LINE",
                    entry.getKey(), String.valueOf(entry.getValue().getId()));
        }
    }

    private void recordFixedAssetIdentities(
            EntityManager em,
            Company company,
            SclxImportPreview preview,
            Map<EntityKey, SclxImportEntityPreview> previews,
            FixedAssetWrite written)
    {
        for (Map.Entry<String, FixedAsset> entry : written.assets().entrySet())
        {
            recordIdentity(em, company, preview, previews, "FIXED_ASSET",
                    entry.getKey(), String.valueOf(entry.getValue().getId()));
        }
        for (Map.Entry<String, FixedAssetDepreciationRun> entry : written.runs().entrySet())
        {
            recordIdentity(em, company, preview, previews, "DEPRECIATION_RUN",
                    entry.getKey(), String.valueOf(entry.getValue().getId()));
        }
    }

    private void recordInventoryIdentities(
            EntityManager em,
            Company company,
            SclxImportPreview preview,
            Map<EntityKey, SclxImportEntityPreview> previews,
            InventoryWrite written)
    {
        for (Map.Entry<String, InventoryItem> entry : written.items().entrySet())
        {
            recordIdentity(em, company, preview, previews, "INVENTORY_ITEM",
                    entry.getKey(), String.valueOf(entry.getValue().getId()));
        }
        for (Map.Entry<String, InventoryMovement> entry : written.movements().entrySet())
        {
            recordIdentity(em, company, preview, previews, "INVENTORY_MOVEMENT",
                    entry.getKey(), String.valueOf(entry.getValue().getId()));
        }
    }

    private void recordBankingIdentities(
            EntityManager em,
            Company company,
            SclxImportPreview preview,
            Map<EntityKey, SclxImportEntityPreview> previews,
            BankingWrite written)
    {
        written.banks().forEach((identity, value) -> recordIdentity(
                em, company, preview, previews, "BANK", identity, String.valueOf(value.getId())));
        written.bankAccounts().forEach((identity, value) -> recordIdentity(
                em, company, preview, previews, "BANK_ACCOUNT", identity, String.valueOf(value.getId())));
        written.batches().forEach((identity, value) -> recordIdentity(
                em, company, preview, previews, "BANK_IMPORT_BATCH", identity, String.valueOf(value.getId())));
        written.statementLines().forEach((identity, value) -> recordIdentity(
                em, company, preview, previews, "BANK_STATEMENT_LINE", identity, String.valueOf(value.getId())));
        written.issues().forEach((identity, value) -> recordIdentity(
                em, company, preview, previews, "BANK_IMPORT_ISSUE", identity, String.valueOf(value.getId())));
        written.reconciliationSessions().forEach((identity, value) -> recordIdentity(
                em, company, preview, previews, "RECONCILIATION_SESSION", identity, String.valueOf(value)));
        written.reconciliationMatches().forEach((identity, value) -> recordIdentity(
                em, company, preview, previews, "RECONCILIATION_MATCH", identity, String.valueOf(value)));
    }

    private void recordPeriodCloseIdentities(
            EntityManager em,
            Company company,
            SclxImportPreview preview,
            Map<EntityKey, SclxImportEntityPreview> previews,
            PeriodCloseWrite written)
    {
        written.ranges().forEach((identity, value) -> recordIdentity(
                em, company, preview, previews, "PERIOD_CLOSE_RANGE", identity, value.toString()));
        written.events().forEach((identity, value) -> recordIdentity(
                em, company, preview, previews, "PERIOD_CLOSE_EVENT", identity, value.toString()));
    }

    private void recordAuditHistoryIdentities(
            EntityManager em,
            Company company,
            SclxImportPreview preview,
            Map<EntityKey, SclxImportEntityPreview> previews,
            AuditHistoryWrite written)
    {
        written.events().forEach((identity, value) -> recordIdentity(
                em, company, preview, previews, "AUDIT_EVENT", identity, String.valueOf(value.getId())));
    }

    private void recordEntityIdentities(
            EntityManager em,
            Company company,
            SclxImportPreview preview,
            Map<EntityKey, SclxImportEntityPreview> previews,
            Map<String, ?> entities,
            String type)
    {
        for (Map.Entry<String, ?> entry : entities.entrySet())
        {
            Object entity = entry.getValue();
            Long localId;
            if (entity instanceof Activity activity)
            {
                localId = activity.getId();
            }
            else if (entity instanceof Counterparty counterparty)
            {
                localId = counterparty.getId();
            }
            else if (entity instanceof Merchant merchant)
            {
                localId = merchant.getId();
            }
            else
            {
                throw new IllegalStateException(
                        "Unsupported SCLX transaction-detail identity entity: " + entity.getClass());
            }
            recordIdentity(em, company, preview, previews, type, entry.getKey(), String.valueOf(localId));
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
            Map<String, TxnSupplementalLine> supplementalLines,
            Set<String> skippedLines,
            int transactionCount)
    {
        private TransactionWrite
        {
            transactions = Map.copyOf(transactions);
            lines = Map.copyOf(lines);
            supplementalLines = Map.copyOf(supplementalLines);
            skippedLines = Set.copyOf(skippedLines);
        }
    }

    private record BudgetWrite(
            Map<String, BudgetPlan> plans,
            Map<String, BudgetLine> lines,
            int businessWriteCount)
    {
        private BudgetWrite
        {
            plans = Map.copyOf(plans);
            lines = Map.copyOf(lines);
        }
    }

    private record FixedAssetWrite(
            Map<String, FixedAsset> assets,
            Map<String, FixedAssetDepreciationRun> runs,
            int businessWriteCount)
    {
        private FixedAssetWrite
        {
            assets = Map.copyOf(assets);
            runs = Map.copyOf(runs);
        }
    }

    private record InventoryWrite(
            Map<String, InventoryItem> items,
            Map<String, InventoryMovement> movements,
            int businessWriteCount)
    {
        private InventoryWrite
        {
            items = Map.copyOf(items);
            movements = Map.copyOf(movements);
        }
    }

    private record BankingWrite(
            Map<String, Bank> banks,
            Map<String, CompanyBankAccount> bankAccounts,
            Map<String, BankImportBatch> batches,
            Map<String, BankStatementLine> statementLines,
            Map<String, ImportIssue> issues,
            Map<String, Long> reconciliationSessions,
            Map<String, Long> reconciliationMatches,
            int businessWriteCount)
    {
        private BankingWrite
        {
            banks = Map.copyOf(banks);
            bankAccounts = Map.copyOf(bankAccounts);
            batches = Map.copyOf(batches);
            statementLines = Map.copyOf(statementLines);
            issues = Map.copyOf(issues);
            reconciliationSessions = Map.copyOf(reconciliationSessions);
            reconciliationMatches = Map.copyOf(reconciliationMatches);
        }
    }

    private record PeriodCloseWrite(
            Map<String, UUID> ranges,
            Map<String, UUID> events,
            int businessWriteCount)
    {
        private PeriodCloseWrite
        {
            ranges = Map.copyOf(ranges);
            events = Map.copyOf(events);
        }
    }

    private record AuditHistoryWrite(
            Map<String, AuditEvent> events,
            int businessWriteCount)
    {
        private AuditHistoryWrite
        {
            events = Map.copyOf(events);
        }
    }
}
