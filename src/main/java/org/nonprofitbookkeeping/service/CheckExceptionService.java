package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/** Company-scoped check review over canonical payment, correction and reconciliation facts. */
public final class CheckExceptionService
{
    public record Row(Long transactionId, Long accountId, String bank, String reference,
            LocalDate date, BigDecimal amount, PaymentReference facts, String lifecycle,
            Long reversalId, Long replacementId, Long replacesId, boolean cleared,
            String exceptions, Long sessionId, Long statementLineId)
    {
    }

    private final Jpa jpa;
    private final Supplier<String> companyCode;
    private final AuthorizationGuard guard;

    public CheckExceptionService(Jpa jpa, Supplier<String> companyCode, AuthorizationGuard guard)
    {
        this.jpa = Objects.requireNonNull(jpa);
        this.companyCode = Objects.requireNonNull(companyCode);
        this.guard = guard;
    }

    /** Age cutoff is operator-selected review criteria, never a legal expiry or accounting instruction. */
    public List<Row> report(Long configuredBankId, LocalDate through, LocalDate issuedBefore)
    {
        Objects.requireNonNull(through, "Review through date");
        if (issuedBefore != null && issuedBefore.isAfter(through))
        {
            throw new IllegalArgumentException("Age review cutoff cannot follow the review date.");
        }
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCode.get());
            Account account = bank(em, company, configuredBankId);
            var splits = em.createQuery("""
                    select s from TxnSplit s join fetch s.txn t join fetch s.account a
                    where t.company = :company and t.txnDate <= :date and s.paymentMethod = 'CHECK'
                      and t.reversalOf is null
                    order by t.txnDate, t.id, s.id
                    """, TxnSplit.class).setParameter("company", company).setParameter("date", through).getResultList();
            var groups = new LinkedHashMap<String, List<TxnSplit>>();
            for (TxnSplit split : splits)
            {
                if (account == null || account.getId().equals(split.getAccount().getId()))
                {
                    String key = split.getTxn().getId() + ":" + split.getAccount().getId() + ":" + split.getPaymentReference();
                    groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(split);
                }
            }
            List<Row> rows = new ArrayList<>();
            for (var group : groups.values())
            {
                TxnSplit split = group.get(0);
                Txn txn = split.getTxn();
                Txn reversal = em.createQuery("from Txn t where t.reversalOf = :txn and t.txnDate <= :date", Txn.class)
                        .setParameter("txn", txn).setParameter("date", through).getResultStream().findFirst().orElse(null);
                Txn replacement = em.createQuery("from Txn t where t.replacementFor = :txn and t.txnDate <= :date order by t.id", Txn.class)
                        .setParameter("txn", txn).setParameter("date", through).getResultStream().findFirst().orElse(null);
                Txn cancellationReversal = reversal == null ? null : em.createQuery(
                        "from Txn t where t.reversalOf = :txn and t.txnDate <= :date", Txn.class)
                        .setParameter("txn", reversal).setParameter("date", through).getResultStream().findFirst().orElse(null);
                PaymentReference facts = PaymentReferences.read(split);
                boolean cleared = group.stream().allMatch(s -> s.isBankCleared()
                        && s.getBankClearedOn() != null && !s.getBankClearedOn().isAfter(through));
                List<String> exceptions = new ArrayList<>();
                String lifecycle = reversal == null ? (cleared ? "CLEARED" : "OUTSTANDING")
                        : cancellationReversal != null ? "CANCELLATION_REVERSED" : (replacement == null ? "VOIDED" : "REISSUED");
                if (reversal == null)
                {
                    if (facts.issuedOn() == null)
                    {
                        exceptions.add("Missing issue date; age is unknown");
                    }
                    if (facts.deliveredOn() == null || facts.deliveredOn().isAfter(through))
                    {
                        exceptions.add("No delivery recorded through review date");
                    }
                    if (!cleared && issuedBefore != null && facts.issuedOn() != null
                            && !facts.issuedOn().isAfter(issuedBefore))
                    {
                        exceptions.add("Age review: obligation unchanged (D04)");
                    }
                    if (facts.evidenceReference() == null)
                    {
                        exceptions.add("Missing supporting evidence reference");
                    }
                }
                else
                {
                    if (cancellationReversal != null)
                    {
                        exceptions.add("Cancellation was reversed by #" + cancellationReversal.getId()
                                + "; review the dated Journal chain and continuing obligation");
                    }
                    exceptions.add("Cancelled pair: original #" + txn.getId() + " / reversal #" + reversal.getId()
                            + (replacement == null ? "" : " / replacement #" + replacement.getId()));
                }
                rows.add(new Row(txn.getId(), split.getAccount().getId(), label(split.getAccount()), facts.reference(),
                        txn.getTxnDate(), group.stream().map(TxnSplit::getAmountSigned).reduce(BigDecimal.ZERO, BigDecimal::add).negate(),
                        facts, lifecycle, reversal == null ? null : reversal.getId(), replacement == null ? null : replacement.getId(),
                        txn.getReplacementFor() == null ? null : txn.getReplacementFor().getId(), cleared,
                        String.join("; ", exceptions), null, null));
            }
            var errors = em.createQuery("""
                    select l from BankStatementLine l join fetch l.bankAccount b join fetch b.account a
                    where l.company = :company and (l.transactionDate <= :date or l.transactionDate is null) and l.status = :status
                    order by l.transactionDate, l.id
                    """, BankStatementLine.class).setParameter("company", company).setParameter("date", through)
                    .setParameter("status", BankStatementLine.Status.ERROR).getResultList();
            for (var error : errors)
            {
                Account ledger = error.getBankAccount().getAccount();
                if (account == null || account.getId().equals(ledger.getId()))
                {
                    rows.add(new Row(null, ledger.getId(), label(ledger), error.getReference(), error.getTransactionDate(),
                            error.getAmount(), null, "BANK_ERROR", null, null, null, false,
                            "Unresolved statement error #" + error.getId() + ": " + Objects.toString(error.getMemo(), "Review in Bank Transactions"),
                            null, error.getId()));
                }
            }
            List<Object[]> differences = em.createNativeQuery("""
                    select s.id, m.txn_split_id, m.statement_line_id, m.match_status, m.resolution_note,
                           s.statement_end_date, b.account_id, a.code, a.name
                    from bank_reconciliation_match m join bank_reconciliation_session s on s.id=m.session_id
                    join company_bank_account b on b.id=s.bank_account_id join account a on a.id=b.account_id
                    where s.company_id=? and s.statement_end_date<=? and s.status<>'FINALIZED'
                      and m.match_status in ('AMOUNT_MISMATCH','DATE_MISMATCH','CLEARED_STATE_MISMATCH','DUPLICATE_POSSIBLE')
                    """, Object[].class).setParameter(1, company.getId()).setParameter(2, through).getResultList();
            for (Object[] difference : differences)
            {
                Long ledgerId = ((Number) difference[6]).longValue();
                if (account != null && !account.getId().equals(ledgerId))
                {
                    continue;
                }
                TxnSplit split = difference[1] == null ? null : em.find(TxnSplit.class, ((Number) difference[1]).longValue());
                rows.add(new Row(split == null ? null : split.getTxn().getId(), ledgerId,
                        difference[7] + " " + difference[8], split == null ? null : split.getPaymentReference(),
                        LocalDate.parse(difference[5].toString()), split == null ? BigDecimal.ZERO : split.getAmountSigned(),
                        null, "BANK_DIFFERENCE", null, null, null, false,
                        "Unresolved " + difference[3] + ": " + Objects.toString(difference[4], ""),
                        ((Number) difference[0]).longValue(), difference[2] == null ? null : ((Number) difference[2]).longValue()));
            }
            return List.copyOf(rows);
        }
    }

    /** Changes review facts for every fund allocation of the selected check, never amounts or cleared state. */
    public void review(long transactionId, long accountId, String reference, LocalDate deliveredOn,
            String evidenceReference, LocalDate reviewedOn, String note, String actor)
    {
        String auditActor = ServiceAuthorization.actor(guard, ApplicationPermission.BOOKKEEPING_WRITE,
                companyCode.get(), "record check review", actor);
        Objects.requireNonNull(reviewedOn, "Review date");
        if (note == null || note.isBlank())
        {
            throw new IllegalArgumentException("A factual review note is required.");
        }
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCode.get());
                SupplementalIntegrityService.lock(em, company);
                Txn txn = em.find(Txn.class, transactionId);
                if (txn == null)
                {
                    throw new IllegalArgumentException("Check transaction not found.");
                }
                new CompanyOwnershipService(jpa).ensureOwnedBy(em, company, txn, "Check transaction");
                if (!"ENTERED".equals(txn.getStatus()) || txn.getReversalOf() != null)
                {
                    throw new IllegalStateException("Retained cancelled check history cannot be changed.");
                }
                TransactionCorrectionService.requireNotReconciled(em, txn.getId(), "review check");
                PeriodCloseRangeService.requireOpen(em, company.getCode(), txn.getTxnDate(), "review check");
                PeriodCloseRangeService.requireOpen(em, company.getCode(), reviewedOn, "record check review");
                var splits = em.createQuery("""
                        from TxnSplit s where s.txn = :txn and s.account.id = :account
                          and s.paymentMethod='CHECK' and s.paymentReference=:reference order by s.id
                        """, TxnSplit.class).setParameter("txn", txn).setParameter("account", accountId)
                        .setParameter("reference", reference).getResultList();
                if (splits.isEmpty())
                {
                    throw new IllegalArgumentException("Selected check identity changed; refresh before review.");
                }
                PaymentReference old = PaymentReferences.read(splits.get(0));
                PaymentReference value = new PaymentReference(old.method(), old.reference(), old.issuedOn(), deliveredOn,
                        evidenceReference, reviewedOn, note);
                if (deliveredOn != null && deliveredOn.isAfter(reviewedOn))
                {
                    throw new IllegalArgumentException("Delivery cannot follow the factual review date.");
                }
                String before = PaymentReferences.snapshot(em, txn);
                splits.forEach(s -> s.setPaymentCheckKey(null));
                em.flush();
                for (TxnSplit split : splits)
                {
                    PaymentReferences.apply(em, split, value);
                }
                AuditEvent audit = new AuditEvent();
                audit.setCompany(company); audit.setActor(auditActor); audit.setActionType("CHECK_REVIEWED");
                audit.setEntityType("Txn"); audit.setEntityId(Long.toString(transactionId));
                audit.setSummary("Factual check review; ledger and obligation unchanged");
                audit.setBeforeValue(before); audit.setAfterValue(PaymentReferences.snapshot(em, txn)); audit.setReason(note);
                em.persist(audit);
                em.getTransaction().commit();
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive())
                {
                    em.getTransaction().rollback();
                }
                throw ex;
            }
        }
    }

    private Account bank(EntityManager em, Company company, Long id)
    {
        if (id == null)
        {
            return null;
        }
        CompanyBankAccount bank = em.find(CompanyBankAccount.class, id);
        if (bank == null || bank.getCompany() == null || !company.getId().equals(bank.getCompany().getId())
                || bank.getAccount() == null)
        {
            throw new IllegalArgumentException("Configured bank account is required.");
        }
        return bank.getAccount();
    }

    private static String label(Account account)
    {
        return account.getCode() + " " + account.getName();
    }
}
