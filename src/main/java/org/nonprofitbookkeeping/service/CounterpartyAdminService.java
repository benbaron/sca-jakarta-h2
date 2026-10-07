package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.AuditEvent;
import java.util.Locale;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@ApplicationScoped
public class CounterpartyAdminService
{
    @Inject
    Jpa jpa;

    private Supplier<String> companyCodeSupplier = () -> "DEFAULT";
    private AuthorizationGuard authorizationGuard;

    public CounterpartyAdminService()
    {
    }

    public CounterpartyAdminService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT");
    }

    public CounterpartyAdminService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this(jpa, companyCodeSupplier, null);
    }

    public CounterpartyAdminService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            AuthorizationGuard authorizationGuard)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.authorizationGuard = authorizationGuard;
    }

    /** Saves the exact selected party and its audit atomically; parties are retained, not deleted. */
    public CounterpartyView save(CounterpartyCommand command)
    {
        Objects.requireNonNull(command, "Counterparty details are required");
        String companyCode = companyCodeSupplier.get();
        String actor = ServiceAuthorization.actor(authorizationGuard, ApplicationPermission.BOOKKEEPING_WRITE,
                companyCode, "save counterparty", "SYSTEM");
        String name = bounded(command.name(), "Counterparty name", 200);
        if (command.kind() == null)
        {
            throw new IllegalArgumentException("Counterparty kind is required.");
        }
        String email = optional(command.email(), "Email", 200);
        String phone = optional(command.phone(), "Phone", 40);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, companyCode);
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);
                Counterparty party;
                if (command.id() == null)
                {
                    party = new Counterparty();
                    party.setCompany(company);
                }
                else
                {
                    party = em.find(Counterparty.class, command.id(), LockModeType.PESSIMISTIC_WRITE);
                    if (party == null)
                    {
                        throw new IllegalArgumentException("Unknown Counterparty ID: " + command.id());
                    }
                    ownership.requireOwnedBy(company, party, "Counterparty");
                }
                var duplicates = em.createQuery("select b.id from Counterparty b where b.company = :company "
                                + "and upper(trim(b.displayName)) = :name", Long.class)
                        .setParameter("company", company).setParameter("name", name.toUpperCase(Locale.ROOT))
                        .getResultList();
                if (duplicates.stream().anyMatch(id -> !id.equals(command.id())))
                {
                    throw new IllegalArgumentException("Counterparty name already exists: " + name);
                }
                String before = party.getId() == null ? null : describe(party);
                party.setDisplayName(name);
                party.setActive(command.active());
                party.setKind(command.kind());
                party.setEmail(email);
                party.setPhone(phone);
                party.setNotes(command.notes() == null || command.notes().isBlank() ? null : command.notes().trim());
                if (party.getId() == null)
                {
                    em.persist(party);
                }
                em.flush();
                AuditEvent audit = new AuditEvent();
                audit.setCompany(company);
                audit.setActor(actor);
                audit.setActionType(before == null ? "COUNTERPARTY_CREATED" : "COUNTERPARTY_UPDATED");
                audit.setEntityType("COUNTERPARTY");
                audit.setEntityId(party.getId().toString());
                audit.setSummary((before == null ? "Created " : "Updated ") + "Counterparty " + name);
                audit.setBeforeValue(before);
                audit.setAfterValue(describe(party));
                em.persist(audit);
                em.getTransaction().commit();
                return view(party);
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

    /** Includes inactive parties for historical maintenance, restricted to the selected company. */
    public List<CounterpartyView> listForMaintenance()
    {
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCodeSupplier.get());
            return em.createQuery("from Counterparty c where c.company = :company order by c.displayName, c.id",
                            Counterparty.class).setParameter("company", company).getResultList().stream()
                    .map(CounterpartyAdminService::view).toList();
        }
    }

    private static CounterpartyView view(Counterparty party)
    {
        return new CounterpartyView(party.getId(), party.getDisplayName(), party.getKind(), party.getEmail(),
                party.getPhone(), party.getNotes(), party.isActive());
    }

    private static String describe(Counterparty party)
    {
        return "name=" + party.getDisplayName() + "; kind=" + party.getKind() + "; active=" + party.isActive()
                + "; email=" + Objects.toString(party.getEmail(), "") + "; phone=" + Objects.toString(party.getPhone(), "")
                + "; notes=" + Objects.toString(party.getNotes(), "");
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

    private static String optional(String value, String label, int limit)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        return bounded(value, label, limit);
    }
}
