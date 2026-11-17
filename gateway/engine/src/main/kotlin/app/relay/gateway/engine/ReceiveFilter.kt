package app.relay.gateway.engine

import app.relay.gateway.model.ReceiveRules

/**
 * How a project's receive rules read a received SMS. Mirrors packages/contracts/src/receive.ts,
 * so the phone lets go exactly what the server would give to some project.
 */
object ReceiveFilter {
    /** As the server allows: how many senders a project lists, and how long each may be. */
    const val MAX_SENDERS = 50
    const val MAX_SENDER_LENGTH = 32

    /** Fewer digits is a short code (a bank's or a carrier's), which no person texts from. */
    private const val MIN_PERSON_DIGITS = 7

    /** Enough to tell numbers apart, few enough to match "0911…" against "+251911…". */
    private const val MATCH_DIGITS = 9

    private val OPT_OUT_WORDS = setOf("STOP", "STOPALL", "UNSUBSCRIBE", "CANCEL", "END", "QUIT")
    private val OPT_IN_WORDS = setOf("START", "UNSTOP", "SUBSCRIBE")

    enum class Match { REPLY, SENDER, ALL }

    /** The digits a person's number is matched on; null for named senders ("CBE") and short codes. */
    fun personDigits(from: String): String? {
        if (from.any(Char::isLetter)) return null
        val digits = from.filter { it in '0'..'9' }
        return if (digits.length < MIN_PERSON_DIGITS) null else digits.takeLast(MATCH_DIGITS)
    }

    /** A name by its letters and digits in upper case, a person's number by its last 9 digits, a short code by its digits. */
    fun senderKey(raw: String): String? {
        val text = raw.trim()
        if (text.any(Char::isLetter)) return JoinKeywords.normalize(text).takeIf { it.isNotEmpty() }?.let { "n:$it" }
        val digits = text.filter { it in '0'..'9' }
        if (digits.isEmpty()) return null
        return if (digits.length >= MIN_PERSON_DIGITS) "t:" + digits.takeLast(MATCH_DIGITS) else "c:$digits"
    }

    /** STOP or START, read as the server reads them: only the letters A–Z of the whole reply. */
    fun isOptWord(body: String): Boolean {
        val word = body.trim().uppercase().filter { it in 'A'..'Z' }
        return word in OPT_OUT_WORDS || word in OPT_IN_WORDS
    }

    /** A project pinned to a SIM only hears that SIM. */
    fun hearsSim(projectSim: Int?, sim: Int?) = projectSim == null || sim == null || projectSim == sim

    /**
     * Why [rules] take an SMS, or null. STOP/START from someone the project texted and a person
     * texting a join keyword count whatever is set.
     */
    fun match(rules: ReceiveRules, from: String, body: String, texted: Boolean, joinKeyword: Boolean, allowAll: Boolean): Match? {
        val person = personDigits(from) != null
        if (person && texted && (rules.replies || isOptWord(body))) return Match.REPLY
        if (person && joinKeyword) return Match.REPLY
        val key = senderKey(from)
        if (key != null && rules.senders.any { senderKey(it) == key }) return Match.SENDER
        if (rules.all && allowAll) return Match.ALL
        return null
    }
}
