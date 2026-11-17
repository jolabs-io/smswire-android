package app.relay.android.designsystem.format

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Display formatting shared by every screen. Pure functions so they're trivially testable. */
object Format {
    private val grouping = NumberFormat.getIntegerInstance(Locale.US)
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val hourMinute = DateTimeFormatter.ofPattern("HH:mm")

    fun count(n: Int): String = grouping.format(n)

    /** 0.981 → "98.1%"; null → "—". */
    fun percent(ratio: Double?): String =
        ratio?.let { String.format(Locale.US, "%.1f%%", it * 100) } ?: "—"

    fun clock(epochMillis: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
        epochMillis?.let { clock.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "—"

    /** Compact age for lists: "now", "12m ago", then the time of day. */
    fun ago(epochMillis: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val seconds = (now - epochMillis) / 1000.0
        return when {
            seconds < 45 -> "now"
            seconds < 3600 -> "${(seconds / 60).roundToInt()}m ago"
            else -> hourMinute.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
        }
    }

    /** Wordier age for "last used": "just now", "2 min ago", "3 hours ago", "3 days ago", "never". */
    fun lastUsed(epochMillis: Long?, now: Long): String {
        if (epochMillis == null) return "never"
        val minutes = (now - epochMillis) / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 48 * 60 -> plural(minutes / 60, "hour")
            else -> plural(minutes / (24 * 60), "day")
        }
    }

    /** "4h 12m" */
    fun duration(millis: Long): String {
        val minutes = millis / 60_000
        return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
    }

    /** Uppercase initials of up to two words: "CRM Reminders" → "CR". */
    /**
     * Which received SMS a project takes, in a few words: "Replies, CBE, 127 +2", "Only CBE",
     * "Every SMS". With [allowsAll] false (this phone isn't set to All), every SMS is held back.
     */
    fun receives(replies: Boolean, all: Boolean, senders: List<String>, allowsAll: Boolean): String {
        if (all && allowsAll) return "Every SMS"
        val shown = senders.take(2).joinToString(", ") + if (senders.size > 2) " +${senders.size - 2}" else ""
        val base = when {
            replies && senders.isNotEmpty() -> "Replies, $shown"
            replies -> "Replies"
            senders.isNotEmpty() -> "Only $shown"
            else -> "STOP and join keywords only"
        }
        return if (all) "$base · every SMS needs All" else base
    }

    fun initials(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.first() }.take(2).joinToString("").uppercase()

    private fun plural(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"} ago"
}
