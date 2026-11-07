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

## P29-C4 — Windows rendering compatibility

Owner diagnostic testing confirmed that `JAVA_TOOL_OPTIONS='-Dprism.order=sw'` restores the missing controls. The standard `FxMain` launcher now applies this default on Windows before `Application.launch`, so the normal `mvn -Pui clean compile javafx:run` command needs no environment workaround. Explicit pipeline selections remain authoritative. Other operating systems keep their existing renderer defaults.

Unit tests cover Windows detection, idempotence, explicit overrides (including an empty value), other platforms and configuration before the actual application-launch callback. The display test uses production window decorations. A separate Windows CI job must run the real application-root snapshot and contrast tests without skips.

Owner review: close the app, update to this slice and run the normal launch command without setting `JAVA_TOOL_OPTIONS`. Confirm the console reports the Windows software default and the login/workspace paints correctly. The diagnostic proves a graphics-pipeline compatibility issue; it does not identify the particular driver or hardware defect.

## P29-C5 — Tooltip popup contrast

After merged PR #410, owner testing found black tooltip text on a dark gray
bubble in a light theme. An actual popup regression reproduces a 1.26:1 contrast
ratio: workspace foreground selectors reach the tooltip skin while the
user-agent stylesheet supplies its dark bubble. Pair the tooltip background and
foreground in application CSS, including the skin's Text node. Light themes use
a light neutral bubble with black text; dark themes use a dark neutral bubble
with white text. Preserve tooltip contents, wrapping, installation and permission
explanations.

The regression opens automatic full-text and custom help tooltips under every
theme, checks at least 4.5:1 contrast and the actual painted text, and reuses the
same tooltips across theme changes. It belongs to `ThemeTextContrastFxTest`,
already required by the Linux and Windows display jobs. Focused display, clean
verification and exact-head CI results are recorded in PLAN.

Owner check: hover an ordinary label and a control with help text in Primer
Light, Nord Light, Cupertino Light and System Default. Confirm a light bubble
with readable black text. Switch to each dark theme, hover the same controls,
then switch back; confirm the text remains readable after each change.
