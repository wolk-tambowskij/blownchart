# Approved upstream contribution plan

Inventory and S/M/L classification approved by the project owner on
2026-07-31, after several rounds of correction against the actual code
and the app's own `about_change_*` changelog strings. This is the
tracking document for Task 3 (`docs/pr/<branch>.md` holds each branch's
actual PR description; this file is the plan/status overview).

Fork point: `upstream/15-dev` @ `v15.0.0-beta3.0`. See
`docs/UPSTREAM_CONTRIB_NOTES.md` for upstream's contribution rules.

## Order (easiest/most-verified first)

| # | Branch | Topic | Class | Issue | Status |
|---|---|---|---|---|---|
| 1 | `fix/folder-list-performance` | O(apps×folders) folder-list loading and per-checkbox editing lag | S | Fixes #6147 | ready for review (pushed to `origin/fix/folder-list-performance`) |
| 2 | `fix/folder-shape-geometry` | Folder background shape approximated to 4 hardcoded shapes instead of the exact configured shape; Cookie/Arch shapes rendered as a circle; preview icons overflow folder bounds for some shape/count combos | S | Related #6495 (closed, 16-dev - verify repro there before citing) | ready for review (pushed to `origin/fix/folder-shape-geometry`) |
| 3 | `feat/search-folder-label` | Search results show which folder an app is in (as a subtitle) - narrower than originally planned, see PR doc scope note | S | - | ready for review (pushed to `origin/feat/search-folder-label` - renamed from the originally planned `feat/drawer-search-folder-label`, which collides with an old stale fork branch of the same name, see cleanup list) |
| 4 | `feat/folder-outline` | Thin gray outline on folder previews/backgrounds, independent of theme/opacity | S | - | ready for review (pushed to `origin/feat/folder-outline`) |
| 5 | `feat/battery-optimization-banner` | Persistent (not one-shot) prompt to exempt the launcher from battery optimization | S | - | ready for review (pushed to `origin/feat/battery-optimization-banner` - renamed from the originally planned `feat/battery-optimization-prompt`, which collides with an old stale fork branch of the same name, see cleanup list) |
| 6 | `feat/hide-launcher-self-entry` | Hide the launcher's own app-drawer entry by default | S | - | ready for review (pushed to `origin/feat/hide-launcher-self-entry`) |
| 7 | `feat/folder-picker-search` | Search bar when choosing apps for a folder | S | - | ready for review (pushed to `origin/feat/folder-picker-search`) |
| 8 | `fix/home-lock-uninstall-widget-bypass` | lockHomeScreen didn't block the Uninstall shortcut (any surface) or new-widget placement/resize | S | - | ready for review (pushed to `origin/fix/home-lock-uninstall-widget-bypass`) |
| 9 | `feat/split-drawer-home-lock` | Split the single lockHomeScreen toggle into independent app-drawer-lock and home-screen-lock | S/M | Fixes #5839 | ready for review (pushed to `origin/feat/split-drawer-home-lock` - **branched from #8, contains its commit too**, since UNINSTALL only has a lockHomeScreen check to split once #8 lands) |
| 10 | `feat/nested-folders` (chain, 3 PRs) | One level of folder-in-folder nesting: 1) data model (`parentFolderId`), 2) UI, 3) export/import + drag handles | L | - | design doc approved 2026-07-31 (see `docs/pr/nested-folders-PROPOSAL.md`); chain PR 1/3 ready for review (`origin/feat/nested-folders-data-model`, `docs/pr/feat-nested-folders-data-model.md`); chain PR 2/3 ready for review (`origin/feat/nested-folders-ui`, **branched from PR 1, contains its commit too**, `docs/pr/feat-nested-folders-ui.md` - scope note: doesn't yet render a nested folder as its own icon inside its parent's open view, see the PR doc); PR 3 (export/import) not yet started |
| 11 | `feat/folder-manual-order` | Optional manual drag-and-drop ordering of folders/folder contents (alphabetical stays default) | S/M | - | pending, after #10's data-model PR lands upstream (the fork's manual-order code is entangled with nesting - folders always sort before apps in both) |
| 12 | `feat/settings-pin-lock` | New PIN/biometric lock gating launcher settings and any exit into system Settings (doesn't cover shortcuts/widgets from other apps) | M/L | - | design doc approved 2026-07-31 (see `docs/pr/settings-pin-lock-PROPOSAL.md`); branch not started yet - fully independent of #10/#11, can be built whenever |

**Reordering note (2026-07-31):** the original plan had PIN-lock (was #11)
before nested folders (was #12). Swapped: `feat/folder-manual-order`
(now #11) is code-dependent on nested folders (now #10), so #10 has to
be tackled first regardless; PIN-lock has no dependency either way, so
there's no cost to moving it last, and doing so groups the two
folder-family items together instead of interleaving an unrelated
security feature between them.

## Explicitly excluded - never propose upstream

Branding (BlownChart icon/name/applicationId: `373d155`, `c49f425`,
`64cf641` icon-provider hunk, `768679b`, `680a132`, `814b3d9`, and the
`ba50533` rebrand merge), and dropping CrowdIn-managed translations in
favor of hand-maintained Russian (`4a77931`, `1ed9e17`) - upstream still
uses Crowdin, this is a fork-specific tooling choice, not a fix. Also
everything from Tasks 1/2/4 of this session (licensing, CI/release
workflows, donations).

## Open follow-up spotted while working branch #6

While branch-switching back to `15-dev`, `PreferenceManager2.kt` there calls
`L3IconShape.INSTANCE.get(context).pickBestShape(context)` (forces the
cached shape-detection singleton to re-run) with a comment about folder
icons keeping a stale shape until process restart otherwise - `.get(context)`
alone only returns the already-cached instance from first launch. This
looks like a second, separate bug from what `fix/folder-shape-geometry`
(#2) already fixed, in the same problem area (icon shape not
re-detected when the underlying preference/mask changes) but a
different code path (the singleton isn't refreshed at all, vs. #2's
wrong-shape-picked-in-the-first-place bugs). Not yet turned into its
own branch - flagging here so it isn't lost.

## Commits that needed hunk-level splitting (not clean cherry-picks)

- `1be078e` - `IconShape.java` hunk → #2; rest → #12.
- `bb8a40a` - `IconShape.kt`/`IconShapeManager.kt`/`ClippedFolderIconLayoutRule.java`/`PreviewItemManager.java`/`IconShape.java` hunks → #2; `SearchResultIconRow.kt`/`SearchTargetFactory.kt`/`About.kt` string hunk → #3; `FolderDao.kt`/`FolderService.kt`/`AppDrawerFoldersPreference.kt` hunks (nested-folder drag handle) → #12.
- `8f8cc12` - `ClippedFolderIconLayoutRule.java` hunk → #2; `FolderDao.kt`/`FolderService.kt`/`PreferenceManager2.kt` hunks → #12.
- `64cf641` - `SearchResultIconRow.kt` hunk → #3; `LawnchairIconProvider.kt` + icon PNGs → excluded (branding).

Since our fork's own commits were built on top of each other (e.g. the
performance and shape fixes assume nested-folders/PIN-lock files that
don't exist upstream yet), branches are **not** built by cherry-picking
fork commits verbatim - each fix is re-implemented directly against
upstream's actual current file content, using the fork commit's diff as
reference.
