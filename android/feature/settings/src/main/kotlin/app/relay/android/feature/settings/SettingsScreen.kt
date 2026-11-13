package app.relay.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.relay.android.designsystem.component.DetailTopBar
import app.relay.android.designsystem.component.Hairline
import app.relay.android.designsystem.component.ListRow
import app.relay.android.designsystem.component.MonoValue
import app.relay.android.designsystem.component.RateSlider
import app.relay.android.designsystem.component.RelayCard
import app.relay.android.designsystem.component.RelayListCard
import app.relay.android.designsystem.component.RelaySheet
import app.relay.android.designsystem.component.RelaySwitch
import app.relay.android.designsystem.component.SecondaryButton
import app.relay.android.designsystem.component.SectionLabel
import app.relay.android.designsystem.component.SegmentedControl
import app.relay.android.designsystem.theme.LocalAppName
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.android.designsystem.theme.mono
import app.relay.android.designsystem.theme.sans
import app.relay.gateway.model.InboxSync

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onFix: (CheckAction) -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    /** Asks to stop the gateway and close the app. */
    onExit: () -> Unit,
    appVersion: String,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unpairOpen by viewModel.unpairOpen.collectAsStateWithLifecycle()
    val smsLimitOpen by viewModel.smsLimitOpen.collectAsStateWithLifecycle()
    // Fixes happen in system screens (battery, default SMS app); re-check on return.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    Box(Modifier.fillMaxSize()) {
        SettingsScreen(
            state,
            onBack = onBack,
            onSim = viewModel::setSim,
            onRate = viewModel::setRate,
            onRetries = viewModel::changeRetries,
            onInboxSync = viewModel::setInboxSync,
            onToggle = viewModel::toggle,
            onCheck = {
                when (it) {
                    CheckAction.WORKSPACE -> viewModel.openUnpair()
                    CheckAction.SMS_LIMIT -> viewModel.openSmsLimit()
                    else -> onFix(it)
                }
            },
            onExit = onExit,
            appVersion = appVersion,
        )
        UnpairSheet(unpairOpen, state.workspace, state.serverUrl, viewModel::dismissUnpair, viewModel::unpair)
        SmsLimitSheet(
            visible = smsLimitOpen,
            limit = state.smsLimit,
            onDismiss = viewModel::dismissSmsLimit,
            onDefaultApp = { onFix(CheckAction.DEFAULT_SMS_APP) },
            onCopyCommand = { onCopy("Commands", state.smsLimit.adbCommand) },
        )
    }
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSim: (app.relay.gateway.model.SimPreference) -> Unit,
    onRate: (Int) -> Unit,
    onRetries: (Int) -> Unit,
    onInboxSync: (InboxSync) -> Unit,
    onToggle: (ToggleKey) -> Unit,
    onCheck: (CheckAction) -> Unit,
    onExit: () -> Unit,
    appVersion: String,
) {
    val app = LocalAppName.current
    Column(Modifier.fillMaxSize().background(RelayColors.Background)) {
        DetailTopBar("Gateway settings", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("Sending", Modifier.padding(start = 4.dp, top = 4.dp))
            RelayCard(radius = 22.dp, padding = 16.dp, spacing = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Default SIM", style = RelayType.RowTitle, color = RelayColors.Text)
                    SegmentedControl(state.sims, state.sim, onSim, trackColor = RelayColors.Background, textSize = 12.5f, captions = state.simCarriers)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text("Max send rate", style = RelayType.RowTitle, color = RelayColors.Text)
                        Text("${state.rate}/min", style = mono(13f, FontWeight.Medium), color = RelayColors.Text)
                    }
                    RateSlider(state.rate, onRate)
                    if (state.rateWarning) {
                        Text("Above 30/min carriers may flag the SIM as spam.", style = RelayType.Hint, color = RelayColors.Amber)
                    }
                    state.rateCap?.let { cap ->
                        Text("Android limits this phone to $cap SMS parts a minute; Relay holds to that. See Device checks.", style = RelayType.Hint, color = RelayColors.Amber)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Retry failed sends", style = RelayType.RowTitle, color = RelayColors.Text)
                        Text("Backoff 30s, 2m, 10m", style = RelayType.Hint, color = RelayColors.Dim)
                    }
                    Stepper(state.retries, onRetries)
                }
            }

            SectionLabel("Incoming SMS", Modifier.padding(start = 4.dp, top = 10.dp))
            RelayCard(radius = 22.dp, padding = 16.dp, spacing = 10.dp) {
                if (state.receivesSms) {
                    Text("Send to the web inbox", style = RelayType.RowTitle, color = RelayColors.Text)
                    SegmentedControl(INBOX_OPTIONS, state.inboxSync, onInboxSync, trackColor = RelayColors.Background, textSize = 12.5f)
                    Text(
                        inboxHint(state.inboxSync),
                        style = RelayType.Hint,
                        color = if (state.inboxSync == InboxSync.ALL) RelayColors.Amber else RelayColors.Dim,
                    )
                } else {
                    Text("Send-only build", style = RelayType.RowTitle, color = RelayColors.Text)
                    Text(SEND_ONLY_HINT, style = RelayType.Hint, color = RelayColors.Amber)
                }
            }

            SectionLabel("Reliability", Modifier.padding(start = 4.dp, top = 10.dp))
            RelayListCard(radius = 22.dp) {
                state.toggles.forEachIndexed { i, (key, on) ->
                    ListRow(key.label, subtitle = key.sub) {
                        RelaySwitch(on, { onToggle(key) }, contentDescription = key.label)
                    }
                    if (i < state.toggles.lastIndex) Hairline()
                }
            }

            SectionLabel("Device checks", Modifier.padding(start = 4.dp, top = 10.dp))
            RelayListCard(radius = 22.dp) {
                state.checks.forEachIndexed { i, check ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (check.action != null) Modifier.clickable { onCheck(check.action) } else Modifier)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(check.label, style = sans(14f, FontWeight.Medium), color = RelayColors.Text)
                        MonoValue(
                            check.value,
                            when (check.tone) {
                                CheckTone.OK -> RelayColors.Lime
                                CheckTone.WARN -> RelayColors.Amber
                                CheckTone.NEUTRAL -> RelayColors.Muted
                            },
                        )
                    }
                    if (i < state.checks.lastIndex) Hairline()
                }
            }

            SectionLabel("App", Modifier.padding(start = 4.dp, top = 10.dp))
            RelayListCard(radius = 22.dp) {
                ListRow("Version") { MonoValue(appVersion, RelayColors.Muted) }
                Hairline()
                ListRow("Exit $app", subtitle = "Stop the gateway and close the app", onClick = onExit) {
                    MonoValue("Exit", RelayColors.Red)
                }
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(RelayColors.Background)
            .border(1.dp, RelayColors.Line, RoundedCornerShape(12.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StepButton("−", "Fewer retries") { onChange(-1) }
        Text(value.toString(), style = mono(15f, FontWeight.Medium), color = RelayColors.Text, textAlign = TextAlign.Center, modifier = Modifier.width(32.dp))
        StepButton("+", "More retries") { onChange(1) }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(RelayColors.SurfaceRaised).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = sans(18f, FontWeight.Medium), color = RelayColors.Text)
    }
}

@Composable
private fun UnpairSheet(visible: Boolean, workspace: String, serverUrl: String, onDismiss: () -> Unit, onUnpair: () -> Unit) {
    RelaySheet(visible = visible, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(workspace.ifEmpty { "Paired" }, style = RelayType.SheetTitle, color = RelayColors.Text)
            Text(
                "This phone sends for ${workspace.ifEmpty { "a workspace" }} through ${serverUrl.substringAfter("://")}. " +
                    "Unpairing stops it; its projects need another phone until you pair again with a new code from the web.",
                style = RelayType.Body,
                color = RelayColors.Muted,
            )
        }
        SecondaryButton(
            "Unpair this phone",
            onClick = onUnpair,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentColor = RelayColors.Red,
            background = RelayColors.Background,
            borderColor = RelayColors.Red.copy(alpha = 0.35f),
        )
    }
}

private const val SEND_ONLY_HINT =
    "This build of the app can't receive SMS, so replies and STOP don't reach the web inbox. " +
        "To get them, install the two-way build from the download page over this one (the pairing stays)."

private val INBOX_OPTIONS = listOf(InboxSync.REPLIES to "Replies", InboxSync.ALL to "All", InboxSync.OFF to "Off")

private fun inboxHint(mode: InboxSync) = when (mode) {
    InboxSync.REPLIES -> "Only replies from numbers this phone texted in the last 30 days, STOP included. Bank codes and other texts stay on the phone."
    InboxSync.ALL -> "Every SMS this phone receives goes to the web inbox, bank codes and personal texts included. Use it on a phone kept only for the gateway."
    InboxSync.OFF -> "No received SMS leave the phone. Replies and STOP won't reach the web."
}
