package app.relay.android.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.relay.gateway.port.SettingsRepository
import app.relay.gateway.service.DeviceSetup
import app.relay.gateway.service.SwitchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SwitchUi(
    /** The workspace the phone sends for now. */
    val workspace: String = "",
    /** Keep sent messages and finished batches on the phone; the queue goes either way. */
    val keepHistory: Boolean = false,
    /** Messages still waiting to send, which switching drops. */
    val unsent: Int = 0,
)

/** Moves a paired phone to another workspace with that workspace's pairing code. */
class SwitchWorkspaceViewModel(
    private val setup: DeviceSetup,
    settings: SettingsRepository,
    defaultServer: String,
    private val deviceName: String,
    private val deviceModel: String,
) : ViewModel() {
    val form = PairingFormState(defaultServer)
    private val keepHistory = MutableStateFlow(false)
    private val _switched = MutableStateFlow<SwitchResult?>(null)

    val state: StateFlow<SwitchUi> = combine(
        setup.identity.filterNotNull().map { it.workspaceName },
        keepHistory,
        setup.unsent,
        ::SwitchUi,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SwitchUi())

    /** Set once the phone has moved; the screen closes. */
    val switched: StateFlow<SwitchResult?> = _switched.asStateFlow()

    init {
        // Typing a code: most workspaces live on the server this phone already uses.
        viewModelScope.launch {
            val current = settings.current().serverUrl
            // Unless a scanned link already filled the form.
            if (current.isNotBlank() && form.state.value.code.isEmpty()) form.onServer(current)
        }
    }

    fun setKeepHistory(keep: Boolean) {
        keepHistory.value = keep
    }

    /** Unlike first pairing, a scan only fills the form: leaving a workspace waits for the user. */
    fun onScanned(text: String) {
        form.fill(text)
    }

    fun onScanFailed(message: String) = form.onScanFailed(message)

    fun submit() {
        if (!form.state.value.canSubmit) return
        viewModelScope.launch {
            form.submit { server, code -> setup.switchTo(server, code, deviceName, deviceModel, keepHistory.value) }
                ?.let { _switched.value = it }
        }
    }
}
