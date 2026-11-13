package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.InstallStatus
import com.example.data.model.SavedPhoneApp
import com.example.data.model.VmApp

@Composable
fun DownloadedAppSelectorRow(
    trackedApps: List<VmApp>,
    savedPhoneApps: List<SavedPhoneApp>,
    selectedPackage: String,
    onSelectPackage: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Open Inside Downloaded App (100 Screens)"
) {
    val downloadedApps = remember(trackedApps, savedPhoneApps) {
        val tracked = trackedApps
            .filter { it.installStatusEnum != InstallStatus.UNINSTALLED && it.packageName.isNotBlank() }
            .map { it.packageName to it.appName }
        val phone = savedPhoneApps.map { it.packageName to it.appName }
        (tracked + phone).distinctBy { it.first }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedPackage.isBlank(),
                onClick = { onSelectPackage("") },
                leadingIcon = if (selectedPackage.isBlank()) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null,
                label = { Text("Auto / Default") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            FilterChip(
                selected = selectedPackage == "com.android.vending",
                onClick = { onSelectPackage("com.android.vending") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Shop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Play Store") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            downloadedApps
                .filter { it.first != "com.android.vending" }
                .forEach { (pkg, label) ->
                    val isSelected = selectedPackage == pkg
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPackage(pkg) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Apps,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
        }
    }
}
