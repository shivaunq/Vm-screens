package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CommandType
import com.example.data.model.DeliveryStatus
import com.example.data.model.SavedPhoneApp
import com.example.ui.components.DownloadedAppSelectorRow
import com.example.ui.components.StatusPill
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.LocalFleetColors
import com.example.ui.viewmodel.FleetLocalControls
import com.example.ui.viewmodel.FleetUiState

@Composable
fun UrlDispatchScreen(
    state: FleetUiState.Ready,
    controls: FleetLocalControls,
    savedPhoneApps: List<SavedPhoneApp>,
    preferredTargetPackage: String = "",
    onTargetPackageSelected: (String) -> Unit = {},
    isBusy: Boolean,
    onOpenSelected: (url: String, targetPackage: String) -> Unit,
    onOpenAll: (url: String, targetPackage: String) -> Unit,
    onNavigateToDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fleetColors = LocalFleetColors.current
    val clipboard = LocalClipboardManager.current
    var urlInput by remember { mutableStateOf("") }
    var targetPackage by remember { mutableStateOf(preferredTargetPackage) }

    LaunchedEffect(preferredTargetPackage) {
        if (preferredTargetPackage.isNotBlank() && targetPackage.isBlank()) {
            targetPackage = preferredTargetPackage
        }
    }

    val urlCommands = remember(state.commands) {
        state.commands.filter { it.commandTypeEnum == CommandType.OPEN_URL }
    }
    val openedCount = remember(urlCommands) {
        urlCommands.count { it.deliveryStatusEnum == DeliveryStatus.OPENED || it.deliveryStatusEnum == DeliveryStatus.DELIVERED }
    }
    val queuedCount = remember(urlCommands) {
        urlCommands.count { it.deliveryStatusEnum == DeliveryStatus.QUEUED }
    }
    val offlineCount = remember(urlCommands) {
        urlCommands.count { it.deliveryStatusEnum == DeliveryStatus.HOST_OFFLINE || it.deliveryStatusEnum == DeliveryStatus.FAILED }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Authorized Testing URL Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Paste one URL and tap OPEN ALL (100) or OPEN SELECTED. Supported VMs open the URL in the matching app and report per-device delivery status.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Paste Testing URL (https://...)") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let {
                                        urlInput = it
                                    }
                                },
                                modifier = Modifier.testTag("paste_url_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste URL from clipboard"
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("url_dispatch_input")
                    )

                    DownloadedAppSelectorRow(
                        trackedApps = state.apps,
                        savedPhoneApps = savedPhoneApps,
                        selectedPackage = targetPackage,
                        onSelectPackage = { pkg ->
                            targetPackage = pkg
                            onTargetPackageSelected(pkg)
                        },
                        title = "Open Inside Downloaded / Phone App"
                    )

                    OutlinedTextField(
                        value = targetPackage,
                        onValueChange = {
                            targetPackage = it
                            onTargetPackageSelected(it)
                        },
                        label = { Text("Target Downloaded App Package (optional)") },
                        placeholder = { Text("Blank opens system default app, or enter package ID") },
                        trailingIcon = {
                            if (targetPackage.isNotEmpty()) {
                                IconButton(onClick = {
                                    targetPackage = ""
                                    onTargetPackageSelected("")
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear package")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("target_package_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${controls.selectedVmIds.size} selected • ${state.vms.size}/100 slots",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(onClick = onNavigateToDevices, shape = RoundedCornerShape(10.dp)) {
                            Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("100 Screens")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onOpenSelected(urlInput, targetPackage) },
                            enabled = !isBusy && controls.selectedVmIds.isNotEmpty() && urlInput.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("open_selected_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_open_selected))
                        }
                        Button(
                            onClick = { onOpenAll(urlInput, targetPackage) },
                            enabled = !isBusy && urlInput.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("open_all_button")
                        ) {
                            Text(stringResource(R.string.action_open_all))
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Delivery & Opening Status (${urlCommands.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (urlCommands.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(label = "$openedCount Opened", color = fleetColors.online)
                        StatusPill(label = "$queuedCount Queued", color = fleetColors.pending)
                        StatusPill(label = "$offlineCount Offline/Err", color = fleetColors.error)
                    }
                }
            }
        }

        if (urlCommands.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                ) {
                    Text(
                        text = "No URLs dispatched yet. Paste a testing URL above and tap OPEN SELECTED or OPEN ALL.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(urlCommands, key = { it.id }) { cmd ->
                val statusColor = fleetColors.colorForDeliveryStatus(cmd.deliveryStatusEnum)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cmd.vmId,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            StatusPill(label = cmd.deliveryStatusEnum.label, color = statusColor)
                        }
                        Text(
                            text = cmd.targetPayload,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (cmd.targetPackage.isNotBlank()) {
                            Text(
                                text = "Target App: ${cmd.targetPackage}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(
                            text = "${cmd.statusMessage} • ${formatTimestampShort(cmd.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
