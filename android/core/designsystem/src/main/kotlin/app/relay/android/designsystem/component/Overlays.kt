package app.relay.android.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.sans
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private val RiseEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * Bottom sheet over a dimmed scrim. Tapping the scrim calls [onDismiss]; pass null for
 * sheets that must be confirmed explicitly (e.g. the one-time API key reveal).
 */
@Composable
fun RelaySheet(
    visible: Boolean,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    spacing: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rise = with(LocalDensity.current) { 28.dp.roundToPx() }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
        val visibility = this
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val slide = with(visibility) {
                Modifier.animateEnterExit(
                    enter = slideInVertically(tween(240, easing = RiseEasing)) { rise },
                    exit = slideOutVertically(tween(160)) { rise },
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(RelayColors.Scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onDismiss?.invoke() },
                    ),
            )
            val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                modifier
                    .align(Alignment.BottomCenter)
                    .then(slide)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.9f)
                    .clip(shape)
                    .background(RelayColors.Surface)
                    .border(1.dp, RelayColors.Line, shape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 34.dp),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(RelayColors.BarIdle),
                )
                content()
            }
        }
    }
}

data class ToastMessage(val id: Long, val text: String)

/** Short confirmations ("API key copied"). Holds state only; [ToastHost] renders and expires it. */
class ToastController {
    private val state = MutableStateFlow<ToastMessage?>(null)
    private var nextId = 0L

    val current: StateFlow<ToastMessage?> = state.asStateFlow()

    fun show(text: String) {
        state.value = ToastMessage(++nextId, text)
    }

    fun dismiss(id: Long) = state.update { if (it?.id == id) null else it }
}

@Composable
fun ToastHost(message: ToastMessage?, onExpired: (Long) -> Unit, modifier: Modifier = Modifier) {
    if (message != null) {
        LaunchedEffect(message.id) {
            delay(2_200)
            onExpired(message.id)
        }
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
        exit = fadeOut(tween(160)),
    ) {
        Text(
            text = message?.text.orEmpty(),
            style = sans(14f, FontWeight.Medium),
            color = RelayColors.Ink,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(RelayColors.Text)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

/** Keeps showing the last non-null [value], so a sheet's content survives its exit animation. */
@Composable
fun <T : Any> rememberLastNonNull(value: T?): T? {
    val last = remember { mutableStateOf(value) }
    if (value != null) last.value = value
    return last.value
}
