package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_media",
    indices = [
        Index(value = ["albumId"]),
        Index(value = ["dateImported"]),
        Index(value = ["mediaType"])
    ]
)
data class VaultMediaItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val encryptedFileName: String,
    val originalName: String,
    val mimeType: String,
    val mediaType: String, // "PHOTO" or "VIDEO"
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val albumId: Long = 1L, // Foreign key referencing VaultAlbum.id
    val dateImported: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

@Entity(tableName = "vault_albums")
data class VaultAlbum(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isSystemDefault: Boolean = false,
    val dateCreated: Long = System.currentTimeMillis()
)

@Entity(tableName = "security_logs")
data class SecurityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // "PIN_SUCCESS", "BIOMETRIC_SUCCESS", "FAILED_PIN", "LOCKOUT", "PIN_CHANGED", "EXPORT", "BACKUP"
    val details: String
)
