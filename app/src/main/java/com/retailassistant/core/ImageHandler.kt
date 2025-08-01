package com.retailassistant.core
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
/**
 * An optimized utility for image processing. It efficiently compresses images
 * on a background thread to prevent UI jank and reduce network payload for uploads.
 * It leverages the Coil library for efficient decoding and resizing.
 */
class ImageHandler(private val context: Context) {
    private companion object {
        private const val TARGET_WIDTH = 1080
        private const val COMPRESSION_QUALITY = 75
        private const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024 // 5MB
    }

    /**
     * FIX: Re-implemented using the Coil library for more efficient and robust image decoding and resizing.
     * This avoids manual sample size calculation and leverages Coil's optimized pipeline.
     */
    suspend fun compressImageForUpload(imageUri: Uri): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            // Build an ImageRequest for Coil to process the image
            val request = ImageRequest.Builder(context)
                .data(imageUri)
                .size(Size(TARGET_WIDTH, TARGET_WIDTH)) // Resize while maintaining aspect ratio
                .allowHardware(false) // Required to access the bitmap
                .build()

            // Execute the request and get the result
            val result = context.imageLoader.execute(request)
            val bitmap = result.drawable?.toBitmap()
                ?: throw IOException("Failed to decode image stream with Coil.")

            // Compress the resulting bitmap
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
}