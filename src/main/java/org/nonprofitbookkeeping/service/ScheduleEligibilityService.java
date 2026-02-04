package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes which schedule kinds are applicable for a given account.
 *
 * Rules:
 * 1) Subtype defaults: account.subtype -> schedule kinds (via account_subtype_schedule_default)
 * 2) Per-account overrides: account_schedule_requirement adds schedule kinds (required or optional)
 *
 * The UI uses this to enable/disable schedule tabs (Excel-like behavior).
 */
@ApplicationScoped
public class ScheduleEligibilityService
{
    @Inject
    Jpa jpa;

    public Set<String> allowedScheduleKindCodes(Account account)
    {
        if (account == null) return Set.of();

        Set<String> out = new LinkedHashSet<>();

        // 1) subtype defaults
        if (account.getSubtype() != null)
        {
            out.addAll(defaultScheduleCodesForSubtype(account.getSubtype()));
        }

        // 2) account-specific requirements (adds; does not remove)
        out.addAll(accountRequirementScheduleCodes(account.getId()));

        return out;
    }

    public Set<String> defaultScheduleCodesForSubtype(AccountSubtype subtype)
    {
        if (subtype == null) return Set.of();

        // If DB is present and seeded, use it (preferred).
        try (EntityManager em = jpa.em())
        {
            List<String> codes = em.createQuery(
                    "select k.code from AccountSubtypeScheduleDefault d join d.scheduleKind k where d.subtype = :s",
                    String.class)
                .setParameter("s", subtype.name())
                .getResultList();

            if (!codes.isEmpty())
            {
                return new LinkedHashSet<>(codes);
            }
        }
        catch (RuntimeException ignore)
        {
            // fall back below (useful during early dev)
        }

        // Fallback mapping (keeps UI usable even before seeding)
        return switch (subtype)
        {
            case RECEIVABLE -> Set.of("RECEIVABLE");
            case PAYABLE -> Set.of("PAYABLE");
            case PREPAID -> Set.of("PREPAID");
            case DEFERRED_REVENUE -> Set.of("DEFERRED_REVENUE");
            case INVENTORY -> Set.of("INVENTORY");
            case FIXED_ASSET -> Set.of("FIXED_ASSET");
            case OTHER_ASSET -> Set.of("OTHER_ASSET");
            case OTHER_LIABILITY -> Set.of("OTHER_LIABILITY");
            case CASH -> Set.of();
        };
    }

    private Set<String> accountRequirementScheduleCodes(Long accountId)
    {
        if (accountId == null) return Set.of();

        try (EntityManager em = jpa.em())
        {
            List<String> codes = em.createQuery(
                    "select k.code from AccountScheduleRequirement r join r.scheduleKind k where r.account.id = :aid",
                    String.class)
                .setParameter("aid", accountId)
                .getResultList();

            return new LinkedHashSet<>(codes);
        }
        catch (RuntimeException ex)
        {
            return Set.of();
        }
    }
}
