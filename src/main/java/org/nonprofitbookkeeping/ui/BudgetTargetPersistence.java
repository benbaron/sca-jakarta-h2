package org.nonprofitbookkeeping.ui;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;

/**
 * BudgetTargetPersistence component.
 */
final class BudgetTargetPersistence
{
    private static final String STORAGE_DIR = ".sca-jakarta-h2";
    private static final String STORAGE_FILE = "budget-targets.properties";

    private BudgetTargetPersistence()
    {
    }

    static Map<String, BigDecimal> load()
    {
        Path path = defaultPath();
        if (!Files.exists(path))
        {
            return Map.of();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            return readFrom(reader);
        }
        catch (IOException ex)
        {
            return Map.of();
        }
    }

    static void save(Map<String, BigDecimal> values)
    {
        Path path = defaultPath();
        try
        {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8))
            {
                writeTo(values, writer);
            }
        }
        catch (IOException ignored)
        {
            // Keep UI deterministic even when persistence is unavailable.
        }
    }

    static Map<String, BigDecimal> readFrom(Reader reader) throws IOException
    {
        Properties props = new Properties();
        props.load(reader);
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (String key : new TreeSet<>(props.stringPropertyNames()))
        {
            String value = props.getProperty(key);
            if (key == null || key.isBlank() || value == null || value.isBlank())
            {
                continue;
            }
            try
            {
                out.put(key.trim(), new BigDecimal(value.trim()));
            }
            catch (RuntimeException ignored)
            {
                // ignore malformed rows
            }
        }
        return out;
    }

    static void writeTo(Map<String, BigDecimal> values, Writer writer) throws IOException
    {
        Properties props = new Properties();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> props.setProperty(e.getKey(), e.getValue().toPlainString()));
        props.store(writer, "Budget targets by fund code");
    }

    private static Path defaultPath()
    {
        return Paths.get(System.getProperty("user.home"), STORAGE_DIR, STORAGE_FILE);
    }
}
