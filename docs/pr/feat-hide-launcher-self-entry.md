### Description

The launcher's own app entry shows up in its own app drawer and folder
pickers like any other installed app, which is generally not useful
(there's no reason to open Lawnchair "as an app" from within itself).
Hides it from the drawer by default via the existing hidden-apps
mechanism, while leaving it fully user-toggleable afterward.

### Reasoning

- `LauncherSelfHideMigration` (new `DataMigration<Preferences>`) adds
  the launcher's own `ComponentKey` to the existing `hidden_apps`
  DataStore set, once, guarded by a `self_app_hidden_seed_applied` flag
  so a user who deliberately un-hides it afterward isn't fought on the
  next launch. Wired into the existing `preferencesDataStore`'s
  `produceMigrations` alongside `SharedPreferencesMigration`.
- The drawer itself already respects the hidden-apps set, so this
  alone was enough to hide it from the main app list. The folder
  app-picker (`SelectAppsForDrawerFolder`), however, didn't consult
  the hidden-apps set at all - so hiding the launcher (or anything
  else) wouldn't remove it from that picker even though it correctly
  disappeared from the drawer. Fixed by filtering `apps` through the
  current `hiddenApps` set there too, the same way the drawer already
  does.

### Testing

Verified: on a fresh install, the launcher's own entry doesn't appear
in the app drawer or in the "select apps for folder" picker. Manually
un-hiding it via Hidden Apps settings makes it reappear in both, and
it stays visible after that (the migration doesn't re-hide it, since
it only ever runs once).

### Compatibility

No changes to the hidden-apps preference's format - this only seeds
its default set once. Existing installs upgrading to this version will
run the migration once on next launch, same as a fresh install.

### Type of change

:white_check_mark: **New feature** (A non-breaking change that adds functionality)
