package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AuthManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("calcvault_auth_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTO_LOCK_INTERVAL_SEC = "auto_lock_interval_sec"
        private const val KEY_STEALTH_FLAG_SECURE = "stealth_flag_secure"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts_count"
        private const val KEY_LOCKOUT_UNTIL_TIMESTAMP = "lockout_until_timestamp"
        private const val KEY_BACKGROUND_TIMESTAMP = "background_timestamp"

        private const val PBKDF2_ITERATIONS = 100_000
        private const val HASH_KEY_LENGTH = 256
        private const val SALT_LENGTH_BYTES = 32

        const val AUTO_LOCK_IMMEDIATE = 0
        const val AUTO_LOCK_30_SECONDS = 30
        const val AUTO_LOCK_1_MINUTE = 60
        const val AUTO_LOCK_5_MINUTES = 300
    }

    private val secureRandom = SecureRandom()

    fun isPinConfigured(): Boolean {
        return prefs.contains(KEY_PIN_HASH) && prefs.contains(KEY_PIN_SALT)
    }

    fun setupMasterPin(newPin: String): Boolean {
        if (newPin.length < 4) return false
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        val hash = hashPin(newPin, salt)

        prefs.edit()
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
            .apply()
        return true
    }

    fun verifyPin(enteredPin: String): Boolean {
        if (isLockedOut()) return false

        val storedHashBase64 = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val storedSaltBase64 = prefs.getString(KEY_PIN_SALT, null) ?: return false

        val storedHash = Base64.decode(storedHashBase64, Base64.NO_WRAP)
        val storedSalt = Base64.decode(storedSaltBase64, Base64.NO_WRAP)

        val enteredHash = hashPin(enteredPin, storedSalt)

        // Constant-time comparison to prevent side-channel timing attacks
        val isValid = MessageDigest.isEqual(storedHash, enteredHash)

        if (isValid) {
            // Reset failure counters upon successful verification
            resetFailedAttempts()
            return true
        } else {
            recordFailedAttempt()
            return false
        }
    }

    fun changePin(currentPin: String, newPin: String): Boolean {
        if (!verifyPin(currentPin)) return false
        return setupMasterPin(newPin)
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    // Rate Limiting & Lockout
    fun getFailedAttempts(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)

    fun isLockedOut(): Boolean {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
        return System.currentTimeMillis() < lockoutUntil
    }

    fun getLockoutRemainingSeconds(): Int {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
        val remainingMs = lockoutUntil - System.currentTimeMillis()
        return if (remainingMs > 0) ((remainingMs + 999) / 1000).toInt() else 0
    }

    fun getAttemptsRemainingBeforeLockout(): Int {
        val attempts = getFailedAttempts()
        return when {
            attempts < 5 -> 5 - attempts
            attempts < 10 -> 10 - attempts
            else -> 1
        }
    }

    private fun recordFailedAttempt() {
        val attempts = getFailedAttempts() + 1
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts)

        if (attempts >= 10) {
            // Lock out for 60 seconds
            val lockoutUntil = System.currentTimeMillis() + 60_000L
            editor.putLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, lockoutUntil)
        } else if (attempts >= 5) {
            // Lock out for 30 seconds
            val lockoutUntil = System.currentTimeMillis() + 30_000L
            editor.putLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, lockoutUntil)
        }
        editor.apply()
    }

    fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
            .apply()
    }

    // Biometrics
    fun isBiometricHardwareAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false) && isBiometricHardwareAvailable()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun authenticateWithBiometrics(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isBiometricHardwareAvailable()) {
            onError("Biometric authentication is not supported or not enrolled on this device.")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    resetFailedAttempts()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Fingerprint unrecognized
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock CalcVault")
            .setSubtitle("Confirm your biometric identity to access private media")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        prompt.authenticate(promptInfo)
    }

    // Auto-Lock Background Tracking
    fun getAutoLockInterval(): Int {
        return prefs.getInt(KEY_AUTO_LOCK_INTERVAL_SEC, AUTO_LOCK_IMMEDIATE)
    }

    fun setAutoLockInterval(seconds: Int) {
        prefs.edit().putInt(KEY_AUTO_LOCK_INTERVAL_SEC, seconds).apply()
    }

    fun onAppMovedToBackground() {
        prefs.edit().putLong(KEY_BACKGROUND_TIMESTAMP, SystemClock.elapsedRealtime()).apply()
    }

    fun shouldAutoLock(): Boolean {
        val backgroundTimestamp = prefs.getLong(KEY_BACKGROUND_TIMESTAMP, 0L)
        if (backgroundTimestamp == 0L) return false

        val intervalSec = getAutoLockInterval()
        val elapsedSec = (SystemClock.elapsedRealtime() - backgroundTimestamp) / 1000L
        return elapsedSec >= intervalSec
    }

    fun clearBackgroundTimestamp() {
        prefs.edit().remove(KEY_BACKGROUND_TIMESTAMP).apply()
    }

    // Stealth / FLAG_SECURE
    fun isFlagSecureEnabled(): Boolean {
        return prefs.getBoolean(KEY_STEALTH_FLAG_SECURE, true)
    }

    fun setFlagSecureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STEALTH_FLAG_SECURE, enabled).apply()
    }
}
