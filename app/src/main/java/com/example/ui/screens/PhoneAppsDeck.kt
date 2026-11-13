package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.SavedPhoneApp
import com.example.util.DeviceInstalledAppsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PhoneAppsDeck(
    savedPhoneApps: List<SavedPhoneApp>,
    selectedVmCount: Int,
    totalVmSlots: Int,
    isBusy: Boolean,
    onAddPhoneApps: (List<SavedPhoneApp>) -> Unit,
    onRemovePhoneApp: (String) -> Unit,
    onLaunchApp: (packageName: String, appName: String, useAll100: Boolean) -> Unit,
    onPushPhoneApp: (app: SavedPhoneApp, apkFiles: List<File>, openAfterInstall: Boolean, useAll100: Boolean) -> Unit,
    onPrefillApkForm: (SavedPhoneApp) -> Unit,
    onNavigateToDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phoneApps by remember { mutableStateOf<List<SavedPhoneApp>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showScannerList by remember { mutableStateOf(true) }
    var activeAppPkg by remember(savedPhoneApps) {
        mutableStateOf(savedPhoneApps.firstOrNull()?.packageName.orEmpty())
    }

    LaunchedEffect(Unit) {
        scanning = true
        phoneApps = DeviceInstalledAppsHelper.loadLaunchableApps(context)
        scanning = false
    }

    val savedPkgSet = remember(savedPhoneApps) { savedPhoneApps.map { it.packageName }.toSet() }
    val filteredPhoneApps = remember(phoneApps, searchQuery) {
        val q = searchQuery.trim().lowercase()
        phoneApps.filter {
            q.isEmpty() || it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }
    val selectedApp = remember(savedPhoneApps, phoneApps, activeAppPkg) {
        savedPhoneApps.firstOrNull { it.packageName == activeAppPkg }
            ?: phoneApps.firstOrNull { it.packageName == activeAppPkg }
            ?: savedPhoneApps.firstOrNull()
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "My Phone Apps -> 100 Devices",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            scanning = true
                            phoneApps = DeviceInstalledAppsHelper.loadLaunchableApps(context)
                            scanning = false
                        }
                    },
                    modifier = Modifier.testTag("scan_phone_apps_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rescan apps installed on this phone")
                }
            }

            Text(
                text = "Import all apps installed on your phone, then select any ONE app to install & open directly on 100 devices — no Google Play Store or Gmail login required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onAddPhoneApps(phoneApps)
                        if (phoneApps.isNotEmpty() && activeAppPkg.isBlank()) {
                            activeAppPkg = phoneApps.first().packageName
                        }
                    },
                    enabled = !scanning && phoneApps.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("add_all_phone_apps_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (scanning) "Scanning..." else "Add All Phone Apps (${phoneApps.size})")
                }
                OutlinedButton(
                    onClick = { showScannerList = !showScannerList },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("toggle_phone_apps_picker_button")
                ) {
                    Text(if (showScannerList) "Hide List" else "Pick Apps")
                }
            }

            if (showScannerList) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search apps on your phone") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredPhoneApps, key = { it.packageName }) { app ->
                        val isAdded = app.packageName in savedPkgSet
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PhoneAppIconBadge(packageName = app.packageName)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${app.packageName} • v${app.versionName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isAdded) {
                                    TextButton(onClick = { activeAppPkg = app.packageName }) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Select")
                                    }
                                } else {
                                    TextButton(
                                        onClick = {
                                            onAddPhoneApps(listOf(app))
                                            activeAppPkg = app.packageName
                                        },
                                        modifier = Modifier.testTag("add_phone_app_${app.packageName}")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(2.dp))
                                        Text("Add")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (savedPhoneApps.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Text(
                    text = "Added Apps (${savedPhoneApps.size}) — Select 1 App to Open on 100 Devices",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    savedPhoneApps.forEach { app ->
                        val isChosen = selectedApp?.packageName == app.packageName
                        FilterChip(
                            selected = isChosen,
                            onClick = { activeAppPkg = app.packageName },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isChosen) Icons.Default.Check else Icons.Default.Apps,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onRemovePhoneApp(app.packageName) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove ${app.appName}",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            },
                            label = { Text(app.appName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }

                if (selectedApp != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Selected: ${selectedApp.appName}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${selectedApp.packageName} • $selectedVmCount sel / $totalVmSlots slots",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(onClick = onNavigateToDevices, shape = RoundedCornerShape(10.dp)) {
                            Text("Choose VMs")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val files = DeviceInstalledAppsHelper.resolveApkFiles(context, selectedApp.packageName)
                                    onPushPhoneApp(selectedApp, files, true, true)
                                }
                            },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("install_open_one_app_all_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Install & Open (100)")
                        }
                        Button(
                            onClick = { onLaunchApp(selectedApp.packageName, selectedApp.appName, true) },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("launch_one_app_all_button")
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Open Only (100)")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val files = DeviceInstalledAppsHelper.resolveApkFiles(context, selectedApp.packageName)
                                    onPushPhoneApp(selectedApp, files, true, false)
                                }
                            },
                            enabled = !isBusy && selectedVmCount > 0,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("install_open_one_app_selected_button")
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Install & Open Sel.")
                        }
                        OutlinedButton(
                            onClick = { onPrefillApkForm(selectedApp) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Fill APK Form")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneAppIconBadge(packageName: String) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        iconBitmap = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                if (drawable is BitmapDrawable && drawable.bitmap != null) {
                    drawable.bitmap.asImageBitmap()
                } else {
                    val w = drawable.intrinsicWidth.coerceIn(48, 96)
                    val h = drawable.intrinsicHeight.coerceIn(48, 96)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(canvas)
                    bmp.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    val bmp = iconBitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
