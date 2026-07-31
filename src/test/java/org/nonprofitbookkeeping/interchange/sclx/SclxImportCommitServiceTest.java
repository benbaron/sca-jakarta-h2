package org.nonprofitbookkeeping.interchange.sclx;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.model.TxnSupplementalLine;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxImportCommitServiceTest
{
    private static final String TARGET = "SCLX_TARGET";
    private static final UUID TRANSACTION_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void commitsCoreGraphAtomicallyAndReimportIsIdempotent(@TempDir Path tempDir) throws Exception
    {
        Path database = tempDir.resolve("core-import");
        Path source = writeSource(tempDir.resolve("core.sclx"));
        try (Jpa jpa = new Jpa(database))
        {
            seedEmptyTarget(jpa);
            SclxImportPreviewService previews = new SclxImportPreviewService(jpa, () -> TARGET);
            SclxImportCommitService service = new SclxImportCommitService(jpa, () -> TARGET);

            SclxImportPreview firstPreview = previews.preview(source);
            assertFalse(firstPreview.hasBlockingErrors(), () -> firstPreview.operation().messages().toString());
            SclxImportResult first = service.commit(source, firstPreview, "tester");

            assertTrue(first.committed());
            assertFalse(first.rolledBack());
            assertEquals(11L, first.counts().created());
            try (EntityManager em = jpa.em())
            {
                Company company = company(em);
                assertEquals("Portable Source Company", company.getDisplayName());
                assertEquals("CAD", company.getDefaultCurrency());
                assertEquals(4, company.getFiscalYearStartMonth());
                assertEquals(1, company.getFiscalYearStartDay());
                assertEquals("Portable Chart", company.getActiveChartOfAccounts().getName());
                assertEquals(2L, count(em, "select count(a) from Account a"));
                assertEquals(1L, count(em, "select count(f) from Fund f"));
                assertEquals(1L, count(em, "select count(t) from Txn t"));
                assertEquals(2L, count(em, "select count(s) from TxnSplit s"));
                assertEquals(1L, count(em, "select count(a) from Activity a"));
                assertEquals(1L, count(em, "select count(c) from Counterparty c"));
                assertEquals(1L, count(em, "select count(m) from Merchant m"));
                assertEquals(1L, count(em, "select count(s) from TxnSupplementalLine s"));
                assertEquals(11L, count(em, "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
                Txn transaction = em.createQuery("from Txn t", Txn.class).getSingleResult();
                assertEquals(TRANSACTION_UUID, transaction.getPortableId());
                assertEquals("Portable Payee", transaction.getPayee().getDisplayName());
                TxnSplit enrichedLine = em.createQuery(
                                "from TxnSplit s where s.merchant is not null", TxnSplit.class)
                        .getSingleResult();
                assertEquals("EVENT", enrichedLine.getActivity().getCode());
                assertEquals("Portable Merchant", enrichedLine.getMerchant().getName());
                TxnSupplementalLine supplemental = em.createQuery(
                                "from TxnSupplementalLine s", TxnSupplementalLine.class)
                        .getSingleResult();
                assertEquals(7, supplemental.getLineOrder());
                assertEquals("PAYABLE", supplemental.getKind());
                assertEquals(1L, count(em,
                        "select count(a) from AuditEvent a where a.actionType = 'SCLX_TRANSACTION_DETAILS_IMPORTED'"));
            }

            SclxImportPreview secondPreview = previews.preview(source);
            assertFalse(secondPreview.hasBlockingErrors(), () -> secondPreview.operation().messages().toString());
            assertEquals(11L, secondPreview.operation().counts().identical());
            SclxImportResult second = service.commit(source, secondPreview, "tester");

            assertTrue(second.committed());
            assertEquals(0L, second.counts().created());
            assertEquals(11L, second.counts().identical());
            try (EntityManager em = jpa.em())
            {
                assertEquals(1L, count(em, "select count(t) from Txn t"));
                assertEquals(2L, count(em, "select count(s) from TxnSplit s"));
                assertEquals(11L, count(em, "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
            }
        }
    }

    @Test
    void lateFailureRollsBackProfileMastersTransactionsIdentitiesAndAudit(@TempDir Path tempDir) throws Exception
    {
        Path database = tempDir.resolve("rollback");
        Path source = writeSource(tempDir.resolve("rollback.sclx"));
        try (Jpa jpa = new Jpa(database))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            SclxImportCommitService service = new SclxImportCommitService(
                    jpa,
                    () -> TARGET,
                    writes -> {
                        if (writes == 8)
                        {
                            throw new IllegalStateException("injected late failure");
                        }
                    });

            SclxImportResult result = service.commit(source, preview, "tester");

            assertFalse(result.committed());
            assertTrue(result.rolledBack());
            assertTrue(result.messages().stream()
                    .anyMatch(message -> message.code().equals("SCLX_COMMIT_ROLLED_BACK")));
            try (EntityManager em = jpa.em())
            {
                Company company = company(em);
                assertEquals("Empty Target", company.getDisplayName());
                assertEquals("USD", company.getDefaultCurrency());
                assertEquals("Empty Chart", company.getActiveChartOfAccounts().getName());
                assertEquals(0L, count(em, "select count(a) from Account a"));
                assertEquals(0L, count(em, "select count(f) from Fund f"));
                assertEquals(0L, count(em, "select count(t) from Txn t"));
                assertEquals(0L, count(em, "select count(a) from Activity a"));
                assertEquals(0L, count(em, "select count(c) from Counterparty c"));
                assertEquals(0L, count(em, "select count(m) from Merchant m"));
                assertEquals(0L, count(em, "select count(s) from TxnSupplementalLine s"));
                assertEquals(0L, count(em, "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
                assertEquals(0L, count(em,
                        "select count(a) from AuditEvent a where a.actionType = 'SCLX_TRANSACTION_DETAILS_IMPORTED'"));
            }
        }
    }

    private static void seedEmptyTarget(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company();
            company.setCode(TARGET);
            company.setDisplayName("Empty Target");
            company.setDefaultCurrency("USD");
            em.persist(company);

            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName("Empty Chart");
            chart.setVersion("EMPTY");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            company.setActiveChartOfAccounts(chart);
            em.getTransaction().commit();
        }
    }

    private static Company company(EntityManager em)
    {
        return em.createQuery("""
                select c from Company c
                left join fetch c.activeChartOfAccounts
                where c.code = :code
                """, Company.class)
                .setParameter("code", TARGET)
                .getSingleResult();
    }

    private static long count(EntityManager em, String jpql)
    {
        return em.createQuery(jpql, Long.class).getSingleResult();
    }

    private static Path writeSource(Path target) throws Exception
    {
        String organizationId = SclxPortableIdentity.organization("SOURCE");
        String asset = SclxPortableIdentity.account("SOURCE", "1000");
        String expense = SclxPortableIdentity.account("SOURCE", "6100");
        String fund = SclxPortableIdentity.fund("SOURCE", "GENERAL");
        String transaction = SclxPortableIdentity.transaction("SOURCE", TRANSACTION_UUID.toString());
        String debitLine = SclxPortableIdentity.transactionLine(transaction, 1);
        String creditLine = SclxPortableIdentity.transactionLine(transaction, 2);
        String activity = SclxPortableIdentity.activity("SOURCE", "EVENT");
        String counterparty = SclxPortableIdentity.counterparty(
                "SOURCE", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String merchant = SclxPortableIdentity.merchant(
                "SOURCE", "99999999-8888-7777-6666-555555555555");
        String supplemental = SclxPortableIdentity.supplementalDetail(transaction, 1);
        Files.writeString(target, """
                {
                  "format": "SCLX",
                  "version": "1.3",
                  "exportedAt": "2026-07-31T12:00:00Z",
                  "organization": {
                    "organizationId": "%s",
                    "code": "SOURCE",
                    "name": "Portable Source Company",
                    "baseCurrency": "CAD",
                    "fiscalYearStart": "2026-04-01"
                  },
                  "chartOfAccounts": [
                    {
                      "accountId": "%s",
                      "code": "1000",
                      "name": "Cash",
                      "type": "BANK",
                      "subtype": "CASH",
                      "increaseSide": "DEBIT",
                      "currency": "CAD",
                      "openingBalance": "0.00",
                      "posting": true,
                      "active": true
                    },
                    {
                      "accountId": "%s",
                      "code": "6100",
                      "name": "Supplies",
                      "type": "EXPENSE",
                      "increaseSide": "DEBIT",
                      "currency": "CAD",
                      "openingBalance": "0.00",
                      "posting": true,
                      "active": true
                    }
                  ],
                  "funds": [
                    {
                      "fundId": "%s",
                      "code": "GENERAL",
                      "name": "General Fund",
                      "type": "UNRESTRICTED",
                      "active": true
                    }
                  ],
                  "budgets": [],
                  "transactions": [
                    {
                      "transactionId": "%s",
                      "transactionDate": "2026-07-15",
                      "description": "Purchase supplies",
                      "status": "ENTERED",
                      "lines": [
                        {
                          "lineId": "%s",
                          "accountId": "%s",
                          "fundId": "%s",
                          "activityId": "%s",
                          "counterpartyId": "%s",
                          "debit": "25.00",
                          "credit": "0"
                        },
                        {
                          "lineId": "%s",
                          "accountId": "%s",
                          "fundId": "%s",
                          "activityId": "%s",
                          "counterpartyId": "%s",
                          "debit": "0",
                          "credit": "25.00"
                        }
                      ]
                    }
                  ],
                  "extensions": {
                    "version": 1,
                    "scaJakartaH2": {
                      "activeChartName": "Portable Chart",
                      "activeChartVersion": "2026",
                      "activities": [
                        {
                          "activityId": "%s",
                          "code": "EVENT",
                          "name": "Portable Event",
                          "active": true
                        }
                      ],
                      "counterparties": {
                        "counterparties": [
                          {
                            "counterpartyId": "%s",
                            "displayName": "Portable Payee",
                            "kind": "ORG",
                            "email": "payee@example.invalid",
                            "phone": null,
                            "notes": "Imported payee",
                            "active": true
                          }
                        ],
                        "merchants": [
                          {
                            "merchantId": "%s",
                            "name": "Portable Merchant",
                            "notes": null,
                            "active": true
                          }
                        ],
                        "transactionLineMerchants": [
                          {
                            "lineId": "%s",
                            "merchantId": "%s"
                          }
                        ]
                      },
                      "supplementalDetails": [
                        {
                          "supplementalDetailId": "%s",
                          "transactionId": "%s",
                          "lineOrder": 7,
                          "kind": "PAYABLE",
                          "entryRef": "AP-1",
                          "counterparty": "Portable Payee",
                          "description": "Portable payable detail",
                          "reference": null,
                          "amount": "25.00",
                          "dueDate": "2026-08-15",
                          "startDate": null,
                          "endDate": null,
                          "notes": "Imported detail"
                        }
                      ]
                    }
                  }
                }
                """.formatted(
                organizationId,
                asset,
                expense,
                fund,
                transaction,
                debitLine,
                expense,
                fund,
                activity,
                counterparty,
                creditLine,
                asset,
                fund,
                activity,
                counterparty,
                activity,
                counterparty,
                merchant,
                debitLine,
                merchant,
                supplemental,
                transaction));
        return target;
    }
}
