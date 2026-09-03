package org.nonprofitbookkeeping.interchange.bank;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.BankCsvMappingProfile;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.AuthorizationGuard;

import java.util.List;
import java.util.UUID;

/** Canonical company-owned persistence boundary for validated bank CSV mapping profiles. */
public final class BankCsvMappingProfileService
{
    private static final int MAX_PROFILES_PER_COMPANY = 1_000;

    private final Jpa jpa;
    private final AuthorizationGuard authorizationGuard;

    public BankCsvMappingProfileService(Jpa jpa)
    {
        this(jpa, null);
    }

    public BankCsvMappingProfileService(Jpa jpa, AuthorizationGuard authorizationGuard)
    {
        this.jpa = java.util.Objects.requireNonNull(jpa, "jpa");
        this.authorizationGuard = authorizationGuard;
    }

    public ProfileSummary create(String companyCode, long bankAccountId, String profileJson)
    {
        requireBookkeepingWrite(companyCode, "create bank CSV mapping profile");
        BankCsvMappingProfileDefinition definition = BankCsvMappingProfileDefinition.parse(profileJson);
        try (EntityManager em = jpa.em())
        {
            var transaction = em.getTransaction();
            transaction.begin();
            try
            {
                Company company = company(em, companyCode);
                CompanyBankAccount bankAccount = bankAccount(em, company, bankAccountId);
                long count = em.createQuery(
                                "select count(p) from BankCsvMappingProfile p where p.company = :company",
                                Long.class)
                        .setParameter("company", company)
                        .getSingleResult();
                if (count >= MAX_PROFILES_PER_COMPANY)
                {
                    throw new IllegalArgumentException("Company already has the maximum 1000 bank CSV mapping profiles.");
                }
                boolean duplicate = em.createQuery("""
                                select count(p) from BankCsvMappingProfile p
                                where p.company = :company
                                  and p.bankAccount = :account
                                  and lower(p.profileName) = :name
                                  and p.profileVersion = :version
                                """, Long.class)
                        .setParameter("company", company)
                        .setParameter("account", bankAccount)
                        .setParameter("name", definition.profileName().toLowerCase(java.util.Locale.ROOT))
                        .setParameter("version", definition.version())
                        .getSingleResult() > 0;
                if (duplicate)
                {
                    throw new IllegalArgumentException("A bank CSV mapping profile with this name and version already exists.");
                }
                BankCsvMappingProfile profile = new BankCsvMappingProfile();
                profile.setCompany(company);
                profile.setBankAccount(bankAccount);
                apply(profile, definition);
                em.persist(profile);
                em.flush();
                ProfileSummary result = summary(profile);
                transaction.commit();
                return result;
            }
            catch (RuntimeException ex)
            {
                if (transaction.isActive()) transaction.rollback();
                throw ex;
            }
        }
    }

    public ProfileSummary replace(long profileId, String companyCode, String profileJson)
    {
        requireBookkeepingWrite(companyCode, "replace bank CSV mapping profile");
        BankCsvMappingProfileDefinition definition = BankCsvMappingProfileDefinition.parse(profileJson);
        try (EntityManager em = jpa.em())
        {
            var transaction = em.getTransaction();
            transaction.begin();
            try
            {
                Company company = company(em, companyCode);
                BankCsvMappingProfile profile = owned(em, company, profileId);
                boolean duplicate = em.createQuery("""
                                select count(p) from BankCsvMappingProfile p
                                where p.company = :company and p.id <> :id
                                  and p.bankAccount = :account
                                  and lower(p.profileName) = :name
                                  and p.profileVersion = :version
                                """, Long.class)
                        .setParameter("company", company)
                        .setParameter("account", profile.getBankAccount())
                        .setParameter("id", profileId)
                        .setParameter("name", definition.profileName().toLowerCase(java.util.Locale.ROOT))
                        .setParameter("version", definition.version())
                        .getSingleResult() > 0;
                if (duplicate)
                {
                    throw new IllegalArgumentException("A bank CSV mapping profile with this name and version already exists.");
                }
                apply(profile, definition);
                profile.touchUpdatedAt();
                em.flush();
                ProfileSummary result = summary(profile);
                transaction.commit();
                return result;
            }
            catch (RuntimeException ex)
            {
                if (transaction.isActive()) transaction.rollback();
                throw ex;
            }
        }
    }

    public List<ProfileSummary> list(String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            Company company = company(em, companyCode);
            return em.createQuery("""
                            select p from BankCsvMappingProfile p
                            where p.company = :company
                            order by lower(p.profileName), p.profileVersion, p.id
                            """, BankCsvMappingProfile.class)
                    .setParameter("company", company)
                    .getResultList().stream().map(BankCsvMappingProfileService::summary).toList();
        }
    }

    public void setActive(long profileId, String companyCode, boolean active)
    {
        requireBookkeepingWrite(companyCode, "change bank CSV mapping profile active state");
        try (EntityManager em = jpa.em())
        {
            var transaction = em.getTransaction();
            transaction.begin();
            try
            {
                Company company = company(em, companyCode);
                BankCsvMappingProfile profile = owned(em, company, profileId);
                profile.setActive(active);
                profile.touchUpdatedAt();
                transaction.commit();
            }
            catch (RuntimeException ex)
            {
                if (transaction.isActive()) transaction.rollback();
                throw ex;
            }
        }
    }

    private void requireBookkeepingWrite(String companyCode, String operation)
    {
        if (authorizationGuard == null)
        {
            return;
        }
        authorizationGuard.require(ApplicationPermission.BOOKKEEPING_WRITE, companyCode, operation);
    }

    static BankCsvMappingProfile owned(EntityManager em, Company company, long profileId)
    {
        return em.createQuery("""
                        select p from BankCsvMappingProfile p
                        join fetch p.company
                        join fetch p.bankAccount
                        where p.id = :id and p.company = :company
                        """, BankCsvMappingProfile.class)
                .setParameter("id", profileId)
                .setParameter("company", company)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Bank CSV mapping profile does not exist for company: " + profileId + "."));
    }

    static Company company(EntityManager em, String companyCode)
    {
        if (companyCode == null || companyCode.isBlank())
        {
            throw new IllegalArgumentException("Company code is required.");
        }
        return em.createQuery("select c from Company c where c.code = :code", Company.class)
                .setParameter("code", companyCode.trim())
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Company does not exist: " + companyCode + "."));
    }

    private static CompanyBankAccount bankAccount(EntityManager em, Company company, long bankAccountId)
    {
        return em.createQuery("""
                        select a from CompanyBankAccount a
                        join fetch a.company
                        where a.id = :id and a.company = :company
                        """, CompanyBankAccount.class)
                .setParameter("id", bankAccountId)
                .setParameter("company", company)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Configured bank account does not exist for company: " + bankAccountId + "."));
    }

    private static void apply(BankCsvMappingProfile profile, BankCsvMappingProfileDefinition definition)
    {
        profile.setProfileName(definition.profileName());
        profile.setProfileVersion(definition.version());
        profile.setDelimiter(definition.persistedDelimiter());
        profile.setSourceEncoding(definition.encoding());
        profile.setAmountMode(definition.amountMode().name());
        profile.setFixedCurrency(blankToNull(definition.fixedCurrency()));
        profile.setFixedAccountId(blankToNull(definition.fixedAccountId()));
        profile.setMappingJson(definition.canonicalJson());
        profile.setActive(true);
    }

    private static ProfileSummary summary(BankCsvMappingProfile profile)
    {
        return new ProfileSummary(
                profile.getId(), profile.getPortableId(), profile.getBankAccount().getId(), profile.getProfileName(),
                profile.getProfileVersion(), profile.getDelimiter(), profile.getSourceEncoding(),
                profile.getAmountMode(), profile.getFixedCurrency(), profile.getFixedAccountId(),
                profile.isActive());
    }

    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ProfileSummary(
            long id,
            UUID portableId,
            long bankAccountId,
            String profileName,
            String profileVersion,
            String delimiter,
            String sourceEncoding,
            String amountMode,
            String fixedCurrency,
            String fixedAccountId,
            boolean active) { }
}