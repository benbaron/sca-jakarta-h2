package org.nonprofitbookkeeping.report;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.FixedAssetDepreciationRun;
import org.nonprofitbookkeeping.model.FixedAssetLifecycleEvent;
import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.model.InventoryMovement;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Company-scoped domain and canonical-ledger queries for fixed-asset and inventory reports. */
public final class AssetInventoryReportQueryService
{
    private static final BigDecimal ZERO = new BigDecimal("0.0000");

    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;

    public AssetInventoryReportQueryService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
    }

    /** Supplies stable-ID choices for report-only domain filters. */
    public FilterCatalog filterCatalog()
    {
        String companyCode = requireCompanyCode();
        try (EntityManager em = jpa.em())
        {
            List<FixedAsset> assets = em.createQuery("""
                    select a from FixedAsset a
                    join fetch a.assetAccount account
                    join fetch a.company company
                    where company.code = :companyCode
                    order by a.name, a.id
                    """, FixedAsset.class)
                    .setParameter("companyCode", companyCode)
                    .getResultList();
            List<InventoryItem> items = em.createQuery("""
                    select i from InventoryItem i
                    join fetch i.inventoryAccount account
                    join fetch i.company company
                    where company.code = :companyCode
                    order by i.name, i.id
                    """, InventoryItem.class)
                    .setParameter("companyCode", companyCode)
                    .getResultList();

            Map<Long, FilterOption> assetAccounts = new LinkedHashMap<>();
            List<FilterOption> assetOptions = new ArrayList<>();
            for (FixedAsset asset : assets)
            {
                assetOptions.add(new FilterOption(asset.getId(), asset.getName()));
                Account account = asset.getAssetAccount();
                assetAccounts.putIfAbsent(account.getId(),
                        new FilterOption(account.getId(), account.getCode() + " — " + account.getName()));
            }

            Map<Long, FilterOption> inventoryAccounts = new LinkedHashMap<>();
            List<FilterOption> itemOptions = new ArrayList<>();
            for (InventoryItem item : items)
            {
                itemOptions.add(new FilterOption(item.getId(), item.getName()));
                Account account = item.getInventoryAccount();
                inventoryAccounts.putIfAbsent(account.getId(),
                        new FilterOption(account.getId(), account.getCode() + " — " + account.getName()));
            }
            return new FilterCatalog(
                    assetOptions,
                    assetAccounts.values().stream().toList(),
                    itemOptions,
                    inventoryAccounts.values().stream().toList());
        }
    }

    public FixedAssetRegisterResult fixedAssetRegister(FixedAssetReportRequest request)
    {
        Objects.requireNonNull(request, "request");
        String companyCode = requireCompanyCode();
        try (EntityManager em = jpa.em())
        {
            List<FixedAsset> assets = loadAssets(em, companyCode, request);
            List<Long> ids = assets.stream().map(FixedAsset::getId).toList();
            Map<Long, BigDecimal> depreciation = depreciationThrough(em, ids, request.endDate());
            Map<Long, List<LifecycleFact>> lifecycle = lifecycleFacts(em, ids);
            Set<Long> grossAccounts = assets.stream()
                    .map(asset -> asset.getAssetAccount().getId())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Set<Long> contraAccounts = assets.stream()
                    .map(asset -> asset.getAccumulatedDepreciationAccount().getId())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

            List<FixedAssetRegisterRow> rows = new ArrayList<>();
            for (FixedAsset asset : assets)
            {
                FixedAssetAsOf values = assetAsOf(
                        asset,
                        request.endDate(),
                        depreciation.getOrDefault(asset.getId(), ZERO),
                        lifecycle.getOrDefault(asset.getId(), List.of()));
                if (request.status() != null && values.status() != request.status())
                {
                    continue;
                }
                rows.add(new FixedAssetRegisterRow(
                        asset.getId(),
                        asset.getName(),
                        values.status(),
                        asset.getAcquisitionDate(),
                        asset.getAssetAccount().getId(),
                        asset.getAssetAccount().getCode(),
                        asset.getAssetAccount().getName(),
                        asset.getAccumulatedDepreciationAccount().getId(),
                        asset.getAccumulatedDepreciationAccount().getCode(),
                        asset.getFund().getId(),
                        asset.getFund().getCode(),
                        scale(asset.getAcquisitionCost()),
                        values.recognizedCost(),
                        values.accumulatedDepreciation(),
                        values.impairment(),
                        values.recognizedContra(),
                        values.bookValue()));
                if (rows.size() >= request.rowLimit())
                {
                    break;
                }
            }

            BigDecimal domainGross = ZERO;
            BigDecimal domainContra = ZERO;
            for (FixedAssetRegisterRow row : rows)
            {
                domainGross = domainGross.add(row.recognizedCost());
                domainContra = domainContra.add(row.recognizedContra());
            }
            LedgerControl ledger = ledgerControl(
                    em, companyCode, grossAccounts, contraAccounts,
                    request.endDate(), request.fundId());
            return new FixedAssetRegisterResult(
                    rows,
                    domainGross,
                    domainContra,
                    domainGross.subtract(domainContra),
                    ledger.gross(),
                    ledger.contra(),
                    ledger.net(),
                    domainGross.subtract(domainContra).subtract(ledger.net()),
                    ledger.openingBalanceExcluded(),
                    reconciliationExplanation(
                            request.filtered(),
                            ledger.openingBalanceExcluded(),
                            "fixed-asset register"));
        }
    }

    public FixedAssetDepreciationResult fixedAssetDepreciation(FixedAssetReportRequest request)
    {
        Objects.requireNonNull(request, "request");
        String companyCode = requireCompanyCode();
        try (EntityManager em = jpa.em())
        {
            List<FixedAsset> assets = loadAssets(em, companyCode, request);
            Map<Long, FixedAsset> byId = new LinkedHashMap<>();
            for (FixedAsset asset : assets)
            {
                byId.put(asset.getId(), asset);
            }
            List<Long> ids = byId.keySet().stream().toList();
            Map<Long, BigDecimal> throughEnd = depreciationThrough(em, ids, request.endDate());
            Map<Long, List<LifecycleFact>> lifecycle = lifecycleFacts(em, ids);
            List<DepreciationReportRow> rows = new ArrayList<>();
            BigDecimal domainContra = ZERO;
            int reconciledAssets = 0;

            if (!ids.isEmpty())
            {
                List<FixedAssetDepreciationRun> runs = em.createQuery("""
                        select r from FixedAssetDepreciationRun r
                        join fetch r.fixedAsset asset
                        join fetch asset.fund
                        join fetch asset.assetAccount
                        join fetch r.transaction txn
                        where asset.id in :ids
                          and r.runDate >= :start and r.runDate <= :end
                        order by r.runDate, r.id
                        """, FixedAssetDepreciationRun.class)
                        .setParameter("ids", ids)
                        .setParameter("start", request.startDate())
                        .setParameter("end", request.endDate())
                        .getResultList();
                for (FixedAssetDepreciationRun run : runs)
                {
                    FixedAsset asset = run.getFixedAsset();
                    if (!matchesStatus(asset, lifecycle.getOrDefault(asset.getId(), List.of()),
                            request.endDate(), request.status()))
                    {
                        continue;
                    }
                    rows.add(new DepreciationReportRow(
                            "Completed depreciation",
                            run.getRunDate(),
                            asset.getId(),
                            asset.getName(),
                            asset.getAssetAccount().getCode(),
                            asset.getFund().getCode(),
                            scale(run.getDepreciationAmount()),
                            null,
                            null,
                            run.getTransaction().getId(),
                            run.getNotes() == null ? "" : run.getNotes()));
                }

                List<FixedAssetLifecycleEvent> impairments = em.createQuery("""
                        select e from FixedAssetLifecycleEvent e
                        join fetch e.fixedAsset asset
                        join fetch asset.fund
                        join fetch asset.assetAccount
                        join fetch e.transaction txn
                        left join fetch e.reversalTransaction reversal
                        where asset.id in :ids
                          and e.eventType = :type
                          and ((e.eventDate >= :start and e.eventDate <= :end)
                            or (reversal.txnDate >= :start and reversal.txnDate <= :end))
                        order by e.eventDate, e.id
                        """, FixedAssetLifecycleEvent.class)
                        .setParameter("ids", ids)
                        .setParameter("type", FixedAssetLifecycleEvent.EventType.IMPAIRMENT)
                        .setParameter("start", request.startDate())
                        .setParameter("end", request.endDate())
                        .getResultList();
                for (FixedAssetLifecycleEvent event : impairments)
                {
                    FixedAsset asset = event.getFixedAsset();
                    if (!matchesStatus(asset, lifecycle.getOrDefault(asset.getId(), List.of()),
                            request.endDate(), request.status()))
                    {
                        continue;
                    }
                    if (!event.getEventDate().isBefore(request.startDate())
                            && !event.getEventDate().isAfter(request.endDate()))
                    {
                        boolean reversedByEnd = event.isReversed()
                                && !event.getReversalTransaction().getTxnDate()
                                        .isAfter(request.endDate());
                        String note = reversedByEnd
                                ? "Reversed by transaction " + event.getReversalTransaction().getId()
                                : event.getNotes();
                        rows.add(new DepreciationReportRow(
                                "Impairment",
                                event.getEventDate(),
                                asset.getId(),
                                asset.getName(),
                                asset.getAssetAccount().getCode(),
                                asset.getFund().getCode(),
                                scale(event.getImpairmentAmount()),
                                null,
                                null,
                                event.getTransaction().getId(),
                                note == null ? "" : note));
                    }
                    if (event.isReversed()
                            && !event.getReversalTransaction().getTxnDate().isBefore(request.startDate())
                            && !event.getReversalTransaction().getTxnDate().isAfter(request.endDate()))
                    {
                        rows.add(new DepreciationReportRow(
                                "Impairment reversal",
                                event.getReversalTransaction().getTxnDate(),
                                asset.getId(),
                                asset.getName(),
                                asset.getAssetAccount().getCode(),
                                asset.getFund().getCode(),
                                scale(event.getImpairmentAmount()).negate(),
                                null,
                                null,
                                event.getReversalTransaction().getId(),
                                "Reverses impairment transaction " + event.getTransaction().getId()));
                    }
                }
            }

            for (FixedAsset asset : assets)
            {
                List<LifecycleFact> facts = lifecycle.getOrDefault(asset.getId(), List.of());
                FixedAssetAsOf asOf = assetAsOf(
                        asset,
                        request.endDate(),
                        throughEnd.getOrDefault(asset.getId(), ZERO),
                        facts);
                if (request.status() != null && asOf.status() != request.status())
                {
                    continue;
                }
                if (reconciledAssets < request.rowLimit())
                {
                    domainContra = domainContra.add(asOf.recognizedContra());
                    reconciledAssets++;
                }
                BigDecimal depreciable = scale(asset.getAcquisitionCost())
                        .subtract(scale(asset.getSalvageValue())).max(ZERO);
                BigDecimal remaining = asOf.status() == FixedAsset.Status.DISPOSED
                        ? ZERO
                        : depreciable.subtract(asOf.accumulatedDepreciation())
                                .subtract(asOf.impairment()).max(ZERO);
                BigDecimal monthly = asset.getUsefulLifeMonths() <= 0
                        ? ZERO
                        : depreciable.divide(BigDecimal.valueOf(asset.getUsefulLifeMonths()), 4,
                                RoundingMode.HALF_UP);
                int periods = monthly.signum() == 0
                        ? 0
                        : remaining.divide(monthly, 0, RoundingMode.CEILING).intValue();
                BigDecimal nextAmount = asOf.status() == FixedAsset.Status.ACTIVE
                        ? monthly.min(remaining)
                        : ZERO;
                String scheduleNote = asOf.status() == FixedAsset.Status.ACTIVE
                        ? "Projection only; no future transaction has been created."
                        : "No next run while asset status is " + asOf.status()
                                + "; remaining basis is retained.";
                rows.add(new DepreciationReportRow(
                        "Schedule summary",
                        request.endDate(),
                        asset.getId(),
                        asset.getName(),
                        asset.getAssetAccount().getCode(),
                        asset.getFund().getCode(),
                        nextAmount,
                        remaining,
                        periods,
                        null,
                        scheduleNote));
            }

            rows.sort(Comparator.comparing(DepreciationReportRow::date)
                    .thenComparing(DepreciationReportRow::assetName)
                    .thenComparing(DepreciationReportRow::rowType));
            boolean truncated = rows.size() > request.rowLimit();
            List<DepreciationReportRow> limited = rows.stream().limit(request.rowLimit()).toList();
            Set<Long> contraAccounts = assets.stream()
                    .map(asset -> asset.getAccumulatedDepreciationAccount().getId())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            LedgerBalance ledger = ledgerBalance(
                    em, companyCode, contraAccounts, request.endDate(), request.fundId());
            return new FixedAssetDepreciationResult(
                    limited,
                    domainContra,
                    ledger.balance(),
                    domainContra.subtract(ledger.balance()),
                    truncated,
                    reconciliationExplanation(
                            request.filtered(), ledger.openingBalanceExcluded(),
                            "fixed-asset depreciation"));
        }
    }

    public InventoryValuationResult inventoryValuation(InventoryReportRequest request)
    {
        Objects.requireNonNull(request, "request");
        String companyCode = requireCompanyCode();
        try (EntityManager em = jpa.em())
        {
            List<InventoryItem> items = loadInventoryItems(em, companyCode, request);
            List<Long> ids = items.stream().map(InventoryItem::getId).toList();
            Map<Long, InventoryPosition> positions = inventoryPositions(em, ids, request.endDate());
            Set<Long> accounts = items.stream()
                    .filter(item -> request.status() == null || item.getStatus() == request.status())
                    .map(item -> item.getInventoryAccount().getId())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            List<InventoryValuationRow> rows = new ArrayList<>();
            for (InventoryItem item : items)
            {
                if (request.status() != null && item.getStatus() != request.status())
                {
                    continue;
                }
                InventoryPosition position = positions.get(item.getId());
                BigDecimal quantity = scale(position == null ? item.getQuantity() : position.quantity());
                BigDecimal unitValue = scale(position == null ? item.getUnitValue() : position.unitValue());
                rows.add(new InventoryValuationRow(
                        item.getId(),
                        item.getName(),
                        item.getItemType(),
                        item.getStatus(),
                        item.getInventoryAccount().getId(),
                        item.getInventoryAccount().getCode(),
                        item.getInventoryAccount().getName(),
                        item.getFund().getId(),
                        item.getFund().getCode(),
                        quantity,
                        item.getUnit(),
                        unitValue,
                        scale(quantity.multiply(unitValue)),
                        position == null ? null : position.movementId(),
                        position == null ? null : position.transactionId()));
                if (rows.size() >= request.rowLimit())
                {
                    break;
                }
            }
            BigDecimal domainValue = rows.stream()
                    .map(InventoryValuationRow::totalValue)
                    .reduce(ZERO, BigDecimal::add);
            LedgerBalance ledger = ledgerBalance(
                    em, companyCode, accounts, request.endDate(), request.fundId());
            BigDecimal unlinked = unlinkedInventoryMovementNetThrough(
                    em, rows.stream().map(InventoryValuationRow::itemId).toList(),
                    request.endDate());
            return new InventoryValuationResult(
                    rows,
                    domainValue,
                    ledger.balance(),
                    domainValue.subtract(ledger.balance()),
                    unlinked,
                    ledger.openingBalanceExcluded(),
                    reconciliationExplanation(
                            request.filtered(), ledger.openingBalanceExcluded(),
                            "inventory valuation"));
        }
    }

    public InventoryMovementResult inventoryMovementHistory(InventoryReportRequest request)
    {
        Objects.requireNonNull(request, "request");
        String companyCode = requireCompanyCode();
        try (EntityManager em = jpa.em())
        {
            Set<Long> accounts = loadInventoryItems(em, companyCode, request).stream()
                    .filter(item -> request.status() == null || item.getStatus() == request.status())
                    .map(item -> item.getInventoryAccount().getId())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            List<InventoryMovement> source = em.createQuery("""
                    select m from InventoryMovement m
                    join fetch m.inventoryItem item
                    join fetch item.company company
                    join fetch item.inventoryAccount account
                    join fetch item.fund fund
                    left join fetch m.transaction txn
                    where company.code = :companyCode
                      and m.movementDate >= :start and m.movementDate <= :end
                      and (:fundId is null or fund.id = :fundId)
                      and (:accountId is null or account.id = :accountId)
                      and (:itemId is null or item.id = :itemId)
                      and (:status is null or item.status = :status)
                    order by m.movementDate, m.id
                    """, InventoryMovement.class)
                    .setParameter("companyCode", companyCode)
                    .setParameter("start", request.startDate())
                    .setParameter("end", request.endDate())
                    .setParameter("fundId", request.fundId())
                    .setParameter("accountId", request.accountId())
                    .setParameter("itemId", request.itemId())
                    .setParameter("status", request.status())
                    .setMaxResults(request.rowLimit())
                    .getResultList();

            List<InventoryMovementReportRow> rows = new ArrayList<>();
            BigDecimal domainNet = ZERO;
            BigDecimal unlinkedNet = ZERO;
            for (InventoryMovement movement : source)
            {
                InventoryItem item = movement.getInventoryItem();
                BigDecimal signedValue = scale(movement.getQuantityChange()
                        .multiply(movement.getUnitValue()));
                domainNet = domainNet.add(signedValue);
                if (movement.getTransaction() == null)
                {
                    unlinkedNet = unlinkedNet.add(signedValue);
                }
                rows.add(new InventoryMovementReportRow(
                        movement.getId(),
                        movement.getMovementDate(),
                        movement.getMovementType(),
                        item.getId(),
                        item.getName(),
                        item.getInventoryAccount().getCode(),
                        item.getFund().getCode(),
                        scale(movement.getQuantityChange()),
                        scale(movement.getResultingQuantity()),
                        scale(movement.getUnitValue()),
                        signedValue,
                        movement.getTransaction() == null ? null : movement.getTransaction().getId(),
                        movement.getTransaction() == null ? "Nonfinancial / no canonical transaction"
                                : "Canonical transaction linked",
                        movement.getNotes() == null ? "" : movement.getNotes()));
            }
            BigDecimal ledgerActivity = ledgerActivity(
                    em, companyCode, accounts, request.startDate(), request.endDate(), request.fundId());
            return new InventoryMovementResult(
                    rows,
                    domainNet,
                    ledgerActivity,
                    domainNet.subtract(ledgerActivity),
                    unlinkedNet,
                    reconciliationExplanation(request.filtered(), ZERO, "inventory movement history"));
        }
    }

    private List<FixedAsset> loadAssets(
            EntityManager em,
            String companyCode,
            FixedAssetReportRequest request)
    {
        return em.createQuery("""
                select asset from FixedAsset asset
                join fetch asset.company company
                join fetch asset.assetAccount assetAccount
                join fetch asset.accumulatedDepreciationAccount accumulatedAccount
                join fetch asset.depreciationExpenseAccount expenseAccount
                join fetch asset.fund fund
                where company.code = :companyCode
                  and asset.acquisitionDate <= :end
                  and (:fundId is null or fund.id = :fundId)
                  and (:accountId is null or assetAccount.id = :accountId)
                  and (:assetId is null or asset.id = :assetId)
                order by asset.name, asset.id
                """, FixedAsset.class)
                .setParameter("companyCode", companyCode)
                .setParameter("end", request.endDate())
                .setParameter("fundId", request.fundId())
                .setParameter("accountId", request.accountId())
                .setParameter("assetId", request.assetId())
                .getResultList();
    }

    private List<InventoryItem> loadInventoryItems(
            EntityManager em,
            String companyCode,
            InventoryReportRequest request)
    {
        return em.createQuery("""
                select item from InventoryItem item
                join fetch item.company company
                join fetch item.inventoryAccount account
                join fetch item.fund fund
                where company.code = :companyCode
                  and item.acquisitionDate <= :end
                  and (:fundId is null or fund.id = :fundId)
                  and (:accountId is null or account.id = :accountId)
                  and (:itemId is null or item.id = :itemId)
                order by item.name, item.id
                """, InventoryItem.class)
                .setParameter("companyCode", companyCode)
                .setParameter("end", request.endDate())
                .setParameter("fundId", request.fundId())
                .setParameter("accountId", request.accountId())
                .setParameter("itemId", request.itemId())
                .getResultList();
    }

    private static Map<Long, BigDecimal> depreciationThrough(
            EntityManager em,
            List<Long> assetIds,
            LocalDate asOf)
    {
        Map<Long, BigDecimal> out = new HashMap<>();
        if (assetIds.isEmpty())
        {
            return out;
        }
        List<Object[]> rows = em.createQuery("""
                select r.fixedAsset.id, coalesce(sum(r.depreciationAmount), 0)
                from FixedAssetDepreciationRun r
                where r.fixedAsset.id in :ids and r.runDate <= :asOf
                group by r.fixedAsset.id
                """, Object[].class)
                .setParameter("ids", assetIds)
                .setParameter("asOf", asOf)
                .getResultList();
        for (Object[] row : rows)
        {
            out.put((Long) row[0], scale((BigDecimal) row[1]));
        }
        return out;
    }

    private static Map<Long, List<LifecycleFact>> lifecycleFacts(
            EntityManager em,
            List<Long> assetIds)
    {
        Map<Long, List<LifecycleFact>> out = new HashMap<>();
        if (assetIds.isEmpty())
        {
            return out;
        }
        List<Object[]> rows = em.createQuery("""
                select e.fixedAsset.id, e.eventType, e.eventDate, e.impairmentAmount,
                       e.assetStatusBefore, e.assetStatusAfter, reversal.txnDate
                from FixedAssetLifecycleEvent e
                left join e.reversalTransaction reversal
                where e.fixedAsset.id in :ids
                order by e.eventDate, e.id
                """, Object[].class)
                .setParameter("ids", assetIds)
                .getResultList();
        for (Object[] row : rows)
        {
            LifecycleFact fact = new LifecycleFact(
                    (FixedAssetLifecycleEvent.EventType) row[1],
                    (LocalDate) row[2],
                    scale((BigDecimal) row[3]),
                    (FixedAsset.Status) row[4],
                    (FixedAsset.Status) row[5],
                    (LocalDate) row[6]);
            out.computeIfAbsent((Long) row[0], ignored -> new ArrayList<>()).add(fact);
        }
        return out;
    }

    private static FixedAssetAsOf assetAsOf(
            FixedAsset asset,
            LocalDate asOf,
            BigDecimal completedDepreciation,
            List<LifecycleFact> facts)
    {
        BigDecimal impairment = ZERO;
        FixedAsset.Status status = asset.getStatus();
        for (LifecycleFact fact : facts)
        {
            if (fact.eventDate().isAfter(asOf))
            {
                if (status == FixedAsset.Status.DISPOSED
                        && (fact.type() == FixedAssetLifecycleEvent.EventType.SALE
                        || fact.type() == FixedAssetLifecycleEvent.EventType.RETIREMENT))
                {
                    status = fact.statusBefore();
                }
                continue;
            }
            boolean reversedByAsOf = fact.reversalDate() != null
                    && !fact.reversalDate().isAfter(asOf);
            if (fact.type() == FixedAssetLifecycleEvent.EventType.IMPAIRMENT && !reversedByAsOf)
            {
                impairment = impairment.add(fact.impairment());
            }
            if (fact.type() != FixedAssetLifecycleEvent.EventType.IMPAIRMENT)
            {
                status = reversedByAsOf ? fact.statusBefore() : fact.statusAfter();
            }
        }
        BigDecimal accumulated = scale(asset.getOpeningAccumulatedDepreciation())
                .add(scale(completedDepreciation));
        boolean recognized = status != FixedAsset.Status.DISPOSED;
        BigDecimal recognizedCost = recognized ? scale(asset.getAcquisitionCost()) : ZERO;
        BigDecimal recognizedContra = recognized ? accumulated.add(impairment) : ZERO;
        BigDecimal book = recognizedCost.subtract(recognizedContra).max(ZERO);
        return new FixedAssetAsOf(
                status, recognizedCost, accumulated, impairment, recognizedContra, book);
    }

    private static boolean matchesStatus(
            FixedAsset asset,
            List<LifecycleFact> facts,
            LocalDate asOf,
            FixedAsset.Status selected)
    {
        return selected == null
                || assetAsOf(asset, asOf, ZERO, facts).status() == selected;
    }

    private static Map<Long, InventoryPosition> inventoryPositions(
            EntityManager em,
            List<Long> itemIds,
            LocalDate asOf)
    {
        Map<Long, InventoryPosition> out = new LinkedHashMap<>();
        if (itemIds.isEmpty())
        {
            return out;
        }
        List<InventoryMovement> through = em.createQuery("""
                select movement from InventoryMovement movement
                join fetch movement.inventoryItem item
                left join fetch movement.transaction
                where item.id in :ids and movement.movementDate <= :asOf
                order by item.id, movement.movementDate desc, movement.id desc
                """, InventoryMovement.class)
                .setParameter("ids", itemIds)
                .setParameter("asOf", asOf)
                .getResultList();
        for (InventoryMovement movement : through)
        {
            out.putIfAbsent(movement.getInventoryItem().getId(), new InventoryPosition(
                    movement.getResultingQuantity(),
                    movement.getUnitValue(),
                    movement.getId(),
                    movement.getTransaction() == null ? null : movement.getTransaction().getId()));
        }
        List<Long> missing = itemIds.stream().filter(id -> !out.containsKey(id)).toList();
        if (!missing.isEmpty())
        {
            List<InventoryMovement> future = em.createQuery("""
                    select movement from InventoryMovement movement
                    join fetch movement.inventoryItem item
                    left join fetch movement.transaction
                    where item.id in :ids and movement.movementDate > :asOf
                    order by item.id, movement.movementDate, movement.id
                    """, InventoryMovement.class)
                    .setParameter("ids", missing)
                    .setParameter("asOf", asOf)
                    .getResultList();
            for (InventoryMovement movement : future)
            {
                out.putIfAbsent(movement.getInventoryItem().getId(), new InventoryPosition(
                        movement.getResultingQuantity().subtract(movement.getQuantityChange()),
                        movement.getUnitValue(),
                        null,
                        null));
            }
        }
        return out;
    }

    private static LedgerControl ledgerControl(
            EntityManager em,
            String companyCode,
            Set<Long> grossAccounts,
            Set<Long> contraAccounts,
            LocalDate asOf,
            Long fundId)
    {
        LedgerBalance gross = ledgerBalance(em, companyCode, grossAccounts, asOf, fundId);
        LedgerBalance contra = ledgerBalance(em, companyCode, contraAccounts, asOf, fundId);
        return new LedgerControl(
                gross.balance(),
                contra.balance(),
                gross.balance().subtract(contra.balance()),
                gross.openingBalanceExcluded().subtract(contra.openingBalanceExcluded()));
    }

    private static LedgerBalance ledgerBalance(
            EntityManager em,
            String companyCode,
            Set<Long> accountIds,
            LocalDate asOf,
            Long fundId)
    {
        if (accountIds.isEmpty())
        {
            return new LedgerBalance(ZERO, ZERO);
        }
        BigDecimal activity = em.createQuery("""
                select coalesce(sum(split.amountSigned), 0)
                from TxnSplit split
                join split.txn txn
                join split.account account
                join split.fund fund
                where txn.company.code = :companyCode
                  and txn.txnDate <= :asOf
                  and account.id in :accountIds
                  and (:fundId is null or fund.id = :fundId)
                """, BigDecimal.class)
                .setParameter("companyCode", companyCode)
                .setParameter("asOf", asOf)
                .setParameter("accountIds", accountIds)
                .setParameter("fundId", fundId)
                .getSingleResult();
        BigDecimal opening = em.createQuery("""
                select coalesce(sum(account.openingBalance), 0)
                from Account account
                where account.id in :accountIds
                  and account.chart.company.code = :companyCode
                """, BigDecimal.class)
                .setParameter("accountIds", accountIds)
                .setParameter("companyCode", companyCode)
                .getSingleResult();
        if (fundId == null)
        {
            return new LedgerBalance(scale(activity).add(scale(opening)), ZERO);
        }
        return new LedgerBalance(scale(activity), scale(opening));
    }

    private static BigDecimal ledgerActivity(
            EntityManager em,
            String companyCode,
            Set<Long> accountIds,
            LocalDate start,
            LocalDate end,
            Long fundId)
    {
        if (accountIds.isEmpty())
        {
            return ZERO;
        }
        return scale(em.createQuery("""
                select coalesce(sum(split.amountSigned), 0)
                from TxnSplit split
                join split.txn txn
                join split.account account
                join split.fund fund
                where txn.company.code = :companyCode
                  and txn.txnDate >= :start and txn.txnDate <= :end
                  and account.id in :accountIds
                  and (:fundId is null or fund.id = :fundId)
                """, BigDecimal.class)
                .setParameter("companyCode", companyCode)
                .setParameter("start", start)
                .setParameter("end", end)
                .setParameter("accountIds", accountIds)
                .setParameter("fundId", fundId)
                .getSingleResult());
    }

    private static BigDecimal unlinkedInventoryMovementNet(
            EntityManager em,
            List<Long> itemIds,
            LocalDate start,
            LocalDate end)
    {
        if (itemIds.isEmpty())
        {
            return ZERO;
        }
        List<Object[]> rows = em.createQuery("""
                select movement.quantityChange, movement.unitValue
                from InventoryMovement movement
                where movement.inventoryItem.id in :itemIds
                  and movement.transaction is null
                  and movement.movementDate >= :start and movement.movementDate <= :end
                """, Object[].class)
                .setParameter("itemIds", itemIds)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
        BigDecimal value = ZERO;
        for (Object[] row : rows)
        {
            value = value.add(scale(((BigDecimal) row[0]).multiply((BigDecimal) row[1])));
        }
        return value;
    }

    private static BigDecimal unlinkedInventoryMovementNetThrough(
            EntityManager em,
            List<Long> itemIds,
            LocalDate end)
    {
        if (itemIds.isEmpty())
        {
            return ZERO;
        }
        List<Object[]> rows = em.createQuery("""
                select movement.quantityChange, movement.unitValue
                from InventoryMovement movement
                where movement.inventoryItem.id in :itemIds
                  and movement.transaction is null
                  and movement.movementDate <= :end
                """, Object[].class)
                .setParameter("itemIds", itemIds)
                .setParameter("end", end)
                .getResultList();
        BigDecimal value = ZERO;
        for (Object[] row : rows)
        {
            value = value.add(scale(((BigDecimal) row[0]).multiply((BigDecimal) row[1])));
        }
        return value;
    }

    private String requireCompanyCode()
    {
        String value = companyCodeSupplier.get();
        if (value == null || value.isBlank())
        {
            throw new IllegalStateException("An active company is required to run this report.");
        }
        return value.strip();
    }

    private static String reconciliationExplanation(
            boolean filtered,
            BigDecimal openingExcluded,
            String reportName)
    {
        StringBuilder text = new StringBuilder(
                "Difference equals the displayed domain total minus the canonical control-account total.");
        if (filtered)
        {
            text.append(" Account, fund, status, or item/asset filters can select fewer domain records than a shared control account contains.");
        }
        if (openingExcluded.signum() != 0)
        {
            text.append(" Fund-scoped ledger totals exclude ")
                    .append(openingExcluded.toPlainString())
                    .append(" of account opening balance because opening balances have no fund dimension.");
        }
        text.append(" Any remaining ").append(reportName)
                .append(" difference is retained visibly; row limits may also reduce displayed domain records, and the report does not infer missing transactions.");
        return text.toString();
    }

    private static BigDecimal scale(BigDecimal value)
    {
        return (value == null ? ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    public record FilterOption(Long id, String label)
    {
        public FilterOption
        {
            if (id == null || id <= 0 || label == null || label.isBlank())
            {
                throw new IllegalArgumentException("A persisted filter option and label are required.");
            }
            label = label.strip();
        }
    }

    public record FilterCatalog(
            List<FilterOption> assets,
            List<FilterOption> assetAccounts,
            List<FilterOption> inventoryItems,
            List<FilterOption> inventoryAccounts)
    {
        public FilterCatalog
        {
            assets = List.copyOf(assets);
            assetAccounts = List.copyOf(assetAccounts);
            inventoryItems = List.copyOf(inventoryItems);
            inventoryAccounts = List.copyOf(inventoryAccounts);
        }
    }

    public record FixedAssetReportRequest(
            LocalDate startDate,
            LocalDate endDate,
            Long fundId,
            Long accountId,
            Long assetId,
            FixedAsset.Status status,
            int rowLimit)
    {
        public FixedAssetReportRequest
        {
            startDate = Objects.requireNonNull(startDate, "startDate");
            endDate = Objects.requireNonNull(endDate, "endDate");
            if (endDate.isBefore(startDate) || rowLimit < 1 || rowLimit > ReportRequest.MAX_ROW_LIMIT)
            {
                throw new IllegalArgumentException("Invalid fixed-asset report date range or row limit.");
            }
        }

        public boolean filtered()
        {
            return fundId != null || accountId != null || assetId != null || status != null;
        }

    }

    public record InventoryReportRequest(
            LocalDate startDate,
            LocalDate endDate,
            Long fundId,
            Long accountId,
            Long itemId,
            InventoryItem.Status status,
            int rowLimit)
    {
        public InventoryReportRequest
        {
            startDate = Objects.requireNonNull(startDate, "startDate");
            endDate = Objects.requireNonNull(endDate, "endDate");
            if (endDate.isBefore(startDate) || rowLimit < 1 || rowLimit > ReportRequest.MAX_ROW_LIMIT)
            {
                throw new IllegalArgumentException("Invalid inventory report date range or row limit.");
            }
        }

        public boolean filtered()
        {
            return fundId != null || accountId != null || itemId != null || status != null;
        }
    }

    public record FixedAssetRegisterRow(
            Long assetId,
            String assetName,
            FixedAsset.Status status,
            LocalDate acquisitionDate,
            Long assetAccountId,
            String assetAccountCode,
            String assetAccountName,
            Long accumulatedAccountId,
            String accumulatedAccountCode,
            Long fundId,
            String fundCode,
            BigDecimal acquisitionCost,
            BigDecimal recognizedCost,
            BigDecimal accumulatedDepreciation,
            BigDecimal impairment,
            BigDecimal recognizedContra,
            BigDecimal bookValue)
    {
    }

    public record FixedAssetRegisterResult(
            List<FixedAssetRegisterRow> rows,
            BigDecimal domainGross,
            BigDecimal domainContra,
            BigDecimal domainNet,
            BigDecimal ledgerGross,
            BigDecimal ledgerContra,
            BigDecimal ledgerNet,
            BigDecimal difference,
            BigDecimal openingBalanceExcluded,
            String explanation)
    {
        public FixedAssetRegisterResult
        {
            rows = List.copyOf(rows);
        }
    }

    public record DepreciationReportRow(
            String rowType,
            LocalDate date,
            Long assetId,
            String assetName,
            String accountCode,
            String fundCode,
            BigDecimal amount,
            BigDecimal remainingDepreciable,
            Integer remainingPeriods,
            Long transactionId,
            String notes)
    {
    }

    public record FixedAssetDepreciationResult(
            List<DepreciationReportRow> rows,
            BigDecimal domainContra,
            BigDecimal ledgerContra,
            BigDecimal difference,
            boolean truncated,
            String explanation)
    {
        public FixedAssetDepreciationResult
        {
            rows = List.copyOf(rows);
        }
    }

    public record InventoryValuationRow(
            Long itemId,
            String itemName,
            String itemType,
            InventoryItem.Status status,
            Long accountId,
            String accountCode,
            String accountName,
            Long fundId,
            String fundCode,
            BigDecimal quantity,
            String unit,
            BigDecimal unitValue,
            BigDecimal totalValue,
            Long latestMovementId,
            Long transactionId)
    {
    }

    public record InventoryValuationResult(
            List<InventoryValuationRow> rows,
            BigDecimal domainValue,
            BigDecimal ledgerValue,
            BigDecimal difference,
            BigDecimal unlinkedMovementNet,
            BigDecimal openingBalanceExcluded,
            String explanation)
    {
        public InventoryValuationResult
        {
            rows = List.copyOf(rows);
        }
    }

    public record InventoryMovementReportRow(
            Long movementId,
            LocalDate movementDate,
            InventoryMovement.MovementType movementType,
            Long itemId,
            String itemName,
            String accountCode,
            String fundCode,
            BigDecimal quantityChange,
            BigDecimal resultingQuantity,
            BigDecimal unitValue,
            BigDecimal signedValue,
            Long transactionId,
            String accountingState,
            String notes)
    {
    }

    public record InventoryMovementResult(
            List<InventoryMovementReportRow> rows,
            BigDecimal domainNet,
            BigDecimal ledgerActivity,
            BigDecimal difference,
            BigDecimal unlinkedMovementNet,
            String explanation)
    {
        public InventoryMovementResult
        {
            rows = List.copyOf(rows);
        }
    }

    private record LifecycleFact(
            FixedAssetLifecycleEvent.EventType type,
            LocalDate eventDate,
            BigDecimal impairment,
            FixedAsset.Status statusBefore,
            FixedAsset.Status statusAfter,
            LocalDate reversalDate)
    {
    }

    private record FixedAssetAsOf(
            FixedAsset.Status status,
            BigDecimal recognizedCost,
            BigDecimal accumulatedDepreciation,
            BigDecimal impairment,
            BigDecimal recognizedContra,
            BigDecimal bookValue)
    {
    }

    private record LedgerBalance(BigDecimal balance, BigDecimal openingBalanceExcluded)
    {
    }

    private record LedgerControl(
            BigDecimal gross,
            BigDecimal contra,
            BigDecimal net,
            BigDecimal openingBalanceExcluded)
    {
    }

    private record InventoryPosition(
            BigDecimal quantity,
            BigDecimal unitValue,
            Long movementId,
            Long transactionId)
    {
    }
}
