package app.relay.sms

/** Decides whether an input looks like a dialable number and normalises it. */
interface PhoneNumberValidator {
    /** Returns the normalised number (digits with optional leading '+'), or null if invalid. */
    fun normalize(raw: String): String?
}

/**
 * Accepts digits with spaces, dashes and brackets, optionally prefixed with '+',
 * and at least [minDigits] digits. Swap in a libphonenumber-backed validator for
 * strict per-country rules.
 */
class LenientPhoneNumberValidator(private val minDigits: Int = 8) : PhoneNumberValidator {
    private val allowed = Regex("""^\+?[\d\s()-]+$""")

    override fun normalize(raw: String): String? {
        val input = raw.trim()
        if (!allowed.matches(input)) return null
        val digits = input.filter(Char::isDigit)
        if (digits.length < minDigits) return null
        return if (input.startsWith("+")) "+$digits" else digits
    }
}

data class ParsedRecipients(val valid: List<String>, val invalid: List<String>)

/** Splits pasted / imported recipient text (newline, comma or semicolon separated). */
class RecipientParser(private val validator: PhoneNumberValidator = LenientPhoneNumberValidator()) {
    private val separators = Regex("[\\n,;]+")

    fun parse(text: String): ParsedRecipients {
        val valid = LinkedHashSet<String>()
        val invalid = mutableListOf<String>()
        text.split(separators).map(String::trim).filter(String::isNotEmpty).forEach { entry ->
            val number = validator.normalize(entry)
            if (number != null) valid += number else invalid += entry
        }
        return ParsedRecipients(valid.toList(), invalid)
    }

    /** Pulls the first phone-number-looking cell from each CSV line; header and other columns are ignored. */
    fun extractFromCsv(csv: String): List<String> = csv.lineSequence()
        .mapNotNull { line ->
            line.split(',', ';', '\t').map { it.trim().trim('"') }.firstNotNullOfOrNull(validator::normalize)
        }
        .distinct()
        .toList()
}
