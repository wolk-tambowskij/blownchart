# Proposal: settings PIN/biometric lock (RFC + design doc)

Status: **draft, not yet a PR** — needs project-owner sign-off before
any branch is created, same process as the nested-folders proposal.

## Motivation / use case

Upstream already has `lockHomeScreen`/`lockAppDrawer` (once #9 in this
plan lands) to stop the launcher's *layout* being casually rearranged.
Neither covers the launcher's own **settings app** or the shortcut that
jumps into system **Settings** — anyone who can open the app drawer can
open Lawnchair's preferences, or the "App info" shortcut straight into
Android Settings, and change anything, including turning the two lock
toggles above back off. On a shared or handed-to-a-kid device, that's
the actual gap: the existing locks protect the workspace, not the
controls that turn the workspace locks off. This proposal adds a PIN
(with optional biometric shortcut) gating (a) launching Lawnchair's own
settings UI and (b) any exit from the launcher into system Settings.

Explicit non-goal, matching the fork's own changelog wording
(`about_change_9`): **this does not gate shortcuts or widgets created by
third-party apps** — a banking app's own PIN screen, a widget's
settings deep-link, etc. are out of scope; only the two exits listed
above are covered. This should be stated up front in the PR description
so reviewers don't read it as a broader parental-control feature.

## UX/design note — flag this as contestable

Unlike the S-class fixes already proposed, this is a genuinely
opinionated feature addition: some launcher maintainers are wary of PIN
gates baked into the launcher itself (attack surface, "security
theater" if the phone itself is unlocked anyway, support burden of
forgotten PINs, interaction with device admin/screen-lock policy). This
is exactly the kind of scope call that should be put to maintainers
before code, not decided unilaterally by sending a PR — see RFC framing
below.

## Current fork implementation (what exists today, to describe faithfully in the RFC/PR)

- **`PinHasher`** (`lawnchair/src/app/lawnchair/security/PinHasher.kt`):
  salted PBKDF2WithHmacSHA256, 120k iterations, 256-bit key, random
  16-byte salt, constant-time comparison. Stores only
  `iterations:base64(salt):base64(derivedKey)` — the raw PIN is never
  persisted.
- **`SettingsLockGate`** (`.../security/SettingsLockGate.kt`): thin
  façade over three DataStore preferences — `settingsLockEnabled`
  (Boolean, default `false`), `settingsLockPinHash` (String, default
  `""`), `settingsLockBiometricEnabled` (Boolean, default `true`, only
  meaningful once a PIN exists). `isEnabled()` requires both the toggle
  on *and* a non-empty stored hash, so a half-configured state (toggle
  on, no PIN set yet) can't lock a user out.
- **`SettingsLockUnlockActivity`** (`.../security/SettingsLockUnlockActivity.kt`):
  a dedicated `FragmentActivity` (needed because `BiometricPrompt`
  requires a `FragmentActivity`/`Fragment` host, and the launcher's
  other activities don't extend one). Two entry points:
  `createUnlockIntent(context, launchIntentAfterUnlock?)` for the normal
  gate, and `createSetupIntent(context)` for first-time PIN creation. If
  the lock isn't enabled and it's not setup mode, it no-ops
  (`RESULT_OK` immediately) instead of blocking the caller.
- **`SettingsLockScreens.kt`**: the actual Compose PIN entry/creation
  UI (`SettingsLockUnlockScreen`) — unlock-with-PIN, create-PIN (with
  confirm field + length validation via `SettingsLockGate.isValidPin`,
  4–16 chars), optional biometric button when
  `settingsLockBiometricEnabled` and `BiometricManager` reports
  `BIOMETRIC_SUCCESS`, plus a "forgot PIN" path that requires a
  successful biometric check before dropping into create-PIN mode
  locally (no Activity restart).
- **Call sites** (two, deliberately minimal):
  - `LawnchairLauncher.requestSettingsUnlock()` /
    `requestSettingsUnlockForIntent()` (`LawnchairLauncher.kt`) — used
    by `LawnchairShortcut.GatedAppInfo` (the long-press "App info"
    shortcut, which launches system Settings) and by
    `ItemClickHandler.java`'s `isSystemSettingsPackage()` check, which
    catches taps on already-placed system-Settings shortcuts/icons.
    `requestSettingsUnlockForIntent` has the unlock Activity itself
    launch the target intent on success (documented in the code as a
    fix for a real bug: launching from an `ActivityResultCallback`
    after the unlock screen returns control silently no-op'd for a
    plain icon tap).
  - `PreferenceActivity.onCreate()` — gates Lawnchair's own settings UI
    itself the same way, with an `isUnlocked` Compose state gate.

## Data model / storage impact

No new database entities — three DataStore Preferences keys only
(`settings_lock_enabled`, `settings_lock_pin_hash`,
`settings_lock_biometric_enabled`), which is upstream's existing
pattern for every other preference (`PreferenceManager2.kt`). No
migration needed since DataStore preferences don't require schema
migrations for new keys — an upgrading user simply gets the defaults
(disabled) until they opt in.

## Backup/restore impact

`settings_lock_pin_hash` lives in the same `preferences.preferences_pb`
DataStore file that `LawnchairBackup.getFiles()` already includes in
Lawnchair's manual `.lawnchairbackup` export (see the nested-folders
proposal's backup section for the full file list — same file). That
means a PIN hash would travel with a manual backup/restore. Worth
flagging explicitly to reviewers as something to decide deliberately,
not by accident: is restoring a locked device's PIN hash onto another
device the intended behavior, or should `settings_lock_pin_hash`/`settings_lock_enabled`
be excluded from `INCLUDE_LAYOUT_AND_SETTINGS` restores? The fork
currently does *not* special-case it (restores it like any other
preference). This is exactly the kind of design question to raise in
the RFC rather than have picked silently.

## Backward compatibility

Fully additive and off by default (`settingsLockEnabled` defaults
`false`). Zero behavior change for upgrading users until they explicitly
opt in and set a PIN. No interaction with the existing
`lockHomeScreen`/`lockAppDrawer` toggles' stored values.

## Alternatives considered

- **Reuse the device's own screen-lock credential (`KeyguardManager.createConfirmDeviceCredentialIntent`)
  instead of a launcher-local PIN.** Simpler, no new hashing/storage, and avoids "yet another PIN to
  remember" — but it means anyone who can unlock the phone at all can
  also open launcher settings, which defeats the shared/handed-to-a-kid-device
  use case this feature targets (the phone is often left unlocked in
  that scenario, or the kid knows the unlock pattern but shouldn't be
  changing launcher settings). Worth raising as an explicit option in
  the RFC even though the fork went with a dedicated PIN.
- **Biometric-only, no PIN fallback.** Rejected: biometric hardware
  isn't universal and `BiometricPrompt` needs a fallback path anyway
  (locked-out state, no enrolled biometrics) — the fork treats biometric
  as a convenience shortcut on top of a mandatory PIN, not a
  replacement.

## RFC framing

M/L-class, and — per the UX note above — a legitimately contestable
scope decision (whether the launcher should own a PIN gate at all, vs.
delegating to the device credential). Recommend opening a GitHub
Discussion/RFC issue describing the feature, the "third-party
shortcuts/widgets are out of scope" boundary, and the backup/restore
question above, before sending a PR — this one is more likely than the
nested-folders proposal to get a maintainer "we'd rather not" answer,
and that's worth learning before writing PR descriptions, not after.

## Proposed PR chain

Unlike nested folders, this doesn't have a natural data-model/UI split
worth forcing — the preferences, gate logic, and UI are small and
tightly coupled (three files: `PinHasher`, `SettingsLockGate`,
`SettingsLockUnlockActivity` + `SettingsLockScreens`). Proposed as a
**single PR**:

1. `PinHasher` + `SettingsLockGate` + `SettingsLockUnlockActivity` +
   `SettingsLockScreens` + the three preferences + the two call sites
   (`LawnchairLauncher` unlock helpers wired into `ItemClickHandler`'s
   system-Settings check and `LawnchairShortcut`'s "App info" shortcut,
   and `PreferenceActivity`'s own gate) + a settings toggle to
   enable/configure it (new `SettingsLockPreferences.kt` screen).

No ordering dependency on nested folders or `feat/folder-manual-order`
— can be built and submitted whenever, independent of the folder-family
chain's progress, as already noted in `UPSTREAM_CONTRIB_BRANCHES.md`.
