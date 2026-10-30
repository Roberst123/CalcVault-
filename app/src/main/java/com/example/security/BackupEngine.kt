package com.example.security

import android.content.Context
import com.example.data.VaultAlbum
import com.example.data.VaultDatabase
import com.example.data.VaultMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupEngine {
    private const val BACKUP_MAGIC_HEADER = "CALCVAULT_ENCRYPTED_BACKUP_V1"
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private const val SALT_LEN = 32
    private const val IV_LEN = 12
    private const val TAG_LEN_BITS = 128

    private val secureRandom = SecureRandom()

    private fun deriveKeyFromPassphrase(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Creates an encrypted backup container streamed to the destination outputStream.
     * Returns the number of media items backed up.
     */
    suspend fun createEncryptedBackup(
        context: Context,
        database: VaultDatabase,
        passphrase: String,
        outputStream: OutputStream,
        onProgress: (Float, String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val mediaDao = database.vaultDao()
        val mediaList = mediaDao.getAllMediaDirect()
        val albumList = mediaDao.getAllAlbumsDirect()

        onProgress(0.05f, "Preparing backup package...")

        // Step 1: Pack unencrypted or internal files into an in-memory or temp ZIP archive
        val tempZipFile = File(context.cacheDir, "temp_backup_${System.currentTimeMillis()}.zip")
        tempZipFile.deleteOnExit()

        try {
            FileOutputStream(tempZipFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    // Add Manifest JSON
                    val manifest = JSONObject().apply {
                        put("version", 1)
                        put("createdAt", System.currentTimeMillis())
                        put("itemCount", mediaList.size)

                        val albumsArray = JSONArray()
                        for (album in albumList) {
                            albumsArray.put(JSONObject().apply {
                                put("id", album.id)
                                put("name", album.name)
                                put("isSystemDefault", album.isSystemDefault)
                                put("dateCreated", album.dateCreated)
                            })
                        }
                        put("albums", albumsArray)

                        val mediaArray = JSONArray()
                        for (item in mediaList) {
                            mediaArray.put(JSONObject().apply {
                                put("id", item.id)
                                put("originalName", item.originalName)
                                put("mimeType", item.mimeType)
                                put("mediaType", item.mediaType)
                                put("sizeBytes", item.sizeBytes)
                                put("durationMs", item.durationMs)
                                put("width", item.width)
                                put("height", item.height)
                                put("albumId", item.albumId)
                                put("dateImported", item.dateImported)
                                put("isFavorite", item.isFavorite)
                                put("zipEntryName", "media/${item.encryptedFileName}")
                            })
                        }
                        put("media", mediaArray)
                    }

                    zos.putNextEntry(ZipEntry("manifest.json"))
                    zos.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // Add all decrypted original bytes or encrypted files
                    // To ensure backup is self-contained even across device keystore changes,
                    // we decrypt using device Keystore and re-encrypt under user's backup passphrase!
                    val total = mediaList.size
                    for ((index, item) in mediaList.withIndex()) {
                        val file = File(CryptoEngine.getVaultDir(context), item.encryptedFileName)
                        if (file.exists()) {
                            onProgress(
                                0.1f + (index.toFloat() / total) * 0.4f,
                                "Securing item ${index + 1} of $total: ${item.originalName}"
                            )
                            zos.putNextEntry(ZipEntry("media/${item.encryptedFileName}"))
                            // Stream decrypted content into ZIP entry
                            val decryptedBytes = CryptoEngine.decryptVaultFileToBytes(context, item.encryptedFileName)
                            zos.write(decryptedBytes)
                            zos.closeEntry()
                        }
                    }
                }
            }

            // Step 2: Encrypt tempZipFile with user's backup passphrase using AES-256-GCM
            onProgress(0.6f, "Encrypting entire backup archive with passphrase...")
            val salt = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }
            val iv = ByteArray(IV_LEN).also { secureRandom.nextBytes(it) }
            val secretKey = deriveKeyFromPassphrase(passphrase, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(TAG_LEN_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            // Header format:
            // [MAGIC_BYTES_UTF8 (30B)] [SALT (32B)] [IV (12B)] [CIPHERTEXT...]
            outputStream.write(BACKUP_MAGIC_HEADER.toByteArray(Charsets.UTF_8))
            outputStream.write(salt)
            outputStream.write(iv)

            val zipLength = tempZipFile.length()
            var bytesEncrypted = 0L

            FileInputStream(tempZipFile).use { fis ->
                CipherOutputStream(outputStream, cipher).use { cos ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        cos.write(buffer, 0, read)
                        bytesEncrypted += read
                        if (zipLength > 0) {
                            onProgress(0.6f + (bytesEncrypted.toFloat() / zipLength) * 0.35f, "Writing encrypted backup...")
                        }
                    }
                    cos.flush()
                }
            }

            onProgress(1.0f, "Backup completed successfully!")
            mediaList.size
        } finally {
            tempZipFile.delete()
        }
    }

    data class RestoreSummary(
        val restoredCount: Int,
        val failedCount: Int,
        val restoredAlbums: Int
    )

    /**
     * Decrypts and restores an encrypted backup package from inputStream using the user's passphrase.
     */
    suspend fun restoreEncryptedBackup(
        context: Context,
        database: VaultDatabase,
        passphrase: String,
        inputStream: InputStream,
        onProgress: (Float, String) -> Unit
    ): RestoreSummary = withContext(Dispatchers.IO) {
        onProgress(0.05f, "Validating backup header...")

        // Read and verify Magic Header
        val headerBytes = ByteArray(BACKUP_MAGIC_HEADER.toByteArray(Charsets.UTF_8).size)
        val readHeader = inputStream.read(headerBytes)
        if (readHeader != headerBytes.size || String(headerBytes, Charsets.UTF_8) != BACKUP_MAGIC_HEADER) {
            throw IllegalArgumentException("Invalid backup file: header does not match CalcVault format.")
        }

        // Read Salt & IV
        val salt = ByteArray(SALT_LEN)
        val readSalt = inputStream.read(salt)
        val iv = ByteArray(IV_LEN)
        val readIv = inputStream.read(iv)

        if (readSalt != SALT_LEN || readIv != IV_LEN) {
            throw IllegalArgumentException("Corrupt backup file: truncated encryption parameters.")
        }

        onProgress(0.15f, "Verifying passphrase and decrypting container...")
        val secretKey = deriveKeyFromPassphrase(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_LEN_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val tempZipFile = File(context.cacheDir, "temp_restore_${System.currentTimeMillis()}.zip")
        tempZipFile.deleteOnExit()

        try {
            // Decrypt stream into temporary ZIP file
            FileOutputStream(tempZipFile).use { fos ->
                CipherInputStream(inputStream, cipher).use { cis ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (cis.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                    }
                    fos.flush()
                }
            }

            onProgress(0.4f, "Extracting backup manifest and media...")
            // Parse ZIP
            var manifestJson: JSONObject? = null
            val mediaEntries = mutableMapOf<String, ByteArray>()

            FileInputStream(tempZipFile).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val baos = ByteArrayOutputStream()
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (zis.read(buffer).also { read = it } != -1) {
                            baos.write(buffer, 0, read)
                        }
                        val bytes = baos.toByteArray()

                        if (name == "manifest.json") {
                            manifestJson = JSONObject(String(bytes, Charsets.UTF_8))
                        } else if (name.startsWith("media/")) {
                            mediaEntries[name] = bytes
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            if (manifestJson == null) {
                throw IllegalStateException("Corrupt backup package: manifest missing.")
            }

            // Restore albums
            val mediaDao = database.vaultDao()
            val albumsArray = manifestJson!!.optJSONArray("albums")
            var restoredAlbumsCount = 0
            val albumIdMap = mutableMapOf<Long, Long>() // oldId -> newId

            if (albumsArray != null) {
                for (i in 0 until albumsArray.length()) {
                    val albumObj = albumsArray.getJSONObject(i)
                    val oldId = albumObj.getLong("id")
                    val name = albumObj.getString("name")
                    val isSystem = albumObj.optBoolean("isSystemDefault", false)

                    val existingAlbum = mediaDao.getAlbumByName(name)
                    if (existingAlbum != null) {
                        albumIdMap[oldId] = existingAlbum.id
                    } else {
                        val newAlbumId = mediaDao.insertAlbum(
                            VaultAlbum(
                                name = name,
                                isSystemDefault = isSystem,
                                dateCreated = System.currentTimeMillis()
                            )
                        )
                        albumIdMap[oldId] = newAlbumId
                        restoredAlbumsCount++
                    }
                }
            }

            // Restore media
            val mediaArray = manifestJson!!.optJSONArray("media")
            var restoredCount = 0
            var failedCount = 0

            if (mediaArray != null) {
                val totalMedia = mediaArray.length()
                for (i in 0 until totalMedia) {
                    val itemObj = mediaArray.getJSONObject(i)
                    val originalName = itemObj.getString("originalName")
                    val zipEntryName = itemObj.getString("zipEntryName")
                    val oldAlbumId = itemObj.getLong("albumId")
                    val targetAlbumId = albumIdMap[oldAlbumId] ?: 1L // Default album

                    val rawDecryptedBytes = mediaEntries[zipEntryName]
                    if (rawDecryptedBytes != null) {
                        try {
                            onProgress(
                                0.5f + (i.toFloat() / totalMedia) * 0.45f,
                                "Re-encrypting item ${i + 1} of $totalMedia: $originalName"
                            )

                            // Encrypt with current device Android Keystore
                            val (newEncryptedFileName, encryptedSize) = CryptoEngine.encryptStreamToVault(
                                context = context,
                                inputStream = ByteArrayInputStream(rawDecryptedBytes),
                                totalBytes = rawDecryptedBytes.size.toLong(),
                                onProgress = {}
                            )

                            val mediaItem = VaultMediaItem(
                                encryptedFileName = newEncryptedFileName,
                                originalName = originalName,
                                mimeType = itemObj.getString("mimeType"),
                                mediaType = itemObj.getString("mediaType"),
                                sizeBytes = encryptedSize,
                                durationMs = itemObj.optLong("durationMs", 0L),
                                width = itemObj.optInt("width", 0),
                                height = itemObj.optInt("height", 0),
                                albumId = targetAlbumId,
                                dateImported = itemObj.optLong("dateImported", System.currentTimeMillis()),
                                isFavorite = itemObj.optBoolean("isFavorite", false)
                            )
                            mediaDao.insertMedia(mediaItem)
                            restoredCount++
                        } catch (e: Exception) {
                            failedCount++
                        }
                    } else {
                        failedCount++
                    }
                }
            }

            onProgress(1.0f, "Restoration complete!")
            RestoreSummary(restoredCount, failedCount, restoredAlbumsCount)
        } finally {
            tempZipFile.delete()
        }
    }
}
