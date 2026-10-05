#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
A="$ROOT/app/src/main/java/id/fajar/zahra"
G="$ROOT/app/src/main/assets/scripts/world.gd"
python3 - <<PY
from pathlib import Path
app=Path("$ROOT/app/build.gradle.kts").read_text()
main=Path("$A/MainActivity.kt").read_text()
vm=Path("$A/AppViewModel.kt").read_text()
repo=Path("$A/data/AppRepository.kt").read_text()
dao=Path("$A/data/Daos.kt").read_text()
db=Path("$A/data/ZahraDatabase.kt").read_text()
backup=Path("$A/backup/BackupRepository.kt").read_text()
contract_test=Path("$ROOT/app/src/test/java/id/fajar/zahra/backup/BackupContractTest.kt").read_text()
root_gradle=Path("$ROOT/build.gradle.kts").read_text()
game=Path("$G").read_text()
checks={
 "api37_compile": 'compileSdk = 37' in app and 'targetSdk = 36' in app,
 "agp9_builtin_kotlin": 'id("com.android.application") version "9.2.1" apply false' in root_gradle and 'org.jetbrains.kotlin.android' not in app and 'kotlinOptions' not in app,
 "compose_compiler_alignment": 'id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false' in root_gradle,
 "ksp_agp9": 'id("com.google.devtools.ksp") version "2.3.12" apply false' in root_gradle,
 "viewmodel_compose": 'androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0' in app and 'androidx.lifecycle.viewmodel.compose.viewModel' in main,
 "backup_schema_contract": 'currentSchemaIsFive' in contract_test and 'assertEquals(5, BackupContract.CURRENT_SCHEMA)' in contract_test,
 "version": 'versionCode = 13' in app and 'versionName = "0.13.0"' in app,
 "profile-success": 'onSuccess: () -> Unit = {}' in vm and 'vm.saveProfile(name, age) {' in main,
 "mission-edit": 'fun updateMission(' in vm and 'suspend fun updateMission(' in repo and 'suspend fun update(m: MissionEntity)' in dao and 'fun MissionEditScreen(' in main,
 "schedule-parse": 'yyyy-MM-dd HH:mm' in main and 'isLenient = false' in main,
 "backup": 'parseLists' in backup and 'validateBackup' in backup and 'Proof mengarah ke misi yang tidak ada' in backup,
 "game": 'const SAVE_VERSION := 9' in game and '"health": health' in game and 'func _upgrade_business()' in game and 'open_compressed' in game and 'FileAccess.COMPRESSION_ZSTD' in game and 'func _generate_dynamic_event()' in game and 'var boredom := 0.0' in game and 'func _record_activity(action_name: String)' in game and 'goal_state' in game and 'market_volatility' in game and 'coalition_strength' in game and 'cost_of_living' in game,
}
for k,v in checks.items(): print(k, 'PASS' if v else 'FAIL')
assert all(checks.values())
PY
test -f "$ROOT/app/src/test/java/id/fajar/zahra/backup/PortableBackupCryptoTest.kt"
test -f "$ROOT/app/src/test/java/id/fajar/zahra/backup/BackupContractTest.kt"
test -f "$ROOT/app/src/test/java/id/fajar/zahra/core/RewardGuardTest.kt"
test -f "$ROOT/app/src/androidTest/java/id/fajar/zahra/bridge/BridgeSecurityInstrumentedTest.kt"
test -f "$ROOT/app/src/androidTest/java/id/fajar/zahra/data/RewardIdempotencyInstrumentedTest.kt"
echo 'static checks: PASS'
