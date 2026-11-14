package app.relay.gateway.service

/** A release's notes (its android/CHANGELOG.md section), as packages/contracts/src/releaseNotes.ts reads them. */
sealed interface ReleaseNotesBlock {
    data class Heading(val text: String) : ReleaseNotesBlock
    data class Items(val items: List<String>) : ReleaseNotesBlock
    data class Text(val text: String) : ReleaseNotesBlock
}

private val HEADING = Regex("^#{1,6}\\s+(.*)$")
private val ITEM = Regex("^[-*]\\s+(.*)$")

/** Headings, lists and paragraphs, in order; other Markdown is left as written. */
fun parseReleaseNotes(notes: String): List<ReleaseNotesBlock> {
    val blocks = mutableListOf<ReleaseNotesBlock>()
    var items: MutableList<String>? = null
    var text: StringBuilder? = null
    fun close() {
        items?.let { blocks += ReleaseNotesBlock.Items(it.toList()) }
        text?.let { blocks += ReleaseNotesBlock.Text(it.toString()) }
        items = null
        text = null
    }
    for (raw in notes.lines()) {
        val line = raw.trim()
        val heading = HEADING.find(line)
        val item = ITEM.find(line)
        when {
            line.isEmpty() -> close()
            heading != null -> {
                close()
                blocks += ReleaseNotesBlock.Heading(heading.groupValues[1].trim())
            }
            item != null -> {
                if (items == null) {
                    close()
                    items = mutableListOf()
                }
                items!! += item.groupValues[1].trim()
            }
            // A wrapped item: its next line, indented or not.
            items != null -> items!!.let { it[it.lastIndex] = it.last() + " " + line }
            text != null -> text!!.append('\n').append(line)
            else -> text = StringBuilder(line)
        }
    }
    close()
    return blocks
}
