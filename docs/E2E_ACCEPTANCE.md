# Zahra End-to-End Acceptance Checklist

## A. Fresh install
1. Install a release APK on a clean Android device.
2. Complete profile onboarding.
3. Create one-time and recurring missions.
4. Enable reminders and grant notification permission when requested.
5. Launch the 3D world.

Expected: no crash, no infinite loop, no blank world and no permission prompt outside the feature that needs it.

## B. Bridge round trip
1. Complete a mission in the Android app.
2. Enter the 3D world and wait for bridge polling.
3. Confirm the world receives the mission event.
4. Trigger a game event.
5. Leave the game and return to the Android app.
6. Confirm the event appears once in app history.
7. Restart the app and confirm it does not appear a second time.

Expected: authenticated events survive process boundaries and are consumed exactly once.

## C. Reward duplication / stale UI
1. Open the same mission screen on two activity states or rapidly tap completion.
2. Complete the same one-time mission repeatedly.
3. For a recurring mission, attempt completion again before its next scheduled time.

Expected: one-time mission grants one ledger row; recurring mission grants one row per cycle; duplicate attempts do not add points.

## D. Tamper detection
1. Create a save in the 3D world.
2. On a test/debug device, modify or truncate the current primary save or its sidecar metadata.
3. Launch the world again.
4. Confirm the invalid primary save is rejected in favor of the last valid signed backup when a backup pair exists.
5. Separately test migration from a genuine pre-v0.13 compressed save; after migration, confirm a new signed save pair is written.

Expected: current v0.13 saves require a matching integrity envelope; the only unsigned compressed path is the backward-compatibility migration for older v0.12 data.

## E. Crash/lifecycle
1. Enter the world and change money, NPC state and city state.
2. Background the app.
3. Return after 1 minute.
4. Force-stop the app after a state change.
5. Relaunch.

Expected: state is never reset to an older valid save by normal lifecycle transitions. A force-stop between writes may lose only changes after the last successful save, not corrupt the save file.

## F. Reminder resync
Test:
- device reboot;
- manual clock change;
- timezone change;
- app update/reinstall while preserving data;
- reminder for an archived/paused mission.

Expected: future active reminders are reconstructed; stale/paused reminders do not fire.

## G. Performance profile
On a representative physical device, record at least 10 minutes in these scenes/states:
- idle home;
- moving through the city;
- NPC-heavy period;
- business/economy day transition;
- event-heavy political day;
- background → foreground cycle.

Capture:
- average FPS;
- 1% low or observed minimum FPS;
- process PSS/RAM;
- battery temperature;
- battery drain rate;
- crash/ANR count.

Recommended release gate for this scope:
- no crash/ANR in the test window;
- no save corruption;
- no sustained severe thermal warning;
- no uncontrolled memory growth across repeated day transitions;
- stable interaction and navigation throughout the session.

Do not use a single FPS number as proof of device readiness. Compare the complete profile against the target device class.

## H. Final release build
The release gate is not complete until all of the following are independently verified:
- Android compile succeeds;
- APK/AAB is exported;
- release signing succeeds with the intended key;
- install and upgrade succeed;
- Godot export/runtime succeeds;
- bridge round trip succeeds on device;
- save integrity/recovery succeeds;
- reminder resync succeeds;
- performance profile passes the target device criteria.
