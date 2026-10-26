package org.nonprofitbookkeeping.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Immutable automated observations, never a close authorization or external-review attestation. */
public record AccountingReadiness(String companyCode, LocalDate start, LocalDate end, Instant capturedAt,
        String snapshotSha256, String dataRevision, List<String> sections, List<Finding> findings)
{
    public AccountingReadiness
    {
        sections = List.copyOf(sections);
        findings = List.copyOf(findings);
    }

    public enum Severity { BLOCKER, WARNING }
    public enum Destination
    {
        JOURNAL, BANK_CONFIGURATION, RECONCILIATION, REPORTS, ASSETS, DEPRECIATION,
        INVENTORY, CUSTODY, BUDGET, TRANSFERS, OPENING_CONVERSION, CHART
    }

    public record Finding(String id, Severity severity, String area, String reference,
            String detail, Destination destination)
    {
    }

    public long blockers()
    {
        return findings.stream().filter(f -> f.severity() == Severity.BLOCKER).count();
    }

    public long warnings()
    {
        return findings.size() - blockers();
    }

    public String automatedStatus()
    {
        return blockers() > 0 ? "Blocked" : warnings() > 0 ? "Review required" : "No automated exceptions";
    }

    static AccountingReadiness assess(String company, LocalDate start, LocalDate end, Instant captured,
            String snapshotSha, Map<String, String> reports, List<PacketOperationalSections.ExceptionRow> exceptions)
    {
        List<Finding> findings = exceptions.stream().map(row -> new Finding(
                digest(List.of(row.severity(), row.area(), row.reference(), row.detail())),
                Severity.valueOf(row.severity()), row.area(), row.reference(), row.detail(), destination(row.area())))
                .distinct().toList();
        var content = new java.util.ArrayList<>(List.of("readiness-content-1", company, start.toString(), end.toString()));
        reports.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
        {
            content.add(entry.getKey());
            content.add(entry.getValue());
        });
        var sections = new java.util.ArrayList<>(reports.keySet());
        sections.add("Readiness Findings");
        return new AccountingReadiness(company, start, end, captured, snapshotSha, digest(content), sections, findings);
    }

    private static Destination destination(String area)
    {
        return switch (area)
        {
            case "Ledger", "Functional Expenses" -> Destination.JOURNAL;
            case "Bank" -> Destination.BANK_CONFIGURATION;
            case "Reconciliation", "Checks" -> Destination.RECONCILIATION;
            case "Assets" -> Destination.ASSETS;
            case "Depreciation" -> Destination.DEPRECIATION;
            case "Inventory", "Inventory Movements" -> Destination.INVENTORY;
            case "Custody" -> Destination.CUSTODY;
            case "Budget" -> Destination.BUDGET;
            case "Transfers" -> Destination.TRANSFERS;
            case "Chart" -> Destination.CHART;
            case "Opening", "Orphan control balances", "Opening trial balance", "Configured bank openings",
                    "Conversion source facts", "Fixed assets" -> Destination.OPENING_CONVERSION;
            default -> Destination.REPORTS;
        };
    }

    private static String digest(List<String> values)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values)
            {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException(ex);
        }
    }
}
