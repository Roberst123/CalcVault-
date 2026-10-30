package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.CalcVaultViewModel

@Composable
fun BackupScreen(
    viewModel: CalcVaultViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val backupState by viewModel.backupState.collectAsState()

    var showCreateBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var selectedRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingExportPassphrase by remember { mutableStateOf("") }

    // SAF Document Launchers
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let { destUri ->
            context.contentResolver.openOutputStream(destUri)?.use { os ->
                viewModel.createEncryptedBackup(pendingExportPassphrase, os)
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { srcUri ->
            selectedRestoreUri = srcUri
            showRestoreDialog = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Backup & Recovery",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Backup Operation Progress Card
        if (backupState.isRunning || backupState.isSuccess || backupState.errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (backupState.errorMessage != null) CrimsonError.copy(alpha = 0.15f)
                    else if (backupState.isSuccess) EmeraldSuccess.copy(alpha = 0.15f)
                    else CharcoalCard
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (backupState.isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = GoldAccent
                            )
                        } else {
                            Icon(
                                imageVector = if (backupState.isSuccess) Icons.Default.Security else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (backupState.isSuccess) EmeraldSuccess else CrimsonError,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = if (backupState.isRunning) "Processing Encrypted Archive..."
                            else if (backupState.isSuccess) "Operation Complete"
                            else "Operation Error",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (backupState.isRunning) {
                        LinearProgressIndicator(
                            progress = { backupState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = GoldAccent
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        text = backupState.errorMessage ?: backupState.statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (backupState.errorMessage != null) CrimsonError else TextSecondary
                    )

                    if (!backupState.isRunning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { viewModel.clearBackupState() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Dismiss", color = GoldAccent)
                        }
                    }
                }
            }
        }

        // Action Buttons Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Encrypted Local Storage Backup",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Export an encrypted .calcvault package containing all your private photos, videos, and album hierarchies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Button(
                    onClick = { showCreateBackupDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("create_backup_button")
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = ObsidianBlack)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Create Encrypted Backup", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { openBackupLauncher.launch(arrayOf("*/*")) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2E3C)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("restore_backup_button")
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = TextPrimary)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Restore from Backup File", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Safe Storage Guidance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CloudOff, contentDescription = null, tint = GoldAccent)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Safe Backup Storage Rules",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "1. CalcVault never uploads your files to any remote server or cloud account.\n" +
                            "2. Transfer your encrypted backup to an offline USB drive, computer, or personal external drive.\n" +
                            "3. Keep your backup passphrase securely memorized or in a password manager. Zero recovery backdoors exist if the passphrase is forgotten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Flash Storage Limitations Notice Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = GoldAccent)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "NAND Flash Storage Notice",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Modern smartphone flash drives utilize wear-leveling algorithms and Flash Translation Layers (FTL). When files are deleted or overwritten in user space, physical flash memory cells are scheduled for garbage collection by the hardware controller rather than immediately zeroed. CalcVault safely shreds internal cache references, but originals remaining in public gallery apps outside the vault should be managed intentionally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }

    // Create Backup Dialog
    if (showCreateBackupDialog) {
        var passphrase by remember { mutableStateOf("") }
        var passphraseConfirm by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showCreateBackupDialog = false },
            title = { Text("Set Backup Passphrase") },
            text = {
                Column {
                    Text(
                        text = "Choose a strong recovery passphrase to encrypt your backup container using AES-256-GCM.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Backup Passphrase (min 6 chars)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("backup_passphrase_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = passphraseConfirm,
                        onValueChange = { passphraseConfirm = it },
                        label = { Text("Confirm Passphrase") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("backup_passphrase_confirm_input")
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
                        if (passphrase.length < 6) {
                            error = "Passphrase must be at least 6 characters"
                        } else if (passphrase != passphraseConfirm) {
                            error = "Passphrases do not match"
                        } else {
                            pendingExportPassphrase = passphrase
                            showCreateBackupDialog = false
                            createBackupLauncher.launch("calcvault_backup_${System.currentTimeMillis()}.calcvault")
                        }
                    },
                    modifier = Modifier.testTag("confirm_export_backup_button")
                ) {
                    Text("Select Destination & Export", color = GoldAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateBackupDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CharcoalCard
        )
    }

    // Restore Backup Dialog
    if (showRestoreDialog && selectedRestoreUri != null) {
        var restorePassphrase by remember { mutableStateOf("") }
        var restoreError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Authenticate Backup Restore") },
            text = {
                Column {
                    Text(
                        text = "Enter the encryption passphrase created when this backup file was generated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = restorePassphrase,
                        onValueChange = { restorePassphrase = it },
                        label = { Text("Backup Passphrase") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("restore_passphrase_input")
                    )

                    restoreError?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = it, color = CrimsonError, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (restorePassphrase.isBlank()) {
                            restoreError = "Please enter the passphrase"
                        } else {
                            showRestoreDialog = false
                            context.contentResolver.openInputStream(selectedRestoreUri!!)?.use { inputStream ->
                                viewModel.restoreEncryptedBackup(restorePassphrase, inputStream)
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_restore_button")
                ) {
                    Text("Decrypt & Restore", color = GoldAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CharcoalCard
        )
    }
}
