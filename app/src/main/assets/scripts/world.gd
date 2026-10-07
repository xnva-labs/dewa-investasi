extends Node3D

# Zahra v0.17 Realistic Life World — procedural realistic 3D foundation.
# One playable character, autonomous NPCs, economy, society and government.
# No network dependency is required for the simulation loop.

const SAVE_VERSION := 17
const WORLD_SIZE := 44
const SAVE_PATH_COMPRESSED := "user://zahra_world.zsav"
const SAVE_PATH_LEGACY := "user://zahra_world.json"
const SAVE_META_PATH := "user://zahra_world.sig"
const SAVE_BACKUP_PATH := "user://zahra_world.zsav.bak"
const SAVE_BACKUP_META_PATH := "user://zahra_world.sig.bak"
const SAVE_COMPRESSION := FileAccess.COMPRESSION_ZSTD
const SAVE_META_VERSION := 1
const PERF_LOG_PATH := "user://zahra_perf.tsv"
const PERF_LOG_LIMIT := 300

var material_cache: Dictionary = {}
var player: CharacterBody3D
var camera: Camera3D
var camera_yaw := 38.0
var camera_pitch := 30.0
var camera_distance := 12.0
var camera_touch_active := false
var last_camera_touch := Vector2.ZERO
var hud: CanvasLayer
var sun_light: DirectionalLight3D
var world_env: Environment
var status_label: Label
var message_label: Label
var touch_vector := Vector2.ZERO
var game_clock_accumulator := 0.0
var npc_schedule_slot := -1
var prayer_notice_key := ""
var policy_cycle := 0
var investment_value := 0.0
var market_sentiment := 50.0
var transport := "Walk"
var home_level := 1
var home_cleanliness := 82.0
var current_job := "Freelancer"
var job_index := 0
var job_catalog := [
    {"title": "Freelancer", "salary": 45.0, "skill": "Technology", "min": 0.0},
    {"title": "Shopkeeper", "salary": 55.0, "skill": "Business", "min": 10.0},
    {"title": "Designer", "salary": 80.0, "skill": "Technology", "min": 15.0},
    {"title": "Public Officer", "salary": 72.0, "skill": "Leadership", "min": 20.0},
]

var day := 1
var time_minutes := 480
var weather := "Sunny"
var money := 120.0
var bank := 0.0
var energy := 92.0
var health := 94.0
var hunger := 18.0
var hygiene := 82.0
var mood := 75.0
var stress := 12.0
var reputation := 10.0

var skills := {
    "Cooking": 5.0,
    "Communication": 5.0,
    "Negotiation": 5.0,
    "Business": 5.0,
    "Study": 5.0,
    "Fitness": 5.0,
    "Gardening": 5.0,
    "Leadership": 3.0,
    "PublicSpeaking": 3.0,
    "Technology": 3.0,
}

var inventory := {
    "Food": 3,
    "Seeds": 5,
    "Book": 1,
}

var prices := {
    "Food": 10.0,
    "Seeds": 8.0,
    "Book": 25.0,
}

var demand := {
    "Food": 1.0,
    "Seeds": 1.0,
    "Book": 1.0,
}

var public_support := 50.0
var government_budget := 5000.0
var tax_rate := 0.08
var policies := {
    "small_business": false,
    "education_fund": false,
    "public_transport": false,
    "market_fairness": false,
    "community_health": false,
}

var own_business := {
    "owned": false,
    "name": "My Food Stall",
    "capital": 0.0,
    "reputation": 0.0,
    "employees": 0,
    "open": false,
    "level": 1,
    "stock": 12,
    "maintenance": 8.0,
}

var businesses := [
    {"name": "Mini Market", "base": 85.0, "workers": 2, "cost": 30.0, "open": true},
    {"name": "Barbershop", "base": 60.0, "workers": 1, "cost": 20.0, "open": true},
    {"name": "Cafe", "base": 75.0, "workers": 2, "cost": 35.0, "open": true},
]

var npc_names := ["Alya", "Nadia", "Raka", "Dimas", "Sinta", "Bagas", "Mira", "Rafi", "Lina", "Tio"]
var npc_traits := [
    ["Friendly", "Talkative"],
    ["Introverted", "Patient"],
    ["Ambitious", "Risk-taking"],
    ["Serious", "Frugal"],
    ["Creative", "Talkative"],
    ["Competitive", "Ambitious"],
    ["Generous", "Patient"],
    ["Cautious", "Frugal"],
    ["Friendly", "Creative"],
    ["Serious", "Cautious"],
]
var npcs: Array[Dictionary] = []
var npc_path := {}
var grid := AStarGrid2D.new()

var business_revenue := 0.0
var interaction_message := ""
var game_unlocks := {}
var bridge_plugin = null
var proposal_state := {
    "active": false,
    "title": "",
    "cost": 0.0,
    "benefit": 0.0,
    "support": 0.0,
    "evidence": 0.0,
}

var election_state := {
    "active": false,
    "support": 0.0,
    "opponent": 0.0,
    "won": false,
    "term_days": 0,
}


# v0.16 complete-life systems: the simulation remains deterministic/offline-first.
var political_career := {
    "rank": "Citizen",
    "organization": "Independent",
    "years_active": 0,
    "public_service": 0.0,
    "policy_wins": 0,
    "constituency": 50.0,
    "integrity": 82.0,
}
var enterprise_state := {
    "employees": 0,
    "wage_bill": 0.0,
    "supplier_reliability": 74.0,
    "production_capacity": 20.0,
    "monthly_tax_paid": 0.0,
    "monthly_profit": 0.0,
    "contracts": 0,
    "market_share": 3.0,
}
var civic_state := {
    "district": "Pusat Kota",
    "services": 60.0,
    "safety": 76.0,
    "education": 62.0,
    "health": 64.0,
    "mobility": 52.0,
    "housing": 58.0,
}
var religious_calendar_state := {
    "ramadan_day": 0,
    "eid_ready": false,
    "study_sessions": 0,
    "mosque_attendance": 0,
    "community_events": 0,
    "waqf_total": 0.0,
}
var simulation_accumulator := 0.0
var world_state_emit_accumulator := 0.0
var detail_labels: Array[Label3D] = []
var street_props: Array[Node3D] = []
var save_path := SAVE_PATH_COMPRESSED

# Dynamic World Brain: lightweight state-driven simulation.
var world_economy := {
    "inflation": 3.2,
    "employment": 92.0,
    "consumer_confidence": 62.0,
    "business_confidence": 58.0,
    "supply_stability": 84.0,
    "city_activity": 60.0,
    "production": 100.0,
    "wage_index": 100.0,
    "household_pressure": 24.0,
    "market_volatility": 18.0,
}
var factions := {
    "business": 52.0,
    "workers": 50.0,
    "youth": 55.0,
    "public": 50.0,
    "community": 58.0,
}
var political_state := {
    "influence": 6.0,
    "political_capital": 20.0,
    "trust": 48.0,
    "political_heat": 22.0,
    "election_cycle_day": 0,
    "next_election_day": 28,
    "policy_approval": 50.0,
    "coalition_strength": 35.0,
    "policy_debt": 0.0,
    "consultation": 20.0,
}
var city_state := {
    "infrastructure": 58.0,
    "services": 60.0,
    "transport_quality": 52.0,
    "business_density": 45.0,
    "public_order": 76.0,
}
var activity_memory := {}
var recent_actions: Array[String] = []
var recent_events: Array[String] = []
var boredom := 0.0
var novelty := 50.0
var event_cooldowns := {}
var active_opportunity := {"id": "", "title": "", "kind": "", "expires": 0}
var business_state := {
    "sector": "Food",
    "price_strategy": "Balanced",
    "ethics_mode": "Fair & Halal",
    "supplier_quality": 72.0,
    "marketing": 35.0,
    "customer_trust": 50.0,
    "competitive_pressure": 30.0,
    "halal_integrity": 88.0,
    "growth_points": 0.0,
}
var world_revision := 0
var world_dirty := true
var current_action_category := "life"
var action_container: GridContainer
var action_category_label: Label

# Faith/community/ethical-life signals used as game systems; not a measure of real-world merit.
var religious_state := {
    "faith": 60.0,
    "religion_knowledge": 5.0,
    "community": 50.0,
    "prayers_today": 0,
    "prayer_total": 0,
    "prayer_streak": 0,
    "charity_total": 0.0,
    "zakat_total": 0.0,
    "last_zakat_day": -999,
    "fasting_today": false,
    "calendar_day": 1,
    "calendar_month": 1,
    "is_ramadan": false,
}
var prayer_flags := {
    "Subuh": false,
    "Dzuhur": false,
    "Ashar": false,
    "Maghrib": false,
    "Isya": false,
}
var prayer_times := {
    "Subuh": 300,
    "Dzuhur": 720,
    "Ashar": 900,
    "Maghrib": 1080,
    "Isya": 1185,
}
var calendar_month_names := [
    "Muharram", "Safar", "Rabiul Awal", "Rabiul Akhir",
    "Jumadil Awal", "Jumadil Akhir", "Rajab", "Sya'ban",
    "Ramadan", "Syawal", "Dzulqa'dah", "Dzulhijjah"
]
var last_save_day := -1
var autosave_accumulator := 0.0
var npc_update_accumulator := 0.0
var environment_update_accumulator := 0.0
var hud_update_accumulator := 0.0
var bridge_poll_accumulator := 0.0
var performance_accumulator := 0.0
var perf_last_fps := 0.0
var perf_min_fps := 999.0
var perf_max_pss_mb := 0.0
var perf_last_pss_mb := 0.0
var perf_last_battery_temp := -1.0
var perf_last_battery_percent := -1
var perf_sample_count := 0
var perf_label: Label
var bridge_ready := false
var save_repair_pending := false
var used_legacy := false

func _ready() -> void:
    _build_navigation_grid()
    _build_world()
    _spawn_player()
    _spawn_npcs()
    _build_hud()
    _load_world()
    _normalize_dynamic_state()
    _init_android_bridge()
    _consume_android_events()
    if FileAccess.file_exists(SAVE_PATH_LEGACY) or not FileAccess.file_exists(SAVE_META_PATH) or save_repair_pending:
        _save_world(true)
    _update_hud()


func _init_android_bridge() -> void:
    bridge_ready = false
    if not Engine.has_singleton("ZahraBridge"):
        return
    bridge_plugin = Engine.get_singleton("ZahraBridge")
    if bridge_plugin == null:
        return
    var protocol_ok := int(bridge_plugin.getBridgeProtocolVersion()) == 2
    var ping_ok := String(bridge_plugin.pingBridge()) == "ZAHRA_BRIDGE_OK"
    var crypto_ok := bool(bridge_plugin.selfTestBridgeContract())
    bridge_ready = protocol_ok and ping_ok and crypto_ok
    if not bridge_ready:
        interaction_message = "Bridge Android belum aktif. Game tetap berjalan offline."

func _consume_android_events() -> void:
    if bridge_plugin == null or not bridge_ready:
        return
    var payload := String(bridge_plugin.consumeAppEvents())
    if payload.is_empty():
        return
    var parsed_events = JSON.parse_string(payload)
    if typeof(parsed_events) != TYPE_ARRAY:
        return
    var ack_ids: Array = []
    for event in parsed_events:
        if typeof(event) != TYPE_DICTIONARY:
            continue
        var event_id := String(event.get("eventId", ""))
        var inner_payload := String(event.get("payload", ""))
        var parsed = JSON.parse_string(inner_payload)
        if typeof(parsed) != TYPE_DICTIONARY or event_id.is_empty():
            continue
        if int(parsed.get("version", 0)) != 1:
            continue
        var event_type := String(parsed.get("type", ""))
        if event_type.length() == 0 or event_type.length() > 64:
            continue
        if event_type == "MISSION_COMPLETED":
            var mission_id := int(parsed.get("missionId", 0))
            if mission_id > 0:
                game_unlocks["mission_%d" % mission_id] = true
                interaction_message = "Sinkronisasi: misi #%d dari aplikasi selesai." % mission_id
        ack_ids.append(event_id)
    if not ack_ids.is_empty():
        bridge_plugin.ackAppEvents(JSON.stringify(ack_ids))

func _emit_android_event(event_type: String, title: String, detail: String) -> void:
    if bridge_plugin != null and bridge_ready:
        bridge_plugin.emitGameEvent(event_type.left(64), title.left(200), detail.left(1000))

func _process(delta: float) -> void:
    game_clock_accumulator += delta * 2.0
    var whole_minutes := int(game_clock_accumulator)
    if whole_minutes > 0:
        game_clock_accumulator -= whole_minutes
        _advance_game_minutes(whole_minutes)

    var slot := int(time_minutes / 30)
    if slot != npc_schedule_slot:
        npc_schedule_slot = slot
        _refresh_npc_schedules()

    _move_player(delta)
    _update_player_health()

    npc_update_accumulator += delta
    if npc_update_accumulator >= 0.20:
        var npc_dt := npc_update_accumulator
        npc_update_accumulator = 0.0
        _update_npcs(npc_dt)

    environment_update_accumulator += delta
    if environment_update_accumulator >= 0.40:
        environment_update_accumulator = 0.0
        _update_environment()

    hud_update_accumulator += delta
    if hud_update_accumulator >= 0.50:
        hud_update_accumulator = 0.0
        _update_hud()

    bridge_poll_accumulator += delta
    if bridge_poll_accumulator >= 3.0:
        bridge_poll_accumulator = 0.0
        _consume_android_events()

    autosave_accumulator += delta
    if autosave_accumulator >= 30.0:
        autosave_accumulator = 0.0
        _save_world()

    simulation_accumulator += delta
    if simulation_accumulator >= 2.0:
        simulation_accumulator = 0.0
        _simulate_living_city(2.0)

    world_state_emit_accumulator += delta
    if world_state_emit_accumulator >= 10.0:
        world_state_emit_accumulator = 0.0
        _emit_world_state()

    performance_accumulator += delta
    if performance_accumulator >= 5.0:
        performance_accumulator = 0.0
        _capture_performance()

func _notification(what: int) -> void:
    if what == NOTIFICATION_APPLICATION_PAUSED or what == NOTIFICATION_WM_CLOSE_REQUEST:
        _save_world(true)

func _unhandled_input(event: InputEvent) -> void:
    if event is InputEventScreenTouch:
        camera_touch_active = event.pressed
        if event.pressed:
            last_camera_touch = event.position
        return
    if event is InputEventScreenDrag and camera_touch_active:
        var drag: Vector2 = event.relative
        camera_yaw = fmod(camera_yaw - drag.x * 0.22, 360.0)
        camera_pitch = clamp(camera_pitch - drag.y * 0.16, 12.0, 52.0)
        _update_camera_transform()

func _update_camera_transform() -> void:
    if camera == null:
        return
    var yaw := deg_to_rad(camera_yaw)
    var pitch := deg_to_rad(camera_pitch)
    var horizontal := cos(pitch) * camera_distance
    camera.position = Vector3(sin(yaw) * horizontal, sin(pitch) * camera_distance, cos(yaw) * horizontal)
    camera.look_at(player.global_position + Vector3(0, 1.0, 0))

func _advance_game_minutes(minutes: int) -> void:
    var steps := maxi(minutes, 0)
    for _i in range(steps):
        time_minutes += 1
        _check_prayer_time()
        if time_minutes >= 1440:
            time_minutes = 0
            day += 1
            _new_day()

func _update_player_health() -> void:
    var health_delta := 0.0
    if hunger > 80.0:
        health_delta -= 0.025
    if energy < 15.0:
        health_delta -= 0.02
    if hygiene < 20.0:
        health_delta -= 0.015
    if stress > 85.0:
        health_delta -= 0.01
    if hunger < 45.0 and energy > 45.0 and stress < 55.0:
        health_delta += 0.004
    health = clamp(health + health_delta, 0.0, 100.0)
    if health < 20.0:
        energy = min(100.0, energy + 0.01)
        mood = max(0.0, mood - 0.01)

func _check_prayer_time() -> void:
    var prayer_name := ""
    for name in prayer_times.keys():
        if time_minutes == int(prayer_times[name]):
            prayer_name = String(name)
            break
    if prayer_name.is_empty():
        return
    var key := "%d:%s" % [day, prayer_name]
    if key == prayer_notice_key:
        return
    prayer_notice_key = key
    interaction_message = "Adzan %s. Waktu ibadah telah masuk." % prayer_name
    world_dirty = true

func _update_religious_calendar() -> void:
    var calendar_day := ((day - 1) % 360) + 1
    var calendar_month := int((calendar_day - 1) / 30) + 1
    religious_state["calendar_day"] = calendar_day
    religious_state["calendar_month"] = calendar_month
    religious_state["is_ramadan"] = calendar_month == 9

func _nearest_prayer_name() -> String:
    var best_name := ""
    var best_distance := 9999
    for name in prayer_times.keys():
        var distance: int = absi(time_minutes - int(prayer_times[name]))
        if distance <= 60 and distance < best_distance:
            best_distance = distance
            best_name = String(name)
    return best_name

func _mat(c: Color) -> StandardMaterial3D:
    var key := c.to_html(true)
    if material_cache.has(key):
        return material_cache[key]
    var material := StandardMaterial3D.new()
    material.albedo_color = c
    material.roughness = 0.88
    material_cache[key] = material
    return material

func _box(n: String, pos: Vector3, size: Vector3, c: Color) -> MeshInstance3D:
    var mesh_node := MeshInstance3D.new()
    var mesh := BoxMesh.new()
    mesh.size = size
    mesh_node.mesh = mesh
    mesh_node.material_override = _mat(c)
    mesh_node.visibility_range_end = 60.0
    mesh_node.position = pos
    mesh_node.name = n
    add_child(mesh_node)
    return mesh_node

func _building(n: String, pos: Vector3, c: Color, title: String) -> void:
    if n == "Home":
        _home_building(pos, c, title)
        return
    if n == "Mosque":
        _mosque_building(pos, c, title)
        return
    _box(n, pos, Vector3(5, 2.5, 4), c)
    _add_building_facade(pos, c, n)
    var body := StaticBody3D.new()
    body.position = pos
    body.name = n + "Collision"
    add_child(body)
    var shape := CollisionShape3D.new()
    var box_shape := BoxShape3D.new()
    box_shape.size = Vector3(5, 2.5, 4)
    shape.shape = box_shape
    body.add_child(shape)
    var label := Label3D.new()
    label.text = title
    label.font_size = 48
    label.position = pos + Vector3(0, 2.2, 0)
    label.modulate = Color("#4D4650")
    add_child(label)

func _mosque_building(pos: Vector3, c: Color, title: String) -> void:
    _box("MosqueBase", pos, Vector3(5.5, 2.1, 4.5), c)
    var dome := MeshInstance3D.new()
    var sphere := SphereMesh.new()
    sphere.height = 2.4
    sphere.radius = 1.55
    dome.mesh = sphere
    dome.material_override = _mat(Color("#B9D5A9"))
    dome.position = pos + Vector3(0, 2.1, 0)
    dome.scale = Vector3(1.0, 0.65, 1.0)
    add_child(dome)
    for side in [-1.0, 1.0]:
        var minaret := MeshInstance3D.new()
        var cylinder := CylinderMesh.new()
        cylinder.height = 4.2
        cylinder.top_radius = 0.18
        cylinder.bottom_radius = 0.24
        minaret.mesh = cylinder
        minaret.material_override = _mat(c.darkened(0.08))
        minaret.position = pos + Vector3(side * 2.45, 1.9, -1.5)
        add_child(minaret)
    var body := StaticBody3D.new()
    body.position = pos
    body.name = "MosqueCollision"
    add_child(body)
    var shape := CollisionShape3D.new()
    var box_shape := BoxShape3D.new()
    box_shape.size = Vector3(5.5, 2.1, 4.5)
    shape.shape = box_shape
    body.add_child(shape)
    var label := Label3D.new()
    label.text = title
    label.font_size = 48
    label.position = pos + Vector3(0, 3.0, 0)
    label.modulate = Color("#405043")
    add_child(label)

func _home_building(pos: Vector3, c: Color, title: String) -> void:
    # Titik acuan bangunan lain adalah pusat kotak (y=1); lantai rumah harus rata dengan tanah (y=0).
    pos += Vector3(0, -1.0, 0)
    var wall_height := 2.5
    var wall_thickness := 0.35
    var width := 5.0
    var depth := 4.0
    _box("HomeFloor", pos + Vector3(0, -0.1, 0), Vector3(width, 0.2, depth), c.darkened(0.12))
    _box("HomeBackWall", pos + Vector3(0, wall_height * 0.5, -depth * 0.5), Vector3(width, wall_height, wall_thickness), c)
    _box("HomeLeftWall", pos + Vector3(-width * 0.5, wall_height * 0.5, 0), Vector3(wall_thickness, wall_height, depth), c)
    _box("HomeRightWall", pos + Vector3(width * 0.5, wall_height * 0.5, 0), Vector3(wall_thickness, wall_height, depth), c)
    _box("HomeFrontLeftWall", pos + Vector3(-1.75, wall_height * 0.5, depth * 0.5), Vector3(1.5, wall_height, wall_thickness), c)
    _box("HomeFrontRightWall", pos + Vector3(1.75, wall_height * 0.5, depth * 0.5), Vector3(1.5, wall_height, wall_thickness), c)
    _home_collision(pos, Vector3(width, wall_height, wall_thickness), Vector3(0, wall_height * 0.5, -depth * 0.5), "HomeBackCollision")
    _home_collision(pos, Vector3(wall_thickness, wall_height, depth), Vector3(-width * 0.5, wall_height * 0.5, 0), "HomeLeftCollision")
    _home_collision(pos, Vector3(wall_thickness, wall_height, depth), Vector3(width * 0.5, wall_height * 0.5, 0), "HomeRightCollision")
    _home_collision(pos, Vector3(1.5, wall_height, wall_thickness), Vector3(-1.75, wall_height * 0.5, depth * 0.5), "HomeFrontLeftCollision")
    _home_collision(pos, Vector3(1.5, wall_height, wall_thickness), Vector3(1.75, wall_height * 0.5, depth * 0.5), "HomeFrontRightCollision")
    _box("Bed", pos + Vector3(-1.2, 0.35, -0.8), Vector3(1.6, 0.5, 1.9), Color("#D7BFCB"))
    _box("Desk", pos + Vector3(1.0, 0.65, -1.1), Vector3(1.4, 0.12, 0.7), Color("#9B785C"))
    _box("Kitchen", pos + Vector3(1.25, 0.55, 1.05), Vector3(1.1, 0.8, 0.6), Color("#C7B59A"))
    var label := Label3D.new()
    label.text = title
    label.font_size = 48
    label.position = pos + Vector3(0, wall_height + 0.4, 0)
    label.modulate = Color("#4D4650")
    add_child(label)

func _home_collision(pos: Vector3, size: Vector3, offset: Vector3, n: String) -> void:
    var body := StaticBody3D.new()
    body.position = pos + offset
    body.name = n
    add_child(body)
    var shape := CollisionShape3D.new()
    var box_shape := BoxShape3D.new()
    box_shape.size = size
    shape.shape = box_shape
    body.add_child(shape)


# ---------------------------------------------------------------------------
# v0.17 REALISTIC LIFE WORLD
# Procedural assets keep the APK self-contained while replacing the old
# low-poly presentation with layered materials, humanoid anatomy, street
# detail, vehicles, vegetation, and realistic lighting.
# ---------------------------------------------------------------------------

func _pbr(c: Color, roughness := 0.72, metallic := 0.0, emission := Color(0,0,0)) -> StandardMaterial3D:
    var key := "pbr:%s:%s:%s:%s" % [c.to_html(true), roughness, metallic, emission.to_html(true)]
    if material_cache.has(key):
        return material_cache[key]
    var m := StandardMaterial3D.new()
    m.albedo_color = c
    m.roughness = roughness
    m.metallic = metallic
    if emission.a > 0.0 or emission.r > 0.0 or emission.g > 0.0 or emission.b > 0.0:
        m.emission_enabled = true
        m.emission = emission
        m.emission_energy_multiplier = 1.8
    material_cache[key] = m
    return m

func _primitive(parent: Node3D, mesh: Mesh, pos: Vector3, mat: Material, scale := Vector3.ONE, name := "Part") -> MeshInstance3D:
    var n := MeshInstance3D.new()
    n.name = name
    n.mesh = mesh
    n.position = pos
    n.scale = scale
    n.material_override = mat
    parent.add_child(n)
    return n

func _build_humanoid_visual(parent: Node3D, index: int, player_character := false) -> void:
    var root := Node3D.new()
    root.name = "HumanoidVisual"
    parent.add_child(root)
    var skin_palette := [
        Color("#B87954"), Color("#D49A73"), Color("#8F5B3F"),
        Color("#E0AA82"), Color("#9E684A"), Color("#C88860")
    ]
    var shirt_palette := [
        Color("#31485C"), Color("#6A3F4B"), Color("#4C6654"),
        Color("#806B45"), Color("#3F4E73"), Color("#7A594C")
    ]
    var skin: StandardMaterial3D = _pbr(skin_palette[index % skin_palette.size()], 0.72)
    var shirt: StandardMaterial3D = _pbr(Color("#8D5F48") if player_character else shirt_palette[index % shirt_palette.size()], 0.84)
    var pants: StandardMaterial3D = _pbr(Color("#273442") if player_character else Color("#30343B").lerp(shirt_palette[index % shirt_palette.size()], 0.18), 0.92)
    var hair: StandardMaterial3D = _pbr(Color("#191713").lerp(Color("#4A2E1F"), float(index % 4) / 8.0), 0.86)
    var shoe := _pbr(Color("#202326"), 0.58, 0.05)

    # Torso and pelvis: proportions closer to a human silhouette than a capsule.
    var torso := BoxMesh.new()
    torso.size = Vector3(0.66, 0.78, 0.34)
    _primitive(root, torso, Vector3(0, 1.25, 0), shirt, Vector3.ONE, "Torso")

    var pelvis := BoxMesh.new()
    pelvis.size = Vector3(0.55, 0.34, 0.32)
    _primitive(root, pelvis, Vector3(0, 0.84, 0), pants, Vector3.ONE, "Pelvis")

    var head := SphereMesh.new()
    head.radius = 0.24
    head.height = 0.50
    _primitive(root, head, Vector3(0, 1.86, 0), skin, Vector3(0.92, 1.0, 0.90), "Head")

    var hair_mesh := SphereMesh.new()
    hair_mesh.radius = 0.245
    hair_mesh.height = 0.25
    _primitive(root, hair_mesh, Vector3(0, 2.02, -0.01), hair, Vector3(0.98, 0.55, 0.98), "Hair")

    # Neck, arms and legs use cylinders for joint-friendly silhouettes.
    var neck := CylinderMesh.new()
    neck.top_radius = 0.09
    neck.bottom_radius = 0.10
    neck.height = 0.16
    _primitive(root, neck, Vector3(0, 1.65, 0), skin, Vector3.ONE, "Neck")

    for side in [-1.0, 1.0]:
        var upper_arm := CylinderMesh.new()
        upper_arm.top_radius = 0.085
        upper_arm.bottom_radius = 0.105
        upper_arm.height = 0.54
        var arm := _primitive(root, upper_arm, Vector3(side * 0.43, 1.28, 0), skin, Vector3.ONE, "Arm")
        arm.rotation_degrees.z = side * -7.0
        arm.set_meta("limb", "arm_%d" % int(side))
        var hand := SphereMesh.new()
        hand.radius = 0.105
        hand.height = 0.21
        _primitive(root, hand, Vector3(side * 0.47, 0.96, 0), skin, Vector3.ONE, "Hand")

        var leg := CylinderMesh.new()
        leg.top_radius = 0.115
        leg.bottom_radius = 0.095
        leg.height = 0.72
        var leg_node := _primitive(root, leg, Vector3(side * 0.17, 0.46, 0), pants, Vector3.ONE, "Leg")
        leg_node.set_meta("limb", "leg_%d" % int(side))
        var foot := BoxMesh.new()
        foot.size = Vector3(0.22, 0.11, 0.38)
        _primitive(root, foot, Vector3(side * 0.17, 0.075, 0.08), shoe, Vector3.ONE, "Foot")

    # Small facial features give the NPCs identity at close range.
    var eye := SphereMesh.new()
    eye.radius = 0.025
    eye.height = 0.05
    var eye_mat := _pbr(Color("#17181A"), 0.25, 0.0)
    _primitive(root, eye, Vector3(-0.085, 1.88, 0.218), eye_mat, Vector3.ONE, "EyeL")
    _primitive(root, eye, Vector3(0.085, 1.88, 0.218), eye_mat, Vector3.ONE, "EyeR")

func _animate_humanoid(root: Node, moving: bool, delta: float, phase: float) -> void:
    if root == null:
        return
    var t := Time.get_ticks_msec() * 0.001 + phase
    var swing := sin(t * (8.0 if moving else 2.0)) * (0.38 if moving else 0.025)
    for child in root.get_children():
        if child is MeshInstance3D and child.has_meta("limb"):
            var limb := String(child.get_meta("limb"))
            if limb.begins_with("leg_"):
                child.rotation_degrees.x = rad_to_deg(swing * (-1.0 if limb.ends_with("-1") else 1.0))
            elif limb.begins_with("arm_"):
                child.rotation_degrees.x = rad_to_deg(-swing * (1.0 if limb.ends_with("1") else -1.0))
    root.position.y = sin(t * 3.0) * (0.018 if moving else 0.006)

func _build_realistic_world() -> void:
    # PBR-like surfaces and denser world dressing. Geometry is procedural so the
    # APK remains offline-first and does not depend on downloaded asset packs.
    if world_env != null:
        world_env.tonemap_mode = Environment.TONE_MAPPER_ACES
        world_env.tonemap_exposure = 1.15
        world_env.ambient_light_energy = 0.72
        world_env.background_energy_multiplier = 0.8
    if sun_light != null:
        sun_light.shadow_enabled = true
        sun_light.directional_shadow_max_distance = 55.0
        sun_light.directional_shadow_pancake_size = 20.0
    _build_road_markings()
    _build_vegetation()
    _build_realistic_vehicles()
    _build_building_details()
    _build_district_signs()

func _build_road_markings() -> void:
    var asphalt := _pbr(Color("#303338"), 0.94)
    # Replace the pale road look with layered asphalt strips and lane paint.
    for x in range(-16, 17, 8):
        _box("AsphaltNS", Vector3(x, 0.015, 0), Vector3(2.9, 0.035, 44), Color("#303338"))
        for z in range(-20, 21, 4):
            _box("LaneDash", Vector3(x, 0.043, float(z)), Vector3(0.07, 0.012, 1.65), Color("#E8D99A"))
    for z in range(-16, 17, 8):
        _box("AsphaltEW", Vector3(0, 0.02, z), Vector3(44, 0.04, 2.9), Color("#303338"))
        for x in range(-20, 21, 4):
            _box("LaneDash", Vector3(float(x), 0.048, z), Vector3(1.65, 0.012, 0.07), Color("#E8D99A"))
    # Zebra crossings at central intersections.
    for x in [-16.0, -8.0, 0.0, 8.0, 16.0]:
        for i in range(-2, 3):
            _box("Crosswalk", Vector3(x + i * 0.35, 0.055, 1.9), Vector3(0.20, 0.012, 1.3), Color("#E7E7E2"))

func _build_vegetation() -> void:
    var trunk_mat := _pbr(Color("#654832"), 0.94)
    var leaf_mat := _pbr(Color("#3E6B43"), 0.88)
    var leaf2 := _pbr(Color("#557E4B"), 0.9)
    var positions := [
        Vector3(-19,0,-19), Vector3(-13,0,-19), Vector3(-5,0,-19), Vector3(5,0,-19), Vector3(13,0,-19), Vector3(19,0,-19),
        Vector3(-19,0,19), Vector3(-13,0,19), Vector3(-5,0,19), Vector3(5,0,19), Vector3(13,0,19), Vector3(19,0,19),
        Vector3(-19,0,-5), Vector3(19,0,-5), Vector3(-19,0,5), Vector3(19,0,5)
    ]
    for i in range(positions.size()):
        var root := Node3D.new()
        root.position = positions[i]
        add_child(root)
        var trunk := CylinderMesh.new()
        trunk.top_radius = 0.10
        trunk.bottom_radius = 0.16
        trunk.height = 1.25
        _primitive(root, trunk, Vector3(0,0.62,0), trunk_mat, Vector3.ONE, "Trunk")
        var crown := SphereMesh.new()
        crown.radius = 0.72
        crown.height = 1.45
        _primitive(root, crown, Vector3(0,1.55,0), leaf_mat if i % 2 == 0 else leaf2, Vector3(1.05,0.9,1.05), "Crown")

func _build_realistic_vehicles() -> void:
    var car_body := _pbr(Color("#49647A"), 0.42, 0.35)
    var glass := _pbr(Color("#5F7580"), 0.18, 0.25)
    glass.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    glass.albedo_color.a = 0.72
    var tire := _pbr(Color("#171819"), 0.98)
    var car_positions := [
        Vector3(-12,0.28,-16), Vector3(4,0.28,-16), Vector3(12,0.28,16),
        Vector3(-4,0.28,16), Vector3(-16,0.28,8), Vector3(16,0.28,-8)
    ]
    for i in range(car_positions.size()):
        var root := Node3D.new()
        root.position = car_positions[i]
        root.rotation_degrees.y = 90.0 if i % 2 == 0 else 0.0
        add_child(root)
        var body := BoxMesh.new()
        body.size = Vector3(1.75,0.42,3.55)
        _primitive(root, body, Vector3(0,0.35,0), car_body, Vector3.ONE, "Body")
        var cabin := BoxMesh.new()
        cabin.size = Vector3(1.45,0.46,1.65)
        _primitive(root, cabin, Vector3(0,0.70,-0.10), glass, Vector3.ONE, "Cabin")
        for side in [-1.0,1.0]:
            for z in [-1.18,1.18]:
                var wheel := CylinderMesh.new()
                wheel.top_radius = 0.25
                wheel.bottom_radius = 0.25
                wheel.height = 0.16
                var wn := _primitive(root, wheel, Vector3(side*0.91,0.26,z), tire, Vector3.ONE, "Wheel")
                wn.rotation_degrees.z = 90.0

func _build_building_details() -> void:
    var frame := _pbr(Color("#4B4D4F"), 0.38, 0.55)
    var glass := _pbr(Color("#6B93A3"), 0.22, 0.32)
    glass.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    glass.albedo_color.a = 0.62
    var locations := [
        Vector3(7,1,-8), Vector3(-8,1,8), Vector3(7,1,8),
        Vector3(0,1,14), Vector3(-14,1,14), Vector3(14,1,-14), Vector3(14,1,14)
    ]
    for p in locations:
        for floor in range(3):
            for col in range(3):
                _box("GlassPanel", p + Vector3(-1.55 + col*1.55, 0.55 + floor*0.72, 2.04), Vector3(1.10,0.46,0.035), Color("#6B93A3"))
                _box("Frame", p + Vector3(-2.12 + col*1.55, 0.55 + floor*0.72, 2.075), Vector3(0.035,0.52,0.06), Color("#4B4D4F"))
    # Rooftop water tanks/AC units sell the scale of occupied buildings.
    for p in locations:
        _box("RooftopUnit", p + Vector3(1.4,2.85,0.9), Vector3(0.55,0.45,0.55), Color("#73787A"))

func _build_district_signs() -> void:
    var signs := [
        [Vector3(-8,2.9,-10.1), "RUMAH & WARGA"],
        [Vector3(7,2.9,-10.1), "PASAR"],
        [Vector3(-8,2.9,10.1), "PENDIDIKAN"],
        [Vector3(0,3.5,-16.5), "MASJID"],
        [Vector3(0,3.5,16.5), "PEMERINTAH"],
        [Vector3(-14,3.5,16.5), "BISNIS"],
        [Vector3(14,3.5,-16.5), "KESEHATAN"]
    ]
    for item in signs:
        var label := Label3D.new()
        label.text = String(item[1])
        label.position = item[0]
        label.font_size = 42
        label.modulate = Color("#F1EEE8")
        label.outline_size = 8
        label.outline_modulate = Color("#202326")
        add_child(label)

func _build_world() -> void:
    var environment := Environment.new()
    environment.background_mode = Environment.BG_COLOR
    environment.background_color = Color("#DCE9E4")
    environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    environment.ambient_light_color = Color("#FFF3E8")
    environment.ambient_light_energy = 1.0
    var world_environment := WorldEnvironment.new()
    world_environment.environment = environment
    add_child(world_environment)
    world_env = environment

    var sun := DirectionalLight3D.new()
    sun.rotation_degrees = Vector3(-55, -25, 0)
    sun.light_energy = 1.2
    sun.shadow_enabled = false
    sun.directional_shadow_max_distance = 32.0
    add_child(sun)
    sun_light = sun

    _box("Ground", Vector3(0, -0.3, 0), Vector3(WORLD_SIZE, 0.5, WORLD_SIZE), Color("#A8C29E"))
    _building("Home", Vector3(-8, 1, -8), Color("#F5E2CB"), "Your Home")
    _building("Market", Vector3(7, 1, -8), Color("#E5D7F0"), "Market")
    _building("Cafe", Vector3(-8, 1, 8), Color("#F1D2C4"), "Cafe")
    _building("School", Vector3(7, 1, 8), Color("#CADBED"), "School")
    _building("Mosque", Vector3(0, 1, -14), Color("#D9EAD5"), "Mosque")
    _building("Government", Vector3(0, 1, 14), Color("#D1DCE8"), "City Hall")
    _building("BusinessHub", Vector3(-14, 1, 14), Color("#E8D2B2"), "Business Hub")
    _building("Hospital", Vector3(14, 1, -14), Color("#D9E8E8"), "Hospital")
    _building("Community", Vector3(14, 1, 14), Color("#D7E5C8"), "Community Center")
    _building("Park", Vector3(14, 1, 0), Color("#B9DDB1"), "Park")

    # Same city-grid idea, but 10 road meshes instead of 42 to reduce draw calls on mobile.
    for x in range(-16, 17, 8):
        _box("RoadNS", Vector3(x, -0.02, 0), Vector3(1.6, 0.06, 44), Color("#D7C9B7"))
    for z in range(-16, 17, 8):
        _box("RoadEW", Vector3(0, -0.01, z), Vector3(44, 0.06, 1.6), Color("#D7C9B7"))
    _build_realistic_world()

func _build_navigation_grid() -> void:
    grid.region = Rect2i(-20, -20, 41, 41)
    grid.cell_size = Vector2(1, 1)
    grid.diagonal_mode = AStarGrid2D.DIAGONAL_MODE_AT_LEAST_ONE_WALKABLE
    grid.update()
    for x in range(-20, 21):
        for z in range(-20, 21):
            if _is_building_cell(x, z):
                grid.set_point_solid(Vector2i(x, z), true)
    grid.set_point_solid(Vector2i(-8, -6), false)

func _is_building_cell(x: int, z: int) -> bool:
    var blocks := [
        Vector2(-8, -8), Vector2(7, -8), Vector2(-8, 8), Vector2(7, 8),
        Vector2(0, -14), Vector2(0, 14), Vector2(-14, 14),
        Vector2(14, -14), Vector2(14, 14), Vector2(14, 0)
    ]
    for block in blocks:
        if abs(x - int(block.x)) <= 3 and abs(z - int(block.y)) <= 2:
            return true
    return false

func _spawn_player() -> void:
    player = CharacterBody3D.new()
    player.position = Vector3(0, 0, 0)
    add_child(player)
    var mesh_node := MeshInstance3D.new()
    var capsule := CapsuleMesh.new()
    capsule.height = 1.8
    capsule.radius = 0.4
    mesh_node.mesh = capsule
    mesh_node.material_override = _mat(Color("#B18D9F"))
    mesh_node.position.y = 1
    mesh_node.visible = false
    player.add_child(mesh_node)
    _build_humanoid_visual(player, 0, true)

    var collision := CollisionShape3D.new()
    var capsule_shape := CapsuleShape3D.new()
    capsule_shape.height = 1.8
    capsule_shape.radius = 0.4
    collision.shape = capsule_shape
    collision.position.y = 1
    player.add_child(collision)

    camera = Camera3D.new()
    camera.current = true
    camera.fov = 58.0
    camera.position = Vector3(8, 8, 10)
    player.add_child(camera)
    _update_camera_transform()


func _add_building_facade(pos: Vector3, c: Color, building_id: String) -> void:
    var accent := c.darkened(0.16)
    # Door
    _box(building_id + "_Door", pos + Vector3(0, 0.78, 2.04), Vector3(0.85, 1.55, 0.10), accent)
    # Windows, two per visible side. They are emissive at night via environment contrast.
    for side in [-1.0, 1.0]:
        for i in range(2):
            var x := -1.35 + float(i) * 2.7
            _box(building_id + "_Window", pos + Vector3(x, 1.35, side * 2.04), Vector3(1.05, 0.75, 0.08), Color("#B9D8E5"))
    var roof := _box(building_id + "_Roof", pos + Vector3(0, 2.72, 0), Vector3(5.25, 0.20, 4.25), accent)
    roof.visibility_range_end = 50.0

func _build_city_facades() -> void:
    # Small residential blocks make the city read as a neighbourhood rather than ten isolated cubes.
    var houses := [
        Vector3(-16, 1.0, -11), Vector3(-11, 1.0, -11), Vector3(-6, 1.0, -11),
        Vector3(9, 1.0, -11), Vector3(14, 1.0, -11),
        Vector3(-16, 1.0, 3), Vector3(-11, 1.0, 3), Vector3(9, 1.0, 3)
    ]
    var palette := [Color("#E8D5C4"), Color("#D6E2D0"), Color("#D7D9E8"), Color("#E6D7B9")]
    for i in range(houses.size()):
        _box("HouseBlock_%d" % i, houses[i], Vector3(3.6, 2.0, 3.0), palette[i % palette.size()])
        _add_building_facade(houses[i], palette[i % palette.size()], "House_%d" % i)

func _build_street_furniture() -> void:
    for x in range(-20, 21, 8):
        _street_lamp(Vector3(float(x), 0, -3.2))
        _street_lamp(Vector3(float(x), 0, 12.0))
    for z in range(-20, 21, 8):
        _street_lamp(Vector3(-3.2, 0, float(z)))
        _street_lamp(Vector3(12.0, 0, float(z)))
    # Sidewalk strips visually separate pedestrians from road lanes.
    for x in [-20.0, -12.0, -4.0, 4.0, 12.0, 20.0]:
        _box("Sidewalk", Vector3(x, 0.03, 0), Vector3(0.55, 0.08, 44), Color("#C8C4BB"))
    for z in [-20.0, -12.0, -4.0, 4.0, 12.0, 20.0]:
        _box("Sidewalk", Vector3(0, 0.04, z), Vector3(44, 0.08, 0.55), Color("#C8C4BB"))

func _street_lamp(pos: Vector3) -> void:
    var root := Node3D.new()
    root.position = pos
    add_child(root)
    street_props.append(root)
    var pole := MeshInstance3D.new()
    var cylinder := CylinderMesh.new()
    cylinder.height = 3.1
    cylinder.top_radius = 0.05
    cylinder.bottom_radius = 0.08
    pole.mesh = cylinder
    pole.material_override = _mat(Color("#3F4650"))
    pole.position.y = 1.55
    root.add_child(pole)
    var lamp := OmniLight3D.new()
    lamp.light_energy = 0.35
    lamp.omni_range = 5.0
    lamp.shadow_enabled = false
    lamp.position.y = 3.05
    root.add_child(lamp)

func _build_greenery() -> void:
    var trees := [
        Vector3(-20,0,-20), Vector3(-20,0,-4), Vector3(-20,0,20),
        Vector3(-4,0,-20), Vector3(4,0,-20), Vector3(20,0,-20),
        Vector3(20,0,-4), Vector3(20,0,4), Vector3(20,0,20),
        Vector3(-4,0,20), Vector3(4,0,20), Vector3(-20,0,20)
    ]
    for i in range(trees.size()):
        _tree(trees[i], 0.85 + float(i % 3) * 0.10)

func _tree(pos: Vector3, scale_factor: float) -> void:
    var root := Node3D.new()
    root.position = pos
    root.scale = Vector3.ONE * scale_factor
    add_child(root)
    street_props.append(root)
    var trunk := MeshInstance3D.new()
    var cyl := CylinderMesh.new()
    cyl.height = 1.7
    cyl.top_radius = 0.16
    cyl.bottom_radius = 0.23
    trunk.mesh = cyl
    trunk.material_override = _mat(Color("#765A43"))
    trunk.position.y = 0.85
    root.add_child(trunk)
    var crown := MeshInstance3D.new()
    var sphere := SphereMesh.new()
    sphere.height = 2.2
    sphere.radius = 1.05
    crown.mesh = sphere
    crown.material_override = _mat(Color("#6E9B6B"))
    crown.position.y = 2.15
    root.add_child(crown)

func _build_vehicles() -> void:
    var car_positions := [
        Vector3(-10,0.35,-4), Vector3(10,0.35,4), Vector3(4,0.35,-12), Vector3(-4,0.35,12)
    ]
    for i in range(car_positions.size()):
        _vehicle(car_positions[i], i % 2 == 0)

func _vehicle(pos: Vector3, rotated: bool) -> void:
    var root := Node3D.new()
    root.position = pos
    if rotated:
        root.rotation.y = PI * 0.5
    add_child(root)
    street_props.append(root)
    _box_child(root, Vector3(0,0.35,0), Vector3(1.8,0.55,3.2), Color("#596C7A"))
    for x in [-0.68, 0.68]:
        for z in [-1.0, 1.0]:
            var wheel := MeshInstance3D.new()
            var cyl := CylinderMesh.new()
            cyl.height = 0.18
            cyl.top_radius = 0.25
            cyl.bottom_radius = 0.25
            wheel.mesh = cyl
            wheel.material_override = _mat(Color("#25282B"))
            wheel.rotation_degrees.x = 90
            wheel.position = Vector3(x,0.25,z)
            root.add_child(wheel)

func _box_child(parent: Node3D, pos: Vector3, size: Vector3, c: Color) -> void:
    var node := MeshInstance3D.new()
    var mesh := BoxMesh.new()
    mesh.size = size
    node.mesh = mesh
    node.material_override = _mat(c)
    node.position = pos
    parent.add_child(node)

func _build_location_markers() -> void:
    var markers := [
        ["AGAMA", Vector3(0,3.9,-14), Color("#6C9270")],
        ["BISNIS", Vector3(-14,3.0,14), Color("#9A7749")],
        ["POLITIK", Vector3(0,3.0,14), Color("#647C9C")],
        ["PASAR", Vector3(7,3.0,-8), Color("#8C6A83")]
    ]
    for item in markers:
        var label := Label3D.new()
        label.text = item[0]
        label.font_size = 38
        label.modulate = item[2]
        label.outline_size = 8
        label.position = item[1]
        add_child(label)
        detail_labels.append(label)

func _spawn_npcs() -> void:
    var home_positions := [
        Vector3(-16, 0.85, -16), Vector3(-11, 0.85, -16), Vector3(-6, 0.85, -16),
        Vector3(-1, 0.85, -16), Vector3(4, 0.85, -16), Vector3(9, 0.85, -16),
        Vector3(14, 0.85, -16), Vector3(-16, 0.85, -11), Vector3(-11, 0.85, -11), Vector3(-6, 0.85, -11)
    ]
    var occupations := ["Teacher", "Student", "Shopkeeper", "Mechanic", "Designer"]
    var work_targets := {
        "Teacher": Vector2(7, 8),
        "Student": Vector2(7, 8),
        "Shopkeeper": Vector2(7, -8),
        "Mechanic": Vector2(14, 0),
        "Designer": Vector2(-8, 8),
    }
    for i in range(npc_names.size()):
        var npc_node := MeshInstance3D.new()
        var capsule := CapsuleMesh.new()
        capsule.height = 1.7
        capsule.radius = 0.35
        npc_node.mesh = capsule
        npc_node.material_override = _mat(Color.from_hsv(float(i) / float(npc_names.size()), 0.25, 0.9))
        npc_node.visible = false
        npc_node.position = home_positions[i % home_positions.size()]
        add_child(npc_node)
        _build_humanoid_visual(npc_node, i, false)

        var occupation: String = occupations[i % occupations.size()]
        var home_target: Vector2 = Vector2(home_positions[i].x, home_positions[i].z)
        var npc := {
            "name": npc_names[i],
            "node": npc_node,
            "visual_root": npc_node.get_node_or_null("HumanoidVisual"),
            "anim_phase": float(i) * 0.61,
            "money": 70.0 + i * 18,
            "opinion": 45.0 + i,
            "relationship": 0.0,
            "mood": 50.0 + i,
            "energy": 90.0,
            "hunger": 25.0,
            "traits": npc_traits[i],
            "occupation": occupation,
            "home": home_target,
            "work_target": work_targets[occupation],
            "goal": Vector3(home_target.x, 0.85, home_target.y),
            "next_social": 0.0,
            "state": "HOME",
            "social_need": 25.0 + float(i % 4) * 10.0,
            "work_performance": 0.70 + float(i % 3) * 0.08,
            "trust": 45.0 + float((i * 7) % 16),
            "goal_state": "stability",
            "political_affinity": ["business", "workers", "youth", "public", "community"][i % 5],
            "memory": [],
            "last_interaction_day": 0,
            "family_id": i % 4,
            "household_size": 2 + (i % 4),
            "income": 65.0 + i * 12.0,
            "faith_practice": 45.0 + (i % 5) * 8.0,
            "business_interest": 30.0 + (i % 6) * 7.0,
            "civic_interest": 35.0 + (i % 5) * 9.0,
        }
        npcs.append(npc)
    _refresh_npc_schedules()

func _npc_target_for_time(npc: Dictionary) -> Vector2:
    var hour := int(time_minutes / 60)
    var hunger_need := float(npc.get("hunger", 25.0))
    var energy_left := float(npc.get("energy", 80.0))
    var social_need := float(npc.get("social_need", 25.0))
    var goal_state := String(npc.get("goal_state", "stability"))
    if energy_left < 18.0:
        npc["state"] = "REST"
        return npc["home"]
    if hunger_need > 78.0:
        npc["state"] = "EAT"
        return Vector2(-8, 8) if int(npc["name"].hash()) % 2 == 0 else Vector2(7, -8)
    if social_need > 72.0 and hour >= 9 and hour < 21:
        npc["state"] = "SOCIAL"
        return Vector2(14, 0)
    if hour < 7 or hour >= 21:
        npc["state"] = "HOME"
        return npc["home"]
    if hour >= 9 and hour < 17:
        if goal_state == "community":
            npc["state"] = "COMMUNITY"
            return Vector2(0, -14)
        if goal_state == "growth" and npc["occupation"] in ["Teacher", "Student"]:
            npc["state"] = "LEARNING"
            return Vector2(7, 8)
        if goal_state == "income":
            npc["state"] = "WORK"
            return npc["work_target"]
        if goal_state == "security" and float(world_economy.get("household_pressure", 0.0)) > 65.0:
            npc["state"] = "MARKET"
            return Vector2(7, -8)
    if hour >= 7 and hour < 12:
        npc["state"] = "WORK"
        return npc["work_target"]
    if hour >= 12 and hour < 13:
        npc["state"] = "BREAK"
        return Vector2(-8, 8) if int(npc["name"].hash()) % 2 == 0 else Vector2(7, -8)
    if hour >= 13 and hour < 17:
        npc["state"] = "WORK"
        return npc["work_target"]
    if hour >= 17 and hour < 19:
        npc["state"] = "SOCIAL"
        return Vector2(14, 0)
    if hour >= 19 and hour < 21:
        npc["state"] = "COMMUNITY"
        return Vector2(0, -14) if int(npc["name"].hash()) % 2 == 0 else Vector2(14, 0)
    npc["state"] = "HOME"
    return npc["home"]

func _refresh_npc_schedules() -> void:
    for npc in npcs:
        _set_npc_destination(npc, _npc_target_for_time(npc))

func _nearest_open_cell(cell: Vector2i) -> Vector2i:
    if not grid.is_point_solid(cell):
        return cell
    for radius in range(1, 8):
        for dx in range(-radius, radius + 1):
            for dz in range(-radius, radius + 1):
                if maxi(absi(dx), absi(dz)) != radius:
                    continue
                var candidate := Vector2i(clampi(cell.x + dx, -20, 20), clampi(cell.y + dz, -20, 20))
                if not grid.is_point_solid(candidate):
                    return candidate
    return cell

func _set_npc_destination(npc: Dictionary, target: Vector2) -> void:
    npc["goal"] = Vector3(target.x, 0.85, target.y)
    npc["repath_in"] = 1.5
    var start := _nearest_open_cell(Vector2i(clampi(roundi(npc["node"].position.x), -20, 20), clampi(roundi(npc["node"].position.z), -20, 20)))
    var end := _nearest_open_cell(Vector2i(clampi(roundi(target.x), -20, 20), clampi(roundi(target.y), -20, 20)))
    var points: Array[Vector2i] = grid.get_id_path(start, end)
    npc_path[npc["name"]] = points

func _update_npcs(delta: float) -> void:
    for npc in npcs:
        var node: MeshInstance3D = npc["node"]
        var points: Array = npc_path.get(npc["name"], [])
        var moving := not points.is_empty()
        _animate_humanoid(npc.get("visual_root"), moving, delta, float(npc.get("anim_phase", 0.0)))
        if points.is_empty():
            npc["repath_in"] = float(npc.get("repath_in", 0.0)) - delta
            if float(npc["repath_in"]) <= 0.0:
                _set_npc_destination(npc, _npc_target_for_time(npc))
            continue
        var next_point: Vector2i = points[0]
        var target := Vector3(next_point.x, 0.85, next_point.y)
        var difference := target - node.position
        difference.y = 0
        if difference.length() < 0.35:
            points.pop_front()
        else:
            node.position += difference.normalized() * delta * (0.9 if weather == "Rain" else 1.15)
        var current_energy := float(npc["energy"])
        var current_hunger := float(npc["hunger"])
        var current_social_need := float(npc.get("social_need", 25.0))
        var hour := int(time_minutes / 60)
        if hour >= 21 or hour < 7:
            current_energy = clamp(current_energy + delta * 1.4, 0.0, 100.0)
        else:
            current_energy = clamp(current_energy - delta * 0.12, 0.0, 100.0)
        current_hunger = clamp(current_hunger + delta * 0.025, 0.0, 100.0)
        current_social_need = clamp(current_social_need + delta * (0.018 if hour >= 8 and hour < 21 else -0.010), 0.0, 100.0)
        if String(npc.get("state", "HOME")) == "SOCIAL":
            current_social_need = max(0.0, current_social_need - delta * 0.10)
        npc["energy"] = current_energy
        npc["hunger"] = current_hunger
        npc["social_need"] = current_social_need
        npc["mood"] = clamp(float(npc["mood"]) + sin(float(time_minutes) * 0.01) * delta - max(0.0, current_hunger - 80.0) * delta * 0.01 + max(0.0, 65.0 - current_social_need) * delta * 0.006, 0.0, 100.0)

func _move_player(delta: float) -> void:
    var input := Input.get_vector("ui_left", "ui_right", "ui_up", "ui_down") + touch_vector
    input = input.limit_length(1.0)
    if input.length() > 0.01:
        var move_speed := 4.5
        match transport:
            "Bike": move_speed = 6.2
            "Bus": move_speed = 5.4
        player.velocity = Vector3(input.x * move_speed, player.velocity.y, input.y * move_speed)
        player.move_and_slide()
        world_dirty = true
        _update_camera_transform()
        energy = clamp(energy - delta * 0.45, 0.0, 100.0)
        skills["Fitness"] = clamp(float(skills["Fitness"]) + delta * 0.004, 0.0, 100.0)
    else:
        player.velocity.x = 0
        player.velocity.z = 0
        energy = clamp(energy - delta * 0.12, 0.0, 100.0)

    hunger = clamp(hunger + delta * 0.08, 0.0, 100.0)
    hygiene = clamp(hygiene - delta * 0.02, 0.0, 100.0)
    stress = clamp(stress + max(0.0, hunger - 70.0) * delta * 0.01, 0.0, 100.0)
    mood = clamp(mood - max(0.0, hunger - 75.0) * delta * 0.01 - max(0.0, stress - 80.0) * delta * 0.005, 0.0, 100.0)
    player.position.x = clamp(player.position.x, -20.0, 20.0)
    player.position.z = clamp(player.position.z, -20.0, 20.0)
    player.position.y = 0.0

func _new_day() -> void:
    var completed_prayers := int(religious_state.get("prayers_today", 0))
    if completed_prayers >= 4:
        religious_state["prayer_streak"] = int(religious_state.get("prayer_streak", 0)) + 1
        religious_state["faith"] = clamp(float(religious_state["faith"]) + 1.0, 0.0, 100.0)
    elif completed_prayers > 0:
        religious_state["prayer_streak"] = 0
    else:
        religious_state["prayer_streak"] = 0
    religious_state["prayers_today"] = 0
    for key in prayer_flags.keys():
        prayer_flags[key] = false
    religious_state["fasting_today"] = false
    _update_religious_calendar()

    _simulate_world_day()
    _simulate_npcs_day()
    _simulate_business_day()

    # Personal daily expenses and recovery. Keep the model forgiving, not punishing.
    money -= 12.0
    energy = min(100.0, energy + 55.0 + float(home_level) * 2.0)
    hunger = min(100.0, hunger + 15.0)
    hygiene = max(20.0, hygiene - 5.0)
    home_cleanliness = max(25.0, home_cleanliness - 4.0)
    stress = max(0.0, stress - 12.0)

    government_budget += max(0.0, world_economy["city_activity"] * 0.35)
    business_revenue = 0.0

    _update_faction_opinions()
    _update_political_cycle()
    _generate_dynamic_event()
    _decay_event_cooldowns()
    _update_boredom_daily()
    weather = ["Sunny", "Cloudy", "Rain", "Sunny", "Windy"].pick_random()
    world_revision += 1
    world_dirty = true
    _save_world(true)

func _simulate_world_day() -> void:
    var inflation := float(world_economy["inflation"])
    var supply := float(world_economy["supply_stability"])
    var confidence := float(world_economy["consumer_confidence"])
    var business_confidence := float(world_economy["business_confidence"])

    var infrastructure_pull := (float(city_state["infrastructure"]) - 50.0) * 0.025
    var supply_pressure := (100.0 - supply) * 0.035
    var demand_pressure := (confidence - 50.0) * 0.020
    var policy_pressure := 0.0
    var productivity_bonus := 0.0

    if policies["small_business"]:
        policy_pressure -= 0.10
        productivity_bonus += 1.6
    if policies["education_fund"]:
        policy_pressure += 0.05
        productivity_bonus += 2.4
    if policies["public_transport"]:
        policy_pressure += 0.08
        productivity_bonus += 1.5

    world_economy["market_volatility"] = clamp(
        float(world_economy["market_volatility"]) * 0.82 +
        (100.0 - supply) * 0.10 +
        float(political_state["political_heat"]) * 0.07 +
        abs(inflation - 3.0) * 0.7,
        5.0, 70.0
    )
    world_economy["inflation"] = clamp(
        inflation + supply_pressure * 0.10 + demand_pressure * 0.12 + policy_pressure + randf_range(-0.28, 0.28),
        0.0, 18.0
    )

    confidence = clamp(
        confidence + demand_pressure - float(world_economy["inflation"]) * 0.04 - float(world_economy["household_pressure"]) * 0.015 + infrastructure_pull * 0.35 + randf_range(-1.6, 1.6),
        5.0, 100.0
    )
    business_confidence = clamp(
        business_confidence + (confidence - 50.0) * 0.035 - float(political_state["political_heat"]) * 0.025 - float(world_economy["market_volatility"]) * 0.018 + productivity_bonus * 0.18 + (float(religious_state["community"]) - 50.0) * 0.015 + randf_range(-1.8, 1.8),
        5.0, 100.0
    )

    world_economy["employment"] = clamp(
        float(world_economy["employment"]) + (business_confidence - 50.0) * 0.030 + (float(city_state["business_density"]) - 45.0) * 0.012 + randf_range(-0.8, 0.8),
        65.0, 100.0
    )
    world_economy["wage_index"] = clamp(
        float(world_economy["wage_index"]) +
        (float(world_economy["employment"]) - 82.0) * 0.11 -
        (float(world_economy["inflation"]) - 3.0) * 0.08 +
        productivity_bonus * 0.12 + randf_range(-0.8, 0.8),
        75.0, 135.0
    )
    world_economy["household_pressure"] = clamp(
        float(world_economy["inflation"]) * 4.2 + (100.0 - confidence) * 0.32 - (float(world_economy["wage_index"]) - 100.0) * 0.35,
        0.0, 100.0
    )
    world_economy["supply_stability"] = clamp(
        supply + randf_range(-2.5, 2.5) + infrastructure_pull * 0.35 - float(world_economy["market_volatility"]) * 0.015,
        25.0, 100.0
    )
    world_economy["production"] = clamp(
        86.0 + (float(world_economy["employment"]) - 75.0) * 0.56 + float(city_state["infrastructure"]) * 0.13 + productivity_bonus,
        60.0, 140.0
    )
    world_economy["consumer_confidence"] = confidence
    world_economy["business_confidence"] = business_confidence
    world_economy["city_activity"] = clamp(
        (confidence + float(world_economy["employment"]) + float(city_state["services"]) + float(city_state["public_order"])) / 4.0 - float(world_economy["household_pressure"]) * 0.08 + randf_range(-1.5, 1.5),
        0.0, 100.0
    )

    # Every market has its own pressure. Prices move gradually instead of teleporting after events.
    for item in prices.keys():
        var item_demand := float(demand[item])
        var product_bias := 0.0
        match String(item):
            "Food": product_bias = float(world_economy["household_pressure"]) * 0.004
            "Seeds": product_bias = (float(city_state["business_density"]) - 45.0) * 0.01
            "Book": product_bias = (float(factions["youth"]) - 50.0) * 0.008
        var price_move := 1.0 + (float(world_economy["inflation"]) - 3.0) * 0.006 + (item_demand - 1.0) * 0.035 + product_bias * 0.01 + randf_range(-0.015, 0.015)
        prices[item] = clamp(float(prices[item]) * price_move, 4.0, 60.0)
        demand[item] = clamp(item_demand * 0.94 + float(world_economy["consumer_confidence"]) / 100.0 * 0.06, 0.5, 1.8)

    market_sentiment = clamp(
        market_sentiment + (business_confidence - 50.0) * 0.08 - float(world_economy["market_volatility"]) * 0.04 + randf_range(-1.5, 1.5),
        0.0, 100.0
    )
    if investment_value > 0.0:
        var investment_return := (market_sentiment - 50.0) * 0.004 - (float(world_economy["market_volatility"]) - 15.0) * 0.001
        investment_value = max(0.0, investment_value * (1.0 + investment_return))

    _update_market()

func _simulate_business_day() -> void:
    for business in businesses:
        if not business["open"]:
            continue
        var demand_factor := 0.75 + float(world_economy["consumer_confidence"]) / 180.0
        var competition_factor := 1.0 - float(city_state["business_density"]) / 300.0
        var support_factor := 0.80 + public_support / 500.0
        var gross: float = business["base"] * demand_factor * competition_factor * support_factor
        var cost: float = business["cost"] + float(business["workers"]) * 8.0
        business_revenue += max(0.0, gross - cost)

    if not own_business["owned"] or not own_business["open"]:
        return

    var stock := int(own_business["stock"])
    if stock <= 0:
        own_business["open"] = false
        interaction_message = "Usaha berhenti: stok habis. Cari supplier sebelum buka lagi."
        return

    var level_factor := 1.0 + float(int(own_business["level"]) - 1) * 0.18
    var sector_factor := 1.0
    match String(business_state["sector"]):
        "Food": sector_factor = 1.08 if world_economy["consumer_confidence"] >= 50.0 else 1.02
        "Retail": sector_factor = 1.00 + float(world_economy["consumer_confidence"]) / 1000.0
        "Service": sector_factor = 0.98 + float(world_economy["employment"]) / 900.0
        "Technology": sector_factor = 0.88 + float(world_economy["business_confidence"]) / 500.0 + float(skills["Technology"]) / 500.0
        "Agriculture": sector_factor = 0.82 + float(world_economy["supply_stability"]) / 450.0
    var strategy_factor := 1.0
    match String(business_state["price_strategy"]):
        "Premium": strategy_factor = 1.08 if business_state["customer_trust"] > 55.0 else 0.90
        "Value": strategy_factor = 0.92 + float(world_economy["consumer_confidence"]) / 700.0
        _: strategy_factor = 1.0
    var marketing_factor := 1.0 + float(business_state["marketing"]) / 500.0
    var demand_factor := 0.70 + float(world_economy["consumer_confidence"]) / 170.0
    var ethics_factor := 1.0
    match String(business_state.get("ethics_mode", "Fair & Halal")):
        "Fair & Halal":
            ethics_factor = 0.99 + float(business_state["halal_integrity"]) * 0.002
        "Competitive":
            ethics_factor = 1.02
        "Aggressive":
            ethics_factor = 1.07 - float(business_state["halal_integrity"]) * 0.0015
    var gross: float = (35.0 + float(skills["Business"]) * 2.0 + public_support * 0.2) * level_factor * sector_factor * strategy_factor * marketing_factor * demand_factor * ethics_factor
    var supplier_cost := maxf(2.0, 7.0 - float(business_state["supplier_quality"]) * 0.03)
    var personal_cost: float = float(own_business["maintenance"]) + float(own_business["employees"]) * 8.0
    var stock_cost: float = min(gross * 0.38, float(stock) * supplier_cost)
    var personal_profit := maxf(-20.0, gross - personal_cost - stock_cost)
    own_business["stock"] = max(0, stock - max(1, int(level_factor)))
    business_revenue += personal_profit
    own_business["capital"] = max(0.0, float(own_business["capital"]) + personal_profit)
    own_business["reputation"] = clamp(float(own_business["reputation"]) + personal_profit * 0.008 + (business_state["customer_trust"] - 50.0) * 0.01, 0.0, 100.0)
    business_state["growth_points"] = max(0.0, float(business_state["growth_points"]) + max(0.0, personal_profit) * 0.12)
    var ethics_mode := String(business_state.get("ethics_mode", "Fair & Halal"))
    business_state["customer_trust"] = clamp(float(business_state["customer_trust"]) + (6.0 if ethics_mode == "Fair & Halal" else 2.0 if ethics_mode == "Competitive" else -2.0) - float(business_state["competitive_pressure"]) * 0.05 + randf_range(-1.0, 1.0), 0.0, 100.0)
    business_state["halal_integrity"] = clamp(float(business_state.get("halal_integrity", 88.0)) + (0.8 if ethics_mode == "Fair & Halal" else -0.15 if ethics_mode == "Competitive" else -1.2), 0.0, 100.0)
    if ethics_mode == "Fair & Halal":
        religious_state["community"] = clamp(float(religious_state["community"]) + 0.15, 0.0, 100.0)
    elif ethics_mode == "Aggressive":
        reputation = max(0.0, reputation - 0.15)
    business_state["competitive_pressure"] = clamp(float(business_state["competitive_pressure"]) + city_state["business_density"] * 0.01 - 0.4, 0.0, 100.0)
    money += max(0.0, business_revenue) * 0.10
    government_budget += max(0.0, business_revenue) * tax_rate

func _update_faction_opinions() -> void:
    factions["business"] = clamp(float(factions["business"]) + (world_economy["business_confidence"] - 50.0) * 0.035 + (0.8 if policies["small_business"] else -0.2) + randf_range(-1.2, 1.2), 0.0, 100.0)
    factions["workers"] = clamp(float(factions["workers"]) + (world_economy["employment"] - 90.0) * 0.08 + (1.5 if policies["education_fund"] else 0.0) + randf_range(-1.2, 1.2), 0.0, 100.0)
    factions["youth"] = clamp(float(factions["youth"]) + (1.5 if policies["education_fund"] else -0.2) + political_state["trust"] * 0.01 - political_state["political_heat"] * 0.01 + randf_range(-1.4, 1.4), 0.0, 100.0)
    factions["public"] = clamp(float(factions["public"]) + (float(world_economy["consumer_confidence"]) - 50.0) * 0.03 + (city_state["services"] - 50.0) * 0.025 + randf_range(-1.5, 1.5), 0.0, 100.0)
    factions["community"] = clamp(float(factions["community"]) + (city_state["public_order"] - 70.0) * 0.03 + political_state["trust"] * 0.012 + randf_range(-1.0, 1.0), 0.0, 100.0)
    public_support = clamp((factions["business"] * 0.18 + factions["workers"] * 0.18 + factions["youth"] * 0.18 + factions["public"] * 0.28 + factions["community"] * 0.18), 0.0, 100.0)

func _update_political_cycle() -> void:
    political_state["election_cycle_day"] = int(political_state["election_cycle_day"]) + 1

    var policy_cost := 0.0
    if policies["small_business"]: policy_cost += 55.0
    if policies["education_fund"]: policy_cost += 75.0
    if policies["public_transport"]: policy_cost += 95.0
    if policies.get("market_fairness", false): policy_cost += 65.0
    if policies.get("community_health", false): policy_cost += 85.0
    var treasury := float(government_budget)
    if policy_cost > treasury:
        political_state["policy_debt"] = clamp(float(political_state["policy_debt"]) + (policy_cost - treasury) * 0.08, 0.0, 100.0)
    else:
        political_state["policy_debt"] = max(0.0, float(political_state["policy_debt"]) - 1.6)
    government_budget = max(0.0, treasury - policy_cost)

    var implementation_quality := 0.0
    if policies["small_business"]:
        implementation_quality += float(factions["business"]) * 0.02
    if policies["education_fund"]:
        implementation_quality += float(factions["workers"] + factions["youth"]) * 0.01
    if policies["public_transport"]:
        implementation_quality += float(city_state["transport_quality"]) * 0.02
    if policies.get("market_fairness", false):
        implementation_quality += float(business_state["customer_trust"]) * 0.018
    if policies.get("community_health", false):
        implementation_quality += float(religious_state["community"]) * 0.018
    implementation_quality -= float(political_state["policy_debt"]) * 0.25

    political_state["consultation"] = clamp(
        float(political_state["consultation"]) * 0.85 + float(political_state["trust"]) * 0.18 - float(political_state["political_heat"]) * 0.07,
        0.0, 100.0
    )
    political_state["policy_approval"] = clamp(
        public_support + implementation_quality * 0.45 + float(political_state["consultation"]) * 0.12 - float(political_state["political_heat"]) * 0.25 - float(political_state["policy_debt"]) * 0.30,
        0.0, 100.0
    )
    political_state["coalition_strength"] = clamp(
        float(political_state["coalition_strength"]) * 0.90 +
        (float(factions["business"]) + float(factions["workers"]) + float(factions["public"])) / 3.0 * 0.10 +
        float(political_state["trust"]) * 0.04 -
        float(political_state["political_heat"]) * 0.03,
        0.0, 100.0
    )

    if day >= int(political_state["next_election_day"]):
        election_state["active"] = false
        political_state["next_election_day"] = day + 28
        political_state["election_cycle_day"] = 0
        political_state["political_heat"] = clamp(float(political_state["political_heat"]) + 12.0, 0.0, 100.0)
        interaction_message = "Siklus pemilu mendekat. Peta dukungan dan kepentingan berubah."

    if bool(election_state["won"]):
        election_state["term_days"] = max(0, int(election_state["term_days"]) - 1)
        if int(election_state["term_days"]) == 0:
            election_state["won"] = false
            political_state["political_heat"] = clamp(float(political_state["political_heat"]) + 4.0, 0.0, 100.0)
            interaction_message = "Masa jabatan selesai. Siklus politik baru terbuka."

    political_state["political_capital"] = clamp(
        float(political_state["political_capital"]) + float(political_state["influence"]) * 0.015 + float(political_state["coalition_strength"]) * 0.01 - 0.25,
        0.0, 100.0
    )
    political_state["trust"] = clamp(
        float(political_state["trust"]) + (public_support - float(political_state["trust"])) * 0.03 + (float(political_state["consultation"]) - 50.0) * 0.008 - float(political_state["political_heat"]) * 0.01,
        0.0, 100.0
    )
    political_state["political_heat"] = clamp(float(political_state["political_heat"]) * 0.96, 0.0, 100.0)

func _update_boredom_daily() -> void:
    boredom = clamp(boredom - 5.0 + novelty * 0.02, 0.0, 100.0)
    novelty = clamp(novelty * 0.92 + 8.0, 0.0, 100.0)

func _record_activity(action_name: String) -> void:
    var count := int(activity_memory.get(action_name, 0))
    activity_memory[action_name] = min(50, count + 1)
    if not recent_actions.is_empty() and recent_actions.back() == action_name:
        boredom = clamp(boredom + 4.0, 0.0, 100.0)
    elif action_name in recent_actions:
        boredom = clamp(boredom + 1.2, 0.0, 100.0)
    else:
        boredom = max(0.0, boredom - 2.5)
        novelty = clamp(novelty + 7.0, 0.0, 100.0)
    recent_actions.append(action_name)
    if recent_actions.size() > 6:
        recent_actions.pop_front()

func _mark_event(event_id: String) -> void:
    event_cooldowns[event_id] = day
    recent_events.append(event_id)
    if recent_events.size() > 12:
        recent_events.pop_front()

func _can_trigger_event(event_id: String, cooldown_days: int) -> bool:
    return day - int(event_cooldowns.get(event_id, -999)) >= cooldown_days

func _decay_event_cooldowns() -> void:
    for key in event_cooldowns.keys():
        if day - int(event_cooldowns[key]) > 21:
            event_cooldowns.erase(key)

func _generate_dynamic_event() -> void:
    var candidates: Array = []
    var pressure_bonus := float(boredom) * 0.20 + maxf(0.0, 55.0 - float(novelty)) * 0.15
    if float(world_economy["inflation"]) >= 6.0 and _can_trigger_event("inflation", 7):
        candidates.append({"id": "inflation", "score": 45.0 + float(world_economy["inflation"]) * 5.0 + pressure_bonus, "text": "Kenaikan inflasi memicu rapat harga dan tekanan publik."})
    if float(world_economy["business_confidence"]) <= 45.0 and _can_trigger_event("business_downturn", 6):
        candidates.append({"id": "business_downturn", "score": 58.0 + (45.0 - float(world_economy["business_confidence"])) * 2.2 + pressure_bonus, "text": "Pelaku usaha menahan ekspansi. Pemasok dan pasar baru menjadi lebih penting."})
    if float(political_state["political_heat"]) >= 40.0 and _can_trigger_event("political_debate", 6):
        candidates.append({"id": "political_debate", "score": 55.0 + float(political_state["political_heat"]) * 0.55 + pressure_bonus, "text": "Debat publik memanas. Reputasi dan koalisi mulai diperhitungkan."})
    if own_business["owned"] and float(business_state["growth_points"]) >= 10.0 and _can_trigger_event("business_opportunity", 5):
        candidates.append({"id": "business_opportunity", "score": 70.0 + boredom * 0.30, "text": "Peluang kontrak atau ekspansi muncul dari jaringan bisnis."})
    if float(public_support) >= 62.0 and _can_trigger_event("community_project", 8):
        candidates.append({"id": "community_project", "score": 55.0 + float(political_state["trust"]) * 0.25 + pressure_bonus, "text": "Komunitas membuka proyek kecil yang bisa memperkuat kepercayaan."})
    if float(city_state["public_order"]) <= 50.0 and _can_trigger_event("public_order", 5):
        candidates.append({"id": "public_order", "score": 66.0 + (50.0 - float(city_state["public_order"])) * 1.8, "text": "Gangguan ketertiban memaksa pemerintah menentukan prioritas layanan."})
    if bool(religious_state.get("is_ramadan", false)) and _can_trigger_event("ramadan_market", 6):
        candidates.append({"id": "ramadan_market", "score": 62.0 + float(religious_state["community"]) * 0.25, "text": "Bulan Ramadan menghidupkan pasar, kegiatan sosial, dan agenda komunitas."})
    if float(religious_state.get("community", 50.0)) >= 65.0 and _can_trigger_event("community_charity", 8):
        candidates.append({"id": "community_charity", "score": 55.0 + float(religious_state["community"]) * 0.35, "text": "Komunitas menggalang bantuan untuk warga yang membutuhkan."})
    if float(world_economy["supply_stability"]) <= 55.0 and _can_trigger_event("supply_shock", 7):
        candidates.append({"id": "supply_shock", "score": 64.0 + (55.0 - float(world_economy["supply_stability"])) * 1.5, "text": "Rantai pasok terganggu. Harga dan strategi bisnis perlu disesuaikan."})
    if not recent_events.is_empty():
        var last_event := String(recent_events.back())
        if last_event == "inflation" and _can_trigger_event("cost_of_living", 8):
            candidates.append({"id": "cost_of_living", "score": 72.0 + float(world_economy["household_pressure"]) * 0.35, "text": "Tekanan biaya hidup berlanjut. Upah, subsidi, dan daya beli mulai diperdebatkan."})
        elif last_event == "supply_shock" and _can_trigger_event("market_recovery", 8) and float(world_economy["supply_stability"]) >= 60.0:
            candidates.append({"id": "market_recovery", "score": 68.0 + float(world_economy["supply_stability"]) * 0.2, "text": "Rantai pasok mulai pulih. Ada ruang untuk mengembalikan harga dan memperbaiki stok."})
        elif last_event == "political_debate" and _can_trigger_event("coalition_shift", 8):
            candidates.append({"id": "coalition_shift", "score": 62.0 + float(political_state["political_heat"]) * 0.35, "text": "Kelompok kepentingan mengubah posisi. Koalisi lama tidak lagi otomatis solid."})
        elif last_event == "public_order" and _can_trigger_event("policy_backlash", 8):
            candidates.append({"id": "policy_backlash", "score": 60.0 + float(political_state["political_heat"]) * 0.25, "text": "Respons pemerintah dipertanyakan. Pilihan kebijakan mulai diuji di ruang publik."})

    if candidates.is_empty():
        interaction_message = "Kota bergerak normal. Ubah strategi, bangun jaringan, atau tunggu kondisi berikutnya."
        return

    # Weighted choice keeps the world less deterministic while still preferring high-pressure events.
    var total_score := 0.0
    for candidate in candidates: total_score += max(1.0, float(candidate["score"]))
    var roll := randf_range(0.0, total_score)
    var chosen: Dictionary = candidates.back()
    var cursor := 0.0
    for candidate in candidates:
        cursor += max(1.0, float(candidate["score"]))
        if roll <= cursor:
            chosen = candidate
            break
    var chosen_id := String(chosen["id"])
    _apply_dynamic_event(chosen_id)
    _mark_event(chosen_id)
    interaction_message = "Event: %s" % String(chosen["text"])
func _apply_dynamic_event(event_id: String) -> void:
    match event_id:
        "inflation":
            prices["Food"] = clamp(float(prices["Food"]) * 1.08, 4.0, 60.0)
            demand["Food"] = clamp(float(demand["Food"]) + 0.10, 0.5, 1.8)
            political_state["political_heat"] = clamp(float(political_state["political_heat"]) + 5.0, 0.0, 100.0)
            factions["public"] = clamp(float(factions["public"]) - 2.0, 0.0, 100.0)
        "business_downturn":
            city_state["business_density"] = max(15.0, float(city_state["business_density"]) - 2.0)
            business_state["competitive_pressure"] = clamp(float(business_state["competitive_pressure"]) + 4.0, 0.0, 100.0)
            political_state["political_capital"] = clamp(float(political_state["political_capital"]) + 2.0, 0.0, 100.0)
        "political_debate":
            political_state["political_heat"] = clamp(float(political_state["political_heat"]) + 7.0, 0.0, 100.0)
            if skills["PublicSpeaking"] >= 20.0:
                political_state["influence"] = clamp(float(political_state["influence"]) + 2.5, 0.0, 100.0)
        "business_opportunity":
            active_opportunity = {"id": "expand_contract", "title": "Kontrak katering", "kind": "business", "expires": day + 3}
            political_state["influence"] = clamp(float(political_state["influence"]) + 1.0, 0.0, 100.0)
        "community_project":
            city_state["services"] = clamp(float(city_state["services"]) + 2.5, 0.0, 100.0)
            political_state["trust"] = clamp(float(political_state["trust"]) + 4.0, 0.0, 100.0)
            reputation = clamp(reputation + 1.5, 0.0, 100.0)
        "public_order":
            city_state["public_order"] = clamp(float(city_state["public_order"]) - 6.0, 0.0, 100.0)
            political_state["political_heat"] = clamp(float(political_state["political_heat"]) + 4.0, 0.0, 100.0)
            active_opportunity = {"id": "public_service", "title": "Tanggap layanan warga", "kind": "community", "expires": day + 2}
        "supply_shock":
            world_economy["supply_stability"] = clamp(float(world_economy["supply_stability"]) - 8.0, 0.0, 100.0)
            prices["Food"] = clamp(float(prices["Food"]) * 1.10, 4.0, 60.0)
            demand["Food"] = clamp(float(demand["Food"]) + 0.08, 0.5, 1.8)
            business_state["competitive_pressure"] = clamp(float(business_state["competitive_pressure"]) + 5.0, 0.0, 100.0)
        "cost_of_living":
            political_state["political_heat"] = clamp(float(political_state["political_heat"]) + 4.0, 0.0, 100.0)
            factions["workers"] = clamp(float(factions["workers"]) - 2.5, 0.0, 100.0)
            factions["public"] = clamp(float(factions["public"]) - 2.0, 0.0, 100.0)
            active_opportunity = {"id": "cost_response", "title": "Program daya beli", "kind": "policy", "expires": day + 3}
        "market_recovery":
            world_economy["supply_stability"] = clamp(float(world_economy["supply_stability"]) + 7.0, 0.0, 100.0)
            market_sentiment = clamp(market_sentiment + 8.0, 0.0, 100.0)
            business_state["competitive_pressure"] = clamp(float(business_state["competitive_pressure"]) - 3.0, 0.0, 100.0)
        "coalition_shift":
            political_state["coalition_strength"] = clamp(float(political_state["coalition_strength"]) - 8.0, 0.0, 100.0)
            political_state["political_heat"] = clamp(float(political_state["political_heat"]) + 5.0, 0.0, 100.0)
        "policy_backlash":
            political_state["policy_approval"] = clamp(float(political_state["policy_approval"]) - 6.0, 0.0, 100.0)
            political_state["consultation"] = clamp(float(political_state["consultation"]) + 8.0, 0.0, 100.0)
            reputation = max(0.0, reputation - 1.0)
        "ramadan_market":
            market_sentiment = clamp(market_sentiment + 6.0, 0.0, 100.0)
            world_economy["city_activity"] = clamp(float(world_economy["city_activity"]) + 4.0, 0.0, 100.0)
            religious_state["community"] = clamp(float(religious_state["community"]) + 2.0, 0.0, 100.0)
        "community_charity":
            religious_state["community"] = clamp(float(religious_state["community"]) + 4.0, 0.0, 100.0)
            city_state["services"] = clamp(float(city_state["services"]) + 1.5, 0.0, 100.0)
            political_state["trust"] = clamp(float(political_state["trust"]) + 2.0, 0.0, 100.0)
            active_opportunity = {"id": "community_aid", "title": "Aksi bantuan warga", "kind": "community", "expires": day + 3}

func _normalize_dynamic_state() -> void:
    if typeof(world_economy) != TYPE_DICTIONARY:
        world_economy = {"inflation": 3.2, "employment": 92.0, "consumer_confidence": 62.0, "business_confidence": 58.0, "supply_stability": 84.0, "city_activity": 60.0, "production": 100.0, "wage_index": 100.0, "household_pressure": 24.0, "market_volatility": 18.0}
    var economy_defaults := {"wage_index": 100.0, "household_pressure": 24.0, "market_volatility": 18.0}
    for key in economy_defaults.keys():
        if not world_economy.has(key): world_economy[key] = economy_defaults[key]
    for key in world_economy.keys():
        world_economy[key] = float(world_economy[key])
    for key in factions.keys():
        factions[key] = clamp(float(factions[key]), 0.0, 100.0)
    var religious_defaults := {"faith": 60.0, "religion_knowledge": 5.0, "community": 50.0, "prayers_today": 0, "prayer_total": 0, "prayer_streak": 0, "charity_total": 0.0, "zakat_total": 0.0, "last_zakat_day": -999, "fasting_today": false, "calendar_day": 1, "calendar_month": 1, "is_ramadan": false}
    if typeof(religious_state) != TYPE_DICTIONARY:
        religious_state = religious_defaults.duplicate(true)
    for key in religious_defaults.keys():
        if not religious_state.has(key): religious_state[key] = religious_defaults[key]
    religious_state["faith"] = clamp(float(religious_state["faith"]), 0.0, 100.0)
    religious_state["religion_knowledge"] = clamp(float(religious_state["religion_knowledge"]), 0.0, 100.0)
    religious_state["community"] = clamp(float(religious_state["community"]), 0.0, 100.0)
    religious_state["prayers_today"] = clampi(int(religious_state["prayers_today"]), 0, 5)
    religious_state["prayer_total"] = max(0, int(religious_state["prayer_total"]))
    religious_state["prayer_streak"] = max(0, int(religious_state["prayer_streak"]))
    religious_state["charity_total"] = max(0.0, float(religious_state["charity_total"]))
    religious_state["zakat_total"] = max(0.0, float(religious_state["zakat_total"]))
    religious_state["last_zakat_day"] = int(religious_state["last_zakat_day"])
    religious_state["fasting_today"] = bool(religious_state["fasting_today"])
    _update_religious_calendar()
    if typeof(prayer_flags) != TYPE_DICTIONARY:
        prayer_flags = {"Subuh": false, "Dzuhur": false, "Ashar": false, "Maghrib": false, "Isya": false}
    for key in ["Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya"]:
        prayer_flags[key] = bool(prayer_flags.get(key, false))

    var career_defaults := {"rank": "Citizen", "organization": "Independent", "years_active": 0, "public_service": 0.0, "policy_wins": 0, "constituency": 50.0, "integrity": 82.0}
    for key in career_defaults.keys():
        if not political_career.has(key): political_career[key] = career_defaults[key]
    political_career["public_service"] = clamp(float(political_career["public_service"]), 0.0, 100.0)
    political_career["constituency"] = clamp(float(political_career["constituency"]), 0.0, 100.0)
    political_career["integrity"] = clamp(float(political_career["integrity"]), 0.0, 100.0)
    for key in enterprise_state.keys():
        enterprise_state[key] = float(enterprise_state[key])
    enterprise_state["employees"] = max(0, int(enterprise_state["employees"]))
    enterprise_state["supplier_reliability"] = clamp(float(enterprise_state["supplier_reliability"]), 0.0, 100.0)
    for key in civic_state.keys():
        civic_state[key] = clamp(float(civic_state[key]), 0.0, 100.0)
    for key in religious_calendar_state.keys():
        if key != "eid_ready": religious_calendar_state[key] = float(religious_calendar_state[key])
    religious_calendar_state["eid_ready"] = bool(religious_calendar_state["eid_ready"])
    var political_defaults := {"coalition_strength": 35.0, "policy_debt": 0.0, "consultation": 20.0}
    for key in political_defaults.keys():
        if not political_state.has(key): political_state[key] = political_defaults[key]
    for key in city_state.keys():
        city_state[key] = clamp(float(city_state[key]), 0.0, 100.0)
    for key in business_state.keys():
        business_state[key] = business_state[key]
    if typeof(active_opportunity) != TYPE_DICTIONARY:
        active_opportunity = {"id": "", "title": "", "kind": "", "expires": 0}
    if day > int(active_opportunity.get("expires", 0)):
        active_opportunity = {"id": "", "title": "", "kind": "", "expires": 0}
    for npc in npcs:
        if typeof(npc.get("memory", [])) != TYPE_ARRAY:
            npc["memory"] = []
        while npc["memory"].size() > 5:
            npc["memory"].pop_front()
        npc["social_need"] = clamp(float(npc.get("social_need", 25.0)), 0.0, 100.0)
        npc["work_performance"] = clamp(float(npc.get("work_performance", 0.75)), 0.2, 1.5)
        npc["trust"] = clamp(float(npc.get("trust", 50.0)), 0.0, 100.0)
        npc["goal_state"] = String(npc.get("goal_state", "stability"))

func _simulate_npcs_day() -> void:
    var wages := {"Teacher": 55.0, "Student": 18.0, "Shopkeeper": 48.0, "Mechanic": 62.0, "Designer": 70.0}
    for npc in npcs:
        var occupation: String = npc["occupation"]
        var base_wage := float(wages.get(occupation, 40.0)) * float(world_economy["wage_index"]) / 100.0
        var confidence_factor := 0.76 + float(world_economy["business_confidence"]) / 230.0
        var performance := clampf(float(npc.get("work_performance", 0.75)) + randf_range(-0.08, 0.08), 0.30, 1.20)
        if float(npc["energy"]) < 35.0 or float(npc["hunger"]) > 75.0:
            performance *= 0.72
        npc["work_performance"] = performance
        npc["money"] = max(0.0, float(npc["money"]) + base_wage * performance * confidence_factor)

        var living_cost := (8.0 + float(world_economy["inflation"]) * 0.7) * (1.0 + float(npc["money"]) / 900.0)
        npc["money"] = max(0.0, float(npc["money"]) - living_cost)
        npc["hunger"] = max(10.0, float(npc["hunger"]) - 25.0)
        npc["energy"] = min(100.0, float(npc["energy"]) + 45.0)
        npc["social_need"] = clamp(float(npc.get("social_need", 25.0)) - 28.0, 0.0, 100.0)

        var affinity := String(npc.get("political_affinity", "public"))
        var policy_delta := 0.0
        if policies["small_business"] and affinity == "business": policy_delta += 2.2
        if policies["education_fund"] and affinity in ["workers", "youth"]: policy_delta += 2.4
        if policies["public_transport"] and affinity in ["workers", "public"]: policy_delta += 1.8
        if float(political_state["political_heat"]) > 55.0: policy_delta -= 1.5

        npc["opinion"] = clamp(float(npc["opinion"]) + policy_delta + randf_range(-1.8, 1.8), 0.0, 100.0)
        npc["trust"] = clamp(float(npc.get("trust", 50.0)) + (float(npc["opinion"]) - 50.0) * 0.02 - float(political_state["political_heat"]) * 0.01 + randf_range(-0.9, 0.9), 0.0, 100.0)

        var goal_state := String(npc.get("goal_state", "stability"))
        if float(npc["hunger"]) > 70.0:
            goal_state = "security"
        elif float(npc.get("social_need", 25.0)) > 65.0:
            goal_state = "community"
        elif float(npc.get("money", 0.0)) < 35.0:
            goal_state = "income"
        elif float(political_state["political_heat"]) > 65.0:
            goal_state = "stability"
        elif policies["education_fund"] and occupation in ["Teacher", "Student"]:
            goal_state = "growth"
        npc["mood"] = clamp(
            float(npc["mood"]) +
            (policy_delta * 0.35) +
            (2.0 if goal_state == "community" else 0.0) -
            (1.8 if goal_state == "security" else 0.0) +
            (1.0 if float(npc["trust"]) > 65.0 else -0.6 if float(npc["trust"]) < 30.0 else 0.0) +
            randf_range(-2.5, 2.5),
            0.0, 100.0
        )

        var memory: Array = npc.get("memory", [])
        if day % 3 == 0:
            memory.append("D%d:%s:op%.0f:trust%.0f" % [day, goal_state, float(npc["opinion"]), float(npc["trust"])])
        while memory.size() > 6:
            memory.pop_front()
        npc["memory"] = memory

    # Three lightweight peer interactions create relationship drift and social context without a per-frame social simulation.
    if npcs.size() > 1:
        for i in range(min(3, npcs.size() - 1)):
            var a: Dictionary = npcs[i]
            var b: Dictionary = npcs[(i + 1) % npcs.size()]
            var affinity_a := String(a.get("political_affinity", "public"))
            var affinity_b := String(b.get("political_affinity", "public"))
            var compatibility := 1.0 if affinity_a == affinity_b else -0.35
            var trust_gap := absf(float(a.get("trust", 50.0)) - float(b.get("trust", 50.0)))
            var interaction_delta := compatibility * 0.8 - trust_gap * 0.006 + randf_range(-0.5, 0.5)
            a["relationship"] = clamp(float(a["relationship"]) + interaction_delta, -100.0, 100.0)
            b["relationship"] = clamp(float(b["relationship"]) + interaction_delta, -100.0, 100.0)
            a["social_need"] = max(0.0, float(a.get("social_need", 25.0)) - 5.0)
            b["social_need"] = max(0.0, float(b.get("social_need", 25.0)) - 5.0)


func _simulate_living_city(_dt: float) -> void:
    # Continuous causal chain: supply -> production -> wages -> households -> tax -> services -> trust.
    var supplier := float(enterprise_state["supplier_reliability"])
    var capacity := float(enterprise_state["production_capacity"])
    var demand_index := float(world_economy["consumer_confidence"]) / 100.0
    var output := capacity * (supplier / 100.0) * demand_index
    enterprise_state["production_capacity"] = clamp(capacity + (float(business_state["supplier_quality"]) - 70.0) * 0.002, 8.0, 200.0)
    enterprise_state["supplier_reliability"] = clamp(supplier + (float(world_economy["supply_stability"]) - supplier) * 0.004, 30.0, 100.0)
    enterprise_state["market_share"] = clamp(float(enterprise_state["market_share"]) + (output - 12.0) * 0.001, 0.5, 35.0)

    if own_business["owned"] and own_business["open"]:
        var wage := float(world_economy["wage_index"]) * 0.12 * float(own_business["employees"])
        var revenue: float = maxf(0.0, output * 0.35 * (float(own_business["level"]) + 0.5))
        var operating_cost := float(own_business["maintenance"]) + wage
        var profit := revenue - operating_cost
        enterprise_state["wage_bill"] = wage
        enterprise_state["monthly_profit"] = profit
        business_revenue = profit
        var tax: float = maxf(0.0, profit) * tax_rate
        enterprise_state["monthly_tax_paid"] = tax
        government_budget += tax * 0.02
        own_business["capital"] = max(0.0, float(own_business["capital"]) + profit * 0.005)
        business_state["customer_trust"] = clamp(float(business_state["customer_trust"]) + (float(business_state["halal_integrity"]) - 75.0) * 0.001, 0.0, 100.0)

    civic_state["services"] = clamp(float(civic_state["services"]) + (float(government_budget) / 5000.0 - 1.0) * 0.002, 20.0, 100.0)
    civic_state["safety"] = clamp(float(civic_state["safety"]) + (float(city_state["public_order"]) - float(civic_state["safety"])) * 0.002, 20.0, 100.0)
    public_support = clamp(public_support + (float(civic_state["services"]) - 60.0) * 0.002 + (float(religious_state["community"]) - 50.0) * 0.001, 0.0, 100.0)

    for npc in npcs:
        var affordability := 100.0 - float(world_economy["household_pressure"])
        npc["income"] = max(20.0, float(npc.get("income", 70.0)) * (0.999 + (float(world_economy["wage_index"]) - 100.0) * 0.00003))
        npc["mood"] = clamp(float(npc["mood"]) + (affordability - 60.0) * 0.0008, 0.0, 100.0)
        npc["opinion"] = clamp(float(npc["opinion"]) + (public_support - 50.0) * 0.0008, 0.0, 100.0)

func _advance_political_career() -> void:
    var integrity := float(political_career["integrity"])
    var service := float(political_career["public_service"]) + 4.0
    var trust := float(political_state["trust"])
    political_career["public_service"] = clamp(service, 0.0, 100.0)
    political_state["influence"] = clamp(float(political_state["influence"]) + 2.0, 0.0, 100.0)
    political_state["political_capital"] = clamp(float(political_state["political_capital"]) + 2.5, 0.0, 100.0)
    political_state["trust"] = clamp(trust + (integrity - 70.0) * 0.05 + 0.8, 0.0, 100.0)
    if service >= 70.0:
        political_career["rank"] = "Community Leader"
    elif service >= 40.0:
        political_career["rank"] = "Organizer"
    else:
        political_career["rank"] = "Citizen"
    interaction_message = "Karier politik: %s · pelayanan %.0f · trust %.0f." % [political_career["rank"], service, political_state["trust"]]

func _simulate_public_budget() -> void:
    var allocation := 0.0
    if policies["education_fund"]:
        allocation += 90.0
        civic_state["education"] = clamp(float(civic_state["education"]) + 1.5, 0.0, 100.0)
    if policies["community_health"]:
        allocation += 80.0
        civic_state["health"] = clamp(float(civic_state["health"]) + 1.5, 0.0, 100.0)
    if policies["public_transport"]:
        allocation += 100.0
        civic_state["mobility"] = clamp(float(civic_state["mobility"]) + 1.5, 0.0, 100.0)
    government_budget = max(0.0, government_budget - allocation)
    city_state["services"] = clamp((float(civic_state["education"]) + float(civic_state["health"]) + float(civic_state["mobility"])) / 3.0, 0.0, 100.0)
    political_state["policy_approval"] = clamp(political_state["policy_approval"] + (float(city_state["services"]) - 60.0) * 0.08, 0.0, 100.0)
    interaction_message = "Anggaran publik disimulasikan: layanan %.0f · approval %.0f." % [city_state["services"], political_state["policy_approval"]]

func _manage_employees() -> void:
    if not own_business["owned"]:
        own_business["owned"] = true
        own_business["open"] = true
        own_business["capital"] = max(float(own_business["capital"]), 100.0)
    var max_workers := 1 + int(own_business["level"]) * 2
    if int(own_business["employees"]) < max_workers and money >= 20.0:
        own_business["employees"] += 1
        enterprise_state["employees"] = own_business["employees"]
        money -= 20.0
        reputation = clamp(reputation + 0.8, 0.0, 100.0)
        interaction_message = "Karyawan direkrut. Tim: %d/%d." % [own_business["employees"], max_workers]
    else:
        interaction_message = "Tim optimal: %d/%d atau modal belum cukup." % [own_business["employees"], max_workers]

func _manage_supply_chain() -> void:
    var cost := 18.0 + float(own_business["level"]) * 4.0
    if money >= cost:
        money -= cost
        enterprise_state["supplier_reliability"] = clamp(float(enterprise_state["supplier_reliability"]) + 7.0, 0.0, 100.0)
        business_state["supplier_quality"] = clamp(float(business_state["supplier_quality"]) + 3.0, 0.0, 100.0)
        own_business["stock"] += 6
        interaction_message = "Rantai pasok diperkuat: stok +6, reliabilitas %.0f." % enterprise_state["supplier_reliability"]
    else:
        interaction_message = "Modal belum cukup untuk kontrak pemasok."

func _waqf_action() -> void:
    var amount := minf(30.0, money)
    if amount <= 0.0:
        interaction_message = "Belum ada dana untuk simulasi wakaf."
        return
    money -= amount
    religious_calendar_state["waqf_total"] = float(religious_calendar_state["waqf_total"]) + amount
    religious_state["community"] = clamp(float(religious_state["community"]) + 2.0, 0.0, 100.0)
    civic_state["services"] = clamp(float(civic_state["services"]) + 0.4, 0.0, 100.0)
    interaction_message = "Dana fasilitas komunitas +$%.0f. Total wakaf simulasi $%.0f." % [amount, religious_calendar_state["waqf_total"]]

func _emit_world_state() -> void:
    var payload := {
        "version": 1,
        "type": "WORLD_STATE",
        "day": day,
        "money": snapped(money, 0.1),
        "business_profit": snapped(float(enterprise_state["monthly_profit"]), 0.1),
        "political_rank": political_career["rank"],
        "political_trust": snapped(float(political_state["trust"]), 0.1),
        "faith": snapped(float(religious_state["faith"]), 0.1),
        "community": snapped(float(religious_state["community"]), 0.1),
        "services": snapped(float(civic_state["services"]), 0.1),
    }
    _emit_android_event("WORLD_STATE", "Zahra World State", JSON.stringify(payload))

func _update_market() -> void:
    market_sentiment = clamp((market_sentiment * 0.72) + float(world_economy["consumer_confidence"]) * 0.18 + float(world_economy["business_confidence"]) * 0.10 + randf_range(-2.5, 2.5), 0.0, 100.0)
    var inflation_factor := 1.0 + (float(world_economy["inflation"]) - 3.0) * 0.012
    for item in prices.keys():
        var pressure := (float(demand[item]) - 1.0) * 0.06 + (market_sentiment - 50.0) / 2500.0
        demand[item] = clamp(float(demand[item]) + pressure + randf_range(-0.025, 0.025), 0.5, 1.8)
        prices[item] = clamp(float(prices[item]) * inflation_factor * (0.98 + float(demand[item]) * 0.02), 4.0, 60.0)

func _generate_daily_event() -> void:
    _generate_dynamic_event()

func _update_environment() -> void:
    if sun_light == null or world_env == null:
        return
    var hour_f := float(time_minutes) / 60.0
    # 0 = malam, 1 = tengah hari (matahari terbit 06:00, terbenam 18:00)
    var daylight := clampf(sin((hour_f - 6.0) / 12.0 * PI), 0.0, 1.0)
    var overcast := 0.0
    if weather == "Rain":
        overcast = 0.35
    sun_light.light_energy = (0.12 + daylight * 1.08) * (1.0 - overcast)
    sun_light.rotation_degrees = Vector3(-14.0 - daylight * 56.0, -25.0, 0.0)
    world_env.ambient_light_energy = (0.38 + daylight * 0.62) * (1.0 - overcast * 0.5)
    world_env.background_color = Color("#16203A").lerp(Color("#DCE9E4"), daylight * (1.0 - overcast * 0.4))

func _build_hud() -> void:
    hud = CanvasLayer.new()
    add_child(hud)

    var root := Control.new()
    root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    hud.add_child(root)

    var panel := ColorRect.new()
    panel.color = Color(0.04, 0.055, 0.065, 0.86)
    panel.position = Vector2(10, 10)
    var viewport_width := get_viewport().get_visible_rect().size.x
    var width := minf(700.0, viewport_width - 20.0)
    panel.size = Vector2(maxf(320.0, width), 228)
    root.add_child(panel)

    status_label = Label.new()
    status_label.position = Vector2(12, 8)
    status_label.add_theme_font_size_override("font_size", 13)
    status_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
    status_label.size = Vector2(panel.size.x - 24, 150)
    panel.add_child(status_label)

    perf_label = Label.new()
    perf_label.position = Vector2(12, 170)
    perf_label.add_theme_font_size_override("font_size", 9)
    panel.add_child(perf_label)

    message_label = Label.new()
    message_label.position = Vector2(12, 190)
    message_label.add_theme_font_size_override("font_size", 12)
    message_label.size = Vector2(panel.size.x - 24, 32)
    message_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
    panel.add_child(message_label)

    var tabs := HBoxContainer.new()
    tabs.position = Vector2(10, 246)
    tabs.size = Vector2(minf(700.0, viewport_width - 20.0), 40)
    root.add_child(tabs)
    var categories := [
        ["life", "KEHIDUPAN"],
        ["religion", "AGAMA"],
        ["business", "BISNIS"],
        ["politics", "POLITIK"],
    ]
    for item in categories:
        var tab := Button.new()
        tab.text = item[1]
        tab.custom_minimum_size = Vector2(0, 38)
        tab.size_flags_horizontal = Control.SIZE_EXPAND_FILL
        var category_id: String = item[0]
        tab.pressed.connect(func() -> void:
            current_action_category = category_id
            _rebuild_action_buttons()
        )
        tabs.add_child(tab)

    action_category_label = Label.new()
    action_category_label.position = Vector2(12, 290)
    action_category_label.add_theme_font_size_override("font_size", 12)
    root.add_child(action_category_label)

    var scroll := ScrollContainer.new()
    scroll.position = Vector2(10, 308)
    scroll.size = Vector2(minf(430.0, viewport_width - 20.0), minf(400.0, get_viewport().get_visible_rect().size.y - 485.0))
    scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
    root.add_child(scroll)

    action_container = GridContainer.new()
    action_container.columns = 2
    action_container.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    scroll.add_child(action_container)

    _rebuild_action_buttons()

    var touch := GridContainer.new()
    touch.columns = 3
    touch.anchor_left = 1.0
    touch.anchor_right = 1.0
    touch.anchor_top = 1.0
    touch.anchor_bottom = 1.0
    touch.offset_left = -250.0
    touch.offset_top = -150.0
    touch.offset_right = -12.0
    touch.offset_bottom = -14.0
    root.add_child(touch)
    _touch_button(touch, "↖", Vector2(-1, -1))
    _touch_button(touch, "↑", Vector2(0, -1))
    _touch_button(touch, "↗", Vector2(1, -1))
    _touch_button(touch, "←", Vector2(-1, 0))
    touch.add_child(Control.new())
    _touch_button(touch, "→", Vector2(1, 0))
    _touch_button(touch, "↙", Vector2(-1, 1))
    _touch_button(touch, "↓", Vector2(0, 1))
    _touch_button(touch, "↘", Vector2(1, 1))

func _action_entries_for_category(category: String) -> Array:
    match category:
        "religion":
            return [
                ["Interaksi Lokasi", "interact"], ["Salat / Ibadah", "pray"],
                ["Belajar Agama", "religion_study"], ["Kegiatan Masjid", "mosque_event"],
                ["Aksi Sosial", "charity"], ["Wakaf / Fasilitas", "waqf"], ["Zakat (Simulasi)", "zakat"],
                ["Puasa Ramadan", "fasting"],
            ]
        "business":
            return [
                ["Interaksi Lokasi", "interact"], ["Bangun / Buka Usaha", "business"],
                ["Stok Usaha", "restock_business"], ["Upgrade Usaha", "upgrade_business"],
                ["Sektor Usaha", "business_sector"], ["Strategi Harga", "business_strategy"],
                ["Etika Operasi", "business_ethics"], ["Kelola Karyawan", "employees"], ["Rantai Pasok", "supply_chain"], ["Deal Bisnis", "business_deal"],
                ["Riset Pasar", "market_research"], ["Negosiasi", "negotiate"],
                ["Ambil Peluang", "opportunity"], ["Investasi $50", "invest"],
                ["Tarik Investasi", "withdraw_investment"],
            ]
        "politics":
            return [
                ["Interaksi Lokasi", "interact"], ["Forum Publik", "forum"],
                ["Debat", "debate"], ["Usulan Kebijakan", "proposal"],
                ["Kebijakan", "policy"], ["Pemilu Fiktif", "election"],
                ["Forum Warga", "community"], ["Strategi Politik", "political_strategy"],
                ["Kampanye", "campaign"], ["Jenjang Karier Politik", "political_career"], ["Simulasi Anggaran", "budget_policy"], ["Ambil Peluang", "opportunity"],
            ]
        _:
            return [
                ["Interaksi Lokasi", "interact"], ["Makan", "eat"],
                ["Mandi", "wash"], ["Pilih Kerja", "job"], ["Kerja", "work"],
                ["Belajar", "study"], ["Masak", "cook"], ["Berkebun", "garden"],
                ["Beli", "buy"], ["Jual", "sell"], ["Ngobrol", "social"],
                ["Transportasi", "transport"], ["Upgrade Rumah", "home_upgrade"],
                ["Bank +$20", "deposit"], ["Tarik Bank", "withdraw"],
                ["Istirahat", "rest"], ["Tidur", "sleep"],
                ["Simpan", "save"], ["Muat", "load"],
            ]

func _rebuild_action_buttons() -> void:
    if action_container == null:
        return
    for child in action_container.get_children():
        child.queue_free()
    var names := {
        "life": "Kehidupan pribadi",
        "religion": "Agama & komunitas",
        "business": "Bisnis & ekonomi",
        "politics": "Politik & pemerintahan",
    }
    action_category_label.text = "MENU · %s" % String(names.get(current_action_category, "Kehidupan"))
    for entry in _action_entries_for_category(current_action_category):
        var button := Button.new()
        button.text = entry[0]
        button.custom_minimum_size = Vector2(190, 44)
        button.focus_mode = Control.FOCUS_NONE
        var action_name: String = entry[1]
        button.pressed.connect(func() -> void: _action(action_name))
        action_container.add_child(button)

func _touch_button(parent: Container, text: String, vector: Vector2) -> void:
    var button := Button.new()
    button.text = text
    button.focus_mode = Control.FOCUS_NONE
    parent.add_child(button)
    button.button_down.connect(func() -> void: touch_vector = vector)
    button.button_up.connect(func() -> void: touch_vector = Vector2.ZERO)

func _prayer_action() -> void:
    var prayer_name := _nearest_prayer_name()
    if prayer_name.is_empty():
        religious_state["faith"] = clamp(float(religious_state["faith"]) + 0.35, 0.0, 100.0)
        interaction_message = "Ibadah pribadi dilakukan. Untuk simulasi salat, gunakan waktu yang dekat dengan jadwal salat."
        return
    if bool(prayer_flags.get(prayer_name, false)):
        interaction_message = "%s sudah tercatat hari ini." % prayer_name
        return
    prayer_flags[prayer_name] = true
    religious_state["prayers_today"] = int(religious_state["prayers_today"]) + 1
    religious_state["prayer_total"] = int(religious_state["prayer_total"]) + 1
    religious_state["faith"] = clamp(float(religious_state["faith"]) + 2.5, 0.0, 100.0)
    religious_state["community"] = clamp(float(religious_state["community"]) + 0.7, 0.0, 100.0)
    mood = clamp(mood + 2.0, 0.0, 100.0)
    stress = clamp(stress - 1.5, 0.0, 100.0)
    interaction_message = "%s selesai. Konsistensi ibadah menjadi bagian dari ritme hidup karakter." % prayer_name
    _emit_android_event("PRAYER_COMPLETED", "Ibadah dalam game", "%s tercatat pada hari %d." % [prayer_name, day])

func _religion_study() -> void:
    if energy < 8.0:
        interaction_message = "Energi terlalu rendah untuk belajar."
        return
    energy -= 8.0
    religious_state["religion_knowledge"] = clamp(float(religious_state["religion_knowledge"]) + 1.6, 0.0, 100.0)
    religious_state["faith"] = clamp(float(religious_state["faith"]) + 1.0, 0.0, 100.0)
    skills["Study"] = clamp(float(skills["Study"]) + 0.8, 0.0, 100.0)
    interaction_message = "Belajar agama selesai. Pengetahuan dan ketenangan karakter meningkat."
    _advance_game_minutes(35)

func _mosque_event() -> void:
    var distance := player.global_position.distance_to(Vector3(0, 0, -14))
    if distance > 8.0:
        interaction_message = "Pergi ke Masjid terlebih dahulu untuk mengikuti kegiatan komunitas."
        return
    religious_state["community"] = clamp(float(religious_state["community"]) + 3.0, 0.0, 100.0)
    political_state["trust"] = clamp(float(political_state["trust"]) + 1.2, 0.0, 100.0)
    reputation = clamp(reputation + 0.5, 0.0, 100.0)
    mood = clamp(mood + 2.5, 0.0, 100.0)
    interaction_message = "Kegiatan masjid selesai. Komunitas dan jaringan sosialmu menguat."
    _advance_game_minutes(45)

func _charity_action() -> void:
    var amount := minf(25.0, money)
    if amount < 10.0:
        interaction_message = "Sisihkan sedikit uang terlebih dahulu untuk aksi sosial."
        return
    money -= amount
    religious_state["charity_total"] = float(religious_state["charity_total"]) + amount
    religious_state["community"] = clamp(float(religious_state["community"]) + 4.0, 0.0, 100.0)
    city_state["services"] = clamp(float(city_state["services"]) + 1.2, 0.0, 100.0)
    political_state["trust"] = clamp(float(political_state["trust"]) + 1.4, 0.0, 100.0)
    reputation = clamp(reputation + 0.9, 0.0, 100.0)
    interaction_message = "Aksi sosial tersalurkan sebesar $%.0f. Dampaknya tercatat pada komunitas." % amount
    _advance_game_minutes(20)

func _zakat_action() -> void:
    var wealth := money + bank + investment_value
    if wealth < 400.0:
        interaction_message = "Simulasi zakat: aset belum mencapai ambang game $400."
        return
    if day - int(religious_state["last_zakat_day"]) < 30:
        interaction_message = "Zakat sudah tercatat pada siklus game ini."
        return
    var amount := wealth * 0.025
    amount = minf(amount, money)
    if amount < 10.0:
        interaction_message = "Dana tunai tidak cukup untuk simulasi zakat saat ini."
        return
    money -= amount
    religious_state["zakat_total"] = float(religious_state["zakat_total"]) + amount
    religious_state["last_zakat_day"] = day
    religious_state["community"] = clamp(float(religious_state["community"]) + 6.0, 0.0, 100.0)
    religious_state["faith"] = clamp(float(religious_state["faith"]) + 1.5, 0.0, 100.0)
    city_state["services"] = clamp(float(city_state["services"]) + 2.0, 0.0, 100.0)
    interaction_message = "Simulasi zakat tercatat: $%.0f kembali ke ekosistem sosial kota." % amount
    _advance_game_minutes(25)

func _fasting_action() -> void:
    if not bool(religious_state.get("is_ramadan", false)):
        interaction_message = "Mode puasa ini tersedia pada Ramadan dalam kalender game."
        return
    religious_state["fasting_today"] = not bool(religious_state["fasting_today"])
    if bool(religious_state["fasting_today"]):
        religious_state["community"] = clamp(float(religious_state["community"]) + 1.5, 0.0, 100.0)
        interaction_message = "Puasa hari ini diaktifkan. Tetap gunakan kebutuhan karakter secara wajar."
    else:
        interaction_message = "Mode puasa hari ini dinonaktifkan."

func _business_ethics() -> void:
    if not own_business["owned"]:
        interaction_message = "Bangun usaha terlebih dahulu untuk mengatur etika operasi."
        return
    var modes := ["Fair & Halal", "Competitive", "Aggressive"]
    var current := modes.find(String(business_state.get("ethics_mode", "Fair & Halal")))
    business_state["ethics_mode"] = modes[(current + 1) % modes.size()]
    var mode := String(business_state["ethics_mode"])
    interaction_message = "Mode operasi: %s. Dampaknya terasa pada margin, kepercayaan, dan integritas usaha." % mode
    _advance_game_minutes(10)

func _interact_location() -> void:
    var candidates := [
        ["Rumah", Vector3(-8, 0, -8), "home"],
        ["Pasar", Vector3(7, 0, -8), "market"],
        ["Kafe", Vector3(-8, 0, 8), "cafe"],
        ["Sekolah", Vector3(7, 0, 8), "school"],
        ["Masjid", Vector3(0, 0, -14), "mosque"],
        ["Balai Kota", Vector3(0, 0, 14), "government"],
        ["Business Hub", Vector3(-14, 0, 14), "business"],
        ["Rumah Sakit", Vector3(14, 0, -14), "hospital"],
        ["Community Center", Vector3(14, 0, 14), "community"],
        ["Taman", Vector3(14, 0, 0), "park"],
    ]
    var best_name := ""
    var best_kind := ""
    var best_distance := 999.0
    for candidate in candidates:
        var distance: float = player.global_position.distance_to(candidate[1])
        if distance < best_distance:
            best_distance = distance
            best_name = candidate[0]
            best_kind = candidate[2]
    if best_distance > 7.5:
        interaction_message = "Belum ada lokasi penting di dekatmu."
        return
    match best_kind:
        "home":
            interaction_message = "Rumah: istirahat, masak, berkebun, dan menyimpan progres."
        "market":
            interaction_message = "Pasar: belanja, jual barang, membaca harga, dan bertemu pedagang."
        "cafe":
            _social_interaction()
        "school":
            _record_activity("study")
            interaction_message = "Sekolah: tempat belajar dan meningkatkan skill."
        "mosque":
            _mosque_event()
        "government":
            _community_meeting()
            interaction_message = "Balai Kota: anggaran, konsultasi publik, kebijakan, dan karier pelayanan publik."
        "business":
            _market_research()
            interaction_message = "Business Hub: pemasok, kontrak, tenaga kerja, ekspansi, dan strategi pasar."
        "hospital":
            health = min(100.0, health + 8.0)
            stress = max(0.0, stress - 3.0)
            interaction_message = "Pemeriksaan kesehatan sederhana selesai."
        "community":
            _community_meeting()
        "park":
            mood = min(100.0, mood + 6.0)
            stress = max(0.0, stress - 4.0)
            interaction_message = "Istirahat di taman membuat suasana hati membaik."
    interaction_message = "%s · %s" % [best_name, interaction_message]

func _action(action_name: String) -> void:
    interaction_message = ""
    _record_activity(action_name)
    world_dirty = true
    match action_name:
        "eat":
            if inventory["Food"] > 0:
                inventory["Food"] -= 1
                hunger = max(0.0, hunger - 35.0)
                health = min(100.0, health + 1.5)
                mood = min(100.0, mood + 5.0)
                demand["Food"] = clamp(float(demand["Food"]) + 0.02, 0.5, 1.8)
                _advance_game_minutes(10)
            else:
                interaction_message = "Tidak ada makanan."
        "wash":
            hygiene = min(100.0, hygiene + 40.0)
            health = min(100.0, health + 0.8)
            home_cleanliness = min(100.0, home_cleanliness + 5.0)
            energy = max(0.0, energy - 3.0)
            _advance_game_minutes(10)
        "job":
            _cycle_job()
        "transport":
            _cycle_transport()
        "home_upgrade":
            _upgrade_home()
        "interact":
            _interact_location()
        "pray":
            _prayer_action()
        "religion_study":
            _religion_study()
        "mosque_event":
            _mosque_event()
        "charity":
            _charity_action()
        "zakat":
            _zakat_action()
        "waqf":
            _waqf_action()
        "fasting":
            _fasting_action()
        "business_ethics":
            _business_ethics()
        "employees":
            _manage_employees()
            _advance_game_minutes(30)
        "supply_chain":
            _manage_supply_chain()
            _advance_game_minutes(30)
        "work":
            if energy >= 15.0:
                var job: Dictionary = job_catalog[job_index]
                var skill_level := float(skills[job["skill"]])
                var wage := float(job["salary"]) + skill_level * 0.55
                money += wage
                energy -= 15.0
                reputation = clamp(reputation + 1.0, 0.0, 100.0)
                skills["Technology"] = clamp(float(skills["Technology"]) + 0.4, 0.0, 100.0)
                mood = max(0.0, mood - 2.0)
                interaction_message = "Kerja %s selesai: +$%.0f." % [current_job, wage]
                _advance_game_minutes(60)
            else:
                interaction_message = "Energi tidak cukup untuk bekerja."
        "study":
            if energy >= 10.0:
                energy -= 10.0
                skills["Study"] = clamp(float(skills["Study"]) + 1.5, 0.0, 100.0)
                skills["Technology"] = clamp(float(skills["Technology"]) + 0.4, 0.0, 100.0)
                stress = clamp(stress + 1.0, 0.0, 100.0)
                _advance_game_minutes(50)
            else:
                interaction_message = "Energi tidak cukup untuk belajar."
        "cook":
            if inventory["Food"] < 10:
                inventory["Food"] += 2
                skills["Cooking"] = clamp(float(skills["Cooking"]) + 1.2, 0.0, 100.0)
                energy = max(0.0, energy - 4.0)
                interaction_message = "Masak berhasil: +2 Food."
                _advance_game_minutes(30)
            else:
                interaction_message = "Persediaan Food sudah cukup."
        "garden":
            if inventory["Seeds"] > 0:
                inventory["Seeds"] -= 1
                var harvest := 2 + int(float(skills["Gardening"]) / 20.0)
                inventory["Food"] += harvest
                skills["Gardening"] = clamp(float(skills["Gardening"]) + 1.3, 0.0, 100.0)
                interaction_message = "Panen kebun: +%d Food." % harvest
                _advance_game_minutes(40)
            else:
                interaction_message = "Benih habis."
        "buy":
            var cost: float = float(prices["Food"])
            if money >= cost:
                money -= cost
                inventory["Food"] += 2
                demand["Food"] = clamp(float(demand["Food"]) - 0.04, 0.5, 1.8)
                interaction_message = "Membeli 2 Food seharga $%.0f." % cost
                _advance_game_minutes(15)
            else:
                interaction_message = "Uang tidak cukup."
        "sell":
            if inventory["Food"] > 0:
                inventory["Food"] -= 1
                var sell_price: float = float(prices["Food"]) * 0.7
                money += sell_price
                demand["Food"] = clamp(float(demand["Food"]) + 0.05, 0.5, 1.8)
                interaction_message = "Menjual 1 Food seharga $%.0f." % sell_price
                _advance_game_minutes(15)
            else:
                interaction_message = "Tidak ada Food untuk dijual."
        "negotiate":
            var score := float(skills["Negotiation"]) + float(reputation) + float(mood) * 0.35
            if score > 80.0:
                money += 65.0
                reputation = clamp(reputation + 2.0, 0.0, 100.0)
                skills["Negotiation"] = clamp(float(skills["Negotiation"]) + 1.0, 0.0, 100.0)
                interaction_message = "Negosiasi berhasil. Kamu memilih tuntutan dengan risiko terukur."
            else:
                money += 15.0
                reputation = max(0.0, reputation - 1.0)
                interaction_message = "Hasil negosiasi kurang optimal."
            _advance_game_minutes(30)
        "social":
            _social_interaction()
            _advance_game_minutes(30)
        "forum":
            public_support = clamp(public_support + float(skills["Communication"]) * 0.06, 0.0, 100.0)
            reputation = clamp(reputation + 0.7, 0.0, 100.0)
            skills["Communication"] = clamp(float(skills["Communication"]) + 1.0, 0.0, 100.0)
            government_budget = max(0.0, government_budget - 15.0)
            interaction_message = "Forum publik selesai. Warga memberi masukan."
            _advance_game_minutes(60)
        "debate":
            _run_debate()
            _advance_game_minutes(60)
        "proposal":
            _start_or_advance_proposal()
            _advance_game_minutes(30)
        "policy":
            _toggle_policy()
            _advance_game_minutes(30)
        "election":
            _run_election()
            _advance_game_minutes(60)
        "business":
            _manage_business()
            _advance_game_minutes(15)
        "restock_business":
            _restock_business()
            _advance_game_minutes(15)
        "upgrade_business":
            _upgrade_business()
            _advance_game_minutes(30)
        "business_deal":
            _business_deal()
            _advance_game_minutes(45)
        "market_research":
            _market_research()
            _advance_game_minutes(30)
        "community":
            _community_meeting()
            _advance_game_minutes(45)
        "political_strategy":
            _political_strategy()
            _advance_game_minutes(45)
        "campaign":
            _campaign_activity()
            _advance_game_minutes(60)
        "political_career":
            _advance_political_career()
            _advance_game_minutes(30)
        "budget_policy":
            _simulate_public_budget()
            _advance_game_minutes(30)
        "opportunity":
            _claim_opportunity()
            _advance_game_minutes(30)
        "business_sector":
            _cycle_business_sector()
        "business_strategy":
            _cycle_business_strategy()
        "deposit":
            if money >= 20.0:
                money -= 20.0
                bank += 20.0
                interaction_message = "$20 disimpan ke bank."
            else:
                interaction_message = "Uang tunai tidak cukup."
            _advance_game_minutes(15)
        "withdraw":
            if bank >= 20.0:
                bank -= 20.0
                money += 20.0
                interaction_message = "$20 ditarik dari bank."
            else:
                interaction_message = "Saldo bank tidak cukup."
            _advance_game_minutes(15)
        "invest":
            if money >= 50.0:
                money -= 50.0
                investment_value += 50.0
                interaction_message = "$50 masuk ke aset investasi."
            else:
                interaction_message = "Modal investasi belum cukup."
            _advance_game_minutes(15)
        "withdraw_investment":
            if investment_value > 0.0:
                money += investment_value
                investment_value = 0.0
                interaction_message = "Aset investasi dicairkan."
            else:
                interaction_message = "Belum ada aset investasi."
            _advance_game_minutes(15)
        "rest":
            energy = min(100.0, energy + 20.0)
            stress = max(0.0, stress - 5.0)
            mood = min(100.0, mood + 2.0)
            interaction_message = "Istirahat sebentar."
            _advance_game_minutes(60)
        "sleep":
            energy = min(100.0, energy + 65.0 + float(home_level) * 2.0)
            health = min(100.0, health + 3.5)
            hunger = min(100.0, hunger + 12.0)
            stress = max(0.0, stress - 20.0)
            hygiene = max(20.0, hygiene - 3.0)
            interaction_message = "Tidur selesai. Hari bergerak maju."
            _advance_game_minutes(480)
        "save":
            _save_world(true)
            interaction_message = "Game tersimpan."
        "load":
            _load_world()
            interaction_message = "Game dimuat."
    # Save dipertekan oleh autosave/lifecycle; tidak lagi dilakukan setiap tap.

func _business_deal() -> void:
    if not own_business["owned"]:
        interaction_message = "Bangun usaha dulu untuk membuka negosiasi bisnis."
        return
    var score := float(skills["Negotiation"]) + float(business_state["supplier_quality"]) * 0.55 + float(political_state["influence"]) * 0.10 + randf_range(-10.0, 10.0)
    if score >= 70.0:
        business_state["supplier_quality"] = clamp(float(business_state["supplier_quality"]) + 7.0, 0.0, 100.0)
        business_state["competitive_pressure"] = clamp(float(business_state["competitive_pressure"]) - 4.0, 0.0, 100.0)
        skills["Negotiation"] = clamp(float(skills["Negotiation"]) + 1.2, 0.0, 100.0)
        political_state["influence"] = clamp(float(political_state["influence"]) + 1.0, 0.0, 100.0)
        interaction_message = "Deal bisnis berhasil. Supplier lebih stabil dan posisi negosiasimu menguat."
    else:
        business_state["supplier_quality"] = clamp(float(business_state["supplier_quality"]) - 3.0, 0.0, 100.0)
        stress = clamp(stress + 2.0, 0.0, 100.0)
        interaction_message = "Deal belum optimal. Supplier masih meminta syarat lebih ketat."

func _market_research() -> void:
    if money < 10.0:
        interaction_message = "Riset pasar membutuhkan $10."
        return
    money -= 10.0
    novelty = clamp(novelty + 10.0, 0.0, 100.0)
    business_state["competitive_pressure"] = clamp(float(business_state["competitive_pressure"]) - 2.5, 0.0, 100.0)
    market_sentiment = clamp(market_sentiment + randf_range(2.0, 6.0), 0.0, 100.0)
    interaction_message = "Riset pasar selesai. Kamu mendapat gambaran permintaan dan kompetitor terbaru."

func _community_meeting() -> void:
    var quality := float(skills["Communication"]) + float(reputation) * 0.35 + float(political_state["trust"]) * 0.25 + randf_range(-8.0, 8.0)
    if quality >= 55.0:
        reputation = clamp(reputation + 1.5, 0.0, 100.0)
        political_state["trust"] = clamp(float(political_state["trust"]) + 3.0, 0.0, 100.0)
        political_state["influence"] = clamp(float(political_state["influence"]) + 1.8, 0.0, 100.0)
        city_state["public_order"] = clamp(float(city_state["public_order"]) + 0.5, 0.0, 100.0)
        interaction_message = "Forum warga produktif. Kepercayaan dan pengaruhmu meningkat."
    else:
        stress = clamp(stress + 1.0, 0.0, 100.0)
        interaction_message = "Forum belum menghasilkan kesepakatan. Pelajari kepentingan warga lebih dulu."

func _political_strategy() -> void:
    var target_support := (float(factions["business"]) + float(factions["public"]) + float(factions["community"])) / 3.0
    var score := float(skills["PublicSpeaking"]) + float(skills["Negotiation"]) * 0.6 + float(political_state["trust"]) * 0.35 + float(political_state["political_capital"]) * 0.25 - float(political_state["political_heat"]) * 0.18 + randf_range(-10.0, 10.0)
    if score >= target_support:
        political_state["political_capital"] = clamp(float(political_state["political_capital"]) + 5.0, 0.0, 100.0)
        political_state["influence"] = clamp(float(political_state["influence"]) + 2.5, 0.0, 100.0)
        political_state["coalition_strength"] = clamp(float(political_state["coalition_strength"]) + 4.0, 0.0, 100.0)
        political_state["consultation"] = clamp(float(political_state["consultation"]) + 3.0, 0.0, 100.0)
        public_support = clamp(public_support + 1.8, 0.0, 100.0)
        interaction_message = "Strategi politik berhasil. Koalisi dan modal politik menguat."
    else:
        political_state["political_heat"] = clamp(float(political_state["political_heat"]) + 3.0, 0.0, 100.0)
        political_state["coalition_strength"] = clamp(float(political_state["coalition_strength"]) - 2.0, 0.0, 100.0)
        interaction_message = "Strategi politik mendapat perlawanan. Data, komunikasi publik, dan koalisi perlu diperkuat."

func _campaign_activity() -> void:
    if day + 7 < int(political_state["next_election_day"]):
        interaction_message = "Belum masuk jendela kampanye. Gunakan waktu ini untuk membangun reputasi, jaringan, dan kepercayaan."
        return
    if not election_state["active"]:
        interaction_message = "Mulai pemilu dulu agar kampanye memiliki target dukungan."
        return
    var score := public_support * 0.38 + reputation * 0.20 + float(political_state["influence"]) * 0.18 + float(political_state["coalition_strength"]) * 0.24 + float(skills["PublicSpeaking"]) * 0.20 - float(political_state["political_heat"]) * 0.15 + randf_range(-8.0, 8.0)
    election_state["support"] = clamp(float(election_state["support"]) + score * 0.075, 0.0, 100.0)
    political_state["influence"] = clamp(float(political_state["influence"]) + 1.2, 0.0, 100.0)
    political_state["political_capital"] = clamp(float(political_state["political_capital"]) - 1.0, 0.0, 100.0)
    political_state["political_heat"] = clamp(float(political_state["political_heat"]) + 0.8, 0.0, 100.0)
    interaction_message = "Kampanye berjalan. Dukungan diperbarui berdasarkan reputasi, jaringan, koalisi, dan kondisi kota."

func _cycle_business_sector() -> void:
    if not own_business["owned"]:
        interaction_message = "Bangun usaha dulu untuk memilih sektor."
        return
    var sectors := ["Food", "Retail", "Service", "Technology", "Agriculture"]
    var current := sectors.find(String(business_state["sector"]))
    business_state["sector"] = sectors[(current + 1) % sectors.size()]
    business_state["growth_points"] = max(0.0, float(business_state["growth_points"]) - 2.0)
    novelty = clamp(novelty + 9.0, 0.0, 100.0)
    interaction_message = "Sektor usaha: %s. Kondisi pasar akan memengaruhi hasilnya." % String(business_state["sector"])

func _cycle_business_strategy() -> void:
    if not own_business["owned"]:
        interaction_message = "Bangun usaha dulu untuk mengatur strategi."
        return
    var strategies := ["Balanced", "Value", "Premium"]
    var current := strategies.find(String(business_state["price_strategy"]))
    business_state["price_strategy"] = strategies[(current + 1) % strategies.size()]
    business_state["customer_trust"] = clamp(float(business_state["customer_trust"]) + (1.5 if business_state["price_strategy"] == "Value" else -0.5), 0.0, 100.0)
    interaction_message = "Strategi harga: %s." % String(business_state["price_strategy"])

func _claim_opportunity() -> void:
    if String(active_opportunity.get("id", "")).is_empty():
        interaction_message = "Belum ada peluang aktif. Bangun relasi, riset, dan tunggu event dunia."
        return
    if day > int(active_opportunity.get("expires", day)):
        active_opportunity = {"id": "", "title": "", "kind": "", "expires": 0}
        interaction_message = "Peluang sudah kedaluwarsa."
        return
    var kind := String(active_opportunity.get("kind", ""))
    if kind == "business" and not own_business["owned"]:
        interaction_message = "Peluang ini membutuhkan usaha yang sudah berjalan."
        return
    if kind == "business":
        var payout := 65.0 + float(skills["Business"]) * 1.6 + float(business_state["customer_trust"]) * 0.35
        money += payout
        business_state["growth_points"] = float(business_state["growth_points"]) + 8.0
        reputation = clamp(reputation + 2.5, 0.0, 100.0)
        political_state["influence"] = clamp(float(political_state["influence"]) + 1.0, 0.0, 100.0)
        interaction_message = "Peluang %s diambil. +$%.0f dan reputasi bisnis meningkat." % [String(active_opportunity["title"]), payout]
    elif kind == "community":
        var quality := float(skills["Communication"]) + float(reputation) * 0.35 + float(political_state["trust"]) * 0.35 + randf_range(-7.0, 7.0)
        if quality >= 48.0:
            city_state["public_order"] = clamp(float(city_state["public_order"]) + 5.0, 0.0, 100.0)
            political_state["trust"] = clamp(float(political_state["trust"]) + 3.0, 0.0, 100.0)
            reputation = clamp(reputation + 2.0, 0.0, 100.0)
            interaction_message = "Tanggap layanan warga berhasil. Ketertiban dan kepercayaan naik."
        else:
            political_state["political_heat"] = clamp(float(political_state["political_heat"]) + 2.0, 0.0, 100.0)
            interaction_message = "Respons layanan belum meyakinkan. Perlu komunikasi dan data yang lebih kuat."
    elif kind == "policy":
        var response_cost := 120.0 + float(political_state["policy_debt"])
        var quality := float(skills["Leadership"]) * 0.35 + float(skills["PublicSpeaking"]) * 0.25 + float(political_state["consultation"]) * 0.35 - float(political_state["political_heat"]) * 0.15
        if government_budget < response_cost:
            interaction_message = "Program daya beli belum bisa berjalan: anggaran pemerintah tidak cukup."
        elif quality >= 38.0:
            government_budget -= response_cost
            political_state["consultation"] = clamp(float(political_state["consultation"]) + 10.0, 0.0, 100.0)
            political_state["trust"] = clamp(float(political_state["trust"]) + 4.0, 0.0, 100.0)
            factions["public"] = clamp(float(factions["public"]) + 3.0, 0.0, 100.0)
            interaction_message = "Respons daya beli diterapkan. Kepercayaan publik membaik."
        else:
            political_state["political_heat"] = clamp(float(political_state["political_heat"]) + 3.0, 0.0, 100.0)
            interaction_message = "Respons kebijakan ditolak dalam simulasi publik. Koalisi perlu diperkuat."
    else:
        interaction_message = "Peluang tidak dikenal dan tidak dijalankan."
    active_opportunity = {"id": "", "title": "", "kind": "", "expires": 0}
    novelty = clamp(novelty + 15.0, 0.0, 100.0)

func _cycle_job() -> void:
    var next_index := (job_index + 1) % job_catalog.size()
    var candidate: Dictionary = job_catalog[next_index]
    var required_skill := float(candidate["min"])
    var actual_skill := float(skills[candidate["skill"]])
    if actual_skill < required_skill:
        interaction_message = "Belum memenuhi syarat %s: butuh %d %s." % [candidate["title"], int(required_skill), candidate["skill"]]
        return
    job_index = next_index
    current_job = String(candidate["title"])
    reputation = clamp(reputation + 0.3, 0.0, 100.0)
    interaction_message = "Pekerjaan aktif: %s · gaji dasar $%.0f." % [current_job, float(candidate["salary"])]

func _cycle_transport() -> void:
    var modes := ["Walk", "Bike", "Bus"]
    var index := modes.find(transport)
    index = (index + 1) % modes.size()
    transport = modes[index]
    interaction_message = "Transportasi: %s." % transport

func _upgrade_home() -> void:
    var cost := 120.0 + float(home_level - 1) * 90.0
    if home_level >= 5:
        interaction_message = "Rumah sudah mencapai level maksimum untuk versi kota ini."
        return
    if money < cost:
        interaction_message = "Upgrade rumah membutuhkan $%.0f." % cost
        return
    money -= cost
    home_level += 1
    home_cleanliness = 100.0
    energy = min(100.0, energy + 5.0)
    interaction_message = "Rumah naik ke level %d. Biaya $%.0f." % [home_level, cost]
    _emit_android_event("HOME_UPGRADED", "Rumah diperbarui", "Level rumah sekarang %d." % home_level)

func _toggle_policy() -> void:
    var keys := ["small_business", "education_fund", "public_transport", "market_fairness", "community_health"]
    var key: String = keys[policy_cycle % keys.size()]
    var activation_cost: float = float({"small_business": 60.0, "education_fund": 90.0, "public_transport": 110.0, "market_fairness": 80.0, "community_health": 100.0}[key])
    if not bool(policies[key]):
        if float(political_state["political_capital"]) < 4.0:
            interaction_message = "Modal politik belum cukup untuk mengaktifkan kebijakan. Bangun dukungan dulu."
            return
        if government_budget < activation_cost:
            interaction_message = "Anggaran pemerintah belum cukup untuk menjalankan kebijakan ini."
            return
        government_budget -= activation_cost
        political_state["political_capital"] = max(0.0, float(political_state["political_capital"]) - 4.0)
        political_state["political_heat"] = clamp(float(political_state["political_heat"]) + 2.0, 0.0, 100.0)
        political_state["coalition_strength"] = clamp(float(political_state["coalition_strength"]) + 2.5, 0.0, 100.0)
        match key:
            "small_business":
                factions["business"] = clamp(float(factions["business"]) + 3.0, 0.0, 100.0)
                city_state["business_density"] = clamp(float(city_state["business_density"]) + 1.0, 0.0, 100.0)
            "education_fund":
                factions["workers"] = clamp(float(factions["workers"]) + 2.5, 0.0, 100.0)
                factions["youth"] = clamp(float(factions["youth"]) + 3.5, 0.0, 100.0)
            "public_transport":
                factions["public"] = clamp(float(factions["public"]) + 2.5, 0.0, 100.0)
                city_state["transport_quality"] = clamp(float(city_state["transport_quality"]) + 2.0, 0.0, 100.0)
            "market_fairness":
                factions["business"] = clamp(float(factions["business"]) + 1.5, 0.0, 100.0)
                factions["public"] = clamp(float(factions["public"]) + 2.0, 0.0, 100.0)
                business_state["competitive_pressure"] = clamp(float(business_state["competitive_pressure"]) - 2.0, 0.0, 100.0)
            "community_health":
                city_state["services"] = clamp(float(city_state["services"]) + 2.5, 0.0, 100.0)
                factions["community"] = clamp(float(factions["community"]) + 3.0, 0.0, 100.0)
                religious_state["community"] = clamp(float(religious_state["community"]) + 2.0, 0.0, 100.0)
        interaction_message = "Kebijakan %s aktif. Dampaknya akan dirasakan warga dan anggaran." % key
    else:
        policies[key] = false
        political_state["political_capital"] = clamp(float(political_state["political_capital"]) + 1.5, 0.0, 100.0)
        political_state["coalition_strength"] = clamp(float(political_state["coalition_strength"]) - 1.0, 0.0, 100.0)
        political_state["political_heat"] = clamp(float(political_state["political_heat"]) + 1.0, 0.0, 100.0)
        interaction_message = "Kebijakan %s dinonaktifkan. Kelompok yang terdampak akan menyesuaikan diri." % key
    policy_cycle = (policy_cycle + 1) % keys.size()
    reputation = clamp(reputation + 0.5, 0.0, 100.0)

func _run_election() -> void:
    if election_state["won"]:
        interaction_message = "Kamu sedang menjalankan masa jabatan."
        return
    var campaign_window := day + 7 >= int(political_state["next_election_day"])
    if not election_state["active"]:
        if not campaign_window:
            interaction_message = "Jendela pemilu belum dibuka. Bangun koalisi dan reputasi terlebih dahulu."
            return
        election_state["active"] = true
        var coalition := float(political_state["coalition_strength"])
        var faction_average := (float(factions["business"]) + float(factions["workers"]) + float(factions["youth"]) + float(factions["public"]) + float(factions["community"])) / 5.0
        election_state["support"] = clamp(public_support * 0.50 + reputation * 0.18 + coalition * 0.18 + faction_average * 0.10 + float(skills["Leadership"]) * 0.25 + float(skills["PublicSpeaking"]) * 0.20, 0.0, 100.0)
        election_state["opponent"] = clamp(46.0 + float(political_state["political_heat"]) * 0.18 + randf_range(0.0, 20.0) - coalition * 0.12, 42.0, 82.0)
        interaction_message = "Pemilu dimulai. Kampanye, debat, koalisi, dan kondisi kota akan memengaruhi hasil."
        return
    var candidate_score := float(election_state["support"]) + float(political_state["coalition_strength"]) * 0.22 + float(skills["PublicSpeaking"]) * 0.35 + float(skills["Negotiation"]) * 0.25 - float(political_state["political_heat"]) * 0.20 + randf_range(-7.0, 7.0)
    var opponent_score := float(election_state["opponent"]) + randf_range(-6.0, 6.0)
    if candidate_score >= opponent_score:
        election_state["won"] = true
        election_state["active"] = false
        election_state["term_days"] = 14
        political_state["influence"] = clamp(float(political_state["influence"]) + 5.0, 0.0, 100.0)
        political_state["trust"] = clamp(float(political_state["trust"]) + 6.0, 0.0, 100.0)
        reputation = clamp(reputation + 8.0, 0.0, 100.0)
        interaction_message = "Kamu memenangkan pemilu fiktif. Sekarang keputusanmu punya konsekuensi anggaran dan koalisi."
    else:
        election_state["active"] = false
        political_state["coalition_strength"] = clamp(float(political_state["coalition_strength"]) - 5.0, 0.0, 100.0)
        political_state["political_heat"] = clamp(float(political_state["political_heat"]) + 4.0, 0.0, 100.0)
        reputation = max(0.0, reputation - 2.0)
        interaction_message = "Kamu kalah dalam pemilu. Koalisi berubah, tetapi skill debat dan pengalaman tetap berkembang."

func _social_interaction() -> void:
    if npcs.is_empty():
        return
    var closest: Dictionary = npcs[0]
    var closest_distance := INF
    for npc in npcs:
        var distance: float = player.global_position.distance_to(npc["node"].global_position)
        if distance < closest_distance:
            closest_distance = distance
            closest = npc
    if closest_distance > 8.0:
        interaction_message = "Tidak ada NPC cukup dekat untuk diajak bicara."
        return
    var trait_bonus: float = 5.0 if closest["traits"].has("Talkative") or closest["traits"].has("Friendly") else 0.0
    var gain := 2.0 + float(skills["Communication"]) * 0.25 + trait_bonus * 0.2
    closest["relationship"] = clamp(float(closest["relationship"]) + gain, -100.0, 100.0)
    closest["mood"] = clamp(float(closest["mood"]) + 3.0, 0.0, 100.0)
    closest["social_need"] = max(0.0, float(closest.get("social_need", 25.0)) - 18.0)
    closest["last_interaction_day"] = day
    var memory: Array = closest.get("memory", [])
    memory.append("D%d:PLAYER:%+.1f" % [day, gain])
    while memory.size() > 5: memory.pop_front()
    closest["memory"] = memory
    var affinity := String(closest.get("political_affinity", "public"))
    if factions.has(affinity): factions[affinity] = clamp(float(factions[affinity]) + 0.8, 0.0, 100.0)
    skills["Communication"] = clamp(float(skills["Communication"]) + 0.8, 0.0, 100.0)
    mood = clamp(mood + 2.0, 0.0, 100.0)
    interaction_message = "Berbicara dengan %s. Hubungan meningkat." % closest["name"]

func _run_debate() -> void:
    var argument_score := float(skills["PublicSpeaking"]) + float(skills["Communication"]) + float(reputation) * 0.4
    var counter_score := randf_range(25.0, 65.0)
    skills["PublicSpeaking"] = clamp(float(skills["PublicSpeaking"]) + 1.0, 0.0, 100.0)
    if argument_score >= counter_score:
        public_support = clamp(public_support + 3.0, 0.0, 100.0)
        interaction_message = "Debat: argumen diterima. Dukungan publik naik."
    else:
        public_support = clamp(public_support - 1.0, 0.0, 100.0)
        interaction_message = "Debat: ada sanggahan kuat. Kamu mendapat poin untuk dipelajari."

func _start_or_advance_proposal() -> void:
    if not proposal_state["active"]:
        proposal_state["active"] = true
        proposal_state["title"] = "Program perbaikan ruang publik"
        proposal_state["cost"] = 250.0
        proposal_state["benefit"] = 18.0
        proposal_state["support"] = public_support
        proposal_state["evidence"] = float(skills["Study"]) + float(skills["Technology"]) * 0.5
        interaction_message = "Proposal dibuat. Gunakan Debat/Forum untuk memperkuat dukungan."
        return
    var quality := float(proposal_state["evidence"]) + float(skills["PublicSpeaking"]) + float(reputation)
    if quality > 95.0 and government_budget >= proposal_state["cost"]:
        government_budget -= proposal_state["cost"]
        public_support = clamp(public_support + proposal_state["benefit"] * 0.15, 0.0, 100.0)
        proposal_state["active"] = false
        reputation = clamp(reputation + 4.0, 0.0, 100.0)
        interaction_message = "Proposal disahkan dan proyek publik berjalan."
    else:
        proposal_state["support"] = clamp(float(proposal_state["support"]) + float(skills["Communication"]) * 0.1, 0.0, 100.0)
        interaction_message = "Proposal masih dibahas. Kumpulkan bukti dan dukungan lebih banyak."

func _restock_business() -> void:
    if not own_business["owned"]:
        interaction_message = "Belum punya usaha."
        return
    var qty := 6 * int(own_business["level"])
    var cost := float(qty) * 6.0
    if money < cost:
        interaction_message = "Stok butuh $%.0f." % cost
        return
    money -= cost
    own_business["stock"] = int(own_business["stock"]) + qty
    interaction_message = "Stok usaha bertambah %d unit." % qty
    skills["Business"] = clamp(float(skills["Business"]) + 0.4, 0.0, 100.0)

func _upgrade_business() -> void:
    if not own_business["owned"]:
        interaction_message = "Bangun usaha terlebih dahulu."
        return
    var level := int(own_business["level"])
    if level >= 5:
        interaction_message = "Usaha sudah level maksimum."
        return
    var cost := 180.0 * float(level)
    if money < cost:
        interaction_message = "Upgrade usaha membutuhkan $%.0f." % cost
        return
    money -= cost
    own_business["level"] = level + 1
    own_business["maintenance"] = 8.0 + float(level) * 3.0
    own_business["stock"] = int(own_business["stock"]) + 4 * level
    own_business["reputation"] = clamp(float(own_business["reputation"]) + 4.0, 0.0, 100.0)
    reputation = clamp(reputation + 1.5, 0.0, 100.0)
    interaction_message = "Usaha naik ke level %d." % int(own_business["level"])
    _emit_android_event("BUSINESS_UPGRADED", "Usaha diperbarui", "Level usaha sekarang %d." % int(own_business["level"]))

func _manage_business() -> void:
    if not own_business["owned"]:
        if money < 150.0:
            interaction_message = "Modal awal usaha membutuhkan $150."
            return
        money -= 150.0
        own_business["owned"] = true
        own_business["capital"] = 150.0
        own_business["open"] = true
        own_business["employees"] = 0
        own_business["level"] = 1
        own_business["stock"] = 12
        own_business["maintenance"] = 8.0
        reputation = clamp(reputation + 2.0, 0.0, 100.0)
        skills["Business"] = clamp(float(skills["Business"]) + 2.0, 0.0, 100.0)
        interaction_message = "Usaha dibuka: My Food Stall."
        _emit_android_event("BUSINESS_STARTED", "Usaha pertama dibuka", "My Food Stall mulai beroperasi.")
        return
    own_business["open"] = not own_business["open"]
    if own_business["open"]:
        own_business["employees"] = clamp(int(skills["Business"] / 35.0), 0, 3)
        interaction_message = "Usaha dibuka kembali."
    else:
        interaction_message = "Usaha ditutup sementara."

func _update_hud() -> void:
    if status_label == null:
        return
    var hour := int(time_minutes / 60)
    var minute := time_minutes % 60
    var business_status := "OFF"
    if own_business["open"]:
        business_status = "ON"
    var office: String = ("Jabatan %d hari" % int(election_state["term_days"])) if bool(election_state["won"]) else "Warga"
    var month_index := clampi(int(religious_state.get("calendar_month", 1)) - 1, 0, calendar_month_names.size() - 1)
    var month_name := String(calendar_month_names[month_index])
    var calendar_label := "RAMADAN" if bool(religious_state.get("is_ramadan", false)) else month_name
    status_label.text = "ZAHRA LIFE · Hari %d · %02d:%02d · %s · %s\n" % [day, hour, minute, weather, calendar_label] +         "AGAMA  Iman %.0f · Salat %d/5 · Streak %d · Komunitas %.0f · Zakat $%.0f · Puasa %s\n" % [
            religious_state["faith"], int(religious_state["prayers_today"]), int(religious_state["prayer_streak"]),
            religious_state["community"], religious_state["zakat_total"], "ON" if religious_state["fasting_today"] else "OFF"
        ] +         "BISNIS  %s Lv.%d · %s · Trust %.0f · Margin +$%.0f · Pasar %.0f\n" % [
            business_state["sector"], int(own_business["level"]), business_state.get("ethics_mode", "Fair & Halal"),
            business_state["customer_trust"], business_revenue, market_sentiment
        ] +         "POLITIK  %s · Support %.0f · Influence %.0f · Trust %.0f · Heat %.0f · Koalisi %.0f\n" % [
            office, public_support, political_state["influence"], political_state["trust"],
            political_state["political_heat"], political_state["coalition_strength"]
        ] +         "HIDUP  $%.0f + Bank $%.0f · HP %.0f · Energi %.0f · Mood %.0f · Reputasi %.0f · Event %d" % [
            money, bank, health, energy, mood, reputation, recent_events.size()
        ]
    message_label.text = interaction_message
    # System strip: concise state visibility without opening menus.
    var system_text := "3D CITY  %d NPC · %s  |  KARIER %s · USaha %d pegawai · WAKAF $%.0f" % [
        npcs.size(), String(civic_state["district"]), String(political_career["rank"]),
        int(enterprise_state["employees"]), float(religious_calendar_state["waqf_total"])
    ]
    if detail_labels.size() > 0 and is_instance_valid(detail_labels[0]):
        detail_labels[0].text = "AGAMA
%s" % system_text

func _capture_performance() -> void:
    perf_last_fps = Engine.get_frames_per_second()
    perf_min_fps = min(perf_min_fps, perf_last_fps)
    var draw_calls := int(Performance.get_monitor(Performance.RENDER_TOTAL_DRAW_CALLS_IN_FRAME))
    var objects := int(Performance.get_monitor(Performance.OBJECT_COUNT))
    if bridge_plugin != null and bridge_ready:
        var snapshot = JSON.parse_string(String(bridge_plugin.getPerformanceSnapshot()))
        if typeof(snapshot) == TYPE_DICTIONARY:
            perf_last_pss_mb = float(snapshot.get("pssMb", 0.0))
            perf_max_pss_mb = max(perf_max_pss_mb, perf_last_pss_mb)
            perf_last_battery_temp = float(snapshot.get("batteryTempC", -1.0))
            perf_last_battery_percent = int(snapshot.get("batteryPercent", -1))
    if OS.is_debug_build():
        perf_sample_count += 1
        if perf_sample_count == 1 or perf_sample_count > PERF_LOG_LIMIT:
            perf_sample_count = 1
            if FileAccess.file_exists(PERF_LOG_PATH): DirAccess.remove_absolute(PERF_LOG_PATH)
            var header := FileAccess.open(PERF_LOG_PATH, FileAccess.WRITE)
            if header != null:
                header.store_line("day\ttime\tfps\tmin_fps\tpss_mb\tpeak_pss_mb\tdraw_calls\tobjects\tbattery_percent\tbattery_temp_c")
                header.close()
        var log_file := FileAccess.open(PERF_LOG_PATH, FileAccess.READ_WRITE)
        if log_file != null:
            log_file.seek_end()
            log_file.store_line("%d\t%d\t%.1f\t%.1f\t%.1f\t%.1f\t%d\t%d\t%d\t%.1f" % [day, time_minutes, perf_last_fps, perf_min_fps, perf_last_pss_mb, perf_max_pss_mb, draw_calls, objects, perf_last_battery_percent, perf_last_battery_temp])
            log_file.close()
        if perf_label != null:
            var temp_text := "--" if perf_last_battery_temp < 0.0 else "%.1fC" % perf_last_battery_temp
            perf_label.text = "QA %dfps · min %.0f · PSS %.1f/peak %.1fMB · draw %d · obj %d · bat %s %s" % [int(perf_last_fps), perf_min_fps, perf_last_pss_mb, perf_max_pss_mb, draw_calls, objects, str(perf_last_battery_percent) + "%", temp_text]

func _save_world(force: bool = false) -> void:
    if not force and not world_dirty:
        return
    var npc_state: Array = []
    for npc in npcs:
        npc_state.append({
            "name": npc["name"],
            "money": npc["money"],
            "opinion": npc["opinion"],
            "relationship": npc["relationship"],
            "mood": npc["mood"],
            "energy": npc["energy"],
            "hunger": npc["hunger"],
            "occupation": npc["occupation"],
            "traits": npc["traits"],
            "state": npc.get("state", "HOME"),
            "social_need": npc.get("social_need", 25.0),
            "work_performance": npc.get("work_performance", 0.75),
            "trust": npc.get("trust", 50.0),
            "goal_state": npc.get("goal_state", "stability"),
            "political_affinity": npc.get("political_affinity", "public"),
            "memory": npc.get("memory", []),
            "last_interaction_day": npc.get("last_interaction_day", 0),
            "family_id": npc.get("family_id", 0),
            "household_size": npc.get("household_size", 2),
            "income": npc.get("income", 70.0),
            "faith_practice": npc.get("faith_practice", 50.0),
            "business_interest": npc.get("business_interest", 40.0),
            "civic_interest": npc.get("civic_interest", 40.0),
            "goal": [npc["goal"].x, npc["goal"].y, npc["goal"].z],
        })
    var state := {
        "version": SAVE_VERSION,
        "day": day,
        "time_minutes": time_minutes,
        "weather": weather,
        "money": money,
        "bank": bank,
        "energy": energy,
        "health": health,
        "hunger": hunger,
        "hygiene": hygiene,
        "mood": mood,
        "stress": stress,
        "reputation": reputation,
        "skills": skills,
        "inventory": inventory,
        "prices": prices,
        "demand": demand,
        "public_support": public_support,
        "government_budget": government_budget,
        "tax_rate": tax_rate,
        "policies": policies,
        "investment_value": investment_value,
        "market_sentiment": market_sentiment,
        "transport": transport,
        "home_level": home_level,
        "home_cleanliness": home_cleanliness,
        "current_job": current_job,
        "job_index": job_index,
        "election": election_state,
        "own_business": own_business,
        "businesses": businesses,
        "proposal": proposal_state,
        "world_economy": world_economy,
        "factions": factions,
        "political_state": political_state,
        "city_state": city_state,
        "activity_memory": activity_memory,
        "recent_actions": recent_actions,
        "recent_events": recent_events,
        "boredom": boredom,
        "novelty": novelty,
        "event_cooldowns": event_cooldowns,
        "active_opportunity": active_opportunity,
        "business_state": business_state,
        "religious_state": religious_state,
        "prayer_flags": prayer_flags,
        "world_revision": world_revision,
        "political_career": political_career,
        "enterprise_state": enterprise_state,
        "civic_state": civic_state,
        "religious_calendar_state": religious_calendar_state,
        "npcs": npc_state,
        "player": [player.position.x, player.position.y, player.position.z],
    }
    var temp_save := save_path + ".tmp"
    var file := FileAccess.open_compressed(temp_save, FileAccess.WRITE, SAVE_COMPRESSION)
    if file == null:
        interaction_message = "Game gagal disimpan: storage tidak tersedia."
        return
    file.store_var(state, false)
    file.flush()
    file.close()

    var digest := FileAccess.get_sha256(temp_save)
    var integrity_message := "zsav-v1:%d:%s" % [SAVE_VERSION, digest]
    var signature := ""
    if bridge_plugin != null and bridge_ready:
        signature = String(bridge_plugin.signGamePayload(integrity_message))
    if signature.is_empty():
        # Desktop/editor fallback. Android release has a Keystore-backed signature through ZahraBridge.
        signature = "UNSIGNED-DEV"

    var meta := {}
    meta["version"] = SAVE_META_VERSION
    meta["saveVersion"] = SAVE_VERSION
    meta["sha256"] = digest
    meta["signature"] = signature
    meta["createdAt"] = Time.get_unix_time_from_system()

    var meta_tmp := SAVE_META_PATH + ".tmp"
    var meta_file := FileAccess.open(meta_tmp, FileAccess.WRITE)
    if meta_file == null:
        return
    meta_file.store_string(JSON.stringify(meta))
    meta_file.flush()
    meta_file.close()

    var had_old_save := FileAccess.file_exists(SAVE_PATH_COMPRESSED)
    var had_old_meta := FileAccess.file_exists(SAVE_META_PATH)
    if had_old_save:
        if FileAccess.file_exists(SAVE_BACKUP_PATH): DirAccess.remove_absolute(SAVE_BACKUP_PATH)
        if DirAccess.rename_absolute(SAVE_PATH_COMPRESSED, SAVE_BACKUP_PATH) != OK:
            if FileAccess.file_exists(temp_save): DirAccess.remove_absolute(temp_save)
            if FileAccess.file_exists(meta_tmp): DirAccess.remove_absolute(meta_tmp)
            interaction_message = "Game gagal menyimpan: backup sebelumnya tidak dapat diamankan."
            return
    if had_old_meta:
        if FileAccess.file_exists(SAVE_BACKUP_META_PATH): DirAccess.remove_absolute(SAVE_BACKUP_META_PATH)
        if DirAccess.rename_absolute(SAVE_META_PATH, SAVE_BACKUP_META_PATH) != OK:
            if had_old_save and FileAccess.file_exists(SAVE_BACKUP_PATH):
                DirAccess.rename_absolute(SAVE_BACKUP_PATH, SAVE_PATH_COMPRESSED)
            if FileAccess.file_exists(temp_save): DirAccess.remove_absolute(temp_save)
            if FileAccess.file_exists(meta_tmp): DirAccess.remove_absolute(meta_tmp)
            interaction_message = "Game gagal menyimpan: metadata backup tidak dapat diamankan."
            return

    var save_ok := DirAccess.rename_absolute(temp_save, SAVE_PATH_COMPRESSED) == OK
    var meta_ok := DirAccess.rename_absolute(meta_tmp, SAVE_META_PATH) == OK
    if not save_ok or not meta_ok:
        if save_ok and FileAccess.file_exists(SAVE_PATH_COMPRESSED): DirAccess.remove_absolute(SAVE_PATH_COMPRESSED)
        if meta_ok and FileAccess.file_exists(SAVE_META_PATH): DirAccess.remove_absolute(SAVE_META_PATH)
        if had_old_save and FileAccess.file_exists(SAVE_BACKUP_PATH):
            DirAccess.rename_absolute(SAVE_BACKUP_PATH, SAVE_PATH_COMPRESSED)
        if had_old_meta and FileAccess.file_exists(SAVE_BACKUP_META_PATH):
            DirAccess.rename_absolute(SAVE_BACKUP_META_PATH, SAVE_META_PATH)
        interaction_message = "Game gagal menyimpan secara atomik; save sebelumnya dipulihkan."
        return
    last_save_day = day
    world_dirty = false

func _read_compressed_state(path: String):
    if not FileAccess.file_exists(path):
        return null
    var compressed := FileAccess.open_compressed(path, FileAccess.READ, SAVE_COMPRESSION)
    if compressed == null:
        return null
    var value = compressed.get_var(false)
    compressed.close()
    return value

func _read_save_meta(meta_path: String, data_path: String) -> Dictionary:
    if not FileAccess.file_exists(meta_path) or not FileAccess.file_exists(data_path):
        return {"ok": false, "reason": "missing"}
    var meta_file := FileAccess.open(meta_path, FileAccess.READ)
    if meta_file == null:
        return {"ok": false, "reason": "invalid"}
    var root = JSON.parse_string(meta_file.get_as_text())
    meta_file.close()
    if typeof(root) != TYPE_DICTIONARY:
        return {"ok": false, "reason": "invalid"}
    if int(root.get("version", 0)) != SAVE_META_VERSION:
        return {"ok": false, "reason": "version"}
    var declared_save_version := int(root.get("saveVersion", 0))
    if declared_save_version <= 0 or declared_save_version > SAVE_VERSION:
        return {"ok": false, "reason": "save-version"}
    var digest := FileAccess.get_sha256(data_path)
    if String(root.get("sha256", "")) != digest:
        return {"ok": false, "reason": "hash"}
    var signature := String(root.get("signature", ""))
    if signature == "UNSIGNED-DEV":
        return {"ok": OS.is_debug_build(), "reason": "unsigned-dev"}
    if bridge_plugin == null or not bridge_ready:
        return {"ok": false, "reason": "bridge-unavailable"}
    var integrity_message := "zsav-v1:%d:%s" % [declared_save_version, digest]
    var valid := bool(bridge_plugin.verifyGamePayload(integrity_message, signature))
    return {"ok": valid, "reason": "signature"}

func _load_world() -> void:
    var parsed = null
    var verified := false

    if FileAccess.file_exists(save_path):
        var meta_result := _read_save_meta(SAVE_META_PATH, save_path)
        if meta_result["ok"]:
            parsed = _read_compressed_state(save_path)
            verified = true
        else:
            var backup_meta := _read_save_meta(SAVE_BACKUP_META_PATH, SAVE_BACKUP_PATH)
            if backup_meta["ok"]:
                parsed = _read_compressed_state(SAVE_BACKUP_PATH)
                if typeof(parsed) == TYPE_DICTIONARY:
                    interaction_message = "Save utama tidak lolos integritas; backup terakhir dipulihkan."
                    verified = true
                    save_repair_pending = true
            elif meta_result["reason"] == "missing":
                # One-time compatibility path for the older v0.12 compressed format.
                # After it is re-saved, subsequent Android release loads require a signature.
                parsed = _read_compressed_state(save_path)
                if parsed != null:
                    save_repair_pending = true
                    interaction_message = "Save terkompresi lama ditemukan dan akan diperbarui ke format terlindungi."
            else:
                interaction_message = "Save game ditolak karena integritasnya tidak valid."

    if parsed == null and FileAccess.file_exists(SAVE_PATH_LEGACY):
        var legacy := FileAccess.open(SAVE_PATH_LEGACY, FileAccess.READ)
        if legacy != null:
            parsed = JSON.parse_string(legacy.get_as_text())
            legacy.close()
            used_legacy = true
            save_repair_pending = true
            interaction_message = "Save lama ditemukan dan akan dimigrasikan ke format baru."

    if parsed == null:
        return
    if typeof(parsed) != TYPE_DICTIONARY:
        return
    var saved_version := int(parsed.get("version", 1))
    if saved_version > SAVE_VERSION:
        interaction_message = "Save game berasal dari versi lebih baru."
        return
    day = max(1, int(parsed.get("day", day)))
    time_minutes = clampi(int(parsed.get("time_minutes", time_minutes)), 0, 1439)
    weather = String(parsed.get("weather", weather))
    money = float(parsed.get("money", money))
    bank = float(parsed.get("bank", bank))
    energy = clamp(float(parsed.get("energy", energy)), 0.0, 100.0)
    health = clamp(float(parsed.get("health", health)), 0.0, 100.0)
    hunger = clamp(float(parsed.get("hunger", hunger)), 0.0, 100.0)
    hygiene = clamp(float(parsed.get("hygiene", hygiene)), 0.0, 100.0)
    mood = clamp(float(parsed.get("mood", mood)), 0.0, 100.0)
    stress = clamp(float(parsed.get("stress", stress)), 0.0, 100.0)
    reputation = clamp(float(parsed.get("reputation", reputation)), 0.0, 100.0)
    public_support = clamp(float(parsed.get("public_support", public_support)), 0.0, 100.0)
    government_budget = max(0.0, float(parsed.get("government_budget", government_budget)))
    tax_rate = clamp(float(parsed.get("tax_rate", tax_rate)), 0.0, 0.5)
    investment_value = max(0.0, float(parsed.get("investment_value", investment_value)))
    market_sentiment = clamp(float(parsed.get("market_sentiment", market_sentiment)), 0.0, 100.0)
    transport = String(parsed.get("transport", transport))
    if transport not in ["Walk", "Bike", "Bus"]:
        transport = "Walk"
    home_level = clampi(int(parsed.get("home_level", home_level)), 1, 5)
    home_cleanliness = clamp(float(parsed.get("home_cleanliness", home_cleanliness)), 0.0, 100.0)
    current_job = String(parsed.get("current_job", current_job))
    job_index = clampi(int(parsed.get("job_index", job_index)), 0, job_catalog.size() - 1)
    for key in skills.keys():
        var saved_skills = parsed.get("skills", {})
        if typeof(saved_skills) == TYPE_DICTIONARY and saved_skills.has(key):
            skills[key] = clamp(float(saved_skills[key]), 0.0, 100.0)
    for key in inventory.keys():
        var saved_inventory = parsed.get("inventory", {})
        if typeof(saved_inventory) == TYPE_DICTIONARY and saved_inventory.has(key):
            inventory[key] = max(0, int(saved_inventory[key]))
    for key in prices.keys():
        var saved_prices = parsed.get("prices", {})
        if typeof(saved_prices) == TYPE_DICTIONARY and saved_prices.has(key):
            prices[key] = clamp(float(saved_prices[key]), 4.0, 60.0)
    for key in demand.keys():
        var saved_demand = parsed.get("demand", {})
        if typeof(saved_demand) == TYPE_DICTIONARY and saved_demand.has(key):
            demand[key] = clamp(float(saved_demand[key]), 0.5, 1.8)
    for key in policies.keys():
        var saved_policies = parsed.get("policies", {})
        if typeof(saved_policies) == TYPE_DICTIONARY and saved_policies.has(key):
            policies[key] = bool(saved_policies[key])
    var saved_election = parsed.get("election", {})
    if typeof(saved_election) == TYPE_DICTIONARY:
        for key in election_state.keys():
            if saved_election.has(key): election_state[key] = saved_election[key]
    var saved_business = parsed.get("own_business", {})
    if typeof(saved_business) == TYPE_DICTIONARY:
        for key in own_business.keys():
            if saved_business.has(key): own_business[key] = saved_business[key]
    var saved_proposal = parsed.get("proposal", {})
    if typeof(saved_proposal) == TYPE_DICTIONARY:
        for key in proposal_state.keys():
            if saved_proposal.has(key): proposal_state[key] = saved_proposal[key]
    for key in world_economy.keys():
        var saved_world_economy = parsed.get("world_economy", {})
        if typeof(saved_world_economy) == TYPE_DICTIONARY and saved_world_economy.has(key): world_economy[key] = float(saved_world_economy[key])
    for key in factions.keys():
        var saved_factions = parsed.get("factions", {})
        if typeof(saved_factions) == TYPE_DICTIONARY and saved_factions.has(key): factions[key] = clamp(float(saved_factions[key]), 0.0, 100.0)
    for key in political_state.keys():
        var saved_political = parsed.get("political_state", {})
        if typeof(saved_political) == TYPE_DICTIONARY and saved_political.has(key): political_state[key] = saved_political[key]
    for key in city_state.keys():
        var saved_city = parsed.get("city_state", {})
        if typeof(saved_city) == TYPE_DICTIONARY and saved_city.has(key): city_state[key] = clamp(float(saved_city[key]), 0.0, 100.0)
    var saved_business_state = parsed.get("business_state", {})
    if typeof(saved_business_state) == TYPE_DICTIONARY:
        for key in business_state.keys():
            if saved_business_state.has(key): business_state[key] = saved_business_state[key]
    if String(business_state.get("ethics_mode", "Fair & Halal")) not in ["Fair & Halal", "Competitive", "Aggressive"]:
        business_state["ethics_mode"] = "Fair & Halal"
    business_state["halal_integrity"] = clamp(float(business_state.get("halal_integrity", 88.0)), 0.0, 100.0)
    var saved_religious_state = parsed.get("religious_state", {})
    if typeof(saved_religious_state) == TYPE_DICTIONARY:
        for key in religious_state.keys():
            if saved_religious_state.has(key): religious_state[key] = saved_religious_state[key]
    var saved_prayer_flags = parsed.get("prayer_flags", {})
    if typeof(saved_prayer_flags) == TYPE_DICTIONARY:
        for key in prayer_flags.keys():
            if saved_prayer_flags.has(key): prayer_flags[key] = bool(saved_prayer_flags[key])
    if String(business_state.get("ethics_mode", "Fair & Halal")) not in ["Fair & Halal", "Competitive", "Aggressive"]:
        business_state["ethics_mode"] = "Fair & Halal"
    business_state["halal_integrity"] = clamp(float(business_state.get("halal_integrity", 88.0)), 0.0, 100.0)

    var saved_religious_state = parsed.get("religious_state", {})
    if typeof(saved_religious_state) == TYPE_DICTIONARY:
        for key in religious_state.keys():
            if saved_religious_state.has(key): religious_state[key] = saved_religious_state[key]
    var saved_prayer_flags = parsed.get("prayer_flags", {})
    if typeof(saved_prayer_flags) == TYPE_DICTIONARY:
        for key in prayer_flags.keys():
            if saved_prayer_flags.has(key): prayer_flags[key] = bool(saved_prayer_flags[key])
    var saved_activity = parsed.get("activity_memory", {})
    if typeof(saved_activity) == TYPE_DICTIONARY:
        activity_memory = saved_activity
    var saved_actions = parsed.get("recent_actions", [])
    if typeof(saved_actions) == TYPE_ARRAY:
        recent_actions.clear()
        for item in saved_actions:
            recent_actions.append(String(item))
    var saved_events = parsed.get("recent_events", [])
    if typeof(saved_events) == TYPE_ARRAY:
        recent_events.clear()
        for item in saved_events:
            recent_events.append(String(item))
    var saved_cooldowns = parsed.get("event_cooldowns", {})
    if typeof(saved_cooldowns) == TYPE_DICTIONARY:
        event_cooldowns = saved_cooldowns
    var saved_opportunity = parsed.get("active_opportunity", {})
    if typeof(saved_opportunity) == TYPE_DICTIONARY:
        active_opportunity = saved_opportunity
        if day > int(active_opportunity.get("expires", 0)):
            active_opportunity = {"id": "", "title": "", "kind": "", "expires": 0}
    boredom = clamp(float(parsed.get("boredom", boredom)), 0.0, 100.0)
    novelty = clamp(float(parsed.get("novelty", novelty)), 0.0, 100.0)
    world_revision = max(0, int(parsed.get("world_revision", world_revision)))

    var saved_career = parsed.get("political_career", {})
    if typeof(saved_career) == TYPE_DICTIONARY:
        for key in political_career.keys():
            if saved_career.has(key): political_career[key] = saved_career[key]
    var saved_enterprise = parsed.get("enterprise_state", {})
    if typeof(saved_enterprise) == TYPE_DICTIONARY:
        for key in enterprise_state.keys():
            if saved_enterprise.has(key): enterprise_state[key] = saved_enterprise[key]
    var saved_civic = parsed.get("civic_state", {})
    if typeof(saved_civic) == TYPE_DICTIONARY:
        for key in civic_state.keys():
            if saved_civic.has(key): civic_state[key] = saved_civic[key]
    var saved_religious_calendar = parsed.get("religious_calendar_state", {})
    if typeof(saved_religious_calendar) == TYPE_DICTIONARY:
        for key in religious_calendar_state.keys():
            if saved_religious_calendar.has(key): religious_calendar_state[key] = saved_religious_calendar[key]

    var npc_state = parsed.get("npcs", null)
    if typeof(npc_state) == TYPE_ARRAY:
        for i in range(min(npc_state.size(), npcs.size())):
            var saved = npc_state[i]
            if typeof(saved) != TYPE_DICTIONARY:
                continue
            npcs[i]["money"] = float(saved.get("money", npcs[i]["money"]))
            npcs[i]["opinion"] = clamp(float(saved.get("opinion", npcs[i]["opinion"])), 0.0, 100.0)
            npcs[i]["relationship"] = clamp(float(saved.get("relationship", npcs[i]["relationship"])), -100.0, 100.0)
            npcs[i]["mood"] = clamp(float(saved.get("mood", npcs[i]["mood"])), 0.0, 100.0)
            npcs[i]["energy"] = clamp(float(saved.get("energy", npcs[i]["energy"])), 0.0, 100.0)
            npcs[i]["hunger"] = clamp(float(saved.get("hunger", npcs[i]["hunger"])), 0.0, 100.0)
            npcs[i]["state"] = String(saved.get("state", npcs[i].get("state", "HOME")))
            npcs[i]["social_need"] = clamp(float(saved.get("social_need", npcs[i].get("social_need", 25.0))), 0.0, 100.0)
            npcs[i]["work_performance"] = clamp(float(saved.get("work_performance", npcs[i].get("work_performance", 0.75))), 0.2, 1.5)
            npcs[i]["trust"] = clamp(float(saved.get("trust", npcs[i].get("trust", 50.0))), 0.0, 100.0)
            npcs[i]["goal_state"] = String(saved.get("goal_state", npcs[i].get("goal_state", "stability")))
            npcs[i]["political_affinity"] = String(saved.get("political_affinity", npcs[i].get("political_affinity", "public")))
            if typeof(saved.get("memory", [])) == TYPE_ARRAY:
                npcs[i]["memory"] = saved.get("memory", []).slice(max(0, saved.get("memory", []).size() - 5))
            npcs[i]["last_interaction_day"] = int(saved.get("last_interaction_day", 0))
            npcs[i]["family_id"] = int(saved.get("family_id", npcs[i].get("family_id", 0)))
            npcs[i]["household_size"] = max(1, int(saved.get("household_size", npcs[i].get("household_size", 2))))
            npcs[i]["income"] = max(0.0, float(saved.get("income", npcs[i].get("income", 70.0))))
            npcs[i]["faith_practice"] = clamp(float(saved.get("faith_practice", npcs[i].get("faith_practice", 50.0))), 0.0, 100.0)
            npcs[i]["business_interest"] = clamp(float(saved.get("business_interest", npcs[i].get("business_interest", 40.0))), 0.0, 100.0)
            npcs[i]["civic_interest"] = clamp(float(saved.get("civic_interest", npcs[i].get("civic_interest", 40.0))), 0.0, 100.0)

    var saved_player = parsed.get("player", null)
    if typeof(saved_player) == TYPE_ARRAY and saved_player.size() == 3:
        player.position = Vector3(float(saved_player[0]), 0.0, float(saved_player[2]))

    _normalize_dynamic_state()
