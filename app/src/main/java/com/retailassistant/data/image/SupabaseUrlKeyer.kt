package com.retailassistant.data.image

import coil.key.Keyer
import coil.request.Options
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * A custom Coil Keyer that extracts the stable path from a temporary Supabase signed URL.
 * This allows Coil to use its disk cache effectively, as the cache key will be consistent
 * even when the signed URL's token changes.
 *
 * e.g., "https://.../storage/v1/object/sign/invoice-scans/path/to/image.jpg?token=..."
 * will be keyed simply as "/invoice-scans/path/to/image.jpg".
 */
class SupabaseUrlKeyer : Keyer<String> {
    override fun key(data: String, options: Options): String? {
        // Only apply this logic to Supabase storage URLs
        if (!data.contains("/storage/v1/object/sign/")) return null
        return try {
            val httpUrl = data.toHttpUrl()
            val pathSegments = httpUrl.pathSegments
            val signIndex = pathSegments.indexOf("sign")
            if (signIndex != -1 && signIndex + 1 < pathSegments.size) {
                // Return the full path after the /sign/ segment, which is the stable identifier.
                // e.g., "invoice-scans/user-id/image.jpg"
                pathSegments.subList(signIndex + 1, pathSegments.size).joinToString("/")
            } else {
                null // Not a valid signed URL structure
            }
        } catch (e: IllegalArgumentException) {
            null // Not a valid URL
        }
    }
}
