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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import app.relay.android.feature.bulk.activeBatches
import app.relay.android.feature.gateway.GatewayRoute
import app.relay.android.feature.gateway.GatewayViewModel
import app.relay.android.feature.messages.MessageDetailSheet
import app.relay.android.feature.messages.MessageDetailViewModel
import app.relay.android.feature.messages.MessagesRoute
import app.relay.android.feature.messages.MessagesViewModel
import app.relay.android.feature.pairing.PairingRoute
import app.relay.android.feature.pairing.PairingViewModel
import app.relay.android.feature.pairing.SwitchWorkspaceRoute
import app.relay.android.feature.pairing.SwitchWorkspaceViewModel
import app.relay.android.feature.projects.ProjectDetailRoute
import app.relay.android.feature.projects.ProjectDetailViewModel
import app.relay.android.feature.projects.ProjectsScreen
import app.relay.android.feature.projects.ProjectsViewModel
import app.relay.android.feature.settings.CheckAction
import app.relay.android.feature.settings.DeviceHealthViewModel
import app.relay.android.feature.settings.DeviceIssuesCard
import app.relay.android.feature.settings.SettingsRoute
import app.relay.android.feature.settings.SettingsViewModel
import app.relay.android.feature.updates.UpdateBanner
import app.relay.android.feature.updates.UpdateSheet
import app.relay.android.feature.updates.UpdateViewModel
import app.relay.android.feature.updates.WhatsNewSheet
import app.relay.android.feature.updates.showsBanner
import app.relay.gateway.service.CheckResult
import app.relay.gateway.service.SwitchResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

/** Pushed screens: they cover the tab bar and go back to the tab that opened them. */
private object Routes {
    const val PROJECTS = "projects"
    const val PROJECT = "project/{id}"
    const val BATCHES = "batches"
    const val SWITCH = "switch"
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
    val setup = container.gateway.deviceSetup
    // Mid-switch the phone is briefly without a pairing, but it isn't unpaired.
    val paired by remember { combine(setup.identity, setup.switching) { identity, switching -> identity != null || switching } }
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
    // Activity-scoped: the update sheet and What's new outlive tab changes.
    val updateVm = viewModel { UpdateViewModel(container.gateway.appUpdates) }
    val whatsNew by updateVm.whatsNew.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(RelayColors.Background)) {
        when (paired) {
            true -> GatewayShell(container, onFix, onExit, scan, updateVm)
            false -> PairingDestination(container, scan)
            null -> Unit
        }
        // Over pairing too: the first launch asks for SMS before the phone is paired.
        RestrictedSmsSheet(restrictedSms, onOpenAppInfo)
        WhatsNewSheet(whatsNew?.versionName, whatsNew?.notes, onDismiss = updateVm::dismissWhatsNew)
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
        PairingRoute(vm, onScan = { scan(vm::onScanned, vm::onScanFailed) }, sendOnly = !BuildConfig.RECEIVES_SMS)
    }
}

/** Three tabs, pushed screens (which hide the tab bar), and the message sheet above everything. */
@Composable
private fun GatewayShell(container: AppContainer, onFix: (CheckAction) -> Unit, onExit: () -> Unit, scan: Scanner, updateVm: UpdateViewModel) {
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

    // A pairing QR opened while paired (from the camera app): offer to move to that workspace.
    val pairingLink by container.pairingLinks.collectAsStateWithLifecycle()
    LaunchedEffect(pairingLink) {
        if (pairingLink != null && nav.currentDestination?.route != Routes.SWITCH) nav.navigate(Routes.SWITCH)
    }

    // Activity-scoped: the sheet floats above whichever screen is showing.
    val detailVm = viewModel { MessageDetailViewModel(g.messageService, g.projectService, g.settings.settings, g.segmentCalculator, toast) }
    val detail by detailVm.state.collectAsStateWithLifecycle()
    // Shell-level so the confirmation covers the tab bar too.
    val exitConfirm = rememberExitConfirm()
    val confirmExit by container.confirmExit.collectAsStateWithLifecycle()
    LaunchedEffect(confirmExit) {
        if (confirmExit) {
            container.confirmExit.value = false
            exitConfirm.ask()
        }
    }
    // App-wide: the Gateway's issues card and the Settings tab's badge show the same checks.
    val healthVm = viewModel { DeviceHealthViewModel(g.settings.settings, container.deviceStatus, container.uiTicker) }
    val issues by healthVm.issues.collectAsStateWithLifecycle()
    val update by updateVm.status.collectAsStateWithLifecycle()
    val updateSheet by updateVm.sheetOpen.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // An update notification was tapped.
    val openUpdate by container.openUpdate.collectAsStateWithLifecycle()
    LaunchedEffect(openUpdate) {
        if (openUpdate) {
            container.openUpdate.value = false
            updateVm.openSheet()
        }
    }
    // Fixes happen in system screens; check again when the app comes back.
    LifecycleResumeEffect(healthVm) {
        healthVm.refresh()
        onPauseOrDispose {}
    }

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
                    val bulkVm = viewModel { BulkViewModel(g.batchService, g.projectService, container.clock::now, container.uiTicker) }
                    val bulk by bulkVm.state.collectAsStateWithLifecycle()
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
                        onOpenMessages = { nav.navigateTab(Tab.MESSAGES) },
                        onOpenMessage = detailVm::open,
                        onOpenProjects = { nav.navigate(Routes.PROJECTS) },
                        onExit = exitConfirm::ask,
                        sendOnly = !BuildConfig.RECEIVES_SMS,
                        onOpenSettings = { nav.navigateTab(Tab.SETTINGS) },
                        notices = {
                            if (issues.isNotEmpty()) item(key = "issues") { DeviceIssuesCard(issues, onFix) }
                            update?.takeIf { it.showsBanner() }?.let { item(key = "update") { UpdateBanner(it, updateVm::openSheet) } }
                        },
                        batches = { activeBatches(bulk.active, bulkVm::toggle) { nav.navigate(Routes.BATCHES) } },
                    )
                }
                composable(Tab.MESSAGES.route) {
                    MessagesRoute(
                        viewModel { MessagesViewModel(g.messageService, g.projectService, container.clock, container.uiTicker) },
                        onOpenMessage = detailVm::open,
                        onOpenBatches = { nav.navigate(Routes.BATCHES) },
                    )
                }
                composable(Tab.SETTINGS.route) {
                    SettingsRoute(
                        viewModel { SettingsViewModel(g.settings, g.deviceSetup, g.sims, container.deviceStatus, toast, g.projectService, container.uiTicker) },
                        onFix = onFix,
                        onCopy = copy,
                        onOpenProjects = { nav.navigate(Routes.PROJECTS) },
                        onOpenSwitch = { nav.navigate(Routes.SWITCH) },
                        updateVersion = update?.update?.versionName,
                        onOpenUpdate = updateVm::openSheet,
                        onCheckUpdates = {
                            scope.launch {
                                when (g.appUpdates.check()) {
                                    CheckResult.UPDATE -> updateVm.openSheet()
                                    CheckResult.UP_TO_DATE -> toast.show("You have the newest version")
                                    CheckResult.OFFLINE -> toast.show("Can't reach the server; try again")
                                }
                            }
                        },
                        onExit = exitConfirm::ask,
                        appVersion = BuildConfig.VERSION_NAME,
                    )
                }
                composable(Routes.SWITCH) {
                    val switchVm = viewModel {
                        SwitchWorkspaceViewModel(g.deviceSetup, g.settings, container.defaultServerUrl, container.deviceName, container.deviceModel)
                    }
                    LaunchedEffect(pairingLink) {
                        pairingLink?.let {
                            container.pairingLinks.value = null
                            switchVm.onScanned(it)
                        }
                    }
                    SwitchWorkspaceRoute(
                        switchVm,
                        onScan = { scan(switchVm::onScanned, switchVm::onScanFailed) },
                        onBack = { nav.popBackStack() },
                        onSwitched = { result ->
                            // Not saved: the Settings tab opens fresh, not on this screen.
                            nav.popBackStack(Tab.GATEWAY.route, inclusive = false)
                            toast.show(switchedMessage(result))
                        },
                    )
                }
                composable(Routes.BATCHES) {
                    BulkRoute(
                        viewModel { BulkViewModel(g.batchService, g.projectService, container.clock::now, container.uiTicker) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Routes.PROJECTS) {
                    val projectsVm = viewModel { ProjectsViewModel(g.projectService) }
                    val rows by projectsVm.rows.collectAsStateWithLifecycle()
                    ProjectsScreen(rows, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.project(it)) })
                }
                composable(Routes.PROJECT) { backStack ->
                    val id = checkNotNull(backStack.arguments?.getString("id"))
                    ProjectDetailRoute(
                        viewModel { ProjectDetailViewModel(id, g.projectService, g.settings, g.sims, container.toast, container.clock::now) },
                        onBack = { nav.popBackStack() },
                        onCopy = copy,
                    )
                }
            }
            if (Tab.entries.any { it.route == route }) {
                val settingsDot = issues.isNotEmpty() || update.showsBanner()
                RelayBottomBar(route, badges = if (settingsDot) setOf(Tab.SETTINGS) else emptySet()) { nav.navigateTab(it) }
            }
        }

        MessageDetailSheet(detail, onDismiss = detailVm::close, onCopyId = { copy("Message ID", it) }, onRetry = detailVm::retry)
        BackHandler(enabled = detail != null, onBack = detailVm::close)
        UpdateSheet(update, updateSheet, onInstall = updateVm::install, onLater = updateVm::later, onDismiss = updateVm::closeSheet)
        BackHandler(enabled = updateSheet, onBack = updateVm::closeSheet)
        ExitSheet(exitConfirm, onExit)
    }
}

private fun switchedMessage(result: SwitchResult) = when (result) {
    is SwitchResult.SameWorkspace -> "Already in ${result.workspaceName}: paired again"
    is SwitchResult.Switched ->
        if (result.leftOld) {
            "Now sending for ${result.workspaceName}"
        } else {
            "Now sending for ${result.workspaceName}. Couldn't reach the old workspace: remove this phone there on the web."
        }
}

private fun NavHostController.navigateTab(tab: Tab) = navigate(tab.route) {
    popUpTo(Tab.GATEWAY.route) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
