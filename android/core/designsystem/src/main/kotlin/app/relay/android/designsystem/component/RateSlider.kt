package app.relay.android.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.relay.android.designsystem.theme.RelayColors

/** Messages-per-minute slider, lime like the rest of the controls. */
@Composable
fun RateSlider(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, range: IntRange = 1..60) {
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = RelayColors.Lime,
            activeTrackColor = RelayColors.Lime,
            inactiveTrackColor = RelayColors.Track,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
