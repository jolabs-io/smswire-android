package app.relay.android.designsystem.component

import androidx.compose.ui.graphics.Color
import app.relay.android.designsystem.theme.RelayColors
import app.relay.protocol.MessageStatus

data class StatusVisual(val label: String, val color: Color)

val MessageStatus.visual: StatusVisual
    get() = when (this) {
        MessageStatus.QUEUED -> StatusVisual("Queued", RelayColors.Amber)
        MessageStatus.SENDING -> StatusVisual("Sending", RelayColors.Blue)
        MessageStatus.SENT -> StatusVisual("Sent", RelayColors.Blue)
        MessageStatus.DELIVERED -> StatusVisual("Delivered", RelayColors.Lime)
        MessageStatus.FAILED -> StatusVisual("Failed", RelayColors.Red)
    }
