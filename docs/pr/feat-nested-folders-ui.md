### Description

Second/third combined step of the nested-folders PR chain. Builds on
`feat/search-folder-label` and `feat/nested-folders-data-model` (this
branch contains both of their commits too - it's meaningless without
the `parentFolderId` schema, and extends the existing "which folder is
this app in" search subtitle rather than duplicating it). Delivers the
full, user-visible nesting experience: creating/managing a nested
folder from Settings, seeing it rendered as its own icon inside its
parent's open folder view in the live drawer, a badge cue on the
parent's closed icon, and a "parent → folder" search subtitle for an
app inside a nested subfolder.

### Reasoning

**Launcher3 (AOSP) side** - a subfolder is just a `FolderInfo` added as
an item inside another `FolderInfo`'s contents, the same way an app
pair is already a second "collection" item type:

- `Folder.willAcceptItemType()`: accepts `ITEM_TYPE_FOLDER` so
  `FolderInfo.add()` no longer throws for a nested folder item.
- `FolderPagedView.createNewView()`: renders a subfolder cell via
  `FolderIcon.inflateFolderAndIcon()` with a new `folder_subfolder.xml`
  layout (sized as one grid cell, like `folder_app_pair.xml`, not like
  `all_apps_folder_icon.xml`'s drawer-row sizing). Tapping it opens its
  own `Folder` popup for free through the existing `FolderInfo` click
  handling - no changes needed there.
- `FolderIcon.getPreviewItemsOnPage()`: excludes subfolders from the
  parent's own closed-icon mini-preview, since a subfolder has no
  static preview `Drawable` of its own yet and would NPE in
  `PreviewItemManager#setDrawable`.
- `FolderIcon.drawNestedFolderBadge()`: draws a small, solid,
  fixed-color folder glyph (new `ic_folder_badge.xml`) straddling the
  bottom-right edge of a parent folder's closed icon when it contains a
  subfolder - centered on the inset edge (not tucked fully inside it),
  so the background's own border passes through the middle of the
  badge instead of running along its edge, which would make a
  solid-colored badge hard to tell apart from the border itself.

**App layer:**

- `FolderService.getFoldersFlow()` (the query that builds the actual
  drawer) now embeds each top-level folder's subfolder as its own
  `FolderInfo` item, appended after the plain apps, instead of
  flattening its apps into the parent. Recursing with no further
  children keeps this to exactly one level deep, enforced by
  `getNestableFolders()` only offering childless folders as valid
  nesting targets elsewhere, not by anything in this query.
- `LawnchairAlphabeticalAppsList`: the folder-rebuild loop now
  re-resolves a subfolder's own apps against the live `AllAppsStore`
  the same way as its parent, instead of trying (and silently failing -
  a `FolderInfo` has no `componentKey`) to resolve the subfolder itself
  that way.
- `AppDrawerFoldersPreference` (Settings): the management list now
  shows every folder, nested ones included - a nested folder renders
  directly under its parent, indented 24dp (matching
  `FolderPagedView`'s own reserved drag-handle width, so the indent
  reads as "more indented than its parent" rather than "shifted left of
  it"), with a small folder icon next to a parent's title when it has a
  subfolder. `FolderEditSheet` gets a "Nest inside" picker (offering
  every other top-level, childless-of-its-own-nesting folder as a
  target, or "None" to un-nest).
- Search (`SearchTargetFactory` / `LawnchairAppSearchAlgorithm`): an
  app inside a nested subfolder now shows "In `<parent>` → `<folder>`"
  instead of just the immediate folder, so the top-level context isn't
  lost. Extends the existing single-level "In `<folder>`" subtitle from
  `feat/search-folder-label` rather than replacing it - a plain app in
  a top-level (non-nested) folder still shows exactly what it did
  before.

### Scope note (read before reviewing)

Manual drag-to-reorder is **not** extended to nested folders in this
PR - a nested folder's position relative to its own siblings under the
same parent isn't wired up (today, un-nesting is how you'd reposition
one). That's `folder-manual-order` territory: a separate, later item in
this contribution plan that explicitly depends on this one landing
first, not something this PR should also be trying to do.

### Testing

Manually walked through: created folder A, created folder B, nested B
into A via the "Nest inside" picker - B now renders indented under A in
the Settings list with a small folder icon next to A's title, and in
the live app drawer, opening A shows B as its own folder cell (tapping
it opens B's own popup) while A's closed icon shows the badge in its
corner. Searched for an app that's inside B - the result subtitle read
"In A → B". Un-nested B via "None" - it's back to being its own
top-level folder in both the Settings list and the drawer, badge gone
from A. Deleted A while it still had a nested child - both were removed
(cascade delete from the data-model PR).

### Compatibility

No schema change in this PR (that was the data-model PR). No behavior
change for existing installs with no nested folders - a plain top-level
folder's rendering, preview, and search subtitle are byte-for-byte the
same as before this PR when it has no subfolder.

### Type of change

:sparkles: **New feature** (non-breaking; manual reordering of nested
folders is a known, separately tracked follow-up - see scope note
above)
