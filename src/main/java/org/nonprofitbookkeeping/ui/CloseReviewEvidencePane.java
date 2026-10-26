package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.AccountingReadiness;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.CloseReviewEvidenceService;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/** Draft editor and immutable factual history inside the existing Period Close workspace. */
final class CloseReviewEvidencePane implements AppPanel
{
    private final VBox root = new VBox(8);
    private final DatePicker start, end;
    private final Supplier<CloseReviewEvidenceService> services;
    private final Supplier<String> scope;
    private final SimpleBooleanProperty busy = new SimpleBooleanProperty(), saved = new SimpleBooleanProperty();
    private final ComboBox<CloseReviewEvidenceService.ReferenceKind> kind = new ComboBox<>();
    private final TextField reference = new TextField(), description = new TextField();
    private final TextArea note = new TextArea(), reason = new TextArea(), retained = new TextArea();
    private final CheckBox attested = new CheckBox("I reviewed the external materials referenced here for this captured snapshot.");
    private final ComboBox<AccountingReadiness.Finding> warning = new ComboBox<>();
    private final TableView<CloseReviewEvidenceService.Reference> references = new TableView<>();
    private final TableView<CloseReviewEvidenceService.Review> history = new TableView<>();
    private final Map<String, String> reasons = new LinkedHashMap<>();
    private final Label status = new Label("Preview the selected dates before recording review facts. No close authorization is granted.");
    private final Button save = new Button("Save Review"), preview = new Button("Preview Review Snapshot");
    private AccountingReadiness captured;
    private UUID request = UUID.randomUUID();
    private long generation;
    private CompletableFuture<?> completion;
    private Runnable capabilitiesChanged = () -> { };

    CloseReviewEvidencePane(DatePicker start, DatePicker end, Supplier<CloseReviewEvidenceService> services, Supplier<String> scope)
    {
        this.start = start; this.end = end; this.services = services; this.scope = scope;
        root.setPadding(new Insets(8)); root.setMinSize(0, 0);
        status.setId("closeReviewStatus"); status.setWrapText(true);
        preview.setId("closeReviewPreview"); save.setId("closeReviewSave");
        Button fresh = button("New Review", "closeReviewNew", this::newReview);
        Button refresh = button("Refresh History", "closeReviewRefresh", this::refreshHistory);
        fresh.disableProperty().bind(busy);
        refresh.disableProperty().bind(busy.or(UiPermissionGate.deniedProperty(ApplicationPermission.EXPORT)));
        preview.disableProperty().bind(busy.or(saved).or(UiPermissionGate.deniedProperty(ApplicationPermission.EXPORT)));
        UiPermissionGate.gate(save, ApplicationPermission.BOOKKEEPING_WRITE, "Record factual review evidence");
        preview.setOnAction(event -> preview()); save.setOnAction(event -> onSave());
        Label explanation = new Label("External references are descriptive; documents are not independently verified. Blockers remain blockers. Saved reviews cannot be edited or deleted; record a new review to correct them.");
        explanation.setWrapText(true);
        root.getChildren().addAll(new FlowPane(8, 8, preview, save, fresh, refresh), explanation, status);
        kind.getItems().setAll(CloseReviewEvidenceService.ReferenceKind.values());
        kind.setConverter(new javafx.util.StringConverter<>()
        {
            @Override public String toString(CloseReviewEvidenceService.ReferenceKind value)
            {
                return value == null ? "" : value.name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
            }
            @Override public CloseReviewEvidenceService.ReferenceKind fromString(String value)
            {
                return null;
            }
        });
        kind.setPromptText("Reference kind"); kind.setId("closeReviewReferenceKind");
        reference.setPromptText("External reference or document location (not fetched)"); reference.setId("closeReviewReference");
        description.setPromptText("Optional description"); description.setId("closeReviewDescription");
        Button add = button("Add Reference", "closeReviewAddReference", this::addReference);
        Button remove = button("Remove Draft Reference", "closeReviewRemoveReference", () ->
        {
            references.getItems().remove(references.getSelectionModel().getSelectedItem()); draftChanged();
        });
        warning.setId("closeReviewWarning"); warning.setPromptText("Choose a warning for an optional reason");
        warning.setMaxWidth(Double.MAX_VALUE);
        warning.setConverter(new javafx.util.StringConverter<>()
        {
            @Override public String toString(AccountingReadiness.Finding value)
            {
                return value == null ? "" : value.area() + " " + value.reference() + ": " + value.detail();
            }
            @Override public AccountingReadiness.Finding fromString(String value)
            {
                return null;
            }
        });
        warning.valueProperty().addListener((observable, oldValue, value) ->
        {
            reason.setText(value == null ? "" : reasons.getOrDefault(value.id(), "")); updateSave();
        });
        reason.setId("closeReviewWarningReason"); reason.setPrefRowCount(2); reason.setPromptText("Reason for this warning (never a blocker waiver)");
        Button setReason = button("Keep Warning Reason", "closeReviewKeepReason", this::keepReason);
        Button removeReason = button("Remove Draft Reason", "closeReviewRemoveReason", () ->
        {
            if (warning.getValue() != null) reasons.remove(warning.getValue().id());
            reason.clear(); draftChanged();
        });
        note.setId("closeReviewNote"); note.setPromptText("Optional review note (maximum 1,000 characters)"); note.setPrefRowCount(2);
        attested.setId("closeReviewAttested"); attested.setWrapText(true);
        VBox editor = new VBox(8, new Label("External references"), kind, reference, description,
                new FlowPane(8, 8, add, remove), new Label("Optional warning reasons"), warning, reason,
                new FlowPane(8, 8, setReason, removeReason), new Label("Review note"), note, attested);
        editor.disableProperty().bind(busy.or(saved).or(UiPermissionGate.deniedProperty(ApplicationPermission.BOOKKEEPING_WRITE)));
        ScrollPane scroll = new ScrollPane(editor); scroll.setFitToWidth(true); scroll.setMinSize(0, 0); scroll.setId("closeReviewEditorScroll");
        references.setId("closeReviewReferences"); history.setId("closeReviewHistory");
        history.setPlaceholder(new Label("Use Refresh History to load retained reviews for these exact dates."));
        references.setPlaceholder(new Label("Add external references to this draft before Save Review."));
        references.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        history.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        references.getColumns().addAll(column("Kind", "kind", 140, row -> row.kind().name()),
                column("Reference", "reference", 320, CloseReviewEvidenceService.Reference::reference),
                column("Description", "description", 300, CloseReviewEvidenceService.Reference::description));
        var format = CompanyUiFormat.activeCompany();
        history.getColumns().addAll(column("Recorded", "recorded", 175, row -> format.formatDateTime(row.recordedAt())),
                column("Recorded by", "actor", 150, CloseReviewEvidenceService.Review::actor),
                column("Blockers retained", "blockers", 130, row -> Long.toString(row.blockers())),
                column("Content revision", "revision", 320, row -> row.payload().dataRevision()));
        retained.setEditable(false); retained.setId("closeReviewRetainedDetails");
        history.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, value) -> showRetained(value));
        SplitPane draftTable = split("period-close-review-references", new Label("Draft references — included only when Save Review succeeds"), references);
        SplitPane historyTable = split("period-close-review-history", new Label("Most recent 100 reviews for these exact dates — retained, read-only facts"), history, retained);
        TabPane details = new TabPane(new Tab("Draft References", draftTable), new Tab("Recorded Reviews", historyTable));
        details.setId("closeReviewDetails");
        details.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        SplitPane main = split("period-close-review-editor", scroll, details);
        VBox.setVgrow(main, javafx.scene.layout.Priority.ALWAYS);
        root.getChildren().add(main);
        start.valueProperty().addListener((observable, oldValue, value) -> invalidate(false));
        end.valueProperty().addListener((observable, oldValue, value) -> invalidate(false));
        note.textProperty().addListener((observable, oldValue, value) -> draftChanged());
        attested.selectedProperty().addListener((observable, oldValue, value) -> updateSave());
        reference.textProperty().addListener((observable, oldValue, value) -> updateSave());
        description.textProperty().addListener((observable, oldValue, value) -> updateSave());
        reason.textProperty().addListener((observable, oldValue, value) -> updateSave());
        busy.addListener((observable, oldValue, value) -> updateSave());
        saved.addListener((observable, oldValue, value) -> updateSave());
        UiPermissionGate.deniedProperty(ApplicationPermission.EXPORT).addListener(new javafx.beans.value.WeakChangeListener<>(permissionListener));
        UiPermissionGate.deniedProperty(ApplicationPermission.BOOKKEEPING_WRITE).addListener(new javafx.beans.value.WeakChangeListener<>(permissionListener));
        updateSave();
    }

    private final javafx.beans.value.ChangeListener<Boolean> permissionListener = (observable, oldValue, value) ->
    {
        if (!UiPermissionGate.allows(ApplicationPermission.EXPORT)) invalidate(true);
        updateSave();
    };

    @Override public String title()
    {
        return "Review Evidence";
    }
    @Override public Node root()
    {
        return root;
    }
    @Override public Set<AppCommand> commandCapabilities()
    {
        return canSave() && UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE) ? Set.of(AppCommand.SAVE_ACTIVE) : Set.of();
    }
    @Override public Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return Optional.of(ApplicationPermission.BOOKKEEPING_WRITE);
    }
    @Override public String commandLabel(AppCommand command)
    {
        return command == AppCommand.SAVE_ACTIVE ? "Save Review" : AppPanel.super.commandLabel(command);
    }
    @Override public void setCommandCapabilitiesChangedListener(Runnable handler) { capabilitiesChanged = handler == null ? () -> { } : handler; }
    @Override public boolean hasUnsavedChanges()
    {
        return !saved.get() && (!references.getItems().isEmpty() || !note.getText().isBlank() || !reference.getText().isBlank()
                || !description.getText().isBlank() || !reason.getText().isBlank() || !reasons.isEmpty() || attested.isSelected());
    }

    private boolean canSave()
    {
        var selected = warning.getValue();
        boolean pendingReason = selected != null && !reason.getText().strip().equals(reasons.getOrDefault(selected.id(), ""));
        return !busy.get() && !saved.get() && captured != null && !references.getItems().isEmpty() && attested.isSelected()
                && reference.getText().isBlank() && description.getText().isBlank() && !pendingReason;
    }

    private void updateSave()
    {
        save.setDisable(!canSave()); capabilitiesChanged.run();
    }

    private void draftChanged()
    {
        request = UUID.randomUUID(); updateSave();
    }

    void invalidate(boolean clearDraft)
    {
        if (!javafx.application.Platform.isFxApplicationThread())
        {
            javafx.application.Platform.runLater(() -> invalidate(clearDraft)); return;
        }
        generation++; captured = null; reasons.clear(); warning.getItems().clear(); reason.clear(); attested.setSelected(false);
        history.getItems().clear(); retained.clear();
        if (clearDraft)
        {
            references.getItems().clear(); reference.clear(); description.clear(); note.clear(); saved.set(false); request = UUID.randomUUID();
        }
        status.setText("Scope changed. Preview again before recording a review. Retained records remain historical."); updateSave();
    }

    private void newReview()
    {
        if (hasUnsavedChanges() && new Alert(Alert.AlertType.CONFIRMATION, "Discard the unsaved review draft?", ButtonType.OK, ButtonType.CANCEL)
                .showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        invalidate(true);
    }

    private void addReference()
    {
        try
        {
            var value = new CloseReviewEvidenceService.Reference(kind.getValue(), reference.getText(), description.getText());
            if (references.getItems().stream().anyMatch(row -> row.kind() == value.kind() && row.reference().equals(value.reference())))
                throw new IllegalArgumentException("This reference is already in the draft");
            if (references.getItems().size() >= 100)
            {
                throw new IllegalArgumentException("A review supports at most 100 references");
            }
            references.getItems().add(value); reference.clear(); description.clear(); draftChanged();
        }
        catch (RuntimeException ex)
        {
            status.setText(UiErrors.safeMessage(ex));
        }
    }

    private void keepReason()
    {
        if (warning.getValue() == null || reason.getText().isBlank() || reason.getText().strip().length() > 1000)
        {
            status.setText("Select a warning and enter a reason of at most 1,000 characters."); return;
        }
        reasons.put(warning.getValue().id(), reason.getText().strip()); reason.setText(reason.getText().strip()); draftChanged();
    }

    private void preview()
    {
        var service = services.get(); LocalDate from = start.getValue(), through = end.getValue(); String key = scope.get();
        captured = null; reasons.clear(); warning.getItems().clear(); attested.setSelected(false); generation++;
        long operation = generation; var done = new CompletableFuture<AccountingReadiness>(); completion = done; busy.set(true);
        status.setText("Capturing review snapshot…");
        UiAsync.run("review-evidence-preview", () ->
        {
            try { return service.preview(from, through); }
            catch (java.io.IOException ex)
            {
                throw new java.io.UncheckedIOException(ex);
            }
        }, result ->
        {
            busy.set(false);
            if (operation != generation || !key.equals(scope.get()) || !UiPermissionGate.allows(ApplicationPermission.EXPORT))
            {
                invalidate(false); done.completeExceptionally(new java.util.concurrent.CancellationException("Review scope changed")); return;
            }
            captured = result;
            warning.getItems().setAll(result.findings().stream().filter(row -> row.severity() == AccountingReadiness.Severity.WARNING).toList());
            status.setText(result.companyCode() + ": " + result.automatedStatus() + "; " + result.blockers() + " blocker(s) retained. Content revision "
                    + result.dataRevision() + ". Saving records review facts only.");
            updateSave(); done.complete(result);
        }, error -> { busy.set(false); captured = null; status.setText("Could not preview review: " + UiErrors.safeMessage(error)); updateSave(); done.completeExceptionally(error); });
    }

    @Override public void onSave()
    {
        if (!canSave() || !UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE))
        {
            return;
        }
        final CloseReviewEvidenceService.Command command;
        try
        {
            command = new CloseReviewEvidenceService.Command(request, captured.start(), captured.end(), captured.dataRevision(),
                    java.util.List.copyOf(references.getItems()), Map.copyOf(reasons), note.getText(), attested.isSelected());
        }
        catch (RuntimeException ex)
        {
            status.setText(UiErrors.safeMessage(ex)); return;
        }
        var service = services.get(); long operation = generation; String key = scope.get();
        var done = new CompletableFuture<CloseReviewEvidenceService.Review>(); completion = done; busy.set(true);
        status.setText("Recording factual review…");
        UiAsync.run("review-evidence-save", () ->
        {
            try { return service.save(command); }
            catch (java.io.IOException ex)
            {
                throw new java.io.UncheckedIOException(ex);
            }
        }, result ->
        {
            busy.set(false);
            if (generation != operation || !key.equals(scope.get()) || !UiPermissionGate.allows(ApplicationPermission.EXPORT))
            {
                invalidate(true);
                status.setText("Review was recorded for the previous company/dates; inspect its Audit History. Current draft cleared.");
                done.complete(result); return;
            }
            saved.set(true); history.getItems().add(0, result); history.getSelectionModel().select(result);
            status.setText("Recorded review by " + result.actor() + ". " + result.blockers() + " blocker(s) remain; no close authorization.");
            done.complete(result);
        }, error -> { busy.set(false); status.setText("Could not save review: " + UiErrors.safeMessage(error)); done.completeExceptionally(error); });
    }

    private void refreshHistory()
    {
        var service = services.get(); LocalDate from = start.getValue(), through = end.getValue(); String key = scope.get(); long operation = generation;
        busy.set(true);
        UiAsync.run("review-evidence-history", () -> service.history(from, through), result ->
        {
            busy.set(false);
            if (generation == operation && key.equals(scope.get()) && UiPermissionGate.allows(ApplicationPermission.EXPORT)) history.getItems().setAll(result);
        }, error -> { busy.set(false); history.getItems().clear(); retained.clear(); status.setText("Could not load review history: " + UiErrors.safeMessage(error)); });
    }

    private void showRetained(CloseReviewEvidenceService.Review review)
    {
        if (review == null) { retained.clear(); return; }
        var format = CompanyUiFormat.activeCompany();
        StringBuilder detail = new StringBuilder("Retained factual self-attestation — no close authorization\n")
                .append(review.companyCode()).append(" | ").append(format.formatDate(LocalDate.parse(review.payload().start())))
                .append(" through ").append(format.formatDate(LocalDate.parse(review.payload().end())))
                .append("\nContent revision: ").append(review.payload().dataRevision()).append("\nNote: ").append(review.payload().note()).append('\n');
        review.payload().references().forEach(value -> detail.append(value.kind()).append(": ").append(value.reference()).append(" — ").append(value.description()).append('\n'));
        review.payload().findings().forEach(value -> detail.append(value.severity()).append(" | ").append(value.area()).append(" | ").append(value.detail())
                .append("\nRecorded reason: ").append(review.payload().warningReasons().getOrDefault(value.id(), "None")).append('\n'));
        retained.setText(detail.toString());
    }

    CompletableFuture<?> completion()
    {
        return completion;
    }

    private static Button button(String label, String id, Runnable action)
    {
        Button button = new Button(label); button.setId(id); button.setOnAction(event -> action.run()); return button;
    }

    private static SplitPane split(String key, Node... nodes)
    {
        SplitPane split = new SplitPane(nodes); split.setOrientation(Orientation.VERTICAL); split.setMinSize(0, 0);
        CompanySplitPaneStateBinder.bind(split, key, 0.5); return split;
    }

    private static <T> TableColumn<T, String> column(String title, String key, int width, Function<T, String> value)
    {
        TableColumn<T, String> column = new TableColumn<>(title); column.setId(key); column.setUserData(key); column.setPrefWidth(width);
        column.setSortable(true); column.setResizable(true); column.setReorderable(true);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue()))); return column;
    }
}
