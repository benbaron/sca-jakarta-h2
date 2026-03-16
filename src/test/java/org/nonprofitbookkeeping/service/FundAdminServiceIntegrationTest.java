package org.nonprofitbookkeeping.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * FundAdminServiceIntegrationTest component.
 */
public class FundAdminServiceIntegrationTest
{
    @Test
    public void upsert_createsAndUpdatesFund() throws Exception
    {
        Path db = Files.createTempFile("fund-admin-it", ".mv.db");
        runMigrations(db);

        Jpa jpa = new Jpa(db);
        try
        {
            FundAdminService service = new FundAdminService(jpa);
            Fund created = service.upsert("GEN", "General Fund", FundType.UNRESTRICTED, true);
            assertNotNull(created.getId());
            assertEquals("GEN", created.getCode());

            Fund updated = service.upsert("GEN", "General Operating Fund", FundType.DESIGNATED, false);
            assertEquals(created.getId(), updated.getId());
            assertEquals("General Operating Fund", updated.getName());
            assertEquals(FundType.DESIGNATED, updated.getFundType());
            assertEquals(false, updated.isActive());
        }
        finally
        {
            jpa.close();
        }
    }

    private static void runMigrations(Path databaseFile)
    {
        String raw = databaseFile.toString();
        String normalized = raw.endsWith(".mv.db") ? raw.substring(0, raw.length() - 6) : raw;
        String jdbc = "jdbc:h2:file:" + normalized + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        Flyway.configure().dataSource(jdbc, "sa", "").load().migrate();
    }
}
