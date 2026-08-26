from pathlib import Path
import re


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one replacement target, found {count}")
    p.write_text(text.replace(old, new, 1))


def regex_replace_once(path: str, pattern: str, replacement: str) -> None:
    p = Path(path)
    text = p.read_text()
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count != 1:
        raise SystemExit(f"{path}: expected one regex target, found {count}")
    p.write_text(updated)


service = "src/main/java/org/nonprofitbookkeeping/service/InventoryService.java"
replace_once(
    service,
    """                validateCommand(command);\n                requireActiveCompanyCommand(command);\n                if (scale(command.quantity()).signum() > 0 && scale(command.unitValue()).signum() > 0)\n""",
    """                validateCommand(command);\n                requireActiveCompanyCommand(command);\n                requireInteractiveCreateStatus(command);\n                if (scale(command.quantity()).signum() > 0 && scale(command.unitValue()).signum() > 0)\n""")
replace_once(
    service,
    """                BigDecimal existingQuantity = scale(item.getQuantity());\n                if (existingQuantity.signum() > 0\n""",
    """                InventoryItem.Status existingStatus = item.getStatus();\n                requireInteractiveStatusUnchanged(item, command);\n                BigDecimal existingQuantity = scale(item.getQuantity());\n                if (existingQuantity.signum() > 0\n""")
replace_once(
    service,
    """                apply(em, item, command);\n                item.setQuantity(existingQuantity);\n                item.touchUpdatedAt();\n""",
    """                apply(em, item, command);\n                item.setQuantity(existingQuantity);\n                item.setStatus(existingStatus);\n                item.touchUpdatedAt();\n""")

lifecycle_method = r'''    /**
     * Changes one inventory item's lifecycle status without deleting its durable history.
     * Status changes serialize on the same item lock used by governed quantity movements.
     */
    public InventoryItemView changeStatus(
            long itemId,
            InventoryItem.Status targetStatus,
            String actor,
            String reason)
    {
        Objects.requireNonNull(targetStatus, "targetStatus");
        String normalizedActor = requireText(actor, "actor");
        String normalizedReason = requireText(reason, "reason");
        String activeCompany = normalizeCompanyCode(companyCodeSupplier.get());
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, activeCompany);
                if (!company.isActive())
                {
                    throw new IllegalStateException("Company " + company.getCode() + " is inactive");
                }
                InventoryItem item = em.find(InventoryItem.class, itemId, LockModeType.PESSIMISTIC_WRITE);
                if (item == null)
                {
                    throw new IllegalArgumentException("Inventory item not found: " + itemId);
                }
                ownership.ensureOwnedBy(em, company, item, "Inventory item");
                InventoryItem.Status currentStatus = item.getStatus();
                if (currentStatus == targetStatus)
                {
                    em.getTransaction().commit();
                    return load(itemId);
                }
                if (currentStatus == InventoryItem.Status.DISPOSED)
                {
                    throw new IllegalStateException(
                            "Disposed inventory items are retained terminal history and cannot be reactivated or deactivated");
                }
                if ((targetStatus == InventoryItem.Status.INACTIVE
                        || targetStatus == InventoryItem.Status.DISPOSED)
                        && scale(item.getQuantity()).signum() != 0)
                {
                    throw new IllegalStateException(
                            "Inventory item must have zero quantity before deactivation or disposal; use governed inventory movements first");
                }
                if (targetStatus == InventoryItem.Status.ACTIVE
                        && currentStatus != InventoryItem.Status.INACTIVE)
                {
                    throw new IllegalStateException("Only an inactive inventory item can be reactivated");
                }
                if (targetStatus == InventoryItem.Status.INACTIVE
                        && currentStatus != InventoryItem.Status.ACTIVE)
                {
                    throw new IllegalStateException("Only an active inventory item can be deactivated");
                }

                item.setStatus(targetStatus);
                item.touchUpdatedAt();
                em.persist(inventoryStatusAudit(
                        company, normalizedActor, item, currentStatus, targetStatus, normalizedReason));
                em.flush();
                em.getTransaction().commit();
                return load(itemId);
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

'''
replace_once(
    service,
    "    public InventoryItemView load(long itemId)\n",
    lifecycle_method + "    public InventoryItemView load(long itemId)\n")

status_audit = r'''    private static AuditEvent inventoryStatusAudit(
            Company company,
            String actor,
            InventoryItem item,
            InventoryItem.Status before,
            InventoryItem.Status after,
            String reason)
    {
        AuditEvent event = new AuditEvent();
        event.setCompany(company);
        event.setActor(actor);
        event.setActionType("INVENTORY_ITEM_STATUS_CHANGED");
        event.setEntityType("InventoryItem");
        event.setEntityId(Long.toString(item.getId()));
        event.setSummary("Changed inventory item " + item.getName() + " from " + before + " to " + after);
        event.setBeforeValue("status=" + before + ",quantity=" + scale(item.getQuantity()));
        event.setAfterValue("status=" + after + ",quantity=" + scale(item.getQuantity()));
        event.setReason(reason);
        return event;
    }

'''
replace_once(
    service,
    "    private void apply(EntityManager em, InventoryItem item, InventoryItemCommand command)\n",
    status_audit + "    private void apply(EntityManager em, InventoryItem item, InventoryItemCommand command)\n")

status_guards = r'''    private static void requireInteractiveCreateStatus(InventoryItemCommand command)
    {
        if (command.status() != null && command.status() != InventoryItem.Status.ACTIVE)
        {
            throw new IllegalStateException(
                    "New interactive inventory items must start ACTIVE; use the explicit lifecycle action after creation");
        }
    }

    private static void requireInteractiveStatusUnchanged(
            InventoryItem item,
            InventoryItemCommand command)
    {
        if (command.status() != null && command.status() != item.getStatus())
        {
            throw new IllegalStateException(
                    "Inventory status changes use the explicit lifecycle action");
        }
    }

'''
replace_once(
    service,
    "    private static void validateCommand(InventoryItemCommand command)\n",
    status_guards + "    private static void validateCommand(InventoryItemCommand command)\n")

panel = "src/main/java/org/nonprofitbookkeeping/ui/InventoryPanel.java"
replace_once(
    panel,
    "    private final ComboBox<InventoryItem.Status> itemStatus = new ComboBox<>();\n",
    "    private final Label itemStatus = new Label(InventoryItem.Status.ACTIVE.name());\n")
replace_once(
    panel,
    """    private final TextField movementActor = new TextField("ui");\n    private final CheckBox confirmNonfinancial = new CheckBox(\n""",
    """    private final TextField movementActor = new TextField("ui");\n    private final TextField lifecycleActor = new TextField("ui");\n    private final TextField lifecycleReason = new TextField();\n    private final Button deactivateItem = new Button("Deactivate Item");\n    private final Button reactivateItem = new Button("Reactivate Item");\n    private final Button disposeItem = new Button("Dispose Item");\n    private final CheckBox confirmNonfinancial = new CheckBox(\n""")
replace_once(
    panel,
    """    private boolean editorOpen;\n    private Long editingItemId;\n""",
    """    private boolean editorOpen;\n    private Long editingItemId;\n    private InventoryItem.Status editingStatus = InventoryItem.Status.ACTIVE;\n""")
replace_once(
    panel,
    """        Button editItem = new Button("Edit Selected");\n        editItem.setOnAction(e -> openEditItemEditor());\n        Button receive = new Button("Receive Quantity");\n""",
    """        Button editItem = new Button("Edit Selected");\n        editItem.setOnAction(e -> openEditItemEditor());\n        deactivateItem.setOnAction(e -> changeSelectedStatus(InventoryItem.Status.INACTIVE));\n        reactivateItem.setOnAction(e -> changeSelectedStatus(InventoryItem.Status.ACTIVE));\n        disposeItem.setOnAction(e -> changeSelectedStatus(InventoryItem.Status.DISPOSED));\n        updateLifecycleActions(null);\n        Button receive = new Button("Receive Quantity");\n""")
replace_once(
    panel,
    """        HBox itemActions = new HBox(8, refresh, newItem, editItem);\n        HBox movementInputs = new HBox(8,\n""",
    """        HBox itemActions = new HBox(8, refresh, newItem, editItem);\n        Label lifecycleHelp = new Label(\n                "Inventory items are never physically deleted. Use governed movements to reach zero quantity before deactivation or disposal; disposed items remain terminal retained history.");\n        lifecycleHelp.setWrapText(true);\n        lifecycleActor.setPrefWidth(120);\n        lifecycleReason.setPrefWidth(300);\n        HBox lifecycleInputs = new HBox(8,\n                new Label("Lifecycle actor"), lifecycleActor,\n                new Label("Reason"), lifecycleReason);\n        HBox lifecycleActions = new HBox(8, deactivateItem, reactivateItem, disposeItem);\n        HBox movementInputs = new HBox(8,\n""")
replace_once(
    panel,
    "        listPanel.getChildren().setAll(itemActions, movementInputs, movementActions, split);\n",
    "        listPanel.getChildren().setAll(itemActions, lifecycleHelp, lifecycleInputs, lifecycleActions, movementInputs, movementActions, split);\n")
replace_once(
    panel,
    """        itemTable.setOnMouseClicked(event -> {\n            if (event.getClickCount() == 2 && itemTable.getSelectionModel().getSelectedItem() != null)\n            {\n                openEditItemEditor();\n            }\n        });\n""",
    """        itemTable.setOnMouseClicked(event -> {\n            if (event.getClickCount() == 2 && itemTable.getSelectionModel().getSelectedItem() != null)\n            {\n                openEditItemEditor();\n            }\n        });\n        itemTable.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) ->\n                updateLifecycleActions(newItem));\n""")
replace_once(
    panel,
    "        itemStatus.getItems().setAll(InventoryItem.Status.values());\n",
    "")

lifecycle_ui = r'''    private void updateLifecycleActions(InventoryItemView selected)
    {
        boolean zeroQuantity = selected != null && selected.quantity().compareTo(BigDecimal.ZERO) == 0;
        deactivateItem.setDisable(selected == null
                || selected.status() != InventoryItem.Status.ACTIVE
                || !zeroQuantity);
        reactivateItem.setDisable(selected == null
                || selected.status() != InventoryItem.Status.INACTIVE);
        disposeItem.setDisable(selected == null
                || selected.status() == InventoryItem.Status.DISPOSED
                || !zeroQuantity);
    }

    private void changeSelectedStatus(InventoryItem.Status targetStatus)
    {
        InventoryItemView selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select an inventory item before changing its lifecycle status.");
            return;
        }
        try
        {
            String actor = requiredText(lifecycleActor, "Lifecycle actor");
            String reason = requiredText(lifecycleReason, "Lifecycle reason");
            if (targetStatus != InventoryItem.Status.ACTIVE)
            {
                Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
                confirmation.setTitle(targetStatus == InventoryItem.Status.DISPOSED
                        ? "Dispose inventory item" : "Deactivate inventory item");
                confirmation.setHeaderText("Change " + selected.name() + " from "
                        + selected.status() + " to " + targetStatus + "?");
                confirmation.setContentText(targetStatus == InventoryItem.Status.DISPOSED
                        ? "The item must already have zero quantity. Disposal is terminal retained history; no item or movement is deleted."
                        : "The item must already have zero quantity. The item and all movement/ledger history remain retained and it can later be reactivated.");
                CompanyDialogUiCompliance.install(confirmation.getDialogPane(), AppPanelId.INVENTORY);
                if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty())
                {
                    status.setText("Inventory lifecycle change cancelled.");
                    return;
                }
            }
            InventoryItemView changed = UiServiceRegistry.inventory().changeStatus(
                    selected.id(), targetStatus, actor, reason);
            lifecycleReason.clear();
            reload();
            selectItem(changed.id());
            status.setText("Changed inventory item " + changed.name() + " to " + changed.status() + ".");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not change inventory item status: " + UiErrors.safeMessage(ex));
        }
    }

'''
replace_once(
    panel,
    "    private void recordMovement(InventoryMovement.MovementType type)\n",
    lifecycle_ui + "    private void recordMovement(InventoryMovement.MovementType type)\n")
replace_once(
    panel,
    "                itemStatus.getValue(),\n",
    "                editingStatus,\n")
replace_once(
    panel,
    """            condition.setValue(InventoryItem.Condition.UNKNOWN);\n            itemStatus.setValue(InventoryItem.Status.ACTIVE);\n            notes.clear();\n""",
    """            condition.setValue(InventoryItem.Condition.UNKNOWN);\n            editingStatus = InventoryItem.Status.ACTIVE;\n            itemStatus.setText(editingStatus.name());\n            notes.clear();\n""")
replace_once(
    panel,
    """            condition.setValue(item.condition());\n            itemStatus.setValue(item.status());\n            notes.setText(item.notes());\n""",
    """            condition.setValue(item.condition());\n            editingStatus = item.status();\n            itemStatus.setText(editingStatus.name());\n            notes.setText(item.notes());\n""")
replace_once(
    panel,
    "        for (ComboBox<?> comboBox : List.of(inventoryAccount, fund, condition, itemStatus))\n",
    "        for (ComboBox<?> comboBox : List.of(inventoryAccount, fund, condition))\n")

service_test = "src/test/java/org/nonprofitbookkeeping/service/InventoryServiceTest.java"
new_tests = r'''    @Test
    public void ordinaryUpdateCannotChangeLifecycleStatus(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("inventory-status-edit-guard")))
        {
            seedCompanyAccountsAndFund(jpa);
            InventoryService service = service(jpa);
            InventoryItemView item = service.create(itemCommand("Lifecycle Guard", BigDecimal.ZERO));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.update(item.id(), itemCommandWithStatus(
                            "Lifecycle Guard", BigDecimal.ZERO, InventoryItem.Status.INACTIVE)));

            assertEquals("Inventory status changes use the explicit lifecycle action", ex.getMessage());
            assertEquals(InventoryItem.Status.ACTIVE, service.load(item.id()).status());
        }
    }

    @Test
    public void lifecycleRequiresZeroQuantityAuditsTransitionsAndKeepsDisposedTerminal(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("inventory-status-lifecycle")))
        {
            seedCompanyAccountsAndFund(jpa);
            InventoryService service = service(jpa);
            InventoryItemView item = service.create(itemCommand("Lifecycle Item", BigDecimal.ZERO));

            assertEquals(InventoryItem.Status.INACTIVE,
                    service.changeStatus(item.id(), InventoryItem.Status.INACTIVE,
                            "tester", "temporarily out of service").status());
            assertEquals(InventoryItem.Status.ACTIVE,
                    service.changeStatus(item.id(), InventoryItem.Status.ACTIVE,
                            "tester", "returned to service").status());

            service.recordMovement(item.id(), new InventoryMovementCommand(
                    InventoryMovement.MovementType.RECEIPT,
                    new BigDecimal("2.0000"),
                    LocalDate.of(2026, 2, 1),
                    CASH_ACCOUNT_ID,
                    false,
                    "Receive two"));

            IllegalStateException quantityGuard = assertThrows(IllegalStateException.class,
                    () -> service.changeStatus(item.id(), InventoryItem.Status.INACTIVE,
                            "tester", "should fail"));
            assertEquals(
                    "Inventory item must have zero quantity before deactivation or disposal; use governed inventory movements first",
                    quantityGuard.getMessage());

            service.recordMovement(item.id(), new InventoryMovementCommand(
                    InventoryMovement.MovementType.ISSUE,
                    new BigDecimal("2.0000"),
                    LocalDate.of(2026, 2, 2),
                    CASH_ACCOUNT_ID,
                    false,
                    "Issue two"));
            InventoryItemView disposed = service.changeStatus(
                    item.id(), InventoryItem.Status.DISPOSED, "tester", "retired from inventory");
            assertEquals(InventoryItem.Status.DISPOSED, disposed.status());
            assertEquals(new BigDecimal("0.0000"), disposed.quantity());

            IllegalStateException terminal = assertThrows(IllegalStateException.class,
                    () -> service.changeStatus(item.id(), InventoryItem.Status.ACTIVE,
                            "tester", "attempt to restore"));
            assertEquals(
                    "Disposed inventory items are retained terminal history and cannot be reactivated or deactivated",
                    terminal.getMessage());

            try (EntityManager em = jpa.em())
            {
                Long auditCount = em.createQuery("""
                                select count(a) from AuditEvent a
                                where a.actionType = :action
                                  and a.entityType = :entityType
                                  and a.entityId = :entityId
                                """, Long.class)
                        .setParameter("action", "INVENTORY_ITEM_STATUS_CHANGED")
                        .setParameter("entityType", "InventoryItem")
                        .setParameter("entityId", Long.toString(item.id()))
                        .getSingleResult();
                assertEquals(3L, auditCount);
            }
        }
    }

    private static InventoryItemCommand itemCommandWithStatus(
            String name,
            BigDecimal quantity,
            InventoryItem.Status status)
    {
        InventoryItemCommand base = itemCommand(name, quantity);
        return new InventoryItemCommand(
                base.companyCode(), base.inventoryAccountId(), base.fundId(), base.name(), base.itemType(),
                base.quantity(), base.unit(), base.unitValue(), base.acquisitionDate(), base.custodian(),
                base.storageLocation(), base.condition(), status, base.notes());
    }

'''
replace_once(
    service_test,
    "    private static InventoryItemCommand itemCommand(String name, BigDecimal quantity)\n",
    new_tests + "    private static InventoryItemCommand itemCommand(String name, BigDecimal quantity)\n")

ui_test = "src/test/java/org/nonprofitbookkeeping/ui/InventoryPanelSourceTest.java"
replace_once(
    ui_test,
    "import static org.junit.jupiter.api.Assertions.assertTrue;\n",
    "import static org.junit.jupiter.api.Assertions.assertFalse;\nimport static org.junit.jupiter.api.Assertions.assertTrue;\n")
ui_lifecycle_test = r'''    @Test
    void inventoryStatusIsServiceOwnedRatherThanDirectlyEdited() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/InventoryPanel.java"));

        assertTrue(source.contains("Deactivate Item"));
        assertTrue(source.contains("Reactivate Item"));
        assertTrue(source.contains("Dispose Item"));
        assertTrue(source.contains("changeStatus("));
        assertTrue(source.contains("disposed items remain terminal retained history"));
        assertTrue(source.contains("private final Label itemStatus"));
        assertFalse(source.contains("itemStatus.getItems().setAll(InventoryItem.Status.values())"));
    }

'''
replace_once(
    ui_test,
    "}\n",
    ui_lifecycle_test + "}\n")

inventory_doc = "doc/inventory/inventory-and-assets.md"
p = Path(inventory_doc)
text = p.read_text()
section = r'''

## P17-C5 inventory item lifecycle

Inventory item status is a durable lifecycle fact, not ordinary editable metadata. `InventoryItem.id` remains the stable identity for the complete item history; the application does not physically delete inventory items or their movement/ledger history.

Interactive item creation starts `ACTIVE`. Ordinary item editing preserves the existing status and cannot select `INACTIVE` or `DISPOSED`. Lifecycle changes use the explicit `InventoryService.changeStatus(...)` service boundary with the active company, stable item ID, factual actor, and nonblank reason.

- `ACTIVE -> INACTIVE` is allowed only when on-hand quantity is exactly zero.
- `INACTIVE -> ACTIVE` reactivates the retained item.
- `ACTIVE` or `INACTIVE -> DISPOSED` is allowed only at zero quantity and is terminal for interactive lifecycle operations.
- A `DISPOSED` item is retained as history and cannot be reactivated or deactivated.
- Nonzero inventory must be reduced through the governed movement workflow before retirement; lifecycle status never substitutes for an ISSUE or ADJUSTMENT and never changes ledger value.

Status transitions acquire the same pessimistic item lock used by confirmed quantity movements, so a movement cannot race a retirement into an invalid stranded balance. The status change and its factual `AuditEvent` commit atomically. Movement history, canonical transaction links, portable identity, and item metadata remain attached to the same durable item.

`createForImport(...)` remains a caller-owned historical restore seam. It may restore an already inactive or disposed source item with its source status and timestamps without fabricating a new local lifecycle event; normal interactive writes cannot use that seam to bypass lifecycle rules.
'''
if "## P17-C5 inventory item lifecycle" in text:
    raise SystemExit("inventory lifecycle section already exists")
p.write_text(text.rstrip() + section + "\n")

matrix = "doc/interface-operation-matrix.md"
p = Path(matrix)
text = p.read_text()
if text.startswith("# Interface operation matrix\n\nStatus:"):
    text = re.sub(
        r"^Status: .*?$",
        "Status: P00 inventory of current main, updated through active P17-C5 inventory-item lifecycle correction.",
        text,
        count=1,
        flags=re.MULTILINE)
else:
    raise SystemExit("interface matrix status header not found")
new_inventory_row = "| `INVENTORY` | `InventoryPanel` | inventory item table, movement-history table, item form with read-only lifecycle status, explicit Deactivate Item / Reactivate Item / Dispose Item actions, account/fund selectors, governed receipt/issue/adjustment preview-confirm actions, financial reversal, Ledger drill-through, retained-history/no-delete guidance | `InventoryService`, canonical `TransactionEntryService`/`TransactionCorrectionService`, account/fund lookup services | `InventoryService.create/update/changeStatus/previewMovement/recordMovement/previewMovementReversal/reverseMovement` | yes | yes | `inventory_item`, `inventory_movement`, `audit_event`, canonical `txn`/`txn_split`, chart accounts, funds | P17-C5 makes lifecycle status service-owned: ordinary edits preserve status; zero quantity is required before deactivation/disposal; disposed items are terminal retained history; lifecycle changes serialize with movements on the item lock and audit atomically; SCLX historical restore retains its caller-owned source-status seam; no physical or placeholder Delete | owner P17-C5 inventory lifecycle checklist plus retained P16-S9/P16-S15 verification | P16-S9/P16-S15/P17-C5 |"
updated, count = re.subn(r"^\| `INVENTORY` \|.*$", new_inventory_row, text, count=1, flags=re.MULTILINE)
if count != 1:
    raise SystemExit(f"interface matrix Inventory row count {count}")
p.write_text(updated)

checklist = Path("doc/P17-C5-inventory-item-lifecycle-user-testing.md")
if checklist.exists():
    raise SystemExit("C5 checklist already exists")
checklist.write_text(r'''# P17-C5 — Inventory item lifecycle user testing

## User-visible behavior

- Inventory item status is no longer a directly editable combo-box field.
- **Deactivate Item**, **Reactivate Item**, and **Dispose Item** are explicit lifecycle operations on the selected durable item.
- Deactivation and disposal require zero on-hand quantity; use normal Issue/Adjustment movement operations first.
- Disposed items remain retained terminal history and are not physically deleted.
- Every successful lifecycle transition requires a factual actor/reason and is written to Audit History.

## Owner acceptance checklist

Use a disposable/test database or a copy of production data.

- [ ] Open **Inventory**, select an `ACTIVE` item with positive quantity, and confirm **Deactivate Item** and **Dispose Item** are unavailable until quantity reaches zero; visible help explains why.
- [ ] Use **Issue Quantity** or **Adjust Count To Quantity** through its normal preview/confirmation to bring the item to zero. Confirm movement history and any canonical transaction remain visible.
- [ ] Enter a lifecycle actor/reason and choose **Deactivate Item**. Cancel once and confirm nothing changes; then confirm the action and verify the same stable item remains listed as `INACTIVE` with its movement history intact.
- [ ] Select the inactive item, enter a reason, choose **Reactivate Item**, and confirm the same item returns to `ACTIVE` without duplication.
- [ ] With quantity still zero, enter a reason and choose **Dispose Item**. Confirm the item remains listed as `DISPOSED`, its prior movements/transaction links remain visible, and the lifecycle UI offers no way to reactivate the disposed item.
- [ ] Open **Audit History** and confirm factual `INVENTORY_ITEM_STATUS_CHANGED` rows exist for the successful transitions with the actor and reason supplied.
- [ ] Open **Edit Selected** for active, inactive, and disposed examples. Confirm status is displayed but cannot be directly edited, while ordinary metadata saves preserve the current status.
- [ ] Confirm there is no generic or placeholder **Delete** action for inventory items.
- [ ] At laptop width, confirm lifecycle guidance/actions, item and movement tables, horizontal/vertical scrolling, and split divider remain usable.

## Acceptance record

Record any failure with item ID/name, quantity/status before and after, visible message, actor/reason, and whether Refresh changes the observed result. Do not merge P17-C5 until final-head GitHub Actions and this checklist are accepted.
''')

print("P17-C5 inventory lifecycle changes staged")
