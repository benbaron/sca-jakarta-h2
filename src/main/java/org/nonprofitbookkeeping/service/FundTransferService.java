package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/** Atomic internal reallocation using the existing transfer fact and canonical ledger. */
public final class FundTransferService
{
    private final Jpa jpa;
    private final Supplier<String> companyCode;
    private final AuthorizationGuard guard;

    public FundTransferService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT", null);
    }

    public FundTransferService(Jpa jpa, Supplier<String> companyCode, AuthorizationGuard guard)
    {
        this.jpa = Objects.requireNonNull(jpa);
        this.companyCode = Objects.requireNonNull(companyCode);
        this.guard = guard;
    }

    public FundTransferView save(FundTransferCommand command)
    {
        String code = companyCode.get();
        String actor = ServiceAuthorization.actor(guard, ApplicationPermission.BOOKKEEPING_WRITE, code,
                "save internal fund transfer", "SYSTEM");
        Objects.requireNonNull(command, "Transfer details are required");
        Objects.requireNonNull(command.requestId(), "Request identity is required");
        Objects.requireNonNull(command.date(), "Transfer date is required");
        if (command.fromFundId() == null || command.toFundId() == null || command.fromFundId().equals(command.toFundId()))
        {
            throw new IllegalArgumentException("Choose two distinct funds.");
        }
        if (command.amount() == null || command.amount().signum() <= 0)
        {
            throw new IllegalArgumentException("Transfer amount must be positive.");
        }
        BigDecimal amount = command.amount().setScale(4, RoundingMode.UNNECESSARY);
        if (amount.precision() > 19)
        {
            throw new IllegalArgumentException("Transfer amount is too large.");
        }
        String explanation = command.explanation() == null ? "" : command.explanation().trim();
        if (explanation.isBlank() || explanation.length() > 500)
        {
            throw new IllegalArgumentException("Explanation is required and must be at most 500 characters.");
        }
        String hash = hash(command, amount, explanation);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                var ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, code);
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);
                var previous = em.createQuery("from FundTransfer f where f.requestId = :id", FundTransfer.class)
                        .setParameter("id", command.requestId()).getResultList();
                if (!previous.isEmpty())
                {
                    var prior = previous.get(0);
                    ownership.requireOwnedBy(company, prior.getPostedTxn(), "Transfer transaction");
                    ownership.requireOwnedBy(company, prior.getFromFund(), "Source fund");
                    ownership.requireOwnedBy(company, prior.getToFund(), "Destination fund");
                    if (!hash.equals(prior.getRequestHash()))
                    {
                        throw new IllegalArgumentException("This transfer request was already saved with different details. Choose New for a different transfer.");
                    }
                    var result = view(prior);
                    em.getTransaction().commit();
                    return result;
                }
                Fund from = requireFund(em, ownership, company, command.fromFundId(), command.date());
                Fund to = requireFund(em, ownership, company, command.toFundId(), command.date());
                Account allocation = requireAccount(em, ownership, company, command.allocationAccountId(), AccountType.ASSET, NormalBalance.DEBIT);
                Account equity = requireAccount(em, ownership, company, command.equityAccountId(), AccountType.EQUITY, NormalBalance.CREDIT);
                requireAvailable(em, company, from, command.date(), amount);
                var lines = List.of(
                        line(allocation, from, BigDecimal.ZERO, amount), line(equity, from, amount, BigDecimal.ZERO),
                        line(allocation, to, amount, BigDecimal.ZERO), line(equity, to, BigDecimal.ZERO, amount));
                Txn txn = new TransactionEntryService(jpa).enter(em, company,
                        new TransactionCommand(command.date(), null, explanation, null, lines),
                        UUID.randomUUID(), actor, "Internal fund transfer: no bank movement");
                FundTransfer transfer = new FundTransfer();
                transfer.setFromFund(from);
                transfer.setToFund(to);
                transfer.setTransferDate(command.date());
                transfer.setAmount(amount);
                transfer.setMemo(explanation);
                transfer.setStatus(FundTransferStatus.POSTED);
                transfer.setPostedTxn(txn);
                transfer.setRequestId(command.requestId());
                transfer.setRequestHash(hash);
                em.persist(transfer);
                em.flush();
                AuditEvent audit = new AuditEvent();
                audit.setCompany(company);
                audit.setActor(actor);
                audit.setActionType("FUND_TRANSFER_CREATED");
                audit.setEntityType("FUND_TRANSFER");
                audit.setEntityId(transfer.getId().toString());
                audit.setSummary("Internal fund transfer " + from.getCode() + " to " + to.getCode());
                audit.setAfterValue("transaction=" + txn.getId() + "; amount=" + amount + "; explanation=" + explanation);
                em.persist(audit);
                var result = view(transfer);
                em.getTransaction().commit();
                return result;
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

    /** Reversal retains original dated facts, including when funds have since been retired. */
    public FundTransferView reverse(long transferId, LocalDate date, String reason)
    {
        String code = companyCode.get();
        String actor = ServiceAuthorization.actor(guard, ApplicationPermission.BOOKKEEPING_WRITE, code,
                "reverse internal fund transfer", "SYSTEM");
        if (reason == null || reason.isBlank() || reason.trim().length() > 500)
        {
            throw new IllegalArgumentException("Reversal reason is required and must be at most 500 characters.");
        }
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = new CompanyOwnershipService(jpa).requireCompany(em, code);
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);
                FundTransfer transfer = em.find(FundTransfer.class, transferId);
                if (transfer == null || transfer.getPostedTxn() == null || transfer.getStatus() != FundTransferStatus.POSTED)
                {
                    throw new IllegalArgumentException("Unknown saved transfer.");
                }
                new CompanyOwnershipService(jpa).requireOwnedBy(company, transfer.getPostedTxn(), "Transfer transaction");
                Txn inverse = new TransactionCorrectionService(jpa).reverse(em, company, transfer.getPostedTxn(), date,
                        actor, reason.trim(), UUID.randomUUID());
                var fact = em.createQuery("from FundTransfer f where f.postedTxn = :txn", FundTransfer.class)
                        .setParameter("txn", inverse).getSingleResult();
                var result = view(fact);
                em.getTransaction().commit();
                return result;
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

    public List<FundTransferView> list()
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("from FundTransfer f join fetch f.postedTxn t join fetch f.fromFund join fetch f.toFund "
                            + "where t.company.code = :code and f.fromFund.company = t.company and f.toFund.company = t.company "
                            + "and f.status = :status order by f.transferDate desc, f.id desc", FundTransfer.class)
                    .setParameter("code", companyCode.get()).setParameter("status", FundTransferStatus.POSTED)
                    .setMaxResults(500).getResultList().stream().map(FundTransferService::view).toList();
        }
    }

    public Choices choices()
    {
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCode.get());
            var funds = em.createQuery("from Fund f where f.company = :company and f.active = true order by f.code", Fund.class)
                    .setParameter("company", company).getResultList().stream()
                    .filter(f -> eligibleType(f) && (f.getRestrictionText() == null || f.getRestrictionText().isBlank()))
                    .map(f -> new Choice(f.getId(), f.getCode() + " — " + f.getName())).toList();
            if (company.getActiveChartOfAccounts() == null || company.getActiveChartOfAccounts().getStatus() != ChartStatus.ACTIVE)
            {
                return new Choices(funds, List.of(), List.of());
            }
            var accounts = em.createQuery("from Account a where a.chart = :chart and a.active = true and a.posting = true order by a.code", Account.class)
                    .setParameter("chart", company.getActiveChartOfAccounts()).getResultList();
            return new Choices(funds, accounts.stream().filter(a -> eligibleAccount(a, AccountType.ASSET, NormalBalance.DEBIT)).map(FundTransferService::choice).toList(),
                    accounts.stream().filter(a -> eligibleAccount(a, AccountType.EQUITY, NormalBalance.CREDIT)).map(FundTransferService::choice).toList());
        }
    }

    /** Exact historical facts; source request hashes remain provenance after native-ID remapping. */
    public record ImportFact(UUID portableId, LocalDate date, BigDecimal amount, String memo, FundTransferStatus status,
            UUID requestId, String requestHash, java.time.Instant createdAt, java.time.Instant updatedAt)
    {
        public ImportFact
        {
            Objects.requireNonNull(portableId); Objects.requireNonNull(date); Objects.requireNonNull(status);
            Objects.requireNonNull(amount); Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
            amount = amount.setScale(4, RoundingMode.UNNECESSARY);
            if (amount.signum() <= 0 || amount.precision() > 19 || memo != null && memo.length() > 500
                    || (requestId == null) != (requestHash == null)
                    || requestHash != null && !requestHash.matches("[0-9a-f]{64}")
                    || createdAt.getNano() % 1000 != 0 || updatedAt.getNano() % 1000 != 0)
                throw new IllegalArgumentException("Invalid retained transfer facts");
        }
    }

    /** Attaches restored metadata to already imported canonical history without posting accounting. */
    public FundTransfer importForInterchange(EntityManager em, Company owner, ImportFact fact, Fund from, Fund to, Txn transaction)
    {
        if (!em.getTransaction().isActive() || !em.contains(owner) || !em.contains(from) || !em.contains(to))
            throw new IllegalStateException("Transfer import requires managed facts and a caller-owned transaction");
        var ownership = new CompanyOwnershipService(jpa);
        ownership.requireOwnedBy(owner, from, "Transfer source"); ownership.requireOwnedBy(owner, to, "Transfer destination");
        if (from.getId().equals(to.getId())) throw new IllegalArgumentException("Transfer funds must be distinct");
        if (transaction == null && fact.status() == FundTransferStatus.POSTED)
            throw new IllegalArgumentException("Saved transfer requires its canonical transaction");
        if (transaction != null)
        {
            if (!em.contains(transaction)) throw new IllegalArgumentException("Canonical transfer must be managed");
            ownership.requireOwnedBy(owner, transaction, "Transfer transaction");
            if (!fact.date().equals(transaction.getTxnDate())) throw new IllegalArgumentException("Transfer date differs from canonical transaction");
            requireImportedShape(em, transaction, from, to, fact.amount());
        }
        var prior = em.createQuery("from FundTransfer f where f.portableId=:id", FundTransfer.class)
                .setParameter("id", fact.portableId()).getResultList();
        if (!prior.isEmpty())
        {
            FundTransfer existing = prior.get(0);
            ownership.requireOwnedBy(owner, existing.getFromFund(), "Retained transfer");
            ownership.requireOwnedBy(owner, existing.getToFund(), "Retained transfer");
            if (!importFact(existing).equals(fact) || !existing.getFromFund().getId().equals(from.getId())
                    || !existing.getToFund().getId().equals(to.getId())
                    || !Objects.equals(existing.getPostedTxn() == null ? null : existing.getPostedTxn().getId(), transaction == null ? null : transaction.getId()))
                throw new IllegalStateException("Retained transfer identity has conflicting facts");
            return existing;
        }
        if (fact.requestId() != null && em.createQuery("select count(f) from FundTransfer f where f.requestId=:request", Long.class)
                .setParameter("request", fact.requestId()).getSingleResult() != 0)
            throw new IllegalStateException("Transfer request identity already belongs to another retained fact");
        if (transaction != null && em.createQuery("select count(f) from FundTransfer f where f.postedTxn=:txn", Long.class)
                .setParameter("txn", transaction).getSingleResult() != 0)
            throw new IllegalStateException("Canonical transaction already has a transfer fact");
        FundTransfer saved = new FundTransfer();
        saved.setPortableId(fact.portableId()); saved.setFromFund(from); saved.setToFund(to); saved.setPostedTxn(transaction);
        saved.setTransferDate(fact.date()); saved.setAmount(fact.amount()); saved.setMemo(fact.memo()); saved.setStatus(fact.status());
        saved.setRequestId(fact.requestId()); saved.setRequestHash(fact.requestHash());
        saved.restoreImportTimestamps(fact.createdAt(), fact.updatedAt()); em.persist(saved);
        return saved;
    }

    public static ImportFact importFact(FundTransfer value)
    {
        return new ImportFact(value.getPortableId(), value.getTransferDate(), value.getAmount(), value.getMemo(), value.getStatus(),
                value.getRequestId(), value.getRequestHash(), value.getCreatedAt(), value.getUpdatedAt());
    }

    /** Revalidates actual retained accounting after Keep target and canonical correction restoration. */
    public void validateImportedGraph(EntityManager em, Company owner, Collection<FundTransfer> facts)
    {
        for (FundTransfer value : facts)
        {
            new CompanyOwnershipService(jpa).requireOwnedBy(owner, value.getFromFund(), "Transfer graph");
            new CompanyOwnershipService(jpa).requireOwnedBy(owner, value.getToFund(), "Transfer graph");
            Txn txn = value.getPostedTxn();
            if (txn == null) continue;
            new CompanyOwnershipService(jpa).requireOwnedBy(owner, txn, "Transfer graph");
            if (!txn.getTxnDate().equals(value.getTransferDate())) throw new IllegalStateException("Retained transfer date differs from its transaction");
            requireImportedShape(em, txn, value.getFromFund(), value.getToFund(), value.getAmount());
            if (txn.getReversalOf() != null)
            {
                var parents = em.createQuery("from FundTransfer f where f.postedTxn=:txn", FundTransfer.class)
                        .setParameter("txn", txn.getReversalOf()).getResultList();
                if (parents.size() != 1 || !parents.get(0).getFromFund().getId().equals(value.getToFund().getId())
                        || !parents.get(0).getToFund().getId().equals(value.getFromFund().getId())
                        || parents.get(0).getAmount().compareTo(value.getAmount()) != 0)
                    throw new IllegalStateException("Transfer reversal graph is incomplete or contradictory");
            }
            for (Txn inverse : em.createQuery("from Txn t where t.reversalOf=:txn", Txn.class).setParameter("txn", txn).getResultList())
            {
                if (em.createQuery("select count(f) from FundTransfer f where f.postedTxn=:txn", Long.class)
                        .setParameter("txn", inverse).getSingleResult() != 1)
                    throw new IllegalStateException("Canonical transfer reversal is missing operational history");
            }
        }
    }

    private static void requireImportedShape(EntityManager em, Txn transaction, Fund from, Fund to, BigDecimal amount)
    {
        var lines = em.createQuery("from TxnSplit s join fetch s.account where s.txn=:txn", TxnSplit.class)
                .setParameter("txn", transaction).getResultList();
        Set<String> scopes = new HashSet<>(); Set<Long> accounts = new HashSet<>();
        for (TxnSplit line : lines)
        {
            Account account = line.getAccount();
            boolean source = line.getFund() != null && line.getFund().getId().equals(from.getId());
            boolean destination = line.getFund() != null && line.getFund().getId().equals(to.getId());
            if ((!source && !destination) || !eligibleAccount(account, AccountType.ASSET, NormalBalance.DEBIT)
                    && !eligibleAccount(account, AccountType.EQUITY, NormalBalance.CREDIT)
                    || line.getAmountSigned().compareTo(source ? amount.negate() : amount) != 0
                    || !scopes.add(account.getId() + ":" + line.getFund().getId()))
                throw new IllegalArgumentException("Transfer metadata requires its exact canonical four-line reallocation");
            accounts.add(account.getId());
        }
        if (lines.size() != 4 || accounts.size() != 2
                || lines.stream().filter(line -> line.getAccount().getAccountType() == AccountType.ASSET).count() != 2)
            throw new IllegalArgumentException("Transfer metadata requires one ordinary allocation ASSET and one EQUITY account across two funds");
    }

    private static Choice choice(Account a)
    {
        return new Choice(a.getId(), a.getCode() + " — " + a.getName());
    }

    private static FundTransferView view(FundTransfer f)
    {
        Txn t = f.getPostedTxn();
        return new FundTransferView(f.getId(), f.getTransferDate(), t.getId(), f.getFromFund().getCode(), f.getToFund().getCode(),
                f.getAmount(), f.getMemo(), "REVERSED".equals(t.getStatus()) ? "Reversed history" : t.getReversalOf() != null ? "Reversal" : "Saved");
    }

    private Fund requireFund(EntityManager em, CompanyOwnershipService ownership, Company company, Long id, LocalDate date)
    {
        Fund f = em.find(Fund.class, id);
        if (f == null)
        {
            throw new IllegalArgumentException("Unknown fund: " + id);
        }
        var seen = new HashSet<Long>();
        for (Fund cursor = f; cursor != null; cursor = cursor.getParent())
        {
            ownership.requireOwnedBy(company, cursor, "Fund");
            if (!seen.add(cursor.getId()) || !cursor.isActive() || !eligibleType(cursor)
                    || cursor.getRestrictionText() != null && !cursor.getRestrictionText().isBlank()
                    || cursor.getEffectiveFrom() != null && date.isBefore(cursor.getEffectiveFrom())
                    || cursor.getEffectiveTo() != null && date.isAfter(cursor.getEffectiveTo()))
            {
                throw new IllegalArgumentException("Fund " + cursor.getCode() + " or its hierarchy is inactive, restricted or ineffective on this date.");
            }
        }
        return f;
    }

    private static boolean eligibleType(Fund f)
    {
        return f.getFundType() == FundType.UNRESTRICTED || f.getFundType() == FundType.DESIGNATED;
    }

    private static boolean eligibleAccount(Account a, AccountType type, NormalBalance normal)
    {
        return a.getAccountType() == type && a.getNormalBalance() == normal && a.getAccountFunction() == null && a.getSubtype() == null;
    }

    private Account requireAccount(EntityManager em, CompanyOwnershipService ownership, Company company, Long id, AccountType type, NormalBalance normal)
    {
        if (id == null)
        {
            throw new IllegalArgumentException("Allocation and net-assets accounts are required.");
        }
        Account a = em.find(Account.class, id);
        if (a == null)
        {
            throw new IllegalArgumentException("Unknown account: " + id);
        }
        ownership.requireOwnedBy(company, a, "Transfer account");
        if (!a.isActive() || !a.isPosting() || company.getActiveChartOfAccounts() == null
                || company.getActiveChartOfAccounts().getStatus() != ChartStatus.ACTIVE
                || !a.getChart().getId().equals(company.getActiveChartOfAccounts().getId()) || !eligibleAccount(a, type, normal))
        {
            throw new IllegalArgumentException("Select active ordinary non-bank allocation ASSET and net-assets EQUITY posting accounts from the active chart.");
        }
        return a;
    }

    private static void requireAvailable(EntityManager em, Company company, Fund fund, LocalDate date, BigDecimal amount)
    {
        var rows = em.createQuery("select t.txnDate, a.normalBalance, sum(s.amountSigned) from TxnSplit s join s.txn t join s.account a "
                        + "where t.company = :company and s.fund = :fund and a.accountType in :types "
                        + "group by t.txnDate, a.normalBalance order by t.txnDate", Object[].class)
                .setParameter("company", company).setParameter("fund", fund)
                .setParameter("types", List.of(AccountType.EQUITY, AccountType.INCOME, AccountType.EXPENSE)).getResultList();
        BigDecimal balance = BigDecimal.ZERO;
        var daily = new TreeMap<LocalDate, BigDecimal>();
        for (var row : rows)
        {
            BigDecimal effect = (BigDecimal) row[2];
            if (row[1] == NormalBalance.DEBIT)
            {
                effect = effect.negate();
            }
            daily.merge((LocalDate) row[0], effect, BigDecimal::add);
        }
        for (var entry : daily.entrySet())
        {
            if (!entry.getKey().isAfter(date))
            {
                balance = balance.add(entry.getValue());
            }
        }
        if (balance.compareTo(amount) < 0)
        {
            throw new IllegalArgumentException("Source fund has insufficient ledger net assets on the transfer date.");
        }
        for (var entry : daily.tailMap(date, false).entrySet())
        {
            balance = balance.add(entry.getValue());
            if (balance.compareTo(amount) < 0)
            {
                throw new IllegalArgumentException("Transfer would overdraw source net assets on a later recorded date.");
            }
        }
    }

    private static TransactionLineCommand line(Account a, Fund f, BigDecimal debit, BigDecimal credit)
    {
        return new TransactionLineCommand(a.getId(), f.getId(), null, null, null, debit, credit, false, null);
    }

    private static String hash(FundTransferCommand c, BigDecimal amount, String explanation)
    {
        String text = c.date() + "|" + c.fromFundId() + "|" + c.toFundId() + "|" + c.allocationAccountId() + "|" + c.equityAccountId() + "|" + amount + "|" + explanation;
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        }
        catch (java.security.NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    public record Choice(Long id, String label)
    {
        @Override
        public String toString()
        {
            return label;
        }
    }
    public record Choices(List<Choice> funds, List<Choice> allocationAccounts, List<Choice> equityAccounts)
    {
    }
}
