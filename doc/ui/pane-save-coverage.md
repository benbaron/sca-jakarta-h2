# Pane and subpane Save coverage (P17-C13)

Scope: every current `PanelFactory` destination, its nested editors, and dialogs reachable from production. The retired Ledger Register and Transaction Editor identifiers resolve to the same Journal. Navigation, Inspector and Alerts are read-only shell surfaces; table/divider geometry saves automatically through existing company state binders. No second save service or persistence authority is introduced.

File -> Save and the toolbar Save remain present on every destination. Editable subpanes expose their specific save scope; command availability follows selected tabs, readiness, busy state and current permissions. A read-only pane has a disabled Save with an explanation. Explicit operations retain their validation and confirmation and persist immediately, so another Save is not required afterward. Export and Backup save output rather than editing accounting records.

## Editable destinations and shared editors

| Pane / subpane | Visible Save route | Existing method / authority |
|---|---|---|
| Journal header and entry lines | Save Entry; File -> Save | `JournalWorkspacePanel.onSave` -> canonical entry/update service |
| Journal Additional Details; Receivable, Payable, Prepaid Expense, Deferred Revenue, Other Asset, Other Liability supplemental tabs | Shared Save Entry; editor help states that all details save together | Same transaction command and atomic service write; no separate detail ledger |
| Banking bank editor | Save Bank; File -> Save prioritizes the dirty bank form | `saveBank` -> `BankConfigurationService` |
| Banking configured-bank-account editor | Save Bank Account | `saveBankAccount` -> same configuration authority; separate form button avoids ambiguity if both forms change |
| Budget Editor category amount | Save Draft Amount; File -> Save on DRAFT only | `saveTarget` -> `BudgetPlanService`; ACTIVE/ARCHIVED versions are read-only |
| Budget Categories | Save; File -> Save | `BudgetCategoryAdminService` |
| Payees / Counterparties | Save; File -> Save | `CounterpartyAdminService` |
| Merchants | Save; File -> Save | `MerchantAdminService` |
| Asset Register profile | Save Asset; File -> Save | `FixedAssetService` |
| Asset lifecycle event editor / preview | Save Lifecycle Event in final accounting confirmation | Existing preview then atomic lifecycle commit; the earlier dialog only collects preview input |
| Inventory item editor | Save Item; File -> Save while New/Edit is open | `saveItem` -> `InventoryService`; list/history mode does not advertise an inactive Save |
| Inventory quantity/value/Fund/Event/Budget movement editor | Save Movement in the existing preview confirmation | Receive/Issue/Adjust -> existing movement service; all shown dimensions belong to that movement |
| Chart of Accounts account editor | Save; File -> Save | Existing stable-ID account administration |
| Chart JSON import preview dialog | Save Imported Accounts | Existing guarded JSON import commit and confirmation |
| Funds | Save; File -> Save | `FundAdminService` |
| Fund Transfers | Save; File -> Save | `FundTransferService`; retained linked history corrects by explicit reversal |
| Events / Activities | Save; File -> Save | `ActivityAdminService`; active/inactive lifecycle unchanged |
| Administration -> Preferences | Save; File -> Save | `SettingsPanel.onSave`; existing machine/company preference scopes |
| Administration -> Company Admin profile | Save; File -> Save | `CompanyAdminPanel.saveCompany` -> `CompanySessionController` |
| Company Admin reporting defaults | Save Reporting Defaults | Existing `reportingDefaultsChanged` -> company preferences; choice changes continue to save immediately; company profile Save remains a separate operation |
| Company creation wizard | Save Company | Existing company wizard result and company service |
| User Admin -> Users / Roles / Company Assignments | Save User / Save Role / Save Assignment; selected-tab File -> Save | Existing user, role and assignment commands |
| User Admin -> Authentication password editor | Save Password | Existing credential service; never changes a role or silently clears a password |
| User Admin -> Authentication timeout editor | Save Timeout; nested File -> Save Timeout | Existing security-settings service; commits typed spinner text. Password edits remain separate drafts |
| Administration -> Company Ownership Diagnostics repair editor | Save Ownership Assignment; corresponding File entry when ready | Existing confirmed `assignOwner`, DATABASE_ADMIN and rollback boundaries |

## Reconciliation subpanes

| Subpane | Save route / behavior |
|---|---|
| Setup | Save New Reconciliation persists a new session through the existing setup command. Load/Edit selects durable history. It does not imply that loaded setup fields can rewrite an existing session |
| Statement manual entry | Save Manual Line invokes the existing validated statement-line service |
| Match difference explanation | Save Difference Explanation invokes the existing factual explanation service; Match/Unmatch/Mark Cleared already persist their governed operation |
| Review / Save | Save Unresolved and selected-tab File -> Save Unresolved use the same session save; Finalize remains explicit. Finalized sessions do not advertise Save |
| Checks / Exceptions factual review | Save Review and selected-tab File -> Save Review write only delivery/evidence/date/note. The menu disables while busy or without an eligible instrument; cancelled history stays read-only. Void/Reissue remain separate confirmed ledger corrections |

Reconciliation Save never finalizes a session or voids/reissues a check implicitly. All permissions, company ownership, closed-period and finalized-reconciliation protections remain in the existing services.

## Import and reviewed-bank subpanes

| Subpane | Save route / behavior |
|---|---|
| COA Rows (accepted/rejected) | Save Accepted COA Rows; selected File entry saves only the exact accepted preview after confirmation |
| SCLX Counts, Entities, Mappings, Transactions | Save Previewed SCLX Import; selected File entry uses the existing full-import confirmation. Mappings/dispositions must be re-previewed and approved under existing rules; these tabs do not independently save a partial import |
| Bank Review Rows / CSV Original Rows | Save Previewed Bank Review; selected File entry uses the existing identity-confirmed bank-review commit. Original CSV rows remain read-only source evidence |
| Bank CSV mapping profile | Save CSV Profile retains its existing explicit file chooser and authoritative service |
| Preview warnings and selected-message details | Read-only diagnostics; saved with the relevant accepted batch where applicable, no independent Save |
| Bank Transactions -> Statement Review acceptance dialog | Save Transaction creates the reviewed balanced canonical transaction via the existing acceptance service, including duplicate confirmation. It never changes bank-cleared facts implicitly |
| Bank Transactions -> Ledger Activity | Read-only projection with Journal navigation and existing export controls |

Import Save follows the selected result tab, not the last format that happened to be previewed. Busy, invalid, unconfirmed and missing previews cannot advertise a ready Save. Cancelling a confirmation leaves H2 unchanged.

## Explicit operations and read-only views

| Destination / subpane | Appropriate persistence or output |
|---|---|
| Depreciation active-period preview | Save Eligible Depreciation invokes the existing explicit batch confirmation. Each asset retains its established atomicity and retry behavior |
| Depreciation completed runs | Read-only durable history; existing report navigation/export |
| Period Close range editor | Save Closed Range uses the existing close service; Reopen Selected is an explicit persisted operation |
| Period Close factual history | Read-only; no history rewrite |
| Dashboard and its balance/budget/recent-transaction/reconciliation regions | Read-only queries and Journal navigation |
| Event Accounting summary, split details, configured-bank context and closeout review | Read-only query projections and Journal/maintenance navigation |
| Budget vs Actual | Read-only Run output |
| Report Library table/formatted results | Read-only query output; Export saves the selected supported report format |
| Audit History filters/history | Read-only audit records; filters do not rewrite history |
| Administration -> Bank Accounts | Read-only configured-account summary; edits belong to Banking |
| Administration -> Database Transfer | Backup Database saves a complete copy; restore/switch are explicit validated operations |
| Diagnostics / Help | Read-only findings/documentation |
| Database Recovery / database wizard | Explicit create/open/repair commands operate on database files; no accounting draft or fake Save |
| Login / account adoption | Explicit session/bootstrap commands; credentials are saved only by User Admin |
| Inspector / Alerts / Navigation | Read-only presentation and routing; existing shell state saves automatically |

Company active-chart selection, activation/deactivation, matching, reversals and lifecycle status changes retain their named action and confirmation. They persist immediately through the governing service and do not introduce an unsaved second model.

## Validation and owner checks

Automated checks must cover the real Save menu routing through nested Administration and Reconciliation, typed timeout persistence, password-draft retention, selected-import readiness/cancellation, live permission gating, and Inventory editor-mode availability. Existing production-route, editor, service and Xvfb tests remain required. Source-only button checks do not establish real persistence or desktop acceptance.

Manual acceptance in a disposable database/company:

1. Visit every editable row above. Edit a field, use the named Save control, reopen the record and confirm it persisted. For Journal, save header, entry-line and each supplemental-tab edit together.
2. Switch Administration/User Admin tabs and verify the File/toolbar label and availability follow the selected subpane. Type a timeout directly into the spinner, save it, and confirm unsaved password text remains until Save Password. Revisit Authentication and confirm unsaved timeout edits remain.
3. In Reconciliation, save a manual line and difference explanation, then Save Unresolved. Switch to Checks / Exceptions and save review facts through File -> Save Review; confirm it does not finalize, clear, void or reissue anything.
4. Preview COA, SCLX and bank files independently. Switch result tabs and confirm File -> Save targets only that format's confirmed batch. Cancel every confirmation and verify nothing persisted; then save valid disposable input and verify the existing import history.
5. Check the explicit movement/lifecycle/depreciation/close confirmations and save boundaries. Read-only result/history panes must explain disabled Save and retain supported Export/Backup controls.
6. Repeat with VIEWER and appropriate lower roles. Save controls requiring an ungranted permission must stay disabled, while read/refresh/navigation remain usable.
7. At laptop size and increased font size, confirm Save labels remain visible or scrollable and every touched region retains its existing dividers and both scroll directions.
