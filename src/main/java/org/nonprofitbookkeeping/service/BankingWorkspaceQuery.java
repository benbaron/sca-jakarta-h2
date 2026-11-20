package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Read-only projections of configured accounts and the canonical journal; never a bank ledger. */
public final class BankingWorkspaceQuery
{
    public record AccountView(long id, String bank, String name, String maskedNumber,
            long ledgerId, String ledgerCode, String ledgerName, String chart,
            boolean active, BigDecimal bookBalance, Long latestSessionId,
            LocalDate statementStart, LocalDate statementEnd, String status,
            BigDecimal clearedOpening, BigDecimal difference, BankAccountCommand configuration)
    {
        public String label()
        {
            return bank + " · " + name + " · " + maskedNumber + " · " + ledgerCode;
        }

        public String ledgerLabel()
        {
            return ledgerCode + " — " + ledgerName + " · " + chart + " · Account ID " + ledgerId;
        }
    }

    public record BankItem(long anchorSplitId, long transactionId, LocalDate date,
            String description, String reference, BigDecimal amount,
            boolean cleared, boolean mixedClearing, LocalDate clearedOn,
            List<Long> splitIds, Long statementLineId)
    {
        public BankItem
        {
            splitIds = List.copyOf(splitIds);
        }
    }

    public record StatementHeader(long batchId, String source, LocalDate start, LocalDate end, BigDecimal ending) { }
    private record Instrument(long transactionId, String method, String reference, LocalDate issuedOn) { }
    private final Jpa jpa;

    public BankingWorkspaceQuery(Jpa jpa)
    {
        this.jpa = Objects.requireNonNull(jpa);
    }

    public List<AccountView> accounts(String companyCode, LocalDate through)
    {
        Objects.requireNonNull(through, "through");
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCode);
            List<CompanyBankAccount> accounts = em.createQuery("""
                    select c from CompanyBankAccount c left join fetch c.bank b
                    left join fetch c.account a left join fetch a.chart
                    where c.company = :company order by b.name, c.name, c.id
                    """, CompanyBankAccount.class).setParameter("company", company).getResultList();
            List<AccountView> result = new ArrayList<>();
            for (CompanyBankAccount account : accounts)
            {
                if (account.getBank() == null || account.getAccount() == null)
                {
                    continue;
                }
                var ledger = account.getAccount();
                @SuppressWarnings("unchecked")
                List<Object[]> sessions = em.createNativeQuery("""
                        select id, statement_start_date, statement_end_date, status, opening_amount, difference_amount
                        from bank_reconciliation_session where company_id = ? and bank_account_id = ?
                        order by statement_end_date desc, id desc
                        """).setParameter(1, company.getId()).setParameter(2, account.getId())
                        .setMaxResults(1).getResultList();
                Object[] row = sessions.isEmpty() ? null : sessions.get(0);
                BigDecimal book = em.createQuery("""
                        select coalesce(sum(s.amountSigned),0) from TxnSplit s
                        where s.txn.company = :company and s.account = :account and s.txn.txnDate <= :through
                        """, BigDecimal.class).setParameter("company", company).setParameter("account", ledger)
                        .setParameter("through", row == null ? through : date(row[2])).getSingleResult();
                var chart = ledger.getChart();
                result.add(new AccountView(account.getId(), account.getBank().getName(), account.getName(),
                        account.getMaskedAccountNumber() == null ? "" : account.getMaskedAccountNumber(),
                        ledger.getId(), ledger.getCode(), ledger.getName(),
                        chart == null ? "No chart" : chart.getName() + " (ID " + chart.getId() + ")",
                        account.isActive(), book, row == null ? null : ((Number) row[0]).longValue(),
                        row == null ? null : date(row[1]), row == null ? null : date(row[2]),
                        row == null ? "No reconciliation" : row[4] == null && !"FINALIZED".equals(row[3])
                                ? "Opening required" : "FINALIZED".equals(row[3]) ? "Balanced · Completed" : String.valueOf(row[3]),
                        row == null ? null : (BigDecimal) row[4],
                        row == null || row[4] == null && !"FINALIZED".equals(row[3]) ? null : (BigDecimal) row[5],
                        new BankAccountCommand(companyCode, account.getBank().getId(), ledger.getId(),
                                account.getMaskedAccountNumber(), account.getNickname(), account.getOpeningDate(),
                                account.getOpeningBalance(), account.getStatementImportFormat(), account.getOfxBankId(),
                                account.getOfxAccountId(), account.getNotes(), account.isActive())));
            }
            return List.copyOf(result);
        }
    }

    public StatementHeader latestStatement(String companyCode, long accountId)
    {
        try (EntityManager em = jpa.em())
        {
            var rows = em.createQuery("""
                    from BankImportBatch b where b.company.code = :company and b.bankAccount.id = :account
                    and b.statementEndDate is not null and b.ledgerBalance is not null and b.status not in (:failed)
                    order by b.importedAt desc, b.id desc
                    """, BankImportBatch.class).setParameter("company", companyCode).setParameter("account", accountId)
                    .setParameter("failed", List.of(BankImportBatch.Status.REJECTED, BankImportBatch.Status.FAILED,
                            BankImportBatch.Status.CANCELLED)).setMaxResults(1).getResultList();
            if (rows.isEmpty())
            {
                return null;
            }
            BankImportBatch row = rows.get(0);
            return new StatementHeader(row.getId(), row.getSourceName(), row.getStatementStartDate(),
                    row.getStatementEndDate(), row.getLedgerBalance());
        }
    }

    public List<BankItem> journal(String companyCode, long configuredAccountId, LocalDate through)
    {
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCode);
            CompanyBankAccount account = em.find(CompanyBankAccount.class, configuredAccountId);
            if (account == null || account.getCompany() == null
                    || !Objects.equals(company.getId(), account.getCompany().getId()))
            {
                throw new IllegalArgumentException("Configured bank account belongs to another company or does not exist.");
            }
            if (account.getAccount() == null || account.getBank() == null)
            {
                throw new IllegalArgumentException("Select a configured bank account linked to a bank and ledger account.");
            }
            List<TxnSplit> splits = em.createQuery("""
                    select s from TxnSplit s join fetch s.txn t left join fetch s.matchedBankStatementLine
                    where t.company = :company and s.account = :account and t.txnDate <= :through
                    order by t.txnDate desc, t.id desc, s.id
                    """, TxnSplit.class).setParameter("company", company).setParameter("account", account.getAccount())
                    .setParameter("through", Objects.requireNonNull(through)).getResultList();
            var groups = new LinkedHashMap<Instrument, List<TxnSplit>>();
            for (TxnSplit split : splits)
            {
                var key = new Instrument(split.getTxn().getId(), split.getPaymentMethod(),
                        split.getPaymentReference(), split.getPaymentIssuedOn());
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(split);
            }
            List<BankItem> result = new ArrayList<>();
            for (var group : groups.values())
            {
                TxnSplit first = group.get(0);
                boolean allCleared = group.stream().allMatch(TxnSplit::isBankCleared);
                LocalDate clearDate = allCleared && group.stream().allMatch(
                        s -> Objects.equals(s.getBankClearedOn(), first.getBankClearedOn()))
                        ? first.getBankClearedOn() : null;
                Long statement = statementId(first);
                final Long candidateStatement = statement;
                if (group.stream().anyMatch(s -> !Objects.equals(statementId(s), candidateStatement)))
                {
                    statement = null;
                }
                result.add(new BankItem(first.getId(), first.getTxn().getId(), first.getTxn().getTxnDate(),
                        first.getTxn().getMemo() == null ? "Journal #" + first.getTxn().getId() : first.getTxn().getMemo(),
                        first.getPaymentReference() == null ? "" : first.getPaymentReference(),
                        group.stream().map(TxnSplit::getAmountSigned).reduce(BigDecimal.ZERO, BigDecimal::add),
                        allCleared, (!allCleared && group.stream().anyMatch(TxnSplit::isBankCleared))
                                || (allCleared && clearDate == null), clearDate,
                        group.stream().map(TxnSplit::getId).toList(), statement));
            }
            return List.copyOf(result);
        }
    }

    private static Long statementId(TxnSplit split)
    {
        return split.getMatchedBankStatementLine() == null ? null : split.getMatchedBankStatementLine().getId();
    }

    private static LocalDate date(Object value)
    {
        return value instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) value;
    }
}
