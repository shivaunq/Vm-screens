package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.CommandType
import com.example.data.model.VmAccountStatus
import com.example.data.model.VmConnectivity
import com.example.data.model.VmGroup
import com.example.data.model.VmSlot
import com.example.data.model.VmStatus
import com.example.ui.theme.LocalFleetColors
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

fun formatTimestampShort(ts: Timestamp?): String {
    if (ts == null) return "Never"
    val fmt = SimpleDateFormat("MMM dd HH:mm:ss", Locale.US)
    return fmt.format(ts.toDate())
}

@Composable
fun KpiTelemetryCard(
    title: String,
    value: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FleetStatusChartCard(
    vms: List<VmSlot>,
    modifier: Modifier = Modifier
) {
    val fleetColors = LocalFleetColors.current
    val counts = remember(vms) {
        VmStatus.entries.map { status ->
            status to vms.count { it.statusEnum == status }
        }
    }
    val total = vms.size.coerceAtLeast(1)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fleet Capacity Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${vms.size} / 100 Slots",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(104.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 14.dp.toPx()
                        val inset = strokeWidth / 2f
                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                        var startAngle = -90f
                        val nonZero = counts.filter { it.second > 0 }
                        if (nonZero.isEmpty()) {
                            drawArc(
                                color = fleetColors.offline.copy(alpha = 0.25f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = strokeWidth)
                            )
                        } else {
                            nonZero.forEach { (status, count) ->
                                val sweep = (count.toFloat() / total.toFloat()) * 360f
                                drawArc(
                                    color = fleetColors.colorForVmStatus(status),
                                    startAngle = startAngle,
                                    sweepAngle = sweep.coerceAtLeast(3f),
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                )
                                startAngle += sweep
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val activeCount = vms.count {
                            it.statusEnum == VmStatus.ONLINE || it.statusEnum == VmStatus.RUNNING
                        }
                        Text(
                            text = "$activeCount",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    counts.forEach { (status, count) ->
                        val color = fleetColors.colorForVmStatus(status)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Text(
                                    text = status.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VmMatrixCell(
    vm: VmSlot,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenRemoteViewer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val fleetColors = LocalFleetColors.current
    val statusColor = fleetColors.colorForVmStatus(vm.statusEnum)
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else statusColor.copy(alpha = 0.35f)

    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenDetail)
            .testTag("vm_cell_${vm.vmId}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = vm.vmId,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleSelect),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected ${vm.vmId}",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                    .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenRemoteViewer),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Monitor,
                        contentDescription = "Open screen for ${vm.vmId}",
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (vm.statusEnum == VmStatus.ONLINE || vm.statusEnum == VmStatus.RUNNING) {
                            "Screen"
                        } else {
                            "Offline"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }

            Text(
                text = vm.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = vm.ipAddress.ifBlank { "IP: Unassigned" },
                style = MaterialTheme.typography.labelSmall,
                color = if (vm.ipAddress.isNotBlank()) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = vm.statusEnum.label,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VmDetailDialog(
    vm: VmSlot,
    groups: List<VmGroup>,
    onDismiss: () -> Unit,
    onSaveSlot: (VmSlot) -> Unit,
    onLifecycleCommand: (CommandType) -> Unit,
    onOpenRemoteScreen: () -> Unit
) {
    val fleetColors = LocalFleetColors.current
    var name by remember(vm.vmId) { mutableStateOf(vm.name) }
    var androidVersion by remember(vm.vmId) { mutableStateOf(vm.androidVersion) }
    var ipAddress by remember(vm.vmId) { mutableStateOf(vm.ipAddress) }
    var assignedGmail by remember(vm.vmId) { mutableStateOf(vm.assignedGmail) }
    var selectedAccountStatus by remember(vm.vmId) { mutableStateOf(vm.accountStatusEnum) }
    var hostUrl by remember(vm.vmId) { mutableStateOf(vm.hostUrl) }
    var streamUrl by remember(vm.vmId) { mutableStateOf(vm.streamUrl) }
    var selectedGroupId by remember(vm.vmId) { mutableStateOf(vm.groupId) }
    var selectedStatus by remember(vm.vmId) { mutableStateOf(vm.statusEnum) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = vm.vmId,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Slot #${vm.slotNumber} • Last seen: ${formatTimestampShort(vm.lastSeenAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_close_dialog)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        label = vm.statusEnum.label,
                        color = fleetColors.colorForVmStatus(vm.statusEnum)
                    )
                    StatusPill(
                        label = vm.connectivityEnum.label,
                        color = if (vm.connectivityEnum == VmConnectivity.DISCONNECTED)
                            fleetColors.offline else fleetColors.online
                    )
                    vm.batteryPercent?.let { pct ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryStd,
                                contentDescription = "Battery",
                                tint = fleetColors.online,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (vm.lastError.isNotBlank()) {
                    Surface(
                        color = fleetColors.error.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Last Error: ${vm.lastError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = fleetColors.error,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onLifecycleCommand(CommandType.START) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = fleetColors.online)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Start")
                    }
                    OutlinedButton(
                        onClick = { onLifecycleCommand(CommandType.RESTART) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Restart")
                    }
                    OutlinedButton(
                        onClick = { onLifecycleCommand(CommandType.STOP) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                }

                OutlinedButton(
                    onClick = onOpenRemoteScreen,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Monitor, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Remote Screen Viewer (WebRTC / Stream)")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Text(
                    text = "Environment Enrollment & Metadata",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Device Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = androidVersion,
                    onValueChange = { androidVersion = it },
                    label = { Text("Android Version (e.g., Android 15)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("Device IP Address (e.g., 10.20.0.101)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vm_ip_address_input")
                )

                OutlinedTextField(
                    value = assignedGmail,
                    onValueChange = { next ->
                        assignedGmail = next
                        if (next.isNotBlank() && selectedAccountStatus == VmAccountStatus.UNASSIGNED) {
                            selectedAccountStatus = VmAccountStatus.GMAIL_ASSIGNED
                        } else if (next.isBlank()) {
                            selectedAccountStatus = VmAccountStatus.UNASSIGNED
                        }
                    },
                    label = { Text("Assigned Individual Gmail (Label Only)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vm_assigned_gmail_input")
                )

                Text(
                    text = "Individual Account & Signup Progress",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VmAccountStatus.entries.forEach { st ->
                        FilterChip(
                            selected = selectedAccountStatus == st,
                            onClick = { selectedAccountStatus = st },
                            label = { Text(st.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = hostUrl,
                    onValueChange = { hostUrl = it },
                    label = { Text("Cloud Host HTTPS URL (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text("WebRTC / Remote Stream URL (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Lifecycle State (Reflects Enrollment / Host Status)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        VmStatus.UNPROVISIONED,
                        VmStatus.REGISTERED,
                        VmStatus.PROVISIONED,
                        VmStatus.OFFLINE
                    ).forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }

                if (groups.isNotEmpty()) {
                    Text(
                        text = "Device Group",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedGroupId == "unassigned",
                            onClick = { selectedGroupId = "unassigned" },
                            label = { Text("Unassigned") }
                        )
                        groups.forEach { grp ->
                            FilterChip(
                                selected = selectedGroupId == grp.id,
                                onClick = { selectedGroupId = grp.id },
                                label = { Text(grp.name) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSaveSlot(
                                vm.copy(
                                    name = name.ifBlank { "Slot ${vm.vmId}" },
                                    androidVersion = androidVersion.ifBlank { "Unassigned" },
                                    ipAddress = ipAddress.trim(),
                                    assignedGmail = assignedGmail.trim().take(160),
                                    accountStatus = selectedAccountStatus.name,
                                    hostUrl = hostUrl.trim(),
                                    streamUrl = streamUrl.trim(),
                                    groupId = selectedGroupId,
                                    status = selectedStatus.name
                                )
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Slot")
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RemoteScreenDialog(
    vm: VmSlot,
    webrtcGatewayUrl: String,
    gatewayBaseUrl: String = "",
    bearerToken: String = "",
    onOpenSlotSettings: () -> Unit = {},
    onOpenHostSettings: () -> Unit = {},
    onOpenGoogleSignIn: () -> Unit = {},
    onSendRemoteText: (String) -> Unit = {},
    onMarkAccountStatus: (VmAccountStatus) -> Unit = {},
    onDismiss: () -> Unit
) {
    val rawEndpoint = vm.streamUrl.trim().ifBlank {
        val base = webrtcGatewayUrl.trim().ifBlank { gatewayBaseUrl.trim() }
        if (base.isNotBlank()) {
            val queryParts = buildList {
                if (bearerToken.isNotBlank()) {
                    add("token=" + java.net.URLEncoder.encode(bearerToken.trim(), "UTF-8"))
                }
                if (vm.assignedGmail.isNotBlank()) {
                    add("email=" + java.net.URLEncoder.encode(vm.assignedGmail.trim(), "UTF-8"))
                }
            }
            val qs = if (queryParts.isEmpty()) "" else "?" + queryParts.joinToString("&")
            "${base.trimEnd('/')}/viewer/${vm.vmId}$qs"
        } else ""
    }
    val isSafeEndpoint = rawEndpoint.startsWith("https://") ||
        rawEndpoint.startsWith("http://10.0.2.2") ||
        rawEndpoint.startsWith("http://127.0.0.1") ||
        rawEndpoint.startsWith("http://localhost")
    val isLiveVm = vm.statusEnum == VmStatus.ONLINE || vm.statusEnum == VmStatus.RUNNING
    val canStream = isSafeEndpoint && (isLiveVm || vm.streamUrl.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (canStream) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (canStream) LocalFleetColors.current.online else LocalFleetColors.current.offline
                )
                Text("Phone Screen • ${vm.vmId}")
            }
        },
        text = {
            if (canStream) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = vm.assignedGmail.ifBlank { "No Gmail assigned (Edit Slot)" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill(
                            label = vm.accountStatusEnum.label,
                            color = if (vm.accountStatusEnum == VmAccountStatus.APP_SIGNED_UP ||
                                vm.accountStatusEnum == VmAccountStatus.PLAY_SIGNED_IN
                            ) {
                                LocalFleetColors.current.online
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (vm.assignedGmail.isNotBlank()) {
                            Button(
                                onClick = { onSendRemoteText(vm.assignedGmail) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("remote_type_gmail_button")
                            ) {
                                Text("Type Gmail", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onOpenSlotSettings,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Set Gmail", maxLines = 1)
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenGoogleSignIn,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("remote_google_signin_button")
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sign-In", maxLines = 1)
                        }
                        val nextStatus = if (vm.accountStatusEnum == VmAccountStatus.APP_SIGNED_UP) {
                            VmAccountStatus.PLAY_SIGNED_IN
                        } else {
                            VmAccountStatus.APP_SIGNED_UP
                        }
                        OutlinedButton(
                            onClick = { onMarkAccountStatus(nextStatus) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("remote_mark_signed_up_button")
                        ) {
                            Text(
                                text = if (vm.accountStatusEnum == VmAccountStatus.APP_SIGNED_UP) "Signed Up" else "Mark Done",
                                maxLines = 1
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    webViewClient = WebViewClient()
                                    loadUrl(rawEndpoint)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Monitor,
                                contentDescription = null,
                                tint = LocalFleetColors.current.offline,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = "${vm.vmId} • ${vm.statusEnum.label}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (vm.assignedGmail.isNotBlank()) {
                                Text(
                                    text = "Gmail: ${vm.assignedGmail} (${vm.accountStatusEnum.label})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = if (rawEndpoint.isBlank()) {
                                    "No live Linux KVM Host Gateway or WebRTC Stream URL configured yet."
                                } else {
                                    "Endpoint must use https:// (or local 10.0.2.2 / 127.0.0.1) and VM must be ONLINE/RUNNING."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenSlotSettings,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Edit Slot", maxLines = 1)
                        }
                        Button(
                            onClick = onOpenHostSettings,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Host Setup", maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
