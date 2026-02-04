package org.nonprofitbookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * Import/export for Chart of Accounts and Funds.
 *
 * This is intentionally pragmatic:
 * - CSV is spreadsheet-friendly for humans
 * - JSON is versionable (“git the chart”)
 *
 * You can extend this later to include aliases, report-section mappings, schedule requirements, etc.
 */
@ApplicationScoped
public class CoaFundIo
{
    @Inject
    private Jpa jpa;

    private final ObjectMapper mapper;

    public CoaFundIo()
    {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    // ---------------------
    // Funds CSV
    // ---------------------

    public void exportFundsCsv(Path file) throws IOException
    {
        try (EntityManager em = jpa.em();
             BufferedWriter w = Files.newBufferedWriter(file))
        {
            w.write("code,name,fund_type,parent_code,is_active,effective_from,effective_to,restriction_text\n");
            List<Fund> funds = em.createQuery("select f from Fund f order by f.code", Fund.class).getResultList();
            for (Fund f : funds)
            {
                String parentCode = (f.getParent() == null) ? "" : f.getParent().getCode();
                w.write(csv(f.getCode()) + "," + csv(f.getName()) + "," + csv(f.getFundType().name()) + "," +
                        csv(parentCode) + "," + csv(Boolean.toString(f.isActive())) + "," +
                        csv(nz(f.getEffectiveFrom())) + "," + csv(nz(f.getEffectiveTo())) + "," +
                        csv(nz(f.getRestrictionText())) + "\n");
            }
        }
    }

    public void importFundsCsv(Path file) throws IOException
    {
        List<Map<String,String>> rows = readCsv(file);

        // Two-pass: create/update funds, then link parents.
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();

            Map<String, Fund> byCode = new HashMap<>();
            List<Fund> existing = em.createQuery("select f from Fund f", Fund.class).getResultList();
            for (Fund f : existing) byCode.put(f.getCode(), f);

            for (Map<String,String> r : rows)
            {
                String code = req(r, "code");
                Fund f = byCode.get(code);
                if (f == null)
                {
                    f = new Fund();
                    f.setCode(code);
                    em.persist(f);
                    byCode.put(code, f);
                }

                f.setName(req(r, "name"));
                f.setFundType(FundType.valueOf(req(r, "fund_type")));
                f.setActive(Boolean.parseBoolean(opt(r, "is_active", "true")));
                f.setEffectiveFrom(parseDate(opt(r, "effective_from", "")));
                f.setEffectiveTo(parseDate(opt(r, "effective_to", "")));
                f.setRestrictionText(opt(r, "restriction_text", null));
                f.touchUpdatedAt();
            }

            // Parent linking pass
            for (Map<String,String> r : rows)
            {
                String code = req(r, "code");
                String parentCode = opt(r, "parent_code", "");
                if (parentCode == null || parentCode.isBlank()) continue;

                Fund child = byCode.get(code);
                Fund parent = byCode.get(parentCode);
                if (parent == null)
                {
                    em.getTransaction().rollback();
                    throw new IOException("Unknown parent_code '" + parentCode + "' for fund '" + code + "'");
                }
                child.setParent(parent);
            }

            em.getTransaction().commit();
        }
    }

    // ---------------------
    // CoA JSON (minimal)
    // ---------------------

    public void exportCoaJson(Path file) throws IOException
    {
        try (EntityManager em = jpa.em())
        {
            List<ChartOfAccounts> charts = em.createQuery("select c from ChartOfAccounts c order by c.id", ChartOfAccounts.class).getResultList();
            List<Account> accts = em.createQuery("select a from Account a order by a.code", Account.class).getResultList();

            Map<String,Object> payload = new LinkedHashMap<>();
            payload.put("charts", charts);
            payload.put("accounts", accts);

            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), payload);
        }
    }

    // ---------------------
    // CoA CSV (accounts only, with parent_code)
    // ---------------------

    public void exportAccountsCsv(Path file, long chartId) throws IOException
    {
        try (EntityManager em = jpa.em();
             BufferedWriter w = Files.newBufferedWriter(file))
        {
            w.write("chart_id,code,name,account_type,subtype,opening_balance,normal_balance,parent_code,is_posting,is_active,effective_from,effective_to,description");
            List<Account> accounts = em.createQuery("select a from Account a where a.chart.id = :cid order by a.code", Account.class)
                .setParameter("cid", chartId)
                .getResultList();

            for (Account a : accounts)
            {
                String parentCode = (a.getParent() == null) ? "" : a.getParent().getCode();
                w.write(a.getChart().getId() + "," + csv(a.getCode()) + "," + csv(a.getName()) + "," +
                        csv(a.getAccountType().name()) + "," + csv(nz(a.getSubtype()==null?"":a.getSubtype().name())) + "," + csv(nz(a.getOpeningBalance())) + "," + csv(a.getNormalBalance().name()) + "," +
                        csv(parentCode) + "," + csv(Boolean.toString(a.isPosting())) + "," +
                        csv(Boolean.toString(a.isActive())) + "," + csv(nz(a.getEffectiveFrom())) + "," +
                        csv(nz(a.getEffectiveTo())) + "," + csv(nz(a.getDescription())) + "\n");
            }
        }
    }

    public long importAccountsCsv(Path file, String chartName, String chartVersion) throws IOException
    {
        List<Map<String,String>> rows = readCsv(file);

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();

            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setName(chartName);
            chart.setVersion(chartVersion);
            chart.setStatus(ChartStatus.DRAFT);
            em.persist(chart);

            Map<String, Account> byCode = new HashMap<>();

            // first pass create accounts
            for (Map<String,String> r : rows)
            {
                Account a = new Account();
                a.setChart(chart);
                a.setCode(req(r, "code"));
                a.setName(req(r, "name"));
                a.setAccountType(AccountType.valueOf(req(r, "account_type")));
                a.setSubtype(parseSubtype(opt(r, "subtype", "")));
                a.setOpeningBalance(parseMoney(opt(r, "opening_balance", "0")));
                a.setNormalBalance(NormalBalance.valueOf(req(r, "normal_balance")));
                a.setPosting(Boolean.parseBoolean(opt(r, "is_posting", "true")));
                a.setActive(Boolean.parseBoolean(opt(r, "is_active", "true")));
                a.setEffectiveFrom(parseDate(opt(r, "effective_from", "")));
                a.setEffectiveTo(parseDate(opt(r, "effective_to", "")));
                a.setDescription(opt(r, "description", null));
                em.persist(a);
                byCode.put(a.getCode(), a);
            }

            // second pass link parents
            for (Map<String,String> r : rows)
            {
                String code = req(r, "code");
                String parentCode = opt(r, "parent_code", "");
                if (parentCode == null || parentCode.isBlank()) continue;

                Account child = byCode.get(code);
                Account parent = byCode.get(parentCode);
                if (parent == null)
                {
                    em.getTransaction().rollback();
                    throw new IOException("Unknown parent_code '" + parentCode + "' for account '" + code + "'");
                }
                child.setParent(parent);
            }

            em.getTransaction().commit();
            return chart.getId();
        }
    }

    // ---------------------
    // Helpers
    // ---------------------

    
    private static AccountSubtype parseSubtype(String s)
    {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;

        // Accept: RECEIVABLE, Receivable, receivable, DeferredRevenue, Deferred_Revenue, Deferred Revenue, etc.
        String normalized = t
            .replace('-', ' ')
            .replace('_', ' ')
            .trim();

        // Insert spaces before capitals for CamelCase (DeferredRevenue -> Deferred Revenue)
        normalized = normalized.replaceAll("(?<=[a-z])(?=[A-Z])", " ");

        normalized = normalized.toUpperCase().replace(' ', '_');

        return AccountSubtype.valueOf(normalized);
    }

    private static BigDecimal parseMoney(String s)
    {
        if (s == null) return BigDecimal.ZERO;
        String t = s.trim();
        if (t.isEmpty()) return BigDecimal.ZERO;

        // Allow commas, currency symbols, parentheses (basic friendly parsing).
        t = t.replace("$", "").replace(",", "");
        boolean paren = t.startsWith("(") && t.endsWith(")");
        if (paren) t = "-" + t.substring(1, t.length() - 1);

        return new BigDecimal(t);
    }

private static String csv(String v)
    {
        if (v == null) return "";
        String s = v.replace("\"", "\"\"");
        return "\"" + s + "\"";
    }

    private static String nz(Object v)
    {
        return v == null ? "" : v.toString();
    }

    private static LocalDate parseDate(String s)
    {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return LocalDate.parse(t);
    }

    private static String req(Map<String,String> r, String k) throws IOException
    {
        String v = r.get(k);
        if (v == null || v.isBlank()) throw new IOException("Missing required column '" + k + "'");
        return v.trim();
    }

    private static String opt(Map<String,String> r, String k, String d)
    {
        String v = r.get(k);
        return (v == null) ? d : v.trim();
    }

    private static List<Map<String,String>> readCsv(Path file) throws IOException
    {
        try (BufferedReader br = Files.newBufferedReader(file))
        {
            String header = br.readLine();
            if (header == null) return List.of();
            List<String> cols = parseCsvLine(header);
            List<Map<String,String>> out = new ArrayList<>();

            String line;
            while ((line = br.readLine()) != null)
            {
                if (line.isBlank()) continue;
                List<String> vals = parseCsvLine(line);
                Map<String,String> row = new HashMap<>();
                for (int i=0; i<cols.size() && i<vals.size(); i++)
                {
                    row.put(cols.get(i), vals.get(i));
                }
                out.add(row);
            }
            return out;
        }
    }

    // Minimal CSV parser for quoted fields (sufficient for our exports).
    private static List<String> parseCsvLine(String line)
    {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i=0; i<line.length(); i++)
        {
            char ch = line.charAt(i);
            if (inQ)
            {
                if (ch == '"')
                {
                    if (i+1 < line.length() && line.charAt(i+1) == '"')
                    {
                        cur.append('"');
                        i++;
                    }
                    else
                    {
                        inQ = false;
                    }
                }
                else
                {
                    cur.append(ch);
                }
            }
            else
            {
                if (ch == '"') inQ = true;
                else if (ch == ',')
                {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                else cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }
}