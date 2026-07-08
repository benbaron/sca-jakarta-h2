package org.nonprofitbookkeeping.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationVersionUniquenessTest
{
    private static final Pattern VERSIONED_SQL = Pattern.compile("^V(\\d+)__.*\\.sql$");

    @Test
    void migrationVersionsAreUnique() throws IOException
    {
        Path migrationDir = Path.of("src/main/resources/db/migration");
        Map<Integer, List<String>> byVersion;
        try (var paths = Files.list(migrationDir))
        {
            byVersion = paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .map(MigrationFile::from)
                    .filter(MigrationFile::versioned)
                    .collect(Collectors.groupingBy(
                            MigrationFile::version,
                            TreeMap::new,
                            Collectors.mapping(MigrationFile::fileName, Collectors.toList())));
        }

        List<String> duplicates = byVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> "V" + entry.getKey() + " -> " + entry.getValue())
                .toList();

        assertTrue(duplicates.isEmpty(), "Duplicate Flyway migration versions: " + duplicates);
    }

    private record MigrationFile(String fileName, Integer version)
    {
        static MigrationFile from(String fileName)
        {
            Matcher matcher = VERSIONED_SQL.matcher(fileName);
            if (!matcher.matches())
            {
                return new MigrationFile(fileName, null);
            }
            return new MigrationFile(fileName, Integer.valueOf(matcher.group(1)));
        }

        boolean versioned()
        {
            return version != null;
        }
    }
}
