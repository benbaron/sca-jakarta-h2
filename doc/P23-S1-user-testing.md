# P23-S1 — Owner review and evidence collection

## Visible changes

Repository documentation adopts P23–P28, selects P23-S1, and defines acceptance and dependency records. The desktop application has no functional change in this slice. Later phases are not implemented or automatically activated.

## Review this slice

1. Check `doc/PLAN.md`: P23-S1 is selected; P21/P22 stay DONE. Later slices remain blocked until their recorded prerequisites are satisfied.
2. Review `doc/P23-P28-runbook-correction-program.md` against the unchanged archived plan. Confirm all 14 findings remain covered and conditional attachments/approvals are not silently adopted.
3. Review `doc/P23-S1-baseline-and-acceptance.md`: verify the G14 statement reflects your observed problem and no source-only finding is described as a passed desktop test.
4. Confirm the named workbook reference is the intended starting point. Identify a different required submission template or instructions if applicable; do not supply real financial data for public test fixtures.
5. Review D03/D04 policy conflicts with the responsible exchequer before the affected implementation slices. This does not hold up the independent G1 correction.
6. Accept S1 documentation only after the checks are satisfactory. Merge/required validation must be confirmed before the plan marks S1 DONE and selects S2.

## Capture G14 environment (pending owner evidence)

| Field | Value to record |
|---|---|
| Executable/build | Version, commit if available, and source of installation |
| Launch | Executable/JAR/Eclipse launch configuration and entry point |
| Session | Effective reserved role and company context; avoid posting credentials or sensitive company records |
| Display | OS, window dimensions, resolution, scaling and sidebar state |
| Saved layout | Whether Journal columns were moved/resized; compare ordinary saved layout with a disposable clean-company layout |
| Navigation | Screenshot or exact route to Activities and Event Accounting, or where expected route is absent |
| Journal | Screenshot or exact location where Fund and Activity selectors cannot be found/used; describe selection and save behavior |
| Reproduction | Steps, expected result, actual result, any message; distinguish absent control, clipped control, disabled permission, empty choice list and lost assignment |

Use a disposable database to attempt creating an event by name, tagging a receipt/expense with Fund and Event, reopening it, and retrieving event results. Record the first failure rather than altering production records to work around it. Existing source labels are **Activities** and **Activity**; the adopted correction aims to make event usage explicit. No assumption is made that your installed build has these controls available.

## Developer validation

Check all changed Markdown links, adopted-slice and G1–G14/A01–A14 coverage, and unchanged archive; run `git diff --check`. `mvn clean verify` was attempted in the implementation environment but Maven is absent. Obtain exact-head CI verification after authorized publication. Do not claim a desktop pass from source-route tests or from this checklist.
