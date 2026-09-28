package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Shared write-time supplemental invariants; callers own the atomic transaction. */
public final class SupplementalIntegrityService
{
    private SupplementalIntegrityService()
    {
    }

    /** Serializes balance-dependent writes for a company, including corrections and imports. */
    public static void lock(EntityManager em, Company company)
    {
        em.lock(company, LockModeType.PESSIMISTIC_WRITE);
    }

    public record Item(SupplementalOpenItemQueryService.Kind kind, UUID id)
    {
    }

    static Set<Item> items(EntityManager em, Txn txn)
    {
        Set<Item> items = new HashSet<>();
        Set<Long> visited = new HashSet<>();
        for (Txn source = txn; source != null && visited.add(source.getId()); source = source.getReversalOf())
        {
            for (Object[] row : em.createQuery("""
                    select l.kind, l.itemId from TxnSupplementalLine l
                    where l.txn = :txn and l.itemId is not null
                    """, Object[].class).setParameter("txn", source).getResultList())
            {
                items.add(new Item(SupplementalOpenItemQueryService.Kind.valueOf((String) row[0]), (UUID) row[1]));
            }
        }
        return items;
    }

    static void requireComplete(EntityManager em, List<TxnSplit> splits)
    {
        em.flush();
        for (int index = 0; index < splits.size(); index++)
        {
            TxnSplit split = splits.get(index);
            boolean governed = java.util.Arrays.stream(SupplementalOpenItemQueryService.Kind.values())
                    .anyMatch(kind -> kind.accountSubtype() == split.getAccount().getSubtype());
            if (!governed)
            {
                continue;
            }
            BigDecimal allocated = em.createQuery("""
                    select coalesce(sum(l.amount), 0) from TxnSupplementalLine l
                    where l.txnSplit = :split and l.itemId is not null and l.itemEffect is not null
                    """, BigDecimal.class).setParameter("split", split).getSingleResult();
            if (allocated.compareTo(split.getAmountSigned().abs()) != 0)
            {
                throw new PostingException("Ledger line " + (index + 1) + " (" + split.getAccount().getCode()
                        + ") requires supplemental allocations of " + split.getAmountSigned().abs()
                        + "; allocated " + allocated + ". Add or repair matching Item ID, Effect and Ledger Line details before saving.");
            }
        }
    }

    /** Checks every effective boundary, so a backdated write cannot overdraw a later application. */
    static void requireAvailable(EntityManager em, Company company, Set<Item> items)
    {
        if (items.isEmpty())
        {
            return;
        }
        em.flush();
        List<LocalDate> dates = em.createQuery("select distinct t.txnDate from Txn t where t.company = :company order by t.txnDate", LocalDate.class)
                .setParameter("company", company).getResultList();
        for (var kind : items.stream().map(Item::kind).distinct().toList())
        {
            for (LocalDate date : dates)
            {
                var projection = SupplementalOpenItemQueryService.query(em, company.getCode(), kind, date, false);
                for (var row : projection.rows())
                {
                    if (items.contains(new Item(kind, row.itemId())) && row.openBalance().signum() < 0)
                    {
                        throw new PostingException("Supplemental item " + row.itemId() + " would be over-applied by "
                                + row.openBalance().abs() + " as of " + date
                                + ". Reduce the application or repair its opening/correction; overpayments require a separate workflow.");
                    }
                }
            }
        }
    }
}
