package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.persistence.DatabaseMigrationService;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Full persisted-row oracle shared by bounded and integrated recovery tests. */
public final class DatabaseSnapshotTestSupport
{
    private DatabaseSnapshotTestSupport() { }
    /** Complete sorted row values, not just entity counts or selected happy-path columns. */
    public static Map<String, List<String>> rows(Path database) throws Exception
    {
        Map<String, List<String>> result = new TreeMap<>();
        try (var connection = DriverManager.getConnection(DatabaseMigrationService.jdbcUrlFor(database), "sa", ""))
        {
            List<String> tables = new ArrayList<>();
            try (var rs = connection.getMetaData().getTables(null, "public", null, new String[]{"BASE TABLE"}))
            {
                while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
            }
            for (String table : tables)
            {
                List<String> values = new ArrayList<>();
                try (var statement = connection.createStatement(); var rs = statement.executeQuery("select * from \"" + table.replace("\"", "\"\"") + "\""))
                {
                    while (rs.next())
                    {
                        StringBuilder row = new StringBuilder();
                        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
                        {
                            String value = rs.getString(i);
                            row.append(value == null ? "N;" : value.length() + ":" + value + ";");
                        }
                        values.add(row.toString());
                    }
                }
                Collections.sort(values); result.put(table, values);
            }
            try (var statement = connection.createStatement(); var rs = statement.executeQuery("select trigger_name, event_manipulation, event_object_table, action_orientation, action_timing from information_schema.triggers"))
            {
                List<String> triggers = new ArrayList<>();
                while (rs.next()) triggers.add(rs.getString(1) + ":" + rs.getString(2) + ":" + rs.getString(3) + ":" + rs.getString(4) + ":" + rs.getString(5));
                Collections.sort(triggers); result.put("<trigger inventory>", triggers);
            }
        }
        assertTrue(result.size() > 50, "Do not silently compare an empty/wrong-schema snapshot");
        return result;
    }
}
