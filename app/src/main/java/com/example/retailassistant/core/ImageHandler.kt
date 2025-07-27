package com.example.retailassistant.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * A highly optimized utility for image processing. It efficiently compresses images
 * on a background thread to prevent UI jank and reduce network payload for uploads.
 *
 * This is an injectable class to improve testability.
 */
class ImageHandler {
    private val TAG = this::class.java.simpleName

    companion object {
        private const val TARGET_WIDTH = 1080
        private const val COMPRESSION_QUALITY = 85 // A good balance between size and quality.
        private const val DOCUMENT_COMPRESSION_QUALITY = 90 // Higher quality for document text clarity
    }

    /**
     * Compresses an image from a given Uri. It first checks dimensions without loading the
     * full image into memory, calculates an optimal sample size, and then compresses it.
     * This is memory-safe and efficient for preparing images for upload.
     *
     * @param context The application context.
     * @param imageUri The Uri of the image to compress.
     * @param isDocument Whether this is a document scan (uses higher quality compression).
     * @return A [ByteArray] of the compressed JPEG image, or null on error.
     */
    suspend fun compressImageForUpload(
        context: Context,
        imageUri: Uri,
        isDocument: Boolean = false
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // First pass: Decode with inJustDecodeBounds=true to check dimensions without allocating memory.
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            // Calculate the optimal `inSampleSize` to scale down the image.
            options.inSampleSize = calculateInSampleSize(options, TARGET_WIDTH)

            // Second pass: Decode the bitmap with the calculated `inSampleSize`.
            options.inJustDecodeBounds = false
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                ByteArrayOutputStream().use { baos ->
                    val quality = if (isDocument) DOCUMENT_COMPRESSION_QUALITY else COMPRESSION_QUALITY
                    bitmap?.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    baos.toByteArray()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error compressing image for upload", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqWidth || width > reqWidth) { // Simplified for square-ish target
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqWidth && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
