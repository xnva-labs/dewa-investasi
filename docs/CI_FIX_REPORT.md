# Zahra v0.13.0 CI/Build Fix Report

This revision fixes the GitHub Actions SDK bootstrap failure and build blockers found during source audit.

## Fixed
- Android SDK is initialized with `android-actions/setup-android@v4`, which installs/exports `sdkmanager`.
- AGP updated from 9.2.0 to 9.2.1.
- Migrated to AGP 9 built-in Kotlin by removing `org.jetbrains.kotlin.android`.
- Compose compiler plugin aligned to Kotlin/AGP 9.2 default KGP 2.2.10.
- Removed legacy `kotlinOptions` configuration.
- Added `androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0` for `viewModel()` used by `MainActivity`.
- Corrected `BackupContractTest` from schema 4 to the current schema 5.
- Suppressed the notification lint permission warning only after the runtime permission check in `ReminderWorker`.
- Prevented malformed signed bridge payloads from becoming a permanent poison-queue item.
- Gradle setup action updated to v6.
- Added CI/static guards for the SDK tool path, built-in Kotlin migration, ViewModel Compose dependency, and backup schema contract.

## Verification
Static checks pass in the source tree. A full Android Gradle build could not be executed inside this container because no Gradle installation/cache is present and outbound dependency download is unavailable here; final compilation/testing must therefore run in GitHub Actions.
