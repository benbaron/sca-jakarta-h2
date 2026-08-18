package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.nonprofitbookkeeping.interchange.InterchangeFormat;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;
import org.nonprofitbookkeeping.interchange.InterchangeMessageSeverity;
import org.nonprofitbookkeeping.interchange.InterchangeOperationCounts;
import org.nonprofitbookkeeping.interchange.InterchangeOperationMode;
import org.nonprofitbookkeeping.interchange.InterchangePreview;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.CompanyOwnershipIssueView;
import org.nonprofitbookkeeping.service.CompanyOwnershipService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Produces the governed read-only SCLX preview. Parsing, local comparison, and
 * diagnostics complete without beginning a transaction or changing H2.
 */
public final class SclxImportPreviewService
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> RECOGNIZED_EXTENSION_KEYS = Set.of(
            "activeChartName", "activeChartVersion", "activities", "counterparties",
            "supplementalDetails", "bankConfiguration",
            "bankStatementFacts", "reconciliation", "fixedAssets", "inventory",
            "periodClose", "auditHistory");
    private static final Set<String> UNSUPPORTED_ROOT_SECTIONS = Set.of(
            "people", "bankAccounts", "officeAssignments", "committeeMemberships", "events",
            "documents", "bankingItems", "outstandingItems", "otherAssetItems", "supplementalItems",
            "assets", "supplies", "bankStatementImports");
    private static final Set<String> ACCOUNT_REFERENCE_FIELDS = Set.of(
            "accountId", "ledgerAccountId", "assetAccountId", "accumulatedDepreciationAccountId",
            "depreciationExpenseAccountId", "inventoryAccountId");
    private static final Set<String> FUND_REFERENCE_FIELDS = Set.of("fundId");
    private static final Set<String> SOURCE_WIN_SUPPORTED_ENTITY_TYPES =
            Set.of("ACTIVITY", "COUNTERPARTY", "MERCHANT");

    private final SclxDocumentParser parser;
    private final SclxStructureValidator structureValidator;
    private final SclxImportTargetReader targetReader;
    private final Supplier<String> companyCodeSupplier;
    private final Supplier<List<CompanyOwnershipIssueView>> ownershipIssuesSupplier;

    public SclxImportPreviewService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this(new SclxDocumentParser(), new SclxStructureValidator(),
                new JpaSclxImportTargetReader(jpa), companyCodeSupplier,
                new CompanyOwnershipService(jpa)::listOpenIssues);
    }

    SclxImportPreviewService(
            SclxDocumentParser parser,
            SclxStructureValidator structureValidator,
            SclxImportTargetReader targetReader,
            Supplier<String> companyCodeSupplier)
    {
        this(parser, structureValidator, targetReader, companyCodeSupplier, List::of);
    }

    SclxImportPreviewService(
            SclxDocumentParser parser,
            SclxStructureValidator structureValidator,
            SclxImportTargetReader targetReader,
            Supplier<String> companyCodeSupplier,
            Supplier<List<CompanyOwnershipIssueView>> ownershipIssuesSupplier)
    {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.structureValidator = Objects.requireNonNull(structureValidator, "structureValidator");
        this.targetReader = Objects.requireNonNull(targetReader, "targetReader");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.ownershipIssuesSupplier = Objects.requireNonNull(
                ownershipIssuesSupplier, "ownershipIssuesSupplier");
    }

    public SclxImportPreview preview(Path source)
    {
        return preview(source, List.of(), List.of(), List.of());
    }

    public SclxImportPreview preview(
            Path source,
            List<SclxImportMappingSelection> mappingSelections)
    {
        return preview(source, mappingSelections, List.of(), List.of());
    }

    public SclxImportPreview preview(
            Path source,
            List<SclxImportMappingSelection> mappingSelections,
            List<SclxImportConflictSelection> conflictSelections)
    {
        return preview(source, mappingSelections, conflictSelections, List.of());
    }

    public SclxImportPreview preview(
            Path source,
            List<SclxImportMappingSelection> mappingSelections,
            List<SclxImportConflictSelection> conflictSelections,
            List<SclxImportDispositionSelection> dispositionSelections)
    {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(mappingSelections, "mappingSelections");
        Objects.requireNonNull(conflictSelections, "conflictSelections");
        Objects.requireNonNull(dispositionSelections, "dispositionSelections");
        SclxParsedDocument document = parser.parse(source, dispositionSelections);
        List<InterchangeValidationMessage> messages = new ArrayList<>();
        document.compatibilityNotices().forEach(notice -> messages.add(message(
                notice.blocking() ? InterchangeMessageSeverity.ERROR : InterchangeMessageSeverity.WARNING,
                notice.code(), notice.path(), notice.message(), notice.blocking())));
        SclxStructureValidation structure = structureValidator.validate(document);
        structure.errors().forEach(error -> messages.add(message(
                InterchangeMessageSeverity.ERROR, "SCLX_STRUCTURE_ERROR", pathOf(error), error, true)));
        structure.warnings().forEach(warning -> messages.add(message(
                InterchangeMessageSeverity.WARNING, "SCLX_STRUCTURE_WARNING", pathOf(warning), warning, false)));

        OrganizationData organization = organization(document.root(), messages);
        String targetCode = requireText(companyCodeSupplier.get(), "target company code");
        SclxImportTargetSnapshot target = targetReader.read(targetCode, organization.sourceSystem());
        addOwnershipDiagnostics(messages, ownershipIssuesSupplier.get());
        Extraction extraction = extract(document.root(), messages);
        List<SclxImportEntityPreview> entities = classify(
                extraction.entities(), target, messages, conflictSelections);
        MappingResult mapping = mappings(document.root(), target, messages, mappingSelections);
        TransactionResult transactionResult = transactions(document.root(), target, messages);

        if (!organization.code().equalsIgnoreCase(target.companyCode()))
        {
            messages.add(message(
                    InterchangeMessageSeverity.WARNING,
                    "SCLX_ORGANIZATION_CODE_DIFFERS",
                    "$.organization.code",
                    "Source organization code " + organization.code() + " differs from target company "
                            + target.companyCode() + ". The target scope remains explicit and unchanged.",
                    false));
        }

        applyIgnoredDispositions(messages, dispositionSelections);

        long warnings = messages.stream()
                .filter(value -> value.severity() == InterchangeMessageSeverity.WARNING)
                .count();
        long errors = messages.stream().filter(InterchangeValidationMessage::blocking).count();
        Set<String> mappedMasterIds = mapping.requirements().stream()
                .filter(value -> value.resolution() == SclxImportMappingRequirement.Resolution.MAPPED)
                .map(SclxImportMappingRequirement::sourceId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> reusedEntityIds = new HashSet<>(mappedMasterIds);
        entities.stream()
                .filter(value -> value.identityMatch() == InterchangeIdentityMatch.NEW)
                .filter(value -> value.localEntityId() != null)
                .map(SclxImportEntityPreview::externalId)
                .forEach(reusedEntityIds::add);
        if (target.populated() && entities.stream().anyMatch(value ->
                value.externalId().equals(organization.organizationId())
                        && value.identityMatch() == InterchangeIdentityMatch.NEW))
        {
            reusedEntityIds.add(organization.organizationId());
        }
        long created = entities.stream()
                .filter(value -> value.identityMatch() == InterchangeIdentityMatch.NEW)
                .filter(value -> !reusedEntityIds.contains(value.externalId()))
                .count();
        long identical = entities.stream()
                .filter(value -> value.identityMatch() == InterchangeIdentityMatch.IDENTICAL
                        || value.conflictChoice() == SclxImportConflictChoice.KEEP_TARGET)
                .count();
        long sourceWins = entities.stream()
                .filter(value -> value.conflictChoice() == SclxImportConflictChoice.TAKE_SOURCE)
                .count();
        long skipped = transactionResult.zeroValueLines() + transactionResult.emptyTransactions();
        long reusedTargetMasters = entities.stream()
                .filter(value -> value.identityMatch() == InterchangeIdentityMatch.NEW)
                .filter(value -> value.localEntityId() != null)
                .count();
        InterchangeOperationCounts operationCounts = new InterchangeOperationCounts(
                extraction.counts().totalEntities(), created,
                mappedMasterIds.size() + reusedTargetMasters + sourceWins,
                identical, skipped, warnings, errors);
        String sourceName = source.getFileName() == null ? source.toString() : source.getFileName().toString();
        InterchangePreview<SclxImportEntityPreview> operation = new InterchangePreview<>(
                InterchangeFormat.SCLX,
                InterchangeOperationMode.PREVIEW_ONLY,
                sourceName,
                target.companyCode() + " — " + target.companyName(),
                document.sha256(),
                entities,
                messages,
                List.of(),
                operationCounts);
        return new SclxImportPreview(
                operation,
                document.version(),
                document.exportedAt(),
                organization.organizationId(),
                organization.code(),
                organization.name(),
                organization.sourceSystem(),
                target.companyCode(),
                target.companyName(),
                target.populated(),
                mapping.recommendedMode(),
                extraction.counts(),
                mapping.requirements(),
                transactionResult.previews(),
                dispositionSelections.stream()
                        .filter(value -> value.disposition() != SclxImportDisposition.NO_CHANGE)
                        .toList());
    }

    private static void applyIgnoredDispositions(
            List<InterchangeValidationMessage> messages,
            List<SclxImportDispositionSelection> selections)
    {
        Set<String> handled = new HashSet<>();
        for (SclxImportDispositionSelection selection : selections)
        {
            if (selection.disposition() != SclxImportDisposition.IGNORE
                    || !handled.add(selection.key()))
            {
                continue;
            }
            List<InterchangeValidationMessage> matches = messages.stream()
                    .filter(message -> message.code().equals(selection.code())
                            && message.path().equals(selection.path()))
                    .toList();
            if (matches.isEmpty())
            {
                messages.add(message(
                        InterchangeMessageSeverity.ERROR,
                        "SCLX_DISPOSITION_MESSAGE_CHANGED",
                        selection.path(),
                        "The ignored preview message is no longer present. Review the fresh messages and choose again.",
                        true));
                continue;
            }
            if (matches.stream().anyMatch(InterchangeValidationMessage::blocking))
            {
                messages.add(message(
                        InterchangeMessageSeverity.ERROR,
                        "SCLX_DISPOSITION_UNSUPPORTED",
                        selection.path(),
                        "Ignore cannot bypass blocking message " + selection.code()
                                + ". Apply a supported correction or drop the affected record.",
                        true));
                continue;
            }
            messages.removeIf(message -> message.code().equals(selection.code())
                    && message.path().equals(selection.path()));
            messages.add(message(
                    InterchangeMessageSeverity.INFO,
                    "SCLX_DISPOSITION_APPLIED",
                    selection.path(),
                    "Ignored nonblocking message " + selection.code()
                            + " for this preview and exact-source commit.",
                    false));
        }
    }

    private static void addOwnershipDiagnostics(
            List<InterchangeValidationMessage> messages,
            List<CompanyOwnershipIssueView> issues)
    {
        for (CompanyOwnershipIssueView issue : List.copyOf(issues))
        {
            messages.add(message(
                    InterchangeMessageSeverity.ERROR,
                    "SCLX_COMPANY_OWNERSHIP_UNRESOLVED",
                    "companyOwnership." + issue.entityType() + "." + issue.entityId(),
                    issue.recordLabel() + ". " + issue.details()
                            + (issue.relationshipCompanyCodes().isEmpty() ? ""
                            : " Related company evidence: "
                                    + String.join(", ", issue.relationshipCompanyCodes()) + ".")
                            + " Resolution: " + issue.resolutionGuidance()
                            + " Open Administration -> Company Ownership Diagnostics.",
                    true));
        }
    }

    private static OrganizationData organization(
            JsonNode root, List<InterchangeValidationMessage> messages)
    {
        JsonNode node = root.get("organization");
        if (node == null || !node.isObject())
        {
            messages.add(message(InterchangeMessageSeverity.ERROR, "SCLX_ORGANIZATION_REQUIRED",
                    "$.organization", "SCLX organization must be an object.", true));
            return new OrganizationData("UNKNOWN", "UNKNOWN", "Unknown organization", "UNKNOWN");
        }
        String id = textOrPlaceholder(node, "organizationId", "UNKNOWN", messages,
                "SCLX_ORGANIZATION_ID_REQUIRED");
        String code = textOrPlaceholder(node, "code", "UNKNOWN", messages,
                "SCLX_ORGANIZATION_CODE_REQUIRED");
        String name = textOrPlaceholder(node, "name", "Unknown organization", messages,
                "SCLX_ORGANIZATION_NAME_REQUIRED");
        return new OrganizationData(id, code, name, id);
    }

    private static Extraction extract(JsonNode root, List<InterchangeValidationMessage> messages)
    {
        Map<String, Long> counts = new LinkedHashMap<>();
        List<IncomingEntity> entities = new ArrayList<>();
        JsonNode organization = root.get("organization");
        if (organization != null && organization.isObject())
        {
            addEntity(organization, "organizationId", "ORGANIZATION", "organizations",
                    "$.organization", counts, entities, messages);
        }
        addArray(root.get("chartOfAccounts"), "accountId", "ACCOUNT", "accounts",
                "$.chartOfAccounts", counts, entities, messages);
        addArray(root.get("funds"), "fundId", "FUND", "funds",
                "$.funds", counts, entities, messages);
        addNestedArray(root.get("budgets"), "budgetId", "BUDGET", "budgets", "lines",
                "lineId", "BUDGET_LINE", "budgetLines", "$.budgets", counts, entities, messages);
        addNestedArray(root.get("transactions"), "transactionId", "TRANSACTION", "transactions", "lines",
                "lineId", "TRANSACTION_LINE", "transactionLines", "$.transactions",
                counts, entities, messages);

        JsonNode extensions = root.path("extensions");
        JsonNode app = extensions.path("scaJakartaH2");
        if (app.isObject())
        {
            addArray(app.get("activities"), "activityId", "ACTIVITY", "activities",
                    "$.extensions.scaJakartaH2.activities", counts, entities, messages);
            addObjectArrays(app.get("counterparties"), "$.extensions.scaJakartaH2.counterparties",
                    counts, entities, messages,
                    new ArraySpec("counterparties", "counterpartyId", "COUNTERPARTY", "counterparties"),
                    new ArraySpec("merchants", "merchantId", "MERCHANT", "merchants"));
            addArray(app.get("supplementalDetails"), "supplementalDetailId", "SUPPLEMENTAL_DETAIL",
                    "supplementalDetails", "$.extensions.scaJakartaH2.supplementalDetails",
                    counts, entities, messages);
            addObjectArrays(app.get("bankConfiguration"), "$.extensions.scaJakartaH2.bankConfiguration",
                    counts, entities, messages,
                    new ArraySpec("banks", "bankId", "BANK", "banks"),
                    new ArraySpec("accounts", "bankAccountId", "BANK_ACCOUNT", "bankAccounts"));
            addObjectArrays(app.get("bankStatementFacts"), "$.extensions.scaJakartaH2.bankStatementFacts",
                    counts, entities, messages,
                    new ArraySpec("importBatches", "importBatchId", "BANK_IMPORT_BATCH", "importBatches"),
                    new ArraySpec("statementLines", "statementLineId", "BANK_STATEMENT_LINE", "statementLines"),
                    new ArraySpec("issues", "issueId", "BANK_IMPORT_ISSUE", "importIssues"));
            addObjectArrays(app.get("reconciliation"), "$.extensions.scaJakartaH2.reconciliation",
                    counts, entities, messages,
                    new ArraySpec("sessions", "reconciliationSessionId", "RECONCILIATION_SESSION",
                            "reconciliationSessions"),
                    new ArraySpec("matches", "reconciliationMatchId", "RECONCILIATION_MATCH",
                            "reconciliationMatches"));
            addObjectArrays(app.get("fixedAssets"), "$.extensions.scaJakartaH2.fixedAssets",
                    counts, entities, messages,
                    new ArraySpec("assets", "assetId", "FIXED_ASSET", "fixedAssets"),
                    new ArraySpec("depreciationRuns", "depreciationRunId", "DEPRECIATION_RUN",
                            "depreciationRuns"));
            addObjectArrays(app.get("inventory"), "$.extensions.scaJakartaH2.inventory",
                    counts, entities, messages,
                    new ArraySpec("items", "itemId", "INVENTORY_ITEM", "inventoryItems"),
                    new ArraySpec("movements", "movementId", "INVENTORY_MOVEMENT", "inventoryMovements"));
            addObjectArrays(app.get("periodClose"), "$.extensions.scaJakartaH2.periodClose",
                    counts, entities, messages,
                    new ArraySpec("ranges", "rangeId", "PERIOD_CLOSE_RANGE", "periodCloseRanges"),
                    new ArraySpec("events", "eventId", "PERIOD_CLOSE_EVENT", "periodCloseEvents"));
            addObjectArrays(app.get("auditHistory"), "$.extensions.scaJakartaH2.auditHistory",
                    counts, entities, messages,
                    new ArraySpec("events", "auditEventId", "AUDIT_EVENT", "auditEvents"));
        }

        long relationships = arraySize(app.path("counterparties").get("transactionLineMerchants"))
                + arraySize(app.path("bankStatementFacts").get("transactionLineClearance"));
        long unsupported = unsupportedSections(root, extensions, app, messages);
        long references = countReferences(root, false);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new Extraction(
                new SclxImportPreviewCounts(counts, total, references, relationships, unsupported),
                List.copyOf(entities));
    }

    private static List<SclxImportEntityPreview> classify(
            List<IncomingEntity> incoming,
            SclxImportTargetSnapshot target,
            List<InterchangeValidationMessage> messages,
            List<SclxImportConflictSelection> selections)
    {
        List<SclxImportEntityPreview> result = new ArrayList<>(incoming.size());
        Set<SclxImportTargetSnapshot.ExternalIdentityKey> seen = new HashSet<>();
        Map<SclxImportTargetSnapshot.ExternalIdentityKey, SclxImportConflictChoice> selected = new HashMap<>();
        for (SclxImportConflictSelection selection : selections)
        {
            SclxImportTargetSnapshot.ExternalIdentityKey key =
                    new SclxImportTargetSnapshot.ExternalIdentityKey(
                            selection.entityType(), selection.externalId());
            if (selected.put(key, selection.choice()) != null)
            {
                messages.add(message(InterchangeMessageSeverity.ERROR,
                        "SCLX_DUPLICATE_CONFLICT_SELECTION", "conflict",
                        "More than one winner was selected for " + selection.entityType() + " "
                                + selection.externalId() + ".", true));
            }
        }
        Set<SclxImportTargetSnapshot.ExternalIdentityKey> projected = new HashSet<>();
        for (IncomingEntity entity : incoming)
        {
            SclxImportTargetSnapshot.ExternalIdentityKey key = new SclxImportTargetSnapshot.ExternalIdentityKey(
                    entity.entityType(), entity.externalId());
            projected.add(key);
            if (!seen.add(key))
            {
                messages.add(message(InterchangeMessageSeverity.ERROR, "SCLX_DUPLICATE_EXTERNAL_ID",
                        entity.path(), "Input repeats " + entity.entityType() + " identity "
                                + entity.externalId() + ".", true));
            }
            SclxImportTargetSnapshot.IdentityFact local = target.identities().get(key);
            InterchangeIdentityMatch match;
            String localId = null;
            String conflictDetail = null;
            if (local == null)
            {
                match = InterchangeIdentityMatch.NEW;
                if ("ACTIVITY".equals(entity.entityType()))
                {
                    SclxImportTargetSnapshot.TargetActivity activity = target.activitiesByCode().get(
                            text(entity.value(), "code"));
                    if (activity != null && activity.name().equals(text(entity.value(), "name"))
                            && activity.active() == entity.value().path("active").asBoolean())
                    {
                        localId = activity.localEntityId();
                        messages.add(message(
                                InterchangeMessageSeverity.WARNING,
                                "SCLX_TARGET_ACTIVITY_REUSED",
                                entity.path(),
                                "The import target already contains compatible Activity " + activity.code()
                                        + "; it will be reused and linked to this SCLX identity.",
                                false));
                    }
                    else if (activity != null)
                    {
                        match = InterchangeIdentityMatch.CONFLICT;
                        localId = activity.localEntityId();
                        conflictDetail = "Target Activity " + activity.code() + " is name='"
                                + activity.name() + "', active=" + activity.active()
                                + "; SCLX is name='" + text(entity.value(), "name") + "', active="
                                + entity.value().path("active").asBoolean() + ".";
                    }
                }
            }
            else if (local.normalizedContentHash().equals(entity.normalizedHash()))
            {
                match = InterchangeIdentityMatch.IDENTICAL;
                localId = local.localEntityId();
            }
            else
            {
                match = InterchangeIdentityMatch.CONFLICT;
                localId = local.localEntityId();
                conflictDetail = entity.entityType() + " identity " + entity.externalId()
                        + " has target hash " + local.normalizedContentHash().substring(0, 12)
                        + " and SCLX hash " + entity.normalizedHash().substring(0, 12) + ".";
            }
            SclxImportConflictChoice choice = match == InterchangeIdentityMatch.CONFLICT
                    ? selected.get(key) : null;
            boolean sourceAllowed = match == InterchangeIdentityMatch.CONFLICT
                    && SOURCE_WIN_SUPPORTED_ENTITY_TYPES.contains(entity.entityType());
            if (match == InterchangeIdentityMatch.CONFLICT)
            {
                if (choice == SclxImportConflictChoice.TAKE_SOURCE && !sourceAllowed)
                {
                    messages.add(message(InterchangeMessageSeverity.ERROR,
                            "SCLX_CONFLICT_SOURCE_UNSAFE", entity.path(),
                            conflictDetail + " Taking the SCLX version is unavailable because this record may own "
                                    + "accounting history. Keep the target version or resolve the protected history first.",
                            true));
                    choice = null;
                }
                else if (choice == null)
                {
                    messages.add(message(InterchangeMessageSeverity.ERROR,
                            "SCLX_CONFLICT_CHOICE_REQUIRED", entity.path(),
                            conflictDetail + " Choose Keep target"
                                    + (sourceAllowed ? " or Take SCLX" : " (Take SCLX is unsafe for this record)")
                                    + ", then apply the choices to preview again.", true));
                }
                else
                {
                    messages.add(message(InterchangeMessageSeverity.WARNING,
                            "SCLX_CONFLICT_RESOLVED", entity.path(),
                            conflictDetail + " Selected winner: "
                                    + (choice == SclxImportConflictChoice.KEEP_TARGET
                                            ? "target record." : "SCLX record."), false));
                }
            }
            result.add(new SclxImportEntityPreview(
                    entity.entityType(), entity.externalId(), entity.path(), entity.normalizedHash(), match, localId,
                    choice, sourceAllowed, conflictDetail));
        }
        selected.keySet().stream()
                .filter(key -> !projected.contains(key))
                .forEach(key -> messages.add(message(InterchangeMessageSeverity.ERROR,
                        "SCLX_CONFLICT_SELECTION_UNKNOWN", "conflict",
                        "The selected conflicting record is no longer present: " + key.entityType() + " "
                                + key.externalId() + ".", true)));
        return result.stream()
                .sorted(Comparator.comparing(SclxImportEntityPreview::entityType)
                        .thenComparing(SclxImportEntityPreview::externalId))
                .toList();
    }

    private static MappingResult mappings(
            JsonNode root,
            SclxImportTargetSnapshot target,
            List<InterchangeValidationMessage> messages,
            List<SclxImportMappingSelection> selections)
    {
        Set<String> usedAccounts = new LinkedHashSet<>();
        Set<String> usedFunds = new LinkedHashSet<>();
        collectReferences(root.path("budgets"), usedAccounts, usedFunds);
        collectReferences(root.path("transactions"), usedAccounts, usedFunds);
        collectReferences(root.path("extensions"), usedAccounts, usedFunds);

        List<SclxImportMappingRequirement> requirements = new ArrayList<>();
        Map<MappingKey, String> selectedTargets = new HashMap<>();
        for (SclxImportMappingSelection selection : selections)
        {
            MappingKey key = new MappingKey(selection.kind(), selection.sourceId());
            if (selectedTargets.put(key, selection.targetCode()) != null)
            {
                messages.add(message(InterchangeMessageSeverity.ERROR,
                        "SCLX_DUPLICATE_MAPPING_SELECTION", "mapping",
                        "More than one target was selected for " + selection.sourceId() + ".", true));
            }
        }
        JsonNode accounts = root.path("chartOfAccounts");
        if (accounts.isArray())
        {
            for (int index = 0; index < accounts.size(); index++)
            {
                JsonNode source = accounts.get(index);
                if (!source.isObject()) continue;
                String sourceId = text(source, "accountId");
                String sourceCode = text(source, "code");
                if (sourceId == null || sourceCode == null) continue;
                boolean used = usedAccounts.contains(sourceId);
                SclxImportMappingRequirement requirement = accountMapping(
                        source, sourceId, sourceCode, used, target,
                        selectedTargets.get(new MappingKey(
                                SclxImportMappingRequirement.Kind.ACCOUNT, sourceId)));
                requirements.add(requirement);
                mappingMessage(requirement, messages);
            }
        }
        JsonNode funds = root.path("funds");
        if (funds.isArray())
        {
            for (int index = 0; index < funds.size(); index++)
            {
                JsonNode source = funds.get(index);
                if (!source.isObject()) continue;
                String sourceId = text(source, "fundId");
                String sourceCode = text(source, "code");
                if (sourceId == null || sourceCode == null) continue;
                boolean used = usedFunds.contains(sourceId);
                SclxImportMappingRequirement requirement = fundMapping(
                        source, sourceId, sourceCode, used, target,
                        selectedTargets.get(new MappingKey(
                                SclxImportMappingRequirement.Kind.FUND, sourceId)));
                requirements.add(requirement);
                mappingMessage(requirement, messages);
            }
        }
        Set<MappingKey> projectedKeys = requirements.stream()
                .map(value -> new MappingKey(value.kind(), value.sourceId()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        selectedTargets.keySet().stream()
                .filter(key -> !projectedKeys.contains(key))
                .forEach(key -> messages.add(message(
                        InterchangeMessageSeverity.ERROR,
                        "SCLX_MAPPING_SELECTION_UNKNOWN",
                        "mapping",
                        "The selected source account or fund is no longer present: " + key.sourceId() + ".",
                        true)));
        Map<TargetMappingKey, List<SclxImportMappingRequirement>> byTarget = requirements.stream()
                .filter(value -> value.resolution() == SclxImportMappingRequirement.Resolution.AS_IS
                        || value.resolution() == SclxImportMappingRequirement.Resolution.MAPPED)
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> new TargetMappingKey(value.kind(), value.targetCode())));
        byTarget.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .sorted(Comparator.comparing(
                                (Map.Entry<TargetMappingKey, List<SclxImportMappingRequirement>> entry) ->
                                        entry.getKey().kind())
                        .thenComparing(entry -> entry.getKey().targetCode()))
                .forEach(entry -> messages.add(message(
                        InterchangeMessageSeverity.ERROR,
                        "SCLX_MAPPING_TARGET_REUSED",
                        "mapping." + entry.getKey().kind().name().toLowerCase(Locale.ROOT)
                                + "." + entry.getKey().targetCode(),
                        "More than one source " + entry.getKey().kind().name().toLowerCase(Locale.ROOT)
                                + " maps to target " + entry.getKey().targetCode()
                                + ". Select a distinct target for each source record.",
                        true)));
        SclxAccountMode mode = requirements.stream()
                .allMatch(value -> value.resolution() == SclxImportMappingRequirement.Resolution.AS_IS
                        || value.resolution() == SclxImportMappingRequirement.Resolution.CREATE)
                ? SclxAccountMode.AS_IS : SclxAccountMode.MAPPED;
        return new MappingResult(mode, requirements.stream()
                .sorted(Comparator.comparing(SclxImportMappingRequirement::kind)
                        .thenComparing(SclxImportMappingRequirement::sourceCode))
                .toList());
    }

    private static SclxImportMappingRequirement accountMapping(
            JsonNode source, String sourceId, String sourceCode, boolean used,
            SclxImportTargetSnapshot target, String selectedTargetCode)
    {
        String targetId = SclxPortableIdentity.account(target.companyCode(), sourceCode);
        if (!target.populated())
        {
            return new SclxImportMappingRequirement(
                    SclxImportMappingRequirement.Kind.ACCOUNT, sourceId, sourceCode,
                    targetId, sourceCode, used, SclxImportMappingRequirement.Resolution.CREATE,
                    "The import will create this account in the target chart.", false);
        }
        SclxImportTargetSnapshot.IdentityFact imported = target.identities().get(
                new SclxImportTargetSnapshot.ExternalIdentityKey("ACCOUNT", sourceId));
        if (imported != null && imported.normalizedContentHash().equals(hash(source)))
        {
            SclxImportTargetSnapshot.TargetAccount importedTarget = target.accountsByCode().values().stream()
                    .filter(value -> value.localEntityId().equals(imported.localEntityId()))
                    .findFirst()
                    .orElse(null);
            if (importedTarget == null)
            {
                return new SclxImportMappingRequirement(
                        SclxImportMappingRequirement.Kind.ACCOUNT, sourceId, sourceCode,
                        null, null, used, SclxImportMappingRequirement.Resolution.UNRESOLVED,
                        "The identical imported account identity no longer resolves to a target account.",
                        true);
            }
            return new SclxImportMappingRequirement(
                    SclxImportMappingRequirement.Kind.ACCOUNT, sourceId, sourceCode,
                    importedTarget.portableId(), importedTarget.code(), used,
                    SclxImportMappingRequirement.Resolution.AS_IS,
                    "The identical imported identity already resolves to this target account.", false);
        }
        List<String> candidates = compatibleAccountCodes(source, used, target);
        String resolvedCode = selectedTargetCode == null ? sourceCode : selectedTargetCode;
        SclxImportTargetSnapshot.TargetAccount local = target.accountsByCode().get(resolvedCode);
        if (local == null)
        {
            if (selectedTargetCode != null)
            {
                return new SclxImportMappingRequirement(
                        SclxImportMappingRequirement.Kind.ACCOUNT, sourceId, sourceCode,
                        null, null, used, SclxImportMappingRequirement.Resolution.UNRESOLVED,
                        "The selected target account no longer exists. Choose another compatible account.",
                        used, candidates);
            }
            return new SclxImportMappingRequirement(
                    SclxImportMappingRequirement.Kind.ACCOUNT, sourceId, sourceCode,
                    targetId, sourceCode, used, SclxImportMappingRequirement.Resolution.CREATE,
                    "No target account uses this code; the import will create it in the existing chart. "
                            + (candidates.isEmpty() ? ""
                            : "Select a compatible existing target to map it instead."),
                    false, candidates);
        }
        boolean compatible = compatibleAccount(source, used, local);
        if (!compatible)
        {
            return new SclxImportMappingRequirement(
                    SclxImportMappingRequirement.Kind.ACCOUNT, sourceId, sourceCode,
                    local.portableId(), local.code(), used,
                    SclxImportMappingRequirement.Resolution.CONFLICT,
                    "The target account type, increase side, posting state, or active state is incompatible. "
                            + (candidates.isEmpty()
                                    ? "Create a compatible target account with a different code, then preview again."
                                    : "Choose a compatible target account from the mapping selector."),
                    used, candidates);
        }
        return new SclxImportMappingRequirement(
                SclxImportMappingRequirement.Kind.ACCOUNT, sourceId, sourceCode,
                local.portableId(), local.code(), used,
                SclxImportMappingRequirement.Resolution.MAPPED,
                "A compatible code match exists. Approve the shown source-to-target mapping before import.",
                false, candidates);
    }

    private static SclxImportMappingRequirement fundMapping(
            JsonNode source, String sourceId, String sourceCode, boolean used,
            SclxImportTargetSnapshot target, String selectedTargetCode)
    {
        String targetId = SclxPortableIdentity.fund(target.companyCode(), sourceCode);
        if (!target.populated())
        {
            return new SclxImportMappingRequirement(
                    SclxImportMappingRequirement.Kind.FUND, sourceId, sourceCode,
                    targetId, sourceCode, used, SclxImportMappingRequirement.Resolution.CREATE,
                    "The import will create this fund in the target company.", false);
        }
        SclxImportTargetSnapshot.IdentityFact imported = target.identities().get(
                new SclxImportTargetSnapshot.ExternalIdentityKey("FUND", sourceId));
        if (imported != null && imported.normalizedContentHash().equals(hash(source)))
        {
            SclxImportTargetSnapshot.TargetFund importedTarget = target.fundsByCode().values().stream()
                    .filter(value -> value.localEntityId().equals(imported.localEntityId()))
                    .findFirst()
                    .orElse(null);
            if (importedTarget == null)
            {
                return new SclxImportMappingRequirement(
                        SclxImportMappingRequirement.Kind.FUND, sourceId, sourceCode,
                        null, null, used, SclxImportMappingRequirement.Resolution.UNRESOLVED,
                        "The identical imported fund identity no longer resolves to a target fund.", true);
            }
            return new SclxImportMappingRequirement(
                    SclxImportMappingRequirement.Kind.FUND, sourceId, sourceCode,
                    importedTarget.portableId(), importedTarget.code(), used,
                    SclxImportMappingRequirement.Resolution.AS_IS,
                    "The identical imported identity already resolves to this target fund.", false);
        }
        List<String> candidates = compatibleFundCodes(source, used, target);
        String resolvedCode = selectedTargetCode == null ? sourceCode : selectedTargetCode;
        SclxImportTargetSnapshot.TargetFund local = target.fundsByCode().get(resolvedCode);
        if (local == null)
        {
            if (selectedTargetCode != null)
            {
                return new SclxImportMappingRequirement(
                        SclxImportMappingRequirement.Kind.FUND, sourceId, sourceCode,
                        null, null, used, SclxImportMappingRequirement.Resolution.UNRESOLVED,
                        "The selected target fund no longer exists. Choose another compatible fund.",
                        used, candidates);
            }
            return new SclxImportMappingRequirement(
                    SclxImportMappingRequirement.Kind.FUND, sourceId, sourceCode,
                    targetId, sourceCode, used, SclxImportMappingRequirement.Resolution.CREATE,
                    "No target fund uses this code; the import will create it in the existing company. "
                            + (candidates.isEmpty() ? ""
                            : "Select a compatible existing target to map it instead."),
                    false, candidates);
        }
        boolean compatible = compatibleFund(source, used, local);
        if (!compatible)
        {
            return new SclxImportMappingRequirement(
                    SclxImportMappingRequirement.Kind.FUND, sourceId, sourceCode,
                    local.portableId(), local.code(), used,
                    SclxImportMappingRequirement.Resolution.CONFLICT,
                    "The target fund type or active state is incompatible. "
                            + (candidates.isEmpty()
                                    ? "Create a compatible target fund with a different code, then preview again."
                                    : "Choose a compatible target fund from the mapping selector."),
                    used, candidates);
        }
        return new SclxImportMappingRequirement(
                SclxImportMappingRequirement.Kind.FUND, sourceId, sourceCode,
                local.portableId(), local.code(), used,
                SclxImportMappingRequirement.Resolution.MAPPED,
                "A compatible code match exists. Approve the shown source-to-target mapping before import.",
                false, candidates);
    }

    private static List<String> compatibleAccountCodes(
            JsonNode source,
            boolean used,
            SclxImportTargetSnapshot target)
    {
        return target.accountsByCode().values().stream()
                .filter(value -> compatibleAccount(source, used, value))
                .map(SclxImportTargetSnapshot.TargetAccount::code)
                .sorted()
                .toList();
    }

    private static boolean compatibleAccount(
            JsonNode source,
            boolean used,
            SclxImportTargetSnapshot.TargetAccount local)
    {
        String sourceType = normalizeAccountType(text(source, "type"));
        String sourceSide = normalizeToken(text(source, "increaseSide"));
        return sourceType.equals(normalizeAccountType(local.type()))
                && sourceSide.equals(normalizeToken(local.increaseSide()))
                && (!used || (local.active() && local.posting()));
    }

    private static List<String> compatibleFundCodes(
            JsonNode source,
            boolean used,
            SclxImportTargetSnapshot target)
    {
        return target.fundsByCode().values().stream()
                .filter(value -> compatibleFund(source, used, value))
                .map(SclxImportTargetSnapshot.TargetFund::code)
                .sorted()
                .toList();
    }

    private static boolean compatibleFund(
            JsonNode source,
            boolean used,
            SclxImportTargetSnapshot.TargetFund local)
    {
        return normalizeToken(text(source, "type")).equals(normalizeToken(local.type()))
                && (!used || local.active());
    }

    private static void mappingMessage(
            SclxImportMappingRequirement mapping,
            List<InterchangeValidationMessage> messages)
    {
        if (mapping.resolution() == SclxImportMappingRequirement.Resolution.MAPPED)
        {
            messages.add(message(InterchangeMessageSeverity.WARNING,
                    "SCLX_MAPPING_APPROVAL_REQUIRED",
                    "mapping." + mapping.kind().name().toLowerCase(Locale.ROOT) + "." + mapping.sourceCode(),
                    mapping.detail(), false));
            return;
        }
        if (!mapping.blocking())
        {
            return;
        }
        String code = switch (mapping.resolution())
        {
            case MAPPED -> "SCLX_EXPLICIT_MAPPING_REQUIRED";
            case CONFLICT -> "SCLX_MAPPING_CONFLICT";
            case UNRESOLVED -> "SCLX_MAPPING_UNRESOLVED";
            case AS_IS, CREATE -> "SCLX_MAPPING_BLOCKED";
        };
        messages.add(message(InterchangeMessageSeverity.ERROR, code,
                "mapping." + mapping.kind().name().toLowerCase(Locale.ROOT) + "." + mapping.sourceCode(),
                mapping.detail(), true));
    }

    private static TransactionResult transactions(
            JsonNode root,
            SclxImportTargetSnapshot target,
            List<InterchangeValidationMessage> messages)
    {
        List<SclxImportTransactionPreview> previews = new ArrayList<>();
        long zeroValueLines = 0L;
        long emptyTransactions = 0L;
        JsonNode transactions = root.path("transactions");
        if (!transactions.isArray())
        {
            return new TransactionResult(List.of(), 0L, 0L);
        }
        for (int index = 0; index < transactions.size(); index++)
        {
            JsonNode transaction = transactions.get(index);
            if (!transaction.isObject()) continue;
            String path = "$.transactions[" + index + "]";
            String id = text(transaction, "transactionId");
            LocalDate date = parseDate(transaction.get("transactionDate"), path + ".transactionDate", messages);
            if (id == null || date == null) continue;
            JsonNode lines = transaction.path("lines");
            int sourceLineCount = lines.isArray() ? lines.size() : 0;
            int postingLines = 0;
            int zeroLines = 0;
            BigDecimal debits = BigDecimal.ZERO;
            BigDecimal credits = BigDecimal.ZERO;
            if (lines.isArray())
            {
                for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++)
                {
                    JsonNode line = lines.get(lineIndex);
                    if (!line.isObject()) continue;
                    String linePath = path + ".lines[" + lineIndex + "]";
                    BigDecimal debit = decimal(line.get("debit"), linePath + ".debit", messages);
                    BigDecimal credit = decimal(line.get("credit"), linePath + ".credit", messages);
                    if (debit.signum() < 0 || credit.signum() < 0)
                    {
                        messages.add(message(InterchangeMessageSeverity.ERROR, "SCLX_NEGATIVE_POSTING_AMOUNT",
                                linePath, "Debit and credit values must not be negative.", true));
                        continue;
                    }
                    if (debit.signum() > 0 && credit.signum() > 0)
                    {
                        messages.add(message(InterchangeMessageSeverity.ERROR, "SCLX_LINE_HAS_DEBIT_AND_CREDIT",
                                linePath, "A transaction line cannot contain both debit and credit.", true));
                        continue;
                    }
                    if (debit.signum() == 0 && credit.signum() == 0)
                    {
                        zeroLines++;
                        zeroValueLines++;
                        messages.add(message(InterchangeMessageSeverity.WARNING, "SCLX_ZERO_VALUE_LINE_SKIPPED",
                                linePath, "This zero-value line would be skipped before commit.", false));
                        continue;
                    }
                    postingLines++;
                    debits = debits.add(debit);
                    credits = credits.add(credit);
                }
            }
            boolean balanced = postingLines >= 2 && debits.compareTo(credits) == 0;
            boolean requiresBalancing = postingLines > 0 && !balanced;
            if (postingLines == 0)
            {
                emptyTransactions++;
                messages.add(message(InterchangeMessageSeverity.WARNING,
                        "SCLX_TRANSACTION_NO_POSTING_LINES", path,
                        "This transaction has no nonzero posting lines and would be skipped.", false));
            }
            else if (requiresBalancing)
            {
                messages.add(message(InterchangeMessageSeverity.ERROR,
                        "SCLX_BALANCING_ACCOUNT_REQUIRED", path,
                        "This transaction is single-sided or unbalanced. Select an active posting cash account "
                                + "and review the generated balancing line before commit.", true));
            }
            SclxImportTargetSnapshot.IdentityFact identity = target.identities().get(
                    new SclxImportTargetSnapshot.ExternalIdentityKey("TRANSACTION", id));
            boolean identical = identity != null
                    && identity.normalizedContentHash().equals(hash(transaction));
            boolean closed = target.isClosed(date);
            if (closed && !identical)
            {
                messages.add(message(InterchangeMessageSeverity.ERROR, "SCLX_CLOSED_PERIOD_CONFLICT", path,
                        "Transaction date " + date + " is inside an authoritative closed range.", true));
            }
            boolean finalized = identity != null && identity.localEntityId() != null
                    && target.finalizedTransactionLocalIds().contains(identity.localEntityId());
            if (finalized && !identical)
            {
                messages.add(message(InterchangeMessageSeverity.ERROR,
                        "SCLX_FINALIZED_RECONCILIATION_CONFLICT", path,
                        "The matching local transaction participates in a finalized reconciliation.", true));
            }
            previews.add(new SclxImportTransactionPreview(
                    id, date, text(transaction, "description"), sourceLineCount, postingLines, zeroLines,
                    debits, credits, balanced, requiresBalancing, closed, finalized));
        }
        return new TransactionResult(List.copyOf(previews), zeroValueLines, emptyTransactions);
    }

    private static void addNestedArray(
            JsonNode values, String idField, String entityType, String countKey,
            String childField, String childIdField, String childType, String childCountKey,
            String path, Map<String, Long> counts, List<IncomingEntity> entities,
            List<InterchangeValidationMessage> messages)
    {
        addArray(values, idField, entityType, countKey, path, counts, entities, messages);
        if (values == null || !values.isArray()) return;
        for (int index = 0; index < values.size(); index++)
        {
            JsonNode parent = values.get(index);
            if (!parent.isObject()) continue;
            addArray(parent.get(childField), childIdField, childType, childCountKey,
                    path + "[" + index + "]." + childField, counts, entities, messages);
        }
    }

    private static void addObjectArrays(
            JsonNode root, String path, Map<String, Long> counts, List<IncomingEntity> entities,
            List<InterchangeValidationMessage> messages, ArraySpec... specs)
    {
        if (root == null || root.isMissingNode() || root.isNull()) return;
        if (!root.isObject())
        {
            messages.add(message(InterchangeMessageSeverity.ERROR, "SCLX_EXTENSION_SHAPE_ERROR", path,
                    path + " must be an object.", true));
            return;
        }
        for (ArraySpec spec : specs)
        {
            addArray(root.get(spec.field()), spec.idField(), spec.entityType(), spec.countKey(),
                    path + "." + spec.field(), counts, entities, messages);
        }
    }

    private static void addArray(
            JsonNode values, String idField, String entityType, String countKey, String path,
            Map<String, Long> counts, List<IncomingEntity> entities,
            List<InterchangeValidationMessage> messages)
    {
        if (values == null || values.isNull() || values.isMissingNode())
        {
            counts.putIfAbsent(countKey, 0L);
            return;
        }
        if (!values.isArray())
        {
            messages.add(message(InterchangeMessageSeverity.ERROR, "SCLX_SECTION_SHAPE_ERROR", path,
                    path + " must be an array.", true));
            counts.putIfAbsent(countKey, 0L);
            return;
        }
        counts.merge(countKey, (long) values.size(), Long::sum);
        for (int index = 0; index < values.size(); index++)
        {
            JsonNode value = values.get(index);
            if (value.isObject())
            {
                addEntity(value, idField, entityType, countKey, path + "[" + index + "]",
                        counts, entities, messages, false);
            }
        }
    }

    private static void addEntity(
            JsonNode value, String idField, String entityType, String countKey, String path,
            Map<String, Long> counts, List<IncomingEntity> entities,
            List<InterchangeValidationMessage> messages)
    {
        counts.merge(countKey, 1L, Long::sum);
        addEntity(value, idField, entityType, countKey, path, counts, entities, messages, false);
    }

    private static void addEntity(
            JsonNode value, String idField, String entityType, String countKey, String path,
            Map<String, Long> counts, List<IncomingEntity> entities,
            List<InterchangeValidationMessage> messages, boolean ignored)
    {
        JsonNode id = value.get(idField);
        if (id == null || !id.isTextual() || id.textValue().isBlank())
        {
            messages.add(message(InterchangeMessageSeverity.ERROR, "SCLX_ENTITY_ID_REQUIRED",
                    path + "." + idField, idField + " is required for " + entityType + ".", true));
            return;
        }
        entities.add(new IncomingEntity(entityType, id.textValue(), path, hash(value), value));
    }

    private static long unsupportedSections(
            JsonNode root, JsonNode extensions, JsonNode app,
            List<InterchangeValidationMessage> messages)
    {
        long count = 0L;
        for (String section : UNSUPPORTED_ROOT_SECTIONS)
        {
            JsonNode value = root.get(section);
            if (!nonEmpty(value)) continue;
            count++;
            messages.add(message(InterchangeMessageSeverity.WARNING, "SCLX_UNSUPPORTED_SECTION",
                    "$." + section, "Section " + section + " contains " + elementCount(value)
                            + " item(s) and is not mapped to current canonical authority.", false));
        }
        if (extensions.isObject())
        {
            var fields = extensions.fieldNames();
            while (fields.hasNext())
            {
                String field = fields.next();
                if (!"version".equals(field) && !"scaJakartaH2".equals(field)
                        && nonEmpty(extensions.get(field)))
                {
                    count++;
                    messages.add(message(InterchangeMessageSeverity.WARNING,
                            "SCLX_UNKNOWN_EXTENSION_NAMESPACE", "$.extensions." + field,
                            "Unknown bounded extension namespace " + field + " is preserved only for review.",
                            false));
                }
            }
        }
        if (app.isObject())
        {
            var fields = app.fieldNames();
            while (fields.hasNext())
            {
                String field = fields.next();
                if (!RECOGNIZED_EXTENSION_KEYS.contains(field) && nonEmpty(app.get(field)))
                {
                    count++;
                    messages.add(message(InterchangeMessageSeverity.WARNING,
                            "SCLX_UNSUPPORTED_APPLICATION_EXTENSION",
                            "$.extensions.scaJakartaH2." + field,
                            "Unknown application extension " + field + " is not interpreted as canonical data.",
                            false));
                }
            }
        }
        return count;
    }

    private static void collectReferences(JsonNode node, Set<String> accounts, Set<String> funds)
    {
        if (node == null || node.isNull() || node.isMissingNode()) return;
        if (node.isObject())
        {
            var fields = node.fields();
            while (fields.hasNext())
            {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getValue().isTextual() && !field.getValue().textValue().isBlank())
                {
                    if (ACCOUNT_REFERENCE_FIELDS.contains(field.getKey())) accounts.add(field.getValue().textValue());
                    if (FUND_REFERENCE_FIELDS.contains(field.getKey())) funds.add(field.getValue().textValue());
                }
                collectReferences(field.getValue(), accounts, funds);
            }
        }
        else if (node.isArray())
        {
            node.forEach(value -> collectReferences(value, accounts, funds));
        }
    }

    private static long countReferences(JsonNode node, boolean identityContext)
    {
        if (node == null || node.isNull() || node.isMissingNode()) return 0L;
        if (node.isArray())
        {
            long count = 0L;
            for (JsonNode value : node) count += countReferences(value, identityContext);
            return count;
        }
        if (!node.isObject()) return 0L;
        long count = 0L;
        var fields = node.fields();
        while (fields.hasNext())
        {
            Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey();
            JsonNode value = field.getValue();
            boolean identity = isIdentityField(name);
            if (!identity && isReferenceField(name) && value.isTextual() && !value.textValue().isBlank()) count++;
            count += countReferences(value, identity);
        }
        return count;
    }

    private static boolean isIdentityField(String name)
    {
        return Set.of("organizationId", "accountId", "fundId", "budgetId", "lineId", "transactionId",
                "activityId", "counterpartyId", "merchantId", "supplementalDetailId", "bankId",
                "bankAccountId", "importBatchId", "statementLineId", "issueId", "reconciliationSessionId",
                "reconciliationMatchId", "assetId", "depreciationRunId", "itemId", "movementId",
                "rangeId", "eventId", "auditEventId").contains(name);
    }

    private static boolean isReferenceField(String name)
    {
        return name.endsWith("Id") && !isIdentityField(name)
                || Set.of("parentAccountId", "parentFundId", "correctionOfTransactionId").contains(name);
    }

    private static BigDecimal decimal(
            JsonNode node, String path, List<InterchangeValidationMessage> messages)
    {
        if (node == null || node.isNull()) return BigDecimal.ZERO;
        try
        {
            if (node.isNumber()) return node.decimalValue();
            if (node.isTextual()) return new BigDecimal(node.textValue().trim());
        }
        catch (NumberFormatException ex)
        {
            // handled below
        }
        messages.add(message(InterchangeMessageSeverity.ERROR, "SCLX_DECIMAL_REQUIRED", path,
                "Expected an exact decimal value.", true));
        return BigDecimal.ZERO;
    }

    private static LocalDate parseDate(
            JsonNode node, String path, List<InterchangeValidationMessage> messages)
    {
        if (node == null || !node.isTextual())
        {
            messages.add(message(InterchangeMessageSeverity.ERROR, "SCLX_DATE_REQUIRED", path,
                    "Expected an ISO local date.", true));
            return null;
        }
        try
        {
            return LocalDate.parse(node.textValue());
        }
        catch (DateTimeException ex)
        {
            messages.add(message(InterchangeMessageSeverity.ERROR, "SCLX_DATE_INVALID", path,
                    "Expected an ISO local date.", true));
            return null;
        }
    }

    private static String textOrPlaceholder(
            JsonNode node, String field, String placeholder,
            List<InterchangeValidationMessage> messages, String code)
    {
        String value = text(node, field);
        if (value != null) return value;
        messages.add(message(InterchangeMessageSeverity.ERROR, code, "$.organization." + field,
                "organization." + field + " is required and must be nonblank text.", true));
        return placeholder;
    }

    private static String text(JsonNode node, String field)
    {
        if (node == null || !node.isObject()) return null;
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.textValue().isBlank()
                ? value.textValue().trim() : null;
    }

    private static String normalizeAccountType(String value)
    {
        String normalized = normalizeToken(value);
        return "REVENUE".equals(normalized) ? "INCOME" : normalized;
    }

    private static String normalizeToken(String value)
    {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_').replace('/', '_');
    }

    private static String hash(JsonNode node)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical(node).getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String canonical(JsonNode node)
    {
        if (node == null || node.isNull()) return "null";
        if (node.isObject())
        {
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            node.fields().forEachRemaining(field -> sorted.put(field.getKey(), field.getValue()));
            StringBuilder value = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, JsonNode> field : sorted.entrySet())
            {
                if (!first) value.append(',');
                first = false;
                value.append(jsonString(field.getKey())).append(':').append(canonical(field.getValue()));
            }
            return value.append('}').toString();
        }
        if (node.isArray())
        {
            StringBuilder value = new StringBuilder("[");
            for (int index = 0; index < node.size(); index++)
            {
                if (index > 0) value.append(',');
                value.append(canonical(node.get(index)));
            }
            return value.append(']').toString();
        }
        if (node.isTextual()) return jsonString(node.textValue());
        if (node.isNumber()) return node.decimalValue().stripTrailingZeros().toPlainString();
        if (node.isBoolean()) return Boolean.toString(node.booleanValue());
        return jsonString(node.asText());
    }

    private static String jsonString(String value)
    {
        try
        {
            return JSON.writeValueAsString(value);
        }
        catch (java.io.IOException ex)
        {
            throw new IllegalStateException("Could not canonicalize JSON text", ex);
        }
    }

    private static InterchangeValidationMessage message(
            InterchangeMessageSeverity severity, String code, String path, String text, boolean blocking)
    {
        return new InterchangeValidationMessage(severity, code, path, text, blocking);
    }

    private static String pathOf(String message)
    {
        int space = message == null ? -1 : message.indexOf(' ');
        return space > 0 && message.charAt(0) == '$' ? message.substring(0, space) : "document";
    }

    private static boolean nonEmpty(JsonNode value)
    {
        return value != null && !value.isNull() && !value.isMissingNode()
                && (!value.isContainerNode() || value.size() > 0);
    }

    private static long elementCount(JsonNode value)
    {
        return value != null && value.isContainerNode() ? value.size() : 1L;
    }

    private static long arraySize(JsonNode value)
    {
        return value != null && value.isArray() ? value.size() : 0L;
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private record OrganizationData(
            String organizationId, String code, String name, String sourceSystem) { }
    private record IncomingEntity(
            String entityType, String externalId, String path, String normalizedHash, JsonNode value) { }
    private record Extraction(SclxImportPreviewCounts counts, List<IncomingEntity> entities) { }
    private record MappingResult(
            SclxAccountMode recommendedMode, List<SclxImportMappingRequirement> requirements) { }
    private record MappingKey(SclxImportMappingRequirement.Kind kind, String sourceId) { }
    private record TargetMappingKey(SclxImportMappingRequirement.Kind kind, String targetCode) { }
    private record TransactionResult(
            List<SclxImportTransactionPreview> previews, long zeroValueLines, long emptyTransactions) { }
    private record ArraySpec(String field, String idField, String entityType, String countKey) { }
}
