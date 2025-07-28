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
                    bitmap?.compress(Bitmap.CompressFormat.JPEG, DOCUMENT_COMPRESSION_QUALITY, baos)
                    baos.toByteArray()
                }
            }
        } catch (e: IOException) {
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
