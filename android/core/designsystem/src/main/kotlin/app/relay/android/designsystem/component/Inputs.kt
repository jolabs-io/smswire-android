package app.relay.android.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.mono
import app.relay.android.designsystem.theme.sans

/** Text input in the Relay style: dark well, hairline border that turns lime on focus. */
@Composable
fun RelayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
    textSize: Float = 15f,
    singleLine: Boolean = true,
    minLines: Int = 1,
    height: Dp? = 48.dp,
    background: Color = RelayColors.Surface,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var focused by remember { mutableStateOf(false) }
    val style: TextStyle = if (monospace) {
        mono(textSize, lineHeight = if (singleLine) null else 1.55f)
    } else {
        sans(textSize, lineHeight = if (singleLine) null else 1.5f)
    }
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        textStyle = style.copy(color = RelayColors.Text),
        cursorBrush = SolidColor(RelayColors.Lime),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (height != null) Modifier.height(height) else Modifier)
                    .clip(shape)
                    .background(background)
                    .border(1.dp, if (focused) RelayColors.Lime else RelayColors.Line, shape)
                    .padding(if (singleLine) PaddingValues(horizontal = 14.dp) else PaddingValues(horizontal = 14.dp, vertical = 12.dp)),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = style, color = RelayColors.Placeholder)
                inner()
            }
        },
    )
}
