from pathlib import Path


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing replacement anchor: {label}')
    return text.replace(old, new, 1)

# FixedAssetService
path = Path('src/main/java/org/nonprofitbookkeeping/service/FixedAssetService.java')
text = path.read_text()
text = replace_once(text,
'''    public FixedAssetView create(FixedAssetCommand command)
    {
        requireInteractiveStatus(command, null);
''',
'''    public FixedAssetView create(FixedAssetCommand command)
    {
        requireInteractiveCreateStatus(command);
''', 'interactive create status')

old_update = '''    public FixedAssetView update(long assetId, FixedAssetCommand command)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                FixedAsset asset = require(em, FixedAsset.class, assetId, "Fixed asset");
                requireInteractiveStatus(command, asset);
                requireLifecycleSafeUpdate(em, asset, command);
                apply(em, asset, command);
                asset.touchUpdatedAt();
                em.getTransaction().commit();
                return load(assetId);
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

'''
new_update = '''    public FixedAssetView update(long assetId, FixedAssetCommand command)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                FixedAsset asset = em.find(
                        FixedAsset.class, assetId, LockModeType.PESSIMISTIC_WRITE);
                if (asset == null)
                {
                    throw new IllegalArgumentException("Fixed asset not found: " + assetId);
                }
                requireInteractiveStatusUnchanged(command, asset);
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(
                        em, normalizeCompanyCode(command.companyCode()));
                ownership.requireOwnedBy(company, asset.getCompany(), "Fixed asset");
                FixedAsset.Status existingStatus = asset.getStatus();
                requireLifecycleSafeUpdate(em, asset, command);
                apply(em, asset, command);
                asset.setStatus(existingStatus);
                asset.touchUpdatedAt();
                em.getTransaction().commit();
                return load(assetId);
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    /** Explicit retained-history lifecycle transition for ACTIVE and INACTIVE assets. */
    public FixedAssetView changeStatus(
            long assetId,
            FixedAsset.Status targetStatus,
            String actor,
            String reason)
    {
        if (targetStatus == null)
        {
            throw new IllegalArgumentException("targetStatus is required");
        }
        if (targetStatus == FixedAsset.Status.DISPOSED)
        {
            throw new IllegalArgumentException(
                    "DISPOSED is created only by the governed Sale or Retirement workflow");
        }
        String normalizedActor = requireText(actor, "actor");
        String normalizedReason = requireText(reason, "reason");
        String companyCode = normalizeCompanyCode(companyCodeSupplier.get());

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, companyCode);
                if (!company.isActive())
                {
                    throw new IllegalStateException("Company " + company.getCode() + " is inactive");
                }
                FixedAsset asset = em.find(
                        FixedAsset.class, assetId, LockModeType.PESSIMISTIC_WRITE);
                if (asset == null)
                {
                    throw new IllegalArgumentException("Fixed asset not found: " + assetId);
                }
                ownership.requireOwnedBy(company, asset.getCompany(), "Fixed asset");
                FixedAsset.Status before = asset.getStatus();
                if (before == FixedAsset.Status.DISPOSED)
                {
                    throw new IllegalStateException(
                            "A disposed asset is retained terminal history; reverse its Sale or Retirement before changing status");
                }
                if (before != targetStatus)
                {
                    boolean supported = (before == FixedAsset.Status.ACTIVE
                            && targetStatus == FixedAsset.Status.INACTIVE)
                            || (before == FixedAsset.Status.INACTIVE
                            && targetStatus == FixedAsset.Status.ACTIVE);
                    if (!supported)
                    {
                        throw new IllegalStateException(
                                "Unsupported fixed-asset status transition: " + before + " -> " + targetStatus);
                    }
                    asset.setStatus(targetStatus);
                    asset.touchUpdatedAt();

                    AuditEvent audit = new AuditEvent();
                    audit.setCompany(company);
                    audit.setActor(normalizedActor);
                    audit.setActionType("FIXED_ASSET_STATUS_CHANGED");
                    audit.setEntityType("FixedAsset");
                    audit.setEntityId(Long.toString(asset.getId()));
                    audit.setSummary("Fixed asset " + asset.getName() + " status "
                            + before + " -> " + targetStatus);
                    audit.setBeforeValue("status=" + before);
                    audit.setAfterValue("status=" + targetStatus);
                    audit.setReason(normalizedReason);
                    em.persist(audit);
                }
                em.getTransaction().commit();
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
        return load(assetId);
    }

'''
text = replace_once(text, old_update, new_update, 'update and status lifecycle')
text = replace_once(text,
'''                FixedAsset asset = require(em, FixedAsset.class, assetId, "Fixed asset");
                ownership.requireOwnedBy(company, asset.getCompany(), "Fixed asset");
                validateDepreciationEligibility(ownership, company, asset, runDate);
''',
'''                FixedAsset asset = em.find(
                        FixedAsset.class, assetId, LockModeType.PESSIMISTIC_WRITE);
                if (asset == null)
                {
                    throw new IllegalArgumentException("Fixed asset not found: " + assetId);
                }
                ownership.requireOwnedBy(company, asset.getCompany(), "Fixed asset");
                validateDepreciationEligibility(ownership, company, asset, runDate);
''', 'depreciation asset lock')
old_status = '''    private static void requireInteractiveStatus(FixedAssetCommand command, FixedAsset existing)
    {
        if (command == null)
        {
            throw new IllegalArgumentException("command is required");
        }
        FixedAsset.Status requested = command.status() == null
                ? FixedAsset.Status.ACTIVE : command.status();
        if (requested == FixedAsset.Status.DISPOSED)
        {
            throw new IllegalArgumentException(
                    "DISPOSED is created only by the governed Sale or Retirement workflow");
        }
        if (existing != null && existing.getStatus() == FixedAsset.Status.DISPOSED)
        {
            throw new IllegalStateException(
                    "A disposed asset is immutable; reverse its lifecycle event before editing it");
        }
    }

'''
new_status = '''    private static void requireInteractiveCreateStatus(FixedAssetCommand command)
    {
        if (command == null)
        {
            throw new IllegalArgumentException("command is required");
        }
        FixedAsset.Status requested = command.status() == null
                ? FixedAsset.Status.ACTIVE : command.status();
        if (requested != FixedAsset.Status.ACTIVE)
        {
            throw new IllegalArgumentException(
                    "New interactive fixed assets must start ACTIVE; use the explicit lifecycle action after creation");
        }
    }

    private static void requireInteractiveStatusUnchanged(
            FixedAssetCommand command,
            FixedAsset existing)
    {
        if (command == null)
        {
            throw new IllegalArgumentException("command is required");
        }
        if (existing.getStatus() == FixedAsset.Status.DISPOSED)
        {
            throw new IllegalStateException(
                    "A disposed asset is immutable; reverse its lifecycle event before editing it");
        }
        FixedAsset.Status requested = command.status() == null
                ? existing.getStatus() : command.status();
        if (requested != existing.getStatus())
        {
            throw new IllegalArgumentException(
                    "Fixed asset status changes use the explicit lifecycle action");
        }
    }

'''
text = replace_once(text, old_status, new_status, 'interactive status helpers')
path.write_text(text)

# AssetsRegisterPanel
path = Path('src/main/java/org/nonprofitbookkeeping/ui/AssetsRegisterPanel.java')
text = path.read_text()
text = replace_once(text,
'''    private final ComboBox<Integer> usefulLifeMonths = new ComboBox<>();
    private final ComboBox<FixedAsset.Status> statusChoice = new ComboBox<>();
    private final TextField name = new TextField();
''',
'''    private final ComboBox<Integer> usefulLifeMonths = new ComboBox<>();
    private final Label lifecycleStatus = new Label("ACTIVE");
    private final TextField lifecycleActor = new TextField(System.getProperty("user.name", "operator"));
    private final TextField lifecycleReason = new TextField();
    private final Button deactivateAsset = new Button("Deactivate Asset");
    private final Button reactivateAsset = new Button("Reactivate Asset");
    private final TextField name = new TextField();
''', 'panel lifecycle fields')
text = replace_once(text,
'''    private List<Account> postingAccounts = List.of();
    private Long selectedAssetId;
    private boolean suppressSelection;
''',
'''    private List<Account> postingAccounts = List.of();
    private Long selectedAssetId;
    private FixedAsset.Status editingStatus = FixedAsset.Status.ACTIVE;
    private boolean suppressSelection;
''', 'panel editing status')
text = replace_once(text,
'''        Button lifecycle = new Button("Record Lifecycle Event...");
        lifecycle.setId("recordFixedAssetLifecycleButton");
''',
'''        deactivateAsset.setId("deactivateFixedAssetButton");
        deactivateAsset.setDisable(true);
        deactivateAsset.setOnAction(e -> changeSelectedStatus(FixedAsset.Status.INACTIVE));
        reactivateAsset.setId("reactivateFixedAssetButton");
        reactivateAsset.setDisable(true);
        reactivateAsset.setOnAction(e -> changeSelectedStatus(FixedAsset.Status.ACTIVE));
        busy.addListener((obs, oldValue, newValue) ->
                updateLifecycleActions(table.getSelectionModel().getSelectedItem()));
        Button lifecycle = new Button("Record Lifecycle Event...");
        lifecycle.setId("recordFixedAssetLifecycleButton");
''', 'panel lifecycle buttons')
text = replace_once(text,
'''        FlowPane actions = new FlowPane(8, 6, refresh, newAsset, save, lifecycle, reverse, drill);
''',
'''        FlowPane actions = new FlowPane(
                8, 6, refresh, newAsset, save, deactivateAsset, reactivateAsset,
                lifecycle, reverse, drill);
''', 'panel actions')
text = replace_once(text,
'''        form.add(new Label("Status"), 0, row);
        form.add(statusChoice, 1, row++);
        notes.setPrefRowCount(2);
        form.add(new Label("Notes"), 0, row);
        form.add(notes, 1, row);
        for (Node field : java.util.List.of(
                name, assetAccount, accumulatedDepreciationAccount, depreciationExpenseAccount,
                fund, acquisitionDate, acquisitionCost, salvageValue, usefulLifeMonths,
                openingAccumulatedDepreciation, statusChoice, notes))
''',
'''        form.add(new Label("Status"), 0, row);
        form.add(lifecycleStatus, 1, row++);
        notes.setPrefRowCount(2);
        form.add(new Label("Notes"), 0, row);
        form.add(notes, 1, row++);
        lifecycleActor.setPromptText("Factual operator");
        lifecycleReason.setPromptText("Required for Activate / Deactivate");
        form.add(new Label("Lifecycle actor"), 0, row);
        form.add(lifecycleActor, 1, row++);
        form.add(new Label("Lifecycle reason"), 0, row);
        form.add(lifecycleReason, 1, row++);
        Label lifecycleGuidance = new Label(
                "Fixed assets are retained and never physically deleted. Deactivate temporarily stops depreciation and financial lifecycle actions; Reactivate resumes them. Use Sale or Retirement for financial disposal; DISPOSED remains retained history until that lifecycle event is reversed.");
        lifecycleGuidance.setWrapText(true);
        form.add(lifecycleGuidance, 0, row, 2, 1);
        for (Node field : java.util.List.of(
                name, assetAccount, accumulatedDepreciationAccount, depreciationExpenseAccount,
                fund, acquisitionDate, acquisitionCost, salvageValue, usefulLifeMonths,
                openingAccumulatedDepreciation, notes, lifecycleActor, lifecycleReason))
''', 'panel status form')
text = replace_once(text,
'''        usefulLifeMonths.setItems(FXCollections.observableArrayList(36, 60, 84));
        usefulLifeMonths.getSelectionModel().select(Integer.valueOf(60));
        statusChoice.setItems(FXCollections.observableArrayList(
                FixedAsset.Status.ACTIVE, FixedAsset.Status.INACTIVE));
        statusChoice.getSelectionModel().select(FixedAsset.Status.ACTIVE);
''',
'''        usefulLifeMonths.setItems(FXCollections.observableArrayList(36, 60, 84));
        usefulLifeMonths.getSelectionModel().select(Integer.valueOf(60));
''', 'panel status choices')
text = replace_once(text,
'''            fillForm(selected);
        });
''',
'''            fillForm(selected);
            updateLifecycleActions(selected);
        });
''', 'selection lifecycle state')
anchor = '''    private void recordLifecycleEvent()
    {
'''
status_methods = '''    private void changeSelectedStatus(FixedAsset.Status targetStatus)
    {
        FixedAssetView selected = table.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a fixed asset before changing its lifecycle status.");
            return;
        }
        if (dirtyState.isDirty())
        {
            status.setText("Save or discard the current asset edits before changing lifecycle status.");
            return;
        }
        String actor = lifecycleActor.getText() == null ? "" : lifecycleActor.getText().trim();
        String reason = lifecycleReason.getText() == null ? "" : lifecycleReason.getText().trim();
        if (actor.isBlank() || reason.isBlank())
        {
            status.setText("Lifecycle actor and reason are required.");
            return;
        }
        String verb = targetStatus == FixedAsset.Status.INACTIVE ? "Deactivate" : "Reactivate";
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle(verb + " Fixed Asset");
        confirmation.setHeaderText(verb + " " + selected.name() + "?");
        confirmation.setContentText(targetStatus == FixedAsset.Status.INACTIVE
                ? "Deactivation retains the asset and all accounting history but stops depreciation and Sale/Retirement/Impairment actions until the asset is reactivated.\n\nReason: " + reason
                : "Reactivation resumes depreciation and governed financial lifecycle eligibility for this retained asset.\n\nReason: " + reason);
        CompanyDialogUiCompliance.install(confirmation, AppPanelId.ASSETS_REGISTER);
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty())
        {
            status.setText(verb + " cancelled; no asset status changed.");
            return;
        }
        busy.set(true);
        updateLifecycleActions(selected);
        status.setText(verb + "ing fixed asset...");
        UiAsync.run("fixed-asset-status-change",
                () -> UiServiceRegistry.fixedAssets().changeStatus(
                        selected.id(), targetStatus, actor, reason),
                changed -> {
                    editingStatus = changed.status();
                    lifecycleStatus.setText(changed.status().name());
                    lifecycleReason.clear();
                    dirtyState.markClean();
                    reload(verb + "d fixed asset " + changed.name() + ".");
                },
                ex -> {
                    busy.set(false);
                    updateLifecycleActions(selected);
                    status.setText("Could not " + verb.toLowerCase() + " fixed asset: "
                            + UiErrors.safeMessage(ex));
                });
    }

    private void updateLifecycleActions(FixedAssetView selected)
    {
        boolean unavailable = busy.get() || selected == null;
        deactivateAsset.setDisable(unavailable || selected.status() != FixedAsset.Status.ACTIVE);
        reactivateAsset.setDisable(unavailable || selected.status() != FixedAsset.Status.INACTIVE);
    }

'''
text = replace_once(text, anchor, status_methods + anchor, 'panel status methods')
text = replace_once(text,
'''                money(openingAccumulatedDepreciation.getText()),
                requireSelected(statusChoice, "status"),
                notes.getText());
''',
'''                money(openingAccumulatedDepreciation.getText()),
                editingStatus,
                notes.getText());
''', 'panel command status')
old_fill_status = '''        if (asset.status() == FixedAsset.Status.DISPOSED)
        {
            statusChoice.getItems().setAll(
                    FixedAsset.Status.ACTIVE,
                    FixedAsset.Status.INACTIVE,
                    FixedAsset.Status.DISPOSED);
        }
        else
        {
            statusChoice.getItems().setAll(FixedAsset.Status.ACTIVE, FixedAsset.Status.INACTIVE);
        }
        statusChoice.getSelectionModel().select(asset.status());
        statusChoice.setDisable(asset.status() == FixedAsset.Status.DISPOSED);
'''
text = replace_once(text, old_fill_status,
'''        editingStatus = asset.status();
        lifecycleStatus.setText(asset.status().name());
        lifecycleReason.clear();
''', 'fill read-only status')
text = replace_once(text,
'''        setAssetFormDisabled(asset.status() == FixedAsset.Status.DISPOSED);
        dirtyState.markClean();
''',
'''        setAssetFormDisabled(asset.status() == FixedAsset.Status.DISPOSED);
        updateLifecycleActions(asset);
        dirtyState.markClean();
''', 'fill action state')
text = replace_once(text,
'''        notes.clear();
        statusChoice.getItems().setAll(FixedAsset.Status.ACTIVE, FixedAsset.Status.INACTIVE);
        setAssetFormDisabled(false);
        statusChoice.getSelectionModel().select(FixedAsset.Status.ACTIVE);
''',
'''        notes.clear();
        editingStatus = FixedAsset.Status.ACTIVE;
        lifecycleStatus.setText(FixedAsset.Status.ACTIVE.name());
        lifecycleReason.clear();
        setAssetFormDisabled(false);
        updateLifecycleActions(null);
''', 'clear status')
text = replace_once(text,
'''                fund, acquisitionDate, acquisitionCost, salvageValue, usefulLifeMonths,
                openingAccumulatedDepreciation, statusChoice, notes))
''',
'''                fund, acquisitionDate, acquisitionCost, salvageValue, usefulLifeMonths,
                openingAccumulatedDepreciation, notes))
''', 'disabled field list')
text = replace_once(text,
'''                acquisitionCost.getText(), salvageValue.getText(), usefulLifeMonths.getValue(),
                openingAccumulatedDepreciation.getText(), statusChoice.getValue(), notes.getText());
''',
'''                acquisitionCost.getText(), salvageValue.getText(), usefulLifeMonths.getValue(),
                openingAccumulatedDepreciation.getText(), notes.getText());
''', 'snapshot status removal')
text = replace_once(text,
'''            Integer usefulLifeMonths,
            String openingAccumulatedDepreciation,
            FixedAsset.Status status,
            String notes)
''',
'''            Integer usefulLifeMonths,
            String openingAccumulatedDepreciation,
            String notes)
''', 'snapshot record')
if 'statusChoice' in text or 'ComboBox<FixedAsset.Status>' in text:
    raise SystemExit('editable fixed-asset status remains in panel')
path.write_text(text)

# Service lifecycle tests
Path('src/test/java/org/nonprofitbookkeeping/service/FixedAssetStatusLifecycleTest.java').write_text(r'''package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedAssetStatusLifecycleTest
{
    private static final long CHART_ID = 31_001L;
    private static final long COMPANY_ID = 31_001L;
    private static final long FUND_ID = 31_001L;
    private static final long ASSET_ACCOUNT_ID = 31_001L;
    private static final long ACCUMULATED_ACCOUNT_ID = 31_002L;
    private static final long EXPENSE_ACCOUNT_ID = 31_003L;

    @Test
    void ordinaryUpdateCannotChangeStatusAndExplicitTransitionsAreAudited(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fixed-asset-status")))
        {
            seed(jpa);
            FixedAssetService service = new FixedAssetService(
                    jpa, new TransactionEntryService(jpa, () -> "SCA"), () -> "SCA");
            FixedAssetView asset = service.create(command("Laptop", FixedAsset.Status.ACTIVE));

            IllegalArgumentException direct = assertThrows(IllegalArgumentException.class,
                    () -> service.update(asset.id(), command("Laptop", FixedAsset.Status.INACTIVE)));
            assertEquals("Fixed asset status changes use the explicit lifecycle action", direct.getMessage());
            assertEquals(FixedAsset.Status.ACTIVE, service.load(asset.id()).status());

            FixedAssetView inactive = service.changeStatus(
                    asset.id(), FixedAsset.Status.INACTIVE, "tester", "Placed in storage");
            assertEquals(FixedAsset.Status.INACTIVE, inactive.status());

            FixedAssetView renamed = service.update(
                    asset.id(), command("Laptop in storage", FixedAsset.Status.INACTIVE));
            assertEquals("Laptop in storage", renamed.name());
            assertEquals(FixedAsset.Status.INACTIVE, renamed.status());

            FixedAssetView active = service.changeStatus(
                    asset.id(), FixedAsset.Status.ACTIVE, "tester", "Returned to service");
            assertEquals(FixedAsset.Status.ACTIVE, active.status());

            try (EntityManager em = jpa.em())
            {
                Long auditCount = em.createQuery(
                                "select count(a) from AuditEvent a where a.actionType = :action "
                                        + "and a.entityType = :type and a.entityId = :id", Long.class)
                        .setParameter("action", "FIXED_ASSET_STATUS_CHANGED")
                        .setParameter("type", "FixedAsset")
                        .setParameter("id", Long.toString(asset.id()))
                        .getSingleResult();
                assertEquals(2L, auditCount.longValue());
            }
        }
    }

    @Test
    void interactiveCreationStartsActiveAndDisposedHistoryCannotBeReactivated(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fixed-asset-terminal")))
        {
            seed(jpa);
            FixedAssetService service = new FixedAssetService(
                    jpa, new TransactionEntryService(jpa, () -> "SCA"), () -> "SCA");

            IllegalArgumentException inactiveCreate = assertThrows(IllegalArgumentException.class,
                    () -> service.create(command("Stored asset", FixedAsset.Status.INACTIVE)));
            assertTrue(inactiveCreate.getMessage().contains("must start ACTIVE"));

            FixedAssetView asset = service.create(command("Disposed history", FixedAsset.Status.ACTIVE));
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("update fixed_asset set status = 'DISPOSED' where id = ?")
                        .setParameter(1, asset.id())
                        .executeUpdate();
                em.getTransaction().commit();
            }

            IllegalStateException reactivate = assertThrows(IllegalStateException.class,
                    () -> service.changeStatus(
                            asset.id(), FixedAsset.Status.ACTIVE, "tester", "unsafe restore"));
            assertTrue(reactivate.getMessage().contains("reverse its Sale or Retirement"));
            assertThrows(IllegalStateException.class,
                    () -> service.update(asset.id(), command("Changed", FixedAsset.Status.DISPOSED)));
        }
    }

    private static FixedAssetCommand command(String name, FixedAsset.Status status)
    {
        return new FixedAssetCommand(
                "SCA", ASSET_ACCOUNT_ID, ACCUMULATED_ACCOUNT_ID, EXPENSE_ACCOUNT_ID, FUND_ID,
                name, LocalDate.of(2026, 1, 1), new BigDecimal("1200.0000"), BigDecimal.ZERO,
                36, FixedAsset.DepreciationMethod.STRAIGHT_LINE, BigDecimal.ZERO, status, "Test asset");
    }

    private static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (?, 'SCA Chart', '1', 'ACTIVE')")
                    .setParameter(1, CHART_ID).executeUpdate();
            em.createNativeQuery("INSERT INTO company (id, code, display_name, active_chart_of_accounts_id) VALUES (?, 'SCA', 'SCA Branch', ?)")
                    .setParameter(1, COMPANY_ID).setParameter(2, CHART_ID).executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = ? WHERE id = ?")
                    .setParameter(1, COMPANY_ID).setParameter(2, CHART_ID).executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, company_id, code, name, fund_type) VALUES (?, ?, 'OPERATING', 'Operating', 'UNRESTRICTED')")
                    .setParameter(1, FUND_ID).setParameter(2, COMPANY_ID).executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (?, ?, '1500', 'Equipment', 'ASSET', 'FIXED_ASSET', 'DEBIT')")
                    .setParameter(1, ASSET_ACCOUNT_ID).setParameter(2, CHART_ID).executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (?, ?, '1590', 'Accumulated Depreciation', 'ASSET', 'FIXED_ASSET', 'CREDIT')")
                    .setParameter(1, ACCUMULATED_ACCOUNT_ID).setParameter(2, CHART_ID).executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (?, ?, '6100', 'Depreciation Expense', 'EXPENSE', 'DEBIT')")
                    .setParameter(1, EXPENSE_ACCOUNT_ID).setParameter(2, CHART_ID).executeUpdate();
            em.getTransaction().commit();
        }
    }
}
''')

Path('src/test/java/org/nonprofitbookkeeping/service/FixedAssetLifecycleSourceTest.java').write_text(r'''package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source guardrails for P17-C7 fixed-asset lifecycle serialization. */
class FixedAssetLifecycleSourceTest
{
    @Test
    void interactiveStatusMetadataAndDepreciationShareTheAssetLock() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/service/FixedAssetService.java"));
        String token = "FixedAsset.class, assetId, LockModeType.PESSIMISTIC_WRITE";
        int occurrences = source.split(Pattern.quote(token), -1).length - 1;

        assertTrue(occurrences >= 3, "update, status change, and depreciation must lock the asset");
        assertTrue(source.contains("Fixed asset status changes use the explicit lifecycle action"));
        assertTrue(source.contains("FIXED_ASSET_STATUS_CHANGED"));
        assertTrue(source.contains("public FixedAsset createForImport("));
        assertTrue(source.contains("asset.initializeImportMetadata(portableId, createdAt, updatedAt)"));
    }
}
''')

Path('src/test/java/org/nonprofitbookkeeping/ui/AssetsRegisterLifecycleSourceTest.java').write_text(r'''package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetsRegisterLifecycleSourceTest
{
    @Test
    void statusIsReadOnlyAndLifecycleActionsAreExplicit() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/AssetsRegisterPanel.java"));

        assertTrue(source.contains("Deactivate Asset"));
        assertTrue(source.contains("Reactivate Asset"));
        assertTrue(source.contains("fixedAssets().changeStatus("));
        assertTrue(source.contains("Fixed assets are retained and never physically deleted"));
        assertTrue(source.contains("lifecycleStatus"));
        assertFalse(source.contains("ComboBox<FixedAsset.Status>"));
        assertFalse(source.contains("statusChoice"));
    }
}
''')

# Governing docs
path = Path('doc/inventory/inventory-and-assets.md')
text = path.read_text()
text = replace_once(text,
'''The editable Asset Register status is limited to `ACTIVE` and `INACTIVE`. `DISPOSED` is a service-owned result of a confirmed Sale or Retirement; it cannot be selected or cleared through ordinary asset editing. A disposed asset returns to its former lifecycle state only when its linked canonical transaction is reversed through the asset lifecycle workflow.
''',
'''Fixed-asset status is service-owned lifecycle state rather than ordinary editable metadata. Interactive creation starts `ACTIVE`; ordinary asset editing preserves the current status. Explicit **Deactivate Asset** and **Reactivate Asset** actions govern `ACTIVE <-> INACTIVE` transitions with a factual actor, nonblank reason, and `AuditEvent`. `DISPOSED` remains a service-owned result of a confirmed Sale or Retirement; it cannot be selected, produced by the ACTIVE/INACTIVE lifecycle action, or cleared through ordinary asset editing. A disposed asset returns to its former lifecycle state only when its linked canonical transaction is reversed through the asset lifecycle workflow.

Deactivation is nonfinancial retained history: it stops depreciation and Sale/Retirement/Impairment eligibility without derecognizing the asset or changing carrying value. Reactivation resumes those governed operations. A financial disposal still requires Sale or Retirement. Fixed assets are never physically deleted from this maintenance surface.
''', 'asset lifecycle doc status')
text += '''

## P17-C7 fixed-asset status lifecycle authority

`FixedAsset.id` remains the durable identity for metadata, depreciation runs, lifecycle events, canonical transactions, and reports. Interactive ACTIVE/INACTIVE transitions use `FixedAssetService.changeStatus(...)`; ordinary metadata updates cannot change status, and interactive creation cannot begin INACTIVE or DISPOSED.

Metadata updates, explicit ACTIVE/INACTIVE changes, monthly depreciation, financial lifecycle commit, and lifecycle reversal all serialize through a pessimistic lock on the same `FixedAsset` row before revalidation and mutation. This prevents stale metadata or status writes from racing a depreciation run or Sale/Retirement/Impairment operation into inconsistent asset state.

`DISPOSED` remains financially authoritative terminal state while its Sale/Retirement is unreversed. It can be undone only by the governed lifecycle reversal, which restores the event's prior status and reverses canonical accounting. The ACTIVE/INACTIVE action never creates or clears DISPOSED.

SCLX `createForImport(...)` remains a caller-owned historical restore seam. It may restore the source status and timestamps without fabricating a local ACTIVE/INACTIVE audit transition; interactive callers cannot use that seam.
'''
path.write_text(text)

path = Path('doc/interface-operation-matrix.md')
lines = path.read_text().splitlines()
for i, line in enumerate(lines):
    if line.startswith('| `ASSETS_REGISTER` |'):
        lines[i] = '| `ASSETS_REGISTER` | `AssetsRegisterPanel` | H2 asset table and form with read-only lifecycle status; explicit Deactivate Asset / Reactivate Asset; lifecycle actor/reason; retained-history/no-delete guidance; lifecycle-history table; save/new; frozen Sale/Retirement/Impairment preview-confirm; domain reversal; Ledger drill-through | `FixedAssetService`, canonical `TransactionEntryService`/`TransactionCorrectionService`, account/fund lookup services | `FixedAssetService.create/update/changeStatus/previewLifecycleEvent/recordLifecycleEvent/previewLifecycleReversal/reverseLifecycleEvent` | yes | yes | `fixed_asset`, `fixed_asset_lifecycle_event`, `fixed_asset_depreciation_run`, canonical `txn`/`txn_split`, `audit_event`, chart accounts, funds | P17-C7 makes ACTIVE/INACTIVE service-owned and audited; ordinary edits preserve status; interactive create starts ACTIVE; metadata/status/depreciation/lifecycle writes serialize on the same asset lock; `DISPOSED` remains Sale/Retirement-owned and is cleared only by domain reversal; SCLX historical restore retains its caller-owned source-status seam; no physical or placeholder Delete | owner P17-C7 fixed-asset lifecycle checklist plus retained P16-S14/P16-S15 verification | P08/P16-S14/P16-S15/P17-C7 |'
        break
else:
    raise SystemExit('ASSETS_REGISTER matrix row not found')
path.write_text('\n'.join(lines) + '\n')

Path('doc/P17-C7-fixed-asset-lifecycle-user-testing.md').write_text('''# P17-C7 fixed-asset lifecycle owner testing

Use a disposable company/database with at least one active fixed asset and valid fixed-asset, accumulated-depreciation, depreciation-expense, and fund references.

1. Open **Asset Register**. Confirm Status is read-only and the toolbar exposes **Deactivate Asset** and **Reactivate Asset**; there is no generic Delete action.
2. Select an ACTIVE asset, enter a lifecycle actor and reason, choose **Deactivate Asset**, confirm the action, and verify the same stable asset remains visible as INACTIVE with all existing depreciation/lifecycle history retained.
3. With that asset INACTIVE, verify monthly depreciation and **Record Lifecycle Event...** cannot proceed until the asset is reactivated.
4. Enter actor/reason and choose **Reactivate Asset**. Verify the same asset returns ACTIVE and can again enter governed depreciation or Sale/Retirement/Impairment workflows.
5. Edit ordinary metadata on ACTIVE and INACTIVE assets and save. Confirm those saves do not change lifecycle status.
6. Record a Sale or Retirement on an ACTIVE disposable asset. Confirm status becomes DISPOSED, ordinary asset editing and Activate/Deactivate are unavailable, and the row/history remain visible.
7. Reverse that Sale/Retirement through **Reverse Selected Lifecycle Event**. Confirm canonical reversal accounting is created and the asset returns to its prior lifecycle status.
8. Open **Audit History** and confirm ACTIVE/INACTIVE transitions appear as `FIXED_ASSET_STATUS_CHANGED` with the factual actor, before/after status, and entered reason.
9. Restart/reopen the company and confirm retained statuses, asset history, lifecycle history, and table/divider state remain authoritative from H2.
''')

# Product assertions
service = Path('src/main/java/org/nonprofitbookkeeping/service/FixedAssetService.java').read_text()
panel = Path('src/main/java/org/nonprofitbookkeeping/ui/AssetsRegisterPanel.java').read_text()
assert 'FIXED_ASSET_STATUS_CHANGED' in service
assert service.count('FixedAsset.class, assetId, LockModeType.PESSIMISTIC_WRITE') >= 3
assert 'Fixed asset status changes use the explicit lifecycle action' in service
assert 'ComboBox<FixedAsset.Status>' not in panel and 'statusChoice' not in panel
assert 'Deactivate Asset' in panel and 'Reactivate Asset' in panel
print('P17-C7 fixed-asset lifecycle implementation staged')
