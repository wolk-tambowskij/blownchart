# Third-Party Code

This document lists source and asset files in this repository that originate
from projects other than Lawnchair/BlownChart itself, as found by scanning
the tree for copyright headers, license files, and common attribution
markers (`Copyright`, `@author`, `Omega`, `saggitt`, `Till`, `LICENSE`).

Everything below was inherited from upstream Lawnchair; none of it was
added by the BlownChart fork. All licenses found are compatible with
GPLv3 redistribution, and all original copyright notices have been left
untouched in the affected files.

| File(s) | Source | License |
|---|---|---|
| `src/`, `src_no_quickstep/`, `res/`, `quickstep/`, and other root-level AOSP-derived modules | The Android Open Source Project (Launcher3) | Apache License 2.0 |
| `lawnchair/` (bulk of the module; original Lawnchair application code) | [Lawnchair](https://github.com/LawnchairLauncher/lawnchair) | GPLv3 (some older files retain their original Apache-2.0 headers, unmodified) |
| `lawnchair/src/app/lawnchair/icons/shape/IconShapeManager.kt`, `IconShape.kt`, `IconCornerShape.kt` | Lawnchair, originally authored by paphonb@xda | GPLv3 |
| `lawnchair/res/drawable/ic_unlocked_recents.xml`, `ic_locked_recents.xml` | [LineageOS](https://github.com/LineageOS) | Apache License 2.0 |
| `lawnchair/src/app/lawnchair/search/algorithms/data/calculator/Expressions.kt` | Keelar, [ExpressionsEvaluator](https://github.com/Keelar), vendored with its own `LICENSE` file in the same directory | MIT License |

## Notes

- No files matching an Omega/Neo Launcher, saggitt, or Till attribution
  pattern were found in the current tree.
- The Apache-licensed AOSP tree and the GPLv3 Lawnchair tree are documented
  together with the fork's own modifications in [`NOTICE`](NOTICE).
- If you add code copied or adapted from another project, add a row here
  and keep that project's copyright header and license text intact in the
  file(s) you add.
