# Resolving an upstream sync conflict with Claude Code

This is the prompt/checklist for running Claude Code locally against a
`sync/upstream-<tag>` branch that `upstream-watch.yml` flagged with
`needs-manual-merge` (see `docs/UPSTREAM_SYNC.md`). It's meant to be run
interactively, with a human reviewing every resolution and doing the
final on-device check — not unattended.

## Setup

```sh
git fetch origin sync/upstream-<tag>
git checkout sync/upstream-<tag>
git remote add upstream https://github.com/LawnchairLauncher/lawnchair.git 2>/dev/null || true
git fetch upstream <tag>
git merge <tag>   # reproduces the conflicts locally
```

Then start Claude Code in the repo root and give it this task.

## The prompt

> This branch is a sync of upstream Lawnchair tag `<tag>` into our fork's
> `15-dev`. `git merge <tag>` is currently mid-conflict. For **each**
> conflicted file:
>
> 1. Show me both sides of the conflict (ours vs. upstream) with enough
>    surrounding context to understand what each side is doing.
> 2. Classify it as one of:
>    - **Our fix/feature still needed** — upstream hasn't touched this
>      logic, or touched it in a way that doesn't address why we changed
>      it. Keep our side, reapplied on top of any unrelated upstream
>      changes in the same file.
>    - **Upstream already fixed it** — upstream's version does what our
>      patch was trying to do (or better). Take upstream's side, drop ours.
>    - **Needs rework** — both sides touch the same logic in incompatible
>      ways and neither can simply be dropped. Propose a merged version
>      that preserves both intents, and explain the reasoning.
> 3. State your classification and reasoning before editing the file, not
>    just the resulting diff.
>
> Do **not** guess silently on anything you're not confident about -
> flag it for me instead of picking a side.
>
> Once every file is resolved: stage everything, but do **not** commit or
> push until I've reviewed the diff.
>
> After I approve, build (`./gradlew assembleLawnWithQuickstepGithubDebug`
> and `spotlessCheck`) and report the result before committing the merge.

## Why a device check is still required

A clean merge and a green build only prove the tree compiles and the
linter is happy - they say nothing about *behavior*. Logical conflicts
(code that merges and builds but now does the wrong thing at runtime -
e.g. an upstream refactor of a class our folder-lock or nested-folder
code hooks into) won't show up in CI. Before merging `sync/upstream-<tag>`
into `15-dev`:

- Install the resulting debug build on a real device (or emulator).
- Exercise every fork-specific feature at least once (drawer folders and
  nesting, folder search, the settings/screen lock, folder backup and
  restore, icon shapes) - not just whatever upstream's tag happened to
  touch, since a merge can silently break something adjacent.
- Only then merge the PR and, separately, consider cutting a new
  BlownChart release tag.
