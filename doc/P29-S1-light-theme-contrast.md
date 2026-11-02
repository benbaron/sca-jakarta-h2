# P29-S1 — Light-theme text contrast

## User-visible correction

The Windows Light theme must render production workspace text in near-black rather than inheriting a low-contrast blue foreground from platform control styles. This applies to menus, toolbar controls, form labels and values, tabs, navigation, inspector content, tables, lists and links. Blue remains an accent for focus outlines, selection backgrounds and icons.

The existing **Light**, **Dark** and **System Default** choices remain. This correction changes only **Light**; adding another theme would make the readable option less discoverable and would leave the reported Light theme defective.

## Required inspection

- `src/main/resources/ui/styles.css`
- `ProductionWorkspaceWindow.applyPreferences(...)`
- `UiThemePreference`
- Settings theme selection and persistence tests
- `doc/ui_design_rules.md`

## Acceptance

- Light-theme foreground rules explicitly cover ordinary JavaFX control roles and the production shell's selected/accent text roles.
- Dark and System Default theme behavior remains unchanged.
- A unit regression binds the Light-theme selectors to the near-black foreground.
- Focused and full Maven verification pass, followed by exact-head GitHub clean, repeat and production JavaFX checks.
- Owner check: select **Light**, revisit Administration / Preferences, and confirm menu, toolbar, tab, form, navigation and inspector text is comfortably readable on Windows.
