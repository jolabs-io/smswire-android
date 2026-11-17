package app.relay.gateway.engine

import java.text.Normalizer
import java.util.Locale

/**
 * How a received text is compared with the workspace's join keywords. Mirrors normalizeKeyword in
 * packages/contracts/src/keywords.ts: NFKC, upper case, then only letters and numbers (any script).
 */
object JoinKeywords {
    /** Longer than any keyword: not worth comparing. */
    const val MAX_LENGTH = 24

    fun normalize(text: String): String {
        val folded = Normalizer.normalize(text, Normalizer.Form.NFKC).uppercase(Locale.ROOT)
        val out = StringBuilder(folded.length)
        var i = 0
        while (i < folded.length) {
            val cp = folded.codePointAt(i)
            if (isLetterOrNumber(cp)) out.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    /** \p{L} or \p{N}, as the server reads them: Ethiopic and other non-decimal numerals count too. */
    private fun isLetterOrNumber(cp: Int): Boolean = Character.isLetter(cp) || when (Character.getType(cp)) {
        Character.DECIMAL_DIGIT_NUMBER.toInt(), Character.LETTER_NUMBER.toInt(), Character.OTHER_NUMBER.toInt() -> true
        else -> false
    }
}
