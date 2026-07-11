# Handoff — Reconcile (formerly Kharcha)

## Goal
Offline Android expense tracker (Kotlin + Compose M3) auto-logging Indian bank/UPI
SMS. **v2 complete, verified on emulator, installed on user's phone (54251VDAQ00090).**

## v2 completed (2026-07-11)
- **CC bill payment handling**: credit-side "payment received" SMSes (SBI/HDFC/HSBC/
  Axis samples) rejected + auto-mark card paid via tail match. Debit-side (CheQ/CRED)
  stored as excluded 💳 CC Payment. Verified live: HSBC payment SMS → card flipped to
  Paid ✓; CheQ ₹36,925.87 stored but totals unchanged.
- **Excluded/self-transfer flow**: per-txn toggle + "always" learning by merchant OR
  sender+direction (covers merchant-less bank notices — user asked for this explicitly).
- **Card bills**: 6 cards seeded (slice due 6, SBI 6, HDFC 4, Axis 30, IndusInd 12,
  HSBC 11; statement days are guesses ~17 days before due — user can edit). Daily 10am
  reminders from due−3 until paid; AlarmManager exact-if-permitted + boot re-arm.
  Cards UI in Budgets tab: mark paid/undo, edit/add/delete, statement cycle sheet.
- **Insights**: forecast line in hero card, anomaly cards (≥2.5× merchant median),
  envelope budgets (rollover), duplicate review queue in More (6h window, dismissible).
- **UX**: tags + tag filters, split tracking ("owed to you", settle in More), widget
  (today spend), app shortcut (quick add), rebrand → Reconcile, logo as adaptive icon
  (bg #0F1724), blue/black-white theme with gradient hero.
- **DB v1→2 migration** written + verified by installing v2 over live v1 data.
- 28 unit tests green. Full build green.

## Current state
- Phone has v2 over its v1 data (migration tested on emulator first).
- Emulator AVD `kharcha_test` exists (shut down). ImageMagick installed via brew.
- Repo uncommitted (user hasn't asked for git).

## Key gotchas
- `gradlew` needs JAVA_HOME exported (see CLAUDE.md).
- NEVER destructive DB fallback — real data on phone. Write Migrations.
- Statement days for cards are estimates; user edits in-app (Budgets → card → Edit).
- SBI card has no tail in its payment SMS — matched by bank-name when unique.
- Emulator SMS sender can't contain `-`.
- zsh doesn't word-split unquoted vars — caused a stray "mipmap-xxhdpi 324" dir once.

## Next steps (ideas)
- POST_NOTIFICATIONS denied path: reminders silently no-op — maybe surface a hint.
- Notification action button "Mark paid" (direct, without opening app).
- Backup/restore of the Room DB file.
- Per-card statement-day auto-detection from statement SMSes.
