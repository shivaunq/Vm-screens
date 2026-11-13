package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Shop
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalFleetColors
import com.example.util.PlayStoreParser

@Composable
fun PlayStoreDownloadCard(
    selectedVmCount: Int,
    totalVmSlots: Int,
    isBusy: Boolean,
    onOpenPlayStoreHome: (useAll100: Boolean) -> Unit,
    onOpenPlayStoreApp: (packageOrUrl: String, useAll100: Boolean) -> Unit,
    onNavigateToDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fleetColors = LocalFleetColors.current
    val clipboard = LocalClipboardManager.current
    var playStoreInput by remember { mutableStateOf("") }
    val parsedPlayPkg = remember(playStoreInput) { PlayStoreParser.extractPackageId(playStoreInput) }
    val isHomeMode = playStoreInput.isBlank()

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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Google Play Store App & Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Open the Google Play Store app across 100 devices, or paste any Play Store URL / package ID to jump directly to its install page. Requires a Google Play system image (com.android.vending) on your host VMs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = playStoreInput,
                onValueChange = { playStoreInput = it },
                label = { Text("Play Store URL or Package ID (blank = Play Store Home)") },
                leadingIcon = { Icon(Icons.Default.Shop, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let {
                                playStoreInput = it
                            }
                        },
                        modifier = Modifier.testTag("paste_play_store_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste Play Store link or package ID"
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("play_store_input")
            )

            if (!isHomeMode) {
                Text(
                    text = if (parsedPlayPkg != null) {
                        "Resolved Play Store Package: $parsedPlayPkg"
                    } else {
                        "Enter a valid package ID (e.g. com.whatsapp) or play.google.com/store/apps/details?id=... link."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (parsedPlayPkg != null) fleetColors.online else fleetColors.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$selectedVmCount selected • $totalVmSlots/100 slots ready",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedButton(onClick = onNavigateToDevices, shape = RoundedCornerShape(10.dp)) {
                    Text("Choose VMs")
                }
            }

            val canExecute = !isBusy && (isHomeMode || parsedPlayPkg != null)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isHomeMode) onOpenPlayStoreHome(false)
                        else onOpenPlayStoreApp(playStoreInput, false)
                    },
                    enabled = canExecute && selectedVmCount > 0,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("play_store_selected_button")
                ) {
                    Icon(Icons.Default.Shop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isHomeMode) "Play Store Sel." else "Download Sel.")
                }
                Button(
                    onClick = {
                        if (isHomeMode) onOpenPlayStoreHome(true)
                        else onOpenPlayStoreApp(playStoreInput, true)
                    },
                    enabled = canExecute,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("play_store_all_button")
                ) {
                    Icon(Icons.Default.Shop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isHomeMode) "Play Store (100)" else "Download (100)")
                }
            }
        }
    }
}
