package com.example.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptoEngine {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "calcvault_master_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val CHUNK_SIZE = 64 * 1024 // 64 KB streaming chunks

    private val secureRandom = SecureRandom()

    @Synchronized
    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry?.secretKey != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(false) // We supply high-entropy random IV explicitly
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun getVaultDir(context: Context): File {
        val dir = File(context.filesDir, "vault_media")
        if (!dir.exists()) {
            dir.mkdirs()
            // Add .nomedia file so public gallery apps and media scanners never index this directory
            File(dir, ".nomedia").createNewFile()
        }
        return dir
    }

    data class MediaInspectionResult(
        val mediaType: String, // "PHOTO" or "VIDEO"
        val mimeType: String,
        val width: Int,
        val height: Int,
        val durationMs: Long
    )

    suspend fun inspectMedia(context: Context, uri: Uri): MediaInspectionResult = withContext(Dispatchers.IO) {
        var mediaType = "PHOTO"
        var mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        var width = 0
        var height = 0
        var durationMs = 0L

        // Check if video by MIME or extension
        if (mimeType.startsWith("video/") || uri.toString().endsWith(".mp4", ignoreCase = true) ||
            uri.toString().endsWith(".mov", ignoreCase = true) || uri.toString().endsWith(".mkv", ignoreCase = true)
        ) {
            mediaType = "VIDEO"
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                width = widthStr?.toIntOrNull() ?: 0
                height = heightStr?.toIntOrNull() ?: 0
                durationMs = durStr?.toLongOrNull() ?: 0L
                retriever.release()
            } catch (_: Exception) { }
        } else {
            // It's a photo, extract dimensions
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    width = options.outWidth
                    height = options.outHeight
                    if (options.outMimeType != null) {
                        mimeType = options.outMimeType
                    }
                }
            } catch (_: Exception) { }
        }

        MediaInspectionResult(mediaType, mimeType, width, height, durationMs)
    }

    /**
     * Encrypts input stream directly into vault media file with progress.
     * Prepends 12-byte IV to the output file.
     */
    suspend fun encryptStreamToVault(
        context: Context,
        inputStream: InputStream,
        totalBytes: Long,
        onProgress: (Float) -> Unit
    ): Pair<String, Long> = withContext(Dispatchers.IO) {
        val vaultDir = getVaultDir(context)
        val encryptedFileName = "${UUID.randomUUID()}.enc"
        val outputFile = File(vaultDir, encryptedFileName)

        val masterKey = getOrCreateMasterKey()
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, spec)

        var bytesWritten = 0L
        FileOutputStream(outputFile).use { fos ->
            // Prepend 12-byte IV
            fos.write(iv)

            CipherOutputStream(fos, cipher).use { cos ->
                val buffer = ByteArray(CHUNK_SIZE)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    cos.write(buffer, 0, bytesRead)
                    bytesWritten += bytesRead
                    if (totalBytes > 0) {
                        onProgress((bytesWritten.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                }
                cos.flush()
            }
        }

        Pair(encryptedFileName, outputFile.length())
    }

    /**
     * Decrypts an encrypted vault file into memory.
     */
    suspend fun decryptVaultFileToBytes(context: Context, encryptedFileName: String): ByteArray = withContext(Dispatchers.IO) {
        val file = File(getVaultDir(context), encryptedFileName)
        if (!file.exists()) throw IllegalArgumentException("Encrypted file not found")

        val masterKey = getOrCreateMasterKey()
        FileInputStream(file).use { fis ->
            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            val readIv = fis.read(iv)
            if (readIv != GCM_IV_LENGTH_BYTES) {
                throw IllegalStateException("Corrupt encryption header: IV truncated")
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)

            val outputStream = ByteArrayOutputStream()
            CipherInputStream(fis, cipher).use { cis ->
                val buffer = ByteArray(CHUNK_SIZE)
                var bytesRead: Int
                while (cis.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
            outputStream.toByteArray()
        }
    }

    /**
     * Decodes a decrypted photo into a scaled Bitmap for smooth display or thumbnail rendering.
     */
    suspend fun loadDecryptedBitmap(
        context: Context,
        encryptedFileName: String,
        targetWidth: Int = 1080,
        targetHeight: Int = 1920
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val decryptedBytes = decryptVaultFileToBytes(context, encryptedFileName)

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, options)

            var sampleSize = 1
            if (options.outHeight > targetHeight || options.outWidth > targetWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / sampleSize) >= targetHeight && (halfWidth / sampleSize) >= targetWidth) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, decodeOptions)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decrypts a video to a secure temporary playback cache file in internal cacheDir.
     * Automatically flagged for immediate deletion upon playback finish.
     */
    suspend fun decryptToTemporaryPlaybackFile(context: Context, encryptedFileName: String): File = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "vault_playback_${System.currentTimeMillis()}.tmp")
        tempFile.deleteOnExit()

        val sourceFile = File(getVaultDir(context), encryptedFileName)
        val masterKey = getOrCreateMasterKey()

        FileInputStream(sourceFile).use { fis ->
            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            fis.read(iv)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)

            FileOutputStream(tempFile).use { fos ->
                CipherInputStream(fis, cipher).use { cis ->
                    val buffer = ByteArray(CHUNK_SIZE)
                    var bytesRead: Int
                    while (cis.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                    }
                    fos.flush()
                }
            }
        }
        tempFile
    }

    /**
     * Securely shreds and deletes a private file.
     * Note: Wear-leveling on modern NAND flash means flash controllers manage physical erase blocks;
     * overwriting helps prevent software-level recovery from file system unallocated blocks.
     */
    suspend fun securelyDeleteVaultFile(context: Context, encryptedFileName: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(getVaultDir(context), encryptedFileName)
        if (!file.exists()) return@withContext true

        try {
            val length = file.length()
            if (length > 0) {
                FileOutputStream(file).use { fos ->
                    val zeroBuffer = ByteArray((length.coerceAtMost(CHUNK_SIZE.toLong())).toInt())
                    Arrays.fill(zeroBuffer, 0.toByte())
                    var written = 0L
                    while (written < length) {
                        val chunk = (length - written).coerceAtMost(zeroBuffer.size.toLong()).toInt()
                        fos.write(zeroBuffer, 0, chunk)
                        written += chunk
                    }
                    fos.flush()
                }
            }
        } catch (_: Exception) { }
        file.delete()
    }

    /**
     * Decrypts an encrypted vault file and streams it directly to an external destination stream (Export).
     */
    suspend fun decryptVaultFileToStream(
        context: Context,
        encryptedFileName: String,
        outputStream: OutputStream
    ) = withContext(Dispatchers.IO) {
        val file = File(getVaultDir(context), encryptedFileName)
        val masterKey = getOrCreateMasterKey()

        FileInputStream(file).use { fis ->
            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            fis.read(iv)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)

            CipherInputStream(fis, cipher).use { cis ->
                val buffer = ByteArray(CHUNK_SIZE)
                var bytesRead: Int
                while (cis.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
            }
        }
    }
}
