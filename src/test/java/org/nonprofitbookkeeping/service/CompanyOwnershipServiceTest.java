package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.CompanyOwnershipIssue;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompanyOwnershipServiceTest
{
    @Test
    void singleCompanyLegacyRowCanBeAdoptedFromDatabaseEvidence(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("ownership-single")); EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Fund fund = new Fund();
            fund.setCode("LEGACY");
            fund.setName("Legacy Fund");
            fund.setFundType(FundType.UNRESTRICTED);
            em.persist(fund);
            em.flush();

            CompanyOwnershipService service = new CompanyOwnershipService(jpa);
            Company company = service.requireCompany(em, "default");
            service.ensureOwnedBy(em, company, fund, "Fund");
            em.getTransaction().commit();

            assertEquals(company.getId(), fund.getCompany().getId());
        }
    }

    @Test
    void multiCompanyUnownedAndCrossCompanyRowsAreRejected(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("ownership-multi")); EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company other = new Company();
            other.setCode("OTHER");
            other.setDisplayName("Other Company");
            em.persist(other);

            Fund unowned = new Fund();
            unowned.setCode("UNOWNED");
            unowned.setName("Unowned Fund");
            unowned.setFundType(FundType.UNRESTRICTED);
            em.persist(unowned);

            Fund otherFund = new Fund();
            otherFund.setCompany(other);
            otherFund.setCode("OTHER");
            otherFund.setName("Other Fund");
            otherFund.setFundType(FundType.UNRESTRICTED);
            em.persist(otherFund);
            em.flush();

            CompanyOwnershipService service = new CompanyOwnershipService(jpa);
            Company defaultCompany = service.requireCompany(em, "DEFAULT");
            assertThrows(CompanyOwnershipException.class,
                    () -> service.ensureOwnedBy(em, defaultCompany, unowned, "Unowned fund"));
            assertThrows(CompanyOwnershipException.class,
                    () -> service.ensureOwnedBy(em, defaultCompany, otherFund, "Other fund"));
            em.getTransaction().rollback();
        }
    }

    @Test
    void explicitAssignmentRepairsOwnerResolvesDiagnosticAndWritesAudit(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("ownership-repair")))
        {
            long activityId;
            long issueId;
            long companyId;
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company company = em.createQuery(
                                "from Company c where c.code = 'DEFAULT'", Company.class)
                        .getSingleResult();
                companyId = company.getId();
                Activity activity = new Activity();
                activity.setCode("LEGACY-ACTIVITY");
                activity.setName("Legacy Activity");
                em.persist(activity);
                em.flush();
                activityId = activity.getId();

                CompanyOwnershipIssue issue = new CompanyOwnershipIssue();
                issue.setEntityType("ACTIVITY");
                issue.setEntityId(Long.toString(activityId));
                issue.setIssueCode("UNRESOLVED_OWNER");
                issue.setCandidateCompanyCount(0);
                issue.setDetails("Activity has no deterministic company owner.");
                em.persist(issue);
                em.flush();
                issueId = issue.getId();
                em.getTransaction().commit();
            }

            CompanyOwnershipService service = new CompanyOwnershipService(jpa);
            CompanyOwnershipIssueView open = service.listOpenIssues().get(0);
            assertEquals("LEGACY-ACTIVITY — Legacy Activity", open.recordLabel());
            assertTrue(open.resolutionGuidance().contains("company receiving the import"));
            assertTrue(!open.resolutionGuidance().contains("historical owner"));

            CompanyOwnershipRepairResult result = service.assignOwner(
                    issueId, companyId, "test-operator", "DEFAULT is the active SCLX import company.");

            assertEquals("ACTIVITY", result.entityType());
            assertEquals("DEFAULT", result.companyCode());
            try (EntityManager em = jpa.em())
            {
                Activity repaired = em.find(Activity.class, activityId);
                CompanyOwnershipIssue resolved = em.find(CompanyOwnershipIssue.class, issueId);
                assertEquals(companyId, repaired.getCompany().getId());
                assertTrue(resolved.getResolvedAt() != null);
                assertEquals(1L, em.createQuery(
                                "select count(a) from AuditEvent a where a.actionType = :action "
                                        + "and a.entityType = :type and a.entityId = :entityId",
                                Long.class)
                        .setParameter("action", "COMPANY_OWNERSHIP_ASSIGNED")
                        .setParameter("type", "ACTIVITY")
                        .setParameter("entityId", Long.toString(activityId))
                        .getSingleResult());
            }
        }
    }

    @Test
    void crossCompanyReferenceCannotBeDisguisedAsOwnerAssignment(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("ownership-cross-reference")); EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = em.createQuery(
                            "from Company c where c.code = 'DEFAULT'", Company.class)
                    .getSingleResult();
            CompanyOwnershipIssue issue = new CompanyOwnershipIssue();
            issue.setEntityType("TXN_SPLIT");
            issue.setEntityId("1");
            issue.setIssueCode("CROSS_COMPANY_REFERENCE");
            issue.setCandidateCompanyCount(2);
            issue.setDetails("Transaction split references another company.");
            em.persist(issue);
            em.flush();
            long issueId = issue.getId();
            long companyId = company.getId();
            em.getTransaction().commit();

            assertThrows(CompanyOwnershipException.class, () ->
                    new CompanyOwnershipService(jpa).assignOwner(
                            issueId, companyId, "test-operator", "Should not be accepted."));
            assertEquals(1, new CompanyOwnershipService(jpa).listOpenIssues().size());
        }
    }

    @Test
    void directAssignmentRejectsRelationshipEvidenceOwnedByAnotherCompany(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("ownership-related-company")))
        {
            long activityId;
            long issueId;
            long defaultCompanyId;
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company defaultCompany = em.createQuery(
                                "from Company c where c.code = 'DEFAULT'", Company.class)
                        .getSingleResult();
                defaultCompanyId = defaultCompany.getId();
                Company other = new Company();
                other.setCode("OTHER");
                other.setDisplayName("Other Company");
                em.persist(other);

                ChartOfAccounts chart = new ChartOfAccounts();
                chart.setCompany(other);
                chart.setName("Other Chart");
                chart.setVersion("1");
                chart.setStatus(ChartStatus.ACTIVE);
                em.persist(chart);
                Account account = new Account();
                account.setChart(chart);
                account.setCode("1000");
                account.setName("Other Cash");
                account.setAccountType(AccountType.ASSET);
                account.setNormalBalance(NormalBalance.DEBIT);
                em.persist(account);
                Fund fund = new Fund();
                fund.setCompany(other);
                fund.setCode("OTHER");
                fund.setName("Other Fund");
                fund.setFundType(FundType.UNRESTRICTED);
                em.persist(fund);
                Activity activity = new Activity();
                activity.setCode("SHARED-ACTIVITY");
                activity.setName("Referenced by Other");
                em.persist(activity);
                Txn transaction = new Txn();
                transaction.setCompany(other);
                transaction.setTxnDate(LocalDate.of(2026, 8, 14));
                transaction.setMemo("Other-company evidence");
                em.persist(transaction);
                TxnSplit split = new TxnSplit();
                split.setTxn(transaction);
                split.setAccount(account);
                split.setFund(fund);
                split.setActivity(activity);
                split.setAmountSigned(BigDecimal.ONE);
                em.persist(split);
                em.flush();
                activityId = activity.getId();

                CompanyOwnershipIssue issue = new CompanyOwnershipIssue();
                issue.setEntityType("ACTIVITY");
                issue.setEntityId(Long.toString(activityId));
                issue.setIssueCode("UNRESOLVED_OWNER");
                issue.setCandidateCompanyCount(1);
                issue.setDetails("Activity has no deterministic company owner.");
                em.persist(issue);
                em.flush();
                issueId = issue.getId();
                em.getTransaction().commit();
            }

            CompanyOwnershipService service = new CompanyOwnershipService(jpa);
            CompanyOwnershipIssueView view = service.listOpenIssues().stream()
                    .filter(value -> value.id().equals(issueId))
                    .findFirst()
                    .orElseThrow();
            assertEquals(java.util.List.of("OTHER"), view.relationshipCompanyCodes());
            assertTrue(!view.companyChoiceCompatible("DEFAULT"));

            CompanyOwnershipException failure = assertThrows(CompanyOwnershipException.class, () ->
                    service.assignOwner(
                            issueId, defaultCompanyId, "test-operator", "Incorrect attempted assignment."));
            assertTrue(failure.getMessage().contains("OTHER"));
            try (EntityManager em = jpa.em())
            {
                assertTrue(em.find(Activity.class, activityId).getCompany() == null);
                assertTrue(em.find(CompanyOwnershipIssue.class, issueId).getResolvedAt() == null);
            }
        }
    }
}
