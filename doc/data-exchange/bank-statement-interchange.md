# Bank-statement interchange contract

## 1. Purpose and authority

This contract governs external bank-statement import and export for one configured company-owned bank account. Supported interchange families are:

- OFX 2.x XML;
- governed practical QFX variants;
- mapped source CSV; and
- normalized round-trip CSV.

Imported rows become durable review facts in `bank_import_batch`, `bank_statement_line`, and `import_issue`. Import MUST NOT automatically create canonical `Txn` or `TxnSplit` records. OFX/QFX export is statement activity, never a double-entry ledger export.

## 2. Content-first recognition

Recognition uses decoded content and envelope structure. Filename is only a hint and never sufficient.

- OFX 2.x XML requires an XML document whose root is `OFX` and whose supported message set is present.
- QFX XML may have a governed QFX/OFX header followed by an XML `OFX` root.
- QFX SGML requires a governed OFX header followed by well-formed supported SGML tags.
- mapped CSV and normalized CSV are recognized by parsed headers and a selected mapping/profile, not `.csv` alone.

A filename/content disagreement is a warning and the content result controls. Ambiguous or mixed content is rejected.

## 3. Configured account and single-account scope

The user MUST select one active `CompanyBankAccount` owned by the active company before commit. The configured record must be linked to an active bank and an active posting ledger account valid for bank use.

Each import operation has single-account scope. A source containing more than one statement account is blocking unless exactly one source account unambiguously matches the selected configured account and all other account blocks are explicitly excluded before commit. Initial P15-S6 behavior SHOULD reject the multi-account file and ask the user to split it.

Identity checks compare available institution/bank ID, routing ID, account type, account ID or masked suffix, and currency:

- exact configured account match: accepted;
- bank/institution mismatch with matching account: blocking by default;
- account-ID mismatch: blocking;
- only a masked suffix available: warning and explicit confirmation;
- no source account identity: warning and explicit confirmation for mapped CSV, blocking for OFX/QFX when the required message set defines identity; and
- source currency mismatch: blocking.

No source identity may select an account belonging to another company.

## 4. OFX 2.x XML

Supported OFX 2.x input is secure XML with root `OFX` and one supported statement response:

- `BANKMSGSRSV1/STMTTRNRS/STMTRS` for checking, savings, money-market, and similar bank accounts; or
- `CREDITCARDMSGSRSV1/CCSTMTTRNRS/CCSTMTRS` for configured credit-card accounts.

Investment, loan, bill-payment, tax, email, profile, and other message sets are unsupported and reported. A file containing an unsupported message set in addition to the selected supported statement produces a warning; a file containing no supported statement is blocking.

OFX version and header metadata MUST be recognized from content. Unsupported future versions are rejected until fixtures and tests are added.

## 5. Governed QFX variants

QFX is governed as an OFX-compatible financial-institution envelope, not merely a `.qfx` suffix.

Supported variants are:

### 5.1 XML-body QFX

An optional ASCII/UTF-8 header followed by OFX 2.x XML. Governed headers include:

- `OFXHEADER:200`;
- `DATA:OFXSGML` or `DATA:OFXXML`;
- `VERSION:200`, `202`, or another explicitly fixture-tested 2.x value;
- `SECURITY:NONE`;
- `ENCODING:UTF-8` or `USASCII`;
- `CHARSET:NONE`, `1252`, or `UTF-8` consistently with decoded bytes;
- `COMPRESSION:NONE`; and
- `OLDFILEUID`/`NEWFILEUID` values.

### 5.2 SGML-body QFX

OFX 1.x-style header and SGML body, including practical values:

- `OFXHEADER:100`;
- `DATA:OFXSGML`;
- `VERSION:102` or `103`;
- `SECURITY:NONE`;
- `ENCODING:USASCII` or `UTF-8`;
- `CHARSET:1252`, `NONE`, or `UTF-8` consistently with the bytes;
- `COMPRESSION:NONE`; and
- `OLDFILEUID:NONE` and `NEWFILEUID:NONE` or valid identifiers.

SGML permits unclosed scalar tags only where the governed parser deliberately supports the OFX 1.x convention. Container nesting and statement boundaries must still be unambiguous.

`SECURITY` other than `NONE`, encrypted payloads, unsupported compression, MIME/base64 wrappers, and unsupported message sets are blocking.

## 6. Normalized statement record

Every accepted source row normalizes to:

- source row/sequence number;
- source statement account identity;
- transaction date when supplied;
- posted date when supplied;
- signed amount;
- source transaction identity, normally FITID;
- transaction type;
- payee/name;
- memo;
- check number;
- reference number;
- currency;
- correction action and corrected FITID/reference when supplied;
- authoritative statement ledger balance and available balance when supplied at statement level; and
- source-specific metadata retained only in a bounded extension map.

At least one valid transaction or posted date, a nonzero amount, and a usable source identity or deterministic fallback fingerprint are required for review persistence.

## 7. Dates and time zones

Normalized dates are ISO local dates.

OFX/QFX accepts governed OFX date-time forms such as `YYYYMMDD`, `YYYYMMDDHHMMSS`, optional fractional seconds, and optional bracketed offset/time-zone suffix. When an explicit offset exists, the parser validates it and derives the institution's stated calendar date without applying the workstation default zone. When no offset exists, the source calendar date is retained and a missing-zone warning is recorded when time-of-day could change date interpretation.

`DTUSER`, `DTPOSTED`, `DTAVAIL`, and statement start/end dates remain distinct where available. The importer MUST NOT replace transaction date with posted date without recording that fallback.

Invalid dates, impossible offsets, or dates outside `1900-01-01` through `9999-12-31` are blocking for the affected row.

## 8. Amount and sign conventions

The normalized amount is the change in the source statement account balance:

- positive means the statement balance increases;
- negative means the statement balance decreases.

For an asset bank account, deposits are normally positive and withdrawals negative. For a liability/credit-card account, source signs are retained as statement-balance changes and the review UI MUST label the account semantics rather than silently invert them.

Mapped CSV may supply either:

- one signed amount column; or
- separate debit and credit columns.

A profile MUST declare the debit/credit convention. The normalized practical profile uses `credit - debit` for an asset bank account. Both columns populated on one row, no amount, zero amount, scale over four decimals, or values outside `DECIMAL(19,4)` are blocking. No silent rounding is allowed.

## 9. FITID, corrections, and duplicates

FITID or equivalent source transaction ID is normalized by trimming surrounding whitespace but otherwise preserves source content for display. Duplicate comparison uses a normalized comparison key scoped to company, selected bank account, source system, and source ID.

Exact duplicate detection includes:

- repeated source ID within the file;
- an existing durable statement line with the same scoped source ID; or
- when no source ID exists, an identical deterministic fingerprint.

An exact duplicate is persisted or reported as `DUPLICATE` according to the review contract and cannot auto-create ledger activity.

Probable duplicate detection uses normalized account, amount, date tolerance, payee/name, memo, check/reference, and correction metadata. It is a warning requiring review.

OFX correction metadata such as `CORRECTFITID` and `CORRECTACTION` is retained. A correction never deletes or mutates a canonical transaction automatically; it creates review facts and issues for explicit resolution.

## 10. Mapped source CSV

CSV uses RFC 4180-style quoting:

- delimiter is explicitly selected from comma, tab, semicolon, or pipe;
- quote character is `"`;
- embedded delimiters, quotes, CR, and LF require quoting;
- embedded quotes are doubled;
- line endings may be CRLF or LF and normalize to LF on export;
- a header row is required; and
- duplicate normalized headers are blocking.

A reusable mapping profile stores only mapping rules and format metadata, not financial rows or credentials. It includes:

- profile name/version;
- delimiter and charset;
- header normalization;
- field-to-column mapping;
- date formats, with explicit order and locale when month names are allowed;
- decimal separator and grouping policy;
- signed amount or debit/credit convention;
- optional fixed currency/account identity; and
- trim/blank behavior.

Profiles are company-scoped when they contain account identity. A profile preview MUST show the original row and normalized values before persistence.

## 11. Normalized round-trip CSV

Normalized export uses UTF-8 without BOM, comma delimiter, LF endings, RFC 4180 quoting, and this fixed header order:

```text
record_version,source_format,source_batch_external_id,source_file_name,statement_line_external_id,institution_id,bank_id,account_id,account_type,transaction_date,posted_date,amount,currency,source_transaction_id,transaction_type,payee_id,payee_name,memo,check_number,reference,correction_action,corrected_source_transaction_id,statement_start_date,statement_end_date,ledger_balance,available_balance,review_status,duplicate_status,matched_transaction_external_id
```

`record_version` is `1.0`. Dates are `YYYY-MM-DD`. Amounts are plain signed decimals with at most four fractional digits. Empty optional fields remain empty, not omitted. `source_batch_external_id`, `statement_line_external_id`, and `matched_transaction_external_id` are portable interchange identities, never local numeric database IDs. `review_status` and `duplicate_status` preserve durable statement-review facts without implying ledger acceptance. Rows are ordered by posted date, transaction date, source transaction ID, deterministic fingerprint, then original source row.

Importing an unchanged normalized export reproduces the same normalized records and duplicate classifications. It does not reproduce a canonical double-entry ledger.

## 12. OFX/QFX export

OFX/QFX export may emit selected durable bank-statement activity for one configured account and date range. It MUST NOT export `Txn`/`TxnSplit` as a purported ledger-exchange document and MUST NOT invent bank-authoritative balances.

When an authoritative imported statement balance is unavailable, the balance element is omitted with a warning. Exported FITID uses retained source identity when present; generated statement-only identity is visibly marked and stable for the same durable line.

Output is deterministic for a fixed request and operation timestamp, secure XML, content-recognizable, atomically written, and accompanied by counts, warnings, byte count, and SHA-256.

## 13. Durable review authority and commit behavior

`bank_import_batch` owns source metadata, selected company/account, source hash/format, status, counts, and operation notes. `bank_statement_line` owns normalized rows and user disposition. `import_issue` owns durable validation/review issues.

Creating a review batch is one transaction. Preview is in-memory. No parser, mapper, normalizer, or batch-creation operation automatically creates `Txn` or `TxnSplit`.

A later explicit accept/match operation may invoke canonical transaction services under current transaction, correction, period-close, and reconciliation protections. That later action is outside raw statement parsing.

## 14. Determinism, atomic writing, and results

Every export uses UTF-8, fixed field/property order, stable row ordering, fixed decimal/date formatting, a temporary file in the destination directory, durable flush where supported, atomic move where supported, explicit overwrite confirmation, and SHA-256 over final bytes.

Import results report source name/hash, detected variant/version/encoding, selected company/account, statement account identities, date range, authoritative balances, total/accepted/rejected/duplicate/warning/error counts, and durable batch identity. Errors identify the source row or XML/SGML path without exposing full account numbers.

## 15. XML/SGML security and resource limits

XML parsing MUST:

- enable secure processing;
- disallow `DOCTYPE`;
- disable external general and parameter entities;
- disable external DTD loading;
- disable XInclude;
- prohibit network and filesystem resolution;
- bound element depth, attributes, text, and expansion; and
- reject duplicate singleton statement/account elements.

Limits are:

| Limit | Value |
|---|---:|
| OFX/QFX/CSV file size | 64 MiB |
| Statement records | 1,000,000 |
| XML/SGML nesting depth | 64 |
| Attributes per XML element | 128 |
| Scalar/text field | 1 MiB |
| CSV columns | 128 |
| CSV logical record | 4 MiB |
| CSV field | 1 MiB |
| Mapping profiles per company | 1,000 |

Input encoding must match governed header content. UTF-8 is preferred. US-ASCII and Windows-1252 are accepted only for supported QFX headers or explicit CSV profiles and are normalized to Unicode. UTF-16/32, invalid byte sequences, NUL bytes, and undeclared encoding changes are rejected.

Malformed documents, unsupported versions, encrypted/unsupported message sets, missing account identity where required, missing or duplicate identifiers, multi-account ambiguity, amount/date violations, and limit violations are blocking. XML external-entity and exponential-entity fixtures MUST be rejected before entity resolution.

## 16. Separation from other exchange types

- OFX/QFX/CSV represent statement activity for review, not double-entry ledger history.
- SCLX is the selected-company business-data exchange and may include reviewed bank facts as one governed section.
- Chart of Accounts JSON carries no bank rows.
- Whole-database transfer carries every database record and schema state.

The UI and service names MUST say **Bank Statement Import/Export**, name the exact source variant, and never present these files as SCLX, COA, database backup, or ledger export.

## 17. P15-S6 implementation sequence

P15-S6-C1 replaces the production Import Preview route's permissive regex/suffix path with
`BankStatementParser`. The parser reads at most 64 MiB, recognizes content before filename, securely
parses governed OFX 2.x and QFX XML, deliberately normalizes governed QFX 1.x unclosed scalar tags,
and returns immutable statement/account/transaction facts. It rejects unsafe XML, unsupported or
encrypted/compressed headers, unsupported versions/message sets, multi-account ambiguity, duplicate
singletons/FITIDs, invalid dates/amounts/corrections, and bounded-resource violations before any
normalizer or H2 service is invoked.

Import Preview displays the detected variant/version, masked source account, currency, transaction
count, and content/filename warnings and explicitly states that no data changed. C1 does not authorize
the legacy File-menu staging action as a durable import. C2 through C4 add configured-account matching,
mapped CSV profiles, the one-transaction durable review write, and the production review workspaces.

P15-S6-C2 adds the first complete OFX/QFX durable-review boundary. `BankStatementReviewService`
captures the absolute source, SHA-256, active company, selected configured account, parsed document,
account-match result, normalized rows, and messages in one exact-scope preview. Commit rehashes and
reparses the file, re-resolves the company/account, and re-applies the identity policy before starting
the database write. A full OFX account-ID mismatch, bank-ID mismatch, inactive configuration,
cross-company account, missing posting account, or currency mismatch is blocking. A suffix-only
configured account is nonblocking only after explicit identity confirmation.

The C2 transaction preserves envelope variant/version/encoding, source institution/bank/account/type,
currency, statement dates and balances, row dates, amounts, unmodified trimmed source identifiers,
check/reference, and correction facts. Duplicate comparison is normalized and scoped to the active
company and selected configured account. An identical source hash, format, and target returns the
existing durable batch without adding rows or another operation audit. New review facts, issues, and
the `BANK_STATEMENT_REVIEW_IMPORTED` audit event commit together; any late failure rolls all of them
back. No `Txn` or `TxnSplit` is created.

The donor JAXB OFX field model informed supported field coverage. Its filename/manual-format selection,
static current-company authority, direct alternate-ledger writes, and reconciliation queue are not
ported.

P15-S6-C3 adds strict mapped bank CSV to the same review authority. A durable profile is owned by one
company and one configured bank account and contains only validated format/mapping metadata. Profiles
are explicitly created, replaced, listed, activated, or deactivated through
`BankCsvMappingProfileService`; they are not Java Preferences, sidecar files, credentials, or financial
rows. Mapping profiles are operational import preferences and are intentionally excluded from SCLX;
whole-database transfer remains their portable backup authority.

`BankCsvParser` accepts only a validated profile and enforces the 64 MiB file, 1,000,000-record,
128-column, 4 MiB logical-record, and 1 MiB field limits. It rejects invalid encoding, UTF-16/32,
NUL bytes, malformed quoting, duplicate normalized headers, missing mapped columns, mixed accounts or
currencies, ambiguous debit/credit rows, invalid dates, and non-`DECIMAL(19,4)` amounts before H2
mutation. The preview retains each original logical row beside its normalized projection.

`BankCsvReviewService` binds approval to the exact source hash, company, configured account, durable
profile portable identity, and canonical profile hash. It re-reads the active profile and reparses the
source before using the C2 atomic batch/line/issue/audit transaction. An unchanged source/profile/target
is the same idempotent no-op as OFX/QFX, and CSV import never creates `Txn` or `TxnSplit` rows.

P15-S6-C4 is the sole production desktop route for bank-statement review import. Import Preview lists
only active configured accounts for the active company, previews OFX/QFX through
`BankStatementReviewService`, previews mapped CSV through `BankCsvReviewService`, displays the exact
normalized rows and original CSV logical rows, and requires an audit actor plus explicit confirmation
before committing the captured file, company, account, and profile revision. A suffix-only account
match additionally requires its visible identity confirmation. Company, file, account, profile, or
profile-state drift invalidates approval and requires a new preview.

Banking displays durable company-scoped batch/row/issue counts and routes import and review actions to
Import Preview and Bank Transactions. Bank Transactions projects persisted `BankStatementLine` facts
through `BankReviewQueryService`; restart and company switching therefore never depend on an in-memory
staging list. Ledger drill-through is available only for an explicitly matched canonical transaction.
The File menu opens these same workspaces and does not invoke the former direct parser/staging path.
`UiWorkspaceDataStore.bankTransactions` is removed after its final production consumer is replaced.

## 18. P15-S7-C1 normalized CSV export boundary

`BankStatementCsvExportService` reconstructs normalized CSV exclusively from durable reviewed statement
facts for one explicitly selected active company-owned configured bank account and inclusive date range.
It never queries `Txn` or `TxnSplit` as statement activity. Batch, statement-line, and matched-transaction
references use intrinsic portable UUIDs; local numeric database identifiers never enter the file.

The serializer writes the frozen 29-column version 1.0 header, UTF-8 without BOM, LF record endings,
RFC 4180 quoting, ISO dates, exact plain decimals, and deterministic row order. Source/account metadata,
correction facts, durable review status, exact/probable duplicate classification, and matched transaction
identity are preserved where authoritative. Missing optional source PAYEEID or statement balances remain
empty and produce aggregate warnings rather than guessed values.

The export rejects an empty date range, invalid or cross-company/inactive account scope, an unconfirmed
overwrite, database-file targets, directories, and symlink paths. Final bytes are forced to a temporary
file in the destination directory and committed by atomic move where supported, with a recoverable
same-directory replacement fallback. The result reports the exact company/account/date scope, row and
byte counts, warnings, and SHA-256. Normalized CSV import, OFX/QFX serialization, and JavaFX controls
remain later P15-S7 slices.

## 19. P15-S7-C2 normalized CSV direct re-import boundary

`NormalizedBankCsvParser` recognizes only the exact frozen 29-column version 1.0 header and applies the
same 64 MiB, 1,000,000-record, 128-column, 4 MiB logical-record, and 1 MiB field limits as mapped CSV.
It accepts UTF-8 without a BOM, uses RFC 4180 quoting, and validates every version, external identity,
source-batch grouping, account/currency value, ISO date, exact decimal, correction pair, review state,
duplicate state, and matched-transaction reference before H2 mutation. It does not require or infer a
mapping profile.

`NormalizedBankCsvReviewService` binds preview and commit to the exact file SHA-256, active company,
selected active configured account, and the same account-identity policy as OFX/QFX. The importer
restores every source batch rather than collapsing a date-range export into a synthetic batch. Migration
V70 retains the exact source batch/line external IDs and source PAYEEID; intrinsic UUID identities are
preserved directly, while governed non-UUID compatibility identities receive deterministic internal
UUIDs and retain their exact external text for re-export.

Commit restores statement metadata, row dates/amounts/text/corrections, durable review status,
exact/probable duplicate state, and only matched canonical transactions already present in the same
company. A missing or cross-company match is blocking and never creates a transaction. All batches,
lines, issues, and the one factual operation audit commit together; the exact same file/target is an
idempotent no-op and a late failure rolls the entire import back. Re-export uses retained identities and
PAYEEID so a normalized CSV export/import/export cycle is byte-stable for governed facts.

## 20. P15-S7-C3 OFX 2.x and governed QFX export boundary

`BankStatementOfxExportService` reuses the C1 durable selected-company/configured-account/date-range
snapshot and the shared atomic interchange writer. `OfxQfxStatementSerializer` emits either bare OFX
2.x XML with the governed processing instruction or a QFX 2.x ASCII header followed by the same UTF-8
XML statement body. Both contain one bank statement only and use deterministic ordering, transaction
UID, LF endings, selected range, account/currency metadata, exact amounts, names/memos, check/reference
fields, and supported correction pairs.

A retained source FITID remains authoritative when it is nonblank and unique within the output. When
OFX requires an identifier but the retained value is blank or duplicated, export derives a stable
`SCA-` identifier from the statement-line portable identity and reports
`BANK_OFX_FITID_DERIVED`; it does not change the durable source ID. Missing transaction type uses the
neutral OFX `OTHER` value with a warning, and a missing posted date uses the retained transaction date
with a warning.

Only the latest unambiguous imported ledger or available balance is emitted, with its retained
statement-end date as `DTASOF`. Missing or conflicting balances are omitted and disclosed. Output is
validated by the strict production parser and remains statement activity only; no `Txn` or `TxnSplit`
query or write participates in export.

## 21. P15-S7-C4 production export route

Bank Transactions is the sole production statement-export surface. It lists active configured bank
accounts for the active company, captures one inclusive date range, and exposes separately labeled
**Export Bank CSV…**, **Export OFX 2.x…**, and **Export QFX…** actions. Banking routes users to this
surface as **Review / Export Statements…**; no File-menu or generic Import/Export Jobs action creates a
parallel route.

`BankStatementExportCoordinator` captures the exact company, configured-account ID, dates, format,
normalized destination, and overwrite decision before starting work. An existing target requires
explicit confirmation. The selected C1 or C3 service then revalidates company/account authority and
performs deterministic serialization plus atomic replacement away from the JavaFX thread.

Completion reports the destination, row and byte counts, warnings, SHA-256, portable account identity,
and path-coded messages. Failure reports that no governed output was committed. The removed selected-row
compatibility path may not return: a table selection is not export authority, and neither the UI nor the
coordinator may reconstruct statement files from ad hoc `BankTransactionRecord` values.

## 22. P15-S8-C1 production normalized CSV import route

Import Preview exposes **Preview Normalized Bank CSV…** separately from **Preview Mapped Bank CSV…**.
The normalized route recognizes only the frozen 29-column normalized CSV 1.0 header and never asks for,
infers, or applies a mapping profile. It requires the active company and one active configured bank
account, then captures one `NormalizedBankCsvReviewService` instance plus the exact file/company/account
scope before preview runs away from the JavaFX thread.

The preview displays normalized statement rows, retained source-batch and row counts, account-match
state, duplicate/review facts, and path-coded messages without changing H2. Approval retains that exact
preview and service instance, requires the audit actor and any suffix-only account confirmation, and
names the source SHA-256, company, account, batch count, row count, no-ledger boundary, and complete
rollback boundary before background commit.

Commit remains owned by `NormalizedBankCsvReviewService`: it re-reads the file, revalidates the hash and
company/account authority, restores every governed durable batch/line/issue/review fact atomically, and
creates no `Txn` or `TxnSplit`. A company switch, source change, account change, blocking message, failed
commit, or cancelled confirmation clears or invalidates approval and requires a new preview.
