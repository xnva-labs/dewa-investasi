# Zahra v0.13.0

Locked personal app + 3D life simulation source milestone, hardened for bridge/reward/save lifecycle and deeper world simulation.

## Core
- One playable character, NPCs non-playable.
- Android native host + embedded Godot world.
- Offline-first app/game bridge.
- Business and political systems are the main long-term progression drivers.

## New in v0.13.0
- Lightweight Dynamic World Brain.
- State-driven economy: inflation, employment, confidence, supply, production and city activity.
- Business engine: sectors, pricing strategy, supplier quality, customer trust, competition and market research.
- Political engine: influence, political capital, trust, political heat, factions, policy approval and election cycles.
- Dynamic events and opportunities selected from world conditions with scoring and cooldowns.
- Anti-monotony system using recent activity memory, boredom and novelty instead of spawning endless quests.
- City feedback loop: infrastructure, services, transport quality, business density and public order.
- Compressed binary save using Godot ZSTD, with SHA-256 integrity metadata, Android Keystore signature, backup recovery, and migration from the older JSON save format.
- Authenticated Android ↔ Godot bridge v2 with explicit inbox/outbox direction, atomic event files and duplicate-resistant event IDs.
- Transactional mission rewards with unique ledger source keys and recurring-cycle protection.
- Reminder resynchronization for reboot, clock, timezone and app replacement.
- Deeper NPC, economy, political and World Brain consequence systems.
- Bounded debug performance telemetry for FPS, memory/PSS, draw calls, battery and temperature.

## Performance principle
The simulation is intentionally not evaluated at full complexity every rendered frame. Most economy, politics, business and NPC state changes are processed on meaningful time steps, while the rendered world handles movement and presentation continuously.

## Verification state
- ZIP/source integrity: verified for this checkpoint.
- Static source checks: verified.
- RewardGuard pure-JVM smoke test: verified.
- Instrumented Android regression tests: added but not executable in this environment.
- Android compile/APK export: not verified in this environment.
- Godot parser/export/runtime: not verified because the Godot executable is not installed here.
- Physical-device, battery, memory and release-signing validation: pending.
