package org.nonprofitbookkeeping.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic CSV parser/mapper for chart-of-accounts account rows.
 */
public class CoaCsvMapper
{
    public List<CoaCsvRow> parse(String csv)
    {
        if (csv == null || csv.isBlank())
        {
            return List.of();
        }

        String[] lines = csv.split("\\R");
        if (lines.length == 0)
        {
            return List.of();
        }

        List<String> headers = parseCsvLine(lines[0]);
        requireHeaders(headers, List.of("code", "name", "account_type", "normal_balance"));

        List<CoaCsvRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++)
        {
            String line = lines[i];
            if (line.isBlank())
            {
                continue;
            }
            List<String> values = parseCsvLine(line);
            Map<String, String> row = map(headers, values);
            rows.add(new CoaCsvRow(
                    required(row, "code"),
                    required(row, "name"),
                    required(row, "account_type"),
                    required(row, "normal_balance"),
                    optional(row, "parent_code")));
        }
        return rows;
    }


    public String write(List<CoaCsvRow> rows)
    {
        List<CoaCsvRow> safeRows = rows == null ? List.of() : rows;
        StringBuilder out = new StringBuilder();
        out.append("code,name,account_type,normal_balance,parent_code\n");
        for (CoaCsvRow row : safeRows)
        {
            out.append(csv(row.code())).append(',')
                    .append(csv(row.name())).append(',')
                    .append(csv(row.accountType())).append(',')
                    .append(csv(row.normalBalance())).append(',')
                    .append(csv(row.parentCode())).append('\n');
        }
        return out.toString();
    }

    private static String csv(String raw)
    {
        String value = raw == null ? "" : raw;
        boolean quote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return quote ? ("\"" + escaped + "\"") : escaped;
    }

    private void requireHeaders(List<String> headers, List<String> required)
    {
        for (String key : required)
        {
            if (!headers.contains(key))
            {
                throw new IllegalArgumentException("Missing required CSV header: " + key);
            }
        }
    }

    private static Map<String, String> map(List<String> headers, List<String> values)
    {
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i < headers.size() && i < values.size(); i++)
        {
            out.put(headers.get(i), values.get(i));
        }
        return out;
    }

    private static String required(Map<String, String> row, String key)
    {
        String value = row.get(key);
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException("Missing required value for column: " + key);
        }
        return value.trim();
    }

    private static String optional(Map<String, String> row, String key)
    {
        String value = row.get(key);
        return value == null ? "" : value.trim();
    }

    private static List<String> parseCsvLine(String line)
    {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++)
        {
            char ch = line.charAt(i);
            if (inQuotes)
            {
                if (ch == '"')
                {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"')
                    {
                        cur.append('"');
                        i++;
                    }
                    else
                    {
                        inQuotes = false;
                    }
                }
                else
                {
                    cur.append(ch);
                }
            }
            else
            {
                if (ch == '"')
                {
                    inQuotes = true;
                }
                else if (ch == ',')
                {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                else
                {
                    cur.append(ch);
                }
            }
        }

        out.add(cur.toString());
        return out;
    }

    public record CoaCsvRow(String code,
                            String name,
                            String accountType,
                            String normalBalance,
                            String parentCode)
    {
    }
}
