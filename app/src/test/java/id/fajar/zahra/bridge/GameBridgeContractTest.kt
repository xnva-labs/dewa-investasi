package id.fajar.zahra.bridge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GameBridgeContractTest {
    @Test fun missionCompletionPayloadHasStableContract() {
        val json=JSONObject().put("version",1).put("type","MISSION_COMPLETED").put("missionId",42L).put("points",20)
        assertEquals(1,json.getInt("version"));assertEquals("MISSION_COMPLETED",json.getString("type"));assertEquals(42L,json.getLong("missionId"));assertEquals(20,json.getInt("points"))
    }
    @Test fun gameEventPayloadHasStableContract() {
        val json=JSONObject().put("version",1).put("type","BUSINESS_STARTED").put("title","Usaha pertama").put("detail","My Food Stall")
        assertEquals("BUSINESS_STARTED",json.getString("type"));assertEquals("Usaha pertama",json.getString("title"))
    }
}
