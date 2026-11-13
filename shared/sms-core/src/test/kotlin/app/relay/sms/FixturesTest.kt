package app.relay.sms

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The same fixtures run against packages/sms-core (TypeScript), so both sides count and validate alike. */
class FixturesTest {
    private fun fixture(name: String) =
        Json.parseToJsonElement(File("fixtures/$name").readText()).jsonArray.map { it.jsonObject }

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.contentOrNull

    @Test
    fun `segment counts match the shared fixtures`() {
        for (case in fixture("segments.json")) {
            val info = GsmSegmentCalculator.calculate(case.string("text")!!)
            val name = case.string("name")
            assertEquals(case.string("encoding"), info.encoding.label, name)
            assertEquals(case.getValue("length").jsonPrimitive.int, info.length, name)
            assertEquals(case.getValue("segments").jsonPrimitive.int, info.segments, name)
        }
    }

    @Test
    fun `phone numbers normalise like the shared fixtures`() {
        val validator = LenientPhoneNumberValidator()
        for (case in fixture("phone-numbers.json")) {
            assertEquals(case.string("normalized"), validator.normalize(case.string("raw")!!), case.string("raw"))
        }
    }
}
