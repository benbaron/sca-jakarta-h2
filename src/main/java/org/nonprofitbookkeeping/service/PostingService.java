package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates balanced transactions (Txn + TxnSplit) using signed amounts.
 *
 * Storage rule:
 * - TxnSplit.amountSigned is stored exactly as provided (signed).
 *
 * Balancing rule:
 * - Convert each split to a DR/CR numeric contribution using Account.normalBalance.
 * - Sum(debit) must equal Sum(credit) to post.
 */
@ApplicationScoped
public class PostingService
{
    @Inject
    private Jpa jpa;

    public record SplitInput(Long accountId,
                             Long fundId,
                             BigDecimal amountSigned,
                             Long activityId,
                             Long merchantId,
                             boolean nmr,
                             String notes) {}

    public Txn post(LocalDate date,
                    Long payeeId,
                    String memo,
                    Long bankAccountId,
                    List<SplitInput> splits)
    {
        Objects.requireNonNull(date, "date");
        if (splits == null || splits.isEmpty())
        {
            throw new PostingException("Cannot post a transaction with no splits.");
        }

        // Basic validations
        for (SplitInput s : splits)
        {
            if (s.accountId() == null) throw new PostingException("Split accountId is required.");
            if (s.fundId() == null) throw new PostingException("Split fundId is required.");
            if (s.amountSigned() == null) throw new PostingException("Split amountSigned is required.");
            if (s.amountSigned().compareTo(BigDecimal.ZERO) == 0) throw new PostingException("Split amountSigned must be non-zero.");
        }

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();

            // Load payee/bank account if provided
            Counterparty payee = (payeeId == null) ? null : em.find(Counterparty.class, payeeId);
            Account bankAccount = (bankAccountId == null) ? null : em.find(Account.class, bankAccountId);

            Txn txn = new Txn();
            txn.setTxnDate(date);
            txn.setPayee(payee);
            txn.setMemo(memo);
            txn.setBankAccount(bankAccount);
            em.persist(txn);

            BigDecimal debitTotal = BigDecimal.ZERO;
            BigDecimal creditTotal = BigDecimal.ZERO;

            List<TxnSplit> persisted = new ArrayList<>();

            for (SplitInput s : splits)
            {
                Account acct = em.find(Account.class, s.accountId());
                if (acct == null) throw new PostingException("Account not found: " + s.accountId());

                Fund fund = em.find(Fund.class, s.fundId());
                if (fund == null) throw new PostingException("Fund not found: " + s.fundId());

                TxnSplit split = new TxnSplit();
                split.setTxn(txn);
                split.setAccount(acct);
                split.setFund(fund);
                split.setAmountSigned(s.amountSigned());
                split.setNmr(s.nmr());
                split.setNotes(s.notes());
                // activity/merchant are optional and not wired into entities yet to keep the skeleton light
                em.persist(split);
                persisted.add(split);

                // Map to debit/credit contributions
                BigDecimal signed = s.amountSigned();
                if (acct.getNormalBalance() == NormalBalance.DEBIT)
                {
                    if (signed.compareTo(BigDecimal.ZERO) > 0) debitTotal = debitTotal.add(signed);
                    else creditTotal = creditTotal.add(signed.abs());
                }
                else
                {
                    if (signed.compareTo(BigDecimal.ZERO) > 0) creditTotal = creditTotal.add(signed);
                    else debitTotal = debitTotal.add(signed.abs());
                }
            }

            if (debitTotal.compareTo(creditTotal) != 0)
            {
                em.getTransaction().rollback();
                throw new PostingException("Transaction does not balance. Debits=" + debitTotal + " Credits=" + creditTotal);
            }

            em.getTransaction().commit();
            return txn;
        }
    }

    public List<JournalLine> journalForTxn(Long txnId)
    {
        try (EntityManager em = jpa.em())
        {
            TypedQuery<Object[]> q = em.createQuery(
                "select t.txnDate, t.id, t.memo, p.displayName, a.code, a.name, f.code, f.name, a.normalBalance, s.amountSigned " +
                "from TxnSplit s " +
                "join s.txn t " +
                "join s.account a " +
                "join s.fund f " +
                "left join t.payee p " +
                "where t.id = :id " +
                "order by a.code", Object[].class);

            q.setParameter("id", txnId);

            List<JournalLine> out = new ArrayList<>();
            for (Object[] r : q.getResultList())
            {
                LocalDate date = (LocalDate) r[0];
                Long id = (Long) r[1];
                String memo = (String) r[2];
                String payee = (String) r[3];
                String acctCode = (String) r[4];
                String acctName = (String) r[5];
                String fundCode = (String) r[6];
                String fundName = (String) r[7];
                NormalBalance nb = (NormalBalance) r[8];
                BigDecimal amt = (BigDecimal) r[9];

                BigDecimal debit = BigDecimal.ZERO;
                BigDecimal credit = BigDecimal.ZERO;

                if (nb == NormalBalance.DEBIT)
                {
                    if (amt.compareTo(BigDecimal.ZERO) > 0) debit = amt;
                    else credit = amt.abs();
                }
                else
                {
                    if (amt.compareTo(BigDecimal.ZERO) > 0) credit = amt;
                    else debit = amt.abs();
                }

                out.add(new JournalLine(date, id, memo, payee, acctCode, acctName, fundCode, fundName, debit, credit));
            }
            return out;
        }
    }
}
