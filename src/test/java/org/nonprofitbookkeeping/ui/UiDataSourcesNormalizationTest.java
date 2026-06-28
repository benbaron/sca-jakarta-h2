package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that UI datasource URLs normalize database filenames consistently.
 */
public class UiDataSourcesNormalizationTest
{
    private static final String OPTIONS =
            ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;"
                    + "INIT=CREATE SCHEMA IF NOT EXISTS PUBLIC\\;SET SCHEMA PUBLIC";

    @Test
    public void jdbcUrlForTests_stripsMvDbSuffix()
    {
        Path databaseFile = Path.of("data/company-a.mv.db");

        String url = UiDataSources.jdbcUrlForTests(databaseFile);

        assertEquals(expectedUrl(databaseFile, ".mv.db"), url);
    }

    @Test
    public void jdbcUrlForTests_stripsDbSuffix()
    {
        Path databaseFile = Path.of("data/company-b.db");

        String url = UiDataSources.jdbcUrlForTests(databaseFile);

        assertEquals(expectedUrl(databaseFile, ".db"), url);
    }

    private static String expectedUrl(Path databaseFile, String suffix)
    {
        String absolutePath = databaseFile.toAbsolutePath().normalize().toString();
        String basePath = absolutePath.substring(0, absolutePath.length() - suffix.length());
        return "jdbc:h2:file:" + basePath + OPTIONS;
    }
}
