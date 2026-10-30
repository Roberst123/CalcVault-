package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.VaultMediaItem
import com.example.security.CryptoEngine
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.CalcVaultViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerDialog(
    item: VaultMediaItem,
    viewModel: CalcVaultViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(item.id) { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val tempVideoFile by viewModel.tempPlaybackFile.collectAsState()

    // SAF Export Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(item.mimeType)
    ) { uri: Uri? ->
        uri?.let { destUri ->
            context.contentResolver.openOutputStream(destUri)?.use { os ->
                viewModel.exportSingleItem(item, os)
            }
        }
    }

    LaunchedEffect(item.id) {
        if (item.mediaType == "PHOTO") {
            bitmap = CryptoEngine.loadDecryptedBitmap(
                context = context,
                encryptedFileName = item.encryptedFileName,
                targetWidth = 2048,
                targetHeight = 2048
            )
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Media View (Photo Zoomable or Video Player)
            if (item.mediaType == "PHOTO") {
                if (bitmap != null) {
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offsetX by remember { mutableFloatStateOf(0f) }
                    var offsetY by remember { mutableFloatStateOf(0f) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = item.originalName,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                        )
                    }
                } else if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GoldAccent)
                    }
                }
            } else {
                // Video Player
                if (tempVideoFile != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    val controller = MediaController(ctx)
                                    controller.setAnchorView(this)
                                    setMediaController(controller)
                                    setVideoPath(tempVideoFile!!.absolutePath)
                                    start()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GoldAccent)
                    }
                }
            }

            // Top Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("viewer_close_button")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }

                Row {
                    IconButton(
                        onClick = { showInfoSheet = true },
                        modifier = Modifier.testTag("viewer_info_button")
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "File Details", tint = Color.White)
                    }

                    IconButton(
                        onClick = { exportLauncher.launch(item.originalName) },
                        modifier = Modifier.testTag("viewer_export_button")
                    ) {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = "Export Decrypted", tint = Color.White)
                    }

                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("viewer_delete_button")
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonError)
                    }
                }
            }

            // Delete Confirmation Dialog
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Permanently Shred Item?") },
                    text = {
                        Text(
                            "This encrypted item will be shredded and removed from your private vault. This action cannot be undone."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                viewModel.deleteSingleItem(item)
                                onDismiss()
                            }
                        ) {
                            Text("Shred & Delete", color = CrimsonError, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    },
                    containerColor = CharcoalCard
                )
            }

            // Media Info BottomSheet
            if (showInfoSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showInfoSheet = false },
                    sheetState = rememberModalBottomSheetState(),
                    containerColor = CharcoalCard
                ) {
                    MediaInfoSheetContent(item = item)
                }
            }
        }
    }
}

@Composable
fun MediaInfoSheetContent(item: VaultMediaItem) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val formattedDate = remember(item.dateImported) { dateFormat.format(Date(item.dateImported)) }
    val formattedSize = remember(item.sizeBytes) {
        val kb = item.sizeBytes / 1024.0
        if (kb > 1024) String.format(Locale.getDefault(), "%.2f MB", kb / 1024.0)
        else String.format(Locale.getDefault(), "%.1f KB", kb)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .navigationBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Encrypted Vault Item Details",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        DetailRow(label = "Filename", value = item.originalName)
        DetailRow(label = "Media Type", value = item.mediaType)
        DetailRow(label = "MIME Format", value = item.mimeType)
        DetailRow(label = "Encrypted Size", value = formattedSize)
        if (item.width > 0 && item.height > 0) {
            DetailRow(label = "Resolution", value = "${item.width} × ${item.height}")
        }
        if (item.durationMs > 0) {
            val sec = (item.durationMs / 1000) % 60
            val min = (item.durationMs / 1000) / 60
            DetailRow(label = "Duration", value = String.format(Locale.getDefault(), "%02d:%02d", min, sec))
        }
        DetailRow(label = "Date Imported", value = formattedDate)
        DetailRow(label = "Cipher Engine", value = "AES-256-GCM (Hardware Keystore)")
        DetailRow(label = "Sandbox Storage", value = "Isolated internal filesDir (.nomedia)")
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
