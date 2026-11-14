package app.relay.android.feature.pairing

import app.relay.gateway.port.RelayApiException
import app.relay.gateway.service.DeviceSetup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

/** The code and server form, shared by pairing a phone and switching its workspace. */
class PairingFormState(defaultServer: String) {
    private val _state = MutableStateFlow(PairingUi(server = defaultServer))
    val state: StateFlow<PairingUi> = _state.asStateFlow()

    fun onCode(value: String) = _state.update { it.copy(code = formatCode(value), error = null) }

    fun onServer(value: String) = _state.update { it.copy(server = value.trim(), error = null) }

    fun toggleServer() = _state.update { it.copy(editingServer = !it.editingServer) }

    fun onScanFailed(message: String) = _state.update { it.copy(error = message) }

    /** Fills in a scanned QR (server and code). False, with the reason shown, if it isn't a pairing code. */
    fun fill(text: String): Boolean {
        val link = DeviceSetup.parseLink(text)
        if (link == null) {
            _state.update { it.copy(error = "That QR code isn't a pairing code. Open Phones → Pair a phone on the web.") }
            return false
        }
        _state.update { it.copy(server = link.serverUrl, code = formatCode(link.code), error = null) }
        return true
    }

    /**
     * Runs [action] with the form's server and code. A failure is shown on the form and gives null;
     * on success the form stays busy, since the screen goes away.
     */
    suspend fun <T> submit(action: suspend (server: String, code: String) -> T): T? {
        val s = _state.value
        if (s.busy) return null
        _state.update { it.copy(busy = true, error = null) }
        return try {
            action(s.server, s.code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RelayApiException) {
            _state.update { it.copy(busy = false, error = e.message) }
            null
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(busy = false, error = e.message, editingServer = true) }
            null
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = "Can't reach ${s.server.substringAfter("://")}. Check the connection and try again.") }
            null
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
