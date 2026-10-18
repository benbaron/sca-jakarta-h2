package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import java.math.BigDecimal;
import java.util.*;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.*;

/** Synthetic canonical receipts and actual payout/discharge facts, without policy configuration. */
public record MerchantSettlementTestFixture(NmrSettlementTestFixture nmr, UUID receivableItem, UUID liabilityItem)
{
    public static MerchantSettlementTestFixture seed(Jpa jpa)
    {
        var nmr = NmrSettlementTestFixture.seed(jpa);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.find(Account.class, nmr.base().cash()).setAccountFunction(AccountFunction.BANK);
            em.getTransaction().commit();
        }
        return new MerchantSettlementTestFixture(nmr, UUID.randomUUID(), UUID.randomUUID());
    }

    public static MerchantSettlementCommand facts(String role, String amount, String evidence, MerchantSettlementCommand.Component... components)
    {
        return new MerchantSettlementCommand(role, amount == null ? null : new BigDecimal(amount), evidence, List.of(components));
    }

    public static MerchantSettlementCommand.Component component(String kind, int index)
    {
        return new MerchantSettlementCommand.Component(kind, index);
    }

    public static TransactionSupplementalLineCommand withFacts(TransactionSupplementalLineCommand c, MerchantSettlementCommand facts)
    {
        return new TransactionSupplementalLineCommand(c.kind(), c.entryRef(), c.counterparty(), c.description(), c.reference(), c.amount(), c.dueDate(),
                c.startDate(), c.endDate(), c.notes(), c.lineOrder(), c.itemId(), c.itemEffect(), c.transactionLineIndex(),
                c.nmrRecipientId(), c.nmrMovement(), c.nmrEvidenceReference(), facts);
    }

    public TransactionCommand receipt()
    {
        var b = nmr.base();
        return new TransactionCommand(DATE, null, "Synthetic gross receipt", null,
                List.of(nmr.line(nmr.receivable(), "1000", "0", nmr.merchant()), nmr.line(b.income(), "0", "900", null), nmr.line(b.liability(), "0", "100", null)),
                List.of(withFacts(nmr.receivable(receivableItem, "INCREASE", 0, "1000"), facts("GROSS_RECEIPT", null, null)),
                        nmr.nmr(liabilityItem, "INCREASE", 2, "100", "COLLECTION", null)));
    }

    public TransactionCommand payout(MerchantSettlementCommand facts)
    {
        return new TransactionCommand(DATE.plusDays(1), null, "Synthetic payout and fee", null,
                List.of(nmr.line(nmr.base().cash(), "950", "0", nmr.merchant()), nmr.line(nmr.fee(), "20", "0", nmr.merchant()), nmr.line(nmr.receivable(), "0", "970", nmr.merchant())),
                List.of(withFacts(nmr.receivable(receivableItem, "DECREASE", 2, "970"), facts)));
    }

    public TransactionCommand payout()
    {
        return payout(facts("SETTLEMENT", "30.0000", "processor/withheld/001", component("PAYOUT", 0), component("FEE", 1)));
    }

    public TransactionCommand discharge()
    {
        return new TransactionCommand(DATE.plusDays(2), null, "Synthetic evidenced discharge", null,
                List.of(nmr.line(nmr.base().liability(), "30", "0", nmr.merchant()), nmr.line(nmr.receivable(), "0", "30", nmr.merchant())),
                List.of(nmr.nmr(liabilityItem, "DECREASE", 0, "30", "MERCHANT_DISCHARGE", "processor/discharged/001"),
                        withFacts(nmr.receivable(receivableItem, "DECREASE", 1, "30"), facts("SETTLEMENT", "0", "processor/discharged/001", component("NMR_DISCHARGE", 0)))));
    }

    public List<Long> history(Jpa jpa)
    {
        var entry = nmr.base().entry(jpa);
        return List.of(entry.enter(receipt()).id(), entry.enter(payout()).id(), entry.enter(discharge()).id());
    }
}
