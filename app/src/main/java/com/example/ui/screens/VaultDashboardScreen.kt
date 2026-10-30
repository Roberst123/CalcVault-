package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.VaultAlbum
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.CalcVaultViewModel
import com.example.viewmodel.MediaSortOrder
import com.example.viewmodel.VaultTab

@Composable
fun VaultDashboardScreen(
    viewModel: CalcVaultViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val mediaList by viewModel.mediaList.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedIds by viewModel.selectedMediaIds.collectAsState()
    val activeViewerItem by viewModel.activeViewerItem.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val albums by viewModel.albums.collectAsState()

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoveAlbumDialog by remember { mutableStateOf(false) }

    // Multi-Select Media Picker Launcher (Zero permissions required on Android 13+, PhotoPicker fallback on older)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importMediaUris(uris)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding(),
        topBar = {
            Column(modifier = Modifier.background(CharcoalSurface)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = when (currentTab) {
                                VaultTab.PHOTOS -> "Private Photos"
                                VaultTab.VIDEOS -> "Private Videos"
                                VaultTab.ALBUMS -> "Vault Albums"
                                VaultTab.BACKUP -> "Backup & Recovery"
                                VaultTab.SETTINGS -> "Security & Settings"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Action Icons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentTab == VaultTab.PHOTOS || currentTab == VaultTab.VIDEOS) {
                            // Search toggle
                            IconButton(
                                onClick = {
                                    showSearch = !showSearch
                                    if (!showSearch) {
                                        searchQuery = ""
                                        viewModel.setSearchQuery("")
                                    }
                                },
                                modifier = Modifier.testTag("dashboard_search_button")
                            ) {
                                Icon(
                                    imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = TextSecondary
                                )
                            }

                            // Sort menu
                            Box {
                                IconButton(
                                    onClick = { showSortMenu = true },
                                    modifier = Modifier.testTag("dashboard_sort_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "Sort",
                                        tint = TextSecondary
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    modifier = Modifier.background(CharcoalCard)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Date (Newest First)", color = TextPrimary) },
                                        onClick = {
                                            viewModel.setSortOrder(MediaSortOrder.DATE_DESC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Date (Oldest First)", color = TextPrimary) },
                                        onClick = {
                                            viewModel.setSortOrder(MediaSortOrder.DATE_ASC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Name (A to Z)", color = TextPrimary) },
                                        onClick = {
                                            viewModel.setSortOrder(MediaSortOrder.NAME_ASC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Size (Largest First)", color = TextPrimary) },
                                        onClick = {
                                            viewModel.setSortOrder(MediaSortOrder.SIZE_DESC)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }

                            // Multi-Select toggle
                            IconButton(
                                onClick = { viewModel.toggleSelectionMode() },
                                modifier = Modifier.testTag("dashboard_multiselect_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Checklist,
                                    contentDescription = "Select",
                                    tint = if (isSelectionMode) GoldAccent else TextSecondary
                                )
                            }
                        }

                        // Quick Lock
                        IconButton(
                            onClick = { viewModel.lockVault() },
                            modifier = Modifier.testTag("dashboard_lock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock Vault",
                                tint = CrimsonError
                            )
                        }
                    }
                }

                // Search Bar field
                AnimatedVisibility(visible = showSearch) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { query ->
                                searchQuery = query
                                viewModel.setSearchQuery(query)
                            },
                            placeholder = { Text("Search by filename...", color = TextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = Color(0xFF374151),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("dashboard_search_input")
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                // Multi-select action bar (when items are selected)
                AnimatedVisibility(visible = isSelectionMode && selectedIds.isNotEmpty()) {
                    Surface(
                        color = CharcoalCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedIds.size} selected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )

                            Row {
                                TextButton(
                                    onClick = { showMoveAlbumDialog = true },
                                    modifier = Modifier.testTag("batch_move_album_button")
                                ) {
                                    Icon(imageVector = Icons.Default.DriveFileMove, contentDescription = null, tint = GoldAccent)
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("Move", color = GoldAccent)
                                }

                                TextButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier.testTag("batch_delete_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = CrimsonError)
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("Delete", color = CrimsonError)
                                }
                            }
                        }
                    }
                }

                // Standard Navigation Bar
                NavigationBar(
                    containerColor = CharcoalSurface,
                    contentColor = TextPrimary
                ) {
                    NavigationBarItem(
                        selected = currentTab == VaultTab.PHOTOS,
                        onClick = { viewModel.setTab(VaultTab.PHOTOS) },
                        icon = { Icon(Icons.Default.Photo, contentDescription = "Photos") },
                        label = { Text("Photos", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBlack,
                            selectedTextColor = GoldAccent,
                            indicatorColor = GoldAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("tab_photos")
                    )

                    NavigationBarItem(
                        selected = currentTab == VaultTab.VIDEOS,
                        onClick = { viewModel.setTab(VaultTab.VIDEOS) },
                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Videos") },
                        label = { Text("Videos", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBlack,
                            selectedTextColor = GoldAccent,
                            indicatorColor = GoldAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("tab_videos")
                    )

                    NavigationBarItem(
                        selected = currentTab == VaultTab.ALBUMS,
                        onClick = { viewModel.setTab(VaultTab.ALBUMS) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Albums") },
                        label = { Text("Albums", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBlack,
                            selectedTextColor = GoldAccent,
                            indicatorColor = GoldAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("tab_albums")
                    )

                    NavigationBarItem(
                        selected = currentTab == VaultTab.BACKUP,
                        onClick = { viewModel.setTab(VaultTab.BACKUP) },
                        icon = { Icon(Icons.Default.Security, contentDescription = "Backup") },
                        label = { Text("Backup", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBlack,
                            selectedTextColor = GoldAccent,
                            indicatorColor = GoldAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("tab_backup")
                    )

                    NavigationBarItem(
                        selected = currentTab == VaultTab.SETTINGS,
                        onClick = { viewModel.setTab(VaultTab.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBlack,
                            selectedTextColor = GoldAccent,
                            indicatorColor = GoldAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("tab_settings")
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab == VaultTab.PHOTOS || currentTab == VaultTab.VIDEOS) {
                FloatingActionButton(
                    onClick = {
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(
                                if (currentTab == VaultTab.VIDEOS) ActivityResultContracts.PickVisualMedia.VideoOnly
                                else ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    },
                    containerColor = GoldAccent,
                    contentColor = ObsidianBlack,
                    modifier = Modifier.testTag("import_media_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Import Media")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ObsidianBlack)
        ) {
            when (currentTab) {
                VaultTab.PHOTOS, VaultTab.VIDEOS -> {
                    MediaGridComponent(
                        mediaList = mediaList,
                        isSelectionMode = isSelectionMode,
                        selectedIds = selectedIds,
                        onItemClick = { item ->
                            if (isSelectionMode) {
                                viewModel.toggleItemSelection(item.id)
                            } else {
                                viewModel.openMediaViewer(item)
                            }
                        },
                        onItemLongClick = { item ->
                            viewModel.toggleItemSelection(item.id)
                        }
                    )
                }
                VaultTab.ALBUMS -> AlbumsScreen(viewModel = viewModel)
                VaultTab.BACKUP -> BackupScreen(viewModel = viewModel)
                VaultTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }

    // Fullscreen Media Viewer Dialog
    activeViewerItem?.let { item ->
        MediaViewerDialog(
            item = item,
            viewModel = viewModel,
            onDismiss = { viewModel.closeMediaViewer() }
        )
    }

    // Import Progress Modal
    if (importProgress.isImporting) {
        Dialog(onDismissRequest = {}) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(44.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Encrypting Media into Vault...",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Item ${importProgress.currentItemIndex} of ${importProgress.totalItems}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LinearProgressIndicator(
                        progress = { importProgress.progress },
                        color = GoldAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = importProgress.currentFileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    // Batch Delete Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Permanently Shred ${selectedIds.size} Items?") },
            text = {
                Text(
                    "Selected encrypted items will be shredded and permanently removed from vault storage. Note: Originals remaining in public device galleries will not be altered."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteSelectedMedia()
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

    // Move to Album Dialog
    if (showMoveAlbumDialog) {
        AlertDialog(
            onDismissRequest = { showMoveAlbumDialog = false },
            title = { Text("Move Selected Media to Album") },
            text = {
                Column {
                    albums.forEach { album ->
                        TextButton(
                            onClick = {
                                viewModel.moveSelectedMediaToAlbum(album.id)
                                showMoveAlbumDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = album.name,
                                color = GoldAccent,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveAlbumDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CharcoalCard
        )
    }
}
