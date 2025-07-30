package com.retailassistant.core
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A centralized error handling utility to provide consistent, user-friendly messages
 * from various exceptions thrown throughout the application.
 */
object ErrorHandler {

    // --- Supabase/Postgres specific error codes for robust matching ---
    private const val POSTGRES_UNIQUE_VIOLATION_CODE = "23505"
    private const val POSTGRES_FOREIGN_KEY_VIOLATION_CODE = "23503"

    fun getErrorMessage(throwable: Throwable, defaultMessage: String = "An unexpected error occurred"): String {
        return when (throwable) {
            is SocketTimeoutException -> "Request timed out. Please check your connection."
            is UnknownHostException -> "No internet connection. Please check your network."
            is HttpRequestException -> "Network request failed. Please try again."  // Generic, as no status available
            is RestException -> parseRestException(throwable)
            is IllegalArgumentException -> sanitizeErrorMessage(throwable.message) ?: "Invalid input provided."
            is IllegalStateException -> sanitizeErrorMessage(throwable.message) ?: "An invalid operation was attempted."
            else -> throwable.message?.takeIf { it.isNotBlank() } ?: defaultMessage
        }
    }

    private fun parseRestException(exception: RestException): String {
        // Parse JSON from exception.message for structured error details
        val errorJson: JsonObject? = try {
            exception.message?.let { Json.decodeFromString(JsonObject.serializer(), it) }
        } catch (e: Exception) {
            null  // Fallback if not valid JSON
        }
        val code = errorJson?.get("code")?.jsonPrimitive?.content
        val details = errorJson?.get("details")?.jsonPrimitive?.content
        val message = errorJson?.get("message")?.jsonPrimitive?.content ?: exception.message

        // Prioritize matching on 'code' as per Supabase docs best practices
        return when (code) {
            POSTGRES_UNIQUE_VIOLATION_CODE -> {
                when {
                    details?.contains("customers_user_id_name_key") == true -> "A customer with this name already exists."
                    else -> "This record already exists."
                }
            }
            POSTGRES_FOREIGN_KEY_VIOLATION_CODE -> "Invalid reference. The related item may have been deleted."

            "email_not_confirmed" -> "Please confirm your email, then sign in."
            "invalid_credentials" -> "Invalid email or password."
            "user_already_exists", "email_exists", "phone_exists" -> "An account with this email or phone already exists. Please sign in."
            "weak_password" -> "Password is too weak. Please use at least 6 characters."
            "email_provider_disabled", "phone_provider_disabled" -> "Signups with email/phone are disabled."
            "over_email_send_rate_limit", "over_sms_send_rate_limit" -> "Too many messages sent. Please wait before trying again."
            "over_request_rate_limit" -> "Too many requests. Please try again in a few minutes."
            "otp_expired" -> "OTP code has expired. Please request a new one."
            "validation_failed" -> "Invalid input format. Please check your details."
            "identity_already_exists" -> "This identity is already linked to an account."

            else -> when (exception.statusCode) {
                401 -> "Authentication failed. Please sign in again."
                403 -> "You don't have permission to perform this action."
                in 500..599 -> "A server error occurred. Please try again later."
                429 -> "Rate limit exceeded. Please wait and try again."
                else -> message ?: "A database error occurred."
            }
        }
    }

    fun isNetworkError(throwable: Throwable): Boolean =
        throwable is SocketTimeoutException || throwable is UnknownHostException || throwable is HttpRequestException

    fun isAuthError(throwable: Throwable): Boolean {
        return when (throwable) {
            is RestException -> throwable.statusCode == 401 || throwable.statusCode == 403
            else -> false  // HttpRequestException has no status, so can't be auth error
        }
    }

    /**
     * Sanitizes error messages to prevent exposure of sensitive information
     */
    private fun sanitizeErrorMessage(message: String?): String? {
        if (message.isNullOrBlank()) return null
        
        // Remove potential sensitive patterns
        val sensitivePatterns = listOf(
            Regex("password[\\s]*[:=][\\s]*\\S+", RegexOption.IGNORE_CASE),
            Regex("token[\\s]*[:=][\\s]*\\S+", RegexOption.IGNORE_CASE),
            Regex("key[\\s]*[:=][\\s]*\\S+", RegexOption.IGNORE_CASE),
            Regex("secret[\\s]*[:=][\\s]*\\S+", RegexOption.IGNORE_CASE),
            Regex("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"), // Credit card patterns
            Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b") // Email patterns in error messages
        )
        
        var sanitized = message ?: return null
        sensitivePatterns.forEach { pattern ->
            sanitized = pattern.replace(sanitized, "[REDACTED]")
        }
        
        return sanitized.take(200) // Limit message length
    }
}
