package com.example.worktrack.backup

import android.content.Context
import android.net.Uri
import com.example.worktrack.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class BackupState(
    val busy: Boolean = false,
    val preview: BackupSummary? = null,
    val message: Int? = null,
    val result: BackupSummary? = null,
    val lastExportAt: Long = 0
)

class BackupController(
    private val context: Context,
    private val service: BackupService,
    private val scope: CoroutineScope,
    private val canStart: () -> Boolean,
    private val onRestored: () -> Unit
) {
    private val preferences = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(BackupState(lastExportAt = preferences.getLong("lastExportAt", 0)))
    val state = mutableState.asStateFlow()
    private var prepared: PreparedBackup? = null

    private fun begin(): Boolean {
        if (state.value.busy || state.value.preview != null) return false
        if (!canStart()) {
            mutableState.value = state.value.copy(message = R.string.backup_busy, result = null)
            return false
        }
        mutableState.value = state.value.copy(busy = true, message = null, result = null)
        return true
    }

    fun export(uri: Uri) {
        if (!begin()) return
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    (context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException()).use { service.export(it) }
                }
                val now = System.currentTimeMillis()
                preferences.edit().putLong("lastExportAt", now).apply()
                mutableState.value = state.value.copy(message = R.string.backup_export_success, result = result, lastExportAt = now)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { failure(e)
            } finally { mutableState.value = state.value.copy(busy = false) }
        }
    }

    fun inspect(uri: Uri) {
        if (!begin()) return
        scope.launch {
            try {
                prepared = withContext(Dispatchers.IO) {
                    (context.contentResolver.openInputStream(uri) ?: throw IOException()).use { service.prepare(it) }
                }
                mutableState.value = state.value.copy(preview = prepared!!.summary)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { failure(e)
            } finally { mutableState.value = state.value.copy(busy = false) }
        }
    }

    fun cancelRestore() {
        if (state.value.busy) return
        val abandoned = prepared
        prepared = null
        mutableState.value = state.value.copy(preview = null)
        scope.launch(Dispatchers.IO) { abandoned?.close() }
    }

    fun confirmRestore() {
        val backup = prepared ?: return
        if (state.value.busy || !canStart()) return
        mutableState.value = state.value.copy(busy = true, preview = null)
        scope.launch {
            try {
                service.restore(backup)
                onRestored()
                mutableState.value = state.value.copy(message = R.string.backup_restore_success, result = backup.summary)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { failure(e)
            } finally {
                prepared = null
                mutableState.value = state.value.copy(busy = false)
            }
        }
    }

    fun clearMessage() { mutableState.value = state.value.copy(message = null, result = null) }

    private fun failure(e: Exception) {
        mutableState.value = state.value.copy(message = when (e) {
            is BackupLimitException -> R.string.backup_too_large
            is InvalidBackupException -> R.string.backup_invalid
            else -> R.string.backup_failed
        }, result = null)
    }
}
