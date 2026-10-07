# Zahra release shrinker rules.
# Godot bridge is loaded by class name from Android metadata/reflection.
-keep class id.fajar.zahra.bridge.ZahraGodotPlugin { *; }
-keep class id.fajar.zahra.GameActivity { *; }

# Bridge contract / serialization entry points.
-keep class id.fajar.zahra.bridge.** { *; }

# Preserve worker/receiver lifecycle classes referenced by Android framework.
-keep class id.fajar.zahra.reminder.** { *; }

# Keep Room's generated database implementation and schema metadata safe.
-keep class id.fajar.zahra.data.** { *; }
