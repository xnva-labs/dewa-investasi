package id.fajar.zahra.bridge

import android.content.Context
import id.fajar.zahra.data.AppEventEntity
import id.fajar.zahra.data.ZahraDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Durable, idempotent, authenticated Android <-> Godot bridge.
 *
 * Transport is file-based only because both sides live in the same application
 * sandbox and offline-first behavior is a project requirement. Each event gets
 * its own atomically-created file so crashes cannot truncate the whole queue.
 */
object GameBridge {
    const val PROTOCOL_VERSION = BridgeSecurity.PROTOCOL_VERSION
    private const val ROOT = "zahra_bridge_v2"
    private const val OUTBOX = "outbox"
    private const val INBOX = "inbox"
    private const val MAX_EVENT_BYTES = 128_000L
    private const val MAX_EVENTS_PER_PASS = 64

    suspend fun enqueueMissionCompleted(context: Context, missionId: Long, points: Int): Boolean = withContext(Dispatchers.IO) {
        if (missionId <= 0L || points !in 0..1000) return@withContext false
        runCatching {
            val eventId = UUID.randomUUID().toString()
            val payload = JSONObject()
                .put("version", 1)
                .put("type", "MISSION_COMPLETED")
                .put("missionId", missionId)
                .put("points", points)
                .put("createdAt", System.currentTimeMillis())
                .toString()
            enqueueEnvelope(context, INBOX, eventId, payload)
        }.isSuccess
    }

    suspend fun consumeGameEvents(context: Context, db: ZahraDatabase): Int = withContext(Dispatchers.IO) {
        val dir = bridgeDir(context, OUTBOX)
        val files = listEventFiles(dir)
        var consumed = 0
        for (file in files.take(MAX_EVENTS_PER_PASS)) {
            if (file.length() <= 0L || file.length() > MAX_EVENT_BYTES) {
                file.delete()
                continue
            }
            val envelope = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull() ?: run {
                file.delete()
                continue
            }
            val protocol = envelope.optInt("protocol", 0)
            val eventId = envelope.optString("eventId", "")
            val signature = envelope.optString("signature", "")
            val payload = envelope.optString("payload", "")
            if (protocol != PROTOCOL_VERSION || !isValidEventId(eventId) || !BridgeSecurity.verifyEvent(protocol, eventId, payload, signature)) {
                file.delete()
                continue
            }
            val json = runCatching { JSONObject(payload) }.getOrNull() ?: run {
                // Invalid signed payloads cannot ever become valid later. Drop them so
                // one corrupt event cannot permanently block the durable queue.
                file.delete()
                continue
            }
            val version = json.optInt("version", 0)
            val type = json.optString("type", "")
            if (version != 1 || type.isBlank()) {
                file.delete()
                continue
            }
            val inserted = runCatching {
                db.eventDao().insertBridgeEvent(
                    AppEventEntity(
                        type = "GAME:$type".take(64),
                        title = json.optString("title", type).take(200),
                        detail = json.optString("detail", "").take(1000),
                        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                        bridgeEventId = eventId
                    )
                )
            }.getOrElse { -1L }
            if (inserted >= 0L) {
                file.delete()
                consumed++
            }
        }
        consumed
    }


    fun clearQueues(context: Context) {
        listOf(inboxDir(context), outboxDir(context)).forEach { dir ->
            dir.listFiles()?.forEach { if (it.isFile) it.delete() }
        }
    }

    fun inboxDir(context: Context): File = bridgeDir(context, INBOX)
    fun outboxDir(context: Context): File = bridgeDir(context, OUTBOX)

    private fun enqueueEnvelope(context: Context, channel: String, eventId: String, payload: String) {
        require(payload.length <= MAX_EVENT_BYTES)
        val dir = bridgeDir(context, channel)
        val envelope = JSONObject()
            .put("protocol", PROTOCOL_VERSION)
            .put("eventId", eventId)
            .put("payload", payload)
            .put("signature", BridgeSecurity.signEvent(PROTOCOL_VERSION, eventId, payload))
        atomicWrite(dir.resolve("$eventId.evt"), envelope.toString())
    }

    private fun bridgeDir(context: Context, channel: String): File =
        context.filesDir.resolve(ROOT).resolve(channel).also { it.mkdirs() }

    private fun listEventFiles(dir: File): List<File> =
        dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".evt") }
            ?.sortedBy { it.name }
            ?: emptyList()

    private fun atomicWrite(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.flush()
            stream.fd.sync()
        }
        check(tmp.renameTo(target)) { "Gagal mengantre bridge event" }
    }

    private fun isValidEventId(value: String): Boolean = runCatching {
        UUID.fromString(value)
        true
    }.getOrDefault(false)
}
