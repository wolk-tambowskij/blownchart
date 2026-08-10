# Approved upstream contribution plan

Inventory and S/M/L classification approved by the project owner on
2026-07-31, after several rounds of correction against the actual code
and the app's own `about_change_*` changelog strings. This is the
tracking document for Task 3 (`docs/pr/<branch>.md` holds each branch's
actual PR description; this file is the plan/status overview).

Fork point: `upstream/15-dev` @ `v15.0.0-beta3.0`. See
`docs/UPSTREAM_CONTRIB_NOTES.md` for upstream's contribution rules.

## 2026-08-01 nuance sweep (branches #1-#9)

Same standard applied to nested folders (#10) after the "build #96
already has this, fully" correction: every branch's diff was checked
against the actual real-fork commit(s) it's based on (via `git show` on
`15-dev`'s history), not just re-read on its own. Real gaps found and
fixed: #1 was missing two of the fork's own three `fix/6147-...` commits
(cross-emission app-lookup caching, app-picker flicker fix) plus an
unrelated pre-existing `Map<String?, AppInfo>` compile error in the same
function; #2 was missing the folder-background shape re-detection call
(the fork's `pickBestShape()` fix, previously flagged as an unassigned
follow-up under branch #6's notes - it's actually part of #2); #3 only
wired the folder-label subtitle into the non-default `LawnchairAppSearchAlgorithm`,
never reaching the actual shipped default (`LawnchairLocalSearchAlgorithm`
via `AppsAndShortcutsSectionBuilder`) - refactored to match the fork's
real architecture (lookup lives in `FolderService`, resolved internally
by `SearchTargetFactory`); #5 was missing the `autoRevokePermissions`
manifest flag, the non-UI half of the fork's real redesign. Branches #4,
#6, #7, #8, #9 were checked and found to already fully match - no
changes needed. All fixes pushed and re-verified via real CI
(`workflow_dispatch` on each branch).

**Fallout (resolved):** #3's refactor removed the per-algorithm
folder-title snapshot that `feat/nested-folders-ui` (#10) was built on
top of and extends. Rebased #10's 4 own commits (data model, UI, and
both CI fixes) onto #3's new tip - the search-integration conflict was
resolved by extending the same centralized `FolderService`/
`SearchTargetFactory` architecture with nested-folder awareness
(`FolderPath(title, parentTitle)`, recursing one level into an embedded
subfolder's own contents) instead of reintroducing a parallel
per-algorithm snapshot. Force-pushed to `origin/feat/nested-folders-ui`
(no PR open yet, so safe) and re-verified via CI.

## 2026-08-01 upstream issue tracker check (Task 3 item #19)

Checked `LawnchairLauncher/lawnchair`'s issue tracker (via GitHub's
public search API, `api.github.com/search/issues`) for existing reports
matching each S-class branch that didn't already have an issue number:

- **#8** (`fix/home-lock-uninstall-widget-bypass`) - found
  [#6929](https://github.com/LawnchairLauncher/lawnchair/issues/6929),
  which explicitly describes this exact bypass ("even when 'Lock Home
  Screen' is enabled, [Uninstall] remains visible"). Worth citing as
  `Related #6929` in the PR description, though the issue itself asks
  for a toggle to hide Uninstall entirely rather than gating it behind
  the lock - our fix is a subset/prerequisite of what it's asking for,
  not a full close.
- **#1** (`fix/folder-list-performance`) - confirmed **not** redundant:
  upstream already merged
  [#6996](https://github.com/LawnchairLauncher/lawnchair/pull/6996), a
  similar folder-loading performance rewrite, but only into `16-dev`
  (merged 2026-07-13), not `15-dev` - our fix stands on its own for the
  branch we're actually targeting, and #6996 being accepted is a good
  precedent that this class of fix is welcome upstream.
- **#5** (`feat/battery-optimization-banner`) - only a loosely related
  hit, [#5422](https://github.com/LawnchairLauncher/lawnchair/issues/5422)
  ("Allow Lawnchair to run in background"), not a close match (that one's
  about RAM residency, not the battery-exemption prompt) - not worth
  citing.
- **#3, #4, #6, #7** - no matching open or closed issues found for
  search-folder-label, folder-outline, hide-launcher-self-entry, or
  folder-picker-search. These stay as pure unsolicited improvements with
  no issue to reference, same as before.

## 2026-08-10 full drift sweep (branches #1, #5, #6, #10, #11-13, plus 3 newly-tracked branches)

Per project-owner instruction that the one-function-one-branch /
correct-classification-by-final-capability / complete-multi-surface-description
/ verified-sync-with-current-15-dev methodology (already applied to #10 and
#14 earlier the same day) must be applied to **every** tracked function, not
just those two: diffed all `15-dev` commits since the Aug 1 sweep's own final
commit (`355ca22ca4`) against every existing branch and against the full
commit log, to catch both (a) incremental improvements made to an
already-tracked feature while working on something else nearby, and (b)
entirely new fork-only work that was never turned into a tracked row at all.

Real gaps found and fixed: #1 was missing a cold-start early-return + IO
dispatch added 2026-08-07; #5 was missing a dashboard warning-order fix added
2026-08-08; #6 was missing a real bug fix (self-hide seed flag stuck after a
cross-build restore) added 2026-08-07, and its PR doc was stale, still
describing the pre-fix boolean-flag mechanism; #10 was missing an entire
second nested-folder-creation mechanism (drag-to-merge) plus 5 rounds of
crash hardening, none of it reflected in the branch or its PR doc. #11, #12,
#13 checked and confirmed unchanged (see their own rows for what was
verified). Three previously-untracked branches added: `fix/loader-model-
thread-priority` (a PR doc existed from Aug 1 but never got a table row -
pure oversight), `feat/backup-lock-wallpaper`, `fix/backup-restore-
completeness`, `feat/folder-delete-confirm` (all four genuinely new work from
Aug 7-10 with zero prior tracking).

**Process note (for future sweeps):** two background agents doing this
consolidation work independently rebuilt `feat/backup-lock-wallpaper` and
`fix/backup-restore-completeness` from a contaminated first attempt (one
agent initially branched from the fork's own branded `15-dev` history
instead of the clean-room base commit, `app.blownchart`/`BlownChart`
throughout) - caught before merge by a `git grep` branding check against
each pushed branch, never reached this file or any PR doc. Rebuilt clean
from `505dbc40e6` and re-verified. Worth a standing habit: always grep a new
clean-room branch for `app\.blownchart\|BlownChart` before marking it ready
for review, not just at the end of a whole sweep.

## Order (easiest/most-verified first)

| # | Branch | Topic | Class | Issue | Status |
|---|---|---|---|---|---|
| 1 | `fix/folder-list-performance` | O(apps×folders) folder-list loading and per-checkbox editing lag, plus a cold-start skip when there are zero folders | S | Targets #6147 (**now closed** - was open when this row was last checked; closed via [#6996](https://github.com/LawnchairLauncher/lawnchair/pull/6996), a similar folder-loading perf rewrite, but that PR only landed on `16-dev`, per row 89's own note - reword the PR description to "targets the still-unfixed 15-dev, #6996 is prior art" rather than `Fixes #6147` as if the issue were still open) | ready for review (pushed to `origin/fix/folder-list-performance` - 2026-08-01 nuance sweep against the fork's real `fix/6147-drawer-folders-performance` history added 2 more fixes this PR had missed: caching the installed-app lookup across `getFoldersFlow()` emissions, not just within one, and excluding the folder being edited from `allFolderPackages` in the app picker to stop a just-toggled item flickering. **2026-08-10 drift check** (against 15-dev commits since the Aug 1 sweep) added one more gap: `getFoldersFlow()` now early-returns before the app-info sweep when there are zero folders defined, and moves the whole flow onto `Dispatchers.IO` - same problem area, a different (cold-start, not N+1) angle. Pushed, CI green) |
| 2 | `fix/folder-shape-geometry` | Folder background shape approximated to 4 hardcoded shapes instead of the exact configured shape; Cookie/Arch shapes rendered as a circle; preview icons overflow folder bounds for some shape/count combos | S | Related #6495 (closed, 16-dev - verify repro there before citing) | ready for review (pushed to `origin/fix/folder-shape-geometry` - 2026-08-01 nuance sweep added the missing `pickBestShape(context)` re-detection call for the *folder background's* own shape singleton, the same class of bug this PR already fixes for individual app icons) |
| 3 | `feat/search-folder-label` | Search results show which folder an app is in (as a subtitle) | S | - | ready for review (pushed to `origin/feat/search-folder-label` - renamed from the originally planned `feat/drawer-search-folder-label`, which collides with an old stale fork branch of the same name, see cleanup list). **2026-08-01 nuance sweep found and fixed a real gap**: this PR originally only wired the folder subtitle into `LawnchairAppSearchAlgorithm`, but the shipped default search engine is `LawnchairLocalSearchAlgorithm` (LOCAL_SEARCH) via `AppsAndShortcutsSectionBuilder` - most users would never have seen the feature. Refactored to match the fork's real architecture: the componentKey→folder-title lookup now lives in `FolderService` itself, and `SearchTargetFactory.createAppSearchTarget()` resolves it internally, so every caller benefits automatically; `AppsAndShortcutsSectionBuilder`'s multi-result case now renders as rows too. **Note:** `feat/nested-folders-ui` (#10) was branched from this PR's old tip and duplicates the now-removed per-algorithm snapshot pattern - needs a rebase onto the new tip before both are proposed together, not yet done. |
| 4 | `feat/folder-outline` | Thin gray outline on folder previews/backgrounds, independent of theme/opacity | S | - | ready for review (pushed to `origin/feat/folder-outline`) |
| 5 | `feat/battery-optimization-banner` | Persistent (not one-shot) prompt to exempt the launcher from battery optimization, ordered ahead of the default-launcher warning on the dashboard | S | - | ready for review (pushed to `origin/feat/battery-optimization-banner` - renamed from the originally planned `feat/battery-optimization-prompt`, which collides with an old stale fork branch of the same name, see cleanup list. 2026-08-01 nuance sweep added the missing `android:autoRevokePermissions="discouraged"` manifest flag, the other half of the fork's real redesign - only the banner UI half had made it into this branch. **2026-08-10 drift check** added one more gap: the dashboard's two warning blocks were reordered so the battery-optimization banner renders before the default-launcher warning, since the latter `finish()`es the activity on tap-through/return, masking the battery banner on devices needing both. Pushed, CI green) |
| 6 | `feat/hide-launcher-self-entry` | Hide the launcher's own app-drawer entry by default, surviving a cross-build backup/restore | S | - | ready for review (pushed to `origin/feat/hide-launcher-self-entry`; 2026-08-01 nuance sweep: verified against the fork's real commit, no gaps. **2026-08-10 drift check** found a real gap: the one-time seed-applied flag was a plain boolean, so a backup made on one build (e.g. debug vs release `applicationId`) restored onto a different one carried over "already applied" without the self-entry ever matching the now-installed launcher's own component - permanently leaving its drawer entry visible after a cross-build restore. Now keys the flag on the launcher's own component identity (a string preference) instead of a bare boolean, so a mismatch re-seeds correctly. Pushed, CI green (one spotless formatting round-trip). `docs/pr/feat-hide-launcher-self-entry.md` updated to match) |
| 7 | `feat/folder-picker-search` | Search bar when choosing apps for a folder | S | - | ready for review (pushed to `origin/feat/folder-picker-search`; 2026-08-01 nuance sweep: verified against the fork's real commits, no gaps) |
| 8 | `fix/home-lock-uninstall-widget-bypass` | lockHomeScreen didn't block the Uninstall shortcut (any surface) or new-widget placement/resize | S | Related #6929 | ready for review (pushed to `origin/fix/home-lock-uninstall-widget-bypass`; 2026-08-01 nuance sweep: verified against the fork's real commits, no gaps) |
| 9 | `feat/split-drawer-home-lock` | Split the single lockHomeScreen toggle into independent app-drawer-lock and home-screen-lock | S/M | Fixes #5839 | ready for review (pushed to `origin/feat/split-drawer-home-lock` - **branched from #8, contains its commit too**, since UNINSTALL only has a lockHomeScreen check to split once #8 lands; 2026-08-01 nuance sweep: verified against the fork's real commit, no gaps) |
| 10 | `feat/nested-folders-ui` (one branch/one feature - see note) | One level of folder-in-folder nesting: data model (`parentFolderId`) + full rendering (app drawer and home screen, both share `Folder`/`FolderPagedView`/`FolderIcon`) + badge + Settings management + search full path + two independent ways to create a nested folder (Settings picker, and drag-to-merge two icons inside an open folder) | L | - | design doc approved 2026-07-31 (see `docs/pr/nested-folders-PROPOSAL.md`); ready for review (`origin/feat/nested-folders-ui`, `docs/pr/feat-nested-folders.md`). **2026-08-10 consolidation**: previously tracked as a "2 PR chain" (`feat/nested-folders-data-model` + `feat/nested-folders-ui`) - per project-owner correction, one user-facing capability is one branch, not several; `feat/nested-folders-ui` already contained the data-model branch's commits as a strict superset, so it's now the sole branch for this feature (branch name itself is a legacy artifact of the old split - kept rather than renamed to `feat/nested-folders`, since that name collides with an old, stale, pre-clean-room branch already on the deletion list; rename once that's cleared). Still branched from `feat/search-folder-label` (#3) and contains its commits too - a real code dependency (the nested search subtitle extends #3's centralized lookup), not incidental bundling, same stacked-PR shape as #9 on #8. `feat/nested-folders-data-model` is now fully redundant - added to the deletion list. Export/import as a follow-up item not yet started (may not be needed - nesting no longer silently drops data without it, unlike the original flatten-based approach). **2026-08-10 drift check** found a substantial gap: the branch only had the Settings-based "Nest inside" picker, missing a second, independently-discovered creation path from the same week of fork development - dragging one icon onto another inside an already-open folder merges them into a new nested subfolder (`Folder.createNestedFolder()`/`getMergeTargetAtRank()`), plus 5 commits of drag/drop crash hardening found while building it (dragging a nested folder back out of a home-screen folder, collapse-to-last-item and cross-folder-drop crashes, a depth guard rejecting double-nesting, a home-screen freeze after merge-create, and an edit-mode-stuck freeze / stale-rank crash on drops from a different open folder). All ported in, and `docs/pr/feat-nested-folders.md` rewritten to cover both creation paths throughout (Description/Reasoning/Testing). Pushed, CI green |
| 15 | `fix/loader-model-thread-priority` | Home-screen/app-drawer/settings loading performance: reference-counted thread-priority elevation (was racing raw `setThreadPriority()` calls) + bulk icon loading in the app list instead of one query per app | M | Partial credit to Lawnchair 16's own upstream fix for the thread-priority half (Bug: 396250724) - this PR's bulk-icon-loading half is separate, unsolicited | ready for review (pushed to `origin/fix/loader-model-thread-priority`, `docs/pr/fix-loader-model-thread-priority.md` - both existed since 2026-08-01 but the doc never got a table row (pure oversight) and the branch name, when found 2026-08-10, turned out to be a leftover working branch from the original fork merge - literally the fork's own branded `15-dev` history at that commit range, not a clean-room build. Rebuilt clean from `505dbc40e6`; all three fork commits (`9f0d2ef2ad`, `5623257a38`, `49dd1d3464`) cherry-picked with zero conflicts since the touched files hadn't been rebranded yet at that point in fork history. CI green) |
| 16 | `feat/backup-lock-wallpaper` | Back up/restore the lock-screen wallpaper independently of the home one, with a live preview next to the home-wallpaper preview on both create and restore screens | S | - | ready for review (pushed to `origin/feat/backup-lock-wallpaper`, `docs/pr/feat-backup-lock-wallpaper.md`. Found 2026-08-10 while auditing 15-dev commits since the Aug 1 sweep - never tracked before. CI green) |
| 17 | `fix/backup-restore-completeness` | Restore reliability: a first restore silently dropped grid-bound home-screen items (folders/icons/widgets); `SharedPreferences`/DataStore's in-process cache clobbered a raw-byte restore of the same file (grid size reverting to default); two crash fixes in that staging code (wrong DataStore staging-file extension order, an over-broad `databases/`-directory delete racing the icon cache) | M | - | ready for review (pushed to `origin/fix/backup-restore-completeness`, `docs/pr/fix-backup-restore-completeness.md`. Found 2026-08-10 while auditing 15-dev commits since the Aug 1 sweep - never tracked before; the fork's own commit history split this into 3 separate iterative fixes (Aug 7-8), all folded into one branch here since they're one coherent restore-reliability bug chain, not distinct issues. CI green) |
| 18 | `feat/folder-delete-confirm` | Confirmation bottom sheet before deleting a folder from Settings, stating the apps inside are not deleted (they return to the drawer as regular entries) | S | Searched, no matching upstream issue found | ready for review (pushed to `origin/feat/folder-delete-confirm`, `docs/pr/feat-folder-delete-confirm.md`. Found 2026-08-10 while auditing 15-dev commits since the Aug 1 sweep - never tracked before; standalone, no dependency on #10's nesting (upstream's actual current folder-preferences code has no nesting concept, wording kept accurate to that). CI green) |
| 11 | `feat/folder-manual-order` | Optional manual drag-and-drop ordering of folders/folder contents (alphabetical stays default), including +/- add/remove buttons for folder contents while in manual-order mode | S/M | - | pending, after #10's data-model PR lands upstream (the fork's manual-order code is entangled with nesting - folders always sort before apps in both). **2026-08-10 drift check**: the underlying feature is fully implemented and merged on `15-dev` (predates the Aug 1 sweep - `914b7a45bc` and related, 2026-07-29/30), plus one more commit since then (`442f7699cf`, 2026-08-10 - the +/- add/remove buttons, now folded into this row's scope for whenever the branch is built). Status unchanged: still correctly gated on #10 landing first |
| 12 | `feat/settings-pin-lock` | New PIN/biometric lock gating launcher settings and any exit into system Settings (doesn't cover shortcuts/widgets from other apps) | M/L | - | design doc approved 2026-07-31 (see `docs/pr/settings-pin-lock-PROPOSAL.md`); branch not started yet - fully independent of #10/#11, can be built whenever. **2026-08-10 drift check**: confirmed no functional code has landed since Aug 1 (only the two mechanical rebrand-refactor commits touch `SettingsLockUnlockActivity.kt`, zero logic change) - status unchanged |
| 13 | `fix/drag-drop-close-crash` | NPE in `DragLayer#animateViewIntoPosition`/`Workspace#onDropExternal` when a workspace-state-transition race leaves `performReorder()`/`addInScreen()` unable to resolve a valid cell | S | Related [#2966](https://github.com/LawnchairLauncher/lawnchair/issues/2966) (closed **not planned**, filed against an old 12.1.0-alpha4 - same NPE signature and drop-onto-workspace path, but no maintainer rationale visible for the closure; **re-verify repro against current upstream before citing**, a "not planned" close could mean rejected-as-unreproducible rather than fixed) | not yet ported to a clean-room branch against upstream's current base - added 2026-08-10, found while auditing fork branches for merge status. **2026-08-10 drift check**: confirmed unchanged - no commits since Aug 1 touch `DragLayer.java` or the relevant `Workspace.java` drop paths (only the two rebrand-refactor commits touch `Workspace.java`, mechanically) |
| 14 | `feat/recents-button-interception` | Reliably opens Recents via the physical button/gesture on firmware where `config_recentsComponentName` is hardcoded to a broken vendor/OEM Recents provider (button/gesture otherwise flashes and immediately falls back) | M | No matching upstream issue found (searched; this symptom is tied to a specific class of firmware, not previously reported against Lawnchair itself) | ready for review (pushed to `origin/feat/recents-button-interception`, `docs/pr/feat-recents-button-interception.md` - the fork's single largest piece of work this session: a `RecentsBounceActivity` that stands in as a plain foreground activity before invoking `GLOBAL_ACTION_RECENTS`, plus a `LawnchairAccessibilityService`-driven `TYPE_ACCESSIBILITY_OVERLAY` over the physical button's on-screen position to intercept its touch before SystemUI's own broken handling runs; opt-in via a new "Recents interception" setting. Filed as a feature, not a fix - this never worked on this firmware class in the first place, so making it work is new capability, not a restoration. CI-verified (compiles clean) but not yet independently re-tested on hardware from this specific clean-room build - the fork's own integrated version (identical logic) is confirmed working on real hardware) |

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
