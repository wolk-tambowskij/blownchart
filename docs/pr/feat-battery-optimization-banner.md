### Description

Aggressive battery optimization can kill the launcher process and
degrade background work (widget updates, notification dots, etc.), and
there's currently no in-app way to fix that beyond digging through
system settings. Adds a persistent settings-dashboard banner prompting
the user to exempt the launcher, styled and positioned identically to
the existing "set as default launcher" warning right above it.

### Reasoning

- `Context.isIgnoringBatteryOptimizations()` (new, `LawnchairUtils.kt`)
  wraps `PowerManager.isIgnoringBatteryOptimizations()`.
- `PreferencesBatteryOptimizationWarning` (new, `PreferencesDashboard.kt`)
  mirrors `PreferencesSetDefaultLauncherWarning` exactly - same `Surface`
  styling, same `PreferenceTemplate` layout, same `TipsAndUpdates` icon -
  just with an intent to `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  instead of `ACTION_HOME_SETTINGS`.
- The one real difference from the default-launcher warning: that one
  doesn't need to re-check its condition, because setting a new default
  launcher relaunches this Activity. Returning from the battery
  optimization settings screen doesn't - it's the same Activity,
  resumed - so a plain `if (!context.isIgnoringBatteryOptimizations())`
  would only ever be evaluated once, on first composition, and the
  banner would never disappear even after the user grants the
  exemption. Wrapping the check in `remember(lifecycleState())` makes
  it re-evaluate on every resume instead.

### Testing

Verified: banner shows when the app isn't exempted, opens the system
battery-optimization dialog on tap, and disappears (without needing to
navigate away and back) once the exemption is granted and the settings
screen resumes.

### Compatibility

New string (`battery_optimization_tip`) and two new public functions.
No existing behavior changed.

### Type of change

:white_check_mark: **New feature** (A non-breaking change that adds functionality)
