# Donations / Поддержать разработку

BlownChart is free software (GPLv3) and always will be — donating never
unlocks a feature, and never will, because copyleft makes any such gate
pointless anyone could just rebuild the app without it. Support is purely
voluntary; see `Settings → About → Support development` in the app.

## Where the config lives

Everything is defined in one place:
[`lawnchair/src/app/lawnchair/donate/DonationMethods.kt`](../lawnchair/src/app/lawnchair/donate/DonationMethods.kt).
Each entry is a `DonationMethod(id, titleRes, type, value, enabled)`:

- `type = LINK` — opened with `ACTION_VIEW` in the browser.
- `type = COPY_TEXT` — copied to the clipboard with a confirmation toast.
- `type = QR` — a QR code is generated **on-device** from `value` (via
  `zxing-core`, no network call, no third-party QR service) and shown in a
  dialog with a copy button.
- `enabled = false` hides the method from the UI. Every method still
  carrying a `<PLACEHOLDER>` value is `enabled = false` by default so
  nothing broken ever reaches a real user - flip it to `true` once you've
  filled in the real value.

The UI (`lawnchair/src/app/lawnchair/ui/preferences/destinations/DonatePreferences.kt`)
just renders whatever `DonationMethods.enabledMethods` returns - there's
nothing else to keep in sync.

## Current channels

| Channel | Type | Status |
|---|---|---|
| PayPal | LINK | **Live** - PayPal's own `/donate/?business=<email>` donate-button URL, using `wolk.tambowskij@gmail.com`. No `paypal.me` handle exists for this account, so this is the correct real PayPal mechanism instead of a fabricated link. |
| YooMoney (ЮMoney) | LINK | **Live** - `https://yoomoney.ru/to/4100119588109985`, YooMoney's standard quick-transfer link format. |
| Boosty | LINK | Placeholder (`enabled = false`) - needs your Boosty page URL. |
| CloudTips | LINK | Placeholder (`enabled = false`) - needs your CloudTips page URL. |
| Mir card | COPY_TEXT | Placeholder (`enabled = false`) - needs a real card number. |
| Crypto wallet | COPY_TEXT | Placeholder (`enabled = false`), optional - needs a wallet address, or delete the entry if you don't want to offer this. |

## Changing a value

Edit the corresponding entry in `DonationMethods.kt` and set `enabled = true`
once the value is real. That's it - no other file references these values.

If a value is sensitive enough that you don't want it sitting in a public
git history in plain text (this applies more to a bank card number than to
a PayPal/YooMoney handle, which are already meant to be public), keep the
commit that fills it in on a private branch, or replace it after the repo
goes public and accept that the placeholder stays in history - GPLv3
donation credentials aren't secrets the way a signing key is, so this
repo's git-history secret scan (see the licensing task) does not, and
should not, flag them.

## Why no in-app purchases / "premium"

Donations here are strictly "buy us a coffee," never a feature unlock.
Two independent reasons:

1. **GPLv3 makes it pointless.** Anyone can legally rebuild BlownChart from
   source with any paywall check removed. Shipping one would only annoy
   honest users while doing nothing to anyone determined to skip it.
2. **It matches the spirit of the project** - BlownChart exists because a
   simple, fast, fully-lockable launcher wasn't available for free; adding
   a paywall would undercut the reason it exists.

## Distribution-channel restrictions

- **Google Play** (`play` flavor, `applicationId com.blownchart.play`):
  Play's Billing policy forbids linking out to external payment systems
  for in-app "payments," which a donation link can be read as. The `play`
  flavor sets `BuildConfig.DONATIONS_SHOW_PAYMENT_LINKS = false`
  (`build.gradle`, in the `play` product flavor block); the donate screen
  then shows a single neutral row pointing to the GitHub repo instead of
  any payment link. Re-verify this against Play's current policy before
  actually publishing to Play - policies change.
- **GitHub / IzzyOnDroid** (`github` flavor): full donation screen, all
  enabled methods, direct links. `BuildConfig.DONATIONS_SHOW_PAYMENT_LINKS = true`.
- **Nightly builds** (`nightly` flavor): same as GitHub - full screen.
- **F-Droid**: F-Droid's guidelines discourage (and in stricter cases
  reject) in-app donation prompts/links; the accepted place for this is
  the `Donate:` field in the app's *F-Droid metadata* (maintained in the
  separate `fdroiddata` repository, not here), not inside the APK. If/when
  this fork is submitted to F-Droid, use a metadata stub along these
  lines in that PR:

  ```yaml
  Donate: https://github.com/wolk-tambowskij/blownchart#support--поддержать
  ```

  (F-Droid's `Donate:` field takes a single URL - point it at this
  README's Support section, which lists every channel, rather than trying
  to enumerate them all in the metadata file itself.)

## Privacy

No network requests, no analytics, no tracking anywhere in the donate
screen. `LINK` opens the system browser (which is then a normal browser
session, outside our control); `COPY_TEXT` and `QR` never leave the device.
