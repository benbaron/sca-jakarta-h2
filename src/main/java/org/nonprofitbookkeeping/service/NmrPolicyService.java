package org.nonprofitbookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.NmrPolicyVersion;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Currency;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Dated company configuration and factual audit; never creates accounting entries. */
public final class NmrPolicyService
{
    private final Jpa jpa;
    private final Supplier<String> companyCode;
    private final AuthorizationGuard guard;

    public NmrPolicyService(Jpa jpa, Supplier<String> companyCode, AuthorizationGuard guard)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCode = Objects.requireNonNull(companyCode, "companyCode");
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    /** Returns retained versions only for the authenticated active company. */
    public List<NmrPolicyView> list()
    {
        String code = currentCompany();
        var session = guard.currentSession().orElseThrow(() -> new AuthorizationException(null, "Log in to read NMR policy configuration."));
        if (!session.companyCode().equalsIgnoreCase(code))
        {
            throw new AuthorizationException(null, "NMR policy configuration belongs to the authenticated company.");
        }
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, code);
            return em.createQuery("from NmrPolicyVersion p where p.company = :company "
                            + "order by p.policyCode, p.category, p.validFrom desc, p.id", NmrPolicyVersion.class)
                    .setParameter("company", company).getResultList().stream().map(NmrPolicyService::view).toList();
        }
    }

    /** Saves one retained version; retries reuse their exact original request identity. */
    public NmrPolicyView save(UUID requestId, NmrPolicyTerms input)
    {
        String code = currentCompany();
        String actor = guard.requireActor(ApplicationPermission.COMPANY_ADMIN, code, "save NMR policy version");
        Objects.requireNonNull(requestId, "Policy request identity is required");
        NmrPolicyTerms terms = normalize(input);
        String hash = fingerprint(terms);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = lockedCompany(em, code);
                var prior = em.createQuery("from NmrPolicyVersion p where p.portableId = :request", NmrPolicyVersion.class)
                        .setParameter("request", requestId).getResultList();
                if (!prior.isEmpty())
                {
                    NmrPolicyVersion existing = prior.get(0);
                    requireOwner(company, existing);
                    if (!hash.equals(existing.getRequestHash()))
                    {
                        throw new IllegalArgumentException("This request identity was already saved with different policy terms. Start New Version.");
                    }
                    em.getTransaction().commit();
                    return view(existing);
                }
                long overlaps = em.createQuery("select count(p) from NmrPolicyVersion p where p.company = :company "
                                + "and p.policyCode = :code and p.category = :category "
                                + "and (p.validTo is null or p.validTo >= :from) "
                                + "and (:through is null or p.validFrom <= :through)", Long.class)
                        .setParameter("company", company).setParameter("code", terms.policyCode())
                        .setParameter("category", terms.category()).setParameter("from", terms.validFrom())
                        .setParameter("through", terms.validTo()).getSingleResult();
                if (overlaps != 0)
                {
                    throw new IllegalArgumentException("Effective dates overlap a saved version for this policy code/category. End the earlier version or choose non-overlapping dates.");
                }
                NmrPolicyVersion policy = new NmrPolicyVersion();
                policy.setPortableId(requestId);
                policy.setCompany(company);
                policy.setRequestHash(hash);
                policy.setSavedBy(actor);
                apply(policy, terms);
                em.persist(policy);
                em.flush();
                audit(em, company, actor, policy, "NMR_POLICY_CREATED", null, null);
                em.getTransaction().commit();
                return view(policy);
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    /** Shortens a retained version's inclusive interval without changing its terms. */
    public NmrPolicyView endVersion(long id, LocalDate through, String reason)
    {
        String code = currentCompany();
        String actor = guard.requireActor(ApplicationPermission.COMPANY_ADMIN, code, "end NMR policy version");
        Objects.requireNonNull(through, "Effective through date is required");
        String explanation = text(reason, "End reason", 1000);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = lockedCompany(em, code);
                NmrPolicyVersion policy = em.find(NmrPolicyVersion.class, id, LockModeType.PESSIMISTIC_WRITE);
                if (policy == null)
                {
                    throw new IllegalArgumentException("Unknown NMR policy version.");
                }
                requireOwner(company, policy);
                if (through.isBefore(policy.getValidFrom())
                        || (policy.getValidTo() != null && through.isAfter(policy.getValidTo())))
                {
                    throw new IllegalArgumentException("End date must be on/after the start and may only shorten the saved interval.");
                }
                if (!through.equals(policy.getValidTo()))
                {
                    String before = view(policy).toString();
                    policy.setValidTo(through);
                    audit(em, company, actor, policy, "NMR_POLICY_ENDED", before, explanation);
                }
                em.getTransaction().commit();
                return view(policy);
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    private String currentCompany()
    {
        return text(companyCode.get(), "Active company", 64);
    }

    private Company lockedCompany(EntityManager em, String code)
    {
        Company company = new CompanyOwnershipService(jpa).requireCompany(em, code);
        em.refresh(company, LockModeType.PESSIMISTIC_WRITE);
        if (!company.isActive())
        {
            throw new IllegalStateException("Reactivate the company before configuring NMR policy.");
        }
        return company;
    }

    private static void requireOwner(Company company, NmrPolicyVersion policy)
    {
        if (!company.getId().equals(policy.getCompany().getId()))
        {
            throw new CompanyOwnershipException("NMR policy version belongs to another company.");
        }
    }

    private static NmrPolicyTerms normalize(NmrPolicyTerms t)
    {
        Objects.requireNonNull(t, "Policy terms are required");
        Objects.requireNonNull(t.validFrom(), "Effective from date is required");
        Objects.requireNonNull(t.sourceDate(), "Source date is required");
        if (t.validTo() != null && t.validTo().isBefore(t.validFrom()))
        {
            throw new IllegalArgumentException("Effective through date cannot precede effective from.");
        }
        if (t.rate() == null || t.rate().signum() < 0)
        {
            throw new IllegalArgumentException("Enter an explicit nonnegative rate; blank does not mean zero.");
        }
        BigDecimal rate;
        try
        {
            rate = t.rate().setScale(4, RoundingMode.UNNECESSARY);
            if (rate.precision() > 19)
            {
                throw new ArithmeticException();
            }
        }
        catch (ArithmeticException ex)
        {
            throw new IllegalArgumentException("Rate must fit 19 digits with at most four decimal places.");
        }
        String currency = text(t.currency(), "ISO currency", 3).toUpperCase(Locale.ROOT);
        try
        {
            Currency.getInstance(currency);
        }
        catch (IllegalArgumentException ex)
        {
            throw new IllegalArgumentException("Enter a valid three-letter ISO currency.");
        }
        return new NmrPolicyTerms(text(t.policyCode(), "Policy code", 60).toUpperCase(Locale.ROOT),
                text(t.category(), "Rate category", 80).toUpperCase(Locale.ROOT), t.validFrom(), t.validTo(), rate, currency,
                text(t.dateBasis(), "Rate date basis", 200), text(t.roundingRule(), "Rounding rule", 200),
                text(t.eligibility(), "Eligibility rule", 1000), text(t.exemptions(), "Exemption rule", 1000),
                text(t.recipient(), "Recipient/legal identity", 300), text(t.reporting(), "Legal reporting classification", 500),
                text(t.merchantPath(), "Merchant/payment path", 1000), text(t.sourceReference(), "Source reference", 500),
                text(t.authority(), "Authority", 200), t.sourceDate());
    }

    private static String text(String value, String label, int limit)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required.");
        }
        String result = value.strip();
        if (result.length() > limit)
        {
            throw new IllegalArgumentException(label + " must be at most " + limit + " characters.");
        }
        return result;
    }

    private static String fingerprint(NmrPolicyTerms t)
    {
        try
        {
            byte[] bytes = new ObjectMapper().writeValueAsBytes(List.of(t.policyCode(), t.category(), t.validFrom().toString(),
                    Objects.toString(t.validTo(), ""), t.rate().toPlainString(), t.currency(), t.dateBasis(), t.roundingRule(),
                    t.eligibility(), t.exemptions(), t.recipient(), t.reporting(), t.merchantPath(), t.sourceReference(),
                    t.authority(), t.sourceDate().toString()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (java.io.IOException | java.security.NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("Could not identify the policy request.", ex);
        }
    }

    private static void audit(EntityManager em, Company company, String actor, NmrPolicyVersion p,
            String action, String before, String reason)
    {
        AuditEvent audit = new AuditEvent();
        audit.setCompany(company);
        audit.setActor(actor);
        audit.setActionType(action);
        audit.setEntityType("NMR_POLICY_VERSION");
        audit.setEntityId(p.getId().toString());
        audit.setSummary(action + " " + p.getPolicyCode() + "/" + p.getCategory());
        audit.setBeforeValue(before);
        audit.setAfterValue(view(p).toString());
        audit.setReason(reason);
        em.persist(audit);
    }

    private static void rollback(EntityManager em)
    {
        if (em.getTransaction().isActive())
        {
            em.getTransaction().rollback();
        }
    }

    private static void apply(NmrPolicyVersion p, NmrPolicyTerms t)
    {
        p.setPolicyCode(t.policyCode());
        p.setCategory(t.category());
        p.setValidFrom(t.validFrom());
        p.setValidTo(t.validTo());
        p.setRate(t.rate());
        p.setCurrency(t.currency());
        p.setDateBasis(t.dateBasis());
        p.setRoundingRule(t.roundingRule());
        p.setEligibility(t.eligibility());
        p.setExemptions(t.exemptions());
        p.setRecipient(t.recipient());
        p.setReporting(t.reporting());
        p.setMerchantPath(t.merchantPath());
        p.setSourceReference(t.sourceReference());
        p.setAuthority(t.authority());
        p.setSourceDate(t.sourceDate());
    }

    private static NmrPolicyView view(NmrPolicyVersion p)
    {
        return new NmrPolicyView(p.getId(), p.getPortableId(), p.getCompany().getCode(), p.getSavedAt(), p.getSavedBy(),
                new NmrPolicyTerms(p.getPolicyCode(), p.getCategory(), p.getValidFrom(), p.getValidTo(), p.getRate(), p.getCurrency(),
                        p.getDateBasis(), p.getRoundingRule(), p.getEligibility(), p.getExemptions(), p.getRecipient(), p.getReporting(),
                        p.getMerchantPath(), p.getSourceReference(), p.getAuthority(), p.getSourceDate()));
    }
}
