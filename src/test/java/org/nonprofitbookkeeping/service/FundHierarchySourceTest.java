package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FundHierarchySourceTest
{
    @Test
    void fundAndSclxWritesShareCompanyLockAndUiExplainsOrdering() throws Exception
    {
        String service = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/service/FundAdminService.java"));
        String sclx = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxImportCommitService.java"));
        String panel = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/FundsPanel.java"));

        assertTrue(service.contains("em.lock(company, LockModeType.PESSIMISTIC_WRITE)"));
        assertTrue(service.contains("Deactivate or reparent active child funds before deactivating fund"));
        assertTrue(service.contains("Reactivate parent fund"));
        assertTrue(sclx.contains("em.lock(company, LockModeType.PESSIMISTIC_WRITE)"));
        assertTrue(sclx.contains("requireActiveFundHierarchy(parent, active, externalId)"));
        assertTrue(panel.contains("deactivate or reparent active children before a parent"));
        assertTrue(panel.contains("reactivate parents before children"));
    }
}
