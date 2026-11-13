package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminProfile
import com.example.data.model.AuditLog
import com.example.data.model.CommandType
import com.example.data.model.SavedPhoneApp
import com.example.data.model.VmAccountStatus
import com.example.data.model.VmApp
import com.example.data.model.VmCommand
import com.example.data.model.VmGroup
import com.example.data.model.VmSlot
import com.example.data.model.VmStatus
import com.example.data.network.GatewayHealthResponse
import com.example.data.network.VmGatewayClient
import com.example.data.prefs.GatewayPreferences
import com.example.data.repository.FleetDataSource
import com.example.util.GmailBatchParser
import com.example.util.PlayStoreParser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface FleetUiState {
    data object Loading : FleetUiState
    data class Ready(
        val profile: AdminProfile?,
        val vms: List<VmSlot>,
        val groups: List<VmGroup>,
        val commands: List<VmCommand>,
        val apps: List<VmApp>,
        val auditLogs: List<AuditLog>
    ) : FleetUiState

    data class Error(val message: String) : FleetUiState
}

enum class DeviceViewMode { GRID, LIST }

data class FleetLocalControls(
    val searchQuery: String = "",
    val statusFilter: VmStatus? = null,
    val groupFilter: String? = null,
    val selectedVmIds: Set<String> = emptySet(),
    val viewMode: DeviceViewMode = DeviceViewMode.GRID,
    val isBusy: Boolean = false,
    val bannerMessage: String? = null,
    val gatewayHealth: GatewayHealthResponse? = null,
    val gatewayHealthError: String? = null,
    val activeVmDetailId: String? = null,
    val remoteViewerVmId: String? = null
)

class FleetViewModel(
    private val dataSource: FleetDataSource,
    private val gatewayPreferences: GatewayPreferences,
    private val gatewayClient: VmGatewayClient,
    val isCloudMode: Boolean,
    val currentUserEmail: String
) : ViewModel() {

    private val _controls = MutableStateFlow(FleetLocalControls())
    val controls: StateFlow<FleetLocalControls> = _controls.asStateFlow()

    val gatewayToken: StateFlow<String> = gatewayPreferences.gatewayTokenFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    val savedPhoneApps: StateFlow<List<SavedPhoneApp>> = gatewayPreferences.savedPhoneAppsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val preferredTargetPackage: StateFlow<String> = gatewayPreferences.preferredTargetPackageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    private val baseDataFlow = combine(
        dataSource.observeProfile(),
        dataSource.observeVms(),
        dataSource.observeGroups(),
        dataSource.observeCommands(),
        dataSource.observeApps()
    ) { profile, vms, groups, commands, apps ->
        Tuple5(profile, vms, groups, commands, apps)
    }

    val uiState: StateFlow<FleetUiState> = combine(
        baseDataFlow,
        dataSource.observeAuditLogs()
    ) { tuple, logs ->
        FleetUiState.Ready(
            profile = tuple.first,
            vms = tuple.second,
            groups = tuple.third,
            commands = tuple.fourth,
            apps = tuple.fifth,
            auditLogs = logs
        ) as FleetUiState
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing fleet data", rootError)
        emit(FleetUiState.Error(rootError.message ?: "Failed to load fleet data"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = FleetUiState.Loading
    )

    init {
        viewModelScope.launch {
            dataSource.ensureInitializedProfile()
        }
    }

    fun initializeHundredSlots() {
        runBusyAction("Initializing VM-001 to VM-100...") {
            dataSource.initializeHundredSlots()
                .fold(
                    onSuccess = { "Initialized 100 unprovisioned VM slots (VM-001..VM-100)." },
                    onFailure = { "Initialization failed: ${it.message}" }
                )
        }
    }

    fun updateSearchQuery(query: String) {
        _controls.update { it.copy(searchQuery = query) }
    }

    fun updateStatusFilter(status: VmStatus?) {
        _controls.update { it.copy(statusFilter = status) }
    }

    fun updateGroupFilter(groupId: String?) {
        _controls.update { it.copy(groupFilter = groupId) }
    }

    fun setViewMode(mode: DeviceViewMode) {
        _controls.update { it.copy(viewMode = mode) }
    }

    fun toggleVmSelection(vmId: String) {
        _controls.update { state ->
            val updated = state.selectedVmIds.toMutableSet()
            if (!updated.add(vmId)) updated.remove(vmId)
            state.copy(selectedVmIds = updated)
        }
    }

    fun selectVms(vmIds: Collection<String>) {
        _controls.update { it.copy(selectedVmIds = vmIds.toSet()) }
    }

    fun clearSelection() {
        _controls.update { it.copy(selectedVmIds = emptySet()) }
    }

    fun openVmDetail(vmId: String?) {
        _controls.update { it.copy(activeVmDetailId = vmId) }
    }

    fun openRemoteViewer(vmId: String?) {
        _controls.update { it.copy(remoteViewerVmId = vmId) }
    }

    fun dismissBanner() {
        _controls.update { it.copy(bannerMessage = null) }
    }

    fun showMessage(message: String) {
        showBanner(message)
    }

    fun setPreferredTargetPackage(packageName: String) {
        viewModelScope.launch {
            gatewayPreferences.setPreferredTargetPackage(packageName)
        }
    }

    fun saveVmSlot(slot: VmSlot) {
        runBusyAction("Saving ${slot.vmId}...") {
            dataSource.saveVmSlot(slot)
                .fold(
                    onSuccess = { "${slot.vmId} configuration saved." },
                    onFailure = { "Failed to update ${slot.vmId}: ${it.message}" }
                )
        }
    }

    fun createGroup(name: String, description: String, colorHex: String) {
        if (name.isBlank()) {
            showBanner("Group name cannot be empty.")
            return
        }
        runBusyAction("Creating group $name...") {
            dataSource.createGroup(name, description, colorHex)
                .fold(
                    onSuccess = { "Created group \"$name\"." },
                    onFailure = { "Failed to create group: ${it.message}" }
                )
        }
    }

    fun assignGroupToSelected(groupId: String) {
        val selected = _controls.value.selectedVmIds.toList()
        if (selected.isEmpty()) {
            showBanner("Select one or more VM slots first.")
            return
        }
        runBusyAction("Assigning ${selected.size} VMs to group...") {
            dataSource.assignGroupToVms(selected, groupId)
                .fold(
                    onSuccess = { "Assigned ${selected.size} VMs to group." },
                    onFailure = { "Group assignment failed: ${it.message}" }
                )
        }
    }

    fun dispatchLifecycle(vm: VmSlot, action: CommandType) {
        val ready = uiState.value as? FleetUiState.Ready ?: return
        val gatewayUrl = ready.profile?.gatewayBaseUrl.orEmpty()
        val token = gatewayToken.value
        runBusyAction("Sending ${action.label} to ${vm.vmId}...") {
            dataSource.dispatchLifecycleCommand(
                vm = vm,
                commandType = action,
                gatewayBaseUrl = gatewayUrl,
                gatewayToken = token
            ).fold(
                onSuccess = { status -> "${vm.vmId}: ${action.label} -> ${status.label}" },
                onFailure = { "Command failed: ${it.message}" }
            )
        }
    }

    fun dispatchLifecycleBulk(action: CommandType, useAll100: Boolean) {
        val ready = uiState.value as? FleetUiState.Ready ?: return
        if (!useAll100 && _controls.value.selectedVmIds.isEmpty()) {
            showBanner("Select one or more VM slots first.")
            return
        }
        val gatewayUrl = ready.profile?.gatewayBaseUrl.orEmpty()
        val token = gatewayToken.value
        val countLabel = if (useAll100) 100 else _controls.value.selectedVmIds.size
        runBusyAction("Sending ${action.label} to $countLabel VMs...") {
            val targets = if (useAll100) {
                if (ready.vms.isEmpty()) {
                    dataSource.initializeHundredSlots()
                    dataSource.observeVms().first { it.isNotEmpty() }
                } else {
                    ready.vms
                }
            } else {
                val selectedIds = _controls.value.selectedVmIds
                ready.vms.filter { it.vmId in selectedIds }
            }
            var delivered = 0
            var queued = 0
            var offline = 0
            coroutineScope {
                targets.chunked(16).forEach { batch ->
                    val results = batch.map { vm ->
                        async {
                            dataSource.dispatchLifecycleCommand(
                                vm = vm,
                                commandType = action,
                                gatewayBaseUrl = gatewayUrl,
                                gatewayToken = token
                            )
                        }
                    }.awaitAll()
                    results.forEach { res ->
                        res.onSuccess { st ->
                            when (st.name) {
                                "DELIVERED", "OPENED", "COMPLETED" -> delivered++
                                "QUEUED" -> queued++
                                else -> offline++
                            }
                        }.onFailure { offline++ }
                    }
                }
            }
            "${action.label} (${targets.size} VMs): $delivered active, $queued queued, $offline host offline."
        }
    }

    fun dispatchUrl(url: String, targetPackage: String, useAll100: Boolean) {
        val raw = url.trim()
        if (raw.isEmpty()) {
            showBanner("Paste a URL first.")
            return
        }
        val normalizedUrl = if (raw.contains("://")) {
            raw
        } else if (raw.contains(".")) {
            "https://$raw"
        } else {
            showBanner("Enter a valid testing URL (e.g., https://example.org).")
            return
        }
        val ready = uiState.value as? FleetUiState.Ready ?: return
        if (!useAll100 && _controls.value.selectedVmIds.isEmpty()) {
            showBanner("Select at least one VM slot or tap OPEN ALL (100).")
            return
        }
        val gatewayUrl = ready.profile?.gatewayBaseUrl.orEmpty()
        val token = gatewayToken.value
        runBusyAction("Dispatching URL to ${if (useAll100) 100 else _controls.value.selectedVmIds.size} VMs...") {
            val targets = if (useAll100) {
                if (ready.vms.isEmpty()) {
                    dataSource.initializeHundredSlots()
                    dataSource.observeVms().first { it.isNotEmpty() }
                } else {
                    ready.vms
                }
            } else {
                val selectedIds = _controls.value.selectedVmIds
                ready.vms.filter { it.vmId in selectedIds }
            }
            if (targets.isEmpty()) {
                return@runBusyAction "No target VM slots found."
            }
            dataSource.dispatchUrlToVms(
                targets = targets,
                url = normalizedUrl,
                targetPackage = targetPackage,
                gatewayBaseUrl = gatewayUrl,
                gatewayToken = token
            ).fold(
                onSuccess = { results ->
                    val opened = results.values.count { it.name == "DELIVERED" || it.name == "OPENED" }
                    val queued = results.values.count { it.name == "QUEUED" }
                    val offline = results.size - opened - queued
                    "URL dispatched (${results.size} VMs): $opened opened, $queued queued, $offline host offline."
                },
                onFailure = { "URL dispatch failed: ${it.message}" }
            )
        }
    }

    fun autoPopulatePhoneAppsIfEmpty(apps: List<SavedPhoneApp>) {
        if (apps.isEmpty()) return
        viewModelScope.launch {
            val current = gatewayPreferences.savedPhoneAppsFlow.first()
            if (current.isEmpty()) {
                gatewayPreferences.addPhoneApps(apps)
            }
        }
    }

    fun addPhoneApps(apps: List<SavedPhoneApp>) {
        if (apps.isEmpty()) {
            showBanner("No launchable phone apps found to add.")
            return
        }
        viewModelScope.launch {
            gatewayPreferences.addPhoneApps(apps)
            showBanner("Added ${apps.size} app(s) from your phone. Pick any one app below to open on 100 devices.")
        }
    }

    fun removePhoneApp(packageName: String) {
        viewModelScope.launch {
            gatewayPreferences.removePhoneApp(packageName)
        }
    }

    fun launchOneAppOnVms(packageName: String, appName: String, useAll100: Boolean) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) {
            showBanner("Select one phone app first.")
            return
        }
        val ready = uiState.value as? FleetUiState.Ready ?: return
        if (!useAll100 && _controls.value.selectedVmIds.isEmpty()) {
            showBanner("Select at least one VM slot or tap Open All (100).")
            return
        }
        val gatewayUrl = ready.profile?.gatewayBaseUrl.orEmpty()
        val token = gatewayToken.value
        val display = appName.trim().ifBlank { cleanPkg }
        val countLabel = if (useAll100) 100 else _controls.value.selectedVmIds.size
        if (cleanPkg != "com.android.vending") {
            viewModelScope.launch {
                gatewayPreferences.setPreferredTargetPackage(cleanPkg)
            }
        }
        runBusyAction("Opening $display on $countLabel devices...") {
            val targets = if (useAll100) {
                if (ready.vms.isEmpty()) {
                    dataSource.initializeHundredSlots()
                    dataSource.observeVms().first { it.isNotEmpty() }
                } else {
                    ready.vms
                }
            } else {
                val selectedIds = _controls.value.selectedVmIds
                ready.vms.filter { it.vmId in selectedIds }
            }
            if (targets.isEmpty()) {
                return@runBusyAction "No target VM slots found."
            }
            dataSource.launchAppOnVms(
                targets = targets,
                packageName = cleanPkg,
                appName = display,
                gatewayBaseUrl = gatewayUrl,
                gatewayToken = token
            ).fold(
                onSuccess = { results ->
                    val opened = results.values.count { it.name == "DELIVERED" || it.name == "OPENED" }
                    val queued = results.values.count { it.name == "QUEUED" }
                    val offline = results.size - opened - queued
                    "$display (${results.size} VMs): $opened opened, $queued queued, $offline host offline/not installed."
                },
                onFailure = { "Open app failed: ${it.message}" }
            )
        }
    }

    fun pushPhoneAppToVms(
        app: SavedPhoneApp,
        apkFiles: List<File>,
        openAfterInstall: Boolean,
        useAll100: Boolean
    ) {
        if (apkFiles.isEmpty()) {
            showBanner("Could not read APK file from phone for ${app.appName}.")
            return
        }
        val ready = uiState.value as? FleetUiState.Ready ?: return
        if (!useAll100 && _controls.value.selectedVmIds.isEmpty()) {
            showBanner("Select at least one VM slot or tap Install & Open (100).")
            return
        }
        val gatewayUrl = ready.profile?.gatewayBaseUrl.orEmpty()
        val token = gatewayToken.value
        val countLabel = if (useAll100) 100 else _controls.value.selectedVmIds.size
        val verb = if (openAfterInstall) "Installing & opening" else "Installing"
        viewModelScope.launch {
            gatewayPreferences.addPhoneApps(listOf(app))
            gatewayPreferences.setPreferredTargetPackage(app.packageName)
        }
        runBusyAction("$verb ${app.appName} (${apkFiles.size} APK) on $countLabel devices...") {
            val targets = if (useAll100) {
                if (ready.vms.isEmpty()) {
                    dataSource.initializeHundredSlots()
                    dataSource.observeVms().first { it.isNotEmpty() }
                } else {
                    ready.vms
                }
            } else {
                val selectedIds = _controls.value.selectedVmIds
                ready.vms.filter { it.vmId in selectedIds }
            }
            if (targets.isEmpty()) {
                return@runBusyAction "No target VM slots found."
            }
            dataSource.pushPhoneAppToVms(
                targets = targets,
                app = app,
                apkFiles = apkFiles,
                openAfterInstall = openAfterInstall,
                gatewayBaseUrl = gatewayUrl,
                gatewayToken = token
            ).fold(
                onSuccess = { results ->
                    val ok = results.values.count {
                        it.name == "COMPLETED" || it.name == "OPENED" || it.name == "DELIVERED"
                    }
                    val queued = results.values.count { it.name == "QUEUED" }
                    val offline = results.size - ok - queued
                    "${app.appName} (${results.size} VMs): $ok done, $queued queued, $offline offline."
                },
                onFailure = { "Phone APK push failed: ${it.message}" }
            )
        }
    }

    fun openInPlayStore(input: String, useAll100: Boolean) {
        val packageId = PlayStoreParser.extractPackageId(input)
        if (packageId == null) {
            showBanner("Enter a valid Play Store URL (https://play.google.com/store/apps/details?id=...) or package ID (e.g., com.whatsapp).")
            return
        }
        viewModelScope.launch {
            gatewayPreferences.addPhoneApps(
                listOf(
                    SavedPhoneApp(
                        packageName = packageId,
                        appName = PlayStoreParser.friendlyAppName(packageId)
                    )
                )
            )
            gatewayPreferences.setPreferredTargetPackage(packageId)
        }
        dispatchUrl(
            url = PlayStoreParser.buildMarketUri(packageId),
            targetPackage = "com.android.vending",
            useAll100 = useAll100
        )
    }

    fun dispatchAppOperation(
        commandType: CommandType,
        packageName: String,
        appName: String,
        versionName: String,
        versionCode: Long,
        apkSourceUrl: String,
        useAll100: Boolean
    ) {
        if (packageName.isBlank() || appName.isBlank()) {
            showBanner("Package name and App name are required.")
            return
        }
        val ready = uiState.value as? FleetUiState.Ready ?: return
        if (!useAll100 && _controls.value.selectedVmIds.isEmpty()) {
            showBanner("Select at least one VM slot first.")
            return
        }
        val gatewayUrl = ready.profile?.gatewayBaseUrl.orEmpty()
        val token = gatewayToken.value
        val countLabel = if (useAll100) 100 else _controls.value.selectedVmIds.size
        if (commandType == CommandType.INSTALL_APK || commandType == CommandType.UPDATE_APK) {
            viewModelScope.launch {
                val cleanPkg = packageName.trim()
                val cleanName = appName.trim().ifBlank { cleanPkg }
                gatewayPreferences.addPhoneApps(
                    listOf(
                        SavedPhoneApp(
                            packageName = cleanPkg,
                            appName = cleanName,
                            versionName = versionName.ifBlank { "1.0.0" },
                            versionCode = versionCode.coerceAtLeast(1L)
                        )
                    )
                )
                gatewayPreferences.setPreferredTargetPackage(cleanPkg)
            }
        }
        runBusyAction("Running ${commandType.label} on $countLabel VMs...") {
            val targets = if (useAll100) {
                if (ready.vms.isEmpty()) {
                    dataSource.initializeHundredSlots()
                    dataSource.observeVms().first { it.isNotEmpty() }
                } else {
                    ready.vms
                }
            } else {
                val selectedIds = _controls.value.selectedVmIds
                ready.vms.filter { it.vmId in selectedIds }
            }
            if (targets.isEmpty()) {
                return@runBusyAction "No target VM slots found."
            }
            dataSource.dispatchAppAction(
                targets = targets,
                commandType = commandType,
                packageName = packageName,
                appName = appName,
                versionName = versionName.ifBlank { "1.0.0" },
                versionCode = versionCode.coerceAtLeast(1L),
                apkSourceUrl = apkSourceUrl,
                gatewayBaseUrl = gatewayUrl,
                gatewayToken = token
            ).fold(
                onSuccess = { "${commandType.label} recorded for ${targets.size} VMs." },
                onFailure = { "APK operation failed: ${it.message}" }
            )
        }
    }

    fun saveGatewayConfig(gatewayBaseUrl: String, webrtcSignalingUrl: String, bearerToken: String) {
        runBusyAction("Saving host gateway config...") {
            gatewayPreferences.setGatewayToken(bearerToken)
            dataSource.updateGatewaySettings(
                gatewayBaseUrl = gatewayBaseUrl,
                webrtcSignalingUrl = webrtcSignalingUrl
            ).fold(
                onSuccess = { "Gateway configuration saved securely." },
                onFailure = { "Failed to save gateway config: ${it.message}" }
            )
        }
    }

    fun assignGmailBatch(rawEmails: String, startSlotNumber: Int = 1) {
        val uniqueEmails = GmailBatchParser.parseUniqueEmails(rawEmails)
        if (uniqueEmails.isEmpty()) {
            showBanner("Paste at least one valid Gmail address (1 per line).")
            return
        }
        val ready = uiState.value as? FleetUiState.Ready ?: return
        runBusyAction("Assigning ${uniqueEmails.size} unique Gmail label(s) to VM slots...") {
            val vms = if (ready.vms.isEmpty()) {
                dataSource.initializeHundredSlots()
                dataSource.observeVms().first { it.isNotEmpty() }
            } else {
                ready.vms
            }
            val mapping = GmailBatchParser.mapEmailsToVms(uniqueEmails, vms, startSlotNumber)
            if (mapping.isEmpty()) {
                return@runBusyAction "No matching VM slots found starting from slot $startSlotNumber."
            }
            dataSource.assignGmailAccountsBatch(mapping).fold(
                onSuccess = { count ->
                    "Assigned $count unique Gmail(s) starting at slot #${startSlotNumber.coerceIn(1, 100)}."
                },
                onFailure = { "Failed to assign Gmails: ${it.message}" }
            )
        }
    }

    fun openGoogleSignInOnVms(useAll100: Boolean) {
        val ready = uiState.value as? FleetUiState.Ready ?: return
        if (!useAll100 && _controls.value.selectedVmIds.isEmpty()) {
            showBanner("Select one or more VM slots or tap Google Sign-In (100).")
            return
        }
        val gatewayUrl = ready.profile?.gatewayBaseUrl.orEmpty()
        val token = gatewayToken.value
        val countLabel = if (useAll100) 100 else _controls.value.selectedVmIds.size
        runBusyAction("Opening Google Sign-In on $countLabel screen(s)...") {
            val targets = if (useAll100) {
                if (ready.vms.isEmpty()) {
                    dataSource.initializeHundredSlots()
                    dataSource.observeVms().first { it.isNotEmpty() }
                } else {
                    ready.vms
                }
            } else {
                val selectedIds = _controls.value.selectedVmIds
                ready.vms.filter { it.vmId in selectedIds }
            }
            dataSource.openGoogleSignInOnVms(targets, gatewayUrl, token).fold(
                onSuccess = { results ->
                    val opened = results.values.count { it.name == "DELIVERED" || it.name == "OPENED" }
                    val queued = results.values.count { it.name == "QUEUED" }
                    val offline = results.size - opened - queued
                    "Google Sign-In (${results.size} VMs): $opened opened, $queued queued, $offline host offline."
                },
                onFailure = { "Google Sign-In dispatch failed: ${it.message}" }
            )
        }
    }

    fun clearAppSessionForSignup(packageName: String, appName: String, useAll100: Boolean) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) {
            showBanner("Select an app from Play Store or Phone Apps first.")
            return
        }
        val ready = uiState.value as? FleetUiState.Ready ?: return
        if (!useAll100 && _controls.value.selectedVmIds.isEmpty()) {
            showBanner("Select VM slots or use Fresh Session (100).")
            return
        }
        val gatewayUrl = ready.profile?.gatewayBaseUrl.orEmpty()
        val token = gatewayToken.value
        val display = appName.trim().ifBlank { cleanPkg }
        val countLabel = if (useAll100) 100 else _controls.value.selectedVmIds.size
        runBusyAction("Resetting $display session for fresh signup on $countLabel screen(s)...") {
            val targets = if (useAll100) {
                if (ready.vms.isEmpty()) {
                    dataSource.initializeHundredSlots()
                    dataSource.observeVms().first { it.isNotEmpty() }
                } else {
                    ready.vms
                }
            } else {
                val selectedIds = _controls.value.selectedVmIds
                ready.vms.filter { it.vmId in selectedIds }
            }
            dataSource.clearAppSessionOnVms(
                targets = targets,
                packageName = cleanPkg,
                appName = display,
                openAfterClear = true,
                gatewayBaseUrl = gatewayUrl,
                gatewayToken = token
            ).fold(
                onSuccess = { results ->
                    val readyCount = results.values.count {
                        it.name == "OPENED" || it.name == "DELIVERED" || it.name == "COMPLETED"
                    }
                    val queued = results.values.count { it.name == "QUEUED" }
                    val offline = results.size - readyCount - queued
                    "$display fresh signup (${results.size} VMs): $readyCount ready, $queued queued, $offline host offline."
                },
                onFailure = { "Fresh session reset failed: ${it.message}" }
            )
        }
    }

    fun markVmAccountStatus(vm: VmSlot, status: VmAccountStatus) {
        saveVmSlot(vm.copy(accountStatus = status.name))
    }

    fun sendRemoteTextToVm(vm: VmSlot, text: String) {
        val clean = text.replace("\n", "").replace("\r", "").trim()
        if (clean.isEmpty()) {
            showBanner("Nothing to send.")
            return
        }
        val ready = uiState.value as? FleetUiState.Ready ?: return
        val endpoint = vm.hostUrl.ifBlank { ready.profile?.gatewayBaseUrl.orEmpty() }
        if (endpoint.isBlank()) {
            showBanner("Configure HTTPS Host Gateway first to send text to ${vm.vmId}.")
            return
        }
        val token = gatewayToken.value
        viewModelScope.launch {
            gatewayClient.sendRemoteText(endpoint, token, vm.vmId, clean).fold(
                onSuccess = { resp ->
                    showBanner(resp.message.ifBlank { "Sent text to ${vm.vmId}" })
                },
                onFailure = { err ->
                    showBanner("Remote text failed on ${vm.vmId}: ${err.message}")
                }
            )
        }
    }

    fun checkGatewayHealth(gatewayBaseUrl: String, bearerToken: String) {
        if (gatewayBaseUrl.isBlank()) {
            _controls.update {
                it.copy(
                    gatewayHealth = null,
                    gatewayHealthError = "No HTTPS Gateway URL configured yet."
                )
            }
            return
        }
        viewModelScope.launch {
            _controls.update { it.copy(isBusy = true, gatewayHealthError = null) }
            gatewayClient.pingHealth(gatewayBaseUrl, bearerToken).fold(
                onSuccess = { health ->
                    var syncedCount = 0
                    gatewayClient.fetchVmStatuses(gatewayBaseUrl, bearerToken).onSuccess { statusResp ->
                        dataSource.syncVmTelemetry(statusResp.vms).onSuccess { syncedCount = it }
                    }
                    _controls.update {
                        it.copy(
                            isBusy = false,
                            gatewayHealth = health,
                            gatewayHealthError = null,
                            bannerMessage = "Gateway OK (${health.hostLabel.ifBlank { "connected" }}) • Synced $syncedCount VM IPs"
                        )
                    }
                },
                onFailure = { err ->
                    _controls.update {
                        it.copy(
                            isBusy = false,
                            gatewayHealth = null,
                            gatewayHealthError = err.message ?: "Host gateway unreachable"
                        )
                    }
                }
            )
        }
    }

    private fun showBanner(message: String) {
        _controls.update { it.copy(bannerMessage = message) }
    }

    private fun runBusyAction(loadingNote: String, block: suspend () -> String) {
        viewModelScope.launch {
            _controls.update { it.copy(isBusy = true, bannerMessage = loadingNote) }
            val resultMessage = block()
            _controls.update { it.copy(isBusy = false, bannerMessage = resultMessage) }
        }
    }

    private data class Tuple5<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )

    private companion object {
        const val TAG = "FleetViewModel"
    }
}
