# Governed P15 data-exchange fixtures

These fixtures freeze the P15-S0 compatibility and security boundary. All organizations, people, account numbers, bank identifiers, transactions, and contact details are fictional. `example.invalid` addresses are deliberately non-routable.

## Fixture families

- `sclx/valid`: readable SCLX 1.0, 1.2, and 1.3 examples.
- `sclx/invalid`: malformed, unsupported-version, duplicate-identity, missing-identity, and missing-reference examples.
- `coa-json/valid/donor-generated.json`: actual output from donor commit `c697630ec1f784ebe8338d7300da6c9ac801b180` using `ChartOfAccountsIOService`.
- `coa-json/valid/sca-coa-1.0.json`: intended deterministic application output.
- `coa-json/invalid`: malformed, unsupported-version, duplicate-code, missing-parent, cycle, and monetary-overflow examples.
- `bank-statement/ofx`: OFX 2.x XML plus malformed, identity, duplicate, message-set, multi-account, and XML-security cases.
- `bank-statement/qfx`: governed XML-body and SGML-body QFX variants plus unsupported header/security cases.
- `bank-statement/csv`: signed-amount input, debit/credit input, normalized round-trip output, and malformed mapping cases.
- `database-transfer/invalid`: corrupt and path-traversal archive cases.
- `limits`: compact generator descriptors for exact accepted-boundary and first-rejected values. Tests synthesize bounded in-memory payloads from these descriptors instead of committing hundreds of MiB or a million rows.

The donor COA fixture was generated in GitHub Actions by compiling donor commit `c697630ec1f784ebe8338d7300da6c9ac801b180`, constructing three fictional accounts, and invoking the donor's unmodified `ChartOfAccountsIOService.exportToJson(...)`. Its artifact was then copied byte-for-byte into this directory.

`manifest.sha256` contains SHA-256 for every governed fixture except itself. Any intentional fixture change must update its contract, focused tests, and manifest in the same commit.
