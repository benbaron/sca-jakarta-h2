package org.nonprofitbookkeeping.interchange.coa;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.interchange.InterchangeFormat;
import org.nonprofitbookkeeping.interchange.InterchangeMessageSeverity;
import org.nonprofitbookkeeping.interchange.InterchangeOperationCounts;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.CompanyOwnershipService;
import org.nonprofitbookkeeping.service.InterchangeIdentityService;

import java.nio.charset.StandardCharsets;
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
import java.util.TreeMap;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** One-transaction commit boundary for a previously reviewed Chart of Accounts JSON preview. */
public final class ChartOfAccountsJsonImportService
{
    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;
    private final ChartOfAccountsJsonService jsonService;
    private final CompanyOwnershipService ownership;
    private final InterchangeIdentityService identityService;
    private final IntConsumer afterAccountWrite;

    public ChartOfAccountsJsonImportService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this(jpa, companyCodeSupplier, ignored -> { });
    }

    ChartOfAccountsJsonImportService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            IntConsumer afterAccountWrite)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.afterAccountWrite = Objects.requireNonNull(afterAccountWrite, "afterAccountWrite");
        this.jsonService = new ChartOfAccountsJsonService(jpa, companyCodeSupplier);
        this.ownership = new CompanyOwnershipService(jpa);
        this.identityService = new InterchangeIdentityService(jpa, ownership);
    }

    /**
     * Re-reads and revalidates the source immediately before beginning the transaction.
     * A changed source, blocking preview, or unsatisfied confirmation cannot enter the commit boundary.
     */
    public CoaImportResult commit(CoaImportPreview approvedPreview)
    {
        Objects.requireNonNull(approvedPreview, "approvedPreview");
        CoaImportPreview current = jsonService.preview(approvedPreview.request());
        if (!approvedPreview.sourceSha256().equals(current.sourceSha256()))
        {
            throw new IllegalStateException(
                    "Chart of Accounts JSON changed after preview; preview the file again before importing.");
        }
        if (current.hasBlockingErrors())
        {
            throw new IllegalStateException("Chart of Accounts JSON has blocking validation errors.");
        }
        if (!current.confirmationsSatisfied())
        {
            throw new IllegalStateException("Required Chart of Accounts JSON confirmations are not satisfied.");
        }

        ownership.requireNoOpenOwnershipIssues();
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = ownership.requireCompany(em, companyCodeSupplier.get());
                ChartOfAccounts targetChart = resolveTargetChart(em, company, current);
                Map<String, Account> accountsByCode = loadAccounts(em, targetChart);
                Map<String, String> sourceToTarget = sourceToTarget(current.items());
                List<CoaPreviewItem> ordered = parentBeforeChild(current.items(), sourceToTarget);
                Map<CoaPreviewItem, Account> writtenAccounts = new LinkedHashMap<>();

                int writes = 0;
                for (CoaPreviewItem item : ordered)
                {
                    if (item.disposition() == CoaPreviewItem.Disposition.BLOCKED)
                    {
                        throw new IllegalStateException(
                                "Blocked account reached the Chart of Accounts JSON commit boundary: "
                                        + item.account().sourceCode() + ".");
                    }
                    if (item.disposition() == CoaPreviewItem.Disposition.IDENTICAL)
                    {
                        Account existing = accountsByCode.get(item.targetCode());
                        if (existing == null)
                        {
                            throw new IllegalStateException(
                                    "Identical preview account is missing from the target chart: "
                                            + item.targetCode() + ".");
                        }
                        writtenAccounts.put(item, existing);
                        continue;
                    }

                    Account account = accountsByCode.get(item.targetCode());
                    boolean created = account == null;
                    if (created)
                    {
                        account = new Account();
                        account.setChart(targetChart);
                        account.setCode(item.targetCode());
                        accountsByCode.put(item.targetCode(), account);
                    }
                    applySupportedFields(account, item, sourceToTarget, accountsByCode);
                    if (created)
                    {
                        em.persist(account);
                    }
                    writtenAccounts.put(item, account);
                    writes++;
                    afterAccountWrite.accept(writes);
                }

                em.flush();
                String sourceSystem = sourceSystem(current);
                for (Map.Entry<CoaPreviewItem, Account> entry : writtenAccounts.entrySet())
                {
                    CoaPreviewItem item = entry.getKey();
                    Account account = entry.getValue();
                    identityService.record(
                            em,
                            company,
                            InterchangeFormat.COA_JSON,
                            sourceSystem,
                            "ACCOUNT",
                            item.account().sourceCode(),
                            normalizedAccountHash(item, sourceToTarget),
                            String.valueOf(account.getId()));
                }
                identityService.record(
                        em,
                        company,
                        InterchangeFormat.COA_JSON,
                        sourceSystem,
                        "CHART",
                        chartExternalId(current),
                        normalizedChartHash(current),
                        String.valueOf(targetChart.getId()));

                em.getTransaction().commit();
                return new CoaImportResult(
                        true,
                        false,
                        current.targetLabel(),
                        current.sourceSha256(),
                        current.items(),
                        current.messages(),
                        current.counts());
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive())
                {
                    em.getTransaction().rollback();
                }
                List<InterchangeValidationMessage> messages = new ArrayList<>(current.messages());
                messages.add(new InterchangeValidationMessage(
                        InterchangeMessageSeverity.ERROR,
                        "COA_COMMIT_ROLLED_BACK",
                        "commit",
                        safeMessage(ex),
                        true));
                InterchangeOperationCounts counts = new InterchangeOperationCounts(
                        current.counts().total(),
                        0,
                        0,
                        current.counts().identical(),
                        current.counts().skipped(),
                        current.counts().warnings(),
                        current.counts().errors() + 1);
                return new CoaImportResult(
                        false,
                        true,
                        current.targetLabel(),
                        current.sourceSha256(),
                        current.items(),
                        messages,
                        counts);
            }
        }
    }

    private ChartOfAccounts resolveTargetChart(
            EntityManager em,
            Company company,
            CoaImportPreview preview)
    {
        if (preview.request().mode() == CoaImportMode.CREATE_NEW_CHART)
        {
            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName(preview.request().targetChartName());
            chart.setVersion(preview.request().targetChartVersion());
            chart.setStatus(ChartStatus.DRAFT);
            em.persist(chart);
            return chart;
        }

        Company managed = em.find(Company.class, company.getId());
        ChartOfAccounts active = managed.getActiveChartOfAccounts();
        if (active == null)
        {
            throw new IllegalStateException("Company " + managed.getCode() + " has no active Chart of Accounts.");
        }
        ownership.ensureOwnedBy(em, managed, active, "Active Chart of Accounts");
        return active;
    }

    private static Map<String, Account> loadAccounts(EntityManager em, ChartOfAccounts chart)
    {
        List<Account> accounts = em.createQuery("""
                select a from Account a
                left join fetch a.parent
                where a.chart = :chart
                order by a.code
                """, Account.class)
                .setParameter("chart", chart)
                .getResultList();
        Map<String, Account> result = new LinkedHashMap<>();
        for (Account account : accounts)
        {
            result.put(account.getCode(), account);
        }
        return result;
    }

    private static void applySupportedFields(
            Account account,
            CoaPreviewItem item,
            Map<String, String> sourceToTarget,
            Map<String, Account> accountsByCode)
    {
        CoaAccountData source = item.account();
        String parentCode = source.parentCode() == null
                ? null
                : sourceToTarget.getOrDefault(source.parentCode(), source.parentCode());
        Account parent = parentCode == null ? null : accountsByCode.get(parentCode);
        if (parentCode != null && parent == null)
        {
            throw new IllegalStateException(
                    "Parent account was not available during import: " + parentCode + ".");
        }

        account.setName(source.name());
        account.setAccountType(source.type());
        account.setSubtype(source.subtype());
        account.setNormalBalance(source.normalBalance());
        account.setParent(parent);
        account.setPosting(source.posting());
        account.setActive(source.active());
        account.setEffectiveFrom(source.effectiveFrom());
        account.setEffectiveTo(source.effectiveTo());
        account.setOpeningBalance(source.openingBalance());
        account.setDescription(source.description());
    }

    private static Map<String, String> sourceToTarget(List<CoaPreviewItem> items)
    {
        Map<String, String> result = new LinkedHashMap<>();
        for (CoaPreviewItem item : items)
        {
            result.put(item.account().sourceCode(), item.targetCode());
        }
        return Map.copyOf(result);
    }

    private static List<CoaPreviewItem> parentBeforeChild(
            List<CoaPreviewItem> items,
            Map<String, String> sourceToTarget)
    {
        Map<String, CoaPreviewItem> byTarget = new HashMap<>();
        for (CoaPreviewItem item : items)
        {
            byTarget.put(item.targetCode(), item);
        }
        List<CoaPreviewItem> sorted = items.stream()
                .sorted(Comparator.comparing(CoaPreviewItem::targetCode))
                .toList();
        List<CoaPreviewItem> ordered = new ArrayList<>();
        Set<String> complete = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (CoaPreviewItem item : sorted)
        {
            visit(item, sourceToTarget, byTarget, complete, visiting, ordered);
        }
        return List.copyOf(ordered);
    }

    private static void visit(
            CoaPreviewItem item,
            Map<String, String> sourceToTarget,
            Map<String, CoaPreviewItem> byTarget,
            Set<String> complete,
            Set<String> visiting,
            List<CoaPreviewItem> ordered)
    {
        String code = item.targetCode();
        if (complete.contains(code))
        {
            return;
        }
        if (!visiting.add(code))
        {
            throw new IllegalStateException("Chart of Accounts JSON hierarchy cycle reached commit: " + code + ".");
        }
        String sourceParent = item.account().parentCode();
        if (sourceParent != null)
        {
            String targetParent = sourceToTarget.getOrDefault(sourceParent, sourceParent);
            CoaPreviewItem parent = byTarget.get(targetParent);
            if (parent != null)
            {
                visit(parent, sourceToTarget, byTarget, complete, visiting, ordered);
            }
        }
        visiting.remove(code);
        complete.add(code);
        ordered.add(item);
    }

    private static String sourceSystem(CoaImportPreview preview)
    {
        return "COA_JSON/"
                + preview.chart().sourceFamily().name()
                + "/"
                + preview.chart().sourceVersion()
                + "/"
                + preview.request().mode().name();
    }

    private static String chartExternalId(CoaImportPreview preview)
    {
        if (preview.request().mode() == CoaImportMode.CREATE_NEW_CHART)
        {
            return preview.request().targetChartName() + "/" + preview.request().targetChartVersion();
        }
        return preview.targetLabel();
    }

    private static String normalizedChartHash(CoaImportPreview preview)
    {
        StringBuilder normalized = new StringBuilder();
        normalized.append(preview.chart().name()).append('\u001f')
                .append(preview.chart().chartVersion()).append('\u001f')
                .append(preview.chart().status()).append('\u001f')
                .append(preview.chart().currency()).append('\n');
        Map<String, String> sourceToTarget = sourceToTarget(preview.items());
        preview.items().stream()
                .sorted(Comparator.comparing(CoaPreviewItem::targetCode))
                .forEach(item -> normalized.append(normalizedAccountText(item, sourceToTarget)).append('\n'));
        return sha256(normalized.toString());
    }

    private static String normalizedAccountHash(
            CoaPreviewItem item,
            Map<String, String> sourceToTarget)
    {
        return sha256(normalizedAccountText(item, sourceToTarget));
    }

    private static String normalizedAccountText(
            CoaPreviewItem item,
            Map<String, String> sourceToTarget)
    {
        CoaAccountData account = item.account();
        String parent = account.parentCode() == null
                ? ""
                : sourceToTarget.getOrDefault(account.parentCode(), account.parentCode());
        return String.join("\u001f",
                item.targetCode(),
                account.name(),
                String.valueOf(account.type()),
                String.valueOf(account.subtype()),
                String.valueOf(account.normalBalance()),
                parent,
                Boolean.toString(account.posting()),
                Boolean.toString(account.active()),
                String.valueOf(account.effectiveFrom()),
                String.valueOf(account.effectiveTo()),
                account.openingBalance().toPlainString(),
                String.valueOf(account.description()),
                String.valueOf(account.currency()),
                String.join(",", account.associatedFundIds()),
                String.join(",", account.supplementalLineKinds()),
                new TreeMap<>(account.unsupportedFields()).toString());
    }

    private static String sha256(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private static String safeMessage(Throwable ex)
    {
        String message = ex.getMessage();
        return message == null || message.isBlank()
                ? ex.getClass().getSimpleName()
                : message;
    }
}
