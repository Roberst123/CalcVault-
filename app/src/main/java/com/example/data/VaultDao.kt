package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    // Media queries
    @Query("SELECT * FROM vault_media ORDER BY dateImported DESC")
    fun getAllMediaFlow(): Flow<List<VaultMediaItem>>

    @Query("SELECT * FROM vault_media WHERE mediaType = 'PHOTO' ORDER BY dateImported DESC")
    fun getAllPhotosFlow(): Flow<List<VaultMediaItem>>

    @Query("SELECT * FROM vault_media WHERE mediaType = 'VIDEO' ORDER BY dateImported DESC")
    fun getAllVideosFlow(): Flow<List<VaultMediaItem>>

    @Query("SELECT * FROM vault_media WHERE albumId = :albumId ORDER BY dateImported DESC")
    fun getMediaByAlbumFlow(albumId: Long): Flow<List<VaultMediaItem>>

    @Query("SELECT * FROM vault_media WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: Long): VaultMediaItem?

    @Query("SELECT * FROM vault_media")
    suspend fun getAllMediaDirect(): List<VaultMediaItem>

    @Query("SELECT * FROM vault_media WHERE originalName LIKE '%' || :query || '%' ORDER BY dateImported DESC")
    fun searchMedia(query: String): Flow<List<VaultMediaItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(item: VaultMediaItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaList(items: List<VaultMediaItem>): List<Long>

    @Update
    suspend fun updateMedia(item: VaultMediaItem)

    @Delete
    suspend fun deleteMedia(item: VaultMediaItem)

    @Query("DELETE FROM vault_media WHERE id IN (:ids)")
    suspend fun deleteMediaByIds(ids: List<Long>)

    @Query("UPDATE vault_media SET albumId = :newAlbumId WHERE id IN (:ids)")
    suspend fun moveMediaToAlbum(ids: List<Long>, newAlbumId: Long)

    @Query("UPDATE vault_media SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("SELECT COUNT(*) FROM vault_media")
    fun getMediaCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vault_media WHERE albumId = :albumId")
    fun getMediaCountForAlbumFlow(albumId: Long): Flow<Int>

    // Albums queries
    @Query("SELECT * FROM vault_albums ORDER BY isSystemDefault DESC, name ASC")
    fun getAllAlbumsFlow(): Flow<List<VaultAlbum>>

    @Query("SELECT * FROM vault_albums")
    suspend fun getAllAlbumsDirect(): List<VaultAlbum>

    @Query("SELECT * FROM vault_albums WHERE name = :name LIMIT 1")
    suspend fun getAlbumByName(name: String): VaultAlbum?

    @Query("SELECT * FROM vault_albums WHERE id = :id LIMIT 1")
    suspend fun getAlbumById(id: Long): VaultAlbum?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: VaultAlbum): Long

    @Update
    suspend fun updateAlbum(album: VaultAlbum)

    @Query("DELETE FROM vault_albums WHERE id = :albumId AND isSystemDefault = 0")
    suspend fun deleteAlbumById(albumId: Long)

    // Security logs
    @Query("SELECT * FROM security_logs ORDER BY timestamp DESC LIMIT 50")
    fun getRecentSecurityLogsFlow(): Flow<List<SecurityLog>>

    @Insert
    suspend fun insertLog(log: SecurityLog)

    @Query("DELETE FROM security_logs")
    suspend fun clearLogs()
}
