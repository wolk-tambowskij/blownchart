### Description

Related to #6495 (that issue was filed and closed as not-planned against
16-dev; this fixes the same underlying bug, verified independently
against 15-dev - worth re-checking whether it's still reproducible on
16-dev before assuming this also closes it there). Folder backgrounds
and preview-item layout didn't correctly follow the user's configured
icon shape: some shapes rendered as a plain circle, others were
approximated to the wrong generic preset, and preview icons could
render past the folder's actual (non-circular) outline.

### Reasoning

Four separate bugs, all rooted in the same area of code:

- `IconShape#pickBestShape` (the framework-level shape delegate used
  for folder backgrounds and reveal animations) approximated the
  user's configured shape by picking whichever of 4 hardcoded presets
  (Circle/RoundedSquare/TearDrop/Squircle) had the smallest area of
  divergence from the OS's adaptive icon mask. This can both miss a
  shape entirely and mismatch others - e.g. Diamond rendered as a
  plain square. `AdaptiveIconShape`, an existing but previously unused
  delegate, already wraps the user's *exact* configured shape from
  Lawnchair's own icon-shape preference - no approximation needed.
  `pickBestShape` now uses it directly, and the preset-matching
  machinery it made obsolete (`getAllShapes`, `getShapeDefinition`,
  and the `RoundedSquare`/`TearDrop`/`Squircle` shape classes) is
  removed.
- `FourSidedCookie`/`SevenSidedCookie`/`Arch` (the Material 3
  Expressive shape presets) use `Corner.fullArc` placeholder corners,
  since they render from custom path data instead of the normal
  corner-based path builder. `IconShape#addShape`'s `isCircle` check
  compares all four corners against `Corner.fullArc` - true for these
  three by coincidence of their placeholder values - so it rendered a
  plain circle instead of ever calling their overridden `addToPath`.
  Each now overrides `addShape` directly to bypass that check.
- `IconShapeManager`'s system-icon-shape detection read
  `AdaptiveIconDrawable(null, null).iconMask` without calling
  `setBounds()` first, so the mask (and every comparison against it)
  was geometrically degenerate, and separately compared that mask in
  a 100x100 box against candidate presets sized for 200x200 - two
  independent reasons the "System" shape option didn't actually match
  the OS's real icon shape.
- `ClippedFolderIconLayoutRule` spaced preview items assuming a
  circular icon boundary, so for some item counts (3, and two of the
  four in the 4-item case) an item's edge could render past the
  folder's actual outline for any shape tighter than a circle in that
  direction. It now probes the real configured shape's `Region` at
  each item's exact angle via binary search and clamps to it, instead
  of assuming a fixed shape.

### Testing

Verified for every built-in icon shape (Circle, Square, RoundedSquare,
Squircle, Sammy, Teardrop, Cylinder, Cupertino, Octagon, Hexagon,
Diamond, Egg, the two Cookie presets, Arch, and System) that:

- The folder background shape visually matches the selected icon shape
  (previously several of these were wrong).
- Preview icons stay within the folder's outline for 1-4+ items.
- Shapes that were already rendering correctly before this change
  (Circle, Square, RoundedSquare, Squircle) are visually unchanged.

### Compatibility

No data model or public API changes. `ClippedFolderIconLayoutRule.init()`
gained a `Context` parameter (its one caller, in `PreviewItemManager`,
is updated in the same commit).

### Type of change

:white_check_mark: **Bug fix** (A non-breaking change that fixes an issue)
