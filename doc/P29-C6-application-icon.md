# P29-C6 application icon

The application uses original blue-and-gold open-ledger artwork. White pages and
ledger lines remain legible at small sizes; a gold chart identifies accounting.
Transparent rounded corners fit native OS surfaces. The editable vector source is
[GenerateApplicationIcons.java](../tools/GenerateApplicationIcons.java); regenerate
PNG resources with `java tools/GenerateApplicationIcons.java` from the repository.
No image library, runtime rendering dependency or external artwork is required.

Before the primary Stage is shown, install 16, 32, 48, 64, 128, 256 and 512 pixel
images through JavaFX `Stage.getIcons().setAll(...)`. The native window manager
chooses window/taskbar/switcher placement and size; do not add an in-app activity
bar or system-tray icon. Where supported, standard `java.awt.Taskbar` with
`Feature.ICON_IMAGE` sets the process's Dock/taskbar image, including macOS.
Check support before calling it. A platform rejecting optional process branding
is logged and does not prevent startup or remove the JavaFX window icons.

The same branding is used before sign-in and in the authenticated workspace,
regardless of theme or window-decoration preference. This runtime change does
not change installer/launcher or pinned-shortcut metadata, which is controlled
by native packaging. There is no new schema, permission, accounting or preference.

Owner checks: launch normally, inspect the app's running taskbar/Dock icon and OS
window switcher, sign in/out, change theme and native decorations, and restart.
Check Windows, macOS and Linux where available; native placement varies. Confirm
the artwork stays clear at normal/high display scaling and startup still works
where process-icon support is absent. Automated tests verify bundled images and
a real JavaFX Stage; virtual-display tests do not prove native Dock appearance.
