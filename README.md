# Reconcile

Offline, SMS-driven expense tracker for Android. Reads Indian bank/UPI SMS and
logs transactions automatically — **no INTERNET permission, ever**. Personal
sideloaded app (not on Play Store).

(App renamed from "Kharcha" in v2; package + internal identifiers keep the
`kharcha`/`expensetracker` names to preserve installed data.)

Kotlin + Jetpack Compose, Material 3, MVVM, Room.

## Features

- Auto-parses bank/UPI SMS → transactions (amount, merchant, account, date),
  with dedup and learned auto-categorization.
- Credit card bill tracking: auto-marks cards paid from bank SMS, due-date
  reminders, editable statement/due cycles.
- Insights: 30-day spend forecast, merchant anomaly detection, envelope
  budgets with rollover, duplicate-transaction review queue.
- Tags, split-expense tracking ("owed to you"), home screen widget, quick-add
  shortcut, CSV export.

## Build

No Android Studio required — CLI only.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug          # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM unit tests (SMS parser suite)
./gradlew installDebug           # build + install on connected device
```

`JAVA_HOME` is also pinned in `gradle.properties`, but the `gradlew` launcher
itself needs it exported. SDK path is in `local.properties`.

## Install on a phone

No Play Store — sideload the APK.

1. Build it (see above) or grab `app/build/outputs/apk/debug/app-debug.apk`
   after `./gradlew assembleDebug`.
2. Get it onto the phone: USB cable, or transfer via any file-share
   (Drive/email/etc.) and download it on-device.
3. On the phone: open the APK file → allow "install unknown apps" for that
   source when prompted → Install.
4. On first launch, grant **SMS** permission (Read + Receive) when asked —
   required for auto-logging transactions. Everything else the app does is
   local; it never requests INTERNET.

Installing over an existing copy keeps your data (DB migrations handle
version upgrades — never uninstall first if you want to keep history).

### Via adb (if phone is USB-connected with USB debugging on)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.abc.expensetracker android.permission.READ_SMS
adb shell pm grant com.abc.expensetracker android.permission.RECEIVE_SMS
```

## Architecture

- `sms/parser/` — pure Kotlin, no Android imports, fully unit-tested on JVM.
  Rejects OTP/promo/reminders → detects direction → amount → merchant/
  account/ref/balance/date. Money is `Long` paise everywhere.
- `sms/` — `SmsReceiver` (realtime) and `SmsImporter` (one-time inbox scan).
- `data/` — Room entities (Txn, Category, Account, Budget, Goal,
  MerchantMapping); `TxnRepository.insertFromSms` is the single write path
  (dedup, auto-categorization, account auto-creation).
- `ui/` — ViewModels, screens, hand-rolled Canvas charts, theme, shared
  components.
- `util/` — money formatting, dates, recurrence heuristics, CSV export.

## Status

v2 complete: CC bill payment handling, excluded/self-transfer flow, card
bills with reminders, insights, tags/splits/widget/shortcut, DB v1→2
migration. Verified on emulator and installed on device.
