package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class VmStatus(val label: String) {
    UNPROVISIONED("Unprovisioned"),
    REGISTERED("Registered"),
    PROVISIONED("Provisioned"),
    ONLINE("Online"),
    RUNNING("Running"),
    OFFLINE("Offline"),
    ERROR("Error");

    companion object {
        fun fromString(value: String): VmStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNPROVISIONED
    }
}

enum class VmConnectivity(val label: String) {
    DISCONNECTED("Disconnected"),
    WIFI("Wi-Fi"),
    ETHERNET("Ethernet"),
    CELLULAR("Cellular"),
    UNKNOWN("Unknown");

    companion object {
        fun fromString(value: String): VmConnectivity =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DISCONNECTED
    }
}

enum class CommandType(val label: String) {
    START("Start VM"),
    STOP("Stop VM"),
    RESTART("Restart VM"),
    OPEN_URL("Open URL"),
    LAUNCH_APP("Open App"),
    OPEN_GOOGLE_SIGNIN("Google Sign-In"),
    INSTALL_APK("Install APK"),
    UPDATE_APK("Update APK"),
    UNINSTALL_APK("Uninstall APK");

    companion object {
        fun fromString(value: String): CommandType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OPEN_URL
    }
}

enum class DeliveryStatus(val label: String) {
    QUEUED("Queued"),
    DELIVERED("Delivered"),
    OPENED("Opened"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    HOST_OFFLINE("Host Offline");

    companion object {
        fun fromString(value: String): DeliveryStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: QUEUED
    }
}

enum class InstallStatus(val label: String) {
    PENDING("Pending"),
    INSTALLED("Installed"),
    UPDATE_AVAILABLE("Update Available"),
    FAILED("Failed"),
    UNINSTALLED("Uninstalled");

    companion object {
        fun fromString(value: String): InstallStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING
    }
}

enum class VmAccountStatus(val label: String) {
    UNASSIGNED("No Gmail"),
    GMAIL_ASSIGNED("Gmail Set"),
    PLAY_SIGNED_IN("Play Signed-In"),
    APP_SIGNED_UP("Signed Up");

    companion object {
        fun fromString(value: String): VmAccountStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNASSIGNED
    }
}

@IgnoreExtraProperties
data class AdminProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "OWNER_ADMIN",
    val fleetInitialized: Boolean = false,
    val gatewayBaseUrl: String = "",
    val webrtcSignalingUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VmSlot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val vmId: String = "",
    val slotNumber: Long = 1L,
    val name: String = "",
    val groupId: String = "unassigned",
    val androidVersion: String = "Unassigned",
    val status: String = VmStatus.UNPROVISIONED.name,
    val connectivity: String = VmConnectivity.DISCONNECTED.name,
    val batteryPercent: Long? = null,
    val ipAddress: String = "",
    val assignedGmail: String = "",
    val accountStatus: String = VmAccountStatus.UNASSIGNED.name,
    val hostUrl: String = "",
    val streamUrl: String = "",
    val lastError: String = "",
    val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val statusEnum: VmStatus get() = VmStatus.fromString(status)
    val connectivityEnum: VmConnectivity get() = VmConnectivity.fromString(connectivity)
    val accountStatusEnum: VmAccountStatus get() = VmAccountStatus.fromString(accountStatus)

    fun toWriteMap(isCreate: Boolean): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "userId" to userId,
            "vmId" to vmId,
            "slotNumber" to slotNumber,
            "name" to name,
            "groupId" to groupId,
            "androidVersion" to androidVersion,
            "status" to status,
            "connectivity" to connectivity,
            "ipAddress" to ipAddress.trim().take(64),
            "assignedGmail" to assignedGmail.trim().take(160),
            "accountStatus" to VmAccountStatus.fromString(accountStatus).name,
            "hostUrl" to hostUrl,
            "streamUrl" to streamUrl,
            "lastError" to lastError,
            "createdAt" to if (isCreate || createdAt == null) FieldValue.serverTimestamp() else createdAt,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        batteryPercent?.let { map["batteryPercent"] = it }
        lastSeenAt?.let { map["lastSeenAt"] = it }
        return map
    }
}

@IgnoreExtraProperties
data class VmGroup(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val colorHex: String = "#8B5CF6",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VmCommand(
    @DocumentId val id: String = "",
    val userId: String = "",
    val vmId: String = "",
    val commandType: String = CommandType.OPEN_URL.name,
    val targetPayload: String = "",
    val targetPackage: String = "",
    val deliveryStatus: String = DeliveryStatus.QUEUED.name,
    val statusMessage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val commandTypeEnum: CommandType get() = CommandType.fromString(commandType)
    val deliveryStatusEnum: DeliveryStatus get() = DeliveryStatus.fromString(deliveryStatus)
}

@IgnoreExtraProperties
data class VmApp(
    @DocumentId val id: String = "",
    val userId: String = "",
    val vmId: String = "",
    val packageName: String = "",
    val appName: String = "",
    val versionName: String = "",
    val versionCode: Long = 1L,
    val apkSourceUrl: String = "",
    val sessionProfileId: String = "",
    val installStatus: String = InstallStatus.PENDING.name,
    val lastError: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val installStatusEnum: InstallStatus get() = InstallStatus.fromString(installStatus)
}

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val actorEmail: String = "",
    val action: String = "",
    val targetScope: String = "",
    val details: String = "",
    val result: String = "SUCCESS",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class SavedPhoneApp(
    val packageName: String,
    val appName: String,
    val versionName: String = "1.0",
    val versionCode: Long = 1L
)
