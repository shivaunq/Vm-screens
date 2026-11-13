package com.example.data.repository

import com.example.data.local.FleetDao
import com.example.data.local.LocalAuditLogEntity
import com.example.data.local.LocalProfileEntity
import com.example.data.local.LocalVmAppEntity
import com.example.data.local.LocalVmCommandEntity
import com.example.data.local.LocalVmGroupEntity
import com.example.data.local.LocalVmSlotEntity
import com.example.data.model.AdminProfile
import com.example.data.model.AuditLog
import com.example.data.model.CommandType
import com.example.data.model.DeliveryStatus
import com.example.data.model.InstallStatus
import com.example.data.model.SavedPhoneApp
import com.example.data.model.VmAccountStatus
import com.example.data.model.VmApp
import com.example.data.model.VmCommand
import com.example.data.model.VmConnectivity
import com.example.data.model.VmGroup
import com.example.data.model.VmSlot
import com.example.data.model.VmStatus
import com.example.data.network.GatewayAppActionRequest
import com.example.data.network.GatewayVmTelemetryItem
import com.example.data.network.VmGatewayClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

interface FleetDataSource {
    val isCloudMode: Boolean
    fun observeProfile(): Flow<AdminProfile?>
    fun observeVms(): Flow<List<VmSlot>>
    fun observeGroups(): Flow<List<VmGroup>>
    fun observeCommands(): Flow<List<VmCommand>>
    fun observeApps(): Flow<List<VmApp>>
    fun observeAuditLogs(): Flow<List<AuditLog>>
    suspend fun ensureInitializedProfile()
    suspend fun initializeHundredSlots(): Result<Unit>
    suspend fun saveVmSlot(slot: VmSlot): Result<Unit>
    suspend fun createGroup(name: String, description: String, colorHex: String): Result<String>
    suspend fun assignGroupToVms(vmIds: List<String>, groupId: String): Result<Unit>
    suspend fun updateGatewaySettings(gatewayBaseUrl: String, webrtcSignalingUrl: String): Result<Unit>
    suspend fun dispatchLifecycleCommand(
        vm: VmSlot,
        commandType: CommandType,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<DeliveryStatus>

    suspend fun dispatchUrlToVms(
        targets: List<VmSlot>,
        url: String,
        targetPackage: String,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Map<String, DeliveryStatus>>

    suspend fun launchAppOnVms(
        targets: List<VmSlot>,
        packageName: String,
        appName: String,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Map<String, DeliveryStatus>>

    suspend fun pushPhoneAppToVms(
        targets: List<VmSlot>,
        app: SavedPhoneApp,
        apkFiles: List<File>,
        openAfterInstall: Boolean,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Map<String, DeliveryStatus>>

    suspend fun dispatchAppAction(
        targets: List<VmSlot>,
        commandType: CommandType,
        packageName: String,
        appName: String,
        versionName: String,
        versionCode: Long,
        apkSourceUrl: String,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Unit>

    suspend fun assignGmailAccountsBatch(assignments: Map<String, String>): Result<Int>
    suspend fun openGoogleSignInOnVms(
        targets: List<VmSlot>,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Map<String, DeliveryStatus>>

    suspend fun clearAppSessionOnVms(
        targets: List<VmSlot>,
        packageName: String,
        appName: String,
        openAfterClear: Boolean,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Map<String, DeliveryStatus>>

    suspend fun syncVmTelemetry(items: List<GatewayVmTelemetryItem>): Result<Int>
}

class FirestoreFleetDataSource(
    private val repo: FleetRepository,
    private val userId: String,
    private val userEmail: String,
    private val userName: String
) : FleetDataSource {
    override val isCloudMode: Boolean = true

    override fun observeProfile() = repo.observeProfile(userId)
    override fun observeVms() = repo.observeVms(userId)
    override fun observeGroups() = repo.observeGroups(userId)
    override fun observeCommands() = repo.observeCommands(userId)
    override fun observeApps() = repo.observeApps(userId)
    override fun observeAuditLogs() = repo.observeAuditLogs(userId)

    override suspend fun ensureInitializedProfile() {
        repo.ensureAdminProfile(userId, userEmail, userName)
    }

    override suspend fun initializeHundredSlots() =
        repo.initializeHundredSlots(userId, userEmail)

    override suspend fun saveVmSlot(slot: VmSlot) =
        repo.saveVmSlot(userId, userEmail, slot)

    override suspend fun createGroup(name: String, description: String, colorHex: String) =
        repo.createGroup(userId, userEmail, name, description, colorHex)

    override suspend fun assignGroupToVms(vmIds: List<String>, groupId: String) =
        repo.assignGroupToVms(userId, userEmail, vmIds, groupId)

    override suspend fun updateGatewaySettings(gatewayBaseUrl: String, webrtcSignalingUrl: String) =
        repo.updateGatewaySettings(userId, userEmail, gatewayBaseUrl, webrtcSignalingUrl)

    override suspend fun dispatchLifecycleCommand(
        vm: VmSlot,
        commandType: CommandType,
        gatewayBaseUrl: String,
        gatewayToken: String
    ) = repo.dispatchLifecycleCommand(userId, userEmail, vm, commandType, gatewayBaseUrl, gatewayToken)

    override suspend fun dispatchUrlToVms(
        targets: List<VmSlot>,
        url: String,
        targetPackage: String,
        gatewayBaseUrl: String,
        gatewayToken: String
    ) = repo.dispatchUrlToVms(userId, userEmail, targets, url, targetPackage, gatewayBaseUrl, gatewayToken)

    override suspend fun launchAppOnVms(
        targets: List<VmSlot>,
        packageName: String,
        appName: String,
        gatewayBaseUrl: String,
        gatewayToken: String
    ) = repo.launchAppOnVms(userId, userEmail, targets, packageName, appName, gatewayBaseUrl, gatewayToken)

    override suspend fun pushPhoneAppToVms(
        targets: List<VmSlot>,
        app: SavedPhoneApp,
        apkFiles: List<File>,
        openAfterInstall: Boolean,
        gatewayBaseUrl: String,
        gatewayToken: String
    ) = repo.pushPhoneAppToVms(userId, userEmail, targets, app, apkFiles, openAfterInstall, gatewayBaseUrl, gatewayToken)

    override suspend fun dispatchAppAction(
        targets: List<VmSlot>,
        commandType: CommandType,
        packageName: String,
        appName: String,
        versionName: String,
        versionCode: Long,
        apkSourceUrl: String,
        gatewayBaseUrl: String,
        gatewayToken: String
    ) = repo.dispatchAppAction(
        userId, userEmail, targets, commandType, packageName, appName, versionName, versionCode, apkSourceUrl, gatewayBaseUrl, gatewayToken
    )

    override suspend fun assignGmailAccountsBatch(assignments: Map<String, String>) =
        repo.assignGmailAccountsBatch(userId, userEmail, assignments)

    override suspend fun openGoogleSignInOnVms(
        targets: List<VmSlot>,
        gatewayBaseUrl: String,
        gatewayToken: String
    ) = repo.openGoogleSignInOnVms(userId, userEmail, targets, gatewayBaseUrl, gatewayToken)

    override suspend fun clearAppSessionOnVms(
        targets: List<VmSlot>,
        packageName: String,
        appName: String,
        openAfterClear: Boolean,
        gatewayBaseUrl: String,
        gatewayToken: String
    ) = repo.clearAppSessionOnVms(
        userId, userEmail, targets, packageName, appName, openAfterClear, gatewayBaseUrl, gatewayToken
    )

    override suspend fun syncVmTelemetry(items: List<GatewayVmTelemetryItem>) =
        repo.syncVmTelemetry(userId, userEmail, items)
}

class LocalRoomFleetDataSource(
    private val dao: FleetDao,
    private val gatewayClient: VmGatewayClient = VmGatewayClient()
) : FleetDataSource {
    override val isCloudMode: Boolean = false
    private val actorLabel = "local-device-admin"

    override fun observeProfile(): Flow<AdminProfile?> =
        dao.observeProfile().map { it?.toDomain() }

    override fun observeVms(): Flow<List<VmSlot>> =
        dao.observeVms().map { list -> list.map { it.toDomain() } }

    override fun observeGroups(): Flow<List<VmGroup>> =
        dao.observeGroups().map { list -> list.map { it.toDomain() } }

    override fun observeCommands(): Flow<List<VmCommand>> =
        dao.observeCommands().map { list -> list.map { it.toDomain() } }

    override fun observeApps(): Flow<List<VmApp>> =
        dao.observeApps().map { list -> list.map { it.toDomain() } }

    override fun observeAuditLogs(): Flow<List<AuditLog>> =
        dao.observeAuditLogs().map { list -> list.map { it.toDomain() } }

    override suspend fun ensureInitializedProfile() {
        if (dao.getProfileOnce() == null) {
            val now = System.currentTimeMillis()
            dao.upsertProfile(
                LocalProfileEntity(
                    id = "local_admin",
                    displayName = "Local Operator",
                    role = "LOCAL_ADMIN",
                    fleetInitialized = false,
                    gatewayBaseUrl = "",
                    webrtcSignalingUrl = "",
                    createdAtMs = now,
                    updatedAtMs = now
                )
            )
        }
    }

    override suspend fun initializeHundredSlots(): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        if (dao.countVms() == 0) {
            val slots = (1..100).map { index ->
                val vmId = "VM-%03d".format(index)
                LocalVmSlotEntity(
                    vmId = vmId,
                    slotNumber = index.toLong(),
                    name = "Slot $vmId",
                    groupId = "unassigned",
                    androidVersion = "Unassigned",
                    status = VmStatus.UNPROVISIONED.name,
                    connectivity = VmConnectivity.DISCONNECTED.name,
                    batteryPercent = null,
                    ipAddress = "",
                    hostUrl = "",
                    streamUrl = "",
                    lastError = "",
                    lastSeenAtMs = null,
                    createdAtMs = now,
                    updatedAtMs = now
                )
            }
            dao.upsertVms(slots)
        }
        val profile = dao.getProfileOnce() ?: LocalProfileEntity(createdAtMs = now, updatedAtMs = now)
        dao.upsertProfile(profile.copy(fleetInitialized = true, updatedAtMs = now))
        logLocal(
            action = "INIT_100_SLOTS",
            targetScope = "VM-001..VM-100",
            details = "Initialized 100 unprovisioned cloud Android VM slots locally",
            result = "SUCCESS"
        )
    }

    override suspend fun saveVmSlot(slot: VmSlot): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        dao.upsertVm(
            LocalVmSlotEntity(
                vmId = slot.vmId,
                slotNumber = slot.slotNumber,
                name = slot.name,
                groupId = slot.groupId,
                androidVersion = slot.androidVersion,
                status = slot.status,
                connectivity = slot.connectivity,
                batteryPercent = slot.batteryPercent,
                ipAddress = slot.ipAddress.trim().take(64),
                assignedGmail = slot.assignedGmail.trim().take(160),
                accountStatus = VmAccountStatus.fromString(slot.accountStatus).name,
                hostUrl = slot.hostUrl,
                streamUrl = slot.streamUrl,
                lastError = slot.lastError,
                lastSeenAtMs = slot.lastSeenAt?.toDate()?.time,
                createdAtMs = slot.createdAt?.toDate()?.time ?: now,
                updatedAtMs = now
            )
        )
        logLocal(
            action = "UPDATE_VM_SLOT",
            targetScope = slot.vmId,
            details = "Updated ${slot.name} (${slot.status}, ip=${slot.ipAddress.ifBlank { "unassigned" }})",
            result = "SUCCESS"
        )
    }

    override suspend fun createGroup(name: String, description: String, colorHex: String): Result<String> = runCatching {
        val id = "grp_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val now = System.currentTimeMillis()
        dao.upsertGroup(
            LocalVmGroupEntity(
                id = id,
                name = name.trim().take(40),
                description = description.trim().take(200),
                colorHex = colorHex.take(16),
                createdAtMs = now,
                updatedAtMs = now
            )
        )
        logLocal(
            action = "CREATE_GROUP",
            targetScope = name.trim(),
            details = "Created local device group ${name.trim()}",
            result = "SUCCESS"
        )
        id
    }

    override suspend fun assignGroupToVms(vmIds: List<String>, groupId: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        vmIds.chunked(50).forEach { chunk ->
            dao.updateGroupForVms(chunk, groupId, now)
        }
        logLocal(
            action = "ASSIGN_GROUP",
            targetScope = "${vmIds.size} VMs",
            details = "Assigned ${vmIds.size} slots to group $groupId",
            result = "SUCCESS"
        )
    }

    override suspend fun updateGatewaySettings(gatewayBaseUrl: String, webrtcSignalingUrl: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val profile = dao.getProfileOnce() ?: LocalProfileEntity(createdAtMs = now, updatedAtMs = now)
        dao.upsertProfile(
            profile.copy(
                gatewayBaseUrl = gatewayBaseUrl.trim().take(500),
                webrtcSignalingUrl = webrtcSignalingUrl.trim().take(500),
                updatedAtMs = now
            )
        )
        logLocal(
            action = "UPDATE_GATEWAY_CONFIG",
            targetScope = "Host Gateway",
            details = "Updated HTTPS gateway endpoint (${gatewayBaseUrl.ifBlank { "cleared" }})",
            result = "SUCCESS"
        )
    }

    override suspend fun dispatchLifecycleCommand(
        vm: VmSlot,
        commandType: CommandType,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<DeliveryStatus> = runCatching {
        val targetGateway = vm.hostUrl.ifBlank { gatewayBaseUrl }
        val (status, message) = if (vm.statusEnum == VmStatus.UNPROVISIONED) {
            DeliveryStatus.HOST_OFFLINE to "Slot ${vm.vmId} is unprovisioned. Enroll a Linux host agent first."
        } else if (targetGateway.isBlank()) {
            DeliveryStatus.QUEUED to "Command queued locally; configure an HTTPS Host Gateway to execute."
        } else {
            gatewayClient.sendLifecycle(targetGateway, gatewayToken, vm.vmId, commandType.name).fold(
                onSuccess = { resp ->
                    if (resp.accepted) DeliveryStatus.DELIVERED to resp.message.ifBlank { "Accepted by host gateway" }
                    else DeliveryStatus.FAILED to resp.message.ifBlank { "Rejected by host gateway" }
                },
                onFailure = { err ->
                    DeliveryStatus.HOST_OFFLINE to "Host unreachable: ${err.message ?: "connection failed"}"
                }
            )
        }
        val now = System.currentTimeMillis()
        dao.insertCommands(
            listOf(
                LocalVmCommandEntity(
                    id = "cmd_" + UUID.randomUUID().toString().replace("-", "").take(16),
                    vmId = vm.vmId,
                    commandType = commandType.name,
                    targetPayload = "",
                    targetPackage = "",
                    deliveryStatus = status.name,
                    statusMessage = message.take(300),
                    createdAtMs = now,
                    updatedAtMs = now
                )
            )
        )
        logLocal(
            action = "VM_${commandType.name}",
            targetScope = vm.vmId,
            details = message,
            result = if (status == DeliveryStatus.DELIVERED) "SUCCESS" else status.name
        )
        status
    }

    override suspend fun dispatchUrlToVms(
        targets: List<VmSlot>,
        url: String,
        targetPackage: String,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Map<String, DeliveryStatus>> = runCatching {
        val normalizedUrl = url.trim().take(1024)
        val normalizedPkg = targetPackage.trim().take(160)
        val remoteResults = mutableMapOf<String, Pair<DeliveryStatus, String>>()
        targets.filter { it.statusEnum != VmStatus.UNPROVISIONED }
            .groupBy { it.hostUrl.ifBlank { gatewayBaseUrl }.trim() }
            .forEach { (endpoint, groupTargets) ->
                if (endpoint.isNotBlank()) {
                    val activeIds = groupTargets.map { it.vmId }
                    gatewayClient.dispatchUrls(endpoint, gatewayToken, activeIds, normalizedUrl, normalizedPkg)
                        .fold(
                            onSuccess = { resp ->
                                resp.results.forEach { item ->
                                    remoteResults[item.vmId] =
                                        DeliveryStatus.fromString(item.status) to item.message.ifBlank { "Dispatched via HTTPS gateway" }
                                }
                            },
                            onFailure = { err ->
                                activeIds.forEach { id ->
                                    remoteResults[id] =
                                        DeliveryStatus.HOST_OFFLINE to "Gateway offline: ${err.message ?: "unreachable"}"
                                }
                            }
                        )
                }
            }

        val now = System.currentTimeMillis()
        val outcomeMap = mutableMapOf<String, DeliveryStatus>()
        val entities = targets.map { vm ->
            val (status, msg) = when {
                vm.statusEnum == VmStatus.UNPROVISIONED ->
                    DeliveryStatus.HOST_OFFLINE to "Slot ${vm.vmId} unprovisioned"
                remoteResults.containsKey(vm.vmId) -> remoteResults.getValue(vm.vmId)
                vm.statusEnum == VmStatus.OFFLINE || vm.statusEnum == VmStatus.ERROR ->
                    DeliveryStatus.HOST_OFFLINE to "VM ${vm.vmId} is ${vm.statusEnum.label}"
                else ->
                    DeliveryStatus.QUEUED to "Queued locally awaiting HTTPS host gateway"
            }
            outcomeMap[vm.vmId] = status
            LocalVmCommandEntity(
                id = "url_" + UUID.randomUUID().toString().replace("-", "").take(16),
                vmId = vm.vmId,
                commandType = CommandType.OPEN_URL.name,
                targetPayload = normalizedUrl,
                targetPackage = normalizedPkg,
                deliveryStatus = status.name,
                statusMessage = msg.take(300),
                createdAtMs = now,
                updatedAtMs = now
            )
        }
        dao.insertCommands(entities)
        val delivered = outcomeMap.values.count { it == DeliveryStatus.DELIVERED || it == DeliveryStatus.OPENED }
        logLocal(
            action = "DISPATCH_URL",
            targetScope = if (targets.size == 100) "ALL_100_VMS" else "${targets.size} VMs",
            details = "URL $normalizedUrl ($delivered active deliveries, ${targets.size - delivered} queued/offline)",
            result = if (delivered > 0) "SUCCESS" else "HOST_OFFLINE"
        )
        outcomeMap
    }

    override suspend fun launchAppOnVms(
        targets: List<VmSlot>,
        packageName: String,
        appName: String,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Map<String, DeliveryStatus>> = runCatching {
        val cleanPkg = packageName.trim().take(160)
        val cleanName = appName.trim().ifBlank { cleanPkg }.take(80)
        val remoteResults = mutableMapOf<String, Pair<DeliveryStatus, String>>()
        targets.filter { it.statusEnum != VmStatus.UNPROVISIONED }
            .groupBy { it.hostUrl.ifBlank { gatewayBaseUrl }.trim() }
            .forEach { (endpoint, groupTargets) ->
                if (endpoint.isNotBlank()) {
                    val activeIds = groupTargets.map { it.vmId }
                    gatewayClient.launchApp(endpoint, gatewayToken, activeIds, cleanPkg, cleanName)
                        .fold(
                            onSuccess = { resp ->
                                resp.results.forEach { item ->
                                    remoteResults[item.vmId] =
                                        DeliveryStatus.fromString(item.status) to item.message.ifBlank { "Launched via HTTPS gateway" }
                                }
                            },
                            onFailure = { err ->
                                activeIds.forEach { id ->
                                    remoteResults[id] =
                                        DeliveryStatus.HOST_OFFLINE to "Gateway offline: ${err.message ?: "unreachable"}"
                                }
                            }
                        )
                }
            }

        val now = System.currentTimeMillis()
        val outcomeMap = mutableMapOf<String, DeliveryStatus>()
        val entities = targets.map { vm ->
            val (status, msg) = when {
                vm.statusEnum == VmStatus.UNPROVISIONED ->
                    DeliveryStatus.HOST_OFFLINE to "Slot ${vm.vmId} unprovisioned"
                remoteResults.containsKey(vm.vmId) -> remoteResults.getValue(vm.vmId)
                vm.statusEnum == VmStatus.OFFLINE || vm.statusEnum == VmStatus.ERROR ->
                    DeliveryStatus.HOST_OFFLINE to "VM ${vm.vmId} is ${vm.statusEnum.label}"
                else ->
                    DeliveryStatus.QUEUED to "Queued locally awaiting HTTPS host gateway"
            }
            outcomeMap[vm.vmId] = status
            LocalVmCommandEntity(
                id = "appopen_" + UUID.randomUUID().toString().replace("-", "").take(16),
                vmId = vm.vmId,
                commandType = CommandType.LAUNCH_APP.name,
                targetPayload = cleanName,
                targetPackage = cleanPkg,
                deliveryStatus = status.name,
                statusMessage = msg.take(300),
                createdAtMs = now,
                updatedAtMs = now
            )
        }
        dao.insertCommands(entities)
        val opened = outcomeMap.values.count { it == DeliveryStatus.DELIVERED || it == DeliveryStatus.OPENED }
        logLocal(
            action = "LAUNCH_APP",
            targetScope = if (targets.size == 100) "ALL_100_VMS" else "${targets.size} VMs",
            details = "Open $cleanName ($cleanPkg): $opened opened, ${targets.size - opened} queued/offline",
            result = if (opened > 0) "SUCCESS" else "HOST_OFFLINE"
        )
        outcomeMap
    }

    override suspend fun pushPhoneAppToVms(
        targets: List<VmSlot>,
        app: SavedPhoneApp,
        apkFiles: List<File>,
        openAfterInstall: Boolean,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Map<String, DeliveryStatus>> = runCatching {
        val cleanPkg = app.packageName.trim().take(160)
        val sanitizedPkg = cleanPkg.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val remoteResults = mutableMapOf<String, Pair<DeliveryStatus, String>>()
        targets.filter { it.statusEnum != VmStatus.UNPROVISIONED }
            .groupBy { it.hostUrl.ifBlank { gatewayBaseUrl }.trim() }
            .forEach { (endpoint, groupTargets) ->
                if (endpoint.isNotBlank()) {
                    val activeIds = groupTargets.map { it.vmId }
                    gatewayClient.uploadPhoneAppAndInstall(
                        baseUrl = endpoint,
                        bearerToken = gatewayToken,
                        apkFiles = apkFiles,
                        vmIds = activeIds,
                        packageName = cleanPkg,
                        appName = app.appName,
                        openAfterInstall = openAfterInstall
                    ).fold(
                        onSuccess = { resp ->
                            resp.results.forEach { item ->
                                remoteResults[item.vmId] =
                                    DeliveryStatus.fromString(item.status) to item.message.ifBlank { "Installed on VM" }
                            }
                        },
                        onFailure = { err ->
                            activeIds.forEach { id ->
                                remoteResults[id] =
                                    DeliveryStatus.HOST_OFFLINE to "Gateway offline: ${err.message ?: "unreachable"}"
                            }
                        }
                    )
                }
            }

        val now = System.currentTimeMillis()
        val outcomeMap = mutableMapOf<String, DeliveryStatus>()
        val appEntities = mutableListOf<LocalVmAppEntity>()
        val cmdEntities = mutableListOf<LocalVmCommandEntity>()

        targets.forEach { vm ->
            val remote = remoteResults[vm.vmId]
            val (status, msg, installStatus) = when {
                vm.statusEnum == VmStatus.UNPROVISIONED -> Triple(
                    DeliveryStatus.HOST_OFFLINE,
                    "Slot ${vm.vmId} unprovisioned",
                    InstallStatus.PENDING
                )
                remote != null -> Triple(
                    remote.first,
                    remote.second,
                    when (remote.first) {
                        DeliveryStatus.COMPLETED, DeliveryStatus.OPENED, DeliveryStatus.DELIVERED -> InstallStatus.INSTALLED
                        DeliveryStatus.FAILED -> InstallStatus.FAILED
                        else -> InstallStatus.PENDING
                    }
                )
                vm.statusEnum == VmStatus.OFFLINE || vm.statusEnum == VmStatus.ERROR -> Triple(
                    DeliveryStatus.HOST_OFFLINE,
                    "VM ${vm.vmId} is ${vm.statusEnum.label}",
                    InstallStatus.PENDING
                )
                else -> Triple(
                    DeliveryStatus.QUEUED,
                    "Queued phone APK push (${apkFiles.size} file(s)); connect HTTPS Host Gateway to execute",
                    InstallStatus.PENDING
                )
            }
            outcomeMap[vm.vmId] = status
            appEntities += LocalVmAppEntity(
                id = "${vm.vmId}_$sanitizedPkg".take(128),
                vmId = vm.vmId,
                packageName = cleanPkg,
                appName = app.appName.take(80),
                versionName = app.versionName.take(32),
                versionCode = app.versionCode,
                apkSourceUrl = "phone://${cleanPkg}",
                sessionProfileId = "iso_${vm.vmId}_$sanitizedPkg".take(80),
                installStatus = installStatus.name,
                lastError = if (installStatus == InstallStatus.FAILED) msg.take(300) else "",
                createdAtMs = now,
                updatedAtMs = now
            )
            cmdEntities += LocalVmCommandEntity(
                id = "push_" + UUID.randomUUID().toString().replace("-", "").take(16),
                vmId = vm.vmId,
                commandType = if (openAfterInstall) CommandType.LAUNCH_APP.name else CommandType.INSTALL_APK.name,
                targetPayload = app.appName.take(80),
                targetPackage = cleanPkg,
                deliveryStatus = status.name,
                statusMessage = msg.take(300),
                createdAtMs = now,
                updatedAtMs = now
            )
        }
        dao.upsertApps(appEntities)
        dao.insertCommands(cmdEntities)
        val successCount = outcomeMap.values.count {
            it == DeliveryStatus.COMPLETED || it == DeliveryStatus.OPENED || it == DeliveryStatus.DELIVERED
        }
        logLocal(
            action = if (openAfterInstall) "PUSH_INSTALL_AND_OPEN" else "PUSH_INSTALL_PHONE_APK",
            targetScope = if (targets.size == 100) "ALL_100_VMS" else "${targets.size} VMs",
            details = "${app.appName} ($cleanPkg): $successCount succeeded, ${targets.size - successCount} queued/offline",
            result = if (successCount > 0) "SUCCESS" else "QUEUED"
        )
        outcomeMap
    }

    override suspend fun dispatchAppAction(
        targets: List<VmSlot>,
        commandType: CommandType,
        packageName: String,
        appName: String,
        versionName: String,
        versionCode: Long,
        apkSourceUrl: String,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Unit> = runCatching {
        val sanitizedPkg = packageName.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val remoteResults: Map<String, Pair<DeliveryStatus, String>> =
            if (gatewayBaseUrl.isNotBlank() && targets.any { it.statusEnum != VmStatus.UNPROVISIONED }) {
                val activeIds = targets.filter { it.statusEnum != VmStatus.UNPROVISIONED }.map { it.vmId }
                gatewayClient.manageApps(
                    gatewayBaseUrl,
                    gatewayToken,
                    GatewayAppActionRequest(
                        vmIds = activeIds,
                        action = commandType.name,
                        packageName = packageName.trim(),
                        appName = appName.trim(),
                        versionName = versionName.trim(),
                        versionCode = versionCode,
                        apkSourceUrl = apkSourceUrl.trim(),
                        sessionProfilePrefix = "isolated"
                    )
                ).fold(
                    onSuccess = { resp ->
                        resp.results.associate { item ->
                            item.vmId to (DeliveryStatus.fromString(item.status) to item.message.ifBlank { "Completed by gateway" })
                        }
                    },
                    onFailure = { err ->
                        activeIds.associateWith {
                            DeliveryStatus.HOST_OFFLINE to "Gateway offline: ${err.message ?: "unreachable"}"
                        }
                    }
                )
            } else {
                emptyMap()
            }

        val now = System.currentTimeMillis()
        val appEntities = mutableListOf<LocalVmAppEntity>()
        val cmdEntities = mutableListOf<LocalVmCommandEntity>()

        targets.forEach { vm ->
            val recordId = "${vm.vmId}_$sanitizedPkg".take(128)
            val remote = remoteResults[vm.vmId]
            val (deliveryStatus, msg, installStatus) = when {
                vm.statusEnum == VmStatus.UNPROVISIONED -> Triple(
                    DeliveryStatus.HOST_OFFLINE,
                    "Slot ${vm.vmId} unprovisioned",
                    InstallStatus.PENDING
                )
                remote != null -> Triple(
                    remote.first,
                    remote.second.ifBlank { "Processed by gateway" },
                    when {
                        remote.first == DeliveryStatus.COMPLETED && commandType == CommandType.UNINSTALL_APK -> InstallStatus.UNINSTALLED
                        remote.first == DeliveryStatus.COMPLETED -> InstallStatus.INSTALLED
                        remote.first == DeliveryStatus.FAILED -> InstallStatus.FAILED
                        else -> InstallStatus.PENDING
                    }
                )
                else -> Triple(
                    DeliveryStatus.QUEUED,
                    "Queued locally for ${vm.vmId} (isolated profile iso_${vm.vmId})",
                    if (commandType == CommandType.UNINSTALL_APK) InstallStatus.UNINSTALLED else InstallStatus.PENDING
                )
            }

            appEntities += LocalVmAppEntity(
                id = recordId,
                vmId = vm.vmId,
                packageName = packageName.trim().take(160),
                appName = appName.trim().take(80),
                versionName = versionName.trim().take(32),
                versionCode = versionCode,
                apkSourceUrl = apkSourceUrl.trim().take(500),
                sessionProfileId = "iso_${vm.vmId}_$sanitizedPkg".take(80),
                installStatus = installStatus.name,
                lastError = if (installStatus == InstallStatus.FAILED) msg.take(300) else "",
                createdAtMs = now,
                updatedAtMs = now
            )
            cmdEntities += LocalVmCommandEntity(
                id = "apk_" + UUID.randomUUID().toString().replace("-", "").take(16),
                vmId = vm.vmId,
                commandType = commandType.name,
                targetPayload = apkSourceUrl.trim().take(1024),
                targetPackage = packageName.trim().take(160),
                deliveryStatus = deliveryStatus.name,
                statusMessage = msg.take(300),
                createdAtMs = now,
                updatedAtMs = now
            )
        }
        dao.upsertApps(appEntities)
        dao.insertCommands(cmdEntities)
        logLocal(
            action = commandType.name,
            targetScope = "${targets.size} VMs (${packageName.trim()})",
            details = "${commandType.label} for $appName v$versionName across ${targets.size} VM slots",
            result = if (gatewayBaseUrl.isBlank()) "QUEUED" else "SUCCESS"
        )
    }

    override suspend fun assignGmailAccountsBatch(assignments: Map<String, String>): Result<Int> = runCatching {
        if (assignments.isEmpty()) return@runCatching 0
        if (dao.countVms() == 0) {
            initializeHundredSlots()
        }
        val currentMap = dao.observeVmsOnce().associateBy { it.vmId }
        val now = System.currentTimeMillis()
        val updated = assignments.mapNotNull { (vmId, rawEmail) ->
            val existing = currentMap[vmId] ?: return@mapNotNull null
            val cleanEmail = rawEmail.trim().take(160)
            val nextStatus = when {
                cleanEmail.isBlank() -> VmAccountStatus.UNASSIGNED.name
                existing.accountStatus == VmAccountStatus.APP_SIGNED_UP.name -> VmAccountStatus.APP_SIGNED_UP.name
                existing.accountStatus == VmAccountStatus.PLAY_SIGNED_IN.name &&
                    existing.assignedGmail.equals(cleanEmail, ignoreCase = true) -> VmAccountStatus.PLAY_SIGNED_IN.name
                else -> VmAccountStatus.GMAIL_ASSIGNED.name
            }
            existing.copy(
                assignedGmail = cleanEmail,
                accountStatus = nextStatus,
                updatedAtMs = now
            )
        }
        if (updated.isNotEmpty()) {
            dao.upsertVms(updated)
            logLocal(
                action = "ASSIGN_GMAIL_ACCOUNTS",
                targetScope = "${updated.size} VMs",
                details = "Assigned individual Gmail labels to ${updated.size} VM slots",
                result = "SUCCESS"
            )
        }
        updated.size
    }

    override suspend fun openGoogleSignInOnVms(
        targets: List<VmSlot>,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Map<String, DeliveryStatus>> = runCatching {
        val remoteResults = mutableMapOf<String, Pair<DeliveryStatus, String>>()
        targets.filter { it.statusEnum != VmStatus.UNPROVISIONED }
            .groupBy { it.hostUrl.ifBlank { gatewayBaseUrl }.trim() }
            .forEach { (endpoint, groupTargets) ->
                if (endpoint.isNotBlank()) {
                    val activeIds = groupTargets.map { it.vmId }
                    gatewayClient.openGoogleSignIn(endpoint, gatewayToken, activeIds).fold(
                        onSuccess = { resp ->
                            resp.results.forEach { item ->
                                remoteResults[item.vmId] =
                                    DeliveryStatus.fromString(item.status) to item.message.ifBlank { "Google Sign-In opened" }
                            }
                        },
                        onFailure = { err ->
                            activeIds.forEach { id ->
                                remoteResults[id] =
                                    DeliveryStatus.HOST_OFFLINE to "Gateway offline: ${err.message ?: "unreachable"}"
                            }
                        }
                    )
                }
            }

        val now = System.currentTimeMillis()
        val outcomeMap = mutableMapOf<String, DeliveryStatus>()
        val entities = targets.map { vm ->
            val (status, msg) = when {
                vm.statusEnum == VmStatus.UNPROVISIONED ->
                    DeliveryStatus.HOST_OFFLINE to "Slot ${vm.vmId} unprovisioned"
                remoteResults.containsKey(vm.vmId) -> remoteResults.getValue(vm.vmId)
                vm.statusEnum == VmStatus.OFFLINE || vm.statusEnum == VmStatus.ERROR ->
                    DeliveryStatus.HOST_OFFLINE to "VM ${vm.vmId} is ${vm.statusEnum.label}"
                else ->
                    DeliveryStatus.QUEUED to "Queued locally awaiting HTTPS host gateway"
            }
            outcomeMap[vm.vmId] = status
            LocalVmCommandEntity(
                id = "signin_" + UUID.randomUUID().toString().replace("-", "").take(16),
                vmId = vm.vmId,
                commandType = CommandType.OPEN_GOOGLE_SIGNIN.name,
                targetPayload = vm.assignedGmail.take(160),
                targetPackage = "com.google.android.gms",
                deliveryStatus = status.name,
                statusMessage = msg.take(300),
                createdAtMs = now,
                updatedAtMs = now
            )
        }
        dao.insertCommands(entities)
        val opened = outcomeMap.values.count { it == DeliveryStatus.DELIVERED || it == DeliveryStatus.OPENED }
        logLocal(
            action = "OPEN_GOOGLE_SIGNIN",
            targetScope = if (targets.size == 100) "ALL_100_VMS" else "${targets.size} VMs",
            details = "Google Sign-In prompt ($opened opened, ${targets.size - opened} queued/offline)",
            result = if (opened > 0) "SUCCESS" else "QUEUED"
        )
        outcomeMap
    }

    override suspend fun clearAppSessionOnVms(
        targets: List<VmSlot>,
        packageName: String,
        appName: String,
        openAfterClear: Boolean,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Map<String, DeliveryStatus>> = runCatching {
        val cleanPkg = packageName.trim().take(160)
        val cleanName = appName.trim().ifBlank { cleanPkg }.take(80)
        val remoteResults = mutableMapOf<String, Pair<DeliveryStatus, String>>()
        targets.filter { it.statusEnum != VmStatus.UNPROVISIONED }
            .groupBy { it.hostUrl.ifBlank { gatewayBaseUrl }.trim() }
            .forEach { (endpoint, groupTargets) ->
                if (endpoint.isNotBlank()) {
                    val activeIds = groupTargets.map { it.vmId }
                    gatewayClient.clearAppSession(endpoint, gatewayToken, activeIds, cleanPkg, openAfterClear)
                        .fold(
                            onSuccess = { resp ->
                                resp.results.forEach { item ->
                                    remoteResults[item.vmId] =
                                        DeliveryStatus.fromString(item.status) to item.message.ifBlank { "Fresh signup session ready" }
                                }
                            },
                            onFailure = { err ->
                                activeIds.forEach { id ->
                                    remoteResults[id] =
                                        DeliveryStatus.HOST_OFFLINE to "Gateway offline: ${err.message ?: "unreachable"}"
                                }
                            }
                        )
                }
            }

        val now = System.currentTimeMillis()
        val outcomeMap = mutableMapOf<String, DeliveryStatus>()
        val entities = targets.map { vm ->
            val (status, msg) = when {
                vm.statusEnum == VmStatus.UNPROVISIONED ->
                    DeliveryStatus.HOST_OFFLINE to "Slot ${vm.vmId} unprovisioned"
                remoteResults.containsKey(vm.vmId) -> remoteResults.getValue(vm.vmId)
                vm.statusEnum == VmStatus.OFFLINE || vm.statusEnum == VmStatus.ERROR ->
                    DeliveryStatus.HOST_OFFLINE to "VM ${vm.vmId} is ${vm.statusEnum.label}"
                else ->
                    DeliveryStatus.QUEUED to "Queued fresh-session reset awaiting HTTPS host gateway"
            }
            outcomeMap[vm.vmId] = status
            LocalVmCommandEntity(
                id = "reset_" + UUID.randomUUID().toString().replace("-", "").take(16),
                vmId = vm.vmId,
                commandType = CommandType.LAUNCH_APP.name,
                targetPayload = "Fresh Signup: $cleanName",
                targetPackage = cleanPkg,
                deliveryStatus = status.name,
                statusMessage = msg.take(300),
                createdAtMs = now,
                updatedAtMs = now
            )
        }
        dao.insertCommands(entities)
        val readyCount = outcomeMap.values.count {
            it == DeliveryStatus.DELIVERED || it == DeliveryStatus.OPENED || it == DeliveryStatus.COMPLETED
        }
        logLocal(
            action = "RESET_APP_SESSION_SIGNUP",
            targetScope = if (targets.size == 100) "ALL_100_VMS" else "${targets.size} VMs",
            details = "Reset & open $cleanName ($cleanPkg): $readyCount ready, ${targets.size - readyCount} queued/offline",
            result = if (readyCount > 0) "SUCCESS" else "QUEUED"
        )
        outcomeMap
    }

    override suspend fun syncVmTelemetry(items: List<GatewayVmTelemetryItem>): Result<Int> = runCatching {
        if (items.isEmpty()) return@runCatching 0
        if (dao.countVms() == 0) {
            initializeHundredSlots()
        }
        val currentMap = dao.observeVmsOnce().associateBy { it.vmId }
        val now = System.currentTimeMillis()
        val updated = items.mapNotNull { item ->
            val existing = currentMap[item.vmId] ?: return@mapNotNull null
            val detectedGmail = item.signedInGmail.trim().take(160)
            val resolvedGmail = existing.assignedGmail.ifBlank { detectedGmail }
            val resolvedAccountStatus = when {
                detectedGmail.isNotBlank() && existing.accountStatus != VmAccountStatus.APP_SIGNED_UP.name ->
                    VmAccountStatus.PLAY_SIGNED_IN.name
                else -> existing.accountStatus
            }
            existing.copy(
                status = VmStatus.fromString(item.status).name,
                connectivity = VmConnectivity.fromString(item.connectivity).name,
                ipAddress = item.ipAddress.trim().take(64).ifBlank { existing.ipAddress },
                androidVersion = item.androidVersion.trim().take(32).ifBlank { existing.androidVersion },
                batteryPercent = item.batteryPercent ?: existing.batteryPercent,
                assignedGmail = resolvedGmail,
                accountStatus = resolvedAccountStatus,
                lastSeenAtMs = now,
                updatedAtMs = now
            )
        }
        if (updated.isNotEmpty()) {
            dao.upsertVms(updated)
            logLocal(
                action = "SYNC_HOST_TELEMETRY",
                targetScope = "${updated.size} VMs",
                details = "Synced live status, IPs & signed-in Gmail state for ${updated.size} enrolled VMs",
                result = "SUCCESS"
            )
        }
        updated.size
    }

    private suspend fun logLocal(
        action: String,
        targetScope: String,
        details: String,
        result: String
    ) {
        dao.insertAuditLog(
            LocalAuditLogEntity(
                id = "log_" + UUID.randomUUID().toString().replace("-", "").take(16),
                actorEmail = actorLabel,
                action = action.take(64),
                targetScope = targetScope.take(128),
                details = details.take(500),
                result = result.take(24),
                createdAtMs = System.currentTimeMillis()
            )
        )
    }
}
