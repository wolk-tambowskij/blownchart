# Upstream PR submission plan

**Nothing in this file gets sent anywhere on its own.** Every batch below
needs an explicit go-ahead from the project owner, PR by PR — this
document only proposes an order and groups branches by risk, it does not
authorize sending any of them. See `docs/UPSTREAM_CONTRIB_BRANCHES.md`
for the full status/issue-citation table this plan is built from, and
`docs/UPSTREAM_CONTRIB_NOTES.md` for upstream's own contribution rules
(PR template, one-topic-per-PR, etc).

## Ordering logic

1. **Small, clean, strong issue match first.** A tight diff with a
   citable `Fixes #NNNN` is the easiest thing for an unfamiliar
   maintainer to review and merge, and builds a track record before
   asking them to look at anything bigger.
2. **Respect real code dependencies.** #9 is branched from #8 and
   contains its commit; #10 is branched from #3 and contains its
   commits (see the table's own dependency notes) - submit the
   prerequisite first, or the diff won't apply cleanly / will look like
   it's proposing unrelated changes.
3. **De-risk anything with an open question before submitting it**, not
   after: an unverified "closed not planned" citation, an unconfirmed
   redundancy with an already-merged upstream PR, or an unreproduced
   symptom needs that verification done first, since a maintainer
   finding the problem for us costs more goodwill than finding it
   ourselves.
4. **Big/invasive last**, once smaller submissions have established the
   fork's PRs are trustworthy - nested folders (#10) is the one L-class
   feature here, with the largest diff and the most crash-hardening
   history behind it. Worth leading with the small wins first.

## Batch 1 - small, clean, strong issue match, no unresolved caveats

Send these first; each stands alone (or with its one stated dependency)
and has either a direct issue match or a clean, well-tested bug fix with
no ambiguity to sort out first.

| Order | Branch | Why first |
|---|---|---|
| 1 | `fix/folder-shape-geometry` (#2) | S, `Related #6495`, no dependency, purely a correctness fix (wrong shape rendered) |
| 2 | `fix/home-lock-uninstall-widget-bypass` (#8) | S, `Related #6929`, no dependency - **must land before #9**, since #9 is branched from it |
| 3 | `feat/split-drawer-home-lock` (#9) | S/M, `Fixes #5839` - direct match to a filed bug report, submit right after #8 |
| 4 | `feat/backup-lock-wallpaper` (#16) | S, `Fixes #5462` - direct match, self-contained |
| 5 | `feat/hide-launcher-self-entry` (#6) | S, no issue but a real, well-isolated bug fix (cross-build restore) plus the base feature |

## Batch 2 - small/medium, no strong issue match but clean additions

No open questions to resolve, just weaker (or no) issue citations - fine
to send once batch 1 has established a track record.

| Order | Branch | Notes |
|---|---|---|
| 6 | `feat/search-folder-label` (#3) | S, no issue - **submit before #10**, which depends on it |
| 7 | `feat/folder-outline` (#4) | S, no issue, self-contained |
| 8 | `feat/folder-picker-search` (#7) | S, no issue, self-contained |
| 9 | `feat/folder-delete-confirm` (#18) | S, no issue, self-contained |
| 10 | `fix/folder-list-performance` (#1) | S, reword the description before sending: `#6147` is still open on `15-dev` even though a similar rewrite (`#6996`) already merged on `16-dev` - frame as "targets the still-unfixed 15-dev", not `Fixes #6147` |
| 11 | `fix/backup-restore-completeness` (#17) | M, `Related #6576` but filed against a cross-version (15→16) restore while this fixes same-version (15→15) - mention the citation as unconfirmed-same-root-cause in the PR body, don't overclaim |

## Batch 3 - needs one verification step before sending

Each of these has a concrete, specific thing to check first - not
because the fix is questionable, but because sending it with an
unverified claim risks a maintainer finding the gap instead of us.

| Order | Branch | What to verify first |
|---|---|---|
| 12 | `fix/drag-drop-close-crash` (#13) | `Related #2966` was closed **not planned** against an old 12.1.0-alpha4 build - reproduce the same NPE against current upstream `15-dev` before citing it; if it still reproduces, the PR body should say so explicitly rather than assuming the old closure still applies |
| 13 | `fix/loader-model-thread-priority` (#15) | Partial credit already given to upstream's own thread-priority fix (Bug 396250724) - confirm whether that upstream fix already covers `15-dev` (not just `16-dev`) before proposing the thread-priority half; the batched icon-loading half is unsolicited and independent either way |
| 14 | `feat/battery-optimization-banner` (#5) | No issue match; touches `android:autoRevokePermissions`, a manifest-level behavior change maintainers may want to discuss rather than take as-is - consider opening as a discussion/draft first rather than a ready-to-merge PR |

## Batch 4 - large feature, most value, most review burden

| Order | Branch | Notes |
|---|---|---|
| 15 | `feat/nested-folders-ui` (#10) | L, `Fixes #5435` (a real, open feature request) - by far the biggest diff here, five+ rounds of real-device crash hardening behind it. Send after #3 (its dependency) has landed, and after batches 1-3 have built some track record with these maintainers. Consider whether upstream would rather see this split into smaller reviewable pieces (data model, then rendering, then the two creation paths) even though it's tracked here as one branch/one capability per the project owner's own earlier ruling for *this* fork's tracking - that ruling doesn't bind how upstream wants it presented |

## Hold - not ready to propose yet

| Branch | Why it's not in the queue above |
|---|---|
| `feat/folder-manual-order` (#11) | Blocked on #10 landing first (code dependency) - not a redundancy problem. **2026-08-11 diff against upstream #6173 confirms this branch is genuinely additive, not a duplicate**: #6173 only adds always-on drag-reorder for apps *within* one folder; this branch adds the alphabetical/manual *toggle* on top of that (#6173 offers no choice) plus a second, separate manual-ordering path for the *folder list itself* (`AppDrawerFoldersPreference.kt`), which #6173 never touches. Once #10 lands, this can move into the batches above - frame the PR body as building on #6173, not re-proposing it, so reviewers don't mistake the scope. |
| `feat/settings-pin-lock` (#12) | Design doc approved, branch not started. Nothing to submit yet. |

## Excluded from upstream entirely

| Branch | Why |
|---|---|
| `feat/recents-button-interception` (#14) | No matching upstream issue (searched) - this works around a specific class of broken OEM/vendor firmware, not a Lawnchair-side bug. Scope is arguably too narrow/fork-specific for upstream to want to maintain; recommend not proposing this one at all rather than assigning it a batch. Flagging here for the project owner to confirm or override. |

Branding, translations, and this session's own tooling (Tasks 1/2/4:
licensing, CI/release workflows, donations) were already excluded in
`docs/UPSTREAM_CONTRIB_BRANCHES.md` and aren't repeated here.
