package com.ironquest.app.ui.you

import android.content.Context
import android.net.Uri
import com.ironquest.app.R
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Largest backup file read into memory (a few years of daily logs are well under 10 MB). */
private const val MAX_BACKUP_BYTES = 50 * 1024 * 1024

sealed interface BackupRead {
    data class Loaded(val text: String) : BackupRead
    data class Failed(val message: UiText) : BackupRead
}

/** Reads a picked file (device storage, Google Drive, …) as text. Never throws except cancellation. */
suspend fun readBackupFile(context: Context, uri: Uri): BackupRead = withContext(Dispatchers.IO) {
    try {
        // Manual loop: InputStream.readNBytes needs API 33, the app supports 26.
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (out.size() <= MAX_BACKUP_BYTES) {
                val count = input.read(buffer)
                if (count < 0) break
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } ?: return@withContext BackupRead.Failed(uiText(R.string.you_backup_open_failed))
        if (bytes.size > MAX_BACKUP_BYTES) {
            BackupRead.Failed(uiText(R.string.you_backup_too_large))
        } else {
            BackupRead.Loaded(String(bytes, Charsets.UTF_8))
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        BackupRead.Failed(uiText(R.string.you_backup_read_failed, error.javaClass.simpleName))
    }
}

/** Writes [text] to a file the user picked with the system "save as" dialog (device or Drive). */
suspend fun writeBackupFile(context: Context, uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        false
    }
}
