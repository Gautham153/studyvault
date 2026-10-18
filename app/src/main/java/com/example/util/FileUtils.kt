package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

data class SelectedFileInfo(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val bytes: ByteArray
)

object FileUtils {
    const val MAX_FILE_SIZE_BYTES = 25L * 1024L * 1024L // 25 MB safe practical limit

    fun readFileFromUri(context: Context, uri: Uri): Result<SelectedFileInfo> {
        return try {
            val contentResolver = context.contentResolver
            var fileName = "Document"
            var fileSize = -1L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                        fileName = cursor.getString(nameIndex)
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            // Enforce 25 MB maximum file size limit
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                return Result.failure(IllegalArgumentException("File exceeds maximum allowed size of 25 MB."))
            }

            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

            val stream = contentResolver.openInputStream(uri)
                ?: return Result.failure(IllegalStateException("Could not open file input stream."))

            val bytes = stream.use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var totalRead = 0L
                var read: Int
                while (input.read(chunk).also { read = it } != -1) {
                    totalRead += read
                    if (totalRead > MAX_FILE_SIZE_BYTES) {
                        return Result.failure(IllegalArgumentException("File exceeds maximum allowed size of 25 MB."))
                    }
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            }

            if (bytes.isEmpty()) {
                return Result.failure(IllegalArgumentException("Selected file is empty."))
            }

            Result.success(
                SelectedFileInfo(
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = bytes.size.toLong(),
                    bytes = bytes
                )
            )
        } catch (e: Exception) {
            Log.e("FileUtils", "Failed to read file from uri: $uri", e)
            Result.failure(e)
        }
    }
}
