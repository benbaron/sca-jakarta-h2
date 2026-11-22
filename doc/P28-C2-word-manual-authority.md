# P28-C2 — Word as the authoritative user manual

## Owner scope and editing contract

The owner explicitly selected Word as the primary source. This supersedes the
Markdown-source/Word-review-copy workflow introduced in P28-C1/#438, now merged
at ef1006f7495b069aa64a5b28d7c01f8ad5358433.

[user-manual.docx](user-manual.docx) is the authoritative, editable manual. Make
corrections directly in Word; use comments and tracked changes for review as
needed. Apply accepted corrections to that document. Preserve review comments,
tracked revisions, formatting, internal links, tables and embedded images;
do not silently accept/reject unrelated changes or delete comments.

[user-manual.md](user-manual.md) is a short landing page, not a second maintained
manual. The former Markdown-to-Word generator is removed. Never reconstruct the
manual from Markdown or extracted text. Git versions the Word document; retain
the latest reviewed file when incorporating owner comments. Engineering Markdown
specifications remain the authority for their respective technical contracts.

## Implementation and verification

Branch: codex/P28-C2-word-primary, fresh from current main after #438 merged;
prior local final receipt preserved as 2421f514. No application, schema,
accounting, screenshot content or private financial data changes.

Add the authority notice near the Word cover and correct its final review
instruction. Modify only word/document.xml inside the existing package; preserve
all other package parts byte-for-byte. Keep existing content, navigation and
illustrations. Replace the Markdown manual with its landing page and update
AGENTS, PLAN and active guide/contract references.

Baseline ReleaseOperatingGuideTest passes two tests, zero failures/errors/skips
(39.6s). Verify package-part preservation, chapter/table/image/bookmark counts,
local links and rendered cover/final page; run the existing guide check, final
clean verification and exact-head Linux/Windows CI. No prose-mirroring tests or
new application dependencies.

## User review and handoff

Open the Word manual, edit it directly and add comments or tracked changes there.
Future manual revisions must preserve those edits and review annotations. The
Markdown page should only direct readers to Word. Review the authority notice
and final review instructions. Keep VERIFYING until owner merge; do not merge
automatically. Final validation/publication receipt follows when available.

Document checks pass: all package parts except word/document.xml are byte-identical;
all other original paragraphs remain intact. Nine tables, two images, 18 resolved
Contents links and 80 bookmarks are preserved, with unchanged annotation counts.
The current source has no comments or tracked changes; preservation checks do not
claim a new editor/import feature. Local links and diff whitespace pass. LibreOffice
render remains 31 pages; cover/final page visually inspected. Changed-guide check
passes two tests, zero failures/errors/skips (29.6s). Evidence is
/workspace/p28-c2-evidence/. Final clean verification and exact-head CI pending.

Published [draft PR #439](https://github.com/benbaron/sca-jakarta-h2/pull/439),
implementation e33827c6a97993194fb6440f241d20d065423273, tree
2e67bafda860d69ddaa8f8d91bf01255eb7e326e. Normal new-branch push and PR
read-back match. This publication receipt changes documentation only; final
clean verification is running and exact-final-head CI remains pending.
