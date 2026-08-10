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
real hardware exhibiting it. Filing as a fix since it's a genuine bug
in how `RecentsGestureHandler` invokes Recents (see Reasoning), not a
new capability.

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
anything.

### Type of change

:white_check_mark: **Bug fix** (A non-breaking change that fixes an issue)
