### Description

Folders can be hard to tell apart from the background depending on the
chosen background color/theme and transparency, both closed (drawer/home
screen preview) and open. Adds a plain 1dp gray outline that stays
visible regardless of theme color or the folder background opacity
preference.

### Reasoning

- **Closed folder preview** (`PreviewBackground.java`): the stroke-drawing
  code already existed - `drawBackgroundStroke()`/`animateBackgroundStroke()`
  already track stroke alpha separately from the background's own alpha,
  so it was already opacity-independent - it was just switched off via
  `DRAW_STROKE = false`. Flipped it on and pointed `mStrokeColor` at a new
  neutral `ColorTokens.FolderOutlineColor` instead of the theme-derived
  `FolderIconBorderColor`, so the outline reads consistently across themes.
- **Open folder background** (`Folder.java`): had no outline at all.
  Rather than adding a stroke directly to the existing `mBackground`
  `GradientDrawable` (which gets `.setAlpha()`'d for the background-opacity
  preference and would wash the outline out along with it), this draws a
  second, separate `GradientDrawable`
  (`DrawableTokens.RoundRectFolderOutline`: same shape/corner radius as
  `RoundRectFolder`, transparent fill, just a stroke) on top of it.

### Testing

Verified the outline is visible around both closed folder previews (in
the drawer and on the home screen) and the open folder, at every
background-opacity setting from fully opaque to fully transparent, and
doesn't change color with the Material You theme.

### Compatibility

No data model or public API changes. Purely visual.

### Type of change

:white_check_mark: **New feature** (A non-breaking change that adds functionality)
