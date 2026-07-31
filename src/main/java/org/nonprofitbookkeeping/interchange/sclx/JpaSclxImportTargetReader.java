package org.nonprofitbookkeeping.interchange.sclx;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.interchange.InterchangeFormat;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.InterchangeIdentity;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Batches every local fact needed by SCLX preview in one read-only EntityManager scope. */
final class JpaSclxImportTargetReader implements SclxImportTargetReader
{
    private final Jpa jpa;

    JpaSclxImportTargetReader(Jpa jpa)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
    }

    @Override
    public SclxImportTargetSnapshot read(String companyCode, String sourceSystem)
    {
        String code = requireText(companyCode, "companyCode").toUpperCase(Locale.ROOT);
        String source = requireText(sourceSystem, "sourceSystem");
        try (EntityManager em = jpa.em())
        {
            Company company = em.createQuery("""
                    select c from Company c
                    left join fetch c.activeChartOfAccounts
                    where upper(c.code) = :code
                    """, Company.class)
                    .setParameter("code", code)
                    .getResultStream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Company does not exist: " + code));

            Map<String, SclxImportTargetSnapshot.TargetAccount> accounts = accounts(em, company);
            Map<String, SclxImportTargetSnapshot.TargetFund> funds = funds(em, company);
            Map<SclxImportTargetSnapshot.ExternalIdentityKey, SclxImportTargetSnapshot.IdentityFact> identities =
                    identities(em, company, source);
            List<SclxImportTargetSnapshot.ClosedRange> closedRanges = closedRanges(em, company);
            Set<String> finalizedTransactions = finalizedTransactionIds(em, company);
            boolean populated = !accounts.isEmpty() || !funds.isEmpty()
                    || count(em, "select count(t) from Txn t where t.company = :company", company) > 0L
                    || count(em, "select count(c) from BudgetCategory c where c.company = :company", company) > 0L
                    || count(em, "select count(p) from BudgetPlan p where p.company = :company", company) > 0L
                    || count(em, "select count(a) from Activity a where a.company = :company", company) > 0L
                    || count(em, "select count(c) from Counterparty c where c.company = :company", company) > 0L
                    || count(em, "select count(m) from Merchant m where m.company = :company", company) > 0L
                    || count(em, "select count(a) from FixedAsset a where a.company = :company", company) > 0L;

            return new SclxImportTargetSnapshot(
                    company.getCode(),
                    company.getDisplayName(),
                    populated,
                    accounts,
                    funds,
                    identities,
                    closedRanges,
                    finalizedTransactions);
        }
    }

    private static Map<String, SclxImportTargetSnapshot.TargetAccount> accounts(
            EntityManager em, Company company)
    {
        ChartOfAccounts chart = company.getActiveChartOfAccounts();
        if (chart == null)
        {
            return Map.of();
        }
        List<Account> values = em.createQuery("""
                select a from Account a
                where a.chart = :chart
                order by a.code
                """, Account.class)
                .setParameter("chart", chart)
                .getResultList();
        Map<String, SclxImportTargetSnapshot.TargetAccount> result = new LinkedHashMap<>();
        for (Account account : values)
        {
            String code = account.getCode();
            result.put(code, new SclxImportTargetSnapshot.TargetAccount(
                    SclxPortableIdentity.account(company.getCode(), code),
                    code,
                    account.getAccountType().name(),
                    account.getNormalBalance().name(),
                    account.isPosting(),
                    account.isActive(),
                    String.valueOf(account.getId())));
        }
        return result;
    }

    private static Map<String, SclxImportTargetSnapshot.TargetFund> funds(
            EntityManager em, Company company)
    {
        List<Fund> values = em.createQuery("""
                select f from Fund f
                where f.company = :company
                order by f.code
                """, Fund.class)
                .setParameter("company", company)
                .getResultList();
        Map<String, SclxImportTargetSnapshot.TargetFund> result = new LinkedHashMap<>();
        for (Fund fund : values)
        {
            String code = fund.getCode();
            result.put(code, new SclxImportTargetSnapshot.TargetFund(
                    SclxPortableIdentity.fund(company.getCode(), code),
                    code,
                    fund.getFundType().name(),
                    fund.isActive(),
                    String.valueOf(fund.getId())));
        }
        return result;
    }

    private static Map<SclxImportTargetSnapshot.ExternalIdentityKey, SclxImportTargetSnapshot.IdentityFact> identities(
            EntityManager em, Company company, String sourceSystem)
    {
        List<InterchangeIdentity> values = em.createQuery("""
                from InterchangeIdentity i
                where i.company = :company
                  and i.formatCode = :format
                  and i.sourceSystem = :source
                order by i.entityType, i.externalId
                """, InterchangeIdentity.class)
                .setParameter("company", company)
                .setParameter("format", InterchangeFormat.SCLX.name())
                .setParameter("source", sourceSystem)
                .getResultList();
        Map<SclxImportTargetSnapshot.ExternalIdentityKey, SclxImportTargetSnapshot.IdentityFact> result =
                new LinkedHashMap<>();
        for (InterchangeIdentity identity : values)
        {
            SclxImportTargetSnapshot.ExternalIdentityKey key = new SclxImportTargetSnapshot.ExternalIdentityKey(
                    identity.getEntityType(), identity.getExternalId());
            SclxImportTargetSnapshot.IdentityFact previous = result.put(key,
                    new SclxImportTargetSnapshot.IdentityFact(
                            identity.getNormalizedContentHash(), identity.getLocalEntityId()));
            if (previous != null)
            {
                throw new IllegalStateException("Duplicate durable interchange identity: "
                        + identity.getEntityType() + " " + identity.getExternalId());
            }
        }
        return result;
    }

    private static List<SclxImportTargetSnapshot.ClosedRange> closedRanges(
            EntityManager em, Company company)
    {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                select start_date, end_date
                from period_close_range
                where company_id = ? and status = 'CLOSED'
                order by start_date, end_date
                """)
                .setParameter(1, company.getId())
                .getResultList();
        return rows.stream()
                .map(row -> new SclxImportTargetSnapshot.ClosedRange(
                        toLocalDate(row[0]), toLocalDate(row[1])))
                .toList();
    }

    private static Set<String> finalizedTransactionIds(EntityManager em, Company company)
    {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("""
                select distinct cast(t.id as varchar)
                from bank_reconciliation_session s
                join bank_reconciliation_match m on m.session_id = s.id
                join txn_split ts on ts.id = m.txn_split_id
                join txn t on t.id = ts.txn_id
                where s.company_id = ?
                  and s.status = 'FINALIZED'
                  and m.txn_split_id is not null
                """)
                .setParameter(1, company.getId())
                .getResultList();
        return rows.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    private static long count(EntityManager em, String query, Company company)
    {
        return em.createQuery(query, Long.class)
                .setParameter("company", company)
                .getSingleResult();
    }

    private static LocalDate toLocalDate(Object value)
    {
        if (value instanceof LocalDate date)
        {
            return date;
        }
        if (value instanceof Date date)
        {
            return date.toLocalDate();
        }
        String text = String.valueOf(value);
        return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
