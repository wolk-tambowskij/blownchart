### Description

Fixes #6147. The "All drawer folders" screen, and app-drawer folder
rendering in general, become extremely slow once a device has a large
number of installed apps - loading the folder list can take minutes,
and every single checkbox toggle in the folder editor takes multiple
seconds.

### Reasoning

Several independent issues compound into this:

- `FolderService.toItemInfo()` resolved a single `componentKey` by
  re-enumerating every installed app via `LauncherApps.getActivityList()`
  - and it was called once per folder item. Folder-list loading was
  effectively `O(total folder items × installed apps)` instead of
  `O(installed apps)`. Now builds a `componentKey -> AppInfo` map once
  per load and reuses it for every item.
- `getFoldersFlow()`/`getAllFolders()` called `FolderDao.getFolderWithItems()`
  once per folder (an N+1 query pattern). `FolderDao` gained a single
  batched `getAllFoldersWithItems()` query instead.
- `FolderDao.insertFolderWithItems()` never deleted a folder's existing
  `FolderItems` rows before inserting the new set. Since `FolderItemEntity`
  uses an auto-generated id, `OnConflictStrategy.REPLACE` never matches
  an existing row, so every save only ever added rows - unchecked apps'
  rows accumulated in the DB indefinitely instead of being removed.
- `FolderViewModel.updateFolderItems()` re-read the folder back from the
  DB via `getFolderInfo()` after every single save, which re-resolves
  every `componentKey` through a fresh `LauncherApps` enumeration -
  redundant, since the caller already has the fully-resolved new
  selection on hand. It now builds the updated `FolderInfo` from that
  directly.
- `reloadHelper.reloadGrid()` (a full launcher model rebind) fired on
  every single checkbox toggle in the folder editor. `updateFolderItems()`
  still persists each toggle immediately (so nothing is lost to process
  death), but the grid reload is now deferred to a new
  `onFolderEditingFinished()`, called once when the user actually leaves
  the folder-editing screen, and skipped entirely if nothing changed.
- `LawnchairAlphabeticalAppsList` used a `List<AppInfo>.contains()` check
  (O(n)) to test whether an app is already shown inside a folder, run
  once per app in the drawer on every refresh. Switched to a
  `Set<String>` of componentKeys for an O(1) lookup.
- `AppDrawerFoldersPreference` gated its loading spinner on
  `appsState().isEmpty()` - a full installed-apps enumeration - even
  though this screen never renders app icons, only folder metadata.
  Removed; the screen no longer waits on an unrelated apps load.

None of this is behavior-visible beyond speed: the folder list, its
contents, and the editing UI end up in the same state as before.

### Testing

Manually verified on a device with ~1700 installed apps and 40 drawer
folders: the folder list, which previously took minutes to load, now
loads promptly, and checking/unchecking apps in the folder editor is
now instant instead of multi-second per toggle. Also verified:

- Unchecking an app in the folder editor actually removes it (previously
  it could resurface after being "removed", per the stale-row bug above).
- The folder list and folder contents are unchanged after these fixes
  compared to before, for the same underlying data.
- Reordering folders and leaving the folder editor still triggers a
  grid reload exactly once.

### Compatibility

No public API, data model, or DB schema changes - this only changes how
existing data is queried and cached in memory. No migration needed.

### Type of change

:white_check_mark: **Performance** (A code change that improves performance)
