# Zahra v0.13.0 status

## Source implemented
- Safe onboarding and profile persistence
- Flexible lists/checklists
- Mission create/edit/complete/pause/resume/archive
- Recurrence and absolute date/time scheduling
- Goodness Points and threshold rewards
- Opt-in reminder scheduling
- Camera/pose/object proof evidence
- Portable password-encrypted backup/restore
- Godot v2 bridge boundary with Keystore HMAC authentication and runtime crypto smoke test
- 3D life simulation with one playable character
- NPC schedules, needs, movement, social state, trust, goals, memory and peer drift
- Business/economy simulation with demand, confidence, inflation, supply stability, wages, household pressure, volatility and investment feedback
- Business sector, pricing strategy, supplier quality, customer trust and market research
- Political influence, political capital, trust, heat, coalition strength, consultation and policy debt
- Faction opinion model and policy approval
- Election cycle and campaign window
- Dynamic event director with weighted selection, cooldowns and consequence-chain events
- Player activity memory, boredom/novelty balancing and dynamic opportunities
- City infrastructure, services, transport quality, business density and public order
- Compressed game saves using Godot ZSTD, SHA-256 sidecar integrity and Android Keystore signature plus legacy JSON migration path

## Verified here
- ZIP integrity
- Updated static source checks
- RewardGuard pure-JVM smoke test
- Source-level bridge/reward/save/reminder/NPC/economy/politics/event/performance checks
- Source-level GDScript duplicate-function and bare-assignment sanity check
- Instrumented Android tests added for Keystore bridge, bridge transport, and Room reward idempotency, but not runnable here

## Not yet verified
- Android SDK/Gradle compile and APK/AAB export (no Gradle executable/Android SDK is available in this environment)
- Instrumented/device tests (added, not executed)
- Godot parser/export/runtime (no Godot executable is available here)
- End-to-end bridge runtime on physical device
- Performance, battery and memory profiling
- Release signing/install upgrade

## Performance design
- Daily world simulation instead of expensive full simulation every frame
- Event cooldowns prevent repeated event spam
- Recent-action window bounds anti-boredom memory
- NPC count remains small in the current world slice
- Save state is binary-compressed with ZSTD instead of plaintext JSON by default
