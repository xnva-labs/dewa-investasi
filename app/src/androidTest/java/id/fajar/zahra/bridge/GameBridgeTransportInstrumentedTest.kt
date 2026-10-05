package id.fajar.zahra.bridge

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.fajar.zahra.data.ZahraDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameBridgeTransportInstrumentedTest {
    @Test fun appEventUsesInboxAndIsAuthenticated() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        GameBridge.clearQueues(context)
        try {
            assertTrue(GameBridge.enqueueMissionCompleted(context, 17L, 25))
            val files = GameBridge.inboxDir(context).listFiles()?.filter { it.name.endsWith(".evt") }.orEmpty()
            assertEquals(1, files.size)
            assertTrue(GameBridge.outboxDir(context).listFiles()?.none { it.name.endsWith(".evt") } != false)

            val envelope = JSONObject(files.single().readText(Charsets.UTF_8))
            val protocol = envelope.getInt("protocol")
            val eventId = envelope.getString("eventId")
            val payload = envelope.getString("payload")
            val signature = envelope.getString("signature")
            assertEquals(GameBridge.PROTOCOL_VERSION, protocol)
            assertTrue(BridgeSecurity.verifyEvent(protocol, eventId, payload, signature))
            assertFalse(BridgeSecurity.verifyEvent(protocol, java.util.UUID.randomUUID().toString(), payload, signature))
        } finally {
            GameBridge.clearQueues(context)
        }
    }

    @Test fun gameOutboxConsumptionIsIdempotentAtRoomBoundary() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        GameBridge.clearQueues(context)
        val db = ZahraDatabase.get(context)
        try {
            val json = JSONObject()
                .put("version", 1)
                .put("type", "TEST_EVENT")
                .put("createdAt", System.currentTimeMillis())
                .toString()
            val eventId = java.util.UUID.randomUUID().toString()
            val envelope = JSONObject()
                .put("protocol", GameBridge.PROTOCOL_VERSION)
                .put("eventId", eventId)
                .put("payload", json)
                .put("signature", BridgeSecurity.signEvent(GameBridge.PROTOCOL_VERSION, eventId, json))
            val out = GameBridge.outboxDir(context).resolve("$eventId.evt")
            out.parentFile?.mkdirs()
            out.writeText(envelope.toString(), Charsets.UTF_8)

            assertEquals(1, GameBridge.consumeGameEvents(context, db))
            assertEquals(0, GameBridge.consumeGameEvents(context, db))
            assertEquals(1, db.eventDao().getAllForBackup().count { it.bridgeEventId == eventId })
        } finally {
            GameBridge.clearQueues(context)
        }
    }
}
