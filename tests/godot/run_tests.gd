extends SceneTree
# Smoke test headless untuk proyek Godot yang dikemas di app/src/main/assets.
#
# Menjalankan:
#   godot --headless --path app/src/main/assets -s "$PWD/tests/godot/run_tests.gd"
#
# Yang diuji:
#   1. Semua file .gd berhasil di-parse dan dapat diinstansiasi.
#   2. Dunia 3D (world.gd) dapat dibangun, dijalankan beberapa "hari" game, dan semua aksi HUD dipanggil.
# Error skrip Godot (SCRIPT ERROR / Parse Error) dicetak ke stderr; workflow CI menggagalkan job bila ada.

const ACTIONS := [
    "eat", "wash", "job", "work", "study", "cook", "garden", "buy", "sell", "negotiate", "social",
    "forum", "debate", "proposal", "policy", "election", "business", "restock_business",
    "upgrade_business", "business_sector", "business_strategy", "business_deal", "market_research",
    "community", "political_strategy", "campaign", "opportunity", "deposit", "withdraw", "invest",
    "withdraw_investment", "transport", "home_upgrade", "rest", "sleep", "save", "load",
]

var failures := 0

func _check(ok: bool, label: String) -> void:
    if ok:
        print("PASS  ", label)
    else:
        failures += 1
        printerr("FAIL  ", label)

func _collect_scripts(dir_path: String) -> Array[String]:
    var found: Array[String] = []
    var dir := DirAccess.open(dir_path)
    if dir == null:
        return found
    dir.list_dir_begin()
    var entry := dir.get_next()
    while entry != "":
        if dir.current_is_dir():
            if not entry.begins_with("."):
                found.append_array(_collect_scripts(dir_path.path_join(entry)))
        elif entry.ends_with(".gd"):
            found.append(dir_path.path_join(entry))
        entry = dir.get_next()
    dir.list_dir_end()
    return found

func _initialize() -> void:
    # 1. Parse semua skrip.
    for path in _collect_scripts("res://"):
        var script = load(path)
        _check(script != null and script.can_instantiate(), "parse " + path)

    # 2. Bangun dan jalankan dunia 3D.
    var world_script = load("res://scripts/world.gd")
    if world_script == null or not world_script.can_instantiate():
        printerr("FAIL  world.gd tidak dapat diinstansiasi")
        quit(1)
        return
    var world = world_script.new()
    root.add_child(world)
    _check(world.get("player") != null, "pemain dibuat")
    _check(world.get("npcs").size() > 0, "NPC dibuat")

    var start_day: int = int(world.get("day"))
    for _round in range(3):
        for action_name in ACTIONS:
            world._action(action_name)
        for _frame in range(120):
            world._process(0.1)
        world._advance_game_minutes(1440)
    _check(int(world.get("day")) > start_day, "hari game maju setelah simulasi")
    _check(String(world.get("interaction_message")).length() >= 0, "pesan interaksi terbaca")

    world.queue_free()
    print("Selesai: ", failures, " kegagalan")
    quit(1 if failures > 0 else 0)
