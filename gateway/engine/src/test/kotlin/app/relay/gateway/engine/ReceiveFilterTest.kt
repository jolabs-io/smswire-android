package app.relay.gateway.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/** The same cases as packages/contracts/test/receive.test.ts: the phone and the server must agree. */
class ReceiveFilterTest {
    @Test
    fun `keys senders like the server`() {
        val cases = listOf(
            "+251911234567" to "t:911234567",
            "0911 234 567" to "t:911234567",
            "+251 (911) 234-567" to "t:911234567",
            "911234" to "c:911234",
            "127" to "c:127",
            " 8994 " to "c:8994",
            "CBE" to "n:CBE",
            "cbe" to "n:CBE",
            "Tele birr" to "n:TELEBIRR",
            "M-PESA" to "n:MPESA",
            "ｃｂｅ" to "n:CBE",
            "ኢትዮ" to "n:ኢትዮ",
            "+" to null,
            "" to null,
            "..." to null,
        )
        for ((raw, key) in cases) assertEquals(key, ReceiveFilter.senderKey(raw), "\"$raw\"")
    }

    @Test
    fun `reads STOP and START like the server`() {
        for (body in listOf("STOP", " stop ", "Stop!", "S.T.O.P", "unsubscribe", "START")) assertEquals(true, ReceiveFilter.isOptWord(body), body)
        for (body in listOf("Stop it please", "OK", "")) assertEquals(false, ReceiveFilter.isOptWord(body), body)
    }
}
