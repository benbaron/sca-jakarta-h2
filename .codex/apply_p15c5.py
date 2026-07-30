from pathlib import Path


def replace(path, old, new, count=-1):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f'missing pattern in {path}: {old[:80]!r}')
    p.write_text(text.replace(old, new, count))

# Core assembler: add period-close inputs and extension mapping.
path = 'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxCoreSnapshotAssembler.java'
replace(path, 'import org.nonprofitbookkeeping.model.TxnSupplementalLine;\n',
        'import org.nonprofitbookkeeping.model.TxnSupplementalLine;\nimport org.nonprofitbookkeeping.service.PeriodCloseEventView;\nimport org.nonprofitbookkeeping.service.PeriodCloseRangeView;\n')
replace(path, 'List<InventoryMovement> inventoryMovements,\n            Instant exportedAt)',
        'List<InventoryMovement> inventoryMovements,\n            List<PeriodCloseRangeView> periodCloseRanges,\n            List<PeriodCloseEventView> periodCloseEvents,\n            Instant exportedAt)')
replace(path, 'Objects.requireNonNull(inventoryMovements, "inventoryMovements");\n        Objects.requireNonNull(exportedAt, "exportedAt");',
        'Objects.requireNonNull(inventoryMovements, "inventoryMovements");\n        Objects.requireNonNull(periodCloseRanges, "periodCloseRanges");\n        Objects.requireNonNull(periodCloseEvents, "periodCloseEvents");\n        Objects.requireNonNull(exportedAt, "exportedAt");')
replace(path, 'supplementalDetails, banking, List.of(), List.of(), List.of(), List.of(), exportedAt);',
        'supplementalDetails, banking, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), exportedAt);')
replace(path, 'extensionValues.put(SclxInventoryExtension.KEY, exportedInventory);',
        'extensionValues.put(SclxInventoryExtension.KEY, exportedInventory);\n        extensionValues.put(SclxPeriodCloseExtension.KEY,\n                new SclxPeriodCloseSnapshotAssembler().assemble(companyCode, periodCloseRanges, periodCloseEvents));')

# Query selected-company period-close service projections.
path = 'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxCoreSnapshotQueryService.java'
replace(path, 'import org.nonprofitbookkeeping.service.CompanyOwnershipService;\n',
        'import org.nonprofitbookkeeping.service.CompanyOwnershipService;\nimport org.nonprofitbookkeeping.service.PeriodCloseEventView;\nimport org.nonprofitbookkeeping.service.PeriodCloseRangeService;\nimport org.nonprofitbookkeeping.service.PeriodCloseRangeView;\n')
replace(path, '                    .setParameter("company", company)\n                    .getResultList();\n\n            return assembler.assemble(',
        '                    .setParameter("company", company)\n                    .getResultList();\n            PeriodCloseRangeService periodCloseService = new PeriodCloseRangeService(jpa);\n            List<PeriodCloseRangeView> periodCloseRanges = periodCloseService.listRanges(company.getCode());\n            List<PeriodCloseEventView> periodCloseEvents = periodCloseService.listEvents(company.getCode());\n\n            return assembler.assemble(', 1)
# The first matching query may not be inventory; repair by relocating if needed.
text = Path(path).read_text()
marker = 'PeriodCloseRangeService periodCloseService'
if text.index(marker) < text.index('List<InventoryMovement> inventoryMovements'):
    block = '''            PeriodCloseRangeService periodCloseService = new PeriodCloseRangeService(jpa);\n            List<PeriodCloseRangeView> periodCloseRanges = periodCloseService.listRanges(company.getCode());\n            List<PeriodCloseEventView> periodCloseEvents = periodCloseService.listEvents(company.getCode());\n\n'''
    text = text.replace(block, '', 1)
    needle = '''            List<InventoryMovement> inventoryMovements = em.createQuery(\n                            "select m from InventoryMovement m "\n                                    + "join fetch m.inventoryItem i "\n                                    + "left join fetch m.transaction "\n                                    + "where i.company = :company order by m.portableId",\n                            InventoryMovement.class)\n                    .setParameter("company", company)\n                    .getResultList();\n'''
    if needle not in text:
        raise SystemExit('inventory query block not found')
    text = text.replace(needle, needle + block)
    Path(path).write_text(text)
replace(path, '                    inventoryItems,\n                    inventoryMovements,\n                    exportedAt);',
        '                    inventoryItems,\n                    inventoryMovements,\n                    periodCloseRanges,\n                    periodCloseEvents,\n                    exportedAt);')

# Mark section included.
replace('src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxExportSection.java',
        'PERIOD_CLOSE(Support.EXTENSION, "extensions.scaJakartaH2.periodClose"',
        'PERIOD_CLOSE(Support.EXTENSION, true, "extensions.scaJakartaH2.periodClose"')

# Strict validation.
path = 'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxExportDocumentValidator.java'
replace(path, '                transactionReferences.transactionIds());\n    }',
        '                transactionReferences.transactionIds());\n        validatePeriodClose(SclxPeriodCloseExtension.data(document.extensions()));\n    }', 1)
insert = '''    private static void validatePeriodClose(SclxPeriodCloseExtension.Data data)\n    {\n        Set<String> rangeIds = SclxPeriodCloseExtension.uniqueRangeIds(data);\n        SclxPeriodCloseExtension.requireUniqueEventIds(data);\n        for (SclxPeriodCloseExtension.RangeEntry range : data.ranges())\n        {\n            if (range.endDate().isBefore(range.startDate()))\n                throw new IllegalArgumentException("period-close range endDate precedes startDate: " + range.rangeId());\n            if (!Set.of("CALCULATED", "CUSTOM").contains(range.rangeKind()))\n                throw new IllegalArgumentException("unsupported period-close rangeKind: " + range.rangeKind());\n            if (!Set.of("CLOSED", "REOPENED").contains(range.status()))\n                throw new IllegalArgumentException("unsupported period-close status: " + range.status());\n            if ("CLOSED".equals(range.status()) && (range.reopenedAt() != null || range.reopenedBy() != null))\n                throw new IllegalArgumentException("closed period-close range contains reopen facts: " + range.rangeId());\n            if ("REOPENED".equals(range.status()) && (range.reopenedAt() == null || range.reopenedBy() == null))\n                throw new IllegalArgumentException("reopened period-close range lacks reopen facts: " + range.rangeId());\n        }\n        for (SclxPeriodCloseExtension.EventEntry event : data.events())\n        {\n            requireReference(event.rangeId(), rangeIds, "period-close event " + event.eventId() + " rangeId");\n            if (!Set.of("CLOSED", "REOPENED").contains(event.eventType()))\n                throw new IllegalArgumentException("unsupported period-close eventType: " + event.eventType());\n        }\n    }\n\n'''
replace(path, '    private static void validateBankConfiguration(', insert + '    private static void validateBankConfiguration(')

# Exact counts while preserving old constructors with zero defaults.
path = 'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxExportCounts.java'
replace(path, '        long inventoryItems,\n        long inventoryMovements,\n        long warnings,',
        '        long inventoryItems,\n        long inventoryMovements,\n        long periodCloseRanges,\n        long periodCloseEvents,\n        long warnings,')
replace(path, '                || inventoryItems < 0L || inventoryMovements < 0L\n                || warnings',
        '                || inventoryItems < 0L || inventoryMovements < 0L\n                || periodCloseRanges < 0L || periodCloseEvents < 0L\n                || warnings')
text = Path(path).read_text()
# Add two zero arguments to every delegating constructor call that currently ends with warning/exclusion/count.
text = text.replace('warnings, exclusions, totalEntities);', '0L, 0L, warnings, exclusions, totalEntities);')
# Undo the canonical return call later by reconstructing it explicitly.
text = text.replace('        SclxInventoryExtension.Data inventoryData = SclxInventoryExtension.data(document.extensions());',
                    '        SclxInventoryExtension.Data inventoryData = SclxInventoryExtension.data(document.extensions());\n        SclxPeriodCloseExtension.Data periodCloseData = SclxPeriodCloseExtension.data(document.extensions());')
text = text.replace('        long inventoryMovementCount = inventoryData.movements().size();',
                    '        long inventoryMovementCount = inventoryData.movements().size();\n        long periodCloseRangeCount = periodCloseData.ranges().size();\n        long periodCloseEventCount = periodCloseData.events().size();')
text = text.replace('                + depreciationRunCount + inventoryItemCount + inventoryMovementCount;',
                    '                + depreciationRunCount + inventoryItemCount + inventoryMovementCount\n                + periodCloseRangeCount + periodCloseEventCount;')
old = '                inventoryItemCount, inventoryMovementCount, warningCount, exclusionCount, entityCount);'
new = '                inventoryItemCount, inventoryMovementCount, periodCloseRangeCount, periodCloseEventCount,\n                warningCount, exclusionCount, entityCount);'
if old not in text:
    raise SystemExit('canonical counts return not found')
text = text.replace(old, new)
Path(path).write_text(text)

replace('src/main/java/org/nonprofitbookkeeping/ui/SclxExportCoordinator.java',
        '                + "\\n  Inventory movements: " + counts.inventoryMovements()\n                + "\\n  Total entities: "',
        '                + "\\n  Inventory movements: " + counts.inventoryMovements()\n                + "\\n  Period-close ranges: " + counts.periodCloseRanges()\n                + "\\n  Period-close events: " + counts.periodCloseEvents()\n                + "\\n  Total entities: "')
