package org.nonprofitbookkeeping.ui;

import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.model.PropertyCustodyEvent;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.PropertyCustodyService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javafx.util.Duration;

/** Physical custody, confirmation, loan, return, transfer, and removal history. */
public final class PropertyCustodyPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<PropertyCustodyService.SubjectView> subjects = new TableView<>();
    private final TableView<PropertyCustodyService.EventView> history = new TableView<>();
    private final ComboBox<PropertyCustodyEvent.EventType> type = new ComboBox<>();
    private final DatePicker date = new DatePicker(LocalDate.now());
    private final TextField quantity = new TextField();
    private final TextField guardian = new TextField();
    private final TextField email = new TextField();
    private final TextField phone = new TextField();
    private final TextField location = new TextField();
    private final ComboBox<InventoryItem.Condition> condition = new ComboBox<>();
    private final ComboBox<PropertyCustodyEvent.ConfirmationStatus> confirmation = new ComboBox<>();
    private final DatePicker confirmationDue = new DatePicker();
    private final DatePicker confirmationDate = new DatePicker();
    private final DatePicker expectedReturn = new DatePicker();
    private final TextField removalReason = new TextField();
    private final TextField authorizationReference = new TextField();
    private final TextField lifecycleSource = new TextField();
    private final TextField movementSource = new TextField();
    private final TextArea notes = new TextArea();
    private final Label status = new Label("Ready.");
    private final CompanyUiFormat format = CompanyUiFormat.activeCompany();
    private Long correctionId;
    private boolean dirty;
    private boolean populating;

    public PropertyCustodyPanel()
    {
        root.setPadding(new Insets(8));
        build();
        reloadSubjects();
    }

    private void build()
    {
        Label title = new Label("Property Custody"); title.getStyleClass().add("panel-title");
        Label help = new Label("Record physical guardianship, confirmations, loans, returns, transfers, and removals. Custody events never create accounting. A removal must cite the canonical lifecycle event or reducing inventory movement that already recorded its accounting treatment.");
        help.setWrapText(true);
        Button add = button("New Custody Event", "propertyCustodyNew", this::onNew);
        Button save = button("Save Custody Event", "propertyCustodySave", this::onSave);
        Button correct = button("Correct Selected Event", "propertyCustodyCorrect", this::correctSelected);
        Button refresh = button("Refresh", "propertyCustodyRefresh", this::reloadSubjects);
        for (Button b : List.of(add, save, correct)) UiPermissionGate.gate(b, ApplicationPermission.BOOKKEEPING_WRITE, b.getText());
        VBox header = new VBox(6, title, help, new FlowPane(8, 6, add, save, correct, refresh), status);
        root.setTop(header);

        subjectColumn("Subject", PropertyCustodyService.SubjectView::name, 220);
        subjectColumn("Register", v -> v.subjectType().name(), 120);
        subjectColumn("Quantity", v -> v.quantity().toPlainString() + " " + v.unit(), 130);
        subjectColumn("Guardian", PropertyCustodyService.SubjectView::guardianName, 160);
        subjectColumn("Location", PropertyCustodyService.SubjectView::location, 160);
        subjectColumn("Confirmation", v -> v.confirmationOverdue() ? "OVERDUE" : v.confirmationStatus().name(), 130);
        subjects.setId("propertyCustodySubjects");
        subjects.getSelectionModel().selectedItemProperty().addListener((o, old, value) -> {
            if (populating || value == null) return;
            if (dirty && old != null && !confirmDiscard())
            {
                populating = true; subjects.getSelectionModel().select(old); populating = false; return;
            }
            reloadHistory(value); resetEditor(value);
        });

        eventColumn("Date", v -> format.formatDate(v.eventDate()), 110);
        eventColumn("Event", PropertyCustodyService.EventView::eventType, 110);
        eventColumn("Guardian", PropertyCustodyService.EventView::guardianName, 150);
        eventColumn("Location", PropertyCustodyService.EventView::location, 150);
        eventColumn("Confirmation", PropertyCustodyService.EventView::confirmationStatus, 120);
        eventColumn("Accounting evidence", PropertyCustodyService.EventView::accountingTreatment, 160);
        eventColumn("History", v -> v.corrected() ? "CORRECTED" : (v.correctionOfEventId() == null ? "ORIGINAL" : "REPLACEMENT"), 120);
        history.setId("propertyCustodyHistory");

        type.getItems().setAll(PropertyCustodyEvent.EventType.values());
        condition.getItems().setAll(InventoryItem.Condition.values());
        confirmation.getItems().setAll(PropertyCustodyEvent.ConfirmationStatus.values());
        for (DatePicker picker : List.of(date, confirmationDue, confirmationDate, expectedReturn)) format.install(picker);
        javafx.scene.layout.GridPane form = new javafx.scene.layout.GridPane(); form.setHgap(8); form.setVgap(6); int row = 0;
        row = row(form, row, "Event type", type); row = row(form, row, "Event date", date);
        row = row(form, row, "Quantity", quantity); row = row(form, row, "Guardian", guardian);
        row = row(form, row, "Email", email); row = row(form, row, "Phone", phone);
        row = row(form, row, "Location", location); row = row(form, row, "Condition", condition);
        row = row(form, row, "Confirmation", confirmation); row = row(form, row, "Confirmation due", confirmationDue);
        row = row(form, row, "Confirmed on", confirmationDate); row = row(form, row, "Expected return", expectedReturn);
        row = row(form, row, "Removal reason", removalReason); row = row(form, row, "Authorization ref", authorizationReference);
        row = row(form, row, "Asset lifecycle event #", lifecycleSource); row = row(form, row, "Inventory movement #", movementSource);
        row(form, row, "Notes", notes);
        notes.setPrefRowCount(3);
        VBox editor = new VBox(6, new Label("Custody event draft"), form);
        ScrollPane editorScroll = new ScrollPane(editor); editorScroll.setFitToWidth(true); editorScroll.setId("propertyCustodyEditorScroll");
        SplitPane lower = new SplitPane(history, editorScroll); lower.setId("propertyCustodyHistoryEditorSplit");
        SplitPane workspace = new SplitPane(subjects, lower); workspace.setId("propertyCustodyWorkspaceSplit"); workspace.setOrientation(javafx.geometry.Orientation.VERTICAL);
        persistDivider(lower, "history-editor", 0.55); persistDivider(workspace, "workspace", 0.42);
        VBox.setVgrow(workspace, Priority.ALWAYS); root.setCenter(workspace);
        installDirtyTracking();
        FullTextTooltipInstaller.install(root);
    }

    private Button button(String text, String id, Runnable action) { Button b = new Button(text); b.setId(id); b.setOnAction(e -> action.run()); return b; }
    private int row(javafx.scene.layout.GridPane grid, int row, String label, Node editor) { grid.add(new Label(label), 0, row); grid.add(editor, 1, row); javafx.scene.layout.GridPane.setHgrow(editor, Priority.ALWAYS); if (editor instanceof Control c) c.setMaxWidth(Double.MAX_VALUE); return row + 1; }
    private <T> void subjectColumn(String title, Function<PropertyCustodyService.SubjectView,T> value, double width) { TableColumn<PropertyCustodyService.SubjectView,T> c = new TableColumn<>(title); c.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(value.apply(v.getValue()))); c.setPrefWidth(width); subjects.getColumns().add(c); }
    private <T> void eventColumn(String title, Function<PropertyCustodyService.EventView,T> value, double width) { TableColumn<PropertyCustodyService.EventView,T> c = new TableColumn<>(title); c.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(value.apply(v.getValue()))); c.setPrefWidth(width); history.getColumns().add(c); }

    private void reloadSubjects()
    {
        try
        {
            Long selected = subjects.getSelectionModel().getSelectedItem() == null ? null : subjects.getSelectionModel().getSelectedItem().subjectId();
            var rows = UiServiceRegistry.propertyCustody().listSubjects(); subjects.getItems().setAll(rows);
            PropertyCustodyService.SubjectView choose = rows.stream().filter(v -> selected != null && v.subjectId() == selected).findFirst().orElse(rows.isEmpty() ? null : rows.get(0));
            if (choose != null) { populating = true; subjects.getSelectionModel().select(choose); populating = false; reloadHistory(choose); if (!dirty) resetEditor(choose); }
            status.setText("Loaded " + rows.size() + " property subject(s).");
        }
        catch (RuntimeException ex) { status.setText("Could not load property custody: " + UiErrors.safeMessage(ex)); }
    }

    private void reloadHistory(PropertyCustodyService.SubjectView subject)
    {
        history.getItems().setAll(UiServiceRegistry.propertyCustody().history(subject.subjectType(), subject.subjectId()));
    }

    private void resetEditor(PropertyCustodyService.SubjectView subject)
    {
        populating = true; correctionId = null; type.setValue(PropertyCustodyEvent.EventType.CONFIRMATION); date.setValue(ActivePeriodContext.get());
        quantity.setText(subject.quantity().toPlainString()); guardian.setText(subject.guardianName()); location.setText(subject.location());
        email.clear(); phone.clear(); condition.setValue(subject.condition()); confirmation.setValue(PropertyCustodyEvent.ConfirmationStatus.NOT_REQUIRED);
        confirmationDue.setValue(null); confirmationDate.setValue(null); expectedReturn.setValue(null); removalReason.clear(); authorizationReference.clear(); lifecycleSource.clear(); movementSource.clear(); notes.clear();
        dirty = false; populating = false;
    }

    private void correctSelected()
    {
        PropertyCustodyService.EventView event = history.getSelectionModel().getSelectedItem();
        PropertyCustodyService.SubjectView subject = subjects.getSelectionModel().getSelectedItem();
        if (event == null || subject == null) { status.setText("Select the latest effective custody event to correct."); return; }
        if (event.corrected()) { status.setText("Select the latest effective event; corrected history is retained read-only."); return; }
        populating = true; correctionId = event.id(); type.setValue(event.eventType()); date.setValue(event.eventDate()); quantity.setText(event.quantity().toPlainString());
        guardian.setText(event.guardianName()); email.setText(event.guardianEmail()); phone.setText(event.guardianPhone()); location.setText(event.location()); condition.setValue(event.condition());
        confirmation.setValue(event.confirmationStatus()); confirmationDue.setValue(event.confirmationDueDate()); confirmationDate.setValue(event.confirmationDate()); expectedReturn.setValue(event.expectedReturnDate());
        removalReason.setText(event.removalReason()); authorizationReference.setText(event.externalAuthorizationReference()); lifecycleSource.setText(text(event.fixedAssetLifecycleEventId())); movementSource.setText(text(event.inventoryMovementId())); notes.setText(event.notes());
        dirty = true; populating = false; status.setText("Editing a replacement for custody event #" + event.id() + ". Save appends history.");
    }

    @Override public void onNew() { PropertyCustodyService.SubjectView s = subjects.getSelectionModel().getSelectedItem(); if (s == null) throw new IllegalStateException("Select a property subject"); resetEditor(s); status.setText("New custody event draft."); }
    @Override public void onSave()
    {
        PropertyCustodyService.SubjectView s = subjects.getSelectionModel().getSelectedItem();
        if (s == null) throw new IllegalStateException("Select a property subject");
        PropertyCustodyService.Command command = new PropertyCustodyService.Command(s.subjectType(), s.subjectId(), type.getValue(), date.getValue(), new BigDecimal(quantity.getText().trim()),
                guardian.getText(), email.getText(), phone.getText(), location.getText(), condition.getValue(), confirmation.getValue(), confirmationDue.getValue(), confirmationDate.getValue(), expectedReturn.getValue(),
                removalReason.getText(), authorizationReference.getText(), notes.getText(), id(lifecycleSource.getText()), id(movementSource.getText()));
        PropertyCustodyService.EventView saved = correctionId == null ? UiServiceRegistry.propertyCustody().save(command, DesktopActorIdentity.current())
                : UiServiceRegistry.propertyCustody().correctLatest(correctionId, command, DesktopActorIdentity.current());
        dirty = false; correctionId = null; reloadSubjects(); status.setText("Saved custody event #" + saved.id() + "; immutable history retained.");
    }

    private void installDirtyTracking() { for (TextInputControl c : List.of(quantity, guardian, email, phone, location, removalReason, authorizationReference, lifecycleSource, movementSource, notes)) c.textProperty().addListener((o,a,b) -> markDirty()); for (ComboBox<?> c : List.of(type, condition, confirmation)) c.valueProperty().addListener((o,a,b) -> markDirty()); for (DatePicker p : List.of(date, confirmationDue, confirmationDate, expectedReturn)) p.valueProperty().addListener((o,a,b) -> markDirty()); }
    private void markDirty() { if (!populating) dirty = true; }
    private boolean confirmDiscard() { return new Alert(Alert.AlertType.CONFIRMATION, "Discard the unsaved custody event draft?", ButtonType.OK, ButtonType.CANCEL).showAndWait().filter(ButtonType.OK::equals).isPresent(); }
    private void persistDivider(SplitPane split, String name, double fallback)
    {
        String company = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        String code = company == null || company.isBlank() ? "DEFAULT" : company;
        String key = "ui.property-custody.divider." + name;
        var preferences = UiServiceRegistry.companyUiPreferences();
        double position = fallback;
        try { String saved = preferences.loadState(code, "ui.property-custody.divider.").get(key); if (saved != null) position = Double.parseDouble(saved); }
        catch (RuntimeException ignored) { position = fallback; }
        split.setDividerPositions(Math.max(0.1, Math.min(0.9, position)));
        PauseTransition delay = new PauseTransition(Duration.millis(250));
        delay.setOnFinished(e -> { try { preferences.saveState(code, Map.of(key, Double.toString(split.getDividerPositions()[0]))); } catch (RuntimeException ex) { status.setText("Could not save layout: " + UiErrors.safeMessage(ex)); } });
        split.getDividers().get(0).positionProperty().addListener((o,a,b) -> delay.playFromStart());
    }
    private static Long id(String value) { return value == null || value.isBlank() ? null : Long.valueOf(value.trim()); }
    private static String text(Long value) { return value == null ? "" : value.toString(); }
    @Override public String title() { return "Property Custody"; }
    @Override public Node root() { return root; }
    @Override public java.util.Set<AppCommand> commandCapabilities() { return AppPanel.capabilities(AppCommand.NEW_ACTIVE, AppCommand.SAVE_ACTIVE); }
    @Override public java.util.Optional<ApplicationPermission> requiredPermission(AppCommand c) { return java.util.Optional.of(ApplicationPermission.BOOKKEEPING_WRITE); }
    @Override public String commandLabel(AppCommand c) { return c == AppCommand.SAVE_ACTIVE ? "Save Custody Event" : AppPanel.super.commandLabel(c); }
    @Override public boolean hasUnsavedChanges() { return dirty; }
    @Override public void onPanelShown() { reloadSubjects(); }
}
