### Description

Locking the home screen (`Lock home screen` toggle) is meant to prevent
accidental changes to it, but three ways to change it weren't actually
gated:

1. An app could still be uninstalled from the long-press menu, on any
   surface (home screen, hotseat, folder, or drawer).
2. A new widget could still be dragged from the widget picker onto the
   home screen.
3. An existing widget's resize frame could still be shown/used.

### Reasoning

Locking the home screen already hid rename/hide/icon-change for drawer
icons via the `CUSTOMIZE` shortcut, and already blocked dragging
existing items around (workspace drags all funnel through
`Workspace.beginDragShared()`'s own lock check). These three gaps had
each fallen through a different, separate code path that never
consulted the flag at all:

- `LawnchairShortcut.UNINSTALL` never checked `lockHomeScreen` - now
  does, right alongside the existing system-app check.
- `ItemLongClickListener.onWidgetItemLongClick()` (the widget-picker's
  own drag-start path, separate from `Workspace`'s) never checked it -
  now does, and returns `false` (refusing the drag) when locked.
- `AppWidgetResizeFrame.showForWidget()` only avoided showing while
  locked as an incidental side effect of its two call sites (drag-drop
  completion, and right after placing a new widget) already being
  blocked elsewhere - added a direct check so resize stays blocked
  even if either of those upstream paths changes later.

### Testing

With `Lock home screen` enabled, verified: long-pressing an app icon
on the home screen, hotseat, in a folder, and in the drawer no longer
offers (or silently no-ops) `Uninstall`; dragging a widget from the
widget picker onto the home screen is refused; and long-pressing an
already-placed widget doesn't show its resize handles. Disabling the
lock restores all three immediately.

### Compatibility

No data model or public API changes - only tightens an existing lock
to actually cover cases it was already supposed to.

### Type of change

:white_check_mark: **Bug fix** (A non-breaking change that fixes an issue)
