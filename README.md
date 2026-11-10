# Kept

A minimalist, offline Android reminder and birthday app with a home-screen widget that rotates through whatever needs you today.

Native Kotlin · Jetpack Compose · Room · DataStore · AlarmManager · RemoteViews. No network, accounts, analytics or backend.

## Build and install

Requirements: JDK 17, Android SDK 35 (build-tools 34/35). The SDK path is read from `local.properties` (`sdk.dir=...`, created for you; Android Studio rewrites it).

```bash
./gradlew assembleDebug          # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 18 unit tests (date logic + JSON export)
adb install -r dist/Kept-debug.apk
```

Or open the folder in Android Studio and press Run. minSdk 26 (Android 8.0), target/compile 35.

## Add the widget

Settings (sliders icon on the Today screen) -> **Add widget to home screen**, or long-press the home screen -> Widgets -> Kept.
Resize it: ~2x2 is the compact layout, 3+ columns x 1 row the wide one, 3+ columns x 2+ rows the large one.

## How it works

| Concern | Approach |
|---|---|
| Eligibility | `domain/Eligibility.kt` is the single source of truth (pure `LocalDate` logic). The app, widget and notifications all call it. |
| Storage | Room (`kept.db`, schema exported to `app/schemas`, explicit migrations only) + DataStore preferences. Dates are stored as epoch-days, never as timestamps. |
| Notifications | One inexact `AlarmManager` alarm per day at the configured time (`setAndAllowWhileIdle`, no exact-alarm permission). Posts one notification per active item (stable id, so no duplicates), at most once per calendar day. Clearing/deleting/editing withdraws the notification. Rebuilt on boot, app update, clock/zone change. |
| Widget rotation | `ViewFlipper` (`autoStart`, 14 s) holding one child `RemoteViews` per item. Runs inside the launcher; zero work by Kept while rotating. |
| Widget refresh | Only when data changes, at local midnight (alarm + `DATE_CHANGED`), on resize, and via the hourly `updatePeriodMillis`. |

### Rules
- Reminder: active from `date - leadDays` until cleared; after its date it is *overdue*, never auto-removed.
- Birthday: yearly. Relevant from `birthday - leadDays` through the birthday itself, then drops out until next year.
- Anniversaries live on the Birthdays tab and follow the birthday rules; the optional year shows as "N years".
- **Feb 29 birthdays are observed on Feb 28 in non-leap years.**
- Search (reminders and birthdays/anniversaries): every word must match a name, or the start of the month (3+ letters), e.g. `alex nov`.
- Widget/notification order: most overdue first, then nearest; reminders before birthdays on ties.

### Platform limitations
- Alarms are inexact: delivery can be a minute or more late (more in Doze). Fine for a daily reminder.
- A force-stopped app receives no broadcasts until it is opened once (Android rule).
- Widgets cannot use custom fonts, so the widget uses the system font; the app uses Space Grotesk (OFL, `licenses/`).
- Some OEM launchers/battery savers may pause widget animation; the widget still shows correct content.
- The rotation interval is fixed (declared in the widget layout), so it is not offered as a setting.

## Design

Dark, flat, geometric. Reminders are **squares**, birthdays are **circles** everywhere (list rows, widget, tab bar, empty state). One amber accent; coral only for "overdue". See `DESIGN.md`.

## Skills used

`.claude/skills/` holds the Impeccable design skill and a selected subset of `rcosteira79/android-skills`
(android-dev, compose, android-data-layer, datastore, kotlin-coroutines, kotlin-flows, android-testing, android-ux, android-debugging).
That collection has no app-widget skill. For the full plugin install, run inside Claude Code: `/plugin marketplace add rcosteira79/android-skills` then `/plugin install android-skills@android-skills`.
