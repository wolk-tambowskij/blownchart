### Description

First of a small PR chain adding one level of folder-in-folder nesting
to app-drawer folders. This PR is data model and backup coverage only -
no UI entry point yet, nothing a user can trigger. UI (rendering a
nested folder, the nest/un-nest gesture) is a follow-up PR once this one
has landed, so the schema and data-layer contract can be reviewed on
their own.

### Reasoning

- `FolderInfoEntity` gets a nullable `parentFolderId` column
  (`MIGRATION_3_4`, additive, existing rows default to `NULL` = still
  top-level). Only one level of nesting is supported: a folder that
  already has a parent can't itself be nested into, and a folder with
  children can't be nested into another folder. This is enforced in
  `FolderService.setParentFolder()`, not by a Room constraint - SQLite
  has no portable way to express "no grandchildren" at the schema level.
- No Room `ForeignKey` on `parentFolderId`, unlike `FolderItems.folderId`
  (which does have one with cascade). A self-referential FK here would
  let SQLite auto-cascade-delete a whole subtree with no chance to run
  the one-level check or any other bookkeeping first. Deletes are
  cascaded explicitly instead: `FolderDao.deleteFolderWithChildren()`
  deletes a folder's children (and their items) before deleting the
  folder itself.
- Also closes a related, pre-existing gap noticed while working in this
  area: the "preferences" Room db - where `Folders`/`FolderItems` (and
  icon overrides, wallpapers) live - was entirely absent from
  `res/xml/backupscheme.xml`, so none of it was covered by Android's
  automatic full-device backup, only by this app's own manual backup
  export. Added it, and added a WAL checkpoint
  (`AppDatabase.checkpointSync()`, already used elsewhere in this
  codebase) before either backup path copies the db file
  (`LauncherBackupAgent#onFullBackup`, `LawnchairBackup#create`), since
  a raw file copy of a WAL-mode db can otherwise miss writes still
  sitting in the `-wal` file. This isn't nesting-specific - it was
  already true for the plain (non-nested) folders already in this
  codebase - but nesting is what made "does this survive backup"
  actually worth checking carefully, so the fix is included here rather
  than filed as a separate, easy-to-forget follow-up.

### Testing

DAO-level: inserted a folder, nested a second folder inside it via
`setParentFolder`, confirmed a third folder can't be nested into the
now-nested one (`setParentFolder` returns `false`), confirmed deleting
the parent also removes the child folder and its items
(`deleteFolderWithChildren`). Manually ran a full-device backup and
restore (Settings → System → Backup, on a test device with backup
transport enabled) before/after this change and confirmed the
`preferences` db file is now present in the backup set.

### Compatibility

Migration is additive and nullable - no data loss, no forced backfill
for existing installs. `MIGRATION_3_4` is registered
(`.addMigrations(MIGRATION_3_4)`), so upgrading users don't hit a
missing-migration crash. No UI changes, so no user-visible behavior
change from this PR alone.

### Type of change

:sparkles: **New feature** (data model only - non-breaking, no UI yet)
