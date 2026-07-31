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
| 2 | `fix/folder-shape-geometry` | Folder background shape approximated to 4 hardcoded shapes instead of the exact configured shape; preview icons overflow folder bounds for some shape/count combos | S | Related #6495 (closed, 16-dev - verify repro on 15-dev before citing) | pending |
| 3 | `feat/drawer-search-folder-label` | Search results show which folder (with icon, bold name, parent path for nested) an app is in | S/M | - | pending |
| 4 | `feat/folder-outline` | Thin gray outline on folder previews/backgrounds, independent of theme/opacity | S | - | pending |
| 5 | `feat/battery-optimization-prompt` | Persistent (not one-shot) prompt to exempt the launcher from battery optimization | S | - | pending |
| 6 | `feat/hide-launcher-self-entry` | Hide the launcher's own app-drawer entry by default | S | - | pending |
| 7 | `feat/folder-picker-search` | Search bar when choosing apps for a folder | S | - | pending |
| 8 | `fix/home-lock-uninstall-widget-bypass` | lockHomeScreen didn't block the Uninstall shortcut (any surface) or new-widget placement/resize | S | - | pending |
| 9 | `feat/split-drawer-home-lock` | Split the single lockHomeScreen toggle into independent app-drawer-lock and home-screen-lock | S/M | Fixes #5839 | pending |
| 10 | `feat/folder-manual-order` | Optional manual drag-and-drop ordering of folders/folder contents (alphabetical stays default) | S/M | - | pending, after nested folders lands upstream |
| 11 | `feat/settings-pin-lock` | New PIN/biometric lock gating launcher settings and any exit into system Settings (doesn't cover shortcuts/widgets from other apps) | M/L | - | pending, design doc first |
| 12 | `feat/nested-folders` | One level of folder-in-folder nesting: data model (`parentFolderId`), UI, export/import, drag handles | L | - | pending, RFC first |

## Explicitly excluded - never propose upstream

Branding (BlownChart icon/name/applicationId: `373d155`, `c49f425`,
`64cf641` icon-provider hunk, `768679b`, `680a132`, `814b3d9`, and the
`ba50533` rebrand merge), and dropping CrowdIn-managed translations in
favor of hand-maintained Russian (`4a77931`, `1ed9e17`) - upstream still
uses Crowdin, this is a fork-specific tooling choice, not a fix. Also
everything from Tasks 1/2/4 of this session (licensing, CI/release
workflows, donations).

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
