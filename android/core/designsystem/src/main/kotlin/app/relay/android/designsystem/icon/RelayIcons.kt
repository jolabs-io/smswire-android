package app.relay.android.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons from the Relay design, drawn on a 24×24 grid. Tint them with `Icon(tint = …)`. */
object RelayIcons {
    /**
     * The brand mark (packages/brand: `logo`), drawn at half scale: a filled dot with two
     * waves each side. Same paths as the launcher icon.
     */
    val Logo: ImageVector = builder("logo").apply {
        addPath(pathData = addPathNodes("M9.75,7.875H14.25A1.75,1.75 0 0 1 16,9.625V12.375A1.75,1.75 0 0 1 14.25,14.125H11.75L9,16.125L9.75,14.125A1.75,1.75 0 0 1 8,12.375V9.625A1.75,1.75 0 0 1 9.75,7.875Z"), fill = SolidColor(Color.Black))
        addPath(
            pathData = addPathNodes("M7.05,7.05a7,7 0,0 0,0 9.9M16.95,7.05a7,7 0,0 1,0 9.9M4.93,4.93a10,10 0,0 0,0 14.14M19.07,4.93a10,10 0,0 1,0 14.14"),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
        )
    }.build()

    val Settings = stroke("settings", 1.75f, "M4 7h9M18 7h2M4 17h3M12 17h8", circle(15.5f, 7f, 2.5f), circle(9.5f, 17f, 2.5f))
    val ChevronRight = stroke("chevron_right", 2f, "M9 6l6 6-6 6")
    val Back = stroke("back", 2f, "M15 5l-7 7 7 7")
    val Search = stroke("search", 1.9f, circle(11f, 11f, 7f), "M20 20l-4-4")
    val Plus = stroke("plus", 2.2f, "M12 5v14M5 12h14")
    val Pause = stroke("pause", 2.4f, "M9 5v14M15 5v14")
    val Play = fill("play", "M7 4.5l13 7.5-13 7.5z")
    val Copy = stroke(
        "copy", 1.8f,
        roundRect(8f, 8f, 12f, 12f, 2.5f),
        "M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2",
    )
    val Eye = stroke("eye", 1.8f, "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z", circle(12f, 12f, 3f))
    val Rotate = stroke("rotate", 2f, "M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7")
    val Upload = stroke("upload", 1.9f, "M12 15V4M7 9l5-5 5 5M4 20h16")
    val Person = stroke("person", 1.9f, circle(12f, 8f, 4f), "M4 21c1-4 4.5-6 8-6s7 2 8 6")
    val Close = stroke("close", 2f, "M6 6l12 12M18 6L6 18")
    val Check = stroke("check", 2.4f, "M5 12.5l4.5 4.5L19 7.5")
    val Power = stroke("power", 2f, "M12 3v9", "M17.66 7.34a8 8 0 1 1-11.32 0")
    val Scan = stroke("scan", 1.9f, "M4 8V5h3M17 5h3v3M20 16v3h-3M7 19H4v-3", "M7 12h10")

    val TabGateway = stroke(
        "tab_gateway", 1.8f,
        circle(12f, 12f, 2f),
        "M8.5 8.5a5 5 0 0 0 0 7M15.5 8.5a5 5 0 0 1 0 7M5.6 5.6a9 9 0 0 0 0 12.8M18.4 5.6a9 9 0 0 1 0 12.8",
    )
    val TabMessages = stroke("tab_messages", 1.8f, "M4 5h16v11H9l-5 4z", "M8 9.5h8M8 12.5h5")
    val TabBulk = stroke("tab_bulk", 1.8f, "M12 3l9 5-9 5-9-5z", "M3 12.5l9 5 9-5M3 17l9 5 9-5")
    val TabProjects = stroke(
        "tab_projects", 1.8f,
        circle(8f, 15f, 4f),
        "M11 12l8.5-8.5M16.5 6.5l2.5 2.5M14.5 8.5l1.8 1.8",
    )

    private fun builder(name: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

    private fun stroke(name: String, width: Float, vararg paths: String): ImageVector =
        builder(name).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun fill(name: String, path: String): ImageVector =
        builder(name).apply { addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)) }.build()

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    private fun roundRect(x: Float, y: Float, w: Float, h: Float, rx: Float): String {
        val iw = w - 2 * rx
        val ih = h - 2 * rx
        return "M${x + rx} ${y}h${iw}a$rx $rx 0 0 1 $rx ${rx}v${ih}a$rx $rx 0 0 1 -$rx ${rx}h-${iw}" +
            "a$rx $rx 0 0 1 -$rx -${rx}v-${ih}a$rx $rx 0 0 1 $rx -${rx}z"
    }
}
