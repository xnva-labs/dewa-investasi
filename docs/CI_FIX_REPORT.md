# Zahra v0.13.0 CI/Build Fix Report

This revision addresses the GitHub Actions SDK bootstrap failure and the subsequent Android AAR metadata failure without changing application features or runtime target behavior.

## Fixed
- Android SDK is initialized with `android-actions/setup-android@v4`, which installs/exports `sdkmanager` and `adb`.
- AGP updated from 9.2.0 to 9.2.1.
- Migrated to AGP 9 built-in Kotlin by removing `org.jetbrains.kotlin.android`.
- Compose compiler plugin aligned to the AGP 9.2 default KGP 2.2.10.
- Removed legacy `kotlinOptions` configuration.
- Added `androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0` for `viewModel()` used by `MainActivity`.
- Corrected `BackupContractTest` from schema 4 to the current schema 5.
- Suppressed the notification lint permission warning only after the runtime permission check in `ReminderWorker`.
- Prevented malformed signed bridge payloads from becoming a permanent poison-queue item.
- Gradle setup action updated to v6.
- **compileSdk updated from 36 to 37** because the resolved current Compose/AndroidX dependencies require API 37 for compilation.
- **targetSdk remains 36** so this fix does not opt the app into Android 17 runtime behavior.
- Removed the redundant/deprecated `sourceSets["main"].assets.srcDir(...)` call; `src/main/assets` is already the standard Android asset directory.
- CI/static guards updated to enforce compileSdk 37 + targetSdk 36 and SDK package installation.

## Root cause of the latest CI failure
`androidx.navigation:navigation-compose:2.10.2`, Compose 1.12.1 artifacts, `androidx.core:core-ktx:1.19.1`, and Lifecycle 2.11.x artifacts in the resolved dependency graph require compilation against Android API 37. The project was still compiling against API 36, so `:app:checkDebugAarMetadata` correctly failed before Kotlin/Java compilation.

## Feature preservation
No application source implementation was changed in this revision. The change from the previous fixed ZIP is limited to:
- build configuration (`compileSdk`),
- CI SDK selection/validation,
- removal of one redundant asset source declaration,
- static audit/result documentation.

The previously applied hardening fixes remain intact.

## Verification performed in this container
- Static project checks: PASS
- Source audit: PASS
- Python audit syntax: PASS
- GDScript duplicate-function sanity: PASS
- Feature-source comparison against the previous fixed ZIP: no Kotlin/Java/GDScript/resource implementation changes from this revision

A full Android Gradle build cannot be truthfully claimed here because this container does not have an Android SDK/Gradle installation or the external dependency cache. GitHub Actions is the final build authority.
