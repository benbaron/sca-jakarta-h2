# P29-S1 — Light-theme text contrast

## User-visible correction

The Windows Light theme must render production workspace text with a readable foreground rather than inheriting a low-contrast system accent from platform control styles. The native Windows title bar may continue to use the operating-system accent color. Inside the application, ordinary text uses black on light painted surfaces and white on dark painted surfaces, independently of the overall theme. A post-layout workspace enforcer applies that foreground using inline JavaFX foreground declarations to existing and dynamically created labeled controls and text nodes, so author and user-agent stylesheets cannot replace the contract. The application adopts the [AtlantaFX](https://github.com/mkpaz/atlantafx) theme set for control and surface styling.

AtlantaFX 2.0.1 is the latest line compatible with this application's Java 17 baseline. Its complete built-in set is exposed: **Primer Light**, **Primer Dark**, **Nord Light**, **Nord Dark**, **Cupertino Light**, **Cupertino Dark**, and **Dracula**, alongside **System Default (JavaFX)**. Existing persisted `LIGHT` and `DARK` values remain valid and map to Primer Light and Primer Dark.

## Required inspection

- AtlantaFX 2.0.1 theme API and built-in theme catalog
- `pom.xml`
- `src/main/resources/ui/styles.css`
- `ProductionWorkspaceWindow.applyPreferences(...)`
- `UiThemePreference`
- Settings theme selection and persistence tests
- `doc/ui_design_rules.md`

## Acceptance

- All seven AtlantaFX themes are available by readable names and apply immediately.
- Existing Light/Dark preference files remain compatible; newly selected themes persist across restart.
- Application shell surfaces use AtlantaFX palette variables while ordinary text is chosen by actual surface luminance, including white report cells in dark themes. The foreground survives a later CSS pass and applies to nodes added after the initial scene build.
- Unit regressions bind every preference to the correct AtlantaFX theme, dark/light shell classification, persistence and production consumption. An actual JavaFX CSS test applies every preference under a virtual display and verifies foreground/background contrast, explicit white and dark surfaces, white reports inside dark themes, and actual application startup painting.
- Focused and full Maven verification pass, followed by exact-head GitHub clean, repeat and production JavaFX checks.
- Owner check: select each theme, revisit Administration / Preferences, and confirm menu, toolbar, tab, form, navigation and inspector text remains readable on Windows. In particular, Primer Light, Nord Light and Cupertino Light must use dark ordinary text.
