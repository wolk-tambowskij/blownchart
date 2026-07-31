### Description

Fixes #5839. `Lock home screen` currently also locks the app drawer's
own long-press menu (rename/hide/change icon/uninstall), which is
surprising - the reporter's expectation, matching this fix, is that
home screen lock should only affect the home screen. Splits the single
`lockHomeScreen` flag into two independent toggles.

**Depends on the previous commit in this branch** (also proposed
separately as its own PR: lockHomeScreen didn't block uninstall on any
surface). That fix is what first made `UNINSTALL` check `lockHomeScreen`
at all - this PR is meaningless without it, since there'd be nothing to
split. If that one merges first, rebase this on top of `15-dev`; if you'd
rather review them together, this branch already includes both commits.

### Reasoning

- New `lockAppDrawer` preference (`PreferenceManager2.kt`,
  `config_default_lock_app_drawer`), exposed as its own `Lock app drawer`
  switch in App Drawer settings, right next to the existing search/
  suggestions options.
- `LawnchairShortcut.isLockedForItem()` (new) decides which of the two
  flags applies based on the long-pressed item's runtime type: drawer
  icons are bound from a plain `AppInfo` (`ModelAppInfo` here), while
  home screen/hotseat/folder icons are always `WorkspaceItemInfo`. Both
  `CUSTOMIZE` and `UNINSTALL` now go through this instead of checking
  `lockHomeScreen` directly.

### Testing

Verified: enabling only `Lock app drawer` blocks rename/hide/uninstall
from the drawer's long-press menu but leaves the home screen, hotseat,
and folders fully editable. Enabling only `Lock home screen` does the
reverse. Enabling both blocks everywhere, matching the old single-flag
behavior. Disabling both restores full editability everywhere.

### Compatibility

Existing `lockHomeScreen` installs keep their current value and
behavior for the home screen; `lockAppDrawer` defaults to off, so
upgrading users see no behavior change on the drawer side until they
opt in.

### Type of change

:white_check_mark: **New feature** (A non-breaking change that adds functionality)
