package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VaultAlbum
import com.example.data.VaultDatabase
import com.example.data.VaultMediaItem
import com.example.data.VaultRepository
import com.example.security.AuthManager
import com.example.security.BackupEngine
import com.example.security.CryptoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.DecimalFormat

enum class VaultScreenState {
    PIN_SETUP,
    CALCULATOR,
    VAULT_DASHBOARD,
    RATE_LIMITED
}

enum class VaultTab {
    PHOTOS,
    VIDEOS,
    ALBUMS,
    BACKUP,
    SETTINGS
}

enum class MediaSortOrder {
    DATE_DESC,
    DATE_ASC,
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    SIZE_ASC
}

data class CalculatorUiState(
    val expression: String = "",
    val resultPreview: String = "",
    val history: List<Pair<String, String>> = emptyList()
)

data class ImportProgressState(
    val isImporting: Boolean = false,
    val progress: Float = 0f,
    val currentItemIndex: Int = 0,
    val totalItems: Int = 0,
    val currentFileName: String = ""
)

data class BackupState(
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

class CalcVaultViewModel(application: Application) : AndroidViewModel(application) {
    private val database = VaultDatabase.getInstance(application)
    val repository = VaultRepository(application, database)
    val authManager = AuthManager(application)

    private val _screenState = MutableStateFlow(
        if (!authManager.isPinConfigured()) VaultScreenState.PIN_SETUP
        else VaultScreenState.CALCULATOR
    )
    val screenState: StateFlow<VaultScreenState> = _screenState.asStateFlow()

    private val _calculatorState = MutableStateFlow(CalculatorUiState())
    val calculatorState: StateFlow<CalculatorUiState> = _calculatorState.asStateFlow()

    private val _currentTab = MutableStateFlow(VaultTab.PHOTOS)
    val currentTab: StateFlow<VaultTab> = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(MediaSortOrder.DATE_DESC)
    val sortOrder: StateFlow<MediaSortOrder> = _sortOrder.asStateFlow()

    private val _selectedAlbumId = MutableStateFlow<Long?>(null) // null = all
    val selectedAlbumId: StateFlow<Long?> = _selectedAlbumId.asStateFlow()

    private val _selectedMediaIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMediaIds: StateFlow<Set<Long>> = _selectedMediaIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _importProgress = MutableStateFlow(ImportProgressState())
    val importProgress: StateFlow<ImportProgressState> = _importProgress.asStateFlow()

    private val _backupState = MutableStateFlow(BackupState())
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _lockoutCountdown = MutableStateFlow(0)
    val lockoutCountdown: StateFlow<Int> = _lockoutCountdown.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Active viewer item
    private val _activeViewerItem = MutableStateFlow<VaultMediaItem?>(null)
    val activeViewerItem: StateFlow<VaultMediaItem?> = _activeViewerItem.asStateFlow()

    // Temporary video playback file
    private val _tempPlaybackFile = MutableStateFlow<File?>(null)
    val tempPlaybackFile: StateFlow<File?> = _tempPlaybackFile.asStateFlow()

    // Combined filtered media list
    val mediaList: StateFlow<List<VaultMediaItem>> = combine(
        repository.allMedia,
        _searchQuery,
        _sortOrder,
        _selectedAlbumId,
        _currentTab
    ) { all, query, sort, albumId, tab ->
        var list = all

        // Filter by tab
        if (tab == VaultTab.PHOTOS) {
            list = list.filter { it.mediaType == "PHOTO" }
        } else if (tab == VaultTab.VIDEOS) {
            list = list.filter { it.mediaType == "VIDEO" }
        }

        // Filter by album if selected
        if (albumId != null) {
            list = list.filter { it.albumId == albumId }
        }

        // Filter by search query
        if (query.isNotBlank()) {
            list = list.filter { it.originalName.contains(query, ignoreCase = true) }
        }

        // Sort
        when (sort) {
            MediaSortOrder.DATE_DESC -> list.sortedByDescending { it.dateImported }
            MediaSortOrder.DATE_ASC -> list.sortedBy { it.dateImported }
            MediaSortOrder.NAME_ASC -> list.sortedBy { it.originalName.lowercase() }
            MediaSortOrder.NAME_DESC -> list.sortedByDescending { it.originalName.lowercase() }
            MediaSortOrder.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
            MediaSortOrder.SIZE_ASC -> list.sortedBy { it.sizeBytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<VaultAlbum>> = repository.albums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val securityLogs = repository.securityLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var lockoutTickerJob: Job? = null

    init {
        checkLockoutStatus()
    }

    private fun checkLockoutStatus() {
        if (authManager.isLockedOut()) {
            _screenState.value = VaultScreenState.RATE_LIMITED
            startLockoutTicker()
        }
    }

    private fun startLockoutTicker() {
        lockoutTickerJob?.cancel()
        lockoutTickerJob = viewModelScope.launch {
            while (authManager.isLockedOut()) {
                _lockoutCountdown.value = authManager.getLockoutRemainingSeconds()
                delay(1000)
            }
            _lockoutCountdown.value = 0
            _screenState.value = VaultScreenState.CALCULATOR
        }
    }

    // Calculator Actions
    fun onCalculatorDigit(digit: String) {
        val current = _calculatorState.value.expression
        val newExpr = if (current == "0" && digit != ".") digit else current + digit
        _calculatorState.value = _calculatorState.value.copy(
            expression = newExpr,
            resultPreview = evaluateExpression(newExpr)
        )
    }

    fun onCalculatorOperator(op: String) {
        val current = _calculatorState.value.expression
        if (current.isEmpty()) return
        val lastChar = current.last()
        val newExpr = if (lastChar in "+-×÷%") {
            current.dropLast(1) + op
        } else {
            current + op
        }
        _calculatorState.value = _calculatorState.value.copy(
            expression = newExpr,
            resultPreview = evaluateExpression(newExpr)
        )
    }

    fun onCalculatorClear() {
        _calculatorState.value = _calculatorState.value.copy(
            expression = "",
            resultPreview = ""
        )
    }

    fun onCalculatorBackspace() {
        val current = _calculatorState.value.expression
        if (current.isNotEmpty()) {
            val newExpr = current.dropLast(1)
            _calculatorState.value = _calculatorState.value.copy(
                expression = newExpr,
                resultPreview = if (newExpr.isNotEmpty()) evaluateExpression(newExpr) else ""
            )
        }
    }

    fun onCalculatorEquals() {
        val entered = _calculatorState.value.expression

        // Check if entered input is the vault master PIN
        if (authManager.verifyPin(entered)) {
            unlockVault("PIN")
            return
        }

        // If rate-limited after failed attempt
        if (authManager.isLockedOut()) {
            _screenState.value = VaultScreenState.RATE_LIMITED
            startLockoutTicker()
            return
        }

        // Normal Calculator Behavior: Evaluate expression and save to calculation history
        val result = evaluateExpression(entered)
        if (result.isNotEmpty() && result != "Error") {
            val newHistory = listOf(entered to result) + _calculatorState.value.history
            _calculatorState.value = _calculatorState.value.copy(
                expression = result,
                resultPreview = "",
                history = newHistory.take(20)
            )
        } else if (entered.isNotBlank()) {
            val remaining = authManager.getAttemptsRemainingBeforeLockout()
            if (authManager.getFailedAttempts() > 0) {
                _statusMessage.value = "Incorrect PIN ($remaining attempts remaining before temporary lockout)"
            }
        }
    }

    private fun evaluateExpression(expr: String): String {
        try {
            if (expr.isEmpty()) return ""
            val clean = expr.replace("×", "*").replace("÷", "/")
            val result = simpleMathEval(clean)
            val df = DecimalFormat("#.########")
            return df.format(result)
        } catch (_: Exception) {
            return ""
        }
    }

    private fun simpleMathEval(str: String): Double {
        // Safe, basic recursive-descent parser for arithmetic without external dependencies
        var pos = -1
        var ch = 0

        fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parseExpression(): Double {
            var x = 0.0
            nextChar()
            fun parseFactor(): Double {
                if (eat('+'.code)) return +parseFactor()
                if (eat('-'.code)) return -parseFactor()
                var factor: Double
                val startPos = pos
                if (eat('('.code)) {
                    factor = parseExpression()
                    eat(')'.code)
                } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                    while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                    factor = str.substring(startPos, pos).toDouble()
                } else {
                    return 0.0
                }
                if (eat('%'.code)) factor /= 100.0
                return factor
            }

            fun parseTerm(): Double {
                var term = parseFactor()
                while (true) {
                    when {
                        eat('*'.code) -> term *= parseFactor()
                        eat('/'.code) -> {
                            val divisor = parseFactor()
                            term = if (divisor != 0.0) term / divisor else 0.0
                        }
                        else -> return term
                    }
                }
            }

            x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        return try {
            parseExpression()
        } catch (_: Exception) {
            0.0
        }
    }

    // Biometric Trigger
    fun triggerBiometricUnlock(activity: FragmentActivity) {
        if (!authManager.isBiometricEnabled()) {
            _statusMessage.value = "Biometric unlock is disabled in Settings."
            return
        }

        authManager.authenticateWithBiometrics(
            activity = activity,
            onSuccess = {
                unlockVault("BIOMETRIC")
            },
            onError = { err ->
                _statusMessage.value = err
            }
        )
    }

    private fun unlockVault(method: String) {
        viewModelScope.launch {
            repository.logEvent(
                eventType = if (method == "PIN") "PIN_SUCCESS" else "BIOMETRIC_SUCCESS",
                details = "Vault unlocked via $method."
            )
        }
        _calculatorState.value = _calculatorState.value.copy(expression = "", resultPreview = "")
        _screenState.value = VaultScreenState.VAULT_DASHBOARD
    }

    fun lockVault() {
        cleanupPlaybackFile()
        _activeViewerItem.value = null
        _isSelectionMode.value = false
        _selectedMediaIds.value = emptySet()
        _screenState.value = VaultScreenState.CALCULATOR
    }

    // Initial PIN Setup
    fun setupInitialPin(pin: String): Boolean {
        if (pin.length < 4) {
            _statusMessage.value = "PIN must be at least 4 digits"
            return false
        }
        val success = authManager.setupMasterPin(pin)
        if (success) {
            viewModelScope.launch {
                repository.logEvent("SETUP_PIN", "Master PIN established.")
            }
            _screenState.value = VaultScreenState.CALCULATOR
            _statusMessage.value = "PIN established successfully. Enter your PIN and press = to unlock."
        }
        return success
    }

    // PIN Change
    fun changeMasterPin(currentPin: String, newPin: String): Boolean {
        if (newPin.length < 4) {
            _statusMessage.value = "New PIN must be at least 4 digits"
            return false
        }
        val success = authManager.changePin(currentPin, newPin)
        if (success) {
            viewModelScope.launch {
                repository.logEvent("PIN_CHANGED", "Master PIN changed successfully.")
            }
            _statusMessage.value = "PIN changed successfully"
        } else {
            _statusMessage.value = "Current PIN is incorrect"
        }
        return success
    }

    // Navigation & Tabs
    fun setTab(tab: VaultTab) {
        _currentTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: MediaSortOrder) {
        _sortOrder.value = order
    }

    fun selectAlbum(albumId: Long?) {
        _selectedAlbumId.value = albumId
    }

    // Multi-Select
    fun toggleSelectionMode() {
        val current = _isSelectionMode.value
        _isSelectionMode.value = !current
        if (current) {
            _selectedMediaIds.value = emptySet()
        }
    }

    fun toggleItemSelection(id: Long) {
        val current = _selectedMediaIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedMediaIds.value = current
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        } else {
            _isSelectionMode.value = true
        }
    }

    fun selectAll(ids: List<Long>) {
        _selectedMediaIds.value = ids.toSet()
        _isSelectionMode.value = true
    }

    fun deselectAll() {
        _selectedMediaIds.value = emptySet()
        _isSelectionMode.value = false
    }

    // Batch Actions
    fun deleteSelectedMedia() {
        val ids = _selectedMediaIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val all = mediaList.value
            val toDelete = all.filter { ids.contains(it.id) }
            repository.deleteMediaItems(toDelete)
            deselectAll()
            _statusMessage.value = "${toDelete.size} items shredded and permanently deleted."
        }
    }

    fun moveSelectedMediaToAlbum(targetAlbumId: Long) {
        val ids = _selectedMediaIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.moveMediaToAlbum(ids, targetAlbumId)
            deselectAll()
            _statusMessage.value = "Moved ${ids.size} items to album."
        }
    }

    fun deleteSingleItem(item: VaultMediaItem) {
        viewModelScope.launch {
            repository.deleteMediaItems(listOf(item))
            if (_activeViewerItem.value?.id == item.id) {
                _activeViewerItem.value = null
            }
            _statusMessage.value = "${item.originalName} permanently deleted."
        }
    }

    // Import Media
    fun importMediaUris(uris: List<Uri>, albumId: Long = _selectedAlbumId.value ?: 1L) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _importProgress.value = ImportProgressState(
                isImporting = true,
                progress = 0f,
                currentItemIndex = 0,
                totalItems = uris.size,
                currentFileName = "Starting encryption..."
            )

            var importedCount = 0
            for ((index, uri) in uris.withIndex()) {
                _importProgress.value = _importProgress.value.copy(
                    currentItemIndex = index + 1,
                    currentFileName = "Encrypting item ${index + 1} of ${uris.size}..."
                )
                val item = repository.importMedia(
                    uri = uri,
                    albumId = albumId,
                    onProgress = { fileProgress ->
                        val overall = (index.toFloat() + fileProgress) / uris.size
                        _importProgress.value = _importProgress.value.copy(progress = overall)
                    }
                )
                if (item != null) importedCount++
            }

            _importProgress.value = ImportProgressState(isImporting = false)
            _statusMessage.value = "Successfully encrypted and imported $importedCount items."
        }
    }

    // Export Media
    fun exportSingleItem(item: VaultMediaItem, outputStream: OutputStream) {
        viewModelScope.launch {
            try {
                repository.exportMediaItem(item, outputStream)
                _statusMessage.value = "Exported ${item.originalName} successfully."
            } catch (e: Exception) {
                _statusMessage.value = "Export failed: ${e.localizedMessage}"
            }
        }
    }

    // Media Viewer
    fun openMediaViewer(item: VaultMediaItem) {
        _activeViewerItem.value = item
        if (item.mediaType == "VIDEO") {
            viewModelScope.launch {
                try {
                    val file = CryptoEngine.decryptToTemporaryPlaybackFile(
                        getApplication(),
                        item.encryptedFileName
                    )
                    _tempPlaybackFile.value = file
                } catch (e: Exception) {
                    _statusMessage.value = "Failed to load video: ${e.localizedMessage}"
                }
            }
        }
    }

    fun closeMediaViewer() {
        _activeViewerItem.value = null
        cleanupPlaybackFile()
    }

    private fun cleanupPlaybackFile() {
        val file = _tempPlaybackFile.value
        if (file != null && file.exists()) {
            file.delete()
        }
        _tempPlaybackFile.value = null
    }

    // Album Creation / Renaming / Deleting
    fun createAlbum(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createAlbum(name.trim())
            _statusMessage.value = "Album '$name' created."
        }
    }

    fun deleteAlbum(album: VaultAlbum) {
        if (album.isSystemDefault) {
            _statusMessage.value = "System default albums cannot be deleted."
            return
        }
        viewModelScope.launch {
            repository.deleteAlbum(album.id)
            if (_selectedAlbumId.value == album.id) {
                _selectedAlbumId.value = null
            }
            _statusMessage.value = "Album '${album.name}' removed."
        }
    }

    // Encrypted Backup & Restore
    fun createEncryptedBackup(passphrase: String, outputStream: OutputStream) {
        if (passphrase.length < 6) {
            _statusMessage.value = "Backup passphrase must be at least 6 characters"
            return
        }
        viewModelScope.launch {
            _backupState.value = BackupState(isRunning = true, progress = 0f, statusText = "Initializing...")
            try {
                val count = BackupEngine.createEncryptedBackup(
                    context = getApplication(),
                    database = database,
                    passphrase = passphrase,
                    outputStream = outputStream,
                    onProgress = { progress, status ->
                        _backupState.value = BackupState(isRunning = true, progress = progress, statusText = status)
                    }
                )
                _backupState.value = BackupState(
                    isRunning = false,
                    isSuccess = true,
                    statusText = "Encrypted backup created with $count items!"
                )
                repository.logEvent("BACKUP_CREATED", "Encrypted backup generated with $count items.")
                _statusMessage.value = "Backup exported successfully."
            } catch (e: Exception) {
                _backupState.value = BackupState(
                    isRunning = false,
                    isSuccess = false,
                    errorMessage = e.localizedMessage ?: "Backup failed"
                )
            }
        }
    }

    fun restoreEncryptedBackup(passphrase: String, inputStream: InputStream) {
        viewModelScope.launch {
            _backupState.value = BackupState(isRunning = true, progress = 0f, statusText = "Verifying...")
            try {
                val result = BackupEngine.restoreEncryptedBackup(
                    context = getApplication(),
                    database = database,
                    passphrase = passphrase,
                    inputStream = inputStream,
                    onProgress = { progress, status ->
                        _backupState.value = BackupState(isRunning = true, progress = progress, statusText = status)
                    }
                )
                _backupState.value = BackupState(
                    isRunning = false,
                    isSuccess = true,
                    statusText = "Restored ${result.restoredCount} media files and ${result.restoredAlbums} albums!"
                )
                repository.logEvent(
                    "BACKUP_RESTORED",
                    "Restored ${result.restoredCount} media files and ${result.restoredAlbums} albums."
                )
                _statusMessage.value = "Restored ${result.restoredCount} items from encrypted backup!"
            } catch (e: Exception) {
                _backupState.value = BackupState(
                    isRunning = false,
                    isSuccess = false,
                    errorMessage = e.localizedMessage ?: "Passphrase invalid or corrupt backup file."
                )
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearBackupState() {
        _backupState.value = BackupState()
    }

    override fun onCleared() {
        super.onCleared()
        cleanupPlaybackFile()
    }
}
