# P28-C1 — User manual first working draft

## Owner scope

Create a task-based manual for a volunteer bookkeeper, including company setup and management, database creation/opening/backup/restore, file import/export and reports/reporting. The owner selected Word as the reader/review format. Keep the editable source in [user-manual.md](user-manual.md) and provide a corresponding [Word copy](user-manual.docx). This is a first working draft for review, not a claim of an approved external SCA procedure.

Describe current merged behavior, including the compact Banking selector, Statement setup / Match entries and inline manual statement entry. Explain terminology, permissions, prerequisites, expected results, correction paths and common errors. Keep company/database/chart/account identities distinct. Describe actual effects: previews, reference configuration, financial saves, clearing, ownership repair and review attestations are different operations.

Core requested chapters must contain usable procedures. Include concise introductions to existing budget, Event, transfer, supplemental, asset, inventory, custody and settlement functions; later review can expand their worked examples. Do not invent missing buttons, universal CSV schemas, automatic policy calculations, official reporting forms or installation procedures that have not been supplied.

## Source and format

Use current main 45cba4ae (C14/#437 merged), current operating/panel guides, UI labels and service guards as evidence. Keep the Markdown manual as the maintained source; Word is the review copy generated from it. Apply accepted Word review comments back to the source before regenerating. The code donor is not needed for documentation of existing merged workflows.

Illustrations under manual/images come from the actual JavaFX tests against synthetic Example Bank data. No uploaded financial file, real company screenshot, credential or user database belongs in this publication. Retain the original image content and describe it as an illustration, not the reader's own account state.

## Generation and verification

The Word build uses Pandoc and a temporary python-docx reference document for readable page, paragraph, table and heading styles. These are documentation tools, not new Java application dependencies. Run python3 scripts/build-user-manual.py from the repository; --output may select a different review-copy path. The build requires Python 3 with python-docx and Pandoc. It starts from Pandoc's native reference document, preserving required list/table/hyperlink styles, and applies the project's readable Word layout. Application users need only Word or another compatible reader, not those build tools. Source headings provide navigation; Contents links must resolve inside Word. The review copy must be readable without the source tree.

Check all local source links and image references. Inspect the Word ZIP/XML for expected chapters, valid internal links, embedded illustrations and absence of macros. Render the Word copy to PDF for pagination/visual inspection using a disposable LibreOffice profile; the PDF is QA evidence unless the owner requests it. Check cover/contents, company procedures, comparison tables, Banking illustrations and reports pages. Run existing ReleaseOperatingGuideTest, final clean verification and exact-head CI; do not create implementation-mirroring tests for prose. No new desktop application validation is claimed for this documentation-only slice.

## Owner review

Open the Word document and use Word comments for corrections. Review the company setup checklist, backup/restore procedure, format-selection table, bank reconciliation and report/export steps first. Confirm organization-specific fiscal dates, account naming, retention, approvals and reporting submissions separately; this application does not supply those external policies. Subsequent manual slices can add worked examples and validated screenshots for additional workflows without changing accounting behavior.

## Progress

Ready-for-review [PR #438](https://github.com/benbaron/sca-jakarta-h2/pull/438), source 3c5591670828177f479fb1c9d9da28451fd0f6d0 on codex/P28-C1-user-manual, base main 45cba4ae. This final receipt remains local to preserve the accepted remote source/check head; PR description contains the final results.

First working draft: approximately 9,100 words, 18 task-based chapters, nine comparison/reference tables and two synthetic native Banking illustrations. Word ZIP/content/bookmark/media checks pass: 18 resolved internal Contents links, correct tables/images, no duplicate header properties or macros. All local links in changed manual/guide files resolve. Final corrected LibreOffice review render has 31 pages; selected cover/Contents, database/company procedures, export/report tables and Banking illustration pages were visually inspected. Build uses Pandoc's native reference styles, avoiding the first template's formatting defects. This is document QA, not a claim that the owner has approved or walked through every procedure.

Baseline and changed-guide checks each passed two tests (26.9s / 36.5s). Final local clean verify passed **1,294 tests, zero failures/errors, 160 expected headless display skips (11:24)**. [Exact-head CI 44965994422](https://github.com/benbaron/sca-jakarta-h2/actions/runs/44965994422) passed Linux clean/repeat/rendering/Period Close/overflow/import/Journal feedback/production-route checks and all 23 Windows display tests across seven suites without skips. No final gate failed. Evidence is /workspace/p28-c1-evidence/.

Next action: owner Word/content review, especially company setup/management, database backup/restore, file-format choice and reporting. Apply accepted comments to Markdown and regenerate the review copy. Keep VERIFYING until owner merge; scope subsequent worked examples from review. Do not merge automatically.
