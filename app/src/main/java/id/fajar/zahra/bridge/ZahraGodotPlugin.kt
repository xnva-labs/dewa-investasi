package id.fajar.zahra.bridge

import android.app.ActivityManager
import android.content.Intent
import android.os.BatteryManager
import android.os.Debug
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ZahraGodotPlugin(godot: Godot) : GodotPlugin(godot) {
    private val rootDir: File
        get() = File(activity.applicationContext.filesDir, "zahra_bridge_v2")
    private val inboxDir: File
        get() = File(rootDir, "inbox")
    private val outboxDir: File
        get() = File(rootDir, "outbox")

    override fun getPluginName(): String = "ZahraBridge"

    @UsedByGodot
    fun getBridgeProtocolVersion(): Int = BridgeSecurity.PROTOCOL_VERSION

    @UsedByGodot
    fun pingBridge(): String = "ZAHRA_BRIDGE_OK"

    /** Runtime smoke test: creates an HMAC and verifies it before the game trusts the bridge. */
    @UsedByGodot
    fun selfTestBridgeContract(): Boolean = runCatching {
        val payload = "zahra-bridge-self-test-v2"
        val signature = BridgeSecurity.sign(payload)
        BridgeSecurity.verify(payload, signature)
    }.getOrDefault(false)

    /** Returns up to 64 authenticated app->game events without deleting them. */
    @UsedByGodot
    fun consumeAppEvents(): String = synchronized(this) {
        inboxDir.mkdirs()
        val result = JSONArray()
        val files = inboxDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".evt") }
            ?.sortedBy { it.name }
            ?.take(64)
            ?: emptyList()
        for (file in files) {
            if (file.length() <= 0L || file.length() > 128_000L) {
                file.delete()
                continue
            }
            val envelope = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull() ?: run {
                file.delete()
                continue
            }
            val protocol = envelope.optInt("protocol", 0)
            val eventId = envelope.optString("eventId", "")
            val payload = envelope.optString("payload", "")
            val signature = envelope.optString("signature", "")
            if (protocol != BridgeSecurity.PROTOCOL_VERSION || !isValidEventId(eventId) || !BridgeSecurity.verifyEvent(protocol, eventId, payload, signature)) {
                file.delete()
                continue
            }
            result.put(JSONObject().put("eventId", eventId).put("payload", payload))
        }
        result.toString()
    }

    @UsedByGodot
    fun ackAppEvents(eventIdsJson: String): Int = synchronized(this) {
        val ids = runCatching { JSONArray(eventIdsJson) }.getOrNull() ?: return@synchronized 0
        var removed = 0
        for (i in 0 until ids.length()) {
            val id = ids.optString(i, "")
            if (!isValidEventId(id)) continue
            if (File(inboxDir, "$id.evt").delete()) removed++
        }
        removed
    }

    @UsedByGodot
    fun emitGameEvent(type: String, title: String, detail: String): Boolean = synchronized(this) {
        val cleanType = type.trim().take(64)
        if (cleanType.isBlank()) return@synchronized false
        runCatching {
            val eventId = UUID.randomUUID().toString()
            val payload = JSONObject()
                .put("version", 1)
                .put("type", cleanType)
                .put("title", title.take(200))
                .put("detail", detail.take(1000))
                .put("createdAt", System.currentTimeMillis())
                .toString()
            val envelope = JSONObject()
                .put("protocol", BridgeSecurity.PROTOCOL_VERSION)
                .put("eventId", eventId)
                .put("payload", payload)
                .put("signature", BridgeSecurity.signEvent(BridgeSecurity.PROTOCOL_VERSION, eventId, payload))
            atomicWrite(File(outboxDir, "$eventId.evt"), envelope.toString())
            true
        }.getOrDefault(false)
    }

    /** Keystore-backed HMAC used by the Godot save-integrity envelope. */
    @UsedByGodot
    fun signGamePayload(payload: String): String = BridgeSecurity.sign(payload)

    @UsedByGodot
    fun verifyGamePayload(payload: String, signature: String): Boolean = BridgeSecurity.verify(payload, signature)

    /** Low-frequency QA telemetry. It does not expose private user content. */
    @UsedByGodot
    fun getPerformanceSnapshot(): String {
        val context = activity.applicationContext
        val manager = context.getSystemService(ActivityManager::class.java)
        val pssKb = runCatching {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            info.totalPss.toLong()
        }.getOrDefault(0L)
        val battery = runCatching {
            val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            Pair(if (scale > 0) (level * 100 / scale) else -1, if (temp >= 0) temp / 10.0 else -1.0)
        }.getOrDefault(Pair(-1, -1.0))
        val availKb = runCatching { ActivityManager.MemoryInfo().also(manager::getMemoryInfo).availMem / 1024L }.getOrDefault(0L)
        return JSONObject()
            .put("pssMb", pssKb / 1024.0)
            .put("availableMb", availKb / 1024.0)
            .put("batteryPercent", battery.first)
            .put("batteryTempC", battery.second)
            .toString()
    }

    private fun atomicWrite(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        check(tmp.renameTo(target)) { "Gagal menulis bridge event" }
    }

    private fun isValidEventId(value: String): Boolean = runCatching {
        UUID.fromString(value)
        true
    }.getOrDefault(false)
}
