# Handoff — Reconcile Android UI Revamp

## Goal and source
- Implemented the dark-only `Reconcile 2.0 — Dark UI Template` from page `Revamp — Velocity Dark`.
- Figma MCP hit the connected Starter-plan quota, so the authoritative implementation reference was the repo-root `Reconcile — Android UI Revamp.pdf` static export.
- Runtime data remains local Room/ViewModel data; merchant names and amounts such as Swiggy/Amazon/Netflix exist only in `@Preview` functions.

## Completed
- Dark design tokens, Roboto type hierarchy, 4dp grid, 12/16/20dp radii, 48dp minimum targets, light system-bar glyphs.
- Reusable `MoneyText`, `TransactionRow`, non-emoji `CategoryIcon`, attention/status rows, metrics, custom progress bars, filters, section headers, four-item bottom nav, button variants, empty states, and chart containers.
- Four-tab shell: Home, Activity, Insights, Plan. Non-tab routes: Settings/Data Quality and `transaction/{id}`.
- Home: spend, budget state, in/out/net, money pulse, attention queue, categories, exact recent-transaction navigation.
- Money Pulse is now interactive: it preserves dated `DaySum` points, defaults to the latest day, selects a bar on tap, highlights the selected bar, and displays its formatted amount/date. Empty data shows a real no-spend message instead of a fake mint bar.
- Activity: search, month/type/category/account filters, date groups, split-aware day totals, explicit income/review/excluded states.
- Insights: real current/previous spending, local straight-line projection, category shifts, top merchants.
- Plan: Budget/Bills/Recurring/Goals using existing budgets, cards, reminder enabled/paid state, recurring detector, statements, and goals.
- Transaction Detail: receipt-first read view, account/instrument/reference/balance, inclusion reason, tags/splits, original SMS, separate edit/delete, warned history-and-future merchant learning.
- Settings/Data Quality: accounts, categories, cards, merchant rules, duplicates, uncategorized, splits, SMS rescan, CSV export, dark appearance, bill notifications, offline/privacy status.
- Responsive bitmap AppWidget retained; visual palette aligned to Velocity Dark and emoji category markers replaced with canvas markers. Refresh/data access mechanics unchanged.
- Consolidated detail edits into one Room row update so category/exclusion/tags/split changes cannot overwrite each other from stale snapshots.

## Compatibility proof
- `applicationId`/namespace: `com.abc.expensetracker` unchanged.
- Room database: `kharcha.db`, version 2 unchanged; no entity/schema change and no migration added.
- SMS parser rules/order, ingestion, category keys, merchant normalization/learning keys, account-tail matching, exclusions, split aggregate SQL, and bill scheduling preserved.
- No backend, networking, auth, cloud service, destructive fallback, or production mock data added.

## Verification completed 2026-08-29
- `compileDebugKotlin`: passed.
- Full `testDebugUnitTest assembleDebug`: passed twice after final fixes.
- Finance JVM tests cover excluded transactions, split-aware debit/day net, projection, and percentage comparison.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (17 MB).
- Emulator normal-phone pass: all six screens inspected at 1080×2400.
- Text scaling: Settings inspected at 1.3× without clipping/overlap; emulator restored to 1.0.
- Touch targets: reusable controls and screen actions use 48dp minimums; verified visually in the phone pass.
- Exclusion proof: emulator Activity showed an excluded CheQ card payment with a ₹0 daily contribution; July counted spend was ₹1,584.65. Split-aware behavior is also covered by JVM tests.
- Dark contrast/system bars corrected and visually rechecked.
- Pixel 9 serial `54251VDAQ00090`: installed with `adb install -r`; app resumed successfully and no app ANR was recorded. Existing app data was not cleared.
- Gradle/Kotlin daemons stopped after builds; temporary emulator shut down.
- Interactive Money Pulse follow-up: `testDebugUnitTest assembleDebug` passed on 2026-08-29; APK regenerated at `app/build/outputs/apk/debug/app-debug.apk`. No Room schema, ingestion, or financial-rule changes.

## Final screenshots
- `artifacts/reconcile-revamp/home.png`
- `artifacts/reconcile-revamp/activity.png`
- `artifacts/reconcile-revamp/insights.png`
- `artifacts/reconcile-revamp/plan.png`
- `artifacts/reconcile-revamp/transaction-detail.png`
- `artifacts/reconcile-revamp/settings.png`

## Primary implementation files
- Shell/routes: `app/src/main/java/com/abc/expensetracker/MainActivity.kt`
- Tokens/components/charts: `ui/theme/Theme.kt`, `ui/common/Components.kt`, `ui/charts/VelocityCharts.kt`
- Screens: `ui/screens/HomeScreen.kt`, `ActivityScreen.kt`, `InsightsScreen.kt`, `PlanScreen.kt`, `PlanSheets.kt`, `SettingsScreen.kt`, `SettingsSheets.kt`, `TransactionDetailScreen.kt`
- State/data reads: `ui/vm/ViewModels.kt`, `data/Daos.kt`
- Math/tests: `util/FinanceMath.kt`, `app/src/test/java/com/abc/expensetracker/util/FinanceMathTest.kt`
- Widget: `widget/TodayWidget.kt` and widget resources.

## Known limitations / cleanup
- Exact node-level Figma context was unavailable because of the Figma quota; Material icon geometry and a few static spacing details are the closest Compose equivalents derived from the supplied PDF.
- Money Pulse currently supports tap selection (compatible with the project Compose version); drag scrubbing is not added.
- Widget-only follow-up: changed the dashboard ImageView from `centerCrop` to `fitXY` so launcher aspect-ratio changes cannot crop the card-payment/recent sections. Debug APK built and installed on Pixel 9; no tests run per request.
- The final widget palette compiles and retains its responsive/data behavior, but the widget was not re-added to a launcher during the last screenshot pass.
- `app/src/main/java/graphify-out/` and `tmp/` are generated analysis/PDF-render artifacts. They were left in place rather than deleted without explicit approval.
- The worktree contained substantial pre-existing parser/bills/widget/data/test changes. Do not reset unrelated changes.
