package com.example.data.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Public application storage:
 * Download/دفتر الفواتير والحسابات/
 *   الصور/
 *   النسخ الاحتياطية/
 *   الملفات/
 */
object AppStorageHelper {
    const val APP_FOLDER = "دفتر الفواتير والحسابات"
    const val IMAGES_FOLDER = "الصور"
    const val BACKUPS_FOLDER = "النسخ الاحتياطية"
    const val FILES_FOLDER = "الملفات"

    fun saveBytesToDownloads(
        context: Context,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        subFolder: String
    ): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + APP_FOLDER + "/" + subFolder)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                try {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    uri
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    APP_FOLDER + File.separator + subFolder
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(bytes) }
                Uri.fromFile(file)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun copyUriToDownloads(
        context: Context,
        sourceUri: Uri,
        fileName: String,
        mimeType: String,
        subFolder: String = IMAGES_FOLDER
    ): Uri? {
        return try {
            val bytes = context.contentResolver.openInputStream(sourceUri)?.use(InputStream::readBytes) ?: return null
            saveBytesToDownloads(context, bytes, fileName, mimeType, subFolder)
        } catch (_: Exception) {
            null
        }
    }

    fun ensureFolders(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val root = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                APP_FOLDER
            )
            listOf(IMAGES_FOLDER, BACKUPS_FOLDER, FILES_FOLDER).forEach {
                File(root, it).mkdirs()
            }
        }
    }
}
