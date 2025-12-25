Lawnchair 15 Beta 2 is now available! This is a stability-focused release that addresses numerous bugs from Beta 1, while adding a few quality of life improvements.

Note that this release only supports QuickSwitch from Android 10 to Android 15 QPR0 (initial release).

This release includes the following improvements:
* Redesigned the launcher search backend infrastructure, with a cleaner UI for settings
  * Replace Mull search provider with IronFox (#5780)
  * Fix launch intent for Kagi search provider (#5800)
* Added support for infinite scrolling in home screen (#5807);
* Added support for re-ordering apps inside app drawer folders (via settings) (#6173)
* Added an option in Settings to clear all items from the home screen (#6125)
* Added an option to turn off the alpha change behind the search bar when scrolling in the app drawer (#5934)
* Improved the layout of the About screen, and added a changelog viewer for Nightly builds (##5711)

Alongside that, several issues have also been fixed:
* Fixed an issue that caused the search widget to fail to launch the Google app on Android 14 and above. 
* Addressed multiple crashes on older Android versions. 
* Resolved a race condition that could cause a crash when customizing an app that was being uninstalled.
* Corrected various visual bugs, including:
  * "Customize" bottom sheet not updating state
  * Search state would not clear when app drawer is hidden (#5933)
  * Wrong icon scaling in app drawer when home screen icons are resized (#5932)
  * Home screen page indicator being partially displayed (#5937)

Alongside that, this release contains the usual performance improvements, miscellaneous bug fixes, dependency updates, and new bugs & translations.

Thanks as well to our new contributors: @lebao3105, @IzzySoft, @foXaCe, @itsaky, @victor-marino, @garghimanshu0786, @VBansal99, @Chaikew, and @abhixv

[Support Lawnchair's development by donating on Open Collective](https://opencollective.com/lawnchair)
