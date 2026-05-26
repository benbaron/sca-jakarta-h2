package org.nonprofitbookkeeping.report.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Values supplied to compact semantic report templates. */
public class SemanticReportValueSet
{
    private final Map<String, Object> scalars = new LinkedHashMap<>();
    private final Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();

    public void put(String key, Object value)
    {
        scalars.put(key, value);
    }

    public Object get(String key)
    {
        return scalars.get(key);
    }

    public Map<String, Object> scalars()
    {
        return Collections.unmodifiableMap(scalars);
    }

    public void putTable(String key, List<Map<String, Object>> rows)
    {
        tables.put(key, new ArrayList<>(rows));
    }

    public List<Map<String, Object>> table(String key)
    {
        return tables.getOrDefault(key, List.of());
    }

    public Map<String, List<Map<String, Object>>> tables()
    {
        return Collections.unmodifiableMap(tables);
    }
}
