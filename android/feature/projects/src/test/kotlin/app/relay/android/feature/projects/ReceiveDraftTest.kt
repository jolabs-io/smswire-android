package app.relay.android.feature.projects

import app.relay.gateway.model.ReceiveRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same rules as the web's useReceiveRules: what can be added, and what gets saved. */
class ReceiveDraftTest {
    @Test
    fun `adds several at once and stops at the first that can't go in`() {
        val d = ReceiveDraft.of(ReceiveRules(senders = listOf("CBE"))).copy(typed = "127, telebirr; cbe, 8994").add()
        assertEquals(listOf("CBE", "127", "telebirr"), d.senders)
        assertEquals("cbe, 8994", d.typed)
        assertEquals(SenderIssue.DUPLICATE, d.issue)
    }

    @Test
    fun `numbers compare by their last nine digits`() {
        assertEquals(SenderIssue.DUPLICATE, senderIssue("0911 234 567", listOf("+251911234567")))
        assertEquals(SenderIssue.INVALID, senderIssue("+", emptyList()))
        assertEquals(SenderIssue.TOO_LONG, senderIssue("x".repeat(33), emptyList()))
        assertEquals(SenderIssue.MAX, senderIssue("127", List(50) { "+2519110000%02d".format(it) }))
        assertNull(senderIssue("  ", emptyList()))
    }

    @Test
    fun `saves a sender still in the box, but not a bad one`() {
        val d = ReceiveDraft(replies = false, all = true, senders = listOf("CBE"), typed = "127")
        assertEquals(ReceiveRules(replies = false, all = true, senders = listOf("CBE", "127")), d.toRules())
        assertTrue(d.copy(typed = "+").blocked)
        assertEquals(listOf("CBE"), d.copy(typed = "cbe").toRules().senders)
        assertFalse(d.copy(typed = "cbe").blocked)
    }

    @Test
    fun `knows when nothing changed`() {
        val saved = ReceiveRules(senders = listOf("CBE"))
        assertFalse(ReceiveDraft.of(saved).changes(saved))
        assertTrue(ReceiveDraft.of(saved).remove("CBE").changes(saved))
        assertFalse(ReceiveDraft.of(null).changes(null))
    }
}
