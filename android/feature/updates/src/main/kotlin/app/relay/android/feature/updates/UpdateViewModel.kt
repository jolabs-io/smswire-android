package app.relay.android.feature.updates

import androidx.lifecycle.ViewModel
import app.relay.gateway.service.AppUpdates
import app.relay.gateway.service.UpdateStatus
import app.relay.gateway.service.WhatsNew
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The update banner and sheet, and the What's new sheet. The work runs in [AppUpdates]' own scope. */
class UpdateViewModel(private val updates: AppUpdates) : ViewModel() {
    val status: StateFlow<UpdateStatus?> = updates.status
    val whatsNew: StateFlow<WhatsNew?> = updates.whatsNew

    private val _sheetOpen = MutableStateFlow(false)
    val sheetOpen: StateFlow<Boolean> = _sheetOpen.asStateFlow()

    fun openSheet() {
        if (status.value != null) _sheetOpen.value = true
    }

    fun closeSheet() {
        _sheetOpen.value = false
    }

    /** Install (or try again). The sheet stays open to show the download. */
    fun install() = updates.install()

    fun later() {
        updates.later()
        _sheetOpen.value = false
    }

    fun dismissWhatsNew() = updates.dismissWhatsNew()
}
