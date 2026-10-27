package com.ironquest.app.ui.imports

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.ironquest.app.R
import com.ironquest.app.domain.importing.Csv
import com.ironquest.app.domain.importing.ImportProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Text of a picked file, or the reason it couldn't be read. Never throws (except cancellation). */
sealed interface CsvSource {
    data class Loaded(val text: String, val fileName: String?) : CsvSource
    data class Failed(val problem: ImportProblem) : CsvSource
}

suspend fun readCsvFile(context: Context, uri: Uri): CsvSource = withContext(Dispatchers.IO) {
    try {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        val limit = Csv.MAX_CHARS + 1
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(limit)
            var read = 0
            while (read < limit) {
                val count = input.read(buffer, read, limit - read)
                if (count < 0) break
                read += count
            }
            buffer.copyOf(read)
        } ?: return@withContext CsvSource.Failed(ImportProblem.CantOpenFile)
        when {
            bytes.size >= limit -> CsvSource.Failed(ImportProblem.FileTooLarge(Csv.MAX_CHARS / 1024))
            bytes.any { it == 0.toByte() } || name?.endsWith(".xlsx", ignoreCase = true) == true -> // not UI text: file extension
                CsvSource.Failed(ImportProblem.NotTextFile)
            else -> CsvSource.Loaded(String(bytes, Charsets.UTF_8), name)
        }
    } catch (denied: SecurityException) {
        CsvSource.Failed(ImportProblem.NoPermission)
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        CsvSource.Failed(ImportProblem.CouldntRead(error.javaClass.simpleName))
    }
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Clipboard text, or null when it's empty or not text. */
fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    val item = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return null
    return runCatching { item.coerceToText(context)?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
}

/**
 * Opens the system share sheet with [text] (a plan as CSV). The receiver pastes it into
 * Import on their phone. Returns false when no app can take it.
 */
fun shareText(context: Context, subject: String, text: String): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.common_share_title, subject)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(chooser)
        true
    } catch (notFound: ActivityNotFoundException) {
        false
    }
}
