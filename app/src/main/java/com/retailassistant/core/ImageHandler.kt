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
        private const val DOCUMENT_COMPRESSION_QUALITY = 90 // Higher quality for text clarity.
    }

    suspend fun compressImageForUpload(imageUri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // Validate URI accessibility first
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: throw IOException("Cannot access image URI")
            inputStream.close()

            // First pass: Decode with inJustDecodeBounds=true to check dimensions without allocating memory.
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            // Validate image dimensions
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                throw IOException("Invalid image dimensions")
            }

            // Calculate the optimal `inSampleSize` to scale down the image.
            options.inSampleSize = calculateInSampleSize(options, TARGET_WIDTH)

            // Second pass: Decode the bitmap with the calculated `inSampleSize`.
            options.inJustDecodeBounds = false
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                    ?: throw IOException("Failed to decode image")
                
                ByteArrayOutputStream().use { baos ->
                    val success = bitmap.compress(Bitmap.CompressFormat.JPEG, DOCUMENT_COMPRESSION_QUALITY, baos)
                    if (!success) {
                        throw IOException("Failed to compress image")
                    }
                    val compressedBytes = baos.toByteArray()
                    
                    // Validate compressed size (max 10MB as per storage bucket limit)
                    if (compressedBytes.size > 10 * 1024 * 1024) {
                        throw IOException("Compressed image too large (${compressedBytes.size / 1024 / 1024}MB)")
                    }
                    
                    compressedBytes
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageHandler", "Image compression failed", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqWidth || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested width.
            while ((halfHeight / inSampleSize) >= reqWidth && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
