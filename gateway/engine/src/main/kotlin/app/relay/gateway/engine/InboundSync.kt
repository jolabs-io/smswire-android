package app.relay.gateway.engine

import app.relay.gateway.model.GatewaySettings
import app.relay.gateway.model.InboxSync
import app.relay.gateway.model.IncomingSms
import app.relay.gateway.port.InboundRepository
import app.relay.gateway.port.JoinKeywordStore
import app.relay.gateway.port.MessageRepository
import app.relay.gateway.port.ProjectRepository
import app.relay.gateway.port.RelayConnection
import app.relay.gateway.port.SettingsRepository
import app.relay.protocol.DeviceFrame
import java.security.MessageDigest

/**
 * Gets received SMS to the server's inbox at least once: stored first, sent now if the
 * socket is up, and re-sent on every reconnect until the server acks the ref.
 *
 * Which ones go is decided here, so a filtered SMS is never stored for upload, let alone sent:
 * an SMS goes when one of the phone's projects takes it under its receive rules (set on the web),
 * within the phone's own [InboxSync]: OFF lets nothing go, REPLIES all but a project's "every SMS",
 * ALL that too. Until the server has sent the rules (or with no project) the phone keeps its old
 * behaviour: replies to it and join keywords on REPLIES, everything on ALL.
 */
class InboundSync(
    private val inbound: InboundRepository,
    private val connection: RelayConnection,
    private val settings: SettingsRepository,
    private val messages: MessageRepository,
    private val joinKeywords: JoinKeywordStore,
    private val projects: ProjectRepository,
) {
    /** A received SMS from the radio. [sentAt] is the sender's timestamp, the same on every broadcast of it. */
    suspend fun receive(from: String, body: String, sentAt: Long, receivedAt: Long, sim: Int?) {
        if (!shouldSync(from, body, receivedAt, sim)) return
        val sms = IncomingSms(refOf(from, body, sentAt), from, body, receivedAt, sim)
        if (inbound.insertIfAbsent(sms)) send(sms)
    }

    suspend fun flush() {
        inbound.unreported().forEach { send(it) }
    }

    suspend fun onAck(ref: String) = inbound.markReported(ref)

    /** The server's list replaces the one kept. */
    suspend fun onJoinKeywords(keywords: List<String>) = joinKeywords.replace(keywords.toSet())

    private suspend fun shouldSync(from: String, body: String, now: Long, sim: Int?): Boolean {
        val ceiling = settings.current().inboxSync
        if (ceiling == InboxSync.OFF) return false
        val all = projects.all()
        if (all.isEmpty() || all.any { it.receive == null }) return legacy(ceiling, from, body, now)
        val digits = ReceiveFilter.personDigits(from)
        val texted = digits?.let { messages.textedBy(it, now - GatewaySettings.REPLY_WINDOW_MS) }.orEmpty()
        val keyword = digits != null && isJoinKeyword(body)
        return all.any { p ->
            ReceiveFilter.hearsSim(p.sim, sim) &&
                ReceiveFilter.match(p.receive!!, from, body, p.id in texted, keyword, allowAll = ceiling == InboxSync.ALL) != null
        }
    }

    private suspend fun legacy(ceiling: InboxSync, from: String, body: String, now: Long) = when (ceiling) {
        InboxSync.ALL -> true
        InboxSync.OFF -> false
        // Only people's numbers: never a bank's name or a short code, keyword or not.
        InboxSync.REPLIES -> ReceiveFilter.personDigits(from)?.let { messages.messagedSince(it, now - GatewaySettings.REPLY_WINDOW_MS) || isJoinKeyword(body) } ?: false
    }

    private suspend fun isJoinKeyword(body: String): Boolean {
        if (body.length > MAX_BODY_FOR_KEYWORD) return false
        val norm = JoinKeywords.normalize(body)
        return norm.isNotEmpty() && norm.length <= JoinKeywords.MAX_LENGTH && norm in joinKeywords.keywords()
    }

    /** With the projects that texted the sender, so the server can tell a reply from a stranger. */
    private suspend fun send(sms: IncomingSms) {
        val texted = ReceiveFilter.personDigits(sms.from)?.let { messages.textedBy(it, sms.receivedAt - GatewaySettings.REPLY_WINDOW_MS) }.orEmpty()
        connection.send(DeviceFrame.Incoming(sms.ref, sms.from, sms.body, sms.receivedAt, sms.sim, texted.sorted()))
    }

    internal companion object {
        /** Longer texts can't be a keyword (24 letters and digits, plus a little punctuation). */
        private const val MAX_BODY_FOR_KEYWORD = 200

        fun refOf(from: String, body: String, sentAt: Long): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("$from|$sentAt|$body".toByteArray())
            return "in_" + digest.take(12).joinToString("") { "%02x".format(it) }
        }
    }
}
