package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CommandType
import com.example.data.model.SavedPhoneApp
import com.example.data.model.VmSlot
import com.example.data.model.VmStatus
import com.example.data.network.VmGatewayClient
import com.example.ui.components.DownloadedAppSelectorRow
import com.example.ui.components.PhoneScreenCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.LocalFleetColors
import com.example.ui.viewmodel.DeviceViewMode
import com.example.ui.viewmodel.FleetLocalControls
import com.example.ui.viewmodel.FleetUiState
import kotlinx.coroutines.delay

@Composable
fun DevicesScreen(
    state: FleetUiState.Ready,
    controls: FleetLocalControls,
    bearerToken: String = "",
    savedPhoneApps: List<SavedPhoneApp> = emptyList(),
    preferredTargetPackage: String = "",
    onTargetPackageSelected: (String) -> Unit = {},
    onOpenUrlBroadcast: (url: String, targetPackage: String, useAll100: Boolean) -> Unit = { _, _, _ -> },
    onBulkLifecycleAll: (CommandType) -> Unit = {},
    onSearchChanged: (String) -> Unit,
    onStatusFilterChanged: (VmStatus?) -> Unit,
    onGroupFilterChanged: (String?) -> Unit,
    onViewModeChanged: (DeviceViewMode) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: (List<String>) -> Unit,
    onClearSelection: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenRemoteViewer: (String) -> Unit,
    onCreateGroup: (name: String, desc: String, colorHex: String) -> Unit,
    onAssignGroupToSelected: (groupId: String) -> Unit,
    onBulkLifecycleSelected: (CommandType) -> Unit = {},
    onLifecycleCommand: (VmSlot, CommandType) -> Unit = { _, _ -> },
    onInitialize100Slots: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showAssignGroupDialog by remember { mutableStateOf(false) }
    var activeOverlayVmId by remember { mutableStateOf<String?>(null) }
    var screenRefreshNonce by remember { mutableIntStateOf(0) }
    var autoRefreshLive by remember { mutableStateOf(true) }
    var showQuickUrlBar by remember { mutableStateOf(false) }
    var quickUrl by remember { mutableStateOf("") }
    val gatewayClient = remember { VmGatewayClient() }
    val gatewayBaseUrl = state.profile?.gatewayBaseUrl.orEmpty()
    val hasLiveVms = remember(state.vms) {
        state.vms.any { it.statusEnum == VmStatus.ONLINE || it.statusEnum == VmStatus.RUNNING }
    }

    LaunchedEffect(autoRefreshLive, hasLiveVms, gatewayBaseUrl, controls.viewMode) {
        if (autoRefreshLive && hasLiveVms && gatewayBaseUrl.isNotBlank() && controls.viewMode == DeviceViewMode.GRID) {
            while (true) {
                delay(4000L)
                screenRefreshNonce++
            }
        }
    }

    val filteredVms by remember(state.vms, controls.searchQuery, controls.statusFilter, controls.groupFilter) {
        derivedStateOf {
            val q = controls.searchQuery.trim().lowercase()
            state.vms.filter { vm ->
                val matchesQuery = q.isEmpty() ||
                    vm.vmId.lowercase().contains(q) ||
                    vm.name.lowercase().contains(q) ||
                    vm.ipAddress.lowercase().contains(q) ||
                    vm.hostUrl.lowercase().contains(q) ||
                    vm.androidVersion.lowercase().contains(q)
                val matchesStatus = controls.statusFilter == null || vm.statusEnum == controls.statusFilter
                val matchesGroup = controls.groupFilter == null || vm.groupId == controls.groupFilter
                matchesQuery && matchesStatus && matchesGroup
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = controls.searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = { Text("Search VM-001..100, IP, name") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (controls.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("device_search_input")
                )

                IconButton(
                    onClick = { screenRefreshNonce++ },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("refresh_phone_screens_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh live phone screens"
                    )
                }

                IconButton(
                    onClick = {
                        onViewModeChanged(
                            if (controls.viewMode == DeviceViewMode.GRID) DeviceViewMode.LIST else DeviceViewMode.GRID
                        )
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (controls.viewMode == DeviceViewMode.GRID)
                            Icons.Default.ViewAgenda else Icons.Default.GridView,
                        contentDescription = "Toggle grid or list view"
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = showQuickUrlBar,
                    onClick = { showQuickUrlBar = !showQuickUrlBar },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Paste URL -> 100") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
                FilterChip(
                    selected = autoRefreshLive,
                    onClick = { autoRefreshLive = !autoRefreshLive },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(if (autoRefreshLive) "Live 4s" else "Manual") }
                )
                FilterChip(
                    selected = false,
                    onClick = { onBulkLifecycleAll(CommandType.START) },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Start All") }
                )
                FilterChip(
                    selected = false,
                    onClick = { onBulkLifecycleAll(CommandType.STOP) },
                    leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Stop All") }
                )
            }

            AnimatedVisibility(visible = showQuickUrlBar) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = quickUrl,
                            onValueChange = { quickUrl = it },
                            placeholder = { Text("Paste URL to open inside app on 100 screens") },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let {
                                            quickUrl = it
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste URL")
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        DownloadedAppSelectorRow(
                            trackedApps = state.apps,
                            savedPhoneApps = savedPhoneApps,
                            selectedPackage = preferredTargetPackage,
                            onSelectPackage = onTargetPackageSelected,
                            title = "Open In App"
                        )
                        Button(
                            onClick = {
                                val useAll = controls.selectedVmIds.isEmpty()
                                onOpenUrlBroadcast(quickUrl, preferredTargetPackage, useAll)
                            },
                            enabled = !controls.isBusy && quickUrl.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (controls.selectedVmIds.isEmpty()) "OPEN ON ALL 100 SCREENS"
                                else "OPEN ON ${controls.selectedVmIds.size} SELECTED SCREENS"
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = controls.statusFilter == null,
                    onClick = { onStatusFilterChanged(null) },
                    label = { Text("All (${state.vms.size})") }
                )
                VmStatus.entries.forEach { st ->
                    val count = state.vms.count { it.statusEnum == st }
                    FilterChip(
                        selected = controls.statusFilter == st,
                        onClick = {
                            onStatusFilterChanged(if (controls.statusFilter == st) null else st)
                        },
                        label = { Text("${st.label} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = controls.groupFilter == null,
                    onClick = { onGroupFilterChanged(null) },
                    label = { Text("All Groups") }
                )
                FilterChip(
                    selected = controls.groupFilter == "unassigned",
                    onClick = {
                        onGroupFilterChanged(if (controls.groupFilter == "unassigned") null else "unassigned")
                    },
                    label = { Text("Unassigned") }
                )
                state.groups.forEach { grp ->
                    FilterChip(
                        selected = controls.groupFilter == grp.id,
                        onClick = {
                            onGroupFilterChanged(if (controls.groupFilter == grp.id) null else grp.id)
                        },
                        label = { Text(grp.name) }
                    )
                }
                OutlinedButton(
                    onClick = { showCreateGroupDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Group", style = MaterialTheme.typography.labelLarge)
                }
            }

            AnimatedVisibility(visible = controls.selectedVmIds.isNotEmpty() || filteredVms.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${controls.selectedVmIds.size} selected • ${filteredVms.size} shown",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onSelectAll(filteredVms.map { it.vmId }) }) {
                            Icon(Icons.Default.CheckBox, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Select Filtered")
                        }
                        if (controls.selectedVmIds.isNotEmpty()) {
                            TextButton(onClick = { showAssignGroupDialog = true }) {
                                Icon(Icons.Default.FolderCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Group")
                            }
                            TextButton(onClick = onClearSelection) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = controls.selectedVmIds.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onBulkLifecycleSelected(CommandType.START) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Start")
                    }
                    OutlinedButton(
                        onClick = { onBulkLifecycleSelected(CommandType.RESTART) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Restart")
                    }
                    OutlinedButton(
                        onClick = { onBulkLifecycleSelected(CommandType.STOP) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
            }
        }

        if (state.vms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "No VM Slots Initialized",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Initialize VM-001 through VM-100 to start enrolling cloud Android environments.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onInitialize100Slots) {
                        Text("Initialize 100 Slots")
                    }
                }
            }
        } else if (controls.viewMode == DeviceViewMode.GRID) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columns = if (maxWidth < 600.dp) {
                    GridCells.Fixed(3)
                } else {
                    GridCells.Adaptive(minSize = 132.dp)
                }
                LazyVerticalGrid(
                    columns = columns,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredVms, key = { it.vmId }) { vm ->
                        PhoneScreenCard(
                            vm = vm,
                            isSelected = vm.vmId in controls.selectedVmIds,
                            isMenuOpen = activeOverlayVmId == vm.vmId,
                            gatewayBaseUrl = gatewayBaseUrl,
                            bearerToken = bearerToken,
                            gatewayClient = gatewayClient,
                            refreshNonce = screenRefreshNonce,
                            onToggleMenu = {
                                activeOverlayVmId = if (activeOverlayVmId == vm.vmId) null else vm.vmId
                            },
                            onToggleSelect = { onToggleSelect(vm.vmId) },
                            onOpenDetail = { onOpenDetail(vm.vmId) },
                            onOpenRemoteViewer = { onOpenRemoteViewer(vm.vmId) },
                            onLifecycleCommand = { cmd -> onLifecycleCommand(vm, cmd) }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredVms, key = { it.vmId }) { vm ->
                    VmListRowCard(
                        vm = vm,
                        isSelected = vm.vmId in controls.selectedVmIds,
                        onToggleSelect = { onToggleSelect(vm.vmId) },
                        onOpenDetail = { onOpenDetail(vm.vmId) },
                        onOpenRemoteViewer = { onOpenRemoteViewer(vm.vmId) }
                    )
                }
            }
        }
    }

    if (showCreateGroupDialog) {
        var groupName by remember { mutableStateOf("") }
        var groupDesc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Create Device Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group Name (e.g., QA Staging Pool)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = groupDesc,
                        onValueChange = { groupDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCreateGroup(groupName, groupDesc, "#8B5CF6")
                    showCreateGroupDialog = false
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAssignGroupDialog) {
        AlertDialog(
            onDismissRequest = { showAssignGroupDialog = false },
            title = { Text("Assign ${controls.selectedVmIds.size} VMs to Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAssignGroupToSelected("unassigned")
                                showAssignGroupDialog = false
                            }
                    ) {
                        Text(
                            text = "Unassigned",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    state.groups.forEach { grp ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAssignGroupToSelected(grp.id)
                                    showAssignGroupDialog = false
                                }
                        ) {
                            Text(
                                text = grp.name,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAssignGroupDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun VmListRowCard(
    vm: VmSlot,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenRemoteViewer: () -> Unit
) {
    val fleetColors = LocalFleetColors.current
    val statusColor = fleetColors.colorForVmStatus(vm.statusEnum)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onOpenDetail)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() }
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = vm.vmId,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = vm.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${vm.androidVersion} • IP: ${vm.ipAddress.ifBlank { "Unassigned" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Last seen: ${formatTimestampShort(vm.lastSeenAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(label = vm.statusEnum.label, color = statusColor)
                    if (vm.batteryPercent != null) {
                        StatusPill(label = "${vm.batteryPercent}%", color = fleetColors.online)
                    }
                }
            }
            IconButton(
                onClick = onOpenRemoteViewer,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Monitor,
                    contentDescription = "Remote screen for ${vm.vmId}",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
