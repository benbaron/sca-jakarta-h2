package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.layout.GridPane;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.service.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Native inline chart metadata/clone editor; account definitions use the existing account panel. */
final class NativeChartEditorPane extends VBox implements AppPanel
{
    enum Mode
    {
        NEW, EDIT, CLONE
    }
    private final Supplier<ChartAdminService> services;
    private final Consumer<ChartDetails> saved;
    private final Runnable back;
    private final TextField name = new TextField();
    private final TextField version = new TextField();
    private final CheckBox retired = new CheckBox("Retired");
    private final ComboBox<ChartSource> source = new ComboBox<>();
    private final Label heading = new Label();
    private final Label target = new Label();
    private final Label details = new Label();
    private final Label explanation = new Label();
    private final Label status = new Label();
    private final Label unavailable = new Label();
    private final Label sourceCaption = new Label("Source chart (clone)");
    private final Label draftLifecycle = new Label("DRAFT");
    private final Button save = new Button("Save Chart");
    private final Button cancel = new Button("Cancel Chart Changes");
    private final Button backButton = new Button("Back to Accounts");
    private final Button refresh = new Button("Refresh Chart Details");
    private final GridPane form = new GridPane();
    private final FormDirtyTracker dirty;
    private Mode mode;
    private ChartDetails snapshot;
    private Long loadedChartId;
    private CoaJsonReviewPane.Scope scope;
    private boolean busy;
    private boolean populating;
    private Runnable capabilitiesChanged = () ->
    {
    };
    private Consumer<RunCommandResult> resultListener = ignored ->
    {
    };
    private final javafx.beans.value.ChangeListener<Boolean> permission = (o,a,b) -> availability();
    NativeChartEditorPane(Supplier<ChartAdminService> services, Consumer<ChartDetails> saved, Runnable back)
    {
        this.services = services;
        this.saved = saved;
        this.back = back;
        setId("nativeChartEditor");
        setSpacing(6);
        setPadding(new Insets(8));
        setMinSize(0,0);
        name.setId("nativeChartName");
        version.setId("nativeChartVersion");
        retired.setId("nativeChartRetired");
        source.setId("nativeChartCloneSource");
        source.setMinWidth(0);
        source.setMaxWidth(Double.MAX_VALUE);
        source.setPrefWidth(520);
        heading.setId("nativeChartHeading");
        details.setId("nativeChartDetails");
        status.setId("nativeChartStatus");
        unavailable.setId("nativeChartUnavailable");
        for (Label label : List.of(heading,target,details,explanation,status,unavailable)) label.setWrapText(true);
        save.setId("nativeChartSave");
        cancel.setId("nativeChartCancel");
        backButton.setId("nativeChartBack");
        refresh.setId("nativeChartRefresh");
        save.setOnAction(event -> onSave());
        cancel.setOnAction(event -> cancel());
        backButton.setOnAction(event ->
        {
            if (!busy) back.run();
        });
        refresh.setOnAction(event -> refresh());
        form.setHgap(8);
        form.setVgap(10);
        form.setMinWidth(Region.USE_PREF_SIZE);
        form.addRow(0,new Label("Target company"),target);
        form.addRow(1,sourceCaption,source);
        form.addRow(2,new Label("Chart name"),name);
        form.addRow(3,new Label("Version"),version);
        StackPane lifecycle = new StackPane(retired,draftLifecycle);
        lifecycle.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        form.addRow(4,new Label("Lifecycle"),lifecycle);
        form.add(details,0,5,2,1);
        form.add(explanation,0,6,2,1);
        details.setMaxWidth(750);
        explanation.setMaxWidth(750);
        GridPane.setHgrow(name,Priority.ALWAYS);
        GridPane.setHgrow(version,Priority.ALWAYS);
        VBox header = new VBox(6,heading,new FlowPane(8,6,backButton,save,cancel,refresh),status,unavailable);
        SplitPane split = new SplitPane(scroll(header),scroll(form));
        split.setOrientation(Orientation.VERTICAL);
        split.setId("nativeChartEditorSplit");
        CompanySplitPaneStateBinder.bind(split,"native-chart-editor",.28);
        VBox.setVgrow(split,Priority.ALWAYS);
        getChildren().add(split);
        dirty = new FormDirtyTracker(this::formSnapshot);
        name.textProperty().addListener((o,a,b) -> availability());
        version.textProperty().addListener((o,a,b) -> availability());
        retired.selectedProperty().addListener((o,a,b) -> availability());
        source.valueProperty().addListener((o,a,b) ->
        {
            if (!populating && b != null && mode == Mode.CLONE) loadSource(b.id(),false);
        });
        UiPermissionGate.deniedProperty(ApplicationPermission.BOOKKEEPING_WRITE).addListener(new javafx.beans.value.WeakChangeListener<>(permission));
        var weak = new java.lang.ref.WeakReference<>(this);
        Runnable changed = () ->
        {
            var pane = weak.get();
            if (pane != null)
            {
                if (pane.scope != null && !pane.scope.currentScope()) pane.status.setText("Return to the original company/database to continue this retained chart draft.");
                pane.availability();
            }
        };
        MainWindow.sharedSessionState().onMultiCompanyChanged(ignored -> onFx(changed));
        MainWindow.sharedSessionState().onDatabaseSelectionChanged(ignored -> onFx(changed));
        FullTextTooltipInstaller.install(this);
        availability();
    }

    void begin(Mode requested, Long selectedId)
    {
        if (busy || !UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE)) return;
        if (hasUnsavedChanges())
        {
            status.setText("Chart draft restored. Save or Cancel Chart Changes before starting another chart operation.");
            return;
        }
        mode = requested;
        scope = CoaJsonReviewPane.Scope.current();
        snapshot = null;
        loadedChartId = null;
        populating = true;
        name.clear();
        version.setText("1");
        retired.setSelected(false);
        source.getItems().clear();
        source.setValue(null);
        populating = false;
        dirty.markClean();
        target.setText(scope.company());
        heading.setText(requested == Mode.NEW ? "New Chart" : requested == Mode.CLONE ? "Clone Chart" : "Edit Chart");
        details.setText(requested == Mode.NEW ? "New chart: DRAFT, no accounts yet." : "Loading chart definitions…");
        explanation.setText("An unused, non-current DRAFT can be removed with Delete Draft Chart after Back to Accounts. ACTIVE, RETIRED and used charts are retained. Retire a non-current chart; unretiring restores DRAFT. Make Active Chart is a separate action.");
        status.setText(requested == Mode.NEW ? "Enter a name and version, then Save Chart." : "Loading…");
        if (requested == Mode.EDIT && selectedId != null) loadSource(selectedId,true);
        else if (requested == Mode.CLONE)
        {
            run("native-chart-sources",ChartAdminService::listCloneSources,choices ->
            {
                populating = true;
                source.getItems().setAll(choices);
                ChartSource initial = choices.stream().filter(c -> selectedId != null && c.id() == selectedId).findFirst()
                .orElse(choices.isEmpty() ? null : choices.get(0));
                source.setValue(initial);
                populating = false;
                if (initial != null) loadSource(initial.id(),true);
                else
                {
                    details.setText("No owned source charts exist in this database. Use New Chart to start an empty chart.");
                    availability();
                }
            });
        }
        availability();
    }

    private void loadSource(long id, boolean initial)
    {
        if (busy || scope == null || !scope.currentScope()) return;
        boolean editing = mode == Mode.EDIT;
        loadedChartId = id;
        snapshot = null;
        run("native-chart-details",service -> editing ? service.load(id) : service.inspectCloneSource(id),result ->
        {
            snapshot = result;
            populating = true;
            if (editing && initial)
            {
                name.setText(result.name());
                version.setText(result.version());
                retired.setSelected(result.status() == ChartStatus.RETIRED);
            }
            else if (!editing && initial)
            {
                name.setText(result.name() + " copy");
                version.setText(result.version());
            }
            populating = false;
            if (editing && initial) dirty.markClean();
            showDetails();
            status.setText(initial ? "Review the chart details and save when ready."
                : "Chart details refreshed; your name/version/lifecycle edits are retained. Review before saving.");
            availability();
        });
    }

    private void showDetails()
    {
        if (snapshot == null) return;
        details.setText(snapshot.toString() + "\n" + snapshot.accountCount() + " accounts including groups/inactive."
            + (mode == Mode.CLONE ? "\nAll opening balances reset to zero (" + snapshot.nonzeroOpeningCount() + " nonzero fields)."
            + " Aliases, report mappings and schedule requirements are copied. Journal entries, bank links, reconciliation and import identities stay with the source."
            : "\nCurrently " + (snapshot.currentForCompany() ? "selected for the company." : "not selected for the company.")
            + " Saved name/version: " + snapshot.name() + " / " + snapshot.version()));
    }

    private void refresh()
    {
        if (busy || mode == null || scope == null || !scope.currentScope()) return;
        Long id = mode == Mode.EDIT ? loadedChartId : source.getValue() == null ? null : source.getValue().id();
        if (id != null) loadSource(id,false);
    }

    @Override
    public void onSave()
    {
        if (!ready()) return;
        String proposedName = name.getText();
        String proposedVersion = version.getText();
        if (mode == Mode.CLONE)
        {
            ChartCloneCommand command = new ChartCloneCommand(snapshot.id(),snapshot.revision(),proposedName,proposedVersion);
            run("native-chart-clone",service -> service.cloneChart(command),this::saved);
        }
        else
        {
            ChartCommand command = new ChartCommand(mode == Mode.NEW ? null : snapshot.id(),proposedName,proposedVersion,
                mode == Mode.EDIT && retired.isSelected(),mode == Mode.NEW ? null : snapshot.revision());
            run("native-chart-save",service -> service.save(command),this::saved);
        }
    }

    private void saved(ChartDetails result)
    {
        snapshot = result;
        mode = Mode.EDIT;
        dirty.markClean();
        showDetails();
        status.setText("Saved chart " + result.name() + " / " + result.version() + " (ID " + result.id() + "). It was not automatically activated.");
        resultListener.accept(new RunCommandResult(true,status.getText()));
        saved.accept(result);
    }

    private void cancel()
    {
        if (busy) return;
        mode = null;
        snapshot = null;
        loadedChartId = null;
        dirty.markClean();
        status.setText("Chart changes cancelled; saved charts are unchanged.");
        availability();
        back.run();
    }

    private <T> void run(String task, Function<ChartAdminService,T> action, Consumer<T> completed)
    {
        if (busy || scope == null || !scope.currentScope()) return;
        final ChartAdminService service;
        try
        {
            service = services.get();
        }
        catch (RuntimeException ex)
        {
            status.setText(UiErrors.safeMessage(ex));
            availability();
            return;
        }
        CoaJsonReviewPane.Scope started = scope;
        busy = true;
        status.setText("Working…");
        availability();
        UiAsync.run(task,() -> action.apply(service),result ->
        {
            busy = false;
            if (!started.currentScope())
            {
                status.setText("Company/database changed; the original chart draft was retained. Return before continuing.");
                availability();
                return;
            }
            completed.accept(result);
            availability();
        },failure ->
        {
            busy = false;
            status.setText("Could not save/load chart: " + UiErrors.safeMessage(failure));
            resultListener.accept(new RunCommandResult(false,status.getText()));
            availability();
        });
    }

    private boolean ready()
    {
        return mode != null && !busy && scope != null && scope.currentScope()
        && UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE) && !name.getText().isBlank() && !version.getText().isBlank()
        && (mode == Mode.NEW || snapshot != null) && (mode != Mode.EDIT || dirty.isDirty());
    }

    private void availability()
    {
        boolean writable = !busy && scope != null && scope.currentScope() && UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE);
        form.setDisable(!writable);
        retired.setDisable(mode != Mode.EDIT || snapshot == null || snapshot.currentForCompany());
        retired.setVisible(mode == Mode.EDIT);
        retired.setManaged(retired.isVisible());
        source.setDisable(!writable || mode != Mode.CLONE);
        source.setVisible(mode == Mode.CLONE);
        source.setManaged(source.isVisible());
        sourceCaption.setVisible(source.isVisible());
        sourceCaption.setManaged(source.isVisible());
        draftLifecycle.setVisible(mode != Mode.EDIT);
        draftLifecycle.setManaged(draftLifecycle.isVisible());
        save.setText(mode == Mode.CLONE ? "Save Cloned Chart" : "Save Chart");
        save.setDisable(!ready());
        backButton.setDisable(busy);
        cancel.setDisable(busy);
        refresh.setDisable(!writable || loadedChartId == null);
        unavailable.setText(busy ? "Operation in progress; chart controls are disabled."
            : !UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE) ? "Creating, cloning and editing chart metadata requires BOOKKEEPING_WRITE (ACCOUNTANT/MANAGER/ADMIN)."
            : scope != null && !scope.currentScope() ? "Return to the original company/database."
            : mode == Mode.EDIT && snapshot != null && snapshot.currentForCompany() ? "The current chart cannot be retired; make another chart active first."
            : "New and cloned charts start as DRAFT. Use Make Active Chart only when you intend to change the company's selection.");
        capabilitiesChanged.run();
    }

    private Object formSnapshot()
    {
        return List.of(name.getText(),version.getText(),retired.isSelected(),source.getValue() == null ? "" : source.getValue().id());
    }

    boolean busy()
    {
        return busy;
    }
    @Override public String title()
    {
        return "Chart Details";
    }
    @Override public Node root()
    {
        return this;
    }
    @Override public void onPanelShown()
    {
        availability();
    }
    @Override public boolean hasUnsavedChanges()
    {
        return mode != null && (dirty.isDirty() || mode == Mode.CLONE && snapshot != null);
    }
    @Override public Set<AppCommand> commandCapabilities()
    {
        return ready() ? AppPanel.capabilities(AppCommand.SAVE_ACTIVE) : Set.of();
    }
    @Override public String commandLabel(AppCommand command)
    {
        return mode == Mode.CLONE ? "Save Cloned Chart" : "Save Chart";
    }
    @Override public Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return Optional.of(ApplicationPermission.BOOKKEEPING_WRITE);
    }
    @Override public String commandResultMessage(AppCommand command)
    {
        return status.getText();
    }
    @Override public String saveUnavailableReason()
    {
        return "Enter a chart name/version, load its details and use a bookkeeping role in the original scope before saving.";
    }
    @Override public void setCommandCapabilitiesChangedListener(Runnable listener)
    {
        capabilitiesChanged = listener;
    }
    @Override public void setCommandResultListener(Consumer<RunCommandResult> listener)
    {
        resultListener = listener;
    }

    private static ScrollPane scroll(Node content)
    {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        pane.setMinSize(0,0);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return pane;
    }

    private static void onFx(Runnable action)
    {
        if (javafx.application.Platform.isFxApplicationThread()) action.run();
        else javafx.application.Platform.runLater(action);
    }
}
