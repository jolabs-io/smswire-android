package app.relay.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.relay.android.AppContainer
import app.relay.android.BuildConfig
import app.relay.android.designsystem.component.ToastHost
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.feature.bulk.BulkRoute
import app.relay.android.feature.bulk.BulkViewModel
import app.relay.android.feature.gateway.GatewayRoute
import app.relay.android.feature.gateway.GatewayViewModel
import app.relay.android.feature.messages.MessageDetailSheet
import app.relay.android.feature.messages.MessageDetailViewModel
import app.relay.android.feature.messages.MessagesRoute
import app.relay.android.feature.messages.MessagesViewModel
import app.relay.android.feature.pairing.PairingRoute
import app.relay.android.feature.pairing.PairingViewModel
import app.relay.android.feature.projects.ProjectDetailRoute
import app.relay.android.feature.projects.ProjectDetailViewModel
import app.relay.android.feature.projects.ProjectsScreen
import app.relay.android.feature.projects.ProjectsViewModel
import app.relay.android.feature.settings.CheckAction
import app.relay.android.feature.settings.SettingsRoute
import app.relay.android.feature.settings.SettingsViewModel
import kotlinx.coroutines.flow.map

private object Routes {
    const val SETTINGS = "settings"
    const val PROJECT = "project/{id}"
    fun project(id: String) = "project/$id"
}

/** Opens the QR scanner: the first callback gets the scanned text, the second an error to show. */
typealias Scanner = (onResult: (String) -> Unit, onError: (String) -> Unit) -> Unit

/**
 * App root: the pairing screen until the phone joins a workspace (and again after it is
 * unpaired or revoked on the web), then the gateway with its tabs. [onPaired] runs when the
 * phone pairs while the app is open, not on a launch that's already paired.
 */
@Composable
fun RelayApp(
    container: AppContainer,
    onFix: (CheckAction) -> Unit,
    onExit: () -> Unit,
    scan: Scanner,
    restrictedSms: RestrictedSmsState,
    onOpenAppInfo: () -> Unit,
    onPaired: () -> Unit,
) {
    val paired by remember { container.gateway.deviceSetup.identity.map { it != null } }
        .collectAsStateWithLifecycle(initialValue = null)
    var sawPairing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(paired) {
        when (paired) {
            false -> sawPairing = true
            true -> if (sawPairing) {
                sawPairing = false
                onPaired()
            }
            null -> Unit
        }
    }
    val toastMessage by container.toast.current.collectAsStateWithLifecycle()
    val pairingLink by container.pairingLinks.collectAsStateWithLifecycle()
    LaunchedEffect(paired, pairingLink) {
        if (paired == true && pairingLink != null) {
            container.pairingLinks.value = null
            container.toast.show("This phone is already paired. Unpair it in Settings to join another workspace.")
        }
    }

    Box(Modifier.fillMaxSize().background(RelayColors.Background)) {
        when (paired) {
            true -> GatewayShell(container, onFix, onExit)
            false -> PairingDestination(container, scan)
            null -> Unit
        }
        // Over pairing too: the first launch asks for SMS before the phone is paired.
        RestrictedSmsSheet(restrictedSms, onOpenAppInfo)
        ToastHost(
            toastMessage,
            onExpired = container.toast::dismiss,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 100.dp),
        )
    }
}

@Composable
private fun PairingDestination(container: AppContainer, scan: Scanner) {
    val g = container.gateway
    val vm = viewModel { PairingViewModel(g.deviceSetup, container.defaultServerUrl, container.deviceName, container.deviceModel) }
    // Opened from the camera app: same as scanning in the app, server and code come from the link.
    val link by container.pairingLinks.collectAsStateWithLifecycle()
    LaunchedEffect(link) {
        link?.let {
            container.pairingLinks.value = null
            vm.onScanned(it)
        }
    }
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        PairingRoute(vm, onScan = { scan(vm::onScanned, vm::onScanFailed) })
    }
}

/** Four tabs, pushed detail screens (which hide the tab bar), and the message sheet above everything. */
@Composable
private fun GatewayShell(container: AppContainer, onFix: (CheckAction) -> Unit, onExit: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val g = container.gateway
    val toast = container.toast
    val clipboard = LocalClipboardManager.current
    val copy: (String, String) -> Unit = { label, value ->
        clipboard.setText(AnnotatedString(value))
        toast.show("$label copied")
    }

    // Activity-scoped: the sheet floats above whichever screen is showing.
    val detailVm = viewModel { MessageDetailViewModel(g.messageService, g.projectService, g.settings.settings, g.segmentCalculator, toast) }
    val detail by detailVm.state.collectAsStateWithLifecycle()
    // Shell-level so the confirmation covers the tab bar too.
    val exitConfirm = rememberExitConfirm()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            NavHost(
                navController = nav,
                startDestination = Tab.GATEWAY.route,
                modifier = Modifier.weight(1f),
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) },
            ) {
                composable(Tab.GATEWAY.route) {
                    GatewayRoute(
                        viewModel = viewModel {
                            GatewayViewModel(
                                controller = g.controller,
                                stats = g.statsService,
                                messages = g.messageService,
                                projects = g.projectService,
                                settings = g.settings.settings,
                                power = g.power,
                                sims = g.sims,
                                deviceSetup = g.deviceSetup,
                                clock = container.clock,
                                ticker = container.uiTicker,
                            )
                        },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        onOpenMessages = { nav.navigateTab(Tab.MESSAGES) },
                        onOpenMessage = detailVm::open,
                        onExit = exitConfirm::ask,
                    )
                }
                composable(Tab.MESSAGES.route) {
                    MessagesRoute(
                        viewModel { MessagesViewModel(g.messageService, g.projectService, container.clock, container.uiTicker) },
                        onOpenMessage = detailVm::open,
                    )
                }
                composable(Tab.BULK.route) {
                    BulkRoute(viewModel { BulkViewModel(g.batchService, g.projectService, container.clock::now, container.uiTicker) })
                }
                composable(Tab.PROJECTS.route) {
                    val projectsVm = viewModel { ProjectsViewModel(g.projectService) }
                    val rows by projectsVm.rows.collectAsStateWithLifecycle()
                    ProjectsScreen(rows, onOpen = { nav.navigate(Routes.project(it)) })
                }
                composable(Routes.PROJECT) { backStack ->
                    val id = checkNotNull(backStack.arguments?.getString("id"))
                    ProjectDetailRoute(
                        viewModel { ProjectDetailViewModel(id, g.projectService, g.sims, container.clock::now) },
                        onBack = { nav.popBackStack() },
                        onCopy = copy,
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsRoute(
                        viewModel { SettingsViewModel(g.settings, g.deviceSetup, g.sims, container.deviceStatus, toast, container.uiTicker) },
                        onBack = { nav.popBackStack() },
                        onFix = onFix,
                        onCopy = copy,
                        onExit = exitConfirm::ask,
                        appVersion = BuildConfig.VERSION_NAME,
                    )
                }
            }
            if (Tab.entries.any { it.route == route }) {
                RelayBottomBar(route) { nav.navigateTab(it) }
            }
        }

        MessageDetailSheet(detail, onDismiss = detailVm::close, onCopyId = { copy("Message ID", it) }, onRetry = detailVm::retry)
        BackHandler(enabled = detail != null, onBack = detailVm::close)
        ExitSheet(exitConfirm, onExit)
    }
}

private fun NavHostController.navigateTab(tab: Tab) = navigate(tab.route) {
    popUpTo(Tab.GATEWAY.route) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
