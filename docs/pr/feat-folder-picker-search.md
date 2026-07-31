### Description

Finding a specific app to add to (or remove from) a drawer folder means
scrolling through the full, potentially long, app list - there's no way
to search. Adds a search field to the top of that screen.

### Reasoning

A case-insensitive substring match on the app label, combined with the
existing "hide apps already in another folder" filter (both conditions
apply together - an app must match the search query *and* pass the
duplicate filter to show up). The field is a fixed header above the
(still reorderable, still checkbox-toggleable) app list, styled to
match the drawer's own rounded search bar.

### Testing

Verified: typing filters the list live, matches are case-insensitive,
clearing via the trailing X button (or the field going empty) restores
the full list, and the existing "filter duplicates" toggle and
reordering still work correctly together with an active search query.

### Compatibility

No data model or public API changes. Purely additive UI.

### Type of change

:white_check_mark: **New feature** (A non-breaking change that adds functionality)
