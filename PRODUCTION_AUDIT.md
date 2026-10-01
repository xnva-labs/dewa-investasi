# SmartEyeX Production Audit

This repository includes a repeatable static integrity scan at `scripts/production-scan.py`.

## Current checks
- XML parsing across Android resources and manifest.
- Kotlin delimiter sanity across the source tree.
- Android resource reference checks for strings, drawables, and mipmaps.
- Merge-conflict marker detection.
- Obvious private-key/API-key literal detection.
- CI integration before unit tests and Android builds.

## Architecture upgrades
- Voice-first interaction layer with local intent routing.
- Companion modes and contextual companion profile.
- Bounded synthetic emotional state for expressive behavior, without claiming consciousness.
- Encrypted personal model for preferences, goals, and interaction count.
- Philosophy/science/mathematics/engineering/invention/teaching/research reasoning profiles.
- Optional voice prosody personalization storing bounded statistics instead of raw audio.
- Cloud context is gated by Memory consent.

## Important implementation boundary
Android platform TTS can adapt pitch/rate, but it cannot by itself guarantee a 90% identity-level voice clone. A production voice-cloning provider or on-device voice model must be integrated behind a dedicated consented voice-synthesis service before making that claim.

## Build verification
The repository CI performs the authoritative Android `test`, `lint`, and build steps on a runner with Android/Gradle tooling. The local audit environment used during this preparation does not contain the Android SDK/Gradle distribution, so a successful local `assembleRelease` is not claimed here.
