package com.example.worktrack.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream

interface BackupMedia {
    fun open(uri: Uri): InputStream?
    fun extension(uri: Uri): String
    fun uriFor(file: File): Uri
}

internal class AndroidBackupMedia(private val context: Context) : BackupMedia {
    override fun open(uri: Uri): InputStream? =
        if (uri.scheme == "content") context.contentResolver.openInputStream(uri) else null

    override fun extension(uri: Uri): String = when (runCatching { context.contentResolver.getType(uri) }.getOrNull()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        else -> "jpg"
    }

    override fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.backup.photos", file)
}
