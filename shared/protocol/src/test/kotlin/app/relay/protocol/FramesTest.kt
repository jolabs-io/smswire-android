package app.relay.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FramesTest {
    @Test
    fun `server frames round trip with type discriminator`() {
        val frame: ServerFrame = ServerFrame.Dispatch(
            listOf(SmsJob(messageId = "msg_1", projectId = "p1", to = "+447700900123", body = "hi", receivedAt = 1)),
        )
        val text = RelayJson.encodeToString(ServerFrame.serializer(), frame)
        assertTrue(text.contains("\"type\":\"dispatch\""))
        assertEquals(frame, RelayJson.decodeFromString(ServerFrame.serializer(), text))
    }

    @Test
    fun `status uses lowercase wire names`() {
        val text = RelayJson.encodeToString(
            DeviceFrame.serializer(),
            DeviceFrame.StatusReport("msg_1", MessageStatus.DELIVERED, at = 5),
        )
        assertTrue(text.contains("\"status\":\"delivered\""))
    }
}
