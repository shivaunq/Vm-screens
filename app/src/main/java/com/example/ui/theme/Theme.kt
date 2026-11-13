package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.model.DeliveryStatus
import com.example.data.model.InstallStatus
import com.example.data.model.VmStatus

@Immutable
data class FleetStatusColors(
    val online: Color = EmeraldOnline,
    val running: Color = CobaltBlue,
    val registered: Color = IndigoRegistered,
    val provisioned: Color = CyanProvisioned,
    val pending: Color = AmberPending,
    val error: Color = RoseError,
    val offline: Color = MutedSlate,
    val borderSubtle: Color = SurfaceStroke
) {
    fun colorForVmStatus(status: VmStatus): Color = when (status) {
        VmStatus.UNPROVISIONED -> offline
        VmStatus.REGISTERED -> registered
        VmStatus.PROVISIONED -> provisioned
        VmStatus.ONLINE -> online
        VmStatus.RUNNING -> running
        VmStatus.OFFLINE -> pending
        VmStatus.ERROR -> error
    }

    fun colorForDeliveryStatus(status: DeliveryStatus): Color = when (status) {
        DeliveryStatus.QUEUED -> pending
        DeliveryStatus.DELIVERED -> provisioned
        DeliveryStatus.OPENED, DeliveryStatus.COMPLETED -> online
        DeliveryStatus.FAILED -> error
        DeliveryStatus.HOST_OFFLINE -> offline
    }

    fun colorForInstallStatus(status: InstallStatus): Color = when (status) {
        InstallStatus.PENDING -> pending
        InstallStatus.INSTALLED -> online
        InstallStatus.UPDATE_AVAILABLE -> running
        InstallStatus.FAILED -> error
        InstallStatus.UNINSTALLED -> offline
    }
}

val LocalFleetColors = staticCompositionLocalOf { FleetStatusColors() }

private val VmMasterDarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = IceWhite,
    primaryContainer = VioletContainer,
    onPrimaryContainer = IceWhite,
    secondary = CobaltBlue,
    onSecondary = IceWhite,
    secondaryContainer = BlueContainer,
    onSecondaryContainer = IceWhite,
    tertiary = CyanTelemetry,
    onTertiary = ObsidianBackground,
    background = ObsidianBackground,
    onBackground = IceWhite,
    surface = MidnightSurface,
    onSurface = IceWhite,
    surfaceVariant = ElevatedCharcoal,
    onSurfaceVariant = CoolSlate,
    outline = SurfaceStroke,
    error = RoseError,
    onError = IceWhite
)

@Composable
fun VmMasterTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalFleetColors provides FleetStatusColors()) {
        MaterialTheme(
            colorScheme = VmMasterDarkColorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
