package app.relay.sms

enum class SmsEncoding(val label: String) {
    GSM7("GSM-7"),
    UCS2("Unicode"),
}

data class SegmentInfo(val length: Int, val encoding: SmsEncoding, val segments: Int)

fun interface SegmentCalculator {
    fun calculate(text: String): SegmentInfo
}

/**
 * Counts SMS parts the way carriers bill them: GSM 03.38 when every character fits
 * (extension characters cost two septets), otherwise UCS-2 in UTF-16 code units.
 */
object GsmSegmentCalculator : SegmentCalculator {
    private const val GSM_SINGLE = 160
    private const val GSM_MULTI = 153
    private const val UCS2_SINGLE = 70
    private const val UCS2_MULTI = 67

    private const val BASIC =
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    private const val EXTENSION = "^{}\\[~]|€\u000C"

    private val basic = BASIC.toSet()
    private val extension = EXTENSION.toSet()

    override fun calculate(text: String): SegmentInfo {
        if (text.isEmpty()) return SegmentInfo(0, SmsEncoding.GSM7, 0)
        val septets = gsmSeptets(text)
        return if (septets != null) {
            SegmentInfo(text.length, SmsEncoding.GSM7, parts(septets, GSM_SINGLE, GSM_MULTI))
        } else {
            SegmentInfo(text.length, SmsEncoding.UCS2, parts(text.length, UCS2_SINGLE, UCS2_MULTI))
        }
    }

    private fun gsmSeptets(text: String): Int? {
        var count = 0
        for (c in text) {
            count += when (c) {
                in basic -> 1
                in extension -> 2
                else -> return null
            }
        }
        return count
    }

    private fun parts(units: Int, single: Int, multi: Int) =
        if (units <= single) 1 else (units + multi - 1) / multi
}
