package app.relay.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.relay.gateway.model.GatewaySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** App-wide phone health: the checks that need fixing, for the Gateway's card and the Settings tab badge. */
class DeviceHealthViewModel(
    settings: Flow<GatewaySettings>,
    private val device: DeviceStatusProvider,
    ticker: Flow<Unit>,
) : ViewModel() {
    /** Bumped when the app resumes: fixes happen in system screens. */
    private val refreshes = MutableStateFlow(0)

    val issues: StateFlow<List<DeviceCheckUi>> = combine(settings, ticker, refreshes) { s, _, _ ->
        deviceChecks(device, s).needingAttention()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        refreshes.value++
    }
}
