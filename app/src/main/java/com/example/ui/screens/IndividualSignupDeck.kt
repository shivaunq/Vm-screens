package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.SavedPhoneApp
import com.example.data.model.VmAccountStatus
import com.example.data.model.VmSlot
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalFleetColors
import com.example.util.GmailBatchParser
import com.example.util.PlayStoreParser

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IndividualSignupDeck(
    vms: List<VmSlot>,
    selectedVmCount: Int,
    preferredTargetPackage: String,
    savedPhoneApps: List<SavedPhoneApp>,
    isBusy: Boolean,
    onAssignGmailBatch: (rawEmails: String, startSlotNumber: Int) -> Unit,
    onOpenGoogleSignIn: (useAll100: Boolean) -> Unit,
    onResetAppSessionForSignup: (packageName: String, appName: String, useAll100: Boolean) -> Unit,
    onNavigateToDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fleetColors = LocalFleetColors.current
    val clipboard = LocalClipboardManager.current
    val assignedCount = remember(vms) { vms.count { it.assignedGmail.isNotBlank() } }
    val playSignedInCount = remember(vms) {
        vms.count {
            it.accountStatusEnum == VmAccountStatus.PLAY_SIGNED_IN ||
                it.accountStatusEnum == VmAccountStatus.APP_SIGNED_UP
        }
    }
    val signedUpCount = remember(vms) {
        vms.count { it.accountStatusEnum == VmAccountStatus.APP_SIGNED_UP }
    }

    var showBatchEditor by remember(assignedCount) { mutableStateOf(assignedCount == 0) }
    var gmailListInput by remember { mutableStateOf("") }
    var startSlotText by remember { mutableStateOf("1") }
    val parsedEmails = remember(gmailListInput) { GmailBatchParser.parseUniqueEmails(gmailListInput) }

    val targetPkg = preferredTargetPackage.trim()
    val targetAppName = remember(targetPkg, savedPhoneApps) {
        when {
            targetPkg.isBlank() -> ""
            else -> savedPhoneApps.firstOrNull { it.packageName == targetPkg }?.appName
                ?: PlayStoreParser.friendlyAppName(targetPkg)
        }
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
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Individual Gmail & App Signup (100)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(
                    onClick = { showBatchEditor = !showBatchEditor },
                    modifier = Modifier.testTag("toggle_gmail_batch_editor")
                ) {
                    Text(if (showBatchEditor) "Hide List" else "Assign Gmails")
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (showBatchEditor) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = "Each VM-001..VM-100 runs with an isolated Play Store & app session so you can sign into a separate Gmail and sign up individually in every downloaded app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill(
                    label = "$assignedCount/${vms.size.coerceAtLeast(100)} Gmail Set",
                    color = if (assignedCount > 0) MaterialTheme.colorScheme.primary else fleetColors.offline
                )
                StatusPill(
                    label = "$playSignedInCount Play Signed-In",
                    color = if (playSignedInCount > 0) fleetColors.online else fleetColors.offline
                )
                StatusPill(
                    label = "$signedUpCount App Signed Up",
                    color = if (signedUpCount > 0) fleetColors.online else fleetColors.offline
                )
            }

            if (showBatchEditor) {
                OutlinedTextField(
                    value = gmailListInput,
                    onValueChange = { gmailListInput = it },
                    label = { Text("Paste Unique Gmails (1 per line -> VM-001..VM-100)") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let {
                                    gmailListInput = it
                                }
                            },
                            modifier = Modifier.testTag("paste_gmail_list_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste Gmail list from clipboard"
                            )
                        }
                    },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gmail_batch_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = startSlotText,
                        onValueChange = { startSlotText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Start Slot #") },
                        singleLine = true,
                        modifier = Modifier.width(116.dp)
                    )
                    Button(
                        onClick = {
                            val startSlot = (startSlotText.toIntOrNull() ?: 1).coerceIn(1, 100)
                            onAssignGmailBatch(gmailListInput, startSlot)
                        },
                        enabled = !isBusy && parsedEmails.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("assign_gmail_batch_button")
                    ) {
                        Text(
                            text = if (parsedEmails.isEmpty()) {
                                "Assign Gmails"
                            } else {
                                "Assign ${parsedEmails.size} Gmail(s)"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = if (parsedEmails.isNotEmpty()) {
                        val start = (startSlotText.toIntOrNull() ?: 1).coerceIn(1, 100)
                        val end = (start + parsedEmails.size - 1).coerceAtMost(100)
                        "Ready: ${parsedEmails.size} unique Gmail(s) -> VM-${"%03d".format(start)}..VM-${"%03d".format(end)}. Passwords are never saved."
                    } else {
                        "Privacy: only Gmail address labels are stored; enter Google passwords only inside each VM's official sign-in screen."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (parsedEmails.isNotEmpty()) fleetColors.online else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onOpenGoogleSignIn(false) },
                    enabled = !isBusy && selectedVmCount > 0,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("google_signin_selected_button")
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Google Sign-In Sel.", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = { onOpenGoogleSignIn(true) },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("google_signin_all_button")
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Google Sign-In (100)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (targetPkg.isNotBlank() && targetPkg != "com.android.vending") {
                Text(
                    text = "Active Signup App: $targetAppName ($targetPkg)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onResetAppSessionForSignup(targetPkg, targetAppName, false) },
                        enabled = !isBusy && selectedVmCount > 0,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("fresh_signup_selected_button")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Fresh Signup Sel.", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = { onResetAppSessionForSignup(targetPkg, targetAppName, true) },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("fresh_signup_all_button")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Fresh Signup (100)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                Text(
                    text = "Tip: Pick or download any app below (Play Store or Phone Apps) to enable 1-tap Fresh Signup Reset across 100 screens.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = onNavigateToDevices,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("goto_100_screens_signup_button")
            ) {
                Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open 100 Screens -> Tap Quick entry to Type Gmail")
            }
        }
    }
}
