# Zahra v0.13.0 Hardening + Deep Simulation Pass

This release keeps the Zahra Master Build Specification v1.0 baseline intact and adds safeguards and deeper simulation systems without removing core features.

## 1. Android ↔ Godot bridge
- Protocol v2 is required on both directions.
- Every bridge event is an individual atomically-written envelope.
- Payloads are authenticated with a per-install Android Keystore HMAC-SHA256 key.
- Event IDs are UUID-validated.
- Godot performs a runtime bridge crypto smoke test before treating the bridge as ready.
- Android and Godot validate the protocol version before accepting events.
- App-side event consumption is idempotent through `bridgeEventId`.
- Queue size and event count are bounded to avoid memory/disk abuse.

## 2. Reward integrity / anti-duplication
- Point ledger has a unique `sourceKey`.
- One-time missions use `MISSION:<id>:ONCE`.
- Recurring missions use `MISSION:<id>:CYCLE:<schedule-anchor>`.
- Completion is re-read from the database inside a transaction, so stale UI objects cannot grant a reward after state changed.
- A recurring mission cannot complete again before its next scheduled cycle.
- Reward claiming is checked against the live points total, not just the cached unlocked flag.
- Backup validation rejects duplicate ledger source keys, invalid point ranges, invalid mission references and reward-state contradictions.

## 3. Crash / lifecycle hardening
- Game autosaves every 45 seconds.
- Game saves on pause/window-close notifications.
- Save writes use a temporary file followed by atomic rename.
- Save metadata contains SHA-256 plus a Keystore-backed signature on Android.
- Corrupt primary saves can fall back to the last backup pair.
- A successful recovery is marked for immediate repair-save on next ready.
- Reminder scheduling is rebuilt on boot, app replacement, clock changes and timezone changes.

## 4. Game save integrity
- Default save format: Godot `store_var()` + ZSTD compression.
- Sidecar signature metadata binds the compressed bytes to a SHA-256 digest.
- Metadata version and declared save version are validated.
- Debug/editor-only `UNSIGNED-DEV` fallback is not accepted by a non-debug build.
- Legacy JSON saves and the older v0.12 compressed save are migrated to the protected format.
- When a current compressed save has missing/invalid metadata, a valid backup pair is preferred before compatibility migration. The compatibility path exists only to preserve older v0.12 data and is not a substitute for signed current saves.

## 5. Performance safeguards
- NPC motion updates are throttled instead of running full simulation every frame.
- Environment and HUD updates are throttled.
- Bridge polling is low frequency.
- World simulation is primarily daily, not per-render-frame.
- NPC state count is deliberately small in the current city slice.
- Debug builds collect a bounded 300-sample `user://zahra_perf.tsv` profile containing FPS, minimum FPS, PSS, draw calls, object count, battery and temperature telemetry.

## 6. NPC deepening
NPCs now maintain:
- needs: energy, hunger, social need;
- mood, opinion, trust and relationship;
- occupation and work performance;
- political affinity;
- a bounded memory trail;
- a current goal-state such as income, security, community, stability or growth;
- lightweight peer relationship drift each simulated day.

The positional `goal` vector remains separate from `goal_state` so navigation stays compatible.

## 7. Dynamic economy
Daily simulation now feeds back:
- inflation;
- consumer confidence;
- business confidence;
- employment;
- wage index;
- household purchasing pressure;
- supply stability;
- production;
- market volatility;
- market sentiment;
- per-item demand and price movement;
- investment value movement.

Business profit depends on sector, pricing strategy, marketing, trust, supplier quality, competition and the macro state.

## 8. Politics and policy
Policies now consume government budget and political capital, shift faction opinions, influence coalition strength and create policy debt when treasury capacity is insufficient.

Political state tracks:
- influence;
- political capital;
- public trust;
- political heat;
- policy approval;
- coalition strength;
- policy debt;
- public consultation.

Election difficulty depends on coalition/faction conditions and political heat. Campaigning only works inside the election window and changes support based on the political state.

## 9. Event Director / World Brain
The event director now mixes:
- pressure-based candidate scoring;
- weighted selection;
- cooldowns;
- boredom/novelty pressure;
- economic/political/community prerequisites;
- consequence-chain events based on the previous event.

Added chain events include cost-of-living pressure, market recovery, coalition shifts and policy backlash.

## 10. Runtime acceptance gates
Source-level checks cannot prove APK/device behavior. Before release, run the device checklist in `docs/E2E_ACCEPTANCE.md` on at least one physical Android device and one clean install/upgrade path.
