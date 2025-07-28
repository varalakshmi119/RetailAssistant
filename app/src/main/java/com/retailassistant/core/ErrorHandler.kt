package com.retailassistant.core

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Centralized error handling utility for consistent user-friendly error messages
 */
object ErrorHandler {
    
    fun getErrorMessage(throwable: Throwable, defaultMessage: String = "An unexpected error occurred"): String {
        return when (throwable) {
            is SocketTimeoutException -> "Request timed out. Please check your connection and try again."
            is UnknownHostException -> "No internet connection. Please check your network and try again."
            is HttpRequestException -> {
                "Network request failed. Please check your connection and try again."
            }
            is RestException -> {
                when {
                    throwable.message?.contains("duplicate key") == true -> 
                        "This record already exists."
                    throwable.message?.contains("foreign key") == true -> 
                        "Invalid reference. Please refresh and try again."
                    throwable.message?.contains("not null") == true -> 
                        "Required information is missing."
                    else -> throwable.message ?: "Database error occurred."
                }
            }
            is IllegalArgumentException -> throwable.message ?: "Invalid input provided."
            is IllegalStateException -> throwable.message ?: "Invalid operation state."
            else -> throwable.message ?: defaultMessage
        }
    }
    
    fun isNetworkError(throwable: Throwable): Boolean {
        return throwable is SocketTimeoutException || 
               throwable is UnknownHostException ||
               throwable is HttpRequestException
    }
    
    fun isAuthError(throwable: Throwable): Boolean {
        return throwable is HttpRequestException && 
               throwable.message?.contains("401") == true ||
               throwable.message?.contains("403") == true
    }
}