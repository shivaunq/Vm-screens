package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.FleetDatabase
import com.example.data.network.VmGatewayClient
import com.example.data.prefs.GatewayPreferences
import com.example.data.repository.FirestoreFleetDataSource
import com.example.data.repository.FleetRepository
import com.example.data.repository.LocalRoomFleetDataSource
import com.example.ui.auth.signOutAdmin
import com.example.ui.components.RemoteScreenDialog
import com.example.ui.components.StatusPill
import com.example.ui.components.VmDetailDialog
import com.example.ui.screens.AppsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.HostAndLogsScreen
import com.example.ui.screens.UrlDispatchScreen
import com.example.ui.theme.LocalFleetColors
import com.example.ui.theme.VmMasterTheme
import com.example.ui.viewmodel.FleetUiState
import com.example.ui.viewmodel.FleetViewModel
import com.example.util.DeviceInstalledAppsHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class FleetTab(val labelRes: Int, val icon: ImageVector) {
    DASHBOARD(R.string.nav_dashboard, Icons.Default.Dashboard),
    DEVICES(R.string.nav_devices, Icons.Default.Devices),
    URLS(R.string.nav_urls, Icons.Default.Link),
    APPS(R.string.nav_apps, Icons.Default.Apps),
    HOST_LOGS(R.string.nav_logs, Icons.Default.Dns)
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VmMasterTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val sessionKey = currentUser?.uid ?: "local_operator"
    key(sessionKey) {
        val viewModel: FleetViewModel = viewModel(
            key = sessionKey,
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY])
                    val gatewayClient = VmGatewayClient()
                    val user = currentUser
                    val dataSource = if (user != null) {
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        FirestoreFleetDataSource(
                            repo = FleetRepository(db, gatewayClient, auth),
                            userId = user.uid,
                            userEmail = user.email.orEmpty(),
                            userName = user.displayName.orEmpty()
                        )
                    } else {
                        val dao = FleetDatabase.getInstance(app).fleetDao()
                        LocalRoomFleetDataSource(dao, gatewayClient)
                    }
                    FleetViewModel(
                        dataSource = dataSource,
                        gatewayPreferences = GatewayPreferences(app),
                        gatewayClient = gatewayClient,
                        isCloudMode = user != null,
                        currentUserEmail = user?.email ?: "Local Operator"
                    )
                }
            }
        )
        MainFleetShell(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFleetShell(viewModel: FleetViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val controls by viewModel.controls.collectAsStateWithLifecycle()
    val bearerToken by viewModel.gatewayToken.collectAsStateWithLifecycle()
    val savedPhoneApps by viewModel.savedPhoneApps.collectAsStateWithLifecycle()
    val preferredTargetPackage by viewModel.preferredTargetPackage.collectAsStateWithLifecycle()
    var activeTab by rememberSaveable { mutableStateOf(FleetTab.DASHBOARD) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    BackHandler(enabled = activeTab != FleetTab.DASHBOARD) {
        activeTab = FleetTab.DASHBOARD
    }

    LaunchedEffect(Unit) {
        val installed = DeviceInstalledAppsHelper.loadLaunchableApps(context)
        viewModel.autoPopulatePhoneAppsIfEmpty(installed)
    }

    LaunchedEffect(controls.bannerMessage) {
        val msg = controls.bannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_vm_master_foreground_1794554591477),
                                contentDescription = stringResource(R.string.cd_app_emblem),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            )
                            Column {
                                Text(
                                    text = "100X VM Master",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(activeTab.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        val ready = uiState as? FleetUiState.Ready
                        val onlineCount = ready?.vms?.count {
                            it.status == "ONLINE" || it.status == "RUNNING"
                        } ?: 0
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            StatusPill(
                                label = if (viewModel.isCloudMode) "Cloud" else "Local",
                                color = if (viewModel.isCloudMode) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary
                            )
                            StatusPill(
                                label = "$onlineCount/100 Live",
                                color = if (onlineCount > 0) LocalFleetColors.current.online
                                else LocalFleetColors.current.offline
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        FleetTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes), maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        FleetTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 1100.dp)
                            .fillMaxSize()
                    ) {
                        if (controls.isBusy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        when (val current = uiState) {
                            is FleetUiState.Loading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }

                            is FleetUiState.Error -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = current.message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }

                            is FleetUiState.Ready -> {
                                when (activeTab) {
                                    FleetTab.DASHBOARD -> DashboardScreen(
                                        state = current,
                                        savedPhoneApps = savedPhoneApps,
                                        preferredTargetPackage = preferredTargetPackage,
                                        onTargetPackageSelected = viewModel::setPreferredTargetPackage,
                                        isBusy = controls.isBusy,
                                        onInitialize100Slots = viewModel::initializeHundredSlots,
                                        onBulkLifecycleAll = { cmd -> viewModel.dispatchLifecycleBulk(cmd, true) },
                                        onOpenUrlAll = { url, pkg -> viewModel.dispatchUrl(url, pkg, true) },
                                        onNavigateToDevices = { activeTab = FleetTab.DEVICES },
                                        onNavigateToLogs = { activeTab = FleetTab.HOST_LOGS }
                                    )

                                    FleetTab.DEVICES -> DevicesScreen(
                                        state = current,
                                        controls = controls,
                                        bearerToken = bearerToken,
                                        savedPhoneApps = savedPhoneApps,
                                        preferredTargetPackage = preferredTargetPackage,
                                        onTargetPackageSelected = viewModel::setPreferredTargetPackage,
                                        onOpenUrlBroadcast = viewModel::dispatchUrl,
                                        onBulkLifecycleAll = { cmd -> viewModel.dispatchLifecycleBulk(cmd, true) },
                                        onSearchChanged = viewModel::updateSearchQuery,
                                        onStatusFilterChanged = viewModel::updateStatusFilter,
                                        onGroupFilterChanged = viewModel::updateGroupFilter,
                                        onViewModeChanged = viewModel::setViewMode,
                                        onToggleSelect = viewModel::toggleVmSelection,
                                        onSelectAll = viewModel::selectVms,
                                        onClearSelection = viewModel::clearSelection,
                                        onOpenDetail = viewModel::openVmDetail,
                                        onOpenRemoteViewer = viewModel::openRemoteViewer,
                                        onCreateGroup = viewModel::createGroup,
                                        onAssignGroupToSelected = viewModel::assignGroupToSelected,
                                        onBulkLifecycleSelected = { cmd -> viewModel.dispatchLifecycleBulk(cmd, false) },
                                        onLifecycleCommand = viewModel::dispatchLifecycle,
                                        onInitialize100Slots = viewModel::initializeHundredSlots
                                    )

                                    FleetTab.URLS -> UrlDispatchScreen(
                                        state = current,
                                        controls = controls,
                                        savedPhoneApps = savedPhoneApps,
                                        preferredTargetPackage = preferredTargetPackage,
                                        onTargetPackageSelected = viewModel::setPreferredTargetPackage,
                                        isBusy = controls.isBusy,
                                        onOpenSelected = { url, pkg -> viewModel.dispatchUrl(url, pkg, false) },
                                        onOpenAll = { url, pkg -> viewModel.dispatchUrl(url, pkg, true) },
                                        onNavigateToDevices = { activeTab = FleetTab.DEVICES }
                                    )

                                    FleetTab.APPS -> AppsScreen(
                                        state = current,
                                        controls = controls,
                                        savedPhoneApps = savedPhoneApps,
                                        preferredTargetPackage = preferredTargetPackage,
                                        isBusy = controls.isBusy,
                                        onAssignGmailBatch = viewModel::assignGmailBatch,
                                        onOpenGoogleSignIn = viewModel::openGoogleSignInOnVms,
                                        onResetAppSessionForSignup = viewModel::clearAppSessionForSignup,
                                        onAddPhoneApps = viewModel::addPhoneApps,
                                        onRemovePhoneApp = viewModel::removePhoneApp,
                                        onLaunchOneApp = viewModel::launchOneAppOnVms,
                                        onOpenPlayStore = viewModel::openInPlayStore,
                                        onPushPhoneApp = viewModel::pushPhoneAppToVms,
                                        onDispatchApp = viewModel::dispatchAppOperation,
                                        onNavigateToDevices = { activeTab = FleetTab.DEVICES }
                                    )

                                    FleetTab.HOST_LOGS -> HostAndLogsScreen(
                                        state = current,
                                        controls = controls,
                                        savedBearerToken = bearerToken,
                                        isCloudMode = viewModel.isCloudMode,
                                        userEmail = viewModel.currentUserEmail,
                                        isBusy = controls.isBusy,
                                        onSaveGatewayConfig = viewModel::saveGatewayConfig,
                                        onCheckGatewayHealth = viewModel::checkGatewayHealth,
                                        onAuthError = viewModel::showMessage,
                                        onSignOut = {
                                            signOutAdmin(credentialManager, scope)
                                        }
                                    )
                                }

                                controls.activeVmDetailId?.let { detailId ->
                                    current.vms.firstOrNull { it.vmId == detailId }?.let { vm ->
                                        VmDetailDialog(
                                            vm = vm,
                                            groups = current.groups,
                                            onDismiss = { viewModel.openVmDetail(null) },
                                            onSaveSlot = viewModel::saveVmSlot,
                                            onLifecycleCommand = { cmd -> viewModel.dispatchLifecycle(vm, cmd) },
                                            onOpenRemoteScreen = {
                                                viewModel.openVmDetail(null)
                                                viewModel.openRemoteViewer(vm.vmId)
                                            }
                                        )
                                    }
                                }

                                controls.remoteViewerVmId?.let { viewerId ->
                                    current.vms.firstOrNull { it.vmId == viewerId }?.let { vm ->
                                        RemoteScreenDialog(
                                            vm = vm,
                                            webrtcGatewayUrl = current.profile?.webrtcSignalingUrl.orEmpty(),
                                            gatewayBaseUrl = vm.hostUrl.ifBlank { current.profile?.gatewayBaseUrl.orEmpty() },
                                            bearerToken = bearerToken,
                                            onOpenSlotSettings = {
                                                viewModel.openRemoteViewer(null)
                                                viewModel.openVmDetail(vm.vmId)
                                            },
                                            onOpenHostSettings = {
                                                viewModel.openRemoteViewer(null)
                                                activeTab = FleetTab.HOST_LOGS
                                            },
                                            onOpenGoogleSignIn = {
                                                viewModel.selectVms(listOf(vm.vmId))
                                                viewModel.openGoogleSignInOnVms(false)
                                            },
                                            onSendRemoteText = { text ->
                                                viewModel.sendRemoteTextToVm(vm, text)
                                            },
                                            onMarkAccountStatus = { status ->
                                                viewModel.markVmAccountStatus(vm, status)
                                            },
                                            onDismiss = { viewModel.openRemoteViewer(null) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
