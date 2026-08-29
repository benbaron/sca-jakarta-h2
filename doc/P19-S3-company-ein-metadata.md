# P19-S3 — Company EIN informational metadata

## Purpose

P19-S3 records the Employer Identification Number (EIN) as optional informational metadata on the company profile. The application does not provide tax filing, tax return preparation, jurisdiction tracking, filing periods, filing addresses, or filing-status workflows.

The owner decision for this slice is explicit: **EIN is informational company data, not tax-filing configuration.**

## Authority

`company.id` remains stable company identity and the `company` row is the production authority for the EIN. P19-S3 adds nullable `company.ein VARCHAR(40)` and exposes it through the existing `Company`, `CompanyCommand`, `CompanyView`, and `CompanyAdminService` profile path.

EIN values are:

- optional;
- trimmed on save;
- stored as `NULL` when blank;
- limited to 40 characters to match the legacy storage capacity; and
- otherwise treated as informational text.

The application deliberately does not impose an IRS-format validator. This avoids claiming filing validity for identifiers that may be entered for informational/reference purposes.

## Legacy `company_tax_profile`

Migration V46 created a `company_tax_profile` side table with EIN plus unused tax-filing-oriented fields. Current production code had no save workflow or report/export consumer for those filing fields.

V75 therefore:

1. adds `company.ein` nondestructively;
2. copies a nonblank legacy `company_tax_profile.ein` into the matching company when the company EIN is blank; and
3. leaves the legacy table and all of its historical columns/data physically intact.

After migration, production Java/JPA no longer maps `CompanyTaxProfile` or queries that table as a live application authority. The entity and `CompanyAdminService.taxProfile(...)` compatibility dead-end are retired. The retained table is historical migration residue only and must not become a second writable EIN source.

## Company Admin behavior

Administration → Company Admin exposes **EIN** in the existing Company profile form. It participates in the same dirty/save/discard and stable-ID edit lifecycle as legal name, branch type, parent organization, fiscal start, and currency.

Changing the company code does not move or recreate EIN data because EIN is stored on the same stable company row.

The UI states that EIN is informational metadata and that no tax-filing workflow is provided. No jurisdiction, filing-name, filing-address, filing-period, return, tax-status, or tax-submission controls are introduced.

## Reporting and accounting boundary

P19-S3 does not alter:

- report headings or report execution;
- PDF/CSV/XLSX/text export content;
- SCLX or Chart of Accounts interchange;
- ledger/accounting data;
- banking or reconciliation;
- company reporting defaults; or
- authentication/authorization.

A future use of EIN in a report or interchange format would require its own explicit consumer requirement and slice. Merely storing EIN does not imply such a consumer.

## Validation

Automated coverage must prove:

- V75 upgrades a V74 database and preserves/backfills legacy EIN without dropping the legacy table;
- a freshly migrated company may have no EIN;
- Company Admin service round-trips EIN by stable company ID across restart and code rename;
- values longer than 40 characters are rejected while no tax-specific format rule is invented;
- Company Admin exposes the actual EIN profile field and no longer advertises deferred tax-filing administration;
- live Java/JPA no longer references `CompanyTaxProfile`; and
- full repository Maven PR Tests pass, including clean verification, repeat tests, and production JavaFX route compliance.

Owner verification is recorded in `doc/P19-S3-company-ein-metadata-user-testing.md`.
