package org.nonprofitbookkeeping.interchange.coa;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.interchange.InterchangeConfirmation;
import org.nonprofitbookkeeping.interchange.InterchangeMessageSeverity;
import org.nonprofitbookkeeping.interchange.InterchangeOperationCounts;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountClassification;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.CompanyOwnershipService;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Parses, previews, and deterministically exports the governed Chart of Accounts JSON formats.
 * Preview is non-mutating; commit support is added only through the later atomic import boundary.
 */
public final class ChartOfAccountsJsonService
{
    private static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_ACCOUNT_COUNT = 100_000;
    private static final int MAX_CODE_POINTS = 64;
    private static final int MAX_NAME_POINTS = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 1024 * 1024;
    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

    private static final Set<String> DONOR_ROOT_FIELDS = Set.of(
            "chartOfAccounts", "rootAccounts", "accountNames", "accounts");
    private static final Set<String> DONOR_ACCOUNT_FIELDS = Set.of(
            "associatedFundIds", "accountNumber", "increaseSide", "name", "accountCode",
            "accountType", "parentAccountId", "currency", "openingBalance",
            "supplementalLineKinds", "effectiveIncreaseSide");
    private static final Set<String> SCA_ROOT_FIELDS = Set.of("format", "version", "chart", "accounts");
    private static final Set<String> SCA_CHART_FIELDS = Set.of("name", "chartVersion", "status", "currency");
    private static final Set<String> SCA_ACCOUNT_FIELDS = Set.of(
            "code", "name", "type", "subtype", "normalBalance", "parentCode", "posting",
            "active", "effectiveFrom", "effectiveTo", "openingBalance", "description");

    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;
    private final CompanyOwnershipService ownership;
    private final ObjectMapper mapper;

    public ChartOfAccountsJsonService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.ownership = new CompanyOwnershipService(jpa);

        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(32)
                        .maxStringLength(MAX_DESCRIPTION_LENGTH)
                        .maxNumberLength(128)
                        .build())
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.mapper = new ObjectMapper(factory)
                .registerModule(new JavaTimeModule())
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    /** Writes deterministic SCA-COA 1.0 JSON for the selected company's active chart. */
    public CoaExportResult exportActiveChart(Path requestedDestination)
    {
        Objects.requireNonNull(requestedDestination, "requestedDestination");
        ownership.requireNoOpenOwnershipIssues();
        Path destination = requestedDestination.toAbsolutePath().normalize();
        if (Files.exists(destination))
        {
            throw new IllegalArgumentException("Chart of Accounts JSON destination already exists: " + destination);
        }
        createParent(destination);

        ExportSnapshot snapshot;
        try (EntityManager em = jpa.em())
        {
            Company company = ownership.requireCompany(em, companyCodeSupplier.get());
            ChartOfAccounts chart = requireActiveChart(em, company);
            ownership.ensureOwnedBy(em, company, chart, "Active Chart of Accounts");
            List<Account> accounts = em.createQuery("""
                    select a from Account a
                    left join fetch a.parent
                    where a.chart = :chart
                    order by a.code
                    """, Account.class)
                    .setParameter("chart", chart)
                    .getResultList();
            snapshot = new ExportSnapshot(
                    chart.getName(),
                    chart.getVersion(),
                    chart.getStatus(),
                    company.getDefaultCurrency(),
                    parentBeforeChild(accounts));
        }

        byte[] output = exportBytes(snapshot);
        Path temporary = destination.resolveSibling(destination.getFileName() + ".partial");
        deleteQuietly(temporary);
        try
        {
            Files.write(temporary, output);
            moveAtomically(temporary, destination);
            return new CoaExportResult(
                    destination,
                    output.length,
                    sha256(output),
                    1L,
                    snapshot.accounts().size());
        }
        catch (IOException ex)
        {
            deleteQuietly(temporary);
            throw new IllegalStateException("Could not export Chart of Accounts JSON: " + ex.getMessage(), ex);
        }
    }

    /** Reads and validates a complete import graph without modifying H2. */
    public CoaImportPreview preview(CoaImportRequest request)
    {
        Objects.requireNonNull(request, "request");
        ownership.requireNoOpenOwnershipIssues();

        SourceBytes source = readSource(request.sourceFile());
        List<InterchangeValidationMessage> messages = new ArrayList<>();
        if (source.bomStripped())
        {
            messages.add(warning(
                    "COA_UTF8_BOM",
                    "$",
                    "A UTF-8 BOM was stripped before parsing."));
        }

        CoaChartData parsed;
        try
        {
            JsonNode root = mapper.readTree(source.jsonText());
            if (root == null || !root.isObject())
            {
                throw new IllegalArgumentException("Chart of Accounts JSON root must be an object.");
            }
            parsed = parseDocument((ObjectNode) root, messages);
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Malformed Chart of Accounts JSON: " + ex.getMessage(), ex);
        }

        if (parsed.accounts().size() > MAX_ACCOUNT_COUNT)
        {
            messages.add(error(
                    "COA_ACCOUNT_LIMIT",
                    "$.accounts",
                    "Chart of Accounts JSON contains " + parsed.accounts().size()
                            + " accounts; the supported maximum is " + MAX_ACCOUNT_COUNT + "."));
        }

        try (EntityManager em = jpa.em())
        {
            Company company = ownership.requireCompany(em, companyCodeSupplier.get());
            TargetContext target = targetContext(em, company, request, messages);
            List<CoaPreviewItem> items = validateAndClassify(
                    em, company, target, parsed, request, messages);
            List<InterchangeConfirmation> confirmations = confirmations(items, request);
            long warnings = messages.stream()
                    .filter(message -> message.severity() == InterchangeMessageSeverity.WARNING)
                    .count();
            long errors = messages.stream().filter(InterchangeValidationMessage::blocking).count();
            long created = items.stream()
                    .filter(item -> item.disposition() == CoaPreviewItem.Disposition.CREATE)
                    .count();
            long updated = items.stream()
                    .filter(item -> item.disposition() == CoaPreviewItem.Disposition.UPDATE)
                    .count();
            long identical = items.stream()
                    .filter(item -> item.disposition() == CoaPreviewItem.Disposition.IDENTICAL)
                    .count();
            long skipped = items.stream()
                    .filter(item -> item.disposition() == CoaPreviewItem.Disposition.BLOCKED)
                    .count();
            InterchangeOperationCounts counts = new InterchangeOperationCounts(
                    items.size(), created, updated, identical, skipped, warnings, errors);
            return new CoaImportPreview(
                    request.sourceFile(),
                    source.sha256(),
                    source.originalBytes().length,
                    request,
                    parsed,
                    target.label(),
                    items,
                    messages,
                    confirmations,
                    counts);
        }
    }

    private CoaChartData parseDocument(ObjectNode root, List<InterchangeValidationMessage> messages)
    {
        if (root.has("format"))
        {
            return parseScaDocument(root, messages);
        }
        if (root.has("chartOfAccounts"))
        {
            return parseDonorDocument(root, messages);
        }
        throw new IllegalArgumentException(
                "Unrecognized Chart of Accounts JSON. Expected donor chartOfAccounts or SCA-COA format/version.");
    }

    private CoaChartData parseScaDocument(ObjectNode root, List<InterchangeValidationMessage> messages)
    {
        warnUnknownFields(root, SCA_ROOT_FIELDS, "$", messages);
        String format = requiredText(root, "format", "$.format");
        String version = requiredText(root, "version", "$.version");
        if (!"SCA-COA".equals(format))
        {
            throw new IllegalArgumentException("Unsupported Chart of Accounts JSON format: " + format);
        }
        if (!"1.0".equals(version))
        {
            throw new IllegalArgumentException("Unsupported SCA-COA version: " + version);
        }
        JsonNode chartNode = root.get("chart");
        if (chartNode == null || !chartNode.isObject())
        {
            throw new IllegalArgumentException("$.chart must be an object.");
        }
        ObjectNode chartObject = (ObjectNode) chartNode;
        warnUnknownFields(chartObject, SCA_CHART_FIELDS, "$.chart", messages);
        String name = requiredText(chartObject, "name", "$.chart.name");
        String chartVersion = requiredText(chartObject, "chartVersion", "$.chart.chartVersion");
        ChartStatus status = parseEnum(
                ChartStatus.class,
                requiredText(chartObject, "status", "$.chart.status"),
                "$.chart.status");
        String currency = requiredText(chartObject, "currency", "$.chart.currency").toUpperCase(Locale.ROOT);
        ArrayNode accountArray = requiredArray(root, "accounts", "$.accounts");
        List<CoaAccountData> accounts = new ArrayList<>(accountArray.size());
        for (int index = 0; index < accountArray.size(); index++)
        {
            JsonNode node = accountArray.get(index);
            if (!node.isObject())
            {
                throw new IllegalArgumentException("$.accounts[" + index + "] must be an object.");
            }
            accounts.add(parseScaAccount((ObjectNode) node, index, currency, messages));
        }
        return new CoaChartData(
                CoaChartData.SourceFamily.SCA_COA_1_0,
                version,
                name,
                chartVersion,
                status,
                currency,
                accounts);
    }

    private CoaAccountData parseScaAccount(
            ObjectNode node,
            int index,
            String currency,
            List<InterchangeValidationMessage> messages)
    {
        String path = "$.accounts[" + index + "]";
        Map<String, String> unknown = warnUnknownFields(node, SCA_ACCOUNT_FIELDS, path, messages);
        String code = requiredText(node, "code", path + ".code");
        TypeMapping mapping = portableType(requiredText(node, "type", path + ".type"), path + ".type");
        return new CoaAccountData(
                code,
                code,
                requiredText(node, "name", path + ".name"),
                mapping.type(),
                mapping.function(),
                optionalEnum(AccountSubtype.class, node.get("subtype"), path + ".subtype"),
                parseEnum(
                        NormalBalance.class,
                        requiredText(node, "normalBalance", path + ".normalBalance"),
                        path + ".normalBalance"),
                optionalText(node.get("parentCode")),
                optionalBoolean(node, "posting", true, path + ".posting"),
                optionalBoolean(node, "active", true, path + ".active"),
                optionalDate(node.get("effectiveFrom"), path + ".effectiveFrom"),
                optionalDate(node.get("effectiveTo"), path + ".effectiveTo"),
                decimal(node.get("openingBalance"), path + ".openingBalance"),
                optionalText(node.get("description")),
                currency,
                List.of(),
                List.of(),
                unknown);
    }

    private CoaChartData parseDonorDocument(ObjectNode root, List<InterchangeValidationMessage> messages)
    {
        warnUnknownFields(root, DONOR_ROOT_FIELDS, "$", messages);
        ArrayNode accountArray = requiredArray(root, "chartOfAccounts", "$.chartOfAccounts");
        List<CoaAccountData> accounts = new ArrayList<>(accountArray.size());
        String currency = "";
        for (int index = 0; index < accountArray.size(); index++)
        {
            JsonNode node = accountArray.get(index);
            if (!node.isObject())
            {
                throw new IllegalArgumentException("$.chartOfAccounts[" + index + "] must be an object.");
            }
            CoaAccountData parsed = parseDonorAccount((ObjectNode) node, index, messages);
            accounts.add(parsed);
            if (currency.isEmpty() && parsed.currency() != null)
            {
                currency = parsed.currency();
            }
        }

        Set<String> parentCodes = accounts.stream()
                .map(CoaAccountData::parentCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        accounts = accounts.stream()
                .map(account -> parentCodes.contains(account.code())
                        ? copyWithPosting(account, false)
                        : account)
                .toList();

        validateDonorRedundancy(root, accountArray, accounts, messages);
        messages.add(warning(
                "COA_DONOR_REDUNDANT_SHAPE",
                "$",
                "Donor compatibility input contains derived rootAccounts/accounts/accountNames fields; only chartOfAccounts is imported."));
        return new CoaChartData(
                CoaChartData.SourceFamily.DONOR_COMPATIBILITY,
                "unversioned",
                "Imported donor chart",
                "donor",
                ChartStatus.DRAFT,
                currency,
                accounts);
    }

    private CoaAccountData parseDonorAccount(
            ObjectNode node,
            int index,
            List<InterchangeValidationMessage> messages)
    {
        String path = "$.chartOfAccounts[" + index + "]";
        Map<String, String> unknown = warnUnknownFields(node, DONOR_ACCOUNT_FIELDS, path, messages);
        String sourceCode = requiredText(node, "accountNumber", path + ".accountNumber");
        String donorType = requiredText(node, "accountType", path + ".accountType");
        TypeMapping mapping = donorType(donorType, path + ".accountType");
        NormalBalance increase = parseEnum(
                NormalBalance.class,
                requiredText(node, "increaseSide", path + ".increaseSide"),
                path + ".increaseSide");
        String effective = optionalText(node.get("effectiveIncreaseSide"));
        if (effective != null && !increase.name().equalsIgnoreCase(effective))
        {
            messages.add(error(
                    "COA_DONOR_EFFECTIVE_SIDE",
                    path + ".effectiveIncreaseSide",
                    "effectiveIncreaseSide does not agree with increaseSide for account " + sourceCode + "."));
        }

        List<String> fundIds = stringArray(node.get("associatedFundIds"), path + ".associatedFundIds");
        if (!fundIds.isEmpty())
        {
            messages.add(warning(
                    "COA_UNSUPPORTED_FUND_IDS",
                    path + ".associatedFundIds",
                    "Account " + sourceCode + " has associatedFundIds that are not persisted by the current Account model."));
        }
        List<String> supplemental = stringArray(
                node.get("supplementalLineKinds"),
                path + ".supplementalLineKinds");
        if (!supplemental.isEmpty())
        {
            messages.add(warning(
                    "COA_UNSUPPORTED_SUPPLEMENTAL_KINDS",
                    path + ".supplementalLineKinds",
                    "Account " + sourceCode + " has supplementalLineKinds that are not persisted by the current Account model."));
        }
        String accountCode = optionalText(node.get("accountCode"));
        if (accountCode != null)
        {
            messages.add(warning(
                    "COA_UNSUPPORTED_ACCOUNT_CODE",
                    path + ".accountCode",
                    "Donor accountCode " + accountCode + " is retained only as compatibility metadata."));
        }

        Map<String, String> unsupported = new LinkedHashMap<>(unknown);
        if (accountCode != null)
        {
            unsupported.put("accountCode", accountCode);
        }
        return new CoaAccountData(
                sourceCode,
                sourceCode,
                requiredText(node, "name", path + ".name"),
                mapping.type(),
                mapping.function(),
                mapping.subtype(),
                increase,
                optionalText(node.get("parentAccountId")),
                true,
                true,
                null,
                null,
                decimal(node.get("openingBalance"), path + ".openingBalance"),
                null,
                optionalText(node.get("currency")),
                fundIds,
                supplemental,
                unsupported);
    }

    private List<CoaPreviewItem> validateAndClassify(
            EntityManager em,
            Company company,
            TargetContext target,
            CoaChartData chart,
            CoaImportRequest request,
            List<InterchangeValidationMessage> messages)
    {
        if (!chart.currency().isBlank()
                && !chart.currency().equalsIgnoreCase(company.getDefaultCurrency()))
        {
            messages.add(error(
                    "COA_CURRENCY_MISMATCH",
                    "$.chart.currency",
                    "Source currency " + chart.currency() + " does not match company currency "
                            + company.getDefaultCurrency() + "."));
        }

        Map<String, String> effectiveCodes = new LinkedHashMap<>();
        Set<String> targetCodes = new LinkedHashSet<>();
        for (int index = 0; index < chart.accounts().size(); index++)
        {
            CoaAccountData account = chart.accounts().get(index);
            String targetCode = targetCode(account, request, index, messages);
            effectiveCodes.put(account.sourceCode(), targetCode);
            if (!targetCode.isBlank() && !targetCodes.add(targetCode))
            {
                messages.add(error(
                        "COA_DUPLICATE_TARGET_CODE",
                        "$.accounts[" + index + "].code",
                        "More than one source account maps to target code " + targetCode + "."));
            }
            validateBasic(account, targetCode, index, messages);
        }

        Map<String, ExistingAccount> existing = target.existingAccounts();
        Map<String, String> parentByTarget = new LinkedHashMap<>();
        for (int index = 0; index < chart.accounts().size(); index++)
        {
            CoaAccountData account = chart.accounts().get(index);
            String targetCode = effectiveCodes.get(account.sourceCode());
            String parentTarget = account.parentCode() == null
                    ? null
                    : effectiveCodes.getOrDefault(account.parentCode(), account.parentCode());
            parentByTarget.put(targetCode, parentTarget);
            if (parentTarget != null
                    && !targetCodes.contains(parentTarget)
                    && !existing.containsKey(parentTarget))
            {
                messages.add(error(
                        "COA_MISSING_PARENT",
                        "$.accounts[" + index + "].parentCode",
                        "Parent account " + parentTarget + " does not exist in the source graph or target chart."));
            }
        }
        validateCycles(parentByTarget, messages);
        validatePostingParents(chart.accounts(), effectiveCodes, parentByTarget, messages);

        List<CoaPreviewItem> items = new ArrayList<>();
        for (int index = 0; index < chart.accounts().size(); index++)
        {
            CoaAccountData account = chart.accounts().get(index);
            String targetCode = effectiveCodes.get(account.sourceCode());
            ExistingAccount current = existing.get(targetCode);
            if (targetCode == null || targetCode.isBlank())
            {
                items.add(new CoaPreviewItem(
                        account, "", CoaPreviewItem.Disposition.BLOCKED, null, false));
                continue;
            }
            if (current == null)
            {
                items.add(new CoaPreviewItem(
                        account, targetCode, CoaPreviewItem.Disposition.CREATE, null, false));
                continue;
            }

            String parentTarget = parentByTarget.get(targetCode);
            boolean identical = identical(current, account, parentTarget);
            if (identical)
            {
                items.add(new CoaPreviewItem(
                        account,
                        targetCode,
                        CoaPreviewItem.Disposition.IDENTICAL,
                        current.id(),
                        current.hasHistory()));
                continue;
            }

            boolean structuralConflict = current.hasHistory()
                    && structuralChange(current, account, parentTarget);
            boolean openingConflict = current.hasHistory()
                    && current.openingBalance().compareTo(account.openingBalance()) != 0;
            if (structuralConflict || openingConflict)
            {
                if (structuralConflict)
                {
                    messages.add(error(
                            "COA_HISTORY_STRUCTURAL_CONFLICT",
                            "$.accounts[" + index + "]",
                            "Account " + targetCode
                                    + " has transaction history and cannot change type, normal balance, parent, or posting state through Chart of Accounts JSON."));
                }
                if (openingConflict)
                {
                    messages.add(error(
                            "COA_HISTORY_OPENING_BALANCE_CONFLICT",
                            "$.accounts[" + index + "].openingBalance",
                            "Account " + targetCode
                                    + " has transaction history and its opening balance differs from the imported value."));
                }
                items.add(new CoaPreviewItem(
                        account,
                        targetCode,
                        CoaPreviewItem.Disposition.BLOCKED,
                        current.id(),
                        true));
            }
            else
            {
                items.add(new CoaPreviewItem(
                        account,
                        targetCode,
                        CoaPreviewItem.Disposition.UPDATE,
                        current.id(),
                        current.hasHistory()));
            }
        }
        return items;
    }

    private TargetContext targetContext(
            EntityManager em,
            Company company,
            CoaImportRequest request,
            List<InterchangeValidationMessage> messages)
    {
        if (request.mode() == CoaImportMode.CREATE_NEW_CHART)
        {
            if (request.targetChartName().isBlank())
            {
                messages.add(error(
                        "COA_TARGET_CHART_NAME",
                        "target.chartName",
                        "A target chart name is required for CREATE_NEW_CHART."));
            }
            if (request.targetChartVersion().isBlank())
            {
                messages.add(error(
                        "COA_TARGET_CHART_VERSION",
                        "target.chartVersion",
                        "A target chart version is required for CREATE_NEW_CHART."));
            }
            if (!request.targetChartName().isBlank() && !request.targetChartVersion().isBlank())
            {
                Long conflicts = em.createQuery("""
                        select count(c) from ChartOfAccounts c
                        where c.company = :company
                          and lower(c.name) = :name
                          and c.version = :version
                        """, Long.class)
                        .setParameter("company", company)
                        .setParameter("name", request.targetChartName().toLowerCase(Locale.ROOT))
                        .setParameter("version", request.targetChartVersion())
                        .getSingleResult();
                if (conflicts > 0)
                {
                    messages.add(error(
                            "COA_TARGET_CHART_CONFLICT",
                            "target",
                            "A chart named " + request.targetChartName() + " version "
                                    + request.targetChartVersion() + " already exists for this company."));
                }
            }
            return new TargetContext(
                    null,
                    "New chart " + request.targetChartName() + " / " + request.targetChartVersion(),
                    Map.of());
        }

        ChartOfAccounts chart = requireActiveChart(em, company);
        ownership.ensureOwnedBy(em, company, chart, "Active Chart of Accounts");
        List<Account> accounts = em.createQuery("""
                select a from Account a
                left join fetch a.parent
                where a.chart = :chart
                order by a.code
                """, Account.class)
                .setParameter("chart", chart)
                .getResultList();
        Map<String, ExistingAccount> existing = new LinkedHashMap<>();
        for (Account account : accounts)
        {
            Long historyCount = em.createQuery(
                    "select count(s) from TxnSplit s where s.account = :account",
                    Long.class)
                    .setParameter("account", account)
                    .getSingleResult();
            existing.put(account.getCode(), new ExistingAccount(
                    account.getId(),
                    account.getCode(),
                    account.getName(),
                    account.getAccountType(),
                    account.getAccountFunction(),
                    account.getSubtype(),
                    account.getNormalBalance(),
                    account.getParent() == null ? null : account.getParent().getCode(),
                    account.isPosting(),
                    account.isActive(),
                    account.getEffectiveFrom(),
                    account.getEffectiveTo(),
                    account.getOpeningBalance(),
                    account.getDescription(),
                    historyCount > 0));
        }
        return new TargetContext(
                chart,
                "Active chart " + chart.getName() + " / " + chart.getVersion(),
                Map.copyOf(existing));
    }

    private static List<InterchangeConfirmation> confirmations(
            List<CoaPreviewItem> items,
            CoaImportRequest request)
    {
        boolean openingBalanceChange = items.stream()
                .filter(item -> item.disposition() == CoaPreviewItem.Disposition.CREATE
                        || item.disposition() == CoaPreviewItem.Disposition.UPDATE)
                .map(CoaPreviewItem::account)
                .anyMatch(account -> account.openingBalance().compareTo(BigDecimal.ZERO) != 0);
        if (!openingBalanceChange)
        {
            return List.of();
        }
        return List.of(new InterchangeConfirmation(
                "COA_OPENING_BALANCES",
                "Import opening-balance fields without creating accounting transactions.",
                true,
                request.confirmOpeningBalances()));
    }

    private byte[] exportBytes(ExportSnapshot snapshot)
    {
        ObjectNode root = mapper.createObjectNode();
        root.put("format", "SCA-COA");
        root.put("version", "1.0");
        ObjectNode chart = root.putObject("chart");
        chart.put("name", snapshot.name());
        chart.put("chartVersion", snapshot.version());
        chart.put("status", snapshot.status().name());
        chart.put("currency", snapshot.currency());
        ArrayNode accounts = root.putArray("accounts");
        for (Account account : snapshot.accounts())
        {
            ObjectNode node = accounts.addObject();
            node.put("code", account.getCode());
            node.put("name", account.getName());
            node.put("type", AccountClassification.portableType(account));
            if (account.getSubtype() != null)
            {
                node.put("subtype", account.getSubtype().name());
            }
            node.put("normalBalance", account.getNormalBalance().name());
            if (account.getParent() != null)
            {
                node.put("parentCode", account.getParent().getCode());
            }
            node.put("posting", account.isPosting());
            node.put("active", account.isActive());
            if (account.getEffectiveFrom() != null)
            {
                node.put("effectiveFrom", account.getEffectiveFrom().toString());
            }
            if (account.getEffectiveTo() != null)
            {
                node.put("effectiveTo", account.getEffectiveTo().toString());
            }
            node.put("openingBalance", account.getOpeningBalance().setScale(2).toPlainString());
            if (account.getDescription() != null && !account.getDescription().isBlank())
            {
                node.put("description", account.getDescription());
            }
        }
        try
        {
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
                    .replace("\r\n", "\n")
                    .replace('\r', '\n');
            if (!json.endsWith("\n"))
            {
                json += "\n";
            }
            return json.getBytes(StandardCharsets.UTF_8);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not serialize Chart of Accounts JSON.", ex);
        }
    }

    private static SourceBytes readSource(Path sourceFile)
    {
        Path source = sourceFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source))
        {
            throw new IllegalArgumentException("Chart of Accounts JSON source does not exist: " + source);
        }
        try
        {
            long size = Files.size(source);
            if (size <= 0 || size > MAX_FILE_BYTES)
            {
                throw new IllegalArgumentException(
                        "Chart of Accounts JSON size is outside the supported range: " + size + " bytes.");
            }
            byte[] original = Files.readAllBytes(source);
            boolean bom = original.length >= 3
                    && (original[0] & 0xff) == 0xef
                    && (original[1] & 0xff) == 0xbb
                    && (original[2] & 0xff) == 0xbf;
            byte[] jsonBytes = bom ? Arrays.copyOfRange(original, 3, original.length) : original;
            String text;
            try
            {
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(jsonBytes))
                        .toString();
            }
            catch (CharacterCodingException ex)
            {
                throw new IllegalArgumentException("Chart of Accounts JSON must be valid UTF-8.", ex);
            }
            return new SourceBytes(original, text, sha256(original), bom);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not read Chart of Accounts JSON: " + source, ex);
        }
    }

    private static String targetCode(
            CoaAccountData account,
            CoaImportRequest request,
            int index,
            List<InterchangeValidationMessage> messages)
    {
        if (request.mode() != CoaImportMode.MAP_CODES)
        {
            return account.code();
        }
        String mapped = request.codeMappings().get(account.sourceCode());
        if (mapped == null || mapped.isBlank())
        {
            messages.add(error(
                    "COA_UNMAPPED_CODE",
                    "$.accounts[" + index + "].code",
                    "MAP_CODES requires a target code for source account " + account.sourceCode() + "."));
            return "";
        }
        return mapped.trim();
    }

    private static void validateBasic(
            CoaAccountData account,
            String targetCode,
            int index,
            List<InterchangeValidationMessage> messages)
    {
        String path = "$.accounts[" + index + "]";
        if (targetCode == null || targetCode.isBlank())
        {
            messages.add(error("COA_CODE_REQUIRED", path + ".code", "Account code is required."));
        }
        else if (targetCode.codePointCount(0, targetCode.length()) > MAX_CODE_POINTS)
        {
            messages.add(error(
                    "COA_CODE_LENGTH",
                    path + ".code",
                    "Account code exceeds " + MAX_CODE_POINTS + " Unicode code points."));
        }
        if (account.name().isBlank())
        {
            messages.add(error("COA_NAME_REQUIRED", path + ".name", "Account name is required."));
        }
        else if (account.name().codePointCount(0, account.name().length()) > MAX_NAME_POINTS)
        {
            messages.add(error(
                    "COA_NAME_LENGTH",
                    path + ".name",
                    "Account name exceeds " + MAX_NAME_POINTS + " Unicode code points."));
        }
        if (account.description() != null && account.description().length() > MAX_DESCRIPTION_LENGTH)
        {
            messages.add(error(
                    "COA_DESCRIPTION_LENGTH",
                    path + ".description",
                    "Account description exceeds the 1 MiB input limit."));
        }
        if (account.type() == null)
        {
            messages.add(error("COA_TYPE_REQUIRED", path + ".type", "Account type is required."));
        }
        if (account.normalBalance() == null)
        {
            messages.add(error(
                    "COA_NORMAL_BALANCE_REQUIRED",
                    path + ".normalBalance",
                    "Normal balance is required."));
        }
        else if (!normalBalanceCompatible(account.type(), account.normalBalance()))
        {
            messages.add(error(
                    "COA_NORMAL_BALANCE_INCOMPATIBLE",
                    path + ".normalBalance",
                    "Normal balance " + account.normalBalance() + " is incompatible with account type "
                            + account.type() + "."));
        }
        if (!subtypeCompatible(account.type(), account.subtype()))
        {
            messages.add(error(
                    "COA_SUBTYPE_INCOMPATIBLE",
                    path + ".subtype",
                    "Subtype " + account.subtype() + " is incompatible with account type " + account.type() + "."));
        }
        if (account.effectiveFrom() != null && !dateInRange(account.effectiveFrom()))
        {
            messages.add(error(
                    "COA_EFFECTIVE_FROM_RANGE",
                    path + ".effectiveFrom",
                    "effectiveFrom must be between " + MIN_DATE + " and " + MAX_DATE + "."));
        }
        if (account.effectiveTo() != null && !dateInRange(account.effectiveTo()))
        {
            messages.add(error(
                    "COA_EFFECTIVE_TO_RANGE",
                    path + ".effectiveTo",
                    "effectiveTo must be between " + MIN_DATE + " and " + MAX_DATE + "."));
        }
        if (account.effectiveFrom() != null && account.effectiveTo() != null
                && account.effectiveTo().isBefore(account.effectiveFrom()))
        {
            messages.add(error(
                    "COA_EFFECTIVE_DATE_ORDER",
                    path,
                    "effectiveTo cannot be before effectiveFrom."));
        }
        try
        {
            account.openingBalance().setScale(2, RoundingMode.UNNECESSARY);
            if (account.openingBalance().precision() > 19)
            {
                throw new ArithmeticException("precision");
            }
        }
        catch (ArithmeticException ex)
        {
            messages.add(error(
                    "COA_OPENING_BALANCE_PRECISION",
                    path + ".openingBalance",
                    "Opening balance must fit DECIMAL(19,2) without rounding."));
        }
    }

    private static void validateCycles(
            Map<String, String> parentByCode,
            List<InterchangeValidationMessage> messages)
    {
        Set<String> complete = new HashSet<>();
        for (String code : parentByCode.keySet())
        {
            if (complete.contains(code))
            {
                continue;
            }
            Set<String> path = new LinkedHashSet<>();
            String current = code;
            while (current != null && parentByCode.containsKey(current))
            {
                if (!path.add(current))
                {
                    messages.add(error(
                            "COA_HIERARCHY_CYCLE",
                            "$.accounts",
                            "Account hierarchy contains a cycle involving " + current + "."));
                    return;
                }
                if (complete.contains(current))
                {
                    break;
                }
                current = parentByCode.get(current);
            }
            complete.addAll(path);
        }
    }

    private static void validatePostingParents(
            List<CoaAccountData> accounts,
            Map<String, String> effectiveCodes,
            Map<String, String> parentByTarget,
            List<InterchangeValidationMessage> messages)
    {
        Map<String, CoaAccountData> byTarget = new HashMap<>();
        for (CoaAccountData account : accounts)
        {
            byTarget.put(effectiveCodes.get(account.sourceCode()), account);
        }
        for (Map.Entry<String, String> entry : parentByTarget.entrySet())
        {
            String parentCode = entry.getValue();
            CoaAccountData parent = byTarget.get(parentCode);
            if (parent != null && parent.posting())
            {
                messages.add(error(
                        "COA_POSTING_PARENT",
                        "$.accounts",
                        "Account " + parentCode + " is marked posting but also has child account "
                                + entry.getKey() + "."));
            }
        }
    }

    private static void validateDonorRedundancy(
            ObjectNode root,
            ArrayNode authoritative,
            List<CoaAccountData> accounts,
            List<InterchangeValidationMessage> messages)
    {
        JsonNode duplicate = root.get("accounts");
        if (duplicate != null && !duplicate.equals(authoritative))
        {
            messages.add(error(
                    "COA_DONOR_ACCOUNTS_MISMATCH",
                    "$.accounts",
                    "Donor accounts does not match authoritative chartOfAccounts."));
        }
        JsonNode names = root.get("accountNames");
        if (names != null)
        {
            String expected = accounts.stream().map(CoaAccountData::name).collect(Collectors.joining(", "));
            if (!names.isTextual() || !expected.equals(names.textValue()))
            {
                messages.add(error(
                        "COA_DONOR_NAMES_MISMATCH",
                        "$.accountNames",
                        "Donor accountNames does not match chartOfAccounts names."));
            }
        }
        JsonNode roots = root.get("rootAccounts");
        if (roots != null)
        {
            if (!roots.isArray())
            {
                messages.add(error(
                        "COA_DONOR_ROOTS_TYPE",
                        "$.rootAccounts",
                        "Donor rootAccounts must be an array when present."));
            }
            else
            {
                Set<String> expected = accounts.stream()
                        .filter(account -> account.parentCode() == null)
                        .map(CoaAccountData::sourceCode)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                Set<String> actual = new LinkedHashSet<>();
                for (JsonNode node : roots)
                {
                    if (node.isObject() && node.hasNonNull("accountNumber"))
                    {
                        actual.add(node.get("accountNumber").asText());
                    }
                }
                if (!expected.equals(actual))
                {
                    messages.add(error(
                            "COA_DONOR_ROOTS_MISMATCH",
                            "$.rootAccounts",
                            "Donor rootAccounts does not match root accounts in chartOfAccounts."));
                }
            }
        }
    }

    private static boolean identical(ExistingAccount current, CoaAccountData source, String parentTarget)
    {
        return Objects.equals(current.name(), source.name())
                && current.type() == source.type()
                && current.function() == source.function()
                && current.subtype() == source.subtype()
                && current.normalBalance() == source.normalBalance()
                && Objects.equals(current.parentCode(), parentTarget)
                && current.posting() == source.posting()
                && current.active() == source.active()
                && Objects.equals(current.effectiveFrom(), source.effectiveFrom())
                && Objects.equals(current.effectiveTo(), source.effectiveTo())
                && current.openingBalance().compareTo(source.openingBalance()) == 0
                && Objects.equals(normalizeOptional(current.description()), normalizeOptional(source.description()));
    }

    private static boolean structuralChange(
            ExistingAccount current,
            CoaAccountData source,
            String parentTarget)
    {
        return current.type() != source.type()
                || current.function() != source.function()
                || current.subtype() != source.subtype()
                || current.normalBalance() != source.normalBalance()
                || !Objects.equals(current.parentCode(), parentTarget)
                || current.posting() != source.posting();
    }

    private static boolean normalBalanceCompatible(AccountType type, NormalBalance balance)
    {
        if (type == null || balance == null)
        {
            return false;
        }
        return switch (type)
        {
            case ASSET, EXPENSE -> balance == NormalBalance.DEBIT;
            case LIABILITY, EQUITY, INCOME -> balance == NormalBalance.CREDIT;
        };
    }

    private static boolean subtypeCompatible(AccountType type, AccountSubtype subtype)
    {
        if (subtype == null || type == null)
        {
            return true;
        }
        return switch (subtype)
        {
            case RECEIVABLE, PREPAID, INVENTORY, FIXED_ASSET, OTHER_ASSET -> type == AccountType.ASSET;
            case PAYABLE, DEFERRED_REVENUE, OTHER_LIABILITY -> type == AccountType.LIABILITY;
            case CASH -> type == AccountType.ASSET;
        };
    }

    private static TypeMapping portableType(String value, String path)
    {
        try
        {
            AccountClassification.PortableType parsed = AccountClassification.parsePortableType(value);
            return new TypeMapping(parsed.type(), parsed.function(), null);
        }
        catch (RuntimeException ex)
        {
            throw new IllegalArgumentException(path + " has unsupported account type " + value + ".", ex);
        }
    }

    private static TypeMapping donorType(String value, String path)
    {
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized)
        {
            case "ASSET" -> new TypeMapping(AccountType.ASSET, null, null);
            case "LIABILITY" -> new TypeMapping(AccountType.LIABILITY, null, null);
            case "EQUITY", "NET_ASSET", "NET_ASSETS" -> new TypeMapping(AccountType.EQUITY, null, null);
            case "INCOME", "REVENUE" -> new TypeMapping(AccountType.INCOME, null, null);
            case "EXPENSE" -> new TypeMapping(AccountType.EXPENSE, null, null);
            case "BANK", "CHECKING", "SAVINGS", "CASH" -> new TypeMapping(AccountType.ASSET, AccountFunction.BANK, AccountSubtype.CASH);
            case "RECEIVABLE" -> new TypeMapping(AccountType.ASSET, null, AccountSubtype.RECEIVABLE);
            case "PAYABLE" -> new TypeMapping(AccountType.LIABILITY, null, AccountSubtype.PAYABLE);
            case "PREPAID" -> new TypeMapping(AccountType.ASSET, null, AccountSubtype.PREPAID);
            case "DEFERRED_REVENUE" -> new TypeMapping(AccountType.LIABILITY, null, AccountSubtype.DEFERRED_REVENUE);
            case "INVENTORY" -> new TypeMapping(AccountType.ASSET, null, AccountSubtype.INVENTORY);
            case "FIXED_ASSET" -> new TypeMapping(AccountType.ASSET, null, AccountSubtype.FIXED_ASSET);
            case "OTHER_ASSET" -> new TypeMapping(AccountType.ASSET, null, AccountSubtype.OTHER_ASSET);
            case "OTHER_LIABILITY" -> new TypeMapping(AccountType.LIABILITY, null, AccountSubtype.OTHER_LIABILITY);
            default -> throw new IllegalArgumentException(path + " has unsupported donor account type " + value + ".");
        };
    }

    private static CoaAccountData copyWithPosting(CoaAccountData account, boolean posting)
    {
        return new CoaAccountData(
                account.sourceCode(),
                account.code(),
                account.name(),
                account.type(),
                account.function(),
                account.subtype(),
                account.normalBalance(),
                account.parentCode(),
                posting,
                account.active(),
                account.effectiveFrom(),
                account.effectiveTo(),
                account.openingBalance(),
                account.description(),
                account.currency(),
                account.associatedFundIds(),
                account.supplementalLineKinds(),
                account.unsupportedFields());
    }

    private static List<Account> parentBeforeChild(List<Account> accounts)
    {
        Map<String, Account> byCode = accounts.stream().collect(Collectors.toMap(
                Account::getCode,
                account -> account,
                (left, right) -> left,
                LinkedHashMap::new));
        List<Account> ordered = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        List<Account> sorted = accounts.stream().sorted(Comparator.comparing(Account::getCode)).toList();
        for (Account account : sorted)
        {
            visitAccount(account, byCode, visited, visiting, ordered);
        }
        return List.copyOf(ordered);
    }

    private static void visitAccount(
            Account account,
            Map<String, Account> byCode,
            Set<String> visited,
            Set<String> visiting,
            List<Account> ordered)
    {
        if (!visited.add(account.getCode()))
        {
            return;
        }
        if (!visiting.add(account.getCode()))
        {
            throw new IllegalStateException("Active Chart of Accounts contains a hierarchy cycle at " + account.getCode() + ".");
        }
        if (account.getParent() != null)
        {
            Account parent = byCode.get(account.getParent().getCode());
            if (parent != null && !ordered.contains(parent))
            {
                visitAccount(parent, byCode, visited, visiting, ordered);
            }
        }
        visiting.remove(account.getCode());
        if (!ordered.contains(account))
        {
            ordered.add(account);
        }
    }

    private static Map<String, String> warnUnknownFields(
            ObjectNode node,
            Set<String> known,
            String path,
            List<InterchangeValidationMessage> messages)
    {
        Map<String, String> result = new LinkedHashMap<>();
        node.fieldNames().forEachRemaining(field -> {
            if (!known.contains(field))
            {
                String value = node.get(field).toString();
                result.put(field, value);
                messages.add(warning(
                        "COA_UNKNOWN_FIELD",
                        path + "." + field,
                        "Unknown field " + field + " is ignored."));
            }
        });
        return Map.copyOf(result);
    }

    private static ArrayNode requiredArray(ObjectNode node, String field, String path)
    {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray())
        {
            throw new IllegalArgumentException(path + " must be an array.");
        }
        return (ArrayNode) value;
    }

    private static String requiredText(ObjectNode node, String field, String path)
    {
        JsonNode value = node.get(field);
        String text = optionalText(value);
        if (text == null)
        {
            throw new IllegalArgumentException(path + " is required and must be nonblank text.");
        }
        return text;
    }

    private static String optionalText(JsonNode value)
    {
        if (value == null || value.isNull())
        {
            return null;
        }
        if (!value.isTextual())
        {
            throw new IllegalArgumentException("Expected text value but found " + value.getNodeType() + ".");
        }
        String text = value.textValue().trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean optionalBoolean(ObjectNode node, String field, boolean defaultValue, String path)
    {
        JsonNode value = node.get(field);
        if (value == null || value.isNull())
        {
            return defaultValue;
        }
        if (!value.isBoolean())
        {
            throw new IllegalArgumentException(path + " must be true or false.");
        }
        return value.booleanValue();
    }

    private static LocalDate optionalDate(JsonNode value, String path)
    {
        String text = optionalText(value);
        if (text == null)
        {
            return null;
        }
        try
        {
            return LocalDate.parse(text);
        }
        catch (RuntimeException ex)
        {
            throw new IllegalArgumentException(path + " must be an ISO date (YYYY-MM-DD).", ex);
        }
    }

    private static BigDecimal decimal(JsonNode value, String path)
    {
        if (value == null || value.isNull())
        {
            return BigDecimal.ZERO;
        }
        String text;
        if (value.isNumber())
        {
            text = value.asText();
        }
        else if (value.isTextual())
        {
            text = value.textValue().trim();
        }
        else
        {
            throw new IllegalArgumentException(path + " must be a plain decimal number or string.");
        }
        if (text.isEmpty() || text.contains("e") || text.contains("E"))
        {
            throw new IllegalArgumentException(path + " must be a finite plain decimal without exponent notation.");
        }
        try
        {
            return new BigDecimal(text);
        }
        catch (NumberFormatException ex)
        {
            throw new IllegalArgumentException(path + " is not a valid decimal.", ex);
        }
    }

    private static List<String> stringArray(JsonNode value, String path)
    {
        if (value == null || value.isNull())
        {
            return List.of();
        }
        if (!value.isArray())
        {
            throw new IllegalArgumentException(path + " must be an array.");
        }
        List<String> result = new ArrayList<>();
        for (int index = 0; index < value.size(); index++)
        {
            JsonNode element = value.get(index);
            if (!element.isTextual())
            {
                throw new IllegalArgumentException(path + "[" + index + "] must be text.");
            }
            result.add(element.textValue());
        }
        return List.copyOf(result);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String path)
    {
        try
        {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        }
        catch (RuntimeException ex)
        {
            throw new IllegalArgumentException(path + " has unsupported value " + value + ".", ex);
        }
    }

    private static <E extends Enum<E>> E optionalEnum(Class<E> type, JsonNode value, String path)
    {
        String text = optionalText(value);
        return text == null ? null : parseEnum(type, text, path);
    }

    private static boolean dateInRange(LocalDate date)
    {
        return !date.isBefore(MIN_DATE) && !date.isAfter(MAX_DATE);
    }

    private static String normalizeOptional(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        return value.trim();
    }

    private static ChartOfAccounts requireActiveChart(EntityManager em, Company company)
    {
        Company managed = em.find(Company.class, company.getId());
        if (managed.getActiveChartOfAccounts() == null)
        {
            throw new IllegalStateException("Company " + managed.getCode() + " has no active Chart of Accounts.");
        }
        return managed.getActiveChartOfAccounts();
    }

    private static void createParent(Path path)
    {
        try
        {
            if (path.getParent() != null)
            {
                Files.createDirectories(path.getParent());
            }
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not create directory " + path.getParent() + ".", ex);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException
    {
        try
        {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException ex)
        {
            Files.move(source, target);
        }
    }

    private static void deleteQuietly(Path path)
    {
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
        }
    }

    private static String sha256(byte[] bytes)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private static InterchangeValidationMessage warning(String code, String path, String message)
    {
        return new InterchangeValidationMessage(
                InterchangeMessageSeverity.WARNING,
                code,
                path,
                message,
                false);
    }

    private static InterchangeValidationMessage error(String code, String path, String message)
    {
        return new InterchangeValidationMessage(
                InterchangeMessageSeverity.ERROR,
                code,
                path,
                message,
                true);
    }

    private record SourceBytes(byte[] originalBytes, String jsonText, String sha256, boolean bomStripped)
    {
        private SourceBytes
        {
            originalBytes = originalBytes.clone();
        }

        @Override
        public byte[] originalBytes()
        {
            return originalBytes.clone();
        }
    }

    private record TypeMapping(AccountType type, AccountFunction function, AccountSubtype subtype)
    {
    }

    private record TargetContext(
            ChartOfAccounts chart,
            String label,
            Map<String, ExistingAccount> existingAccounts)
    {
    }

    private record ExistingAccount(
            Long id,
            String code,
            String name,
            AccountType type,
            AccountFunction function,
            AccountSubtype subtype,
            NormalBalance normalBalance,
            String parentCode,
            boolean posting,
            boolean active,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            BigDecimal openingBalance,
            String description,
            boolean hasHistory)
    {
    }

    private record ExportSnapshot(
            String name,
            String version,
            ChartStatus status,
            String currency,
            List<Account> accounts)
    {
    }
}
