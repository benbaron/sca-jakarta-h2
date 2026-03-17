package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UiDataSourcesNormalizationTest component.
 */
public class UiDataSourcesNormalizationTest
{
    @Test
    public void jdbcUrlForTests_stripsMvDbSuffix()
    {
        String url = UiDataSources.jdbcUrlForTests(Path.of("data/company-a.mv.db"));
        assertEquals("jdbc:h2:file:data/company-a;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=CREATE SCHEMA IF NOT EXISTS PUBLIC\\;SET SCHEMA PUBLIC", url);
    }

    @Test
    public void jdbcUrlForTests_stripsDbSuffix()
    {
        String url = UiDataSources.jdbcUrlForTests(Path.of("data/company-b.db"));
        assertEquals("jdbc:h2:file:data/company-b;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=CREATE SCHEMA IF NOT EXISTS PUBLIC\\;SET SCHEMA PUBLIC", url);
    }
}
