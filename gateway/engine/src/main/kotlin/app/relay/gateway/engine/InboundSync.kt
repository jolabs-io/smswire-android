package app.relay.gateway.engine

import app.relay.gateway.model.GatewaySettings
import app.relay.gateway.model.InboxSync
import app.relay.gateway.model.IncomingSms
import app.relay.gateway.port.InboundRepository
import app.relay.gateway.port.MessageRepository
import app.relay.gateway.port.RelayConnection
import app.relay.gateway.port.SettingsRepository
import app.relay.protocol.DeviceFrame
import java.security.MessageDigest

/**
 * Gets received SMS to the server's inbox at least once: stored first, sent now if the
 * socket is up, and re-sent on every reconnect until the server acks the ref.
 *
 * Which ones go is the phone's [InboxSync] setting, decided here so a filtered SMS is never
 * stored for upload, let alone sent.
 */
class InboundSync(
    private val inbound: InboundRepository,
    private val connection: RelayConnection,
    private val settings: SettingsRepository,
    private val messages: MessageRepository,
) {
    /** A received SMS from the radio. [sentAt] is the sender's timestamp, the same on every broadcast of it. */
    suspend fun receive(from: String, body: String, sentAt: Long, receivedAt: Long, sim: Int?) {
        if (!shouldSync(from, receivedAt)) return
        val sms = IncomingSms(refOf(from, body, sentAt), from, body, receivedAt, sim)
        if (inbound.insertIfAbsent(sms)) send(sms)
    }

    suspend fun flush() {
        inbound.unreported().forEach(::send)
    }

    suspend fun onAck(ref: String) = inbound.markReported(ref)

    private suspend fun shouldSync(from: String, now: Long) = when (settings.current().inboxSync) {
        InboxSync.ALL -> true
        InboxSync.OFF -> false
        InboxSync.REPLIES -> replyDigits(from)?.let { messages.messagedSince(it, now - GatewaySettings.REPLY_WINDOW_MS) } ?: false
    }

    private fun send(sms: IncomingSms) {
        connection.send(DeviceFrame.Incoming(sms.ref, sms.from, sms.body, sms.receivedAt, sms.sim))
    }

    internal companion object {
        /** Fewer digits is a short code (a bank's or a carrier's), which no one replies from. */
        private const val MIN_DIGITS = 7

        /** Enough to tell numbers apart, few enough to match "0911…" against "+251911…". */
        private const val MATCH_DIGITS = 9

        /** The digits a reply from [from] is matched on; null for named senders ("CBE") and short codes. */
        fun replyDigits(from: String): String? {
            if (from.any(Char::isLetter)) return null
            val digits = from.filter(Char::isDigit)
            return if (digits.length < MIN_DIGITS) null else digits.takeLast(MATCH_DIGITS)
        }

        fun refOf(from: String, body: String, sentAt: Long): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("$from|$sentAt|$body".toByteArray())
            return "in_" + digest.take(12).joinToString("") { "%02x".format(it) }
        }
    }
}
