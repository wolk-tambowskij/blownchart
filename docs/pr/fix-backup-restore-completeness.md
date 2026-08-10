### Description

Fixes a chain of restore-reliability bugs found through real-device
backup/restore testing, all in `LawnchairBackup.restore()`: grid-bound
home-screen items (folders/icons/widgets) silently didn't come back on
the first restore after importing a backup, a raw restore of the
SharedPreferences/DataStore file was getting clobbered by this process's
own stale in-memory cache of that file (grid size reverting to default
being the most visible symptom), and two crash bugs in the fix for that
second issue.

### Reasoning

- **Missing home-screen items on first restore.** `restore()` called
  `RestoreDbTask.performRestore()` directly on an ad-hoc
  `ModelDbController` while the app was still running, instead of going
  through `RestoreDbTask.setPending()` and letting the normal cold-start
  path (`ModelDbController`'s `restoreIfNeeded()`, on the next launch
  after `restartLauncher()`) do the restore - the same route
  `LauncherBackupAgent#onRestoreFinished()` uses for a real Android
  backup/restore. Calling `performRestore()` directly skipped the
  `InvariantDeviceProfile` reinitialization that only `restoreIfNeeded()`
  does, so home-screen items (grid-bound) silently didn't come back,
  while app-drawer contents and settings (not grid-bound) restored fine -
  masking the gap until the same backup was restored a second time, by
  which point that reinit had already happened as an ordinary side effect
  of the launcher running normally in between.
- **Grid size (and other settings) reverting after restore.**
  `SharedPreferencesImpl` and Jetpack DataStore both cache a file's
  entire contents in memory the first time anything in this process
  reads it - virtually guaranteed just from the Settings/Restore UI being
  open - and both unconditionally flush that full in-memory snapshot back
  to disk on their next write from anywhere, clobbering a raw byte-level
  restore of the same file with this process's stale pre-restore state
  before the process even gets a chance to restart.
  `restoreSharedPreferencesFile()`/`restoreDataStoreFile()` now stage the
  backup's bytes under a name never touched by this process, read them
  back as a guaranteed-fresh instance, and replay every entry onto the
  real (possibly already-cached) instance through its own Editor/`edit{}`
  - updating the same live object every other reader in this process
  shares, instead of a file on disk nothing in-process is watching. The
  now-redundant (and, worse, actively harmful given the ordering) direct
  `DeviceGridState(info.gridState).writeToPrefs()` call is removed.
- **Crash: bad DataStore staging filename.** The staging file for
  `restoreDataStoreFile()` needs to end in the `preferences_pb` extension
  or `PreferenceDataStoreFactory.create()` throws
  `IllegalStateException` - the timestamp suffix has to go before that
  extension, not after.
- **Crash: over-broad directory delete.** Restoring cleared stale restore
  state via `context.getDatabasePath(LAUNCHER_DB_FILE_NAME).parentFile
  ?.deleteRecursively()`, but `.parentFile` is the entire `databases/`
  directory - home to unrelated SQLite databases this process has open
  independently (e.g. the icon cache). Wiping the whole directory raced
  with a background write on one of those and crashed the process.
  Scoped the cleanup to just `restored.db` and its `-wal`/`-shm`/
  `-journal` sidecars instead.

### Testing

Verified on-device: restoring a backup with a custom (non-default) grid
size now restores the grid correctly on the first attempt, along with
previously-missing folders and widgets. Restoring a backup whose
DataStore-backed settings differ from the currently-cached in-memory
state no longer reverts to the stale cached values. No crash on restore
of a backup that includes layout and settings.

### Compatibility

No backup file format changes - this only changes how the existing zip
entries are replayed during restore. Existing backups (including ones
made before this fix) restore correctly with the corrected logic.

### Type of change

:bug: **Bug fix** (A non-breaking change which fixes an issue)
