# P27-C8 all company charts in SCLX

The owner requests export of every chart owned by the selected company, including unused, DRAFT and RETIRED charts and inactive accounts. The SCLX 1.3 application extension carries charts, stable UUID-based chart identities, complete account-to-chart membership and the nullable selected chart identity. Account references are qualified by chart identity; equal codes across charts remain distinct. The published SCLX 1.3 standard has no named-chart collection but permits extensions. Chart metadata is application-specific; generic readers are not claimed to preserve it. No SCLX 1.4 standard exists in the inspected specification package. Existing files without the extension retain single-chart mapping behavior.

A nondestructive migration adds intrinsic chart UUIDs without changing assignments, statuses or history. Atomic import restores chart membership and statuses, resolves account references by portable identity and rejects foreign-company UUIDs, missing/cross-chart parents and inconsistent chart facts. Existing populated-target consent, permissions, conflict/revalidation, rollback and idempotency remain. A populated target keeps its selected chart; an empty target restores the source selection (including no selection). Existing source chart metadata conflicts require explicit Keep target, never a silent overwrite.

This deliberately replaces the earlier selected-chart export guard. Foreign-company references still fail. New Journal choices remain limited to the selected chart; exporting historical company-owned charts does not broaden interactive entry permissions.

Owner checks: export a disposable company with two charts sharing account 1000, one unused retired chart and bank/history links to both. Import into an empty disposable company, inspect names/statuses/selection and exact bank/transaction account chart links; export/reimport/restart and confirm no duplicate charts/accounts. Retain the original database/file. No user database is used for development.

Specification inspection: current benbaron/SCFX-SCA-Data-Exchange-Format SCLX-specification-package/sclx-1.3-expanded.md §4.3 defines account identity and active status, §8 permits extensions; sclx-1.3-ledger.json defines one chartOfAccounts account array and an unconstrained extensions object, no chartId or named chart collection. The earlier 1.4 proposal is withdrawn; no official standard version is introduced.

## Extension wire contract

The root version remains **1.3**. `extensions.scaJakartaH2.charts` contains exactly:

- integer `version: 1`;
- `charts`: every company-owned chart, including empty/unused charts, each with exactly `chartId`, `name`, `chartVersion`, `status` and boolean `selected`;
- `accounts`: complete membership rows, each with exactly `accountId` and `chartId`, covering the core `chartOfAccounts` array once.

Chart identities are `chart:<intrinsic UUID>`; account identities are `chart-account:<chart UUID>:<encoded account code>`. Both are independent of local numeric IDs, chart names, status, selected-chart changes and the target company code. Codes retain the existing governed normalization/escaping. Chart UUID ownership is globally checked in the target database. DRAFT/ACTIVE/RETIRED status is separate from the company selection pointer; at most one chart is selected and that chart must be ACTIVE. No selection is represented by all flags false. Names and version labels are preserved exactly within native field bounds. Chart timestamps are deliberately outside this extension; account effective dates/descriptions retain the existing SCLX exclusions. This is not a whole-database backup.

Missing/unknown fields or versions, duplicate UUIDs/identities/memberships, dangling or partial membership, duplicate codes within a chart, inconsistent account identity/membership, cross-chart parents and contradictory selection block import before writes. Foreign-company records still block export. Import never makes Journal accept new entries from unselected charts and never moves existing accounts by matching their code. Populated targets retain their selection, including an existing selection on an otherwise empty chart. New charts/account references restore atomically in the existing import transaction. Known unchanged chart/account identities are reused; changed native facts require explicit Keep target and cannot be silently overwritten. Legacy files without this extension keep the existing single-chart mapping behavior.

Readers that do not implement this extension are not promised a lossless named-chart round trip. Retain the enriched original and a database backup when exchanging with workbook/VBA or other readers.

Previously imported files retain their original identities and mapping rules. New chart-qualified account identities can change whole-transaction hashes compared with older exports of the same source history; existing targets may therefore require explicit Keep target review. Import never infers chart equivalence from a name/version/code or duplicates accounting to bypass a conflict. Older generic readers may reject, flatten or discard this application extension; retain the enriched file and original database when exchanging with such readers.
