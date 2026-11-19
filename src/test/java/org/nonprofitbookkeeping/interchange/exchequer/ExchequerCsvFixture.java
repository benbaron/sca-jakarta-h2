package org.nonprofitbookkeeping.interchange.exchequer;

import java.nio.file.*;
import java.util.*;

/** Synthetic worksheet only; never uses uploaded financial records. */
public final class ExchequerCsvFixture
{
    private static final int[] SPLITS = {32, 58, 84, 110};
    public static Path write(Path path, String... dates) throws Exception
    {
        var records = header();
        int ordinal = 0;
        for (String date : dates)
        {
            var row = row(++ordinal, date, "10.12");
            row.set(7, "Example source payee " + ordinal);
            split(row, 0, "10.12", "Example Income", "");
            records.add(row);
        }
        Files.writeString(path, csv(records));
        return path;
    }
    public static String csv(List<List<String>> records)
    {
        return records.stream().map(row -> row.stream().map(value -> "\"" + value.replace("\"", "\"\"") + "\"")
                .collect(java.util.stream.Collectors.joining(","))).collect(java.util.stream.Collectors.joining("\r\n"));
    }

    public static List<String> blank()
    {
        return new ArrayList<>(java.util.Collections.nCopies(639, ""));
    }

    public static List<List<String>> header()
    {
        List<List<String>> records = new ArrayList<>(); for (int i = 0; i < 8; i++) records.add(blank());
        records.get(0).set(116, "SCA, INC. EXCHEQUER REPORT (2026-03)");
        records.get(1).set(1, "Example branch"); records.get(2).set(1, "2026 LEDGER, REPORTING TO Q3");
        records.get(5).set(1, "Checking"); records.get(7).set(1, "1,000.00"); records.get(7).set(3, "12/31/2025");
        String[] core = {"Row", "Balance*", "Transaction", "Date", "on Statement", "or Chq #", "or Tfr Date", "Legal Name of Person/Organization", "Details/Notes", "Bank Account", "The Bank", "Budget Category", "The Budget", "Fund", "Merchant", "Row #", "Reversed"};
        for (int i = 0; i < core.length; i++) records.get(6).set(i, core[i]);
        for (int start : SPLITS)
        {
            String[] headers = {"Row", "Amount ", "Income Category", "Expense Category", "(Exp. Type)", "Num", "#Purch"};
            for (int i = 0; i < headers.length; i++) records.get(6).set(start + i, headers[i]);
        }
        return records;
    }

    public static List<String> row(int number, String date, String net)
    {
        var row = blank(); row.set(0, Integer.toString(number)); row.set(1, "1,000.00"); row.set(2, net); row.set(3, date);
        row.set(9, "Checking"); row.set(10, "Now"); row.set(12, "Now"); row.set(13, "General Fund");
        for (int start : SPLITS) { row.set(start, Integer.toString(number)); row.set(start + 4, "AR (Events)"); }
        return row;
    }

    public static void split(List<String> row, int ordinal, String amount, String income, String expense)
    {
        int start = SPLITS[ordinal]; row.set(start + 1, amount); row.set(start + 2, income); row.set(start + 3, expense);
    }
}
