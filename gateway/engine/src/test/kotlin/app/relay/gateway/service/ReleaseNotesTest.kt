package app.relay.gateway.service

import kotlin.test.Test
import kotlin.test.assertEquals

class ReleaseNotesTest {
    @Test
    fun `reads headings, items (wrapped ones too) and paragraphs`() {
        val notes = "### New\n- Updates install themselves\n- A long item\n  that wraps\n\nThanks for testing.\nMore soon.\n\n### Fixed\n* Pairing offline"
        assertEquals(
            listOf(
                ReleaseNotesBlock.Heading("New"),
                ReleaseNotesBlock.Items(listOf("Updates install themselves", "A long item that wraps")),
                ReleaseNotesBlock.Text("Thanks for testing.\nMore soon."),
                ReleaseNotesBlock.Heading("Fixed"),
                ReleaseNotesBlock.Items(listOf("Pairing offline")),
            ),
            parseReleaseNotes(notes),
        )
    }
}
