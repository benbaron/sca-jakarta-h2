# P17-C10 legacy UI retirement user testing

## User-visible changes

P17-C10 removes unreachable duplicate/reference UI implementations and a disconnected prototype customer-panel registry. It does not remove the production Dashboard, Journal, production workspace shell, or the stable `LEDGER_REGISTER` / `TXN_EDITOR` compatibility identifiers that normalize to Journal.

No accounting, persistence, report calculation, import, banking, reconciliation, period-close, or database schema behavior is changed by this slice.

## Manual verification

Use an ordinary or disposable company in the production desktop workspace.

1. Start the application normally and confirm the production workspace opens with Dashboard selected.
2. Confirm the left navigation exposes one **Journal** destination and does not expose separate **Ledger Register**, **Transaction Editor**, **Inspect Journal**, **Open Item Schedules**, **Event Lifecycle**, or generic **Import / Export** prototype destinations.
3. Open **Journal** from navigation and confirm the unified Journal register/editor opens and can be reused rather than creating a second legacy transaction tab.
4. Return to Dashboard and choose **New Transaction**. Confirm the same canonical Journal workspace opens in new-entry mode.
5. From any existing report or production action that drills to Journal, confirm the existing Journal tab is selected/reused and the navigation context is applied rather than opening a separate Ledger Register or Transaction Editor tab.
6. Make a harmless unsaved edit in Journal, choose **Close All Tabs**, and confirm the production shell still prompts before discarding unsaved work; cancel the prompt and confirm the Journal remains open.
7. Open Dashboard, Banking, Report Library, Settings, Diagnostics, and Help and confirm the normal production panels still open through the current workspace shell.
8. Confirm no alternate/reference window or duplicate Dashboard is shown during normal startup or navigation.

## Acceptance

Accept P17-C10 when the checks above pass and Maven PR Tests are green on the exact final branch head. Stop before merge for owner acceptance unless the owner separately authorizes merge.
