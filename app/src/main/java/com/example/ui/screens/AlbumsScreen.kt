package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultAlbum
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.CalcVaultViewModel
import com.example.viewmodel.VaultTab

@Composable
fun AlbumsScreen(
    viewModel: CalcVaultViewModel,
    modifier: Modifier = Modifier
) {
    val albums by viewModel.albums.collectAsState()
    val allMedia by viewModel.repository.allMedia.collectAsState(initial = emptyList())

    var showCreateDialog by remember { mutableStateOf(false) }
    var albumToDelete by remember { mutableStateOf<VaultAlbum?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Media Albums",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("create_album_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = ObsidianBlack)
                Spacer(modifier = Modifier.size(4.dp))
                Text("New Album", color = ObsidianBlack, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(albums, key = { it.id }) { album ->
                val count = allMedia.count { it.albumId == album.id }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.selectAlbum(album.id)
                            viewModel.setTab(VaultTab.PHOTOS)
                        }
                        .testTag("album_card_${album.id}"),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF262833)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (album.isSystemDefault) Icons.Default.FolderSpecial else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = GoldAccent,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.size(16.dp))

                            Column {
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "$count encrypted items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        if (!album.isSystemDefault) {
                            IconButton(
                                onClick = { albumToDelete = album },
                                modifier = Modifier.testTag("delete_album_${album.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Album",
                                    tint = CrimsonError
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Album Dialog
    if (showCreateDialog) {
        var newAlbumName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Private Album") },
            text = {
                OutlinedTextField(
                    value = newAlbumName,
                    onValueChange = { newAlbumName = it },
                    label = { Text("Album Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("new_album_name_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newAlbumName.isNotBlank()) {
                            viewModel.createAlbum(newAlbumName)
                            showCreateDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_album_button")
                ) {
                    Text("Create", color = GoldAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CharcoalCard
        )
    }

    // Delete Album Dialog
    albumToDelete?.let { album ->
        AlertDialog(
            onDismissRequest = { albumToDelete = null },
            title = { Text("Delete Album '${album.name}'?") },
            text = {
                Text("Deleting this album will move its media back into the default 'Recents' album without deleting the files.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAlbum(album)
                        albumToDelete = null
                    }
                ) {
                    Text("Delete Album", color = CrimsonError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { albumToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CharcoalCard
        )
    }
}
