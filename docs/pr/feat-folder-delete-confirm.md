### Description

Tapping the delete icon next to a folder in the app-drawer folder
management screen removed the folder immediately, with no way to back
out. Adds a confirmation bottom sheet before the delete actually
happens, and makes clear in the confirmation text that deleting a
folder does not delete the apps inside it - they simply return to the
app drawer as regular entries.

### Reasoning

- `AppDrawerFoldersPreference`'s per-item delete handler previously
  called straight through to `onDeleteFolder` (and the folder-order
  list cleanup that goes with it) as soon as the delete icon's
  `IconButton` was tapped.
- Split that into two pieces: `performDeleteFolder`, which keeps doing
  exactly what the old inline lambda did (strip the folder from
  `drawerListOrder` and invoke `onDeleteFolder`), and
  `confirmDeleteFolder`, a new wrapper that opens a
  `ModalBottomSheetContent` (the same bottom-sheet primitive already
  used for `FolderEditSheet` in this file) with a Cancel/Delete button
  pair. `FolderItem`'s `onItemDelete` now points at
  `confirmDeleteFolder` instead of the old inline lambda;
  `performDeleteFolder` only runs if the user taps Delete in the
  sheet.
- Two new strings: `delete_folder_confirm_title` (interpolates the
  folder's own name) and `delete_folder_confirm_message`. The existing
  `action_delete` and `android.R.string.cancel` strings cover the
  buttons, matching the Cancel/confirm pattern already used elsewhere
  in this screen (`FolderEditSheet`'s Cancel/OK pair).

### Issue

No matching upstream issue found. Searched
`LawnchairLauncher/lawnchair`'s issue tracker for "delete folder
confirmation", "accidental folder delete", "confirm before deleting a
folder", and related phrasing - no open or closed report asking for
(or describing being burned by the lack of) a confirmation step before
folder deletion turned up. This is an unsolicited protective
improvement, not tied to a filed report.

### Testing

Verified: tapping the delete icon on a folder in Settings > App
drawer > Folders now opens a bottom sheet naming the folder and
stating that its apps aren't deleted, with Cancel and Delete buttons.
Cancel (or dismissing the sheet) leaves the folder and its position in
the list untouched. Delete removes the folder and updates
`drawerListOrder` exactly as before this change. Apps that were inside
the deleted folder reappear as normal top-level entries in the app
drawer, unaffected by the deletion.

### Compatibility

Purely additive UI change - no preference schema or data model
changes. `FolderInfo`/`FolderDao`/`FolderService` and the
`drawerListOrder` preference format are untouched; existing folders
and their saved order are unaffected.

### Dependency note

This branch is **fully standalone** - it targets the plain,
non-nested folder-preferences screen as it exists on `15-dev` at
`v15.0.0-beta3.0` (no `parentFolderId`/nesting concepts anywhere in
this file at that point) and does not depend on
`feat/nested-folders-ui` (#10). The confirmation message is
deliberately worded as "this will not delete the apps inside it" -
not "any nested subfolders will also be deleted" - because nesting
isn't present in this branch's base and the confirmation is fully
meaningful without it. If this branch is ever rebased on top of #10,
the message should be revisited to also mention nested-subfolder
cascade deletion, matching the fork's own (branded) version of this
fix.

### Type of change

:white_check_mark: **New feature** (no prior confirmation step existed
to restore - this is new protection against an accidental destructive
action that was previously one tap away with no way to undo it)
