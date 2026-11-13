package app.relay.gateway.engine

import app.relay.gateway.model.Message
import app.relay.gateway.model.MessageTimeline
import app.relay.protocol.MessageStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageStateMachineTest {
    private val queued = Message("m1", "p1", "+447700900123", "hi", MessageStatus.QUEUED, MessageTimeline(1, 2))

    @Test
    fun `happy path records timestamps and attempts`() {
        val sending = MessageStateMachine.apply(queued, Transition.StartSending(2), 10)!!
        assertEquals(1, sending.attempts)
        assertEquals(2, sending.sim)
        val sent = MessageStateMachine.apply(sending, Transition.Sent, 20)!!
        val delivered = MessageStateMachine.apply(sent, Transition.Delivered, 30)!!
        assertEquals(MessageStatus.DELIVERED, delivered.status)
        assertEquals(20L, delivered.timeline.sentAt)
        assertEquals(30L, delivered.timeline.deliveredAt)
    }

    @Test
    fun `late callbacks are ignored`() {
        val delivered = queued.copy(status = MessageStatus.DELIVERED)
        assertNull(MessageStateMachine.apply(delivered, Transition.Sent, 5))
        assertNull(MessageStateMachine.apply(queued, Transition.Delivered, 5))
    }

    @Test
    fun `delivery report without sent callback fills sentAt`() {
        val sending = queued.copy(status = MessageStatus.SENDING)
        val delivered = MessageStateMachine.apply(sending, Transition.Delivered, 7)!!
        assertEquals(7L, delivered.timeline.sentAt)
    }

    @Test
    fun `manual retry only from failed and resets attempts`() {
        assertNull(MessageStateMachine.apply(queued, Transition.ManualRetry, 5))
        val failed = queued.copy(status = MessageStatus.FAILED, attempts = 3, error = "x", timeline = queued.timeline.copy(failedAt = 4))
        val retried = MessageStateMachine.apply(failed, Transition.ManualRetry, 50)!!
        assertEquals(MessageStatus.QUEUED, retried.status)
        assertEquals(0, retried.attempts)
        assertNull(retried.error)
        assertNull(retried.timeline.failedAt)
        assertEquals(50L, retried.timeline.queuedAt)
    }
}
