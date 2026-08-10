### Description

The launcher's own app entry shows up in its own app drawer and folder
pickers like any other installed app, which is generally not useful
(there's no reason to open Lawnchair "as an app" from within itself).
Hides it from the drawer by default via the existing hidden-apps
mechanism, while leaving it fully user-toggleable afterward - and keeps
that seed correct across a backup/restore between two different builds
of the app (e.g. debug vs release, or a build made before a
package/class rename).

### Reasoning

- `LauncherSelfHideMigration` (new `DataMigration<Preferences>`) adds
  the launcher's own `ComponentKey` to the existing `hidden_apps`
  DataStore set, once, so a user who deliberately un-hides it
  afterward isn't fought on the next launch. Wired into the existing
  `preferencesDataStore`'s `produceMigrations` alongside
  `SharedPreferencesMigration`.
- The drawer itself already respects the hidden-apps set, so this
  alone was enough to hide it from the main app list. The folder
  app-picker (`SelectAppsForDrawerFolder`), however, didn't consult
  the hidden-apps set at all - so hiding the launcher (or anything
  else) wouldn't remove it from that picker even though it correctly
  disappeared from the drawer. Fixed by filtering `apps` through the
  current `hiddenApps` set there too, the same way the drawer already
  does.
- The one-time "applied" guard is keyed on the launcher's own
  component identity (a string preference holding the last-seeded
  `selfComponentKey`), not a plain boolean. A bare boolean flag means a
  backup made on one build and restored onto a different one (debug vs
  release `applicationId`, or a build from before a package/class
  rename) carries over "already applied" without the seeded entry ever
  actually matching the now-installed launcher's own component -
  permanently preventing the seed from running again for the component
  that's actually installed, and leaving its icon stuck visible in the
  drawer after the restore. Comparing against the real component
  identity means a mismatch re-seeds correctly, while a user's later
  manual un-hide for a build that's already correctly seeded still
  isn't fought.

### Testing

Verified: on a fresh install, the launcher's own entry doesn't appear
in the app drawer or in the "select apps for folder" picker. Manually
un-hiding it via Hidden Apps settings makes it reappear in both, and
it stays visible after that (the migration doesn't re-hide it, since
it only ever runs once). Cross-build restore: seeded and hid the entry
on one build, restored that backup onto a different build (different
`applicationId`) - the entry re-seeded and stayed hidden instead of
getting stuck visible.

### Compatibility

No changes to the hidden-apps preference's format - this only seeds
its default set once. Existing installs upgrading to this version will
run the migration once on next launch, same as a fresh install. The
migration's own guard key changes from a boolean to a string
preference; an install that already ran the old boolean-guarded
version simply re-seeds once more on upgrade (idempotent - hiding an
already-hidden entry is a no-op).

### Type of change

:white_check_mark: **New feature** (A non-breaking change that adds functionality)
