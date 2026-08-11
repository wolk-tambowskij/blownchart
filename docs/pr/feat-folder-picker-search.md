### Description

Finding a specific app to add to (or remove from) a drawer folder means
scrolling through the full, potentially long, app list - there's no way
to search. Adds a search field to the top of that screen, and to the
hidden-apps selection screen (`HiddenAppsPreferences`), which has the
exact same problem for the exact same reason - both are just checkbox
lists over the full installed-app set with no way to narrow it down.

### Reasoning

A case-insensitive substring match on the app label. On the folder
picker, this combines with the existing "hide apps already in another
folder" filter (both conditions apply together - an app must match the
search query *and* pass the duplicate filter to show up); on the
hidden-apps screen there's no such second filter, so it's just the
search match on its own. Both fields are a fixed header above the
respective (still reorderable/checkbox-toggleable) app list, styled
identically - the same rounded search bar, the same placeholder string
(`all_apps_search_bar_hint`). The hidden-apps screen's overflow menu
(select-all/invert/reset) is left operating on the full app list,
unfiltered by search, matching its pre-existing behavior.

### Testing

Verified on both screens: typing filters the list live, matches are
case-insensitive, clearing via the trailing X button (or the field
going empty) restores the full list. On the folder picker specifically,
confirmed the existing "filter duplicates" toggle and reordering still
work correctly together with an active search query.

### Compatibility

No data model or public API changes. Purely additive UI.

### Type of change

:white_check_mark: **New feature** (A non-breaking change that adds functionality)
