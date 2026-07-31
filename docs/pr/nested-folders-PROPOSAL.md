# Proposal: one level of app-drawer folder nesting (RFC + design doc)

Status: **draft, not yet a PR** — per Task 3's process, this needs
project-owner sign-off before any branch is created, and the
recommendation below is to open a GitHub Discussion/RFC issue upstream
before submitting code at all.

## Motivation / use case

Upstream `15-dev` already ships app-drawer folders (`FolderInfoEntity`,
`FolderDao`, `FolderService`, `AppDrawerFoldersPreference`,
`SelectAppsForDrawerFolder` all exist there today). Users who lean on
folders heavily to manage a large drawer run into a flat namespace: e.g.
a top-level "Google" folder, and inside it separate concerns ("Google —
Work" vs "Google — Personal") that users currently have to flatten into
one folder or spread across several top-level folders. The fork adds
**one level** of folder-in-folder nesting: a folder can contain other
folders, but a nested folder can't itself contain another nested folder.

This is scoped deliberately narrow. It is not "unlimited depth nesting"
(see Alternatives below).

## Current upstream state (baseline, confirmed by reading `upstream/15-dev`)

- `FolderInfoEntity` (`lawnchair/src/app/lawnchair/data/folder/FolderEntity.kt`):
  `id`, `title`, `hide`, `rank`, `timestamp`. No parent concept.
- `FolderItemEntity`: `id`, `folderId` (FK → `Folders.id`, cascade
  delete/update), `rank`, `item_info` (component key), `timestamp`.
- `AppDatabase.kt`: Room `@Database(..., version = 3)`, with
  `MIGRATION_1_3` (creates `Wallpapers`/`Folders`/`FolderItems` from
  scratch) and `MIGRATION_2_3` (adds the `FolderItems.folderId` index).
  No `fallbackToDestructiveMigration()` — every version bump needs an
  explicit `Migration`.
- `FolderDao.kt`: plain CRUD — `insertFolder`, `insertFolderItems`,
  `getFolderWithItems`, `getAllFolders` (`Flow`), `updateFolderInfo`,
  `deleteFolder`. `FolderItemEntity.item_info` stores a serialized
  component key, so a folder's "items" today can only be apps, never
  another folder.

## Proposed data model change

Add one nullable column to `FolderInfoEntity`:

```kotlin
@Entity(tableName = "Folders")
data class FolderInfoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val hide: Boolean = false,
    val rank: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    // Null = top-level folder. Set to another folder's id to nest this
    // folder inside it. One level only: a folder with a non-null
    // parentFolderId is rejected as a nesting target by the DAO/service
    // layer (enforced in code, not by a DB constraint - SQLite has no
    // portable "no grandchildren" check).
    val parentFolderId: Int? = null,
)
```

with `indices = [Index(value = ["parentFolderId"])]` for the
child-lookup query. Migration `MIGRATION_3_4`:

```sql
ALTER TABLE Folders ADD COLUMN parentFolderId INTEGER;
CREATE INDEX IF NOT EXISTS index_Folders_parentFolderId ON Folders(parentFolderId);
```

Additive and nullable, so every existing row gets `NULL` (top-level,
unchanged) automatically — no backfill needed. No `ForeignKey` on
`parentFolderId`: unlike `FolderItems.folderId` (which does have an FK
with cascade), a self-referential FK on `Folders.id` would let SQLite
auto-cascade-delete a whole subtree with no chance to enforce the
one-level rule or run the app's own bookkeeping. Deletes are cascaded
explicitly in `FolderDao`/`FolderService` code instead: deleting a
parent explicitly deletes its child folders (and their items) first.

The "how is a folder placed inside another folder" question is a
representation choice, not yet nailed down: either (a) reuse
`FolderItemEntity.item_info` to hold a folder reference alongside app
component keys (needs a discriminator), or (b) treat `parentFolderId`
alone as sufficient and have `FolderService` synthesize the child
folder's list entry when building a parent's contents. Whichever we
pick, that decision belongs in PR 1 alongside the migration, since it
changes `FolderDao`'s query shape.

## UI

- Rendering: a nested folder shows up as an item inside its parent
  folder's open view, using a folder-shaped icon (reusing the existing
  closed-folder preview drawing, `PreviewBackground`/`ClippedFolderIconLayoutRule`,
  at item-icon size) instead of an app icon.
- Nesting interaction: drag a folder onto another folder to nest it
  (mirrors the existing drag-app-onto-app-to-create-folder gesture).
  Un-nesting: drag the nested folder back out to the drawer.
  One-level enforcement lives in the drop-target check: a folder that
  already has a non-null `parentFolderId`, or a folder that already
  has children, refuses to accept a further nesting drop.
- `AppDrawerFoldersPreference` (folder management list) needs a way to
  show the parent/child relationship (indent or a small badge) so users
  can find and un-nest folders without relying on the drawer drag
  gesture.

## Export / import

Upstream doesn't have a folder export/import feature yet at all — the
fork adds this alongside nesting (`feat: export/import drawer folder
scheme as JSON`, currently fork commit `7cfc4b5`). If we propose that as
its own separate contribution, nesting's export/import piece is just
"add `parentFolderId` to the existing JSON shape, default it to `null`
on import for schemas that predate nesting." If export/import isn't
proposed at all in the near term, PR 3 below is dropped and nesting
ships as data model + UI only (two PRs, not three).

## Backup / restore impact

Checked `upstream/15-dev`'s two backup paths directly:

- **Lawnchair's own manual backup/restore** (`LawnchairBackup.kt`,
  `.lawnchairbackup` zip files): `getFiles()` includes
  `PREFS_DB_FILE_NAME = "preferences"` — that's the same Room database
  `Folders`/`FolderItems` live in. So folder data (including nesting,
  once this lands) is already carried by Lawnchair's own backup feature
  with zero extra work — it's a raw file copy of the whole db.
- **Android OS-level auto full-backup** (`res/xml/backupscheme.xml`):
  only lists `launcher.db` (grid layout databases) and the
  `com.android.launcher3.prefs.xml` shared-prefs file. The Room
  `preferences` db (icon overrides, wallpapers, *and* folders) is
  **not** in that list, so it is not covered by Android's automatic
  cloud backup today. This is true for the existing non-nested folder
  feature already upstream — nesting doesn't change or regress
  anything here, just worth flagging to reviewers so it isn't mistaken
  for a new gap introduced by this PR.

## Backward compatibility

- Migration is additive/nullable — no data loss, no forced backfill.
- No `fallbackToDestructiveMigration()` is configured, so the
  `MIGRATION_3_4` object must actually be registered
  (`.addMigrations(MIGRATION_3_4)`) or upgrading users crash on first
  launch after the update. (Same pattern as the existing
  `MIGRATION_1_3`/`MIGRATION_2_3` registration.)
- Downgrading the app (installing an older build over a newer one) is
  not a supported Room path in this codebase already — not something
  nesting introduces.

## Alternatives considered

- **Unlimited nesting depth.** Rejected: needs cycle detection, harder
  UI (indefinite indentation, deeper drag/drop reasoning), and most
  requests for this feature (and comparable launchers with folder
  nesting) stop at one level. One level covers the "group of groups"
  use case without the complexity.
- **No dedicated `parentFolderId` column — encode nesting purely via
  `FolderItemEntity.item_info`.** Rejected: folders and apps would then
  share one loosely-typed string column with an implicit discriminator,
  which is harder to query (`getAllFolders()` couldn't stay a flat
  `SELECT * FROM Folders`) and harder to migrate later.

## RFC framing

This is an L-class change (schema + two/three non-trivial UI+behavior
areas) that also makes an opinionated UX call (folders-in-folders is a
feature some launcher maintainers deliberately avoid, citing
discoverability). Recommendation: open a GitHub Discussion or an RFC-style
issue on `LawnchairLauncher/lawnchair` first, describing the one-level
cap and the nesting/un-nesting gesture, before sending any PR — so
maintainers can weigh in on scope and the interaction design before
code review has to relitigate it. Do not open PR 1 until that
discussion has run its course (or the maintainers explicitly say "just
send the PRs").

## Proposed PR chain (sequential, each independently buildable/mergeable)

1. **Data model.** `parentFolderId` column + `MIGRATION_3_4` +
   `FolderDao`/`FolderService` support (`getChildFolders(parentId)`,
   one-level validation on "set parent", explicit cascade delete of
   children when a parent folder is deleted). No UI entry point yet —
   inert until PR 2, but fully covered by DAO-level tests. Keeps PR 1
   reviewable as "just the schema and data-layer contract."
2. **UI.** Render nested folders inside their parent's open view,
   nest/un-nest drag gesture, one-level guard in the drop-target check,
   parent/child indication in `AppDrawerFoldersPreference`.
3. **Export/import.** Only if the export/import feature itself is
   proposed upstream (see above) — extends its JSON schema with
   `parentFolderId` and old-schema-compatible defaulting. If
   export/import isn't being proposed, this PR is dropped and the chain
   is two PRs, not three.

Each PR gets its own `docs/pr/<branch-name>.md` once branched, per the
usual process for this task.
