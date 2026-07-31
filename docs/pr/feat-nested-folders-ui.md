### Description

Second of the nested-folders PR chain. Builds on
`feat/nested-folders-data-model` (the `parentFolderId` column and
`FolderDao`/`FolderService` support - this PR is meaningless without
it, and this branch includes its commit too). Lets a user actually
nest/un-nest a folder, and manage a nested folder independently, from
the "App drawer folders" Settings screen.

### Reasoning

- `FolderEditSheet` gets a "Nest inside" picker, offering every other
  top-level folder as a valid target, plus "None" to un-nest. A folder
  that already has children of its own offers no targets at all -
  nesting it further would push those children two levels deep, which
  isn't supported (see `FolderService.getNestableFolders`).
- The folder list (`AppDrawerFoldersPreference`) now shows every
  folder flat, including nested ones, instead of only top-level ones -
  a nested folder's row shows "nested in `<parent title>`" so it stays
  independently reachable to rename, delete, or un-nest without relying
  on a drag gesture in the live drawer.
- `FolderService` gets two flows instead of one: `getFoldersFlow()`
  (top-level only, for the actual drawer) and the new
  `getAllFoldersFlatFlow()` (every folder + its parent id, for this
  Settings screen). Before this PR both consumers shared one flow that
  returned literally every row - harmless while nesting didn't exist
  yet, but once it does, feeding every folder (nested ones included)
  straight into the drawer-building code would render a nested folder
  as its own second top-level icon, defeating the point of nesting it.

### Scope note (read before reviewing)

This PR does **not** yet render a nested folder as its own icon inside
its parent's open folder view in the live app drawer - that needs
deeper `Folder`/`PreviewItemManager` UI work in `com.android.launcher3`
that I don't have a reliable way to verify without on-device visual
testing, so I'm not guessing at it here. In the meantime,
`FolderService.getFoldersFlow()` merges a nested folder's apps into its
parent's contents when building the drawer, so nesting a folder never
makes its apps silently disappear from the drawer - they're just not
visually grouped under a second-level icon yet. Happy to follow up with
that once this lands, or take direction on it if a maintainer has a
preferred approach for representing a nested folder inside
`FolderInfo.contents` (an `ItemInfo` subtype check in the relevant
`BubbleTextView`/icon-binding code, most likely).

### Testing

Manually walked through: created folders A and B, nested B inside A
(the picker offers A as a target) - B's row then shows "nested in A".
Nested a third folder C into A too - A now has two children, B and C.
Opening A's own picker offers no targets, since A has children (nesting
it would push B and C two levels deep). Opening B's picker no longer
lists C, since only top-level folders are offered and C already has a
parent (A) - it only lists A itself (harmless to reselect) and any
other unrelated top-level folder. Un-nested B via "None" - its row goes
back to showing no parent subtitle. Deleted A - B and C are both
removed along with it (cascade delete from the PR 1 data-model change).

### Compatibility

No schema change in this PR (that was PR 1). No behavior change for
existing installs with no nested folders - `getFoldersFlow()`'s
top-level-only query returns exactly the same set of folders as before
when nothing is nested.

### Type of change

:sparkles: **New feature** (non-breaking; drawer-icon rendering for a
nested folder is a known, separately tracked follow-up - see scope note
above)
