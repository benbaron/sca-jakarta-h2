package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FundHierarchyLifecycleTest
{
    @Test
    void hierarchyRetiresChildFirstAndReactivatesParentFirst(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fund-hierarchy-order")))
        {
            FundAdminService service = new FundAdminService(jpa);
            Fund parent = service.save(command(null, "PARENT", true, null));
            Fund child = service.save(command(null, "CHILD", true, parent.getId()));

            IllegalStateException parentFirst = assertThrows(IllegalStateException.class,
                    () -> service.save(command(parent.getId(), "PARENT", false, null)));
            assertEquals("Deactivate or reparent active child funds before deactivating fund PARENT.",
                    parentFirst.getMessage());
            assertTrue(find(jpa, parent.getId()).isActive());
            assertTrue(find(jpa, child.getId()).isActive());

            Fund inactiveChild = service.save(command(child.getId(), "CHILD", false, parent.getId()));
            Fund inactiveParent = service.save(command(parent.getId(), "PARENT", false, null));
            assertEquals(child.getId(), inactiveChild.getId());
            assertEquals(parent.getId(), inactiveParent.getId());
            assertFalse(find(jpa, child.getId()).isActive());
            assertFalse(find(jpa, parent.getId()).isActive());

            IllegalStateException childFirst = assertThrows(IllegalStateException.class,
                    () -> service.save(command(child.getId(), "CHILD", true, parent.getId())));
            assertEquals("Active fund requires an active parent hierarchy. Reactivate parent fund PARENT first.",
                    childFirst.getMessage());
            assertFalse(find(jpa, child.getId()).isActive());

            service.save(command(parent.getId(), "PARENT", true, null));
            service.save(command(child.getId(), "CHILD", true, parent.getId()));
            assertTrue(find(jpa, parent.getId()).isActive());
            assertTrue(find(jpa, child.getId()).isActive());
        }
    }

    @Test
    void activeCreationAndReparentingRejectInactiveParentWhileInactiveHistoryIsAllowed(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fund-hierarchy-parent-state")))
        {
            FundAdminService service = new FundAdminService(jpa);
            Fund inactiveParent = service.save(command(null, "OLD", false, null));
            Fund inactiveChild = service.save(command(null, "OLDCHILD", false, inactiveParent.getId()));
            assertFalse(inactiveChild.isActive());

            IllegalStateException activate = assertThrows(IllegalStateException.class,
                    () -> service.save(command(inactiveChild.getId(), "OLDCHILD", true, inactiveParent.getId())));
            assertTrue(activate.getMessage().contains("Reactivate parent fund OLD first"));

            IllegalStateException createActive = assertThrows(IllegalStateException.class,
                    () -> service.save(command(null, "BADCHILD", true, inactiveParent.getId())));
            assertTrue(createActive.getMessage().contains("Reactivate parent fund OLD first"));

            Fund active = service.save(command(null, "ACTIVE", true, null));
            IllegalStateException reparent = assertThrows(IllegalStateException.class,
                    () -> service.save(command(active.getId(), "ACTIVE", true, inactiveParent.getId())));
            assertTrue(reparent.getMessage().contains("Reactivate parent fund OLD first"));
            assertNull(find(jpa, active.getId()).getParent());
        }
    }

    @Test
    void activeLookupFailsClosedForLegacyInvalidHierarchyWhileMaintenanceStillShowsIt(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fund-hierarchy-legacy")))
        {
            FundAdminService service = new FundAdminService(jpa);
            Fund parent = service.save(command(null, "LEGACYPARENT", true, null));
            Fund child = service.save(command(null, "LEGACYCHILD", true, parent.getId()));

            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.find(Fund.class, parent.getId()).setActive(false);
                em.getTransaction().commit();
            }

            FundLookupService lookup = new FundLookupService(jpa);
            assertFalse(lookup.listActiveFunds().stream().anyMatch(fund -> fund.getId().equals(child.getId())));
            assertTrue(lookup.listAllFunds().stream().anyMatch(fund -> fund.getId().equals(child.getId())));
        }
    }

    private static FundCommand command(Long id, String code, boolean active, Long parentId)
    {
        return new FundCommand(id, code, code + " Fund", FundType.UNRESTRICTED,
                active, parentId, null, null, null);
    }

    private static Fund find(Jpa jpa, long id)
    {
        try (var em = jpa.em())
        {
            return em.createQuery(
                            "select f from Fund f left join fetch f.parent where f.id = :id", Fund.class)
                    .setParameter("id", id)
                    .getSingleResult();
        }
    }
}
