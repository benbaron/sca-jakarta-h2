package org.nonprofitbookkeeping.interchange.sclx;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Populated canonical graph for native backup/recovery and old-engine compatibility checks. */
final class NativeRecoveryFixture
{
    static final String COMPANY = "DEFAULT";
    static final LocalDate CLOSED_START = LocalDate.of(2025, 11, 1), CLOSED_END = LocalDate.of(2025, 11, 30);
    final AssetAcquisitionTestFixture assets;
    final FundTransferTestFixture base;
    final UUID transferRequest = UUID.randomUUID();
    final UUID merchantItem = UUID.randomUUID(), nmrItem = UUID.randomUUID();
    FundTransferView transfer;
    PeriodCloseRangeView closedRange;
    UUID reviewId;
    long event, category, recipient, merchant, inventory;
    final Map<SupplementalOpenItemQueryService.Kind, Long> controls = new EnumMap<>(SupplementalOpenItemQueryService.Kind.class);

    private NativeRecoveryFixture(Jpa jpa)
    {
        assets = AssetAcquisitionTestFixture.seed(jpa);
        base = assets.base();
    }

    static NativeRecoveryFixture populate(Jpa jpa, Path database) throws Exception
    {
        var f = new NativeRecoveryFixture(jpa);
        f.masters(jpa);
        var conversion = new OpeningConversionService(jpa, () -> COMPANY);
        var asOf = LocalDate.of(2025, 10, 31);
        var proposal = conversion.preview(asOf);
        assertTrue(proposal.ready(), proposal.checks().toString());
        conversion.saveAcceptance(UUID.randomUUID(), asOf, proposal.fingerprint(), "Synthetic conversion evidence");
        var periods = new PeriodCloseRangeService(jpa);
        var review = PeriodCloseTestFixture.readyReview(jpa, database, COMPANY, CLOSED_START, CLOSED_END);
        f.closedRange = periods.closeRange(COMPANY, CLOSED_START, CLOSED_END, "CUSTOM", "tester", "Retained closed period", review.id());
        var start = LocalDate.of(2025, 12, 1); var end = LocalDate.of(2025, 12, 31);
        review = PeriodCloseTestFixture.readyReview(jpa, database, COMPANY, start, end);
        f.reviewId = review.id();
        var range = periods.closeRange(COMPANY, start, end, "CUSTOM", "tester", "Reviewed", review.id());
        periods.reopenRange(range.id(), "tester", "Retain correction history", ClosedPeriodPolicy.REQUIRE_REASON, true);
        var transfers = new FundTransferService(jpa);
        f.transfer = transfers.save(f.base.command(f.transferRequest, "75"));
        transfers.reverse(f.transfer.id(), FundTransferTestFixture.DATE.plusDays(5), "Synthetic reversal");
        f.transfer = transfers.save(f.base.command(f.transferRequest, "75"));
        f.itemsAndRecognition(jpa);
        f.merchantAndNmr(jpa);
        f.property(jpa);
        f.budgetsAndChecks(jpa);
        f.banking(jpa);
        new SecurityBootstrapService(jpa).initializeIfUnambiguous();
        var users = new UserAdminService(jpa, () -> COMPANY);
        var user = users.saveUser(new AppUserCommand(null, "recovery-only-user", "Recovery Only", null, true, "tester"));
        long admin = users.listUsers().stream().filter(u -> u.getUsername().equals("ADMIN")).findFirst().orElseThrow().getId();
        long role = users.listRoles().stream().filter(r -> r.getCode().equals("VIEWER")).findFirst().orElseThrow().getId();
        users.assignRole(new UserRoleAssignmentCommand(user.getId(), role, LocalDate.of(2025, 1, 1), "tester"));
        new SecurityAdminService(jpa).setPassword(admin, COMPANY, user.getId(), "synthetic-recovery-password".toCharArray());
        new NmrPolicyService(jpa, () -> COMPANY, guard(jpa, COMPANY, ReservedSecurityRole.MANAGER))
                .save(UUID.randomUUID(), SclxNmrPolicyRoundTripTest.terms(LocalDate.of(2026, 1, 1), null));
        // A real second-company ledger and persisted compatibility/UI facts test deliberate exclusions.
        var other = NmrItemTestFixture.seed(jpa);
        other.entry(jpa).enter(other.receipt(UUID.randomUUID(), other.recipient(), other.event()));
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("insert into budget_category_alias (budget_category_id,alias,source_system) values (?,'RECOVERY-ONLY-ALIAS','legacy')")
                    .setParameter(1, f.category).executeUpdate();
            em.createNativeQuery("insert into company_ui_state (company_code,state_key,state_value) values ('DEFAULT','recovery-only-divider','0.375')").executeUpdate();
            em.createNativeQuery("insert into company_ui_preference (company_code,currency_symbol) values ('DEFAULT','€')").executeUpdate();
            var audit = new AuditEvent(); audit.setActionType("RECOVERY_ONLY_GLOBAL"); audit.setActor("synthetic");
            audit.setSummary("Synthetic global recovery fact"); audit.setEntityType("GLOBAL"); audit.setEntityId("unowned"); audit.setAfterValue("Opaque retained source text"); em.persist(audit);
            em.getTransaction().commit();
        }
        return f;
    }

    private void masters(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            var company = em.find(Account.class, base.bank()).getChart().getCompany();
            var chart = company.getActiveChartOfAccounts();
            for (var kind : SupplementalOpenItemQueryService.Kind.values())
            {
                boolean liability = Set.of(SupplementalOpenItemQueryService.Kind.PAYABLE,
                        SupplementalOpenItemQueryService.Kind.DEFERRED_REVENUE, SupplementalOpenItemQueryService.Kind.OTHER_LIABILITY).contains(kind);
                controls.put(kind, account(em, chart, "S" + kind.ordinal(), liability ? AccountType.LIABILITY : AccountType.ASSET, kind.accountSubtype()).getId());
            }
            inventory = account(em, chart, "1700", AccountType.ASSET, AccountSubtype.INVENTORY).getId();
            var activity = new Activity(); activity.setCompany(company); activity.setCode("EVENT"); activity.setName("Synthetic event"); activity.setActive(true); em.persist(activity);
            var used = SclxBudgetCategoriesTest.category(company, "PROGRAM", "Program café", "Exact source metadata"); em.persist(used);
            var unused = SclxBudgetCategoriesTest.category(company, "UNUSED", "Inactive unused category", null); unused.setActive(false); em.persist(unused);
            var fund = new Fund(); fund.setCompany(company); fund.setCode("UNUSED"); fund.setName("Inactive child"); fund.setFundType(FundType.DESIGNATED);
            fund.setParent(em.find(Fund.class, base.source())); fund.setActive(false); em.persist(fund);
            var party = new Counterparty(); party.setCompany(company); party.setDisplayName("Synthetic recipient"); em.persist(party);
            var processor = new Merchant(); processor.setCompany(company); processor.setName("Synthetic processor"); processor.setActive(true); em.persist(processor);
            var bank = new Bank(); bank.setCompany(company); bank.setName("Synthetic bank"); em.persist(bank);
            var account = new CompanyBankAccount(); account.setCompany(company); account.setBank(bank); account.setAccount(em.find(Account.class, base.bank()));
            account.setName("Synthetic checking"); account.setOpeningDate(LocalDate.of(2025, 1, 1)); account.setOpeningBalance(BigDecimal.ZERO); em.persist(account);
            em.flush(); event = activity.getId(); category = used.getId(); recipient = party.getId(); merchant = processor.getId();
            em.getTransaction().commit();
        }
    }

    private void itemsAndRecognition(Jpa jpa)
    {
        var entries = new TransactionEntryService(jpa);
        for (var kind : SupplementalOpenItemQueryService.Kind.values())
        {
            UUID item = UUID.randomUUID();
            boolean liability = Set.of(SupplementalOpenItemQueryService.Kind.PAYABLE,
                    SupplementalOpenItemQueryService.Kind.DEFERRED_REVENUE, SupplementalOpenItemQueryService.Kind.OTHER_LIABILITY).contains(kind);
            boolean recognition = kind == SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE || kind == SupplementalOpenItemQueryService.Kind.DEFERRED_REVENUE;
            String opening = recognition ? "1200" : "100";
            entries.enter(itemCommand(kind, item, LocalDate.of(2026, 1, 10), opening, true, liability));
            if (recognition)
            {
                var service = new SupplementalRecognitionService(jpa, () -> COMPANY);
                var preview = service.preview(new SupplementalRecognitionService.Request(kind, item, LocalDate.of(2026, 1, 10),
                        LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 31),
                        kind == SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE ? base.expense() : base.income(),
                        kind == SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE ? ExpenseFunction.AR : null));
                assertEquals(1, preview.proposals().size()); service.save(preview.proposals().get(0));
            }
            else
            {
                var partial = entries.enter(itemCommand(kind, item, LocalDate.of(2026, 2, 10), "30", false, liability));
                new TransactionCorrectionService(jpa).reverse(partial.id(), LocalDate.of(2026, 3, 5), "tester", "Retained partial correction", false);
            }
        }
    }

    private TransactionCommand itemCommand(SupplementalOpenItemQueryService.Kind kind, UUID item, LocalDate date, String amount, boolean increase, boolean liability)
    {
        boolean debit = increase != liability;
        long offset = liability ? base.bank() : base.income();
        return new TransactionCommand(date, null, kind + (increase ? " opening" : " partial"), null,
                List.of(line(controls.get(kind), debit ? amount : "0", debit ? "0" : amount, null),
                        line(offset, debit ? "0" : amount, debit ? amount : "0", null)),
                List.of(new TransactionSupplementalLineCommand(kind.name(), kind.name(), "Synthetic party", "Retained item", "external/item",
                        new BigDecimal(amount), null, null, null, "Exact notes", 0, item, increase ? "INCREASE" : "DECREASE", 0)));
    }

    private void merchantAndNmr(Jpa jpa)
    {
        var nmr = new NmrSettlementTestFixture(new NmrItemTestFixture(base.bank(), base.income(),
                controls.get(SupplementalOpenItemQueryService.Kind.OTHER_LIABILITY), base.source(), event, recipient),
                controls.get(SupplementalOpenItemQueryService.Kind.RECEIVABLE), base.expense(), merchant);
        var fixture = new MerchantSettlementTestFixture(nmr, merchantItem, nmrItem);
        var entries = new TransactionEntryService(jpa);
        entries.enter(fixture.receipt());
        var assistant = new MerchantSettlementAssistantService(jpa, () -> COMPANY, entries);
        assistant.save(assistant.preview(new MerchantSettlementAssistantService.Request(merchantItem, LocalDate.of(2026, 6, 2),
                base.bank(), new BigDecimal("950"), base.expense(), new BigDecimal("20"), ExpenseFunction.FR,
                null, null, new BigDecimal("30"), "processor/withheld/001", "Synthetic payout")));
        entries.enter(fixture.discharge());
        entries.enter(new TransactionCommand(LocalDate.of(2026, 6, 30), null, "Branch remittance", null,
                List.of(nmr.line(nmr.base().liability(), "70", "0", null), nmr.line(base.bank(), "0", "70", null)),
                List.of(nmr.nmr(nmrItem, "DECREASE", 0, "70", "BRANCH_REMITTANCE", "external/remit/001"))));
    }

    private void property(Jpa jpa)
    {
        var fixed = assets.service(jpa);
        var purchased = fixed.recordAcquisition(fixed.previewAcquisition(new AssetAcquisitionCommand(null,
                assets.command("Purchased asset", "1000.1234"), AssetAcquisitionSource.NEW_PURCHASE, base.bank(), null), UUID.randomUUID()));
        fixed.runMonthlyDepreciation(purchased.id(), AssetAcquisitionTestFixture.DATE.plusMonths(1), "Retained depreciation");
        var stock = new InventoryService(jpa, new TransactionEntryService(jpa), () -> COMPANY);
        var item = stock.create(new InventoryItemCommand(COMPANY, inventory, base.source(), "Valued stock", "Supply", BigDecimal.ZERO, "each", BigDecimal.ZERO,
                LocalDate.of(2026, 5, 1), "Alex", "Hall", InventoryItem.Condition.GOOD, InventoryItem.Status.ACTIVE, "Retained values"));
        for (int i = 0; i < 2; i++)
            stock.recordMovement(stock.previewMovement(item.id(), new InventoryMovementCommand(InventoryMovement.MovementType.RECEIPT,
                    BigDecimal.TEN, LocalDate.of(2026, 5, 1 + i), base.bank(), false, "Weighted receipt", event, category, false, new BigDecimal(2 + i))), "tester");
        var issue = stock.recordMovement(stock.previewMovement(item.id(), new InventoryMovementCommand(InventoryMovement.MovementType.ISSUE,
                new BigDecimal("4"), LocalDate.of(2026, 5, 3), base.expense(), false, "Event supplies", event, category, false)), "tester");
        stock.reverseMovement(stock.previewMovementReversal(issue.id(), LocalDate.of(2026, 5, 4), "Retained issue correction"), "tester");
        var supply = stock.create(new InventoryItemCommand(COMPANY, inventory, base.source(), "Expensed flags", "Supply", new BigDecimal("4"), "each", BigDecimal.ZERO,
                InventoryItem.FinancialTreatment.EXPENSED_SUPPLY, LocalDate.of(2026, 5, 4), "Alex", "Hall", InventoryItem.Condition.GOOD, InventoryItem.Status.ACTIVE, "Nonfinancial supplies"));
        var custody = new PropertyCustodyService(jpa, () -> COMPANY);
        var original = custody.save(custody(supply.id(), PropertyCustodyEvent.EventType.ASSIGNMENT, "Alex"), "tester");
        custody.correctLatest(original.id(), custody(supply.id(), PropertyCustodyEvent.EventType.TRANSFER, "Morgan"), "tester");
        stock.update(supply.id(), new InventoryItemCommand(COMPANY, inventory, base.source(), "Expensed flags", "Supply", new BigDecimal("4"), "each", BigDecimal.ZERO,
                InventoryItem.FinancialTreatment.EXPENSED_SUPPLY, LocalDate.of(2026, 5, 4), "Later register maintainer", "Store",
                InventoryItem.Condition.GOOD, InventoryItem.Status.ACTIVE, "Nonfinancial supplies"));
    }

    private PropertyCustodyService.Command custody(long item, PropertyCustodyEvent.EventType type, String guardian)
    {
        return new PropertyCustodyService.Command(PropertyCustodyService.SubjectType.INVENTORY_ITEM, item, type,
                LocalDate.of(2026, 5, 5), new BigDecimal("4"), guardian, "synthetic@example.invalid", "555-0100", "Hall",
                InventoryItem.Condition.GOOD, PropertyCustodyEvent.ConfirmationStatus.CONFIRMED, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 5),
                LocalDate.of(2027, 1, 1), null, "external/AUTH", "Exact notes", null, null);
    }

    private void budgetsAndChecks(Jpa jpa)
    {
        var service = new BudgetPlanService(jpa);
        for (String version : List.of("OLD", "ACTIVE", "DRAFT"))
        {
            var plan = service.createDraft(new BudgetPlanCommand("Fiscal " + version, 2026, version, LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30), "Header " + version));
            service.replaceDraftLines(plan.id(), List.of(new BudgetLineCommand(category, base.source(), event, YearMonth.of(2027, 1), new BigDecimal("123.4567"), "Exact line notes")));
            if (!version.equals("DRAFT")) service.activate(plan.id());
        }
        var date = LocalDate.of(2026, 12, 31);
        var payment = new PaymentReference(PaymentReference.Method.CHECK, "000123", date, null);
        var entries = new TransactionEntryService(jpa);
        var saved = entries.enter(new TransactionCommand(date, null, "Year-end check", null,
                List.of(line(base.expense(), "10", "0", ExpenseFunction.OA), new TransactionLineCommand(base.bank(), base.source(), category, event, null,
                        BigDecimal.ZERO, BigDecimal.TEN, false, "", payment))));
        new CheckExceptionService(jpa, () -> COMPANY, null).review(saved.id(), base.bank(), "000123", date, "external/check/123", date, "Delivered", "tester");
        new TransactionCorrectionService(jpa).reissueCheck(saved.id(), date.plusDays(2), "tester", "Lost instrument",
                new PaymentReference(PaymentReference.Method.CHECK, "000124", date.plusDays(2), null));
    }

    private void banking(Jpa jpa)
    {
        long bankAccount;
        try (var em = jpa.em())
        {
            bankAccount = em.createQuery("select a.id from CompanyBankAccount a where a.company.code='DEFAULT'", Long.class).getSingleResult();
        }
        new BankImportReviewService(jpa).createReviewBatch(new BankImportReviewCommand(COMPANY, bankAccount,
                "synthetic.csv", "/synthetic/statement.csv", "synthetic-hash", BankImportBatch.SourceFormat.CSV,
                List.of(new BankTransactionRecord("fit-opening", "20260101000000", new BigDecimal("200"), "CREDIT", "Synthetic", "Opening resources")), "Retained import"));
        long statement, split;
        try (var em = jpa.em())
        {
            statement = em.createQuery("select l.id from BankStatementLine l where l.company.code='DEFAULT'", Long.class).getSingleResult();
            split = em.createQuery("select s.id from TxnSplit s where s.txn.company.code='DEFAULT' and s.txn.memo='Opening resources' and s.account.id=:account", Long.class)
                    .setParameter("account", base.bank()).getSingleResult();
        }
        var workspace = new BankReconciliationWorkspaceService(jpa);
        var session = workspace.start(new BankReconciliationWorkspaceService.StartCommand(COMPANY, bankAccount, LocalDate.of(2026, 1, 31), new BigDecimal("200"),
                BankReconciliationWorkspaceService.ClearedStatePolicy.WARN_ONLY, "Retained partial reconciliation"));
        workspace.matchSelected(session.sessionId(), statement, split, true);
        workspace.save(session.sessionId(), false);
    }

    private TransactionLineCommand line(long account, String debit, String credit, ExpenseFunction function)
    {
        return new TransactionLineCommand(account, base.source(), category, event, null, new BigDecimal(debit), new BigDecimal(credit), false, "  Retained café\nExact split  ", null, function);
    }

    private static Account account(EntityManager em, ChartOfAccounts chart, String code, AccountType type, AccountSubtype subtype)
    {
        var account = new Account(); account.setChart(chart); account.setCode(code); account.setName(code); account.setAccountType(type); account.setSubtype(subtype);
        account.setNormalBalance(type == AccountType.LIABILITY ? NormalBalance.CREDIT : NormalBalance.DEBIT); account.setPosting(true); account.setActive(true); em.persist(account); em.flush(); return account;
    }

    static AuthorizationGuard guard(Jpa jpa, String company, ReservedSecurityRole role)
    {
        return new AuthorizationGuard(jpa, () -> Optional.of(new AuthenticatedUserSession(9001L, "synthetic", "Synthetic", company,
                Set.of(role), Instant.EPOCH, Instant.EPOCH)));
    }
}
