# QA report — Zahra v0.18.1

## Fix addressed
The supplied GitHub Actions log reported Kotlin compilation failure with the diagnostic `This material API is experimental and is likely to change or to be removed in the future.` The likely triggering Material 3 API is `CenterAlignedTopAppBar` in `IslamicContentScreen.kt`; that file now explicitly opts in to `ExperimentalMaterial3Api`. The camera screen also declares the same opt-in defensively.

## Added
- Cirebon daily prayer timetable from AlAdhan's `timingsByCity` API.
- Optional daily reminders for Subuh, Dzuhur, Ashar, Maghrib, and Isya.
- Optional sahur reminder 30 minutes before the returned Imsak time and an iftar reminder at Maghrib.
- Calm notification channel, Android 13+ notification permission prompt, daily rescheduling, and dashboard shortcut.

## Verification limits
- Static source checks, duplicate-import scan, XML parsing, and archive integrity are run for this package.
- A full Android Gradle build and device test must still run in GitHub Actions; the current workspace does not have a Gradle executable/wrapper script and Android SDK configured.
- WorkManager notifications are battery-aware and may not fire at the exact minute. Prayer times can vary by calculation method and local authority; confirm against Cirebon's official/local mosque timetable.
