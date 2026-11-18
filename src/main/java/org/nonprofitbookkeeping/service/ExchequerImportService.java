package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.interchange.exchequer.ExchequerReportCsvDocument;
import org.nonprofitbookkeeping.interchange.exchequer.ExchequerReportCsvImporter;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;

/** Session-only review, followed by one authorized atomic canonical ledger import. */
public final class ExchequerImportService
{
    private final Jpa jpa;
    private final String companyCode;
    private final AuthorizationGuard guard;

    public ExchequerImportService(Jpa jpa, String companyCode, AuthorizationGuard guard)
    {
        this.jpa = Objects.requireNonNull(jpa);
        this.companyCode = Objects.requireNonNull(companyCode).strip().toUpperCase(Locale.ROOT);
        this.guard = guard;
    }

    public record ProposedEntry(int sourceLine, TransactionCommand command)
    {
    }

    public record Preview(String companyCode, ExchequerReportCsvDocument source,
                          List<ProposedEntry> entries)
    {
        public Preview
        {
            entries = List.copyOf(entries);
        }
    }

    public record Result(List<Long> transactionIds)
    {
        public Result
        {
            transactionIds = List.copyOf(transactionIds);
        }
    }

    /** Leaves labels unmapped: identical codes/names are not stable account identities. */
    public Preview preview(Path source)
    {
        var document = new ExchequerReportCsvImporter().read(source);
        List<ProposedEntry> entries = new ArrayList<>();
        for (var row : document.rows())
        {
            if (row.kind() == ExchequerReportCsvDocument.RowKind.NON_POSTING)
            {
                continue;
            }
            List<TransactionLineCommand> lines = new ArrayList<>();
            if (row.netAmount() != null && row.netAmount().signum() != 0)
            {
                lines.add(line(row.netAmount(), "Bank: " + row.bankAccount()));
            }
            for (var split : row.splits())
            {
                BigDecimal effect = split.bankEffect();
                if (effect != null && effect.signum() != 0)
                {
                    lines.add(line(effect.negate(), "Category: " + split.category()));
                }
            }
            String memo = String.join(" | ", List.of(row.payee(), row.notes()).stream()
                    .filter(s -> s != null && !s.isBlank()).toList());
            entries.add(new ProposedEntry(row.sourceLine(),
                    new TransactionCommand(row.transactionDate(), null, memo, null, lines, List.of(),
                            new TransactionInformation(row.reference(), row.statementDate(), row.incomingDate()))));
        }
        return new Preview(companyCode, document, entries);
    }

    private static TransactionLineCommand line(BigDecimal signedDebit, String label)
    {
        return new TransactionLineCommand(null, null, null, null, null,
                signedDebit.max(BigDecimal.ZERO), signedDebit.negate().max(BigDecimal.ZERO), false, label);
    }

    /** Exact source identities survive correction/deletion because audit receipts are retained. */
    public Set<Integer> importedSourceLines(Preview preview)
    {
        requireScope(preview);
        Set<Integer> result = new HashSet<>();
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCode);
            for (var row : preview.source().rows())
            {
                if (hasReceipt(em, identity(company, row)))
                {
                    result.add(row.sourceLine());
                }
            }
        }
        return Set.copyOf(result);
    }

    /** No writes occur until the source, every edited command and duplicate acknowledgement pass. */
    public Result commit(Preview preview, List<ProposedEntry> reviewed, boolean acknowledgeSimilar)
    {
        String actor = ServiceAuthorization.actor(guard, ApplicationPermission.BOOKKEEPING_WRITE,
                companyCode, "commit reviewed Exchequer CSV entries", "system");
        requireScope(preview);
        reviewed = List.copyOf(reviewed);
        if (reviewed.isEmpty())
        {
            throw new IllegalArgumentException("There are no proposed entries to commit.");
        }
        var current = new ExchequerReportCsvImporter().read(preview.source().source(),
                Charset.forName(preview.source().encoding()));
        if (!current.sourceSha256().equals(preview.source().sourceSha256()))
        {
            throw new IllegalArgumentException("The source CSV changed. Open it again before committing.");
        }
        Map<Integer, ExchequerReportCsvDocument.Row> rows = new HashMap<>();
        current.rows().forEach(row -> rows.put(row.sourceLine(), row));
        Set<Integer> seen = new HashSet<>();
        Set<Integer> eligible = new HashSet<>();
        preview.entries().forEach(row -> eligible.add(row.sourceLine()));
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCode);
                SupplementalIntegrityService.lock(em, company);
                em.refresh(company);
                List<Long> ids = new ArrayList<>();
                for (var proposal : reviewed)
                {
                    int sourceLine = proposal.sourceLine();
                    var row = rows.get(sourceLine);
                    if (row == null || !eligible.contains(sourceLine) || !seen.add(sourceLine))
                    {
                        throw new IllegalArgumentException("Unknown or repeated proposed source line " + sourceLine);
                    }
                    try
                    {
                        UUID identity = identity(company, row);
                        if (hasReceipt(em, identity))
                        {
                            throw new IllegalArgumentException("This source record was already imported; remove it from the proposals.");
                        }
                        validateCurrentReferences(em, company, proposal.command());
                        if (!acknowledgeSimilar && hasSimilar(em, company, proposal.command()))
                        {
                            throw new IllegalArgumentException("A journal entry with this date and amount already exists. Review Journal and acknowledge similar entries before committing.");
                        }
                        Txn txn = new TransactionEntryService(jpa).enter(em, company, proposal.command(),
                                UUID.nameUUIDFromBytes((identity + ":txn").getBytes(StandardCharsets.UTF_8)), actor,
                                "Reviewed Exchequer CSV source " + current.sourceSha256() + ", line " + sourceLine);
                        em.flush();
                        ids.add(txn.getId());
                        AuditEvent receipt = new AuditEvent();
                        receipt.initializeOperationIdentity(identity);
                        receipt.setCompany(company);
                        receipt.setActor(actor);
                        receipt.setActionType("EXCHEQUER_CSV_IMPORTED");
                        receipt.setEntityType("Txn");
                        receipt.setEntityId(txn.getId().toString());
                        receipt.setSummary("Imported reviewed Exchequer CSV line " + sourceLine);
                        receipt.setReason("Source SHA-256 " + current.sourceSha256());
                        receipt.setBeforeValue(row.originalRecord());
                        receipt.setAfterValue(proposal.command().toString());
                        em.persist(receipt);
                    }
                    catch (RuntimeException ex)
                    {
                        throw new IllegalArgumentException("Source line " + sourceLine + " (worksheet row "
                                + row.worksheetRow() + "): " + ex.getMessage(), ex);
                    }
                }
                em.getTransaction().commit();
                return new Result(ids);
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

    private void requireScope(Preview preview)
    {
        if (preview == null || !companyCode.equals(preview.companyCode()))
        {
            throw new IllegalArgumentException("The import review belongs to a different company.");
        }
    }

    private UUID identity(Company company, ExchequerReportCsvDocument.Row row)
    {
        StringBuilder fingerprint = new StringBuilder("exchequer-csv:v1:" + company.getId() + ":");
        row.originalFields().forEach(field -> fingerprint.append(field.length()).append(':').append(field));
        return UUID.nameUUIDFromBytes(fingerprint.toString().getBytes(StandardCharsets.UTF_8));
    }

    private boolean hasReceipt(EntityManager em, UUID identity)
    {
        return !em.createQuery("select a.id from AuditEvent a where a.portableId = :id", Long.class)
                .setParameter("id", identity).setMaxResults(1).getResultList().isEmpty();
    }

    /** Pure, exact validation of one CSV candidate, including lossless ledger storage precision. */
    public static TransactionValidationResult validateCandidate(TransactionCommand command)
    {
        return new TransactionCommandValidator().validate(command);
    }

    private void validateCurrentReferences(EntityManager em, Company company, TransactionCommand command)
    {
        var validation = validateCandidate(command);
        if (!validation.valid())
        {
            throw new IllegalArgumentException(String.join(" ", validation.errors()));
        }
        var chart = company.getActiveChartOfAccounts();
        if (chart == null)
        {
            throw new IllegalArgumentException("Select an active Chart of Accounts before importing.");
        }
        new CompanyOwnershipService(jpa).ensureOwnedBy(em, company, chart, "Selected chart");
        if (command.memo() != null && command.memo().length() > 500)
        {
            throw new IllegalArgumentException("Memo must be at most 500 characters. Full source text remains in the import audit.");
        }
        if (command.bankAccountId() != null)
        {
            Account header = em.find(Account.class, command.bankAccountId());
            if (header == null || !header.isActive() || !header.isPosting()
                    || !chart.getId().equals(header.getChart().getId()))
            {
                throw new IllegalArgumentException("Header bank account must be active and in the selected chart.");
            }
        }
        if (command.payeeId() != null)
        {
            Counterparty payee = em.find(Counterparty.class, command.payeeId());
            if (payee == null || !payee.isActive())
            {
                throw new IllegalArgumentException("Payee must be active. Refresh choices and review mappings.");
            }
        }
        for (var line : command.lines())
        {
            if (line.budgetCategoryId() != null)
            {
                BudgetCategory category = em.find(BudgetCategory.class, line.budgetCategoryId());
                if (category == null || !category.isActive())
                {
                    throw new IllegalArgumentException("Budget category must be active.");
                }
            }
            if (line.activityId() != null)
            {
                Activity activity = em.find(Activity.class, line.activityId());
                if (activity == null || !activity.isActive())
                {
                    throw new IllegalArgumentException("Event must be active.");
                }
            }
            if (line.merchantId() != null)
            {
                Merchant merchant = em.find(Merchant.class, line.merchantId());
                if (merchant == null || !merchant.isActive())
                {
                    throw new IllegalArgumentException("Merchant must be active.");
                }
            }
            if (line.notes() != null && line.notes().length() > 500)
            {
                throw new IllegalArgumentException("Line notes must be at most 500 characters.");
            }
            Account account = em.find(Account.class, line.accountId());
            Fund fund = em.find(Fund.class, line.fundId());
            if (account == null || !account.isActive() || !account.isPosting()
                    || !chart.getId().equals(account.getChart().getId()))
            {
                throw new IllegalArgumentException("Every account must be an active posting account in the selected chart. Refresh choices and review mappings.");
            }
            if (fund == null || !fund.isActive())
            {
                throw new IllegalArgumentException("Every fund must be active. Refresh choices and review mappings.");
            }
        }
    }

    private static boolean hasSimilar(EntityManager em, Company company, TransactionCommand command)
    {
        BigDecimal total = command.lines().stream().map(TransactionLineCommand::debit)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Long, BigDecimal> totals = new HashMap<>();
        for (var split : em.createQuery("from TxnSplit s join fetch s.account where s.txn.company = :company and s.txn.txnDate = :date", TxnSplit.class)
                .setParameter("company", company).setParameter("date", command.date()).getResultList())
        {
            BigDecimal debit = split.getAccount().getNormalBalance() == NormalBalance.DEBIT
                    ? split.getAmountSigned() : split.getAmountSigned().negate();
            totals.merge(split.getTxn().getId(), debit.max(BigDecimal.ZERO), BigDecimal::add);
        }
        return totals.values().stream().anyMatch(value -> value.compareTo(total) == 0);
    }
}
