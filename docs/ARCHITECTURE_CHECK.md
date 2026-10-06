# Architecture check

Android is the application host. Godot is the 3D engine. The intended data boundary is Room/DataStore on Android and a versioned game save inside Godot. The eventual app-game bridge must use explicit typed events, not direct database access.

Core functionality is offline-first. AI is optional enrichment with deterministic fallbacks.

Goodness Points are gamification only and never a measurement of religious merit.

Camera verification remains optional and must provide a manual fallback.
