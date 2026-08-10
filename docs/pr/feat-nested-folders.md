### Description

One level of folder-in-folder nesting for folders, covering both surfaces
Launcher3 already renders a `FolderInfo` on - the app drawer's own folder
list, and any folder icon a user has placed on the home screen - since both
share the same `Folder`/`FolderIcon`/`FolderPagedView` (AOSP) rendering
pipeline this PR extends. A subfolder is just a `FolderInfo` added as an item
inside another `FolderInfo`'s contents, the same way an app pair is already a
second "collection" item type; nesting, badge, and search-path changes below
apply everywhere a folder can appear, not to the drawer specifically.

There are two independent ways to end up with a nested folder, and this PR
covers both:

- **Settings picker**: create or edit a folder in Settings and use its "Nest
  inside" field to place it inside another top-level folder (or "None" to
  un-nest). Works from Settings regardless of whether either folder is
  currently open anywhere.
- **Drag-to-merge**: with a folder already open (drawer or home screen), drag
  one of its icons - an app, shortcut, app pair, or another already-placed
  icon - directly onto a sibling icon in that same open folder, the same
  gesture that creates a brand-new top-level folder by dragging one
  home-screen icon onto another. The two icons merge into a brand-new nested
  subfolder in place of the sibling that was dropped onto. This is a new
  capability - dragging one icon onto another to create a folder already
  existed for the workspace, but doing it again one level down, inside an
  already-open folder, didn't exist before this PR on the fork or upstream.

Both paths converge on the same underlying model change (a `FolderInfo`
nested inside another `FolderInfo`'s contents) and the same one-level depth
guard, so everything nesting does once created - rendering, badge, search
subtitle, un-nesting - behaves identically no matter which path created it.

This branch is stacked on `feat/search-folder-label` (#3) and contains its
commits too - the nested-folder search subtitle extends that PR's "which
folder is this app in" lookup rather than duplicating it, so this PR is
meaningless without it landing first (same dependency shape as #9 on #8).
Everything from `feat/search-folder-label`'s own commits onward that isn't
about search-folder-label itself is this PR's actual content: the data model
(`parentFolderId`), and the full user-visible nesting experience - creating a
nested folder from Settings or by dragging one icon onto another inside an
already-open folder, seeing it rendered as its own icon inside its parent's
open folder view (in the drawer, and on the home screen if the parent folder
is placed there), a badge cue on the parent's closed icon, a "parent →
folder" search subtitle for an app inside a nested subfolder, and dragging a
nested folder back out of its parent to un-nest it.

### Reasoning

**Data model:**

- `FolderInfoEntity` gets a nullable `parentFolderId` column
  (`MIGRATION_3_4`, additive, existing rows default to `NULL` = still
  top-level). Only one level of nesting is supported: a folder that already
  has a parent can't itself be nested into, and a folder with children can't
  be nested into another folder. Enforced in `FolderService.setParentFolder()`
  for the Settings path, not by a Room constraint - SQLite has no portable
  way to express "no grandchildren" at the schema level. The drag-to-merge
  path enforces the identical rule its own way - see below - so a nested
  folder can't be created with more than one level of depth regardless of
  which entry point is used.
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
  `FolderInfo.add()` no longer throws for a nested folder item, regardless of
  which path put it there.
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

**Drag-and-drop (moving a nested folder, and creating one by merging icons
inside an already-open folder):**

Nested folders being real `FolderInfo` items - not a display-only grouping -
means the existing drag/drop machinery in `Folder` and `FolderIcon` mostly
just works once `willAcceptItemType()` accepts one, but a handful of paths
assumed every item in a folder was a plain app/shortcut and needed explicit
handling, plus one entirely new interaction:

- **Dragging a nested folder back out**: `Folder.onLongClick()` skips
  starting a drag for a `FolderInfo`-tagged item only when
  `isInAppDrawer()` is true for the folder being dragged out of. An
  app-drawer subfolder isn't a real model item with a home-screen slot to
  drop into - its position is managed from the folder list instead - but a
  nested folder inside a real home-screen folder is a database-backed item
  like any sibling app or app pair, so it drags out the normal way (back to
  the home screen, un-nesting it, or into a different open folder).
- **Dropping onto an existing nested-subfolder icon**: `Folder.onDragOver()`
  now detects when the drag is hovering an already-nested `FolderIcon` shown
  inside this folder's own grid (`getFolderIconAtRank()`) and hands hover
  off to that icon's own `onDragEnter`/`onDragExit` - the same accept
  highlight and spring-loaded auto-open a folder icon on the home screen
  gets - instead of reordering the dragged item as a new sibling next to it.
  `Folder.onDrop()` mirrors this: a drop that lands on the nested icon is
  forwarded to `FolderIcon#onDrop` rather than added to this folder directly.
- **Creating a nested folder by merging two icons**: dragging one icon onto
  a plain sibling icon inside an already-open folder - not onto an existing
  subfolder icon, that's the case above - now creates a brand-new nested
  folder from the two, the in-folder equivalent of dragging one home-screen
  icon onto another to create a top-level folder. `Folder.getMergeTargetAtRank()`
  identifies the hover target the same way `getFolderIconAtRank()` does for
  existing subfolders, and `Folder.createNestedFolder()` does the actual
  merge: creates a new `FolderInfo`, adds both items to it via the database,
  and swaps it into this folder's contents in place of the sibling that was
  dropped onto. Mirrors `Workspace#willCreateUserFolder`/
  `createUserFolderIfNecessary` and `Launcher#addFolder`, adapted to a
  folder's own rank-based content instead of the workspace's `CellLayout`
  grid.
- **Depth guard on both paths**: nesting is capped at one level everywhere,
  not just through the Settings picker. `getFolderIconAtRank()` only ever
  runs for icons shown inside an already-open folder, so any `FolderIcon` it
  finds there is by definition already one level nested - it rejects a
  dragged `FolderInfo` outright rather than checking case-by-case, which
  also means dragging a folder onto an already-nested subfolder icon falls
  through instead of creating a second level. `getMergeTargetAtRank()`
  applies the same two-sided check for the merge-create path: a dragged
  `FolderInfo` is rejected (a folder that already has a parent can't become
  a merge target's new sibling folder), and `Folder.isNested()` refuses to
  run the merge at all when *this* folder - the one currently open, whose
  grid the drag is hovering - is itself already a nested subfolder, since
  that would create a folder two levels deep.
- **Crash and race-condition hardening**, found testing the above on-device:
  - *Collapsing a nested folder to its last item*: a nested folder isn't
    backed by a workspace/hotseat `CellLayout` position - its container is
    its parent folder's id, not `CONTAINER_DESKTOP`/`HOTSEAT` - so the
    existing collapse path (built around `Launcher#getCellLayout`) either
    crashed or silently corrupted state. `LauncherDelegate.findParentFolder()`
    walks a folder icon's view ancestry for a `FolderPagedView` to detect
    this case, and `replaceNestedFolderWithFinalItem()` handles it by editing
    the parent folder's own contents list (`FolderInfo#add`/`#remove`)
    instead of the workspace grid, reusing the same listener-driven path
    every other nested-folder content change already goes through.
  - *Cross-folder drops*: dragging an item out of an already-open folder and
    dropping it directly onto a different, already-open folder (both can be
    open at once, unlike top-level folders) used stale/wrong state in two
    places - `Folder#onDrop` decided whether the dropped item was one of its
    own children purely from `mIsExternalDrag`, which only the spring-loaded
    auto-open path sets, so a folder that was already open before the drag
    started used a stale `mCurrentDragView`; and `mEmptyCellRank`, which only
    a delayed alarm keeps current during hover on *this* folder's own grid,
    could be badly out of range for the drop target once it came from
    somewhere else. Both now also check `d.dragSource != this` and recompute
    directly from the drop's own position instead of trusting leftover state
    from this folder's last unrelated drag.
  - *Hover state going stale between `onDragOver` and `onDrop`*: the last
    `onDragOver` hover result (which nested icon or merge target, if any,
    the drag was over) can be a frame stale by the time the finger actually
    lifts. Both the nested-subfolder handoff and the merge-create path in
    `onDrop` recompute the target directly from the drop's own position as a
    fallback rather than trusting only the cached hover state, so a drop
    that's still over the target isn't missed.
  - *Home-screen freeze after a merge-create drop*: `createNestedFolder()`
    does no fly-into-place animation of its own for the dragged item, unlike
    the normal add-to-folder path, so `DragObject#deferDragViewCleanupPostAnimation`
    (defaults to `true`) never got cleared - `DragController#endDrag()` skips
    removing the floating drag-shadow view and calling every drag listener's
    `onDragEnd()` while it's still set, freezing the home screen's rendering
    and touch handling until something else forced a re-layout. Fixed by
    setting it to `false` immediately in `createNestedFolder()`, the same way
    the existing pending-item branch of `onDrop` already does.
  - *Stuck in spring-loaded/edit mode after a merge-create drop*:
    `createNestedFolder()` returns before ever reaching the
    `mIsDragInProgress` reset and `goToState(NORMAL, ...)` call at the bottom
    of the normal `onDrop` flow, since it's called from an early-return
    branch. Added the same cleanup at the end of `createNestedFolder()`
    itself.

**App layer:**

- `FolderService.getFoldersFlow()` (the query that builds the drawer) now
  embeds each top-level folder's subfolder as its own `FolderInfo` item,
  appended after the plain apps, instead of flattening its apps into the
  parent. Recursing with no further children keeps this to exactly one level
  deep, matching the same depth guard enforced by `getNestableFolders()` (for
  the Settings path) and `getFolderIconAtRank()`/`isNested()` (for the
  drag-to-merge path) elsewhere, not by anything in this query.
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
  "None" to un-nest) - the Settings-side entry point alongside drag-to-merge.
- Search (`SearchTargetFactory` / `LawnchairAppSearchAlgorithm`): an app
  inside a nested subfolder now shows "In `<parent>` → `<folder>`" instead of
  just the immediate folder. Extends the existing single-level "In
  `<folder>`" subtitle from `feat/search-folder-label` rather than replacing
  it - a plain app in a top-level (non-nested) folder still shows exactly
  what it did before. Applies identically no matter which of the two paths
  created the nesting.

### Scope note (read before reviewing)

Manual drag-to-reorder is **not** extended to nested folders in this PR - a
nested folder's position relative to its own siblings under the same parent
isn't wired up (today, un-nesting is how you'd reposition one). Drag-to-merge
*creates* a nested folder but doesn't add general reordering of one once it
exists. That's `folder-manual-order` territory: a separate, later item in
this contribution plan that explicitly depends on this one landing first.

### Testing

DAO-level: inserted a folder, nested a second folder inside it via
`setParentFolder`, confirmed a third folder can't be nested into the
now-nested one (`setParentFolder` returns `false`), confirmed deleting the
parent also removes the child folder and its items
(`deleteFolderWithChildren`). Manually ran a full-device backup and restore
before/after this change and confirmed the `preferences` db file is now
present in the backup set.

UI, Settings path: created folder A, created folder B, nested B into A via
the "Nest inside" picker - B rendered indented under A in the Settings list
with a small folder icon next to A's title, and in the live app drawer,
opening A showed B as its own folder cell (tapping it opened B's own popup)
while A's closed icon showed the badge. Placed A on the home screen and
confirmed the same rendering (subfolder cell, badge) there too. Searched for
an app inside B - the result subtitle read "In A → B". Un-nested B via
"None" - back to top-level in both the Settings list and every surface it
appears on, badge gone from A. Deleted A while it still had a nested child -
both were removed (cascade delete).

UI, drag-to-merge path: placed folder C on the home screen, opened it,
dragged one app icon onto a second app icon already inside C - a brand-new
nested folder appeared in C's grid containing exactly those two apps, C
picked up the badge, and the merge completed with no freeze: the drag shadow
cleared immediately and the home screen stayed interactive (regression check
for the `deferDragViewCleanupPostAnimation` freeze). Confirmed the launcher
returned to normal state (not stuck spring-loaded/edit mode) right after the
merge. Repeated the same merge from the app drawer's own folder view.
Confirmed the depth guard on this path: opened the newly-created nested
folder and tried dragging one of its two items onto the other - no
second-level folder was created (rejected, since the open folder is itself
already nested); dragged a different top-level folder from the home screen
onto a sibling icon inside an open (non-nested) folder - also rejected, no
folder-in-a-merge-target scenario. Dragged an app onto the nested subfolder's
own icon (both from the home screen, and from within the already-open
parent) - added to the subfolder both times, no crash. Reduced a nested
folder down to its last item by removing its other contents - the folder
collapsed and the final item took its place directly in the parent, no
crash, no orphaned placeholder.

UI, moving/un-nesting via drag: nested B into A (Settings path), opened A,
long-pressed B's icon and dragged it out onto an empty part of the home
screen - B became a top-level home-screen folder again with no crash, and
A's badge cleared. Confirmed an app-drawer subfolder still refuses to start
a drag at all (unaffected by this fix, since `isInAppDrawer()` is true for
that case).

UI, cross-folder and race-condition checks: opened a nested subfolder inside
its already-open parent (both open at once) and dragged an item from the
subfolder directly onto the parent - no crash (previously crashed on a
stale `mCurrentDragView`/`mEmptyCellRank`). Dragged an item quickly across a
nested-subfolder icon and released right at its edge, deliberately trying to
outrun the hover state, to check the drop still lands correctly via the
position-recompute fallback rather than being dropped on the floor.

### Compatibility

Migration is additive and nullable - no data loss, no forced backfill for
existing installs. `MIGRATION_3_4` is registered
(`.addMigrations(MIGRATION_3_4)`). No behavior change for existing installs
with no nested folders - a plain top-level folder's rendering, preview, drag
behavior, and search subtitle are byte-for-byte the same as before this PR
when it has no subfolder, on either surface.

### Type of change

:sparkles: **New feature** (non-breaking; manual reordering of nested
folders is a known, separately tracked follow-up - see scope note above)
