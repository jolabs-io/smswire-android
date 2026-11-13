package app.relay.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden JSON shared with packages/contracts (TypeScript). Every example must decode and
 * re-encode to the same JSON here, and parse to the same JSON with the zod schemas there,
 * so the phone and the API can't drift apart.
 */
class FixturesTest {
    private val serializers: Map<String, KSerializer<*>> = mapOf(
        "ServerFrame" to ServerFrame.serializer(),
        "DeviceFrame" to DeviceFrame.serializer(),
        "PairDeviceRequest" to PairDeviceRequest.serializer(),
        "PairDeviceResponse" to PairDeviceResponse.serializer(),
        "ProjectDto" to ProjectDto.serializer(),
        "BatchDto" to BatchDto.serializer(),
        "SendMessageRequest" to SendMessageRequest.serializer(),
        "BulkSendRequest" to BulkSendRequest.serializer(),
        "MessageView" to MessageView.serializer(),
        "MessagePage" to MessagePage.serializer(),
        "BulkSendResponse" to BulkSendResponse.serializer(),
        "ErrorResponse" to ErrorResponse.serializer(),
        "StatusWebhookPayload" to StatusWebhookPayload.serializer(),
        "OtpRequest" to OtpRequest.serializer(),
        "OtpResponse" to OtpResponse.serializer(),
        "OtpVerifyRequest" to OtpVerifyRequest.serializer(),
        "OtpVerifyResponse" to OtpVerifyResponse.serializer(),
        "ScheduleRequest" to ScheduleRequest.serializer(),
        "ScheduleUpdate" to ScheduleUpdate.serializer(),
        "ScheduleView" to ScheduleView.serializer(),
        "SchedulePage" to SchedulePage.serializer(),
    )

    @Test
    fun `every fixture round-trips through its serializer`() {
        val files = File("fixtures").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        check(files.isNotEmpty()) { "no fixtures found" }
        for (file in files) {
            for ((type, examples) in RelayJson.parseToJsonElement(file.readText()).jsonObject) {
                @Suppress("UNCHECKED_CAST")
                val serializer = serializers[type] as KSerializer<Any?>? ?: error("${file.name}: no serializer for $type")
                for (example in examples.jsonArray) {
                    val decoded = RelayJson.decodeFromJsonElement(serializer, example)
                    assertEquals(example, RelayJson.encodeToJsonElement(serializer, decoded), "${file.name} $type")
                }
            }
        }
    }
}
