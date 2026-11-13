package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CommandType
import com.example.data.model.InstallStatus
import com.example.data.model.SavedPhoneApp
import com.example.data.model.VmStatus
import com.example.ui.components.DownloadedAppSelectorRow
import com.example.ui.components.FleetStatusChartCard
import com.example.ui.components.KpiTelemetryCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.LocalFleetColors
import com.example.ui.viewmodel.FleetUiState

@Composable
fun DashboardScreen(
    state: FleetUiState.Ready,
    savedPhoneApps: List<SavedPhoneApp> = emptyList(),
    preferredTargetPackage: String = "",
    onTargetPackageSelected: (String) -> Unit = {},
    isBusy: Boolean,
    onInitialize100Slots: () -> Unit,
    onBulkLifecycleAll: (CommandType) -> Unit = {},
    onOpenUrlAll: (url: String, targetPackage: String) -> Unit = { _, _ -> },
    onNavigateToDevices: () -> Unit,
    onNavigateToLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fleetColors = LocalFleetColors.current
    val clipboard = LocalClipboardManager.current
    var quickUrl by remember { mutableStateOf("") }
    val vms = state.vms
    val totalCount = vms.size
    val onlineCount = vms.count { it.statusEnum == VmStatus.ONLINE }
    val runningCount = vms.count { it.statusEnum == VmStatus.RUNNING }
    val offlineCount = vms.count {
        it.statusEnum == VmStatus.OFFLINE || it.statusEnum == VmStatus.UNPROVISIONED
    }
    val errorCount = vms.count { it.statusEnum == VmStatus.ERROR }
    val installedAppsCount = state.apps.count { it.installStatusEnum == InstallStatus.INSTALLED }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (vms.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_fleet_hero_1794554613234),
                        contentDescription = stringResource(R.string.cd_hero_banner),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.35f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatusPill(label = "PHASE 1 READY", color = MaterialTheme.colorScheme.tertiary)
                        Text(
                            text = "Provision 100 Honest Inventory Slots",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Create VM-001 through VM-100 in Cloud Firestore. Every slot begins as Unprovisioned until a real Linux KVM Android Emulator enrolls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onInitialize100Slots,
                            enabled = !isBusy,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("init_100_slots_button")
                        ) {
                            Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.init_100_slots_cta))
                        }
                    }
                }
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1-to-100 Master Control & Quick URL",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onNavigateToDevices,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("100 Screens", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onBulkLifecycleAll(CommandType.START) },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = fleetColors.online),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("master_start_all_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Start All", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onBulkLifecycleAll(CommandType.RESTART) },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("master_restart_all_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restart", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onBulkLifecycleAll(CommandType.STOP) },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = fleetColors.error),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("master_stop_all_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Stop All", maxLines = 1)
                        }
                    }

                    OutlinedTextField(
                        value = quickUrl,
                        onValueChange = { quickUrl = it },
                        label = { Text("Paste One URL for All 100 Devices") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let {
                                        quickUrl = it
                                    }
                                },
                                modifier = Modifier.testTag("dashboard_paste_url_button")
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste URL")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_quick_url_input")
                    )

                    DownloadedAppSelectorRow(
                        trackedApps = state.apps,
                        savedPhoneApps = savedPhoneApps,
                        selectedPackage = preferredTargetPackage,
                        onSelectPackage = onTargetPackageSelected,
                        title = "Open URL Inside App (Downloaded / Phone / Play Store)"
                    )

                    Button(
                        onClick = { onOpenUrlAll(quickUrl, preferredTargetPackage) },
                        enabled = !isBusy && quickUrl.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dashboard_open_all_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("OPEN URL ON ALL 100 SCREENS")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiTelemetryCard(
                    title = "Total Devices",
                    value = "$totalCount / 100",
                    accent = MaterialTheme.colorScheme.primary,
                    icon = Icons.Default.Devices,
                    modifier = Modifier.weight(1f)
                )
                KpiTelemetryCard(
                    title = "Online Devices",
                    value = onlineCount.toString(),
                    accent = fleetColors.online,
                    icon = Icons.Default.CloudDone,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiTelemetryCard(
                    title = "Running VMs",
                    value = runningCount.toString(),
                    accent = fleetColors.running,
                    icon = Icons.Default.PlayCircle,
                    modifier = Modifier.weight(1f)
                )
                KpiTelemetryCard(
                    title = "Offline / Unprov.",
                    value = offlineCount.toString(),
                    accent = fleetColors.offline,
                    icon = Icons.Default.CloudOff,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiTelemetryCard(
                    title = "Error Devices",
                    value = errorCount.toString(),
                    accent = fleetColors.error,
                    icon = Icons.Default.ErrorOutline,
                    modifier = Modifier.weight(1f)
                )
                KpiTelemetryCard(
                    title = "Installed Apps",
                    value = installedAppsCount.toString(),
                    accent = MaterialTheme.colorScheme.tertiary,
                    icon = Icons.Default.Apps,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            FleetStatusChartCard(vms = vms)
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Host Gateway Connection",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val gw = state.profile?.gatewayBaseUrl.orEmpty()
                        Text(
                            text = if (gw.isBlank()) "Not configured (Firestore queue mode)" else gw,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = if (vms.isEmpty()) onNavigateToLogs else onNavigateToDevices,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (vms.isEmpty()) "Configure" else "100 Screens")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity Logs",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${state.auditLogs.size} events",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.auditLogs.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                ) {
                    Text(
                        text = "No administrator activity recorded yet. Initialize the 100 VM slots or enroll a cloud node to generate live audit logs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(state.auditLogs.take(6), key = { it.id }) { log ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${log.action} • ${log.targetScope}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = log.result,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (log.result == "SUCCESS") fleetColors.online else fleetColors.pending
                            )
                        }
                        Text(
                            text = log.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${log.actorEmail} • ${formatTimestampShort(log.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}
