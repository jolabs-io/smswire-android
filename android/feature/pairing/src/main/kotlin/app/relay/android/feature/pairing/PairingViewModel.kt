package app.relay.android.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.relay.gateway.port.RelayApiException
import app.relay.gateway.service.DeviceSetup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PairingUi(
    val server: String,
    val code: String = "",
    /** The server field is tucked away: the QR carries it, and most people use one server. */
    val editingServer: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = code.count(Char::isLetterOrDigit) >= 8 && server.isNotBlank() && !busy
}

/** Joins this phone to a workspace with a code (typed, or scanned from the web's QR). */
class PairingViewModel(
    private val setup: DeviceSetup,
    defaultServer: String,
    private val deviceName: String,
    private val deviceModel: String,
) : ViewModel() {
    private val _state = MutableStateFlow(PairingUi(server = defaultServer))
    val state: StateFlow<PairingUi> = _state.asStateFlow()

    fun onCode(value: String) = _state.update { it.copy(code = formatCode(value), error = null) }

    fun onServer(value: String) = _state.update { it.copy(server = value.trim(), error = null) }

    fun toggleServer() = _state.update { it.copy(editingServer = !it.editingServer) }

    /** A scanned QR carries both the server and the code, so pairing starts right away. */
    fun onScanned(text: String) {
        val link = DeviceSetup.parseLink(text)
        if (link == null) {
            _state.update { it.copy(error = "That QR code isn't a pairing code. Open Phones → Pair a phone on the web.") }
            return
        }
        _state.update { it.copy(server = link.serverUrl, code = formatCode(link.code), error = null) }
        submit()
    }

    fun onScanFailed(message: String) = _state.update { it.copy(error = message) }

    fun submit() {
        val s = _state.value
        if (s.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                // Success needs no UI here: the app swaps this screen out once paired.
                setup.pair(s.server, s.code, deviceName, deviceModel)
            } catch (e: RelayApiException) {
                _state.update { it.copy(busy = false, error = e.message) }
            } catch (e: IllegalArgumentException) {
                _state.update { it.copy(busy = false, error = e.message, editingServer = true) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "Can't reach ${s.server.substringAfter("://")}. Check the connection and try again.") }
            }
        }
    }

    private companion object {
        /** "ktqzw8zj" → "KTQZ-W8ZJ", the way the web shows it. */
        fun formatCode(raw: String): String {
            val clean = raw.uppercase().filter(Char::isLetterOrDigit).take(8)
            return if (clean.length > 4) clean.take(4) + "-" + clean.drop(4) else clean
        }
    }
}
