package app.relay.android.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.relay.android.designsystem.format.Format
import app.relay.gateway.service.ProjectService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectRowUi(
    val id: String,
    val name: String,
    val initials: String,
    val slug: String,
    val enabled: Boolean,
    val today: String,
)

/** The projects this phone sends for. They're created and edited on the web dashboard. */
class ProjectsViewModel(private val projects: ProjectService) : ViewModel() {
    val rows: StateFlow<List<ProjectRowUi>> = projects.observeAll().map { list ->
        list.map {
            ProjectRowUi(
                id = it.project.id,
                name = it.project.name,
                initials = Format.initials(it.project.name),
                slug = it.project.slug,
                enabled = it.project.enabled,
                today = Format.count(it.activity.today),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { runCatching { projects.refresh() } }
    }
}
