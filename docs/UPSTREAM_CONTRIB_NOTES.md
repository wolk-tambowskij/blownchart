# Notes on contributing to upstream Lawnchair

Summary of `CONTRIBUTING.md` (unmodified from upstream in this repo) and
`.github/pull_request_template.md`, for prepping PRs targeting
[LawnchairLauncher/lawnchair](https://github.com/LawnchairLauncher/lawnchair).
See `docs/UPSTREAM_CONTRIB_BRANCHES.md` for how this fork applies these
rules (inventory, S/M/L classification, and the actual branches).

## Where code goes

- `lawnchair/` — Lawnchair's own code. New files belong here by default.
- `src/` — a clone of the Launcher3 (AOSP) codebase with modifications.
  Keep changes here to a minimum.

## Base branch

All PRs target `15-dev` (matches the Android version this branch tracks).

## Review tier (determines the PR process, not just a label)

| Tier | Examples | Process |
|---|---|---|
| Trivial | Typo/comment fixes, style-only changes | Direct commit (not applicable to us — we're an external contributor, everything from us needs a PR) |
| Simple, self-contained | Single-file bug fixes, minor UX polish | PR, one reviewer, auto-merge after CI + approval |
| Medium complexity | New settings screen, new drawer search provider | Detailed PR, core team review |
| Major architectural | Touches core foundation (e.g. an Android version rebase) | Very detailed PR, mandatory formal approval, no merge-on-silence |

This tiering is effectively our S/M/L split too (see the feature
classification table) — S ≈ simple/self-contained, M ≈ medium
complexity, L ≈ major architectural (RFC-first).

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):
`type(scope): subject`, e.g. `feat(settings): Add toggle for new feature`.
Allowed types: `feat`, `fix`, `style`, `refactor`, `perf`, `docs`, `test`,
`chore`.

## String naming (`strings.xml`)

| Type | Format | Example |
|---|---|---|
| Generic word | `$1` | `disagree_or_agree` |
| Action | `$1_action` | `apply_action` |
| Preference/popup label | `$1_label` | `folders_label` |
| Preference/popup description | `$1_description` | `folders_description` |
| Preference choice | `$1_choice` | `off_choice` |
| Feature string | `(feature)_$1` | `colorpicker_hsb` |
| Launcher-area string | `$1_launcher` | `device_contacts_launcher` |

## Build/lint expectations

- Kotlin: follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- The repo's own CI (`ci.yml`) runs `./gradlew spotlessCheck` — run this
  locally before opening an upstream PR.
- Recommended local variant while developing: `lawnWithQuickstepGithubDebug`.

## PR template (`.github/pull_request_template.md`)

Sections to fill in: **Description** (one paragraph, `Fixes #issue` if
applicable), optional **Reasoning** for major changes, optional but
encouraged **Testing** steps, and a **Type of change** checklist (bug
fix / new feature / breaking change / refactor / performance / style /
docs / chore — mark exactly one).

## What this means for our fork-derived PRs

- Strip all fork-only content before opening a PR: branding
  (`com.blownchart*` application IDs, BlownChart naming/icons), our CI
  workflows, `docs/` files specific to this fork, the donation screen,
  `.upstream-sync-state`, etc. Upstream never sees any of it.
- One topic per branch/PR - a bugfix and a feature never share a branch,
  even if we happened to fix them in the same fork commit.
- Reference an existing upstream issue with `Fixes #N` wherever one
  exists (see the issue-tracker check in the inventory) - it measurably
  raises the odds of a quick merge and costs us nothing to check.
