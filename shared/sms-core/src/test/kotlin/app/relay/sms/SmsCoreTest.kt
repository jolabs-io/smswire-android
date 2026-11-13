package app.relay.sms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SegmentCalculatorTest {
    @Test
    fun `plain text up to 160 chars is one GSM segment`() {
        assertEquals(SegmentInfo(160, SmsEncoding.GSM7, 1), GsmSegmentCalculator.calculate("a".repeat(160)))
    }

    @Test
    fun `long GSM text splits at 153`() {
        assertEquals(2, GsmSegmentCalculator.calculate("a".repeat(161)).segments)
        assertEquals(3, GsmSegmentCalculator.calculate("a".repeat(307)).segments)
    }

    @Test
    fun `extension characters cost two septets`() {
        assertEquals(2, GsmSegmentCalculator.calculate("€".repeat(81)).segments)
    }

    @Test
    fun `emoji switches to unicode`() {
        val info = GsmSegmentCalculator.calculate("Hi 👋")
        assertEquals(SmsEncoding.UCS2, info.encoding)
        assertEquals(1, info.segments)
        assertEquals(2, GsmSegmentCalculator.calculate("ü".repeat(70) + "😀").segments)
    }

    @Test
    fun `empty text has no segments`() {
        assertEquals(0, GsmSegmentCalculator.calculate("").segments)
    }
}

class RecipientParserTest {
    private val parser = RecipientParser()

    @Test
    fun `splits on newlines commas and semicolons`() {
        val result = parser.parse("+44 7700 900112\n+44 7700 900348, 07700900571;abc\n\n123")
        assertEquals(listOf("+447700900112", "+447700900348", "07700900571"), result.valid)
        assertEquals(listOf("abc", "123"), result.invalid)
    }

    @Test
    fun `duplicates are counted once`() {
        assertEquals(1, parser.parse("+447700900112\n+44 7700 900112").valid.size)
    }

    @Test
    fun `extracts numbers from csv columns`() {
        val csv = "name,phone\n\"Tom\",+44 7700 900731\nIvy,07700900154\nno number,abc"
        assertEquals(listOf("+447700900731", "07700900154"), parser.extractFromCsv(csv))
    }

    @Test
    fun `validator rejects letters`() {
        assertNull(LenientPhoneNumberValidator().normalize("+44 7700 ABC"))
    }
}
