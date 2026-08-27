# P17-C6 — Fund hierarchy lifecycle user testing

Use a disposable/test database or a copy of production data.

- [ ] Open **Funds** and confirm the editor visibly explains child-first deactivation/reparenting and parent-first reactivation.
- [ ] Create an active parent Fund and an active child Fund beneath it. Confirm both rows retain stable IDs after Refresh.
- [ ] Clear **Active** on the parent while the child is still active and Save. Confirm the save is rejected and Refresh shows both Funds still active.
- [ ] Deactivate the child first, then deactivate the parent. Confirm both same Fund rows remain listed as inactive and no historical reference is deleted.
- [ ] While the parent is inactive, try to reactivate the child. Confirm the save is rejected. Reactivate the parent first, then the child, and confirm both original IDs are reused.
- [ ] Create an inactive parent and an inactive child beneath it; confirm this retained-history hierarchy is allowed. Then attempt to make the child active while the parent remains inactive and confirm rejection.
- [ ] With another active Fund, attempt to reparent it beneath an inactive parent. Confirm the save is rejected and Refresh shows the original parent/state intact.
- [ ] Confirm **Delete Unused** still physically deletes a genuinely unreferenced Fund after confirmation, but a Fund referenced by a child or accounting/history record cannot be deleted and instead receives the deactivation explanation.
- [ ] If an SCLX test file is available, preview an active child whose parent is inactive and confirm preview blocks it rather than allowing commit. A valid active hierarchy should continue through normal preview/mapping behavior.
- [ ] At laptop width, confirm Fund table/editor scrolling, divider state, company-formatted dates, parent selector, and lifecycle guidance remain usable.

Record failures with Fund IDs/codes, parent IDs, Active state before/after, visible message, and whether Refresh changes the result. Do not merge P17-C6 until final-head GitHub Actions and this checklist are accepted.
