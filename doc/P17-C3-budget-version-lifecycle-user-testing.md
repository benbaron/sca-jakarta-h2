# P17-C3 — Budget version lifecycle user testing

## User-visible changes

- Budget Editor now lists retained `DRAFT`, `ACTIVE`, and `ARCHIVED` versions for the selected fiscal year.
- **Archive Draft** retires an abandoned draft without physically deleting the plan or its budget lines.
- Archived versions remain selectable as read-only history.
- Active versions cannot be manually archived; activating a replacement draft continues to archive the prior active version atomically.
- The workspace explains the retained-history lifecycle and does not expose a placeholder Delete operation.

## Owner acceptance checklist

Use a disposable/test database or a copy of production data.

- [ ] Open **Budget Editor** for a fiscal year with an active version and create **New Draft**.
- [ ] Enter at least one nonzero category amount and choose **Save Draft Amount**. Note the draft version code and amount.
- [ ] Select the draft and choose **Archive Draft**. Cancel once and confirm the draft remains unchanged.
- [ ] Choose **Archive Draft** again and confirm the action. Verify the same version remains in the selector as `ARCHIVED`, its saved category amount is still visible, and the amount editor/save/activate/archive controls are disabled for that archived version.
- [ ] Refresh Budget, switch away and back, and confirm the archived version and its lines still exist.
- [ ] Select the current `ACTIVE` version and confirm **Archive Draft** is disabled.
- [ ] Choose **Create Revision** from the active version, modify and save the new draft, then **Activate Version**. Confirm the former active version becomes `ARCHIVED` and the replacement becomes `ACTIVE`.
- [ ] Open **Budget vs Actual** and confirm it uses the newly active version; archived history must not become the variance authority.
- [ ] Confirm there is no **Delete Selected** or generic Delete button in Budget Editor and the visible lifecycle text explains retained history.
- [ ] At laptop width, confirm the selector, lifecycle text, table/editor divider, and horizontal/vertical table scrolling remain usable.

## Acceptance record

Record any failed step with the fiscal year, version code, visible status/message, and whether Refresh Budget or reopening the company changes the result. Do not merge P17-C3 until GitHub Actions and this checklist are accepted.
