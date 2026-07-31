### Description

Search results for an app don't currently show anything about it being
inside a drawer folder. When the top (and only) match also has a row of
shortcuts shown below it, that expanded row can now show "In &lt;folder
name&gt;" as its subtitle.

### Reasoning

`LawnchairAppSearchAlgorithm` now keeps a lightweight
`componentKey -> folder title` snapshot, refreshed via
`FolderService.getFoldersFlow()` whenever folders change (same pattern
already used for the `hiddenApps`/`enableFuzzySearch`/etc. snapshots in
this class). `SearchTargetFactory.createAppSearchTarget()` takes an
optional `folderTitle` and, when present, attaches a `SearchActionCompat`
whose only purpose is to carry that as a subtitle - `SearchResultIconRow`
already renders `target.searchAction?.subtitle` when present, so no
view-layer changes were needed for this specific surface.

**Scope note - please read before merging:** search results are shown
through two different view types depending on the `LayoutType`:

- `LayoutType.SMALL_ICON_HORIZONTAL_TEXT` (`SearchResultIconRow`) - a
  row with title + subtitle. This PR's subtitle reaches this view. It's
  only used for the single-top-match-with-shortcuts case, and for
  `LawnchairLocalSearchAlgorithm` (the alternate `LOCAL_SEARCH` engine,
  off by default), which isn't wired up in this PR.
- `LayoutType.ICON_SINGLE_VERTICAL_TEXT` (`SearchResultIcon`) - the
  common case for multiple app matches, a plain icon+title grid cell
  with **no subtitle slot at all**. Showing the folder there needs an
  actual UI addition to that view (a small badge or second line), not
  just data plumbing, and I'm leaving that as a follow-up rather than
  guessing at layout changes I can't visually verify here.

So as submitted, this is a real but narrow slice of the original idea -
happy to follow up with the `SearchResultIcon` UI change as a separate
PR once this shape is agreed on, or fold it in here if you'd rather see
the complete picture in one PR.

### Testing

Manually verified: searching for an app that's inside a drawer folder,
when it's the sole match and shows a shortcuts row, displays "In
&lt;folder name&gt;" under its title. Apps not in any folder are
unaffected (no subtitle, same as before). Apps in a folder but shown in
the plain grid (multi-match case) are unaffected by this PR, per the
scope note above.

### Compatibility

`SearchTargetFactory.createAppSearchTarget()` gained an optional third
parameter with a default value - not a breaking change for any other
caller.

### Type of change

:white_check_mark: **New feature** (A non-breaking change that adds functionality)
