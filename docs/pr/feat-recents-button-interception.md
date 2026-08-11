### Description

On firmware where `config_recentsComponentName` is hardcoded to a
different app's `RecentsActivity` (typically a vendor/OEM overlay
that overrides Lawnchair as the system's Recents/Overview provider),
invoking Recents directly from the launcher's own resumed context -
either via the physical Recents button/gesture, or
`performGlobalAction(GLOBAL_ACTION_RECENTS)` called from within
Lawnchair itself (`RecentsGestureHandler`) - makes the OS flash that
other app's Recents screen and immediately fall back to whatever was
open before, as if a stray back press had dismissed it. The user sees
Recents "not working" at all.

No existing upstream issue was found describing this specific
firmware-hardcoded-provider symptom; this was found and fixed against
real hardware exhibiting it. Filed as a feature, not a fix: Recents
never worked via the button/gesture on this class of firmware in
Lawnchair (or any launcher invoking it the same way) to begin with, so
making it work is new capability, not the restoration of behavior that
regressed from some prior working state.

### Reasoning

Two distinct problems, one root cause each:

1. **The physical button/gesture path is broken entirely** on
   affected firmware, since the OS routes the button press directly
   to `config_recentsComponentName`'s activity - bypassing Lawnchair's
   own gesture handling - and that activity's own rendering is what's
   broken.
2. **`RecentsGestureHandler`'s own invocation is also broken** on the
   same firmware, for a related but distinct reason: calling
   `performGlobalAction(GLOBAL_ACTION_RECENTS)` directly from the
   launcher's own resumed activity apparently makes the OS treat the
   transition as equivalent to a double-tap-to-dismiss, rather than a
   single open - confirmed by testing that a real, unassisted physical
   double-press of the Recents button *does* open it correctly on the
   same device, while a single press (or a single
   `performGlobalAction` call) does not.

Both are worked around by never invoking `GLOBAL_ACTION_RECENTS` from
the launcher's own context - instead, `LawnchairAccessibilityService.bounceToRecents()`
briefly resumes the real, already-existing task of whichever app the
user had open last, then fires `GLOBAL_ACTION_RECENTS` from *there*.
Two earlier approaches were tried and abandoned before landing on this
one - both are worth understanding, since either could plausibly work
better on a different vendor's firmware and either is a natural next
thing to reach for if this one regresses on some device:

- **A transparent stand-in `Activity`.** A `taskAffinity=""`,
  `excludeFromRecents`, `noHistory` activity that briefly became the
  resumed foreground activity - a plain, non-launcher app, from the
  OS's perspective - then fired `GLOBAL_ACTION_RECENTS` itself, a beat
  later. This worked, but a real `Activity` means a real `Task`, and on
  this firmware that task could be left behind as its own independently
  swipeable ghost/duplicate card in Recents - see "Rejected: the
  bounce-activity ghost card" below for the full account of that dead
  end, since diagnosing it produced two escalating rounds of fixes
  before the whole approach was abandoned as fundamentally unclean.
- **A focusable `TYPE_ACCESSIBILITY_OVERLAY` window.** Once the ghost
  card made the Activity approach clearly unworkable, the next idea was
  a window that could grab real window focus (confirmed via logcat:
  `WindowManager: Changing focus from...to...`) without creating any
  `Task` at all - no Task, no possible ghost card, by construction. This
  did not work on real hardware: `WindowManagerService`'s focus
  bookkeeping for an accessibility-overlay window apparently isn't
  equivalent, at the `ActivityTaskManager` level, to a real app actually
  being resumed - the dismiss-misinterpretation bug persisted.
- **Resuming the real last-used app (current approach).** Since neither
  a fake `Activity` nor a fake focus change was "real" enough to the OS,
  the working fix stopped pretending: resume an app the user *actually*
  had open, using `FLAG_ACTIVITY_REORDER_TO_FRONT` on its existing task
  (not a new one, so nothing new is added to Recents - no Task, no
  possible ghost card, same guarantee the overlay attempt was going
  for, without its focus-bookkeeping problem), then fire
  `GLOBAL_ACTION_RECENTS` a short settle-delay later. There is nothing
  fake about it from the OS's perspective, since it's a genuine resumed
  app.

`findRecentForegroundPackages()` queries `UsageEvents` (the raw event
stream, not `UsageStatsManager`'s daily-bucketed aggregate query, since
only the former preserves correct chronological ordering) for the most
recent `ACTIVITY_RESUMED`/`MOVE_TO_FOREGROUND` events, excluding this
launcher's own package, `com.android.systemui`, and the system Recents
provider's own package (`LawnchairApp.systemRecentsComponentName`,
read fresh per call - see "Follow-up: HOME after Recents broke the next
invocation" below for why that last exclusion exists) and any package
recently confirmed dismissed from Recents (see "Follow-up: swiping the
last app away, or 'clear all', resurrected it anyway" below). Falls
back to firing `GLOBAL_ACTION_RECENTS` directly - the original,
occasionally-misbehaving behavior - when there's no eligible app to
resume: freshly booted device, all recent history purged, or
"Usage access" not granted (see Compatibility).

For the physical-button path specifically, since the OS routes that
press away from the launcher entirely, there's no window-state event
Lawnchair can react to in time - by the time any accessibility event
fires, the OS's own broken rendering attempt has often already begun.
`LawnchairAccessibilityService` (previously an inert skeleton -
`eventTypes = 0` - every Lawnchair build already declares this service
and prompts to enable it for other accessibility-dependent features,
like Double-Tap-to-Sleep) watches `TYPE_WINDOWS_CHANGED` system-wide,
locates the Recents button's on-screen bounds in the navigation bar (by
view ID first, falling back to a content-description search for
firmware that renamed/dropped the standard AOSP id), and keeps an
invisible `TYPE_ACCESSIBILITY_OVERLAY` view positioned exactly over it
- purely to intercept the *touch* before SystemUI's own (broken) button
handling ever runs, not to grab focus the way the rejected
focus-grabbing overlay tried to (a `FLAG_NOT_FOCUSABLE` window,
unrelated to that dead end beyond sharing a window type). A tap on that
overlay calls `bounceToRecents()` the same way the gesture path does.
This only applies in 3-button navigation mode - gesture navigation has
no on-screen button to overlay, so the overlay is torn down (and
nothing is watched) when gesture nav is active.

Both paths funnel through the same method and share one
self-trigger-cooldown timestamp (`LawnchairApp.lastRecentsSelfTriggerAtMs`),
since `bounceToRecents()`'s own `GLOBAL_ACTION_RECENTS` call is itself
a window-state change that could otherwise be mistaken for a fresh
press.

The whole mechanism is opt-in via a new "Recents interception" setting
(`recentsButtonInterception`, default off) in Quickstep preferences,
since it depends entirely on this firmware quirk existing at all -
harmless but pointless overhead on firmware where Recents already
works correctly.

#### Rejected: the bounce-activity ghost card

Kept here because diagnosing it is instructive, even though none of
this code exists in the current implementation. Once the transparent
`Activity` approach (described above) was working, real-device testing
surfaced a second, distinct problem caused by the same firmware quirk:
the bounce activity itself, despite being transparent and only on
screen for well under a tenth of a second, could be left behind as its
own separate, independently swipeable card in the Recents list -
alongside, not instead of, the real Recents screen. That ghost card
showed a live snapshot of whatever was drawn behind the bounce activity
but labeled with this app's own launcher icon, reading as a confusing
duplicate; tapping it (after its task was already torn down) resurrected
a dead, non-interactive instance. Testing confirmed this firmware
honors neither `excludeFromRecents` nor `setRecentsScreenshotEnabled`,
and a Recents card cannot be force-removed at all on Android 11+ once
created - so a first round of fixes (blank task icon, unconditional
`FLAG_SECURE`, a stale-resurrection timestamp check redirecting a
fresh-instance ghost-tap home) turned out insufficient on retest: the
card was still occasionally visible with live content, and this
firmware sometimes resumed the *same, still-alive* instance instead of
creating a fresh one, which the timestamp check couldn't see. A second
round (content-swap to a placeholder color in `onPause()`, a same-instance
`hasTriggeredRecents` stale-tap detector in `onResume()`) fixed that,
but then surfaced a genuine lifecycle bug of its own - evidenced by
`ActivityTaskManager: Duplicate finish request` warnings in logcat -
where `onCreate()`'s stale-tap bail-out didn't actually stop
`onResume()` from independently re-triggering Recents in the same
lifecycle pass, overriding the home-bounce. Fixed with a shared
bail-out flag. At that point the whole class of problem - an
ever-escalating set of special cases needed to make a *fake* Task
behave, because it's still a real Task as far as Recents is concerned
- was judged not worth carrying forward, and the approach was replaced
outright rather than patched further.

#### Follow-up: "Usage access" permission check was always reporting denied

`Context.hasUsageStatsAccess()` originally used
`checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)`.
Despite being declared as a normal manifest permission,
`PACKAGE_USAGE_STATS` is actually gated for third-party apps through
`AppOpsManager`'s special-app-access model, not the regular
permission-grant system - `checkCallingOrSelfPermission` for it
reliably returns `PERMISSION_DENIED` regardless of whether the user has
actually enabled "Usage access" in Settings, since its declared
protection level was never meant to be satisfied by a normal grant
dialog. Fixed with `AppOpsManager.checkOpNoThrow(OPSTR_GET_USAGE_STATS, ...)`,
the correct check for this specific permission.

#### Follow-up: HOME after Recents broke the next invocation

Visiting Recents and returning home via the nav button, then pressing
the physical button/gesture again, reproduced the original
flash-and-dismiss symptom. Root cause: the real system Recents screen
itself generates a genuine `ACTIVITY_RESUMED`/`MOVE_TO_FOREGROUND`
event for its own package (`LawnchairApp.systemRecentsComponentName`),
same as any other app - without excluding it,
`findRecentForegroundPackages()` picked it as "the last used app" and
tried to resume the Recents provider itself as if it were a normal
app, which is meaningless and reproduces the bug this whole mechanism
works around. Fixed by adding that package (read fresh per call, not
cached, since which package provides Recents is fixed per-device but
this lookup is cheap) to the exclusion list alongside this launcher's
own package and `com.android.systemui`.

#### Follow-up: swiping the last app away, or "clear all", resurrected it anyway

`UsageEvents` has no concept of a task being removed from Recents -
swiping a card away, or hitting "clear all", doesn't generate any new
event. Without accounting for that, the very next `bounceToRecents()`
call would still find the same stale `ACTIVITY_RESUMED` entry and
resume (i.e. relaunch) an app the user had just explicitly dismissed.
Fixed by watching the live Recents window via
`TYPE_WINDOW_CONTENT_CHANGED` while it's open: for each of the top
`TRACKED_CANDIDATE_LIMIT` most-recent candidate apps
(`bounceToRecents()` computes and tracks all of them, not only the one
it actually resumes - a single-app check would just resurrect whichever
one happened to be second once cleared), a label-match against the
window's accessibility node tree tells whether that app's card is
still visible; once observed to disappear after having been seen, that
package is marked dismissed with the current timestamp, and
`findRecentForegroundPackages()` skips it in favor of whichever app was
used before it - correctly falling through past *every* app cleared at
once by "clear all," not just the single most-recent one. A
false-positive dismissal (e.g. mistaking a card vanishing because the
user tapped it to reopen the app) is harmless and self-heals: that
reopen is itself a fresh, newer `ACTIVITY_RESUMED` event, which
naturally supersedes the recorded dismissal timestamp.

#### Follow-up: two reliability banners, one of them initially broken

Two Quickstep preference banners, matching the existing
accessibility-service one, surface prerequisites this feature silently
depended on without saying so:

- **"Usage access" banner.** Without it, `hasUsageStatsAccess()` (see
  above) returns false and the button/gesture silently falls back to
  the original, occasionally-misbehaving direct-`performGlobalAction`
  behavior with no indication why - `PACKAGE_USAGE_STATS` can't be
  pre-granted from the manifest or requested via a standard permission
  dialog, only pointed at from Settings.
- **Device admin banner.** Not required for correctness, but some OEMs
  are less aggressive about killing a device-admin app's background
  service/process during memory or battery cleanup - directly relevant
  to keeping this feature's accessibility service alive. Reuses the
  same admin receiver as the existing "Double tap to sleep" gesture
  (`SleepMethodDeviceAdmin.SleepDeviceAdmin`) rather than declaring a
  new one. The first version of this banner's tap handler added
  `FLAG_ACTIVITY_NEW_TASK` to the `ACTION_ADD_DEVICE_ADMIN` intent, by
  analogy with the other two banners - but the device-admin activation
  screen verifies its calling activity to detect its caller, and
  `FLAG_ACTIVITY_NEW_TASK` breaks that chain, so the screen finished
  itself immediately: a flash-and-return, with a dead entry briefly
  visible in Recents. Fixed by dropping the flag, matching the same
  call already working correctly in `ServiceWarningDialog`'s "open
  settings" button for the sleep-gesture flow.

### Testing

Verified on real hardware exhibiting the firmware-hardcoded-provider
bug described above, with "Recents interception" enabled, across the
full sequence of approaches above (transparent Activity, then
focus-grabbing overlay, then the current resume-last-app mechanism):

- Physical Recents button in 3-button nav: reliably opens the real
  Recents screen instead of flashing and falling back.
- Double-tap-Home gesture (routed through `RecentsGestureHandler`):
  same result.
- Gesture navigation mode: touch-interception overlay is not shown
  (nothing to overlay); button-path interception is inert, gesture path
  still routes through `bounceToRecents()`.
- Toggling the setting off restores the original (broken, on this
  firmware) behavior for both paths.
- Rotating the screen and switching between 3-button/gesture nav at
  runtime correctly repositions or removes the touch-interception
  overlay.
- Confirmed stably working once "Usage access" was actually granted and
  the permission-check bug (see follow-up above) was fixed - the visible
  flash of the resumed app while the focus change settles was
  additionally trimmed down (`RESUME_SETTLE_DELAY_MS`) to be as
  unobtrusive as possible without reverting to the flash-and-dismiss
  fallback.
- Visiting Recents, returning home via the nav button, then invoking
  Recents again: previously broke (see HOME-after-Recents follow-up
  above), now works.
- Swiping the resumed app's card away in Recents, then invoking Recents
  again: previously resurrected it; now correctly falls through to the
  app used before it.
- "Clear all" in Recents, then invoking Recents again: an earlier pass
  of the swipe-away fix only tracked the single most-recently-resumed
  app, so clearing everything just resurrected whichever app happened
  to be second most recent; fixed by tracking presence of the top
  `TRACKED_CANDIDATE_LIMIT` candidates instead of just one (see
  follow-up above).
- Device admin banner: confirmed the pre-fix version visibly
  flashed-and-returned without granting anything (see follow-up above);
  post-fix, tapping it correctly opens the device-admin activation
  screen and granting persists.

### Compatibility

No data model changes. Adds one new preference key
(`recents_button_interception`, default `false`). No new activities or
manifest components - the transparent bounce activity from an earlier
iteration of this PR was removed entirely along with the approach it
belonged to. `LawnchairAccessibilityService`'s `serviceInfo` requests
`FLAG_RETRIEVE_INTERACTIVE_WINDOWS` and `FLAG_REPORT_VIEW_IDS`, and
watches `TYPE_WINDOWS_CHANGED` (touch-interception overlay) and
`TYPE_WINDOW_CONTENT_CHANGED` (dismiss tracking) - both only take
effect when the new setting is on. `accessibility_service_config.xml`
gains `android:canRetrieveWindowContent="true"`, required for
`getWindows()`/`findAccessibilityNodeInfosByViewId()` to return
anything. Reading the last-used app requires the user to separately
grant "Usage access" (`PACKAGE_USAGE_STATS`/`AppOpsManager`, surfaced
via a settings banner - see follow-up above); without it, the feature
degrades to its original, occasionally-misbehaving direct-`performGlobalAction`
behavior rather than failing outright. The device-admin banner requests
no new admin receiver - it reuses the one already declared for the
"Double tap to sleep" gesture, so granting it is a single shared
capability, not per-feature. All new state
(`lastRecentsSelfTriggerAtMs`, the dismiss-tracking maps) is in-memory
on `LawnchairApp`/`LawnchairAccessibilityService`, with no persistence.

### Type of change

:white_check_mark: **New feature** (A non-breaking change that adds functionality)
