package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Factual links validated inside the existing canonical write transaction. */
public final class MerchantSettlementFacts
{
    private MerchantSettlementFacts()
    {
    }

    /** Shared strict value validation, also used by interchange preflight. */
    public static void validate(MerchantSettlementCommand command, String kind, String effect)
    {
        if (command == null)
        {
            return;
        }
        try
        {
            var role = MerchantReceivableRole.valueOf(command.role());
            if (!"RECEIVABLE".equals(kind) || !(role == MerchantReceivableRole.GROSS_RECEIPT ? "INCREASE" : "DECREASE").equals(effect)
                    || (role == MerchantReceivableRole.GROSS_RECEIPT && !command.components().isEmpty()))
            {
                throw new IllegalArgumentException("Merchant role requires its matching RECEIVABLE effect; gross receipt has no settlement components.");
            }
            String evidence = command.evidenceReference();
            if (evidence != null && (evidence.isBlank() || evidence.trim().length() > 500))
            {
                throw new IllegalArgumentException("Merchant evidence must be nonblank text within 500 characters.");
            }
            if (command.withheldUnconfirmed() != null)
            {
                BigDecimal value = command.withheldUnconfirmed().setScale(4, RoundingMode.UNNECESSARY);
                if (value.signum() < 0 || value.precision() > 19 || evidence == null)
                {
                    throw new IllegalArgumentException("Observed withholding requires a nonnegative exact four-decimal amount and evidence.");
                }
            }
            Set<Integer> indexes = new HashSet<>();
            for (var component : command.components())
            {
                MerchantComponentKind.valueOf(component.kind());
                if (component.transactionLineIndex() == null || component.transactionLineIndex() < 0
                        || !indexes.add(component.transactionLineIndex()))
                {
                    throw new IllegalArgumentException("Merchant component line must be a distinct nonnegative line index.");
                }
            }
        }
        catch (IllegalArgumentException | ArithmeticException | NullPointerException ex)
        {
            throw new PostingException("Invalid merchant settlement facts: " + ex.getMessage());
        }
    }

    static void apply(TxnSupplementalLine line, MerchantSettlementCommand command, List<TxnSplit> splits)
    {
        if (command == null)
        {
            return;
        }
        validate(command, line.getKind(), line.getItemEffect() == null ? null : line.getItemEffect().name());
        TxnSplit receivable = line.getTxnSplit();
        if (receivable == null || line.getItemId() == null || receivable.getMerchant() == null || receivable.getActivity() == null
                || receivable.getAccount().getAccountType() != AccountType.ASSET
                || receivable.getAccount().getNormalBalance() != NormalBalance.DEBIT)
        {
            throw new PostingException("Merchant facts require a debit-normal receivable allocation with Merchant and Event on its linked line.");
        }
        line.setMerchantRole(MerchantReceivableRole.valueOf(command.role()));
        line.setMerchantWithheldUnconfirmed(command.withheldUnconfirmed() == null ? null : command.withheldUnconfirmed().setScale(4));
        line.setMerchantEvidenceReference(command.evidenceReference() == null ? null : command.evidenceReference().trim());
        for (var component : command.components())
        {
            if (component.transactionLineIndex() >= splits.size())
            {
                throw new PostingException("Merchant component does not resolve to an owning transaction line.");
            }
            line.getMerchantComponents().put(splits.get(component.transactionLineIndex()), MerchantComponentKind.valueOf(component.kind()));
        }
    }

    static void requireValid(EntityManager em, Company company, Set<SupplementalIntegrityService.Item> items)
    {
        for (var item : items)
        {
            if (item.kind() != SupplementalOpenItemQueryService.Kind.RECEIVABLE)
            {
                continue;
            }
            List<TxnSupplementalLine> rows = em.createQuery("from TxnSupplementalLine l where l.txn.company = :company and l.itemId = :item",
                    TxnSupplementalLine.class).setParameter("company", company).setParameter("item", item.id()).getResultList();
            var designated = rows.stream().filter(l -> l.getMerchantRole() != null).findFirst();
            if (designated.isEmpty())
            {
                continue;
            }
            TxnSplit original = designated.get().getTxnSplit();
            for (var row : rows)
            {
                TxnSplit split = row.getTxnSplit();
                if (split == null || split.getMerchant() == null || split.getActivity() == null
                        || !split.getAccount().getId().equals(original.getAccount().getId())
                        || !split.getFund().getId().equals(original.getFund().getId())
                        || !split.getActivity().getId().equals(original.getActivity().getId())
                        || !split.getMerchant().getId().equals(original.getMerchant().getId()))
                {
                    throw new PostingException("Merchant receivable item must retain one Account, Fund, Event and Merchant across allocations.");
                }
                BigDecimal total = BigDecimal.ZERO;
                for (var link : row.getMerchantComponents().entrySet())
                {
                    TxnSplit component = link.getKey();
                    BigDecimal debit = component.getAccount().getNormalBalance() == NormalBalance.DEBIT
                            ? component.getAmountSigned() : component.getAmountSigned().negate();
                    if (row.getMerchantRole() != MerchantReceivableRole.SETTLEMENT || !component.getTxn().getId().equals(row.getTxn().getId())
                            || debit.signum() <= 0 || component.getId().equals(split.getId())
                            || component.getMerchant() == null || component.getActivity() == null
                            || !component.getMerchant().getId().equals(split.getMerchant().getId())
                            || !component.getFund().getId().equals(split.getFund().getId())
                            || !component.getActivity().getId().equals(split.getActivity().getId()))
                    {
                        throw new PostingException("Merchant components require distinct debit lines in the same transaction, Merchant, Fund and Event.");
                    }
                    switch (link.getValue())
                    {
                        case PAYOUT ->
                        {
                            if (component.getAccount().getAccountType() != AccountType.ASSET
                                    || component.getAccount().getNormalBalance() != NormalBalance.DEBIT
                                    || !AccountClassification.isBankLedgerAccount(component.getAccount()))
                            {
                                throw new PostingException("Payout component requires a debit-normal BANK asset line.");
                            }
                        }
                        case FEE ->
                        {
                            if (component.getAccount().getAccountType() != AccountType.EXPENSE
                                    || component.getAccount().getNormalBalance() != NormalBalance.DEBIT)
                            {
                                throw new PostingException("Fee component requires a debit-normal EXPENSE line.");
                            }
                        }
                        case NMR_DISCHARGE ->
                        {
                            BigDecimal explained = em.createQuery("""
                                    select coalesce(sum(l.amount), 0) from TxnSupplementalLine l
                                    where l.txnSplit = :split and l.nmrMovement = :movement
                                      and l.nmrRecipient is not null and l.nmrEvidenceReference is not null
                                      and l.itemEffect = :effect
                                    """, BigDecimal.class).setParameter("split", component)
                                    .setParameter("movement", NmrMovementKind.MERCHANT_DISCHARGE)
                                    .setParameter("effect", SupplementalItemEffect.DECREASE).getSingleResult();
                            if (explained.compareTo(debit) != 0)
                            {
                                throw new PostingException("NMR component must be fully allocated to evidenced MERCHANT_DISCHARGE facts.");
                            }
                        }
                    }
                    total = total.add(debit);
                }
                if (total.compareTo(row.getAmount()) > 0)
                {
                    throw new PostingException("Merchant component amounts exceed their receivable allocation; split shared component lines explicitly.");
                }
            }
        }
    }

    static String snapshot(EntityManager em, Txn txn)
    {
        return em.createQuery("from TxnSupplementalLine l where l.txn = :txn and l.merchantRole is not null order by l.lineOrder, l.id", TxnSupplementalLine.class)
                .setParameter("txn", txn).getResultList().stream().map(l -> "item=" + l.getItemId() + ",role=" + l.getMerchantRole()
                        + ",withheldObserved=" + l.getMerchantWithheldUnconfirmed() + ",evidence=" + l.getMerchantEvidenceReference()
                        + ",components=" + l.getMerchantComponents().entrySet().stream()
                                .sorted(java.util.Comparator.comparing(e -> e.getKey().getId()))
                                .map(e -> e.getValue() + ":" + e.getKey().getId()).collect(java.util.stream.Collectors.joining(",")))
                .collect(java.util.stream.Collectors.joining(";"));
    }

    static MerchantSettlementCommand view(TxnSupplementalLine row, List<TxnSplit> splits)
    {
        if (row.getMerchantRole() == null)
        {
            return null;
        }
        return new MerchantSettlementCommand(row.getMerchantRole().name(), row.getMerchantWithheldUnconfirmed(), row.getMerchantEvidenceReference(),
                row.getMerchantComponents().entrySet().stream()
                        .map(e -> new MerchantSettlementCommand.Component(e.getValue().name(), splits.indexOf(e.getKey())))
                        .sorted(java.util.Comparator.comparing(MerchantSettlementCommand.Component::kind)
                                .thenComparing(MerchantSettlementCommand.Component::transactionLineIndex)).toList());
    }
}
