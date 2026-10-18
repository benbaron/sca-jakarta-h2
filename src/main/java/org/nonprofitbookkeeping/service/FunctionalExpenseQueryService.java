package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/** Read-only purpose projection of actual canonical expense debits less credits. */
public final class FunctionalExpenseQueryService
{
    private final Jpa jpa;
    private final Supplier<String> company;

    public FunctionalExpenseQueryService(Jpa jpa, Supplier<String> company)
    {
        this.jpa = Objects.requireNonNull(jpa);
        this.company = Objects.requireNonNull(company);
    }

    public Result query(LocalDate start, LocalDate end, String fundCode, Long eventId)
    {
        if (start == null || end == null || end.isBefore(start))
        {
            throw new IllegalArgumentException("A valid functional-expense date range is required.");
        }
        try (EntityManager em = jpa.em())
        {
            CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
            Company owner = ownership.requireCompany(em, company.get());
            if (eventId != null)
            {
                Activity event = em.find(Activity.class, eventId);
                if (event == null)
                {
                    throw new IllegalArgumentException("The selected Event no longer exists.");
                }
                ownership.ensureOwnedBy(em, owner, event, "Event");
            }
            List<TxnSplit> splits = em.createQuery("""
                    select s from TxnSplit s join fetch s.txn t join fetch s.account a
                    join fetch s.fund f left join fetch s.activity e
                    where t.company = :company and a.accountType = :expense
                    and t.txnDate >= :start and t.txnDate <= :end
                    and (:fund is null or f.code = :fund) and (:event is null or e.id = :event)
                    order by t.txnDate, t.id, s.id
                    """, TxnSplit.class).setParameter("company", owner).setParameter("expense", AccountType.EXPENSE)
                    .setParameter("start", start).setParameter("end", end)
                    .setParameter("fund", fundCode == null || fundCode.isBlank() ? null : fundCode)
                    .setParameter("event", eventId).getResultList();
            List<Detail> details = new ArrayList<>();
            Map<ExpenseFunction, BigDecimal> totals = new EnumMap<>(ExpenseFunction.class);
            for (ExpenseFunction function : ExpenseFunction.values())
            {
                totals.put(function, new BigDecimal("0.0000"));
            }
            BigDecimal unclassified = new BigDecimal("0.0000"), unknownGross = unclassified, gl = unclassified;
            for (TxnSplit split : splits)
            {
                ownership.ensureOwnedBy(em, owner, split.getAccount(), "Account");
                ownership.ensureOwnedBy(em, owner, split.getFund(), "Fund");
                ownership.ensureOwnedBy(em, owner, split.getActivity(), "Event");
                BigDecimal net = split.getAccount().getNormalBalance() == NormalBalance.DEBIT
                        ? split.getAmountSigned() : split.getAmountSigned().negate();
                gl = gl.add(net);
                ExpenseFunction function = split.getExpenseFunction();
                if (function == null)
                {
                    unclassified = unclassified.add(net);
                    unknownGross = unknownGross.add(net.abs());
                }
                else
                {
                    totals.merge(function, net, BigDecimal::add);
                }
                Activity event = split.getActivity();
                details.add(new Detail(split.getTxn().getId(), split.getId(), split.getTxn().getTxnDate(),
                        split.getAccount().getCode(), split.getAccount().getName(), split.getFund().getCode(),
                        event == null ? "" : event.getCode() + " — " + event.getName(), function, net,
                        split.getTxn().getMemo() == null ? "" : split.getTxn().getMemo(), split.getTxn().getReversalOf() != null));
            }
            return new Result(start, end, details, totals, unclassified, unknownGross, gl);
        }
    }

    public record Detail(long transactionId, long splitId, LocalDate date, String accountCode, String accountName,
                         String fund, String event, ExpenseFunction function, BigDecimal net, String memo, boolean correction)
    {
    }

    public record Result(LocalDate start, LocalDate end, List<Detail> details, Map<ExpenseFunction, BigDecimal> totals,
                         BigDecimal unclassified, BigDecimal unknownGross, BigDecimal gl)
    {
        public Result
        {
            details = List.copyOf(details);
            totals = Map.copyOf(totals);
        }

        public boolean ready()
        {
            return unknownGross.signum() == 0;
        }
    }
}
