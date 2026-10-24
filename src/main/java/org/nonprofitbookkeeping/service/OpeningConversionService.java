package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.report.AssetInventoryReportQueryService;
import org.nonprofitbookkeeping.report.ReportRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Reviews existing opening authorities and records one immutable conversion acceptance. */
public final class OpeningConversionService
{
    private static final BigDecimal ZERO = new BigDecimal("0.0000");
    private static final String ACTION = "OPENING_CONVERSION_ACCEPTED";
    private static final String ENTITY_TYPE = "OpeningConversion";

    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;
    private final AuthorizationGuard authorizationGuard;
    private final SaveHook saveHook;

    public OpeningConversionService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this(jpa, companyCodeSupplier, null);
    }

    public OpeningConversionService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            AuthorizationGuard authorizationGuard)
    {
        this(jpa, companyCodeSupplier, authorizationGuard, event -> { });
    }

    OpeningConversionService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            AuthorizationGuard authorizationGuard,
            SaveHook saveHook)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.authorizationGuard = authorizationGuard;
        this.saveHook = Objects.requireNonNull(saveHook, "saveHook");
    }

    public Preview preview(LocalDate asOfDate)
    {
        LocalDate cutoff = Objects.requireNonNull(asOfDate, "asOfDate");
        String companyCode = requireCompanyCode();
        BaseFacts base;
        try (EntityManager em = jpa.em())
        {
            Company company = requireCompany(em, companyCode, LockModeType.NONE);
            base = baseFacts(em, company, cutoff);
        }

        AssetInventoryReportQueryService reports =
                new AssetInventoryReportQueryService(jpa, () -> companyCode);
        var assetResult = reports.fixedAssetRegister(
                new AssetInventoryReportQueryService.FixedAssetReportRequest(
                        cutoff, cutoff, null, null, null, null, ReportRequest.MAX_ROW_LIMIT));
        var inventoryResult = reports.inventoryValuation(
                new AssetInventoryReportQueryService.InventoryReportRequest(
                        cutoff, cutoff, null, null, null, null, ReportRequest.MAX_ROW_LIMIT));

        return assemble(cutoff, base, assetResult, inventoryResult);
    }

    public Acceptance saveAcceptance(
            UUID requestId,
            LocalDate asOfDate,
            String expectedFingerprint,
            String note)
    {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(asOfDate, "asOfDate");
        if (expectedFingerprint == null || !expectedFingerprint.matches("[0-9a-f]{64}"))
        {
            throw new IllegalArgumentException("A valid preview fingerprint is required.");
        }
        String fixedNote = note == null ? "" : note.strip();
        if (fixedNote.length() > 1000)
        {
            throw new IllegalArgumentException("Conversion acceptance note must not exceed 1000 characters.");
        }
        String companyCode = requireCompanyCode();
        String actor = ServiceAuthorization.actor(
                authorizationGuard,
                ApplicationPermission.BOOKKEEPING_WRITE,
                companyCode,
                "save opening conversion acceptance",
                "system");

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = requireCompany(em, companyCode, LockModeType.PESSIMISTIC_WRITE);
                Preview current = preview(asOfDate);
                if (!current.fingerprint().equals(expectedFingerprint))
                {
                    throw new IllegalStateException(
                            "Opening conversion preview is stale; preview again before saving.");
                }
                if (!current.ready())
                {
                    throw new IllegalStateException(
                            "Opening conversion has unresolved checks and cannot be accepted.");
                }
                Acceptance existing = acceptance(em, company);
                if (existing != null)
                {
                    if (existing.asOfDate().equals(asOfDate)
                            && existing.fingerprint().equals(expectedFingerprint))
                    {
                        em.getTransaction().commit();
                        return existing;
                    }
                    throw new IllegalStateException(
                            "This company already has an accepted opening conversion snapshot.");
                }
                AuditEvent identityCollision = em.createQuery(
                                "from AuditEvent e where e.portableId = :id", AuditEvent.class)
                        .setParameter("id", requestId)
                        .getResultStream().findFirst().orElse(null);
                if (identityCollision != null)
                {
                    throw new IllegalStateException(
                            "Opening conversion request identity is already used by another operation.");
                }

                AuditEvent event = new AuditEvent();
                event.initializeOperationIdentity(requestId);
                event.setCompany(company);
                event.setActor(actor);
                event.setActionType(ACTION);
                event.setEntityType(ENTITY_TYPE);
                event.setEntityId(company.getCode());
                event.setSummary("Accepted reviewed opening conversion through " + asOfDate);
                event.setBeforeValue("status=READY;fingerprint=" + expectedFingerprint);
                event.setAfterValue(acceptanceValue(current));
                event.setReason(fixedNote.isEmpty()
                        ? "Reviewed opening conversion acceptance."
                        : fixedNote);
                em.persist(event);
                em.flush();
                saveHook.afterPersist(event);
                Acceptance saved = acceptance(event);
                em.getTransaction().commit();
                return saved;
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive())
                {
                    em.getTransaction().rollback();
                }
                throw ex;
            }
        }
    }

    private Preview assemble(
            LocalDate cutoff,
            BaseFacts base,
            AssetInventoryReportQueryService.FixedAssetRegisterResult assets,
            AssetInventoryReportQueryService.InventoryValuationResult inventory)
    {
        List<Check> checks = new ArrayList<>();
        checks.add(check(
                "Opening trial balance",
                base.openingDebits().compareTo(base.openingCredits()) == 0,
                base.accountOpenings().isEmpty(),
                base.openingDebits().subtract(base.openingCredits()),
                "Debits " + money(base.openingDebits()) + "; credits "
                        + money(base.openingCredits()) + "."));

        boolean banksReady = base.bankOpenings().stream().allMatch(row -> !row.blocking());
        checks.add(check(
                "Configured bank openings",
                banksReady,
                base.bankOpenings().isEmpty(),
                base.bankOpenings().stream().map(BankOpening::difference)
                        .map(BigDecimal::abs).reduce(ZERO, BigDecimal::add),
                base.bankOpenings().isEmpty()
                        ? "No configured bank opening is in this snapshot."
                        : "Ledger/configured baselines are compared once; "
                        + base.outstandingBankItems().size() + " uncleared canonical item(s) remain individually listed."));

        boolean assetsReady = base.assetCount() <= ReportRequest.MAX_ROW_LIMIT
                && assets.difference().compareTo(ZERO) == 0;
        checks.add(check(
                "Fixed assets",
                assetsReady,
                base.assetCount() == 0,
                assets.difference(),
                "Register net " + money(assets.domainNet()) + "; ledger net "
                        + money(assets.ledgerNet()) + "; difference " + money(assets.difference()) + "."));

        boolean inventoryReady = base.inventoryCount() <= ReportRequest.MAX_ROW_LIMIT
                && inventory.difference().compareTo(ZERO) == 0;
        checks.add(check(
                "Inventory",
                inventoryReady,
                base.inventoryCount() == 0,
                inventory.difference(),
                "Register carrying value " + money(inventory.domainValue()) + "; ledger "
                        + money(inventory.ledgerValue()) + "; difference " + money(inventory.difference()) + "."));

        checks.add(check(
                "Orphan control balances",
                base.orphanControls().isEmpty(),
                false,
                base.orphanControls().stream().map(OrphanControl::balance)
                        .map(BigDecimal::abs).reduce(ZERO, BigDecimal::add),
                base.orphanControls().isEmpty()
                        ? "Every nonzero bank, fixed-asset and inventory control balance has an owning configuration/register."
                        : base.orphanControls().size() + " nonzero control balance(s) lack an owning record."));

        for (OpenItemSummary summary : base.openItems())
        {
            checks.add(check(
                    summary.kind().displayName(),
                    summary.ready(),
                    summary.rowCount() == 0 && summary.controlCount() == 0,
                    summary.difference(),
                    summary.openCount() + " open item(s), " + money(summary.openAmount())
                            + "; " + summary.gapCount() + " unresolved allocation gap(s)."));
        }

        boolean hasSources = !base.accountOpenings().isEmpty()
                || !base.bankOpenings().isEmpty()
                || base.assetCount() > 0
                || base.inventoryCount() > 0
                || base.openItems().stream().anyMatch(value -> value.rowCount() > 0
                || value.controlCount() > 0);
        checks.add(new Check(
                "Conversion source facts",
                hasSources ? CheckStatus.PASS : CheckStatus.BLOCKED,
                ZERO,
                hasSources
                        ? "The preview contains opening or carryover facts."
                        : "Enter or import reviewed opening facts before accepting conversion."));

        List<OpenItemGap> gaps = base.openItemGaps();
        String fingerprint = fingerprint(cutoff, base, assets, inventory, checks);
        Acceptance accepted = base.acceptance();
        boolean ready = checks.stream().noneMatch(Check::blocking);
        return new Preview(
                cutoff,
                checks,
                base.accountOpenings(),
                base.bankOpenings(),
                base.outstandingBankItems(),
                base.orphanControls(),
                base.openItems(),
                gaps,
                assets.domainNet(),
                assets.ledgerNet(),
                inventory.domainValue(),
                inventory.ledgerValue(),
                fingerprint,
                ready,
                accepted);
    }

    private BaseFacts baseFacts(EntityManager em, Company company, LocalDate cutoff)
    {
        List<Account> accounts = em.createQuery("""
                select a from Account a join fetch a.chart chart
                where chart = :chart order by a.code, a.id
                """, Account.class)
                .setParameter("chart", company.getActiveChartOfAccounts())
                .getResultList();
        Map<Long, BigDecimal> activity = new HashMap<>();
        for (Object[] row : em.createQuery("""
                select s.account.id, coalesce(sum(s.amountSigned), 0)
                from TxnSplit s join s.txn t
                where t.company = :company and t.txnDate <= :cutoff
                  and t.status in ('ENTERED', 'REVERSED')
                group by s.account.id
                """, Object[].class)
                .setParameter("company", company)
                .setParameter("cutoff", cutoff)
                .getResultList())
        {
            activity.put((Long) row[0], scale((BigDecimal) row[1]));
        }

        List<AccountOpening> openings = new ArrayList<>();
        BigDecimal openingDebits = ZERO;
        BigDecimal openingCredits = ZERO;
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        for (Account account : accounts)
        {
            BigDecimal opening = scale(account.getOpeningBalance());
            BigDecimal balance = opening.add(activity.getOrDefault(account.getId(), ZERO));
            balances.put(account.getId(), scale(balance));
            if (opening.signum() == 0)
            {
                continue;
            }
            BigDecimal debit = debit(account.getNormalBalance(), opening);
            BigDecimal credit = credit(account.getNormalBalance(), opening);
            openingDebits = openingDebits.add(debit);
            openingCredits = openingCredits.add(credit);
            openings.add(new AccountOpening(
                    account.getId(), account.getCode(), account.getName(), account.getSubtype(),
                    account.getNormalBalance(), opening, debit, credit));
        }

        List<CompanyBankAccount> configured = em.createQuery("""
                select b from CompanyBankAccount b left join fetch b.account
                where b.company = :company order by b.name, b.id
                """, CompanyBankAccount.class)
                .setParameter("company", company)
                .getResultList();
        Map<Long, Long> bankLinks = new HashMap<>();
        for (CompanyBankAccount bank : configured)
        {
            if (bank.getAccount() != null)
            {
                bankLinks.merge(bank.getAccount().getId(), 1L, Long::sum);
            }
        }
        List<BankOpening> bankOpenings = new ArrayList<>();
        Set<Long> configuredAccountIds = new HashSet<>();
        for (CompanyBankAccount bank : configured)
        {
            Account account = bank.getAccount();
            BigDecimal configuredOpening = scale(bank.getOpeningBalance());
            BigDecimal ledgerOpening = account == null ? ZERO : scale(account.getOpeningBalance());
            BigDecimal difference = ledgerOpening.subtract(configuredOpening).setScale(4);
            boolean duplicate = account != null && bankLinks.getOrDefault(account.getId(), 0L) > 1;
            boolean dateInvalid = bank.getOpeningDate() == null
                    || bank.getOpeningDate().isAfter(cutoff);
            boolean blocking = account == null || account.getAccountFunction() != AccountFunction.BANK
                    || difference.signum() != 0 || duplicate || dateInvalid;
            if (account != null)
            {
                configuredAccountIds.add(account.getId());
            }
            bankOpenings.add(new BankOpening(
                    bank.getId(), bank.getName(), account == null ? null : account.getId(),
                    account == null ? "" : account.getCode(), bank.getOpeningDate(),
                    ledgerOpening, configuredOpening, difference, duplicate, blocking,
                    blocking ? bankRepair(account, difference, duplicate, dateInvalid) : "Ready"));
        }

        List<BankOutstandingItem> outstanding = new ArrayList<>();
        if (!configuredAccountIds.isEmpty())
        {
            for (TxnSplit split : em.createQuery("""
                    select s from TxnSplit s join fetch s.txn t join fetch s.account
                    where t.company = :company and s.account.id in :accountIds
                      and t.txnDate <= :cutoff and t.status in ('ENTERED', 'REVERSED')
                      and s.bankCleared = false
                    order by t.txnDate, t.id, s.id
                    """, TxnSplit.class)
                    .setParameter("company", company)
                    .setParameter("accountIds", configuredAccountIds)
                    .setParameter("cutoff", cutoff)
                    .getResultList())
            {
                outstanding.add(new BankOutstandingItem(
                        split.getTxn().getId(), split.getId(), split.getTxn().getTxnDate(),
                        split.getAccount().getId(), split.getAccount().getCode(),
                        scale(split.getAmountSigned()),
                        firstText(split.getPaymentReference(), split.getTxn().getMemo(),
                                "Transaction " + split.getTxn().getId())));
            }
        }

        long assetCount = scalarCount(em,
                "select count(a) from FixedAsset a where a.company = :company and a.acquisitionDate <= :cutoff",
                company, cutoff);
        long inventoryCount = scalarCount(em,
                "select count(i) from InventoryItem i where i.company = :company and i.acquisitionDate <= :cutoff",
                company, cutoff);
        Set<Long> assetAccounts = new HashSet<>(em.createQuery("""
                select distinct a.assetAccount.id from FixedAsset a
                where a.company = :company and a.acquisitionDate <= :cutoff
                """, Long.class).setParameter("company", company).setParameter("cutoff", cutoff).getResultList());
        assetAccounts.addAll(em.createQuery("""
                select distinct a.accumulatedDepreciationAccount.id from FixedAsset a
                where a.company = :company and a.acquisitionDate <= :cutoff
                """, Long.class).setParameter("company", company).setParameter("cutoff", cutoff).getResultList());
        Set<Long> inventoryAccounts = new HashSet<>(em.createQuery("""
                select distinct i.inventoryAccount.id from InventoryItem i
                where i.company = :company and i.acquisitionDate <= :cutoff
                """, Long.class).setParameter("company", company).setParameter("cutoff", cutoff).getResultList());

        List<OrphanControl> orphanControls = new ArrayList<>();
        for (Account account : accounts)
        {
            BigDecimal balance = balances.getOrDefault(account.getId(), ZERO);
            if (balance.signum() == 0)
            {
                continue;
            }
            boolean orphanBank = account.getAccountFunction() == AccountFunction.BANK
                    && !configuredAccountIds.contains(account.getId());
            boolean orphanAsset = account.getSubtype() == AccountSubtype.FIXED_ASSET
                    && !assetAccounts.contains(account.getId());
            boolean orphanInventory = account.getSubtype() == AccountSubtype.INVENTORY
                    && !inventoryAccounts.contains(account.getId());
            if (orphanBank || orphanAsset || orphanInventory)
            {
                String area = orphanBank ? "BANK" : orphanAsset ? "FIXED_ASSET" : "INVENTORY";
                orphanControls.add(new OrphanControl(
                        account.getId(), account.getCode(), account.getName(), area, balance,
                        "Create/link the owning configuration or register record, or correct the control balance."));
            }
        }

        List<OpenItemSummary> openItems = new ArrayList<>();
        List<OpenItemGap> openItemGaps = new ArrayList<>();
        for (SupplementalOpenItemQueryService.Kind kind : SupplementalOpenItemQueryService.Kind.values())
        {
            var result = SupplementalOpenItemQueryService.query(em, company.getCode(), kind, cutoff);
            BigDecimal difference = result.reconciliation().controls().stream()
                    .map(control -> control.difference().abs())
                    .reduce(ZERO, BigDecimal::add);
            BigDecimal unmatched = result.reconciliation().controls().stream()
                    .map(control -> control.unmatched().abs())
                    .reduce(ZERO, BigDecimal::add);
            openItems.add(new OpenItemSummary(
                    kind,
                    result.rows().size(),
                    result.openCount(),
                    scale(result.openAmount()),
                    result.reconciliation().controls().size(),
                    result.reconciliation().gaps().size(),
                    scale(unmatched),
                    scale(difference),
                    result.ready()));
            for (SupplementalReconciliationService.Gap gap : result.reconciliation().gaps())
            {
                openItemGaps.add(new OpenItemGap(
                        kind, gap.transactionId(), gap.splitId(), gap.accountCode(), gap.date(),
                        scale(gap.ledger()), scale(gap.explained()), scale(gap.unmatched()), gap.repair()));
            }
        }
        openItemGaps.sort(Comparator.comparing((OpenItemGap gap) -> gap.kind().name())
                .thenComparing(OpenItemGap::date)
                .thenComparingLong(OpenItemGap::transactionId)
                .thenComparingLong(OpenItemGap::splitId));

        return new BaseFacts(
                openings, scale(openingDebits), scale(openingCredits), bankOpenings,
                outstanding, orphanControls, openItems, openItemGaps, assetCount, inventoryCount,
                acceptance(em, company));
    }

    private static long scalarCount(
            EntityManager em, String query, Company company, LocalDate cutoff)
    {
        return em.createQuery(query, Long.class)
                .setParameter("company", company)
                .setParameter("cutoff", cutoff)
                .getSingleResult();
    }

    private static Check check(
            String area, boolean passed, boolean informational, BigDecimal difference, String detail)
    {
        CheckStatus status = passed ? (informational ? CheckStatus.INFO : CheckStatus.PASS)
                : CheckStatus.BLOCKED;
        return new Check(area, status, scale(difference), detail);
    }

    private static String fingerprint(
            LocalDate cutoff,
            BaseFacts base,
            AssetInventoryReportQueryService.FixedAssetRegisterResult assets,
            AssetInventoryReportQueryService.InventoryValuationResult inventory,
            List<Check> checks)
    {
        StringBuilder value = new StringBuilder("asOf=").append(cutoff);
        base.accountOpenings().forEach(row -> value.append("|account:").append(row.accountId())
                .append(':').append(row.accountCode()).append(':').append(row.accountName())
                .append(':').append(row.subtype()).append(':').append(row.normalBalance())
                .append(':').append(row.openingBalance()).append(':').append(row.debit())
                .append(':').append(row.credit()));
        base.bankOpenings().forEach(row -> value.append("|bank:").append(row.bankAccountId())
                .append(':').append(row.bankAccountName()).append(':').append(row.accountId())
                .append(':').append(row.accountCode()).append(':').append(row.openingDate())
                .append(':').append(row.ledgerOpening()).append(':').append(row.configuredOpening())
                .append(':').append(row.difference()).append(':').append(row.duplicateLink())
                .append(':').append(row.blocking()).append(':').append(row.repair()));
        base.outstandingBankItems().forEach(row -> value.append("|outstanding:")
                .append(row.transactionId()).append(':').append(row.splitId()).append(':')
                .append(row.date()).append(':').append(row.accountId()).append(':')
                .append(row.accountCode()).append(':').append(row.amount()).append(':')
                .append(row.reference()));
        base.orphanControls().forEach(row -> value.append("|orphan:").append(row.accountId())
                .append(':').append(row.accountCode()).append(':').append(row.accountName())
                .append(':').append(row.area()).append(':').append(row.balance())
                .append(':').append(row.repair()));
        base.openItems().forEach(row -> value.append("|open:").append(row.kind()).append(':')
                .append(row.rowCount()).append(':').append(row.openCount()).append(':')
                .append(row.openAmount()).append(':').append(row.controlCount()).append(':')
                .append(row.gapCount()).append(':').append(row.unmatched()).append(':')
                .append(row.difference()).append(':').append(row.ready()));
        base.openItemGaps().forEach(row -> value.append("|gap:").append(row.kind()).append(':')
                .append(row.transactionId()).append(':').append(row.splitId()).append(':')
                .append(row.accountCode()).append(':').append(row.date()).append(':')
                .append(row.ledger()).append(':').append(row.explained()).append(':')
                .append(row.unmatched()).append(':').append(row.repair()));
        assets.rows().forEach(row -> value.append("|asset:").append(row.assetId()).append(':')
                .append(row.recognizedCost()).append(':').append(row.recognizedContra())
                .append(':').append(row.bookValue()));
        inventory.rows().forEach(row -> value.append("|inventory:").append(row.itemId()).append(':')
                .append(row.quantity()).append(':').append(row.totalValue()));
        checks.forEach(row -> value.append("|check:").append(row.area()).append(':')
                .append(row.status()).append(':').append(row.difference()).append(':')
                .append(row.detail()));
        return sha256(value.toString());
    }

    private Acceptance acceptance(EntityManager em, Company company)
    {
        List<AuditEvent> events = em.createQuery("""
                from AuditEvent e where e.company = :company and e.actionType = :action
                order by e.occurredAt, e.id
                """, AuditEvent.class)
                .setParameter("company", company)
                .setParameter("action", ACTION)
                .getResultList();
        if (events.size() > 1)
        {
            throw new IllegalStateException("Multiple opening conversion acceptances exist for this company.");
        }
        return events.isEmpty() ? null : acceptance(events.get(0));
    }

    private static Acceptance acceptance(AuditEvent event)
    {
        Map<String, String> values = parse(event.getAfterValue());
        try
        {
            return new Acceptance(
                    event.getId(), event.getPortableId(), LocalDate.parse(values.get("asOf")),
                    values.get("fingerprint"), event.getActor(), event.getOccurredAt(), event.getReason());
        }
        catch (RuntimeException ex)
        {
            throw new IllegalStateException("Opening conversion acceptance audit fact is invalid.", ex);
        }
    }

    private static String acceptanceValue(Preview preview)
    {
        return "asOf=" + preview.asOfDate()
                + ";fingerprint=" + preview.fingerprint()
                + ";openingAccounts=" + preview.accountOpenings().size()
                + ";bankAccounts=" + preview.bankOpenings().size()
                + ";outstandingBankItems=" + preview.outstandingBankItems().size()
                + ";assetNet=" + preview.assetRegisterNet()
                + ";inventoryValue=" + preview.inventoryRegisterValue()
                + ";openItemGaps=" + preview.openItemGaps().size();
    }

    private static Map<String, String> parse(String value)
    {
        Map<String, String> result = new LinkedHashMap<>();
        if (value != null)
        {
            for (String pair : value.split(";"))
            {
                int separator = pair.indexOf('=');
                if (separator > 0)
                {
                    result.put(pair.substring(0, separator), pair.substring(separator + 1));
                }
            }
        }
        return result;
    }

    private String requireCompanyCode()
    {
        String value = companyCodeSupplier.get();
        if (value == null || value.isBlank())
        {
            throw new IllegalStateException("An active company is required for opening conversion review.");
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }

    private static Company requireCompany(
            EntityManager em, String companyCode, LockModeType lockMode)
    {
        Company company = em.createQuery("""
                from Company c left join fetch c.activeChartOfAccounts
                where upper(c.code) = :code
                """, Company.class)
                .setParameter("code", companyCode)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyCode));
        if (company.getActiveChartOfAccounts() == null)
        {
            throw new IllegalStateException("The active company requires an active Chart of Accounts.");
        }
        if (lockMode != LockModeType.NONE)
        {
            em.lock(company, lockMode);
        }
        return company;
    }

    private static BigDecimal debit(NormalBalance normal, BigDecimal amount)
    {
        BigDecimal signed = normal == NormalBalance.DEBIT ? amount : amount.negate();
        return signed.signum() > 0 ? scale(signed) : ZERO;
    }

    private static BigDecimal credit(NormalBalance normal, BigDecimal amount)
    {
        BigDecimal signed = normal == NormalBalance.CREDIT ? amount : amount.negate();
        return signed.signum() > 0 ? scale(signed) : ZERO;
    }

    private static BigDecimal scale(BigDecimal value)
    {
        return (value == null ? ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private static String money(BigDecimal value)
    {
        return scale(value).toPlainString();
    }

    private static String firstText(String... values)
    {
        for (String value : values)
        {
            if (value != null && !value.isBlank())
            {
                return value.strip();
            }
        }
        return "";
    }

    private static String bankRepair(
            Account account, BigDecimal difference, boolean duplicate, boolean dateInvalid)
    {
        if (account == null)
        {
            return "Link the configured bank account to its company-owned BANK ledger account.";
        }
        if (account.getAccountFunction() != AccountFunction.BANK)
        {
            return "The linked ledger account must have BANK function.";
        }
        if (duplicate)
        {
            return "One ledger bank account cannot own multiple configured opening baselines.";
        }
        if (dateInvalid)
        {
            return "Set an opening date on or before the conversion cutoff.";
        }
        if (difference.signum() != 0)
        {
            return "Make the configured bank baseline equal the linked ledger account opening; outstanding splits remain separate.";
        }
        return "Review the configured bank opening.";
    }

    private static String sha256(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte item : digest)
            {
                out.append(String.format(Locale.ROOT, "%02x", item));
            }
            return out.toString();
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    public enum CheckStatus
    {
        PASS,
        INFO,
        BLOCKED
    }

    public record Check(String area, CheckStatus status, BigDecimal difference, String detail)
    {
        public boolean blocking() { return status == CheckStatus.BLOCKED; }
    }

    public record AccountOpening(
            long accountId,
            String accountCode,
            String accountName,
            AccountSubtype subtype,
            NormalBalance normalBalance,
            BigDecimal openingBalance,
            BigDecimal debit,
            BigDecimal credit)
    {
    }

    public record BankOpening(
            long bankAccountId,
            String bankAccountName,
            Long accountId,
            String accountCode,
            LocalDate openingDate,
            BigDecimal ledgerOpening,
            BigDecimal configuredOpening,
            BigDecimal difference,
            boolean duplicateLink,
            boolean blocking,
            String repair)
    {
    }

    public record BankOutstandingItem(
            long transactionId,
            long splitId,
            LocalDate date,
            long accountId,
            String accountCode,
            BigDecimal amount,
            String reference)
    {
    }

    public record OrphanControl(
            long accountId,
            String accountCode,
            String accountName,
            String area,
            BigDecimal balance,
            String repair)
    {
    }

    public record OpenItemSummary(
            SupplementalOpenItemQueryService.Kind kind,
            int rowCount,
            long openCount,
            BigDecimal openAmount,
            int controlCount,
            int gapCount,
            BigDecimal unmatched,
            BigDecimal difference,
            boolean ready)
    {
    }

    public record OpenItemGap(
            SupplementalOpenItemQueryService.Kind kind,
            long transactionId,
            long splitId,
            String accountCode,
            LocalDate date,
            BigDecimal ledger,
            BigDecimal explained,
            BigDecimal unmatched,
            String repair)
    {
    }

    public record Acceptance(
            long auditEventId,
            UUID requestId,
            LocalDate asOfDate,
            String fingerprint,
            String actor,
            java.time.Instant acceptedAt,
            String note)
    {
    }

    public record Preview(
            LocalDate asOfDate,
            List<Check> checks,
            List<AccountOpening> accountOpenings,
            List<BankOpening> bankOpenings,
            List<BankOutstandingItem> outstandingBankItems,
            List<OrphanControl> orphanControls,
            List<OpenItemSummary> openItems,
            List<OpenItemGap> openItemGaps,
            BigDecimal assetRegisterNet,
            BigDecimal assetLedgerNet,
            BigDecimal inventoryRegisterValue,
            BigDecimal inventoryLedgerValue,
            String fingerprint,
            boolean ready,
            Acceptance acceptance)
    {
        public Preview
        {
            checks = List.copyOf(checks);
            accountOpenings = List.copyOf(accountOpenings);
            bankOpenings = List.copyOf(bankOpenings);
            outstandingBankItems = List.copyOf(outstandingBankItems);
            orphanControls = List.copyOf(orphanControls);
            openItems = List.copyOf(openItems);
            openItemGaps = List.copyOf(openItemGaps);
        }

        public boolean saveAvailable()
        {
            return ready && acceptance == null;
        }
    }

    private record BaseFacts(
            List<AccountOpening> accountOpenings,
            BigDecimal openingDebits,
            BigDecimal openingCredits,
            List<BankOpening> bankOpenings,
            List<BankOutstandingItem> outstandingBankItems,
            List<OrphanControl> orphanControls,
            List<OpenItemSummary> openItems,
            List<OpenItemGap> openItemGaps,
            long assetCount,
            long inventoryCount,
            Acceptance acceptance)
    {
    }

    @FunctionalInterface
    interface SaveHook
    {
        void afterPersist(AuditEvent event);
    }
}
