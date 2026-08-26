from pathlib import Path

service = Path('src/main/java/org/nonprofitbookkeeping/service/InventoryService.java')
text = service.read_text()
old = '                InventoryItem item = require(em, InventoryItem.class, itemId, "Inventory item");\n'
new = '''                InventoryItem item = em.find(\n                        InventoryItem.class, itemId, LockModeType.PESSIMISTIC_WRITE);\n                if (item == null)\n                {\n                    throw new IllegalArgumentException("Inventory item not found: " + itemId);\n                }\n'''
if text.count(old) != 1:
    raise SystemExit(f'InventoryService update lock target count={text.count(old)}')
service.write_text(text.replace(old, new, 1))

test = Path('src/test/java/org/nonprofitbookkeeping/service/InventoryLifecycleSourceTest.java')
if test.exists():
    raise SystemExit('InventoryLifecycleSourceTest already exists')
test.write_text('''package org.nonprofitbookkeeping.service;\n\nimport org.junit.jupiter.api.Test;\n\nimport java.nio.file.Files;\nimport java.nio.file.Path;\n\nimport static org.junit.jupiter.api.Assertions.assertTrue;\n\n/** Source guardrails for P17-C5 inventory lifecycle serialization. */\nclass InventoryLifecycleSourceTest\n{\n    @Test\n    void metadataAndLifecycleWritesUseTheSameInventoryItemLock() throws Exception\n    {\n        String source = Files.readString(Path.of(\n                "src/main/java/org/nonprofitbookkeeping/service/InventoryService.java"));\n\n        assertTrue(source.contains(\n                "InventoryItem.class, itemId, LockModeType.PESSIMISTIC_WRITE"));\n        assertTrue(source.contains(\n                "InventoryItem.class, preview.inventoryItemId(), LockModeType.PESSIMISTIC_WRITE"));\n        assertTrue(source.contains("Inventory status changes use the explicit lifecycle action"));\n    }\n}\n''')
print('P17-C5 metadata lock correction staged')
