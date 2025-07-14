> [!TIP]
> For the story behind this release, see [the announcement](https://lawnchair.app/blog/lawnchair-15-beta-1) on our website.

Lawnchair 15 Beta 1 is a foundational release based on Launcher3 from Android 15. This version works with QuickSwitch from Android 10 to Android 15 QPR1. Higher Android versions are not yet supported.

### New Features
*   **Android 15 Support:** Includes core platform features like Private Space and App Archiving.
*   **App Drawer Folders:** A major new way to organize your app drawer.
    *   **Manual Folders:** Create, edit, and re-arrange your own custom folders.
    *   **Automatic Organization ("Caddy"):** An experimental feature to automatically categorize your entire app drawer into smart folders.
*   **Dock Enhancements:**
    *   Add a background to the dock with options for color and corner radius.
    *   Place widgets directly in the dock.
    *   Show icon labels for apps in the dock.
*   **Wallpaper Carousel:** A new pop-up menu item to quickly switch between your current and recent wallpapers, similar to the Pixel Launcher.
*   **App Pausing:** For rooted users with QuickSwitch, you can now manually pause applications directly from the launcher.
*   **Expanded Search Options:**
    *   Add custom search engines for web suggestions in the app drawer.
    *   New web search providers added, including Ecosia, Kagi, Firefox, Iceraven, and Mull.
*   **"Deck" (Experimental):** An initial implementation of a "no app drawer" mode. *Please create a launcher backup before trying this feature to prevent data loss.*

### Improvements
*   **UI:** Updated many UI components to better align with Material 3 design principles.
*   **Gestures:** Added "Open Recents Screen" and "Open Assistant" as new gesture actions.
*   **Pop-Up Menu:** The long-press menu options can now be reordered.
*   **Settings:** Reorganized many settings for a more intuitive experience and centralized all search-related settings into a single screen.

### Core & Under-the-Hood
*   **Type-Safe Navigation:** The settings infrastructure has been rewritten using modern Jetpack Compose Navigation for enhanced stability.
*   **Build & Dependency Updates:** Major updates to dependencies and build scripts improve performance and maintainability.
*   **New Translations:** Translations have been updated from Crowdin.
*   **Nightly Builds:** A formal nightly build system is now in place for easier access to development versions.
*   **Crash & Bug Fixes:** Implemented numerous fixes for various OEM skins (Lenovo, Motorola), custom ROMs, and older Android versions.

### Regressions & Known Issues
*   **Icon Badges:** Icon badges for work profile apps are temporarily non-functional due to core changes in the A15 rebase. This is a high-priority item for a future update.
*   **'Customize Icon' State:** The bottom sheet for customizing an icon may not update its state immediately. Restarting the launcher will apply the change.
*   **App Drawer folders:** As of now, you can't edit app drawer folders from the app drawer. Please visit the settings screen to change the contents of each folder.

Other issues that you may encounter can be found at [our FAQ](https://lawnchair.app/faq/#common-issues).

### Community & Thanks
This release marks a new chapter in how we engage with our community. We recently formed the **Lawnchair Triage Team**, a group of dedicated volunteers who have already begun the massive task of organizing our issue tracker. Their early efforts have been invaluable in helping us focus development.

Thanks as well to all the people who have [donated to our Open Collective](https://opencollective.com/lawnchair) and [submitted translations on Crowdin](https://lawnchair.crowdin.com/).

And, as always, a huge thanks to all our code contributors for this cycle: @validcube, @Morty0Smith, @benjaminkitt, and @tgex0
