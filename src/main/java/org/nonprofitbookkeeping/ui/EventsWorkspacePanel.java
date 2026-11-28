package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import org.nonprofitbookkeeping.service.ApplicationPermission;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Two views of the same Activity records, with one maintenance draft and workspace tab. */
final class EventsWorkspacePanel implements AppPanel
{
    private final EventAccountingPanel accounting = new EventAccountingPanel();
    private final ActivitiesPanel maintenance = new ActivitiesPanel();
    private final WorkPaneTabs tabs = new WorkPaneTabs("eventsWorkspaceTabs")
            .addPane("Accounting", accounting.root())
            .addPane("Manage Events", maintenance.root());
    private Runnable capabilitiesChanged = () -> {};
    private Long activityOnEntry;
    private boolean openingMaintenance;

    EventsWorkspacePanel()
    {
        tabs.getSelectionModel().select(1);
        tabs.getSelectionModel().selectedIndexProperty().addListener((observable, previous, selected) ->
        {
            if (selected.intValue() == 0)
            {
                accounting.onPanelShown();
                Long managed = maintenance.selectedActivityId();
                if (!Objects.equals(managed, activityOnEntry)) accounting.selectActivity(managed);
            }
            else
            {
                activityOnEntry = maintenance.selectedActivityId();
                if (!openingMaintenance) maintenance.selectActivity(accounting.selectedActivityId());
            }
            capabilitiesChanged.run();
        });
        maintenance.setCommandCapabilitiesChangedListener(() -> capabilitiesChanged.run());
        accounting.setManageAction(() -> tabs.getSelectionModel().select(1));
        maintenance.setViewAccountingAction(() ->
        {
            tabs.getSelectionModel().select(0);
            accounting.selectActivity(maintenance.selectedActivityId());
        });
    }

    void showDestination(AppPanelId requested)
    {
        if (requested == AppPanelId.EVENT_ACCOUNTING)
        {
            tabs.getSelectionModel().select(0);
            return;
        }
        openingMaintenance = true;
        try
        {
            tabs.getSelectionModel().select(1);
        }
        finally
        {
            openingMaintenance = false;
        }
    }

    EventAccountingPanel accounting()
    {
        return accounting;
    }

    private AppPanel active()
    {
        return tabs.getSelectionModel().getSelectedIndex() == 0 ? accounting : maintenance;
    }

    @Override public String title() { return "Events / Activities"; }
    @Override public Node root() { return tabs; }
    @Override public Set<AppCommand> commandCapabilities() { return active().commandCapabilities(); }
    @Override public Optional<ApplicationPermission> requiredPermission(AppCommand command) { return active().requiredPermission(command); }
    @Override public String commandLabel(AppCommand command) { return active().commandLabel(command); }
    @Override public RunCommandResult executeCommand(AppCommand command) { return active().executeCommand(command); }
    @Override public void onNew() { active().onNew(); }
    @Override public void onSave() { active().onSave(); }
    @Override public boolean hasUnsavedChanges() { return maintenance.hasUnsavedChanges(); }
    @Override public void onPanelShown() { active().onPanelShown(); }

    @Override
    public void onPanelShownFrom(AppPanelId origin, String title)
    {
        maintenance.onPanelShownFrom(origin, title);
        if (active() == accounting) accounting.onPanelShown();
    }

    @Override
    public void onImportAddSuggestion(ImportAddSuggestion suggestion)
    {
        showDestination(AppPanelId.ACTIVITIES);
        maintenance.onImportAddSuggestion(suggestion);
    }

    @Override
    public void setCommandCapabilitiesChangedListener(Runnable listener)
    {
        capabilitiesChanged = listener;
    }

    @Override
    public void setCommandResultListener(Consumer<RunCommandResult> listener)
    {
        maintenance.setCommandResultListener(listener);
    }
}
