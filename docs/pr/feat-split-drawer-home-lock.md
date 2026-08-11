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
- **Real-device follow-up**: `lockAppDrawer` didn't cover a search-only
  escape hatch - typing into app drawer search shows a gear icon
  (`SearchResultSearchSettings`, drawer-search-only, confirmed via
  `SearchSettingsSectionBuilder`'s sole wiring into
  `LawnchairLocalSearchAlgorithm`) that jumps straight into the
  drawer's own search settings, letting the lock be bypassed by
  reconfiguring search behavior instead of tampering with apps
  directly. Now hidden (`bind()`, called on every search-result bind)
  whenever `lockAppDrawer` is on, matching how every other locked-out
  action here doesn't offer the escape hatch at all rather than
  showing it disabled.

### Testing

Verified: enabling only `Lock app drawer` blocks rename/hide/uninstall
from the drawer's long-press menu but leaves the home screen, hotseat,
and folders fully editable. Enabling only `Lock home screen` does the
reverse. Enabling both blocks everywhere, matching the old single-flag
behavior. Disabling both restores full editability everywhere. With
`Lock app drawer` on, confirmed the search-settings gear icon no
longer appears when typing into app drawer search; with it off, the
icon appears and still opens search settings as before.

### Compatibility

Existing `lockHomeScreen` installs keep their current value and
behavior for the home screen; `lockAppDrawer` defaults to off, so
upgrading users see no behavior change on the drawer side until they
opt in.

### Type of change

:white_check_mark: **New feature** (A non-breaking change that adds functionality)
