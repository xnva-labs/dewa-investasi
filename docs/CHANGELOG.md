# CHANGELOG

## v0.12.0
- Added a lightweight state-driven World Brain for economy, business, politics and city state.
- Added inflation, employment, consumer confidence, business confidence, supply stability, production and city activity metrics.
- Added faction opinion model for business, workers, youth, public and community groups.
- Added political influence, political capital, trust, heat, policy approval and election cycle state.
- Added dynamic business sector and pricing strategy controls.
- Added market research, supplier/business negotiation, community meetings, political strategy and campaign actions.
- Added dynamic event scoring, event cooldowns, contextual economic/political events and business opportunities.
- Added activity memory plus boredom/novelty balancing to reduce repetitive gameplay.
- Added city infrastructure, services, transport quality, business density and public order feedback loops.
- Reworked daily business revenue to react to confidence, demand, competition, strategy and sector.
- Upgraded game save schema to version 8 and switched default save storage to compressed ZSTD binary using Godot FileAccess.
- Added migration from the previous plaintext JSON save when present.
- Bumped Android app version to 0.12.0 / versionCode 12.

## v0.11.0
- Added editable mission records without deleting historical IDs.
- Added safe mission update rescheduling.
- Added absolute date/time validation for mission schedules.
- Added mission pause history events and list toggle result validation.
- Kept portable backup and game health/business systems.

## v0.13.0 - hardening + world deepening
- Authenticated, durable Android ↔ Godot bridge protocol v2 with explicit inbox/outbox direction and event-ID-bound signatures.
- Transactional, idempotent mission reward ledger and recurring-cycle guard.
- Save integrity sidecar using SHA-256 + Android Keystore HMAC, with backup recovery and safer pair-commit rollback.
- Fixed Android→Godot mission-event queue direction and hardened the NPC goal-state assignment regression.
- Lifecycle/clock/timezone reminder resynchronization.
- NPC trust, goals, bounded memory and peer interaction drift.
- Macro economy feedback for wages, purchasing pressure and market volatility.
- Policy budget costs, coalition strength, consultation and policy debt.
- Election difficulty tied to political state.
- Consequence-chain World Brain events.
- Bounded debug performance telemetry.
