package com.retailassistant.data.remote

import android.util.Base64
import com.retailassistant.BuildConfig
import com.retailassistant.data.db.ExtractedInvoiceData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.SocketTimeoutException

// --- Gemini API Data Classes ---
@Serializable private data class GeminiRequest(val contents: List<Content>, val generationConfig: GenerationConfig)
@Serializable private data class Content(val parts: List<Part>)
@Serializable private data class Part(val text: String? = null, val inlineData: InlineData? = null)
@Serializable private data class InlineData(val mimeType: String, val data: String)
@Serializable private data class GenerationConfig(val responseMimeType: String)
@Serializable private data class GeminiResponse(val candidates: List<Candidate>)
@Serializable private data class Candidate(val content: Content)
@Serializable private data class GeminiErrorResponse(val error: ErrorBody)
@Serializable private data class ErrorBody(val message: String)

/**
 * A robust client for interacting with the Google Gemini API. It handles request
 * building, JSON response parsing, and error handling gracefully.
 */
class GeminiClient {
    private val modelId = "gemini-1.5-flash" // A fast and capable model
    private val apiKey = BuildConfig.GEMINI_API_KEY
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"

    private val client by lazy {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                })
            }
            engine {
                connectTimeout = 60_000
                socketTimeout = 60_000
            }
        }
    }

    suspend fun extractInvoiceData(imageBytes: ByteArray): Result<ExtractedInvoiceData> {
        return try {
            // Validate API key
            if (apiKey.isBlank() || apiKey == "YOUR_API_KEY") {
                throw IllegalStateException("Gemini API key is not configured in local.properties.")
            }
            
            // Validate image data
            if (imageBytes.isEmpty()) {
                throw IllegalArgumentException("Image data is empty")
            }
            
            // Check image size (Gemini has limits)
            if (imageBytes.size > 20 * 1024 * 1024) { // 20MB limit
                throw IllegalArgumentException("Image too large for AI processing (${imageBytes.size / 1024 / 1024}MB)")
            }
            
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val request = buildRequest(base64Image)

            val responseString: String = client.post(apiUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            val extractedData = parseAndDecodeResponse(responseString)
            
            // Validate extracted data
            if (extractedData.customerName.isNullOrBlank() && 
                extractedData.totalAmount == null && 
                extractedData.date.isNullOrBlank()) {
                android.util.Log.w("GeminiClient", "AI extracted minimal data from image")
            }
            
            Result.success(extractedData)
        } catch (e: SocketTimeoutException) {
            android.util.Log.e("GeminiClient", "AI service timeout", e)
            Result.failure(Exception("AI service timed out. Please try again."))
        } catch (e: IllegalStateException) {
            android.util.Log.e("GeminiClient", "Configuration error", e)
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("GeminiClient", "Invalid input", e)
            Result.failure(e)
        } catch (e: Exception) {
            android.util.Log.e("GeminiClient", "AI extraction failed", e)
            Result.failure(Exception("AI extraction failed: ${e.message ?: "An unknown error occurred."}"))
        }
    }

    private fun parseAndDecodeResponse(responseString: String): ExtractedInvoiceData {
        val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        // Gemini can return either a successful response or an error object.
        return if (responseString.contains("\"candidates\"")) {
            val response = jsonParser.decodeFromString<GeminiResponse>(responseString)
            val jsonText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("AI response was empty or malformed.")
            // The response itself is a JSON string, which we decode.
            jsonParser.decodeFromString<ExtractedInvoiceData>(jsonText)
        } else {
            val errorResponse = jsonParser.decodeFromString<GeminiErrorResponse>(responseString)
            throw Exception("Gemini API Error: ${errorResponse.error.message}")
        }
    }

    private fun buildRequest(base64ImageData: String): GeminiRequest {
        // This prompt is hyper-specific and engineered for reliability.
        val prompt = """
            You are an expert AI for the "Retail Assistant" app, designed for small business owners in India.
            Your task is to analyze the provided invoice image and extract key information into a single, valid JSON object.
            Extract these fields:
            - "customer_name": The person or company name being billed.
            - "date": The invoice issue date in "YYYY-MM-DD" format.
            - "due_date": The payment due date in "YYYY-MM-DD" format. If no due date is found, use the issue date.
            - "phone_number": The customer's phone number.
            - "email": The customer's email address.
            - "total_amount": The final total amount as a numeric value (e.g., 123.45 or 15000).

            **CRITICAL RULES:**
            1. Your entire output MUST BE ONLY the JSON object. No extra text, comments, markdown, or explanations.
            2. If a specific value (like phone_number or email) is not found, use `null` for that field in the JSON.
            3. The JSON must be perfectly valid and parseable. `total_amount` MUST be a number, not a string. Do not include currency symbols.
        """.trimIndent()

        val contents = listOf(Content(listOf(
            Part(text = prompt),
            Part(inlineData = InlineData("image/jpeg", base64ImageData))
        )))

        // Forcing JSON output is a powerful feature of modern Gemini models.
        val generationConfig = GenerationConfig(responseMimeType = "application/json")
        return GeminiRequest(contents, generationConfig)
    }
}
