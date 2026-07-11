# Reconcile — offline SMS-driven expense tracker

(App renamed from "Kharcha" in v2; package + code names keep the `kharcha`/
`expensetracker` identifiers to preserve installed data.)

Android app (Kotlin + Jetpack Compose, Material 3). Reads Indian bank/UPI SMS
and logs transactions automatically. 100% offline — **no INTERNET permission,
ever**. Personal sideloaded app; no Play Store constraints.

## Build (CLI only — no Android Studio on this machine)

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug          # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM unit tests (SMS parser suite lives here)
./gradlew installDebug           # build + install on connected device
```

`JAVA_HOME` is also pinned in `gradle.properties` (`org.gradle.java.home`), but the
`gradlew` launcher itself needs it exported. SDK path lives in `local.properties`
(`sdk.dir=/opt/homebrew/share/android-commandlinetools`). adb & emulator:
`/opt/homebrew/share/android-commandlinetools/{platform-tools,emulator}`.

Toolchain (installed via Homebrew): `openjdk@17`, `android-commandlinetools`
(platform 35, build-tools 35.0.0, emulator + `system-images;android-35;google_apis;arm64-v8a`,
AVD name `kharcha_test`), `gradle` (only used once to generate the wrapper; builds use `./gradlew`).

## Emulator smoke test

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
$ANDROID_HOME/emulator/emulator -avd kharcha_test -no-window -no-audio &  # wait for sys.boot_completed=1
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.abc.expensetracker android.permission.READ_SMS
adb shell pm grant com.abc.expensetracker android.permission.RECEIVE_SMS
adb emu sms send HDFCBK 'Spent Rs.125 On HDFC Bank Card 2854 At MERCHANT On 2026-07-09:21:35:29.'
adb exec-out screencap -p > /tmp/shot.png
```

## Architecture

MVVM, manual DI (`AppContainer` in `KharchaApp`), single-activity Compose navigation.

- `sms/parser/` — **pure Kotlin, no Android imports, fully unit-tested on JVM.**
  - `ParserRules.kt` — regex rule tables. To support a new bank/format, extend the
    lists here; `SmsParser` itself should not change.
  - `SmsParser.kt` — reject filters (OTP/promo/reminders/requests) → direction
    (earliest keyword wins) → amount (verb-adjacent, then first currency amount)
    → merchant/account/ref/balance/date. Money is **Long paise** everywhere.
  - `Categorizer.kt` — keyword → category key table.
- `sms/` — `SmsReceiver` (RECEIVE_SMS realtime, goAsync), `SmsImporter`
  (READ_SMS one-time full-inbox scan, progress via StateFlow).
- `data/` — Room: Txn, Category, Account, Budget, Goal, MerchantMapping.
  `TxnRepository.insertFromSms` is the single write path — dedup (3 layers:
  smsHash unique index / same UPI ref+amount / same amount+direction within ±3 min),
  auto-categorization with the MerchantMapping learning layer, and account
  auto-creation per (bank, last-4) all live there.
- `ui/` — `vm/ViewModels.kt` (one VM per tab + `VmFactory`), `screens/`,
  `charts/Charts.kt` (hand-rolled Canvas donut/bars/line — no chart library),
  `theme/`, `common/Components.kt`.
- `util/` — Money (Indian digit grouping), Dates, Recurring (cadence heuristic),
  CsvExport (FileProvider share).

## v2 feature map

- **CC bill payments**: credit-side "payment received towards your card" SMSes are
  rejected by the parser AND routed through `SmsParser.parseCcBillPayment` →
  `TxnRepository.recordCardPayment` auto-marks that card's cycle paid. Debit-side
  payments (CheQ/CRED/BBPS) are stored but categorized `ccpayment` + `excluded=true`.
- **Excluded transactions** (`Txn.excluded`): never counted in any aggregate. Set
  automatically via `Category.excludeFromTotals` (ccpayment, selftransfer) or the
  learning layer; user toggles per-txn with an "always for this merchant/sender" option.
- **Learning layer v2**: `MerchantMapping` now keys by merchantNorm OR
  `sender:<addr>:<direction>` for merchant-less bank notices, and carries `excluded`.
- **Card bills** (`card_bills`): per-card dueDay/statementDay, editable in Budgets tab.
  `BillReminders` schedules a daily ~10:00 AlarmManager check (exact if permitted);
  reminders run from due−3 until marked paid. `BootReceiver` re-arms after reboot.
- **Insights**: forecast (30-day rolling average), anomalies (≥2.5× merchant median,
  ≥3 priors, ≥₹500 over), envelope budgets (`Budget.rollover`), duplicate review queue
  (same amount within 6h, beyond the ±3 min auto-window; dismissals in `dup_dismissals`).
- **Splits**: `Txn.splitOwedPaise/splitWith/splitSettled`; debit aggregates count
  `amountPaise - splitOwedPaise`. Settle from More → "Owed to you".
- **Tags**: comma-separated on `Txn.tags`; filter chips in History.
- **Widget** (`widget/TodayWidget`, RemoteViews): today's spend vs daily budget slice.
- **Shortcut**: long-press icon → "Add expense" (`quickAdd` intent extra).
- **DB migrations**: `MIGRATION_1_2` in KharchaDb.kt. Never use destructive fallback —
  the phone install carries real data. Bump version + write a Migration for schema changes.

## Conventions

- Money = `Long` paise. Never Double for storage/math; format via `util/Money`.
- Category `key` strings link DB rows ↔ `Categorizer` rules — keep in sync (`Seed.kt`).
- User category corrections go through `TxnRepository.setCategoryLearning`
  (writes MerchantMapping + recategorizes that merchant's history). Never update
  `Txn.categoryId` directly for SMS transactions.
- Category colors in `Seed.kt` were validated with the dataviz palette validator
  (light + dark surfaces). If you add a category, keep OKLCH L in 0.48–0.67 and
  chroma ≥ 0.1, and re-run the validator.
- Parser changes: add a unit test with the real SMS body first (`SmsParserTest`).
- No new permissions. Especially not INTERNET.
