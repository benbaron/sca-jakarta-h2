package org.nonprofitbookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/** Acquisition implementation owned by FixedAssetService; canonical amounts remain on TxnSplit. */
final class FixedAssetAcquisitions
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ACTION = "FIXED_ASSET_ACQUISITION_ACCEPTED";
    private final Jpa jpa;
    private final FixedAssetService owner;
    private final TransactionEntryService entries;
    private final Supplier<String> company;
    private final AuthorizationGuard guard;

    FixedAssetAcquisitions(Jpa jpa, FixedAssetService owner, TransactionEntryService entries,
                           Supplier<String> company, AuthorizationGuard guard)
    {
        this.jpa = jpa;
        this.owner = owner;
        this.entries = entries;
        this.company = company;
        this.guard = guard;
    }

    AssetAcquisitionPreview preview(AssetAcquisitionCommand input, UUID request)
    {
        Objects.requireNonNull(request, "Acquisition request identity is required");
        var command = normalize(input);
        try (EntityManager em = jpa.em())
        {
            Company selected = selectedCompany(em, command);
            FixedAsset accepted = replay(em, selected, request, fingerprint(selected, command));
            if (accepted != null)
            {
                return acceptedPreview(request, command, accepted);
            }
            return build(em, selected, command, request);
        }
    }

    FixedAssetView accept(AssetAcquisitionPreview preview)
    {
        String actor = ServiceAuthorization.actor(guard, ApplicationPermission.BOOKKEEPING_WRITE,
                company.get(), "accept fixed-asset acquisition", "system");
        Objects.requireNonNull(preview, "Acquisition preview is required");
        Objects.requireNonNull(preview.requestId(), "Acquisition request identity is required");
        var command = normalize(preview.command());
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company selected = selectedCompany(em, command);
                SupplementalIntegrityService.lock(em, selected);
                String fingerprint = fingerprint(selected, command);
                FixedAsset accepted = replay(em, selected, preview.requestId(), fingerprint);
                if (accepted != null)
                {
                    FixedAssetView result = owner.toView(em, accepted);
                    em.getTransaction().commit();
                    return result;
                }
                if (command.existingAssetId() != null)
                {
                    ownedAsset(em, selected, command.existingAssetId(), true);
                }
                AssetAcquisitionPreview rebuilt = build(em, selected, command, preview.requestId());
                if (!rebuilt.equals(preview))
                {
                    throw new IllegalStateException("Acquisition preview changed; review a fresh preview before saving.");
                }
                if (em.createQuery("select count(a) from FixedAsset a where a.portableId = :id", Long.class)
                        .setParameter("id", preview.requestId()).getSingleResult() > 0)
                {
                    throw new IllegalStateException("Acquisition identity already belongs to a retained asset.");
                }
                FixedAsset asset;
                String before = null;
                if (command.existingAssetId() == null)
                {
                    Instant now = Instant.now();
                    asset = owner.createForImport(em, selected, command.asset(), preview.requestId(), now, now);
                }
                else
                {
                    asset = ownedAsset(em, selected, command.existingAssetId(), true);
                    before = snapshot(asset);
                }
                TxnSplit split = null;
                if (command.source() == AssetAcquisitionSource.NEW_PURCHASE)
                {
                    UUID transactionId = UUID.nameUUIDFromBytes((preview.requestId() + ":asset-purchase-v1")
                            .getBytes(StandardCharsets.UTF_8));
                    Txn transaction = entries.enter(em, selected, rebuilt.transaction(), transactionId, actor,
                            "Fixed-asset bank acquisition");
                    split = em.createQuery("from TxnSplit s where s.txn = :txn and s.account.id = :account", TxnSplit.class)
                            .setParameter("txn", transaction).setParameter("account", asset.getAssetAccount().getId())
                            .getSingleResult();
                }
                else if (command.source() == AssetAcquisitionSource.EXISTING_ENTRY)
                {
                    split = em.find(TxnSplit.class, command.splitId());
                }
                asset.setAcquisitionSource(command.source());
                asset.setAcquisitionSplit(split);
                asset.touchUpdatedAt();
                em.flush();
                AuditEvent event = audit(selected, asset, actor, ACTION, before, fingerprint);
                event.initializeOperationIdentity(preview.requestId());
                em.persist(event);
                em.flush();
                FixedAssetView result = owner.toView(em, asset);
                em.getTransaction().commit();
                return result;
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    FixedAssetView removeLink(long assetId, String reason)
    {
        String actor = ServiceAuthorization.actor(guard, ApplicationPermission.BOOKKEEPING_WRITE,
                company.get(), "remove fixed-asset acquisition link", "system");
        if (reason == null || reason.isBlank())
        {
            throw new IllegalArgumentException("A reason for removing the acquisition link is required.");
        }
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company selected = new CompanyOwnershipService(jpa).requireCompany(em, company.get());
                SupplementalIntegrityService.lock(em, selected);
                FixedAsset asset = ownedAsset(em, selected, assetId, true);
                TxnSplit split = asset.getAcquisitionSplit();
                if (split == null)
                {
                    throw new IllegalStateException("This asset has no acquisition link to remove.");
                }
                long runs = em.createQuery("select count(r) from FixedAssetDepreciationRun r where r.fixedAsset = :asset", Long.class)
                        .setParameter("asset", asset).getSingleResult();
                long events = em.createQuery("select count(e) from FixedAssetLifecycleEvent e where e.fixedAsset = :asset", Long.class)
                        .setParameter("asset", asset).getSingleResult();
                if (runs + events > 0)
                {
                    throw new IllegalStateException("Acquisition links supporting depreciation or lifecycle history are retained. Use the asset lifecycle correction workflow.");
                }
                Txn transaction = split.getTxn();
                PeriodCloseRangeService.requireOpen(em, selected.getCode(), transaction.getTxnDate(), "remove asset acquisition link");
                FixedAssetService.requireTransactionOutsideCompletedReconciliation(em, transaction.getId());
                for (TxnSplit line : em.createQuery("from TxnSplit s where s.txn = :txn", TxnSplit.class)
                        .setParameter("txn", transaction).getResultList())
                {
                    FixedAssetService.requireOutsideFinalizedReconciliation(em, selected, line.getAccount(),
                            transaction.getTxnDate(), "remove asset acquisition link");
                }
                String before = snapshot(asset);
                asset.setAcquisitionSplit(null);
                asset.setAcquisitionSource(AssetAcquisitionSource.REGISTER_ONLY);
                asset.touchUpdatedAt();
                em.persist(audit(selected, asset, actor, "FIXED_ASSET_ACQUISITION_UNLINKED", before, reason.trim()));
                em.flush();
                FixedAssetView result = owner.toView(em, asset);
                em.getTransaction().commit();
                return result;
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    List<AssetAcquisitionCandidate> candidates()
    {
        try (EntityManager em = jpa.em())
        {
            Company selected = new CompanyOwnershipService(jpa).requireCompany(em, company.get());
            List<AssetAcquisitionCandidate> result = new ArrayList<>();
            for (TxnSplit split : em.createQuery("""
                    from TxnSplit s join fetch s.txn t join fetch s.account a join fetch s.fund
                    where t.company = :company and t.status = 'ENTERED' and t.reversalOf is null
                      and a.accountType = :type and a.subtype = :subtype and a.normalBalance = :balance
                      and s.amountSigned > 0 order by t.txnDate desc, t.id, s.id
                    """, TxnSplit.class).setParameter("company", selected).setParameter("type", AccountType.ASSET)
                    .setParameter("subtype", AccountSubtype.FIXED_ASSET).setParameter("balance", NormalBalance.DEBIT)
                    .getResultList())
            {
                if (generated(em, split.getTxn().getId()))
                {
                    continue;
                }
                BigDecimal claimed = claimed(em, split);
                BigDecimal remaining = split.getAmountSigned().subtract(claimed);
                if (remaining.signum() > 0)
                {
                    result.add(new AssetAcquisitionCandidate(split.getId(), split.getTxn().getId(), split.getTxn().getTxnDate(),
                            split.getAccount().getId(), split.getFund().getId(), split.getAccount().getCode(),
                            split.getFund().getCode(), split.getAmountSigned(), claimed, remaining,
                            split.getTxn().getMemo() == null ? "" : split.getTxn().getMemo()));
                }
            }
            return List.copyOf(result);
        }
    }

    private AssetAcquisitionPreview build(EntityManager em, Company selected,
                                           AssetAcquisitionCommand command, UUID request)
    {
        if (!selected.isActive())
        {
            throw new IllegalStateException("The active company is inactive.");
        }
        FixedAsset candidate = new FixedAsset();
        owner.apply(em, candidate, command.asset());
        if (command.existingAssetId() == null)
        {
            if (candidate.getStatus() != FixedAsset.Status.ACTIVE)
            {
                throw new IllegalArgumentException("New acquisitions start ACTIVE.");
            }
        }
        else
        {
            FixedAsset existing = ownedAsset(em, selected, command.existingAssetId(), false);
            if (existing.getAcquisitionSplit() != null)
            {
                throw new IllegalStateException("This asset already has a retained acquisition link.");
            }
            if (!assetCommand(existing).equals(command.asset()))
            {
                throw new IllegalStateException("Save register edits before linking its existing accounting.");
            }
        }
        if (command.source() == AssetAcquisitionSource.REGISTER_ONLY)
        {
            return new AssetAcquisitionPreview(request, command, null, label(candidate.getAssetAccount()),
                    "Register only; no accounting will be created", null, null, null, null);
        }
        if (candidate.getAcquisitionCost().signum() <= 0 || candidate.getAssetAccount().getNormalBalance() != NormalBalance.DEBIT)
        {
            throw new IllegalArgumentException("Linked acquisition requires a positive cost and debit-normal FIXED_ASSET account.");
        }
        if (command.source() == AssetAcquisitionSource.EXISTING_ENTRY)
        {
            TxnSplit split = em.find(TxnSplit.class, command.splitId());
            requireLink(em, selected, candidate, split);
            BigDecimal claimed = claimed(em, split);
            return new AssetAcquisitionPreview(request, command, null, label(candidate.getAssetAccount()),
                    "Txn #" + split.getTxn().getId() + " — " + split.getTxn().getMemo(), split.getAmountSigned(), claimed,
                    split.getAmountSigned().subtract(claimed).subtract(candidate.getAcquisitionCost()), null);
        }
        Account bank = em.find(Account.class, command.bankAccountId());
        new CompanyOwnershipService(jpa).ensureOwnedBy(em, selected, bank, "Acquisition BANK account");
        if (bank == null || !AccountClassification.isBankLedgerAccount(bank) || bank.getId().equals(candidate.getAssetAccount().getId()))
        {
            throw new IllegalArgumentException("New purchase requires a distinct BANK/ASSET debit-normal account. Use Journal then Link existing entry for other accounting.");
        }
        usable(candidate.getAssetAccount(), candidate.getAcquisitionDate());
        usable(bank, candidate.getAcquisitionDate());
        Fund fund = candidate.getFund();
        if (!fund.isActive() || (fund.getEffectiveFrom() != null && candidate.getAcquisitionDate().isBefore(fund.getEffectiveFrom()))
                || (fund.getEffectiveTo() != null && candidate.getAcquisitionDate().isAfter(fund.getEffectiveTo())))
        {
            throw new IllegalStateException("Acquisition Fund is inactive or outside its effective dates.");
        }
        if (candidate.getOpeningAccumulatedDepreciation().signum() != 0)
        {
            throw new IllegalArgumentException("New bank purchases cannot invent opening depreciation; use a historical register/link route.");
        }
        PeriodCloseRangeService.requireOpen(em, selected.getCode(), candidate.getAcquisitionDate(), "record asset acquisition");
        FixedAssetService.requireOutsideFinalizedReconciliation(em, selected, bank, candidate.getAcquisitionDate(), "record asset acquisition");
        BigDecimal cost = candidate.getAcquisitionCost();
        var transaction = new TransactionCommand(candidate.getAcquisitionDate(), null, "Asset acquisition: " + candidate.getName(), null,
                List.of(new TransactionLineCommand(candidate.getAssetAccount().getId(), fund.getId(), null, null, null,
                                cost, BigDecimal.ZERO, false, candidate.getNotes()),
                        new TransactionLineCommand(bank.getId(), fund.getId(), null, null, null,
                                BigDecimal.ZERO, cost, false, candidate.getNotes())));
        return new AssetAcquisitionPreview(request, command, transaction, label(candidate.getAssetAccount()), label(bank),
                cost, new BigDecimal("0.0000"), new BigDecimal("0.0000"), null);
    }

    /** Final capacity authority is shared by native acceptance and caller-owned SCLX restore. */
    static void requireLink(EntityManager em, Company company, FixedAsset asset, TxnSplit split)
    {
        if (split == null || !company.getId().equals(split.getTxn().getCompany().getId())
                || !company.getId().equals(asset.getCompany().getId())
                || !split.getAccount().getId().equals(asset.getAssetAccount().getId())
                || !split.getFund().getId().equals(asset.getFund().getId())
                || !split.getTxn().getTxnDate().equals(asset.getAcquisitionDate())
                || split.getAccount().getAccountType() != AccountType.ASSET
                || split.getAccount().getSubtype() != AccountSubtype.FIXED_ASSET
                || split.getAccount().getNormalBalance() != NormalBalance.DEBIT
                || !"ENTERED".equals(split.getTxn().getStatus()) || split.getTxn().getReversalOf() != null
                || split.getAmountSigned().signum() <= 0 || asset.getAcquisitionCost().signum() <= 0
                || generated(em, split.getTxn().getId()))
        {
            throw new IllegalArgumentException("Acquisition must resolve to an ordinary entered FIXED_ASSET debit with matching company, account, Fund and date.");
        }
        BigDecimal used = claimed(em, split);
        if (asset.getAcquisitionSplit() != null && asset.getAcquisitionSplit().getId().equals(split.getId()))
        {
            used = used.subtract(asset.getAcquisitionCost());
        }
        if (used.add(asset.getAcquisitionCost()).compareTo(split.getAmountSigned()) > 0)
        {
            throw new IllegalStateException("Acquisition cost exceeds this debit's remaining unclaimed amount.");
        }
    }

    static void requireBankPurchase(EntityManager em, FixedAsset asset, TxnSplit assetDebit)
    {
        List<TxnSplit> lines = em.createQuery(
                        "from TxnSplit s join fetch s.account where s.txn = :txn", TxnSplit.class)
                .setParameter("txn", assetDebit.getTxn())
                .getResultList();
        if (lines.size() != 2)
        {
            throw new IllegalArgumentException(
                    "A NEW_PURCHASE source requires exactly one asset debit and one BANK credit.");
        }
        TxnSplit bankCredit = lines.stream()
                .filter(line -> !line.getId().equals(assetDebit.getId()))
                .findFirst()
                .orElseThrow();
        if (!AccountClassification.isBankLedgerAccount(bankCredit.getAccount())
                || !bankCredit.getFund().getId().equals(asset.getFund().getId())
                || bankCredit.getAmountSigned().compareTo(asset.getAcquisitionCost().negate()) != 0
                || assetDebit.getAmountSigned().compareTo(asset.getAcquisitionCost()) != 0)
        {
            throw new IllegalArgumentException(
                    "A NEW_PURCHASE source requires an exact same-Fund BANK credit and asset debit.");
        }
    }

    static AssetAcquisitionView view(EntityManager em, FixedAsset asset)
    {
        TxnSplit split = asset.getAcquisitionSplit();
        if (split == null)
        {
            return new AssetAcquisitionView(asset.getAcquisitionSource(), null, null, null, null, null);
        }
        BigDecimal used = claimed(em, split);
        return new AssetAcquisitionView(asset.getAcquisitionSource(), split.getId(), split.getTxn().getId(),
                split.getAmountSigned(), used, split.getAmountSigned().subtract(used));
    }

    static void requireTransactionUnclaimed(EntityManager em, long transactionId)
    {
        if (em.createQuery("select count(a) from FixedAsset a where a.acquisitionSplit.txn.id = :txn", Long.class)
                .setParameter("txn", transactionId).getSingleResult() > 0)
        {
            throw new PostingException("Transaction " + transactionId + " supports fixed-asset acquisition. Remove eligible acquisition links in Asset Register before Journal correction; links supporting later asset activity are retained.");
        }
    }

    static void requireBasisUnchanged(FixedAsset asset, FixedAssetCommand command)
    {
        if (asset.getAcquisitionSplit() != null && (!Objects.equals(asset.getAssetAccount().getId(), command.assetAccountId())
                || !Objects.equals(asset.getFund().getId(), command.fundId())
                || !Objects.equals(asset.getAcquisitionDate(), command.acquisitionDate())
                || asset.getAcquisitionCost().compareTo(command.acquisitionCost()) != 0
                || asset.getOpeningAccumulatedDepreciation().compareTo(command.openingAccumulatedDepreciation()) != 0
                || !Objects.equals(asset.getAccumulatedDepreciationAccount().getId(), command.accumulatedDepreciationAccountId())
                || !Objects.equals(asset.getDepreciationExpenseAccount().getId(), command.depreciationExpenseAccountId())))
        {
            throw new IllegalStateException("Linked acquisition basis is retained. Remove an eligible link before changing acquisition accounts, Fund, date, cost or opening depreciation.");
        }
    }

    private static BigDecimal claimed(EntityManager em, TxnSplit split)
    {
        return em.createQuery("select coalesce(sum(a.acquisitionCost), 0) from FixedAsset a where a.acquisitionSplit = :split", BigDecimal.class)
                .setParameter("split", split).getSingleResult().setScale(4, RoundingMode.UNNECESSARY);
    }

    private static boolean generated(EntityManager em, long transaction)
    {
        Number count = (Number)em.createNativeQuery("""
                select (select count(*) from fixed_asset_depreciation_run where transaction_id = ?1)
                     + (select count(*) from fixed_asset_lifecycle_event where transaction_id = ?1 or reversal_transaction_id = ?1)
                     + (select count(*) from inventory_movement where transaction_id = ?1)
                     + (select count(*) from fund_transfer where posted_txn_id = ?1)
                """).setParameter(1, transaction).getSingleResult();
        return count.longValue() > 0;
    }

    private Company selectedCompany(EntityManager em, AssetAcquisitionCommand command)
    {
        Company selected = new CompanyOwnershipService(jpa).requireCompany(em, company.get());
        if (!selected.getCode().equalsIgnoreCase(command.asset().companyCode()))
        {
            throw new CompanyOwnershipException("Asset acquisition belongs to another company.");
        }
        return selected;
    }

    private static FixedAsset ownedAsset(EntityManager em, Company selected, long id, boolean lock)
    {
        FixedAsset asset = lock ? em.find(FixedAsset.class, id, LockModeType.PESSIMISTIC_WRITE) : em.find(FixedAsset.class, id);
        if (asset == null || !selected.getId().equals(asset.getCompany().getId()))
        {
            throw new CompanyOwnershipException("Asset does not belong to the active company.");
        }
        return asset;
    }

    private static AssetAcquisitionCommand normalize(AssetAcquisitionCommand command)
    {
        Objects.requireNonNull(command, "Acquisition command is required");
        Objects.requireNonNull(command.source(), "Select an acquisition route; no accounting route is inferred");
        FixedAssetService.validateCommand(command.asset());
        FixedAssetCommand a = command.asset();
        var normalized = new FixedAssetCommand(a.companyCode().trim().toUpperCase(Locale.ROOT), a.assetAccountId(),
                a.accumulatedDepreciationAccountId(), a.depreciationExpenseAccountId(), a.fundId(), a.name().trim(),
                a.acquisitionDate(), exact(a.acquisitionCost()), exact(a.salvageValue()), a.usefulLifeMonths(),
                a.depreciationMethod() == null ? FixedAsset.DepreciationMethod.STRAIGHT_LINE : a.depreciationMethod(),
                exact(a.openingAccumulatedDepreciation()), a.status() == null ? FixedAsset.Status.ACTIVE : a.status(), a.notes() == null ? "" : a.notes());
        boolean purchase = command.source() == AssetAcquisitionSource.NEW_PURCHASE;
        boolean link = command.source() == AssetAcquisitionSource.EXISTING_ENTRY;
        if (purchase != (command.bankAccountId() != null) || link != (command.splitId() != null)
                || (command.existingAssetId() != null && purchase))
        {
            throw new IllegalArgumentException("Choose only the BANK or existing debit required by the selected acquisition route.");
        }
        return new AssetAcquisitionCommand(command.existingAssetId(), normalized, command.source(), command.bankAccountId(), command.splitId());
    }

    private static FixedAssetCommand assetCommand(FixedAsset a)
    {
        return new FixedAssetCommand(a.getCompany().getCode(), a.getAssetAccount().getId(), a.getAccumulatedDepreciationAccount().getId(),
                a.getDepreciationExpenseAccount().getId(), a.getFund().getId(), a.getName(), a.getAcquisitionDate(),
                exact(a.getAcquisitionCost()), exact(a.getSalvageValue()), a.getUsefulLifeMonths(), a.getDepreciationMethod(),
                exact(a.getOpeningAccumulatedDepreciation()), a.getStatus(), a.getNotes() == null ? "" : a.getNotes());
    }

    private static BigDecimal exact(BigDecimal amount)
    {
        try
        {
            BigDecimal value = amount.setScale(4, RoundingMode.UNNECESSARY);
            if (value.precision() > 19)
            {
                throw new ArithmeticException("Amount exceeds canonical precision");
            }
            return value;
        }
        catch (ArithmeticException ex)
        {
            throw new IllegalArgumentException("Acquisition amounts require at most four decimals within canonical precision.", ex);
        }
    }

    private static String fingerprint(Company selected, AssetAcquisitionCommand c)
    {
        var a = c.asset();
        var facts = Arrays.asList("asset-acquisition-v1", selected.getId(), c.existingAssetId(), c.source().name(), c.bankAccountId(), c.splitId(),
                Arrays.asList(a.companyCode(), a.assetAccountId(), a.accumulatedDepreciationAccountId(), a.depreciationExpenseAccountId(), a.fundId(),
                        a.name(), a.acquisitionDate().toString(), a.acquisitionCost().toPlainString(), a.salvageValue().toPlainString(),
                        a.usefulLifeMonths(), a.depreciationMethod().name(), a.openingAccumulatedDepreciation().toPlainString(), a.status().name(), a.notes()));
        try
        {
            return "Fixed-asset acquisition v1 SHA-256: " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(facts)));
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("Cannot fingerprint acquisition request", ex);
        }
    }

    private static FixedAsset replay(EntityManager em, Company selected, UUID request, String reason)
    {
        var receipts = em.createQuery("from AuditEvent a where a.portableId = :id", AuditEvent.class).setParameter("id", request).getResultList();
        if (receipts.isEmpty())
        {
            return null;
        }
        AuditEvent event = receipts.get(0);
        if (!ACTION.equals(event.getActionType()) || event.getCompany() == null
                || !selected.getId().equals(event.getCompany().getId()) || !reason.equals(event.getReason()))
        {
            throw new IllegalStateException("Acquisition request was already accepted with different details or belongs to another operation.");
        }
        try
        {
            UUID assetId = UUID.fromString(JSON.readTree(event.getAfterValue()).path("assetId").asText());
            FixedAsset asset = em.createQuery("from FixedAsset a where a.portableId = :id", FixedAsset.class)
                    .setParameter("id", assetId).getSingleResult();
            if (!selected.getId().equals(asset.getCompany().getId()))
            {
                throw new CompanyOwnershipException("Acquisition receipt and retained asset ownership differ.");
            }
            return asset;
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("Accepted acquisition asset cannot be resolved; a retry cannot recreate it.", ex);
        }
    }

    private static AssetAcquisitionPreview acceptedPreview(UUID request, AssetAcquisitionCommand command, FixedAsset asset)
    {
        return new AssetAcquisitionPreview(request, command, null, label(asset.getAssetAccount()),
                "Already accepted; reload retained asset without accounting writes", null, null, null, asset.getId());
    }

    private static AuditEvent audit(Company company, FixedAsset asset, String actor, String action, String before, String reason)
    {
        AuditEvent event = new AuditEvent();
        event.setSummary(action + ": " + asset.getName());
        event.setCompany(company); event.setActor(actor); event.setActionType(action); event.setEntityType("FixedAsset");
        event.setEntityId(asset.getId().toString()); event.setBeforeValue(before); event.setAfterValue(snapshot(asset)); event.setReason(reason);
        return event;
    }

    private static String snapshot(FixedAsset asset)
    {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("assetId", asset.getPortableId().toString()); facts.put("name", asset.getName());
        facts.put("source", asset.getAcquisitionSource() == null ? null : asset.getAcquisitionSource().name());
        facts.put("cost", asset.getAcquisitionCost().toPlainString());
        facts.put("splitId", asset.getAcquisitionSplit() == null ? null : asset.getAcquisitionSplit().getId());
        facts.put("transactionId", asset.getAcquisitionSplit() == null ? null : asset.getAcquisitionSplit().getTxn().getPortableId().toString());
        try
        {
            return JSON.writeValueAsString(facts);
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("Cannot record acquisition audit facts", ex);
        }
    }

    private static void usable(Account account, LocalDate date)
    {
        if (!account.isActive() || !account.isPosting()
                || (account.getEffectiveFrom() != null && date.isBefore(account.getEffectiveFrom()))
                || (account.getEffectiveTo() != null && date.isAfter(account.getEffectiveTo())))
        {
            throw new IllegalStateException("Acquisition account is inactive, nonposting or outside its effective dates.");
        }
    }

    private static String label(Account account)
    {
        return account.getCode() + " — " + account.getName();
    }

    private static void rollback(EntityManager em)
    {
        if (em.getTransaction().isActive())
        {
            em.getTransaction().rollback();
        }
    }

}
