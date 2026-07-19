package org.nonprofitbookkeeping.service;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Queries factual runtime and authoritative-database health for the Diagnostics workspace. */
public final class DiagnosticsQueryService
{
    public enum CheckStatus
    {
        OK,
        WARNING,
        FAILED
    }

    public record Count(int active, int total)
    {
        public Count
        {
            if (active < 0 || total < 0 || active > total)
            {
                throw new IllegalArgumentException("Diagnostic counts must satisfy 0 <= active <= total");
            }
        }
    }

    public record Report(
            Instant runtimeTimestamp,
            String javaVersion,
            String activeCompanyCode,
            Path activeDatabasePath,
            CheckStatus datasourceStatus,
            CheckStatus qualityStatus,
            Count accounts,
            Count funds,
            Map<String, Integer> duplicateAccountCodes,
            Map<String, Integer> duplicateFundCodes,
            String failureMessage)
    {
        public Report
        {
            runtimeTimestamp = Objects.requireNonNull(runtimeTimestamp, "runtimeTimestamp");
            javaVersion = Objects.requireNonNullElse(javaVersion, "unknown");
            activeCompanyCode = Objects.requireNonNullElse(activeCompanyCode, "");
            activeDatabasePath = Objects.requireNonNull(activeDatabasePath, "activeDatabasePath")
                    .toAbsolutePath().normalize();
            datasourceStatus = Objects.requireNonNull(datasourceStatus, "datasourceStatus");
            qualityStatus = Objects.requireNonNull(qualityStatus, "qualityStatus");
            accounts = Objects.requireNonNull(accounts, "accounts");
            funds = Objects.requireNonNull(funds, "funds");
            duplicateAccountCodes = immutableCopy(duplicateAccountCodes);
            duplicateFundCodes = immutableCopy(duplicateFundCodes);
            failureMessage = failureMessage == null ? "" : failureMessage;
        }

        public boolean available()
        {
            return datasourceStatus != CheckStatus.FAILED;
        }

        private static Map<String, Integer> immutableCopy(Map<String, Integer> source)
        {
            return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(source, "source")));
        }
    }

    @FunctionalInterface
    interface ConnectionProbe
    {
        void verify() throws Exception;
    }

    private final ConnectionProbe connectionProbe;
    private final Supplier<List<String>> accountCodes;
    private final IntSupplier activeAccountCount;
    private final Supplier<List<String>> fundCodes;
    private final IntSupplier activeFundCount;
    private final Supplier<String> activeCompanyCode;
    private final Supplier<Path> activeDatabasePath;
    private final Clock clock;
    private final Supplier<String> javaVersion;

    public DiagnosticsQueryService(
            DataSource dataSource,
            AccountLookupService accountLookup,
            FundLookupService fundLookup,
            Supplier<String> activeCompanyCode,
            Supplier<Path> activeDatabasePath)
    {
        this(
                () ->
                {
                    try (var ignored = Objects.requireNonNull(dataSource, "dataSource").getConnection())
                    {
                        // Opening and closing the connection is the factual datasource probe.
                    }
                },
                () -> Objects.requireNonNull(accountLookup, "accountLookup")
                        .listPostingAccountsIncludingInactive().stream().map(account -> account.getCode()).toList(),
                () -> accountLookup.listActivePostingAccounts().size(),
                () -> Objects.requireNonNull(fundLookup, "fundLookup")
                        .listAllFunds().stream().map(fund -> fund.getCode()).toList(),
                () -> fundLookup.listActiveFunds().size(),
                activeCompanyCode,
                activeDatabasePath,
                Clock.systemUTC(),
                () -> System.getProperty("java.version", "unknown"));
    }

    DiagnosticsQueryService(
            ConnectionProbe connectionProbe,
            Supplier<List<String>> accountCodes,
            IntSupplier activeAccountCount,
            Supplier<List<String>> fundCodes,
            IntSupplier activeFundCount,
            Supplier<String> activeCompanyCode,
            Supplier<Path> activeDatabasePath,
            Clock clock,
            Supplier<String> javaVersion)
    {
        this.connectionProbe = Objects.requireNonNull(connectionProbe, "connectionProbe");
        this.accountCodes = Objects.requireNonNull(accountCodes, "accountCodes");
        this.activeAccountCount = Objects.requireNonNull(activeAccountCount, "activeAccountCount");
        this.fundCodes = Objects.requireNonNull(fundCodes, "fundCodes");
        this.activeFundCount = Objects.requireNonNull(activeFundCount, "activeFundCount");
        this.activeCompanyCode = Objects.requireNonNull(activeCompanyCode, "activeCompanyCode");
        this.activeDatabasePath = Objects.requireNonNull(activeDatabasePath, "activeDatabasePath");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.javaVersion = Objects.requireNonNull(javaVersion, "javaVersion");
    }

    public Report query()
    {
        Instant timestamp = clock.instant();
        String version = javaVersion.get();
        String company = activeCompanyCode.get();
        Path database = activeDatabasePath.get();
        try
        {
            connectionProbe.verify();
            List<String> allAccountCodes = accountCodes.get();
            List<String> allFundCodes = fundCodes.get();
            Count accounts = new Count(activeAccountCount.getAsInt(), allAccountCodes.size());
            Count funds = new Count(activeFundCount.getAsInt(), allFundCodes.size());
            Map<String, Integer> duplicateAccounts = duplicateCodes(allAccountCodes);
            Map<String, Integer> duplicateFunds = duplicateCodes(allFundCodes);
            CheckStatus quality = accounts.active() == 0
                    || funds.active() == 0
                    || !duplicateAccounts.isEmpty()
                    || !duplicateFunds.isEmpty()
                    ? CheckStatus.WARNING
                    : CheckStatus.OK;
            return new Report(
                    timestamp,
                    version,
                    company,
                    database,
                    CheckStatus.OK,
                    quality,
                    accounts,
                    funds,
                    duplicateAccounts,
                    duplicateFunds,
                    "");
        }
        catch (Exception ex)
        {
            return new Report(
                    timestamp,
                    version,
                    company,
                    database,
                    CheckStatus.FAILED,
                    CheckStatus.FAILED,
                    new Count(0, 0),
                    new Count(0, 0),
                    Map.of(),
                    Map.of(),
                    safeMessage(ex));
        }
    }

    static Map<String, Integer> duplicateCodes(List<String> codes)
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String code : Objects.requireNonNull(codes, "codes"))
        {
            if (code == null || code.isBlank())
            {
                continue;
            }
            counts.merge(code, 1, Integer::sum);
        }
        counts.entrySet().removeIf(entry -> entry.getValue() < 2);
        return counts;
    }

    private static String safeMessage(Exception failure)
    {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
    }
}
