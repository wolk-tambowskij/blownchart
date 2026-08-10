### Description

One level of folder-in-folder nesting for folders, covering both surfaces
Launcher3 already renders a `FolderInfo` on - the app drawer's own folder
list, and any folder icon a user has placed on the home screen - since both
share the same `Folder`/`FolderIcon`/`FolderPagedView` (AOSP) rendering
pipeline this PR extends. A subfolder is just a `FolderInfo` added as an item
inside another `FolderInfo`'s contents, the same way an app pair is already a
second "collection" item type; nesting, badge, and search-path changes below
apply everywhere a folder can appear, not to the drawer specifically.

This branch is stacked on `feat/search-folder-label` (#3) and contains its
commits too - the nested-folder search subtitle extends that PR's "which
folder is this app in" lookup rather than duplicating it, so this PR is
meaningless without it landing first (same dependency shape as #9 on #8).
Everything from `feat/search-folder-label`'s own commits onward that isn't
about search-folder-label itself is this PR's actual content: the data model
(`parentFolderId`), and the full user-visible nesting experience -
creating/managing a nested folder from Settings, seeing it rendered as its
own icon inside its parent's open folder view (in the drawer, and on the home
screen if the parent folder is placed there), a badge cue on the parent's
closed icon, and a "parent → folder" search subtitle for an app inside a
nested subfolder.

### Reasoning

**Data model:**

- `FolderInfoEntity` gets a nullable `parentFolderId` column
  (`MIGRATION_3_4`, additive, existing rows default to `NULL` = still
  top-level). Only one level of nesting is supported: a folder that already
  has a parent can't itself be nested into, and a folder with children can't
  be nested into another folder. Enforced in `FolderService.setParentFolder()`,
  not by a Room constraint - SQLite has no portable way to express "no
  grandchildren" at the schema level.
- No Room `ForeignKey` on `parentFolderId`, unlike `FolderItems.folderId`
  (which does have one with cascade). A self-referential FK here would let
  SQLite auto-cascade-delete a whole subtree with no chance to run the
  one-level check or any other bookkeeping first. Deletes are cascaded
  explicitly instead: `FolderDao.deleteFolderWithChildren()` deletes a
  folder's children (and their items) before deleting the folder itself.
- Also closes a related, pre-existing gap noticed while working in this area:
  the "preferences" Room db - where `Folders`/`FolderItems` (and icon
  overrides, wallpapers) live - was entirely absent from
  `res/xml/backupscheme.xml`, so none of it was covered by Android's
  automatic full-device backup, only by this app's own manual backup export.
  Added it, and added a WAL checkpoint (`AppDatabase.checkpointSync()`,
  already used elsewhere in this codebase) before either backup path copies
  the db file (`LauncherBackupAgent#onFullBackup`, `LawnchairBackup#create`),
  since a raw file copy of a WAL-mode db can otherwise miss writes still
  sitting in the `-wal` file. Not nesting-specific - already true for plain
  folders - but nesting is what made "does this survive backup" worth
  checking carefully, so it's included here rather than filed as a
  separate, easy-to-forget follow-up.

**Rendering (`Folder`/`FolderIcon`/`FolderPagedView` - shared by the drawer
and the home screen):**

- `Folder.willAcceptItemType()`: accepts `ITEM_TYPE_FOLDER` so
  `FolderInfo.add()` no longer throws for a nested folder item.
- `FolderPagedView.createNewView()`: renders a subfolder cell via
  `FolderIcon.inflateFolderAndIcon()` with a new `folder_subfolder.xml`
  layout (sized as one grid cell, like `folder_app_pair.xml`). Tapping it
  opens its own `Folder` popup for free through the existing `FolderInfo`
  click handling - no changes needed there, and this is why the same code
  path renders correctly whether the parent folder lives in the drawer or on
  the home screen.
- `FolderIcon.getPreviewItemsOnPage()`: excludes subfolders from the parent's
  own closed-icon mini-preview, since a subfolder has no static preview
  `Drawable` of its own yet and would NPE in `PreviewItemManager#setDrawable`.
- `FolderIcon.drawNestedFolderBadge()`: draws a small, solid, fixed-color
  folder glyph (new `ic_folder_badge.xml`) straddling the bottom-right edge
  of a parent folder's closed icon when it contains a subfolder - centered on
  the inset edge, so the background's own border passes through the middle
  of the badge instead of running along its edge.

**App layer:**

- `FolderService.getFoldersFlow()` (the query that builds the drawer) now
  embeds each top-level folder's subfolder as its own `FolderInfo` item,
  appended after the plain apps, instead of flattening its apps into the
  parent. Recursing with no further children keeps this to exactly one level
  deep, enforced by `getNestableFolders()` only offering childless folders as
  valid nesting targets elsewhere, not by anything in this query.
- `LawnchairAlphabeticalAppsList`: the folder-rebuild loop now re-resolves a
  subfolder's own apps against the live `AllAppsStore` the same way as its
  parent, instead of trying (and silently failing - a `FolderInfo` has no
  `componentKey`) to resolve the subfolder itself that way.
- `AppDrawerFoldersPreference` (Settings): the management list shows every
  folder, nested ones included - a nested folder renders directly under its
  parent, indented 24dp (matching `FolderPagedView`'s own reserved
  drag-handle width), with a small folder icon next to a parent's title when
  it has a subfolder. `FolderEditSheet` gets a "Nest inside" picker (offering
  every other top-level, childless-of-its-own-nesting folder as a target, or
  "None" to un-nest).
- Search (`SearchTargetFactory` / `LawnchairAppSearchAlgorithm`): an app
  inside a nested subfolder now shows "In `<parent>` → `<folder>`" instead of
  just the immediate folder. Extends the existing single-level "In
  `<folder>`" subtitle from `feat/search-folder-label` rather than replacing
  it - a plain app in a top-level (non-nested) folder still shows exactly
  what it did before.

### Scope note (read before reviewing)

Manual drag-to-reorder is **not** extended to nested folders in this PR - a
nested folder's position relative to its own siblings under the same parent
isn't wired up (today, un-nesting is how you'd reposition one). That's
`folder-manual-order` territory: a separate, later item in this contribution
plan that explicitly depends on this one landing first.

### Testing

DAO-level: inserted a folder, nested a second folder inside it via
`setParentFolder`, confirmed a third folder can't be nested into the
now-nested one (`setParentFolder` returns `false`), confirmed deleting the
parent also removes the child folder and its items
(`deleteFolderWithChildren`). Manually ran a full-device backup and restore
before/after this change and confirmed the `preferences` db file is now
present in the backup set.

UI: created folder A, created folder B, nested B into A via the "Nest
inside" picker - B rendered indented under A in the Settings list with a
small folder icon next to A's title, and in the live app drawer, opening A
showed B as its own folder cell (tapping it opened B's own popup) while A's
closed icon showed the badge. Placed A on the home screen and confirmed the
same rendering (subfolder cell, badge) there too. Searched for an app inside
B - the result subtitle read "In A → B". Un-nested B via "None" - back to
top-level in both the Settings list and every surface it appears on, badge
gone from A. Deleted A while it still had a nested child - both were
removed (cascade delete).

### Compatibility

Migration is additive and nullable - no data loss, no forced backfill for
existing installs. `MIGRATION_3_4` is registered
(`.addMigrations(MIGRATION_3_4)`). No behavior change for existing installs
with no nested folders - a plain top-level folder's rendering, preview, and
search subtitle are byte-for-byte the same as before this PR when it has no
subfolder, on either surface.

### Type of change

:sparkles: **New feature** (non-breaking; manual reordering of nested
folders is a known, separately tracked follow-up - see scope note above)
