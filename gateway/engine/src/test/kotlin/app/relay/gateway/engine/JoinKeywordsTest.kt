package app.relay.gateway.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/** The same cases as packages/contracts/test/keywords.test.ts: the phone and the server must agree. */
class JoinKeywordsTest {
    @Test
    fun `normalises like the server`() {
        val cases = listOf(
            "a2" to "A2",
            " A 2! " to "A2",
            "ok." to "OK",
            "Join news" to "JOINNEWS",
            "ｏｋ" to "OK",
            "እሺ" to "እሺ",
            " እሺ! " to "እሺ",
            "نعم" to "نعم",
            "Oui ✅" to "OUI",
            "፩" to "፩",
            "..." to "",
        )
        for ((raw, norm) in cases) assertEquals(norm, JoinKeywords.normalize(raw), "\"$raw\"")
    }
}
