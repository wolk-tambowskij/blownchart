### Description

First launch (home screen appearing) and app-drawer list building were
much slower than they should be on a device with a large number of
installed apps - up to 10-15s just to see the home screen, and up to
30s for the app drawer's list to finish building, on a device with
1766 installed apps. Settings screens backed by the same installed-app
enumeration (the app-picker for a drawer folder, hidden apps, pick app
for a gesture) were even worse - up to 40s+ on first open.

Part of the fix is original to this fork; part of it borrows a
thread-scheduling fix Google already shipped upstream for Lawnchair 16
(`362fa9e96a1`, Bug: 396250724), ported back onto this fork's
pre-Kotlin-conversion loader code since 15-dev predates that rewrite.

### Reasoning

Three independent issues stacked on top of each other:

- `MODEL_EXECUTOR` (the single thread every app/icon load runs on) had
  its scheduling priority toggled via plain, uncoordinated
  `setThreadPriority()` calls from three separate places (`IconCache`'s
  icon-request tracking, and two spots in `BaseLauncherBinder`). Whichever
  call landed last won - so `BaseLauncherBinder`'s deliberate drop to
  `THREAD_PRIORITY_BACKGROUND` right after the initial workspace bind
  could stomp over `IconCache`'s still-active elevation, and since
  `loadAllApps()`'s icon-cache queries for the whole app list run on
  that same thread immediately afterwards, the entire all-apps load
  could silently execute at background (throttled) priority. Replaced
  with a reference-counted `elevatePriority()`/`restorePriority()` pair
  on `LooperExecutor`, keyed by caller, with `LoaderTask.run()`
  bracketing its entire execution in it - this is the ported-from-
  upstream part.
- `allAppBulkIconLoading` - a preference that switches all-apps icon
  loading from one SQL query per app to a single grouped query - already
  existed and worked correctly, but shipped disabled by default.
  Flipped the default to enabled (the toggle itself is kept, in case a
  device needs to fall back).
- `appsState()` (backing the folder app-picker, hidden-apps list, and
  pick-app-for-gesture screens) had its own, completely separate
  per-app icon loading in `App`'s own constructor - one synchronous
  `IconCache.getTitleAndIcon()` call per installed app, unrelated to and
  unaffected by the two fixes above. This was the dominant cost for
  those screens specifically. Batched into a single
  `getTitlesAndIconsInBulk()` call, same pattern `LoaderTask` already
  used.

### Testing

Manually verified on the same ~1766-app / 40-folder device used for the
earlier folder-list-performance fix:

- Home screen now appears effectively immediately on launch (was
  10-15s).
- App drawer list build dropped to ~15-25s (was up to 30s) - the
  remaining cost is not icon loading and is a separate, not yet
  investigated cost (likely per-app `PackageManager` metadata queries
  in `AppInfo` construction).
- The folder app-picker (and the other `appsState()`-backed screens)
  went from 30-40s+ to effectively instant.

### Compatibility

No public API, data model, or DB schema changes. The bulk-icon-loading
toggle is preserved (now defaulting to on) rather than removed, unlike
upstream Lawnchair 16, so it can still be turned off per-device if
needed.

### Type of change

:white_check_mark: **Performance** (A code change that improves performance)
