package com.retailassistant.core

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * A centralized error handling utility to provide consistent, user-friendly messages
 * from various exceptions thrown throughout the application.
 */
object ErrorHandler {

    fun getErrorMessage(throwable: Throwable, defaultMessage: String = "An unexpected error occurred"): String {
        return when (throwable) {
            is SocketTimeoutException -> "Request timed out. Please check your connection."
            is UnknownHostException -> "No internet connection. Please check your network."
            is HttpRequestException -> "Network request failed. Please try again."
            is RestException -> parseRestException(throwable)
            is IllegalArgumentException -> throwable.message ?: "Invalid input provided."
            is IllegalStateException -> throwable.message ?: "An invalid operation was attempted."
            else -> throwable.message?.takeIf { it.isNotBlank() } ?: defaultMessage
        }
    }

    private fun parseRestException(exception: RestException): String {
        val message = exception.message ?: "A database error occurred."
        return when {
            message.contains("customers_user_id_name_key") -> "A customer with this name already exists."
            message.contains("duplicate key") -> "This record already exists."
            message.contains("foreign key constraint") -> "Invalid reference. Please refresh and try again."
            else -> message
        }
    }

    fun isNetworkError(throwable: Throwable): Boolean =
        throwable is SocketTimeoutException || throwable is UnknownHostException || throwable is HttpRequestException

    fun isAuthError(throwable: Throwable): Boolean {
        return if (throwable is HttpRequestException) {
            try {
                val statusCode = throwable.message?.let { msg ->
                    when {
                        msg.contains("401") -> 401
                        msg.contains("403") -> 403
                        else -> null
                    }
                }
                statusCode == 401 || statusCode == 403
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }
}
