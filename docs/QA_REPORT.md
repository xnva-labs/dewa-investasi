# QA report — Zahra v0.18.0

## Automated checks completed in this workspace

- `python3 tools/audit/audit_all.py`: passed, no findings.
- Kotlin source import scan: passed, no duplicated import lines.
- Pure Kotlin progression, difficulty, recurrence, and reward-guard sources: compiled successfully with `kotlinc`.
- Smoke checks passed for difficulty bands, scaled water rewards, annual level progression, plant stages, and alternate-day Puasa Daud recurrence.
- Progression tests were expanded to ensure leaf/message milestones continue after the plant's mature visual stage.
- `soft_chime.wav` verified as valid mono PCM WAV (22,050 Hz, 0.88 seconds).
- Full-source syntax diagnostics were scanned; no parser-error patterns were detected.

## Not verified here

A complete Android/Gradle build, Room KSP generation, JVM JUnit suite, instrumentation tests, and runtime testing on a device/emulator could not be run in this environment: there is no installed `gradle` executable, no `gradlew` or wrapper JAR in this project snapshot, and no Android SDK environment configured. The static audit and pure-Kotlin smoke checks are useful but are not a substitute for that full build.

## Content and progression notes

- Hadith and dua text are loaded from third-party public APIs and therefore require internet access. The app shows source/reference details and encourages checking references for study.
- Activity difficulty is a local heuristic based on title/description/category, not a trained or cloud AI model; users should treat its estimate as adjustable guidance.
- EXP, water, cat food, and levels are game-like habit motivators, not measures of religious merit.
- Affectionate leaf messages are explicitly labeled private notes and not presented as hadith or Quran quotations.
