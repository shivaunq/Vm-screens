package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CommandType
import com.example.data.model.VmAccountStatus
import com.example.data.model.VmSlot
import com.example.data.model.VmStatus
import com.example.data.network.VmGatewayClient
import com.example.ui.theme.LocalFleetColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PhoneScreenCard(
    vm: VmSlot,
    isSelected: Boolean,
    isMenuOpen: Boolean,
    gatewayBaseUrl: String,
    bearerToken: String,
    gatewayClient: VmGatewayClient,
    refreshNonce: Int,
    onToggleMenu: () -> Unit,
    onToggleSelect: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenRemoteViewer: () -> Unit,
    onLifecycleCommand: (CommandType) -> Unit,
    modifier: Modifier = Modifier
) {
    val fleetColors = LocalFleetColors.current
    val statusColor = fleetColors.colorForVmStatus(vm.statusEnum)
    val isLive = vm.statusEnum == VmStatus.ONLINE || vm.statusEnum == VmStatus.RUNNING
    val effectiveHost = vm.hostUrl.ifBlank { gatewayBaseUrl }
    var liveBitmap by remember(vm.vmId) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(vm.vmId, isLive, effectiveHost, bearerToken, refreshNonce) {
        if (isLive && effectiveHost.isNotBlank()) {
            val bytes = gatewayClient.fetchScreenBitmapBytes(
                baseUrl = effectiveHost,
                bearerToken = bearerToken,
                vmId = vm.vmId
            )
            if (bytes != null && bytes.isNotEmpty()) {
                val decoded = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
                if (decoded != null) {
                    liveBitmap = decoded
                }
            }
        } else {
            liveBitmap = null
        }
    }

    val borderWidth = if (isSelected || isMenuOpen) 2.dp else 1.dp
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isMenuOpen -> Color(0xFF38BDF8)
        isLive -> fleetColors.online.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.55f)
            .clip(RoundedCornerShape(14.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onToggleMenu)
            .testTag("vm_cell_${vm.vmId}")
    ) {
        val capturedScreen = liveBitmap
        if (capturedScreen != null) {
            Image(
                bitmap = capturedScreen,
                contentDescription = "Live screen of ${vm.vmId}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CyanCloudWallpaper(
                isLive = isLive,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top subtle scrim for crisp contrast on Android version + edit icons
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.48f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 6.dp, vertical = 5.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val versionLabel = vm.androidVersion
                    .takeIf { it.isNotBlank() && !it.equals("Unassigned", ignoreCase = true) }
                    ?: vm.vmId
                Text(
                    text = versionLabel,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenDetail)
                            .testTag("vm_edit_${vm.vmId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit ${vm.vmId} settings",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Black.copy(alpha = 0.35f)
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                            .clickable(onClick = onToggleSelect)
                            .testTag("vm_select_${vm.vmId}")
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected ${vm.vmId}",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        // Honest status / slot / IP footer strip at bottom of screen
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f)
                        )
                    )
                )
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = vm.vmId,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (isLive) "LIVE" else vm.statusEnum.label,
                    color = if (isLive) fleetColors.online else Color.White.copy(alpha = 0.8f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            Text(
                text = vm.ipAddress.ifBlank { "No IP" },
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (vm.assignedGmail.isNotBlank() || vm.accountStatusEnum != VmAccountStatus.UNASSIGNED) {
                val acctColor = when (vm.accountStatusEnum) {
                    VmAccountStatus.APP_SIGNED_UP -> fleetColors.online
                    VmAccountStatus.PLAY_SIGNED_IN -> Color(0xFF38BDF8)
                    else -> Color(0xFFFDE68A)
                }
                val acctLabel = if (vm.assignedGmail.isNotBlank()) {
                    vm.assignedGmail.substringBefore("@") + "@"
                } else {
                    vm.accountStatusEnum.label
                }
                Text(
                    text = "${vm.accountStatusEnum.label} • $acctLabel",
                    color = acctColor,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Translucent 3-action overlay menu (Quick entry / Setting / ShutDown-Start) matching screenshot
        AnimatedVisibility(
            visible = isMenuOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xB8071E26))
                    .clickable(onClick = onToggleMenu),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    PhoneOverlayActionButton(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        label = "Quick entry",
                        contentDesc = "Quick entry remote screen for ${vm.vmId}",
                        tag = "vm_quick_entry_${vm.vmId}",
                        onClick = {
                            onToggleMenu()
                            onOpenRemoteViewer()
                        }
                    )
                    PhoneOverlayActionButton(
                        icon = Icons.Default.Settings,
                        label = "Setting",
                        contentDesc = "Settings for ${vm.vmId}",
                        tag = "vm_setting_${vm.vmId}",
                        onClick = {
                            onToggleMenu()
                            onOpenDetail()
                        }
                    )
                    PhoneOverlayActionButton(
                        icon = Icons.Default.PowerSettingsNew,
                        label = if (isLive) "ShutDown" else "Start",
                        contentDesc = if (isLive) "Shut down ${vm.vmId}" else "Start ${vm.vmId}",
                        tag = "vm_power_${vm.vmId}",
                        tint = if (isLive) Color(0xFFFCA5A5) else Color(0xFF86EFAC),
                        onClick = {
                            onToggleMenu()
                            onLifecycleCommand(if (isLive) CommandType.STOP else CommandType.START)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneOverlayActionButton(
    icon: ImageVector,
    label: String,
    contentDesc: String,
    tag: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag(tag)
    ) {
        Surface(
            color = Color.White.copy(alpha = 0.08f),
            shape = CircleShape,
            modifier = Modifier
                .size(36.dp)
                .border(1.2.dp, tint.copy(alpha = 0.78f), CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDesc,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun CyanCloudWallpaper(
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = if (isLive) {
                    listOf(Color(0xFF14B8A6), Color(0xFF38BDF8))
                } else {
                    listOf(Color(0xFF14E0D2), Color(0xFF3BD5FF))
                }
            )
        )

        // Top-left sweeping curved cyan pane matching the cloud-phone tile style
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF2AF5E4).copy(alpha = 0.75f), Color(0xFF06B6D4).copy(alpha = 0.45f)),
                start = Offset(0f, 0f),
                end = Offset(w * 0.9f, h * 0.5f)
            ),
            radius = w * 0.95f,
            center = Offset(w * 0.12f, h * 0.14f)
        )

        // Diagonal geometric facet 1
        val facetOne = Path().apply {
            moveTo(0f, h * 0.44f)
            lineTo(w, h * 0.24f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = facetOne,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF0EA5E9).copy(alpha = 0.35f), Color(0xFF0284C7).copy(alpha = 0.25f)),
                start = Offset(0f, h * 0.4f),
                end = Offset(w, h)
            )
        )

        // Diagonal geometric facet 2
        val facetTwo = Path().apply {
            moveTo(0f, h * 0.56f)
            lineTo(w * 0.78f, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = facetTwo,
            color = Color(0xFF22D3EE).copy(alpha = 0.42f)
        )
    }
}
