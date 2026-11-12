import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.nonprofitbookkeeping.persistence.DatabaseMigrationService;
class Generate252Manifest
{
    static String digest(byte[] bytes) throws Exception
    {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    public static void main(String[] args) throws Exception
    {
        var manifest = new TreeMap<String, Object>();
        var tables = new TreeMap<String, Object>();
        try (var connection = DriverManager.getConnection(DatabaseMigrationService.jdbcUrlFor(Path.of(args[0])), "sa", ""))
        {
            try (var statement = connection.createStatement(); var rs = statement.executeQuery("select h2version()"))
            {
                rs.next(); String version = rs.getString(1);
                if (!version.equals("2.5.252")) throw new IllegalStateException("Generate with H2 2.5.252: " + version);
                manifest.put("sourceH2", version);
            }
            var names = new TreeSet<String>();
            try (var rs = connection.getMetaData().getTables(null, "public", null, new String[]{"BASE TABLE"}))
            {
                while (rs.next()) names.add(rs.getString("TABLE_NAME"));
            }
            for (String table : names)
            {
                var columns = new ArrayList<String>(); var rows = new ArrayList<String>();
                try (var statement = connection.createStatement(); var rs = statement.executeQuery("select * from \"" + table + "\""))
                {
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) columns.add(rs.getMetaData().getColumnName(i));
                    while (rs.next())
                    {
                        var row = new StringBuilder();
                        for (int i = 1; i <= columns.size(); i++)
                        {
                            String value = rs.getString(i); row.append(value == null ? "N;" : value.length() + ":" + value + ";");
                        }
                        rows.add(row.toString());
                    }
                }
                Collections.sort(rows);
                tables.put(table, Map.of("columns", columns, "rows", rows.size(), "sha256", digest(String.join("\n", rows).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            }
            var triggers = new ArrayList<String>();
            try (var statement = connection.createStatement(); var rs = statement.executeQuery("select trigger_name,event_manipulation,event_object_table,action_orientation,action_timing from information_schema.triggers"))
            {
                while (rs.next()) triggers.add(rs.getString(1) + ":" + rs.getString(2) + ":" + rs.getString(3) + ":" + rs.getString(4) + ":" + rs.getString(5));
            }
            Collections.sort(triggers); manifest.put("triggers", triggers);
        }
        manifest.put("sourceFlyway", "13.12.0"); manifest.put("schemaVersion", 92);
        manifest.put("seedCommit", args.length > 3 ? args[3] : "ec4d358f4568dbf6ee689fe3356f0ad0a7817400");
        manifest.put("backupKind", "Offline native backup after all source connections closed");
        manifest.put("backupSha256", digest(Files.readAllBytes(Path.of(args[1])))); manifest.put("tables", tables);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of(args[2]).toFile(), manifest);
        System.out.println("Captured previous-runtime fields for " + tables.size() + " public tables; backup " + manifest.get("backupSha256"));
    }
}
