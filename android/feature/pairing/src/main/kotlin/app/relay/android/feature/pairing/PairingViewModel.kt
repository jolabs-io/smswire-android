package app.relay.android.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.relay.gateway.service.DeviceSetup
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Joins this phone to a workspace with a code (typed, or scanned from the web's QR). */
class PairingViewModel(
    private val setup: DeviceSetup,
    defaultServer: String,
    private val deviceName: String,
    private val deviceModel: String,
) : ViewModel() {
    val form = PairingFormState(defaultServer)
    val state: StateFlow<PairingUi> = form.state

    /** A scanned QR carries both the server and the code, so pairing starts right away. */
    fun onScanned(text: String) {
        if (form.fill(text)) submit()
    }

    fun onScanFailed(message: String) = form.onScanFailed(message)

    fun submit() {
        // Success needs no UI here: the app swaps this screen out once paired.
        viewModelScope.launch { form.submit { server, code -> setup.pair(server, code, deviceName, deviceModel) } }
    }
}
