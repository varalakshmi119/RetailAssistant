package com.retailassistant.core
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
/**
 * An optimized utility for image processing. It efficiently compresses images
 * on a background thread to prevent UI jank and reduce network payload for uploads.
 */
class ImageHandler(private val context: Context) {
    private companion object {
        private const val TARGET_WIDTH = 1080
        private const val COMPRESSION_QUALITY = 80
        private const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024 // 5MB
    }
    suspend fun compressImageForUpload(imageUri: Uri): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                throw IOException("Invalid image dimensions")
            }
            options.inSampleSize = calculateInSampleSize(options, TARGET_WIDTH)
            options.inJustDecodeBounds = false
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: throw IOException("Failed to decode image stream.")
            ByteArrayOutputStream().use { baos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, baos)
                val compressedBytes = baos.toByteArray()
                if (compressedBytes.size > MAX_FILE_SIZE_BYTES) {
                    throw IOException("Compressed image is too large (${compressedBytes.size / 1024}KB). Max size is ${MAX_FILE_SIZE_BYTES / 1024 / 1024}MB.")
                }
                compressedBytes
            }
        }
    }
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqWidth || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqWidth && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
