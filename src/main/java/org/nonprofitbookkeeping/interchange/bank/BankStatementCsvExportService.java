package org.nonprofitbookkeeping.interchange.bank;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.interchange.AtomicInterchangeFileWriter;
import org.nonprofitbookkeeping.interchange.InterchangeMessageSeverity;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Reconstructs and atomically exports one configured account's durable reviewed rows as normalized CSV. */
public final class BankStatementCsvExportService
{
    private final Jpa jpa;
    private final Supplier<Path> activeDatabasePath;
    private final NormalizedBankCsvSerializer serializer;
    private final AtomicInterchangeFileWriter fileWriter;

    public BankStatementCsvExportService(Jpa jpa, Supplier<Path> activeDatabasePath)
    {
        this(jpa, activeDatabasePath, new NormalizedBankCsvSerializer(), new AtomicInterchangeFileWriter());
    }

    BankStatementCsvExportService(
            Jpa jpa,
            Supplier<Path> activeDatabasePath,
            NormalizedBankCsvSerializer serializer,
            AtomicInterchangeFileWriter fileWriter)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.activeDatabasePath = Objects.requireNonNull(activeDatabasePath, "activeDatabasePath");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.fileWriter = Objects.requireNonNull(fileWriter, "fileWriter");
    }

    public BankStatementExportResult export(BankStatementExportRequest request)
    {
        Objects.requireNonNull(request, "request");
        Snapshot snapshot = snapshot(request);
        if (snapshot.rows().isEmpty())
        {
            throw new IllegalArgumentException("No durable bank-statement rows exist in the selected date range.");
        }
        byte[] bytes = serializer.serialize(snapshot.rows());
        Path destination = fileWriter.write(
                request.destination(), bytes, request.overwriteExisting(), activeDatabasePath.get(),
                "Bank-statement");
        return new BankStatementExportResult(
                destination,
                request.companyCode(),
                snapshot.bankAccountExternalId(),
                request.fromDate(),
                request.throughDate(),
                snapshot.rows().size(),
                bytes.length,
                sha256(bytes),
                snapshot.messages());
    }

    Snapshot snapshot(BankStatementExportRequest request)
    {
        try (EntityManager em = jpa.em())
        {
            CompanyBankAccount account = em.createQuery("""
                            select a from CompanyBankAccount a
                            join fetch a.company c
                            join fetch a.bank b
                            join fetch a.account p
                            where a.id = :accountId and c.code = :companyCode
                            """, CompanyBankAccount.class)
                    .setParameter("accountId", request.bankAccountId())
                    .setParameter("companyCode", request.companyCode())
                    .getResultStream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Configured bank account does not belong to the selected company."));
            if (!account.getCompany().isActive()
                    || !account.isActive()
                    || !account.getBank().isActive()
                    || !account.getAccount().isActive())
            {
                throw new IllegalArgumentException(
                        "Company, bank, configured bank account, and posting account must be active for export.");
            }

            Set<Long> probableDuplicateLines = new HashSet<>(em.createQuery("""
                            select distinct i.statementLine.id from ImportIssue i
                            where i.statementLine is not null
                              and i.statementLine.company = :company
                              and i.statementLine.bankAccount = :account
                              and i.code = 'PROBABLE_DUPLICATE'
                            """, Long.class)
                    .setParameter("company", account.getCompany())
                    .setParameter("account", account)
                    .getResultList());

            List<Object[]> values = em.createQuery("""
                            select l.id, b.sourceFormat, b.portableId, b.sourceExternalId, b.sourceName,
                                   l.portableId, l.sourceExternalId,
                                   b.sourceInstitutionId, b.sourceBankId, b.sourceAccountId, b.sourceAccountType,
                                   l.transactionDate, l.postedDate, l.amount, l.currency, b.currency,
                                   l.sourceTransactionId, l.transactionType, l.sourcePayeeId, l.name, l.memo,
                                   l.checkNumber, l.reference, l.correctionAction, l.correctedSourceTransactionId,
                                   b.statementStartDate, b.statementEndDate, b.ledgerBalance, b.availableBalance,
                                   l.status, mt.portableId, l.deterministicFingerprint, l.sourceRowNumber
                              from BankStatementLine l
                              join l.batch b
                              left join l.matchedTransaction mt
                             where l.company = :company
                               and l.bankAccount = :account
                               and coalesce(l.postedDate, l.transactionDate) between :fromDate and :throughDate
                             order by case when l.postedDate is null then 1 else 0 end,
                                      l.postedDate,
                                      case when l.transactionDate is null then 1 else 0 end,
                                      l.transactionDate, l.sourceTransactionId,
                                      l.deterministicFingerprint, l.sourceRowNumber, l.id
                            """, Object[].class)
                    .setParameter("company", account.getCompany())
                    .setParameter("account", account)
                    .setParameter("fromDate", request.fromDate())
                    .setParameter("throughDate", request.throughDate())
                    .setMaxResults(1_000_001)
                    .getResultList();
            if (values.size() > 1_000_000)
            {
                throw new IllegalArgumentException(
                        "Bank-statement export exceeds the 1,000,000-row operation limit.");
            }

            List<BankStatementExportRow> rows = new ArrayList<>(values.size());
            int missingPayeeIds = 0;
            int missingLedgerBalances = 0;
            int missingAvailableBalances = 0;
            for (Object[] value : values)
            {
                long lineId = (Long) value[0];
                BankStatementLine.Status status = (BankStatementLine.Status) value[29];
                String duplicateStatus = status == BankStatementLine.Status.DUPLICATE
                        ? "EXACT"
                        : probableDuplicateLines.contains(lineId) ? "PROBABLE" : "";
                String currency = firstText((String) value[14], (String) value[15], account.getCompany().getDefaultCurrency());
                rows.add(new BankStatementExportRow(
                        value[1].toString(),
                        firstText((String) value[3], value[2].toString()),
                        (String) value[4],
                        firstText((String) value[6], value[5].toString()),
                        (String) value[7],
                        (String) value[8],
                        (String) value[9],
                        (String) value[10],
                        (LocalDate) value[11],
                        (LocalDate) value[12],
                        (BigDecimal) value[13],
                        currency,
                        (String) value[16],
                        (String) value[17],
                        (String) value[18],
                        (String) value[19],
                        (String) value[20],
                        (String) value[21],
                        (String) value[22],
                        (String) value[23],
                        (String) value[24],
                        (LocalDate) value[25],
                        (LocalDate) value[26],
                        (BigDecimal) value[27],
                        (BigDecimal) value[28],
                        status.name(),
                        duplicateStatus,
                        value[30] == null ? "" : value[30].toString()));
                if (value[18] == null)
                {
                    missingPayeeIds++;
                }
                if (value[27] == null)
                {
                    missingLedgerBalances++;
                }
                if (value[28] == null)
                {
                    missingAvailableBalances++;
                }
            }
            List<InterchangeValidationMessage> messages = warnings(
                    missingPayeeIds, missingLedgerBalances, missingAvailableBalances);
            return new Snapshot(
                    account.getPortableId().toString(),
                    firstTextOrBlank(account.getOfxBankId()),
                    firstTextOrBlank(account.getOfxAccountId()),
                    firstTextOrBlank(account.getAccountType()),
                    account.getCompany().getDefaultCurrency(),
                    List.copyOf(rows),
                    messages);
        }
    }

    private static List<InterchangeValidationMessage> warnings(
            int missingPayeeIds, int missingLedgerBalances, int missingAvailableBalances)
    {
        List<InterchangeValidationMessage> messages = new ArrayList<>();
        if (missingPayeeIds > 0)
        {
            messages.add(warning(
                    "BANK_CSV_PAYEE_ID_UNAVAILABLE",
                    "rows.payee_id",
                    "Durable bank review does not retain source PAYEEID; " + missingPayeeIds
                            + " exported row(s) leave payee_id empty."));
        }
        if (missingLedgerBalances > 0)
        {
            messages.add(warning(
                    "BANK_CSV_LEDGER_BALANCE_UNAVAILABLE",
                    "rows.ledger_balance",
                    missingLedgerBalances + " exported row(s) have no authoritative imported ledger balance."));
        }
        if (missingAvailableBalances > 0)
        {
            messages.add(warning(
                    "BANK_CSV_AVAILABLE_BALANCE_UNAVAILABLE",
                    "rows.available_balance",
                    missingAvailableBalances + " exported row(s) have no authoritative imported available balance."));
        }
        return List.copyOf(messages);
    }

    private static InterchangeValidationMessage warning(String code, String path, String message)
    {
        return new InterchangeValidationMessage(
                InterchangeMessageSeverity.WARNING, code, path, message, false);
    }

    private static String firstText(String... values)
    {
        for (String value : values)
        {
            if (value != null && !value.isBlank())
            {
                return value.trim();
            }
        }
        throw new IllegalArgumentException("Exported bank row has no currency.");
    }

    private static String firstTextOrBlank(String value)
    {
        return value == null ? "" : value.trim();
    }

    private static String sha256(byte[] bytes)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    record Snapshot(
            String bankAccountExternalId,
            String configuredBankId,
            String configuredAccountId,
            String configuredAccountType,
            String companyCurrency,
            List<BankStatementExportRow> rows,
            List<InterchangeValidationMessage> messages)
    {
        Snapshot
        {
            UUID.fromString(bankAccountExternalId);
            rows = List.copyOf(rows);
            messages = List.copyOf(messages);
        }
    }
}
