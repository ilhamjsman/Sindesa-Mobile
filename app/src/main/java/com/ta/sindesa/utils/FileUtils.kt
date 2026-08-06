package com.ta.sindesa.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Environment
import androidx.core.content.FileProvider

import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream

object FileUtils {

    fun uriToFile(context: Context, uri: Uri): File {
        if (uri.scheme == "file") {
            val path = uri.path
            if (!path.isNullOrEmpty()) {
                val existingFile = File(path)
                if (existingFile.exists() && existingFile.length() > 0) {
                    return existingFile
                }
            }
        }

        val contentResolver = context.contentResolver
        val fileName = getFileName(context, uri)
        val file = File(context.cacheDir, "upload_" + System.currentTimeMillis() + "_" + fileName)
        
        val inputStream: InputStream? = contentResolver.openInputStream(uri)
        val outputStream = FileOutputStream(file)
        
        // Check if it's an image and needs orientation fix & compression
        val mimeType = contentResolver.getType(uri)
        val isImage = (mimeType != null && mimeType.startsWith("image/")) ||
                fileName.endsWith(".jpg", true) ||
                fileName.endsWith(".jpeg", true) ||
                fileName.endsWith(".png", true)
        
        if (isImage) {
            compressImage(inputStream, outputStream)
        } else {
            inputStream?.copyTo(outputStream)
        }
        
        inputStream?.close()
        outputStream.close()
        return file
    }

    private fun compressImage(inputStream: InputStream?, outputStream: FileOutputStream) {
        if (inputStream == null) return

        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) return

        // Reading EXIF orientation tags from raw image bytes
        var degrees = 0
        try {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        // Calculate sample size for responsive memory usage
        options.inSampleSize = calculateInSampleSize(options, 1080, 1080)
        options.inJustDecodeBounds = false
        
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return

        // Rotate bitmap if EXIF metadata requires orientation correction
        if (degrees != 0) {
            try {
                val matrix = Matrix()
                matrix.postRotate(degrees.toFloat())
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = rotatedBitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Compress to 85% quality JPEG
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        bitmap.recycle()
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                if (cut != null) {
                    result = result.substring(cut + 1)
                }
            }
        }
        return result ?: "File terpilih"
    }

    fun createImageUri(context: Context): Uri? {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File(storageDir, "JPEG_${timeStamp}.jpg").apply {
            createNewFile()
            deleteOnExit()
        }
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            null
        }
    }

    fun clearCacheFiles(context: Context) {
        try {
            context.cacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {}
    }
}
