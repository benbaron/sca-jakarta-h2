
package org.nonprofitbookkeeping.app;

import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;

/**
 * Seeds a minimal, usable starting dataset:
 * - One Chart of Accounts
 * - A few foundational accounts (cash/bank, income, expense, fund transfer clearing)
 * - One default fund (GENERAL)
 * - Basic schedule kinds (optional)
 *
 * The goal is to make the system immediately postable for demos and early UI work.
 *
 * Idempotency:
 * - If the same chart name+version exists, it is reused.
 * - Accounts are upserted by (chart, code).
 * - Funds are upserted by code.
 */
@ApplicationScoped
@Command(
	name = "seed",
	mixinStandardHelpOptions = true,
	description = "Seed a minimal Chart of Accounts and Funds for development/demo use.")
public class SeedCommand implements Runnable
{
	@Inject private Jpa jpa;
	
	@Option(names = "--chart-name", defaultValue = "SCA Default CoA",
		description = "Chart name") private String chartName;
	
	@Option(names = "--chart-version", defaultValue = "2026.1",
		description = "Chart version label") private String chartVersion;
	
	@Option(names = "--activate", defaultValue = "true",
		description = "Mark chart status ACTIVE (otherwise DRAFT)") private boolean activate;
	
	@Option(names = "--fund-code", defaultValue = "GENERAL",
		description = "Default fund code") private String fundCode;
	
	@Option(names = "--fund-name", defaultValue = "General Fund",
		description = "Default fund name") private String fundName;
	
	@Option(names = "--include-schedule-kinds", defaultValue = "true",
		description = "Insert common schedule kinds") private boolean includeScheduleKinds;
	
	@Override
	public void run()
	{
		
		try (EntityManager em = jpa.em())
		{
			em.getTransaction().begin();
			
			ChartOfAccounts chart =
				findOrCreateChart(em, chartName, chartVersion);
			chart.setStatus(activate ? ChartStatus.ACTIVE : ChartStatus.DRAFT);
			chart.touchUpdatedAt();
			
			// Foundational accounts. Codes are intentionally stable and
			// human-friendly.
			// Adjust names later to match your report vocabulary.
			upsertAccount(em, chart, "1000", "Cash / Bank", AccountType.BANK,
				AccountSubtype.CASH, NormalBalance.DEBIT, null, true, "0.00");
			upsertAccount(em, chart, "4000", "Income (General)",
				AccountType.INCOME, null, NormalBalance.CREDIT, null, true,
				"0.00");
			upsertAccount(em, chart, "5000", "Expense (General)",
				AccountType.EXPENSE, null, NormalBalance.DEBIT, null, true,
				"0.00");
			upsertAccount(em, chart, "3000", "Fund Transfer Clearing",
				AccountType.EQUITY, null, NormalBalance.CREDIT, null, true,
				"0.00");
			
			// Default fund
			Fund fund = findFundByCode(em, fundCode);
			
			if (fund == null)
			{
				fund = new Fund();
				fund.setCode(fundCode);
				fund.setName(fundName);
				fund.setFundType(FundType.UNRESTRICTED);
				em.persist(fund);
			}
			else
			{
				fund.setName(fundName);
				
				if (fund.getFundType() == null)
				{
					fund.setFundType(FundType.UNRESTRICTED);
				}
				
			}
			
			fund.touchUpdatedAt();
			
			if (includeScheduleKinds)
			{
				upsertScheduleKind(em, "RECEIVABLE", "Receivable");
				upsertScheduleKind(em, "PAYABLE", "Payable");
				upsertScheduleKind(em, "PREPAID", "Prepaid Expense");
				upsertScheduleKind(em, "DEFERRED_REVENUE", "Deferred Revenue");
				upsertScheduleKind(em, "OTHER_ASSET",
					"Other Asset (Deposits/Other)");
				upsertScheduleKind(em, "OTHER_LIABILITY", "Other Liability");
				upsertScheduleKind(em, "INVENTORY", "Inventory");
				upsertScheduleKind(em, "FIXED_ASSET", "Fixed Asset");
			}
			
			// Subtype->Schedule defaults (used for UI tab gating and default
			// validation)
			// You can still override any account with explicit
			// account_schedule_requirement rows.
			upsertSubtypeScheduleDefault(em, "RECEIVABLE", "RECEIVABLE");
			upsertSubtypeScheduleDefault(em, "PAYABLE", "PAYABLE");
			upsertSubtypeScheduleDefault(em, "PREPAID", "PREPAID");
			upsertSubtypeScheduleDefault(em, "DEFERRED_REVENUE",
				"DEFERRED_REVENUE");
			upsertSubtypeScheduleDefault(em, "INVENTORY", "INVENTORY");
			upsertSubtypeScheduleDefault(em, "FIXED_ASSET", "FIXED_ASSET");
			upsertSubtypeScheduleDefault(em, "OTHER_ASSET", "OTHER_ASSET");
			upsertSubtypeScheduleDefault(em, "OTHER_LIABILITY",
				"OTHER_LIABILITY");
			
			
			em.getTransaction().commit();
			
			System.out.println("Seed complete.");
			System.out.println("Chart: id=" + chart.getId() + " name=" +
				chart.getName() + " version=" + chart.getVersion() +
				" status=" + chart.getStatus());
			System.out.println("Fund:  code=" + fund.getCode() + " name=" +
				fund.getName() + " type=" + fund.getFundType());
			System.out.println(
				"Accounts seeded (or updated): 1000, 3000, 4000, 5000");
		}
		catch (RuntimeException ex)
		{
			// Let picocli surface non-zero exit (Main handles exit code); print
			// a helpful error.
			System.err.println("Seed failed: " + ex.getMessage());
			throw ex;
		}
		
	}
	
	private static ChartOfAccounts findOrCreateChart(EntityManager em,
		String name, String version)
	{
		List<ChartOfAccounts> charts = em.createQuery(
			"select c from ChartOfAccounts c where c.name = :name and c.version = :ver order by c.id",
			ChartOfAccounts.class)
			.setParameter("name", name)
			.setParameter("ver", version)
			.getResultList();
		
		if (!charts.isEmpty())
		{
			return charts.get(0);
		}
		
		ChartOfAccounts c = new ChartOfAccounts();
		c.setName(name);
		c.setVersion(version);
		c.setStatus(ChartStatus.DRAFT);
		em.persist(c);
		return c;
		
	}
	
	private static void upsertAccount(EntityManager em,
		ChartOfAccounts chart,
		String code,
		String name,
		AccountType type,
		AccountSubtype subtype,
		NormalBalance normal,
		String parentCode,
		boolean posting,
		String openingBalance)
	{
		List<Account> matches = em.createQuery(
			"select a from Account a where a.chart = :chart and a.code = :code",
			Account.class)
			.setParameter("chart", chart)
			.setParameter("code", code)
			.getResultList();
		
		Account a = matches.isEmpty() ? null : matches.get(0);
		
		if (a == null)
		{
			a = new Account();
			a.setChart(chart);
			a.setCode(code);
			em.persist(a);
		}
		
		a.setName(name);
		a.setAccountType(type);
		a.setSubtype(subtype);
		a.setOpeningBalance(new java.math.BigDecimal(openingBalance));
		a.setNormalBalance(normal);
		a.setPosting(posting);
		a.setActive(true);
		
		if (parentCode != null && !parentCode.isBlank())
		{
			List<Account> parents = em.createQuery(
				"select p from Account p where p.chart = :chart and p.code = :code",
				Account.class)
				.setParameter("chart", chart)
				.setParameter("code", parentCode)
				.getResultList();
			
			if (!parents.isEmpty())
			{
				a.setParent(parents.get(0));
			}
			
		}
		
	}
	
	private static Fund findFundByCode(EntityManager em, String code)
	{
		List<Fund> funds =
			em.createQuery("select f from Fund f where f.code = :c", Fund.class)
				.setParameter("c", code)
				.getResultList();
		return funds.isEmpty() ? null : funds.get(0);
		
	}
	
	private static void upsertScheduleKind(EntityManager em, String code,
		String name)
	{
		List<ScheduleKind> ks = em
			.createQuery("select k from ScheduleKind k where k.code = :c",
				ScheduleKind.class)
			.setParameter("c", code)
			.getResultList();
		
		ScheduleKind k = ks.isEmpty() ? null : ks.get(0);
		
		if (k == null)
		{
			k = new ScheduleKind();
			k.setCode(code);
			k.setName(name);
			em.persist(k);
		}
		else
		{
			k.setName(name);
		}
		
	}
	
	private static void upsertSubtypeScheduleDefault(EntityManager em,
		String subtypeCode, String scheduleKindCode)
	{
		ScheduleKind kind = findScheduleKindByCode(em, scheduleKindCode);
		if (kind == null)
			return;
		
		Long existingId = em.createQuery(
			"select d.id from AccountSubtypeScheduleDefault d where d.subtype = :sub and d.scheduleKind = :k",
			Long.class)
			.setParameter("sub", subtypeCode)
			.setParameter("k", kind)
			.setMaxResults(1)
			.getResultStream()
			.findFirst()
			.orElse(null);
		
		if (existingId == null)
		{
			AccountSubtypeScheduleDefault d =
				new AccountSubtypeScheduleDefault();
			d.setSubtype(subtypeCode);
			d.setScheduleKind(kind);
			em.persist(d);
		}
		
	}
	
	private static ScheduleKind findScheduleKindByCode(EntityManager em,
		String code)
	{
		return em
			.createQuery("from ScheduleKind where code = :c",
				ScheduleKind.class)
			.setParameter("c", code)
			.setMaxResults(1)
			.getResultStream()
			.findFirst()
			.orElse(null);
		
	}
	
}
