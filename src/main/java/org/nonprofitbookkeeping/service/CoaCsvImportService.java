package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.interchange.InterchangeFormat;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.InterchangeIdentity;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Exact-scope, one-transaction commit boundary for accepted Chart of Accounts CSV rows.
 */
public final class CoaCsvImportService
{
    private static final String SOURCE_SYSTEM_PREFIX = "COA_CSV/IMPORT_PREVIEW/";
    private static final String BATCH_ENTITY_TYPE = "COA_CSV_BATCH";
    private static final String ACCOUNT_ENTITY_TYPE = "ACCOUNT";

    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;
    private final ImportPreviewService syntaxPreviewService;
    private final AccountAdminService accountAdminService;
    private final CompanyOwnershipService ownership;
    private final InterchangeIdentityService identityService;
    private final IntConsumer afterAccountWrite;

    public CoaCsvImportService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this(jpa, companyCodeSupplier, ignored -> { });
    }

    CoaCsvImportService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            IntConsumer afterAccountWrite)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.afterAccountWrite = Objects.requireNonNull(afterAccountWrite, "afterAccountWrite");
        this.syntaxPreviewService = new ImportPreviewService();
        this.accountAdminService = new AccountAdminService(jpa, companyCodeSupplier);
        this.ownership = new CompanyOwnershipService(jpa);
        this.identityService = new InterchangeIdentityService(jpa, ownership);
    }

    /** Produces a non-mutating preview bound to the exact source, company, chart, and target state. */
    public CoaCsvBatchPreview preview(Path source)
    {
        Path normalizedSource = requireSource(source);
        String sourceSha256 = sha256File(normalizedSource);
        ImportPreviewService.CoaPreviewResult syntax = syntaxPreviewService.previewCoaCsv(normalizedSource);
        String companyCode = requireCompanyCode(companyCodeSupplier.get());

        ownership.requireNoOpenOwnershipIssues();
        try (EntityManager em = jpa.em())
        {
            Company company = ownership.requireCompany(em, companyCode);
            ChartOfAccounts chart = resolveTargetChart(em, company);
            Map<String, Account> accounts = loadAccounts(em, chart);
            Validation validation = validateRows(em, chart, accounts, syntax.acceptedRows());
            return new CoaCsvBatchPreview(
                    normalizedSource,
                    syntax.sourceName(),
                    sourceSha256,
                    company.getId(),
                    company.getCode(),
                    chart.getId(),
                    chartLabel(chart),
                    targetFingerprint(chart, accounts),
                    syntax.totalRowCount(),
                    syntax.acceptedRows(),
                    syntax.rejectedRows(),
                    syntax.warnings(),
                    validation.errors(),
                    false);
        }
    }

    /** Commits only the exact confirmed preview. Any commit-time failure rolls the entire batch back. */
    public CoaCsvBatchCommitResult commit(CoaCsvBatchPreview approvedPreview, String actor)
    {
        Objects.requireNonNull(approvedPreview, "approvedPreview");
        String cleanActor;
        try
        {
            cleanActor = requireText(actor, "Import actor", 200);
        }
        catch (RuntimeException ex)
        {
            return failure(false, approvedPreview.acceptedCount(), "confirmation/actor", ex);
        }
        if (!approvedPreview.confirmed())
        {
            return failure(false, approvedPreview.acceptedCount(), "confirmation",
                    new IllegalStateException("Confirm the exact COA CSV preview before committing."));
        }
        if (approvedPreview.hasBlockingErrors())
        {
            return failure(false, approvedPreview.acceptedCount(), "preview",
                    new IllegalStateException("COA CSV preview contains blocking validation errors."));
        }

        String activeCompany = requireCompanyCode(companyCodeSupplier.get());
        if (!activeCompany.equalsIgnoreCase(approvedPreview.companyCode()))
        {
            return failure(false, approvedPreview.acceptedCount(), "company",
                    new IllegalStateException("Active company changed after preview; preview the COA CSV again."));
        }
        if (!Files.isRegularFile(approvedPreview.sourcePath()))
        {
            return failure(false, approvedPreview.acceptedCount(), "source",
                    new IllegalStateException("Previewed COA CSV source is no longer available; preview again."));
        }
        String currentSourceSha = sha256File(approvedPreview.sourcePath());
        if (!approvedPreview.sourceSha256().equals(currentSourceSha))
        {
            return failure(false, approvedPreview.acceptedCount(), "source",
                    new IllegalStateException("COA CSV changed after preview; preview the file again."));
        }

        ImportPreviewService.CoaPreviewResult currentSyntax;
        try
        {
            currentSyntax = syntaxPreviewService.previewCoaCsv(approvedPreview.sourcePath());
        }
        catch (RuntimeException ex)
        {
            return failure(false, approvedPreview.acceptedCount(), "source", ex);
        }
        if (!approvedPreview.acceptedRows().equals(currentSyntax.acceptedRows())
                || !approvedPreview.rejectedRows().equals(currentSyntax.rejectedRows()))
        {
            return failure(false, approvedPreview.acceptedCount(), "source/rows",
                    new IllegalStateException("COA CSV parsed rows changed after preview; preview the file again."));
        }

        ownership.requireNoOpenOwnershipIssues();
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = ownership.requireCompany(em, activeCompany);
                if (!company.getId().equals(approvedPreview.companyId()))
                {
                    throw new PreviewDriftException("Company identity changed after preview; preview the COA CSV again.");
                }
                ChartOfAccounts chart = resolveTargetChart(em, company);
                if (!chart.getId().equals(approvedPreview.targetChartId()))
                {
                    throw new PreviewDriftException("Target Chart of Accounts changed after preview; preview the COA CSV again.");
                }

                Map<String, Account> accounts = loadAccounts(em, chart);
                String fingerprint = targetFingerprint(chart, accounts);
                if (!approvedPreview.targetFingerprint().equals(fingerprint))
                {
                    throw new PreviewDriftException("Target Chart of Accounts data changed after preview; preview the COA CSV again.");
                }

                Validation validation = validateRows(em, chart, accounts, approvedPreview.acceptedRows());
                if (!validation.errors().isEmpty())
                {
                    throw new IllegalStateException(validation.errors().get(0));
                }

                List<PreparedRow> ordered = parentBeforeChild(validation.preparedRows(), accounts);
                int created = 0;
                int updated = 0;
                int skipped = 0;
                int writes = 0;
                Map<String, Account> committedAccounts = new LinkedHashMap<>();

                for (PreparedRow row : ordered)
                {
                    Account existing = accounts.get(row.code());
                    if (existing != null && isIdentical(existing, row))
                    {
                        committedAccounts.put(row.code(), existing);
                        skipped++;
                        continue;
                    }

                    boolean isCreate = existing == null;
                    Account written = accountAdminService.upsert(
                            em,
                            company,
                            chart,
                            row.code(),
                            row.name(),
                            row.accountType(),
                            row.normalBalance(),
                            null,
                            row.parentCode(),
                            true);
                    accounts.put(row.code(), written);
                    committedAccounts.put(row.code(), written);
                    if (isCreate)
                    {
                        created++;
                    }
                    else
                    {
                        updated++;
                    }
                    writes++;
                    afterAccountWrite.accept(writes);
                }

                em.flush();
                String sourceSystem = sourceSystem(approvedPreview);
                boolean alreadyCommitted = hasBatchIdentity(em, company, sourceSystem);
                for (PreparedRow row : ordered)
                {
                    Account account = committedAccounts.get(row.code());
                    if (account == null)
                    {
                        account = accounts.get(row.code());
                    }
                    identityService.record(
                            em,
                            company,
                            InterchangeFormat.COA_CSV,
                            sourceSystem,
                            ACCOUNT_ENTITY_TYPE,
                            row.code(),
                            normalizedRowHash(row),
                            String.valueOf(account.getId()));
                }
                identityService.record(
                        em,
                        company,
                        InterchangeFormat.COA_CSV,
                        sourceSystem,
                        BATCH_ENTITY_TYPE,
                        "BATCH",
                        approvedPreview.sourceSha256(),
                        String.valueOf(chart.getId()));

                if (!alreadyCommitted)
                {
                    AuditEvent audit = new AuditEvent();
                    audit.setCompany(company);
                    audit.setActor(cleanActor);
                    audit.setActionType("COA_CSV_IMPORT");
                    audit.setEntityType("CHART_OF_ACCOUNTS");
                    audit.setEntityId(String.valueOf(chart.getId()));
                    audit.setSummary("Committed accepted COA CSV rows atomically: created=" + created
                            + ", updated=" + updated + ", skipped=" + skipped + ".");
                    audit.setAfterValue("sourceSha256=" + approvedPreview.sourceSha256()
                            + "; accepted=" + approvedPreview.acceptedCount());
                    audit.setReason("Import Preview accepted-row commit");
                    em.persist(audit);
                }

                em.flush();
                em.getTransaction().commit();
                return new CoaCsvBatchCommitResult(
                        true,
                        false,
                        approvedPreview.acceptedCount(),
                        created,
                        updated,
                        skipped,
                        null,
                        List.of());
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive())
                {
                    em.getTransaction().rollback();
                }
                String path = ex instanceof RowCommitException rowEx
                        ? "row/" + rowEx.code()
                        : ex instanceof PreviewDriftException ? "preview-drift" : "commit";
                return failure(true, approvedPreview.acceptedCount(), path, ex);
            }
        }
    }

    public static AccountType parseAccountTypeToken(String token)
    {
        String normalized = normalizeEnumToken(token);
        if ("REVENUE".equals(normalized))
        {
            return AccountType.INCOME;
        }
        return AccountType.valueOf(normalized);
    }

    public static NormalBalance parseNormalBalanceToken(String token)
    {
        String normalized = normalizeEnumToken(token);
        if ("DR".equals(normalized))
        {
            return NormalBalance.DEBIT;
        }
        if ("CR".equals(normalized))
        {
            return NormalBalance.CREDIT;
        }
        return NormalBalance.valueOf(normalized);
    }

    private Validation validateRows(
            EntityManager em,
            ChartOfAccounts chart,
            Map<String, Account> accounts,
            List<CoaCsvMapper.CoaCsvRow> sourceRows)
    {
        List<String> errors = new ArrayList<>();
        List<PreparedRow> prepared = new ArrayList<>();
        Map<String, Integer> codeCounts = new LinkedHashMap<>();
        List<CoaCsvMapper.CoaCsvRow> rows = sourceRows == null ? List.of() : List.copyOf(sourceRows);

        for (CoaCsvMapper.CoaCsvRow source : rows)
        {
            if (source == null)
            {
                errors.add("COA CSV accepted rows contain a null row.");
                continue;
            }
            String code;
            String name;
            try
            {
                code = requireText(source.code(), "Account code", 64);
                name = requireText(source.name(), "Account name", 200);
                AccountType type = parseAccountTypeToken(source.accountType());
                NormalBalance normal = parseNormalBalanceToken(source.normalBalance());
                String parent = optionalText(source.parentCode(), 64, "Parent account code");
                PreparedRow row = new PreparedRow(code, name, type, normal, parent);
                prepared.add(row);
                codeCounts.merge(code, 1, Integer::sum);
            }
            catch (RuntimeException ex)
            {
                errors.add((source.code() == null ? "(unknown row)" : source.code()) + ": " + safeMessage(ex));
            }
        }

        codeCounts.forEach((code, count) ->
        {
            if (count > 1)
            {
                errors.add("Duplicate account code in accepted COA CSV rows: " + code + ".");
            }
        });

        Map<String, PreparedRow> batchByCode = new LinkedHashMap<>();
        for (PreparedRow row : prepared)
        {
            batchByCode.putIfAbsent(row.code(), row);
        }
        for (PreparedRow row : prepared)
        {
            if (row.parentCode() != null && row.parentCode().equals(row.code()))
            {
                errors.add(row.code() + ": parent account cannot be the same account.");
                continue;
            }
            if (row.parentCode() != null)
            {
                PreparedRow batchParent = batchByCode.get(row.parentCode());
                Account existingParent = accounts.get(row.parentCode());
                if (batchParent == null && existingParent == null)
                {
                    errors.add(row.code() + ": parent account does not exist in the target chart or accepted batch: "
                            + row.parentCode() + ".");
                }
                else if (batchParent == null && !existingParent.isActive())
                {
                    errors.add(row.code() + ": parent account is inactive: " + row.parentCode() + ".");
                }
            }

            Account existing = accounts.get(row.code());
            if (existing != null && hasTransactionHistory(em, existing))
            {
                String currentParent = existing.getParent() == null ? null : existing.getParent().getCode();
                if (existing.getAccountType() != row.accountType()
                        || existing.getNormalBalance() != row.normalBalance()
                        || !Objects.equals(currentParent, row.parentCode()))
                {
                    errors.add(row.code() + ": type, normal balance, or hierarchy cannot change because the account has transaction history.");
                }
            }
        }

        try
        {
            validateResultingHierarchy(accounts, batchByCode);
        }
        catch (RuntimeException ex)
        {
            errors.add(safeMessage(ex));
        }

        return new Validation(List.copyOf(prepared), List.copyOf(errors));
    }

    private static void validateResultingHierarchy(
            Map<String, Account> accounts,
            Map<String, PreparedRow> batchByCode)
    {
        Map<String, String> parentByCode = new LinkedHashMap<>();
        for (Account account : accounts.values())
        {
            parentByCode.put(account.getCode(), account.getParent() == null ? null : account.getParent().getCode());
        }
        batchByCode.values().forEach(row -> parentByCode.put(row.code(), row.parentCode()));

        Set<String> complete = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String code : parentByCode.keySet())
        {
            visitHierarchy(code, parentByCode, complete, visiting);
        }
    }

    private static void visitHierarchy(
            String code,
            Map<String, String> parentByCode,
            Set<String> complete,
            Set<String> visiting)
    {
        if (complete.contains(code))
        {
            return;
        }
        if (!visiting.add(code))
        {
            throw new IllegalArgumentException("COA CSV hierarchy contains a cycle involving account " + code + ".");
        }
        String parent = parentByCode.get(code);
        if (parent != null && parentByCode.containsKey(parent))
        {
            visitHierarchy(parent, parentByCode, complete, visiting);
        }
        visiting.remove(code);
        complete.add(code);
    }

    private static List<PreparedRow> parentBeforeChild(
            List<PreparedRow> rows,
            Map<String, Account> existingAccounts)
    {
        Map<String, PreparedRow> byCode = new HashMap<>();
        rows.forEach(row -> byCode.put(row.code(), row));
        List<PreparedRow> ordered = new ArrayList<>();
        Set<String> complete = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        rows.stream().sorted(Comparator.comparing(PreparedRow::code)).forEach(row ->
                visitBatch(row, byCode, existingAccounts, complete, visiting, ordered));
        return List.copyOf(ordered);
    }

    private static void visitBatch(
            PreparedRow row,
            Map<String, PreparedRow> byCode,
            Map<String, Account> existingAccounts,
            Set<String> complete,
            Set<String> visiting,
            List<PreparedRow> ordered)
    {
        if (complete.contains(row.code()))
        {
            return;
        }
        if (!visiting.add(row.code()))
        {
            throw new RowCommitException(row.code(), "COA CSV hierarchy cycle reached commit boundary.");
        }
        if (row.parentCode() != null)
        {
            PreparedRow batchParent = byCode.get(row.parentCode());
            if (batchParent != null)
            {
                visitBatch(batchParent, byCode, existingAccounts, complete, visiting, ordered);
            }
            else if (!existingAccounts.containsKey(row.parentCode()))
            {
                throw new RowCommitException(row.code(), "Parent account disappeared before commit: " + row.parentCode() + ".");
            }
        }
        visiting.remove(row.code());
        complete.add(row.code());
        ordered.add(row);
    }

    private ChartOfAccounts resolveTargetChart(EntityManager em, Company company)
    {
        Company managed = em.find(Company.class, company.getId());
        if (managed.getActiveChartOfAccounts() != null)
        {
            ChartOfAccounts active = managed.getActiveChartOfAccounts();
            ownership.requireOwnedBy(managed, active, "Active Chart of Accounts");
            if (active.getStatus() != ChartStatus.ACTIVE)
            {
                throw new IllegalStateException("Selected company Chart of Accounts is not active.");
            }
            return active;
        }

        List<ChartOfAccounts> activeCharts = em.createQuery(
                        "from ChartOfAccounts c where c.company = :company and c.status = :status order by c.id",
                        ChartOfAccounts.class)
                .setParameter("company", managed)
                .setParameter("status", ChartStatus.ACTIVE)
                .setMaxResults(2)
                .getResultList();
        if (activeCharts.size() != 1)
        {
            throw new IllegalStateException("Company must have exactly one active Chart of Accounts before COA CSV preview.");
        }
        return activeCharts.get(0);
    }

    private static Map<String, Account> loadAccounts(EntityManager em, ChartOfAccounts chart)
    {
        List<Account> values = em.createQuery("""
                select a from Account a
                left join fetch a.parent
                where a.chart = :chart
                order by a.code
                """, Account.class)
                .setParameter("chart", chart)
                .getResultList();
        Map<String, Account> result = new LinkedHashMap<>();
        for (Account account : values)
        {
            if (result.put(account.getCode(), account) != null)
            {
                throw new IllegalStateException("Target chart contains duplicate account code: " + account.getCode() + ".");
            }
        }
        return result;
    }

    private static boolean hasTransactionHistory(EntityManager em, Account account)
    {
        Long count = em.createQuery(
                        "select count(s) from TxnSplit s where s.account = :account", Long.class)
                .setParameter("account", account)
                .getSingleResult();
        return count > 0L;
    }

    private static boolean isIdentical(Account account, PreparedRow row)
    {
        String parent = account.getParent() == null ? null : account.getParent().getCode();
        return account.getName().equals(row.name())
                && account.getAccountType() == row.accountType()
                && account.getNormalBalance() == row.normalBalance()
                && Objects.equals(parent, row.parentCode())
                && account.isActive();
    }

    private static String targetFingerprint(ChartOfAccounts chart, Map<String, Account> accounts)
    {
        StringBuilder normalized = new StringBuilder();
        normalized.append(chart.getId()).append('\u001f')
                .append(chart.getCompany() == null ? "" : chart.getCompany().getId()).append('\u001f')
                .append(chart.getName()).append('\u001f')
                .append(chart.getVersion()).append('\u001f')
                .append(chart.getStatus()).append('\n');
        accounts.values().stream()
                .sorted(Comparator.comparing(Account::getCode))
                .forEach(account -> normalized.append(account.getCode()).append('\u001f')
                        .append(account.getName()).append('\u001f')
                        .append(account.getAccountType()).append('\u001f')
                        .append(account.getNormalBalance()).append('\u001f')
                        .append(account.getSubtype()).append('\u001f')
                        .append(account.getParent() == null ? "" : account.getParent().getCode()).append('\u001f')
                        .append(account.isPosting()).append('\u001f')
                        .append(account.isActive()).append('\u001f')
                        .append(account.getEffectiveFrom()).append('\u001f')
                        .append(account.getEffectiveTo()).append('\u001f')
                        .append(account.getOpeningBalance()).append('\u001f')
                        .append(account.getDescription()).append('\n'));
        return sha256(normalized.toString());
    }

    private static String normalizedRowHash(PreparedRow row)
    {
        return sha256(String.join("\u001f",
                row.code(),
                row.name(),
                row.accountType().name(),
                row.normalBalance().name(),
                Objects.toString(row.parentCode(), ""),
                "active=true"));
    }

    private static boolean hasBatchIdentity(EntityManager em, Company company, String sourceSystem)
    {
        Long count = em.createQuery("""
                select count(i) from InterchangeIdentity i
                where i.company = :company
                  and i.formatCode = :format
                  and i.sourceSystem = :source
                  and i.entityType = :type
                  and i.externalId = :externalId
                """, Long.class)
                .setParameter("company", company)
                .setParameter("format", InterchangeFormat.COA_CSV.name())
                .setParameter("source", sourceSystem)
                .setParameter("type", BATCH_ENTITY_TYPE)
                .setParameter("externalId", "BATCH")
                .getSingleResult();
        return count > 0L;
    }

    private static String sourceSystem(CoaCsvBatchPreview preview)
    {
        return SOURCE_SYSTEM_PREFIX + preview.sourceSha256() + "/chart-" + preview.targetChartId();
    }

    private static String chartLabel(ChartOfAccounts chart)
    {
        return chart.getName() + " (" + chart.getVersion() + ")";
    }

    private static Path requireSource(Path source)
    {
        if (source == null)
        {
            throw new IllegalArgumentException("COA CSV source path is required.");
        }
        Path normalized = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized))
        {
            throw new IllegalArgumentException("COA CSV source does not exist: " + normalized + ".");
        }
        return normalized;
    }

    private static String sha256File(Path source)
    {
        try
        {
            return sha256Bytes(Files.readAllBytes(source));
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Could not read COA CSV source: " + source + ".", ex);
        }
    }

    private static String sha256(String value)
    {
        return sha256Bytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Bytes(byte[] value)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private static String requireCompanyCode(String value)
    {
        return requireText(value, "Company code", 64);
    }

    private static String requireText(String value, String label, int maxLength)
    {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty())
        {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (clean.length() > maxLength)
        {
            throw new IllegalArgumentException(label + " exceeds " + maxLength + " characters.");
        }
        return clean;
    }

    private static String optionalText(String value, int maxLength, String label)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        return requireText(value, label, maxLength);
    }

    private static String normalizeEnumToken(String token)
    {
        if (token == null || token.isBlank())
        {
            throw new IllegalArgumentException("Enum token is required.");
        }
        return token.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
                .replace('/', '_');
    }

    private static String safeMessage(Throwable ex)
    {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private static CoaCsvBatchCommitResult failure(
            boolean rolledBack,
            int totalAccepted,
            String path,
            Throwable ex)
    {
        return new CoaCsvBatchCommitResult(
                false,
                rolledBack,
                totalAccepted,
                0,
                0,
                0,
                path,
                List.of(safeMessage(ex)));
    }

    private record PreparedRow(
            String code,
            String name,
            AccountType accountType,
            NormalBalance normalBalance,
            String parentCode)
    {
    }

    private record Validation(List<PreparedRow> preparedRows, List<String> errors)
    {
    }

    private static final class PreviewDriftException extends IllegalStateException
    {
        private PreviewDriftException(String message)
        {
            super(message);
        }
    }

    private static final class RowCommitException extends IllegalStateException
    {
        private final String code;

        private RowCommitException(String code, String message)
        {
            super(message);
            this.code = code;
        }

        private String code()
        {
            return code;
        }
    }

    public record CoaCsvBatchPreview(
            Path sourcePath,
            String sourceName,
            String sourceSha256,
            Long companyId,
            String companyCode,
            Long targetChartId,
            String targetChartLabel,
            String targetFingerprint,
            int totalRowCount,
            List<CoaCsvMapper.CoaCsvRow> acceptedRows,
            List<ImportPreviewService.RejectedCoaRow> rejectedRows,
            List<String> warnings,
            List<String> blockingErrors,
            boolean confirmed)
    {
        public CoaCsvBatchPreview
        {
            sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
            sourceName = Objects.requireNonNull(sourceName, "sourceName");
            sourceSha256 = Objects.requireNonNull(sourceSha256, "sourceSha256");
            companyId = Objects.requireNonNull(companyId, "companyId");
            companyCode = Objects.requireNonNull(companyCode, "companyCode");
            targetChartId = Objects.requireNonNull(targetChartId, "targetChartId");
            targetChartLabel = Objects.requireNonNull(targetChartLabel, "targetChartLabel");
            targetFingerprint = Objects.requireNonNull(targetFingerprint, "targetFingerprint");
            acceptedRows = List.copyOf(Objects.requireNonNull(acceptedRows, "acceptedRows"));
            rejectedRows = List.copyOf(Objects.requireNonNull(rejectedRows, "rejectedRows"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
            blockingErrors = List.copyOf(Objects.requireNonNull(blockingErrors, "blockingErrors"));
        }

        public int acceptedCount()
        {
            return acceptedRows.size();
        }

        public int rejectedCount()
        {
            return rejectedRows.size();
        }

        public boolean hasBlockingErrors()
        {
            return !blockingErrors.isEmpty();
        }

        public CoaCsvBatchPreview confirmedCopy()
        {
            return new CoaCsvBatchPreview(
                    sourcePath, sourceName, sourceSha256, companyId, companyCode,
                    targetChartId, targetChartLabel, targetFingerprint, totalRowCount,
                    acceptedRows, rejectedRows, warnings, blockingErrors, true);
        }
    }

    public record CoaCsvBatchCommitResult(
            boolean committed,
            boolean rolledBack,
            int totalAccepted,
            int createdCount,
            int updatedCount,
            int skippedCount,
            String errorPath,
            List<String> errors)
    {
        public CoaCsvBatchCommitResult
        {
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
        }
    }
}
