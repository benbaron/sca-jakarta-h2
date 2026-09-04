package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.AuthorizationPolicy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Presentation-only JavaFX permission gate backed by the current authenticated session.
 * Authoritative mutation services remain the security boundary.
 */
public final class UiPermissionGate
{
    private static final UiSessionState SESSION = ApplicationSessionContext.sharedSessionState();
    private static final Map<ApplicationPermission, ReadOnlyBooleanWrapper> DENIED =
            new EnumMap<>(ApplicationPermission.class);

    static
    {
        for (ApplicationPermission permission : ApplicationPermission.values())
        {
            DENIED.put(permission, new ReadOnlyBooleanWrapper(!allowsNow(permission)));
        }
        SESSION.onAuthenticationChanged(ignored -> refreshAll());
    }

    private UiPermissionGate()
    {
    }

    public static boolean allows(ApplicationPermission permission)
    {
        return allowsNow(Objects.requireNonNull(permission, "permission"));
    }

    public static ReadOnlyBooleanProperty deniedProperty(ApplicationPermission permission)
    {
        return DENIED.get(Objects.requireNonNull(permission, "permission")).getReadOnlyProperty();
    }

    public static String deniedExplanation(ApplicationPermission permission, String operation)
    {
        Objects.requireNonNull(permission, "permission");
        String fixedOperation = operation == null || operation.isBlank() ? "This operation" : operation.strip();
        return fixedOperation + " requires " + permission + ". Your current role does not grant that permission.";
    }

    /**
     * Adds a permission condition to an otherwise locally-managed unbound control.
     * Later calls to setDisable(...) continue to represent the control's local state.
     */
    public static void gate(ButtonBase control, ApplicationPermission permission, String operation)
    {
        gateControl(control, permission, operation);
    }

    public static void gate(Control control, ApplicationPermission permission, String operation)
    {
        gateControl(control, permission, operation);
    }

    private static void gateControl(Control control, ApplicationPermission permission, String operation)
    {
        Objects.requireNonNull(control, "control");
        Objects.requireNonNull(permission, "permission");
        if (control.disableProperty().isBound())
        {
            throw new IllegalArgumentException(
                    "Use UiPermissionGate.deniedProperty(...) in the existing disable binding for bound controls.");
        }

        GateState state = new GateState(control.isDisable(), control.getTooltip(), permission, operation);
        ChangeListener<Boolean> disabledListener = (observable, oldValue, newValue) ->
        {
            if (state.applying)
            {
                return;
            }
            state.localDisabled = Boolean.TRUE.equals(newValue);
            apply(control, state);
        };
        control.disableProperty().addListener(disabledListener);
        state.disabledListener = disabledListener;

        ChangeListener<Boolean> permissionListener = (observable, oldValue, newValue) -> apply(control, state);
        state.permissionListener = permissionListener;
        deniedProperty(permission).addListener(new WeakChangeListener<>(permissionListener));
        apply(control, state);
    }

    private static void apply(Control control, GateState state)
    {
        boolean denied = deniedProperty(state.permission).get();
        state.applying = true;
        try
        {
            control.setDisable(state.localDisabled || denied);
        }
        finally
        {
            state.applying = false;
        }
        control.setTooltip(denied
                ? new Tooltip(deniedExplanation(state.permission, state.operation))
                : state.originalTooltip);
    }

    private static boolean allowsNow(ApplicationPermission permission)
    {
        return SESSION.authenticatedUser()
                .map(session -> AuthorizationPolicy.allows(session.effectiveRoles(), permission))
                .orElse(false);
    }

    private static void refreshAll()
    {
        for (Map.Entry<ApplicationPermission, ReadOnlyBooleanWrapper> entry : DENIED.entrySet())
        {
            entry.getValue().set(!allowsNow(entry.getKey()));
        }
    }

    private static final class GateState
    {
        private boolean localDisabled;
        private boolean applying;
        private final Tooltip originalTooltip;
        private final ApplicationPermission permission;
        private final String operation;
        @SuppressWarnings("unused")
        private ChangeListener<Boolean> disabledListener;
        @SuppressWarnings("unused")
        private ChangeListener<Boolean> permissionListener;

        private GateState(
                boolean localDisabled,
                Tooltip originalTooltip,
                ApplicationPermission permission,
                String operation)
        {
            this.localDisabled = localDisabled;
            this.originalTooltip = originalTooltip;
            this.permission = permission;
            this.operation = operation;
        }
    }
}
