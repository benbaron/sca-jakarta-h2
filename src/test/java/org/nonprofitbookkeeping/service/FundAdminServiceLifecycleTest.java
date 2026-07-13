package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundAlias;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FundAdminServiceLifecycleTest
{
    @Test
    void stableIdUpdatePreservesIdentityAndPersistsLifecycleFields(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fund-stable-edit")))
        {
            FundAdminService service = new FundAdminService(jpa);
            Fund parent = service.save(command(null, "GENERAL", "General", FundType.UNRESTRICTED, true,
                    null, null, null, null));
            Fund created = service.save(command(null, "EVENT", "Event", FundType.EVENT, true,
                    parent.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "Annual event"));

            Fund updated = service.save(command(created.getId(), "EVENT-2026", "2026 Event", FundType.DESIGNATED, false,
                    parent.getId(), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 11, 30), "Restricted to event costs"));

            assertEquals(created.getId(), updated.getId());
            try (EntityManager em = jpa.em())
            {
                assertEquals(2L, em.createQuery("select count(f) from Fund f", Long.class).getSingleResult());
                Fund stored = em.createQuery(
                                "from Fund f left join fetch f.parent where f.id = :id", Fund.class)
                        .setParameter("id", created.getId())
                        .getSingleResult();
                assertEquals("EVENT-2026", stored.getCode());
                assertEquals("2026 Event", stored.getName());
                assertEquals(FundType.DESIGNATED, stored.getFundType());
                assertFalse(stored.isActive());
                assertEquals(parent.getId(), stored.getParent().getId());
                assertEquals(LocalDate.of(2026, 2, 1), stored.getEffectiveFrom());
                assertEquals(LocalDate.of(2026, 11, 30), stored.getEffectiveTo());
                assertEquals("Restricted to event costs", stored.getRestrictionText());
            }
        }
    }

    @Test
    void validationRejectsDuplicateCodesInvalidDatesAndParentCycles(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fund-validation")))
        {
            FundAdminService service = new FundAdminService(jpa);
            Fund root = service.save(command(null, "ROOT", "Root", FundType.UNRESTRICTED, true,
                    null, null, null, null));
            Fund child = service.save(command(null, "CHILD", "Child", FundType.DESIGNATED, true,
                    root.getId(), null, null, null));

            assertThrows(IllegalArgumentException.class, () -> service.save(command(
                    null, "root", "Duplicate", FundType.OTHER, true, null, null, null, null)));
            assertThrows(IllegalArgumentException.class, () -> service.save(command(
                    child.getId(), "CHILD", "Child", FundType.DESIGNATED, true, child.getId(), null, null, null)));
            assertThrows(IllegalArgumentException.class, () -> service.save(command(
                    root.getId(), "ROOT", "Root", FundType.UNRESTRICTED, true, child.getId(), null, null, null)));
            assertThrows(IllegalArgumentException.class, () -> service.save(command(
                    child.getId(), "CHILD", "Child", FundType.DESIGNATED, true, root.getId(),
                    LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1), null)));
        }
    }

    @Test
    void deleteUnusedRemovesOnlyUnreferencedFundsAndDeactivationPreservesHistory(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fund-delete")))
        {
            FundAdminService service = new FundAdminService(jpa);
            Fund unused = service.save(command(null, "UNUSED", "Unused", FundType.OTHER, true,
                    null, null, null, null));
            Fund used = service.save(command(null, "USED", "Used", FundType.UNRESTRICTED, true,
                    null, null, null, null));

            FundUsage unusedUsage = service.usage(unused.getId());
            assertTrue(unusedUsage.canDelete());
            assertEquals(0L, unusedUsage.totalReferences());
            service.deleteUnused(unused.getId());

            try (EntityManager em = jpa.em())
            {
                assertNull(em.find(Fund.class, unused.getId()));
                em.getTransaction().begin();
                FundAlias alias = new FundAlias();
                alias.setFund(em.find(Fund.class, used.getId()));
                alias.setAliasText("Operating");
                alias.setSource("test");
                em.persist(alias);
                em.getTransaction().commit();
            }

            FundUsage usage = service.usage(used.getId());
            assertEquals(1L, usage.aliases());
            assertFalse(usage.canDelete());
            IllegalStateException blocked = assertThrows(IllegalStateException.class,
                    () -> service.deleteUnused(used.getId()));
            assertTrue(blocked.getMessage().contains("Deactivate it instead"));

            Fund deactivated = service.save(command(used.getId(), "USED", "Used", FundType.UNRESTRICTED, false,
                    null, null, null, null));
            assertNotNull(deactivated);
            assertFalse(deactivated.isActive());
            assertEquals(1L, service.usage(used.getId()).aliases());
        }
    }

    private static FundCommand command(Long id,
                                       String code,
                                       String name,
                                       FundType type,
                                       boolean active,
                                       Long parentId,
                                       LocalDate from,
                                       LocalDate to,
                                       String restriction)
    {
        return new FundCommand(id, code, name, type, active, parentId, from, to, restriction);
    }
}
