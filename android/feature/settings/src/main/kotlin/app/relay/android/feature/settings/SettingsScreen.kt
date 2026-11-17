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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.relay.android.designsystem.component.Hairline
import app.relay.android.designsystem.component.LeaveWorkspaceOptions
import app.relay.android.designsystem.component.ListRow
import app.relay.android.designsystem.component.MonoValue
import app.relay.android.designsystem.component.RateSlider
import app.relay.android.designsystem.component.RelayCard
import app.relay.android.designsystem.component.RelayListCard
import app.relay.android.designsystem.component.RelaySheet
import app.relay.android.designsystem.component.RelaySwitch
import app.relay.android.designsystem.component.ScreenHeader
import app.relay.android.designsystem.component.SecondaryButton
import app.relay.android.designsystem.component.SectionLabel
import app.relay.android.designsystem.component.SegmentedControl
import app.relay.android.designsystem.component.sendOnlyHint
import app.relay.android.designsystem.icon.RelayIcons
import app.relay.android.designsystem.theme.LocalAppName
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.android.designsystem.theme.mono
import app.relay.android.designsystem.theme.sans
import app.relay.gateway.model.InboxSync
import app.relay.gateway.model.UpdateMode

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onFix: (CheckAction) -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    onOpenProjects: () -> Unit,
    /** Opens Switch workspace: join another workspace with its pairing code. */
    onOpenSwitch: () -> Unit,
    /** A newer version the phone can update to; null when it's up to date. */
    updateVersion: String?,
    /** Opens the update sheet. */
    onOpenUpdate: () -> Unit,
    /** Asks the server for a newer version now. */
    onCheckUpdates: () -> Unit,
    /** Asks to stop the gateway and close the app. */
    onExit: () -> Unit,
    appVersion: String,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unpair by viewModel.unpair.collectAsStateWithLifecycle()
    val smsLimitOpen by viewModel.smsLimitOpen.collectAsStateWithLifecycle()
    // Fixes happen in system screens (battery, default SMS app); re-check on return.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    Box(Modifier.fillMaxSize()) {
        SettingsScreen(
            state,
            onSim = viewModel::setSim,
            onRate = viewModel::setRate,
            onRetries = viewModel::changeRetries,
            onInboxSync = viewModel::setInboxSync,
            onUpdateMode = viewModel::setUpdateMode,
            updateVersion = updateVersion,
            onOpenUpdate = onOpenUpdate,
            onCheckUpdates = onCheckUpdates,
            onToggle = viewModel::toggle,
            onCheck = {
                when (it) {
                    CheckAction.SMS_LIMIT -> viewModel.openSmsLimit()
                    else -> onFix(it)
                }
            },
            onOpenProjects = onOpenProjects,
            onOpenSwitch = onOpenSwitch,
            onUnpair = viewModel::openUnpair,
            onExit = onExit,
            appVersion = appVersion,
        )
        UnpairSheet(
            unpair,
            state.workspace,
            state.serverHost,
            onDismiss = viewModel::dismissUnpair,
            onKeepHistory = viewModel::setKeepHistory,
            onUnpair = viewModel::unpair,
            onForget = viewModel::forgetAnyway,
        )
        SmsLimitSheet(
            visible = smsLimitOpen,
            limit = state.smsLimit,
            onDismiss = viewModel::dismissSmsLimit,
            onDefaultApp = { onFix(CheckAction.DEFAULT_SMS_APP) },
            onCopyCommand = { onCopy("Commands", state.smsLimit.adbCommand) },
        )
    }
}

/**
 * The Settings tab. Phone-only things come first (device checks, then the inbox); sending and
 * reliability are also editable on the web dashboard and sync both ways.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSim: (app.relay.gateway.model.SimPreference) -> Unit,
    onRate: (Int) -> Unit,
    onRetries: (Int) -> Unit,
    onInboxSync: (InboxSync) -> Unit,
    onUpdateMode: (UpdateMode) -> Unit,
    updateVersion: String?,
    onOpenUpdate: () -> Unit,
    onCheckUpdates: () -> Unit,
    onToggle: (ToggleKey) -> Unit,
    onCheck: (CheckAction) -> Unit,
    onOpenProjects: () -> Unit,
    onOpenSwitch: () -> Unit,
    onUnpair: () -> Unit,
    onExit: () -> Unit,
    appVersion: String,
) {
    val app = LocalAppName.current
    Column(
        Modifier
            .fillMaxSize()
            .background(RelayColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Settings")

        SectionLabel("Device checks", Modifier.padding(start = 4.dp, top = 4.dp))
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

        SyncedSectionLabel("Sending")
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
                    Text("Android limits this phone to $cap SMS parts a minute; $app holds to that. See Device checks.", style = RelayType.Hint, color = RelayColors.Amber)
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
                if (state.inboxSync != InboxSync.OFF && state.receiving.isNotEmpty()) ReceivingList(state.receiving)
            } else {
                Text("Send-only build", style = RelayType.RowTitle, color = RelayColors.Text)
                Text(sendOnlyHint(LocalAppName.current), style = RelayType.Hint, color = RelayColors.Amber)
            }
        }

        SyncedSectionLabel("Reliability")
        RelayListCard(radius = 22.dp) {
            state.toggles.forEachIndexed { i, (key, on) ->
                ListRow(key.label, subtitle = key.sub) {
                    RelaySwitch(on, { onToggle(key) }, contentDescription = key.label)
                }
                if (i < state.toggles.lastIndex) Hairline()
            }
        }

        SectionLabel("Workspace", Modifier.padding(start = 4.dp, top = 10.dp))
        RelayListCard(radius = 22.dp) {
            ListRow(
                state.workspace.ifEmpty { state.serverHost },
                subtitle = if (state.workspace.isEmpty()) "Paired" else "Paired through ${state.serverHost}",
            ) {}
            Hairline()
            ListRow("Projects", subtitle = "The ones this phone sends for", onClick = onOpenProjects) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MonoValue(state.projectCount.toString(), RelayColors.Muted)
                    Icon(RelayIcons.ChevronRight, contentDescription = null, tint = RelayColors.Dim, modifier = Modifier.size(16.dp))
                }
            }
            Hairline()
            ListRow("Switch workspace", subtitle = "Join another workspace with its pairing code", onClick = onOpenSwitch) {
                Icon(RelayIcons.ChevronRight, contentDescription = null, tint = RelayColors.Dim, modifier = Modifier.size(16.dp))
            }
            Hairline()
            ListRow("Unpair this phone", subtitle = "Stop sending for this workspace", onClick = onUnpair) {
                MonoValue("Unpair", RelayColors.Red)
            }
        }

        SyncedSectionLabel("Updates")
        RelayCard(radius = 22.dp, padding = 16.dp, spacing = 10.dp) {
            Text("Install new versions", style = RelayType.RowTitle, color = RelayColors.Text)
            SegmentedControl(UPDATE_OPTIONS, state.updateMode, onUpdateMode, trackColor = RelayColors.Background, textSize = 12.5f)
            Text(
                when (state.updateMode) {
                    UpdateMode.ASK -> "A notification says when one is out; you choose when it installs."
                    UpdateMode.AUTO -> "Downloads and installs once nothing is waiting to send. The gateway pauses for a moment and comes back."
                },
                style = RelayType.Hint,
                color = RelayColors.Dim,
            )
        }

        SectionLabel("App", Modifier.padding(start = 4.dp, top = 10.dp))
        RelayListCard(radius = 22.dp) {
            if (updateVersion != null) {
                ListRow("Version $appVersion", subtitle = "$updateVersion is available", onClick = onOpenUpdate) {
                    MonoValue("Update", RelayColors.Amber)
                }
            } else {
                ListRow("Version") { MonoValue(appVersion, RelayColors.Muted) }
            }
            Hairline()
            ListRow("Check for updates", onClick = onCheckUpdates) {
                Icon(RelayIcons.ChevronRight, contentDescription = null, tint = RelayColors.Dim, modifier = Modifier.size(16.dp))
            }
            Hairline()
            ListRow("Exit $app", subtitle = "Stop the gateway and close the app", onClick = onExit) {
                MonoValue("Exit", RelayColors.Red)
            }
        }
    }
}

/** Label of a section the web dashboard can change too. */
@Composable
private fun SyncedSectionLabel(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(text)
        Text("Synced with web", style = sans(11.5f), color = RelayColors.Dim)
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

/**
 * Asks before unpairing (with what happens to the phone's records), then, if the server can't be
 * reached, offers to try again or to leave without telling it.
 */
@Composable
private fun UnpairSheet(
    ui: UnpairUi,
    workspace: String,
    serverHost: String,
    onDismiss: () -> Unit,
    onKeepHistory: (Boolean) -> Unit,
    onUnpair: () -> Unit,
    onForget: () -> Unit,
) {
    val name = workspace.ifEmpty { "this workspace" }
    val busy = ui.step == UnpairStep.BUSY
    RelaySheet(visible = ui.step != UnpairStep.CLOSED, onDismiss = onDismiss) {
        if (ui.step == UnpairStep.OFFLINE) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Can't reach $serverHost", style = RelayType.SheetTitle, color = RelayColors.Text)
                Text(
                    "The server needs to hear that this phone is leaving, so $name stops sending through it. Check the connection and try again.",
                    style = RelayType.Body,
                    color = RelayColors.Muted,
                )
            }
            SecondaryButton("Try again", onClick = onUnpair, modifier = Modifier.fillMaxWidth().height(48.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DangerButton("Forget anyway", onForget)
                Text(
                    "$name keeps listing this phone, and its projects keep waiting for it, until someone removes it on the web (Phones).",
                    style = RelayType.Hint,
                    color = RelayColors.Dim,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(workspace.ifEmpty { "Paired" }, style = RelayType.SheetTitle, color = RelayColors.Text)
                Text(
                    "This phone sends for $name through $serverHost. Unpairing stops it; its projects need another phone. " +
                        "To send for another workspace instead, use Switch workspace.",
                    style = RelayType.Body,
                    color = RelayColors.Muted,
                )
            }
            LeaveWorkspaceOptions(ui.keepHistory, onKeepHistory, ui.unsent, workspace)
            DangerButton(if (busy) "Unpairing…" else "Unpair this phone", { if (!busy) onUnpair() })
        }
    }
}

@Composable
private fun DangerButton(text: String, onClick: () -> Unit) {
    SecondaryButton(
        text,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentColor = RelayColors.Red,
        background = RelayColors.Background,
        borderColor = RelayColors.Red.copy(alpha = 0.35f),
    )
}

private val UPDATE_OPTIONS = listOf(UpdateMode.ASK to "Ask me", UpdateMode.AUTO to "Automatic")

private val INBOX_OPTIONS = listOf(InboxSync.REPLIES to "Rules", InboxSync.ALL to "All", InboxSync.OFF to "Off")

private fun inboxHint(mode: InboxSync) = when (mode) {
    InboxSync.REPLIES -> "What each project takes, as set on the web: replies to it (STOP included), the senders it lists, and join keywords. Everything else stays on the phone, even for a project set to every SMS."
    InboxSync.ALL -> "Projects set to every SMS on the web get every SMS on their SIM, bank codes and personal texts included. Use it on a phone kept only for the gateway."
    InboxSync.OFF -> "No received SMS leave the phone. Replies and STOP won't reach the web."
}

/** Each project with what it receives; set per project on the web. */
@Composable
private fun ReceivingList(receiving: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        receiving.forEach { (project, takes) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(project, style = RelayType.Hint, color = RelayColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(takes, style = RelayType.Hint, color = RelayColors.Text, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
            }
        }
        Text("Set per project on the web dashboard.", style = RelayType.Hint, color = RelayColors.Dim)
    }
}
