# P16-S16 User role and assignment maintenance — owner testing

## Preconditions

- Use a disposable migrated database with two active companies.
- Keep one user and one non-default role available for testing.
- Open Administration -> User Admin at desktop and laptop width.

## Users and roles

1. On Users, create a user, edit its username, and refresh. Confirm one row with the same stable identity remains and the edited values reload.
2. On Roles, create a role, edit its code/name/description, and refresh. Confirm one role row remains and Active is visible.
3. Assign the role to the user. Attempt to deactivate either the user or role and confirm the service explains that active assignments must first be ended or revoked.
4. End the assignment, then deactivate and reactivate the user and role. Confirm historical assignment rows remain visible throughout.
5. Confirm there is no Delete User, Delete Role, or Delete Assignment control.

## Assignment history and company scope

1. On Company Assignments, confirm the displayed company is the active production company and there is no arbitrary company selector.
2. Create an assignment with a start date. Attempt the same or an overlapping assignment and confirm it is rejected without another row.
3. End the assignment with an end date and reason. Reassign the same role starting after that end date and confirm a second history row is created rather than reactivating the first.
4. Revoke the second assignment. Confirm a reason is required and the row remains visibly Revoked.
5. Switch to the second company. Confirm only that company's assignment history appears; create one assignment, switch back, and confirm the first company's rows return unchanged.

## Audit, commands, and deferral wording

1. Open Audit History and confirm user, role, assignment, end, and revoke events appear with the entered factual actor, before/after facts, and reason where supplied.
2. Select Users, Roles, and Company Assignments in turn. Confirm global New and Save act on the selected inner tab.
3. Select Authentication. Confirm global New/Save disable and the panel states that authentication and runtime permission enforcement are deferred.
4. At laptop width, move each horizontal divider and confirm tables and editors remain independently scrollable. Reopen the company and confirm divider/table state restores.

## Acceptance

- [ ] Stable-ID user and role edits retain one row and reload after restart.
- [ ] Active references protect user/role deactivation and no hard-delete control exists.
- [ ] Assignment end/revoke retains non-overlapping dated history.
- [ ] Company switching isolates assignment history exactly.
- [ ] Every material change is visible in factual Audit History.
- [ ] UI wording never claims login, authentication, or permission enforcement.

Owner result: PENDING

Notes:

-
