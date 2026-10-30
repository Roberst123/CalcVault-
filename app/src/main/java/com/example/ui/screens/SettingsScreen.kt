package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.AuthManager
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.CalcVaultViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: CalcVaultViewModel,
    modifier: Modifier = Modifier
) {
    val auth = viewModel.authManager
    var isBiometric by remember { mutableStateOf(auth.isBiometricEnabled()) }
    var autoLockSec by remember { mutableIntStateOf(auth.getAutoLockInterval()) }
    var isFlagSecure by remember { mutableStateOf(auth.isFlagSecureEnabled()) }

    var showChangePinDialog by remember { mutableStateOf(false) }
    var showAutoLockDialog by remember { mutableStateOf(false) }
    var showAuditLogsDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Security & Settings",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Authentication Settings Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Authentication Controls",
                    style = MaterialTheme.typography.titleMedium,
                    color = GoldAccent,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Change PIN
                SettingsClickableRow(
                    icon = Icons.Default.Key,
                    title = "Change Master PIN",
                    subtitle = "Update your disguise calculator unlock PIN",
                    onClick = { showChangePinDialog = true },
                    testTag = "change_pin_row"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Biometrics Toggle
                SettingsSwitchRow(
                    icon = Icons.Default.Fingerprint,
                    title = "Biometric Authentication",
                    subtitle = if (auth.isBiometricHardwareAvailable()) "Unlock vault via fingerprint or face recognition"
                    else "Hardware sensor not available or un-enrolled",
                    checked = isBiometric,
                    enabled = auth.isBiometricHardwareAvailable(),
                    onCheckedChange = { checked ->
                        isBiometric = checked
                        auth.setBiometricEnabled(checked)
                    },
                    testTag = "biometric_toggle"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Auto-Lock Timer
                val autoLockLabel = when (autoLockSec) {
                    AuthManager.AUTO_LOCK_IMMEDIATE -> "Immediately on backgrounding"
                    AuthManager.AUTO_LOCK_30_SECONDS -> "After 30 seconds"
                    AuthManager.AUTO_LOCK_1_MINUTE -> "After 1 minute"
                    AuthManager.AUTO_LOCK_5_MINUTES -> "After 5 minutes"
                    else -> "$autoLockSec seconds"
                }

                SettingsClickableRow(
                    icon = Icons.Default.LockClock,
                    title = "Auto-Lock Timeout",
                    subtitle = autoLockLabel,
                    onClick = { showAutoLockDialog = true },
                    testTag = "auto_lock_row"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Stealth Mode / FLAG_SECURE
                SettingsSwitchRow(
                    icon = Icons.Default.VisibilityOff,
                    title = "Recent Apps Disguise (FLAG_SECURE)",
                    subtitle = "Blocks screenshots and masks vault preview in App Switcher",
                    checked = isFlagSecure,
                    enabled = true,
                    onCheckedChange = { checked ->
                        isFlagSecure = checked
                        auth.setFlagSecureEnabled(checked)
                    },
                    testTag = "flag_secure_toggle"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy & Logs Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Transparency & Audit",
                    style = MaterialTheme.typography.titleMedium,
                    color = GoldAccent,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsClickableRow(
                    icon = Icons.Default.History,
                    title = "Security Audit Logs",
                    subtitle = "Review timestamped authentications, exports, and lockouts",
                    onClick = { showAuditLogsDialog = true },
                    testTag = "audit_logs_row"
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsClickableRow(
                    icon = Icons.Default.Policy,
                    title = "Privacy Policy & Architecture",
                    subtitle = "Review offline guarantees, permissions, and cryptography details",
                    onClick = { showPrivacyPolicyDialog = true },
                    testTag = "privacy_policy_row"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Zero-Backdoor Guarantee Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = GoldAccent,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.size(12.dp))

                Column {
                    Text(
                        text = "Zero-Backdoor Cryptographic Policy",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "CalcVault enforces strict zero-knowledge security. There is no secret universal master PIN, no remote server recovery, and no engineering bypass. If your PIN and backup passphrase are lost, the encrypted media cannot be recovered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }

    // Change PIN Dialog
    if (showChangePinDialog) {
        var currentPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            title = { Text("Change Master PIN") },
            text = {
                Column {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { currentPin = it },
                        label = { Text("Current PIN") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("current_pin_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it },
                        label = { Text("New Secret PIN (4-8 digits)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("new_pin_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { confirmPin = it },
                        label = { Text("Confirm New PIN") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("confirm_new_pin_input")
                    )

                    error?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = it, color = CrimsonError, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPin.length < 4) {
                            error = "New PIN must be at least 4 digits"
                        } else if (newPin != confirmPin) {
                            error = "New PINs do not match"
                        } else {
                            val success = viewModel.changeMasterPin(currentPin, newPin)
                            if (success) {
                                showChangePinDialog = false
                            } else {
                                error = "Current PIN is incorrect"
                            }
                        }
                    },
                    modifier = Modifier.testTag("submit_change_pin_button")
                ) {
                    Text("Update PIN", color = GoldAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CharcoalCard
        )
    }

    // Auto-Lock Timeout Dialog
    if (showAutoLockDialog) {
        val options = listOf(
            AuthManager.AUTO_LOCK_IMMEDIATE to "Immediately",
            AuthManager.AUTO_LOCK_30_SECONDS to "30 Seconds",
            AuthManager.AUTO_LOCK_1_MINUTE to "1 Minute",
            AuthManager.AUTO_LOCK_5_MINUTES to "5 Minutes"
        )

        AlertDialog(
            onDismissRequest = { showAutoLockDialog = false },
            title = { Text("Auto-Lock Interval") },
            text = {
                Column {
                    Text(
                        text = "Automatically re-lock the vault when the application stays backgrounded for this duration:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    options.forEach { (sec, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    autoLockSec = sec
                                    auth.setAutoLockInterval(sec)
                                    showAutoLockDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (autoLockSec == sec),
                                onClick = {
                                    autoLockSec = sec
                                    auth.setAutoLockInterval(sec)
                                    showAutoLockDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = GoldAccent)
                            )
                            Text(
                                text = label,
                                color = TextPrimary,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAutoLockDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = CharcoalCard
        )
    }

    // Audit Logs Dialog
    if (showAuditLogsDialog) {
        val logs by viewModel.securityLogs.collectAsState()
        val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

        AlertDialog(
            onDismissRequest = { showAuditLogsDialog = false },
            title = { Text("Security Audit Logs") },
            text = {
                Column(modifier = Modifier.height(300.dp)) {
                    if (logs.isEmpty()) {
                        Text(
                            text = "No audit entries recorded yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    } else {
                        LazyColumn {
                            items(logs) { log ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = log.eventType,
                                            color = GoldAccent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = dateFormat.format(Date(log.timestamp)),
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(
                                        text = log.details,
                                        color = TextPrimary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAuditLogsDialog = false }) {
                    Text("Done", color = GoldAccent)
                }
            },
            containerColor = CharcoalCard
        )
    }

    // Privacy Policy Dialog
    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            title = { Text("CalcVault Privacy Policy") },
            text = {
                Column(
                    modifier = Modifier
                        .height(350.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "1. Zero Data Collection & Zero Telemetry\n" +
                                "CalcVault is built strictly offline. It contains no tracking SDKs, no advertising networks, no analytics trackers, and makes zero network calls.\n\n" +
                                "2. Cryptographic Storage Architecture\n" +
                                "All imported media is encrypted using 256-bit AES in Galois/Counter Mode (AES-GCM). Cryptographic master keys are stored securely within the hardware-backed Android KeyStore.\n\n" +
                                "3. Isolation & Gallery Privacy\n" +
                                "Encrypted files reside strictly inside the application's sandboxed storage directory with .nomedia flags active. No public photo viewer or gallery app can index or display private content.\n\n" +
                                "4. Zero Universal Backdoor\n" +
                                "CalcVault cannot decrypt your files without your secret PIN, enrolled biometric, or encrypted backup passphrase.\n\n" +
                                "5. Permissions\n" +
                                "The app declares minimal permissions (Biometrics, Haptic Vibration). It never asks for Location, Contacts, Microphone, SMS, or Phone logs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyPolicyDialog = false }) {
                    Text("Understood", color = GoldAccent)
                }
            },
            containerColor = CharcoalCard
        )
    }
}

@Composable
fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) GoldAccent else TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary.copy(alpha = if (enabled) 1f else 0.5f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GoldAccent,
                checkedTrackColor = Color(0xFF332008)
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
