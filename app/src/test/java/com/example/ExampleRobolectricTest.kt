package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.VaultAlbum
import com.example.data.VaultDatabase
import com.example.data.VaultMediaItem
import com.example.security.AuthManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var authManager: AuthManager
    private lateinit var db: VaultDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        authManager = AuthManager(context)
        db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Test
    fun testAppNameResourceMatchesCalcVault() {
        val appName = context.getString(R.string.app_name)
        assertEquals("CalcVault", appName)
    }

    @Test
    fun testPinSetupAndVerification() {
        val testPin = "7492"
        assertTrue(authManager.setupMasterPin(testPin))
        assertTrue(authManager.isPinConfigured())
        assertTrue(authManager.verifyPin("7492"))
        assertFalse(authManager.verifyPin("1234"))
    }

    @Test
    fun testPinRateLimitingAndLockout() {
        val testPin = "5821"
        authManager.setupMasterPin(testPin)

        // Enter 5 incorrect attempts to trigger 30s lockout
        for (i in 1..4) {
            assertFalse(authManager.verifyPin("0000"))
            assertFalse(authManager.isLockedOut())
        }

        // 5th failed attempt triggers rate-limiting lockout
        assertFalse(authManager.verifyPin("0000"))
        assertTrue(authManager.isLockedOut())
        assertTrue(authManager.getLockoutRemainingSeconds() > 0)
    }

    @Test
    fun testDatabaseMediaAndAlbumPersistence() = runBlocking {
        val dao = db.vaultDao()
        val albumId = dao.insertAlbum(VaultAlbum(name = "Secret Project", isSystemDefault = false))
        assertTrue(albumId > 0)

        val mediaItem = VaultMediaItem(
            encryptedFileName = "test_encrypted_file.enc",
            originalName = "financial_document.png",
            mimeType = "image/png",
            mediaType = "PHOTO",
            sizeBytes = 1024L,
            albumId = albumId
        )
        val mediaId = dao.insertMedia(mediaItem)
        assertTrue(mediaId > 0)

        val fetched = dao.getMediaById(mediaId)
        assertEquals("financial_document.png", fetched?.originalName)
        assertEquals("PHOTO", fetched?.mediaType)
    }
}
