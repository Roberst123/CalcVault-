package com.example.data

import android.content.Context
import android.net.Uri
import com.example.security.CryptoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class VaultRepository(
    private val context: Context,
    private val database: VaultDatabase
) {
    private val dao = database.vaultDao()

    val allMedia: Flow<List<VaultMediaItem>> = dao.getAllMediaFlow()
    val photos: Flow<List<VaultMediaItem>> = dao.getAllPhotosFlow()
    val videos: Flow<List<VaultMediaItem>> = dao.getAllVideosFlow()
    val albums: Flow<List<VaultAlbum>> = dao.getAllAlbumsFlow()
    val securityLogs: Flow<List<SecurityLog>> = dao.getRecentSecurityLogsFlow()

    fun search(query: String): Flow<List<VaultMediaItem>> = dao.searchMedia(query)
    fun getMediaByAlbum(albumId: Long): Flow<List<VaultMediaItem>> = dao.getMediaByAlbumFlow(albumId)

    suspend fun importMedia(
        uri: Uri,
        albumId: Long = 1L,
        onProgress: (Float) -> Unit
    ): VaultMediaItem? = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val inspection = CryptoEngine.inspectMedia(context, uri)

        var fileName = "media_${System.currentTimeMillis()}"
        // Query display name from ContentResolver
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
            }
        } catch (_: Exception) { }

        val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return@withContext null

        val (encryptedFileName, encryptedSize) = CryptoEngine.encryptStreamToVault(
            context = context,
            inputStream = inputStream,
            totalBytes = -1L,
            onProgress = onProgress
        )

        val mediaItem = VaultMediaItem(
            encryptedFileName = encryptedFileName,
            originalName = fileName,
            mimeType = inspection.mimeType,
            mediaType = inspection.mediaType,
            sizeBytes = encryptedSize,
            durationMs = inspection.durationMs,
            width = inspection.width,
            height = inspection.height,
            albumId = albumId,
            dateImported = System.currentTimeMillis()
        )

        val id = dao.insertMedia(mediaItem)
        dao.insertLog(
            SecurityLog(
                eventType = "IMPORT_MEDIA",
                details = "Encrypted $fileName (${inspection.mediaType}, ${(encryptedSize / 1024)} KB) into vault."
            )
        )

        mediaItem.copy(id = id)
    }

    suspend fun deleteMediaItems(items: List<VaultMediaItem>) = withContext(Dispatchers.IO) {
        for (item in items) {
            CryptoEngine.securelyDeleteVaultFile(context, item.encryptedFileName)
        }
        dao.deleteMediaByIds(items.map { it.id })
        dao.insertLog(
            SecurityLog(
                eventType = "DELETE_MEDIA",
                details = "Permanently shredded ${items.size} encrypted items from vault storage."
            )
        )
    }

    suspend fun exportMediaItem(item: VaultMediaItem, outputStream: OutputStream) = withContext(Dispatchers.IO) {
        CryptoEngine.decryptVaultFileToStream(context, item.encryptedFileName, outputStream)
        dao.insertLog(
            SecurityLog(
                eventType = "EXPORT_MEDIA",
                details = "Decrypted and exported ${item.originalName} to public storage."
            )
        )
    }

    suspend fun moveMediaToAlbum(itemIds: List<Long>, targetAlbumId: Long) = withContext(Dispatchers.IO) {
        dao.moveMediaToAlbum(itemIds, targetAlbumId)
    }

    suspend fun createAlbum(name: String): Long = withContext(Dispatchers.IO) {
        dao.insertAlbum(
            VaultAlbum(name = name, isSystemDefault = false, dateCreated = System.currentTimeMillis())
        )
    }

    suspend fun deleteAlbum(albumId: Long) = withContext(Dispatchers.IO) {
        // Move any media in this album to default "Recents" (id 1)
        val albumMedia = dao.getAllMediaDirect().filter { it.albumId == albumId }
        if (albumMedia.isNotEmpty()) {
            dao.moveMediaToAlbum(albumMedia.map { it.id }, 1L)
        }
        dao.deleteAlbumById(albumId)
    }

    suspend fun toggleFavorite(itemId: Long) = withContext(Dispatchers.IO) {
        dao.toggleFavorite(itemId)
    }

    suspend fun logEvent(eventType: String, details: String) = withContext(Dispatchers.IO) {
        dao.insertLog(SecurityLog(eventType = eventType, details = details))
    }
}
