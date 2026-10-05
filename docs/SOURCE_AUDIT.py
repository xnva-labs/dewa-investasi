from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
app = (ROOT / 'app/build.gradle.kts').read_text()
manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
bridge = (ROOT / 'app/src/main/java/id/fajar/zahra/bridge/BridgeSecurity.kt').read_text()
gamebridge = (ROOT / 'app/src/main/java/id/fajar/zahra/bridge/GameBridge.kt').read_text()
plugin = (ROOT / 'app/src/main/java/id/fajar/zahra/bridge/ZahraGodotPlugin.kt').read_text()
repo = (ROOT / 'app/src/main/java/id/fajar/zahra/data/AppRepository.kt').read_text()
dao = (ROOT / 'app/src/main/java/id/fajar/zahra/data/Daos.kt').read_text()
entity = (ROOT / 'app/src/main/java/id/fajar/zahra/data/Entities.kt').read_text()
reminder = (ROOT / 'app/src/main/java/id/fajar/zahra/reminder/ReminderWorker.kt').read_text()
receiver = (ROOT / 'app/src/main/java/id/fajar/zahra/reminder/ReminderRescheduleReceiver.kt').read_text()
game = (ROOT / 'app/src/main/assets/scripts/world.gd').read_text()

checks = {
    'android_sdk_target': 'compileSdk = 37' in app and 'targetSdk = 36' in app and 'minSdk = 24' in app,
    'version_0_13': 'versionCode = 13' in app and 'versionName = "0.13.0"' in app,
    'backup_disabled': 'android:allowBackup="false"' in manifest,
    'backup_rules': (ROOT / 'app/src/main/res/xml/backup_rules.xml').exists() and (ROOT / 'app/src/main/res/xml/data_extraction_rules.xml').exists(),
    'bridge_protocol_2': 'PROTOCOL_VERSION = 2' in bridge and 'PROTOCOL_VERSION' in gamebridge and 'getBridgeProtocolVersion' in plugin,
    'event_id_bound_signature': 'fun eventMessage(protocol: Int, eventId: String, payload: String)' in bridge and 'verifyEvent(protocol, eventId, payload, signature)' in gamebridge and 'verifyEvent' in plugin,
    'bridge_event_unique': 'unique = true' in entity and 'bridgeEventId' in entity and 'insertBridgeEvent' in dao,
    'point_source_unique': 'sourceKey' in entity and 'addIfNew' in dao and 'OnConflictStrategy.IGNORE' in dao and 'sourceKey =' in repo,
    'recurring_guard': 'RewardGuard.canCompleteRecurring' in repo,
    'save_integrity': 'get_sha256' in game and 'zahra_world.sig' in game and 'UNSIGNED-DEV' in game and 'save_repair_pending' in game,
    'lifecycle_save': 'NOTIFICATION_APPLICATION_PAUSED' in game and 'NOTIFICATION_WM_CLOSE_REQUEST' in game,
    'reminder_resync': 'ACTION_BOOT_COMPLETED' in receiver and 'ACTION_TIMEZONE_CHANGED' in receiver and 'ACTION_MY_PACKAGE_REPLACED' in receiver and 'TIME_SET' in manifest,
    'stale_reminder_guard': 'mission.scheduledAt != scheduledAt' in reminder,
    'npc_depth': 'goal_state' in game and 'trust' in game and 'memory' in game and '_simulate_npcs_day' in game and 'goal_state = "security"' in game and 'goal_state = "growth"' in game and '\n            goal = "' not in game,
    'economy_depth': 'market_volatility' in game and 'consumer_confidence' in game and 'supply_stability' in game and 'household_pressure' in game,
    'politics_depth': 'coalition_strength' in game and 'policy_debt' in game and '_run_election' in game and '_toggle_policy' in game,
    'event_director': '_generate_dynamic_event' in game and 'cost_of_living' in game and 'coalition_shift' in game and 'policy_backlash' in game,
    'bridge_direction': 'enqueueEnvelope(context, INBOX, eventId, payload)' in gamebridge and 'enqueueEnvelope(context, OUTBOX, eventId, payload)' not in gamebridge.split('private fun enqueueEnvelope', 1)[0],
    'perf_telemetry': '_capture_performance' in game and 'zahra_perf.tsv' in game and 'battery_temp' in game,
    'android_tests': all((ROOT / p).exists() for p in [
        'app/src/androidTest/java/id/fajar/zahra/bridge/BridgeSecurityInstrumentedTest.kt',
        'app/src/androidTest/java/id/fajar/zahra/data/RewardIdempotencyInstrumentedTest.kt',
        'app/src/androidTest/java/id/fajar/zahra/bridge/GameBridgeTransportInstrumentedTest.kt',
    ]),
}

for key, value in checks.items():
    print(f'{key}: {"PASS" if value else "FAIL"}')
assert all(checks.values()), [key for key, value in checks.items() if not value]

lines = game.splitlines()
funcs = [line.strip().split('(')[0][5:] for line in lines if line.strip().startswith('func ')]
duplicates = sorted({name for name in funcs if funcs.count(name) > 1})
print('world_gd_functions:', len(funcs))
print('world_gd_duplicate_functions:', duplicates)
assert not duplicates
assert '"goal": Vector3' in game and 'goal_state =' in game

# Heuristic GDScript check for bare assignments to names not declared as members,
# function parameters, or function-local variables. This is deliberately conservative.
class_names = set()
for line in lines:
    if line and not line[0].isspace():
        m = re.match(r'(?:var|const)\s+([A-Za-z_]\w*)', line)
        if m:
            class_names.add(m.group(1))

suspicious = []
current = None
locals_by_func = {}
for lineno, line in enumerate(lines, 1):
    fm = re.match(r'func\s+(\w+)\s*\(([^)]*)\)', line)
    if fm:
        current = fm.group(1)
        locals_by_func[current] = set()
        for pm in re.finditer(r'([A-Za-z_]\w*)\s*:', fm.group(2)):
            locals_by_func[current].add(pm.group(1))
        continue
    if current is None:
        continue
    indent = len(line) - len(line.lstrip())
    if indent == 0:
        current = None
        continue
    lm = re.match(r'\s+(?:var|const)\s+([A-Za-z_]\w*)', line)
    if lm:
        locals_by_func[current].add(lm.group(1))
    am = re.match(r'\s+([A-Za-z_]\w*)\s*=\s*(?!=)', line)
    if am:
        name = am.group(1)
        if name not in class_names and name not in locals_by_func[current]:
            suspicious.append((lineno, current, name, line.strip()))
print('gdscript_bare_assignment_unknown:', suspicious)
assert not suspicious
