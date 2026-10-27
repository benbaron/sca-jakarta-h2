package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Historical fixtures use the governed interchange seam; interactive close tests use real reviewed source facts. */
public final class PeriodCloseTestFixture
{
    public static PeriodCloseRangeView historical(Jpa jpa, String code, LocalDate start, LocalDate end, String kind, String actor, String reason)
    {
        UUID id = UUID.randomUUID(); Instant at = Instant.now();
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            var owner = new CompanyOwnershipService(jpa).requireCompany(em, code);
            new PeriodCloseRangeService(jpa).importForInterchange(em, owner,
                    List.of(new PeriodCloseRangeService.RangeImport("historical-range", id, start, end, kind, "CLOSED", at, actor, reason, null, null, null)),
                    List.of(new PeriodCloseRangeService.EventImport("historical-event", UUID.randomUUID(), "historical-range", "CLOSED", actor, reason, at)), Map.of(), Map.of());
            var audit = new AuditEvent(); audit.setCompany(owner); audit.setActor(actor); audit.setActionType("PERIOD_RANGE_CLOSED");
            audit.setEntityType("PeriodCloseRange"); audit.setEntityId(id.toString()); audit.setSummary("Imported synthetic historical closed range"); audit.setReason(reason);
            em.persist(audit); em.getTransaction().commit();
        }
        return new PeriodCloseRangeService(jpa).loadRange(id);
    }

    public static CloseReviewEvidenceService.Review readyReview(Jpa jpa, Path db, String code, LocalDate start, LocalDate end) throws Exception
    {
        long bankId, fundId, categoryId;
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            var company = new CompanyOwnershipService(jpa).requireCompany(em, code);
            if (company.getActiveChartOfAccounts() == null)
            {
                var chart = new ChartOfAccounts(); chart.setCompany(company); chart.setName("Synthetic close chart"); chart.setVersion("1"); chart.setStatus(ChartStatus.ACTIVE);
                em.persist(chart); company.setActiveChartOfAccounts(chart);
            }
            var accounts = em.createQuery("from CompanyBankAccount b where b.company = :c", CompanyBankAccount.class).setParameter("c", company).getResultList();
            if (accounts.isEmpty())
            {
                var account = new Account(); account.setChart(company.getActiveChartOfAccounts()); account.setCode("1001"); account.setName("Synthetic close bank");
                account.setAccountType(AccountType.ASSET); account.setNormalBalance(NormalBalance.DEBIT); account.setAccountFunction(AccountFunction.BANK); account.setPosting(true); account.setActive(true); em.persist(account);
                var institution = new Bank(); institution.setCompany(company); institution.setName("Synthetic bank"); em.persist(institution);
                var bank = new CompanyBankAccount(); bank.setCompany(company); bank.setBank(institution); bank.setAccount(account); bank.setName("Synthetic account");
                bank.setOpeningDate(start.withDayOfYear(1)); bank.setOpeningBalance(BigDecimal.ZERO); em.persist(bank); accounts = List.of(bank);
                var income = new Account(); income.setChart(company.getActiveChartOfAccounts()); income.setCode("4001"); income.setName("Synthetic income");
                income.setAccountType(AccountType.INCOME); income.setNormalBalance(NormalBalance.CREDIT); income.setActive(true); income.setPosting(true); em.persist(income);
            }
            bankId = accounts.get(0).getId();
            var fund = em.createQuery("from Fund f where f.company = :c", Fund.class).setParameter("c", company).getResultStream().findFirst().orElse(null);
            if (fund == null)
            {
                fund = new Fund(); fund.setCompany(company); fund.setCode("GENERAL"); fund.setName("General"); fund.setFundType(FundType.UNRESTRICTED); fund.setActive(true); em.persist(fund);
            }
            fundId = fund.getId();
            var category = em.createQuery("from BudgetCategory b where b.company = :c", BudgetCategory.class).setParameter("c", company).getResultStream().findFirst().orElse(null);
            if (category == null)
            {
                category = new BudgetCategory(); category.setCompany(company); category.setCode("CLOSE"); category.setName("Synthetic budget"); em.persist(category);
            }
            categoryId = category.getId(); em.getTransaction().commit();
        }
        var budget = new BudgetPlanService(jpa, () -> code); var fiscal = budget.fiscalRange(end);
        if (budget.activeForFiscalYear(fiscal.fiscalYear()).isEmpty())
        {
            var draft = budget.createDraft(new BudgetPlanCommand("Synthetic close budget", fiscal.fiscalYear(), "GATE", fiscal.fiscalYearStart(), fiscal.fiscalYearEnd(), "Synthetic fixture"));
            budget.replaceDraftLines(draft.id(), List.of(new BudgetLineCommand(categoryId, fundId, null, null, new BigDecimal("1000"), "Explicit fixture target"))); budget.activate(draft.id());
        }
        var workspace = new BankReconciliationWorkspaceService(jpa);
        if (workspace.listSessions(code).stream().noneMatch(s -> s.statementEndDate().equals(end)))
        {
            var session = workspace.start(new BankReconciliationWorkspaceService.StartCommand(code, bankId, end, BigDecimal.ZERO,
                    BankReconciliationWorkspaceService.ClearedStatePolicy.WARN_ONLY, "Synthetic balanced statement"));
            workspace.save(session.sessionId(), true);
        }
        var service = evidence(jpa, db, code);
        var preview = service.preview(start, end);
        if (preview.blockers() != 0) throw new AssertionError(preview.findings());
        Map<String, String> reasons = new java.util.TreeMap<>();
        preview.findings().forEach(f -> reasons.put(f.id(), "Synthetic reviewed timing exception"));
        return service.save(new CloseReviewEvidenceService.Command(UUID.randomUUID(), start, end, preview.dataRevision(),
                List.of(new CloseReviewEvidenceService.Reference(CloseReviewEvidenceService.ReferenceKind.OTHER, "synthetic/close-packet", "Explicit synthetic evidence")), reasons, "Synthetic factual review", true));
    }

    static CloseReviewEvidenceService evidence(Jpa jpa, Path db, String code)
    {
        return new CloseReviewEvidenceService(jpa, () -> code, () -> new CoreAccountingPacketService(() -> db, () -> code, null), null);
    }
}
