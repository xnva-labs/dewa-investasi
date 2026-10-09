# Zahra Game Polish v0.16.0

## Included
- Reworked player/NPC proportions into a restrained stylized humanoid: smaller head, layered hair, subtle facial features, torso, arms/hands, legs/shoes, and calmer limb swing.
- Added doors/windows/trim and simple paths/road links around the houses.
- Simplified onboarding and HUD copy, removed decorative emojis from the objective/HUD, and reduced unsolicited random NPC dialogue.
- Capped renderer pixel ratio at 1.5 and shadow map at 512 to reduce GPU/memory pressure on phones.
- Added WebGL capability fallback and a clearer renderer dependency failure screen.
- Added a secondary CDN source for Three.js and INTERNET permission.
- Updated app version to 0.16.0.

## Known limitation
The input archive did not contain `three.min.js`; network access was unavailable in the build environment, so the library could not be vendored into the package. The game therefore needs internet access to load Three.js from a CDN. For fully offline play, place the compatible Three.js r128 build at `app/src/main/assets/game/three.min.js` and change the script tag to load that local file first. The game remains stylized 3D, not photorealistic.

## Checks performed
- `python3 tools/audit/audit_all.py`: PASS.
- `node --check` for each inline JavaScript block in `app/src/main/assets/game/index.html`: PASS.
- Android APK build/device runtime test: NOT RUN; Gradle wrapper JAR, installed Gradle/Android SDK, and Godot binary were not available in this environment.
