package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.model.AccountClassification;
import org.nonprofitbookkeeping.model.Txn;
import java.util.Objects;

/** Shared payment validation for canonical entry, corrections and interchange. */
public final class PaymentReferences
{
    private PaymentReferences()
    {
    }

    public static PaymentReference read(TxnSplit split)
    {
        return split.getPaymentMethod() == null ? null : new PaymentReference(
                PaymentReference.Method.valueOf(split.getPaymentMethod()), split.getPaymentReference(),
                split.getPaymentIssuedOn(), split.getPaymentDeliveredOn());
    }

    public static void apply(EntityManager em, TxnSplit split, PaymentReference value)
    {
        if (value != null && !AccountClassification.isBank(split.getAccount()))
        {
            throw new IllegalArgumentException("Payment references belong to a BANK account line.");
        }
        String key = value != null && value.method() == PaymentReference.Method.CHECK
                && split.getTxn().getReversalOf() == null ? value.reference() : null;
        if (key != null)
        {
            if (!AccountClassification.isBankLedgerAccount(split.getAccount()) || split.getAmountSigned().signum() >= 0)
            {
                throw new IllegalArgumentException("Issued checks require an outgoing bank line; a receipt check is not issued by this account.");
            }
            // All canonical entry/import/correction writers hold the existing company lock.
            var existing = em.createQuery(
                    "select s from TxnSplit s where s.account = :account and s.paymentCheckKey = :key", TxnSplit.class)
                    .setParameter("account", split.getAccount()).setParameter("key", key).getResultList();
            for (TxnSplit other : existing)
            {
                if (Objects.equals(other.getId(), split.getId()))
                {
                    continue;
                }
                if (!Objects.equals(other.getTxn().getId(), split.getTxn().getId()))
                {
                    throw new IllegalArgumentException("Check number already used for this issuing account: " + key);
                }
                if (!Objects.equals(read(other), value))
                {
                    throw new IllegalArgumentException("Bank lines for the same check must share identical issue/delivery facts.");
                }
                // One instrument may allocate its bank effect among several fund lines.
                // Exactly one split reserves its number for the complete transaction.
                key = null;
            }
        }
        split.setPaymentMethod(value == null ? null : value.method().name());
        split.setPaymentReference(value == null ? null : value.reference());
        split.setPaymentIssuedOn(value == null ? null : value.issuedOn());
        split.setPaymentDeliveredOn(value == null ? null : value.deliveredOn());
        split.setPaymentCheckKey(key);
    }

    public static String snapshot(EntityManager em, Txn txn)
    {
        return em.createQuery("select s from TxnSplit s where s.txn = :txn order by s.id", TxnSplit.class)
                .setParameter("txn", txn).getResultList().stream()
                .filter(s -> s.getPaymentMethod() != null)
                .map(s -> "bank=" + s.getAccount().getId() + ":" + read(s))
                .collect(java.util.stream.Collectors.joining(";"));
    }
}
