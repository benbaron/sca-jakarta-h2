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
 * history and correction relationships into the selected company. Existing
 * unrelated target history is preserved; identical identities are reused,
 * new identities are added, and explicitly resolved safe master conflicts are
 * applied under the same transaction.</p>
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
        this.auditHistoryService = new AuditHistoryService(jpa, companyCodeSupplier);
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
        return commit(source, approvedPreview, actor, false);
    }

    public SclxImportResult commit(
            Path source,
            SclxImportPreview approvedPreview,
            String actor,
            boolean approvedMappings)
    {
        return commit(source, approvedPreview, actor, approvedMappings, false);
    }

    public SclxImportResult commit(
            Path source,
            SclxImportPreview approvedPreview,
            String actor,
            boolean approvedMappings,
            boolean approvedExistingCompanyImport)
    {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(approvedPreview, "approvedPreview");
        List<SclxImportMappingSelection> approvedSelections = approvedPreview.mappings().stream()
                .filter(value -> value.resolution() == SclxImportMappingRequirement.Resolution.MAPPED)
                .map(value -> new SclxImportMappingSelection(
                        value.kind(), value.sourceId(), value.targetCode()))
                .toList();
        List<SclxImportConflictSelection> approvedConflictSelections = approvedPreview.operation().items().stream()
                .filter(value -> value.conflictChoice() != null)
                .map(value -> new SclxImportConflictSelection(
                        value.entityType(), value.externalId(), value.conflictChoice()))
                .toList();
        SclxImportPreview current = previewService.preview(
                source, approvedSelections, approvedConflictSelections);
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
        if (!approvedPreview.mappings().equals(current.mappings()))
        {
            throw new IllegalStateException("The SCLX account or fund mappings changed after preview.");
        }
        if (!approvedPreview.operation().items().equals(current.operation().items()))
        {
            throw new IllegalStateException(
                    "The SCLX identity or conflict choices changed after preview.");
        }
        if (current.recommendedAccountMode() == SclxAccountMode.MAPPED && !approvedMappings)
        {
            throw new IllegalStateException(
                    "Approve the displayed SCLX account and fund mappings before importing.");
        }
        boolean existingCompanyMerge = current.targetPopulated()
                && (current.operation().counts().created() > 0L
                || current.operation().counts().updated() > 0L);
        if (existingCompanyMerge && !approvedExistingCompanyImport)
        {
            throw new IllegalStateException(
                    "Approve the nondestructive SCLX import into the existing company before importing.");
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
                int writes = 0;
                if (!current.targetPopulated())
                {
                    applyOrganization(company, root.path("organization"));
                    afterBusinessWrite.accept(++writes);
                }

                ChartOfAccounts chart = targetChart(em, company, root, current.targetPopulated());
                Map<String, Account> accounts = writeAccounts(
                        em, chart, root.path("chartOfAccounts"), current.mappings(), previews, writes);
                writes += newMasterCount(current.mappings(), SclxImportMappingRequirement.Kind.ACCOUNT);
                Map<String, Fund> funds = writeFunds(
                        em, company, root.path("funds"), current.mappings(), previews, writes);
                writes += newMasterCount(current.mappings(), SclxImportMappingRequirement.Kind.FUND);
                BudgetWrite writtenBudgets = writeBudgets(
                        em, company, budgets, funds, previews, writes);
                writes += writtenBudgets.businessWriteCount();
                Map<String, Activity> activities = writeActivities(
                        em, company, details.activities(), previews, writes);
                writes += newEntityCount(previews, "ACTIVITY", details.activities().stream()
                        .map(SclxTransactionDetailImportData.ActivityValue::externalId).toList());
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
                        + ",created=" + actualCreatedCount(current)
                        + ",mapped=" + current.operation().counts().updated()
                        + ",accountMode=" + current.recommendedAccountMode()
                        + ",mappings=" + mappingAudit(current.mappings())
                        + ",targetReuses=" + targetReuseAudit(current));
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
        int restored = 0;
        for (SclxCorrectionImportData.CorrectionValue value : source.relationships())
        {
            Txn correction = required(transactions, value.transactionId(), "correction transaction");
            Txn corrected = required(
                    transactions, value.correctedTransactionId(), "corrected transaction");
            Txn current = "REVERSAL".equals(value.correctionType())
                    ? correction.getReversalOf() : correction.getReplacementFor();
            if (current != null && current.getId().equals(corrected.getId()))
            {
                continue;
            }
            transactionCorrectionService.restoreRelationshipForImport(
                    em, company, correction, value.correctionType(), corrected);
            afterBusinessWrite.accept(++writes);
            restored++;
        }
        return restored;
    }

    private static void applyOrganization(Company company, JsonNode organization)
    {
        company.setDisplayName(text(organization, "name"));
        String currency = optionalText(organization, "baseCurrency");
        if (currency != null)
        {
            company.setDefaultCurrency(currency.toUpperCase(Locale.ROOT));
        }
        String fiscalYearStart = optionalText(organization, "fiscalYearStart");
        if (fiscalYearStart != null)
        {
            LocalDate fiscalStart = LocalDate.parse(fiscalYearStart);
            company.setFiscalYearStartMonth(fiscalStart.getMonthValue());
            company.setFiscalYearStartDay(fiscalStart.getDayOfMonth());
        }
        company.touchUpdatedAt();
    }

    private ChartOfAccounts targetChart(
            EntityManager em,
            Company company,
            JsonNode root,
            boolean preserveExisting)
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
        if (!preserveExisting)
        {
            chart.setName(chartName == null ? company.getDisplayName() + " Chart of Accounts" : chartName);
            chart.setVersion(chartVersion == null
                    ? "SCLX-" + SclxVersion.writerVersion().externalValue()
                    : chartVersion);
            chart.setStatus(ChartStatus.ACTIVE);
            chart.touchUpdatedAt();
        }
        return chart;
    }

    private Map<String, Account> writeAccounts(
            EntityManager em,
            ChartOfAccounts chart,
            JsonNode values,
            List<SclxImportMappingRequirement> mappings,
            Map<EntityKey, SclxImportEntityPreview> previews,
            int writesBefore)
    {
        List<JsonNode> ordered = parentFirst(values, "accountId", "parentAccountId", "account");
        Map<String, Account> result = new LinkedHashMap<>();
        int writes = writesBefore;
        for (JsonNode value : ordered)
        {
            String externalId = text(value, "accountId");
            SclxImportMappingRequirement mapping = requiredMapping(
                    mappings, SclxImportMappingRequirement.Kind.ACCOUNT, externalId);
            Account existing = findAccount(em, chart, mapping.targetCode());
            if ((mapping.resolution() == SclxImportMappingRequirement.Resolution.AS_IS
                    || mapping.resolution() == SclxImportMappingRequirement.Resolution.MAPPED)
                    && existing != null)
            {
                requireCompatibleMappedAccount(value, mapping, existing);
                result.put(externalId, existing);
                continue;
            }
            if (mapping.resolution() != SclxImportMappingRequirement.Resolution.CREATE)
            {
                throw new IllegalStateException(
                        "SCLX account mapping is not resolved: " + mapping.sourceCode() + ".");
            }
            requireNew(previews, "ACCOUNT", externalId);
            if (existing != null)
            {
                throw new IllegalStateException(
                        "SCLX account mapping no longer matches the target chart: " + mapping.sourceCode() + ".");
            }
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
            List<SclxImportMappingRequirement> mappings,
            Map<EntityKey, SclxImportEntityPreview> previews,
            int writesBefore)
    {
        List<JsonNode> ordered = parentFirst(values, "fundId", "parentFundId", "fund");
        Map<String, Fund> result = new LinkedHashMap<>();
        int writes = writesBefore;
        for (JsonNode value : ordered)
        {
            String externalId = text(value, "fundId");
            SclxImportMappingRequirement mapping = requiredMapping(
                    mappings, SclxImportMappingRequirement.Kind.FUND, externalId);
            Fund existing = findFund(em, company, mapping.targetCode());
            if ((mapping.resolution() == SclxImportMappingRequirement.Resolution.AS_IS
                    || mapping.resolution() == SclxImportMappingRequirement.Resolution.MAPPED)
                    && existing != null)
            {
                requireCompatibleMappedFund(value, mapping, existing);
                result.put(externalId, existing);
                continue;
            }
            if (mapping.resolution() != SclxImportMappingRequirement.Resolution.CREATE)
            {
                throw new IllegalStateException(
                        "SCLX fund mapping is not resolved: " + mapping.sourceCode() + ".");
            }
            requireNew(previews, "FUND", externalId);
            if (existing != null)
            {
                throw new IllegalStateException(
                        "SCLX fund mapping no longer matches the target company: " + mapping.sourceCode() + ".");
            }
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
            SclxImportEntityPreview preview = required(
                    previews, new EntityKey("ACTIVITY", value.externalId()), "preview identity");
            if (reuseExisting(preview))
            {
                Activity existing = localEntity(em, preview, Activity.class);
                ownership.requireOwnedBy(company, existing, "SCLX Activity");
                if (preview.conflictChoice() == SclxImportConflictChoice.TAKE_SOURCE)
                {
                    existing.setCode(value.code());
                    existing.setName(value.name());
                    existing.setActive(value.active());
                    afterBusinessWrite.accept(++writes);
                }
                result.put(value.externalId(), existing);
                continue;
            }
            if (preview.localEntityId() != null)
            {
                Activity existing = em.find(Activity.class, Long.valueOf(preview.localEntityId()));
                if (existing == null || existing.getCompany() == null
                        || !existing.getCompany().getId().equals(company.getId())
                        || !existing.getCode().equals(value.code())
                        || !existing.getName().equals(value.name())
                        || existing.isActive() != value.active())
                {
                    throw new IllegalStateException(
                            "The compatible target Activity changed after preview: " + value.code() + ".");
                }
                result.put(value.externalId(), existing);
                continue;
            }
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
        int businessWrites = 0;
        Map<String, BudgetCategory> categories = new LinkedHashMap<>();
        for (String code : categoryCodes.stream().sorted().toList())
        {
            BudgetCategory category = em.createQuery(
                            "from BudgetCategory c where c.company = :company and c.code = :code",
                            BudgetCategory.class)
                    .setParameter("company", company)
                    .setParameter("code", code)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
            if (category == null)
            {
                category = budgetCategoryAdminService.createForImport(em, company, code);
                afterBusinessWrite.accept(++writes);
                businessWrites++;
            }
            categories.put(code, category);
        }
        em.flush();

        Map<String, BudgetPlan> plans = new LinkedHashMap<>();
        Map<String, BudgetLine> lines = new LinkedHashMap<>();
        for (SclxBudgetImportData.BudgetValue budget : source.budgets())
        {
            SclxImportEntityPreview budgetPreview = required(
                    previews, new EntityKey("BUDGET", budget.externalId()), "preview identity");
            if (reuseExisting(budgetPreview))
            {
                BudgetPlan existing = localEntity(em, budgetPreview, BudgetPlan.class);
                ownership.requireOwnedBy(company, existing, "SCLX budget");
                plans.put(budget.externalId(), existing);
                for (SclxBudgetImportData.LineValue line : budget.lines())
                {
                    SclxImportEntityPreview linePreview = required(
                            previews, new EntityKey("BUDGET_LINE", line.externalId()), "preview identity");
                    if (reuseExisting(linePreview))
                    {
                        BudgetLine existingLine = localEntity(em, linePreview, BudgetLine.class);
                        if (!existingLine.getBudgetPlan().getId().equals(existing.getId()))
                        {
                            throw new IllegalStateException(
                                    "SCLX budget line no longer belongs to its previewed budget.");
                        }
                        lines.put(line.externalId(), existingLine);
                        continue;
                    }
                    requireNew(previews, "BUDGET_LINE", line.externalId());
                    BudgetLine added = new BudgetLine();
                    added.setBudgetPlan(existing);
                    added.setBudgetCategory(required(categories, line.categoryCode(), "budget category"));
                    added.setFund(line.fundId() == null ? null
                            : required(funds, line.fundId(), "budget fund"));
                    added.setPeriodMonth(line.periodMonth());
                    added.setAmount(line.amount());
                    added.setNotes("");
                    em.persist(added);
                    lines.put(line.externalId(), added);
                    afterBusinessWrite.accept(++writes);
                    businessWrites++;
                }
                continue;
            }
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
            businessWrites++;
            for (int index = 0; index < persistedLines.size(); index++)
            {
                afterBusinessWrite.accept(++writes);
                businessWrites++;
            }
        }
        return new BudgetWrite(
                plans,
                lines,
                businessWrites);
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
            SclxImportEntityPreview preview = required(
                    previews, new EntityKey("COUNTERPARTY", value.externalId()), "preview identity");
            if (reuseExisting(preview))
            {
                Counterparty existing = localEntity(em, preview, Counterparty.class);
                ownership.requireOwnedBy(company, existing, "SCLX counterparty");
                if (preview.conflictChoice() == SclxImportConflictChoice.TAKE_SOURCE)
                {
                    existing.setDisplayName(value.displayName());
                    existing.setKind(value.kind());
                    existing.setEmail(value.email());
                    existing.setPhone(value.phone());
                    existing.setNotes(value.notes());
                    existing.setActive(value.active());
                    afterBusinessWrite.accept(++writes);
                }
                result.put(value.externalId(), existing);
                continue;
            }
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
            SclxImportEntityPreview preview = required(
                    previews, new EntityKey("MERCHANT", value.externalId()), "preview identity");
            if (reuseExisting(preview))
            {
                Merchant existing = localEntity(em, preview, Merchant.class);
                ownership.requireOwnedBy(company, existing, "SCLX merchant");
                if (preview.conflictChoice() == SclxImportConflictChoice.TAKE_SOURCE)
                {
                    existing.setName(value.name());
                    existing.setNotes(value.notes());
                    existing.setActive(value.active());
                    afterBusinessWrite.accept(++writes);
                }
                result.put(value.externalId(), existing);
                continue;
            }
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
            SclxImportEntityPreview transactionPreview = required(
                    previews, new EntityKey("TRANSACTION", transactionId), "preview identity");
            if (reuseExisting(transactionPreview))
            {
                Txn existing = localEntity(em, transactionPreview, Txn.class);
                ownership.requireOwnedBy(company, existing, "SCLX transaction");
                transactions.put(transactionId, existing);
                for (JsonNode line : value.path("lines"))
                {
                    String lineId = text(line, "lineId");
                    SclxImportEntityPreview linePreview = required(
                            previews, new EntityKey("TRANSACTION_LINE", lineId), "preview identity");
                    if (!reuseExisting(linePreview))
                    {
                        throw new IllegalStateException("A retained SCLX transaction cannot import a replacement line: "
                                + lineId + ".");
                    }
                    if (linePreview.localEntityId() == null)
                    {
                        skippedLines.add(lineId);
                        continue;
                    }
                    TxnSplit existingLine = localEntity(em, linePreview, TxnSplit.class);
                    if (!existingLine.getTxn().getId().equals(existing.getId()))
                    {
                        throw new IllegalStateException(
                                "SCLX transaction line no longer belongs to its previewed transaction.");
                    }
                    lines.put(lineId, existingLine);
                }
                for (SclxTransactionDetailImportData.SupplementalValue detail
                        : details.supplementalForTransaction(transactionId))
                {
                    SclxImportEntityPreview detailPreview = required(
                            previews, new EntityKey("SUPPLEMENTAL_DETAIL", detail.externalId()),
                            "preview identity");
                    if (!reuseExisting(detailPreview))
                    {
                        throw new IllegalStateException(
                                "A retained SCLX transaction cannot import a replacement supplemental detail: "
                                        + detail.externalId() + ".");
                    }
                    TxnSupplementalLine existingDetail = localEntity(
                            em, detailPreview, TxnSupplementalLine.class);
                    if (!existingDetail.getTxn().getId().equals(existing.getId()))
                    {
                        throw new IllegalStateException(
                                "SCLX supplemental detail no longer belongs to its previewed transaction.");
                    }
                    supplementalLines.put(detail.externalId(), existingDetail);
                }
                continue;
            }
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
        int businessWrites = 0;
        Map<String, FixedAsset> assets = new LinkedHashMap<>();
        for (SclxFixedAssetImportData.AssetValue value : source.assets())
        {
            SclxImportEntityPreview assetPreview = required(
                    previews, new EntityKey("FIXED_ASSET", value.externalId()), "preview identity");
            if (reuseExisting(assetPreview))
            {
                FixedAsset existing = localEntity(em, assetPreview, FixedAsset.class);
                ownership.requireOwnedBy(company, existing, "SCLX fixed asset");
                assets.put(value.externalId(), existing);
                continue;
            }
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
            businessWrites++;
        }
        em.flush();

        Map<String, FixedAssetDepreciationRun> runs = new LinkedHashMap<>();
        for (SclxFixedAssetImportData.RunValue value : source.runs())
        {
            SclxImportEntityPreview runPreview = required(
                    previews, new EntityKey("DEPRECIATION_RUN", value.externalId()), "preview identity");
            if (reuseExisting(runPreview))
            {
                FixedAssetDepreciationRun existing = localEntity(
                        em, runPreview, FixedAssetDepreciationRun.class);
                FixedAsset expectedAsset = required(assets, value.assetId(), "depreciation-run fixed asset");
                if (!existing.getFixedAsset().getId().equals(expectedAsset.getId()))
                {
                    throw new IllegalStateException(
                            "SCLX depreciation run no longer belongs to its previewed fixed asset.");
                }
                runs.put(value.externalId(), existing);
                continue;
            }
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
            businessWrites++;
        }
        return new FixedAssetWrite(assets, runs, businessWrites);
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
        int businessWrites = 0;
        Map<String, InventoryItem> items = new LinkedHashMap<>();
        for (SclxInventoryImportData.ItemValue value : source.items())
        {
            SclxImportEntityPreview itemPreview = required(
                    previews, new EntityKey("INVENTORY_ITEM", value.externalId()), "preview identity");
            if (reuseExisting(itemPreview))
            {
                InventoryItem existing = localEntity(em, itemPreview, InventoryItem.class);
                ownership.requireOwnedBy(company, existing, "SCLX inventory item");
                items.put(value.externalId(), existing);
                continue;
            }
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
            businessWrites++;
        }
        em.flush();

        Map<String, InventoryMovement> movements = new LinkedHashMap<>();
        for (SclxInventoryImportData.MovementValue value : source.movements())
        {
            SclxImportEntityPreview movementPreview = required(
                    previews, new EntityKey("INVENTORY_MOVEMENT", value.externalId()), "preview identity");
            if (reuseExisting(movementPreview))
            {
                InventoryMovement existing = localEntity(em, movementPreview, InventoryMovement.class);
                InventoryItem expectedItem = required(items, value.itemId(), "inventory movement item");
                if (!existing.getInventoryItem().getId().equals(expectedItem.getId()))
                {
                    throw new IllegalStateException(
                            "SCLX inventory movement no longer belongs to its previewed item.");
                }
                movements.put(value.externalId(), existing);
                continue;
            }
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
            businessWrites++;
        }
        return new InventoryWrite(items, movements, businessWrites);
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
            SclxImportEntityPreview bankPreview = required(
                    previews, new EntityKey("BANK", value.externalId()), "preview identity");
            if (reuseExisting(bankPreview))
            {
                Bank existing = localEntity(em, bankPreview, Bank.class);
                ownership.requireOwnedBy(company, existing.getCompany(), "SCLX bank");
                banks.put(value.externalId(), existing);
                continue;
            }
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
            SclxImportEntityPreview accountPreview = required(
                    previews, new EntityKey("BANK_ACCOUNT", value.externalId()), "preview identity");
            if (reuseExisting(accountPreview))
            {
                CompanyBankAccount existing = localEntity(em, accountPreview, CompanyBankAccount.class);
                ownership.requireOwnedBy(company, existing.getCompany(), "SCLX bank account");
                bankAccounts.put(value.externalId(), existing);
                continue;
            }
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

        Map<String, BankImportBatch> existingBatches = new LinkedHashMap<>();
        source.batches().stream().filter(value -> reuseExisting(required(
                        previews, new EntityKey("BANK_IMPORT_BATCH", value.externalId()), "preview identity")))
                .forEach(value -> existingBatches.put(value.externalId(), localEntity(
                        em, required(previews, new EntityKey("BANK_IMPORT_BATCH", value.externalId()),
                                "preview identity"), BankImportBatch.class)));
        Map<String, BankStatementLine> existingStatementLines = new LinkedHashMap<>();
        source.statementLines().stream().filter(value -> reuseExisting(required(
                        previews, new EntityKey("BANK_STATEMENT_LINE", value.externalId()), "preview identity")))
                .forEach(value -> existingStatementLines.put(value.externalId(), localEntity(
                        em, required(previews, new EntityKey("BANK_STATEMENT_LINE", value.externalId()),
                                "preview identity"), BankStatementLine.class)));
        Map<String, ImportIssue> existingIssues = new LinkedHashMap<>();
        source.issues().stream().filter(value -> reuseExisting(required(
                        previews, new EntityKey("BANK_IMPORT_ISSUE", value.externalId()), "preview identity")))
                .forEach(value -> existingIssues.put(value.externalId(), localEntity(
                        em, required(previews, new EntityKey("BANK_IMPORT_ISSUE", value.externalId()),
                                "preview identity"), ImportIssue.class)));
        List<SclxBankingImportData.BatchValue> newBatches = source.batches().stream()
                .filter(value -> !reuseExisting(required(previews,
                        new EntityKey("BANK_IMPORT_BATCH", value.externalId()), "preview identity")))
                .peek(value -> requireNew(previews, "BANK_IMPORT_BATCH", value.externalId()))
                .toList();
        List<SclxBankingImportData.StatementLineValue> newStatementLines = source.statementLines().stream()
                .filter(value -> !reuseExisting(required(previews,
                        new EntityKey("BANK_STATEMENT_LINE", value.externalId()), "preview identity")))
                .peek(value -> requireNew(previews, "BANK_STATEMENT_LINE", value.externalId()))
                .toList();
        List<SclxBankingImportData.IssueValue> newIssues = source.issues().stream()
                .filter(value -> !reuseExisting(required(previews,
                        new EntityKey("BANK_IMPORT_ISSUE", value.externalId()), "preview identity")))
                .peek(value -> requireNew(previews, "BANK_IMPORT_ISSUE", value.externalId()))
                .toList();
        BankImportReviewService.ImportedFacts importedFacts = bankImportReviewService.importForInterchange(
                em,
                company,
                newBatches.stream().map(value -> new BankImportReviewService.BatchImport(
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
                newStatementLines.stream().map(value -> new BankImportReviewService.StatementLineImport(
                        value.externalId(), portableUuid(value.externalId()), value.importBatchId(),
                        value.bankAccountId(), value.sourceRowNumber(), value.sourceTransactionId(),
                        value.deterministicFingerprint(), value.statementAccountIdentifier(),
                        value.transactionDate(), value.postedDate(), value.amount(), value.transactionType(),
                        value.name(), value.memo(), value.checkNumber(), value.reference(), value.currency(),
                        value.correctionAction(), value.correctedSourceTransactionId(), value.status(),
                        value.dispositionNote(), value.acceptedTransactionId(),
                        value.matchedTransactionId())).toList(),
                newIssues.stream().map(value -> new BankImportReviewService.IssueImport(
                        value.externalId(), portableUuid(value.externalId()), value.importBatchId(),
                        value.statementLineId(), value.sourceRowNumber(), value.severity(), value.code(),
                        value.message(), value.createdAt())).toList(),
                bankAccounts,
                transactions.transactions(),
                existingBatches,
                existingStatementLines,
                existingIssues);
        if (!newBatches.isEmpty() || !newStatementLines.isEmpty() || !newIssues.isEmpty())
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

        Map<String, Long> existingSessions = new LinkedHashMap<>();
        source.sessions().stream().filter(value -> reuseExisting(required(
                        previews, new EntityKey("RECONCILIATION_SESSION", value.externalId()), "preview identity")))
                .forEach(value -> existingSessions.put(value.externalId(), localLongId(required(
                        previews, new EntityKey("RECONCILIATION_SESSION", value.externalId()),
                        "preview identity"))));
        Map<String, Long> existingMatches = new LinkedHashMap<>();
        source.matches().stream().filter(value -> reuseExisting(required(
                        previews, new EntityKey("RECONCILIATION_MATCH", value.externalId()), "preview identity")))
                .forEach(value -> existingMatches.put(value.externalId(), localLongId(required(
                        previews, new EntityKey("RECONCILIATION_MATCH", value.externalId()),
                        "preview identity"))));
        List<SclxBankingImportData.SessionValue> newSessions = source.sessions().stream()
                .filter(value -> !reuseExisting(required(previews,
                        new EntityKey("RECONCILIATION_SESSION", value.externalId()), "preview identity")))
                .peek(value -> requireNew(previews, "RECONCILIATION_SESSION", value.externalId()))
                .toList();
        List<SclxBankingImportData.MatchValue> newMatches = source.matches().stream()
                .filter(value -> !reuseExisting(required(previews,
                        new EntityKey("RECONCILIATION_MATCH", value.externalId()), "preview identity")))
                .peek(value -> requireNew(previews, "RECONCILIATION_MATCH", value.externalId()))
                .toList();
        BankReconciliationWorkspaceService.ImportedReconciliation reconciliations =
                bankReconciliationService.importForInterchange(
                        em,
                        company,
                        newSessions.stream().map(value ->
                                new BankReconciliationWorkspaceService.SessionImport(
                                        value.externalId(), portableUuid(value.externalId()), value.bankAccountId(),
                                        value.statementStartDate(), value.statementEndDate(),
                                        value.statementEndingBalance(), value.mismatchPolicy(), value.status(),
                                        value.notes(), value.beginningBalance(), value.bookBalanceAll(),
                                        value.bookBalanceCleared(), value.differenceAmount(), value.createdAt(),
                                        value.updatedAt())).toList(),
                        newMatches.stream().map(value ->
                                new BankReconciliationWorkspaceService.MatchImport(
                                        value.externalId(), portableUuid(value.externalId()),
                                        value.reconciliationSessionId(), value.statementLineId(),
                                        value.transactionLineId(), value.matchStatus(), value.resolutionNote(),
                                        value.createdAt(), value.updatedAt())).toList(),
                        bankAccounts,
                        importedFacts.lines(),
                        transactions.lines(),
                        existingSessions,
                        existingMatches);
        if (!newSessions.isEmpty() || !newMatches.isEmpty())
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
        Map<String, UUID> existingRanges = new LinkedHashMap<>();
        source.ranges().stream().filter(value -> reuseExisting(required(
                        previews, new EntityKey("PERIOD_CLOSE_RANGE", value.externalId()), "preview identity")))
                .forEach(value -> existingRanges.put(value.externalId(), localUuid(required(
                        previews, new EntityKey("PERIOD_CLOSE_RANGE", value.externalId()),
                        "preview identity"))));
        Map<String, UUID> existingEvents = new LinkedHashMap<>();
        source.events().stream().filter(value -> reuseExisting(required(
                        previews, new EntityKey("PERIOD_CLOSE_EVENT", value.externalId()), "preview identity")))
                .forEach(value -> existingEvents.put(value.externalId(), localUuid(required(
                        previews, new EntityKey("PERIOD_CLOSE_EVENT", value.externalId()),
                        "preview identity"))));
        List<SclxPeriodCloseImportData.RangeValue> newRanges = source.ranges().stream()
                .filter(value -> !reuseExisting(required(previews,
                        new EntityKey("PERIOD_CLOSE_RANGE", value.externalId()), "preview identity")))
                .peek(value -> requireNew(previews, "PERIOD_CLOSE_RANGE", value.externalId()))
                .toList();
        List<SclxPeriodCloseImportData.EventValue> newEvents = source.events().stream()
                .filter(value -> !reuseExisting(required(previews,
                        new EntityKey("PERIOD_CLOSE_EVENT", value.externalId()), "preview identity")))
                .peek(value -> requireNew(previews, "PERIOD_CLOSE_EVENT", value.externalId()))
                .toList();
        PeriodCloseRangeService.ImportedPeriodClose imported = periodCloseRangeService.importForInterchange(
                em,
                company,
                newRanges.stream().map(value -> new PeriodCloseRangeService.RangeImport(
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
                newEvents.stream().map(value -> new PeriodCloseRangeService.EventImport(
                        value.externalId(),
                        portableUuid(value.externalId()),
                        value.rangeId(),
                        value.eventType(),
                        value.actor(),
                        value.reason(),
                        value.eventAt())).toList(),
                existingRanges,
                existingEvents);
        for (int index = 0; index < newRanges.size() + newEvents.size(); index++)
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
        Map<String, AuditEvent> existingEvents = new LinkedHashMap<>();
        source.events().stream().filter(value -> reuseExisting(required(
                        previews, new EntityKey("AUDIT_EVENT", value.externalId()), "preview identity")))
                .forEach(value -> existingEvents.put(value.externalId(), localEntity(
                        em, required(previews, new EntityKey("AUDIT_EVENT", value.externalId()),
                                "preview identity"), AuditEvent.class)));
        List<SclxAuditHistoryImportData.EventValue> newEvents = source.events().stream()
                .filter(value -> !reuseExisting(required(previews,
                        new EntityKey("AUDIT_EVENT", value.externalId()), "preview identity")))
                .peek(value -> requireNew(previews, "AUDIT_EVENT", value.externalId()))
                .toList();
        AuditHistoryService.ImportedAuditHistory imported = auditHistoryService.importForInterchange(
                em,
                company,
                newEvents.stream().map(value -> new AuditHistoryService.AuditEventImport(
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
                        value.reason())).toList(),
                existingEvents);
        for (int index = 0; index < newEvents.size(); index++)
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
        if (item.identityMatch() == InterchangeIdentityMatch.CONFLICT)
        {
            if (item.conflictChoice() == SclxImportConflictChoice.KEEP_TARGET)
            {
                return;
            }
            if (item.conflictChoice() == SclxImportConflictChoice.TAKE_SOURCE)
            {
                identityService.acceptSourceConflict(
                        em,
                        company,
                        InterchangeFormat.SCLX,
                        preview.sourceSystem(),
                        type,
                        externalId,
                        item.normalizedContentHash(),
                        localId);
                return;
            }
            throw new IllegalStateException("SCLX conflict has no selected winner: "
                    + type + " " + externalId + ".");
        }
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

    private static SclxImportMappingRequirement requiredMapping(
            List<SclxImportMappingRequirement> mappings,
            SclxImportMappingRequirement.Kind kind,
            String sourceId)
    {
        return mappings.stream()
                .filter(value -> value.kind() == kind && value.sourceId().equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing SCLX " + kind.name().toLowerCase(Locale.ROOT)
                                + " mapping for " + sourceId + "."));
    }

    private static int newMasterCount(
            List<SclxImportMappingRequirement> mappings,
            SclxImportMappingRequirement.Kind kind)
    {
        return (int) mappings.stream()
                .filter(value -> value.kind() == kind)
                .filter(value -> value.resolution() == SclxImportMappingRequirement.Resolution.CREATE)
                .count();
    }

    private static String mappingAudit(List<SclxImportMappingRequirement> mappings)
    {
        return mappings.stream()
                .map(value -> value.kind().name() + ":" + value.sourceCode()
                        + "->" + value.targetCode() + "(" + value.resolution().name() + ")")
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private static void requireCompatibleMappedAccount(
            JsonNode source,
            SclxImportMappingRequirement mapping,
            Account target)
    {
        AccountType sourceType = enumValue(AccountType.class, text(source, "type"), "account type");
        NormalBalance sourceSide = enumValue(
                NormalBalance.class, text(source, "increaseSide"), "increase side");
        if (sourceType != target.getAccountType()
                || sourceSide != target.getNormalBalance()
                || (mapping.used() && (!target.isActive() || !target.isPosting())))
        {
            throw new IllegalStateException(
                    "The approved target account is no longer compatible: " + target.getCode() + ".");
        }
    }

    private static void requireCompatibleMappedFund(
            JsonNode source,
            SclxImportMappingRequirement mapping,
            Fund target)
    {
        FundType sourceType = enumValue(FundType.class, text(source, "type"), "fund type");
        if (sourceType != target.getFundType() || (mapping.used() && !target.isActive()))
        {
            throw new IllegalStateException(
                    "The approved target fund is no longer compatible: " + target.getCode() + ".");
        }
    }

    private static Account findAccount(EntityManager em, ChartOfAccounts chart, String code)
    {
        return em.createQuery(
                        "from Account a where a.chart = :chart and a.code = :code", Account.class)
                .setParameter("chart", chart)
                .setParameter("code", code)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    private static Fund findFund(EntityManager em, Company company, String code)
    {
        return em.createQuery(
                        "from Fund f where f.company = :company and f.code = :code", Fund.class)
                .setParameter("company", company)
                .setParameter("code", code)
                .getResultStream()
                .findFirst()
                .orElse(null);
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

    private static boolean reuseExisting(SclxImportEntityPreview item)
    {
        return item.identityMatch() == InterchangeIdentityMatch.IDENTICAL
                || (item.identityMatch() == InterchangeIdentityMatch.CONFLICT
                        && item.conflictChoice() != null);
    }

    private static <T> T localEntity(
            EntityManager em,
            SclxImportEntityPreview item,
            Class<T> entityType)
    {
        if (item.localEntityId() == null)
        {
            throw new IllegalStateException("SCLX identity no longer resolves to a local record: "
                    + item.entityType() + " " + item.externalId() + ".");
        }
        final Long id;
        try
        {
            id = Long.valueOf(item.localEntityId());
        }
        catch (NumberFormatException ex)
        {
            throw new IllegalStateException("SCLX identity has an invalid local record ID: "
                    + item.entityType() + " " + item.externalId() + ".", ex);
        }
        T entity = em.find(entityType, id);
        if (entity == null)
        {
            throw new IllegalStateException("SCLX identity local record disappeared after preview: "
                    + item.entityType() + " " + item.externalId() + ".");
        }
        return entity;
    }

    private static long localLongId(SclxImportEntityPreview item)
    {
        if (item.localEntityId() == null)
        {
            throw new IllegalStateException("SCLX identity no longer resolves to a local record: "
                    + item.entityType() + " " + item.externalId() + ".");
        }
        try
        {
            return Long.parseLong(item.localEntityId());
        }
        catch (NumberFormatException ex)
        {
            throw new IllegalStateException("SCLX identity has an invalid local record ID: "
                    + item.entityType() + " " + item.externalId() + ".", ex);
        }
    }

    private static UUID localUuid(SclxImportEntityPreview item)
    {
        if (item.localEntityId() == null)
        {
            throw new IllegalStateException("SCLX identity no longer resolves to a local record: "
                    + item.entityType() + " " + item.externalId() + ".");
        }
        try
        {
            return UUID.fromString(item.localEntityId());
        }
        catch (IllegalArgumentException ex)
        {
            throw new IllegalStateException("SCLX identity has an invalid local UUID: "
                    + item.entityType() + " " + item.externalId() + ".", ex);
        }
    }

    private static long newEntityCount(
            Map<EntityKey, SclxImportEntityPreview> previews,
            String type,
            List<String> externalIds)
    {
        return externalIds.stream()
                .map(externalId -> required(
                        previews, new EntityKey(type, externalId), "preview identity"))
                .filter(item -> item.identityMatch() == InterchangeIdentityMatch.NEW)
                .filter(item -> item.localEntityId() == null)
                .count();
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
                preview.operation().counts().updated(),
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

    private static String targetReuseAudit(SclxImportPreview preview)
    {
        return preview.operation().items().stream()
                .filter(item -> item.identityMatch() == InterchangeIdentityMatch.NEW)
                .filter(item -> item.localEntityId() != null)
                .map(item -> item.entityType() + ":" + item.externalId() + "->" + item.localEntityId())
                .sorted()
                .collect(java.util.stream.Collectors.joining("|"));
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
