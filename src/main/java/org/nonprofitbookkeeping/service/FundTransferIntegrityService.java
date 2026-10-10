package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.*;

/** Keeps operational transfer history and canonical correction facts atomic. */
final class FundTransferIntegrityService
{
    private FundTransferIntegrityService()
    {
    }

    static void requireUnlinked(EntityManager em, long transactionId, String operation)
    {
        if (em.createQuery("select count(f) from FundTransfer f where f.postedTxn.id = :id", Long.class)
                .setParameter("id", transactionId).getSingleResult() > 0)
        {
            throw new IllegalStateException("Cannot " + operation
                    + " a fund-transfer transaction. Reverse it through Fund Transfers or Journal, then enter a corrected transfer.");
        }
    }

    static void reverseLinked(EntityManager em, Company company, Txn original, Txn reversal, String actor)
    {
        var rows = em.createQuery("from FundTransfer f where f.postedTxn = :txn", FundTransfer.class)
                .setParameter("txn", original).getResultList();
        if (rows.size() > 1)
        {
            throw new IllegalStateException("Multiple transfer facts reference this transaction; repair the ambiguity before reversal.");
        }
        if (rows.isEmpty())
        {
            return;
        }
        FundTransfer prior = rows.get(0);
        if (prior.getStatus() != FundTransferStatus.POSTED)
        {
            throw new IllegalStateException("Only a saved transfer can be reversed; repair the legacy transfer link first.");
        }
        if (reversal.getCorrectionNote() == null || reversal.getCorrectionNote().isBlank()
                || reversal.getCorrectionNote().length() > 500)
        {
            throw new IllegalArgumentException("Transfer reversal requires a reason of at most 500 characters.");
        }
        if (prior.getFromFund().getCompany() == null || prior.getToFund().getCompany() == null
                || !company.getId().equals(prior.getFromFund().getCompany().getId())
                || !company.getId().equals(prior.getToFund().getCompany().getId()))
        {
            throw new CompanyOwnershipException("Transfer funds must belong to the transaction company.");
        }
        FundTransfer inverse = new FundTransfer();
        inverse.setFromFund(prior.getToFund());
        inverse.setToFund(prior.getFromFund());
        inverse.setTransferDate(reversal.getTxnDate());
        inverse.setAmount(prior.getAmount());
        inverse.setMemo("Reversal of transfer " + prior.getId());
        inverse.setStatus(FundTransferStatus.POSTED);
        inverse.setPostedTxn(reversal);
        em.persist(inverse);
        em.flush();
        AuditEvent audit = new AuditEvent();
        audit.setCompany(company);
        audit.setActor(actor);
        audit.setActionType("FUND_TRANSFER_REVERSED");
        audit.setEntityType("FUND_TRANSFER");
        audit.setEntityId(inverse.getId().toString());
        audit.setSummary(inverse.getMemo());
        audit.setBeforeValue("original=" + prior.getId());
        audit.setAfterValue("transaction=" + reversal.getId() + "; amount=" + inverse.getAmount());
        em.persist(audit);
    }
}
