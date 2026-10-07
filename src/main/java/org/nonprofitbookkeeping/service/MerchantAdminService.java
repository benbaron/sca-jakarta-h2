package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Merchant;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

@ApplicationScoped
public class MerchantAdminService
{
    @Inject
    Jpa jpa;

    private Supplier<String> companyCodeSupplier = () -> "DEFAULT";
    private AuthorizationGuard authorizationGuard;

    public MerchantAdminService()
    {
    }

    public MerchantAdminService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT");
    }

    public MerchantAdminService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this(jpa, companyCodeSupplier, null);
    }

    public MerchantAdminService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            AuthorizationGuard authorizationGuard)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.authorizationGuard = authorizationGuard;
    }

    /** Saves the exact selected merchant and its audit atomically; merchants are retained, not deleted. */
    public MerchantView save(MerchantCommand command)
    {
        Objects.requireNonNull(command, "Merchant details are required");
        String companyCode = companyCodeSupplier.get();
        String actor = ServiceAuthorization.actor(authorizationGuard, ApplicationPermission.BOOKKEEPING_WRITE,
                companyCode, "save merchant", "SYSTEM");
        String name = bounded(command.name(), "Merchant name", 200);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, companyCode);
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);
                Merchant merchant;
                if (command.id() == null)
                {
                    merchant = new Merchant();
                    merchant.setCompany(company);
                }
                else
                {
                    merchant = em.find(Merchant.class, command.id(), LockModeType.PESSIMISTIC_WRITE);
                    if (merchant == null)
                    {
                        throw new IllegalArgumentException("Unknown Merchant ID: " + command.id());
                    }
                    ownership.requireOwnedBy(company, merchant, "Merchant");
                }
                var duplicates = em.createQuery("select b.id from Merchant b where b.company = :company "
                                + "and upper(trim(b.name)) = :name", Long.class)
                        .setParameter("company", company).setParameter("name", name.toUpperCase(Locale.ROOT))
                        .getResultList();
                if (duplicates.stream().anyMatch(id -> !id.equals(command.id())))
                {
                    throw new IllegalArgumentException("Merchant name already exists: " + name);
                }
                String before = merchant.getId() == null ? null : describe(merchant);
                merchant.setName(name);
                merchant.setActive(command.active());
                merchant.setNotes(command.notes() == null || command.notes().isBlank() ? null : command.notes().trim());
                if (merchant.getId() == null)
                {
                    em.persist(merchant);
                }
                em.flush();
                AuditEvent audit = new AuditEvent();
                audit.setCompany(company);
                audit.setActor(actor);
                audit.setActionType(before == null ? "MERCHANT_CREATED" : "MERCHANT_UPDATED");
                audit.setEntityType("MERCHANT");
                audit.setEntityId(merchant.getId().toString());
                audit.setSummary((before == null ? "Created " : "Updated ") + "Merchant " + name);
                audit.setBeforeValue(before);
                audit.setAfterValue(describe(merchant));
                em.persist(audit);
                em.getTransaction().commit();
                return view(merchant);
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

    /** Includes inactive merchants for historical maintenance, restricted to the selected company. */
    public List<MerchantView> listForMaintenance()
    {
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCodeSupplier.get());
            return em.createQuery("from Merchant c where c.company = :company order by c.name, c.id",
                            Merchant.class).setParameter("company", company).getResultList().stream()
                    .map(MerchantAdminService::view).toList();
        }
    }

    private static MerchantView view(Merchant merchant)
    {
        return new MerchantView(merchant.getId(), merchant.getName(), merchant.getNotes(), merchant.isActive());
    }

    private static String describe(Merchant merchant)
    {
        return "name=" + merchant.getName() + "; active=" + merchant.isActive()
                + "; notes=" + Objects.toString(merchant.getNotes(), "");
    }

    private static String bounded(String value, String label, int limit)
    {
        String text = requireText(value, label);
        if (text.length() > limit)
        {
            throw new IllegalArgumentException(label + " must be at most " + limit + " characters.");
        }
        return text;
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }
}
