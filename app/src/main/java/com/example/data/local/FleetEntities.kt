package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.AdminProfile
import com.example.data.model.AuditLog
import com.example.data.model.VmAccountStatus
import com.example.data.model.VmApp
import com.example.data.model.VmCommand
import com.example.data.model.VmGroup
import com.example.data.model.VmSlot
import com.google.firebase.Timestamp
import java.util.Date

private fun Long?.toTimestampOrNull(): Timestamp? =
    this?.takeIf { it > 0L }?.let { Timestamp(Date(it)) }

@Entity(tableName = "local_profile")
data class LocalProfileEntity(
    @PrimaryKey val id: String = "local_admin",
    val displayName: String = "Local Operator",
    val role: String = "LOCAL_ADMIN",
    val fleetInitialized: Boolean = false,
    val gatewayBaseUrl: String = "",
    val webrtcSignalingUrl: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    fun toDomain() = AdminProfile(
        id = id,
        userId = "local",
        email = "Local Device Mode",
        displayName = displayName,
        role = role,
        fleetInitialized = fleetInitialized,
        gatewayBaseUrl = gatewayBaseUrl,
        webrtcSignalingUrl = webrtcSignalingUrl,
        createdAt = createdAtMs.toTimestampOrNull(),
        updatedAt = updatedAtMs.toTimestampOrNull()
    )
}

@Entity(tableName = "local_vms")
data class LocalVmSlotEntity(
    @PrimaryKey val vmId: String,
    val slotNumber: Long,
    val name: String,
    val groupId: String,
    val androidVersion: String,
    val status: String,
    val connectivity: String,
    val batteryPercent: Long?,
    val ipAddress: String = "",
    val assignedGmail: String = "",
    val accountStatus: String = VmAccountStatus.UNASSIGNED.name,
    val hostUrl: String,
    val streamUrl: String,
    val lastError: String,
    val lastSeenAtMs: Long?,
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    fun toDomain() = VmSlot(
        id = vmId,
        userId = "local",
        vmId = vmId,
        slotNumber = slotNumber,
        name = name,
        groupId = groupId,
        androidVersion = androidVersion,
        status = status,
        connectivity = connectivity,
        batteryPercent = batteryPercent,
        ipAddress = ipAddress,
        assignedGmail = assignedGmail,
        accountStatus = accountStatus,
        hostUrl = hostUrl,
        streamUrl = streamUrl,
        lastError = lastError,
        lastSeenAt = lastSeenAtMs.toTimestampOrNull(),
        createdAt = createdAtMs.toTimestampOrNull(),
        updatedAt = updatedAtMs.toTimestampOrNull()
    )
}

@Entity(tableName = "local_vm_groups")
data class LocalVmGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val colorHex: String,
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    fun toDomain() = VmGroup(
        id = id,
        userId = "local",
        name = name,
        description = description,
        colorHex = colorHex,
        createdAt = createdAtMs.toTimestampOrNull(),
        updatedAt = updatedAtMs.toTimestampOrNull()
    )
}

@Entity(tableName = "local_vm_commands")
data class LocalVmCommandEntity(
    @PrimaryKey val id: String,
    val vmId: String,
    val commandType: String,
    val targetPayload: String,
    val targetPackage: String,
    val deliveryStatus: String,
    val statusMessage: String,
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    fun toDomain() = VmCommand(
        id = id,
        userId = "local",
        vmId = vmId,
        commandType = commandType,
        targetPayload = targetPayload,
        targetPackage = targetPackage,
        deliveryStatus = deliveryStatus,
        statusMessage = statusMessage,
        createdAt = createdAtMs.toTimestampOrNull(),
        updatedAt = updatedAtMs.toTimestampOrNull()
    )
}

@Entity(tableName = "local_vm_apps")
data class LocalVmAppEntity(
    @PrimaryKey val id: String,
    val vmId: String,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val apkSourceUrl: String,
    val sessionProfileId: String,
    val installStatus: String,
    val lastError: String,
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    fun toDomain() = VmApp(
        id = id,
        userId = "local",
        vmId = vmId,
        packageName = packageName,
        appName = appName,
        versionName = versionName,
        versionCode = versionCode,
        apkSourceUrl = apkSourceUrl,
        sessionProfileId = sessionProfileId,
        installStatus = installStatus,
        lastError = lastError,
        createdAt = createdAtMs.toTimestampOrNull(),
        updatedAt = updatedAtMs.toTimestampOrNull()
    )
}

@Entity(tableName = "local_audit_logs")
data class LocalAuditLogEntity(
    @PrimaryKey val id: String,
    val actorEmail: String,
    val action: String,
    val targetScope: String,
    val details: String,
    val result: String,
    val createdAtMs: Long
) {
    fun toDomain() = AuditLog(
        id = id,
        userId = "local",
        actorEmail = actorEmail,
        action = action,
        targetScope = targetScope,
        details = details,
        result = result,
        createdAt = createdAtMs.toTimestampOrNull()
    )
}
