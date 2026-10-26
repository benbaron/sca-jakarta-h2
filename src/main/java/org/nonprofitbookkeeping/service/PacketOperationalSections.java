package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.report.AssetInventoryReportQueryService;
import org.nonprofitbookkeeping.report.ReportRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Operational projections over the packet's isolated database, never over the changing live database. */
final class PacketOperationalSections
{
    static final int DOMAIN_SENTINEL = ReportRequest.MAX_ROW_LIMIT;
    private final Jpa jpa;
    private final String company;
    private final LocalDate start;
    private final LocalDate end;
    private final int capacity;
    private final List<ExceptionRow> exceptions = new ArrayList<>();

    PacketOperationalSections(Jpa jpa, String company, LocalDate start, LocalDate end, int capacity)
    {
        this.jpa = jpa;
        this.company = company;
        this.start = start;
        this.end = end;
        this.capacity = capacity;
    }

    List<ExceptionRow> append(Map<String, String> output)
    {
        ledgerAndBanks(output);
        schedules(output);
        assets(output);
        budgets(output);
        transfers(output);
        table(output, "Exceptions and Notes", ExceptionRow.class, exceptions);
        return List.copyOf(exceptions);
    }

    private void ledgerAndBanks(Map<String, String> out)
    {
        List<LedgerRow> ledger = ledger(start, false);
        table(out, "General Ledger", LedgerRow.class, ledger);
        for (var row : ledger)
        {
            if (row.eventId() == null) issue("WARNING", "Ledger", row.splitId().toString(), "No Event recorded; no tag inferred.");
            if (row.accountType() == AccountType.EXPENSE && row.function() == null)
                issue("BLOCKER", "Functional Expenses", row.splitId().toString(), "Unclassified expense purpose.");
        }
        List<LedgerRow> bank = ledger(null, true);
        table(out, "Bank Activity", LedgerRow.class, bank.stream().filter(r -> !r.date().isBefore(start)).toList());
        table(out, "Outstanding Bank Items", LedgerRow.class, bank.stream().filter(r -> !r.clearedThroughCutoff()).toList());
        long outstanding = bank.stream().filter(r -> !r.clearedThroughCutoff()).count();
        if (outstanding > 0)
        {
            issue("WARNING", "Reconciliation", "", outstanding + " bank line(s) remain outstanding through cutoff; inspect instruments and statement timing.");
        }
        for (var row : bank)
        {
            if (row.recordedCleared() && row.clearedOn() == null)
                issue("BLOCKER", "Bank", row.splitId().toString(), "Cleared date unknown; historical clearing cannot be established.");
        }
        List<Map<String, Object>> configurations = new ArrayList<>();
        var configured = new BankConfigurationService(jpa).listBankAccounts(company);
        var configuredIds = new java.util.HashSet<Long>();
        for (var account : configured)
        {
            if (account.getOpeningDate() != null && account.getOpeningDate().isAfter(end)) continue;
            Account gl = account.getAccount();
            if (gl == null)
            {
                issue("BLOCKER", "Bank", account.getId().toString(), "Configured bank has no ledger account.");
                continue;
            }
            if (!configuredIds.add(gl.getId())) issue("BLOCKER", "Bank", gl.getId().toString(), "Several configured banks share one ledger account; no balance allocation inferred.");
            BigDecimal opening = Objects.requireNonNullElse(gl.getOpeningBalance(), BigDecimal.ZERO);
            BigDecimal before = bank.stream().filter(r -> r.accountId().equals(gl.getId()) && r.date().isBefore(start))
                    .map(r -> r.debit().subtract(r.credit())).reduce(opening, BigDecimal::add);
            BigDecimal closing = bank.stream().filter(r -> r.accountId().equals(gl.getId()))
                    .map(r -> r.debit().subtract(r.credit())).reduce(opening, BigDecimal::add);
            configurations.add(PacketCsv.row("bankId", account.getId(), "name", account.getName(),
                    "institution", account.getInstitutionName(), "maskedNumber", account.getMaskedAccountNumber(),
                    "accountId", gl.getId(), "accountCode", gl.getCode(), "active", account.isActive(),
                    "configuredOpeningDate", account.getOpeningDate(), "configuredOpeningBalance", account.getOpeningBalance(),
                    "ledgerOpeningBalance", opening, "bookBeginning", before, "bookEnding", closing));
        }
        for (var row : bank)
        {
            if (!configuredIds.contains(row.accountId()))
            {
                issue("BLOCKER", "Bank", row.accountId().toString(), "BANK ledger account lacks a configured bank baseline.");
                configuredIds.add(row.accountId());
            }
        }
        if (configurations.isEmpty()) issue("BLOCKER", "Bank", "", "No configured bank baseline is available through cutoff.");
        out.put("Bank Configuration", PacketCsv.rows(List.of("bankId", "name", "institution", "maskedNumber", "accountId", "accountCode", "active",
                "configuredOpeningDate", "configuredOpeningBalance", "ledgerOpeningBalance", "bookBeginning", "bookEnding"), configurations));
        var workspace = new BankReconciliationWorkspaceService(jpa);
        List<Map<String, Object>> sessions = new ArrayList<>();
        List<Map<String, Object>> differences = new ArrayList<>();
        List<Map<String, Object>> statementLines = new ArrayList<>();
        List<Map<String, Object>> matchedLedger = new ArrayList<>();
        for (var session : workspace.listSessions(company))
        {
            if (session.statementEndDate().isAfter(end)) continue;
            var snapshot = workspace.readForPacket(session.id(), company);
            sessions.add(PacketCsv.row("sessionId", snapshot.sessionId(), "bankId", snapshot.bankAccountId(),
                    "bank", snapshot.bankAccountLabel(), "start", snapshot.statementStartDate(), "end", snapshot.statementEndDate(),
                    "recordedStatus", snapshot.status(), "beginning", snapshot.balances().beginningBalance(),
                    "bookAll", snapshot.balances().bookBalanceAllTransactions(), "bookCleared", snapshot.balances().bookBalanceClearedOnly(),
                    "statementEnding", snapshot.balances().statementEndingBalance(), "difference", snapshot.balances().difference(),
                    "basis", "Recorded session state at snapshot; not a historical metadata reconstruction"));
            for (var difference : snapshot.differences())
            {
                differences.add(PacketCsv.row("sessionId", session.id(), "category", difference.category(),
                        "ledgerDate", difference.ledgerDate(), "statementDate", difference.statementDate(),
                        "ledgerAmount", difference.ledgerAmount(), "statementAmount", difference.statementAmount(), "description", difference.description()));
            }
            for (var line : snapshot.statementEntries()) statementLines.add(PacketCsv.row("sessionId", session.id(),
                    "statementLineId", line.statementLineId(), "date", line.date(), "description", line.description(),
                    "reference", line.reference(), "amount", line.amount(), "recordedClearedState", line.clearedState(),
                    "matchStatus", line.matchStatus(), "matchedLedgerSplitId", line.matchedLedgerSplitId(), "resolution", line.resolution()));
            for (var line : snapshot.ledgerLines()) matchedLedger.add(PacketCsv.row("sessionId", session.id(),
                    "splitId", line.splitId(), "transactionId", line.txnId(), "date", line.date(), "memo", line.memo(),
                    "transactionNumber", line.transactionNumber(), "amount", line.amount(), "recordedCleared", line.cleared(),
                    "matchedStatementLineId", line.matchedStatementLineId(), "matchStatus", line.matchStatus(),
                    "reference", line.paymentReference(), "correctionHistory", line.correctionHistory()));
            if (session.status() != BankReconciliationWorkspaceService.SessionStatus.FINALIZED || snapshot.balances().difference().signum() != 0)
                issue("BLOCKER", "Reconciliation", Long.toString(session.id()), "Recorded reconciliation is unfinished or has a balance difference.");
        }
        if (!configurations.isEmpty() && sessions.isEmpty()) issue("BLOCKER", "Reconciliation", "", "No recorded reconciliation session through cutoff.");
        out.put("Reconciliation Sessions", PacketCsv.rows(List.of("sessionId", "bankId", "bank", "start", "end", "recordedStatus", "beginning",
                "bookAll", "bookCleared", "statementEnding", "difference", "basis"), sessions));
        out.put("Reconciliation Differences", PacketCsv.rows(List.of("sessionId", "category", "ledgerDate", "statementDate", "ledgerAmount", "statementAmount", "description"), differences));
        out.put("Reconciled Statement Lines", PacketCsv.rows(List.of("sessionId", "statementLineId", "date", "description", "reference", "amount",
                "recordedClearedState", "matchStatus", "matchedLedgerSplitId", "resolution"), statementLines));
        out.put("Reconciled Ledger Lines", PacketCsv.rows(List.of("sessionId", "splitId", "transactionId", "date", "memo", "transactionNumber", "amount",
                "recordedCleared", "matchedStatementLineId", "matchStatus", "reference", "correctionHistory"), matchedLedger));
        var checks = new CheckExceptionService(jpa, () -> company, null).report(null, end, null);
        table(out, "Check Exceptions", CheckExceptionService.Row.class, checks);
        for (var check : checks)
            if (!check.exceptions().isBlank()) issue("WARNING", "Checks", Objects.toString(check.transactionId(), ""), check.exceptions());
    }

    private List<LedgerRow> ledger(LocalDate from, boolean banksOnly)
    {
        try (var em = jpa.em())
        {
            var splits = em.createQuery("""
                    select s from TxnSplit s join fetch s.txn t join fetch s.account a join fetch s.fund f
                    left join fetch s.activity e left join fetch s.budgetCategory c left join fetch s.merchant m
                    left join fetch t.payee p left join fetch t.reversalOf rev left join fetch t.replacementFor rep
                    where t.company.code = :company and t.txnDate <= :end
                    and (:start is null or t.txnDate >= :start)
                    and (:banks = false or a.accountFunction = :bank)
                    order by t.txnDate, t.id, s.id
                    """, TxnSplit.class).setParameter("company", company).setParameter("start", from)
                    .setParameter("end", end).setParameter("banks", banksOnly).setParameter("bank", AccountFunction.BANK)
                    .setMaxResults(capacity + 1).getResultList();
            if (splits.size() > capacity) throw new IllegalArgumentException("Packet exceeds ledger capacity; choose a smaller period.");
            List<LedgerRow> result = new ArrayList<>();
            for (var split : splits)
            {
                var txn = split.getTxn();
                BigDecimal signed = split.getAccount().getNormalBalance() == NormalBalance.DEBIT
                        ? split.getAmountSigned() : split.getAmountSigned().negate();
                var event = split.getActivity(); var category = split.getBudgetCategory(); var merchant = split.getMerchant();
                result.add(new LedgerRow(txn.getTxnDate(), txn.getId(), split.getId(), split.getAccount().getId(),
                        split.getAccount().getCode(), split.getAccount().getName(), split.getAccount().getAccountType(),
                        split.getFund().getId(), split.getFund().getCode(), split.getFund().getName(),
                        event == null ? null : event.getId(), event == null ? "" : event.getCode(), event == null ? "" : event.getName(),
                        category == null ? null : category.getId(), category == null ? "" : category.getCode(), category == null ? "" : category.getName(),
                        merchant == null ? null : merchant.getId(), merchant == null ? "" : merchant.getName(), split.getExpenseFunction(),
                        signed.max(BigDecimal.ZERO), signed.negate().max(BigDecimal.ZERO), txn.getMemo(),
                        txn.getPayee() == null ? null : txn.getPayee().getId(), txn.getPayee() == null ? "" : txn.getPayee().getDisplayName(),
                        txn.getReversalOf() == null ? null : txn.getReversalOf().getId(), txn.getReplacementFor() == null ? null : txn.getReplacementFor().getId(),
                        split.getPaymentMethod(), split.getPaymentReference(), split.getPaymentIssuedOn(), split.getPaymentDeliveredOn(),
                        split.getPaymentEvidenceReference(), split.getPaymentReviewedOn(), split.getPaymentReviewNote(), split.isNmr(), split.getNotes(),
                        split.isBankCleared(), split.getBankClearedOn(),
                        split.isBankCleared() && split.getBankClearedOn() != null && !split.getBankClearedOn().isAfter(end)));
            }
            return result;
        }
    }

    private void schedules(Map<String, String> out)
    {
        var queries = new SupplementalOpenItemQueryService(jpa, () -> company);
        List<Map<String, Object>> openings = new ArrayList<>();
        List<Map<String, Object>> controls = new ArrayList<>();
        List<Map<String, Object>> gaps = new ArrayList<>();
        List<Map<String, Object>> movements = new ArrayList<>();
        List<Map<String, Object>> balances = new ArrayList<>();
        for (var kind : SupplementalOpenItemQueryService.Kind.values())
        {
            var before = queries.query(kind, start.minusDays(1));
            var closing = queries.query(kind, end);
            table(out, "Schedule " + kind.name(), SupplementalOpenItemQueryService.Row.class, closing.rows());
            long unsettled = closing.authoritativeRows().stream().filter(row -> row.openBalance().signum() != 0).count();
            if (unsettled > 0)
            {
                issue("WARNING", "Unsettled Items", kind.name(), unsettled + " item(s) retain a balance at cutoff; review due dates and supporting evidence.");
            }
            for (var row : before.rows()) openings.add(PacketCsv.row("kind", kind, "itemId", row.itemId(), "opening", row.openBalance(), "status", row.status()));
            for (var scoped : List.of(before, closing))
            {
                String scope = scoped == before ? "OPENING" : "CLOSING";
                for (var control : scoped.reconciliation().controls()) controls.add(PacketCsv.row("kind", kind, "scope", scope, "accountId", control.accountId(), "code", control.code(),
                        "ledger", control.ledger(), "explained", control.explained(), "unmatched", control.unmatched(), "difference", control.difference()));
                for (var gap : scoped.reconciliation().gaps()) gaps.add(PacketCsv.row("kind", kind, "scope", scope, "transactionId", gap.transactionId(), "splitId", gap.splitId(),
                        "date", gap.date(), "account", gap.accountCode(), "unmatched", gap.unmatched(), "repair", gap.repair()));
            }
            Map<java.util.UUID, BigDecimal> net = new LinkedHashMap<>();
            try (var em = jpa.em())
            {
                for (var effect : SupplementalOpenItemQueryService.periodEffects(em, company, kind, start, end))
                {
                    var line = effect.allocation();
                    BigDecimal delta = line.getAmount().multiply(BigDecimal.valueOf(effect.direction()
                            * (line.getItemEffect() == SupplementalItemEffect.INCREASE ? 1 : -1)));
                    net.merge(line.getItemId(), delta, BigDecimal::add);
                    movements.add(PacketCsv.row("kind", kind, "itemId", line.getItemId(), "allocationId", line.getId(),
                            "date", effect.date(), "transactionId", effect.transactionId(), "sourceTransactionId", line.getTxn().getId(),
                            "sourceSplitId", line.getTxnSplit().getId(), "effect", line.getItemEffect(), "direction", effect.direction(), "change", delta));
                }
            }
            Map<java.util.UUID, BigDecimal> initial = new LinkedHashMap<>(), last = new LinkedHashMap<>();
            before.authoritativeRows().forEach(row -> initial.put(row.itemId(), row.openBalance()));
            closing.authoritativeRows().forEach(row -> last.put(row.itemId(), row.openBalance()));
            var ids = new java.util.LinkedHashSet<>(initial.keySet()); ids.addAll(last.keySet());
            for (var id : ids)
            {
                BigDecimal beginning = initial.getOrDefault(id, BigDecimal.ZERO), change = net.getOrDefault(id, BigDecimal.ZERO), ending = last.getOrDefault(id, BigDecimal.ZERO);
                BigDecimal difference = beginning.add(change).subtract(ending);
                balances.add(PacketCsv.row("kind", kind, "itemId", id, "opening", beginning, "periodChange", change, "closing", ending, "difference", difference));
                if (difference.signum() != 0) issue("BLOCKER", "Schedule Period", id.toString(), "Opening plus canonical effects does not equal closing.");
            }
            if (!before.ready() || !closing.ready()) issue("BLOCKER", "Schedules", kind.name(), "Opening/closing allocation coverage or control difference requires review.");
        }
        out.put("Schedule Openings", PacketCsv.rows(List.of("kind", "itemId", "opening", "status"), openings));
        out.put("Schedule Controls", PacketCsv.rows(List.of("kind", "scope", "accountId", "code", "ledger", "explained", "unmatched", "difference"), controls));
        out.put("Schedule Gaps", PacketCsv.rows(List.of("kind", "scope", "transactionId", "splitId", "date", "account", "unmatched", "repair"), gaps));
        out.put("Schedule Movements", PacketCsv.rows(List.of("kind", "itemId", "allocationId", "date", "transactionId", "sourceTransactionId", "sourceSplitId", "effect", "direction", "change"), movements));
        out.put("Schedule Period Balances", PacketCsv.rows(List.of("kind", "itemId", "opening", "periodChange", "closing", "difference"), balances));
        var nmr = queries.queryNmrPeriod(start, end);
        table(out, "NMR Summaries", NmrPeriodQueryService.Summary.class, nmr.summaries());
        table(out, "NMR Movements", NmrPeriodQueryService.Movement.class, nmr.movements());
        table(out, "NMR Controls", NmrLiabilityQueryService.Control.class, nmr.closing().controls());
        table(out, "NMR Opening Controls", NmrLiabilityQueryService.Control.class, nmr.opening().controls());
        table(out, "NMR Legacy Flags", NmrLiabilityQueryService.LegacyFlag.class, nmr.closing().legacyFlags());
        table(out, "NMR Opening Legacy Flags", NmrLiabilityQueryService.LegacyFlag.class, nmr.opening().legacyFlags());
        if (!nmr.ready()) issue("BLOCKER", "NMR", "", "Unclassified NMR or liability/control coverage requires review.");
        var merchant = queries.queryMerchantReceivables(start, end);
        table(out, "Merchant Summaries", MerchantReceivableQueryService.Summary.class, merchant.summaries());
        table(out, "Merchant Movements", MerchantReceivableQueryService.Movement.class, merchant.movements());
        if (!merchant.ready()) issue("BLOCKER", "Merchant Receivables", "", "Unexplained receivable movements or allocation gaps require review.");
    }

    private void assets(Map<String, String> out)
    {
        var queries = new AssetInventoryReportQueryService(jpa, () -> company);
        var assetRequest = new AssetInventoryReportQueryService.FixedAssetReportRequest(start, end, null, null, null, null, DOMAIN_SENTINEL);
        var inventoryRequest = new AssetInventoryReportQueryService.InventoryReportRequest(start, end, null, null, null, null, DOMAIN_SENTINEL);
        var register = queries.fixedAssetRegister(assetRequest);
        var depreciation = queries.fixedAssetDepreciation(assetRequest);
        var inventory = queries.inventoryValuation(inventoryRequest);
        var movements = queries.inventoryMovementHistory(inventoryRequest);
        var custody = queries.propertyCustodyAsOf(end, DOMAIN_SENTINEL);
        domainTable(out, "Fixed Assets", AssetInventoryReportQueryService.FixedAssetRegisterRow.class, register.rows());
        domainTable(out, "Depreciation", AssetInventoryReportQueryService.DepreciationReportRow.class, depreciation.rows());
        domainTable(out, "Inventory and Supplies", AssetInventoryReportQueryService.InventoryValuationRow.class, inventory.rows());
        domainTable(out, "Inventory Movements", AssetInventoryReportQueryService.InventoryMovementReportRow.class, movements.rows());
        domainTable(out, "Property Custody", AssetInventoryReportQueryService.PropertyCustodyReportRow.class, custody);
        List<Map<String, Object>> links = new ArrayList<>();
        try (var em = jpa.em())
        {
            for (var asset : em.createQuery("""
                    select a from FixedAsset a left join fetch a.acquisitionSplit s left join fetch s.txn t
                    where a.company.code = :company and a.acquisitionDate <= :end order by a.id
                    """, FixedAsset.class).setParameter("company", company).setParameter("end", end).getResultList())
                links.add(PacketCsv.row("assetId", asset.getId(), "name", asset.getName(), "acquisitionDate", asset.getAcquisitionDate(),
                        "source", asset.getAcquisitionSource(), "cost", asset.getAcquisitionCost(),
                        "splitId", asset.getAcquisitionSplit() == null ? null : asset.getAcquisitionSplit().getId(),
                        "transactionId", asset.getAcquisitionSplit() == null ? null : asset.getAcquisitionSplit().getTxn().getId()));
        }
        out.put("Asset Acquisition Links", PacketCsv.rows(List.of("assetId", "name", "acquisitionDate", "source", "cost", "splitId", "transactionId"), links));
        out.put("Asset Controls", PacketCsv.metadata(register));
        out.put("Depreciation Controls", PacketCsv.metadata(depreciation));
        out.put("Inventory Controls", PacketCsv.metadata(inventory));
        out.put("Inventory Movement Controls", PacketCsv.metadata(movements));
        if (depreciation.truncated()) throw new IllegalArgumentException("Depreciation packet capacity exceeded; no truncated packet was written.");
        if (register.difference().signum() != 0) issue("BLOCKER", "Assets", "", register.explanation());
        if (depreciation.difference().signum() != 0) issue("BLOCKER", "Depreciation", "", depreciation.explanation());
        if (inventory.difference().signum() != 0 || inventory.unlinkedMovementNet().signum() != 0) issue("BLOCKER", "Inventory", "", inventory.explanation());
        if (movements.difference().signum() != 0 || movements.unlinkedMovementNet().signum() != 0) issue("BLOCKER", "Inventory Movements", "", movements.explanation());
        Map<String, AssetInventoryReportQueryService.PropertyCustodyReportRow> currentCustody = new LinkedHashMap<>();
        for (var row : custody)
            if (!row.corrected()) currentCustody.putIfAbsent(row.subjectType() + ":" + row.subjectId(), row);
        for (var row : currentCustody.values())
        {
            if (row.overdue() || row.confirmationStatus() == PropertyCustodyEvent.ConfirmationStatus.EXCEPTION)
                issue("WARNING", "Custody", row.eventId().toString(), "Latest recorded custody confirmation is overdue or excepted at cutoff.");
            if (row.expectedReturnDate() != null && row.expectedReturnDate().isBefore(end)
                    && row.eventType() != PropertyCustodyEvent.EventType.RETURN && row.eventType() != PropertyCustodyEvent.EventType.REMOVAL)
                issue("WARNING", "Custody", row.eventId().toString(), "Latest recorded return date precedes cutoff; inspect custody history.");
        }
        boolean chartAvailable;
        try (var em = jpa.em())
        {
            chartAvailable = new CompanyOwnershipService(jpa).requireCompany(em, company).getActiveChartOfAccounts() != null;
        }
        if (chartAvailable)
        {
            var review = new OpeningConversionService(jpa, () -> company).preview(end);
            table(out, "Opening Controls", OpeningConversionService.Check.class, review.checks());
            table(out, "Orphan Controls", OpeningConversionService.OrphanControl.class, review.orphanControls());
            for (var check : review.checks())
                if (check.blocking()) issue("BLOCKER", check.area(), "", check.detail());
        }
        else
        {
            table(out, "Opening Controls", OpeningConversionService.Check.class, List.of());
            table(out, "Orphan Controls", OpeningConversionService.OrphanControl.class, List.of());
            issue("BLOCKER", "Chart", "", "No active Chart of Accounts; opening/control coverage is unavailable.");
        }
    }

    private void budgets(Map<String, String> out)
    {
        var service = new BudgetPlanService(jpa, () -> company);
        List<Map<String, Object>> plans = new ArrayList<>();
        List<Map<String, Object>> lines = new ArrayList<>();
        List<Map<String, Object>> variances = new ArrayList<>();
        if ((long) end.getYear() - start.getYear() > 100) throw new IllegalArgumentException("Packet capacity is 100 fiscal years.");
        LocalDate cursor = start;
        while (!cursor.isAfter(end))
        {
            var fiscal = service.fiscalRange(cursor);
            LocalDate cutoff = end.isBefore(fiscal.fiscalYearEnd()) ? end : fiscal.fiscalYearEnd();
            var versions = service.versionsForFiscalYear(fiscal.fiscalYear());
            for (var version : versions)
            {
                plans.add(PacketCsv.row("planId", version.id(), "name", version.name(), "fiscalYear", version.fiscalYear(),
                        "version", version.versionCode(), "recordedStatus", version.status(), "periodStart", version.periodStart(), "periodEnd", version.periodEnd(),
                        "activatedAt", version.activatedAt(), "archivedAt", version.archivedAt(), "notes", version.notes()));
                for (var line : version.lines()) lines.add(PacketCsv.row("planId", version.id(), "lineId", line.id(), "category", line.budgetCategoryCode(),
                        "fundId", line.fundId(), "fund", line.fundCode(), "eventId", line.activityId(), "event", line.activityCode(),
                        "month", line.periodMonth(), "amount", line.amount(), "notes", line.notes()));
            }
            var active = service.activeForFiscalYear(fiscal.fiscalYear());
            if (active.isEmpty()) issue("BLOCKER", "Budget", Integer.toString(fiscal.fiscalYear()), "No active plan at snapshot; comparison unavailable rather than zero budget.");
            else
            {
                for (var row : service.activeVariance(cutoff)) variances.add(PacketCsv.row("planId", active.get().id(), "version", active.get().versionCode(),
                        "basis", "Canonical fiscal YTD; targets not prorated", "start", fiscal.fiscalYearStart(), "end", cutoff,
                        "category", row.budgetCategoryCode(), "fund", row.fundCode().orElse(""), "event", row.activityCode().orElse(""),
                        "budget", row.budget(), "actual", row.actual(), "variance", row.variance()));
            }
            if (cutoff.equals(end)) break;
            cursor = fiscal.fiscalYearEnd().plusDays(1);
        }
        out.put("Budget Versions", PacketCsv.rows(List.of("planId", "name", "fiscalYear", "version", "recordedStatus", "periodStart", "periodEnd", "activatedAt", "archivedAt", "notes"), plans));
        out.put("Budget Lines", PacketCsv.rows(List.of("planId", "lineId", "category", "fundId", "fund", "eventId", "event", "month", "amount", "notes"), lines));
        out.put("Budget vs Actual", PacketCsv.rows(List.of("planId", "version", "basis", "start", "end", "category", "fund", "event", "budget", "actual", "variance"), variances));
    }

    private void transfers(Map<String, String> out)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (var em = jpa.em())
        {
            var source = em.createQuery("""
                    select f from FundTransfer f join fetch f.fromFund a join fetch f.toFund b
                    left join fetch f.postedTxn t left join fetch t.reversalOf original
                    where a.company.code = :company and b.company = a.company
                    and f.transferDate between :start and :end
                    order by f.transferDate, f.id
                    """, FundTransfer.class).setParameter("company", company).setParameter("start", start).setParameter("end", end)
                    .setMaxResults(capacity + 1).getResultList();
            if (source.size() > capacity) throw new IllegalArgumentException("Transfer packet row capacity exceeded.");
            for (var f : source)
            {
                boolean reversal = f.getPostedTxn() != null && f.getPostedTxn().getReversalOf() != null;
                rows.add(transferRow(f, f.getTransferDate(), f.getAmount(), f.getPostedTxn(), reversal ? "DATED REVERSAL" : "RECORDED TRANSFER"));
                if (f.getPostedTxn() == null)
                {
                    issue("WARNING", "Transfers", f.getId().toString(), "Recorded transfer has no canonical transaction; no accounting effect is inferred.");
                }
            }
        }
        out.put("Fund Transfers", PacketCsv.rows(List.of("transferId", "date", "originalTransferDate", "record", "fromFundId", "fromFund", "toFundId", "toFund", "amount", "recordedStatus", "transactionId", "reversalOfTransactionId", "accountingEffect", "memo"), rows));
    }

    private static Map<String, Object> transferRow(FundTransfer transfer, LocalDate date, BigDecimal amount, Txn transaction, String record)
    {
        return PacketCsv.row("transferId", transfer.getId(), "date", date,
                "originalTransferDate", transaction != null && transaction.getReversalOf() != null ? transaction.getReversalOf().getTxnDate() : transfer.getTransferDate(), "record", record,
                "fromFundId", transfer.getFromFund().getId(), "fromFund", transfer.getFromFund().getCode(),
                "toFundId", transfer.getToFund().getId(), "toFund", transfer.getToFund().getCode(), "amount", amount,
                "recordedStatus", transfer.getStatus(), "transactionId", transaction == null ? null : transaction.getId(),
                "reversalOfTransactionId", transaction == null || transaction.getReversalOf() == null ? null : transaction.getReversalOf().getId(),
                "accountingEffect", transaction == null ? "NONE — no canonical transaction" : "Canonical transaction; see General Ledger", "memo", transfer.getMemo());
    }

    private void domainTable(Map<String, String> out, String name, Class<?> type, List<?> rows)
    {
        if (rows.size() >= DOMAIN_SENTINEL) throw new IllegalArgumentException(name + " reaches the packet domain capacity (4,999 rows); no truncated output was written.");
        table(out, name, type, rows);
    }

    private void table(Map<String, String> out, String name, Class<?> type, List<?> rows)
    {
        if (rows.size() > capacity) throw new IllegalArgumentException(name + " packet row capacity exceeded.");
        out.put(name, PacketCsv.records(type, rows));
    }

    private void issue(String severity, String area, String reference, String detail)
    {
        exceptions.add(new ExceptionRow(severity, area, reference, detail));
    }

    public record ExceptionRow(String severity, String area, String reference, String detail) { }
    public record LedgerRow(LocalDate date, Long transactionId, Long splitId, Long accountId, String accountCode, String accountName,
            AccountType accountType, Long fundId, String fund, String fundName, Long eventId, String event, String eventName, Long categoryId, String category, String categoryName,
            Long merchantId, String merchant, ExpenseFunction function, BigDecimal debit, BigDecimal credit, String memo,
            Long payeeId, String payee,
            Long reversalOf, Long replacementFor, String paymentMethod, String reference, LocalDate issuedOn, LocalDate deliveredOn,
            String evidence, LocalDate reviewedOn, String reviewNote, boolean nmr, String splitNotes, boolean recordedCleared, LocalDate clearedOn, boolean clearedThroughCutoff) { }
}
