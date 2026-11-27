package org.nonprofitbookkeeping.interchange.coa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Transient applied proposals; the original document and database are never edited here. */
public final class CoaImportReviewDraft
{
    private final CoaImportPreview original;
    private final Map<Integer, CoaAccountData> replacements = new HashMap<>();
    private final Set<Integer> excluded = new HashSet<>();
    private final Set<Integer> updated = new HashSet<>();
    private final Map<String, String> mappings = new HashMap<>();

    public CoaImportReviewDraft(CoaImportPreview original)
    {
        this.original = original;
        mappings.putAll(original.request().codeMappings());
    }

    public CoaImportReviewDraft copy()
    {
        var copy = new CoaImportReviewDraft(original);
        copy.replacements.putAll(replacements);
        copy.excluded.addAll(excluded);
        copy.updated.addAll(updated);
        copy.mappings.clear();
        copy.mappings.putAll(mappings);
        return copy;
    }

    /** Rebinds applied edits only after an explicit review of a new destination. */
    public CoaImportReviewDraft retarget(CoaImportPreview target)
    {
        if (!original.sourceSha256().equals(target.sourceSha256())
                || original.scope().companyId() != target.scope().companyId())
        {
            throw new IllegalStateException("The source file or company changed. Restore the original source or start a new review.");
        }
        var result = new CoaImportReviewDraft(target);
        result.replacements.putAll(replacements);
        result.excluded.addAll(excluded);
        result.updated.addAll(updated);
        return result;
    }

    public List<Integer> rows()
    {
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < original.originalChart().accounts().size(); i++)
        {
            if (!excluded.contains(i)) rows.add(i);
        }
        return List.copyOf(rows);
    }

    public CoaAccountData account(int row)
    {
        CoaAccountData account = replacements.getOrDefault(row, original.originalChart().accounts().get(row));
        if (original.request().mode() == CoaImportMode.MAP_CODES)
        {
            return definition(account, mappings.getOrDefault(account.sourceCode(), ""), account.name(), account.type(),
                account.function(), account.subtype(), account.normalBalance(), account.parentCode(), account.posting(),
                account.active(), account.effectiveFrom(), account.effectiveTo(), account.openingBalance(),
                account.description(), account.currency());
        }
        return account;
    }

    public void apply(int row, CoaAccountData account)
    {
        if (!rows().contains(row)) throw new IllegalArgumentException("Account proposal is no longer present");
        replacements.put(row, account);
        updated.add(row);
        if (original.request().mode() == CoaImportMode.MAP_CODES)
        {
            mappings.put(account.sourceCode(), account.code());
        }
    }

    public void remove(List<Integer> rows)
    {
        Set<Integer> present = new HashSet<>(rows());
        if (!present.containsAll(rows)) throw new IllegalArgumentException("Unknown proposed account");
        for (int row : rows)
        {
            excluded.add(row);
            replacements.remove(row);
            updated.remove(row);
        }
    }

    public boolean updated(int row)
    {
        return updated.contains(row);
    }

    public int removedCount()
    {
        return excluded.size();
    }

    public void clearUpdateMarkers()
    {
        updated.clear();
    }

    public CoaImportRequest request(boolean openingConfirmed)
    {
        var initial = original.request();
        return new CoaImportRequest(initial.sourceFile(), initial.mode(), initial.targetChartName(),
            initial.targetChartVersion(), mappings, openingConfirmed,
            new CoaReviewEdits(original.sourceSha256(), original.scope(), replacements, excluded), initial.targetChartId());
    }

    public static CoaAccountData definition(CoaAccountData source, String code, String name,
        org.nonprofitbookkeeping.model.AccountType type, org.nonprofitbookkeeping.model.AccountFunction function,
        org.nonprofitbookkeeping.model.AccountSubtype subtype, org.nonprofitbookkeeping.model.NormalBalance balance,
        String parent, boolean posting, boolean active, java.time.LocalDate from, java.time.LocalDate to,
        java.math.BigDecimal opening, String description, String currency)
    {
        return new CoaAccountData(source.sourceCode(), code, name, type, function, subtype, balance, parent, posting,
            active, from, to, opening, description, currency, source.associatedFundIds(),
            source.supplementalLineKinds(), source.unsupportedFields());
    }
}
