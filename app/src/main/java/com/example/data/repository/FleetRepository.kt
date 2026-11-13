package com.example.data.repository

import android.content.Context
import com.example.R
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class FleetRepository(
    private val db: FirebaseFirestore,
    private val gatewayClient: VmGatewayClient = VmGatewayClient(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userDoc(userId: String) = db.collection("users").document(userId)

    fun observeProfile(userId: String): Flow<AdminProfile?> {
        val docRef = userDoc(userId).collection("profile").document("admin")
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(AdminProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.GET, docRef.path, userId)
                throw error
            }
    }

    fun observeVms(userId: String): Flow<List<VmSlot>> {
        val colRef = userDoc(userId).collection("vms")
        return colRef.orderBy("slotNumber", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(VmSlot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, colRef.path, userId)
                throw error
            }
    }

    fun observeGroups(userId: String): Flow<List<VmGroup>> {
        val colRef = userDoc(userId).collection("vm_groups")
        return colRef.orderBy("name", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(VmGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, colRef.path, userId)
                throw error
            }
    }

    fun observeCommands(userId: String): Flow<List<VmCommand>> {
        val colRef = userDoc(userId).collection("vm_commands")
        return colRef.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(150)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(VmCommand::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, colRef.path, userId)
                throw error
            }
    }

    fun observeApps(userId: String): Flow<List<VmApp>> {
        val colRef = userDoc(userId).collection("vm_apps")
        return colRef.orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(200)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(VmApp::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, colRef.path, userId)
                throw error
            }
    }

    fun observeAuditLogs(userId: String): Flow<List<AuditLog>> {
        val colRef = userDoc(userId).collection("audit_logs")
        return colRef.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(120)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, colRef.path, userId)
                throw error
            }
    }

    suspend fun ensureAdminProfile(userId: String, email: String, displayName: String): Result<AdminProfile> {
        val docRef = userDoc(userId).collection("profile").document("admin")
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(AdminProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)!!
                Result.success(existing)
            } else {
                val payload = mapOf(
                    "userId" to userId,
                    "email" to email.take(160),
                    "displayName" to displayName.ifBlank { "Administrator" }.take(100),
                    "role" to "OWNER_ADMIN",
                    "fleetInitialized" to false,
                    "gatewayBaseUrl" to "",
                    "webrtcSignalingUrl" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                val created = docRef.get().await().toObject(
                    AdminProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: AdminProfile(
                    id = "admin",
                    userId = userId,
                    email = email,
                    displayName = displayName,
                    role = "OWNER_ADMIN",
                    fleetInitialized = false
                )
                Result.success(created)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun initializeHundredSlots(userId: String, actorEmail: String): Result<Unit> {
        val vmsCol = userDoc(userId).collection("vms")
        val profileRef = userDoc(userId).collection("profile").document("admin")
        return try {
            val existingVms = vmsCol.limit(1).get().await()
            if (existingVms.isEmpty) {
                val slots = (1..100).map { index ->
                    val vmId = "VM-%03d".format(index)
                    VmSlot(
                        userId = userId,
                        vmId = vmId,
                        slotNumber = index.toLong(),
                        name = "Slot $vmId",
                        groupId = "unassigned",
                        androidVersion = "Unassigned",
                        status = VmStatus.UNPROVISIONED.name,
                        connectivity = VmConnectivity.DISCONNECTED.name,
                        hostUrl = "",
                        streamUrl = "",
                        lastError = ""
                    )
                }
                // Commit in two 50-doc batches to stay well within Firestore 500-op batch limits
                slots.chunked(50).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { slot ->
                        batch.set(vmsCol.document(slot.vmId), slot.toWriteMap(isCreate = true))
                    }
                    batch.commit().await()
                }
            }
            profileRef.update(
                mapOf(
                    "fleetInitialized" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            appendAuditLog(
                userId = userId,
                actorEmail = actorEmail,
                action = "INIT_100_SLOTS",
                targetScope = "VM-001..VM-100",
                details = "Initialized 100 unprovisioned cloud Android VM slots",
                result = "SUCCESS"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, vmsCol.path, auth)
            Result.failure(e)
        }
    }

    suspend fun saveVmSlot(userId: String, actorEmail: String, slot: VmSlot): Result<Unit> {
        val docRef = userDoc(userId).collection("vms").document(slot.vmId)
        return try {
            val isCreate = slot.createdAt == null
            docRef.set(slot.copy(userId = userId).toWriteMap(isCreate = isCreate)).await()
            appendAuditLog(
                userId = userId,
                actorEmail = actorEmail,
                action = "UPDATE_VM_SLOT",
                targetScope = slot.vmId,
                details = "Updated ${slot.name} (${slot.status}, group=${slot.groupId})",
                result = "SUCCESS"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun createGroup(
        userId: String,
        actorEmail: String,
        name: String,
        description: String,
        colorHex: String
    ): Result<String> {
        val id = "grp_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val docRef = userDoc(userId).collection("vm_groups").document(id)
        return try {
            val payload = mapOf(
                "userId" to userId,
                "name" to name.trim().take(40),
                "description" to description.trim().take(200),
                "colorHex" to colorHex.take(16),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            appendAuditLog(
                userId = userId,
                actorEmail = actorEmail,
                action = "CREATE_GROUP",
                targetScope = name.trim(),
                details = "Created device group ${name.trim()}",
                result = "SUCCESS"
            )
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun assignGroupToVms(
        userId: String,
        actorEmail: String,
        vmIds: List<String>,
        groupId: String
    ): Result<Unit> {
        val vmsCol = userDoc(userId).collection("vms")
        return try {
            vmIds.chunked(50).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { vmId ->
                    batch.update(
                        vmsCol.document(vmId),
                        mapOf("groupId" to groupId, "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
                batch.commit().await()
            }
            appendAuditLog(
                userId = userId,
                actorEmail = actorEmail,
                action = "ASSIGN_GROUP",
                targetScope = "${vmIds.size} VMs",
                details = "Assigned ${vmIds.size} slots to group $groupId",
                result = "SUCCESS"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, vmsCol.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateGatewaySettings(
        userId: String,
        actorEmail: String,
        gatewayBaseUrl: String,
        webrtcSignalingUrl: String
    ): Result<Unit> {
        val docRef = userDoc(userId).collection("profile").document("admin")
        return try {
            docRef.update(
                mapOf(
                    "gatewayBaseUrl" to gatewayBaseUrl.trim().take(500),
                    "webrtcSignalingUrl" to webrtcSignalingUrl.trim().take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            appendAuditLog(
                userId = userId,
                actorEmail = actorEmail,
                action = "UPDATE_GATEWAY_CONFIG",
                targetScope = "Host Gateway",
                details = "Updated HTTPS gateway endpoint (${gatewayBaseUrl.ifBlank { "cleared" }})",
                result = "SUCCESS"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun dispatchLifecycleCommand(
        userId: String,
        actorEmail: String,
        vm: VmSlot,
        commandType: CommandType,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<DeliveryStatus> {
        require(commandType in setOf(CommandType.START, CommandType.STOP, CommandType.RESTART))
        val targetGateway = vm.hostUrl.ifBlank { gatewayBaseUrl }
        val (status, message) = if (vm.statusEnum == VmStatus.UNPROVISIONED) {
            DeliveryStatus.HOST_OFFLINE to "Slot ${vm.vmId} is unprovisioned. Enroll a Linux host agent first."
        } else if (targetGateway.isBlank()) {
            DeliveryStatus.QUEUED to "Command queued in Firestore; configure an HTTPS Host Gateway to execute."
        } else {
            val remote = gatewayClient.sendLifecycle(targetGateway, gatewayToken, vm.vmId, commandType.name)
            remote.fold(
                onSuccess = { resp ->
                    if (resp.accepted) DeliveryStatus.DELIVERED to resp.message.ifBlank { "Accepted by host gateway" }
                    else DeliveryStatus.FAILED to resp.message.ifBlank { "Rejected by host gateway" }
                },
                onFailure = { err ->
                    DeliveryStatus.HOST_OFFLINE to "Host unreachable: ${err.message ?: "connection failed"}"
                }
            )
        }

        val cmdId = "cmd_" + UUID.randomUUID().toString().replace("-", "").take(16)
        val cmdRef = userDoc(userId).collection("vm_commands").document(cmdId)
        return try {
            cmdRef.set(
                mapOf(
                    "userId" to userId,
                    "vmId" to vm.vmId,
                    "commandType" to commandType.name,
                    "targetPayload" to "",
                    "targetPackage" to "",
                    "deliveryStatus" to status.name,
                    "statusMessage" to message.take(300),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            appendAuditLog(
                userId = userId,
                actorEmail = actorEmail,
                action = "VM_${commandType.name}",
                targetScope = vm.vmId,
                details = message,
                result = status.name.let { if (it == "DELIVERED") "SUCCESS" else it }
            )
            Result.success(status)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, cmdRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun dispatchUrlToVms(
        userId: String,
        actorEmail: String,
        targets: List<VmSlot>,
        url: String,
        targetPackage: String,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Map<String, DeliveryStatus>> {
        val commandsCol = userDoc(userId).collection("vm_commands")
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
                                    val st = DeliveryStatus.fromString(item.status)
                                    remoteResults[item.vmId] = st to item.message.ifBlank { "Dispatched via HTTPS gateway" }
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

        return try {
            val outcomeMap = mutableMapOf<String, DeliveryStatus>()
            targets.chunked(50).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { vm ->
                    val (status, msg) = when {
                        vm.statusEnum == VmStatus.UNPROVISIONED ->
                            DeliveryStatus.HOST_OFFLINE to "Slot ${vm.vmId} unprovisioned"
                        remoteResults.containsKey(vm.vmId) -> remoteResults.getValue(vm.vmId)
                        vm.statusEnum == VmStatus.OFFLINE || vm.statusEnum == VmStatus.ERROR ->
                            DeliveryStatus.HOST_OFFLINE to "VM ${vm.vmId} is ${vm.statusEnum.label}"
                        else ->
                            DeliveryStatus.QUEUED to "Queued in Firestore awaiting HTTPS host gateway"
                    }
                    outcomeMap[vm.vmId] = status
                    val cmdId = "url_" + UUID.randomUUID().toString().replace("-", "").take(16)
                    batch.set(
                        commandsCol.document(cmdId),
                        mapOf(
                            "userId" to userId,
                            "vmId" to vm.vmId,
                            "commandType" to CommandType.OPEN_URL.name,
                            "targetPayload" to normalizedUrl,
                            "targetPackage" to normalizedPkg,
                            "deliveryStatus" to status.name,
                            "statusMessage" to msg.take(300),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }

            val deliveredCount = outcomeMap.values.count { it == DeliveryStatus.DELIVERED || it == DeliveryStatus.OPENED }
            appendAuditLog(
                userId = userId,
                actorEmail = actorEmail,
                action = "DISPATCH_URL",
                targetScope = if (targets.size == 100) "ALL_100_VMS" else "${targets.size} VMs",
                details = "URL $normalizedUrl ($deliveredCount active deliveries, ${targets.size - deliveredCount} queued/offline)",
                result = if (deliveredCount > 0) "SUCCESS" else "HOST_OFFLINE"
            )
            Result.success(outcomeMap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commandsCol.path, auth)
            Result.failure(e)
        }
    }

    suspend fun launchAppOnVms(
        userId: String,
        actorEmail: String,
        targets: List<VmSlot>,
        packageName: String,
        appName: String,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Map<String, DeliveryStatus>> {
        val commandsCol = userDoc(userId).collection("vm_commands")
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
                                    val st = DeliveryStatus.fromString(item.status)
                                    remoteResults[item.vmId] = st to item.message.ifBlank { "Launched via HTTPS gateway" }
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

        return try {
            val outcomeMap = mutableMapOf<String, DeliveryStatus>()
            targets.chunked(50).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { vm ->
                    val (status, msg) = when {
                        vm.statusEnum == VmStatus.UNPROVISIONED ->
                            DeliveryStatus.HOST_OFFLINE to "Slot ${vm.vmId} unprovisioned"
                        remoteResults.containsKey(vm.vmId) -> remoteResults.getValue(vm.vmId)
                        vm.statusEnum == VmStatus.OFFLINE || vm.statusEnum == VmStatus.ERROR ->
                            DeliveryStatus.HOST_OFFLINE to "VM ${vm.vmId} is ${vm.statusEnum.label}"
                        else ->
                            DeliveryStatus.QUEUED to "Queued in Firestore awaiting HTTPS host gateway"
                    }
                    outcomeMap[vm.vmId] = status
                    val cmdId = "appopen_" + UUID.randomUUID().toString().replace("-", "").take(16)
                    batch.set(
                        commandsCol.document(cmdId),
                        mapOf(
                            "userId" to userId,
                            "vmId" to vm.vmId,
                            "commandType" to CommandType.LAUNCH_APP.name,
                            "targetPayload" to cleanName,
                            "targetPackage" to cleanPkg,
                            "deliveryStatus" to status.name,
                            "statusMessage" to msg.take(300),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }

            val openedCount = outcomeMap.values.count { it == DeliveryStatus.DELIVERED || it == DeliveryStatus.OPENED }
            appendAuditLog(
                userId = userId,
                actorEmail = actorEmail,
                action = "LAUNCH_APP",
                targetScope = if (targets.size == 100) "ALL_100_VMS" else "${targets.size} VMs",
                details = "Open $cleanName ($cleanPkg): $openedCount opened, ${targets.size - openedCount} queued/offline",
                result = if (openedCount > 0) "SUCCESS" else "HOST_OFFLINE"
            )
            Result.success(outcomeMap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commandsCol.path, auth)
            Result.failure(e)
        }
    }

    suspend fun pushPhoneAppToVms(
        userId: String,
        actorEmail: String,
        targets: List<VmSlot>,
        app: SavedPhoneApp,
        apkFiles: List<File>,
        openAfterInstall: Boolean,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Map<String, DeliveryStatus>> {
        val appsCol = userDoc(userId).collection("vm_apps")
        val cmdsCol = userDoc(userId).collection("vm_commands")
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
                                val st = DeliveryStatus.fromString(item.status)
                                remoteResults[item.vmId] = st to item.message.ifBlank { "Installed on VM" }
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

        return try {
            val outcomeMap = mutableMapOf<String, DeliveryStatus>()
            targets.chunked(25).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { vm ->
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
                    val recordId = "${vm.vmId}_$sanitizedPkg".take(128)
                    batch.set(
                        appsCol.document(recordId),
                        mapOf(
                            "userId" to userId,
                            "vmId" to vm.vmId,
                            "packageName" to cleanPkg,
                            "appName" to app.appName.take(80),
                            "versionName" to app.versionName.take(32),
                            "versionCode" to app.versionCode,
                            "apkSourceUrl" to "phone://$cleanPkg",
                            "sessionProfileId" to "iso_${vm.vmId}_$sanitizedPkg".take(80),
                            "installStatus" to installStatus.name,
                            "lastError" to if (installStatus == InstallStatus.FAILED) msg.take(300) else "",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    val cmdId = "push_" + UUID.randomUUID().toString().replace("-", "").take(16)
                    batch.set(
                        cmdsCol.document(cmdId),
                        mapOf(
                            "userId" to userId,
                            "vmId" to vm.vmId,
                            "commandType" to (if (openAfterInstall) CommandType.LAUNCH_APP.name else CommandType.INSTALL_APK.name),
                            "targetPayload" to app.appName.take(80),
                            "targetPackage" to cleanPkg,
                            "deliveryStatus" to status.name,
                            "statusMessage" to msg.take(300),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }

            val okCount = outcomeMap.values.count {
                it == DeliveryStatus.COMPLETED || it == DeliveryStatus.OPENED || it == DeliveryStatus.DELIVERED
            }
            appendAuditLog(
                userId = userId,
                actorEmail = actorEmail,
                action = if (openAfterInstall) "PUSH_INSTALL_AND_OPEN" else "PUSH_INSTALL_PHONE_APK",
                targetScope = if (targets.size == 100) "ALL_100_VMS" else "${targets.size} VMs",
                details = "${app.appName} ($cleanPkg): $okCount succeeded, ${targets.size - okCount} queued/offline",
                result = if (okCount > 0) "SUCCESS" else "QUEUED"
            )
            Result.success(outcomeMap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, appsCol.path, auth)
            Result.failure(e)
        }
    }

    suspend fun dispatchAppAction(
        userId: String,
        actorEmail: String,
        targets: List<VmSlot>,
        commandType: CommandType,
        packageName: String,
        appName: String,
        versionName: String,
        versionCode: Long,
        apkSourceUrl: String,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Unit> {
        require(commandType in setOf(CommandType.INSTALL_APK, CommandType.UPDATE_APK, CommandType.UNINSTALL_APK))
        val appsCol = userDoc(userId).collection("vm_apps")
        val cmdsCol = userDoc(userId).collection("vm_commands")
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

        return try {
            targets.chunked(25).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { vm ->
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
                            "Queued in Firestore for ${vm.vmId} (isolated profile iso_${vm.vmId})",
                            if (commandType == CommandType.UNINSTALL_APK) InstallStatus.UNINSTALLED else InstallStatus.PENDING
                        )
                    }

                    val appRef = appsCol.document(recordId)
                    batch.set(
                        appRef,
                        mapOf(
                            "userId" to userId,
                            "vmId" to vm.vmId,
                            "packageName" to packageName.trim().take(160),
                            "appName" to appName.trim().take(80),
                            "versionName" to versionName.trim().take(32),
                            "versionCode" to versionCode,
                            "apkSourceUrl" to apkSourceUrl.trim().take(500),
                            "sessionProfileId" to "iso_${vm.vmId}_$sanitizedPkg".take(80),
                            "installStatus" to installStatus.name,
                            "lastError" to if (installStatus == InstallStatus.FAILED) msg.take(300) else "",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )

                    val cmdId = "apk_" + UUID.randomUUID().toString().replace("-", "").take(16)
                    batch.set(
                        cmdsCol.document(cmdId),
                        mapOf(
                            "userId" to userId,
                            "vmId" to vm.vmId,
                            "commandType" to commandType.name,
                            "targetPayload" to apkSourceUrl.trim().take(1024),
                            "targetPackage" to packageName.trim().take(160),
                            "deliveryStatus" to deliveryStatus.name,
                            "statusMessage" to msg.take(300),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }

            appendAuditLog(
                userId = userId,
                actorEmail = actorEmail,
                action = commandType.name,
                targetScope = "${targets.size} VMs (${packageName.trim()})",
                details = "${commandType.label} for $appName v$versionName across ${targets.size} VM slots",
                result = if (gatewayBaseUrl.isBlank()) "QUEUED" else "SUCCESS"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, appsCol.path, auth)
            Result.failure(e)
        }
    }

    suspend fun assignGmailAccountsBatch(
        userId: String,
        actorEmail: String,
        assignments: Map<String, String>
    ): Result<Int> {
        if (assignments.isEmpty()) return Result.success(0)
        val vmsCol = userDoc(userId).collection("vms")
        return try {
            val entries = assignments.entries.toList()
            entries.chunked(50).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { (vmId, rawEmail) ->
                    val cleanEmail = rawEmail.trim().take(160)
                    val nextStatus = if (cleanEmail.isBlank()) {
                        VmAccountStatus.UNASSIGNED.name
                    } else {
                        VmAccountStatus.GMAIL_ASSIGNED.name
                    }
                    batch.update(
                        vmsCol.document(vmId),
                        mapOf(
                            "assignedGmail" to cleanEmail,
                            "accountStatus" to nextStatus,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            appendAuditLog(
                userId = userId,
                actorEmail = actorEmail,
                action = "ASSIGN_GMAIL_ACCOUNTS",
                targetScope = "${assignments.size} VMs",
                details = "Assigned individual Gmail labels to ${assignments.size} VM slots",
                result = "SUCCESS"
            )
            Result.success(assignments.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, vmsCol.path, auth)
            Result.failure(e)
        }
    }

    suspend fun openGoogleSignInOnVms(
        userId: String,
        actorEmail: String,
        targets: List<VmSlot>,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Map<String, DeliveryStatus>> {
        val commandsCol = userDoc(userId).collection("vm_commands")
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

        return try {
            val outcomeMap = mutableMapOf<String, DeliveryStatus>()
            targets.chunked(50).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { vm ->
                    val (status, msg) = when {
                        vm.statusEnum == VmStatus.UNPROVISIONED ->
                            DeliveryStatus.HOST_OFFLINE to "Slot ${vm.vmId} unprovisioned"
                        remoteResults.containsKey(vm.vmId) -> remoteResults.getValue(vm.vmId)
                        vm.statusEnum == VmStatus.OFFLINE || vm.statusEnum == VmStatus.ERROR ->
                            DeliveryStatus.HOST_OFFLINE to "VM ${vm.vmId} is ${vm.statusEnum.label}"
                        else ->
                            DeliveryStatus.QUEUED to "Queued in Firestore awaiting HTTPS host gateway"
                    }
                    outcomeMap[vm.vmId] = status
                    val cmdId = "signin_" + UUID.randomUUID().toString().replace("-", "").take(16)
                    batch.set(
                        commandsCol.document(cmdId),
                        mapOf(
                            "userId" to userId,
                            "vmId" to vm.vmId,
                            "commandType" to CommandType.OPEN_GOOGLE_SIGNIN.name,
                            "targetPayload" to vm.assignedGmail.take(160),
                            "targetPackage" to "com.google.android.gms",
                            "deliveryStatus" to status.name,
                            "statusMessage" to msg.take(300),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            val openedCount = outcomeMap.values.count { it == DeliveryStatus.DELIVERED || it == DeliveryStatus.OPENED }
            appendAuditLog(
                userId = userId,
                actorEmail = actorEmail,
                action = "OPEN_GOOGLE_SIGNIN",
                targetScope = if (targets.size == 100) "ALL_100_VMS" else "${targets.size} VMs",
                details = "Google Sign-In prompt ($openedCount opened, ${targets.size - openedCount} queued/offline)",
                result = if (openedCount > 0) "SUCCESS" else "QUEUED"
            )
            Result.success(outcomeMap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commandsCol.path, auth)
            Result.failure(e)
        }
    }

    suspend fun clearAppSessionOnVms(
        userId: String,
        actorEmail: String,
        targets: List<VmSlot>,
        packageName: String,
        appName: String,
        openAfterClear: Boolean,
        gatewayBaseUrl: String,
        gatewayToken: String
    ): Result<Map<String, DeliveryStatus>> {
        val commandsCol = userDoc(userId).collection("vm_commands")
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

        return try {
            val outcomeMap = mutableMapOf<String, DeliveryStatus>()
            targets.chunked(50).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { vm ->
                    val (status, msg) = when {
                        vm.statusEnum == VmStatus.UNPROVISIONED ->
                            DeliveryStatus.HOST_OFFLINE to "Slot ${vm.vmId} unprovisioned"
                        remoteResults.containsKey(vm.vmId) -> remoteResults.getValue(vm.vmId)
                        vm.statusEnum == VmStatus.OFFLINE || vm.statusEnum == VmStatus.ERROR ->
                            DeliveryStatus.HOST_OFFLINE to "VM ${vm.vmId} is ${vm.statusEnum.label}"
                        else ->
                            DeliveryStatus.QUEUED to "Queued fresh-session reset in Firestore awaiting HTTPS host gateway"
                    }
                    outcomeMap[vm.vmId] = status
                    val cmdId = "reset_" + UUID.randomUUID().toString().replace("-", "").take(16)
                    batch.set(
                        commandsCol.document(cmdId),
                        mapOf(
                            "userId" to userId,
                            "vmId" to vm.vmId,
                            "commandType" to CommandType.LAUNCH_APP.name,
                            "targetPayload" to "Fresh Signup: $cleanName",
                            "targetPackage" to cleanPkg,
                            "deliveryStatus" to status.name,
                            "statusMessage" to msg.take(300),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            val readyCount = outcomeMap.values.count {
                it == DeliveryStatus.DELIVERED || it == DeliveryStatus.OPENED || it == DeliveryStatus.COMPLETED
            }
            appendAuditLog(
                userId = userId,
                actorEmail = actorEmail,
                action = "RESET_APP_SESSION_SIGNUP",
                targetScope = if (targets.size == 100) "ALL_100_VMS" else "${targets.size} VMs",
                details = "Reset & open $cleanName ($cleanPkg): $readyCount ready, ${targets.size - readyCount} queued/offline",
                result = if (readyCount > 0) "SUCCESS" else "QUEUED"
            )
            Result.success(outcomeMap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commandsCol.path, auth)
            Result.failure(e)
        }
    }

    suspend fun syncVmTelemetry(
        userId: String,
        actorEmail: String,
        items: List<GatewayVmTelemetryItem>
    ): Result<Int> {
        if (items.isEmpty()) return Result.success(0)
        val vmsCol = userDoc(userId).collection("vms")
        return try {
            items.chunked(50).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { item ->
                    val updateMap = mutableMapOf<String, Any>(
                        "status" to VmStatus.fromString(item.status).name,
                        "connectivity" to VmConnectivity.fromString(item.connectivity).name,
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    item.ipAddress.trim().take(64).takeIf { it.isNotEmpty() }?.let {
                        updateMap["ipAddress"] = it
                    }
                    item.androidVersion.trim().take(32).takeIf { it.isNotEmpty() }?.let {
                        updateMap["androidVersion"] = it
                    }
                    item.batteryPercent?.let { updateMap["batteryPercent"] = it.coerceIn(0L, 100L) }
                    item.signedInGmail.trim().take(160).takeIf { it.isNotEmpty() }?.let { email ->
                        updateMap["assignedGmail"] = email
                        updateMap["accountStatus"] = VmAccountStatus.PLAY_SIGNED_IN.name
                    }
                    batch.update(vmsCol.document(item.vmId), updateMap)
                }
                batch.commit().await()
            }
            appendAuditLog(
                userId = userId,
                actorEmail = actorEmail,
                action = "SYNC_HOST_TELEMETRY",
                targetScope = "${items.size} VMs",
                details = "Synced live status, IPs & signed-in Gmail state for ${items.size} enrolled VMs",
                result = "SUCCESS"
            )
            Result.success(items.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, vmsCol.path, auth)
            Result.failure(e)
        }
    }

    suspend fun appendAuditLog(
        userId: String,
        actorEmail: String,
        action: String,
        targetScope: String,
        details: String,
        result: String
    ) {
        val logId = "log_" + UUID.randomUUID().toString().replace("-", "").take(16)
        val docRef = userDoc(userId).collection("audit_logs").document(logId)
        try {
            docRef.set(
                mapOf(
                    "userId" to userId,
                    "actorEmail" to actorEmail.ifBlank { "admin@unknown" }.take(160),
                    "action" to action.take(64),
                    "targetScope" to targetScope.take(128),
                    "details" to details.take(500),
                    "result" to result.take(24),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
        }
    }

    private fun unwrapAndLog(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path, auth)
        }
    }
}
