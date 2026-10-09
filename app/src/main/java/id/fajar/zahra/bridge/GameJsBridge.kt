package id.fajar.zahra.bridge

import android.content.Context
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Jembatan JavaScript <-> app untuk game three.js. Memakai antrean berkas dan tanda tangan HMAC yang sama dengan [GameBridge].
 * Dipanggil dari thread WebView, jadi semua akses berkas disinkronkan.
 */
class GameJsBridge(private val context: Context) {

    /** Mengembalikan maksimal 64 event app->game yang sah (tanpa menghapusnya). Format: [{"eventId":..,"payload":..}] */
    @JavascriptInterface
    fun consumeEvents(): String = synchronized(this) {
        val result = JSONArray()
        val files = GameBridge.inboxDir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".evt") }
            ?.sortedBy { it.name }
            ?.take(64)
            ?: emptyList()
        for (file in files) {
            if (file.length() <= 0L || file.length() > 128_000L) {
                file.delete()
                continue
            }
            val envelope = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
            if (envelope == null) {
                file.delete()
                continue
            }
            val protocol = envelope.optInt("protocol", 0)
            val eventId = envelope.optString("eventId", "")
            val payload = envelope.optString("payload", "")
            val signature = envelope.optString("signature", "")
            if (protocol != BridgeSecurity.PROTOCOL_VERSION || !isValidEventId(eventId) ||
                !BridgeSecurity.verifyEvent(protocol, eventId, payload, signature)
            ) {
                file.delete()
                continue
            }
            result.put(JSONObject().put("eventId", eventId).put("payload", payload))
        }
        result.toString()
    }

    /** Menghapus event yang sudah diproses game. Argumen: JSON array berisi eventId. */
    @JavascriptInterface
    fun ack(eventIdsJson: String): Int = synchronized(this) {
        val ids = runCatching { JSONArray(eventIdsJson) }.getOrNull()
        var removed = 0
        if (ids != null) {
            for (i in 0 until ids.length()) {
                val id = ids.optString(i, "")
                if (isValidEventId(id) && File(GameBridge.inboxDir(context), "$id.evt").delete()) removed++
            }
        }
        removed
    }

    /** Game -> app: catat kejadian (misalnya amal baik) supaya muncul di History aplikasi. */
    @JavascriptInterface
    fun emit(type: String, title: String, detail: String): Boolean = synchronized(this) {
        val cleanType = type.trim().take(64)
        val dir = GameBridge.outboxDir(context)
        if (cleanType.isBlank() || (dir.listFiles()?.size ?: 0) > 200) {
            false
        } else {
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
                val target = File(dir, "$eventId.evt")
                val tmp = File(dir, "$eventId.evt.tmp")
                tmp.writeText(envelope.toString(), Charsets.UTF_8)
                check(tmp.renameTo(target)) { "Gagal menulis bridge event" }
                true
            }.getOrDefault(false)
        }
    }

    private fun isValidEventId(value: String): Boolean = runCatching {
        UUID.fromString(value)
        true
    }.getOrDefault(false)
}
