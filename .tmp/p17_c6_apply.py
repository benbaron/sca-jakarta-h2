from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{path}: expected one target, found {count}')
    p.write_text(text.replace(old, new, 1))

# Fund service: serialize hierarchy writes and enforce lifecycle ordering.
service = 'src/main/java/org/nonprofitbookkeeping/service/FundAdminService.java'
replace_once(service,
    'import jakarta.persistence.EntityManager;\n',
    'import jakarta.persistence.EntityManager;\nimport jakarta.persistence.LockModeType;\n')
replace_once(service,
    '                Company company = ownership.requireCompany(em, companyCodeSupplier.get());\n                Fund fund = command.id() == null\n',
    '                Company company = ownership.requireCompany(em, companyCodeSupplier.get());\n                em.lock(company, LockModeType.PESSIMISTIC_WRITE);\n                Fund fund = command.id() == null\n')
replace_once(service,
    '                Company company = ownership.requireCompany(em, companyCodeSupplier.get());\n                Fund existing = em.createQuery(\n',
    '                Company company = ownership.requireCompany(em, companyCodeSupplier.get());\n                em.lock(company, LockModeType.PESSIMISTIC_WRITE);\n                Fund existing = em.createQuery(\n')
replace_once(service,
    '                Company company = ownership.requireCompany(em, companyCodeSupplier.get());\n                Fund fund = requireFund(em, fundId);\n                ownership.ensureOwnedBy(em, company, fund, "Fund");\n                FundUsage usage = usage(em, fundId);\n',
    '                Company company = ownership.requireCompany(em, companyCodeSupplier.get());\n                em.lock(company, LockModeType.PESSIMISTIC_WRITE);\n                Fund fund = requireFund(em, fundId);\n                ownership.ensureOwnedBy(em, company, fund, "Fund");\n                FundUsage usage = usage(em, fundId);\n')
replace_once(service,
    '''        Fund parent = loadValidatedParent(em, ownership, company, command.id(), command.parentFundId());
        fund.setCode(code);
        fund.setName(name);
        fund.setFundType(command.fundType());
        fund.setActive(command.active());
        fund.setParent(parent);
''',
    '''        Fund parent = loadValidatedParent(em, ownership, company, command.id(), command.parentFundId());
        validateHierarchyLifecycle(em, company, fund, parent, command.active());
        fund.setCode(code);
        fund.setName(name);
        fund.setFundType(command.fundType());
        fund.setParent(parent);
        fund.setActive(command.active());
''')
hierarchy_helpers = '''    private static void validateHierarchyLifecycle(
            EntityManager em,
            Company company,
            Fund fund,
            Fund parent,
            boolean active)
    {
        if (active)
        {
            Fund cursor = parent;
            Set<Long> visited = new HashSet<>();
            while (cursor != null)
            {
                Long cursorId = cursor.getId();
                if (cursorId != null && !visited.add(cursorId))
                {
                    throw new IllegalArgumentException("The selected parent belongs to an existing circular fund hierarchy.");
                }
                if (!cursor.isActive())
                {
                    throw new IllegalStateException(
                            "Active fund requires an active parent hierarchy. Reactivate parent fund "
                                    + cursor.getCode() + " first.");
                }
                cursor = cursor.getParent();
            }
            return;
        }

        if (fund.getId() == null)
        {
            return;
        }
        Long activeChildren = em.createQuery(
                        "select count(f) from Fund f where f.company = :company and f.parent = :parent and f.active = true",
                        Long.class)
                .setParameter("company", company)
                .setParameter("parent", fund)
                .getSingleResult();
        if (activeChildren > 0L)
        {
            throw new IllegalStateException(
                    "Deactivate or reparent active child funds before deactivating fund "
                            + fund.getCode() + ".");
        }
    }

'''
replace_once(service,
    '    private static Fund loadValidatedParent(EntityManager em, CompanyOwnershipService ownership, Company company, Long fundId, Long parentFundId)\n',
    hierarchy_helpers + '    private static Fund loadValidatedParent(EntityManager em, CompanyOwnershipService ownership, Company company, Long fundId, Long parentFundId)\n')

# Funds UI: make hierarchy ordering visible instead of a hidden service rule.
panel = 'src/main/java/org/nonprofitbookkeeping/ui/FundsPanel.java'
replace_once(panel,
    '        Label lifecycle = new Label("Clearing Active and saving deactivates a referenced fund without removing historical transactions, budgets, assets, inventory, aliases, transfers, or child-fund relationships.");\n',
    '        Label lifecycle = new Label("Clearing Active and saving deactivates a referenced fund without removing historical transactions, budgets, assets, inventory, aliases, transfers, or child-fund relationships. Active child funds require an active parent hierarchy: deactivate or reparent active children before a parent, and reactivate parents before children.");\n')

# SCLX structure validation: parent references, cycles, and active ancestry fail before commit.
structure = 'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxStructureValidator.java'
replace_once(structure,
    'import java.util.List;\nimport java.util.Objects;\nimport java.util.Set;\n',
    'import java.util.LinkedHashMap;\nimport java.util.List;\nimport java.util.Map;\nimport java.util.Objects;\nimport java.util.Set;\n')
replace_once(structure,
    '        identities(transactions, "transactionId", "$.transactions", errors);\n\n        long lineCount = validateTransactions(transactions, accountIds, fundIds, budgetIds, errors);\n',
    '        identities(transactions, "transactionId", "$.transactions", errors);\n\n        validateFunds(funds, fundIds, errors);\n        long lineCount = validateTransactions(transactions, accountIds, fundIds, budgetIds, errors);\n')
structure_helper = '''    private static void validateFunds(JsonNode funds, Set<String> fundIds, List<String> errors)
    {
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        for (JsonNode fund : funds)
        {
            JsonNode id = fund == null ? null : fund.get("fundId");
            if (fund != null && fund.isObject() && id != null && id.isTextual() && !id.textValue().isBlank())
            {
                byId.putIfAbsent(id.textValue(), fund);
            }
        }

        for (int index = 0; index < funds.size(); index++)
        {
            JsonNode fund = funds.get(index);
            if (!fund.isObject())
            {
                continue;
            }
            String path = "$.funds[" + index + "]";
            reference(fund, "parentFundId", path, fundIds, false, errors);
            JsonNode parentNode = fund.get("parentFundId");
            if (parentNode == null || parentNode.isNull() || !parentNode.isTextual() || parentNode.textValue().isBlank())
            {
                continue;
            }

            boolean active = fund.has("active") && fund.get("active").isBoolean() && fund.get("active").asBoolean();
            Set<String> visited = new HashSet<>();
            String parentId = parentNode.textValue();
            while (parentId != null)
            {
                if (!visited.add(parentId))
                {
                    errors.add(path + ".parentFundId creates a circular fund hierarchy");
                    break;
                }
                JsonNode parent = byId.get(parentId);
                if (parent == null)
                {
                    break;
                }
                if (active && parent.has("active") && parent.get("active").isBoolean()
                        && !parent.get("active").asBoolean())
                {
                    errors.add(path + ".parentFundId places an active fund beneath inactive parent fund " + parentId);
                    break;
                }
                JsonNode next = parent.get("parentFundId");
                parentId = next != null && next.isTextual() && !next.textValue().isBlank()
                        ? next.textValue() : null;
            }
        }
    }

'''
replace_once(structure,
    '    private static long validateTransactions(JsonNode transactions, Set<String> accounts,\n',
    structure_helper + '    private static long validateTransactions(JsonNode transactions, Set<String> accounts,\n')

# SCLX export: never serialize an invalid active hierarchy as a valid portable document.
export_validator = 'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxExportDocumentValidator.java'
replace_once(export_validator,
    'import java.util.HashSet;\nimport java.util.List;\nimport java.util.Objects;\nimport java.util.Set;\n',
    'import java.util.HashSet;\nimport java.util.LinkedHashMap;\nimport java.util.List;\nimport java.util.Map;\nimport java.util.Objects;\nimport java.util.Set;\n')
replace_once(export_validator,
    '''    private static void validateFundParents(List<SclxExportDocument.Fund> funds, Set<String> fundIds)
    {
        for (SclxExportDocument.Fund fund : funds)
        {
            requireOptionalReference(fund.parentFundId(), fundIds,
                    "fund " + fund.fundId() + " parentFundId");
        }
    }
''',
    '''    private static void validateFundParents(List<SclxExportDocument.Fund> funds, Set<String> fundIds)
    {
        Map<String, SclxExportDocument.Fund> byId = new LinkedHashMap<>();
        for (SclxExportDocument.Fund fund : funds)
        {
            byId.put(fund.fundId(), fund);
            requireOptionalReference(fund.parentFundId(), fundIds,
                    "fund " + fund.fundId() + " parentFundId");
        }
        for (SclxExportDocument.Fund fund : funds)
        {
            Set<String> visited = new HashSet<>();
            String parentId = fund.parentFundId();
            while (parentId != null)
            {
                if (!visited.add(parentId))
                {
                    throw new IllegalArgumentException(
                            "fund " + fund.fundId() + " has a circular parent hierarchy");
                }
                SclxExportDocument.Fund parent = byId.get(parentId);
                if (parent == null)
                {
                    break;
                }
                if (fund.active() && !parent.active())
                {
                    throw new IllegalArgumentException(
                            "active fund " + fund.fundId() + " has inactive parent fund " + parentId);
                }
                parentId = parent.parentFundId();
            }
        }
    }
''')

# SCLX commit: serialize fund writes with interactive Fund administration and fail closed defensively.
commit = 'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxImportCommitService.java'
replace_once(commit,
    'import jakarta.persistence.EntityManager;\n',
    'import jakarta.persistence.EntityManager;\nimport jakarta.persistence.LockModeType;\n')
replace_once(commit,
    '''    {
        List<JsonNode> ordered = parentFirst(values, "fundId", "parentFundId", "fund");
        Map<String, Fund> result = new LinkedHashMap<>();
''',
    '''    {
        em.lock(company, LockModeType.PESSIMISTIC_WRITE);
        List<JsonNode> ordered = parentFirst(values, "fundId", "parentFundId", "fund");
        Map<String, Fund> result = new LinkedHashMap<>();
''')
replace_once(commit,
    '''            fund.setFundType(enumValue(FundType.class, text(value, "type"), "fund type"));
            String parentId = optionalText(value, "parentFundId");
            fund.setParent(parentId == null ? null : required(result, parentId, "parent fund"));
            fund.setActive(requiredBoolean(value, "active"));
            fund.setEffectiveFrom(optionalDate(value, "effectiveFrom"));
''',
    '''            fund.setFundType(enumValue(FundType.class, text(value, "type"), "fund type"));
            String parentId = optionalText(value, "parentFundId");
            Fund parent = parentId == null ? null : required(result, parentId, "parent fund");
            boolean active = requiredBoolean(value, "active");
            requireActiveFundHierarchy(parent, active, externalId);
            fund.setParent(parent);
            fund.setActive(active);
            fund.setEffectiveFrom(optionalDate(value, "effectiveFrom"));
''')
commit_helper = '''    private static void requireActiveFundHierarchy(Fund parent, boolean active, String externalId)
    {
        if (!active)
        {
            return;
        }
        Set<Fund> visited = new HashSet<>();
        Fund cursor = parent;
        while (cursor != null)
        {
            if (!visited.add(cursor))
            {
                throw new IllegalStateException("SCLX fund hierarchy is circular for " + externalId + ".");
            }
            if (!cursor.isActive())
            {
                throw new IllegalStateException(
                        "SCLX active fund " + externalId + " requires active parent fund "
                                + cursor.getCode() + ".");
            }
            cursor = cursor.getParent();
        }
    }

'''
replace_once(commit,
    '    private Map<String, Activity> writeActivities(\n',
    commit_helper + '    private Map<String, Activity> writeActivities(\n')

# Interface matrix: replace the old minimal Funds row with the governed lifecycle authority.
matrix = 'doc/interface-operation-matrix.md'
replace_once(matrix,
    '| `FUNDS` | `FundsPanel` | table, fields, fund type/status selectors, save/new | fund lookup/admin services | `FundAdminService` | yes | yes | JPA fund model | real admin write path exists | composition/validation hardening | P12 |',
    '| `FUNDS` | `FundsPanel` | H2 fund table/editor; stable-ID New/Save; fund type, parent, effective dates, restriction/purpose, Active state; real Delete Unused; visible retained-history and hierarchy-order guidance | `FundLookupService.listAllFunds/listActiveFunds` | `FundAdminService.save/upsert/deleteUnused`; SCLX uses governed preflight plus caller-owned import transaction | yes | yes | company-owned `fund` hierarchy, transaction/budget/asset/inventory/alias/transfer references, company UI state | P17-C6 serializes hierarchy writes/deletion on company authority; active funds require active parent ancestry; parents cannot deactivate while active children remain; inactive hierarchy is retained history; physical deletion remains limited to completely unreferenced funds; SCLX structure/export/import enforce the same hierarchy invariant | owner P17-C6 Fund hierarchy lifecycle checklist | P12/P17-C6 |')

# Governing Fund lifecycle contract.
Path('doc/funds').mkdir(parents=True, exist_ok=True)
Path('doc/funds/fund-lifecycle.md').write_text('''# Fund lifecycle and hierarchy contract

## Authority and identity

`Fund.id` is the durable H2 identity. Code, name, type, parent, effective dates, restriction/purpose text, and Active state are editable business fields; changing them does not create a replacement Fund row.

`FundAdminService` is the interactive write authority. `FundLookupService` is the company-scoped read authority. Transactions, budgets, fixed assets, inventory, aliases, and transfers continue to reference the same retained Fund identity.

## Retirement versus deletion

A referenced Fund is retired by setting **Active** off. Deactivation preserves the Fund row and all historical references. The existing **Delete Unused** operation is a real physical delete, but only when `FundUsage` proves there are no transaction, budget, asset, inventory, alias, transfer, or child-Fund references.

There is no placeholder Delete operation. If a Fund is referenced, the UI explains that deactivation is the supported lifecycle action.

## Hierarchy invariant

An active Fund must have an active parent hierarchy.

- Creating, reactivating, or reparenting an active Fund beneath an inactive parent is rejected.
- A parent Fund cannot be deactivated while any direct child Fund remains active.
- Retirement therefore proceeds child-first (or by reparenting active children).
- Reactivation proceeds parent-first.
- Inactive children beneath inactive parents are valid retained history.
- Existing self-parent and circular-parent protections remain mandatory.

Interactive hierarchy writes and protected deletion serialize through a pessimistic lock on the owning Company so the check and mutation cannot race another Fund hierarchy change.

## SCLX boundary

SCLX does not bypass this lifecycle contract.

- Structure validation resolves `parentFundId`, rejects circular Fund hierarchies, and rejects an active source Fund beneath an inactive source ancestor before commit.
- Export validation refuses to serialize a Fund graph that violates the same hierarchy rule.
- Fund creation during SCLX commit takes the same Company write lock used by interactive Fund administration and rechecks active parent ancestry before persisting.

This does not reinterpret source inactive Funds, synthesize lifecycle audit facts, or create a second Fund store. Existing-company mappings that reuse compatible target Funds remain governed by the normal SCLX mapping/identity contract.

## Non-goals

P17-C6 does not change Fund accounting semantics, transaction posting, budget calculations, report grouping, transfer accounting, Fund type definitions, or effective-date policy. It adds no schema migration and no parallel persistence path.
''')

# Owner desktop acceptance checklist.
Path('doc/P17-C6-fund-hierarchy-lifecycle-user-testing.md').write_text('''# P17-C6 — Fund hierarchy lifecycle user testing

Use a disposable/test database or a copy of production data.

- [ ] Open **Funds** and confirm the editor visibly explains child-first deactivation/reparenting and parent-first reactivation.
- [ ] Create an active parent Fund and an active child Fund beneath it. Confirm both rows retain stable IDs after Refresh.
- [ ] Clear **Active** on the parent while the child is still active and Save. Confirm the save is rejected and Refresh shows both Funds still active.
- [ ] Deactivate the child first, then deactivate the parent. Confirm both same Fund rows remain listed as inactive and no historical reference is deleted.
- [ ] While the parent is inactive, try to reactivate the child. Confirm the save is rejected. Reactivate the parent first, then the child, and confirm both original IDs are reused.
- [ ] Create an inactive parent and an inactive child beneath it; confirm this retained-history hierarchy is allowed. Then attempt to make the child active while the parent remains inactive and confirm rejection.
- [ ] With another active Fund, attempt to reparent it beneath an inactive parent. Confirm the save is rejected and Refresh shows the original parent/state intact.
- [ ] Confirm **Delete Unused** still physically deletes a genuinely unreferenced Fund after confirmation, but a Fund referenced by a child or accounting/history record cannot be deleted and instead receives the deactivation explanation.
- [ ] If an SCLX test file is available, preview an active child whose parent is inactive and confirm preview blocks it rather than allowing commit. A valid active hierarchy should continue through normal preview/mapping behavior.
- [ ] At laptop width, confirm Fund table/editor scrolling, divider state, company-formatted dates, parent selector, and lifecycle guidance remain usable.

Record failures with Fund IDs/codes, parent IDs, Active state before/after, visible message, and whether Refresh changes the result. Do not merge P17-C6 until final-head GitHub Actions and this checklist are accepted.
''')

# H2 Fund lifecycle regression.
Path('src/test/java/org/nonprofitbookkeeping/service/FundHierarchyLifecycleTest.java').write_text('''package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FundHierarchyLifecycleTest
{
    @Test
    void hierarchyRetiresChildFirstAndReactivatesParentFirst(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fund-hierarchy-order")))
        {
            FundAdminService service = new FundAdminService(jpa);
            Fund parent = service.save(command(null, "PARENT", true, null));
            Fund child = service.save(command(null, "CHILD", true, parent.getId()));

            IllegalStateException parentFirst = assertThrows(IllegalStateException.class,
                    () -> service.save(command(parent.getId(), "PARENT", false, null)));
            assertEquals("Deactivate or reparent active child funds before deactivating fund PARENT.",
                    parentFirst.getMessage());
            assertTrue(find(jpa, parent.getId()).isActive());
            assertTrue(find(jpa, child.getId()).isActive());

            Fund inactiveChild = service.save(command(child.getId(), "CHILD", false, parent.getId()));
            Fund inactiveParent = service.save(command(parent.getId(), "PARENT", false, null));
            assertEquals(child.getId(), inactiveChild.getId());
            assertEquals(parent.getId(), inactiveParent.getId());
            assertFalse(find(jpa, child.getId()).isActive());
            assertFalse(find(jpa, parent.getId()).isActive());

            IllegalStateException childFirst = assertThrows(IllegalStateException.class,
                    () -> service.save(command(child.getId(), "CHILD", true, parent.getId())));
            assertEquals("Active fund requires an active parent hierarchy. Reactivate parent fund PARENT first.",
                    childFirst.getMessage());
            assertFalse(find(jpa, child.getId()).isActive());

            service.save(command(parent.getId(), "PARENT", true, null));
            service.save(command(child.getId(), "CHILD", true, parent.getId()));
            assertTrue(find(jpa, parent.getId()).isActive());
            assertTrue(find(jpa, child.getId()).isActive());
        }
    }

    @Test
    void activeCreationAndReparentingRejectInactiveParentWhileInactiveHistoryIsAllowed(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fund-hierarchy-parent-state")))
        {
            FundAdminService service = new FundAdminService(jpa);
            Fund inactiveParent = service.save(command(null, "OLD", false, null));
            Fund inactiveChild = service.save(command(null, "OLDCHILD", false, inactiveParent.getId()));
            assertFalse(inactiveChild.isActive());

            IllegalStateException activate = assertThrows(IllegalStateException.class,
                    () -> service.save(command(inactiveChild.getId(), "OLDCHILD", true, inactiveParent.getId())));
            assertTrue(activate.getMessage().contains("Reactivate parent fund OLD first"));

            IllegalStateException createActive = assertThrows(IllegalStateException.class,
                    () -> service.save(command(null, "BADCHILD", true, inactiveParent.getId())));
            assertTrue(createActive.getMessage().contains("Reactivate parent fund OLD first"));

            Fund active = service.save(command(null, "ACTIVE", true, null));
            IllegalStateException reparent = assertThrows(IllegalStateException.class,
                    () -> service.save(command(active.getId(), "ACTIVE", true, inactiveParent.getId())));
            assertTrue(reparent.getMessage().contains("Reactivate parent fund OLD first"));
            assertEquals(null, find(jpa, active.getId()).getParent());
        }
    }

    private static FundCommand command(Long id, String code, boolean active, Long parentId)
    {
        return new FundCommand(id, code, code + " Fund", FundType.UNRESTRICTED,
                active, parentId, null, null, null);
    }

    private static Fund find(Jpa jpa, long id)
    {
        try (var em = jpa.em())
        {
            Fund fund = em.createQuery(
                            "select f from Fund f left join fetch f.parent where f.id = :id", Fund.class)
                    .setParameter("id", id)
                    .getSingleResult();
            return fund;
        }
    }
}
''')

# SCLX runtime regressions for preflight and export.
Path('src/test/java/org/nonprofitbookkeeping/interchange/sclx/SclxFundHierarchyLifecycleTest.java').write_text('''package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxFundHierarchyLifecycleTest
{
    private final SclxDocumentParser parser = new SclxDocumentParser();

    @Test
    void structureRejectsActiveFundBeneathInactiveParent()
    {
        SclxParsedDocument document = parser.parse("""
                {
                  "format":"SCLX",
                  "version":"1.3",
                  "funds":[
                    {"fundId":"fund-parent","active":false},
                    {"fundId":"fund-child","parentFundId":"fund-parent","active":true}
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8));

        SclxStructureValidation result = new SclxStructureValidator().validate(document);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(message ->
                message.contains("active fund beneath inactive parent fund fund-parent")),
                result.errors().toString());
    }

    @Test
    void structureRejectsCircularFundHierarchy()
    {
        SclxParsedDocument document = parser.parse("""
                {
                  "format":"SCLX",
                  "version":"1.3",
                  "funds":[
                    {"fundId":"fund-a","parentFundId":"fund-b","active":false},
                    {"fundId":"fund-b","parentFundId":"fund-a","active":false}
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8));

        SclxStructureValidation result = new SclxStructureValidator().validate(document);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(message -> message.contains("circular fund hierarchy")),
                result.errors().toString());
    }

    @Test
    void exportRejectsActiveFundBeneathInactiveParent()
    {
        SclxExportDocument document = SclxExportDocument.version13(
                Instant.parse("2026-08-27T00:00:00Z"),
                new SclxExportDocument.Organization(
                        "company:TEST", "TEST", "Test Company", "USD", LocalDate.of(2026, 1, 1)),
                List.of(),
                List.of(
                        new SclxExportDocument.Fund(
                                "fund-parent", "PARENT", "Parent", "UNRESTRICTED", null,
                                false, null, null, null),
                        new SclxExportDocument.Fund(
                                "fund-child", "CHILD", "Child", "UNRESTRICTED", "fund-parent",
                                true, null, null, null)),
                List.of(),
                List.of(),
                new SclxExportDocument.Extensions(1, Map.of()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new SclxExportDocumentValidator().validate(document));
        assertTrue(ex.getMessage().contains("active fund fund-child has inactive parent fund fund-parent"));
    }
}
''')

# Source/UI guard: same lock boundary and visible lifecycle ordering must remain wired.
Path('src/test/java/org/nonprofitbookkeeping/service/FundHierarchySourceTest.java').write_text('''package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FundHierarchySourceTest
{
    @Test
    void fundAndSclxWritesShareCompanyLockAndUiExplainsOrdering() throws Exception
    {
        String service = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/service/FundAdminService.java"));
        String sclx = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxImportCommitService.java"));
        String panel = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/FundsPanel.java"));

        assertTrue(service.contains("em.lock(company, LockModeType.PESSIMISTIC_WRITE)"));
        assertTrue(service.contains("Deactivate or reparent active child funds before deactivating fund"));
        assertTrue(service.contains("Reactivate parent fund"));
        assertTrue(sclx.contains("em.lock(company, LockModeType.PESSIMISTIC_WRITE)"));
        assertTrue(sclx.contains("requireActiveFundHierarchy(parent, active, externalId)"));
        assertTrue(panel.contains("deactivate or reparent active children before a parent"));
        assertTrue(panel.contains("reactivate parents before children"));
    }
}
''')

# Normalize new/edited Markdown and Java files to one final newline.
for filename in [
        matrix,
        'doc/funds/fund-lifecycle.md',
        'doc/P17-C6-fund-hierarchy-lifecycle-user-testing.md',
        service,
        panel,
        structure,
        export_validator,
        commit,
        'src/test/java/org/nonprofitbookkeeping/service/FundHierarchyLifecycleTest.java',
        'src/test/java/org/nonprofitbookkeeping/service/FundHierarchySourceTest.java',
        'src/test/java/org/nonprofitbookkeeping/interchange/sclx/SclxFundHierarchyLifecycleTest.java']:
    p = Path(filename)
    p.write_text(p.read_text().rstrip() + '\n')

print('P17-C6 fund hierarchy lifecycle changes staged')
