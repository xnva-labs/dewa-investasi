package id.fajar.zahra.bridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BridgeSecurityInstrumentedTest {
    @Test fun keystoreHmacRoundTripAndTamperDetection() {
        val payload = "bridge-e2e-${System.currentTimeMillis()}"
        val signature = BridgeSecurity.sign(payload)
        assertTrue(BridgeSecurity.verify(payload, signature))
        assertFalse(BridgeSecurity.verify(payload + "-tampered", signature))

        val eventId = java.util.UUID.randomUUID().toString()
        val eventSignature = BridgeSecurity.signEvent(BridgeSecurity.PROTOCOL_VERSION, eventId, payload)
        assertTrue(BridgeSecurity.verifyEvent(BridgeSecurity.PROTOCOL_VERSION, eventId, payload, eventSignature))
        assertFalse(BridgeSecurity.verifyEvent(BridgeSecurity.PROTOCOL_VERSION, java.util.UUID.randomUUID().toString(), payload, eventSignature))
    }
}
