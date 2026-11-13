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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CommandType
import com.example.data.model.SavedPhoneApp
import com.example.ui.components.StatusPill
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.LocalFleetColors
import com.example.ui.viewmodel.FleetLocalControls
import com.example.ui.viewmodel.FleetUiState
import java.io.File

@Composable
fun AppsScreen(
    state: FleetUiState.Ready,
    controls: FleetLocalControls,
    savedPhoneApps: List<SavedPhoneApp>,
    preferredTargetPackage: String,
    isBusy: Boolean,
    onAssignGmailBatch: (rawEmails: String, startSlotNumber: Int) -> Unit,
    onOpenGoogleSignIn: (useAll100: Boolean) -> Unit,
    onResetAppSessionForSignup: (packageName: String, appName: String, useAll100: Boolean) -> Unit,
    onAddPhoneApps: (List<SavedPhoneApp>) -> Unit,
    onRemovePhoneApp: (String) -> Unit,
    onLaunchOneApp: (packageName: String, appName: String, useAll100: Boolean) -> Unit,
    onOpenPlayStore: (packageOrUrl: String, useAll100: Boolean) -> Unit,
    onPushPhoneApp: (app: SavedPhoneApp, apkFiles: List<File>, openAfterInstall: Boolean, useAll100: Boolean) -> Unit,
    onDispatchApp: (
        commandType: CommandType,
        packageName: String,
        appName: String,
        versionName: String,
        versionCode: Long,
        apkSourceUrl: String,
        useAll100: Boolean
    ) -> Unit,
    onNavigateToDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fleetColors = LocalFleetColors.current
    var packageName by remember { mutableStateOf("") }
    var appName by remember { mutableStateOf("") }
    var versionName by remember { mutableStateOf("1.0.0") }
    var versionCodeText by remember { mutableStateOf("1") }
    var apkSourceUrl by remember { mutableStateOf("") }
    val appLaunchCommands = remember(state.commands) {
        state.commands.filter { it.commandTypeEnum == CommandType.LAUNCH_APP }.take(25)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            IndividualSignupDeck(
                vms = state.vms,
                selectedVmCount = controls.selectedVmIds.size,
                preferredTargetPackage = preferredTargetPackage,
                savedPhoneApps = savedPhoneApps,
                isBusy = isBusy,
                onAssignGmailBatch = onAssignGmailBatch,
                onOpenGoogleSignIn = onOpenGoogleSignIn,
                onResetAppSessionForSignup = onResetAppSessionForSignup,
                onNavigateToDevices = onNavigateToDevices
            )
        }
        item {
            PlayStoreDownloadCard(
                selectedVmCount = controls.selectedVmIds.size,
                totalVmSlots = state.vms.size,
                isBusy = isBusy,
                onOpenPlayStoreHome = { all100 ->
                    onLaunchOneApp("com.android.vending", "Google Play Store", all100)
                },
                onOpenPlayStoreApp = onOpenPlayStore,
                onNavigateToDevices = onNavigateToDevices
            )
        }
        item {
            PhoneAppsDeck(
                savedPhoneApps = savedPhoneApps,
                selectedVmCount = controls.selectedVmIds.size,
                totalVmSlots = state.vms.size,
                isBusy = isBusy,
                onAddPhoneApps = onAddPhoneApps,
                onRemovePhoneApp = onRemovePhoneApp,
                onLaunchApp = onLaunchOneApp,
                onPushPhoneApp = onPushPhoneApp,
                onPrefillApkForm = { chosen ->
                    appName = chosen.appName
                    packageName = chosen.packageName
                    versionName = chosen.versionName
                    versionCodeText = chosen.versionCode.toString()
                },
                onNavigateToDevices = onNavigateToDevices
            )
        }

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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Authorized APK & Session Management",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Install, update, or uninstall authorized APKs in isolated VM environments. Each VM keeps its own app data and login session profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = appName,
                        onValueChange = { appName = it },
                        label = { Text("App Name (e.g., QA Staging Client)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apk_app_name_input")
                    )

                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = { Text("Package ID (e.g., com.company.staging)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apk_package_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = versionName,
                            onValueChange = { versionName = it },
                            label = { Text("Version Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = versionCodeText,
                            onValueChange = { versionCodeText = it.filter(Char::isDigit) },
                            label = { Text("Version Code") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = apkSourceUrl,
                        onValueChange = { apkSourceUrl = it },
                        label = { Text("Authorized APK Source HTTPS URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${controls.selectedVmIds.size} device(s) selected",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(onClick = onNavigateToDevices, shape = RoundedCornerShape(10.dp)) {
                            Text("Choose VMs")
                        }
                    }

                    val vCode = versionCodeText.toLongOrNull() ?: 1L
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onDispatchApp(
                                    CommandType.INSTALL_APK,
                                    packageName,
                                    appName,
                                    versionName,
                                    vCode,
                                    apkSourceUrl,
                                    false
                                )
                            },
                            enabled = !isBusy && controls.selectedVmIds.isNotEmpty() && packageName.isNotBlank() && appName.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("install_apk_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Install")
                        }

                        Button(
                            onClick = {
                                onDispatchApp(
                                    CommandType.INSTALL_APK,
                                    packageName,
                                    appName,
                                    versionName,
                                    vCode,
                                    apkSourceUrl,
                                    true
                                )
                            },
                            enabled = !isBusy && packageName.isNotBlank() && appName.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("install_all_apk_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Install All")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDispatchApp(
                                    CommandType.UPDATE_APK,
                                    packageName,
                                    appName,
                                    versionName,
                                    vCode,
                                    apkSourceUrl,
                                    false
                                )
                            },
                            enabled = !isBusy && controls.selectedVmIds.isNotEmpty() && packageName.isNotBlank() && appName.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Update Selected")
                        }

                        OutlinedButton(
                            onClick = {
                                onDispatchApp(
                                    CommandType.UNINSTALL_APK,
                                    packageName,
                                    appName,
                                    versionName,
                                    vCode,
                                    apkSourceUrl,
                                    false
                                )
                            },
                            enabled = !isBusy && controls.selectedVmIds.isNotEmpty() && packageName.isNotBlank() && appName.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = fleetColors.error),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remove Selected")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Per-VM Installed Applications (${state.apps.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (state.apps.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                ) {
                    Text(
                        text = "No per-VM APK records yet. Select VMs and dispatch an authorized APK installation above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(state.apps, key = { it.id }) { app ->
                val color = fleetColors.colorForInstallStatus(app.installStatusEnum)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${app.vmId} • ${app.appName} (v${app.versionName})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            StatusPill(label = app.installStatusEnum.label, color = color)
                        }
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Isolated Session: ${app.sessionProfileId} • ${formatTimestampShort(app.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (app.lastError.isNotBlank()) {
                            Text(
                                text = "Error: ${app.lastError}",
                                style = MaterialTheme.typography.bodySmall,
                                color = fleetColors.error
                            )
                        }
                    }
                }
            }
        }

        if (appLaunchCommands.isNotEmpty()) {
            item {
                Text(
                    text = "Recent App Open Status (${appLaunchCommands.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            items(appLaunchCommands, key = { it.id }) { cmd ->
                val color = fleetColors.colorForDeliveryStatus(cmd.deliveryStatusEnum)
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
                                text = "${cmd.vmId} • ${cmd.targetPayload}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            StatusPill(label = cmd.deliveryStatusEnum.label, color = color)
                        }
                        if (cmd.targetPackage.isNotBlank()) {
                            Text(
                                text = cmd.targetPackage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
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
