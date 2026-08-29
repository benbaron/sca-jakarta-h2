# P19-S3 — Company EIN informational metadata owner verification

## User-visible change

Company Admin now includes an optional **EIN** field as ordinary company profile metadata. It is informational only; there is no tax-filing workflow.

## Manual checks

Use a disposable database and, when possible, two companies.

1. Open **Administration → Company Admin** and confirm **EIN** appears in the Company profile form.
2. Enter an EIN such as `12-3456789`, save, switch to another company and back, and confirm the value is retained.
3. Close and reopen the application/database and confirm the EIN remains attached to the same company.
4. Edit the company code and save. Confirm the company retains the same EIN and stable company record rather than creating a second EIN record.
5. Clear the EIN, save, reload, and confirm it remains blank/absent.
6. Enter more than 40 characters and confirm Save is rejected with a clear maximum-length message and no partial write.
7. Confirm the application does not require an IRS-specific punctuation pattern; EIN is informational text rather than validated filing data.
8. Confirm no tax jurisdiction, filing name/address, filing period, return, tax status, or tax submission controls appear.
9. With two companies, set different EIN values and confirm switching companies never leaks one company's EIN into the other.
10. Open Report Library and export an existing report. Confirm P19-S3 has not inserted EIN into report headings/exports or otherwise changed existing report behavior.
11. Confirm normal accounting, Banking, Chart of Accounts assignment, and Reporting defaults behavior remains unchanged.
12. At laptop width and a narrower window, confirm the EIN control remains reachable in the existing Company Admin editor scroll and the form does not clip required controls.

## Legacy upgrade check

If you have a disposable pre-P19-S3 database containing a legacy `company_tax_profile.ein`, open it with this build and confirm that value appears in Company Admin after Flyway migration. Do not modify or delete the original database solely for this test.

## Acceptance boundary

Do not accept P19-S3 if EIN behaves as tax-filing configuration, introduces filing/jurisdiction workflow, changes accounting/report output, becomes company-crossing state, or loses a legacy EIN during migration.
