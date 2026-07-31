# Syncing with upstream Lawnchair

BlownChart is a fork of [Lawnchair](https://github.com/LawnchairLauncher/lawnchair)
and follows Lawnchair's own branch-per-Android-version convention rather
than a single `main` branch: our current working branch is `15-dev`,
tracking Lawnchair's own `15-dev` branch (Launcher3 for Android 15). When
this fork moves on to Android 16, the same pattern applies to a `16-dev`
branch, tracking upstream's `16-dev`.

## Remotes

```sh
git remote add upstream https://github.com/LawnchairLauncher/lawnchair.git
git fetch upstream 15-dev --tags
```

Sessions/containers are ephemeral and don't persist `git remote` config,
so re-run `git remote add upstream ...` whenever you start fresh (the CI
workflows below add it themselves, every run).

There is no separate local `upstream-mirror` branch to maintain: the
remote-tracking ref `upstream/15-dev`, refreshed by `git fetch upstream`,
already serves that purpose — it is never committed to directly, only
fetched.

## How upstream releases this branch

Lawnchair tags this branch's history as `v15.<minor>.<patch>[-betaN]`
(e.g. `v15.0.0-beta3.0`). `upstream-watch.yml` (below) tracks the newest
`v15.*` tag reachable from `upstream/15-dev` as "the current upstream
release" for this branch.

## Branching strategy for our own changes

- `15-dev` — our actual working/release branch. All fork commits land
  here (directly or via PR).
- `sync/upstream-<tag>` — created automatically by `upstream-watch.yml`
  when a new upstream tag appears; holds the merge/rebase of our history
  onto that tag. Never edited by hand except to resolve conflicts (see
  `docs/RESOLVE_CONFLICTS.md`).
- Feature/fix branches (`feat/...`, `fix/...`) — used both for normal fork
  development and, per Task 3's process, for isolating changes we intend
  to upstream as clean PRs to `LawnchairLauncher/lawnchair`. Keeping fork
  changes in reasonably atomic commits (already our practice) is what
  makes both of these possible: it's what lets `upstream-watch.yml`'s
  merge succeed cleanly more often, and what lets us cherry-pick a single
  feature out for an upstream PR without dragging in unrelated fork-only
  changes (branding, applicationId, our CI).

## The sync workflow, end to end

1. `upstream-watch.yml` runs daily (and on manual dispatch). It fetches
   `upstream/15-dev` and its tags, and checks whether a newer `v15.*` tag
   exists than what's already merged into our `15-dev` (checked via
   ancestry, not a mutable counter — see the workflow for details).
2. If yes, and no `sync/upstream-<tag>` branch/PR already exists for it,
   the workflow creates that branch from `15-dev`, attempts
   `git merge upstream/<tag>`, and:
   - **Clean merge:** builds a debug APK, runs `spotlessCheck`, pushes the
     branch, and opens a PR titled `Sync upstream <tag>` summarizing what
     changed and whether the build succeeded.
   - **Conflicts:** aborts the merge (a merge with unresolved conflicts
     can't be pushed as a real commit), pushes `sync/upstream-<tag>` as a
     plain copy of `15-dev` so a human can pick it up with one
     `git fetch && git checkout sync/upstream-<tag> && git merge
     upstream/<tag>`, and opens a GitHub issue labeled `needs-manual-merge`
     listing every file the attempted merge conflicted on. It does **not**
     attempt to resolve anything automatically.
3. A human reviews the PR (and, for conflicts, resolves them — optionally
   with Claude Code's help, see `docs/RESOLVE_CONFLICTS.md`), confirms the
   build and a real-device check, and merges into `15-dev` themselves.
   Tagging a new BlownChart release (`vX.Y.Z`, triggering `release.yml`)
   is a separate, deliberate step after that.

Nothing in this pipeline merges into `15-dev` or cuts a release without a
human in the loop.

## `.upstream-sync-state`

A plain-text file at the repo root recording the last upstream tag for
which `upstream-watch.yml` opened a sync PR, purely as a human-readable
log (e.g. "we noticed v15.0.0-beta4.0 on 2026-08-01, PR #47"). It is
**not** the source of truth for whether a tag still needs syncing — that's
always re-derived from actual branch ancestry, so a stale or hand-edited
state file can't cause a release to be silently skipped.
