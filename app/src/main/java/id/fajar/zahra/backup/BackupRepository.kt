package id.fajar.zahra.backup

import androidx.room.withTransaction
import id.fajar.zahra.data.AppEventEntity
import id.fajar.zahra.data.ListEntity
import id.fajar.zahra.data.ListItemEntity
import id.fajar.zahra.data.MissionEntity
import id.fajar.zahra.data.PointLedgerEntity
import id.fajar.zahra.data.ProfileEntity
import id.fajar.zahra.data.ProofEntity
import id.fajar.zahra.data.RewardEntity
import id.fajar.zahra.data.ZahraDatabase
import id.fajar.zahra.data.YearlyProgressEntity
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class BackupRepository(private val db: ZahraDatabase) {
    suspend fun exportPlaintext(): ByteArray {
        val root = JSONObject()
            .put("schema", BackupContract.CURRENT_SCHEMA)
            .put("format", BackupContract.FILE_EXTENSION)
            .put("exportedAt", System.currentTimeMillis())

        db.profileDao().observe().first()?.let { p ->
            root.put("profile", JSONObject()
                .put("id", p.id)
                .put("name", p.name)
                .put("age", p.age)
                .put("createdAt", p.createdAt)
                .put("updatedAt", p.updatedAt))
        }

        root.put("missions", JSONArray().apply {
            db.missionDao().observeAll().first().forEach { m ->
                put(JSONObject()
                    .put("id", m.id).put("title", m.title).put("description", m.description)
                    .put("category", m.category).put("difficulty", m.difficulty).put("points", m.points)
                    .put("scheduleType", m.scheduleType)
                    .put("scheduledAt", m.scheduledAt ?: JSONObject.NULL)
                    .put("repeatRule", m.repeatRule ?: JSONObject.NULL)
                    .put("proofType", m.proofType).put("proofTarget", m.proofTarget)
                    .put("status", m.status).put("createdAt", m.createdAt).put("updatedAt", m.updatedAt)
                    .put("completedAt", m.completedAt ?: JSONObject.NULL)
                    .put("completionCount", m.completionCount).put("streak", m.streak))
            }
        })

        root.put("rewards", JSONArray().apply {
            db.rewardDao().observeAll().first().forEach { r ->
                put(JSONObject()
                    .put("id", r.id).put("title", r.title).put("description", r.description)
                    .put("threshold", r.threshold).put("unlocked", r.unlocked).put("claimed", r.claimed)
                    .put("unlockedAt", r.unlockedAt ?: JSONObject.NULL)
                    .put("claimedAt", r.claimedAt ?: JSONObject.NULL))
            }
        })

        root.put("points", JSONArray().apply {
            db.pointDao().getAllForBackup().forEach { p ->
                put(JSONObject()
                    .put("id", p.id).put("sourceType", p.sourceType)
                    .put("sourceId", p.sourceId ?: JSONObject.NULL).put("sourceKey", p.sourceKey)
                    .put("label", p.label).put("points", p.points).put("createdAt", p.createdAt))
            }
        })

        root.put("proofs", JSONArray().apply {
            db.proofDao().getAllForBackup().forEach { p ->
                // Images are never embedded in the portable backup.
                put(JSONObject()
                    .put("id", p.id).put("missionId", p.missionId).put("proofType", p.proofType)
                    .put("status", p.status).put("message", p.message).put("confidence", p.confidence)
                    .put("photoRetained", false).put("createdAt", p.createdAt))
            }
        })

        root.put("lists", JSONArray().apply {
            db.listDao().getAllForBackup().forEach { l ->
                put(JSONObject()
                    .put("id", l.id).put("title", l.title).put("description", l.description)
                    .put("status", l.status).put("createdAt", l.createdAt).put("updatedAt", l.updatedAt))
            }
        })

        root.put("listItems", JSONArray().apply {
            db.listItemDao().getAllForBackup().forEach { item ->
                put(JSONObject()
                    .put("id", item.id).put("listId", item.listId).put("title", item.title)
                    .put("checked", item.checked).put("createdAt", item.createdAt)
                    .put("checkedAt", item.checkedAt ?: JSONObject.NULL))
            }
        })

        root.put("yearlyProgress", JSONArray().apply {
            db.yearProgressDao().observeAll().first().forEach { p ->
                put(JSONObject()
                    .put("year", p.year).put("level", p.level).put("experience", p.experience)
                    .put("water", p.water).put("catFood", p.catFood).put("plantStage", p.plantStage)
                    .put("leafDrops", p.leafDrops).put("lastLeafMessageIndex", p.lastLeafMessageIndex)
                    .put("createdAt", p.createdAt).put("updatedAt", p.updatedAt))
            }
        })

        root.put("events", JSONArray().apply {
            db.eventDao().getAllForBackup().forEach { e ->
                put(JSONObject()
                    .put("id", e.id).put("type", e.type).put("title", e.title)
                    .put("detail", e.detail).put("createdAt", e.createdAt)
                    .put("bridgeEventId", e.bridgeEventId ?: JSONObject.NULL))
            }
        })

        return root.toString().toByteArray(Charsets.UTF_8)
    }

    suspend fun importPlaintext(bytes: ByteArray) {
        require(bytes.isNotEmpty()) { "Backup kosong" }
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        val schema = root.optInt("schema", 0)
        require(schema in 1..BackupContract.CURRENT_SCHEMA) { "Versi backup tidak didukung" }
        validateBackup(root)

        val profile = root.optJSONObject("profile")?.let { p ->
            ProfileEntity(
                id = 1,
                name = p.optString("name").trim().take(80),
                age = p.optInt("age", 1).coerceIn(1, 120),
                createdAt = p.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = p.optLong("updatedAt", System.currentTimeMillis())
            )
        }

        val missions = parseMissions(root.optJSONArray("missions") ?: JSONArray())
        val rewards = parseRewards(root.optJSONArray("rewards") ?: JSONArray())
        val points = parsePoints(root.optJSONArray("points") ?: JSONArray())
        val proofs = parseProofs(root.optJSONArray("proofs") ?: JSONArray())
        val lists = parseLists(root.optJSONArray("lists") ?: JSONArray())
        val listItems = parseListItems(root.optJSONArray("listItems") ?: JSONArray())
        val events = parseEvents(root.optJSONArray("events") ?: JSONArray())
        val yearlyProgress = parseYearlyProgress(root.optJSONArray("yearlyProgress") ?: JSONArray())

        db.withTransaction {
            db.profileDao().deleteAll()
            db.missionDao().deleteAll()
            db.rewardDao().deleteAll()
            db.pointDao().deleteAll()
            db.proofDao().deleteAll()
            db.listItemDao().deleteAll()
            db.listDao().deleteAll()
            db.eventDao().deleteAll()
            db.yearProgressDao().deleteAll()

            profile?.let { db.profileDao().save(it) }
            missions.forEach { db.missionDao().insertWithId(it) }
            if (rewards.isNotEmpty()) db.rewardDao().insertAll(rewards)
            if (points.isNotEmpty()) db.pointDao().addAll(points)
            if (proofs.isNotEmpty()) db.proofDao().insertAll(proofs)
            lists.forEach { db.listDao().insertWithId(it) }
            listItems.forEach { db.listItemDao().insertWithId(it) }
            if (events.isNotEmpty()) db.eventDao().insertAll(events)
            yearlyProgress.forEach { db.yearProgressDao().save(it) }
            db.eventDao().insert(AppEventEntity(type = "BACKUP_IMPORTED", title = "Backup dipulihkan", detail = "Data berhasil divalidasi dan dimuat"))
        }
    }

    private fun validateBackup(root: JSONObject) {
        root.optJSONObject("profile")?.let {
            require(it.optString("name").trim().isNotBlank()) { "Profil backup tidak valid" }
            require(it.optInt("age", 0) in 1..120) { "Usia profil backup tidak valid" }
        }

        val missions = root.optJSONArray("missions") ?: JSONArray()
        val missionIds = HashSet<Long>(missions.length())
        for (i in 0 until missions.length()) {
            val m = missions.getJSONObject(i)
            require(missionIds.add(m.getLong("id"))) { "ID misi duplikat" }
            require(m.optString("title").trim().isNotBlank()) { "Misi backup memiliki judul kosong" }
            require(m.optString("proofType", "NONE") in setOf("NONE", "PHOTO", "POSE", "OBJECT")) { "Jenis proof tidak valid" }
            require(m.optString("status", "ACTIVE") in setOf("ACTIVE", "COMPLETED", "PAUSED", "ARCHIVED")) { "Status misi tidak valid" }
        }

        val listIds = HashSet<Long>()
        val lists = root.optJSONArray("lists") ?: JSONArray()
        for (i in 0 until lists.length()) {
            val l = lists.getJSONObject(i)
            require(listIds.add(l.getLong("id"))) { "ID daftar duplikat" }
            require(l.optString("title").trim().isNotBlank()) { "Daftar backup memiliki judul kosong" }
            require(l.optString("status", "ACTIVE") in setOf("ACTIVE", "ARCHIVED")) { "Status daftar tidak valid" }
        }

        val listItemIds = HashSet<Long>()
        val listItems = root.optJSONArray("listItems") ?: JSONArray()
        for (i in 0 until listItems.length()) {
            val item = listItems.getJSONObject(i)
            require(listItemIds.add(item.getLong("id"))) { "ID item daftar duplikat" }
            require(listIds.contains(item.getLong("listId"))) { "Item daftar mengarah ke daftar yang tidak ada" }
            require(item.optString("title").trim().isNotBlank()) { "Item daftar kosong" }
        }

        val proofIds = HashSet<Long>()
        val proofs = root.optJSONArray("proofs") ?: JSONArray()
        for (i in 0 until proofs.length()) {
            val proof = proofs.getJSONObject(i)
            require(proofIds.add(proof.getLong("id"))) { "ID proof duplikat" }
            require(missionIds.contains(proof.getLong("missionId"))) { "Proof mengarah ke misi yang tidak ada" }
            require(proof.optString("proofType", "NONE") in setOf("PHOTO", "POSE", "OBJECT")) { "Jenis proof tidak valid" }
            require(proof.optString("status", "CONFIRMED") in setOf("CONFIRMED", "MANUAL_CONFIRMED")) { "Status proof tidak valid" }
        }

        val pointIds = HashSet<Long>()
        val points = root.optJSONArray("points") ?: JSONArray()
        val missionPointCounts = HashMap<Long, Int>()
        val sourceKeys = HashSet<String>()
        val missionCompletionCounts = HashMap<Long, Int>()
        for (i in 0 until missions.length()) {
            val m = missions.getJSONObject(i)
            missionCompletionCounts[m.getLong("id")] = m.optInt("completionCount", 0).coerceAtLeast(0)
        }
        for (i in 0 until points.length()) {
            val point = points.getJSONObject(i)
            require(pointIds.add(point.getLong("id"))) { "ID ledger duplikat" }
            val sourceKey = point.optString("sourceKey", "LEGACY:${point.getLong("id")}").trim()
            require(sourceKey.isNotBlank() && sourceKeys.add(sourceKey)) { "Source key ledger duplikat" }
            val pointsValue = point.optInt("points", 0)
            require(pointsValue in 0..1000) { "Nilai poin backup tidak valid" }
            if (point.optString("sourceType", "") == "MISSION") {
                val missionId = point.optLong("sourceId", -1L)
                require(missionCompletionCounts.containsKey(missionId)) { "Ledger menunjuk misi yang tidak ada" }
                val count = (missionPointCounts[missionId] ?: 0) + 1
                missionPointCounts[missionId] = count
                require(count <= (missionCompletionCounts[missionId] ?: 0)) { "Ledger misi melebihi jumlah completion" }
            }
        }

        val rewardIds = HashSet<Long>()
        val rewards = root.optJSONArray("rewards") ?: JSONArray()
        var totalPoints = 0
        for (i in 0 until points.length()) totalPoints += points.getJSONObject(i).optInt("points", 0)
        for (i in 0 until rewards.length()) {
            val reward = rewards.getJSONObject(i)
            require(rewardIds.add(reward.getLong("id"))) { "ID reward duplikat" }
            require(reward.optInt("threshold", -1) >= 0) { "Threshold reward tidak valid" }
            if (reward.optBoolean("claimed")) require(reward.optBoolean("unlocked")) { "Reward claimed tetapi belum unlocked" }
            if (reward.optBoolean("unlocked")) require(reward.getInt("threshold") <= totalPoints) { "Reward unlocked melebihi total poin" }
        }

        val annualYears = HashSet<Int>()
        val annualRecords = root.optJSONArray("yearlyProgress") ?: JSONArray()
        for (i in 0 until annualRecords.length()) {
            val annual = annualRecords.getJSONObject(i)
            val year = annual.optInt("year", 0)
            require(year in 1900..9999 && annualYears.add(year)) { "Tahun progres duplikat atau tidak valid" }
            require(annual.optInt("level", 1) in 1..999) { "Level tahunan tidak valid" }
            require(annual.optInt("experience", 0) >= 0) { "EXP tahunan tidak valid" }
            require(annual.optInt("water", 0) >= 0 && annual.optInt("catFood", 0) >= 0) { "Reward tahunan tidak valid" }
            require(annual.optInt("plantStage", 0) in 0..6) { "Tahap tanaman tidak valid" }
            require(annual.optInt("leafDrops", 0) >= 0) { "Jumlah daun tidak valid" }
            require(annual.optInt("lastLeafMessageIndex", -1) in -1..7) { "Pesan daun tidak valid" }
        }

        val eventIds = HashSet<Long>()
        val events = root.optJSONArray("events") ?: JSONArray()
        val bridgeIds = HashSet<String>()
        for (i in 0 until events.length()) {
            val e = events.getJSONObject(i)
            require(eventIds.add(e.getLong("id"))) { "ID event duplikat" }
            if (!e.isNull("bridgeEventId")) {
                val bridgeId = e.optString("bridgeEventId", "").trim()
                require(bridgeId.isNotBlank() && bridgeIds.add(bridgeId)) { "Bridge event ID duplikat" }
            }
        }
    }

    private fun parseMissions(a: JSONArray): List<MissionEntity> = buildList {
        for (i in 0 until a.length()) {
            val m = a.getJSONObject(i)
            add(MissionEntity(
                id = m.getLong("id"), title = m.getString("title").trim().take(120),
                description = m.optString("description").take(500),
                category = m.optString("category", "General").take(50).ifBlank { "General" },
                difficulty = m.optInt("difficulty", 1).coerceIn(1, 10), points = m.optInt("points", 10).coerceIn(0, 1000),
                scheduleType = m.optString("scheduleType", "NONE"), scheduledAt = m.optLongOrNull("scheduledAt"),
                repeatRule = m.optStringOrNull("repeatRule"), proofType = m.optString("proofType", "NONE"),
                proofTarget = m.optString("proofTarget", "").take(100), status = m.optString("status", "ACTIVE"),
                createdAt = m.optLong("createdAt", System.currentTimeMillis()), updatedAt = m.optLong("updatedAt", System.currentTimeMillis()),
                completedAt = m.optLongOrNull("completedAt"), completionCount = m.optInt("completionCount", 0).coerceAtLeast(0), streak = m.optInt("streak", 0).coerceAtLeast(0)
            ))
        }
    }

    private fun parseRewards(a: JSONArray): List<RewardEntity> = buildList {
        for (i in 0 until a.length()) {
            val r = a.getJSONObject(i)
            add(RewardEntity(
                id = r.getLong("id"), title = r.getString("title").take(120), description = r.optString("description").take(500),
                threshold = r.getInt("threshold").coerceAtLeast(0), unlocked = r.optBoolean("unlocked"), claimed = r.optBoolean("claimed"),
                unlockedAt = r.optLongOrNull("unlockedAt"), claimedAt = r.optLongOrNull("claimedAt")
            ))
        }
    }

    private fun parsePoints(a: JSONArray): List<PointLedgerEntity> = buildList {
        for (i in 0 until a.length()) {
            val e = a.getJSONObject(i)
            val id = e.getLong("id")
            add(PointLedgerEntity(id=id, sourceType=e.getString("sourceType").take(64), sourceId=e.optLongOrNull("sourceId"), sourceKey=e.optString("sourceKey", "LEGACY:$id").take(240), label=e.getString("label").take(200), points=e.getInt("points").coerceIn(0,1000), createdAt=e.getLong("createdAt")))
        }
    }

    private fun parseProofs(a: JSONArray): List<ProofEntity> = buildList {
        for (i in 0 until a.length()) {
            val e = a.getJSONObject(i)
            add(ProofEntity(id=e.getLong("id"), missionId=e.getLong("missionId"), proofType=e.getString("proofType").take(16), status=e.getString("status").take(32), message=e.optString("message").take(500), confidence=e.optDouble("confidence",0.0).toFloat().coerceIn(0f,1f), photoRetained=false, createdAt=e.optLong("createdAt",System.currentTimeMillis())))
        }
    }

    private fun parseLists(a: JSONArray): List<ListEntity> = buildList {
        for (i in 0 until a.length()) {
            val e=a.getJSONObject(i)
            add(ListEntity(id=e.getLong("id"), title=e.getString("title").trim().take(120), description=e.optString("description").take(500), status=e.optString("status","ACTIVE"), createdAt=e.optLong("createdAt",System.currentTimeMillis()), updatedAt=e.optLong("updatedAt",System.currentTimeMillis())))
        }
    }

    private fun parseListItems(a: JSONArray): List<ListItemEntity> = buildList {
        for (i in 0 until a.length()) {
            val e=a.getJSONObject(i)
            add(ListItemEntity(id=e.getLong("id"), listId=e.getLong("listId"), title=e.getString("title").trim().take(160), checked=e.optBoolean("checked"), createdAt=e.optLong("createdAt",System.currentTimeMillis()), checkedAt=e.optLongOrNull("checkedAt")))
        }
    }

    private fun parseYearlyProgress(a: JSONArray): List<YearlyProgressEntity> = buildList {
        for (i in 0 until a.length()) {
            val e = a.getJSONObject(i)
            add(YearlyProgressEntity(
                year = e.getInt("year"),
                level = e.optInt("level", 1).coerceIn(1, 999),
                experience = e.optInt("experience", 0).coerceAtLeast(0),
                water = e.optInt("water", 0).coerceAtLeast(0),
                catFood = e.optInt("catFood", 0).coerceAtLeast(0),
                plantStage = e.optInt("plantStage", 0).coerceIn(0, 6),
                leafDrops = e.optInt("leafDrops", 0).coerceAtLeast(0),
                lastLeafMessageIndex = e.optInt("lastLeafMessageIndex", -1).coerceIn(-1, 7),
                createdAt = e.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = e.optLong("updatedAt", System.currentTimeMillis())
            ))
        }
    }

    private fun parseEvents(a: JSONArray): List<AppEventEntity> = buildList {
        for (i in 0 until a.length()) {
            val e=a.getJSONObject(i)
            add(AppEventEntity(id=e.getLong("id"), type=e.getString("type").take(64), title=e.getString("title").take(200), detail=e.getString("detail").take(1000), createdAt=e.getLong("createdAt"), bridgeEventId=e.optStringOrNull("bridgeEventId")))
        }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
}
