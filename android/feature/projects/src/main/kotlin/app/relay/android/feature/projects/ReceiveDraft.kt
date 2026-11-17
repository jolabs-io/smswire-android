package app.relay.android.feature.projects

import app.relay.gateway.engine.ReceiveFilter
import app.relay.gateway.model.ReceiveRules

/** Why a typed sender can't be added; the same checks as the web. */
enum class SenderIssue(val message: String) {
    INVALID("Enter a phone number, a short code or a sender name"),
    TOO_LONG("Use at most ${ReceiveFilter.MAX_SENDER_LENGTH} characters"),
    DUPLICATE("Already in the list"),
    MAX("That's the most: ${ReceiveFilter.MAX_SENDERS} senders"),
}

fun senderIssue(raw: String, senders: List<String>): SenderIssue? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    if (text.length > ReceiveFilter.MAX_SENDER_LENGTH) return SenderIssue.TOO_LONG
    val key = ReceiveFilter.senderKey(text) ?: return SenderIssue.INVALID
    if (senders.any { ReceiveFilter.senderKey(it) == key }) return SenderIssue.DUPLICATE
    if (senders.size >= ReceiveFilter.MAX_SENDERS) return SenderIssue.MAX
    return null
}

/** A project's receive rules while they're edited on the phone, with the sender still being typed. */
data class ReceiveDraft(
    val replies: Boolean,
    val all: Boolean,
    val senders: List<String>,
    val typed: String = "",
) {
    /** What stops the next sender in the box (the first, when several are typed) from going in. */
    val issue: SenderIssue? get() = senderIssue(typed.split(SEPARATORS).firstOrNull { it.isNotBlank() } ?: "", senders)

    /** What's typed can't be saved; a duplicate or one too many is just left out. */
    val blocked: Boolean get() = issue == SenderIssue.INVALID || issue == SenderIssue.TOO_LONG

    /** Adds what's typed (commas, semicolons and new lines split several), stopping at the first that can't go in. */
    fun add(): ReceiveDraft {
        val parts = typed.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }
        val list = senders.toMutableList()
        parts.forEachIndexed { i, part ->
            if (senderIssue(part, list) != null) return copy(senders = list, typed = parts.drop(i).joinToString(", "))
            list += part
        }
        return copy(senders = list, typed = "")
    }

    fun remove(sender: String) = copy(senders = senders - sender)

    /** What's saved: a sender still in the box counts when it's fine. */
    fun toRules(): ReceiveRules {
        // Senders still in the box count when they're fine, as if Add had been tapped.
        val added = if (typed.isBlank()) this else add()
        return ReceiveRules(replies, all, added.senders)
    }

    fun changes(saved: ReceiveRules?) = toRules() != (saved ?: ReceiveRules())

    companion object {
        private val SEPARATORS = Regex("[,،、;\\n]")

        fun of(rules: ReceiveRules?) = (rules ?: ReceiveRules()).let { ReceiveDraft(it.replies, it.all, it.senders) }
    }
}
