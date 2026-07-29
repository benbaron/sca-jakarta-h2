package org.nonprofitbookkeeping.interchange.sclx;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.ImportIssue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Loads the selected company's bounded banking and native reconciliation graph. */
final class SclxBankingSnapshotQuery
{
    SclxBankingSnapshot query(EntityManager em, Company company)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(company.getId(), "selected company id");

        List<Bank> banks = em.createQuery(
                        "select b from Bank b where b.company = :company order by b.portableId",
                        Bank.class)
                .setParameter("company", company)
                .getResultList();
        List<CompanyBankAccount> accounts = em.createQuery(
                        "select a from CompanyBankAccount a "
                                + "left join fetch a.bank "
                                + "left join fetch a.account ledger "
                                + "left join fetch ledger.chart "
                                + "where a.company = :company order by a.portableId",
                        CompanyBankAccount.class)
                .setParameter("company", company)
                .getResultList();
        List<BankImportBatch> batches = em.createQuery(
                        "select b from BankImportBatch b "
                                + "left join fetch b.bankAccount "
                                + "where b.company = :company order by b.portableId",
                        BankImportBatch.class)
                .setParameter("company", company)
                .getResultList();
        List<BankStatementLine> lines = em.createQuery(
                        "select l from BankStatementLine l "
                                + "join fetch l.batch "
                                + "left join fetch l.bankAccount "
                                + "left join fetch l.acceptedTransaction "
                                + "left join fetch l.matchedTransaction "
                                + "where l.company = :company order by l.portableId",
                        BankStatementLine.class)
                .setParameter("company", company)
                .getResultList();
        List<ImportIssue> issues = em.createQuery(
                        "select i from ImportIssue i "
                                + "join fetch i.batch b "
                                + "left join fetch i.statementLine "
                                + "where b.company = :company order by i.portableId",
                        ImportIssue.class)
                .setParameter("company", company)
                .getResultList();

        @SuppressWarnings("unchecked")
        List<Object[]> sessionRows = em.createNativeQuery("""
                        select s.portable_id,
                               a.portable_id,
                               s.statement_start_date,
                               s.statement_end_date,
                               s.statement_ending_balance,
                               s.mismatch_policy,
                               s.status,
                               s.notes,
                               s.beginning_balance,
                               s.book_balance_all,
                               s.book_balance_cleared,
                               s.difference_amount,
                               s.created_at,
                               s.updated_at
                          from bank_reconciliation_session s
                          join company_bank_account a on a.id = s.bank_account_id
                         where s.company_id = ?
                         order by s.portable_id
                        """)
                .setParameter(1, company.getId())
                .getResultList();
        List<SclxBankingSnapshot.ReconciliationSession> sessions = sessionRows.stream()
                .map(SclxBankingSnapshotQuery::session)
                .toList();

        @SuppressWarnings("unchecked")
        List<Object[]> matchRows = em.createNativeQuery("""
                        select m.portable_id,
                               s.portable_id,
                               l.portable_id,
                               m.txn_split_id,
                               m.match_status,
                               m.resolution_note,
                               m.created_at,
                               m.updated_at
                          from bank_reconciliation_match m
                          join bank_reconciliation_session s on s.id = m.session_id
                          left join bank_statement_line l on l.id = m.statement_line_id
                         where s.company_id = ?
                         order by m.portable_id
                        """)
                .setParameter(1, company.getId())
                .getResultList();
        List<SclxBankingSnapshot.ReconciliationMatch> matches = matchRows.stream()
                .map(SclxBankingSnapshotQuery::match)
                .toList();

        return new SclxBankingSnapshot(banks, accounts, batches, lines, issues, sessions, matches);
    }

    private static SclxBankingSnapshot.ReconciliationSession session(Object[] row)
    {
        return new SclxBankingSnapshot.ReconciliationSession(
                uuid(row[0], "reconciliation session portable_id"),
                uuid(row[1], "reconciliation bank account portable_id"),
                date(row[2], "statement_start_date"),
                date(row[3], "statement_end_date"),
                decimal(row[4], true, "statement_ending_balance"),
                text(row[5], "mismatch_policy"),
                text(row[6], "status"),
                optionalText(row[7]),
                decimal(row[8], false, "beginning_balance"),
                decimal(row[9], false, "book_balance_all"),
                decimal(row[10], false, "book_balance_cleared"),
                decimal(row[11], false, "difference_amount"),
                instant(row[12], "created_at"),
                instant(row[13], "updated_at"));
    }

    private static SclxBankingSnapshot.ReconciliationMatch match(Object[] row)
    {
        return new SclxBankingSnapshot.ReconciliationMatch(
                uuid(row[0], "reconciliation match portable_id"),
                uuid(row[1], "reconciliation session portable_id"),
                nullableUuid(row[2]),
                row[3] == null ? null : ((Number) row[3]).longValue(),
                text(row[4], "match_status"),
                optionalText(row[5]),
                instant(row[6], "created_at"),
                instant(row[7], "updated_at"));
    }

    private static UUID uuid(Object value, String field)
    {
        UUID uuid = nullableUuid(value);
        return Objects.requireNonNull(uuid, field);
    }

    private static UUID nullableUuid(Object value)
    {
        if (value == null)
        {
            return null;
        }
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private static LocalDate date(Object value, String field)
    {
        Objects.requireNonNull(value, field);
        if (value instanceof LocalDate date)
        {
            return date;
        }
        if (value instanceof java.sql.Date date)
        {
            return date.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private static Instant instant(Object value, String field)
    {
        Objects.requireNonNull(value, field);
        if (value instanceof Instant instant)
        {
            return instant;
        }
        if (value instanceof OffsetDateTime offset)
        {
            return offset.toInstant();
        }
        if (value instanceof Timestamp timestamp)
        {
            return timestamp.toInstant();
        }
        if (value instanceof LocalDateTime local)
        {
            return local.toInstant(ZoneOffset.UTC);
        }
        return Instant.parse(value.toString());
    }

    private static BigDecimal decimal(Object value, boolean nullable, String field)
    {
        if (value == null && nullable)
        {
            return null;
        }
        Objects.requireNonNull(value, field);
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private static String text(Object value, String field)
    {
        Objects.requireNonNull(value, field);
        String text = value.toString();
        if (text.isBlank())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return text;
    }

    private static String optionalText(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text.strip();
    }
}
