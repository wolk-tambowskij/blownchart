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
the launcher's own context. The new `RecentsBounceActivity` is a
transparent, `taskAffinity=""`, `excludeFromRecents`,
`autoRemoveFromRecents`, `noHistory` activity whose only job is to
briefly become the resumed foreground activity - a plain, non-launcher
app, from the OS's perspective - and then fire
`GLOBAL_ACTION_RECENTS` itself, a beat later. It finishes itself in
`onStop()` (not `onPause()`, which can fire while the real Recents
window is still only partially drawn over it and race that
transition) via `finishAndRemoveTask()`, so it never lingers as a real
task once Recents has taken over.

For the physical-button path specifically, since the OS routes that
press away from the launcher entirely, there's no window-state event
Lawnchair can react to in time - by the time any accessibility event
fires, the OS's own broken rendering attempt has often already begun.
`LawnchairAccessibilityService` (previously an inert skeleton -
`eventTypes = 0` - every Lawnchair build already declares this service
and prompts to enable it for other accessibility-dependent features,
like Double-Tap-to-Sleep) instead watches `TYPE_WINDOWS_CHANGED`
system-wide, locates the Recents button's on-screen bounds in the
navigation bar (by view ID first, falling back to a content-description
search for firmware that renamed/dropped the standard AOSP id), and
keeps an invisible `TYPE_ACCESSIBILITY_OVERLAY` view positioned exactly
over it. A tap on that overlay intercepts the touch before SystemUI's
own (broken) button handling ever runs, and launches
`RecentsBounceActivity` the same way the gesture path does. This only
applies in 3-button navigation mode - gesture navigation has no
on-screen button to overlay, so the overlay is torn down (and nothing
is watched) when gesture nav is active.

Both paths funnel through the same bounce activity and share one
self-trigger-cooldown timestamp
(`LawnchairApp.lastRecentsSelfTriggerAtMs`), since the bounce
activity's own `GLOBAL_ACTION_RECENTS` call is itself a window-state
change that could otherwise be mistaken for a fresh press.

The whole mechanism is opt-in via a new "Recents interception" setting
(`recentsButtonInterception`, default off) in Quickstep preferences,
since it depends entirely on this firmware quirk existing at all -
harmless but pointless overhead on firmware where Recents already
works correctly.

#### Follow-up: ghost/duplicate card in Recents

Once the above was working, real-device testing surfaced a second,
distinct problem caused by the same firmware quirk: `RecentsBounceActivity`
itself, despite being transparent and only on screen for well under a
tenth of a second, could be left behind as its own separate, independently
swipeable card in the Recents list - alongside, not instead of, the real
Recents screen. That ghost card showed a live snapshot of whatever was
drawn behind the bounce activity (i.e. the app that was open before the
button/gesture fired) but labeled with this app's own launcher icon,
reading as a confusing duplicate. Worse, since the bounce activity's task
was already torn down (`finishAndRemoveTask()` in `onStop()`), tapping
that stale card resurrected a fresh instance of the bare, non-interactive
transparent activity via its cached launch intent - a dead, frozen screen
with nothing to show and nothing to tap except the OS navigation buttons.

Testing established that on this class of vendor firmware, neither of
the two standard tools for keeping an activity out of Recents are
reliably honored: `excludeFromRecents` (manifest attribute, already
declared on `RecentsBounceActivity` from the start) and
`setRecentsScreenshotEnabled(false)` (API 33+, an `ActivityTaskManager`-level
call). Per confirmed platform behavior, a Recents card also cannot be
force-removed at all on Android 11+ once the OS has created one,
regardless of vendor/firmware - so the fix could not be "prevent the card,"
only "make the card harmless if it appears anyway":

1. **Blank task icon.** `setTaskDescription(ActivityManager.TaskDescription(" "))`
   only blanks the task's label - the icon still defaults to this app's own
   launcher icon unless a `Bitmap` is passed explicitly. A 1x1 transparent
   bitmap is passed instead, so any ghost card that does appear no longer
   reads as "this app."
2. **Unconditional `FLAG_SECURE`.** `setRecentsScreenshotEnabled(false)` is
   applied where available (API 33+) but is not trusted alone, since it's
   an `ActivityTaskManager`-level API this firmware has already shown it
   can ignore. `FLAG_SECURE` is added on the window unconditionally,
   alongside it, as a second, lower-level line of defense: it's enforced
   by the display compositor (SurfaceFlinger) itself, which even a custom
   vendor Recents renderer generally can't route around the way it can
   ignore a manifest attribute or a task-manager API call. With both in
   place, any card that still appears is a static placeholder rather than
   a live screenshot of whatever was behind it.
3. **Stale-resurrection detection.** `RecentsGestureHandler` and
   `LawnchairAccessibilityService`/`BlownChartAccessibilityService` both
   stamp a new `lastRecentsBounceActivityLaunchedAtMs` timestamp
   immediately before starting `RecentsBounceActivity`. `onCreate()`
   compares the elapsed time against that stamp: a gap under 3 seconds is
   a genuine fresh launch and proceeds normally; a larger gap means this
   `onCreate()` call was not caused by either legitimate launch path, i.e.
   it's the OS resurrecting a stale card's cached intent from a direct tap.
   In that case, instead of running the normal
   suppress-snapshot-then-trigger-Recents flow (which has nothing useful
   left to do - the original task this card depicted is long gone), it
   redirects straight to the home screen (`ACTION_MAIN`/`CATEGORY_HOME`)
   and calls `finishAndRemoveTask()`, so a tap on a stale ghost card lands
   the user back on their home screen instead of a dead, button-only
   screen.

### Testing

Verified on real hardware exhibiting the firmware-hardcoded-provider
bug described above, with "Recents interception" enabled:

- Physical Recents button in 3-button nav: reliably opens the real
  Recents screen instead of flashing and falling back.
- Double-tap-Home gesture (routed through `RecentsGestureHandler`):
  same result.
- Gesture navigation mode: overlay is not shown (nothing to overlay);
  button-path interception is inert, gesture path still routes through
  the bounce activity.
- Toggling the setting off restores the original (broken, on this
  firmware) behavior for both paths.
- Rotating the screen and switching between 3-button/gesture nav at
  runtime correctly repositions or removes the overlay.
- Ghost/duplicate card follow-up: with the blank icon and unconditional
  `FLAG_SECURE` in place, no live-content duplicate card labeled as this
  app appears in Recents after repeated button/gesture use. Tapping any
  placeholder card that does still appear from an already-finished bounce
  task redirects to the home screen instead of showing a dead,
  non-interactive screen.

Not yet independently re-verified against a from-scratch build of this
specific clean-room branch on hardware (built and tested as part of
the fork's own integrated codebase, which carries the identical logic)
- worth a real-device smoke test before merging upstream.

### Compatibility

No data model changes. Adds one new preference key
(`recents_button_interception`, default `false`) and one new activity
(`RecentsBounceActivity`, not exported, no intent filters beyond the
default). `LawnchairAccessibilityService`'s `serviceInfo` now requests
`FLAG_RETRIEVE_INTERACTIVE_WINDOWS` and `FLAG_REPORT_VIEW_IDS`, and
watches `TYPE_WINDOWS_CHANGED` instead of no events - only takes effect
when the new setting is on. `accessibility_service_config.xml` gains
`android:canRetrieveWindowContent="true"`, required for
`getWindows()`/`findAccessibilityNodeInfosByViewId()` to return
anything. The ghost-card follow-up adds no new preferences or
components: `setRecentsScreenshotEnabled` is only called on API 33+
(no-op below that), `FLAG_SECURE` and the blank `TaskDescription` are
applied to the existing bounce activity's own window/task only, and
`lastRecentsBounceActivityLaunchedAtMs` is an in-memory `Application`
field with no persistence.

### Type of change

:white_check_mark: **New feature** (A non-breaking change that adds functionality)
