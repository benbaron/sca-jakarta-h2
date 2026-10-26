package org.nonprofitbookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.nonprofitbookkeeping.persistence.DatabaseTransferService;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.report.ReportDefinition;
import org.nonprofitbookkeeping.report.FunctionalExpenseReportBuilder;
import org.nonprofitbookkeeping.report.ReportFundOption;
import org.nonprofitbookkeeping.report.ReportRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Non-mutating company-wide accounting packet from one private H2 backup snapshot. */
public final class CoreAccountingPacketService
{
    static final int MAX_LEDGER_ROWS = 1_000_000;
    public static final List<String> OMITTED = List.of();
    private final Supplier<Path> database;
    private final Supplier<String> company;
    private final AuthorizationGuard guard;
    private final int ledgerCapacity;

    public CoreAccountingPacketService(Supplier<Path> database, Supplier<String> company, AuthorizationGuard guard)
    {
        this(database, company, guard, MAX_LEDGER_ROWS);
    }

    CoreAccountingPacketService(Supplier<Path> database, Supplier<String> company, AuthorizationGuard guard, int ledgerCapacity)
    {
        this.database = Objects.requireNonNull(database);
        this.company = Objects.requireNonNull(company);
        this.guard = guard;
        if (ledgerCapacity < 1 || ledgerCapacity > MAX_LEDGER_ROWS)
        {
            throw new IllegalArgumentException("Invalid ledger row capacity.");
        }
        this.ledgerCapacity = ledgerCapacity;
    }

    public Result export(LocalDate start, LocalDate end, Path destination) throws IOException
    {
        String code = validateScope(start, end);
        requireExport(code);
        Path target = Objects.requireNonNull(destination).toAbsolutePath().normalize();
        if (Files.exists(target))
        {
            throw new IOException("Packet destination already exists; choose a new filename.");
        }
        if (!Files.isDirectory(target.getParent()))
        {
            throw new IOException("Destination directory does not exist.");
        }
        Captured captured = capture(start, end, code);
        Map<String, String> reports = captured.reports();
        AccountingReadiness readiness = captured.readiness();
        Path staged = null;
        try
        {
            Instant created = readiness.capturedAt();
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("packetVersion", "accounting-3");
            manifest.put("applicationVersion", applicationVersion());
            manifest.put("companyCode", code);
            manifest.put("companyName", captured.displayName());
            manifest.put("periodStart", start.toString());
            manifest.put("periodEndInclusive", end.toString());
            manifest.put("balanceCutoff", end.toString());
            manifest.put("scope", "All funds and all events; selected Report Library filters do not apply");
            manifest.put("createdAt", created.toString());
            manifest.put("snapshotSha256", readiness.snapshotSha256());
            manifest.put("dataRevision", readiness.dataRevision());
            manifest.put("automatedReadiness", readiness.automatedStatus());
            manifest.put("readinessBlockers", readiness.blockers());
            manifest.put("readinessWarnings", readiness.warnings());
            manifest.put("readinessBasis", "Automated snapshot observations only; external evidence not attested; no close authorization");
            manifest.put("status", readiness.findings().isEmpty() ? "ACCOUNTING PACKET — no close-readiness certification"
                    : "INCOMPLETE — accounting packet has exceptions; no close-readiness certification");
            manifest.put("omittedSections", OMITTED);
            manifest.put("exceptions", captured.exceptions());
            manifest.put("ledgerCapacity", ledgerCapacity);
            manifest.put("assetInventoryCustodyCapacity", PacketOperationalSections.DOMAIN_SENTINEL - 1);
            manifest.put("budgetBasis", "Active version at captured snapshot, explicitly identified; canonical fiscal YTD through cutoff, not prorated; all retained versions and lines included");
            manifest.put("bankMetadataBasis", "Recorded reconciliation/check metadata at snapshot; cleared-through-cutoff uses recorded clearing dates, not age or cancellation");
            manifest.put("precision", "Exact decimal strings; no additional presentation rounding; XLSX amounts are text");
            manifest.put("csvSafety", "Text beginning with =, +, -, @, tab or carriage return is prefixed with an apostrophe; numeric amounts remain exact");
            manifest.put("sections", List.copyOf(reports.keySet()));
            staged = Files.createTempFile(target.getParent(), ".sca-packet-", ".partial");
            writePacket(staged, reports, manifest);
            requireExport(code);
            // A hard link publishes a completed file atomically and never replaces an existing destination.
            Files.createLink(target, staged);
            return new Result(target, readiness.snapshotSha256(), created, List.copyOf(reports.keySet()), captured.exceptions().size());
        }
        finally
        {
            if (staged != null)
            {
                Files.deleteIfExists(staged);
            }
        }
    }

    /** Shares export's private snapshot/projections; never closes a range or records an attestation. */
    public AccountingReadiness previewReadiness(LocalDate start, LocalDate end) throws IOException
    {
        String code = validateScope(start, end);
        requireExport(code);
        AccountingReadiness readiness = capture(start, end, code).readiness();
        requireExport(code);
        return readiness;
    }

    private String validateScope(LocalDate start, LocalDate end)
    {
        Objects.requireNonNull(start, "Start date is required");
        Objects.requireNonNull(end, "End date is required");
        if (end.isBefore(start))
        {
            throw new IllegalArgumentException("End date must not precede start date.");
        }
        if (start.equals(LocalDate.MIN))
        {
            throw new IllegalArgumentException("Packet start needs a representable preceding date for opening balances.");
        }
        String code = Objects.requireNonNull(company.get(), "Company is required").strip().toUpperCase(java.util.Locale.ROOT);
        if (code.isBlank())
        {
            throw new IllegalArgumentException("Company is required.");
        }
        return code;
    }

    private Captured capture(LocalDate start, LocalDate end, String code) throws IOException
    {
        Path workspace = Files.createTempDirectory("sca-core-packet-");
        try
        {
            Path source = database.get();
            DatabaseTransferService transfer = new DatabaseTransferService(() -> source, ignored -> { });
            var backup = transfer.backUpDatabase(workspace.resolve("snapshot.zip"));
            Path copy = workspace.resolve("snapshot");
            transfer.restoreDatabaseCopy(backup.backupFile(), copy);
            Map<String, String> reports = new LinkedHashMap<>();
            String displayName;
            List<PacketOperationalSections.ExceptionRow> exceptions;
            try (Jpa snapshot = new Jpa(copy))
            {
                displayName = new CompanyAdminService(snapshot).findCompany(code)
                        .orElseThrow(() -> new IllegalArgumentException("Company is absent from captured snapshot.")).displayName();
                FinancialReportService financial = new FinancialReportService(snapshot, () -> code);
                var trial = financial.trialBalance(end, null);
                var balance = financial.balanceSheet(end, null);
                reports.put("Trial Balance", FinancialReportRenderer.renderTrialBalanceCsv(trial));
                reports.put("Balance Sheet", FinancialReportRenderer.renderBalanceSheetCsv(balance));
                reports.put("Income Statement", FinancialReportRenderer.renderIncomeStatementCsv(financial.incomeStatement(start, end, null)));
                var request = new ReportRequest(ReportDefinition.FUNCTIONAL_EXPENSES, start, end,
                        ReportFundOption.ALL_FUNDS, ReportRequest.DEFAULT_ROW_LIMIT);
                reports.put("Functional Expenses", FunctionalExpenseReportBuilder.completeResult(request,
                        financial.functionalExpenses(start, end, null, null), FinancialReportDisplayFormat.plain()).csv());
                exceptions = new java.util.ArrayList<>(new PacketOperationalSections(snapshot, code, start, end, ledgerCapacity).append(reports));
                if (!trial.isBalanced())
                {
                    exceptions.add(new PacketOperationalSections.ExceptionRow("BLOCKER", "Trial Balance", "",
                            "Debits minus credits: " + trial.totalDebits().subtract(trial.totalCredits()).toPlainString()));
                }
                if (!balance.isBalanced())
                {
                    exceptions.add(new PacketOperationalSections.ExceptionRow("BLOCKER", "Balance Sheet", "",
                            "Assets minus liabilities and net assets: " + balance.totalAssets().subtract(balance.liabilitiesAndEquity()).toPlainString()));
                }
                reports.put("Exceptions and Notes", PacketCsv.records(PacketOperationalSections.ExceptionRow.class, exceptions));
            }
            AccountingReadiness readiness = AccountingReadiness.assess(code, start, end, Instant.now(), backup.sha256(), reports, exceptions);
            reports.put("Readiness Findings", PacketCsv.records(AccountingReadiness.Finding.class, readiness.findings()));
            validateReports(reports);
            return new Captured(displayName, reports, List.copyOf(exceptions), readiness);
        }
        finally
        {
            try (var paths = Files.walk(workspace))
            {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private void validateReports(Map<String, String> reports) throws IOException
    {
        for (String csv : reports.values())
        {
            var rows = PoiXlsxFinancialReportAdapter.parseCsv(csv);
            if (rows.size() > Math.min(1_048_576, ledgerCapacity + 1))
            {
                throw new IOException("Worksheet row capacity exceeded.");
            }
            if (rows.stream().flatMap(List::stream).anyMatch(value -> value.length() > 32767))
            {
                throw new IOException("Worksheet cell capacity exceeded.");
            }
        }
    }

    private void writePacket(Path staged, Map<String, String> reports, Map<String, Object> manifest) throws IOException
    {
        try (OutputStream output = Files.newOutputStream(staged); ZipOutputStream zip = new ZipOutputStream(output);
                XSSFWorkbook workbook = new XSSFWorkbook())
        {
            var sheet = workbook.createSheet("Manifest");
            int index = 0;
            for (var entry : manifest.entrySet())
            {
                var row = sheet.createRow(index++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue() instanceof java.util.Collection<?> values
                        ? values.size() + " entries; see manifest.json and the corresponding worksheets"
                        : entry.getValue().toString());
            }
            sheet.setColumnWidth(0, 26 * 256);
            sheet.setColumnWidth(1, 100 * 256);
            for (var entry : reports.entrySet())
            {
                var reportSheet = workbook.createSheet(entry.getKey());
                List<List<String>> rows = PoiXlsxFinancialReportAdapter.parseCsv(entry.getValue());
                StringBuilder safeCsv = new StringBuilder();
                for (int r = 0; r < rows.size(); r++)
                {
                    var row = reportSheet.createRow(r);
                    List<String> values = rows.get(r);
                    for (int c = 0; c < values.size(); c++)
                    {
                        String value = values.get(c);
                        row.createCell(c).setCellValue(value);
                        if (c > 0)
                        {
                            safeCsv.append(',');
                        }
                        safeCsv.append('"').append(csvSafe(value).replace("\"", "\"\"")).append('"');
                    }
                    safeCsv.append('\n');
                }
                reportSheet.createFreezePane(0, 1);
                for (int c = 0; c < rows.stream().mapToInt(List::size).max().orElse(0); c++)
                {
                    reportSheet.setColumnWidth(c, 24 * 256);
                }
                write(zip, entry.getKey() + ".csv", safeCsv.toString().getBytes(StandardCharsets.UTF_8));
            }
            write(zip, "manifest.json", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            zip.putNextEntry(new ZipEntry("Accounting Packet.xlsx"));
            workbook.write(zip);
            zip.closeEntry();
        }
    }

    private record Captured(String displayName, Map<String, String> reports,
            List<PacketOperationalSections.ExceptionRow> exceptions, AccountingReadiness readiness) { }

    private void requireExport(String code)
    {
        ServiceAuthorization.require(guard, ApplicationPermission.EXPORT, code, "capture accounting packet/readiness");
    }

    private static String csvSafe(String value)
    {
        if (value.isEmpty() || value.matches("-?\\d+(\\.\\d+)?"))
        {
            return value;
        }
        return "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    private static String applicationVersion() throws IOException
    {
        try (var input = CoreAccountingPacketService.class.getResourceAsStream(
                "/META-INF/maven/org.nonprofitbookkeeping/sca-ledger-h2-jakarta/pom.properties"))
        {
            if (input == null)
            {
                return "development (version metadata unavailable)";
            }
            var properties = new java.util.Properties();
            properties.load(input);
            return properties.getProperty("version", "unknown");
        }
    }

    private static void write(ZipOutputStream zip, String name, byte[] bytes) throws IOException
    {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    public record Result(Path destination, String snapshotSha256, Instant createdAt, List<String> sections, int exceptionCount) { }
}
